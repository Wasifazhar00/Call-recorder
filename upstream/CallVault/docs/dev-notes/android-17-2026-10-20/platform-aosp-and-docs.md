# Android 17 (API 37): platform changes that touch call recording, and CallVault

Researched 2026-10-20. Read-only, nothing changed in the repo.
Status of every item here: 📐 read from source or docs, **not measured on a phone**.

How strongly each claim is held:
- **VERIFIED-FROM-SOURCE**: I read it in AOSP at tag `android-17.0.0_r1` (build `CP2A.260605.016`), compared with `android-16.0.0_r4`.
- **DOCUMENTED**: Google's developer docs say so. I did not check the code.
- **REPORTED/UNCONFIRMED**: press or forum reports only.

Release flags: `android-17.0.0_r1` is build CP2A, so the `cp2a` release config applies (`platform/build/release`, `release_configs/cp2a.textproto` inherits `cp1a`, then `bp4a`). OEM builds can set flags differently.

## Ranked by impact on CallVault

| # | Change | Applies to | Hits shell daemon? | Impact on us | Status |
|---|---|---|---|---|---|
| 1 | Wireless debugging now **stays on** when Wi-Fi drops or the network is untrusted. It restarts by itself on a trusted network. | All apps (it's platform behavior) | n/a | Breaks our "Android switched it back off" check. Changes the debugging-switches model. | VERIFIED-FROM-SOURCE |
| 2 | `AudioRecord` private fields and `native_setup` signature changed | Every process that uses reflection on it | **Yes** | `BypassedAudioRecord` (the vivo fallback) throws on Android 17 | VERIFIED-FROM-SOURCE |
| 3 | The adb mDNS service is now advertised by system_server (NsdManager), with a new service name and TXT records | Platform | n/a | `AdbMdns` discovery probably still works. Needs one test on an Android 17 phone. | VERIFIED-FROM-SOURCE (the change); effect UNCONFIRMED |
| 4 | Google may stop adbd listening on loopback (127.0.0.1) | Future, not in 17.0 | — | Would kill our whole transport. We connect to 127.0.0.1 every time. | REPORTED/UNCONFIRMED |
| 5 | Developer verification for sideloaded apps (outside the OS release, through Play services) | Certified devices, Android 7+ | — | Stores in 4 countries from 2026-09-30; everywhere in 2027. Our `pm install` updater looks exempt, but the PackageInstaller fallback is not. | DOCUMENTED |
| 6 | Background audio hardening (playback, focus, volume) | All apps; stricter at target 37 | No (shell is exempt) | Nothing at target 36. At target 37, playing a recording in the background can go silent. | VERIFIED-FROM-SOURCE + DOCUMENTED |
| 7 | `ACCESS_LOCAL_NETWORK` runtime permission | Target 37 only | No (shell targets 36) | Already in the backlog. Loopback is not clearly exempt. | DOCUMENTED |
| 8 | App memory limiter (cgroups, kills over-limit apps) | All apps with uid ≥ 10000, when the vendor ships a config | No | Transcription and summary models run in the app process and could be killed in the background | VERIFIED-FROM-SOURCE (mechanism); limits depend on the device |
| 9 | `Settings.Global.ADB_ENABLED` / `DEVELOPMENT_SETTINGS_ENABLED` read as "0" | App uids | No (uid < 10000 sees the real value) | Already known. `ADB_WIFI_ENABLED` is **not** redacted. | VERIFIED-FROM-SOURCE |
| 10 | Capture rules: CAPTURE_AUDIO_OUTPUT, VOICE_CALL, concurrent capture, policy-mix registration | — | — | **No change found** | VERIFIED-FROM-SOURCE |
| 11 | Shell (`com.android.shell`) permissions | — | Yes | Nothing removed. Shell now declares `targetSdkVersion=36`. | VERIFIED-FROM-SOURCE |
| 12 | Smaller items: system-usage playback needs MODIFY_AUDIO_ROUTING; `createRecord` refused for frozen callers; persistent policy mixes; Android Advanced Protection Mode (AAPM); QPR2 changes; Pixel call recording | various | — | Low, or a possible opportunity | mixed |

---

## 1. Wireless debugging stays on across network loss (the "ADB Wi-Fi 2.0" change)

**What changed.** A new flag `com.android.server.adb.allow_adb_wifi_reconnect` is ENABLED and READ_ONLY in cp2a.
- Source: `build/release` `aconfig/cp2a/com.android.server.adb/allow_adb_wifi_reconnect_flag_values.textproto`.
- With the flag on, `AdbDebuggingManager` **no longer writes `adb_wifi_enabled=0`** when:
  - there is no Wi-Fi;
  - the network is untrusted;
  - the user taps "deny" on the network prompt.
- Instead it registers `AdbWifiNetworkMonitor` and sends `MSG_START_TLS_SERVICE` / `MSG_STOP_TLS_SERVICE`. adbd's TLS server stops and starts on its own as the phone joins and leaves trusted networks.
- Source: `frameworks/base` `services/core/java/com/android/server/adb/AdbDebuggingManager.java`, at tag 17. See the `MSG_ADBDWIFI_ENABLE`, `MSG_START_TLS_SERVICE`, `MSG_STOP_TLS_SERVICE`, `MSG_ADBWIFI_ALLOW` and `MSG_ADBWIFI_DENY` branches. Every old `putInt(ADB_WIFI_ENABLED, 0)` is now behind `if (!allowReconnect)`.
- A network counts as trusted if its BSSID **or** SSID matches one the user marked "Always allow". Before, only the BSSID counted, so mesh Wi-Fi with several access points now reconnects. Source: `AdbKeyStore.isTrustedNetwork(bssid, ssid)` and `AdbAuthorizationStore.WifiNetwork`.
- The pairing key still expires the same way as before. I found no change to `ADB_ALLOWED_CONNECTION_TIME` handling, so issue #43 stands as it is.
- The press calls this "ADB Wi-Fi 2.0" (androidauthority.com/android-17-adb-wi-fi-2-0-3678411, which returned 403, so I only saw the search snippet). It is also reported to be a rewrite of adbd's Wi-Fi code as a 4,000-line Rust library. I did not verify the Rust part.

**Who it applies to.** Every device running this build, whatever an app targets. OEMs may turn the flag off.

**Impact on CallVault.**
- `integrations/adb/AdbShell.kt:332-340` (`connectViaWirelessDebuggingWithReason`) reads back the `adb_wifi_enabled=1` we just wrote, to spot "Android switched it back off". On Android 17 Android never switches it back off. So `WIRELESS_DEBUGGING_REFUSED` never fires, and we fall through to the full mDNS wait (`MDNS_TIMEOUT_MS`), the #39 symptom. The cause also changes: we report `NO_ADB_SERVICE` instead of "untrusted network / no Wi-Fi".
- `integrations/adb/WirelessDebuggingOffCause.kt`: `ANDROID_REFUSED` and `ANDROID_NO_WIFI` can no longer happen on Android 17. A 0 now almost always means the user, or us, turned it off. That makes the cause easier to tell, but the design comment and `docs/dev-notes/2026-09-14-debugging-switches-model.md` are wrong for Android 17.
- `integrations/adb/DeveloperOptionsPolicy.kt:48` says "`adb_wifi_enabled`, which Android 17 leaves truthful". The value is no longer redacted, which is true. But "1" no longer means adbd's TLS port is up: it stays 1 off Wi-Fi. Any status UI that shows "Wireless debugging on" from this setting will be optimistic.
- Upside: after a Wi-Fi blip on a trusted network, adbd comes back without us writing the setting. Reconnects should be faster, and there are fewer chances to hit the reboot or WD-off-by-user deadlock.
- To fix: on API ≥ 37, don't treat a read-back 1 as proof. Use a short TLS-port probe or `WifiState`, plus a trusted-network hint, before starting the long mDNS wait.

## 2. `AudioRecord` internals changed, so `BypassedAudioRecord` breaks

**What changed.**
- `frameworks/base` `media/java/android/media/AudioRecord.java` (tag 17): the fields `mChannelCount`, `mChannelMask` and `mChannelIndexMask` are removed. They are replaced by `private AudioFormat.ChannelMasks mChannelMasks`.
- The private native method is now `native_setup(Object, Object, int[], Object /*ChannelMasks*/, int, int, int[], Parcel, long, int, int)`. It was `(…, int channelMask, int channelIndexMask, …)`.
- The deprecated `String opPackageName` wrapper still exists, but it is marked `maxTargetSdk=R`.

**Who it applies to.** Any process that uses reflection on these. That includes our `app_process` shell daemon, since hidden-API rules don't save you from a field that no longer exists.

**Impact on CallVault.**
- `server/BypassedAudioRecord.kt:105` (`field("mChannelCount")` / `field("mChannelMask")`) will throw `NoSuchFieldException`.
- The SDK ≥ 34 `native_setup` lookup (`…, i, i, i, i, IntArray, Parcel, l, i, i`) will throw `NoSuchMethodException`.
- This path runs only after the normal path fails, which in practice means vivo. **On vivo phones with Android 17 (OriginOS on 17), app-call recording through this fallback will fail.**
- Upstream scrcpy `Workarounds.java` still uses the old fields (checked 2026-10-20, latest release v5.0.1), so there is no upstream fix to port yet.
- To fix: on API ≥ 37, set `mChannelMasks = new AudioFormat.ChannelMasks(positionMask, indexMask)` (hidden constructor) and call the new `native_setup` signature.
- I checked the other hidden calls we make: `AudioPolicy.createAudioRecordSink`, `getRegistration`, `AudioManager.registerAudioPolicyStatic` / `unregisterAudioPolicyAsyncStatic`, `AudioMixingRule.Builder.addMixRule`, `AudioMix.Builder.build`. All still exist at tag 17.

## 3. adb mDNS advertising moved into system_server

**What changed.**
- With `com.android.adbdauth.flags.use_tls_lifecycle` ENABLED in cp2a, adbd no longer advertises `_adb-tls-connect._tcp` through `mdnsd`. On API 37 it calls `adbd_auth_register_service()`. system_server's new `AdbdServicesManager` then registers it through `NsdManager` and holds a Wi-Fi `MulticastLock` "to keep responding to mDNS probes even if the screen is off".
- Sources: `packages/modules/adb` `daemon/mdns.cpp` (tag 17) and `frameworks/base` `services/core/java/com/android/server/adb/AdbdServicesManager.java`.
- The service name is now the device GUID instead of `adb-<serial>`.
- The TXT record changes to `v=2.0`, `name`, `api`, `given_name`, `serial`.
- The classic `adb tcpip` port is also advertised as `_adb._tcp` once the framework connects (`on_framework_connected`). Our offline-recording loopback port is therefore announced on the LAN, as it was through mdnsd before.

**Impact on CallVault.**
- `integrations/adb/AdbMdns.kt` matches on the full service type and checks the address against our own interfaces (`isOneOfOurAddresses`) and the loopback bind probe. The new names shouldn't matter.
- The open question is whether NsdManager discovery in our app reliably sees a service that the same system_server advertises. I could not settle that from source. **Test once on an Android 17 Pixel**: pair fresh, toggle Wi-Fi, screen off for 10 minutes, then let a call trigger the recorder.
- The multicast lock should make discovery more reliable with the screen off. That is a possible relief for the screen-off notes, not measured.

## 4. Possible future block on adbd's loopback listener (REPORTED/UNCONFIRMED)

**What's reported.**
- After CVE-2026-0073 (an adbd wireless TLS authentication bypass in `adbd_tls_verify_cert`, fixed in the 2026-05-01 bulletin, https://source.android.com/docs/security/bulletin/2026/2026-05-01), someone asked on the Issue Tracker to let users choose which interface adbd binds to.
- A Google adb maintainer reportedly replied: "How about restricting it to only bind to the Wi-Fi interface, wlan0?" That would block 127.0.0.1.
- Coverage: androidauthority.com/google-android-on-device-adb-changes-discussion-3691371, gigazine 2026-07-29, braindetox.kr (2026-07-27).
- No code change is public. In the tag 17 source the TLS server still listens on all interfaces, which we rely on.
- AOSP `main` for `packages/modules/adb` stops at 2025-03, because Google now publishes code only at release tags. We will not see such a change before it ships.

**Impact.** This would be fatal for us and for Shizuku. Every CallVault connection goes to `127.0.0.1`: `AdbShell.kt:360`, `:529`, and `AdbPairingService.kt:191`. Watch the 17 QPR and 18 tags for changes to `daemon/` and `transport_socket_server.cpp`. Plan B is to connect to the phone's own Wi-Fi IP instead of 127.0.0.1. That would survive a wlan0-only bind, but on target 37 it falls under `ACCESS_LOCAL_NETWORK`.

## 5. Developer verification for apps installed outside Play (DOCUMENTED)

Sources:
- https://developer.android.com/developer-verification
- https://developer.android.com/developer-verification/guides/faq

What they say:
- From 2026-09-30, installs from participating stores in Brazil, Indonesia, Singapore and Thailand are checked, on certified devices running Android 7+.
- Global rollout follows in 2027. It is delivered through Google Play services, not tied to Android 17.
- Unregistered apps can only be installed or updated through the "advanced flow" (developer mode, a restart, a 24-hour wait, biometric confirmation) **or ADB**. The FAQ says: "Apps installed using ADB won't require verification."
- There is a limited-distribution tier (up to 20 devices, no ID) and an open-source registration guide.

Impact on CallVault:
- Sideloading from GitHub is not affected yet; that starts in 2027.
- `system/updates/UpdateInstaller.kt` streams updates through `pm install -S` over our own shell. That looks like an ADB install, so it probably stays exempt. I have not confirmed that an on-device shell counts as "ADB" for the verifier.
- The `installViaPackageInstaller` fallback will fail for an unregistered app once enforcement reaches the user.
- The first install from GitHub still needs the advanced flow, or registering as an open-source developer.

## 6. Background audio hardening (playback, focus, volume only)

**What changed.**
- Docs: https://developer.android.com/about/versions/17/changes/bg-audio.
- On Android 17, every app needs a visible activity or a foreground service (not `SHORT_SERVICE`) to play audio, request focus or change volume. If not, calls fail silently, and focus returns `AUDIOFOCUS_REQUEST_FAILED`.
- At target 37 the foreground service must also have while-in-use (WIU) capability.
- Recording (`AudioRecord`) is not affected.

**Source.** `frameworks/av` `services/audioflinger/Tracks.cpp:3728-3776` (`getHardeningDecision`):
- Any uid holding `MODIFY_AUDIO_ROUTING` or `MODIFY_PHONE_STATE` is exempt (`PRIVILEGED_APP`). Shell holds both, so the daemon's `server/CaptureSelfTest.kt` tone track is exempt.
- Apps with target < 37 get only `PARTIAL` enforcement (`TARGET_SDK`).
- Test with `adb shell cmd audio set-enable-hardening enable|disable|throw`.

**Impact on CallVault.**
- At target 36: none. Our specialUse keep-alive foreground service satisfies `PARTIAL`.
- At target 37: if the user plays a recording (`ui/viewmodels/RecordingPlaybackController.kt`, `MediaPlayer`) and then leaves the app or turns the screen off, playback goes silent. The keep-alive service is started at boot or from the background, so it has no WIU. A fix would be a `mediaPlayback` foreground service started from the UI, or media3 `MediaSessionService`. Add this to the "Targeting Android 17" backlog.

## 7. `ACCESS_LOCAL_NETWORK` (target 37, already in the backlog)

- Docs: https://developer.android.com/privacy-and-security/local-network-permission. It applies only at target ≥ 37.
- Below 37, holding `INTERNET` gives an implicit grant.
- Denied TCP connections usually time out, and UDP gets `EPERM`.
- The page **does not say whether 127.0.0.1 is exempt**. Test loopback connects as well as NsdManager when we bump the target.
- Shell now declares `ACCESS_LOCAL_NETWORK` (featureFlag `access_local_network_permission_enabled`) and targets 36, so the daemon is unaffected.

## 8. App memory limiter

**What changed.**
- New `MemoryLimiter` in system_server. Source: `frameworks/base` `services/core/java/com/android/server/am/MemoryLimiter.md` at tag 17.
- It uses cgroup v2 `memory.high` / `memory.swap.max`, but **only for uid ≥ 10000**.
- It is active only if the vendor ships `/vendor/etc/memory-limiter-config.xml`.
- A process over anon + swap limit is notified, then killed 30 seconds later. The exit reason is `REASON_OTHER` with "MemoryLimiter:AnonSwap".
- A foreground-service process gets the "Not-visible" limits.
- Docs: https://developer.android.com/about/versions/17/behavior-changes-all (all apps).

**Impact.**
- The shell daemon is exempt, so recording is safe.
- On-device transcription and summaries (whisper and llama models in the main app process, with no `android:process`) running in the background could be killed on low-RAM phones that ship a config.
- To catch it: log `ApplicationExitInfo` descriptions in the diagnostics. Test with `am memory-limiter status` and `am memory-limiter manual <pid> <MB>`.

## 9. Settings redaction (already known; scope confirmed)

- `core/java/android/provider/Settings.java` (tag 17), lines 14764 and 15050: `@Readable(redactedValue = "0")` is on `ADB_ENABLED` and `DEVELOPMENT_SETTINGS_ENABLED` only.
- The check is client-side in `NameValueCache` (lines 3846-3858). It redacts only when the calling appId ≥ `FIRST_APPLICATION_UID`. The shell daemon (2000) reads real values.
- `ADB_WIFI_ENABLED` (line 14772) is plain `@Readable`, not redacted.
- There is a new setting, `ADB_DISCONNECT_SESSIONS_ON_REVOKE`. It disconnects live adb sessions when the user revokes debugging authorizations. That is user-initiated only.

## 10. Capture rules: no change found (good news)

- `frameworks/av` diff from 16_r4 to 17_r1:
  - `media/utils/ServiceUtilities.cpp` is unchanged (recordingAllowed, captureAudioOutputAllowed, captureVoiceCommunicationOutputAllowed, and so on).
  - `AudioPolicyService::updateUidStates_l` (the concurrent-capture and silencing rules) is unchanged.
  - `AudioService.isPolicyRegisterAllowed` (policy-mix and loopback registration checks) is unchanged.
- `OpRecordAudioMonitor::checkOp` now uses `checkOperationForDevice` with the attribution tag. That is a refactor; shell still passes.
- **Nothing on Android 17 takes away the shell's ability to capture VOICE_CALL, uplink/downlink, or VoIP playback.**

## 11. Shell permission list, 16 → 17

`frameworks/base` `packages/Shell/AndroidManifest.xml`:
- **No permissions removed.** These are still present: RECORD_AUDIO (line 344), CAPTURE_AUDIO_OUTPUT (561), MANAGE_DEBUGGING (564), MODIFY_AUDIO_ROUTING (739), CAPTURE_MEDIA_OUTPUT (901), CAPTURE_VOICE_COMMUNICATION_OUTPUT (907), WRITE_SECURE_SETTINGS (157), MODIFY_PHONE_STATE (272), START_FOREGROUND_SERVICES_FROM_BACKGROUND (290).
- Added: ACCESS_LOCAL_NETWORK, MANAGE_ASSISTANT_AUDIO, OVERRIDE_MEDIA_SESSION_OWNER, plus many for CTS tests.
- New `<uses-sdk android:targetSdkVersion="36"/>` (line 1245). Checks keyed on the shell's target SDK (audio hardening, local network) treat the daemon as a target-36 app.

## 12. Smaller items

- **System-usage playback needs MODIFY_AUDIO_ROUTING.** `getOutputForAttr` now applies that check to every system usage, not just SPEAKER_CLEANUP (`AudioPolicyInterfaceImpl.cpp`, around line 426). We only play `USAGE_VOICE_COMMUNICATION`, so there is no impact.
- **`createRecord` refuses frozen callers.** It returns PERMISSION_DENIED if the calling pid is frozen (`AudioFlinger.cpp` createRecord, `checkFrozen`). Our daemon creates the records, so there is no impact.
- **Persistent policy mixes.** A new hidden `AudioMix.mIsPersistent` keeps both remote-submix ends connected from registration onward (`AudioPolicyManager.cpp` `registerPolicyMixes` and `startInput`). That **might** help with our "arm before the call" limit. 📐 Not tested; it's an opportunity only.
- **`capture_offloaded_audio` flag.** Playback capture may now include compress-offload tracks. Minor.
- **Advanced Protection Mode (AAPM).** The AOSP hooks are 2G, unknown sources, USB data, WEP, MTE, insecure Wi-Fi autojoin, and non-tool accessibility services. **None of them disable ADB or Wireless debugging.** But "unknown sources" blocks sideloading CallVault and the PackageInstaller update fallback. That predates 17; it has been in AOSP since Android 16.
- **QPR2 (betas through 7.1, 2026-10-16).**
  - Call-forwarding USSD now needs more than `CALL_PHONE`. That doesn't affect us.
  - Self-broadcasts no longer keep the sender alive. We send none (grep finds no `sendBroadcast`).
  - Source: https://developer.android.com/about/versions/17/qpr2/release-notes.
- **QPR1 Beta 4 bug.** "Wireless ADB and local network-dependent apps failed to connect" (issuetracker 506418219) was fixed in a later beta. Ask any reporter on that build to update.
- **Target-37-only changes we already meet or don't use.**
  - Static-final reflection lock: we write only instance fields (`AudioRecord.*`, `ActivityThread.mInitialApplication`).
  - Safer native DCL: `System.load` is only used in the daemon, from the read-only APK lib directory.
  - IntentSender background-activity-launch rules, content capture, and the `MessageQueue` rewrite: not used.
- **Implicit URI grants for `ACTION_SEND` (Android 18).** Our share intents already add `FLAG_GRANT_READ_URI_PERMISSION` plus ClipData (`system/RecordingShare.kt`, `system/SystemIntentHelpers.kt`).
- **Pixel call recording.** Google's own Phone app "Call Recording" is rolling out to every Pixel country in 2026, on Pixel 6 and later, with a spoken notice to the other party (9to5google 2026-02-19; androidauthority pixel-call-recording-worldwide-3642708). That is competition, not a platform restriction.
- **Android 18.** No public preview or behavior-changes page was found as of 2026-10-20.
- **Not covered:** a line-by-line Telecom/telephony diff, and MediaProjection (CallVault doesn't use it).

## What needs a real Android 17 phone (all 🧪 until the maintainer confirms)

1. Wireless debugging on, then leave Wi-Fi. Does `adb_wifi_enabled` stay 1? How long does our connect take, and what reason do we report?
2. Return to the trusted Wi-Fi. Does adbd come back without us, and does `AdbMdns` find the port (service advertised by system_server)?
3. On vivo with Android 17, does the normal far-party sink path work? If not, the fallback crashes (item 2).
4. Optional: `am memory-limiter status`, and summarise a long call in the background.
