# Auto-approving "Allow wireless debugging on this network?" (2026-10-21)

🧪 VERIFYING (the untrusted-network case ✅ VERIFIED 2026-10-21 by the maintainer) — built 2026-10-21 on `feat/auto-approve-wifi-network` (opt-in switch "Approve new Wi-Fi networks
automatically", Settings ▸ General ▸ Experimental). Research read from AOSP (`android-16.0.0_r4`, `android-17.0.0_r1`)
and the six repos a user suggested. **Not yet tried on a network Android does not trust** (see "Tested" below).

Feature request (2026-10-21, user with screenshot): when Wireless debugging is switched on while the phone is on a Wi-Fi
network Android does not yet trust, Android shows "Allow wireless debugging on this network?" (SSID, BSSID,
"Always allow on this network", Cancel / Allow). Recording waits until someone taps Allow. The user asked whether
CallVault could approve it automatically.

## Verdict: yes, without root and without an accessibility service, while a shell process is alive

- The dialog's Allow button calls `IAdbManager.allowWirelessDebugging(alwaysAllow, bssid)` (Android 11-16) /
  `allowWirelessDebugging(alwaysAllow, bssid, ssid)` (Android 17). The only check is the `MANAGE_DEBUGGING`
  permission (`AdbService.java:316-320` on 16, `:323-327` on 17).
- **The shell uid holds `MANAGE_DEBUGGING`** (`packages/Shell/AndroidManifest.xml:540`, still there on 17). Our
  daemon and Shizuku's server both run as shell.
- **No prompt has to be showing.** `MSG_ADBWIFI_ALLOW` (`AdbDebuggingManager.java:850-873` on 16) only checks that
  wireless debugging is not already running and that the BSSID passed is the one the phone is connected to now.
  It then turns wireless debugging on, and with `alwaysAllow` adds the network to the trusted list. So the call can
  replace our `adb_wifi_enabled=1` write; Android then never asks.
- On Android 17 the network must match by SSID (`ADM17.java:1040`), and trust is stored as BSSID + SSID.

## What it needs

1. **A live shell process at the moment the network changes.** The daemon survives Wireless debugging going off
   as long as adbd keeps running, which it does while USB debugging is on (measured, [[debugging-switches-model]]).
   Shizuku's server is the same. If no shell process is alive (adbd dead, or USB debugging off), there is no way
   in, and the prompt stays: the user taps Allow once, as today.
2. **The current BSSID and SSID.** The app cannot read them in the background without location (KeepADB measured
   this: placeholders `02:00:00:00:00:00` / `<unknown ssid>` unless an activity is in the foreground). The daemon
   can: `cmd wifi status` as shell, or `WifiManager` from the shell process. Read them in the daemon.
3. **Two call shapes**, picked by reflection: `(boolean, String)` up to 16, `(boolean, String, String)` on 17.

## Decisions for the maintainer

- **Opt-in, off by default.** The prompt is Android's protection against opening the debugging port on a network
  the owner did not choose (a café, a hotel). Only paired keys can connect, so the risk is limited, but approving
  every network silently is a choice the user should make. It fits the planned Recordings ▸ Reliability group
  ([settings-per-page plan](2026-10-20-settings-per-page-plan.md)), next to "Keep Wireless debugging on".
- **Allow once, not always** (`alwaysAllow = false`), suggested. Android's own trusted list stays the user's; we
  approve again on the next visit. Cheaper to undo: turning our switch off restores Android's behaviour fully.
- Optional narrowing later: only networks the phone has saved (not open hotspots), or an in-app allowlist.

## Respect the user's own switch

Only call it where we would write Wireless debugging on today. `WirelessDebuggingEnableGate` already decides that
(`RESPECT_USER` when the user switched it off). Auto-approve must not get round that gate ([[only-undo-switches-we-set]]).

## The six suggested repos (read only, nothing built or run)

| Repo | What it does | Handles this prompt? |
|---|---|---|
| m00sfett/KeepADB | Keeps Wireless debugging on; its own trusted-networks list | **No.** README: "Android may show its own confirmation… KeepADB waits." |
| mouldybread/adb-auto-enable | On boot: Wireless debugging + legacy `tcpip 5555` | No |
| tangshui9527/adb-auto-enable | Copy of the one above | No |
| nozza87/Auto_ADB | Accessibility service that reads the **pairing code** dialog | No (different dialog) |
| Tarunswamy-Muralidharan/adb-quickstart | Quick Settings tile, PC scripts | No |
| project-SIMPLE/adb-auto-enable | Accessibility service that clicks "Always allow on this network" (`android:id/button3`) on Meta Quest's popup | **Yes, by clicking it** |

Clicking it with an accessibility service works only when the prompt is shown, depends on the vendor's dialog
layout, and needs a permission that Android 13+ restricts for sideloaded apps and that users rightly distrust. The
shell call above needs none of that, so it is the better route.

## Checks on a phone when built

1. OP9, built-in mode, USB debugging on: join a Wi-Fi never used with Wireless debugging. No prompt; recorder
   reconnects; Wi-Fi network not added to Android's list (with allow-once).
2. Same in Shizuku mode.
3. Switch off: prompt appears as before.
4. User turned Wireless debugging off themselves: stays off.
5. Android 17 phone (none yet): the three-argument call.

## Built (2026-10-21)

- Recorder side: `server/WirelessDebuggingApproval.kt` reads `cmd wifi status`, calls `allowWirelessDebugging`
  by reflection (2 or 3 arguments). New AIDL method `approveWirelessDebuggingNetwork(alwaysAllow)`, appended (id 28).
- App side: `NetworkApprovalPolicy` (setting on, built-in mode, recorder alive, gate says WRITE).
  `AdbShell.enableWirelessDebugging` tries the approval before the plain write. `DaemonKeepAliveService` approves
  4 s after the phone joins a Wi-Fi (`approveCurrentNetworkIfWanted`, never falls back to the plain write).
- **Design change from the research:** approving only when CallVault switches Wireless debugging on was not
  enough. That happens when the recorder has to be restarted, and then there is no shell process left. So the
  approval is made when the phone joins a Wi-Fi, while the recorder still runs.
- Approve once by default. "Always" only when "Wireless debugging off when idle" is on, because a once-only
  approval would be gone before the next restart.
- Mode-gated switch (`wd_auto_approve_network`, WIRELESS_DEBUGGING_CONTROL); Settings-only (decision row in
  `SettingDecisionsTest`). Strings in 15 languages.

## Tested (OP9, OxygenOS 14 / Android 14, built-in mode, 2026-10-21) — 🧪

- Wi-Fi off and on, home network (already trusted), Wireless debugging off before: back on 4 s after joining.
  Log: `CV:WdApproval: Approved Wi-Fi "Baba-5G" … (alwaysAllow=false)` from the recorder (shell pid), then
  `adbd: adbwifi started`. So OxygenOS lets the shell make the call. Before this build it stayed off.
- Switched off by hand (simulated with `settings put`), then Wi-Fi off and on: stayed off, `RESPECT_USER`.
- ✅ VERIFIED 2026-10-21 (maintainer, "nope it didnt come up"): OP9 moved from home Wi-Fi straight to the
  OP12 hotspot "Feroza" (never trusted). No prompt. Log: Android "Network disconnected. Disabling adbwifi" →
  CallVault reclassified it as Android's 3 s later → recorder approved "Feroza" 6.5 s after the switch-off →
  `adbwifi started`. The hotspot was not added to Android's trusted list (allow-once).
- **The first hotspot try found a bug, fixed in `25cf2162`:** moving straight between networks, Wi-Fi reads
  connected when Android writes the switch off, so CallVault took it for the user's tap and refused to switch
  it on again (`RESPECT_USER`). Pre-existing: it also blocked the recorder restart after any direct network
  switch. Now a switch-off within 6 s of a Wi-Fi loss is `ANDROID_NETWORK_CHANGED`. 🧪 beyond this one run.
- Shizuku mode: switch shown off and greyed, "Not available in Shizuku mode" (checked by Claude on the OP9).
- **Not tested:** a real recorder restart on the new network (it should find Wireless debugging on), "WD off
  when idle" + always-allow, Android 17.
