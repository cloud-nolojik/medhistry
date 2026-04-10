package com.medhistry.patient.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * MedHistry brand colours — matched to the app icon palette:
 *   dark navy bg   #0E2A3A → #163E4E
 *   primary teal   #50C8DC
 *   primary light  #E0F7FA
 *   cyan glow      #78DCE8
 *   accent (ok)    #2ECC71
 *   danger         #E74C3C
 *   warn           #F39C12
 */
object MedHistryColors {
    val Primary = Color(0xFF50C8DC)
    val PrimaryDark = Color(0xFF3AA8BC)
    val PrimaryLight = Color(0xFFE0F7FA)
    val NavyDark = Color(0xFF0E2A3A)
    val NavyLight = Color(0xFF163E4E)
    val CyanGlow = Color(0xFF78DCE8)
    val Accent = Color(0xFF2ECC71)
    val AccentDark = Color(0xFF27AE60)
    val Danger = Color(0xFFE74C3C)
    val Warn = Color(0xFFF39C12)
    val TextPrimary = Color(0xFF1A1A2E)
    val TextSecondary = Color(0xFF6B7280)
    val TextLight = Color(0xFF9CA3AF)
    val Background = Color(0xFFF8FAFC)
    val Surface = Color(0xFFFFFFFF)
    val Border = Color(0xFFE5E7EB)
}

private val LightColors = lightColorScheme(
    primary = MedHistryColors.Primary,
    onPrimary = Color.White,
    primaryContainer = MedHistryColors.PrimaryLight,
    onPrimaryContainer = MedHistryColors.PrimaryDark,
    secondary = MedHistryColors.Accent,
    onSecondary = Color.White,
    background = MedHistryColors.Background,
    onBackground = MedHistryColors.TextPrimary,
    surface = MedHistryColors.Surface,
    onSurface = MedHistryColors.TextPrimary,
    onSurfaceVariant = MedHistryColors.TextSecondary,
    error = MedHistryColors.Danger,
    outline = MedHistryColors.Border,
)

@Composable
fun MedHistryTheme(content: @Composable () -> Unit) {
    // Force light theme — the prototype is designed light-only for v1.
    @Suppress("UNUSED_VARIABLE")
    val dark = isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = LightColors,
        content = content,
    )
}
