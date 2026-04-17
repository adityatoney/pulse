# Health API Audit & Hybrid Integration Plan

## Phase 0: Rename App from "FitbitClone" to "Pulse"

Rename across the entire codebase:
- **Package names:** `com.fitbitclone` → `com.pulse` (all modules)
- **App label:** Update `app_name` string resource to "Pulse"
- **Database name:** `fitbit-clone.db` → `pulse.db`
- **Convention plugin IDs:** `fitbit.android.*` → `pulse.android.*`
- **Build logic module references:** Update `build-logic/convention` plugin IDs
- **Directory structure:** Rename `com/fitbitclone/` → `com/pulse/` in all `src/main/kotlin/` and `src/test/kotlin/` trees
- **Gradle module names:** Keep module directory names as-is (app, data, domain, feature/*) — only rename package identifiers
- **Files to update:** `settings.gradle.kts`, all `build.gradle.kts`, all `AndroidManifest.xml`, all Kotlin source files, `FitbitDatabase.kt`, `libs.versions.toml` plugin IDs
- **App icon:** Generate an adaptive icon — a stylized pulse/heartbeat wave in a rounded square, using Material You teal/cyan accent on dark background. Create vector drawable for `ic_launcher_foreground.xml` and update `ic_launcher.xml` / `ic_launcher_round.xml`

---

## Context

The app currently uses only the on-device Health Connect SDK (`androidx.health.connect:connect-client:1.1.0-alpha07`) to read health data. This has two problems:

1. **Missing data types** — We only read 10 of 50+ HC record types; many Fitbit-synced types (Weight, SpO2, HRV, Sleep stages) are ignored
2. **Data accuracy** — HC SDK's `aggregate()` can double-count when phone + watch both write steps. The Google Health REST API's `reconcile` endpoint does server-side conflict resolution, filtering noisy phone data and keeping clean watch data
3. **Precision** — HC uses meters (rounded); the REST API uses millimeters, preventing compounding rounding errors over long distances

**Recommended architecture (per user direction): Hybrid Model**
- **HC SDK** for live/today data (heart rate, current steps) — low latency, feels fast
- **Google Health REST API** (`reconcile` endpoint) for yesterday+ historical data — accurate, deduplicated, authoritative

---

## Phase 1: Fix Current HC Integration Issues

### 1.1 Upgrade SDK to stable
- **File:** `gradle/libs.versions.toml` line 41
- Change `healthConnect = "1.1.0-alpha07"` → `healthConnect = "1.1.0"`
- Stable release (Oct 2025) — unlocks `HeartRateVariabilityRmssdRecord`, `SkinTemperatureRecord`, `Vo2MaxRecord`

### 1.2 Fix sleep (returns null)
- **File:** `data/.../repository/HealthRepositoryImpl.kt` line 173-174
- `observeSleep()` is hardcoded to `flowOf(null)`. The data source `HealthConnectDataSource.readSleep()` already works
- Fix the time range for sleep: use noon-to-noon window (sleep sessions span midnight)
- Map `SleepSessionRecord.stages` to a `SleepSummary` domain object
- Create `SleepSessionEntity` + `SleepSessionDao` for Room storage (DB version 1→2)

### 1.3 Fix Zone Minutes (currently estimated from calories)
- **File:** `data/.../repository/HealthRepositoryImpl.kt` lines 203-215
- Replace the crude `(totalKcal - 1800) / 7.0` heuristic with `ZoneMinuteCalculator` (already exists at `domain/.../usecase/ZoneMinuteCalculator.kt`)
- Use `hc.readHeartRateSamples()` + `hc.restingHeartRate()` to compute real zone minutes from HR data
- Default age=30 with TODO for user preferences

### 1.4 Clean up permissions
- **File:** `data/.../health/HealthConnectDataSource.kt` lines 51-68
- Remove unused `SpeedRecord` read permission + import
- Add new read permissions for expanded record types (Phase 2)
- **File:** `data/src/main/AndroidManifest.xml`
- Add matching manifest permissions for new record types

---

## Phase 2: Expand HC Record Type Coverage

Add the record types Fitbit syncs to HC that we currently ignore.

### 2.1 Add domain model entries
- **File:** `domain/.../model/MetricType.kt`
- Add: `Weight`, `BodyFat`, `SpO2`, `SkinTemperature`, `HRV`, `VO2Max`
- **File:** `domain/.../model/MeasurementUnit.kt`
- Add: `Kilograms`, `Percent`, `Celsius`, `Milliseconds`

### 2.2 Add HealthConnectDataSource methods
- **File:** `data/.../health/HealthConnectDataSource.kt`
- Add read methods (each uses `readRecords()` + takes last sample for the day):
  - `readWeight(day, zone): Double?` — `WeightRecord` (kg)
  - `readBodyFat(day, zone): Double?` — `BodyFatRecord` (%)
  - `readSpO2(day, zone): Double?` — `OxygenSaturationRecord` (%)
  - `readSkinTemperature(day, zone): Double?` — `SkinTemperatureRecord` (delta C)
  - `readHrv(day, zone): Double?` — `HeartRateVariabilityRmssdRecord` (ms)
  - `readVo2Max(day, zone): Double?` — `Vo2MaxRecord`
- Add these to `requiredPermissions` (read-only) and `ChangesTokenRequest.recordTypes`

### 2.3 Expand refreshFromHealthConnect
- **File:** `data/.../repository/HealthRepositoryImpl.kt`
- In `refreshFromHealthConnect()`, iterate days and call new data source methods
- Upsert to `daily_aggregates` with `metric = "Weight"` / `"BodyFat"` / etc.
- No schema change needed — existing `daily_aggregates` table uses `metric` string column

### 2.4 Sleep database table
- **Create:** `data/.../local/entity/SleepSessionEntity.kt` — id, startUtcMs, endUtcMs, totalMinutes, deepMinutes, remMinutes, lightMinutes, awakeMinutes
- **Create:** `data/.../local/dao/SleepSessionDao.kt`
- **Modify:** `data/.../local/FitbitDatabase.kt` — add entity, bump version to 2, add destructive migration (dev phase)
- **Modify:** `data/.../di/DataModule.kt` — provide SleepSessionDao

---

## Phase 3: Google Health REST API Integration (Hybrid Model)

### Architecture

```
Today's data:     HC SDK → Room → UI  (fast, real-time)
Yesterday+ data:  REST API reconcile → Room (overwrites HC data) → UI  (accurate, deduplicated)
```

The `reconcile` endpoint at `GET /v4/users/me/dataTypes/{type}/dataPoints:reconcile` with `dataSourceFamily=users/me/dataSourceFamilies/google-wearables` returns server-side conflict-resolved data.

### 3.1 OAuth / Google Sign-In
- **Create:** `data/.../auth/GoogleHealthAuthManager.kt`
- Uses existing `androidx.credentials` dependency (already in `data/build.gradle.kts`)
- Handles OAuth 2.0 flow for scope: `https://www.googleapis.com/auth/googlehealth.activity_and_fitness.readonly` (+ health_metrics, sleep scopes)
- Stores refresh token in encrypted DataStore
- Provides `getAccessToken(): String?` for REST calls
- **Modify:** `data/build.gradle.kts` — add `GOOGLE_HEALTH_CLIENT_ID` BuildConfig from `local.properties`

### 3.2 REST API client
- **Modify:** `data/.../cloud/GoogleHealthRemoteDataSource.kt` — expand interface with reconcile-oriented methods:
  ```kotlin
  suspend fun reconcileSteps(range: DateRange): Map<LocalDate, Long>
  suspend fun reconcileDistance(range: DateRange): Map<LocalDate, Double>  // millimeters
  suspend fun reconcileCalories(range: DateRange): Map<LocalDate, Double>
  suspend fun reconcileZoneMinutes(range: DateRange): Map<LocalDate, Int>
  suspend fun reconcileExercise(range: DateRange): List<ExerciseSession>
  suspend fun reconcileSleep(range: DateRange): List<SleepSummary>
  suspend fun reconcileWeight(range: DateRange): Map<LocalDate, Double>
  suspend fun reconcileHrv(range: DateRange): Map<LocalDate, Double>
  suspend fun reconcileSpO2(range: DateRange): Map<LocalDate, Double>
  suspend fun reconcileVo2Max(range: DateRange): Map<LocalDate, Double>
  ```
- **Create:** `data/.../cloud/GoogleHealthRestClient.kt` — concrete implementation
  - Uses Ktor HttpClient (already a dependency) to call `health.googleapis.com/v4/`
  - Auth header: `Bearer {accessToken}` from GoogleHealthAuthManager
  - Applies `dataSourceFamily=users/me/dataSourceFamilies/google-wearables` filter
  - Distance returned in **millimeters** for precision
- **Create:** `data/.../cloud/dto/GoogleHealthDtos.kt` — kotlinx-serialization DTOs for reconcile responses
- Keep `NoopGoogleHealthRemoteDataSource` as fallback (returns empty results)

### 3.3 Hybrid sync strategy in repository
- **Modify:** `data/.../repository/HealthRepositoryImpl.kt`
- Add `refreshFromCloudApi(range: DateRange): Result<Unit>`:
  1. Check `googleHealthReconcile` feature flag (already exists in FeatureFlagRepository)
  2. Check user is authenticated (has valid access token)
  3. Call reconcile endpoints for each data type
  4. **Overwrite** Room data for dates in the range (reconciled = authoritative)
  5. Convert distances from millimeters → miles for storage consistency
- Sync logic:
  - **Today**: HC SDK only (fast, real-time)
  - **Yesterday and older**: REST API reconcile overwrites HC SDK data
  - If REST API unavailable/unauthenticated: fall back to HC SDK data (still works)

### 3.4 Update sync worker
- **Modify:** `data/.../work/HealthConnectSyncWorker.kt`
- After `health.refreshFromHealthConnect(range)`, call `health.refreshFromCloudApi(yesterdayRange)`
- Guard with try/catch so REST failure doesn't block HC sync
- Only reconcile yesterday+ (not today — today uses live HC data)

### 3.5 DI wiring
- **Modify:** `data/.../di/DataModule.kt`
- Replace `NoopGoogleHealthRemoteDataSource` with `GoogleHealthRestClient` (conditional on auth state)
- Add providers for `GoogleHealthAuthManager`, Ktor `HttpClient`

---

## Phase 4: UI for New Data Types

### 4.1 Dashboard expansion
- **Modify:** `feature/dashboard/.../state/DashboardState.kt` — add optional fields: `restingHr`, `hrv`, `spo2`, `weight`, `sleepSummary`
- **Modify:** `feature/dashboard/.../ui/DashboardScreen.kt` — add tiles for new metrics (tappable → metric detail)
- **Modify:** `feature/dashboard/.../viewmodel/DashboardViewModel.kt` — wire new state from TodaySummary

### 4.2 Metric detail screen (already generic)
- The existing `MetricDetailViewModel` + `MetricDetailScreen` work with any `MetricType` via `observeRange` on `daily_aggregates`
- Just need to add `formatValue` cases for new types (kg, %, ms, etc.)

### 4.3 Debug seeding for new types
- **Modify:** `data/.../repository/DebugRepositoryImpl.kt` — add Weight, BodyFat, SpO2, HRV rows to `seedFakeData()`

---

## Existing Code to Reuse

| Utility | Path | Purpose |
|---------|------|---------|
| `ZoneMinuteCalculator` | `domain/.../usecase/ZoneMinuteCalculator.kt` | Compute zone minutes from HR samples (replace calorie heuristic) |
| `GoogleHealthRemoteDataSource` | `data/.../cloud/GoogleHealthRemoteDataSource.kt` | Interface seam already exists with `NoopGoogleHealthRemoteDataSource` |
| `googleHealthReconcile` flag | `data/.../datastore/FeatureFlagRepository.kt` | Feature flag already exists to gate REST API |
| Ktor HTTP client | `data/build.gradle.kts` lines 93-97 | Already a dependency, just needs HttpClient provider |
| Credential Manager | `data/build.gradle.kts` lines 100-102 | Already a dependency for Google Sign-In |
| `readHeartRateSamples()` | `data/.../health/HealthConnectDataSource.kt` line 134 | Already reads HR samples (needed for zone minutes) |
| `readSleep()` | `data/.../health/HealthConnectDataSource.kt` line 146 | Already reads sleep (just not called) |
| `restingHeartRate()` | `data/.../health/HealthConnectDataSource.kt` line 125 | Already reads resting HR |

---

## Implementation Order

| # | Task | Phase | Risk |
|---|------|-------|------|
| 1 | SDK upgrade to 1.1.0 stable | 1.1 | Trivial |
| 2 | Permission cleanup | 1.4 | Low |
| 3 | Fix zone minutes (use ZoneMinuteCalculator) | 1.3 | Medium |
| 4 | Domain model expansion (MetricType + MeasurementUnit) | 2.1 | Low |
| 5 | HC data source expansion (Weight, SpO2, HRV, etc.) | 2.2 | Medium |
| 6 | Repository refresh expansion | 2.3 | Medium |
| 7 | Sleep entity + DB migration + fix observeSleep | 1.2 + 2.4 | Medium |
| 8 | Debug seeding for new types | 4.3 | Low |
| 9 | OAuth / Google Sign-In setup | 3.1 | Large |
| 10 | REST API client + DTOs | 3.2 | Large |
| 11 | Hybrid sync in repository | 3.3 | Medium |
| 12 | Sync worker update | 3.4 | Low |
| 13 | DI wiring | 3.5 | Low |
| 14 | Dashboard UI for new metrics | 4.1-4.2 | Medium |

Steps 1-8 can be one PR (HC improvements). Steps 9-13 a second PR (REST API). Step 14 a third PR (UI).

---

## Verification

1. **Build:** `./gradlew assembleDebug` passes
2. **HC data types:** After install, open Debug Menu → Data Coverage should show expanded date ranges and more metric types
3. **Sleep:** Dashboard recovery block should show sleep data (if Fitbit synced sleep to HC)
4. **Zone Minutes:** Should now match Fitbit's zone minutes more closely (computed from HR, not estimated from calories)
5. **New metrics:** Weight, SpO2, HRV tiles should appear on dashboard (if data exists in HC)
6. **REST API (Phase 3):** Enable `googleHealthReconcile` flag → sign in with Google → pull to refresh → yesterday's data should reconcile and match Fitbit app exactly
7. **Precision test:** Compare distance values with Fitbit app — REST API path should match more closely than HC SDK path
8. **Existing tests:** `./gradlew test` — existing ZoneMinuteCalculator tests should still pass
