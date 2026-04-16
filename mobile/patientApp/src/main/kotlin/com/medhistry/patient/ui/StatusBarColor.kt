package com.medhistry.patient.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * Paints the system status bar so it blends with the screen's background
 * and flips the icon tint so time/battery stay legible.
 *
 * Drop this at the top of any screen composable whose background doesn't
 * match the default light app theme. On leave, the previous colour & icon
 * style are restored, so other screens keep their look.
 *
 * @param color Status bar background — usually the same colour the screen
 *   paints at its top edge (for gradients, pass the top colour).
 * @param darkIcons true for dark icons (use on light backgrounds), false
 *   for light/white icons (use on dark backgrounds).
 */
@Composable
fun StatusBarColor(color: Color, darkIcons: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    DisposableEffect(view, color, darkIcons) {
        val window = (view.context as Activity).window
        val previousColor = window.statusBarColor
        val previousLight = WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars

        @Suppress("DEPRECATION")
        window.statusBarColor = color.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkIcons

        onDispose {
            @Suppress("DEPRECATION")
            window.statusBarColor = previousColor
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = previousLight
        }
    }
}
