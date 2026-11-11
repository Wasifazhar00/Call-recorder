# Shizuku-first product profile

This repository builds a locally customized profile of the pinned CallVault engine. The upstream
submodule is not edited or rewritten in Git history. Instead, `scripts/apply_shizuku_profile.py`
applies a small, revision-checked build-time patch.

## Default behaviour

- Shizuku is the default privilege backend. Install and start Shizuku, then grant this app Shizuku
  permission. The recorder borrows Shizuku's already-running shell-privileged service.
- Automatic incoming and outgoing carrier-call recording are enabled by default after the app's
  legal/disclaimer onboarding. The user can turn either direction off in settings.
- Recording files, call metadata, playback and the library are local-first. Internet is not required
  for call capture or playback. Optional model downloads, update checks and cloud backup are not
  needed for the core recorder.
- The app must keep a visible recording state and respect applicable consent laws. This profile does
  not suppress announcements or hide recording activity.

## Debugging and offline behaviour

Shizuku mode does not use CallVault's embedded ADB pairing flow and does not require CallVault to
keep Wireless debugging enabled. Once Shizuku is running and this app has permission, Wireless
debugging can be off and the core recorder can work without internet.

On a non-rooted phone, Shizuku itself still needs a supported startup path. After reboot, Shizuku may
need to be started again. Its trusted-Wi-Fi auto-start is one option; starting it over USB ADB from a
computer is another. Root is not assumed. This project does not claim that a non-rooted phone can
restart Shizuku after every reboot with all debugging switches disabled.

## Battery and APK-size policy

- Do not poll continuously for Shizuku or keep a wake lock when no call is being recorded.
- Keep call capture in the foreground only while required by Android's recording/service rules.
- Keep optional transcription, summarisation, cloud backup and model downloads user-triggered.
- Build a release-optimized arm64 APK for size measurements; the earlier debug artifact was
  129,452,046 bytes and contains debug-build native libraries. A smaller release artifact is not
  evidence of lower runtime battery use.
- Do not remove recorder, lifecycle, permission, recovery or storage code just to reduce APK size.
  Native AI libraries are candidates for a separately tested "core recorder" flavor, not for blind
  deletion: UI and JNI entry points must be disabled together and verified before excluding them.

## OnePlus 11R acceptance tests

Target: CPH2487, Android 16 / OxygenOS 16.0.5, Google Phone, non-rooted.

1. Start Shizuku using a supported method and grant this app access.
2. Turn Wireless debugging off; enable Airplane mode, then re-enable cellular service while keeping
   Wi-Fi and mobile data off. Verify an incoming and outgoing cellular call are captured.
3. Verify both participants are audible in each saved recording.
4. Repeat with Wireless debugging on, then compare behaviour.
5. Reboot and verify the exact Shizuku restart requirement; do not infer auto-start from a warm session.
6. Test screen-off, Bluetooth/wired audio routing, app process death, battery saver and long idle.
7. Confirm there are no empty/silent files reported as successful recordings.

OxygenOS may require a separate system-optimization permission adjustment for shell grants. Never
change that setting automatically; explain its security implications and request the user's choice.

## Preferred manager: Shizuku-Next

For this device trial, prefer [Shizuku-Next](https://github.com/rushiranpise/Shizuku-Next) as the
Shizuku manager/server app rather than bundling its manager APK into the recorder. CallVault's
pinned `ShizukuBackend` uses the standard Shizuku API and explicitly supports Shizuku variants;
it detects the manager by its declared permission rather than assuming only the stock package name.
Shizuku-Next states that it preserves the Shizuku package/API compatibility, so no fork-specific
recorder code or private API dependency should be necessary.

Shizuku-Next adds watchdog/recovery and optional boot-start features, plus experimental startup
methods without Wi-Fi. These are useful to test, not guaranteed capabilities on this OnePlus model.
The no-Wi-Fi startup modes are documented as experimental/device-dependent; the normal wireless
debugging and USB/PC start paths remain available. The recorder must not call Shizuku-Next's private
manager internals or bundle its manager code: it should request permission through the stable Shizuku
API only.

**Install warning:** Shizuku-Next uses a different signing key from official Shizuku and other forks.
Its project says the existing official/fork app must be uninstalled before installing Shizuku-Next;
an old server may remain active until stopped or the phone is rebooted. Back up setup notes first,
install only from its official releases, then grant the recorder's Shizuku permission again.
Do not install or uninstall a manager automatically from the recorder.

Test the normal Shizuku-Next wireless-debugging startup first, then turn wireless debugging off
while the server remains running and verify calls. Separately test its experimental no-Wi-Fi startup
and boot recovery after a full reboot. Do not label that path supported until verified on CPH2487 /
OxygenOS 16.0.5.
