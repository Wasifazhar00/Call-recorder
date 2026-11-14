package com.baba.callvault.server

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorderCallerPolicyTest {

    private val ownUid = 10249

    @Test
    fun `the shell daemon, the system, root and the app itself are trusted`() {
        assertTrue(RecorderCallerPolicy.isTrusted(2000, ownUid))
        assertTrue(RecorderCallerPolicy.isTrusted(1000, ownUid))
        assertTrue(RecorderCallerPolicy.isTrusted(0, ownUid))
        assertTrue(RecorderCallerPolicy.isTrusted(ownUid, ownUid))
    }

    @Test
    fun `any other app is refused`() {
        assertFalse(RecorderCallerPolicy.isTrusted(10250, ownUid))
        assertFalse(RecorderCallerPolicy.isTrusted(10123, ownUid))
        assertFalse(RecorderCallerPolicy.isTrusted(1001, ownUid)) // phone, not system
    }
}
