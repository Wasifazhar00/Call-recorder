# Android 17 and call-recording / ADB apps — competitor findings

Researched 2026-10-20. Read-only: GitHub issues, PRs and commits, plus Android developer docs.
Our checkout was at `integration/next` 51ef5413. CallVault targets and compiles against SDK 36.

Markers: **CONFIRMED** = a fix shipped and users said it works. **CLAIMED** = one PR or one reporter says so, not reproduced elsewhere. **SPECULATION** = my own reading.

## Short answer

- I found no report of Android 17 breaking call audio capture: VOICE_CALL, playback capture or scrcpy audio. That holds for BCR, ShizuCallRecorder, scrcpy, JemRec and our own tracker.
- What broke on Android 17 is everything around it: detecting the debugging switches, mDNS and the local-network permission, wireless-debugging pairing, and hidden PackageManager APIs.
- The one recording failure from an Android 17 user (our #53) is a NullPointerException inside Honor's AudioRecord. That is a vendor beta bug, not an AOSP change.

## Ranked by relevance to CallVault

### 1. Settings.Global ADB_ENABLED / DEVELOPMENT_SETTINGS_ENABLED read "0" — CONFIRMED everywhere, we already handle it

- Arrived on Pixels with the September 2026 update, build CP3A.260905.009, which reporters call "Android 17 QPR1". The Android docs now say both settings "will always return 0 for all third-party apps".
- thedjchi/Shizuku #301 (2026-09-16, open): "USB debugging not enabled" on Start. Maintainer fix not merged. Community fixes:
  - RazGame beta, commit 0b3611a.
  - trevornk commit 3c0de08c: on API 37+, trusts a non-zero value and otherwise assumes ADB is on. If ADB really is off, the connect attempt fails.
  - Issue: https://github.com/thedjchi/Shizuku/issues/301
- thedjchi/Shizuku #204: same symptom, piling up comments since 2026-09-15.
- d4rken-org/porter #127 → PR #122, merged 2026-09-26: on SDK_INT < 37 a 0 is trusted; on 37+ the app goes straight to discovery.
  - Note from the PR: the 37.1 emulator image still reports the real value, so the minor SDK version does not identify redacting builds.
- rushiranpise/Shizuku-Next #4 (closed 2026-10-01): same approach, "assume on at API 37+". Users confirmed it on CP3A.260905.009.
- jemcik/JemRec PR #24 (2026-10-20): a new non-root, no-Shizuku call recorder.
  - Reads the settings as ON / OFF / UNKNOWN, and treats a 0 on SDK 37+ as UNKNOWN.
  - Says the redaction is behind a flag: on for Pixels, off on the emulator image and on MagicOS 11.
  - Cites our #40.
- **Us:** fixed in 2.4.0 (#40, both reporters confirmed). The fix uses `UsbDebuggingPolicy` / `DeveloperOptionsPolicy` with `init.svc.adbd` corroboration, not an SDK_INT gate.
  - That is stronger than the forks' "assume on at 37+": it also covers Android 16 ROMs that backport the redaction.
  - Nothing to do.

### 2. Local network protection: mDNS device picker and ACCESS_LOCAL_NETWORK — CONFIRMED for apps targeting 37; we are not exposed yet

- The rule: apps that target 37 must request the `ACCESS_LOCAL_NETWORK` runtime permission (Nearby devices group). Otherwise NsdManager discovery and LAN connects are intercepted by a system "Choose a device to connect" picker.
  - Apps that target 36 or lower and hold INTERNET keep an implicit grant ("split permission model", per the Android local-network-permission docs).
- thedjchi/Shizuku PR #226 (merged 2026-07-02) requests the permission in the pairing screen only.
- thedjchi/Shizuku #302 (2026-09-16, open) shows the gap: the normal already-paired Start path calls `adbMdns.start()` with no permission check.
  - Result: a picker for the phone's own `adb-*` endpoint on every start.
- frdminc/ShizukuTendCF #25 (2026-10-06):
  - Headless and boot starts can block on a picker nobody sees.
  - Fix: request the permission wherever discovery runs, and set `DiscoveryRequest.FLAG_NO_PICKER` on every discovery call on API 37.
  - A missing grant then fails cleanly (`FAILURE_PERMISSION_DENIED`) instead of opening a picker.
- Batestinha/shizuku-thedjchi-daemonstatebroadcast `docs/android17-wireless-discovery.md` is the most complete write-up. It covers both `_adb-tls-pairing` and `_adb-tls-connect`, denial, revoke, worker and boot paths, and recovery notifications.
- **Us:** targetSdk = 36 and no `ACCESS_LOCAL_NETWORK` in the manifest, so the implicit grant applies today.
  - Before we raise targetSdk to 37, every `AdbMdns.discoverPort` call needs the permission plus `FLAG_NO_PICKER`. That includes the daemon keep-alive and boot reconnect paths, not just onboarding.
  - Otherwise a background reconnect after a reboot can hang on a hidden picker, which means missed calls.

### 3. Pairing daemon may advertise the LAN address, not loopback — CLAIMED, conflicts with our users' reports

- thedjchi/Shizuku PR #226, item 1 (tested on Android 17 QPR1 Beta 5/6):
  - "The adb pairing daemon now advertises (via mDNS) and binds to the device's local-network address rather than loopback."
  - Their fix: `AdbMdns` passes on the resolved host, the port probe binds that host, and pairing connects to it instead of `127.0.0.1`.
- **Us:** we pair at `127.0.0.1` (`AdbPairingService.kt:191`) and probe `127.0.0.1` (`AdbMdns.kt:128`).
  - If adbd listens only on the Wi-Fi address, `MdnsServiceVerdict.NOTHING_LISTENING_ON_LOOPBACK` drops the real service and discovery finds nothing.
  - Our own enum KDoc already names this risk and this fix ("dial the resolved address instead of probing loopback").
- Counter-evidence: in #40 a brand-new Pixel 8 user on the September Android 17 build paired fine with 2.4.0. So it is not universal on stable Pixels. It may have been a beta-only behaviour.
- Related: madeye/ServerBox #23 (2026-10-02, Pixel 7a, Android 17, targets 36): "pairing always fails". No diagnosis.
- SPECULATION: a cheap hedge is to fall back to the resolved host when the loopback probe says nothing is listening.
- Watch for any report with the `NOTHING_LISTENING_ON_LOOPBACK` log line.

### 4. USE_LOOPBACK_INTERFACE — docs-only, matters only at targetSdk 37

- Android 17 adds the install-time permission `USE_LOOPBACK_INTERFACE`. For apps targeting 37, cross-app traffic over 127.0.0.1 / ::1 needs it declared by both sender and receiver. Traffic inside one app is exempt.
- Cross-profile loopback is blocked for all apps whatever their target. Okta Verify broke on this.
- thedjchi #172: "Shizuku doesn't seem to need it", from emulator testing.
- **Us:** app↔daemon traffic goes over an abstract unix socket (`RecorderSession.kt`), not TCP loopback, so it is unaffected. Our ADB connect to adbd at `127.0.0.1` goes to a native daemon, not an app.
  - SPECULATION: probably unaffected, but untested. Declare the permission defensively when moving to targetSdk 37.
  - Work-profile and secondary-user installs would be hit by the cross-profile block now, whatever our target.

### 5. Hidden API change: IPackageManager.getInstalledPackages returns PackageInfoList — CONFIRMED, does not affect us

- Shizuku's authorized-apps list is empty on Android 17 in official RikkaApps Shizuku 13.6.0. See #1965, #2383, #2095, #1988; still open, and the repo is effectively unmaintained.
- Already-granted apps keep working. New requests still prompt. Users cannot change grants afterwards.
- Fixes:
  - kerneldroid/Nightzuku commit 6ea7e749 (InstalledPackagesCompat: `getInstalledPackagesAsUser` first, then unwrap `PackageInfoList`).
  - thedjchi PR #226.
  - App Manager commit 836c7248 (#1948, shipped in 4.1.1).
- **Us:** we only call the public `pm.getInstalledPackages` (`VoipAppList.kt:39`), so we are not affected.
  - The side effect for our Shizuku-mode users: on official Shizuku they cannot revoke or re-grant CallVault from the Shizuku UI. Users report official Shizuku "Unable to load" on One UI 9 (#2611, 2026-10-19).
  - Our support docs could point Android 17 users to the thedjchi fork, Shizuku-Next or Porter.

### 6. Shizuku forks in flux — CONFIRMED ecosystem churn

- thedjchi (13.7.0) is still broken on Pixel Android 17 for Start (#301). The maintainer has paused.
- What users moved to on Android 17:
  - rushiranpise/Shizuku-Next (14.0.x).
  - d4rken-org/porter (0.8.1-rc0, tested on Pixel 10 Android 17).
  - RazGame beta.
- **Us:** our memory already says to detect Shizuku by permission, not by package name. That matters more now that users are spread across forks.

### 7. Honor MagicOS 11 (Android 17 beta): NPE inside AudioRecord.startRecording — CLAIMED vendor bug

- Our #53 (2026-10-13, open): VoIP recording crashes at `AudioRecord.java:1573` with a framework NPE. Carrier calls work.
- No matching report anywhere else. scrcpy #6991 and #7048 are Android 17 framework NPEs too, but in SystemUI and screen capture, not audio.
- Nothing to fix until Honor ships stable.

### 8. Samsung One UI 9 Wi-Fi Calling records silent — not Android 17 specific

- Our #45 is the Samsung VoWiFi HAL limit that already exists (memory: samsung-vowifi-not-recordable). Not caused by Android 17.

### 9. App memory limiter (Android 17, all apps) — docs-only, SPECULATION for us

- Android 17 kills apps above a RAM-based memory limit. The exit has `REASON_OTHER` with "MemoryLimiter:AnonSwap" in the description.
- Testing commands: `am memory-limiter ignore|manual|status`.
- No call-recorder reports. Sentry-java #6134 adds handling for it.
- **Us:** on-device whisper and llama runs in the app process could hit it on low-RAM phones. If transcription dies silently on Android 17, check `ApplicationExitInfo` for that string.

### 10. 16 KB page-size compat warning — CLAIMED by JemRec, low relevance

- JemRec PR #24: on the Android 17 16 KB emulator image, `libconscrypt_jni.so` from Conscrypt 2.5.3 is not 16 KB aligned. Android runs the app in page-size compat mode with a warning dialog. Fixed by moving to Conscrypt 2.7.0.
- **Us:** we ship `org.conscrypt:conscrypt-android:2.5.2` (`app/build.gradle.kts:431`) for libadb pairing, so the same warning is likely on 16 KB devices. Very few phones run 16 KB today.
- Worth bumping to 2.7.0 and checking that pairing still works. Untested.

### 11. Wi-Fi network not allowed for wireless debugging — not new to Android 17

- JemRec PR #24, item 3: when Wireless debugging is switched on over an untrusted Wi-Fi, adbd stays silent until the user taps Allow. JemRec now reports `NETWORK_NOT_ALLOWED` with an "Allow this Wi-Fi" action.
- **Us:** already handled: `BaseConnect.WIRELESS_DEBUGGING_REFUSED` in `AdbShell.kt`, from issue #39.

## Checked, nothing Android 17 related found

- **chenxiaolong/BCR** (root):
  - Raised targetSdk to 37 on 2026-06-26 (commit 17329dc5): "No changes are needed". No recording breakage reported.
  - #964 (2026-10-14): the LineageOS 24 (Android 17) file manager no longer shows `/sdcard/Android/data`. Workaround: pick an output folder elsewhere.
  - SPECULATION: matters to us only if any user-facing file lives in Android/data. Our scrcpy server jar does; recordings do not, as far as I saw.
- **kitsumed/ShizuCallRecorder** (our upstream):
  - #102: an AppLogger crash on Pixel 8 Pro Android 17, fixed in v1.3.3. It was a generic bug.
  - #110: Shizuku binder dies silently, maintainer says "a Shizuku issue". Not Android 17 specific; it matches our own rebind-at-call lessons.
- **Genymobile/scrcpy:**
  - No Android 17 audio regression.
  - e71cdb878 (2026-08-07): playback capture now matches all usages, not just USAGE_MEDIA. It is not an Android 17 change. Our VoIP mix chooses its own usages.
- **hari161008/Ever-Call-Recorder, libadb-android, LADB, aShell:** no Android 17 issues or commits.
  - libadb-android PR #36 (bounded waits) is useful, but it is not about Android 17.
- **Magisk and LSPosed call-recording modules, Google Dialer recording mods:** GitHub search found no Android 17 reports. Search coverage there is weak.

## Sources

- https://github.com/thedjchi/Shizuku/issues/301 · /302 · /172 · /223 · /204 · https://github.com/thedjchi/Shizuku/pull/226
- https://github.com/trevornk/Shizuku/commit/3c0de08c · https://github.com/kerneldroid/Nightzuku/commit/6ea7e74984f860398760f5111a15083ea004c842
- https://github.com/d4rken-org/porter/issues/127 · https://github.com/d4rken-org/porter/pull/122
- https://github.com/rushiranpise/Shizuku-Next/issues/4
- https://github.com/frdminc/ShizukuTendCF/issues/25 · https://github.com/Batestinha/shizuku-thedjchi-daemonstatebroadcast/blob/HEAD/docs/android17-wireless-discovery.md
- https://github.com/jemcik/JemRec/pull/24
- https://github.com/RikkaApps/Shizuku/issues/1965 · /2383 · /2611
- https://github.com/MuntashirAkon/AppManager/issues/1948
- https://github.com/chenxiaolong/BCR/issues/964 · https://github.com/chenxiaolong/BCR/commit/17329dc55
- https://github.com/kitsumed/ShizuCallRecorder/issues/102 · /110
- https://github.com/Genymobile/scrcpy/issues/6991 · https://github.com/Genymobile/scrcpy/commit/e71cdb878
- https://github.com/madeye/ServerBox/issues/23
- https://github.com/madkongo/CallVault/issues/40 · /45 · /53
- https://developer.android.com/about/versions/17/behavior-changes-17 · https://developer.android.com/about/versions/17/behavior-changes-all · https://developer.android.com/privacy-and-security/local-network-permission
