/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.services.recording

import com.baba.callvault.data.PrivilegedMode

/**
 * When [DaemonKeepAliveService] runs, and what it may do.
 *
 * It has two jobs: keep OUR daemon alive (standalone only — over our own ADB), and host
 * [VoipCallDetector]. Shizuku keeps its own user service alive, so in Shizuku mode only the second job
 * is left, and only when app calls are recorded at all. One rule, read by every place that starts or
 * stops the service, so they cannot disagree.
 */
object KeepAlivePolicy {

    fun shouldRun(mode: PrivilegedMode, voipEnabled: Boolean): Boolean = !mode.needsShizuku || voipEnabled

    /**
     * Whether recovery may touch our embedded ADB — Wireless debugging, adbd, the loopback. Never in
     * Shizuku mode: its owner chose Shizuku so that none of that would happen, and a keep-alive that
     * relaunched our ADB daemon there is how a wrong host once served Shizuku mode on the OP9.
     */
    fun mayUseAdb(mode: PrivilegedMode): Boolean = !mode.needsShizuku
}
