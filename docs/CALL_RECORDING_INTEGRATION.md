# Call recording integration and device validation

## Engine

The repository pins [CallVault](https://github.com/madkongo/CallVault), derived from
[ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder), as the complete
`vendor/CallVault` Git submodule at commit
`00a77594085d61aee2c8513c649911704fd970c6`.

CallVault provides the coupled capture engine, privileged recorder service, Shizuku integration,
permissions, lifecycle handling, recovery, local recording library and playback. Do not replace this
with a microphone-only `MediaRecorder` implementation: ordinary apps cannot reliably capture both
cellular call directions through that API on modern Android.

## Android permissions and dialer compatibility

The manifest requests the ordinary app permissions used for call-state detection and metadata:
`READ_PHONE_STATE`, `READ_CALL_LOG`, and `READ_CONTACTS`; notifications are requested
through `POST_NOTIFICATIONS`. The setup flow requests these through Android's runtime permission
UI and must respect denial or revocation. These permissions allow CallVault to observe supported
call-state/call-log information; they do **not** grant access to Google Phone's private audio
pipeline or the protected `CAPTURE_AUDIO_OUTPUT` permission.

There is no public Google Phone permission that a third-party app can request to obtain its
internal uplink/downlink audio stream. Likewise, OnePlus/Oplus dialer permissions protected by
the platform signature or phone UID cannot be granted to `com.baba.callvault` by adding a
manifest declaration, Shizuku app-op, or role change. The supported CallVault capture route
remains the shell-UID `VOICE_CALL` backend, subject to the device actually authorizing it.

## OEM dialer / InCallUI audit

The supplied OnePlus/Oplus package audit exposed call-recording helpers inside the privileged
Oplus in-call application. Those helpers run in the phone/OEM security domain and rely on
platform-protected permissions and UID/signature privileges. Copying the wrapper classes into
CallVault, declaring the same permission in a normal app manifest, or requesting Shizuku access
does not transfer those privileges. The Oplus implementation is therefore not a usable drop-in
third-party backend for this package.

CallVault continues to use its existing shell-UID `VOICE_CALL` capture path through Shizuku.
The in-app **Capture capability diagnostics** report checks protected permission availability for
the recorder host; it is diagnostic only and cannot prove that OxygenOS will deliver both sides of
a live call. The final proof remains a real incoming and outgoing call test on the target handset.

The audit also found no supported third-party API for suppressing Google Phone's mandatory
recording disclosure. The app must not patch the dialer, mute announcements, spoof the phone UID,
or silently force speakerphone. CallVault does not need to change the user's audio route to start
its recorder.

## Product profile applied by this repository

`scripts/apply_shizuku_profile.py` applies a revision-checked set of build-time defaults to the
pinned submodule. It intentionally does not rewrite upstream history or remove upstream notices.

- Shizuku is the default privileged backend.
- Automatic incoming and outgoing cellular recording are enabled by default after the app's
  disclaimer/onboarding; users can turn either off.
- Call recording, local storage, call history and playback are intended to work offline while
  Shizuku is already running and permission has been granted.
- CallVault's own embedded ADB/Wireless-debugging pairing is not the selected backend.
- The app does not suppress recording announcements or hide recording status.

See [SHIZUKU_FIRST_PROFILE.md](SHIZUKU_FIRST_PROFILE.md) for the reboot limitation, offline test
matrix, size policy and battery policy.

## Offline operation and fallback boundary

Offline operation is distinct from an audio-capture backend. The app can record locally with an
already-running, authorized Shizuku server; its embedded ADB mode has its own local debugging
requirements. Internet access is not required for the core local recording path after setup. Optional
features such as release updates, cloud backup, remote model downloads and cloud transcription need
network access and should remain disabled or unavailable in a strict offline session. Local playback
and already-installed on-device models remain local.

There is currently **no Accessibility-based cellular-call audio recorder** in the pinned engine.
Accessibility may observe permitted UI events and, on some Android versions, may be allowed to use
the microphone input during a call. It does not grant the protected call downlink or the
`CAPTURE_AUDIO_OUTPUT` permission. Therefore a microphone-only fallback, if added in a future
version, must be an explicit opt-in, visibly disclosed, foreground-notified, and labelled
"Microphone-only — remote caller may be missing". It must never be represented as two-sided capture,
must not silently switch audio routes or enable speakerphone, and must not save a failed/near-silent
capture as a successful recording.

A normal third-party app cannot become a system/privileged process by enabling Accessibility or
using Shizuku to impersonate Android's system identity. Do not add process-identity spoofing,
signature-permission workarounds, hidden capture APIs, or announcement suppression. Reliable
two-sided capture remains dependent on a supported privileged/OEM capture route and successful
device-level tests. On this OnePlus 11R target, that remains unverified.

## Build pipeline

The CI workflow:
1. Checks out the exact CallVault submodule and its nested native submodules.
2. Applies the revision-checked Shizuku-first defaults twice (idempotence check).
3. Verifies that the master carrier switch, incoming/outgoing automation defaults, call receivers,
   foreground service and Shizuku binder path remain connected.
4. Runs the release unit-test suite and blocking Android lint.
5. Builds the optimized release variant.
6. Aligns and signs the test artifact with a disposable CI debug key.
7. Uploads the APK and reports its size and SHA-256.

The CI signing key is not a production signing key. The artifact is for installation/testing only;
a permanent signing key and update strategy must be selected before public distribution.

## OnePlus 11R target and known constraints

- Device: OnePlus 11R (CPH2487)
- OS: Android 16 / OxygenOS 16.0.5
- Dialer: Google Phone
- Root: none

CallVault upstream documentation reports tests on OnePlus 12, not this OnePlus 11R. Compatibility
must be established on the exact target hardware and build.

In Shizuku mode, the recorder borrows an already-running Shizuku server. The app itself does not need
Wireless debugging pairing. However, on a non-rooted device Shizuku still needs a startup path. It
may need to be restarted after reboot; trusted-Wi-Fi auto-start or USB ADB from a computer are
possible approaches. The project must not claim it can restart Shizuku after reboot with all
debugging options disabled.

OxygenOS may require a separate system-optimization setting for shell permission grants. Never
change it automatically; explain the security trade-off and let the user decide.

## Acceptance tests

Do not call the APK compatible until these pass on the OnePlus 11R:

1. Grant this app Shizuku permission while Shizuku is running.
2. Turn Wireless debugging off and disable internet access. Keep cellular service available.
3. Place an incoming call and confirm automatic recording starts after connection.
4. Place an outgoing call and confirm automatic recording starts after connection.
5. Independently verify that both participants are intelligible in both recordings.
6. Verify files finalize, play, appear in the library and retain correct call metadata.
7. Repeat with Wireless debugging on and compare.
8. Reboot and verify Shizuku's actual restart requirements; do not infer this from a warm session.
9. Test screen-off, Bluetooth/wired audio, battery saver, idle standby and app process death.
10. Verify failed capture is clearly reported and never represented by an empty/silent successful file.

## Privacy and license

Recording laws vary by jurisdiction. Obtain consent required by applicable law and respect the other
participant's privacy. Keep recording status visible. Do not bypass or suppress announcements.

Preserve the upstream GPL-3.0-or-later license, Section 7 additional terms, copyright and
modification notices, and third-party licenses. See [UPSTREAM_NOTICES.md](../UPSTREAM_NOTICES.md).
