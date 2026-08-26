package com.waxew.hesabyar.data

// Context برای دسترسی به حافظه خصوصی برنامه استفاده می‌شود.
import android.content.Context

// JSONArray لیست تاریخچه را به یک رشته JSON تبدیل می‌کند.
import org.json.JSONArray

// JSONObject هر رکورد تاریخچه را مدل می‌کند.
import org.json.JSONObject

/** یک رکورد ذخیره‌شده از نتیجه محاسبات کاربر. */
data class HistoryEntry(
    // شناسه یکتا که بر اساس زمان ایجاد می‌شود.
    val id: Long,

    // عنوان ابزار مثل «تخفیف» یا «سود».
    val title: String,

    // ورودی‌های مهم محاسبه برای یادآوری کاربر.
    val details: String,

    // خروجی نهایی محاسبه.
    val result: String,

    // زمان ایجاد رکورد؛ به‌صورت timestamp میلی‌ثانیه‌ای ذخیره می‌شود.
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * ذخیره‌ساز محلی تاریخچه حسابیار.
 *
 * در نسخه‌های بعدی می‌توان این کلاس را به Room مهاجرت داد؛ کل UI فقط با همین Repository کار می‌کند.
 */
class HistoryRepository(context: Context) {

    // فایل خصوصی تاریخچه. Android هنگام Update آن را حفظ می‌کند.
    private val prefs = context.getSharedPreferences("hesabyar_history", Context.MODE_PRIVATE)

    /** تاریخچه قبلی را از JSON محلی می‌خواند. */
    fun load(): List<HistoryEntry> = runCatching {
        // اگر هنوز چیزی ذخیره نشده باشد، آرایه خالی خوانده می‌شود.
        val array = JSONArray(prefs.getString(KEY_ITEMS, "[]"))

        // آرایه JSON به List از HistoryEntry تبدیل می‌شود.
        buildList {
            // تمام آیتم‌های JSON پیمایش می‌شوند.
            for (index in 0 until array.length()) {
                // آبجکت فعلی از آرایه استخراج می‌شود.
                val item = array.getJSONObject(index)

                // فیلدهای JSON به مدل داخلی تبدیل می‌شوند.
                add(
                    HistoryEntry(
                        id = item.getLong("id"),
                        title = item.getString("title"),
                        details = item.getString("details"),
                        result = item.getString("result"),
                        createdAt = item.getLong("createdAt")
                    )
                )
            }
        }
    }.getOrDefault(emptyList()) // اگر JSON خراب باشد برنامه Crash نمی‌کند و تاریخچه خالی برمی‌گردد.

    /** حداکثر ۱۰۰ رکورد آخر را روی دستگاه ذخیره می‌کند. */
    fun save(items: List<HistoryEntry>) {
        // یک آرایه JSON جدید برای خروجی ساخته می‌شود.
        val array = JSONArray()

        // فقط ۱۰۰ نتیجه آخر ذخیره می‌شوند تا حجم SharedPreferences کنترل شود.
        items.take(MAX_HISTORY_ITEMS).forEach { entry ->
            // هر HistoryEntry به JSONObject تبدیل و به آرایه اضافه می‌شود.
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("title", entry.title)
                    .put("details", entry.details)
                    .put("result", entry.result)
                    .put("createdAt", entry.createdAt)
            )
        }

        // JSON نهایی در حافظه خصوصی برنامه ذخیره می‌شود.
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    /** ثابت‌های مربوط به ذخیره تاریخچه. */
    private companion object {
        // کلید رشته JSON در SharedPreferences.
        const val KEY_ITEMS = "items"

        // سقف تعداد رکوردهای قابل نگهداری.
        const val MAX_HISTORY_ITEMS = 100
    }
}
