# Fork review: manikya and sanju0070 (2026-10-20)

📐 CALCULATED — read the code, nothing built or tested

Read-only review of two small forks. Fork code was read through the GitHub API only. Nothing was built, run, or posted.
Our side is `integration/next` at `de3fe50a`.

## 1. manikya/CallVault `samsung-whatsapp-voip-fallback` (b370763d, 2026-08-22)

**Verdict: skip. We already fixed the same bug, in a better way, one day later.**

### What it does

- On One UI, a WhatsApp call makes the device call state read OFFHOOK.
- The old gate asked `TelephonyManager.getCallState`, saw OFFHOOK, and refused to start the VoIP recorder.
- The fork works around that by name: if the audio-owning app's label contains "WhatsApp", it skips the gate.
- It also calls `VoipRecordingCoordinator.onCallStarted` from the carrier service, and `onCallEnded` when the carrier service stops.
- The rest is personal build glue: `.patched` app id, debug-key signing of release, an older NDK, `checkReleaseBuilds = false`, and a hardcoded `com.baba.callvault.patched.recorder` authority.

### Is it mic-only?

No. It starts our normal VoIP recorder (`VoipRecordingCoordinator.onCallStarted`), which records both sides.
So it does not break the "both sides or it fails" rule. It is a gate bypass, not a single-side fallback.

### Why we don't need it

- Our gate now asks Telecom whether the **carrier** has a call up (`TelecomManager.isInManagedCall`), not the device call state.
  A WhatsApp call is self-managed, so it no longer blocks. See `app/src/main/java/com/baba/callvault/services/recording/VoipTelephonyGate.kt:35-42` (the comment names this exact One UI case) and `:78-82`.
- That fix is commit `898f0690` (2026-08-23), shipped in v2.2.0.
- The detector uses it at `app/src/main/java/com/baba/callvault/services/recording/VoipCallDetector.kt:215`.
- The carrier service already stands down for an app call, so no second file is made: `RecordingForegroundService.kt:298-315` (#54, `bffdd4d2`), using `isVoipCallInProgress()` at `RecordingForegroundService.kt:194`.

### Why the fork's approach would be worse here

- It works only for apps whose label contains "WhatsApp". Our gate covers Signal, Telegram and the rest.
- It adds a second place that starts the VoIP recorder (the carrier service). Two start paths is the race class in `only-one-recorder-host` memory.
- Its `onCallEnded` on carrier stop could end a VoIP recording that the VoIP path still owns.
- It bypasses the gate on name alone, so a real Wi-Fi-calling carrier call while WhatsApp holds audio could be recorded twice.

Nothing to adopt. Samsung Wi-Fi Calling (#45) is a separate limit and this fork does not touch it.

## 2. sanju0070/CallVault `fix-build` (0cfcd092, 9f6c5ee7)

**Verdict: adapt one thing (model import from file). Skip the rest.**

It is a private, stripped build: no updater, no network downloads, Shizuku and transcription hidden behind compile-time flags (`FeatureFlags.kt`).

### Model import from file — adapt

- Adds `ModelRepository.importFromUri(context, model, uri)`: the user picks the model file with the system picker (`OpenDocument`), it is copied to the `.part` file, size-checked, then passed to our existing `finalizeDownload` (SHA-256 gate).
- UI: `ui/common/ModelImport.kt` (`rememberModelImporter`), wired into the transcription and summariser rows in place of Download.
- **We do not have this.** Our only path is network download. No import code in `app/src/main/java` (grep for `importFromUri`, model `OpenDocument`: only settings import at `SettingsScreen.kt:332` and `HomeScreen.kt:576`).
- It reuses our own gate, so it is small: `ModelRepository.kt:166` (`partFileFor`) and `ModelRepository.kt:194` (`finalizeDownload`).
- Who it helps: users on metered or flaky links, the "download damaged" reporter (`model-download-damaged-report` memory), and offline/F-Droid-minded users.
- If we adopt it, offer it **next to** Download, not instead of it. And fix these gaps in the fork's version:
  - It does not take the per-model writer lock (`ModelDownloadWorker.kt:77`, `:288`). A running download and an import would both write the same `.part`. That is the double-writer bug we already fixed once. Take the lock or use a separate temp file.
  - It deletes any banked partial download before copying, without asking.
  - It runs in a composable's coroutine scope; leaving the screen mid-copy of a 3.46 GB file cancels it. A worker would be safer.
  - It skips the summariser's requirements dialog.
  - It does not check free space first.
  - Its strings are English-only (`translatable="false"`); our lint requires translations.

### "Standby" — skip

- "Standby" means: when the master switch is set to STOPPED, also cancel the retention, Drive-sync and transcription schedules, and restore them on ACTIVE (`RecordingSwitch.tearDown/bringUp`, plus a STOPPED check in each scheduler's `apply`).
- Our STOPPED stops the keep-alive and the daemon only: `RecordingSwitch.kt:46-64`. Housekeeping keeps running.
- Skip, because stopping recording should not stop syncing or pruning the recordings the user already has. A user who pauses recording would silently stop getting Drive backups. If anyone asks for a true "do nothing" mode, it should be its own setting.

### Everything else — skip

- Network removal, updater removal, hidden Shizuku and transcription: personal choices for a private build, not improvements.
- `isDebuggable = false` on **debug**: our release build is already non-debuggable; debug must stay debuggable for development.
- Deleting `MANAGE_EXTERNAL_STORAGE` from the debug manifest: that file is debug-only and never ships (`app/src/debug/AndroidManifest.xml:5-21`).
- One useful confirmation: the fork keeps `INTERNET` because the loopback ADB socket needs it. Same as ours (`AndroidManifest.xml:24`).

## Summary

- manikya: skip. Not mic-only (it uses the both-sides VoIP recorder), but our Telecom-based gate (`898f0690`, v2.2.0) already fixes Samsung WhatsApp for all apps.
- sanju0070: adapt "import model from file" as an extra option beside Download, with the lock fix. Skip standby and the stripping.
