# Plan: Health Intelligence Service — Signal-Based Insights

## Context

Two complementary changes:

1. **New Insights tab** (replaces Coach) — Deep signal-based intelligence: Circadian Delta, Weekly Support Level, Longitudinal Baseline Shifts. Pre-computed during sync.
2. **Replace WoW/MoM on existing views** — Dashboard and MetricDetail tabs get contextual insight cards (Streak, PR, Goal Consistency, etc.) instead of blanket WoW/MoM badges.

### Philosophy
- **Investing lens**: Support levels, baseline shifts, structural trends
- **Clinical lens**: Circadian rhythm, basal rate changes, cardiovascular markers
- **Pre-computed**: All insights computed during sync, stored in `insights` table, read instantly by UI

---

## Navigation

**Before:** Today | Coach | You
**After:** Today | Insights | You

Replace Coach `NavigationBarItem` in `DashboardScreen.kt` (line ~224).

---

## Insights Table Schema

All insights — both the signal-based ones (Insights tab) and contextual ones (Dashboard/MetricDetail) — are pre-computed and stored in a single table.

```sql
CREATE TABLE insights (
    id TEXT NOT NULL,               -- "{date}:{type}:{metric}" deterministic key
    date TEXT NOT NULL,             -- anchor date (yyyy-MM-dd)
    type TEXT NOT NULL,             -- CircadianDelta, SupportLevel, BasalTrend, Streak, PersonalRecord, etc.
    metric TEXT NOT NULL,           -- Steps, RestingHeartRate, HRV, etc.
    category TEXT NOT NULL,         -- Daily, Weekly, Longitudinal
    context TEXT NOT NULL,          -- Where eligible: InsightsTab, Dashboard, DetailDay, DetailWeek, DetailMonth, Detail3M6MY
    headline TEXT NOT NULL,         -- Short signal text
    body TEXT NOT NULL,             -- Longer explanation
    sentiment TEXT NOT NULL,        -- Positive, Neutral, Negative, Celebratory
    score REAL NOT NULL,            -- 0..1 ranking priority
    signalValue REAL,              -- The computed delta/percentage
    metadata TEXT,                  -- JSON blob for extra data
    computedAtMs INTEGER NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_insights_date ON insights(date);
CREATE INDEX idx_insights_context ON insights(context, date);
CREATE INDEX idx_insights_category ON insights(category, date);
```

The `context` column determines which surface an insight appears on. One insight type can be stored multiple times with different contexts if needed, or the DAO query can filter by context.

---

## New Data Layer: Hourly Ingestion (for Circadian Delta)

### New table: `raw_hourly_metrics`

```sql
CREATE TABLE raw_hourly_metrics (
    date TEXT NOT NULL,
    hour INTEGER NOT NULL,
    metric TEXT NOT NULL,
    value REAL NOT NULL,
    source TEXT NOT NULL,
    ingestedAtMs INTEGER NOT NULL,
    PRIMARY KEY (date, hour, metric, source)
);
CREATE INDEX idx_hourly_metric_date ON raw_hourly_metrics(metric, date);
```

### New HC fetch method

**File:** `data/.../health/HealthConnectDataSource.kt` — add:

```kotlin
suspend fun stepsByHour(day: JavaLocalDate, zone: ZoneId): Map<Int, Long> {
    val c = client ?: return emptyMap()
    val start = day.atStartOfDay(zone).toInstant()
    val end = day.plusDays(1).atStartOfDay(zone).toInstant()
    val req = AggregateGroupByDurationRequest(
        metrics = setOf(StepsRecord.COUNT_TOTAL),
        timeRangeFilter = TimeRangeFilter.between(start, end),
        timeRangeSlicer = java.time.Duration.ofHours(1),
    )
    return c.aggregateGroupByDuration(req).associate { bucket ->
        bucket.startTime.atZone(zone).hour to (bucket.result[StepsRecord.COUNT_TOTAL] ?: 0L)
    }
}
```

### Ingestion hook in `EnhancedHealthSyncManager.fetchBulkAggregates()`

```kotlin
for (day in generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }) {
    val hourly = hc.stepsByHour(day, zone)
    val entities = hourly.map { (hour, value) ->
        RawHourlyMetricEntity(
            date = day.toString(), hour = hour, metric = "Steps",
            value = value.toDouble(), source = "HealthConnect", ingestedAtMs = nowMs,
        )
    }
    rawHourlyDao.insertAll(entities)
}
```

---

## HealthIntelligenceService

**New file:** `data/src/main/kotlin/com/pulse/data/compute/HealthIntelligenceService.kt`

Computes ALL insight types — both signal-based and contextual — and stores them in the `insights` table.

```kotlin
@Singleton
class HealthIntelligenceService @Inject constructor(
    private val summaryDao: SummaryDailyMetricDao,
    private val rawHourlyDao: RawHourlyMetricDao,
    private val insightDao: InsightDao,
    private val goalDao: GoalDao,
    private val clock: Clock,
) {
    suspend fun computeAll(datesAffected: List<String>) {
        val today = datesAffected.maxOrNull() ?: return
        // Signal-based (Insights tab)
        computeCircadianDelta(today)
        computeSupportLevel(today)
        computeBasalTrends(today)
        // Contextual (Dashboard + MetricDetail)
        computeStreaks(today)
        computePersonalRecords(today)
        computeGoalConsistency(today)
        computeAnomalies(today)
        computePaceTrajectory(today)
        computeWoW(today)
        computeMoM(today)
    }
}
```

### Hook point in `EnhancedHealthSyncManager`

After `computeEngine.processQueue()` (line 84 in `syncRecent()`, line 125 in `backfillHistory()`):

```kotlin
computeEngine.processQueue()
intelligenceService.computeAll(datesAffected)  // ← NEW
```

---

## Signal-Based Insights (Insights Tab)

### Task 1: Circadian Delta — "Daily Rhythm Signal"

Compare cumulative steps at current hour to historical expected value for same day-of-week.

**SQL Queries:**
```sql
-- Today's cumulative steps up to current hour
SELECT SUM(value) FROM raw_hourly_metrics
WHERE date = :today AND metric = 'Steps' AND hour <= :currentHour;

-- Historical same-DOW cumulatives (last 4 weeks)
SELECT date, SUM(value) AS cumulative FROM raw_hourly_metrics
WHERE date IN (:sameDowDates) AND metric = 'Steps' AND hour <= :currentHour
GROUP BY date;
```

**Output:** `"+12% vs your typical Saturday noon pace"` → context: `InsightsTab`, category: `Daily`

### Task 2: Weekly Support Level — "Consistency Signal"

Track the activity floor (minimum daily total) WoW. Rising floor = structural improvement.

**SQL Queries:**
```sql
SELECT MIN(total) FROM summary_daily_metrics
WHERE metric = :metric AND date BETWEEN :weekStart AND :weekEnd AND total > 0;
```

**Output:** `"Activity floor risen +15% WoW. You are raising your baseline support level."` → context: `InsightsTab`, category: `Weekly`

### Task 3: Longitudinal Baseline Shift — "Basal Trend Analysis"

30-day rolling average vs 90-day baseline for Steps, Calories, Distance, RHR, HRV.

**SQL Queries:**
```sql
SELECT AVG(total) FROM summary_daily_metrics
WHERE metric = :metric AND date BETWEEN :start AND :end AND total > 0;

SELECT COUNT(*) FROM summary_daily_metrics
WHERE metric = :metric AND date BETWEEN :start AND :end AND total > 0;
```

**Output:** `"Structural update: Resting heart rate stabilized at 58bpm, 4% improvement over Q1 baseline"` → context: `InsightsTab`, category: `Longitudinal`

---

## Contextual Insights (Dashboard + MetricDetail)

These replace the WoW/MoM badges on existing views.

### Insight → View mapping

| Insight Type | Dashboard | Day | Week | Month | 3M/6M/Y |
|-------------|-----------|-----|------|-------|---------|
| **Streak** | top 2 | - | top 2 | - | - |
| **PersonalRecord** | top 2 | top 1 | top 2 | top 2 | top 1 |
| **Anomaly** | top 2 | top 1 | - | - | - |
| **GoalConsistency** | - | - | top 2 | top 2 | top 1 |
| **PaceTrajectory** | - | - | top 2 | top 2 | - |
| **TrendDirection** (BasalTrend) | top 2 | - | - | top 2 | top 1 |
| **WoW** | - | - | top 2 | - | - |
| **MoM** | - | - | - | top 2 | - |
| **ComparisonAnchor** | top 2 | top 1 | - | - | - |

"top N" = eligible, ranked by score, at most N shown per view.

### Streak Calculator
```kotlin
suspend fun computeStreaks(date: String) {
    for (metric in listOf("Steps", "ActiveCalories", "Distance", "ZoneMinutes")) {
        val days = summaryDao.getRange(metric, ninetyDaysAgo, date)
        val goal = goalDao.get(metric)?.target ?: defaultGoal(metric)
        var streak = 0
        for (day in days.sortedByDescending { it.date }) {
            if (day.total >= goal) streak++ else break
        }
        if (streak < 3) continue
        insightDao.upsert(listOf(InsightEntity(
            id = "$date:Streak:$metric",
            context = "Dashboard,DetailWeek",  // eligible surfaces
            headline = "$streak-day streak!",
            body = "You've hit your ${metric.lowercase()} goal every day since ...",
            sentiment = "Celebratory",
            score = minOf(1.0f, streak / 14f),
            ...
        )))
    }
}
```

### Personal Record Calculator
```kotlin
suspend fun computePersonalRecords(date: String) {
    for (metric in listOf("Steps", "ActiveCalories", "Distance", "ZoneMinutes")) {
        val todayValue = summaryDao.get(date, metric)?.total ?: continue
        val allTimeBest = summaryDao.bestEver(metric)
        if (todayValue >= (allTimeBest?.total ?: 0.0) && todayValue > 0) {
            insightDao.upsert(listOf(InsightEntity(
                id = "$date:PersonalRecord:$metric",
                context = "Dashboard,DetailDay,DetailWeek,DetailMonth,Detail3M6MY",
                headline = "New best! ${formatValue(todayValue, metric)}",
                body = "Your highest ${metric.lowercase()} day ever",
                sentiment = "Celebratory",
                score = 1.0f,  // Always top priority
                ...
            )))
        }
    }
}
```

### Goal Consistency Calculator
```kotlin
suspend fun computeGoalConsistency(date: String) {
    // For current week
    val weekDays = summaryDao.getRange(metric, thisMonday, date)
    val goal = goalDao.get(metric)?.target ?: defaultGoal(metric)
    val hits = weekDays.count { it.total >= goal }
    val total = weekDays.size
    val pct = hits.toFloat() / total
    // Store with context = "DetailWeek,DetailMonth,Detail3M6MY"
    // headline = "Goal hit $hits/$total days (${"%.0f".format(pct*100)}%)"
}
```

### Anomaly Calculator
```kotlin
suspend fun computeAnomalies(date: String) {
    val last30 = summaryDao.getRange(metric, thirtyDaysAgo, yesterday)
    val mean = last30.map { it.total }.average()
    val stddev = /* compute stddev */
    val todayValue = summaryDao.get(date, metric)?.total ?: return
    val zScore = (todayValue - mean) / stddev
    if (abs(zScore) > 1.5) {
        // Store with context = "Dashboard,DetailDay"
        // headline = "Standout day!" or "Unusually low"
    }
}
```

### WoW/MoM Wrappers
Wrap existing `CalculateWoWUseCase.windows()` logic to compute and store as insights:
- WoW → context = `"DetailWeek"` only
- MoM → context = `"DetailMonth"` only

---

## Dashboard + MetricDetail: Additive Only (no replacements)

Existing WoW/MoM badges and all D/W/M/3M/6M/Y views stay **completely untouched**. Contextual insights are added as a **new section below the existing badges** so both can be evaluated side-by-side.

### Dashboard — add insights section below WoW/MoM

**File:** `feature/dashboard/state/DashboardState.kt`
```kotlin
// KEEP existing:
val wow: DeltaPercent? = null,
val mom: DeltaPercent? = null,
// ADD alongside:
val insights: List<Insight> = emptyList(),
```

**File:** `feature/dashboard/viewmodel/DashboardViewModel.kt`
- KEEP `calcWoW` and `calcMoM` — unchanged
- ADD `getInsights: GetInsightsUseCase` as an additional constructor param
- In `wireStreams()`: ADD a new flow alongside the existing combine:
  ```kotlin
  getInsights(date, "Dashboard", limit = 2).onEach { insights ->
      _state.update { it.copy(insights = insights) }
  }.launchIn(viewModelScope)
  ```

**File:** `feature/dashboard/ui/DashboardScreen.kt`
- KEEP the `WoWMoMBadge` row as-is
- ADD a new `InsightCard` section below it (rendered from `state.insights`)

### MetricDetail — add insights section below WoW/MoM

**File:** `feature/detail/state/MetricDetailState.kt`
```kotlin
// KEEP existing:
val wow: DeltaPercent? = null,
val mom: DeltaPercent? = null,
// ADD alongside:
val insights: List<Insight> = emptyList(),
```

**File:** `feature/detail/viewmodel/MetricDetailViewModel.kt`
- KEEP `calcWoW` and `calcMoM` — unchanged
- ADD `getInsights: GetInsightsUseCase` as additional constructor param
- In `rewire()`: ADD insight flow alongside existing WoW/MoM. Map `Timeframe` → context:
  ```kotlin
  val insightContext = when (s.timeframe) {
      Timeframe.Day -> "DetailDay"
      Timeframe.Week -> "DetailWeek"
      Timeframe.Month -> "DetailMonth"
      else -> "Detail3M6MY"
  }
  ```
- New flow (not replacing anything):
  - Day: 1 insight (comparison or anomaly)
  - Week: 2 insights (goal consistency + streak)
  - Month: 2 insights (pace trajectory + consistency)
  - 3M/6M/Y: 1 insight (trend or consistency)

**File:** `feature/detail/ui/MetricDetailScreen.kt`
- KEEP the `WoWMoMBadge` row as-is
- ADD `InsightCard` section below the badges (rendered from `state.insights`)

---

## InsightDao — Context-aware queries

```kotlin
@Dao
interface InsightDao {
    @Upsert
    suspend fun upsert(insights: List<InsightEntity>)

    @Query("SELECT * FROM insights WHERE date = :date AND context LIKE '%' || :context || '%' ORDER BY score DESC LIMIT :limit")
    fun observeByContext(date: String, context: String, limit: Int): Flow<List<InsightEntity>>

    @Query("SELECT * FROM insights WHERE date = :date AND context LIKE '%' || :context || '%' AND metric = :metric ORDER BY score DESC LIMIT :limit")
    fun observeByContextAndMetric(date: String, context: String, metric: String, limit: Int): Flow<List<InsightEntity>>

    @Query("SELECT * FROM insights WHERE category = :category AND date BETWEEN :start AND :end ORDER BY date DESC, score DESC")
    fun observeByCategory(category: String, start: String, end: String): Flow<List<InsightEntity>>

    @Query("DELETE FROM insights WHERE date < :cutoff")
    suspend fun pruneOlderThan(cutoff: String)
}
```

---

## GetInsightsUseCase

```kotlin
class GetInsightsUseCase @Inject constructor(
    private val repo: InsightsRepository,
) {
    // For Dashboard: best insights across all metrics
    operator fun invoke(date: LocalDate, context: String, limit: Int = 2): Flow<List<Insight>>

    // For MetricDetail: insights for a specific metric
    operator fun invoke(date: LocalDate, context: String, metric: MetricType, limit: Int = 2): Flow<List<Insight>>
}
```

Thin wrapper — just reads from pre-computed `insights` table. No heavy computation on UI thread.

---

## Database Migration (v6 → v7)

```kotlin
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS raw_hourly_metrics (
                date TEXT NOT NULL, hour INTEGER NOT NULL, metric TEXT NOT NULL,
                value REAL NOT NULL, source TEXT NOT NULL, ingestedAtMs INTEGER NOT NULL,
                PRIMARY KEY (date, hour, metric, source)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_hourly_metric_date ON raw_hourly_metrics(metric, date)")

        db.execSQL("""
            CREATE TABLE IF NOT EXISTS insights (
                id TEXT NOT NULL, date TEXT NOT NULL, type TEXT NOT NULL,
                metric TEXT NOT NULL, category TEXT NOT NULL, context TEXT NOT NULL,
                headline TEXT NOT NULL, body TEXT NOT NULL, sentiment TEXT NOT NULL,
                score REAL NOT NULL, signalValue REAL, metadata TEXT,
                computedAtMs INTEGER NOT NULL,
                PRIMARY KEY (id)
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_insights_date ON insights(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_insights_context ON insights(context, date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_insights_category ON insights(category, date)")
    }
}
```

---

## Implementation Phases

### Phase 1: Data layer foundation
1. Create `RawHourlyMetricEntity` + `RawHourlyMetricDao`
2. Create `InsightEntity` + `InsightDao`
3. Add `MIGRATION_6_7` to `PulseDatabase` (version → 7)
4. Register new DAOs in `DataModule`
5. Add `stepsByHour()` to `HealthConnectDataSource`
6. Add hourly ingestion to `EnhancedHealthSyncManager.fetchBulkAggregates()`
7. Add `minInRange()`, `avgInRange()`, `countInRange()`, `bestEver()`, `getRange()` to `SummaryDailyMetricDao`
8. **Verify:** Build succeeds, migration runs, hourly data populates after sync

### Phase 2: HealthIntelligenceService — all calculators
1. Create `HealthIntelligenceService` with:
   - Signal-based: `computeCircadianDelta()`, `computeSupportLevel()`, `computeBasalTrends()`
   - Contextual: `computeStreaks()`, `computePersonalRecords()`, `computeGoalConsistency()`, `computeAnomalies()`, `computePaceTrajectory()`, `computeWoW()`, `computeMoM()`
2. Hook into `EnhancedHealthSyncManager` after `computeEngine.processQueue()`
3. **Verify:** After sync, `insights` table has rows with correct `context` values

### Phase 3: Domain model + repository
1. Create `Insight` domain model in `domain/.../model/Insight.kt`
2. Create `InsightsRepository` interface + `InsightsRepositoryImpl`
3. Create `GetInsightsUseCase` — reads from pre-computed table, filters by context

### Phase 4: Dashboard + MetricDetail integration (additive, no replacements)
1. ADD `insights: List<Insight>` to `DashboardState` (keep `wow`/`mom` unchanged)
2. ADD `getInsights: GetInsightsUseCase` to `DashboardViewModel` (keep `calcWoW`/`calcMoM`)
3. ADD `InsightCard` section below existing `WoWMoMBadge` row in `DashboardScreen`
4. ADD `insights: List<Insight>` to `MetricDetailState` (keep `wow`/`mom` unchanged)
5. ADD `getInsights: GetInsightsUseCase` to `MetricDetailViewModel` (keep `calcWoW`/`calcMoM`)
6. ADD `InsightCard` section below existing `WoWMoMBadge` row in `MetricDetailScreen`
7. Create `InsightCard` composable in `:core`
8. **Verify:**
   - Existing WoW/MoM badges still appear and work as before
   - Dashboard: new insight cards appear below badges (streak + trend or anomaly)
   - Week tab: new section shows goal consistency + streak
   - Month tab: new section shows pace trajectory + consistency
   - Day tab: new section shows comparison vs yesterday
   - 3M/6M/Y: new section shows goal consistency or trend
   - PR appears immediately when set
   - Streak appears on dashboard after 3+ days

### Phase 5: Insights tab (new feature module)
1. Create `feature/insights/` module (build.gradle.kts, navigation, state, viewmodel, screen)
2. Replace Coach tab with Insights in `DashboardScreen.kt` bottom nav
3. Wire navigation in `DashboardState`, `DashboardViewModel`, `MainActivity`
4. Build `InsightsScreen` with three sections:
   - **Right Now** — Circadian Delta
   - **This Week** — Support Level
   - **Big Picture** — Basal Trends
5. Register module in `settings.gradle.kts` and `app/build.gradle.kts`

### Phase 6: Polish
- Empty states per section/view when data is insufficient
- Multi-metric insight ranking on dashboard (best insight across Steps/Distance/Calories/ZoneMinutes)
- Prune old insights during sync (`insightDao.pruneOlderThan(sixMonthsAgo)`)

---

## Critical Files

| File | Action |
|------|--------|
| `data/.../entity/RawHourlyMetricEntity.kt` | **NEW** |
| `data/.../dao/RawHourlyMetricDao.kt` | **NEW** |
| `data/.../entity/InsightEntity.kt` | **NEW** |
| `data/.../dao/InsightDao.kt` | **NEW** |
| `data/.../compute/HealthIntelligenceService.kt` | **NEW** |
| `domain/.../model/Insight.kt` | **NEW** |
| `domain/.../repository/InsightsRepository.kt` | **NEW** |
| `data/.../repository/InsightsRepositoryImpl.kt` | **NEW** |
| `domain/.../usecase/GetInsightsUseCase.kt` | **NEW** |
| `feature/insights/.../navigation/InsightsNavigation.kt` | **NEW** |
| `feature/insights/.../state/InsightsState.kt` | **NEW** |
| `feature/insights/.../viewmodel/InsightsViewModel.kt` | **NEW** |
| `feature/insights/.../ui/InsightsScreen.kt` | **NEW** |
| `core/.../ui/insights/InsightCard.kt` | **NEW** |
| `data/.../local/PulseDatabase.kt` | MODIFY — v7, new entities/DAOs, MIGRATION_6_7 |
| `data/.../di/DataModule.kt` | MODIFY — new DAO providers, migration |
| `data/.../health/HealthConnectDataSource.kt` | MODIFY — add `stepsByHour()` |
| `data/.../sync/EnhancedHealthSyncManager.kt` | MODIFY — hourly ingestion + intelligence hook |
| `data/.../dao/SummaryDailyMetricDao.kt` | MODIFY — add `minInRange()`, `avgInRange()`, `countInRange()`, `bestEver()`, `getRange()` |
| `feature/dashboard/state/DashboardState.kt` | MODIFY — `insights` replaces `wow`/`mom`, add OpenInsights intent/effect |
| `feature/dashboard/viewmodel/DashboardViewModel.kt` | MODIFY — `GetInsightsUseCase` replaces `calcWoW`/`calcMoM`, handle OpenInsights |
| `feature/dashboard/ui/DashboardScreen.kt` | MODIFY — `InsightCard`s replace `WoWMoMBadge`, Coach → Insights nav |
| `feature/detail/state/MetricDetailState.kt` | MODIFY — `insights` replaces `wow`/`mom` |
| `feature/detail/viewmodel/MetricDetailViewModel.kt` | MODIFY — `GetInsightsUseCase` replaces `calcWoW`/`calcMoM` |
| `feature/detail/ui/MetricDetailScreen.kt` | MODIFY — `InsightCard`s replace `WoWMoMBadge` |
| `app/.../MainActivity.kt` | MODIFY — register insights route |
| `app/build.gradle.kts` | MODIFY — add feature:insights dependency |
| `settings.gradle.kts` | MODIFY — include :feature:insights |

### Existing code to reuse
- `SummaryComputeEngine` pattern — `HealthIntelligenceService` follows same `@Singleton` + DAO injection
- `CalculateWoWUseCase.windows()` — week boundary logic, wrapped as WoW insight
- `CalculateMoMUseCase` — month boundary logic, wrapped as MoM insight
- `WoWMoMBadge` — design reference for `InsightCard` styling
- `SummaryDailyMetricDao.observeRange()` — primary data source
- `DeltaPercent.from()` — reuse for delta computation

---

## Verification

### Insights Tab
1. **Navigation**: Insights tab replaces Coach in bottom nav
2. **Circadian Delta**: "Right Now" section shows rhythm signal with delta %
3. **Support Level**: "This Week" section shows activity floor WoW
4. **Basal Trends**: "Big Picture" section shows 30d vs 90d baseline for RHR, HRV, Steps
5. **Pre-computation**: Insights load instantly (no spinner)
6. **Empty states**: Graceful when < 4 weeks (Circadian) or < 90 days (Basal)

### Dashboard + MetricDetail (additive — existing views unchanged)
7. **Dashboard**: WoW/MoM badges remain; new insight cards appear BELOW them (e.g., streak + trend)
8. **Detail Week tab**: WoW/MoM badges remain; new section shows goal consistency + streak
9. **Detail Month tab**: WoW/MoM badges remain; new section shows pace trajectory + consistency
10. **Detail Day tab**: WoW/MoM badges remain; new section shows comparison vs yesterday or anomaly
11. **Detail 3M/6M/Y tabs**: WoW/MoM badges remain; new section shows goal consistency or trend
12. **Personal Record**: Set a new best day → celebratory insight appears immediately in new section
13. **Streak**: Hit goal 3+ days → streak insight appears in dashboard's new section
14. **No insight duplication**: Same insight type doesn't appear in views where it's not eligible
15. **Existing views untouched**: D/W/M/3M/6M/Y tabs, WoW/MoM badges, charts, comparison lists — all unchanged
