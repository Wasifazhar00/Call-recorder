# CallVault

CallVault is an Android call recorder. It records supported phone and VoIP calls, keeps recordings in a searchable local library, and provides playback and file-management tools.

## Features

- Automatic incoming and outgoing call recording, with separate controls where supported by the device.
- Recording backends for supported Android/OEM configurations, including Shizuku-assisted capture.
- VoIP capture options for supported calling apps.
- Recording library with search, playback, notes, tags, favourites, flags and labels.
- Import audio files, share recordings or selected clips, trim and merge recordings.
- Optional local storage management and Google Drive folder sync through Android's document picker.
- App lock, notifications, appearance settings, and setup/permission diagnostics.

Recording availability and whether both sides are audible depend on the Android version, device firmware, dialer, call type and selected capture backend. CallVault cannot override platform or OEM restrictions.

## Build

The Android project is in `upstream/CallVault`.

- JDK 17
- Android SDK
- Android NDK `27.2.12479018`
- CMake

Open `upstream/CallVault` in Android Studio or build with Gradle from that directory. A release build without the configured signing keystore is unsigned and must be signed before installation.

## Privacy

Recordings and user-authored recording metadata are stored locally unless the user configures a cloud-copy destination. Drive access is mediated by Android's document picker; CallVault does not collect or upload call audio to an AI transcription or summarisation service.

## License

See [LICENSE](LICENSE) for the project license and additional terms. Third-party notices are in [NOTICE.md](upstream/CallVault/NOTICE.md).
