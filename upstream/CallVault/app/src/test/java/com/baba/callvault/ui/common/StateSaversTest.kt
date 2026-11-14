/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.common

import android.net.Uri
import com.baba.callvault.data.recordings.DeleteScope
import com.baba.callvault.ui.navigation.HomeSection
import com.baba.callvault.ui.navigation.LibrarySelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Encoding Home's state so it survives a rotation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StateSaversTest {

    @Test
    fun `selection survives a round-trip`() {
        val selection = setOf(Uri.parse("content://recordings/a.ogg"), Uri.parse("content://recordings/b.ogg"))
        assertEquals(selection, decodeUriSet(encodeUriSet(selection)))
    }

    @Test
    fun `an empty selection round-trips to empty, not to null`() {
        assertEquals(emptySet<Uri>(), decodeUriSet(encodeUriSet(emptySet<Uri>())))
    }

    @Test
    fun `a uri containing separators and spaces survives`() {
        val awkward = setOf(
            Uri.parse("content://recordings/20260904 in +1602,609.ogg"),
            Uri.parse("content://recordings/%2Fnested%2Fname.ogg"),
        )
        assertEquals(awkward, decodeUriSet(encodeUriSet(awkward)))
    }

    @Test
    fun `restoring does not depend on ordering`() {
        val a = Uri.parse("content://recordings/a.ogg")
        val b = Uri.parse("content://recordings/b.ogg")
        assertEquals(decodeUriSet(encodeUriSet(setOf(a, b))), decodeUriSet(encodeUriSet(setOf(b, a))))
    }

    @Test
    fun `every delete scope round-trips`() {
        DeleteScope.entries.forEach { scope ->
            assertEquals(scope, decodeDeleteScope(encodeDeleteScope(scope)))
        }
    }

    @Test
    fun `an unknown stored delete scope falls back to the safe default`() {
        assertEquals(DeleteScope.BOTH, decodeDeleteScope("NO_SUCH_SCOPE"))
    }

    @Test
    fun `encoded forms are plain strings so the framework can bundle them`() {
        assertTrue(encodeUriSet(setOf(Uri.parse("content://x"))).all { it is String })
        assertTrue(encodeLibrarySelection(LibrarySelection.EMPTY).all { it is String })
    }

    @Test
    fun `a library selection round-trips with the page it was picked on`() {
        val selection = LibrarySelection(
            section = HomeSection.Recordings,
            names = setOf("a.ogg", "b with spaces + plus | pipe.m4a"),
        )
        assertEquals(selection, decodeLibrarySelection(encodeLibrarySelection(selection)))
    }

    @Test
    fun `an empty library selection round-trips to nothing selected`() {
        assertEquals(LibrarySelection.EMPTY, decodeLibrarySelection(encodeLibrarySelection(LibrarySelection.EMPTY)))
    }

    @Test
    fun `a stored section key that no longer exists means nothing is selected`() {
        assertEquals(LibrarySelection.EMPTY, decodeLibrarySelection(listOf("1", "no-such-section", "a.ogg")))
    }

    @Test
    fun `an open selection with nothing picked round-trips as still open`() {
        val selection = LibrarySelection(section = HomeSection.Recordings, names = emptySet(), active = true)
        assertEquals(selection, decodeLibrarySelection(encodeLibrarySelection(selection)))
    }
}
