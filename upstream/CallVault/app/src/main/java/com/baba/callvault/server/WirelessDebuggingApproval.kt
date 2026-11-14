/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.server

import com.baba.callvault.utils.AppLogger
import java.util.concurrent.TimeUnit

/** What [WirelessDebuggingApproval.approveCurrentNetwork] did. Sent to the app by name. */
enum class NetworkApproval {
    APPROVED,

    /** `cmd wifi status` named no network, or only placeholders: not on Wi-Fi, or the shell could not read it. */
    NO_WIFI_IDENTITY,

    /** No `adb` system service, or no `allowWirelessDebugging` of a shape we know. */
    UNSUPPORTED,

    FAILED,
}

/** The network Android's "Allow wireless debugging on this network?" prompt asks about. */
data class WifiIdentity(val ssid: String, val bssid: String)

/**
 * Approves the current Wi-Fi network for Wireless debugging, the way the prompt's Allow button does, so
 * Android never shows "Allow wireless debugging on this network?".
 *
 * Runs in the shell-uid recorder process. The prompt's button calls `IAdbManager.allowWirelessDebugging`,
 * guarded only by `MANAGE_DEBUGGING`, which the shell holds (`packages/Shell/AndroidManifest.xml`). Android
 * then switches Wireless debugging on for this network; no prompt needs to be showing (`AdbDebuggingManager`,
 * `MSG_ADBWIFI_ALLOW`). Read from AOSP 16 and 17; see docs/dev-notes/2026-10-21-auto-approve-new-wifi-network.md.
 */
object WirelessDebuggingApproval {

    private const val TAG = "CV:WdApproval"
    private const val CMD = "/system/bin/cmd"
    private const val TIMEOUT_SEC = 10L

    /** What Android reports when it will not say which network this is. */
    private const val UNKNOWN_SSID = "<unknown ssid>"
    private const val PLACEHOLDER_BSSID = "02:00:00:00:00:00"

    /** `WifiInfo.toString()`: `SSID: "name", BSSID: aa:bb:cc:dd:ee:ff, MAC: …`. The SSID keeps its quotes. */
    private val WIFI_INFO = Regex("""WifiInfo: SSID: (.*?), BSSID: ([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5})""")

    /**
     * @param alwaysAllow also add the network to Android's trusted list, as ticking "Always allow" does.
     *   False approves this visit only.
     */
    fun approveCurrentNetwork(alwaysAllow: Boolean): NetworkApproval {
        val identity = parseWifiStatus(runCmdWifiStatus())
        if (identity == null) {
            AppLogger.i(TAG, "No Wi-Fi network to approve")
            return NetworkApproval.NO_WIFI_IDENTITY
        }
        return runCatching { callAllow(identity, alwaysAllow) }
            .onFailure { AppLogger.w(TAG, "allowWirelessDebugging failed: ${it.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(NetworkApproval.FAILED)
    }

    /** The network `cmd wifi status` reports as connected, or null when there is none it will name. */
    fun parseWifiStatus(output: String): WifiIdentity? {
        val match = WIFI_INFO.find(output) ?: return null
        val ssid = match.groupValues[1].trim()
        val bssid = match.groupValues[2].lowercase()
        if (ssid.isEmpty() || ssid == UNKNOWN_SSID || ssid == "\"\"") return null
        if (bssid == PLACEHOLDER_BSSID) return null
        return WifiIdentity(ssid = ssid, bssid = bssid)
    }

    /**
     * The argument list for the shape of `allowWirelessDebugging` this phone has, or null for one we do not
     * know. Android 11-16: `(boolean alwaysAllow, String bssid)`. Android 17: `(boolean, String bssid, String ssid)`.
     */
    fun argumentsFor(parameterCount: Int, identity: WifiIdentity, alwaysAllow: Boolean): Array<Any>? =
        when (parameterCount) {
            2 -> arrayOf(alwaysAllow, identity.bssid)
            3 -> arrayOf(alwaysAllow, identity.bssid, identity.ssid)
            else -> null
        }

    private fun callAllow(identity: WifiIdentity, alwaysAllow: Boolean): NetworkApproval {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "adb")
        if (binder == null) {
            AppLogger.w(TAG, "No adb system service")
            return NetworkApproval.UNSUPPORTED
        }
        val adbManager = Class.forName("android.debug.IAdbManager\$Stub")
            .getMethod("asInterface", Class.forName("android.os.IBinder"))
            .invoke(null, binder) ?: return NetworkApproval.UNSUPPORTED
        val method = adbManager.javaClass.methods
            .filter { it.name == "allowWirelessDebugging" }
            .firstOrNull { argumentsFor(it.parameterCount, identity, alwaysAllow) != null }
        if (method == null) {
            AppLogger.w(TAG, "allowWirelessDebugging has no shape we know")
            return NetworkApproval.UNSUPPORTED
        }
        method.invoke(adbManager, *argumentsFor(method.parameterCount, identity, alwaysAllow)!!)
        // The network is named in the daemon's own log only, never sent anywhere.
        AppLogger.i(TAG, "Approved Wi-Fi ${identity.ssid} for Wireless debugging (alwaysAllow=$alwaysAllow)")
        return NetworkApproval.APPROVED
    }

    private fun runCmdWifiStatus(): String = runCatching {
        val process = ProcessBuilder(CMD, "wifi", "status").redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        if (!process.waitFor(TIMEOUT_SEC, TimeUnit.SECONDS)) process.destroyForcibly()
        output
    }.getOrElse {
        AppLogger.w(TAG, "'cmd wifi status' could not run: ${it.message}")
        ""
    }
}
