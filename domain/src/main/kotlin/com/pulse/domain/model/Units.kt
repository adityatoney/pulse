package com.pulse.domain.model

/** Typed measurement units to keep conversions honest. */
sealed interface MeasurementUnit {
    data object Count : MeasurementUnit
    data object Meters : MeasurementUnit
    data object Miles : MeasurementUnit
    data object Kilocalories : MeasurementUnit
    data object Bpm : MeasurementUnit
    data object Minutes : MeasurementUnit
    data object Floors : MeasurementUnit
    data object MetersPerSecond : MeasurementUnit
    data object Kilograms : MeasurementUnit
    data object Percent : MeasurementUnit
    data object Celsius : MeasurementUnit
    data object Milliseconds : MeasurementUnit
}

/** Where a piece of data originally came from. */
enum class DataSource {
    HealthConnect,
    GoogleHealth,
    Manual,
    Seeded,
}

enum class Cadence { Daily, Weekly }

enum class TrendDirection { Up, Down, Flat }

enum class Aggregation { Sum, Avg, Max, Min }

/** Timeframe tabs shown on MetricDetail (D/W/M/3M/6M/Y). */
enum class Timeframe {
    Day,
    Week,
    Month,
    ThreeMonths,
    SixMonths,
    Year;

    val label: String
        get() = when (this) {
            Day -> "Day"
            Week -> "Week"
            Month -> "Month"
            ThreeMonths -> "3M"
            SixMonths -> "6M"
            Year -> "Year"
        }
}
