package com.pulse.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.os.Build
import androidx.compose.foundation.shape.RoundedCornerShape

private val LightColors = lightColorScheme(
    primary = Forest900,
    onPrimary = Cream50,
    primaryContainer = Sage100,
    onPrimaryContainer = Forest900,
    secondary = Forest500,
    onSecondary = Cream50,
    secondaryContainer = Sage300,
    onSecondaryContainer = Forest900,
    tertiary = Coral500,
    onTertiary = Cream50,
    background = Cream50,
    onBackground = Ink900,
    surface = Cream100,
    onSurface = Ink900,
    surfaceVariant = Sage100,
    onSurfaceVariant = Ink700,
    outline = Forest500.copy(alpha = 0.3f),
    outlineVariant = Sage300,
    error = ErrorRed,
    onError = Cream50,
)

private val DarkColors = darkColorScheme(
    primary = Sage300,
    onPrimary = Ink900,
    primaryContainer = Forest700,
    onPrimaryContainer = Sage100,
    secondary = Sage500,
    onSecondary = Ink900,
    secondaryContainer = Forest500,
    onSecondaryContainer = Cream50,
    tertiary = CoralLight,
    background = Color(0xFF0B0F0D),
    onBackground = Cream50,
    surface = Color(0xFF141A17),
    onSurface = Cream50,
    surfaceVariant = Color(0xFF1E2622),
    onSurfaceVariant = Sage300,
    outline = Sage300.copy(alpha = 0.3f),
    error = Color(0xFFF2B8B5),
    onError = Ink900,
)

val FitbitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

/** Per-ring gradient pack. Each ring uses a 2-hue sweep for depth. */
data class RingPalette(
    val steps: Brush,
    val distance: Brush,
    val calories: Brush,
    val zone: Brush,
    val track: Color,
)

val LocalRingPalette = staticCompositionLocalOf<RingPalette> {
    error("RingPalette not provided")
}

@Composable
fun FitbitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val rings = RingPalette(
        steps = Brush.sweepGradient(listOf(Forest300, Forest900, Forest300)),
        distance = Brush.sweepGradient(listOf(SkyLight, Sky500, SkyLight)),
        calories = Brush.sweepGradient(listOf(CoralLight, Coral500, CoralLight)),
        zone = Brush.sweepGradient(listOf(MustardLight, Mustard500, MustardLight)),
        track = scheme.surfaceVariant,
    )

    CompositionLocalProvider(LocalRingPalette provides rings) {
        MaterialTheme(
            colorScheme = scheme,
            typography = FitbitTypography,
            shapes = FitbitShapes,
            content = content,
        )
    }
}
