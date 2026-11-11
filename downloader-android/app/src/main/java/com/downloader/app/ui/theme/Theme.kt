package com.downloader.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.downloader.app.R

/*
 * الهوية: «فاخر داكن». أسود دافئ بدرجات متفاوتة + ذهبي يُستخدم للتأكيد فقط
 * (زر التحميل، التقدّم، العنصر المحدد). كل ما عداه محايد.
 */
object Palette {
    val Ink = Color(0xFF0A0A0B)          // الخلفية
    val Surface = Color(0xFF121214)      // بطاقات أساسية
    val Raised = Color(0xFF19191C)       // مرتفع
    val RaisedHigh = Color(0xFF212125)   // أعلى
    val RaisedTop = Color(0xFF2B2B30)    // مسارات/حقول
    val Hairline = Color(0xFF2E2D2A)
    val HairlineSoft = Color(0xFF1F1F22)
    val Cream = Color(0xFFEFE9DC)        // النص الأساسي
    val Muted = Color(0xFF9D988D)        // نص ثانوي
    val Dim = Color(0xFF6B675F)          // نص خافت
    val Gold = Color(0xFFD9B36A)
    val GoldBright = Color(0xFFF4E2B4)
    val GoldDeep = Color(0xFFA9772B)
    val OnGold = Color(0xFF1B1305)
    val Danger = Color(0xFFE8776B)
    val Success = Color(0xFF93C48C)
}

@Immutable
data class ExtendedColors(
    val gold: Color,
    val goldBright: Color,
    val goldDeep: Color,
    val onGold: Color,
    val dim: Color,
    val hairline: Color,
    val hairlineSoft: Color,
    val success: Color,
    val goldBrush: Brush,
)

private val Extended = ExtendedColors(
    gold = Palette.Gold,
    goldBright = Palette.GoldBright,
    goldDeep = Palette.GoldDeep,
    onGold = Palette.OnGold,
    dim = Palette.Dim,
    hairline = Palette.Hairline,
    hairlineSoft = Palette.HairlineSoft,
    success = Palette.Success,
    goldBrush = Brush.linearGradient(listOf(Palette.GoldBright, Palette.Gold, Palette.GoldDeep)),
)

val LocalExtendedColors = staticCompositionLocalOf { Extended }

private val Colors = darkColorScheme(
    primary = Palette.Gold,
    onPrimary = Palette.OnGold,
    primaryContainer = Color(0xFF3A2C12),
    onPrimaryContainer = Palette.GoldBright,
    secondary = Palette.Muted,
    onSecondary = Palette.Ink,
    background = Palette.Ink,
    onBackground = Palette.Cream,
    surface = Palette.Surface,
    onSurface = Palette.Cream,
    surfaceVariant = Palette.RaisedHigh,
    onSurfaceVariant = Palette.Muted,
    surfaceContainerLowest = Palette.Ink,
    surfaceContainerLow = Palette.Surface,
    surfaceContainer = Palette.Raised,
    surfaceContainerHigh = Palette.RaisedHigh,
    surfaceContainerHighest = Palette.RaisedTop,
    outline = Palette.Hairline,
    outlineVariant = Palette.HairlineSoft,
    error = Palette.Danger,
    onError = Palette.Ink,
)

val Tajawal = FontFamily(
    Font(R.font.tajawal_regular, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, spacing: Double = 0.0) = TextStyle(
    fontFamily = Tajawal, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = spacing.sp,
)

private val Type = Typography(
    displaySmall = style(34, 44, FontWeight.ExtraBold),
    headlineMedium = style(28, 38, FontWeight.ExtraBold),
    headlineSmall = style(24, 34, FontWeight.Bold),
    titleLarge = style(20, 30, FontWeight.Bold),
    titleMedium = style(17, 26, FontWeight.Bold),
    titleSmall = style(15, 22, FontWeight.Bold),
    bodyLarge = style(16, 26, FontWeight.Normal),
    bodyMedium = style(14, 23, FontWeight.Normal),
    bodySmall = style(12, 19, FontWeight.Normal),
    labelLarge = style(15, 22, FontWeight.Bold),
    labelMedium = style(13, 19, FontWeight.Medium),
    labelSmall = style(11, 16, FontWeight.Medium),
)

/** أرقام بعرض ثابت للأحجام والنسب حتى لا «ترقص» أثناء التقدّم. */
val NumberStyle = TextStyle(fontFamily = Tajawal, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum")

// لغة الأشكال: زوايا حادة للشارات، متوسطة للصفوف، ناعمة للحاويات الكبيرة.
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

object AppTheme {
    val extra: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

@Composable
fun DownloaderTheme(content: @Composable () -> Unit) {
    // الواجهة عربية بالكامل: نفرض RTL مهما كانت لغة الجهاز، والمظهر داكن دائماً (هوية ثابتة).
    CompositionLocalProvider(
        LocalExtendedColors provides Extended,
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(colorScheme = Colors, typography = Type, shapes = AppShapes, content = content)
    }
}
