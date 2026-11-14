/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No screen may cache the privileged mode with an unkeyed `remember {}`.
 *
 * The mode changes while Settings is open (the mode switch lives on it). Three rows remembered it once,
 * so after a switch they kept the old mode's greyed-out state until Settings was reopened — measured on
 * the OP9 2026-10-19, with "record away from Wi-Fi" looking available in Shizuku mode. Rows read
 * `currentPrivilegedMode()` / `capabilityAvailable()` instead. A keyed `remember(x)` is allowed: it is
 * re-read when its key changes.
 */
class ModeAwareUiTest {

    /** The setup wizard cannot change mode while it is open, so its one-time read is correct. */
    private val allowed = setOf("WizardScreen.kt")

    @Test
    fun `no screen caches the privileged mode once`() {
        val uiDir = listOf("src/main/java/com/baba/callvault/ui", "app/src/main/java/com/baba/callvault/ui")
            .map(::File).first { it.isDirectory }
        val unkeyed = Regex("""remember\s*\{[^}]*(getPrivilegedMode|needsShizuku)""")
        val offenders = uiDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name !in allowed }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, line -> if (unkeyed.containsMatchIn(line)) "${f.name}:${i + 1}" else null } }
            .toList()
        assertTrue("Read the mode with currentPrivilegedMode() instead: $offenders", offenders.isEmpty())
    }
}
