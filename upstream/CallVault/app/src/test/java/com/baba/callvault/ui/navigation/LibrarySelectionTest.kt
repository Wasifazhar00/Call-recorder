/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A multi-selection that cannot leak between sections.
 *
 * Worth pinning because the failure is silent and the next tap is a delete: the selected names must
 * remain scoped to the section where the selection was made.
 */
class LibrarySelectionTest {

    @Test
    fun nothing_is_selected_to_begin_with() {
        assertTrue(LibrarySelection.EMPTY.names.isEmpty())
        assertNull(LibrarySelection.EMPTY.section)
        assertTrue(LibrarySelection.EMPTY.on(HomeSection.Recordings).isEmpty())
    }

    @Test
    fun selection_mode_is_closed_to_begin_with() {
        assertFalse(LibrarySelection.EMPTY.active)
        assertFalse(LibrarySelection.EMPTY.activeOn(HomeSection.Recordings))
    }

    @Test
    fun a_row_picked_on_recordings_is_selected_there() {
        val selection = LibrarySelection.EMPTY.toggled(HomeSection.Recordings, "a.ogg")
        assertEquals(setOf("a.ogg"), selection.on(HomeSection.Recordings))
    }

    @Test
    fun a_row_picked_on_recordings_is_not_selected_on_hub() {
        // The selection must not carry across sections, even if the same display name appears in both.
        val selection = LibrarySelection.EMPTY.toggled(HomeSection.Recordings, "a.ogg")
        assertTrue(selection.on(HomeSection.Hub).isEmpty())
    }

    @Test
    fun picking_on_another_page_replaces_the_selection_rather_than_adding_to_it() {
        val selection = LibrarySelection.EMPTY
            .toggled(HomeSection.Recordings, "a.ogg")
            .toggled(HomeSection.Recordings, "b.ogg")
            .toggled(HomeSection.Hub, "c.ogg")

        assertEquals(setOf("c.ogg"), selection.on(HomeSection.Hub))
        assertTrue(selection.on(HomeSection.Recordings).isEmpty())
    }

    @Test
    fun tapping_a_selected_row_again_takes_it_out() {
        val selection = LibrarySelection.EMPTY
            .toggled(HomeSection.Recordings, "a.ogg")
            .toggled(HomeSection.Recordings, "b.ogg")
            .toggled(HomeSection.Recordings, "a.ogg")

        assertEquals(setOf("b.ogg"), selection.on(HomeSection.Recordings))
    }

    @Test
    fun deselecting_the_last_row_keeps_selection_mode_open() {
        // The maintainer's rule (2026-10-12): clearing a selection — by un-picking the last row, or
        // by the "Deselect all" row — must NOT drop out of selection mode. The bar stays up at zero
        // picked; only the back arrow leaves (which sets EMPTY). So the section and the active flag
        // survive an empty name set.
        val selection = LibrarySelection.EMPTY
            .toggled(HomeSection.Recordings, "a.ogg")
            .toggled(HomeSection.Recordings, "a.ogg")

        assertTrue(selection.names.isEmpty())
        assertTrue(selection.active)
        assertEquals(HomeSection.Recordings, selection.section)
        assertTrue(selection.activeOn(HomeSection.Recordings))
        // Still gated by page: the other section is not in selection mode.
        assertFalse(selection.activeOn(HomeSection.Hub))
    }

    @Test
    fun select_all_then_deselect_all_stays_in_mode() {
        val all = LibrarySelection.EMPTY.selectedAll(HomeSection.Hub, setOf("a", "b", "c"))
        assertEquals(setOf("a", "b", "c"), all.on(HomeSection.Hub))
        assertTrue(all.activeOn(HomeSection.Hub))

        // Empty names is the "Deselect all" action: cleared, but the mode stays open on the page.
        val cleared = all.selectedAll(HomeSection.Hub, emptySet())
        assertTrue(cleared.names.isEmpty())
        assertTrue(cleared.active)
        assertTrue(cleared.activeOn(HomeSection.Hub))
    }

    @Test
    fun backing_out_is_the_only_thing_that_closes_selection_mode() {
        val selection = LibrarySelection.EMPTY.toggled(HomeSection.Recordings, "a.ogg")
        assertTrue(selection.active)

        // The back arrow / clearSelection sets EMPTY — the one representation of "mode closed".
        assertFalse(LibrarySelection.EMPTY.active)
    }

    @Test
    fun a_name_that_is_in_both_libraries_is_still_only_selected_where_it_was_picked() {
        // The same recording name can appear in more than one section.
        val shared = "20260916_120000.000+0300_Dad.ogg"
        val selection = LibrarySelection.EMPTY.toggled(HomeSection.Hub, shared)

        assertTrue(selection.on(HomeSection.Hub).contains(shared))
        assertFalse(selection.on(HomeSection.Recordings).contains(shared))
        assertFalse(selection.on(HomeSection.Recordings).contains(shared))
    }
}
