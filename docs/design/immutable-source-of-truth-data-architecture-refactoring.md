# Plan: Immutable Source-of-Truth Data Architecture Refactoring

## Context

The current data layer violates immutability: ingestion code (HC + Fitbit) writes aggregates directly to `daily_aggregates`, user preferences influence which metric key gets queried, and exercise aggregate computation overwrites rows in the same table as raw ingestion. This creates bugs (distance toggle not working, stale data after re-sync) and makes recomputation impossible since raw source data isn't preserved separately.

This refactoring separates the database into **Raw** (write-once from external sources) and **Summary** (computed from raw + user profile) layers, with a `ComputeJob` bridge between them.

---

## New Schema

### Raw Layer (immutable, write-once from HC/Fitbit/Google Health)

#### Table: `raw_daily_metrics`
Stores daily-granularity values per source. HC and Fitbit APIs return daily totals for Steps/Distance/Calories — this is the finest granularity available.

```
PK: (date, metric, source)
INDEX: (date, metric)

date: TEXT           -- ISO yyyy-MM-dd
metric: TEXT         -- MetricType.name (Steps, Distance, ActiveCalories, Floors, etc.)
source: TEXT         -- "HealthConnect", "Fitbit", "GoogleHealth", "legacy"
value: REAL          -- The raw value from the source
unit: TEXT           -- "count", "miles", "kcal", etc.
externalId: TEXT?    -- Dedup key: "hc-steps-2026-04-17"
ingestedAtMs: INTEGER
```

**New file:** `data/.../entity/RawDailyMetricEntity.kt`

#### Table: `raw_samples`
Stores timestamp-granular data (HR samples, vitals). Replaces current `health_samples`.

```
PK: id (autoGenerate)
UNIQUE INDEX: externalId
INDEX: (type, startUtcMs, endUtcMs)

type: TEXT           -- "HeartRate", "Weight", "SpO2", "HRV", etc.
value: REAL
unit: TEXT
startUtcMs: INTEGER
endUtcMs: INTEGER
source: TEXT         -- "HealthConnect", "Fitbit"
externalId: TEXT?    -- HC metadata.id or fitbit-{logId}
ingestedAtMs: INTEGER
```

**New file:** `data/.../entity/RawSampleEntity.kt`

#### Unchanged Raw Tables
- `exercise_sessions` — already immutable with stable external PK
- `exercise_hr_samples`, `exercise_laps`, `exercise_route_points` — children of exercise_sessions
- `sleep_sessions` — already immutable with external PK

### Summary Layer (computed, recomputable)

#### Table: `summary_daily_metrics`
Replaces `daily_aggregates`. Populated exclusively by `SummaryComputeEngine`.

```
PK: (date, metric)

date: TEXT
metric: TEXT         -- Steps, Distance, ActiveCalories, ZoneMinutes, etc.
total: REAL
goal: REAL?
sampleCount: INTEGER
computedAtMs: INTEGER
computationVersion: INTEGER DEFAULT 1  -- bump to force global recompute
sourceUsed: TEXT?    -- which raw source won conflict resolution
dirty: INTEGER DEFAULT 1              -- for cloud backup sync
remoteVersion: INTEGER?
```

**New file:** `data/.../entity/SummaryDailyMetricEntity.kt`

#### Table: `compute_queue`
Lightweight dirty-date tracker. Ingestion writes dates here; ComputeWorker drains it.

```
PK: (date, metric)

date: TEXT
metric: TEXT
enqueuedAtMs: INTEGER
```

**New file:** `data/.../entity/ComputeQueueEntity.kt`

### Tables to Deprecate (Phase 5)
- `daily_aggregates` → replaced by `raw_daily_metrics` + `summary_daily_metrics`
- `health_samples` → replaced by `raw_samples`

---

## DAOs

### New DAOs (4 files in `data/.../dao/`)

| DAO | Key Methods |
|-----|-------------|
| `RawDailyMetricDao` | `insertAll()`, `getForDateAndMetric()`, `getRange()`, `existsByExternalId()` |
| `RawSampleDao` | `insertAll(onConflict=IGNORE)`, `observeRange()`, `getRange()` |
| `SummaryDailyMetricDao` | `upsert()`, `observe()`, `observeRange()`, `dirty()`, `markSynced()` |
| `ComputeQueueDao` | `enqueue()`, `dequeue(limit)`, `remove()`, `count()` |

`SummaryDailyMetricDao` mirrors current `DailyAggregateDao` signatures so the read-side migration is mechanical (swap DAO reference).

---

## SummaryComputeEngine

**New file:** `data/.../compute/SummaryComputeEngine.kt`

Core class that bridges Raw → Summary. Injected with all raw DAOs, exercise DAO, goals DAO, prefs repo, clock.

### Key Methods

| Method | Purpose |
|--------|---------|
| `processQueue(batchSize=500)` | Drain compute_queue, recompute affected summaries |
| `computeForDate(date)` | Read raw_daily_metrics for date, resolve source conflicts, write summary |
| `computeZoneMinutes(date)` | Read HR from raw_samples + user profile → ZoneMinuteCalculator → summary |
| `computeExerciseAggregates(date)` | Sum exercise_sessions for date → ExerciseDistance/ExerciseCalories in summary |
| `recomputeAll(days)` | Enqueue last N days for full recomputation |
| `invalidateMetric(metric, days)` | Enqueue specific metric for recomputation (e.g., after profile change) |

### Source Conflict Resolution

When multiple sources report the same metric for the same date (e.g., HC says 9000 steps, Fitbit says 9500):

```
Priority: Fitbit > GoogleHealth > HealthConnect > legacy
For each (date, metric): take first source in priority order that has value > 0.
Fallback: take max across all sources.
```

### User Preference Handling

The `activityOnlyDistance` / `activityOnlyCalories` toggles no longer affect the read side. Instead:

- Compute engine reads the current pref when computing Distance/ActiveCalories summaries
- If `activityOnlyDistance=true`: summary Distance = sum of exercise_sessions distance for that date
- If `activityOnlyDistance=false`: summary Distance = raw_daily_metrics Distance value (HC/Fitbit total)
- Toggle change → `recomputeAll()` → summary table updates → reactive Flows auto-update UI

This eliminates `ExerciseDistance`/`ExerciseCalories` from the UI layer entirely.

### Zone Minutes (Task 4)

Zone minutes depend on raw HR samples + user profile (resting HR, age). The compute engine:

1. Reads HR samples from `raw_samples` for the date
2. Reads current resting HR and age from user preferences
3. Calls existing `ZoneMinuteCalculator.calculate(samples, restingHr, age)`
4. Writes result to `summary_daily_metrics` under `ZoneMinutes`

When user changes resting HR or age → `invalidateMetric(ZoneMinutes, days=365)` → recompute all zone minutes from raw HR without touching raw data.

---

## SummaryComputeWorker

**New file:** `data/.../work/SummaryComputeWorker.kt`

`@HiltWorker` that calls `computeEngine.processQueue()`. Enqueued as a one-shot expedited worker after each ingestion batch. Typical latency: <500ms for 7 days of data.

---

## Ingestion Pipeline Changes

### EnhancedHealthSyncManager
**File:** `data/.../sync/EnhancedHealthSyncManager.kt`

| Current | New |
|---------|-----|
| `fetchBulkAggregates()` → upserts `DailyAggregateEntity` | → inserts `RawDailyMetricEntity(source="HealthConnect")` + enqueues compute |
| `fetchBulkVitals()` → writes vitals + computes zone mins → `DailyAggregateEntity` | → writes vitals to `raw_daily_metrics`, HR samples to `raw_samples`, enqueues ZoneMinutes compute |
| `computeExerciseAggregates()` → writes ExerciseDistance/ExerciseCalories to daily_aggregates | **REMOVED** — summary engine handles this |
| Constructor: `aggregateDao`, `sampleDao`, `goalDao` | Constructor: `rawDailyDao`, `rawSampleDao`, `computeQueueDao` |

### FitbitSyncManager
**File:** `data/.../cloud/fitbit/FitbitSyncManager.kt`

Same pattern: replace `aggregateDao.upsert(rows)` with `rawDailyDao.insertAll(rows)` where source="Fitbit", then enqueue compute. Remove `buildGoalMap()` (goals are a summary concern).

### HealthRepositoryImpl.refreshFromCloudApi
**File:** `data/.../repository/HealthRepositoryImpl.kt` (lines ~473-549)

Redirect writes to `raw_daily_metrics` with source="GoogleHealth", enqueue compute.

---

## Read-Side Changes

### HealthRepositoryImpl
**File:** `data/.../repository/HealthRepositoryImpl.kt`

| Current | New |
|---------|-----|
| `aggregateDao.observe(date, metric)` | `summaryDao.observe(date, metric)` |
| `aggregateDao.observeRange(metric, start, end)` | `summaryDao.observeRange(metric, start, end)` |
| 7-flow combine in `observeTodaySummary` (Steps, Distance, ExerciseDistance, ActiveCalories, ExerciseCalories, ZoneMinutes, Sleep) | **4-flow combine**: Steps, Distance, ActiveCalories, ZoneMinutes + Sleep + Goals (no ExerciseDistance/ExerciseCalories branching) |
| `resolveDbMetric()` + `flatMapLatest` on prefs | **REMOVED** — summary table already has the right value |
| `recomputeAggregates()` calls syncManager | Calls `computeEngine.recomputeAll(days)` |

### YouViewModel (preference toggle)
**File:** `feature/you/.../YouViewModel.kt`

When user toggles `activityOnlyDistance` or `activityOnlyCalories`:
1. Save pref (unchanged)
2. Call `computeEngine.recomputeAll()` to rewrite summaries with new preference applied

---

## Database Migration (v4 → v5)

**File:** `data/.../local/PulseDatabase.kt`

```
MIGRATION_4_5:
1. CREATE TABLE raw_daily_metrics (...)
2. CREATE TABLE raw_samples (...)
3. CREATE TABLE summary_daily_metrics (...)
4. CREATE TABLE compute_queue (...)
5. INSERT INTO raw_daily_metrics SELECT ... FROM daily_aggregates (as source="legacy")
6. INSERT INTO summary_daily_metrics SELECT ... FROM daily_aggregates
7. INSERT INTO raw_samples SELECT ... FROM health_samples
```

Non-destructive: old tables remain for dual-read during transition.

---

## Implementation Phases

### Phase 1: Foundation (no behavior change)
1. Create 4 new entity classes
2. Create 4 new DAO interfaces
3. Update `PulseDatabase` (entities array, DAO methods, version=5, MIGRATION_4_5)
4. Update `DataModule` (new providers, migration registration)
5. Create `SummaryComputeEngine` class
6. Create `SummaryComputeWorker`
7. **Verify:** Build succeeds, migration runs, old tables preserved, new tables populated

### Phase 2: Dual-write ingestion
8. Modify `EnhancedHealthSyncManager` to write to BOTH old tables AND new raw tables + enqueue compute
9. Modify `FitbitSyncManager` similarly
10. Wire `SummaryComputeWorker` into `SyncScheduler`
11. **Verify:** Both old and new tables populated after sync, summary matches daily_aggregates

### Phase 3: Read-side migration
12. Switch `HealthRepositoryImpl` reads from `aggregateDao` → `summaryDao`
13. Simplify `observeTodaySummary` (remove ExerciseDistance/ExerciseCalories branching)
14. Simplify `observeSeries` (remove `resolveDbMetric`, `flatMapLatest` on prefs)
15. Update `SyncRepositoryImpl` to use `summaryDao`
16. Wire preference toggle → `computeEngine.recomputeAll()`
17. **Verify:** UI works entirely from summary tables, toggle works correctly

### Phase 4: Remove dual-write
18. Remove all writes to `daily_aggregates` from ingestion code
19. Remove `computeExerciseAggregates()` from `EnhancedHealthSyncManager`
20. Remove `DailyAggregateDao` dependency from all classes

### Phase 5: Cleanup (separate PR)
21. MIGRATION_5_6: DROP TABLE daily_aggregates, DROP TABLE health_samples
22. Remove `DailyAggregateEntity`, `DailyAggregateDao`, `HealthSampleEntity`, `HealthSampleDao`
23. Clean up `MetricType` (deprecate or remove `ExerciseDistance`/`ExerciseCalories` from UI code)

---

## Critical Files

| File | Change Type |
|------|-------------|
| `data/.../entity/RawDailyMetricEntity.kt` | **NEW** |
| `data/.../entity/RawSampleEntity.kt` | **NEW** |
| `data/.../entity/SummaryDailyMetricEntity.kt` | **NEW** |
| `data/.../entity/ComputeQueueEntity.kt` | **NEW** |
| `data/.../dao/RawDailyMetricDao.kt` | **NEW** |
| `data/.../dao/RawSampleDao.kt` | **NEW** |
| `data/.../dao/SummaryDailyMetricDao.kt` | **NEW** |
| `data/.../dao/ComputeQueueDao.kt` | **NEW** |
| `data/.../compute/SummaryComputeEngine.kt` | **NEW** |
| `data/.../work/SummaryComputeWorker.kt` | **NEW** |
| `data/.../local/PulseDatabase.kt` | MODIFY — v5, new entities/DAOs, MIGRATION_4_5 |
| `data/.../di/DataModule.kt` | MODIFY — new DAO providers, migration |
| `data/.../sync/EnhancedHealthSyncManager.kt` | MODIFY — raw writes + enqueue |
| `data/.../cloud/fitbit/FitbitSyncManager.kt` | MODIFY — raw writes + enqueue |
| `data/.../repository/HealthRepositoryImpl.kt` | MODIFY — summaryDao reads, simplify prefs |
| `data/.../work/SyncScheduler.kt` | MODIFY — schedule SummaryComputeWorker |
| `feature/you/.../YouViewModel.kt` | MODIFY — trigger recompute on pref toggle |
| `domain/.../model/MetricType.kt` | MODIFY — deprecate ExerciseDistance/ExerciseCalories |
| `data/.../mapper/Mappers.kt` | MODIFY — add SummaryDailyMetricEntity.toDomain() |

---

## Verification

1. **Migration:** Install over existing app, verify all old data appears in new tables via debug menu
2. **Ingestion:** Force sync (HC + Fitbit), check raw_daily_metrics has rows with correct source attribution
3. **Compute:** Verify summary_daily_metrics matches expected values after compute worker runs
4. **Dashboard:** Steps/Distance/Calories/ZoneMinutes display correctly
5. **Charts:** W/M/3M/6M/Y views show correct aggregated data from summaries
6. **Toggle:** Switch activityOnlyDistance on/off, verify dashboard updates within ~1s
7. **Profile change:** Update resting HR in You screen, verify zone minutes recalculate
8. **Multi-source:** With both HC and Fitbit connected, verify source priority produces correct summary
9. **Backup/restore:** Backup and restore, verify all tables intact after migration
