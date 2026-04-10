package com.medhistry.doctor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * MedHistry Doctor brand colours — matched to the app icon palette:
 *   dark navy bg   #0E2A3A → #163E4E
 *   primary teal   #50C8DC
 *   cyan glow      #78DCE8
 */
object DoctorColors {
    val Primary = Color(0xFF50C8DC)
    val PrimaryDark = Color(0xFF3AA8BC)
    val PrimaryLight = Color(0xFFE0F7FA)
    val NavyDark = Color(0xFF0E2A3A)
    val NavyLight = Color(0xFF163E4E)
    val CyanGlow = Color(0xFF78DCE8)
    val Accent = Color(0xFF2ECC71)
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
    primary = DoctorColors.Primary,
    onPrimary = Color.White,
    primaryContainer = DoctorColors.PrimaryLight,
    onPrimaryContainer = DoctorColors.PrimaryDark,
    secondary = DoctorColors.Accent,
    onSecondary = Color.White,
    background = DoctorColors.Background,
    onBackground = DoctorColors.TextPrimary,
    surface = DoctorColors.Surface,
    onSurface = DoctorColors.TextPrimary,
    onSurfaceVariant = DoctorColors.TextSecondary,
    error = DoctorColors.Danger,
    outline = DoctorColors.Border,
)

@Composable
fun MedHistryDoctorTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = LightColors, content = content)
}
