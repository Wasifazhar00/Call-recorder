# Call Recorder for Android

**Product direction:** a local-first automatic cellular call recorder for incoming and outgoing calls,
with a Huawei-like simple call list, visible recording status and playback.

## Target device

- OnePlus 11R (CPH2487)
- Android 16 / OxygenOS 16.0.5
- Google Phone dialer
- Non-rooted

Exact device compatibility is not yet verified. A successful Android build does not prove that
OxygenOS exposes both call-audio directions on this model.

## Recording engine

The project uses [CallVault](https://github.com/madkongo/CallVault), derived from
[ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder), pinned as the
`vendor/CallVault` submodule at commit
`00a77594085d61aee2c8513c649911704fd970c6`.

This product profile selects **Shizuku mode by default**. With Shizuku already running and permission
granted, the recorder does not need its own Wireless debugging pairing. Wireless debugging can be
turned off, and core recording/storage/playback work offline. Internet is only needed for optional
features such as update checks, model downloads or cloud backup.

**Important reboot limitation:** on a non-rooted device, Shizuku itself must be started by a supported
method. Depending on the Shizuku setup, it may need to be restarted after reboot (for example through
trusted Wi-Fi auto-start or USB ADB from a computer). This app cannot promise to re-start Shizuku
with every debugging option disabled.

## Product defaults

The build-time profile sets Shizuku as the default backend and enables automatic incoming/outgoing
carrier-call recording after the app's disclaimer/onboarding. Either direction can be disabled by the
user. Recording activity must remain visible and applicable consent requirements must be respected.
No announcement suppression or stealth recording is implemented.

## Build

Clone with submodules:

```bash
git clone --recurse-submodules https://github.com/Wasifazhar00/Call-recorder.git
cd Call-recorder
python3 scripts/apply_shizuku_profile.py
cd vendor/CallVault
./gradlew :app:assembleRelease
```

The GitHub Actions workflow applies the same revision-checked profile and produces a release-optimized
arm64 APK signed with a temporary CI test key. That artifact is for testing only; it is not signed
with a permanent production key. Its size is measured in CI.

## Battery and size

Core capture is intended to run only around calls. The app should not continuously poll Shizuku or
hold wake locks while idle. Transcription, summaries, backup and model downloads remain optional and
user-triggered. Do not remove native or service code without disabling every dependent UI/JNI path and
running the acceptance tests.

The previous debug APK was 129,452,046 bytes; this is a debug-build measurement, not a release-size
baseline. A smaller APK does not by itself prove better battery life.

## Device test status

No physical OnePlus 11R test has been run from CI. Before describing this as working, verify incoming
and outgoing calls, both audio directions, offline capture with Wireless debugging off, reboot/Shizuku
recovery, screen-off behaviour, Bluetooth/wired routing, and finalized playable files on the exact
target phone.

## Privacy and licensing

Call recording laws vary. Obtain consent required by applicable law and respect all participants.
Keep recordings on-device unless the user explicitly exports or enables backup.

Retain the upstream GPL-3.0-or-later license, Section 7 additional terms, copyright notices,
modification notices and third-party notices. See [UPSTREAM_NOTICES.md](UPSTREAM_NOTICES.md) and the
upstream [LICENSE](https://github.com/madkongo/CallVault/blob/main/LICENSE) and
[NOTICE](https://github.com/madkongo/CallVault/blob/main/NOTICE.md).
