package com.baba.callvault.server

import android.os.Process

/**
 * Who may call [RecorderBinderProvider]. The provider has to be exported so the shell-uid daemon can
 * reach it, which means any app can call it too. Only our own recorder hosts may hand us anything:
 * the daemon (shell), the system, root, or this app itself. Any other app is refused.
 *
 * Without this check on `sendBinder`, any app could hand us its own recorder and receive the output
 * file of every new recording.
 */
object RecorderCallerPolicy {

    /** uid 0. A root daemon could already do anything, so trusting it here costs nothing. */
    private const val ROOT_UID = 0

    fun isTrusted(callingUid: Int, ownUid: Int = Process.myUid()): Boolean =
        callingUid == Process.SHELL_UID ||
            callingUid == Process.SYSTEM_UID ||
            callingUid == ROOT_UID ||
            callingUid == ownUid
}
