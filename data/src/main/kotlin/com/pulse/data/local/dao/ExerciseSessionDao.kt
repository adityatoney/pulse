package com.pulse.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.pulse.data.local.entity.ExerciseSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExerciseSessionDao {

    @Upsert
    suspend fun upsert(sessions: List<ExerciseSessionEntity>)

    @Query("SELECT * FROM exercise_sessions WHERE startUtcMs BETWEEN :fromMs AND :toMs ORDER BY startUtcMs DESC")
    fun observeRange(fromMs: Long, toMs: Long): Flow<List<ExerciseSessionEntity>>

    @Query("SELECT * FROM exercise_sessions WHERE startUtcMs BETWEEN :fromMs AND :toMs ORDER BY startUtcMs ASC")
    suspend fun getRange(fromMs: Long, toMs: Long): List<ExerciseSessionEntity>

    @Query("SELECT * FROM exercise_sessions WHERE dirty = 1 LIMIT :limit")
    suspend fun dirty(limit: Int): List<ExerciseSessionEntity>

    @Query("UPDATE exercise_sessions SET dirty = 0 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("SELECT COALESCE(SUM(calories), 0.0) FROM exercise_sessions WHERE startUtcMs BETWEEN :fromMs AND :toMs")
    fun sumCalories(fromMs: Long, toMs: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(distanceMeters), 0.0) FROM exercise_sessions WHERE startUtcMs BETWEEN :fromMs AND :toMs")
    fun sumDistanceMeters(fromMs: Long, toMs: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(zoneMinutes), 0) FROM exercise_sessions WHERE startUtcMs BETWEEN :fromMs AND :toMs")
    fun sumZoneMinutes(fromMs: Long, toMs: Long): Flow<Int>

    @Query("SELECT * FROM exercise_sessions WHERE id = :id")
    suspend fun findById(id: String): ExerciseSessionEntity?

    /**
     * Find sessions that overlap with the given time window.
     * Two sessions overlap if one starts before the other ends and vice versa.
     */
    @Query("""
        SELECT * FROM exercise_sessions
        WHERE startUtcMs < :endMs AND endUtcMs > :startMs
    """)
    suspend fun findOverlapping(startMs: Long, endMs: Long): List<ExerciseSessionEntity>

    @Query("UPDATE exercise_sessions SET zoneMinutes = :zoneMinutes WHERE id = :id")
    suspend fun updateZoneMinutes(id: String, zoneMinutes: Int)

    @Query("UPDATE exercise_sessions SET calories = :calories, distanceMeters = :distanceMeters, steps = :steps, userEdited = 1 WHERE id = :id")
    suspend fun updateMetrics(id: String, calories: Double, distanceMeters: Double?, steps: Int?)

    @Query("SELECT id FROM exercise_sessions WHERE userEdited = 1")
    suspend fun getUserEditedIds(): List<String>

    @Query("SELECT COUNT(DISTINCT date(startUtcMs/1000, 'unixepoch', 'localtime')) FROM exercise_sessions WHERE startUtcMs BETWEEN :fromMs AND :toMs")
    suspend fun exerciseDayCount(fromMs: Long, toMs: Long): Int

    @Query("SELECT COUNT(*) FROM exercise_sessions")
    suspend fun totalCount(): Int

    @Query("DELETE FROM exercise_sessions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM exercise_sessions")
    suspend fun clear()

    @Query("SELECT * FROM exercise_sessions")
    suspend fun getAll(): List<ExerciseSessionEntity>

    /**
     * Remove duplicate exercise sessions that overlap in time.
     *
     * Priority order (highest wins):
     * 1. Manually logged (Google Fit, or Fitbit non-auto)
     * 2. Tracker-logged (Fitbit device)
     * 3. Auto-detected (Fitbit auto_detected)
     *
     * Within the same priority, the longer session wins.
     */
    suspend fun deduplicateOverlapping(toleranceMs: Long = 60_000L) {
        val all = getAll().sortedBy { it.startUtcMs }
        val toRemove = mutableSetOf<String>()

        for (i in all.indices) {
            if (all[i].id in toRemove) continue
            for (j in i + 1 until all.size) {
                if (all[j].id in toRemove) continue
                // Stop scanning once sessions are too far apart
                if (all[j].startUtcMs > all[i].endUtcMs + toleranceMs) break

                // Check overlap
                if (all[i].startUtcMs < all[j].endUtcMs + toleranceMs &&
                    all[i].endUtcMs > all[j].startUtcMs - toleranceMs
                ) {
                    val loser = pickLoser(all[i], all[j])
                    toRemove.add(loser.id)
                }
            }
        }

        if (toRemove.isNotEmpty()) {
            deleteByIds(toRemove.toList())
        }
    }

    private fun pickLoser(
        a: ExerciseSessionEntity,
        b: ExerciseSessionEntity,
    ): ExerciseSessionEntity {
        val prioA = sourcePriority(a)
        val prioB = sourcePriority(b)
        // Higher priority wins (lower number = loser)
        if (prioA != prioB) return if (prioA < prioB) a else b
        // Same priority: longer duration wins
        val durA = a.endUtcMs - a.startUtcMs
        val durB = b.endUtcMs - b.startUtcMs
        if (durA != durB) return if (durA < durB) a else b
        // Tie-break: Fitbit API loses to HealthConnect
        if (a.id.startsWith("fitbit-") && !b.id.startsWith("fitbit-")) return a
        if (b.id.startsWith("fitbit-") && !a.id.startsWith("fitbit-")) return b
        return b
    }

    /**
     * Priority: manual/Google Fit (3) > tracker/HC Fitbit (2) > auto-detected (1)
     */
    private fun sourcePriority(session: ExerciseSessionEntity): Int = when {
        // Google Fit = manually logged
        session.sourceJson == "com.google.android.apps.fitness" -> 3
        // Fitbit auto-detected
        session.sourceJson == "fitbit:auto" -> 1
        // Everything else (Fitbit tracker, HC Fitbit Mobile, etc.)
        else -> 2
    }
}
