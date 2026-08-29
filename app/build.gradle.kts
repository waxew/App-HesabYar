// این فایل تمام تنظیمات Build ماژول اصلی برنامه حسابیار را نگه می‌دارد.
plugins {
    // ماژول را به یک Android Application قابل نصب تبدیل می‌کند.
    id("com.android.application")

    // پشتیبانی Kotlin را برای کدهای Android فعال می‌کند.
    id("org.jetbrains.kotlin.android")

    // کامپایل Jetpack Compose را با Kotlin 2 فعال می‌کند.
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    // Namespace برای کلاس‌های تولیدشده مثل BuildConfig و R استفاده می‌شود.
    namespace = "com.waxew.hesabyar"

    // نسخه SDK مورد استفاده هنگام کامپایل سورس.
    compileSdk = 35

    defaultConfig {
        // applicationId هویت دائمی برنامه در Android است؛ برای آپدیت‌ها نباید تغییر کند.
        applicationId = "com.waxew.hesabyar"

        // حداقل Android 7.0 برای نصب برنامه کافی است.
        minSdk = 24

        // برنامه برای رفتارهای Android API 35 هدف‌گذاری شده است.
        targetSdk = 35

        // هر انتشار جدید باید versionCode بزرگ‌تری از نسخه قبل داشته باشد.
        versionCode = 4

        // نسخه نمایشی برنامه که در صفحه «درباره نرم افزار» دیده می‌شود.
        versionName = "3.0.0"

        // Runner استاندارد تست‌های Instrumentation Android.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // اجازه می‌دهد VectorDrawable روی نسخه‌های قدیمی‌تر Android هم درست کار کند.
        vectorDrawables.useSupportLibrary = true
    }

    // مسیر و اطلاعات کلید Release فقط از متغیر محیطی خوانده می‌شوند تا کلید وارد GitHub نشود.
    val keystorePath = System.getenv("HESABYAR_KEYSTORE_FILE")
    val keystorePassword = System.getenv("HESABYAR_KEYSTORE_PASSWORD")
    val keyAliasValue = System.getenv("HESABYAR_KEY_ALIAS")
    val keyPasswordValue = System.getenv("HESABYAR_KEY_PASSWORD")

    // فقط وقتی هر چهار مقدار امضا وجود داشته باشد، Release داخل Gradle امضا می‌شود.
    val canSignRelease = !keystorePath.isNullOrBlank() &&
        !keystorePassword.isNullOrBlank() &&
        !keyAliasValue.isNullOrBlank() &&
        !keyPasswordValue.isNullOrBlank()

    // SigningConfig فقط در محیطی ساخته می‌شود که کلید خصوصی واقعاً در اختیار Build باشد.
    if (canSignRelease) {
        signingConfigs {
            create("release") {
                // فایل JKS حاوی کلید خصوصی امضای حسابیار است.
                storeFile = file(keystorePath!!)

                // رمز Keystore از Environment دریافت می‌شود.
                storePassword = keystorePassword

                // Alias کلید داخل Keystore.
                keyAlias = keyAliasValue

                // رمز خود کلید امضا.
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        debug {
            // نسخه Debug شناسه متفاوت دارد تا کنار نسخه پابلیش نصب شود و با آن اشتباه نشود.
            applicationIdSuffix = ".debug"

            // روی نام نسخه Debug نیز پسوند مشخص قرار می‌گیرد.
            versionNameSuffix = "-debug"
        }

        release {
            // R8 کدهای بدون استفاده را حذف و خروجی پابلیش را کوچک‌تر می‌کند.
            isMinifyEnabled = true

            // Resourceهای بلااستفاده از APK پابلیش حذف می‌شوند.
            isShrinkResources = true

            // قوانین پیش‌فرض بهینه‌سازی Android و قوانین اختصاصی پروژه اعمال می‌شوند.
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // اگر کلید در Environment موجود باشد، همین BuildType با کلید ثابت حسابیار امضا می‌شود.
            if (canSignRelease) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        // سورس Java با سطح زبان Java 17 کامپایل می‌شود.
        sourceCompatibility = JavaVersion.VERSION_17

        // Bytecode Java نیز با Java 17 سازگار است.
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        // خروجی Kotlin روی JVM 17 هدف‌گذاری می‌شود.
        jvmTarget = "17"
    }

    buildFeatures {
        // Jetpack Compose رابط کاربری پروژه را فعال می‌کند.
        compose = true

        // BuildConfig برای خواندن VERSION_NAME و VERSION_CODE لازم است.
        buildConfig = true
    }

    packaging {
        resources {
            // Licenseهای تکراری کتابخانه‌ها از بسته نهایی حذف می‌شوند تا Conflict رخ ندهد.
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // BOM نسخه سازگار تمام کتابخانه‌های Compose را یک‌جا مدیریت می‌کند.
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")

    // BOM برای کد اصلی برنامه.
    implementation(composeBom)

    // همان BOM برای تست‌های Android.
    androidTestImplementation(composeBom)

    // Extensionهای Kotlin برای Android Core.
    implementation("androidx.core:core-ktx:1.15.0")

    // Activity مبتنی بر Compose و BackHandler.
    implementation("androidx.activity:activity-compose:1.10.0")

    // Lifecycle runtime برای مدیریت چرخه حیات Activity.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // اتصال Lifecycle به Composableها.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    // هسته UI در Jetpack Compose.
    implementation("androidx.compose.ui:ui")

    // Preview برای مشاهده Composableها در Android Studio.
    implementation("androidx.compose.ui:ui-tooling-preview")

    // Material 3 برای کامپوننت‌ها و Drawer/NavigationBar/Dialogs.
    implementation("androidx.compose.material3:material3")

    // مجموعه آیکون‌های محاسبات، منو، درصد، تخفیف و سایر بخش‌ها.
    implementation("androidx.compose.material:material-icons-extended")

    // ابزارهای Debug رابط کاربری؛ وارد APK پابلیش نمی‌شوند.
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Manifest کمکی برای تست UI فقط در Debug.
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // مدل OCR به‌صورت Bundled داخل APK قرار می‌گیرد تا برای خواندن عدد قیمت به دانلود مدل وابسته نباشد.
    implementation("com.google.mlkit:text-recognition:16.0.1")

    // مدل Barcode نیز Bundled است و اسکن QR/Barcode را روی خود دستگاه انجام می‌دهد.
    implementation("com.google.mlkit:barcode-scanning:17.3.0")

    // JUnit برای تست خودکار موتور محاسبات و جلوگیری از Regression فرمول‌ها.
    testImplementation("junit:junit:4.13.2")
}
