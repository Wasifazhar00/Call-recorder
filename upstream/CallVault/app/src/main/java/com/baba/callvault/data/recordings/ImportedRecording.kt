package com.baba.callvault.data.recordings

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stable filename metadata for audio files imported into the recordings folder.
 *
 * The marker at the third underscore-delimited token distinguishes imported files from recordings
 * made by the app. It survives a catalog rebuild because it is stored in the file name itself.
 */
object ImportedRecording {
    const val TOKEN = "import"
    private const val MARKER_SLOT = 2
    private const val STAMP_PATTERN = "yyyyMMdd_HHmmss.SSSZ"
    private const val MAX_LABEL_LENGTH = 40
    private val forbiddenInLabel = Regex("""[/\\:*?"<>|_]""")
    private val sourceExtension = Regex("""\\.[A-Za-z0-9]{1,5}$""")
    private val stampHead = Regex("""\\d{8}""")

    fun nameFor(importedAtMillis: Long, label: String?, extension: String): String {
        val stamp = SimpleDateFormat(STAMP_PATTERN, Locale.CANADA).format(Date(importedAtMillis))
        val suffix = labelFor(label)?.let { "_$it" }.orEmpty()
        val ext = when {
            extension.isBlank() -> ""
            extension.startsWith('.') -> extension
            else -> ".$extension"
        }
        return "${stamp}_$TOKEN$suffix$ext"
    }

    fun labelFor(raw: String?): String? {
        val source = raw?.trim().orEmpty()
        if (source.isEmpty()) return null
        return source
            .replace(sourceExtension, "")
            .replace(forbiddenInLabel, "")
            .trim()
            .trimStart('.')
            .trim()
            .take(MAX_LABEL_LENGTH)
            .trim()
            .ifBlank { null }
    }

    fun isImported(displayName: String): Boolean {
        val parts = tokensOf(displayName)
        return parts.size > MARKER_SLOT &&
            stampHead.matches(parts[0]) &&
            parts[MARKER_SLOT] == TOKEN
    }

    fun stampTokenOf(displayName: String): String? {
        if (!isImported(displayName)) return null
        return tokensOf(displayName).take(MARKER_SLOT).joinToString("_")
    }

    fun labelOf(displayName: String): String? {
        if (!isImported(displayName)) return null
        return tokensOf(displayName).drop(MARKER_SLOT + 1).joinToString("_").ifBlank { null }
    }

    private fun tokensOf(displayName: String): List<String> {
        val base = if (displayName.lastIndexOf('.') > displayName.lastIndexOf('_')) {
            displayName.substringBeforeLast('.')
        } else {
            displayName
        }
        return base.split('_')
    }
}
