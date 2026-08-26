package com.waxew.hesabyar

// Bundle وضعیت ذخیره‌شده Activity را هنگام بازسازی سیستم نگه می‌دارد.
import android.os.Bundle

// ComponentActivity میزبان سبک و مناسب Jetpack Compose است.
import androidx.activity.ComponentActivity

// setContent درخت UI برنامه را با Compose راه‌اندازی می‌کند.
import androidx.activity.compose.setContent

// WindowCompat برای فعال‌کردن طراحی Edge-to-Edge استفاده می‌شود.
import androidx.core.view.WindowCompat

/**
 * Activity اصلی حسابیار.
 *
 * این کلاس عمداً کوچک نگه داشته شده است؛ منطق برنامه و ناوبری داخل Composableهای حسابیار قرار دارد.
 */
class MainActivity : ComponentActivity() {

    /** نقطه ورود Activity هنگام اجرای برنامه. */
    override fun onCreate(savedInstanceState: Bundle?) {
        // ابتدا چرخه حیات استاندارد Android اجرا می‌شود.
        super.onCreate(savedInstanceState)

        // Compose اجازه دارد پشت Status Bar و Navigation Bar رسم شود؛ Insets داخل UI مدیریت می‌شوند.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // ریشه رابط کاربری حسابیار روی Activity قرار می‌گیرد.
        setContent {
            HesabYarApp()
        }
    }
}
