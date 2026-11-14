# Fork review: slman890/CallVault `feature/local-call-assistant`

📐 CALCULATED — read the code, nothing built or tested

Fork base is `9933d929` (v2.4.5-11). It is 3 commits ahead of our main and 79 behind. Our side was checked
on `integration/next` on 2026-10-20. The fork's docs are in Arabic. I read them in full.

The fork is a **rebranded app**, not a patch for us. It changes the package to
`io.github.slman890.localcallassistant`, the name to "Local Call Assistant", the icon, the update repo to
`slman890/CallVault` and turns the update check off by default. None of that is for us.

## Verdict

| Commit | What | Real for us? | Decision |
|---|---|---|---|
| `8534f237` part 1: decoder liveness | Stops decoding with an error after 120 s with no PCM output | Yes, in theory. Our decode loop has no bound. No field report of it. | **Adopt** (small, ~15 lines + 1 string + test) |
| `8534f237` part 2: missing model | A tap with a missing model fails at once. An automatic run retries only twice. | Mostly no. Taps are already gated. Automatic runs still retry for ever, which is by design. | **Skip** (optional: cap automatic retries) |
| `8534f237` part 3: unreadable metadata | Marks FAILED when the duration read throws | No. Our duration read never throws. | **Skip** |
| `8534f237` part 4: model pinned at enqueue | Puts the model id in the work input | No real bug. It adds a new failure mode (pinned model deleted). | **Skip** |
| `8534f237` part 5: stage labels | Pill says "Loading model / Preparing audio / Saving transcript" | UX nicety only | Skip for now (adapt later if wanted) |
| `8534f237` part 6: call SIM label | Saves which SIM/account took the call. Shows "SIM 1 (name)" in the list. | Feature we do not have. No user has asked (0 GitHub issues for "SIM"/"dual"). | **Adapt** if wanted. Cheap with our existing call-log lookup. Don't take their DB change as is. |
| `4051f48c` | Full phone app: dialpad, recents, contacts, favourites, in-call screen, `InCallService`, `ROLE_DIALER` | Overlaps dialer mode, which the maintainer **abandoned** on 2026-07-24 | **Skip** |
| `525a3108` | Docs: build baseline, Bluetooth "acceptance gate" test matrix | Every Bluetooth row is NOT_RUN. No new finding. | **Skip** |

## Evidence

### 1. Decoder liveness — real, small, adopt

- Our decode loop is `AudioDecoder.kt:510-560`. `INFO_TRY_AGAIN_LATER -> Unit` is at line 535.
- The loop only ends on the output end-of-stream flag or on `shouldStop()` (line 514).
- If a codec stops giving output and never sends end-of-stream, the loop spins for ever. The pill
  shows "working" until the user taps Stop.
- Their fix: a tiny `AudioDecodeLiveness` class. It records the time of the last non-empty output buffer.
  It throws `AudioDecodeStalledException` after 120 s with none. A long decode is fine as long as audio
  keeps coming.
- The runner then stores a clear message ("Audio decoding produced no output for two minutes. The
  recording was kept.") instead of the raw exception text.
- Cost: one new file, three lines in `decodeRange`, one `when` in the runner's `onFailure`, one string
  (×14 locales), a unit test. Low risk.
- We have no report of this happening. It is a guard, not a fix for a known bug.

### 2. Missing model — already handled on the paths that matter

- Their bug: the worker returned `Result.retry()` for ever when the model file was missing
  (`TranscriptionWorker.kt:70-74`, still the same in ours). A tapped recording would spin for ever.
- Ours: every tap goes through `startTranscription` in `HomeScreen.kt:733-755`. Line 744 checks
  `ModelRepository.isInstalled` and shows the "model missing" dialog first. `ShareImportViewModel.kt:226`
  does the same. So a tap cannot reach the worker without a model.
- What is left: the call-end run (`TranscriptionScheduler.kt:175-193`) still retries without limit
  when the model is missing. That is on purpose: the comment at `TranscriptionWorker.kt:68-69` says
  it waits for the download to land.
- One small side effect is real: manual work uses `APPEND_OR_REPLACE` (`TranscriptionScheduler.kt:131-135`).
  A call-end job that keeps retrying holds up a tap queued behind it. It clears as soon as the selected
  model is installed, and the tap path will not queue without one. So this is minor.
- Their cap (2 retries, then FAILED) would turn a "waits for download" job into a FAILED row. I would not
  take it.

### 3. Unreadable metadata — not a bug for us

- Their fix wraps `audioDurationMs(uri)` (`TranscriptionRunner.kt:161`) in try/catch.
- Our `AudioDecoder.durationMs` (`AudioDecoder.kt:336-354`) already catches every exception and
  returns 0. It cannot throw. The new catch only matters for a test lambda.

### 4. Model pinned at enqueue, `abortWatcher` in `finally`

- Ours reads the model from settings when the worker runs (`TranscriptionWorker.kt:65`). Changing the
  model in Settings between queueing and running uses the new model. That is arguably what the user
  wants.
- Pinning creates a new case: the pinned model gets deleted, and the job fails.
- The `try/finally` around `abortWatcher.cancel()` (`TranscriptionWorker.kt:151-177`) changes nothing.
  The watcher is a child of the worker's `withContext`. If `runBatch` throws, structured concurrency
  cancels it anyway.

### 5. Call SIM label — a feature, not a fix

How theirs works:
- After a carrier call ends, `CallSimLookup.resolve` reads the call log 6 times, 400 ms apart. It reads
  `PHONE_ACCOUNT_COMPONENT_NAME` and `PHONE_ACCOUNT_ID`.
- It matches by direction, time window and number. If two different accounts match, it gives up and
  shows "SIM unknown". It never falls back to the default SIM.
- It turns the account into subId → slot, display name and carrier with
  `TelephonyManager.getSubscriptionId(PhoneAccountHandle)` and `SubscriptionManager`. Both use the
  existing `READ_PHONE_STATE`. No new permissions.
- It stores a SHA-256 of the raw account id, not the id itself. Slot, name and carrier go into five new
  Room columns.
- The Home subtitle shows "SIM 1 (Partner)". VoIP and imported recordings are skipped.

What we already have:
- No SIM handling anywhere. A grep for `PHONE_ACCOUNT`, `SubscriptionManager` and `PhoneAccountHandle`
  finds nothing in `app/src/main`.
- The BCR details file writes `"sim_slot": null` every time (`BcrMetadata.kt:83`). This feature would
  fill a field we already emit.
- We already read the call log after the call, with retries, for the Honor preset (#56):
  `honorCallStartFromLog` / `readCallLogSince`, `RecordingForegroundService.kt:662-699`. Adding
  `PHONE_ACCOUNT_ID` and `PHONE_ACCOUNT_COMPONENT_NAME` to that query is the cheap way in. It also
  matches numbers with `PhoneNumberUtils.compare`.
- Their number match is weaker: they keep digits only and strip a leading "00". So `+972…` against
  `05…` does not match. The lookup then gives up and shows "unknown".

If we adapt it:
- Reuse our call-log read. Don't add a second reader.
- Store the result in the catalog, or in the BCR details sidecar only.
- Think about a `{sim_slot}` filename token too. Dual-SIM users of BCR use that one.
- **Do not take their `RecordingDatabase` change as is.** It bumps v3→v4. It also **removes
  `fallbackToDestructiveMigration`** and turns on `exportSchema` (`RecordingDatabase.kt:33,91-92` in
  ours). That is a policy change. It needs its own decision.
- Who benefits: dual-SIM users who want to know which line took a call. That is common in Israel,
  India and the Arab world. No user has asked yet.
- Cost: about 100 lines, one migration, three strings ×14 locales, and a test on a real dual-SIM phone.
  Neither of our phones is confirmed dual-SIM.

### 6. Phone UI / default dialer (`4051f48c`) — skip

- Size: about 1,100 new Kotlin lines in `app/.../dialer/` (`PhoneScreen` 373, `InCallActivity` 212,
  `LocalInCallService` 155, `PhoneRepository` 148, `DialerCalls` 144, and more). Also 67 strings in
  two languages, three instrumented Telecom tests and a fake `ConnectionService`.
- Manifest: adds `CALL_PHONE`, `USE_FULL_SCREEN_INTENT`, `WAKE_LOCK`, `DIAL`/`tel:` intent filters and an
  `InCallService` with `IN_CALL_SERVICE_UI`.
- Invasive: `MainActivity` now opens the **dialpad by default** (`PhoneEntry.wantsPhone` returns true
  for a plain launch). The library is reached through a button. A Hub card is added.
- It does not touch recording. It only hands the live `PhoneAccountHandle` to the SIM lookup.
- This is the same ground as our dialer mode (PR #6), which the maintainer **abandoned** on 2026-07-24.
  The branch is deleted. See memory `dialer-mode-status.md`. Ours was further along: T9, audio-route
  picker, ADB-forced default dialer.
- Their own docs say it ran only on an x86 emulator with fake accounts. Real SIM: NOT_RUN.
- Their earlier plan suggested basing it on Fossify Phone (GPL-3.0). That might be worth a look if dialer
  mode ever comes back.

### 7. Bluetooth "acceptance gate" (`525a3108`) — nothing new

- `TEST_MATRIX.md` and `docs/DEVICE_TEST_GUIDE.md` give a test protocol. It covers earpiece, speaker,
  classic BT, car BT, USB-C and LE Audio, in and out, three repeats each. It records near/far dBFS, codec
  from the file, the capture path, and HFP/SCO vs A2DP.
- **Every row is NOT_RUN or NOT_TESTED.** They had no phone connected. There is no finding about
  Bluetooth recording.
- Their troubleshooting steps match what we already do: re-grant `WRITE_SECURE_SETTINGS` after an update,
  one recorder host only, no mic+speaker fallback.
- The checklist is a fair template for our own LE Audio test (memory
  `bluetooth-capture-works-le-audio-untested.md`). It is not evidence.
- One side note in `BASELINE_BUILD.md`: lint on our v2.4.5 base gave 31 errors (MissingPermission 1,
  NewApi 5, WrongConstant 2, MissingQuantity 16, LocalContextGetResourceValueCall 5,
  NonObservableLocale 2). We may already know this. It is worth a `lintDebug` run on `integration/next`.

## Licence note

The fork keeps GPL-3.0 and adds to NOTICE. If we take code from it, credit `slman890` in the commit and
NOTICE.
