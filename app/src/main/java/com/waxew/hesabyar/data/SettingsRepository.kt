package com.waxew.hesabyar.data

// Context برای دسترسی به SharedPreferences محلی Android لازم است.
import android.content.Context

/** حالت‌های قابل انتخاب ظاهر برنامه. */
enum class ThemeMode {
    // برنامه از حالت روشن/تاریک خود گوشی پیروی می‌کند.
    SYSTEM,

    // حالت روشن همیشه فعال می‌ماند.
    LIGHT,

    // حالت تاریک همیشه فعال می‌ماند.
    DARK
}

/** واحدهای پولی قابل نمایش در فیلدها و نتایج حسابیار. */
enum class CurrencyMode(val label: String) {
    // نمایش مبالغ به تومان.
    TOMAN("تومان"),

    // نمایش مبالغ به ریال.
    RIAL("ریال")
}

/**
 * مخزن تنظیمات سبک برنامه.
 *
 * SharedPreferences برای این داده‌های کوچک مناسب است و هنگام نصب آپدیت روی نسخه قبلی حفظ می‌شود.
 */
class SettingsRepository(context: Context) {

    // فایل تنظیمات خصوصی حسابیار؛ حذف نصب برنامه آن را پاک می‌کند اما Update آن را نگه می‌دارد.
    private val prefs = context.getSharedPreferences("hesabyar_settings", Context.MODE_PRIVATE)

    /** حالت ظاهری ذخیره‌شده کاربر. */
    var themeMode: ThemeMode
        get() = runCatching {
            // مقدار String ذخیره‌شده به enum تبدیل می‌شود.
            ThemeMode.valueOf(prefs.getString(KEY_THEME, ThemeMode.SYSTEM.name)!!)
        }.getOrDefault(ThemeMode.SYSTEM)
        set(value) {
            // تغییر حالت ظاهر به‌صورت غیرهمزمان ذخیره می‌شود.
            prefs.edit().putString(KEY_THEME, value.name).apply()
        }

    /** واحد پولی انتخاب‌شده کاربر. */
    var currencyMode: CurrencyMode
        get() = runCatching {
            // مقدار قبلی خوانده و به enum تبدیل می‌شود.
            CurrencyMode.valueOf(prefs.getString(KEY_CURRENCY, CurrencyMode.TOMAN.name)!!)
        }.getOrDefault(CurrencyMode.TOMAN)
        set(value) {
            // انتخاب جدید کاربر برای اجرای بعدی برنامه ذخیره می‌شود.
            prefs.edit().putString(KEY_CURRENCY, value.name).apply()
        }

    /** مشخص می‌کند بررسی خودکار نسخه جدید فعال باشد یا نه. */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) {
            prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
        }

    /** نام نمایشی کاربر در بالای Drawer. */
    var profileName: String
        get() = prefs.getString(KEY_PROFILE_NAME, "کاربر حسابیار")?.ifBlank { "کاربر حسابیار" } ?: "کاربر حسابیار"
        set(value) {
            prefs.edit().putString(KEY_PROFILE_NAME, value.trim().take(40)).apply()
        }

    /** URI تصویر پروفایل انتخاب‌شده؛ فایل تصویر داخل Repository عمومی ذخیره نمی‌شود. */
    var profileImageUri: String
        get() = prefs.getString(KEY_PROFILE_IMAGE_URI, "") ?: ""
        set(value) {
            prefs.edit().putString(KEY_PROFILE_IMAGE_URI, value).apply()
        }

    /** کلیدهای SharedPreferences در یک محل ثابت نگه داشته می‌شوند تا در آپدیت‌ها تغییر نکنند. */
    private companion object {
        const val KEY_THEME = "theme"
        const val KEY_CURRENCY = "currency"
        const val KEY_NOTIFICATIONS = "notifications_enabled"
        const val KEY_PROFILE_NAME = "profile_name"
        const val KEY_PROFILE_IMAGE_URI = "profile_image_uri"
    }
}
