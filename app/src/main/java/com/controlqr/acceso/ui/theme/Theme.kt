package com.controlqr.acceso.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Azul industrial: se lee bien bajo sol directo, que es donde vive una caseta de acceso. */
private val Brand = Color(0xFF0F3D5C)
private val BrandLight = Color(0xFF3C6E92)
private val Accent = Color(0xFF00897B)

val VerdeAcceso = Color(0xFF1B8E3B)
val RojoRechazo = Color(0xFFC62828)
val AmbarAviso = Color(0xFFE68A00)

private val LightColors = lightColorScheme(
    primary = Brand,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E5F2),
    onPrimaryContainer = Color(0xFF07293E),
    secondary = Accent,
    onSecondary = Color.White,
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF121A20),
    surface = Color.White,
    onSurface = Color(0xFF121A20),
    surfaceVariant = Color(0xFFE6ECF1),
    onSurfaceVariant = Color(0xFF48555F),
    error = RojoRechazo,
    outline = Color(0xFFB4C1CB)
)

private val DarkColors = darkColorScheme(
    primary = BrandLight,
    onPrimary = Color(0xFF04202F),
    primaryContainer = Color(0xFF16465F),
    onPrimaryContainer = Color(0xFFD3E5F2),
    secondary = Color(0xFF4DB6AC),
    onSecondary = Color(0xFF00312B),
    background = Color(0xFF0E1418),
    onBackground = Color(0xFFE2E8ED),
    surface = Color(0xFF161E24),
    onSurface = Color(0xFFE2E8ED),
    surfaceVariant = Color(0xFF2A353D),
    onSurfaceVariant = Color(0xFFB6C2CB),
    error = Color(0xFFEF5350),
    outline = Color(0xFF5A6872)
)

private val AppTypography = Typography(
    // Los números de los tableros se leen de un vistazo, a un brazo de distancia.
    displaySmall = Typography().displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
)

@Composable
fun ControlQrTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColors else LightColors
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.primary.toArgb()
            window.navigationBarColor = colors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
}
