/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */


package com.baba.callvault.transcription.model

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * A model the user picked from storage is copied to an `.import` temp file first, and only renamed
 * into place once its digest matches — the same gate a download passes through.
 */
class ModelImportRepositoryTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val bytes = byteArrayOf(1, 2, 3)

    private val model = object : DownloadableModel {
        override val id = "import-test"
        override val fileName = "import-test.bin"
        override val url = "https://example.invalid/import-test.bin"
        override val sha256 = sha256Of(bytes)
        override val sizeBytes = 3L
    }

    private fun sha256Of(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    @Test
    fun `finalizeImport installs a matching file and removes the temp`() {
        val imp = ModelRepository.importFileFor(folder.root, model).apply { writeBytes(bytes) }

        val ok = ModelRepository.finalizeImport(folder.root, model)

        assertTrue(ok)
        assertEquals(bytes.toList(), File(folder.root, model.fileName).readBytes().toList())
        assertFalse(imp.exists())
    }

    @Test
    fun `finalizeImport deletes a mismatching temp and installs nothing`() {
        val imp = ModelRepository.importFileFor(folder.root, model).apply { writeBytes(bytes) }

        val ok = ModelRepository.finalizeImport(folder.root, model, expectedSha256 = "de".repeat(32))

        assertFalse(ok)
        assertFalse(imp.exists())
        assertFalse(File(folder.root, model.fileName).exists())
    }

    @Test
    fun `finalizeImport removes the paused download only on success`() {
        val part = ModelRepository.partFileFor(folder.root, model).apply { writeBytes(byteArrayOf(9, 9)) }
        ModelRepository.importFileFor(folder.root, model).writeBytes(bytes)

        ModelRepository.finalizeImport(folder.root, model, expectedSha256 = "de".repeat(32))
        assertEquals("a failed import must leave the paused download", 2L, part.length())

        ModelRepository.importFileFor(folder.root, model).writeBytes(bytes)
        assertTrue(ModelRepository.finalizeImport(folder.root, model))
        assertFalse(part.exists())
    }
}
