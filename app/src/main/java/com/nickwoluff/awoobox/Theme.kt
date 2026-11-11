package com.nickwoluff.awoobox

import android.os.Build
import android.annotation.SuppressLint
import androidx.annotation.RequiresApi
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.utilities.DynamicScheme
import com.google.android.material.color.utilities.Hct
import com.google.android.material.color.utilities.MaterialDynamicColors
import com.google.android.material.color.utilities.SchemeExpressive
import com.google.android.material.color.utilities.SchemeNeutral
import com.google.android.material.color.utilities.SchemeTonalSpot
import com.google.android.material.color.utilities.SchemeVibrant

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AwooTheme(
    preferences: ThemePreferences,
    content: @Composable () -> Unit,
) {
    val dark = when (preferences.mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        preferences.dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            remember(context, dark, preferences.paletteStyle) {
                dynamicMaterialColorScheme(context, preferences.paletteStyle, dark)
            }
        }
        else -> remember(dark, preferences.seedColor, preferences.paletteStyle) {
            generatedMaterialColorScheme(preferences.seedColor.argb, preferences.paletteStyle, dark)
        }
    }
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@RequiresApi(Build.VERSION_CODES.S)
private fun dynamicMaterialColorScheme(
    context: android.content.Context,
    paletteStyle: PaletteStyle,
    dark: Boolean,
): ColorScheme {
    val sourceArgb = context.getColor(android.R.color.system_accent1_500)
    return generatedMaterialColorScheme(sourceArgb, paletteStyle, dark)
}

/**
 * Material 3 semantic roles generated from a single seed color.
 *
 * Material Components' Scheme* generators are the canonical source for Tonal Spot, Vibrant,
 * Expressive and Neutral palettes. Compose only exposes the system dynamic palette, so custom
 * palette styles are generated here and mapped into Compose's ColorScheme roles.
 */
@SuppressLint("RestrictedApi")
private fun generatedMaterialColorScheme(
    sourceArgb: Int,
    paletteStyle: PaletteStyle,
    dark: Boolean,
): ColorScheme {
    val roles = MaterialDynamicColors()
    val source = Hct.fromInt(sourceArgb)
    val scheme: DynamicScheme = when (paletteStyle) {
        PaletteStyle.TONAL_SPOT -> SchemeTonalSpot(source, dark, 0.0)
        PaletteStyle.VIBRANT -> SchemeVibrant(source, dark, 0.0)
        PaletteStyle.EXPRESSIVE -> SchemeExpressive(source, dark, 0.0)
        PaletteStyle.NEUTRAL -> SchemeNeutral(source, dark, 0.0)
    }
    fun role(color: com.google.android.material.color.utilities.DynamicColor) =
        Color(color.getArgb(scheme))

    return (if (dark) darkColorScheme() else lightColorScheme()).copy(
        primary = role(roles.primary()),
        onPrimary = role(roles.onPrimary()),
        primaryContainer = role(roles.primaryContainer()),
        onPrimaryContainer = role(roles.onPrimaryContainer()),
        inversePrimary = role(roles.inversePrimary()),
        secondary = role(roles.secondary()),
        onSecondary = role(roles.onSecondary()),
        secondaryContainer = role(roles.secondaryContainer()),
        onSecondaryContainer = role(roles.onSecondaryContainer()),
        tertiary = role(roles.tertiary()),
        onTertiary = role(roles.onTertiary()),
        tertiaryContainer = role(roles.tertiaryContainer()),
        onTertiaryContainer = role(roles.onTertiaryContainer()),
        background = role(roles.background()),
        onBackground = role(roles.onBackground()),
        surface = role(roles.surface()),
        onSurface = role(roles.onSurface()),
        surfaceVariant = role(roles.surfaceVariant()),
        onSurfaceVariant = role(roles.onSurfaceVariant()),
        surfaceTint = role(roles.surfaceTint()),
        inverseSurface = role(roles.inverseSurface()),
        inverseOnSurface = role(roles.inverseOnSurface()),
        error = role(roles.error()),
        onError = role(roles.onError()),
        errorContainer = role(roles.errorContainer()),
        onErrorContainer = role(roles.onErrorContainer()),
        outline = role(roles.outline()),
        outlineVariant = role(roles.outlineVariant()),
        scrim = role(roles.scrim()),
        surfaceBright = role(roles.surfaceBright()),
        surfaceContainer = role(roles.surfaceContainer()),
        surfaceContainerHigh = role(roles.surfaceContainerHigh()),
        surfaceContainerHighest = role(roles.surfaceContainerHighest()),
        surfaceContainerLow = role(roles.surfaceContainerLow()),
        surfaceContainerLowest = role(roles.surfaceContainerLowest()),
        surfaceDim = role(roles.surfaceDim()),
    )
}
