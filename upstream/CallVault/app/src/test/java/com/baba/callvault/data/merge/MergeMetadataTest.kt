/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.data.merge

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.baba.callvault.data.recordings.db.MergePartEntry
import com.baba.callvault.data.library.db.RecordingFlagEntry
import com.baba.callvault.data.library.db.RecordingNoteEntry
import com.baba.callvault.data.library.db.RecordingTagEntry
import com.baba.callvault.data.library.db.LibraryDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Merging carries marks, tags, favourites and notes from the parts to the new recording. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MergeMetadataTest {

    private lateinit var context: Context
    private val merged = "20260901_143200.001+0300_out_Dan.m4a"
    private val partA = "20260901_143200.000+0300_out_Dan.m4a"
    private val partB = "20260901_144000.000+0300_in_Dan.m4a"
    private val parts = listOf(
        MergePartEntry(merged, 1, partA, 0, 236, 0L, 5_000_000L, 1L, 21_333L, 0L),
        MergePartEntry(merged, 2, partB, 236, 142, 5_000_000L, 3_000_000L, 2L, 21_333L, 0L),
    )

    @Before
    fun setUp() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        listOf(merged, partA, partB).forEach { MergeMetadata.onUnMerge(context, it) }
    }

    private fun db() = LibraryDatabase.get(context)

    @Test
    fun marks_shift_and_a_new_one_lands_on_the_seam() = runBlocking {
        db().flagDao().upsertAll(listOf(RecordingFlagEntry(partA, 2_000L)))
        db().flagDao().upsertAll(listOf(RecordingFlagEntry(partB, 500L)))

        MergeMetadata.onMerge(context, merged, parts)

        assertEquals(listOf(2_000L, 5_000L, 5_500L), db().flagDao().flagsFor(merged))
    }

    @Test
    fun tags_are_unioned_and_the_star_carries_if_either_part_had_it() = runBlocking {
        db().tagDao().add(RecordingTagEntry(partA, "work"))
        db().tagDao().add(RecordingTagEntry(partB, "work"))
        db().tagDao().add(RecordingTagEntry(partB, "urgent"))
        db().favouriteDao().add(partB)

        MergeMetadata.onMerge(context, merged, parts)

        assertEquals(listOf("urgent", "work"), db().tagDao().tagsFor(merged))
        assertTrue(db().favouriteDao().isFavourite(merged))
    }

    @Test
    fun notes_are_joined_in_part_order() = runBlocking {
        db().noteDao().upsertNote(RecordingNoteEntry(partA, "first note", 1L))
        db().noteDao().upsertNote(RecordingNoteEntry(partB, "second note", 2L))

        MergeMetadata.onMerge(context, merged, parts)

        assertEquals("first note\n\nsecond note", db().noteDao().note(merged)?.text)
    }

    @Test
    fun un_merging_removes_merged_metadata_and_leaves_each_part_untouched() = runBlocking {
        db().tagDao().add(RecordingTagEntry(partA, "work"))
        db().favouriteDao().add(partA)
        db().noteDao().upsertNote(RecordingNoteEntry(partA, "ring back", 1L))
        db().flagDao().upsertAll(listOf(RecordingFlagEntry(partA, 2_000L)))
        MergeMetadata.onMerge(context, merged, parts)

        MergeMetadata.onUnMerge(context, merged)

        assertTrue(db().tagDao().tagsFor(merged).isEmpty())
        assertFalse(db().favouriteDao().isFavourite(merged))
        assertTrue(db().flagDao().flagsFor(merged).isEmpty())
        assertEquals(listOf("work"), db().tagDao().tagsFor(partA))
        assertTrue(db().favouriteDao().isFavourite(partA))
        assertEquals(listOf(2_000L), db().flagDao().flagsFor(partA))
        assertEquals("ring back", db().noteDao().note(partA)?.text)
    }
}
