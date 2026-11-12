# Cellular uplink/downlink capture research

Date: 2026-11-12

## Finding

CallVault already contains the correct non-root capture architecture for cellular calls: it runs the recorder in the ADB/Shizuku shell-UID process and selects Android's privileged `MediaRecorder.AudioSource.VOICE_CALL` source. That source is the combined call uplink + downlink path. The direct recorder attempts stereo capture first and mono as a fallback; the scrcpy server path also supports `voice-call`.

This is not a normal-app microphone workaround. The Android API explicitly requires `CAPTURE_AUDIO_OUTPUT` for `VOICE_CALL` and `VOICE_DOWNLINK`, and reserves that permission for system components. On an unrooted phone, the project therefore depends on the shell-UID transport (Shizuku or ADB) actually being available. A permission declaration in the APK cannot grant this protected permission.

## Repositories reviewed

- [ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder) — GPL-3.0; non-root Shizuku/ADB shell recorder. Its `AudioRecordCapture` opens `AudioRecord` in the shell process, uses `VOICE_CALL`, and includes an OEM constructor fallback. CallVault is already derived from this project, so adding a second copy of the same capture engine would duplicate code and risk divergent fixes.
- [Basic Call Recorder (BCR)](https://github.com/chenxiaolong/BCR) — GPL-3.0; mature rooted/custom-firmware implementation with separate uplink/downlink stereo support. It is a useful reference for root/system deployments, but its privileged system integration is not a no-root solution.
- [Android call-recorder prototype](https://github.com/DanielAlongE/android-call-recorder) — documents direct `VOICE_UPLINK` and `VOICE_DOWNLINK` capture and the protected permission requirement. It is a useful API reference, not proof that an ordinary app can use those sources.
- [Android AudioSource API](https://developer.android.com/reference/android/media/MediaRecorder.AudioSource) — confirms that `VOICE_CALL` combines uplink and downlink and requires `CAPTURE_AUDIO_OUTPUT`.

## Integration decision

1. Keep the existing single `VOICE_CALL` stereo capture as the primary cellular path. It is the least risky way to obtain both directions when the device's audio HAL exposes them.
2. Do **not** blindly add simultaneous independent `VOICE_UPLINK` + `VOICE_DOWNLINK` AudioRecord instances as an automatic fallback. CallVault's upstream fork review records device testing where a second concurrent capture silenced the local side without an error. A pair of successfully-created recorders is not proof that both tracks contain audio.
3. Preserve the existing microphone fallback only as an explicitly labelled degraded mode; it must never be presented as two-sided capture.
4. Keep Shizuku as the preferred unrooted backend. Embedded ADB/loopback remains an alternative with its existing one-time setup and reboot/re-arm constraints. Fully offline recording after the required transport is armed is distinct from zero-debug first-time setup.
5. Do not add protected permission declarations as if they grant access. Diagnostics must report the actual process UID and actual capture result.

## CI guard added

The root profile verifier now asserts that the pinned engine maps the `voice-call` source to `MediaRecorder.AudioSource.VOICE_CALL` and that the mapping test remains present. This prevents a future profile change from silently replacing the cellular capture source.

## Device acceptance test still required

No CI build can prove the OnePlus 11R (CPH2487), Android 16 / OxygenOS 16.0.5 audio HAL exposes both sides. On that phone, test incoming and outgoing SIM calls separately, with earpiece, Bluetooth and wired headset, and inspect both channels for actual near/far speech. Also test screen-off, reboot recovery, and offline operation after transport setup. Reject any recording that has a valid container but silent/missing remote audio.

## Licensing

No source files were copied from the repositories reviewed in this note. If source code is later ported from ShizuCallRecorder or BCR, preserve the applicable GPL-3.0-or-later license, copyright and modification notices, and all upstream Section 7 additional terms.
