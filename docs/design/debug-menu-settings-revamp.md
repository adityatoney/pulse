# Plan: Debug Menu & Settings Revamp

## Context

The Debug Menu has grown into a catch-all with 9 sections mixing dev tools, user settings, and data provider management. This revamp:
- Strips debug to bare essentials (seed data, cache, export, dev flags, info)
- Moves user-facing settings (dark mode, dynamic color) to You screen
- Consolidates all data provider management (Fitbit, HC, Google Health) in You with a single "Sync Now" button
- Always shows Cloud Backup in You (removes feature flag gate)
- Removes dead feature flags (SharedElementTransitions, VicoGradientBars)

---

## Debug Menu — After (6 sections → stripped to essentials)

### Keep
1. **Data**: Seed fake data, Seed realistic week, Clear cache, Hard reset, Export CSV, Export backup JSON
2. **Feature Flags**: WoW/MoM on dashboard, Google Health reconciliation (dev-only flags)
3. **Data Coverage**: Simplified (step range, total days, exercises, sleep — compact)
4. **Info**: Version, Git SHA, Device, HC SDK
5. **Last action log**: As-is

### Remove entirely
- **Sync section** (Force sync, Simulate network failure) — sync moves to You > Data Providers
- **Health Connect section** (Open HC app, Dump raw records, Reset change token)
- **Google Health API section** (Sign in/out → moves to Data Providers)
- **Fitbit API section** (Connect/disconnect/sync → already in Data Providers)
- **Background Sync section** (backfill status, sync window)
- **Feature flag toggles for**: SharedElementTransitions, VicoGradientBars, ForceDarkMode, UseDynamicColor, DriveBackupEnabled

---

## You Screen — After

Current order (unchanged items keep position):
1. Profile Hero
2. Today Snapshot
3. Daily Goals
4. Heart Rate Zones
5. Body Stats
6. **Data Providers Card** (ENHANCED — add Google Health row + Sync Now button)
7. **Appearance Card** (NEW — dark mode + dynamic color toggles)
8. Dashboard Metrics Card (existing, unchanged)
9. **Cloud Backup Card** (ALWAYS SHOWN — remove `backupEnabled` feature flag gate)

### Enhanced Data Providers Card

Add Google Health as third provider row + a "Sync Now" button:

```
┌─────────────────────────────────────────┐
│ 🔗 Data Providers                       │
│ Connect data sources for richer history │
│                                         │
│ [🟢] Fitbit        Synced to 2026-04-18 │  [Disconnect]
│ [🟢] Health Connect  On-device, ~30 days │  (always on)
│ [🔴] Google Health    Cloud health archive│  [Connect]
│                                         │
│            [ Sync Now ]                 │
│                                         │
└─────────────────────────────────────────┘
```

- Google Health row follows same `ProviderRow` pattern as Fitbit/HC
- "Sync Now" button triggers HC sync worker (same as debug's ForceSyncNow) + Fitbit sync if connected
- Show sync status feedback inline (syncing spinner, "Last synced X ago")

### New Appearance Card

```
┌─────────────────────────────────────────┐
│ 🎨 Appearance                           │
│                                         │
│ Dark mode              [toggle]         │
│ Dynamic colors         [toggle]         │
└─────────────────────────────────────────┘
```

Uses same `PrefToggleRow` composable as Dashboard Metrics card. Toggles call `featureFlags.setFlag()` (same storage, just moved from debug UI).

---

## Feature Flag Changes

### Remove from enum + snapshot + proto
- `SharedElementTransitions` — not consumed anywhere, hardcode true
- `VicoGradientBars` — not consumed anywhere, hardcode true
- `DriveBackupEnabled` — backup always shown in You

### Keep in FeatureFlagKey (toggle from different UI)
- `ForceDarkMode` — toggle moves from debug → You > Appearance
- `UseDynamicColor` — toggle moves from debug → You > Appearance
- `WowMomOnDashboard` — stays in debug
- `GoogleHealthReconcile` — stays in debug

---

## Implementation Steps

### Step 1: Feature flags cleanup

**`data/src/main/proto/feature_flags.proto`**
- Remove fields: `shared_element_transitions`, `vico_gradient_bars`, `drive_backup_enabled`

**`data/.../datastore/FeatureFlagRepository.kt`**
- Remove `SharedElementTransitions`, `VicoGradientBars`, `DriveBackupEnabled` from `FeatureFlagKey` enum
- Remove `sharedElementTransitions`, `vicoGradientBars`, `driveBackupEnabled` from `FeatureFlagSnapshot`
- Remove their cases from `setFlag()` and `observe()` mapping
- Update `Default` companion object

**`data/.../datastore/FeatureFlagsSerializer.kt`**
- Remove references to removed proto fields in the mapper

### Step 2: You screen — always show backup

**`feature/you/state/YouState.kt`**
- Remove `backupEnabled: Boolean = false`

**`feature/you/viewmodel/YouViewModel.kt`**
- Remove `observeFeatureFlags()` method (was only used for `driveBackupEnabled`)
- Remove its call from `init {}`

**`feature/you/ui/YouScreen.kt`**
- Remove `if (state.backupEnabled)` gate on `CloudBackupCard` — always render it

### Step 3: You screen — Appearance card

**`feature/you/state/YouState.kt`**
- Add: `forceDarkMode: Boolean = false`, `useDynamicColor: Boolean = false`

**`feature/you/state/YouIntent.kt`** (inside YouState.kt)
- Add: `data class SetDarkMode(val enabled: Boolean) : YouIntent`
- Add: `data class SetDynamicColor(val enabled: Boolean) : YouIntent`

**`feature/you/viewmodel/YouViewModel.kt`**
- Re-add `observeFeatureFlags()` but observe `forceDarkMode` and `useDynamicColor` instead of `driveBackupEnabled`
- Add intent handlers for `SetDarkMode` and `SetDynamicColor` → call `featureFlags.setFlag()`

**`feature/you/ui/YouScreen.kt`**
- Add `AppearanceCard` composable using `PrefToggleRow` (reuse existing composable)
- Place between Body Stats and Dashboard Metrics cards in the LazyColumn

### Step 4: You screen — Enhanced Data Providers + Sync

**`feature/you/state/YouState.kt`**
- Add: `googleHealthSignedIn: Boolean = false`
- Add: `syncing: Boolean = false`, `syncMessage: String? = null`

**`feature/you/state/YouIntent.kt`** (inside YouState.kt)
- Add: `data object GoogleHealthSignIn : YouIntent`
- Add: `data object GoogleHealthSignOut : YouIntent`
- Add: `data object SyncNow : YouIntent`

**`feature/you/state/YouEffect.kt`** (inside YouState.kt)
- Add: `data object LaunchGoogleHealthSignIn : YouEffect`

**`feature/you/viewmodel/YouViewModel.kt`**
- Add constructor params: `val googleHealthAuthManager: GoogleHealthAuthManager`, `private val syncScheduler: SyncScheduler` (or `DebugRepository` which has `forceSyncNow()`)
- Add `loadGoogleHealthStatus()` in init
- Handle `GoogleHealthSignIn` → emit `LaunchGoogleHealthSignIn` effect
- Handle `GoogleHealthSignOut` → call `googleHealthAuthManager.signOut()`
- Handle `SyncNow` → call `debug.forceSyncNow()` (enqueues HC sync worker) + if Fitbit connected, trigger `fitbitSyncManager.sync()`
- Add `onGoogleSignInResult()` callback

**`feature/you/ui/YouScreen.kt`**
- Add Google Health `ProviderRow` to `DataProvidersCard`
- Add "Sync Now" button at bottom of `DataProvidersCard`
- Handle `LaunchGoogleHealthSignIn` effect in `YouRoute`

### Step 5: Debug menu — strip down

**`feature/debug/state/DebugMenuState.kt`**
- Remove: `syncWorkerState`, `googleHealthSignedIn`, `fitbitSignedIn`, `fitbitSyncCursor`, `fitbitSyncProgress`, `backfillCursor`, `backfillComplete`, `syncWindowStart`, `syncWindowEnd`
- Remove: `SyncWorkerState` enum

**`feature/debug/state/DebugMenuIntent.kt`** (inside DebugMenuState.kt)
- Remove: `ForceSyncNow`, `SimulateNetworkFailure`, `OpenHealthConnect`, `ResetChangeToken`, `DumpRecords`, `GoogleHealthSignIn`, `GoogleHealthSignOut`, `FitbitSignIn`, `FitbitSignOut`, `ForceFitbitSync`

**`feature/debug/state/DebugMenuEffect.kt`** (inside DebugMenuState.kt)
- Remove: `OpenHealthConnectApp`, `NavigateToRecordDump`, `LaunchGoogleSignIn`, `LaunchFitbitSignIn`

**`feature/debug/viewmodel/DebugMenuViewModel.kt`**
- Remove constructor params: `authManager`, `fitbitAuthManager`, `fitbitSyncManager`, `syncScheduler`, `workManager`
- Remove `observeSyncWorker()`, `loadFitbitCursor()`, `onSignInResult()`
- Remove intent handlers for all removed intents
- Keep: Load, SeedFakeData, SeedRealisticWeek, RequestClearCache, RequestHardReset, ConfirmDestructive, CancelConfirm, ExportCsv, ExportBackup, ToggleFlag, Dismiss

**`feature/debug/ui/DebugMenuScreen.kt`**
- Remove: Sync section, Health Connect section, Google Health API section, Fitbit API section, Background Sync section
- Remove: Feature flag rows for SharedElementTransitions, VicoGradientBars, ForceDarkMode, UseDynamicColor, DriveBackupEnabled
- Keep: Data section, Feature Flags (WoW/MoM + GoogleHealthReconcile only), Data Coverage (simplified), Info, Last action
- Remove: `SyncActionItem` composable
- Remove effect handlers: `OpenHealthConnectApp`, `LaunchGoogleSignIn`, `LaunchFitbitSignIn`
- Simplify Data Coverage to just show key stats in compact form

---

## Critical Files

| File | Action |
|------|--------|
| `data/src/main/proto/feature_flags.proto` | Remove 3 proto fields |
| `data/.../datastore/FeatureFlagRepository.kt` | Remove 3 enum entries + snapshot fields |
| `data/.../datastore/FeatureFlagsSerializer.kt` | Remove references to removed fields |
| `feature/you/state/YouState.kt` | Remove `backupEnabled`, add appearance + google health + sync state |
| `feature/you/viewmodel/YouViewModel.kt` | Add appearance toggles, Google Health, Sync Now |
| `feature/you/ui/YouScreen.kt` | Always show backup, add Appearance card, enhance Data Providers |
| `feature/debug/state/DebugMenuState.kt` | Remove sync/provider state, SyncWorkerState enum |
| `feature/debug/viewmodel/DebugMenuViewModel.kt` | Remove provider/sync deps + handlers |
| `feature/debug/ui/DebugMenuScreen.kt` | Strip to essentials |

---

## Verification

1. **Debug menu**: Only shows Data, Feature Flags (2 toggles), Data Coverage, Info sections
2. **You > Data Providers**: Shows Fitbit, Health Connect, Google Health rows + "Sync Now" button
3. **You > Appearance**: Dark mode and Dynamic color toggles work, theme updates live
4. **You > Cloud Backup**: Always visible (no feature flag gate), backup/restore works
5. **Theme**: `MainActivity` still reads `forceDarkMode`/`useDynamicColor` from same FeatureFlagRepository — toggles from You screen update theme immediately
6. **Removed flags**: SharedElementTransitions, VicoGradientBars effectively hardcoded to true (removed from storage, code never reads them)
7. **No regressions**: Seed data, clear cache, export CSV, export backup still work from debug
