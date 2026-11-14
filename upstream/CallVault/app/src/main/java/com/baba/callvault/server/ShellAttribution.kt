/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.server

import com.baba.callvault.utils.AppLogger

/**
 * Makes a Shizuku-hosted recorder present itself as the shell, as our own `app_process` daemon does.
 *
 * Shizuku loads OUR Application into the process it starts and stores it in
 * `ActivityThread.mInitialApplication`. Every AudioRecord then carries uid 2000 + `com.baba.callvault`,
 * a pair no package owns: AudioFlinger warns, and AppOps hard-denies at start —
 * `startInput permission denied: recording not allowed for attribution source … packageName: com.baba.callvault`
 * (measured on the OP9, 2026-10-19). With the field cleared, `AttributionSource.myAttributionSource()`
 * falls back to the uid's own package — the shell — exactly as in a bare `app_process`.
 *
 * The same fix FMZlinkR (PR #5) found on-device. Process-global and one-way, which is the point: every
 * record this process ever opens must carry the shell's identity.
 */
object ShellAttribution {

    private const val TAG = "CV:ShellAttribution"

    /** @return true when the process no longer presents an app identity. */
    fun detachFromApp(): Boolean = runCatching {
        val atCls = Class.forName("android.app.ActivityThread")
        val thread = atCls.getMethod("currentActivityThread").invoke(null)
            ?: return true // no ActivityThread at all: already a bare process
        val field = atCls.getDeclaredField("mInitialApplication").apply { isAccessible = true }
        val before = field.get(thread)?.javaClass?.name
        field.set(thread, null)
        AppLogger.i(TAG, "Detached from the app identity (was ${before ?: "none"})")
        true
    }.onFailure { AppLogger.e(TAG, "Could not detach from the app identity: ${it.message}", it) }
        .getOrDefault(false)
}
