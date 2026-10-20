package app.aiplanner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DeepBlue = Color(0xFF0E1B3D)
private val Indigo = Color(0xFF3B5BDB)
private val Teal = Color(0xFF0CA789)

private val LightColors = lightColorScheme(
    primary = Indigo,
    secondary = Teal,
    tertiary = Violet,
    background = Color(0xFFF6F7FB),
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF91A7FF),
    secondary = Color(0xFF5CDFC4),
    tertiary = Color(0xFFC0ABFF),
    background = DeepBlue,
    surface = Color(0xFF16264F),
)

@Composable
fun AITimePlannerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colors = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}

/** Цвет блока расписания по категории (для таймлайна). */
object CategoryPalette {
    val work = Color(0xFF3B5BDB)
    val study = Color(0xFF7048E8)
    val health = Color(0xFF0CA789)
    val family = Color(0xFFE8590C)
    val home = Color(0xFFF59F00)
    val rest = Color(0xFF868E96)
    val personal = Color(0xFF1098AD)
}
