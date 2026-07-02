package com.trekmate.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TrekGreen = Color(0xFF2E7D32)
private val TrekGreenLight = Color(0xFF60AD5E)
private val TrekOrange = Color(0xFFE65100)
private val TrekRed = Color(0xFFD32F2F)

private val LightColors = lightColorScheme(
    primary = TrekGreen,
    secondary = TrekGreenLight,
    error = TrekRed,
    tertiary = TrekOrange
)

@Composable
fun TrekMateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
