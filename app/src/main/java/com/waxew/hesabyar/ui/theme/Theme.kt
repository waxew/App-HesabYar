package com.waxew.hesabyar.ui.theme

// تشخیص حالت تاریک فعلی سیستم.
import androidx.compose.foundation.isSystemInDarkTheme

// MaterialTheme تم و رنگ‌ها را در کل درخت Compose در دسترس قرار می‌دهد.
import androidx.compose.material3.MaterialTheme

// سازنده ColorScheme مناسب حالت تاریک.
import androidx.compose.material3.darkColorScheme

// سازنده ColorScheme مناسب حالت روشن.
import androidx.compose.material3.lightColorScheme

// annotation مربوط به توابع Composable.
import androidx.compose.runtime.Composable

// نوع تنظیمات ظاهری ذخیره‌شده کاربر.
import com.waxew.hesabyar.data.ThemeMode

// Color برای تعریف رنگ‌های ثابت برند حسابیار.
import androidx.compose.ui.graphics.Color

/** پالت روشن حسابیار با آبی اصلی و پس‌زمینه خنثی. */
private val LightColors = lightColorScheme(
    // رنگ اصلی دکمه‌ها، آیکون‌ها و تاکیدهای رابط.
    primary = Color(0xFF1D4ED8),

    // رنگ محتوا روی سطح primary.
    onPrimary = Color.White,

    // سطح ملایم برای Result Cardها.
    primaryContainer = Color(0xFFDBEAFE),

    // متن روی primaryContainer.
    onPrimaryContainer = Color(0xFF172554),

    // رنگ ثانویه برای اجزای مکمل.
    secondary = Color(0xFF0F766E),

    // سطح ثانویه ملایم.
    secondaryContainer = Color(0xFFCCFBF1),

    // پس‌زمینه عمومی صفحه‌ها.
    background = Color(0xFFF7F9FC),

    // سطح کارت‌ها.
    surface = Color(0xFFFFFFFF),

    // سطح جایگزین برای بخش‌های کم‌اهمیت‌تر.
    surfaceVariant = Color(0xFFEFF3F8)
)

/** پالت تاریک حسابیار با کنتراست مناسب برای شب. */
private val DarkColors = darkColorScheme(
    // آبی روشن‌تر برای خوانایی روی پس‌زمینه تاریک.
    primary = Color(0xFF93C5FD),

    // متن روی primary در حالت تاریک.
    onPrimary = Color(0xFF172554),

    // سطح تاکید تاریک.
    primaryContainer = Color(0xFF1E3A8A),

    // رنگ ثانویه روشن.
    secondary = Color(0xFF5EEAD4),

    // پس‌زمینه اصلی حالت تاریک.
    background = Color(0xFF0B1220),

    // سطح کارت‌ها در حالت تاریک.
    surface = Color(0xFF111827),

    // سطح جایگزین در حالت تاریک.
    surfaceVariant = Color(0xFF1F2937)
)

/** Theme مرکزی حسابیار که بر اساس تنظیم کاربر ColorScheme مناسب را انتخاب می‌کند. */
@Composable
fun HesabYarTheme(themeMode: ThemeMode, content: @Composable () -> Unit) {
    // تصمیم نهایی درباره روشن یا تاریک بودن رابط.
    val dark = when (themeMode) {
        // در حالت SYSTEM از تنظیم گوشی پیروی می‌کنیم.
        ThemeMode.SYSTEM -> isSystemInDarkTheme()

        // در حالت LIGHT همیشه پالت روشن استفاده می‌شود.
        ThemeMode.LIGHT -> false

        // در حالت DARK همیشه پالت تاریک استفاده می‌شود.
        ThemeMode.DARK -> true
    }

    // ColorScheme انتخاب‌شده به تمام Composableهای فرزند تزریق می‌شود.
    MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, content = content)
}
