/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.server

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WirelessDebuggingApprovalTest {

    /** `cmd wifi status` on the OP9 (OxygenOS 14), 2026-10-21, trimmed. */
    private val op9 = """
        Wifi is enabled
        Wifi scanning is always available
        ==== Primary ClientModeManager instance ====
        Wifi is connected to "Baba-5G"
        WifiInfo: SSID: "Baba-5G", BSSID: b0:a7:b9:4c:70:29, MAC: b2:a0:07:e6:23:2f, IP: /192.168.1.199, Security type: 2, Supplicant state: COMPLETED
        successfulTxPackets: 628016
    """.trimIndent()

    @Test
    fun `reads the network name with its quotes, as Android compares it, and the access point`() {
        assertEquals(
            WifiIdentity(ssid = "\"Baba-5G\"", bssid = "b0:a7:b9:4c:70:29"),
            WirelessDebuggingApproval.parseWifiStatus(op9),
        )
    }

    @Test
    fun `a name containing a comma survives`() {
        val out = """WifiInfo: SSID: "Cafe, upstairs", BSSID: AA:BB:CC:DD:EE:FF, MAC: 02:00:00:00:00:00"""
        assertEquals(
            WifiIdentity(ssid = "\"Cafe, upstairs\"", bssid = "aa:bb:cc:dd:ee:ff"),
            WirelessDebuggingApproval.parseWifiStatus(out),
        )
    }

    @Test
    fun `no network when Wi-Fi is off or the identity is hidden`() {
        assertNull(WirelessDebuggingApproval.parseWifiStatus("Wifi is disabled\n"))
        assertNull(WirelessDebuggingApproval.parseWifiStatus(""))
        assertNull(
            WirelessDebuggingApproval.parseWifiStatus(
                "WifiInfo: SSID: <unknown ssid>, BSSID: b0:a7:b9:4c:70:29, MAC: x"
            )
        )
        assertNull(
            WirelessDebuggingApproval.parseWifiStatus(
                "WifiInfo: SSID: \"Home\", BSSID: 02:00:00:00:00:00, MAC: x"
            )
        )
    }

    @Test
    fun `passes the arguments in the order each Android version expects`() {
        val id = WifiIdentity(ssid = "\"Home\"", bssid = "aa:bb:cc:dd:ee:ff")
        // Android 11-16: (alwaysAllow, bssid).
        assertArrayEquals(arrayOf<Any>(false, "aa:bb:cc:dd:ee:ff"), WirelessDebuggingApproval.argumentsFor(2, id, false))
        // Android 17: (alwaysAllow, bssid, ssid).
        assertArrayEquals(
            arrayOf<Any>(true, "aa:bb:cc:dd:ee:ff", "\"Home\""),
            WirelessDebuggingApproval.argumentsFor(3, id, true),
        )
        assertNull(WirelessDebuggingApproval.argumentsFor(4, id, true))
    }
}
