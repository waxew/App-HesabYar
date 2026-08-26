package com.waxew.hesabyar.update

// BuildConfig نسخه نصب‌شده فعلی را در زمان Build در اختیار برنامه می‌گذارد.
import com.waxew.hesabyar.BuildConfig

// JSONObject فایل JSON نسخه جدید را Parse می‌کند.
import org.json.JSONObject

// HttpURLConnection برای دریافت Manifest آپدیت بدون کتابخانه شبکه اضافی استفاده می‌شود.
import java.net.HttpURLConnection

// URL آدرس فایل latest.json را باز می‌کند.
import java.net.URL

/** اطلاعات نسخه جدیدی که از Update Manifest دریافت شده است. */
data class UpdateInfo(
    // versionCode نسخه جدید؛ معیار اصلی مقایسه با نسخه نصب‌شده است.
    val versionCode: Int,
    // نام نمایشی نسخه مثل 1.0.1.
    val versionName: String,
    // لینک دریافت APK پابلیش.
    val downloadUrl: String,
    // توضیح کوتاه تغییرات نسخه.
    val notes: String
)

/** بررسی‌کننده سبک آپدیت حسابیار. خطاهای شبکه نباید باعث Crash شوند. */
object UpdateChecker {
    // Manifest عمومی نسخه آخر در همان مخزن GitHub نگهداری می‌شود.
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/waxew/App-HesabYar/main/distribution/latest.json"

    // Timeout اتصال برای جلوگیری از معطل ماندن برنامه روی شبکه ضعیف.
    private const val CONNECT_TIMEOUT_MS = 5_000

    // Timeout خواندن پاسخ سرور.
    private const val READ_TIMEOUT_MS = 5_000

    /** اگر نسخه جدیدتری موجود باشد اطلاعات آن را برمی‌گرداند؛ در غیر این صورت null. */
    fun check(): UpdateInfo? = runCatching {
        // اتصال HTTP ساخته می‌شود.
        val connection = URL(MANIFEST_URL).openConnection() as HttpURLConnection

        try {
            // محدودیت زمان اتصال.
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            // محدودیت زمان خواندن.
            connection.readTimeout = READ_TIMEOUT_MS
            // درخواست فقط خواندنی است.
            connection.requestMethod = "GET"
            // فرمت پاسخ مورد انتظار.
            connection.setRequestProperty("Accept", "application/json")
            // User-Agent نسخه فعلی.
            connection.setRequestProperty("User-Agent", "HesabYar/${BuildConfig.VERSION_NAME}")

            // پاسخ ناموفق یعنی فعلاً آپدیتی نمایش داده نشود.
            if (connection.responseCode !in 200..299) return@runCatching null

            // خواندن کل JSON با بسته شدن خودکار Stream.
            val jsonText = connection.inputStream.bufferedReader().use { reader -> reader.readText() }
            // Parse پاسخ.
            val root = JSONObject(jsonText)
            // versionCode نسخه سرور.
            val remoteCode = root.getInt("versionCode")

            // اگر نسخه سرور جدیدتر نیست، نتیجه null است.
            if (remoteCode <= BuildConfig.VERSION_CODE) return@runCatching null

            // مدل نسخه جدید ساخته می‌شود.
            UpdateInfo(
                versionCode = remoteCode,
                versionName = root.getString("versionName"),
                downloadUrl = root.getString("downloadUrl"),
                notes = root.optString("notes", "نسخه جدید حسابیار آماده است.")
            )
        } finally {
            // اتصال همیشه آزاد می‌شود.
            connection.disconnect()
        }
    }.getOrNull()
}
