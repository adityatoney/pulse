# Plan: Enhanced Health Connect Sync Engine + Google Drive Backup

## Context

The current sync engine in `HealthRepositoryImpl.refreshFromHealthConnect()` makes ~480+ Health Connect API calls per 15-minute cycle (per-day loops for vitals, per-day sleep reads, per-session exercise calls), blowing through the Health Connect quota. This refactoring introduces a "Type-First" bulk-fetch strategy, the Changes API for incremental sync, and priority-tiered WorkManager scheduling — reducing the call count to ~25 for initial sync and ~1-5 for incremental. Separately, we add Google Drive backup/restore using the REST API v3 with `appDataFolder` scope.

---

## Part 1: Enhanced Health Connect Sync Engine

### 1.1 Add Bulk-Read Methods to `HealthConnectDataSource`

**File:** `data/src/main/kotlin/com/pulse/data/health/HealthConnectDataSource.kt`

Add 8 new range-based methods that replace per-day reads with single full-range calls:

**Low-density types (1 call each for entire 365-day range):**
- `readWeightRange(start: JavaInstant, end: JavaInstant): List<Pair<JavaLocalDate, Double>>`
- `readBodyFatRange(...)`, `readSpO2Range(...)`, `readHrvRange(...)`, `readVo2MaxRange(...)`, `readSkinTemperatureRange(...)`, `readRestingHeartRateRange(...)`

Each calls `client.readRecords(ReadRecordsRequest(RecordType::class, TimeRangeFilter.between(start, end)))` once, groups results by day. Replaces the current 14-day × 7-type = 98 calls with exactly 7 calls.

**High-density type with pagination:**
- `readHeartRateSamplesRange(start: JavaInstant, end: JavaInstant): List<Pair<JavaInstant, Int>>` — uses `pageToken` to fetch all pages (up to 1000 records per page)

**Bulk sleep:**
- `readSleepRange(start: JavaInstant, end: JavaInstant): List<SleepSessionRecord>` — single range call instead of per-day loop

**Changes API:**
- `getChanges(token: String): ChangesResult` — calls `client.getChanges(ChangesRequest(token))`, iterates through changes, returns upserted/deleted record IDs + next token
- Add `ChangesResult` sealed interface: `Success(upsertedIds, deletedIds, nextToken)` | `TokenExpired`

Note: `requestChangesToken()` already exists at line 240 but is never called — we'll now use it.

### 1.2 Create `EnhancedHealthSyncManager`

**New file:** `data/src/main/kotlin/com/pulse/data/sync/EnhancedHealthSyncManager.kt`

Singleton injected with `HealthConnectDataSource`, all relevant DAOs, `SyncStateDao`, `GoalDao`, `Clock`.

**Key methods:**

| Method | Purpose | Used by |
|--------|---------|---------|
| `syncRecent(days: Int = 7)` | Tries incremental first, falls back to type-first bulk fetch for last N days | Tier 1 worker, periodic sync |
| `backfillHistory(totalDays: Int = 365, chunkDays: Int = 30)` | Backfills remaining history in 30-day chunks with 5s delay between chunks | Tier 2 worker |
| `syncIncremental()` | Uses Changes API with stored token for near-free sync | Called by `syncRecent` |

**Internal helpers:**
- `fetchBulkAggregates(start, end, zone)` — reuses existing efficient `stepsByDay`/`distanceByDay`/`activeCaloriesByDay`/`totalCaloriesByDay` (unchanged, already efficient)
- `fetchBulkVitals(start, end, zone)` — 7 bulk range calls instead of 7 × N per-day calls
- `fetchBulkSleep(start, end, zone)` — single range read
- `fetchExerciseWithDetails(start, end, zone)` — bulk session read, then batch aggregate + HR
- `computeZoneMinutes(start, end, zone)` — uses already-fetched HR samples + resting HR from bulk vitals

**Token management via `SyncStateDao`:**
- Key `"hc_changes_token"` — stores the Health Connect changes token
- Key `"hc_backfill_cursor"` — stores the earliest date already backfilled (ISO string), so crashes resume from where they left off

**Quota management:**
- `executeWithQuotaGuard(block)` — wraps HC calls, parses `availableQuota` from error messages
- If quota < 0.1, stores `"hc_quota_pause_until"` timestamp in `SyncStateDao` and returns `Result.retry()`
- All sync methods check `isQuotaPaused()` before proceeding

### 1.3 Create Tiered WorkManager Workers

**New file:** `data/src/main/kotlin/com/pulse/data/work/ImmediateSyncWorker.kt`
- `@HiltWorker`, calls `syncManager.syncRecent(days = 7)`
- Used for Tier 1 (app launch) and periodic 15-min incremental sync
- Backoff: `BackoffPolicy.EXPONENTIAL`, initial delay 5 minutes

**New file:** `data/src/main/kotlin/com/pulse/data/work/HistoryBackfillWorker.kt`
- `@HiltWorker`, calls `syncManager.backfillHistory(totalDays = 365, chunkDays = 30)`
- Used for Tier 2 background backfill
- Constraints: `setRequiresBatteryNotLow(true)`
- Backoff: `BackoffPolicy.EXPONENTIAL`, initial delay 5 minutes

### 1.4 Update `SyncScheduler`

**File:** `data/src/main/kotlin/com/pulse/data/work/SyncScheduler.kt`

Replace single `schedulePeriodic()` with:
- `scheduleImmediateSync()` — `OneTimeWork`, expedited, `ExistingWorkPolicy.KEEP`
- `scheduleHistoryBackfill()` — `OneTimeWork`, battery-not-low constraint, `ExistingWorkPolicy.KEEP`
- `schedulePeriodic()` — `PeriodicWork` every 15 min using `ImmediateSyncWorker` (incremental via Changes API), exponential backoff from 5 min
- `cancelLegacyWorker()` — cancels old `HealthConnectSyncWorker.UNIQUE_NAME`

### 1.5 Wire Up Integration

**Modify `PulseApplication.kt`:**
```kotlin
override fun onCreate() {
    super.onCreate()
    syncScheduler.cancelLegacyWorker()
    syncScheduler.scheduleImmediateSync()
    syncScheduler.scheduleHistoryBackfill()
    syncScheduler.schedulePeriodic()
}
```

**Modify `HealthRepositoryImpl.kt`:**
- Inject `EnhancedHealthSyncManager`
- `refreshFromHealthConnect(range)` delegates to `syncManager.syncRecent(days)` — removes the 226-line old sync body (lines 428-654)

**Modify `SyncRepositoryImpl.kt`:**
- `forceSyncNow()` enqueues `ImmediateSyncWorker` instead of `HealthConnectSyncWorker`

### 1.6 API Call Count Comparison

| Component | Current | New (Type-First) | New (Incremental) |
|-----------|---------|-------------------|--------------------|
| Aggregates (steps/dist/cal) | 4 | 4 | 0 (Changes API) |
| Vitals (7 types × 14 days) | 98 | 7 | 0 |
| HR samples (14 days) | 14 | 1 (paginated) | 0 |
| Sleep (per-day) | 365 | 1 | 0 |
| Exercise (N sessions × 3 calls) | ~30 | ~12 | 0 |
| Zone minutes (14 × 2) | 28 | 0 (reuses fetched data) | 0 |
| **Total** | **~539** | **~25** | **1-5** |

---

## Part 2: Google Drive Backup System

### 2.1 Create `DriveAuthManager`

**New file:** `data/src/main/kotlin/com/pulse/data/cloud/DriveAuthManager.kt`

Follows the same pattern as `GoogleHealthAuthManager` (Credential Manager + ID token exchange), but scoped to `https://www.googleapis.com/auth/drive.appdata`. Separate from health auth to avoid conflating token lifecycles.

Same structure: `signIn(activityContext)`, `getAccessToken()`, `signOut()`. Uses same `GOOGLE_HEALTH_WEB_CLIENT_ID` (same GCP project).

### 2.2 Create Backup Data Model

**New file:** `data/src/main/kotlin/com/pulse/data/cloud/backup/BackupPayload.kt`

```kotlin
@Serializable
data class BackupPayload(
    val version: Int,              // Schema version (1)
    val dbVersion: Int,            // PulseDatabase version (4)
    val appVersion: String,
    val createdAtMs: Long,
    val dailyAggregates: List<DailyAggregateEntity>,
    val exerciseSessions: List<ExerciseSessionEntity>,
    val exerciseHrSamples: List<ExerciseHrSampleEntity>,
    val exerciseLaps: List<ExerciseLapEntity>,
    val exerciseRoutePoints: List<ExerciseRoutePointEntity>,
    val healthSamples: List<HealthSampleEntity>,
    val sleepSessions: List<SleepSessionEntity>,
    val syncState: List<SyncStateEntity>,
    val goals: List<GoalEntity>,
)
```

**Add `@Serializable` annotation to all 9 entity classes** — they're pure data classes with primitive fields, so `@Serializable` and `@Entity` coexist without conflict.

### 2.3 Add `getAll()` Queries to DAOs

Add `@Query("SELECT * FROM <table>") suspend fun getAll(): List<Entity>` to all 9 DAOs for backup serialization:
- `DailyAggregateDao`, `ExerciseSessionDao`, `ExerciseHrSampleDao`, `ExerciseLapDao`, `ExerciseRoutePointDao`, `HealthSampleDao`, `SleepSessionDao`, `SyncStateDao`, `GoalDao`

### 2.4 Create `DriveBackupManager`

**New file:** `data/src/main/kotlin/com/pulse/data/cloud/backup/DriveBackupManager.kt`

Singleton injected with `HttpClient`, `DriveAuthManager`, `PulseDatabase`, `Clock`.

**Methods:**
- `backup(): Result<BackupMetadata>` — reads all tables via `getAll()`, serializes to JSON, GZIP compresses, uploads to Drive appDataFolder as `pulse_backup.json.gz` via multipart/related upload
- `findBackup(): BackupMetadata?` — queries Drive for existing backup file, returns metadata with `modifiedTime`
- `restore(): Result<Int>` — downloads, decompresses, deserializes, clears tables, upserts all data in a transaction (parent tables before children for FK ordering)
- `isDatabaseEmpty(): Boolean` — checks counts on key tables

**Drive REST API v3 calls (via Ktor HttpClient):**
- List: `GET /drive/v3/files?spaces=appDataFolder&q=name='pulse_backup.json.gz'`
- Upload (create): `POST /upload/drive/v3/files?uploadType=multipart` with multipart/related body
- Upload (update): `PATCH /upload/drive/v3/files/{fileId}?uploadType=multipart`
- Download: `GET /drive/v3/files/{fileId}?alt=media`

### 2.5 Conflict Resolution

**New file:** `data/src/main/kotlin/com/pulse/data/cloud/backup/BackupConflictResolver.kt`

Uses `last_backup_at_ms` (new field in Preferences proto) vs Drive file `modifiedTime`:
- Local DB empty → auto-restore
- Local newer than cloud → skip restore (back up instead)
- Cloud newer than local → prompt user
- Never backed up + DB not empty → prompt user

### 2.6 Proto DataStore Extension

**Modify `data/src/main/proto/feature_flags.proto`:**
- Add `int64 last_backup_at_ms = 7` and `string drive_backup_file_id = 8` to `Preferences`
- Add `bool drive_backup_enabled = 9` to `FeatureFlags`

### 2.7 Auto-Restore on App Launch

**New file:** `data/src/main/kotlin/com/pulse/data/cloud/backup/BackupRestoreInitializer.kt`

Singleton injected in `PulseApplication.onCreate()`. Checks: is Drive authenticated? Is DB empty? If yes to both, attempts restore from cloud backup.

### 2.8 Periodic Backup Worker

**New file:** `data/src/main/kotlin/com/pulse/data/work/DriveBackupWorker.kt`

`@HiltWorker`, runs every 24 hours via WorkManager. Constraints: network required, battery not low. Silently skips if not authenticated.

Schedule via `SyncScheduler.schedulePeriodicBackup()`.

### 2.9 Domain Interface

**New file:** `domain/src/main/kotlin/com/pulse/domain/repository/BackupRepository.kt`

Interface with `backup()`, `findBackup()`, `restore()`, `isDatabaseEmpty()`, `isBackupAvailable`.

**New file:** `data/src/main/kotlin/com/pulse/data/repository/BackupRepositoryImpl.kt`

Delegates to `DriveBackupManager`. Bound in `DataBindsModule`.

---

## Part 3: OAuth Sign-In Updates

The existing `GoogleHealthAuthManager` handles health API scopes. We add a parallel `DriveAuthManager` for `drive.appdata`. Both use the same Credential Manager and GCP client ID, but maintain separate token caches and lifecycles.

**Note:** Both auth managers currently store tokens only in memory (lost on process death). The `DriveBackupWorker` running in WorkManager needs persistent tokens. Consider persisting refresh tokens in `SyncStateDao` or encrypted preferences — this is important for the backup worker to function after app restart.

---

## Implementation Order

| Step | Description | Files |
|------|-------------|-------|
| 1 | Add bulk-read + getChanges methods to `HealthConnectDataSource` | `HealthConnectDataSource.kt` |
| 2 | Create `EnhancedHealthSyncManager` | New: `data/.../sync/EnhancedHealthSyncManager.kt` |
| 3 | Create `ImmediateSyncWorker` + `HistoryBackfillWorker` | New: 2 files in `data/.../work/` |
| 4 | Update `SyncScheduler` with tiered scheduling | `SyncScheduler.kt` |
| 5 | Update `PulseApplication.onCreate()` | `PulseApplication.kt` |
| 6 | Delegate `refreshFromHealthConnect` to new sync manager | `HealthRepositoryImpl.kt` |
| 7 | Update `forceSyncNow` to use new worker | `SyncRepositoryImpl.kt` |
| 8 | Add `@Serializable` to all 9 entities | 9 entity files |
| 9 | Add `getAll()` to all 9 DAOs | 9 DAO files |
| 10 | Create `BackupPayload`, `DriveAuthManager`, `DriveBackupManager` | New: 3 files |
| 11 | Create `BackupConflictResolver`, `BackupRestoreInitializer` | New: 2 files |
| 12 | Create `DriveBackupWorker`, `BackupRepository` interface + impl | New: 3 files |
| 13 | Update proto, DI module, SyncScheduler for backup | 3 modified files |
| 14 | Wire backup restore in `PulseApplication` | `PulseApplication.kt` |

---

## Verification

1. **Sync engine**: Run the app, trigger force sync, check Logcat for `EnhancedHealthSyncManager` logs confirming bulk reads (7 vitals calls, 1 sleep call, etc. vs the old per-day pattern). Verify data appears on Dashboard.
2. **Changes API**: After initial sync, wait 15 minutes for periodic sync. Confirm Logcat shows incremental sync using changes token (1-5 calls instead of 25+).
3. **History backfill**: Check WorkManager inspector for `HistoryBackfillWorker` completion. Verify historical data appears in metric detail charts.
4. **Quota handling**: Simulate rate limit by rapid force-syncs. Verify exponential backoff kicks in and quota pause flag is set.
5. **Drive backup**: Sign in to Drive, trigger manual backup. Verify file appears in Drive appDataFolder (use Drive API explorer). Check GZIP file size is reasonable.
6. **Drive restore**: Clear local DB (debug menu), restart app. Verify auto-restore populates all tables from cloud backup.
7. **Conflict resolution**: Create local data, then restore older backup. Verify local data is preserved (not overwritten by older backup).
