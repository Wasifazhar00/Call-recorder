package com.wasifazhar.callrecorder

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

object AudioFileTools {
    fun durationMs(file: File): Long = try {
        if (file.extension.equals("wav", true)) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(24); val rate = readLeInt(raf)
                raf.seek(28); val byteRate = readLeInt(raf)
                raf.seek(40); val data = readLeInt(raf)
                if (byteRate > 0) data.toLong() * 1000L / byteRate else 0L
            }
        } else {
            val ex = MediaExtractor()
            try {
                ex.setDataSource(file.absolutePath)
                (0 until ex.trackCount).mapNotNull { ex.getTrackFormat(it).let { f -> if (f.containsKey(MediaFormat.KEY_DURATION)) f.getLong(MediaFormat.KEY_DURATION) else null } }.maxOrNull()?.div(1000L) ?: 0L
            } finally { ex.release() }
        }
    } catch (_: Exception) { 0L }

    fun trim(source: File, target: File, startMs: Long, endMs: Long) {
        require(startMs >= 0 && endMs > startMs)
        if (source.extension.equals("wav", true)) trimWav(source, target, startMs, endMs) else trimM4a(source, target, startMs, endMs)
    }

    private fun trimWav(source: File, target: File, startMs: Long, endMs: Long) {
        RandomAccessFile(source, "r").use { input ->
            val header = ByteArray(44); input.readFully(header)
            val view = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
            val byteRate = view.getInt(28)
            val dataSize = view.getInt(40).coerceAtLeast(0)
            val start = ((startMs * byteRate / 1000L) / 2L * 2L).coerceAtMost(dataSize.toLong())
            val end = ((endMs * byteRate / 1000L) / 2L * 2L).coerceAtMost(dataSize.toLong())
            require(end > start)
            val count = (end - start).toInt()
            view.putInt(4, 36 + count); view.putInt(40, count)
            RandomAccessFile(target, "rw").use { out ->
                out.setLength(0); out.write(header); input.seek(44 + start)
                val buffer = ByteArray(64 * 1024); var remaining = count
                while (remaining > 0) { val n = input.read(buffer, 0, minOf(buffer.size, remaining)); if (n < 0) break; out.write(buffer, 0, n); remaining -= n }
                require(remaining == 0)
            }
        }
    }

    private fun trimM4a(source: File, target: File, startMs: Long, endMs: Long) {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        try {
            extractor.setDataSource(source.absolutePath)
            val audioTrack = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: error("No audio track found")
            extractor.selectTrack(audioTrack)
            val format = extractor.getTrackFormat(audioTrack)
            muxer = MediaMuxer(target.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val outTrack = muxer.addTrack(format)
            muxer.start()
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            val buffer = ByteBuffer.allocate(1024 * 1024)
            val info = android.media.MediaCodec.BufferInfo()
            while (true) {
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                val time = extractor.sampleTime
                if (time < 0 || time >= endMs * 1000L) break
                info.set(0, size, (time - startMs * 1000L).coerceAtLeast(0L), extractor.sampleFlags)
                muxer.writeSampleData(outTrack, buffer, info)
                if (!extractor.advance()) break
            }
            muxer.stop()
        } finally {
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }

    private fun readLeInt(file: RandomAccessFile): Int {
        val b = ByteArray(4); file.readFully(b)
        return ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).int
    }
}
