/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.screens

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pairing hints tell the user which button to tap, and that button is labelled `permission_adb_pair`.
 * They used to say "Tap Authorize" in every language after the button became "Pair". Checked per locale,
 * because each translation spells the button its own way.
 */
class AdbHintNamesButtonTest {

    private val resDir = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    private val hintKeys = listOf("permission_adb_hint", "permission_adb_hint_wd_on")

    private fun stringsIn(dir: File): Map<String, String> {
        val pattern = Regex("""<string name="([a-z0-9_]+)"[^>]*>(.*?)</string>""")
        return dir.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty()
            .flatMap { pattern.findAll(it.readText()).map { m -> m.groupValues[1] to m.groupValues[2] }.toList() }
            .toMap()
    }

    @Test
    fun `every pairing hint names the Pair button in its own language`() {
        val locales = resDir.listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
        val offenders = mutableListOf<String>()
        var checked = 0
        for (dir in locales.sortedBy { it.name }) {
            val strings = stringsIn(dir)
            val button = strings["permission_adb_pair"] ?: continue
            for (key in hintKeys) {
                val hint = strings[key] ?: continue
                checked++
                if (!hint.contains(button)) offenders += "${dir.name}/$key does not contain \"$button\": $hint"
            }
        }
        assertTrue("no pairing hints found under $resDir", checked > 0)
        assertTrue("Hints that name another button:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
