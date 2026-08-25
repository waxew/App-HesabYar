# حسابیار (HesabYar)

حسابیار یک اپ اندرویدی فارسی برای محاسبات روزمره خرید، تخفیف، سود و قیمت‌گذاری است.

## نسخه 1.0.0

- محاسبه تخفیف ساده و چندمرحله‌ای
- محاسبه سود خالص، Markup و Profit Margin
- محاسبه قیمت فروش بر اساس سود یا حاشیه سود هدف
- محاسبات درصد
- درصد افزایش / کاهش
- مالیات
- مقایسه قیمت واحد دو کالا
- نقطه سربه‌سر
- تاریخچه محلی تا 100 محاسبه
- حالت روشن / تاریک / سیستم
- تومان / ریال
- پشتیبانی از ورود اعداد فارسی، عربی و انگلیسی
- Update Manifest برای تشخیص نسخه جدید

## فناوری

- Kotlin
- Jetpack Compose
- Material 3
- minSdk 24 / targetSdk 35
- Package: `com.waxew.hesabyar`

## نسخه‌بندی و آپدیت

نسخه 1.0.0 با `versionCode = 1` شروع می‌شود. برای هر انتشار بعدی باید `versionCode` افزایش یابد و APK با همان کلید امضای Release امضا شود. فایل `distribution/latest.json` مشخصات آخرین نسخه را نگه می‌دارد و اپ در شروع اجرا آن را بررسی می‌کند.

کلید امضای Release عمداً در GitHub ذخیره نمی‌شود. نگهداری امن آن برای امکان نصب نسخه‌های آینده روی نسخه قبلی ضروری است.

## Build

```bash
gradle :app:assembleDebug
```

برای Release امضاشده، متغیرهای محیطی زیر لازم‌اند:

- `HESABYAR_KEYSTORE_FILE`
- `HESABYAR_KEYSTORE_PASSWORD`
- `HESABYAR_KEY_ALIAS`
- `HESABYAR_KEY_PASSWORD`

سپس:

```bash
gradle :app:assembleRelease
```

## برنامه نسخه بعدی

نسخه 1.1/2.0 می‌تواند Android Home Screen Widget، محاسبه سریع از ویجت و ابزارهای فروشنده پیشرفته‌تر را اضافه کند.
