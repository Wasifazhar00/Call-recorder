# Android 17 and call recording (2026-10-20)

📐 CALCULATED — desk research only. Sources: AOSP `android-16.0.0_r4` → `android-17.0.0_r1` (build CP2A),
Google's docs, competitor repos and issue trackers, and 71,419 Play reviews, the web and Reddit. Nothing has
been tested on an Android 17 phone; we have none. The three full reports, with links and file:line, are in
`android-17-2026-10-20/`.

## Bottom line

- **Call audio itself is unchanged.** We found no change to the capture rules (`VOICE_CALL`,
  `CAPTURE_AUDIO_OUTPUT`, concurrent capture, policy mixes), and the shell lost no permissions. No
  competitor or user reports Android 17 breaking the audio. *Read from source; not measured.*
- **What changes is the debugging transport around it**, plus one vivo-only fallback.
- Most of the rest only applies once we target SDK 37. We target 36.

## Affects us now, at target 36

| # | Change | Impact on CallVault | Evidence |
|---|---|---|---|
| 1 | **Wireless debugging stays "on" when Wi-Fi drops or the network is untrusted** ("ADB Wi-Fi 2.0", flag `allow_adb_wifi_reconnect` on). Android no longer writes `adb_wifi_enabled=0`. adbd stops and restarts its own TLS server. Trusted networks now match on BSSID *or* SSID. | Our "Android switched it back off" check (`AdbShell.kt:332-340`) never fires, so on an untrusted network we wait the full mDNS timeout again (the #39 symptom). A setting of 1 no longer means "reachable", so `WirelessDebuggingOffCause` and [[debugging-switches-model]] are wrong on 17. **Good side:** returning to a trusted Wi-Fi, including mesh networks, may bring debugging back without us. | Read in source |
| 2 | **`AudioRecord` internals changed:** `mChannelCount`/`mChannelMask` are gone (now `mChannelMasks`), and the private `native_setup` signature changed. | `server/BypassedAudioRecord.kt` (lines 87-88, 119) throws. It is the fallback for vendors whose `AudioRecord` constructor crashes, in practice **vivo**. **App-call recording on vivo with Android 17 would fail.** scrcpy has no fix yet. | Read in source |
| 3 | adb's mDNS advertising moved into system_server, with a new service name and TXT record. | Probably fine: we match on service type only (`AdbMdns.kt:53`). Two Android 17 Pixel users in #40 connect on 2.4.0. | Read in source + user reports |
| 4 | Debugging switches read "0" for apps. | **Already handled** in 2.4.0 (#40). Every Shizuku fork and Porter fixed the same thing later. | Known |
| 5 | Pairing service may listen on the Wi-Fi address instead of loopback (one fork's claim, QPR1 betas). | We pair and probe at `127.0.0.1`, so we would drop it as `NOTHING_LISTENING_ON_LOOPBACK`. Not seen on stable. Cheap hedge: fall back to the resolved host when that verdict appears. | One report |
| 6 | Official Shizuku shows an empty authorised-apps list (a hidden API changed). | Not our bug. Android 17 users are moving to Shizuku forks (Shizuku-Next, Porter), so our Shizuku help text should name a working one. | Reported widely |

## Coming, or on the day we target 37

| # | Change | What we will need |
|---|---|---|
| 7 | `ACCESS_LOCAL_NETWORK` runtime permission gates mDNS and local sockets. | Declare and request it before the first discovery. Pass `FLAG_NO_PICKER` on **every** discovery, including keep-alive and boot reconnect: Shizuku forks got a "Choose a device" picker on every start, and a boot start hung on a picker nobody saw. Already in the backlog. |
| 8 | `USE_LOOPBACK_INTERFACE`. | Our app↔daemon link is an abstract unix socket, so it is unaffected. Declare it anyway as a precaution. |
| 9 | Background audio hardening. | Playing a recording in the background can go silent. Needs a media-playback setup that qualifies. |
| 10 | **Developer verification** for apps installed outside Play: 4 countries from 2026-09-30, **global in 2027**. Delivered through Play services on Android 7+, so it is not tied to Android 17. | Installing from GitHub will need the "advanced flow" (developer mode, restart, 24-hour wait), unless we register as an open-source developer. Our `pm install` updater looks exempt ("ADB installs won't require verification"), but that is unconfirmed for an on-device shell. The PackageInstaller fallback will fail. **This is a distribution decision for the maintainer, not a code fix.** |
| 11 | Proposal, not shipped: stop adbd listening on loopback (after CVE-2026-0073; tracker 526109803, 541312863). | Would break every on-device ADB app, CallVault and Shizuku-over-ADB included, and our off-Wi-Fi recording. Discussion only, no commit found. AOSP no longer develops in public, so we would see it only at a release tag. **Watch.** |
| 12 | App memory limiter (when the vendor ships a config). | On-device transcription and summaries run in the app process and could be killed in the background on low-RAM phones. The daemon is exempt. No reports yet. |
| 13 | 16 KB page size. | We ship Conscrypt 2.5.2. 2.7.0 is 16 KB-aligned; JemRec moved to it. |

## Market signal

- Cube ACR (56.6M installs, the leading active recorder) added a Wireless-debugging "ADB Call Line" around
  July 2026. Since June, 38 of its reviews complain:
  - it only works on Wi-Fi;
  - debugging switches off every 5-15 minutes;
  - it needs re-pairing.

  One user asks for Shizuku instead. A further 168 say only their own side is recorded. These are the
  problems CallVault has already worked through (keep-alive, off-Wi-Fi recording, pair again, Shizuku mode).
- Only 10 of the 71,419 reviews mention "Android 17". Users don't name the OS version, so silence there
  proves nothing.
- One UI 9 and Google Phone now announce that a call is being recorded, and many reviews complain about it.

## Checks that need an Android 17 phone

1. Leave Wi-Fi, then join an untrusted network: how long until CallVault gives up, and what does it say? (#1)
2. Return to a trusted Wi-Fi: does the recorder come back on its own? (#1, possibly good news)
3. mDNS discovery and pairing after a reboot and a reconnect. (#3, #5)
4. On a vivo with Android 17: an app call, to see whether the fallback throws. (#2)
