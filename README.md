# حسابیار (HesabYar)

حسابیار یک اپ اندرویدی فارسی، آفلاین‌محور و قابل‌گسترش برای محاسبات خرید، تخفیف، سود، قیمت‌گذاری، مقایسه فروش و مدیریت سبک اطلاعات مالی روزمره است.

## نسخه 3.0.0

- `versionCode = 4`
- `applicationId = com.waxew.hesabyar`
- Kotlin + Jetpack Compose + Material 3
- `minSdk 24` / `targetSdk 35`
- سازگار با نصب روی نسخه‌های قبلی با همان Application ID و کلید Release

## امکانات

### محاسبات پایه و فروشنده
- تخفیف ساده/چندمرحله‌ای، درصد، افزایش و کاهش، مالیات
- سود خالص، Markup، Profit Margin، Margin هدف و نقطه سربه‌سر
- قیمت‌گذاری عمده و سناریوهای فروش سریع/متعادل/سود بیشتر
- جداکننده هزارگان هنگام ورود مبلغ و پشتیبانی اعداد فارسی/عربی/انگلیسی

### مرکز ابزارهای حرفه‌ای v3
- پروفایل مارکت‌پلیس و قوانین کارمزد ثابت، درصدی و پلکانی
- مقایسه سود خالص بین کانال‌های فروش
- مقایسه خرید نقدی و اقساطی و هزینه واقعی اقساط
- نمودار/تاریخچه قیمت، بهترین قیمت، تورم شخصی و Shrinkflation
- هشدار افت Margin هنگام افزایش بهای خرید
- What-if Slider برای مشاهده زنده اثر تخفیف روی سود
- فاکتور آفلاین و خروجی PDF
- چند پروفایل کاری/فروشگاهی
- Import گروهی محصولات از CSV/XLSX
- Backup/Restore کامل JSON و Backup رمزدار AES-256-GCM
- OCR فاکتور با استخراج خطوط احتمالی کالا/مبلغ و اسکن Barcode/QR
- جستجو و Favorite ابزارها و تاریخچه
- تبدیل واحد و ارز با نرخ دستی
- فرمان متنی فارسی برای قیمت‌گذاری سریع
- Cash Check ماهانه برای سربه‌سر و سود هدف

### Android Integration
- Widget واکنش‌گرا
- App Shortcuts برای تخفیف، سود و درصد
- Quick Settings Tile
- Drawer راست‌به‌چپ با پروفایل و دسترسی کامل
- رفتار Back صحیح بین صفحات

### داده و خروجی
- ذخیره اطلاعات اصلی به‌صورت محلی روی دستگاه
- PDF / CSV / Excel `.xlsx`
- Backup کامل شامل تاریخچه، تنظیمات، پروفایل، سبد خرید، Price Book و داده‌های حرفه‌ای v3
- کلید خصوصی Release هرگز در مخزن عمومی GitHub نگهداری نمی‌شود

## Build

CI قبل از انتشار Unit Test و هر دو Variant را می‌سازد:

```bash
gradle :app:testDebugUnitTest
gradle :app:assembleDebug
gradle :app:assembleRelease
```

امضای Release با متغیرهای محیطی زیر انجام می‌شود:
- `HESABYAR_KEYSTORE_FILE`
- `HESABYAR_KEYSTORE_PASSWORD`
- `HESABYAR_KEY_ALIAS`
- `HESABYAR_KEY_PASSWORD`

## ساختار مهم

- `CalculationEngine.kt`: فرمول‌های پایه
- `AdvancedCalculationEngine.kt`: قیمت‌گذاری فروشنده و سناریوها
- `V3Engine.kt`: موتور مستقل ابزارهای حرفه‌ای v3
- `V3Screens.kt`: UI ابزارهای حرفه‌ای
- `V3Repositories.kt`: داده محلی امکانات v3
- `V3DataTools.kt`: رمزگذاری، Import، PDF فاکتور و OCR ساختاریافته
- `DataTransferManager.kt`: PDF/CSV/XLSX و Backup/Restore
- `HesabYarWidgetProvider.kt`: Widget
- `HesabYarTileService.kt`: Quick Settings Tile
- `UpdateChecker.kt`: بررسی نسخه جدید

## قرارداد آپدیت

1. `applicationId` تغییر نکند.
2. `versionCode` در هر انتشار افزایش یابد.
3. APK با همان Keystore دائمی حسابیار امضا شود.
4. SharedPreferences موجود بدون Migration حذف یا تغییر نام داده نشوند.
5. `distribution/latest.json` فقط بعد از Verify APK نهایی به نسخه جدید تغییر کند.

جزئیات تغییرات در `CHANGELOG.md` و برنامه بعدی در `ROADMAP.md` ثبت شده است.
