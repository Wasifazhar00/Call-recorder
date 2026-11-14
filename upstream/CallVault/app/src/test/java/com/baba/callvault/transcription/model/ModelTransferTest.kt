/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.transcription.model

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelTransferTest {

    private fun fake(modelId: String, size: Long) = object : DownloadableModel {
        override val id = modelId
        override val fileName = "$modelId.bin"
        override val url = "https://example.invalid/$modelId.bin"
        override val sha256 = "00"
        override val sizeBytes = size
    }

    private val a = fake("a", 3)
    private val b = fake("b", 5)
    private val bytes = Random(1).nextBytes(70_000)

    private fun sha256Of(data: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(data).joinToString("") { "%02x".format(it) }

    @Test
    fun `the target's own size passes the size check`() {
        assertEquals(ModelTransfer.ImportCheck.SizeMatches, ModelTransfer.checkSize(3, a, listOf(a, b)))
    }

    @Test
    fun `a different catalogue model's size is named, not installed`() {
        assertEquals(ModelTransfer.ImportCheck.OtherModel(b), ModelTransfer.checkSize(5, a, listOf(a, b)))
    }

    @Test
    fun `any other size is the wrong file`() {
        assertEquals(ModelTransfer.ImportCheck.WrongFile, ModelTransfer.checkSize(4, a, listOf(a, b)))
    }

    @Test
    fun `an unknown size is left to the digest`() {
        assertEquals(ModelTransfer.ImportCheck.SizeMatches, ModelTransfer.checkSize(-1, a, listOf(a, b)))
    }

    @Test
    fun `copyAndHash copies every byte and returns the digest`() {
        val out = ByteArrayOutputStream()
        val result = ModelTransfer.copyAndHash(ByteArrayInputStream(bytes), out, bytes.size.toLong(), {})
        assertArrayEquals(bytes, out.toByteArray())
        assertEquals(bytes.size.toLong(), result.bytes)
        assertEquals(sha256Of(bytes), result.sha256)
    }

    @Test(expected = ModelTransfer.LongerThanExpectedException::class)
    fun `copyAndHash refuses a source longer than expected`() {
        ModelTransfer.copyAndHash(ByteArrayInputStream(bytes), ByteArrayOutputStream(), bytes.size - 1L, {})
    }

    @Test
    fun `copyAndHash reports rising whole percentages ending at 100`() {
        val seen = mutableListOf<Int>()
        ModelTransfer.copyAndHash(ByteArrayInputStream(bytes), ByteArrayOutputStream(), bytes.size.toLong(), { seen += it })
        assertTrue(seen.zipWithNext().all { (x, y) -> y > x })
        assertEquals(100, seen.last())
    }

    @Test(expected = CancellationException::class)
    fun `copyAndHash stops when asked`() {
        ModelTransfer.copyAndHash(ByteArrayInputStream(bytes), ByteArrayOutputStream(), bytes.size.toLong(), {}, isStopped = { true })
    }

    @Test
    fun `hashOnly returns the digest`() {
        assertEquals(sha256Of(bytes), ModelTransfer.hashOnly(ByteArrayInputStream(bytes)))
    }
}
