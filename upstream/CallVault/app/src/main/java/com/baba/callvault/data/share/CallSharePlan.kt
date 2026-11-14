package com.baba.callvault.data.share

/** The only data CallVault exports from a call: its audio recording. */
enum class SharePart { RECORDING }

data class ShareableCall(
    val displayName: String,
    val title: String,
    val audioUri: String?,
) {
    fun has(part: SharePart): Boolean = part == SharePart.RECORDING && audioUri != null
}

/** Rules for choosing and sending one or more call recordings. */
object CallSharePlan {
    fun available(calls: List<ShareableCall>): Set<SharePart> =
        if (calls.any { it.has(SharePart.RECORDING) }) setOf(SharePart.RECORDING) else emptySet()

    fun initialTicks(start: SharePart, available: Set<SharePart>): Set<SharePart> =
        if (start in available) setOf(start) else available.firstOrNull()?.let(::setOf) ?: emptySet()

    fun asFiles(ticks: Set<SharePart>): Boolean = SharePart.RECORDING in ticks
}
