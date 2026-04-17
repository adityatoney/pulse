# Fitbit / Pixel Watch Clone

A personal, sideloaded Android app that reads on-device health data via Health Connect, aggregates it locally, and syncs to a Convex backend. Styled to match Fitbit / Google Fit dashboards with a hand-rolled Activity Ring UI.

> Not intended for Play Store publication. Single-user, debug APK only.

## Stack

- **UI:** Kotlin + Jetpack Compose + Material 3
- **Architecture:** Clean Architecture + MVI + Repository pattern, 7 Gradle modules
- **Health:** `androidx.health.connect:connect-client` as single source of truth
- **Persistence:** Room + Proto DataStore
- **Sync:** WorkManager → Convex (TypeScript reactive backend)
- **Charts:** Custom Canvas Activity Rings + bar chart; Vico wired for future richer charts
- **Testing:** JUnit, Turbine, MockK, Truth; Maestro for E2E

## Module layout

```
:app                    // Application, MainActivity, NavHost
:core                   // Theme, design system, rings, chips, badges, formatters
:domain                 // Pure Kotlin entities, repositories, use cases
:data                   // Repository impls, Health Connect, Convex, Room, WorkManager, DataStore
:feature:dashboard      // Today screen (rings + today metrics)
:feature:detail         // Metric Detail (D/W/M/3M/6M/Y + charts + WoW/MoM)
:feature:debug          // Hidden Debug Menu (seed, clear, sync, feature flags)
```

## Quickstart

### One-time setup
```bash
# Java 21, Android SDK API 35, Android Studio (latest stable)
# Node 20+ for Convex

npm install                       # installs Convex CLI deps
cp local.properties.template local.properties
# edit local.properties to point CONVEX_URL at your dev deployment
```

### Port registry

Fitbit is project #7 in the local port registry (base 10710):

| Port | Role |
|---|---|
| **10710** | Convex backend API (Android client connects here) |
| **10715** | Convex site proxy (HTTP actions, public assets) |
| **10717** | Convex local dashboard UI |

These are read from `.env.local` by `scripts/convex-dev.sh`.

### Run locally — pick a backend mode

Two options for running Convex locally. The emulator always connects to
`http://10.0.2.2:10710`; what differs is **how** port 10710 is served.

#### Option 1 — Self-hosted via Docker (recommended)

Persistent, production-shaped, survives reboots, no cloud account needed.

```bash
npm run docker:up           # boot the stack (first run generates INSTANCE_SECRET)
npm run docker:push         # deploy convex/ functions to the local backend
# Or hot-reload on function changes:
npm run docker:watch
```

Endpoints after boot:
- Backend API → `http://127.0.0.1:10710`
- Site proxy → `http://127.0.0.1:10715`
- Dashboard → `http://127.0.0.1:10717`

Stopping / reset:
```bash
npm run docker:down         # stop, preserve data volume
npm run docker:wipe         # stop + delete data volume (💣 irreversible)
npm run docker:logs         # tail backend logs
npm run docker:key          # print admin key (paste into dashboard prompt)
```

See `docker-compose.yml`, `.env.docker.template`, `scripts/docker-*.sh`.

#### Option 2 — Convex CLI dev mode

Quicker to iterate for TypeScript-only changes (no Docker needed) but requires
a free Convex cloud account.

```bash
npm run dev                 # local CLI dev mode on the assigned ports
# or
npm run dev:cloud           # cloud dev deployment (uses your Convex account)
```

#### Android side

```bash
./gradlew :app:installDebug
# or hit ▶ in Android Studio with the Pixel 10 Pro API 35 emulator running
```

Emulator reaches the laptop's Convex backend via the loopback
`10.0.2.2:10710`. For a real device on the same LAN, point `CONVEX_URL` in
`local.properties` at your laptop's LAN IP (still port 10710).

### Seeding test data

Three paths:

1. **In-app Debug Menu** (recommended) — long-press the sync chip in the top bar
   or 5-tap the battery chip. Actions: Seed 90 days, Seed realistic week,
   Export CSV, Force sync, Simulate network failure, toggle feature flags.
2. **Deep link** — `adb shell am start -W -a android.intent.action.VIEW -d "fitbit-clone://debug"`
3. **Health Connect Toolbox** — `adb install health-connect-toolbox.apk`
   (from `github.com/android/health-samples`) and seed via its UI.

### Tests

```bash
./gradlew :domain:test              # JVM unit tests (WoW/MoM, ZoneMinute, series math)
./gradlew :data:test                # Room DAO + mapper tests
./gradlew check                     # Everything
maestro test .maestro/              # E2E flows on a running emulator
```

### Sideloading to your phone

```bash
# Wired
./gradlew :app:installDebug

# Wireless (Android 11+)
adb pair <ip:port>
adb install app/build/outputs/apk/debug/app-debug.apk
```

No signing key required — debug signing is fine for sideload.

## Dashboard screen architecture

```
DashboardRoute → DashboardScreen (Scaffold)
  topBar:    BatteryChip | fitbit | SyncStatusChip + Coach + Profile
  body:      DateScrollerRow → ActivityRingHero → SecondaryRingRow
             → WoWMoMBadges → Recovery section
  bottomBar: Today | Coach | You
  fab:       "+" (logs manual exercise — sprint 3)
```

The hero ring uses Compose `Canvas.drawArc(startAngle=135, sweep=270)` with a
`Brush.sweepGradient`, `StrokeCap.Round`, and an `animateFloatAsState` bouncy
spring. Progress can overshoot to 1.25× (caps arc at 360°).

## MVI contracts

Per feature: `FooState`, `FooIntent`, `FooEffect`. ViewModels expose a
`StateFlow<State>` and a `Channel<Effect>` (collected via
`flowWithLifecycle(..., STARTED)` in the Route composable to avoid duplicated
navigation on rotation). `SavedStateHandle` persists `selectedDate` and
`timeframe` across process death.

## Convex schema

```
users · goals · healthSamples · dailyAggregates · exerciseSessions · syncState
```

All mutations are user-scoped and idempotent (keyed by `clientId` or
`(userId, date, metric)` with a monotonic `version`). Queries are reactive —
the Android client subscribes to `getDashboard(date)` and `getSyncStatus(deviceId)`.

## Known flagged items

- **Convex Android SDK:** bridged via a thin wrapper in `data/cloud/ConvexDataSource.kt`.
  In the current checkout it runs in an in-memory fallback mode; flip `useRealClient`
  once you've pointed `BuildConfig.CONVEX_URL` at a live deployment.
- **Google Health REST:** Google Fit REST shut down June 2025. We ship a
  `NoopGoogleHealthRemoteDataSource`; wire a real impl when Health Connect REST GAs.
- **Zone Minutes:** HC has no native record. Derived from HR samples via
  `ZoneMinuteCalculator` (fat-burn 1×, cardio 2×, peak 2×, HRR thresholds 0.5/0.7/0.85).
- **Play Store features** (in-app review, listing, signing, privacy policy URL)
  intentionally omitted — this app is sideload-only.
