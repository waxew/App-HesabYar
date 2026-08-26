// این فایل نسخه پلاگین‌های مشترک پروژه را مشخص می‌کند.
plugins {
    // پلاگین ساخت برنامه Android؛ apply=false یعنی در ماژول app فعال خواهد شد.
    id("com.android.application") version "8.7.3" apply false

    // پلاگین Kotlin برای کدنویسی Native Android.
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false

    // پلاگین رسمی Compose Compiler که با Kotlin 2 هماهنگ است.
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
