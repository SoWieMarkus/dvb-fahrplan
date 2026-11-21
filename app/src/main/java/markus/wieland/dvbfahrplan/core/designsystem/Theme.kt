package markus.wieland.dvbfahrplan.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The app's own small design system. No Material: a warm paper background, ink-colored text,
 * line colors as the only loud color, and a station-board feel for times.
 */
@Immutable
data class FahrplanColors(
    val background: Color,
    val surface: Color,
    val surfaceSunken: Color,
    val ink: Color,
    val inkMuted: Color,
    val inkFaint: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val onTime: Color,
    val delayed: Color,
    val cancelled: Color,
    val isDark: Boolean,
)

private val Light = FahrplanColors(
    background = Color(0xFFF4F2EE),
    surface = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFEAE6DF),
    ink = Color(0xFF17191B),
    inkMuted = Color(0xFF64625D),
    inkFaint = Color(0xFFA5A199),
    line = Color(0xFFDDD8CF),
    accent = Color(0xFFFFA200),
    onAccent = Color(0xFF17191B),
    onTime = Color(0xFF2F7A4B),
    delayed = Color(0xFFC2410C),
    cancelled = Color(0xFFB4122B),
    isDark = false,
)

private val Dark = FahrplanColors(
    background = Color(0xFF111213),
    surface = Color(0xFF1B1C1E),
    surfaceSunken = Color(0xFF0B0C0D),
    ink = Color(0xFFF1EEE8),
    inkMuted = Color(0xFF9E9A92),
    inkFaint = Color(0xFF5E5B56),
    line = Color(0xFF2C2D2F),
    accent = Color(0xFFFFB020),
    onAccent = Color(0xFF17191B),
    onTime = Color(0xFF6CC48D),
    delayed = Color(0xFFFF8A4C),
    cancelled = Color(0xFFFF5D6E),
    isDark = true,
)

private val Condensed = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Normal),
    Font(DeviceFontFamilyName("sans-serif-condensed"), FontWeight.Bold),
)

@Immutable
data class FahrplanType(
    /** Big station names and screen titles. */
    val title: TextStyle = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 32.sp, letterSpacing = (-0.2).sp),
    val heading: TextStyle = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp),
    /** Small caps-like section labels. */
    val label: TextStyle = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
    val body: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    val bodyStrong: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
    val caption: TextStyle = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
    /** Departure countdowns and clock times, like on a station board. */
    // Condensed like the stop names, with tabular figures ("tnum") so times line up in columns.
    val timeLarge: TextStyle = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 25.sp, lineHeight = 28.sp, fontFeatureSettings = "tnum"),
    val time: TextStyle = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 21.sp, fontFeatureSettings = "tnum"),
    val timeSmall: TextStyle = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 17.sp, fontFeatureSettings = "tnum"),
    val badge: TextStyle = TextStyle(fontFamily = Condensed, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp),
)

private val LocalColors = staticCompositionLocalOf { Light }
private val LocalType = staticCompositionLocalOf { FahrplanType() }

object FahrplanTheme {
    val colors: FahrplanColors @Composable get() = LocalColors.current
    val type: FahrplanType @Composable get() = LocalType.current
}

@Composable
fun FahrplanTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (dark) Dark else Light
    CompositionLocalProvider(
        LocalColors provides colors,
        LocalType provides FahrplanType(),
        LocalTextSelectionColors provides TextSelectionColors(colors.accent, colors.accent.copy(alpha = 0.35f)),
        content = content,
    )
}
