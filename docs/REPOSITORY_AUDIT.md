# Repository audit — 2026-11-11

## Executive result

The actual product APK is the pinned CallVault Android application in `vendor/CallVault`, not
the old root-level microphone recorder. The root microphone-only prototype and its competing Gradle
project have been removed. The app is configured for automatic incoming and outgoing **carrier-call**
recording by default on a fresh install, with Shizuku as the default privilege provider.

This is a source/build audit, **not a device-compatibility certification**. No physical OnePlus 11R
(CPH2487, Android 16 / OxygenOS 16.0.5, Google Phone) has been available to run the acceptance
tests. A green cloud build cannot prove that the ROM exposes both cellular audio directions.

## Scope reviewed

- Repository root build layout, submodule pin, CI workflow, build-profile patcher, documentation,
  licensing/provenance notes and all root-owned source files.
- Recursive upstream CallVault tree inventory: 1,375 tracked files at pinned commit
  `00a77594085d61aee2c8513c649911704fd970c6` (CallVault v2.5.0).
- High-risk execution path and related tests: phone-state and outgoing-call receivers,
  `CallSessionManager`, preferences and auto-record policy, recording foreground service,
  audio engine, recorder backend, Shizuku binding/user service, shell recorder service, manifest
  permissions/components, setup prerequisites, and the Shizuku round-trip/device tests.
- CI job logs, unit-test result, lint report, release APK alignment/signature/package/ABI metrics.

This is a focused code-path and repository-structure audit; it is not a manual line-by-line review
of every upstream asset, generated translation, model, native dependency or third-party library.

## Confirmed call-recording path

1. `PhoneStateReceiver` handles carrier phone-state broadcasts; `NewOutgoingCallReceiver` supplies
   the outgoing dialled number.
2. `CallSessionManager` builds call metadata and evaluates the carrier master switch, direction
   automation, contact/country filters and optional answer-wait policy.
3. The manager sends start/standby/stop commands to `RecordingForegroundService`.
4. `RecordingForegroundService` creates an `AudioRecordingEngine` session and reports startup,
   capture-start and daemon-loss failures visibly.
5. The engine obtains the recorder through `RecorderBackend`; Shizuku mode binds
   `ShizukuBackend` to `RecorderUserService`, which starts the same `RecorderServiceImpl` used by
   the standalone shell backend.
6. The recorder service performs the privileged audio capture and writes the recording file.

## Changes made in this audit

- Removed the unrelated root microphone recorder and its competing root Gradle files. The CallVault
  submodule is now the only Android product target.
- Kept the upstream submodule pinned rather than cherry-picking unreviewed native/UI files.
- Extended the revision-checked Shizuku profile patch to annotate the Telecom permission boundary:
  `managedCallInProgress` already wraps the guarded call in `runCatching`, which handles a revoked
  `READ_PHONE_STATE` permission; lint did not infer that Kotlin exception handling.
- Added `scripts/verify_shizuku_profile.py`. CI now fails if carrier recording is disabled, either
  automatic direction default is lost, the phone-call receivers/foreground service are missing, or
  the Shizuku binder path is disconnected.
- Restored Android lint as a blocking CI gate after addressing its reported permission finding.
- Kept the unit-test fixture explicitly in standalone mode for the ADB-prerequisite tests; this tests
  the behaviour those tests claim to exercise rather than accidentally testing Shizuku mode.

## Configuration details that matter

- `CARRIER_RECORDING_ENABLED` remains true.
- `AUTO_RECORD_INCOMING` and `AUTO_RECORD_OUTGOING` are patched to true.
- The default privileged mode is patched to Shizuku.
- These are **defaults**, not a forced reset of saved user preferences. If the app was already
  installed and has stored values, its existing choices are preserved; check Settings → Recording
  and Settings → Connection after installing.
- Shizuku must be running and this app must have Shizuku permission. On a non-rooted phone it may
  need to be started again after reboot. The recorder does not require its own persistent Wireless
  debugging session while the Shizuku server is already alive.
- The app keeps recording state visible and does not suppress announcements or consent notices.

The default audio source is Android's `VOICE_CALL` source. The engine requests two capture
channels for that source on its handoff path (uplink/downlink) and downmixes to the app's normal
mono recording format. This is the correct intended path for both participants, but the shell-level
capture result remains OEM-dependent and must be listened to on the OnePlus 11R.

## Android platform caveats found during research

- The app targets Android API 36 (minimum API 30), appropriate for the stated Android 16 device.
- The carrier state path uses `ACTION_PHONE_STATE_CHANGED`; the outgoing receiver also listens for
  `ACTION_NEW_OUTGOING_CALL` to preserve the originally dialled number for redirection/metadata.
  Android deprecated that outgoing broadcast in API 29; from Android 15 it is no longer ordered and
  Android does not guarantee its timeliness. It is therefore a best-effort number-enrichment signal,
  not the only trigger for recording. The carrier call-state path still determines incoming/outgoing
  state and requests the recording.
- `READ_CALL_LOG` is a hard-restricted Android permission that the installer must allowlist.
  If the installation path does not allowlist it, call-log lookup and number enrichment may be
  unavailable even when the app is otherwise installed. This can affect contact filters and
  displayed caller details; it does not by itself prove that the audio capture path is unavailable.
- The official Shizuku manager is the baseline recommendation. Compatible forks with experimental
  no-Wi-Fi startup or watchdog behaviour are not verified on this phone and are not prerequisites.

## CI evidence and release status

Run #45 (commit `0f69c62220ad21427624ded98141375986b97f43`) passed unit tests, built the release
variant, aligned/signed the test APK and verified package `com.baba.callvault` and ARM64-only
native libraries. Artifact metrics from that run:

- APK size: 70,248,832 bytes
- SHA-256: `31cf70286c926b0ee26eb0ece1c6a3f7ad4e0ebab7794376d6287bc66d890a5e`
- ABI: `arm64-v8a`

That run preceded the latest audit hardening, so a fresh CI run on the final audit commit must pass
before its artifact is handed over. The artifact is signed with a disposable CI test key, not a
production key. Do not uninstall an existing app if you need to preserve its recordings/settings;
the package/signing-key relationship must be checked before installing over another build.

## Remaining release blockers

1. Fresh CI must pass unit tests, blocking lint, APK signing verification and profile-contract checks
   on the final commit.
2. Install on the actual OnePlus 11R and test incoming and outgoing carrier calls separately.
3. Listen to both sides in both recordings; confirm no silent/empty files are reported as success.
4. Repeat with Wireless debugging off and mobile data/Wi-Fi unavailable while Shizuku remains active.
5. Test after reboot, screen-off, battery saver, idle standby, Bluetooth and wired headset routing.
6. Confirm finalized files, metadata, library listing and playback.
7. Establish a permanent signing key and safe update strategy before calling any build a stable
   public release.

Until the target-device tests pass, describe the APK as a **device-test build**, not a fully verified
or guaranteed working OnePlus 11R release.
