# CallVault product requirements

This document consolidates the requested product scope for the CallVault Android application.

## Primary purpose

A reliable, local-first Android cellular call recorder focused on automatic incoming and outgoing call recording, a straightforward setup, and dependable playback and recording management.

## Target device

- OnePlus 11R (CPH2487)
- Android 16 / OxygenOS 16.0.5
- Google Phone dialer
- Non-rooted configuration as the primary target, with separate supported configurations for rooted devices

## Automatic call recording

- Automatically detect incoming and outgoing cellular calls.
- Start recording when a call is answered/connected and stop when it ends.
- Capture both the device owner's voice and the remote participant's voice when the device and selected recording backend permit it.
- Record without forcing loudspeaker mode.
- Save finalized recordings automatically and recover safely from interruptions.
- Prevent duplicate recordings and handle call-state transitions, process recreation, reboot, and long calls.
- Keep recording status visible and provide useful failure diagnostics.

## Recording backends and profiles

Provide capability-detected configurations for:
- Standard Android microphone recording.
- Device/OEM-supported call recording.
- Shizuku-backed recording.
- ADB/shell-backed recording.
- Root-backed recording.
- OEM/system-integrated recording where the device firmware supports it.

Include a simple first-run selector, rooted and non-rooted setup paths, backend status, permission diagnostics, and a clear explanation of the capabilities actually available on the current device. Support a low-setup path whenever the selected backend allows it.

## Recording library

- Search and filter by caller, phone number, date, direction, and duration.
- Show incoming/outgoing direction, caller details, date, time, and duration.
- Play, pause, seek, rename, delete, share, and export recordings.
- Support configurable quality and available audio formats.
- Include storage usage, retention, backup, and restore controls.
- Preserve recording history and files across application updates.
- Retain useful call-history, notes, tags, favourites, and merge functionality where already supported.

## Audio engine and reliability

- Use the most capable supported audio source and route for each device/backend.
- Include source and route diagnostics, recording health checks, and recoverable error handling.
- Handle audio focus, Bluetooth and wired routing, screen-off operation, background execution, and interruptions.
- Optimize CPU, memory, storage, battery consumption, and native-library packaging.
- Keep recording active only as required by a call and finalize playable files reliably.

## User interface

Provide a polished, simple interface containing:
- Dashboard and current recording/backend status.
- Automatic incoming and outgoing recording controls.
- Recording library and playback.
- Recording mode/backend selection.
- Setup, permission, and compatibility diagnostics.
- Storage, retention, backup, and restore settings.
- General settings and theme controls.

## Remove AI/transcription/summarization

Remove the transcription, language-selection, translation, AI-summary, and model-management feature set from the shipped project, including its UI, settings, scheduling, workers, model downloads/assets, Whisper/Llama inference integrations, JNI/native targets, dependencies, and feature-specific tests and documentation. Preserve the core call-recording, call-history, playback, and recording-management features. Review shared transcript-related database or metadata code before removal so unrelated recording-library features and existing user data are not broken.

## Build and delivery

- Keep the existing GitHub repository and pinned CallVault engine as the project foundation.
- Review source modules, manifests, Gradle configuration, native build configuration, resources, tests, and CI.
- Keep profile patches deterministic and idempotent, with revision checks and verification.
- Run unit tests, Android lint, and release APK builds after changes.
- Fix regressions across the full build rather than stopping at the first reported error.
- Verify package name, target ABI, APK signature, size, and checksum.
- Deliver an installable APK and report the exact CI result and artifact.

## Acceptance criteria

1. Incoming and outgoing calls are detected and recording starts/stops automatically.
2. Both audio directions are verified on the target phone using the selected supported backend.
3. Recording works without forcing loudspeaker mode.
4. Setup and backend status are understandable without manual source-code changes.
5. Recording library and playback remain functional.
6. AI transcription, language selection, summaries, and model-download features are absent from the shipped app.
7. Unit tests, lint, and release build pass.
8. Device-specific claims are marked verified only after testing on the OnePlus 11R / OxygenOS 16.0.5.
