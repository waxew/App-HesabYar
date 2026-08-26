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

    /**
     * مشخص می‌کند بررسی خودکار نسخه جدید فعال باشد یا نه.
     * این گزینه همان بخش «اعلان‌ها» در تنظیمات است و به‌طور پیش‌فرض فعال است.
     */
    var notificationsEnabled: Boolean
        get() = prefs.getBoolean(KEY_NOTIFICATIONS, true)
        set(value) {
            // وضعیت اعلان/بررسی نسخه جدید ذخیره می‌شود.
            prefs.edit().putBoolean(KEY_NOTIFICATIONS, value).apply()
        }

    /** کلیدهای SharedPreferences در یک محل ثابت نگه داشته می‌شوند تا در آپدیت‌ها تغییر نکنند. */
    private companion object {
        // کلید Theme.
        const val KEY_THEME = "theme"

        // کلید واحد پول.
        const val KEY_CURRENCY = "currency"

        // کلید اعلان نسخه‌های جدید.
        const val KEY_NOTIFICATIONS = "notifications_enabled"
    }
}
