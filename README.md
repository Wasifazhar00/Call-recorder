# Voice Recorder for Android

A privacy-first Kotlin Android voice recorder for Android 8.0+ (tested build target: Android 15/API 35; intended to run on Android 16 where platform policies allow).

## Features in this version (1.1.0)

- One-tap microphone recording to AAC/M4A
- Uncompressed PCM WAV recording with valid WAV headers
- Selectable microphone input where Android exposes a routable device
- Live amplitude waveform while recording
- Playback seeking and playback speed controls
- Trim M4A or WAV into a separate copy while preserving the original
- Bookmarks, folders, tags, transcript and notes metadata stored locally
- On-device WAV transcription when the installed speech service supports PCM file input and an offline model
- One-tap microphone recording to AAC/M4A
- Foreground microphone service with an ongoing notification
- Pause, resume, stop and save
- Configurable 64 / 128 / 192 kbps audio quality
- Optional stereo capture when supported by the selected microphone/device
- Local recording library, newest first (M4A and WAV)
- Search by recording filename, folder and tags
- Playback, rename, favorite, share and delete
- Bookmark, metadata/notes editor and trim-copy actions
- No account, analytics, advertising or network permission
- App-private recording storage; share only when the user chooses

## Build an APK

The GitHub Actions workflow builds debug and unsigned release APKs on every push to `main`, on pull requests, and on manual dispatch.

1. Open the repository's **Actions** tab.
2. Select **Android APK** and choose **Run workflow**, or push a commit to `main`.
3. Open the completed workflow run and download the `voice-recorder-debug-apk` artifact.
4. For a tagged release, push a tag such as `v1.0.0`; Actions creates a GitHub Release with the unsigned release APK attached.

The debug APK is suitable for device testing. The release APK produced by CI is unsigned. To distribute a production APK, configure a private signing key in GitHub Actions secrets and add a signing step; never commit a keystore or passwords to this repository.

## Install on OnePlus 11R

Download the debug APK from the workflow artifact, open it on the phone, and allow installation from that source if Android asks. Launch the app and grant microphone permission. Start recording while the app is visible; the ongoing notification provides recording status and controls.

## Feature notes and compatibility

WAV is PCM 16-bit at 44.1 kHz. M4A uses AAC. Input routing is a preference request and Android/OxygenOS may ignore unavailable routes. WAV transcription depends on the device's on-device speech recognizer supporting external PCM audio and having an offline language model installed; language and device support vary. Transcription is offered for WAV files only. Compressed M4A trimming may be approximate around codec frame boundaries. Tags, folders, bookmarks and notes are stored in app preferences keyed to the recording filename.

## Storage and privacy

Files are stored in the app-specific external files directory under `Recordings`. Android removes app-specific files when the app is uninstalled unless they are backed up separately. Use Share to export a recording to another app or storage location.

## Important call-recording limitation

This app records sound from the selected microphone. It does **not** record cellular calls internally, capture both telephony uplink/downlink channels, bypass Android/OxygenOS restrictions, or suppress call recording announcements. The Huawei `HwCallRecorder 11.0.0.109` APK depended on Huawei system UID, signature permissions and Huawei telephony services that are not available to a normal OnePlus app.

For call recording, use only a supported device/OS feature or a separately reviewed solution that explicitly meets the device's requirements and local consent laws. Do not assume a microphone recording contains the remote caller.

## Open-source inspiration

The feature design was informed by public projects and app descriptions including:

- [Audio Recorder (Dimowner)](https://github.com/Dimowner/AudioRecorder) — M4A/WAV, playback, waveform, settings and bookmarks (Apache-2.0 project; verify upstream license before reusing code).
- [Flambo](https://github.com/MoHamed-B-M/flambo) — offline-first recorder, background controls and transcription concepts.
- [VoiceRecorder](https://github.com/inhahe/VoiceRecorder) — foreground service, pause/resume and audio input configuration.
- [Android foreground service documentation](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone).

This repository is an independent implementation. No upstream source code is copied into this project.

## Version

- Application ID: `com.wasifazhar.callrecorder`
- Version: `1.1.0`
- Minimum Android: 8.0 (API 26)
- Compile/target SDK: 35
