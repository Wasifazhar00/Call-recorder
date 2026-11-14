/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.transcription.model

import com.baba.callvault.summary.SummaryModel
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.CancellationException

/**
 * Pure-JVM core of importing a model file the user already has (GitHub #58).
 *
 * Kept free of Android so the worker that calls it stays thin and this can be unit-tested. The
 * size check exists to give a useful answer cheaply: a file that is a different catalogue model
 * is named rather than rejected as "corrupt"; the digest, not the size, is what finally decides.
 */
object ModelTransfer {

    private const val BUFFER_BYTES = 64 * 1024
    private const val PERCENT = 100L

    sealed interface ImportCheck {
        /** Size equals the target's (or is unknown); the digest decides. */
        data object SizeMatches : ImportCheck

        /** The size belongs to a different catalogue model. */
        data class OtherModel(val model: DownloadableModel) : ImportCheck

        /** The size matches nothing we ship. */
        data object WrongFile : ImportCheck
    }

    data class CopyResult(val bytes: Long, val sha256: String)

    /**
     * The source kept going past the model's size, so it is not that model's file. Its own type so
     * the import can say "wrong file" rather than "couldn't read it": the file read fine, it is just
     * the wrong one, and picking it again would not help.
     */
    class LongerThanExpectedException(expectedSize: Long) :
        IOException("longer than the expected $expectedSize bytes")

    /** Every model the app can fetch. */
    fun catalogue(): List<DownloadableModel> = TranscriptionModel.entries + SummaryModel.entries

    /** A negative [pickedSize] means the provider reported none, so the digest is left to decide. */
    fun checkSize(pickedSize: Long, target: DownloadableModel, catalogue: List<DownloadableModel>): ImportCheck {
        if (pickedSize < 0 || pickedSize == target.sizeBytes) return ImportCheck.SizeMatches
        val other = catalogue.firstOrNull { it.id != target.id && it.sizeBytes == pickedSize }
        return if (other != null) ImportCheck.OtherModel(other) else ImportCheck.WrongFile
    }

    /**
     * Copies [input] to [output] while hashing, in one pass. Throws [LongerThanExpectedException] as
     * soon as more than [expectedSize] bytes arrive, and [CancellationException] when [isStopped] turns true.
     */
    fun copyAndHash(
        input: InputStream,
        output: OutputStream,
        expectedSize: Long,
        onProgress: (percent: Int) -> Unit,
        isStopped: () -> Boolean = { false },
    ): CopyResult {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0L
        var lastPercent = -1
        while (true) {
            if (isStopped()) throw CancellationException("import stopped")
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > expectedSize) throw LongerThanExpectedException(expectedSize)
            digest.update(buffer, 0, read)
            output.write(buffer, 0, read)
            val percent = if (expectedSize > 0) (total * PERCENT / expectedSize).toInt() else 0
            if (percent != lastPercent) {
                lastPercent = percent
                onProgress(percent)
            }
        }
        return CopyResult(total, digest.toHex())
    }

    /** Digest of [input] without copying it, to confirm an [ImportCheck.OtherModel] hit. */
    fun hashOnly(input: InputStream, isStopped: () -> Boolean = { false }): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_BYTES)
        while (true) {
            if (isStopped()) throw CancellationException("hash stopped")
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.toHex()
    }

    private fun MessageDigest.toHex(): String = digest().joinToString("") { "%02x".format(it) }
}
