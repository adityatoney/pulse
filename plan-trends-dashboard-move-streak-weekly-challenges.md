# Plan: Trends Dashboard + Move Streak & Weekly Challenges

## Context

The user chose two Apple-inspired features to add to Pulse:

1. **Trends Dashboard** — A section in the Insights screen showing sparkline trend arrows for key metrics, comparing the last 30 days vs the prior 30 days. Inspired by Apple Fitness's Trends tab.
2. **Move Streak & Weekly Challenges** — A streak counter on the Dashboard showing consecutive days of closing all 3 activity rings, plus auto-generated weekly challenges on the Insights screen.

These features build on existing infrastructure: `CalculateWoWUseCase` pattern for use cases, `HealthRepository.observeSeries()` for data, `GoalsRepository.observeGoals()` for goal resolution, and the MVI state/intent/effect pattern used throughout.

---

## Feature 1: Trends Dashboard

### Step 1.1: Domain — `CalculateTrendsUseCase`

**New file: `domain/src/main/kotlin/com/pulse/domain/usecase/CalculateTrendsUseCase.kt`**

Follow the `CalculateWoWUseCase` pattern (constructor-injected `HealthRepository`, `operator fun invoke` returning `Flow`).

```kotlin
class CalculateTrendsUseCase @Inject constructor(
    private val health: HealthRepository,
) {
    operator fun invoke(metrics: List<MetricType>, anchor: LocalDate): Flow<List<MetricTrend>>
}
```

For each metric in the list:
- Fetch 60 days of daily data via `health.observeSeries(metric, DateRange(anchor - 60d, anchor), Bucket.Day)`
- Split into two 30-day windows: `recent` (last 30 days) and `prior` (days 31-60)
- Compute: `recentAvg`, `priorAvg`, `deltaPercent = DeltaPercent.from(recentAvg, priorAvg)`
- Extract last 30 daily values as `sparklinePoints: List<Float>` (normalized 0-1 for rendering)
- Return `MetricTrend` per metric

### Step 1.2: Domain — `MetricTrend` Model

**Add to `domain/src/main/kotlin/com/pulse/domain/model/HealthMetric.kt`** (alongside existing `DeltaPercent`):

```kotlin
data class MetricTrend(
    val metric: MetricType,
    val recentAvg: Double,
    val priorAvg: Double,
    val delta: DeltaPercent?,
    val sparklinePoints: List<Float>,  // normalized 0-1, last 30 days
)
```

### Step 1.3: State — Add trends to `InsightsState`

**`feature/insights/src/main/kotlin/com/pulse/feature/insights/state/InsightsState.kt`**

Add field:
```kotlin
val trends: List<MetricTrend> = emptyList(),
```

### Step 1.4: ViewModel — Wire trends into `InsightsViewModel`

**`feature/insights/src/main/kotlin/com/pulse/feature/insights/viewmodel/InsightsViewModel.kt`**

- Inject `CalculateTrendsUseCase` in constructor
- In `wireStreams()`, add a new stream:
```kotlin
val trendMetrics = listOf(
    MetricType.Steps, MetricType.Distance, MetricType.ActiveCalories,
    MetricType.ZoneMinutes, MetricType.Sleep, MetricType.RestingHeartRate,
)
calculateTrends(trendMetrics, todayDate).onEach { trends ->
    _state.update { it.copy(trends = trends) }
}.launchIn(viewModelScope)
```

### Step 1.5: UI — `Sparkline` Composable

**New file: `core/src/main/kotlin/com/pulse/core/ui/chart/Sparkline.kt`**

Minimal Canvas composable drawing a polyline from normalized points:

```kotlin
@Composable
fun Sparkline(
    points: List<Float>,       // normalized 0-1
    color: Color,
    modifier: Modifier = Modifier,
)
```

- Size: fills modifier (caller sets height ~32dp, width ~80dp)
- Draws a smooth line path through points
- No axes, labels, or grid — pure sparkline

### Step 1.6: UI — `TrendCard` Composable

**New file: `feature/insights/src/main/kotlin/com/pulse/feature/insights/ui/components/TrendCard.kt`**

A `Card` showing one metric's trend:

```
┌──────────────────────────────┐
│  Steps                       │
│  ↑ 12%    ~~~~~~~~           │
│  8,432 avg    (sparkline)    │
└──────────────────────────────┘
```

- Metric name (label)
- Trend arrow + delta percentage (green ↑ / red ↓ / gray →), using `TrendDirection` from `DeltaPercent`
- Current 30-day average
- Sparkline on the right side
- For RestingHeartRate and Sleep, invert the trend sentiment (lower RHR = good = green)

### Step 1.7: UI — Trends Section in `InsightsScreen`

**`feature/insights/src/main/kotlin/com/pulse/feature/insights/ui/InsightsScreen.kt`**

Add a new "Trends" section between "Right Now" and "This Week":

```kotlin
// ── Trends ──
if (state.trends.isNotEmpty()) {
    Spacer(Modifier.height(8.dp))
    SectionHeader("Trends")
    // 2-column LazyVerticalGrid or FlowRow of TrendCards
    // Display all 6 metrics in a 2x3 grid
}
```

Use a 2-column layout (two cards per row) via `FlowRow` or manual `Row` pairs, since we're inside a `verticalScroll` Column (can't nest `LazyVerticalGrid`).

---

## Feature 2: Move Streak

### Step 2.1: Domain — `ComputeStreakUseCase`

**New file: `domain/src/main/kotlin/com/pulse/domain/usecase/ComputeStreakUseCase.kt`**

```kotlin
class ComputeStreakUseCase @Inject constructor(
    private val health: HealthRepository,
    private val goalsRepo: GoalsRepository,
) {
    operator fun invoke(anchor: LocalDate): Flow<MoveStreak>
}
```

Logic:
- Fetch last 365 days of Steps, ActiveCalories, Distance via `combine()` of 3 `observeSeries()` flows
- Fetch goals via `goalsRepo.observeGoals()` — use `Goal.target` for each metric, fallback to defaults (10k/500/5)
- For each date, check if all 3 metrics met their goals
- Walk backwards from `anchor` counting consecutive goal-met days = `currentStreak`
- Find the longest consecutive run in the 365-day window = `longestStreak`
- Return `MoveStreak(current, longest, lastClosedDate)`

### Step 2.2: Domain — `MoveStreak` Model

**Add to `domain/src/main/kotlin/com/pulse/domain/model/HealthMetric.kt`**:

```kotlin
data class MoveStreak(
    val currentStreak: Int,
    val longestStreak: Int,
    val lastClosedDate: LocalDate?,
)
```

### Step 2.3: State — Add streak to `DashboardState`

**`feature/dashboard/src/main/kotlin/com/pulse/feature/dashboard/state/DashboardState.kt`**

Add field:
```kotlin
val moveStreak: MoveStreak? = null,
```

### Step 2.4: ViewModel — Wire streak into `DashboardViewModel`

**`feature/dashboard/src/main/kotlin/com/pulse/feature/dashboard/viewmodel/DashboardViewModel.kt`**

- Inject `ComputeStreakUseCase` in constructor
- In the existing `wireStreams()` or `loadData()` method, add:
```kotlin
computeStreak(todayDate).onEach { streak ->
    _state.update { it.copy(moveStreak = streak) }
}.launchIn(viewModelScope)
```

### Step 2.5: UI — `StreakBadge` Composable

**New file: `core/src/main/kotlin/com/pulse/core/ui/streak/StreakBadge.kt`**

A compact badge showing the current move streak:

```
🔥 12-day streak
```

- Fire icon (Material `LocalFire` or emoji) + streak count + "day streak" label
- Background: subtle warm gradient when streak > 0, muted when 0
- Shows "longest: X days" as secondary text when current < longest
- Compact enough to fit between the ring tiles and the insights section on Dashboard

### Step 2.6: UI — Place `StreakBadge` on Dashboard

**`feature/dashboard/src/main/kotlin/com/pulse/feature/dashboard/ui/DashboardScreen.kt`**

Add `StreakBadge` below the activity ring tiles row, above the insights/exercises section. Renders only when `state.moveStreak != null && state.moveStreak.currentStreak > 0`.

---

## Feature 3: Weekly Challenges

### Step 3.1: Domain — `GenerateWeeklyChallengesUseCase`

**New file: `domain/src/main/kotlin/com/pulse/domain/usecase/GenerateWeeklyChallengesUseCase.kt`**

```kotlin
class GenerateWeeklyChallengesUseCase @Inject constructor(
    private val health: HealthRepository,
    private val goalsRepo: GoalsRepository,
) {
    operator fun invoke(anchor: LocalDate): Flow<List<WeeklyChallenge>>
}
```

Logic:
- Fetch last 2 weeks of data for Steps, ActiveCalories, Distance
- Compute last week's performance per metric (daily avg, days goal met)
- Generate 2-3 challenges for the current week based on patterns:
  - **Beat Your Average**: "Average 9,000+ steps this week" (last week avg + 5-10%)
  - **Close All Rings**: "Close all 3 rings X days this week" (last week count + 1, capped at 7)
  - **Metric-Specific Push**: If one metric lagged, target it: "Hit your calorie goal 5 days"
- Track progress: compute current week's progress toward each challenge
- Return `List<WeeklyChallenge>` (2-3 items)

### Step 3.2: Domain — `WeeklyChallenge` Model

**Add to `domain/src/main/kotlin/com/pulse/domain/model/HealthMetric.kt`**:

```kotlin
data class WeeklyChallenge(
    val id: String,                  // stable ID for the week
    val title: String,               // "Average 9,000+ steps"
    val description: String,         // "You averaged 8,200 last week. Push a little further!"
    val metric: MetricType?,         // null for multi-metric challenges
    val targetValue: Double,
    val currentValue: Double,
    val progress: Float,             // currentValue / targetValue, capped at 1.0
    val isComplete: Boolean,
)
```

### Step 3.3: State — Add challenges to `InsightsState`

**`feature/insights/src/main/kotlin/com/pulse/feature/insights/state/InsightsState.kt`**

Add field:
```kotlin
val weeklyChallenges: List<WeeklyChallenge> = emptyList(),
```

### Step 3.4: ViewModel — Wire challenges into `InsightsViewModel`

**`feature/insights/src/main/kotlin/com/pulse/feature/insights/viewmodel/InsightsViewModel.kt`**

- Inject `GenerateWeeklyChallengesUseCase` in constructor
- In `wireStreams()`:
```kotlin
generateChallenges(todayDate).onEach { challenges ->
    _state.update { it.copy(weeklyChallenges = challenges) }
}.launchIn(viewModelScope)
```

### Step 3.5: UI — `ChallengeCard` Composable

**New file: `feature/insights/src/main/kotlin/com/pulse/feature/insights/ui/components/ChallengeCard.kt`**

A card showing one weekly challenge:

```
┌──────────────────────────────┐
│  Average 9,000+ steps        │
│  You averaged 8,200 last wk  │
│  ████████░░░░  6,800 / 9,000 │
│                    76%       │
└──────────────────────────────┘
```

- Title + description
- `LinearProgressIndicator` showing progress
- Current value / target value
- Checkmark overlay when complete
- Green accent for completed challenges

### Step 3.6: UI — Challenges in `InsightsScreen` "This Week" Section

**`feature/insights/src/main/kotlin/com/pulse/feature/insights/ui/InsightsScreen.kt`**

Add challenges inside the existing "This Week" section, before the weekly bar chart:

```kotlin
// Weekly challenges
if (state.weeklyChallenges.isNotEmpty()) {
    SubSectionLabel("Challenges")
    state.weeklyChallenges.forEach { challenge ->
        ChallengeCard(challenge = challenge)
    }
}
```

Also update the `hasAnyContent` check to include `state.weeklyChallenges.isNotEmpty()` and `state.trends.isNotEmpty()`.

---

## Critical Files

| File | Action |
|------|--------|
| `domain/.../model/HealthMetric.kt` | Add `MetricTrend`, `MoveStreak`, `WeeklyChallenge` models |
| `domain/.../usecase/CalculateTrendsUseCase.kt` | **New** — 30d vs prior 30d comparison per metric |
| `domain/.../usecase/ComputeStreakUseCase.kt` | **New** — consecutive ring-closure days |
| `domain/.../usecase/GenerateWeeklyChallengesUseCase.kt` | **New** — auto-generated weekly challenges |
| `feature/insights/.../state/InsightsState.kt` | Add `trends`, `weeklyChallenges` fields |
| `feature/insights/.../viewmodel/InsightsViewModel.kt` | Inject + wire trends and challenges use cases |
| `feature/insights/.../ui/InsightsScreen.kt` | Add Trends section, challenge cards in This Week |
| `feature/insights/.../ui/components/TrendCard.kt` | **New** — trend card with sparkline + delta |
| `feature/insights/.../ui/components/ChallengeCard.kt` | **New** — challenge progress card |
| `core/.../ui/chart/Sparkline.kt` | **New** — minimal Canvas sparkline composable |
| `core/.../ui/streak/StreakBadge.kt` | **New** — fire icon + streak count badge |
| `feature/dashboard/.../state/DashboardState.kt` | Add `moveStreak` field |
| `feature/dashboard/.../viewmodel/DashboardViewModel.kt` | Inject + wire `ComputeStreakUseCase` |
| `feature/dashboard/.../ui/DashboardScreen.kt` | Add `StreakBadge` below ring tiles |

**Existing code reused** (no modifications needed):
- `CalculateWoWUseCase` (`domain/.../usecase/`) — pattern template for new use cases
- `DeltaPercent.from()` (`domain/.../model/HealthMetric.kt`) — reused for trend computation
- `HealthRepository.observeSeries()` — all data fetching
- `GoalsRepository.observeGoals()` — goal resolution for streak + challenges
- `LocalRingPalette` (`core/.../theme/Theme.kt`) — colors for trend cards

---

## Implementation Order

1. Domain models (`MetricTrend`, `MoveStreak`, `WeeklyChallenge`) in `HealthMetric.kt`
2. `CalculateTrendsUseCase` → `InsightsState` → `InsightsViewModel` wiring
3. `Sparkline` + `TrendCard` composables → Trends section in `InsightsScreen`
4. `ComputeStreakUseCase` → `DashboardState` → `DashboardViewModel` wiring
5. `StreakBadge` → placement in `DashboardScreen`
6. `GenerateWeeklyChallengesUseCase` → `InsightsState` → `InsightsViewModel` wiring
7. `ChallengeCard` → placement in `InsightsScreen`

---

## Verification

1. **Trends section**: 6 metric cards in 2-column layout between "Right Now" and "This Week". Each shows metric name, trend arrow, delta %, 30-day average, and sparkline.
2. **Trend accuracy**: Verify trend direction matches actual data — compare Steps last 30d avg vs prior 30d avg manually.
3. **Sparkline rendering**: Smooth polyline, no clipping, proper normalization (min point at bottom, max at top).
4. **Inverted sentiment**: RestingHeartRate decrease shows green ↑ (improvement), increase shows red ↓.
5. **Streak badge**: Shows on Dashboard below ring tiles. Fire icon + "X-day streak". Shows "longest: Y days" when current < longest. Hidden when streak is 0.
6. **Streak accuracy**: Close all 3 rings today, verify streak increments. Break one ring tomorrow, verify streak resets to 0.
7. **Weekly challenges**: 2-3 cards appear in Insights "This Week" section. Progress bars update as current week progresses.
8. **Challenge generation**: Challenges are contextual — if steps lagged last week, a steps-focused challenge appears. Targets are ~5-10% above last week's performance.
9. **Completed challenges**: Progress bar fills to 100%, checkmark appears, green accent.
10. **Empty states**: No trends section when no data. No challenges when insufficient history (< 1 week). Streak badge hidden when 0.
11. **Build**: `./gradlew :feature:insights:assembleDebug :feature:dashboard:assembleDebug` passes.
