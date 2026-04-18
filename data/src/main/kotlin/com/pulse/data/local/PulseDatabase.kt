package com.pulse.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pulse.data.local.dao.ComputeQueueDao
import com.pulse.data.local.dao.ExerciseHrSampleDao
import com.pulse.data.local.dao.ExerciseLapDao
import com.pulse.data.local.dao.ExerciseRoutePointDao
import com.pulse.data.local.dao.ExerciseSessionDao
import com.pulse.data.local.dao.GoalDao
import com.pulse.data.local.dao.RawDailyMetricDao
import com.pulse.data.local.dao.RawSampleDao
import com.pulse.data.local.dao.SleepSessionDao
import com.pulse.data.local.dao.SummaryDailyMetricDao
import com.pulse.data.local.dao.SyncStateDao
import com.pulse.data.local.entity.ComputeQueueEntity
import com.pulse.data.local.entity.ExerciseHrSampleEntity
import com.pulse.data.local.entity.ExerciseLapEntity
import com.pulse.data.local.entity.ExerciseRoutePointEntity
import com.pulse.data.local.entity.ExerciseSessionEntity
import com.pulse.data.local.entity.GoalEntity
import com.pulse.data.local.entity.RawDailyMetricEntity
import com.pulse.data.local.entity.RawSampleEntity
import com.pulse.data.local.entity.SleepSessionEntity
import com.pulse.data.local.entity.SummaryDailyMetricEntity
import com.pulse.data.local.entity.SyncStateEntity

@Database(
    entities = [
        ExerciseSessionEntity::class,
        ExerciseHrSampleEntity::class,
        ExerciseLapEntity::class,
        ExerciseRoutePointEntity::class,
        SleepSessionEntity::class,
        SyncStateEntity::class,
        GoalEntity::class,
        RawDailyMetricEntity::class,
        RawSampleEntity::class,
        SummaryDailyMetricEntity::class,
        ComputeQueueEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class PulseDatabase : RoomDatabase() {
    abstract fun exerciseSessionDao(): ExerciseSessionDao
    abstract fun exerciseHrSampleDao(): ExerciseHrSampleDao
    abstract fun exerciseLapDao(): ExerciseLapDao
    abstract fun exerciseRoutePointDao(): ExerciseRoutePointDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun goalDao(): GoalDao
    abstract fun sleepSessionDao(): SleepSessionDao
    abstract fun rawDailyMetricDao(): RawDailyMetricDao
    abstract fun rawSampleDao(): RawSampleDao
    abstract fun summaryDailyMetricDao(): SummaryDailyMetricDao
    abstract fun computeQueueDao(): ComputeQueueDao

    companion object {
        const val NAME = "pulse.db"
        const val VERSION = 6

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

        /** v4 → v5: Add raw/summary split tables and compute queue. */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create raw_daily_metrics
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS raw_daily_metrics (
                        date TEXT NOT NULL,
                        metric TEXT NOT NULL,
                        source TEXT NOT NULL,
                        value REAL NOT NULL,
                        unit TEXT NOT NULL,
                        externalId TEXT,
                        ingestedAtMs INTEGER NOT NULL,
                        PRIMARY KEY (date, metric, source)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_raw_daily_metrics_date_metric ON raw_daily_metrics(date, metric)")

                // 2. Create raw_samples
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS raw_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        type TEXT NOT NULL,
                        value REAL NOT NULL,
                        unit TEXT NOT NULL,
                        startUtcMs INTEGER NOT NULL,
                        endUtcMs INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        externalId TEXT,
                        ingestedAtMs INTEGER NOT NULL
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS index_raw_samples_type_startUtcMs_endUtcMs ON raw_samples(type, startUtcMs, endUtcMs)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_raw_samples_externalId ON raw_samples(externalId)")

                // 3. Create summary_daily_metrics
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS summary_daily_metrics (
                        date TEXT NOT NULL,
                        metric TEXT NOT NULL,
                        total REAL NOT NULL,
                        goal REAL,
                        sampleCount INTEGER NOT NULL,
                        computedAtMs INTEGER NOT NULL,
                        computationVersion INTEGER NOT NULL DEFAULT 1,
                        sourceUsed TEXT,
                        dirty INTEGER NOT NULL DEFAULT 1,
                        remoteVersion INTEGER,
                        PRIMARY KEY (date, metric)
                    )
                """.trimIndent())

                // 4. Create compute_queue
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS compute_queue (
                        date TEXT NOT NULL,
                        metric TEXT NOT NULL,
                        enqueuedAtMs INTEGER NOT NULL,
                        PRIMARY KEY (date, metric)
                    )
                """.trimIndent())

                // 5. Migrate existing daily_aggregates → raw_daily_metrics (as "legacy" source)
                db.execSQL("""
                    INSERT OR IGNORE INTO raw_daily_metrics (date, metric, source, value, unit, externalId, ingestedAtMs)
                    SELECT date, metric, 'legacy', total, 'count', 'legacy-' || date || '-' || metric, computedAtMs
                    FROM daily_aggregates
                """.trimIndent())

                // 6. Copy daily_aggregates → summary_daily_metrics
                db.execSQL("""
                    INSERT OR IGNORE INTO summary_daily_metrics (date, metric, total, goal, sampleCount, computedAtMs, sourceUsed, dirty, remoteVersion)
                    SELECT date, metric, total, goal, sampleCount, computedAtMs, 'legacy', dirty, remoteVersion
                    FROM daily_aggregates
                """.trimIndent())

                // 7. Migrate health_samples → raw_samples
                db.execSQL("""
                    INSERT OR IGNORE INTO raw_samples (type, value, unit, startUtcMs, endUtcMs, source, externalId, ingestedAtMs)
                    SELECT type, value, unit, startUtcMs, endUtcMs, source, id, endUtcMs
                    FROM health_samples
                """.trimIndent())
            }
        }

        /** v5 → v6: Drop legacy daily_aggregates and health_samples tables. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS daily_aggregates")
                db.execSQL("DROP TABLE IF EXISTS health_samples")
            }
        }
    }
}
