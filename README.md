# Call Recorder for Android

**Product direction:** a local-first automatic cellular call recorder for incoming and outgoing calls,
with a simple call list, visible recording status and playback.

## Target device

- OnePlus 11R (CPH2487)
- Android 16 / OxygenOS 16.0.5
- Google Phone dialer
- Non-rooted

Exact device compatibility is not yet verified. A successful Android build does not prove that
OxygenOS exposes both call-audio directions on this model.

## Recording engine

The product build uses [CallVault](https://github.com/madkongo/CallVault), derived from
[ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder), pinned as the
`vendor/CallVault` submodule at commit
`00a77594085d61aee2c8513c649911704fd970c6`.

**Single product engine:** the shipped/test APK is built from the pinned `vendor/CallVault`
submodule. The obsolete top-level microphone-only recorder and its competing Gradle project have
been removed so there is no longer a second, misleading APK target. Open/build the CallVault
submodule as documented below.

This product profile selects **Shizuku mode by default**. With Shizuku already running and permission
granted, the recorder does not need its own Wireless debugging pairing. Wireless debugging can be
turned off, and core recording/storage/playback work offline. Internet is only needed for update checks or cloud backup.

**Important reboot limitation:** on a non-rooted device, Shizuku itself must be started by a supported
method. Depending on the Shizuku setup, it may need to be restarted after reboot. This app cannot
promise to re-start Shizuku with every debugging option disabled.

## Shizuku manager

Use the official Shizuku manager unless you have a specific reason to test a compatible fork.
The recorder uses Shizuku's public API and does not need a bundled or modified manager.

On a non-rooted phone, Shizuku still needs a supported startup method. Wireless debugging or
starting it over USB from a computer are established options. Compatible forks may offer additional
startup or recovery experiments, but none has been verified on the OnePlus 11R / OxygenOS 16.0.5.
Do not uninstall or replace an existing Shizuku manager merely to try an experimental startup method;
manager forks can use different signing keys and require a separate migration.

## Product defaults

The build-time profile sets Shizuku as the default backend and enables automatic incoming/outgoing
carrier-call recording after the app's disclaimer/onboarding. Either direction can be disabled by
the user. Recording activity must remain visible and applicable consent requirements must be respected.
No announcement suppression or stealth recording is implemented.

## AI project graph

For AI-assisted review and handoff, see the [shareable architecture and knowledge graph](docs/AI_PROJECT_GRAPH.md) and its [machine-readable JSON graph](docs/AI_PROJECT_GRAPH.json). These describe the repository structure, pinned engine, privilege backends, recording/data flow, CI pipeline and target-device validation boundaries.

## Build

Clone with submodules:

```bash
git clone --recurse-submodules https://github.com/Wasifazhar00/Call-recorder.git
cd Call-recorder
python3 scripts/apply_shizuku_profile.py
cd vendor/CallVault
./gradlew :app:assembleRelease
```

The GitHub Actions workflow applies the revision-checked profile twice, verifies the incoming/outgoing
call wiring, runs unit tests and blocking Android lint, builds the arm64 release APK, verifies its
package/ABI/signature, and records its exact byte size and SHA-256. The CI APK is signed with a
temporary test key, not a permanent production key.

## Battery and size

The pinned engine already restricts native packaging to `arm64-v8a`, appropriate for this target.
Core capture is intended to run only around calls. AI transcription, summaries and model downloads are excluded from the product build. Backup remains optional. A smaller APK does not by itself prove better battery life.

Do not remove native or service code unless every dependent UI/JNI path is disabled and the call
recording acceptance tests still pass.

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
