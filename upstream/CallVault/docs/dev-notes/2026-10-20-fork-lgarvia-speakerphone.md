# Fork review: lgarvia/CallVault — speakerphone route resilience + 2 s idle debounce

📐 CALCULATED — read the code, nothing built or tested

Commit `2f988633` (Luis, 2026-09-29) on `lgarvia/CallVault` branch `feature/speakerphone-route-resilience`.
Its parent is `aa1cae53` = `v2.2.0-6`. Compared against our `integration/next` on 2026-10-20.

## Verdict

**Skip the commit. Take one small idea from it, rewritten for our code.**

- Handoff disabled → **skip.** It switches off Resilient recording for everyone. The bug it works around
  was fixed in v2.3.0.
- 2 s IDLE debounce → **skip.** No evidence it fixes anything, and it can glue two calls into one file.
- Direct-path reconnect → **adapt.** Our direct loop has a real (rare) gap: a dead `AudioRecord` makes it
  spin silently until the call ends. Worth a small fix of our own.

The patch would not apply anyway: `captureLoop` no longer exists. The direct path was split into
`readLoop` + `encodeLoop` with a queue between them.

## What the fork says broke

On a POCO X7 Pro (HyperOS, Android 16) a 45 s call recorded only 23 s. It stopped the moment speaker was
pressed. The fork's dev-note blames:

1. Handoff: `CBLK_INVALID` on route change ends the drain, and the app cannot rebuild the track.
2. Direct: `read()` returns errors after the route change and nothing recovers.
3. `CALL_STATE_IDLE` stops at once, so a "transient" IDLE or call waiting could cut the recording.

Their dev-note says "Verified in production". It shows no log. Only cause 1 matches their symptom
(recording ends at the moment of the switch, call carries on).

## Does the same failure exist in integration/next?

### Handoff path — already fixed (v2.3.0)

- The fork's base predates `7ee9afe8` "rebuild the capture when the platform takes it away mid-call"
  (first in v2.3.0). On their base, `CBLK_INVALID` really did end the recording. That is their bug.
- Today the drain is a supervisor. On `INVALIDATED` it asks the daemon for a new capture and resumes
  into the **same** pipe and encoder, up to 3 times: `HandoffReceiver.kt:336-420`, `MAX_REARMS = 3` at
  `HandoffReceiver.kt:83`. The native drain keeps the pipe open on a resumable exit:
  `audiohandoff.cpp:332-337` and the `keepPipeOpen` close at the end of `nativeDrainToPipe`.
- So the fork's fix (`HandoffPolicy.isUsable = false`) is not needed. It would also remove the feature
  that survives a daemon death mid-call. Our policy is at `HandoffPolicy.kt:47-48`.
- Known gap that remains: a route change **while the daemon is down** cannot be rebuilt
  (`2026-09-15-wedge-regression-and-app-flow-map.md`, row "HandoffReceiver.kt:383-411"). Rare.

### Route changes are not a known trigger here

- Tested on the OP12 on 2026-09-05: speaker at +2 s, earpiece at +9.7 s, more switches after.
  Result `37.1s encoded of 37.1s captured`, no invalidation
  (`2026-09-03-shell-mic-open-first-evidence.md:856-874`).
- Speaker → Bluetooth mid-call was also tested with handoff on: no invalidation
  (`spike-audio-handoff.md:510-513`).
- MediaTek / HyperOS may differ. That is not proven either way. We have no POCO X7 Pro.

### Direct path — a smaller gap than the fork claims, but a real one

- The daemon uses a normal Java `AudioRecord`. Its native `obtainBuffer()` sees an invalidated track and
  calls `restoreRecord_l()` itself (the reasoning in `7ee9afe8`'s message). So a route change
  normally never reaches our loop as an error.
- If the restore fails, `read()` returns a negative code (e.g. `ERROR_DEAD_OBJECT`) forever. Our loop
  does `if (read <= 0) continue` (`DirectAudioRecorderSession.kt:232-234`):
  - no log, no counter — the debug report cannot show it happened;
  - a busy spin at `THREAD_PRIORITY_URGENT_AUDIO` until the call ends;
  - the file just stops growing, and still reports success.
- The recording does **not** end early on its own — it ends at IDLE as normal. The audio after the
  failure is simply missing.

### VoIP path — not touched by the fork

- A failed read ends that side's feeder thread: `VoipCaptureSession.kt:444-450` ("feeder ending").
  Same restore argument applies; rare.
- VoIP call end is detected from audio mode, not `PHONE_STATE`, and already has a 1.5 s end debounce
  (`VoipCallDetector.kt:203`, `END_DEBOUNCE_MS = 1_500L` at `:297`). The fork's IDLE debounce does not
  reach it.

### Scrcpy path (Shizuku)

- Capture runs inside scrcpy's own `AudioRecord`, which gets the same platform restore. Not touched.

## Is a 2 s IDLE debounce safe for us? No.

Our IDLE handler is `CallSessionManager.kt:209-221`. It now also cancels `answerJob` (record-from-answer)
and schedules `CutOffRescueWorker`. The fork's version predates both.

- **Route switches do not send IDLE.** `PHONE_STATE` IDLE means no call is left. So the debounce does
  nothing for the reported bug.
- **Call waiting does not send IDLE either.** The legacy state stays OFFHOOK (or shows RINGING) while
  any call is up. A real IDLE in the middle of a call has never been reported to us. No issue matches
  (`gh issue list` for speaker / route / truncated / bluetooth: nothing).
- **It can merge two calls.** The fork cancels the pending stop on any RINGING or OFFHOOK. A new call
  within 2 s of hanging up (call back, redial, an incoming call right after) cancels the stop. The old
  recording keeps running into the new call, `session.clear()` never runs, and the old call's metadata
  (number, direction) stays locked onto it.
- **Only-one-recorder-host rules:** not broken directly. Nothing starts a second host. But the
  service stays in "recording" 2 s longer, and the next call's START meets a service that has not
  stopped. That is the `wasRecordingServiceStartIntentSend` / stuck-notification area
  (`stuck-call-in-progress-notification` memory), which is fragile already.
- **Voicemail / missed call / record-from-answer:** each recording gets 2 s of post-call audio
  (hang-up tone or silence). Duration checks against the call log, the Honor-preset call-log time
  (#56) and the cut-off rescue all compare against call times; 2 s more makes them drift. Not
  measured.
- **It hides real ends.** If `tm.callState` is OFFHOOK after 2 s, it keeps recording with no further
  check — nothing ever stops it except the next IDLE.

## If we adapt: small plan (direct path only)

1. In `readLoop`, count consecutive `read <= 0`. Log the first error code once, and the count at stop
   (add it to `captureDiagnosticsLine`).
2. Sleep a few ms between failed reads instead of spinning.
3. After N consecutive errors (e.g. 4, about 100 ms), release the record and reopen with
   `openAudioRecord(androidSource)` for up to 2 s. On success, keep the same queue, encoder and muxer.
   If the channel count changes, stop and finalise (the encoder is mono but turns/downmix depend on it).
4. Insert silence for the gap, so the timeline does not compress (the fork does not).
5. If reopen fails, end the read loop so the file finalises, and say so in the log.
6. Do **not** take the IDLE debounce or the `HandoffPolicy` change.

Note the "no second voice capture during a call" rule: release the old record **before** opening the
new one, never two at once. The fork does this in the right order.

## How to test on a phone

Route switching alone will not exercise the new code — on our phones it does not break capture. So:

1. **Regression check, both carrier paths.** OP12 standalone, Resilient recording ON, then OFF. Real
   carrier call, 60 s. Speaker at +5 s, earpiece at +15 s, Bluetooth at +25 s, back at +35 s.
   Read the log for `Recording via DIRECT AudioRecord` vs the handoff line, any `drain segment` /
   `rebuilding the capture` lines, and check the file length equals the call length.
2. **Recovery check.** Needs a fault: a debug-only flag that makes `read()` return
   `ERROR_DEAD_OBJECT` for a few hundred ms at +10 s. Expect a reopen log line, one continuous file of
   the full length, and a short silence at +10 s.
3. **Ask the reporter.** The fork author's POCO X7 Pro is the only phone where route switches were seen
   to cut a recording. Ask Luis whether he still sees it on v2.3.0 or later with Resilient recording on,
   and for a debug log of one call. If it still happens, the log names the path that failed.
