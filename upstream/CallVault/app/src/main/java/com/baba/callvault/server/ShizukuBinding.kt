/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.server

/**
 * Whether [ShizukuBackend]'s existing binding still means anything.
 *
 * Shizuku does not reliably call `onServiceDisconnected` when the recorder process dies, so "we have a
 * connection" outlived the recorder: measured on the OP9 2026-10-19, after the process was killed every
 * rebind answered "Already bound" and waited out its timeout for a binder that never came. A binding is
 * stale when the recorder it delivered is dead, or when it never delivered one at all in good time.
 */
object ShizukuBinding {

    /** Longer than Shizuku takes to start a cold user service, with margin. */
    const val ANSWER_TIMEOUT_MS = 15_000L

    /**
     * @param deliveredAlive null while no binder has been delivered; otherwise whether it is alive.
     * @param msSinceBind how long ago this binding was asked for.
     */
    fun isStale(deliveredAlive: Boolean?, msSinceBind: Long): Boolean = when (deliveredAlive) {
        true -> false
        false -> true
        null -> msSinceBind > ANSWER_TIMEOUT_MS
    }
}
