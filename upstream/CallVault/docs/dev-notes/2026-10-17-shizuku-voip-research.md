# VoIP recording in Shizuku mode — research (2026-10-17)

**Status: cause MEASURED on the OP9 2026-10-19; fix proven by a call-free self-test; 🧪 no real call yet.**

## 2026-10-19 measurement (OP9 Pro, Android 14, ColorOS, Shizuku mode, branch `spike/shizuku-voip`)

A capture self-test (`CaptureSelfTest`, triggered by a DUMP-guarded receiver) arms the VoIP policy, opens
the far-side sink + a MIC record, and plays a USAGE_VOICE_COMMUNICATION tone — a VoIP downlink without a
call. Same test, same process type, before/after:

| | before | after `ShellAttribution.detachFromApp()` |
|---|---|---|
| logcat | `does not correspond to the uid 2000`; AppOps `Specified package "com.baba.callvault" under uid 2000`; `startInput permission denied` | none |
| policy arm | true | true |
| far-side sink | null | RECORDING, −16.3 dB (the tone) |
| MIC | init, start refused | RECORDING, −20.5 dB |

- **Attribution was the cause** — exactly as read from AOSP. Clearing `mInitialApplication` in the
  UserService constructor fixes it (FMZlinkR's fix). Option A (spawn a daemon) is not needed.
- **Open question 1 answered:** standalone never needed `clearCallingIdentity` because `VoipCaptureSession`
  builds its records on the `recorder-worker` thread, where the calling uid is our own. Only a capture
  built on a binder thread (as the self-test is) sees the app's uid. Production code is already safe.
- Still to do: lift the self-imposed gates (ModeCapability, keep-alive stand-down, VoIP detection host),
  then a real WhatsApp call and a carrier call in Shizuku mode (direct capture now succeeds instead of
  falling back to scrcpy — a behaviour change that needs its own two-sided call).

## 2026-10-19 real call — ✅ VERIFIED (maintainer) WhatsApp in Shizuku mode, OP9 → OP12

The maintainer placed a WhatsApp call from the OP9 (Shizuku mode, build `2137e7d5`) and reports it
"recorded properly". Proof it went through the Shizuku host, from `dumpsys audio`:

- `registerAudioPolicy … u/pid:2000/18762` at 11:20:24; pid 18762 = `com.baba.callvault:recorder` (the
  Shizuku user service). No `RecorderServer` app_process existed.
- During the call (12:12:09–12:12:26): `src:REMOTE_SUBMIX … pack:com.android.shell` (far side) and
  `src:MIC … pack:com.android.shell` (near side), all `not silenced` — the shell identity, not ours.
- File `20261019_121209.682+0300_voip-WhatsApp_פרוזה.ogg`, 16 s, Opus 48 kHz mono (the shipped mono
  encode), levels continuous, no dead stretch.

⚠️ Open: the near MIC record was re-taken 7 times in ~14 s (riid 239→287), i.e. `VoipCaptureSession`'s
zero-chunk re-take fired repeatedly — WhatsApp kept winning the mic on the OP9. Each re-take can cost a
fraction of a second of the near side. Not yet known whether the OP9 does the same in standalone mode
(A/B needed) or whether it is Shizuku-specific.

Below is the original research (📐, from source), kept as written.

## The short answer

VoIP is not blocked by Android in Shizuku mode. It is blocked by **who the process says it is**.

- Shizuku starts its UserService with `ActivityThread.systemMain()`, then loads **our** Application
  into `ActivityThread.mInitialApplication`
  ([UserService.java L60-92](https://github.com/RikkaApps/Shizuku-API/blob/a27f6e4151ba7b39965ca47edb2bf0aeed7102e5/server-shared/src/main/java/rikka/shizuku/server/UserService.java#L60-L92)).
- So every `AudioRecord` there is attributed to **uid 2000 + `com.baba.callvault`** — a pair no
  package owns. (`AttributionSource.myAttributionSource()` reads `mInitialApplication` first.)
- Android 14: creation only runs a *preflight* AppOps check that swallows the mismatch → create OK.
  Start runs `startProxyOp` → `verifyAndGetBypass` throws → `MODE_ERRORED` → hard deny.
  **That is exactly our symptom: "AudioRecord failed to enter RECORDING state".**
  Android 16 (`audioserver_permissions`) rejects at create instead (`createFromTrustedUidNoPackage: invalid attr`).
- Our standalone daemon and scrcpy are fresh `app_process` processes with no Application, so
  attribution falls back to `getPackagesForUid(2000)[0]` = the shell package → valid → works.
- A second, separate problem: AIDL calls arrive carrying the **app's** calling uid. `AudioPolicy`
  with a null Context checks permissions against `Binder.getCallingUid()`, so `createAudioRecordSink()`
  can return null unless the call is wrapped in `Binder.clearCallingIdentity()` (found on-device by
  FMZlinkR PR #3).
- The Looper theory is **ruled out**: Shizuku's `ServiceStarter` already calls `prepareMainLooper()`.
- `registerAudioPolicy` checks only the calling **uid's** permissions, not the package
  (`AudioService.isPolicyRegisterAllowed`) — so it works from uid 2000 once identity is cleared.
  The policy dies with the process that registered it (`linkToDeath`).

Our own gate is self-imposed: `ModeCapability.kt:37-41` disables `VOIP_RECORDING` when
`mode.needsShizuku`, and `DaemonKeepAliveService` (which hosts `VoipCallDetector`) stops itself in
Shizuku mode (`:351`, `:678-680`). The recorder AIDL (`RecorderServiceImpl.armVoipCapture`,
`startVoipRecording`) is already exposed in both hosts. **"VoIP can't work under Shizuku" was never
measured** — it was extrapolated from the carrier `AudioRecord` failure.

## Who else has solved it

| Project | Fix inside the Shizuku UserService | Result |
|---|---|---|
| [FMZlinkR](https://github.com/fumizo07/FMZlinkR) (GPLv3, "Based on CallVault VoIP capture work") | `clearCallingIdentity` around arm/sink/mic ([PR #3](https://github.com/fumizo07/FMZlinkR/pull/3)) + set `mInitialApplication = null` ([PR #5](https://github.com/fumizo07/FMZlinkR/pull/5); `ShellAudioAttribution.kt`) | Author reports two-sided Rakuten Link calls, one device |
| [ShizuCallRecorder v2](https://github.com/kitsumed/ShizuCallRecorder/tree/the-big-big-audio-rework) (upstream, unreleased) | `clearCallingIdentity` + `clearCallingWorkSource`; scrcpy-style `FakeContext` (`com.android.shell`) Application swapped into `mInitialApplication`, `mBoundApplication.appInfo` patched (`ShellWorkarounds.kt`) | Maintainer reports A11/13/14/16 (16 on emulator); VoIP A14+ only; "never worked on Samsung" |
| [cally](https://github.com/LyoSU/cally) | `WrappedShellContext` + ActivityThread patches | Carrier audio works; maintainer says VoIP impossible (#27) |
| [AudioBridge](https://github.com/mu23XR/AudioBridge/issues/36) | Same failure; PR #40 clears identity + `Builder.setContext(shell ctx)` | Open |
| ShizuCallRecorder v1 / our Shizuku path | Child `app_process` (scrcpy) spawned from the UserService | Works — proves a fresh child process can record |

Demand: ~10 duplicate WhatsApp/Telegram requests on ShizuCallRecorder (#1, #44, #47, #67, #79, #108,
#129…), our #9. Watch-out from SCR #1: an A16 Samsung user reports the WhatsApp caller couldn't hear
them while recording (mic held exclusively) — the near-side MIC record must not starve the VoIP app.

## Two ways to do it

**A. Spawn our standalone daemon from the Shizuku UserService (recommended first).**
`ProcessBuilder("app_process", "/", "com.baba.callvault.server.RecorderServer", apk)` with
`CLASSPATH=<apk>` — the same pattern `RecorderSession.kt:106-115` already uses for scrcpy. The child
has no Application, so it is the exact process our VoIP recipe already runs in. Shizuku users get the
whole standalone stack: VoIP, direct capture, resilient recording, speaker turns. No ADB involved
after Shizuku is up. Costs:
- `killStaleRecorders` (`RecorderServiceImpl.kt:392-419`) matches both `RecorderServer` and
  `:recorder` — parent and child would kill each other. Must be taught about the pair.
- Both binders land in one `RecorderConnection`; decide that the child is the recorder and the
  UserService is only its launcher. Respect [[only-one-recorder-host]]'s five rules.
- Child lifetime: spawn without `setsid` so it dies with the UserService (no orphan after a Shizuku
  stop or an app update); re-spawn on `MY_PACKAGE_REPLACED` like the service itself.
- `ModeCapability`, `HandoffPolicy`, keep-alive stand-down and `PostUpdateRecovery` assume
  "Shizuku = scrcpy only" and need revisiting.

**B. Fix attribution inside the UserService (what FMZlinkR and SCR v2 do).**
Clear calling identity around every audio call, and null or replace `mInitialApplication` with a
`com.android.shell` context before any audio object is built. Smaller change, but patches hidden
`ActivityThread` fields, and OnePlus/OPlus also hooks `AudioRecord.startRecording` via
`ActivityThread.currentApplication()` (scrcpy #4153; unverified on ColorOS 14). Keeps scrcpy for
carrier calls unless we also move those over.

## Open questions to settle in the spike

1. Our standalone daemon calls `VoipAudioPolicy.arm()` on a binder thread from the app — yet it
   works without `clearCallingIdentity`. Why does the null-Context permission check pass there but
   (per FMZlinkR) not in a UserService? Measure; don't assume.
2. Confirm the cause on the OP9 in Shizuku mode: logcat should show
   `Specified package "com.baba.callvault" under uid 2000` (AppOps) and
   `startInput permission denied: recording not allowed` (APS).
3. Android 13 and below stay out either way (shell lacks `CAPTURE_VOICE_COMMUNICATION_OUTPUT`).

## Spike plan (needs the OP9 + Shizuku running)

1. Logcat baseline: one carrier call in Shizuku mode, capture the AppOps/APS lines above.
2. Option A behind a debug flag: UserService spawns `RecorderServer`; confirm the daemon binder
   arrives; `armVoipCapture` → true.
3. Real WhatsApp call; listen for **both** voices; check the far channel is digital silence
   (≈ −80 dB) while only the near side speaks.
4. Regression: a carrier call in Shizuku mode and one in standalone mode, both two-sided.
5. Only if A is blocked (e.g. SELinux `avc:` denials on the exec), try B.
