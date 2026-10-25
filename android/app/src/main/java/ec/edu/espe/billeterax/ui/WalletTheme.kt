package ec.edu.espe.billeterax.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import ec.edu.espe.billeterax.model.Appearance

private val Light = lightColorScheme(
    primary = Color(0xFF3656D8), onPrimary = Color.White, primaryContainer = Color(0xFFE2E8FF), onPrimaryContainer = Color(0xFF192E76),
    secondary = Color(0xFF087F70), onSecondary = Color.White, secondaryContainer = Color(0xFFD9F3EB), onSecondaryContainer = Color(0xFF075B4E),
    tertiary = Color(0xFF846127), tertiaryContainer = Color(0xFFFFECCC), onTertiaryContainer = Color(0xFF634612),
    background = Color(0xFFF5F7FC), onBackground = Color(0xFF17243D), surface = Color.White, onSurface = Color(0xFF17243D),
    surfaceVariant = Color(0xFFEBEFF8), onSurfaceVariant = Color(0xFF5B6980), outline = Color(0xFF7F8DA4), outlineVariant = Color(0xFFDDE4F0),
    error = Color(0xFFB93949), errorContainer = Color(0xFFFFE9ED), onErrorContainer = Color(0xFF8E2536))
private val Dark = darkColorScheme(
    primary = Color(0xFFB4C3FF), onPrimary = Color(0xFF142A73), primaryContainer = Color(0xFF26386D), onPrimaryContainer = Color(0xFFDCE4FF),
    secondary = Color(0xFF70DBC2), onSecondary = Color(0xFF043C32), secondaryContainer = Color(0xFF173F3A), onSecondaryContainer = Color(0xFFA2EFDC),
    tertiary = Color(0xFFE7C48B), tertiaryContainer = Color(0xFF4A3B24), onTertiaryContainer = Color(0xFFFFE1AE),
    background = Color(0xFF0D1422), onBackground = Color(0xFFE7EDF8), surface = Color(0xFF172132), onSurface = Color(0xFFE7EDF8),
    surfaceVariant = Color(0xFF222E43), onSurfaceVariant = Color(0xFFAEBBD0), outline = Color(0xFF8493AC), outlineVariant = Color(0xFF33425B),
    error = Color(0xFFFFA8B6), errorContainer = Color(0xFF482632), onErrorContainer = Color(0xFFFFDCE3))

@Composable fun WalletTheme(appearance: Appearance, content: @Composable () -> Unit) {
    val dark = when (appearance) { Appearance.SYSTEM -> isSystemInDarkTheme(); Appearance.LIGHT -> false; Appearance.DARK -> true }
    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark; isAppearanceLightNavigationBars = !dark
            }
        }
    }
    val typography = Typography(
        headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp),
        headlineSmall = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 30.sp),
        titleLarge = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 21.sp, lineHeight = 27.sp))
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = typography,
        shapes = Shapes(medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(24.dp)), content = content)
}
@Composable internal fun incomeColor() = if (MaterialTheme.colorScheme.background.luminance() < .3f) Color(0xFF72DEC0) else Color(0xFF087C5C)
@Composable internal fun expenseColor() = MaterialTheme.colorScheme.error
