// این فایل مشخص می‌کند پروژه چه نامی دارد و Gradle وابستگی‌ها را از کدام مخزن‌ها دریافت کند.
pluginManagement {
    // مخزن رسمی Google شامل پلاگین‌ها و کتابخانه‌های Android است.
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// این بخش مخزن‌های مورد استفاده همه ماژول‌های پروژه را یک‌دست می‌کند.
dependencyResolutionManagement {
    // اگر یک ماژول مخزن جداگانه تعریف کند، Build متوقف می‌شود تا وابستگی‌ها قابل پیش‌بینی بمانند.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    // مخزن‌های اصلی کتابخانه‌های برنامه.
    repositories {
        google()
        mavenCentral()
    }
}

// نامی که Android Studio برای پروژه نمایش می‌دهد.
rootProject.name = "HesabYar"

// ماژول اصلی اپلیکیشن اندروید.
include(":app")
