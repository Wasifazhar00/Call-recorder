# Call Recorder Compatibility Prototype

A minimal Android/Kotlin project for testing whether the device exposes cellular call audio capture to an ordinary app.

## Important limitations

This is a **compatibility test**, not a guaranteed call recorder. Android and device vendors restrict access to cellular call audio. On many Android 10+ devices, an ordinary third-party app cannot capture both sides of a phone call using `MediaRecorder.AudioSource.VOICE_CALL`. A successful build or installation does not mean call audio will be available.

This project does not root the phone, bypass Android security, hide recording, or suppress legally required recording announcements. Use it only where recording is lawful and with the other participant's consent.

## Build

Open this repository in Android Studio with Android SDK 35 installed, then build the `app` configuration. The included GitHub Actions workflow also attempts a debug APK build.

## Test

1. Install the debug APK on the target device.
2. Grant microphone permission when prompted.
3. Start a test call only after obtaining consent.
4. Start and stop the capture test from the app.
5. Check the app's status and the saved test file. Silence or a failed start indicates the OS/vendor does not expose this audio source to the app.

## Device target

The initial test target is OnePlus 11R / Android 16. Compatibility is not guaranteed. The app deliberately reports platform restrictions rather than claiming that call recording works.
