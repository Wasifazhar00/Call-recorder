/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.data.transcripts.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * What a recording was last called — its display title and date line — cached while the recordings list loads.
 *
 * The catalog knows a recording by file name; its contact-resolved title and date come from the recordings scan.
 * This table caches the last result until that scan completes.
 *
 * It lives beside other user-authored metadata, not in the rebuildable recording catalog.
 *
 * @param label    the row's title, un-isolated: contact name, else the number, else the file name.
 * @param subtitle the date line under it, or null for a recording with no parsable date.
 */
@Entity(tableName = "recording_labels")
data class RecordingLabelEntry(
    @PrimaryKey val displayName: String,
    val label: String,
    val subtitle: String?,
)

@Dao
interface RecordingLabelDao {

    @Query("SELECT * FROM recording_labels")
    fun observeAll(): Flow<List<RecordingLabelEntry>>

    @Query("SELECT * FROM recording_labels")
    suspend fun all(): List<RecordingLabelEntry>

    @Upsert
    suspend fun upsertAll(entries: List<RecordingLabelEntry>)

    @Query("DELETE FROM recording_labels WHERE displayName = :displayName")
    suspend fun deleteFor(displayName: String)
}
