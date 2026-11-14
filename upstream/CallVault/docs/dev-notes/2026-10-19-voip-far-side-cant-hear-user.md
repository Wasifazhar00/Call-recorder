# VoIP: the far party cannot hear the user while we record (2026-10-19)

📐 CALCULATED — from AOSP source and one sibling project's field report. Nothing measured on the reporter's
phone. Reporter's device, Android build and log: **unknown, requested**.

## The report

While CallVault records a WhatsApp call, the other person cannot hear the user at all. When the user stops
the recording, they hear him again.

## Likely cause: our near-side `MIC` capture wins the mic arbitration and WhatsApp is zero-filled

- The daemon runs as uid 2000. AudioPolicy treats every uid < 10000 as PROCESS_STATE_TOP without asking
  ActivityManager (`AudioPolicyService.cpp` A16 ~L1564, `ServiceUtilities.h` ~L48), so our capture is always "on top".
- Among non-privacy-sensitive captures only one uid gets audio: the latest starter among on-top clients.
  WhatsApp is on top only while its call screen is in front (not with the screen off on the earpiece,
  not in the background).
- `VOICE_COMMUNICATION` is privacy-sensitive by default and is protected; `MIC` is not. So:
  - WhatsApp on `VOICE_COMMUNICATION` (our OnePlus phones): both captures coexist. Matches what we measured.
  - WhatsApp on `src:MIC` (Galaxy S24 FE in July, and Samsung A53 in the SCR report): **WhatsApp is silenced
    for as long as we record.** AudioFlinger zero-fills a silenced track (`Threads.cpp` ~L8957). That is the report.
- Our re-take (`VoipCaptureSession.retakeMic`, after 15 all-zero chunks) is not needed to cause it, but every
  re-take makes us the latest starter again, re-silencing WhatsApp even when its screen is in front.
- Android 16 QPR2 adds flag `concurrent_audio_record_bypass_permission` (on in bp4a). With it, shell's
  CAPTURE_AUDIO_OUTPUT no longer lets us coexist with a privacy-sensitive capture: then OUR mic is the one
  silenced, and in one combination both sides get zeros. (OP9, Android 14: flag not present.)

Full source walk: scratchpad `research-voip-mic-arbitration.md`. Field reports:
`research-voip-mute-field-reports.md` (both from this session; copy in if they need to be kept).

## The same bug elsewhere

kitsumed/ShizuCallRecorder#1, comment 2026-10-17 (Samsung Galaxy A53, Android 16): "the other person cannot
hear my voice at all while recording is active". Fixed in their commit 72ce2850e2 (`setPrivacySensitive(false)`
on their mic capture); the reporter confirmed it on 2026-10-18. **That fix does not carry over**: SCR captured with
`VOICE_COMMUNICATION` (sensitive by default), while ours is already `MIC` (non-sensitive). For us it is only a guard.

## ⚠️ Correction to 2026-07-30-voip-near-party-silenced-on-one-ui.md

That note says winning the mic is safe because "with the phones in separate rooms, the far end still heard
everything". **This is contradicted** by the AOSP source, the SCR report and this report. Do not read it as
proof that taking the mic is safe. Its own dump shows `com.whatsapp ... SILENCED` while our capture won.

## Fix direction — 📐 not built, maintainer to decide

1. Before opening our mic, read the VoIP app's recording source via `getActiveRecordingConfigurations()`
   (daemon has MODIFY_AUDIO_ROUTING, so it sees uid/package/source/`isClientSilenced`). If it is `MIC`, do
   not take the mic.
2. While recording, if the VoIP app's capture shows `isClientSilenced`, release our mic at once.
3. Re-take only when Android says OUR record is silenced and the VoIP app is on `VOICE_COMMUNICATION`.
4. Log every client's source and silenced state at call start and at each re-take.
5. Set `setPrivacySensitive(false)` explicitly on our MIC record (guard only).

Steps 1–2 mean such calls cannot be recorded two-sided on that phone. Per the binary rule
(no single-side fallback), the recording should then fail with a clear message rather than mute the user.
Never keep a recording at the cost of muting the user's call.

## To settle it

- From the reporter: phone model, Android version/build, CallVault version, standalone or Shizuku, earpiece /
  speaker / Bluetooth, whether WhatsApp's call screen was in front or the screen was off, and a debug report
  shared within a few minutes of a 30 s recorded test call (it carries the `dumpsys audio` record history:
  look for `pack:com.whatsapp src:MIC SILENCED`).
- In-house: Galaxy S24 FE, WhatsApp call on the earpiece with the screen off, phones in separate rooms,
  listen on the far phone.
