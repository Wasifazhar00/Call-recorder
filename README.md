# Call Recorder for Android

**Project direction:** automatic cellular call recording for incoming and outgoing calls — not a microphone/voice-memo recorder.

This repository previously contained a microphone recorder. That implementation cannot reliably capture the remote side of a cellular call on modern Android, so it is not presented as a call recorder.

## Target device

- OnePlus 11R (CPH2487)
- Android 16 / OxygenOS 16.0.5
- Google Phone dialer
- Non-rooted device

Device-specific compatibility is **not yet verified**. An Android version being supported by an upstream project does not prove that this exact OnePlus model/build will work.

## Intended features

- Detect incoming and outgoing cellular calls.
- Start recording automatically after a call connects, according to user-configured rules.
- Capture both caller and recipient audio when the device audio stack permits it.
- Save recordings locally with call date/time and available contact metadata.
- Browse, search, play, export and delete recordings.
- Show clear recording status and provide user-controlled recording rules.
- No stealth mode, no attempt to suppress or bypass recording announcements.

## Recording engine research

The selected integration candidate is [CallVault](https://github.com/madkongo/CallVault), a GPL-3.0 project derived from [ShizuCallRecorder](https://github.com/kitsumed/ShizuCallRecorder).

CallVault's documented architecture uses Android's wireless debugging / embedded ADB shell privileges (or Shizuku) to access call audio. This is fundamentally different from the ordinary `RECORD_AUDIO` microphone API used by the previous app.

Upstream source reviewed for this integration:
- CallVault release commit: https://github.com/madkongo/CallVault/commit/00a77594085d61aee2c8513c649911704fd970c6
- Upstream project documentation: https://github.com/madkongo/CallVault
- Upstream notices: https://github.com/madkongo/CallVault/blob/main/NOTICE.md

## Important status

The current integration branch pins the complete CallVault source as the `vendor/CallVault` Git submodule at the reviewed commit. A GitHub Actions workflow now attempts to build the upstream debug APK from that pinned source. This is a reproducible upstream build step, not yet a branded or merged application: the existing root Android app is not the call-recording engine, and no successful OnePlus 11R call-capture test has been performed.

Before an APK is described as working on this phone, the upstream debug build must succeed, its setup flow must be reviewed, any branding/package changes must preserve upstream attribution, and real incoming/outgoing calls must be tested on the OnePlus 11R.

## OnePlus setup caveats

CallVault documents that some OPPO/OnePlus/Realme builds may require an additional Developer options setting, **Disable system optimization**, before the ADB permission grants work. This reduces certain platform protections. Do not enable it unless you understand the trade-off and the setup flow specifically confirms it is required on this device.

Wireless debugging access may need to be re-established after reboot. OxygenOS background execution also needs to be configured so the recorder is not killed.

## Privacy, consent and safety

Call recording laws vary by location. Obtain any consent required by applicable law and respect the other participant's privacy. The app must visibly indicate its recording state and must not claim to bypass system announcements or consent requirements.

Recordings should remain on-device unless the user explicitly exports or backs them up.

## License

Any code derived from CallVault/ShizuCallRecorder must retain the applicable GPL-3.0 licensing, copyright notices, modification notices and required source-code availability. Review all bundled assets and third-party licenses separately. See [CallVault LICENSE](https://github.com/madkongo/CallVault/blob/main/LICENSE) and [NOTICE](https://github.com/madkongo/CallVault/blob/main/NOTICE.md).

## Build the pinned upstream debug APK

1. Open the repository's **Actions** tab.
2. Select **CallVault upstream debug APK** and open the run for the integration branch or pull request.
3. When the workflow succeeds, download the `callvault-upstream-debug-apk` artifact.
4. This artifact is the upstream CallVault debug build, not yet a customized build of this repository. Review upstream setup instructions and consent requirements before use.

To clone locally with the engine source included, use `git clone --recurse-submodules https://github.com/Wasifazhar00/Call-recorder.git`.

A successful CI build proves only that the upstream source compiles. It does not prove that both sides of calls are captured on the OnePlus 11R. Do not install an APK from the old microphone-recorder workflow expecting cellular call recording.
