/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.server

import android.annotation.SuppressLint
import android.media.AudioRecord
import com.baba.callvault.utils.AppLogger

/**
 * The one way the recorder host opens an [AudioRecord] from a capture source.
 *
 * vivo's ROM modified `AudioRecord`'s public constructor so that it crashes in a process with no Context — every
 * recorder-host process. 2.4.3 fixed only the app-call far-party sink; the report of 2026-09-28 15:30 (iQOO
 * V2507A, 2.4.3) showed the other four constructors failing the same way — the app-call microphone (so app calls
 * still failed), the direct capture and Resilient recording (so phone calls survived only on the scrcpy fallback).
 * Each had swallowed the crash with `runCatching { … }.getOrNull()`, so the log never said why.
 *
 * Tries the public constructor first — unchanged on every other phone — and only on the vendor crash
 * ([VendorConstructorCrash]) builds the same record through [BypassedAudioRecord]. Any other failure is logged
 * with its cause and returns null.
 */
internal object HostAudioRecord {

    private const val TAG = "CV:HostAudioRecord"

    /**
     * @param what a few words for the log ("VoIP mic", "direct voice-call"…).
     * @return the record — the caller still checks [AudioRecord.getState] — or null when it could not be built.
     */
    @SuppressLint("MissingPermission")
    fun open(what: String, source: Int, sampleRate: Int, channelMask: Int, encoding: Int, bufferBytes: Int): AudioRecord? {
        val normal = runCatching { AudioRecord(source, sampleRate, channelMask, encoding, bufferBytes) }
        normal.getOrNull()?.let { return it }
        val error = normal.exceptionOrNull()!!
        if (!VendorConstructorCrash.matches(error)) {
            AppLogger.w(TAG, "$what: AudioRecord(source=$source) threw: ${error.javaClass.simpleName}: ${error.message}")
            return null
        }
        AppLogger.w(TAG, "$what: the ROM's AudioRecord constructor crashed without a Context " +
            "(${VendorConstructorCrash.rootCause(error).message}); building it without that constructor")
        return runCatching {
            BypassedAudioRecord.create(
                capturePreset = source,
                tags = emptyList(),
                sampleRate = sampleRate,
                channelMask = channelMask,
                channelCount = Integer.bitCount(channelMask),
                encoding = encoding,
                bufferSizeInBytes = bufferBytes,
            )
        }.onSuccess { AppLogger.i(TAG, "$what: built without the vendor constructor (state=${it.state})") }
            .onFailure { AppLogger.e(TAG, "$what: building without the vendor constructor failed too: ${it.message}", it) }
            .getOrNull()
    }

    /**
     * Starts [record]. Only on the crash Honor put into `startRecording()` ([VendorStartCrash], issue #53) does it
     * give the process a shell Application ([ShellApplication]) and start the same record again — the crash
     * comes before the real start, so the record is untouched. Any other failure propagates as before.
     */
    fun start(what: String, record: AudioRecord) =
        startWithRepair(what, start = { record.startRecording() }, repair = ShellApplication::install)

    internal fun startWithRepair(what: String, start: () -> Unit, repair: () -> Boolean) {
        val error = runCatching(start).exceptionOrNull() ?: return
        if (!VendorStartCrash.matches(error)) throw error
        AppLogger.w(TAG, "$what: the ROM's startRecording() needs an Application " +
            "(${VendorConstructorCrash.rootCause(error).message}); giving the recorder a shell one")
        if (!repair()) throw error
        start()
        AppLogger.i(TAG, "$what: started after the shell Application was installed")
    }
}
