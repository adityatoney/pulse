package com.pulse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pulse.data.local.dao.DailyAggregateDao
import com.pulse.data.local.dao.ExerciseHrSampleDao
import com.pulse.data.local.dao.ExerciseLapDao
import com.pulse.data.local.dao.ExerciseRoutePointDao
import com.pulse.data.local.dao.ExerciseSessionDao
import com.pulse.data.local.dao.GoalDao
import com.pulse.data.local.dao.HealthSampleDao
import com.pulse.data.local.dao.SleepSessionDao
import com.pulse.data.local.dao.SyncStateDao
import com.pulse.data.local.entity.DailyAggregateEntity
import com.pulse.data.local.entity.ExerciseHrSampleEntity
import com.pulse.data.local.entity.ExerciseLapEntity
import com.pulse.data.local.entity.ExerciseRoutePointEntity
import com.pulse.data.local.entity.ExerciseSessionEntity
import com.pulse.data.local.entity.GoalEntity
import com.pulse.data.local.entity.HealthSampleEntity
import com.pulse.data.local.entity.SleepSessionEntity
import com.pulse.data.local.entity.SyncStateEntity

@Database(
    entities = [
        DailyAggregateEntity::class,
        ExerciseSessionEntity::class,
        ExerciseHrSampleEntity::class,
        ExerciseLapEntity::class,
        ExerciseRoutePointEntity::class,
        HealthSampleEntity::class,
        SleepSessionEntity::class,
        SyncStateEntity::class,
        GoalEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun dailyAggregateDao(): DailyAggregateDao
    abstract fun exerciseSessionDao(): ExerciseSessionDao
    abstract fun exerciseHrSampleDao(): ExerciseHrSampleDao
    abstract fun exerciseLapDao(): ExerciseLapDao
    abstract fun exerciseRoutePointDao(): ExerciseRoutePointDao
    abstract fun healthSampleDao(): HealthSampleDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun goalDao(): GoalDao
    abstract fun sleepSessionDao(): SleepSessionDao

    companion object {
        const val NAME = "pulse.db"

        /** v2 → v3: Add exercise HR samples, laps, and extra columns on exercise_sessions. */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE exercise_sessions ADD COLUMN steps INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE exercise_sessions ADD COLUMN avgPaceSecondsPerMile INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE exercise_sessions ADD COLUMN elevationGainMeters REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE exercise_sessions ADD COLUMN zoneMinutes INTEGER DEFAULT NULL")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS exercise_hr_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId TEXT NOT NULL,
                        timestampMs INTEGER NOT NULL,
                        bpm INTEGER NOT NULL,
                        FOREIGN KEY (sessionId) REFERENCES exercise_sessions(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_hr_samples_sessionId ON exercise_hr_samples(sessionId)")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS exercise_laps (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId TEXT NOT NULL,
                        lapNumber INTEGER NOT NULL,
                        distanceMeters REAL NOT NULL,
                        durationMs INTEGER NOT NULL,
                        paceSecondsPerMile INTEGER,
                        FOREIGN KEY (sessionId) REFERENCES exercise_sessions(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_laps_sessionId ON exercise_laps(sessionId)")
            }
        }

        /** v3 → v4: Add exercise route points table. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS exercise_route_points (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId TEXT NOT NULL,
                        timestampMs INTEGER NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        altitude REAL,
                        FOREIGN KEY (sessionId) REFERENCES exercise_sessions(id) ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_exercise_route_points_sessionId ON exercise_route_points(sessionId)")
            }
        }
    }
}
