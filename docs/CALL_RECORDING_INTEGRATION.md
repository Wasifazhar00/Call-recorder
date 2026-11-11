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
