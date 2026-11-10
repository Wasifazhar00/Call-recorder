# CallVault integration plan

## Why this engine

The existing app records from `MediaRecorder.AudioSource.MIC`. Android does not expose both cellular call directions to an ordinary third-party microphone recorder. A phone-state listener alone only detects call state; it does not grant access to telephony audio.

CallVault is the selected upstream engine because it already implements the difficult capture path and documents automatic phone-call recording, local storage and built-in ADB/Shizuku modes.

## Integration stages

### Stage 0 — repository correction (this branch)
- Correct the product definition: incoming/outgoing cellular calls, automatic start, both sides.
- Record exact target-device constraints and upstream provenance.
- Keep the old microphone implementation from being misrepresented as a call recorder.
- Preserve the existing `main` branch while the new direction is reviewed.

### Stage 1 — vendor/import upstream source
- Import the complete CallVault source at a pinned commit, including Gradle version catalog, wrapper, native CMake/JNI sources, assets, resources, workflows, tests, LICENSE and NOTICE.
- Preserve upstream copyright and GPL-3.0 terms.
- Audit bundled and downloaded dependencies/models individually.
- Record all local modifications in a maintained patch or clearly marked files.
- Do not copy only the audio service: capture, ADB bootstrap, lifecycle, permission handling and recovery are coupled.

### Stage 2 — make it the user's app
- Apply the user's app display name, launcher artwork and package/application ID only after checking all hard-coded authorities, providers, deep links, update channels and signing assumptions.
- Retain a visible upstream acknowledgements/license screen and full source notices.
- Keep the call-capture engine and its permissions intact; avoid adding unrelated permissions/features.
- Keep the app local-first and make any export/backup opt-in.

### Stage 3 — build and verify
- Reproduce the upstream debug build first, unchanged.
- Add CI with pinned JDK, Android SDK, NDK and Gradle versions matching the upstream project.
- Verify debug APK installation before attempting a branded build.
- Test incoming and outgoing calls, both audio directions, Bluetooth/wired routing, screen-off/background behaviour, reboot recovery, missed/declined calls and file integrity.
- Test on the exact OnePlus 11R CPH2487 / OxygenOS 16.0.5. Do not infer success from OnePlus 12 results.

## Device-specific risks

- The upstream README reports OnePlus 12 testing, not OnePlus 11R testing.
- OxygenOS may block shell permission grants until the documented system-optimization setting is changed. That setting has security trade-offs and must never be silently toggled.
- Wireless debugging access may need re-pairing/re-arming after reboot; background restrictions can stop capture.
- Carrier VoLTE/VoWiFi behaviour may differ and must be reported as unsupported if the device test fails.
- Google Phone is the target dialer; the project must not rely on replacing it as the default dialer unless a later, tested requirement proves that necessary.

## Acceptance criteria

A build is not called a call recorder until all of these pass on the target phone:
1. An incoming call automatically starts a recording after connection when the selected rule allows it.
2. An outgoing call automatically starts a recording after connection when the selected rule allows it.
3. The saved file contains intelligible audio from both participants, verified independently.
4. The file is finalized when the call ends and appears in the app library.
5. A failed capture is surfaced clearly; the app never creates a misleading empty/silent file and labels it successful.
6. Recording status is visible to the user; no attempt is made to suppress announcements or bypass consent.

## Current branch status

This branch completes Stage 0 only. It does not yet contain the CallVault source or a working call-recording APK. A separate upstream-source import is required before a functional app can be built.
