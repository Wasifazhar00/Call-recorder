package com.baba.callvault.data.audio

/** Bounded time windows used when drawing long recording waveforms. */
internal object AudioChunkPlan {
    data class Chunk(val startMs: Long, val endMs: Long)
    fun plan(totalMs: Long, targetMs: Long = 5 * 60 * 1000L): List<Chunk> {
        if (totalMs <= 0L || totalMs <= targetMs) return listOf(Chunk(0L, totalMs))
        val result = mutableListOf<Chunk>()
        var start = 0L
        while (start < totalMs) {
            var end = minOf(start + targetMs, totalMs)
            if (totalMs - end in 1 until 45_000L) end = totalMs
            result += Chunk(start, end)
            start = end
        }
        return result
    }
}
