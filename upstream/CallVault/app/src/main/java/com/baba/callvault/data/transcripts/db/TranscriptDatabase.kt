/*
 * CallVault: FOSS call recording, self-contained over embedded ADB
 *  Copyright (C) 2026-present The CallVault Authors
 *  This software is licensed under the GNU General Public License v3 or later, with additional terms as permitted under Section 7.
 *  The full license text is available in the LICENSE file at the root of this project.
 *  This software is distributed WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package com.baba.callvault.data.transcripts.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * Persistent metadata for recordings.
 *
 * The recordings catalog is a rebuildable cache; notes, tags, favourites, flags and labels are
 * user-authored metadata and must survive its rebuilds. This database therefore has real migrations
 * and no destructive fallback. Earlier versions also stored generated text; the upgrade path below
 * removes those legacy tables while retaining the user's recording metadata.
 */
@Database(
    entities = [
        RecordingNoteEntry::class,
        RecordingWaveformEntry::class,
        RecordingTagEntry::class,
        RecordingFavouriteEntry::class,
        RecordingFlagEntry::class,
        RecordingLabelEntry::class
    ],
    version = 11,
    exportSchema = true
)
abstract class TranscriptDatabase : RoomDatabase() {

    abstract fun noteDao(): RecordingNoteDao
    abstract fun tagDao(): RecordingTagDao
    abstract fun favouriteDao(): RecordingFavouriteDao
    abstract fun flagDao(): RecordingFlagDao
    abstract fun labelDao(): RecordingLabelDao

    companion object {
        // Keep the existing filename so the user's metadata database is upgraded in place.
        private const val DB_NAME = "transcripts.db"

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recording_notes` (" +
                        "`displayName` TEXT NOT NULL, `text` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`displayName`))"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recording_waveforms` (" +
                        "`displayName` TEXT NOT NULL, `peaks` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`displayName`))"
                )
            }
        }

        // Historical versions introduced generated text. These no-op hops preserve the supported
        // upgrade path from every shipped schema without bringing those features back into the app.
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = Unit
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recording_tags` (" +
                        "`displayName` TEXT NOT NULL, `tag` TEXT NOT NULL, " +
                        "PRIMARY KEY(`displayName`, `tag`))"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recording_tags_tag` ON `recording_tags` (`tag`)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recording_favourites` (" +
                        "`displayName` TEXT NOT NULL, PRIMARY KEY(`displayName`))"
                )
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recording_flags` (" +
                        "`displayName` TEXT NOT NULL, `atMs` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`displayName`, `atMs`))"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recording_flags_displayName` " +
                        "ON `recording_flags` (`displayName`)"
                )
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recording_labels` (" +
                        "`displayName` TEXT NOT NULL, `label` TEXT NOT NULL, `subtitle` TEXT, " +
                        "PRIMARY KEY(`displayName`))"
                )
            }
        }

        // Older builds may have these rows. Remove them without touching user-authored metadata.
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `call_summaries_fts`")
                db.execSQL("DROP TABLE IF EXISTS `call_summaries`")
                db.execSQL("DROP TABLE IF EXISTS `speaker_turns`")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `transcript_segments_fts`")
                db.execSQL("DROP TABLE IF EXISTS `transcript_segments`")
                db.execSQL("DROP TABLE IF EXISTS `transcripts`")
                db.execSQL("DROP TABLE IF EXISTS `recording_notes_fts`")
                db.execSQL("DROP TABLE IF EXISTS `call_summaries_fts`")
                db.execSQL("DROP TABLE IF EXISTS `call_summaries`")
                db.execSQL("DROP TABLE IF EXISTS `speaker_turns`")
            }
        }

        internal val MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
        )

        @Volatile
        private var INSTANCE: TranscriptDatabase? = null

        fun get(context: Context): TranscriptDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TranscriptDatabase::class.java,
                    DB_NAME,
                ).addMigrations(*MIGRATIONS)
                    .build().also { INSTANCE = it }
            }

        /** True when the metadata database has been created, without creating it as a side effect. */
        fun exists(context: Context): Boolean =
            INSTANCE != null || context.applicationContext.getDatabasePath(DB_NAME).exists()
    }
}
