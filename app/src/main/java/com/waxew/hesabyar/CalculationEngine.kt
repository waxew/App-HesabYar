package com.waxew.hesabyar

// ceil برای گرد کردن تعداد فروش سربه‌سر رو به بالا استفاده می‌شود.
import kotlin.math.ceil

/** خروجی محاسبه تخفیف. */
data class DiscountResult(
    // قیمت نهایی بعد از تمام تخفیف‌ها.
    val finalPrice: Double,
    // مقدار پولی که نسبت به قیمت اولیه کم شده است.
    val savedAmount: Double,
    // درصد واقعی تخفیف ترکیبی.
    val effectiveDiscount: Double
)

/** خروجی محاسبه سود. */
data class ProfitResult(
    // هزینه خرید به‌علاوه هزینه‌های جانبی.
    val totalCost: Double,
    // قیمت فروش منهای هزینه واقعی.
    val profit: Double,
    // سود نسبت به هزینه؛ در هزینه صفر تعریف نمی‌شود.
    val markupPercent: Double?,
    // سود نسبت به قیمت فروش؛ در فروش صفر تعریف نمی‌شود.
    val marginPercent: Double?
)

/** خروجی مالیات. */
data class TaxResult(
    // مبلغ خود مالیات.
    val taxAmount: Double,
    // مبلغ پایه به‌علاوه مالیات.
    val totalAmount: Double
)

/** خروجی مقایسه دو بسته. */
data class CompareResult(
    // قیمت هر واحد کالای اول.
    val firstUnitPrice: Double,
    // قیمت هر واحد کالای دوم.
    val secondUnitPrice: Double,
    // 1 یعنی کالای اول و 2 یعنی کالای دوم به‌صرفه‌تر است.
    val winner: Int,
    // درصد تقریبی مزیت قیمت واحد گزینه ارزان‌تر.
    val savingPercent: Double
)

/**
 * موتور مستقل محاسبات حسابیار.
 * جدا بودن فرمول‌ها از UI باعث می‌شود هم تست‌پذیر باشند و هم در Widget نسخه‌های بعدی دوباره استفاده شوند.
 */
object CalculationEngine {

    /** محاسبه یک یا دو تخفیف متوالی. */
    fun discount(price: Double, firstDiscount: Double, secondDiscount: Double = 0.0): DiscountResult? {
        // ورودی‌های خارج از محدوده معتبر رد می‌شوند تا قیمت منفی تولید نشود.
        if (price < 0.0 || firstDiscount !in 0.0..100.0 || secondDiscount !in 0.0..100.0) return null

        // تخفیف اول روی قیمت اصلی اعمال می‌شود و تخفیف دوم روی مبلغ باقی‌مانده.
        val finalPrice = price * (1.0 - firstDiscount / 100.0) * (1.0 - secondDiscount / 100.0)
        // مقدار صرفه‌جویی پولی از اختلاف قیمت‌ها به دست می‌آید.
        val savedAmount = price - finalPrice
        // برای قیمت صفر، درصد واقعی را صفر در نظر می‌گیریم تا تقسیم بر صفر رخ ندهد.
        val effectiveDiscount = if (price > 0.0) (1.0 - finalPrice / price) * 100.0 else 0.0

        // نتیجه کامل به UI تحویل داده می‌شود.
        return DiscountResult(finalPrice, savedAmount, effectiveDiscount)
    }

    /** محاسبه هزینه واقعی، سود، Markup و Margin. */
    fun profit(purchaseCost: Double, extraCost: Double, salePrice: Double): ProfitResult? {
        // هزینه‌ها و قیمت فروش منفی معنی ندارند.
        if (purchaseCost < 0.0 || extraCost < 0.0 || salePrice < 0.0) return null

        // هزینه واقعی از جمع خرید و هزینه جانبی به دست می‌آید.
        val totalCost = purchaseCost + extraCost
        // سود می‌تواند منفی باشد و در این حالت نشان‌دهنده ضرر است.
        val profit = salePrice - totalCost
        // Markup سود را نسبت به هزینه واقعی می‌سنجد؛ هزینه صفر تقسیم را نامعتبر می‌کند.
        val markup = if (totalCost > 0.0) profit / totalCost * 100.0 else null
        // Margin سود را نسبت به قیمت فروش می‌سنجد؛ فروش صفر تقسیم را نامعتبر می‌کند.
        val margin = if (salePrice > 0.0) profit / salePrice * 100.0 else null

        // تمام شاخص‌های سود در یک مدل برگردانده می‌شوند.
        return ProfitResult(totalCost, profit, markup, margin)
    }

    /** محاسبه قیمت فروش بر اساس سود روی هزینه یا حاشیه سود هدف. */
    fun targetPrice(purchaseCost: Double, extraCost: Double, targetPercent: Double, useMargin: Boolean): Double? {
        // هزینه‌ها و درصد هدف منفی در این ابزار پذیرفته نمی‌شوند.
        if (purchaseCost < 0.0 || extraCost < 0.0 || targetPercent < 0.0) return null

        // هزینه واقعی پایه محاسبه قیمت است.
        val totalCost = purchaseCost + extraCost
        // فرمول Margin در 100٪ یا بیشتر مخرج صفر/منفی دارد و باید نامعتبر شناخته شود.
        if (useMargin && targetPercent >= 100.0) return null

        // در حالت Margin قیمت = هزینه / (1 - margin) است؛ در حالت Markup قیمت = هزینه * (1 + markup).
        return if (useMargin) totalCost / (1.0 - targetPercent / 100.0) else totalCost * (1.0 + targetPercent / 100.0)
    }

    /** محاسبه X درصد از Y. */
    fun percentageOf(percent: Double, value: Double): Double? {
        // در این ابزار ورودی منفی پذیرفته نمی‌شود تا رفتار برای کاربر عادی واضح بماند.
        if (percent < 0.0 || value < 0.0) return null
        // درصد به ضریب اعشاری تبدیل و در مقدار ضرب می‌شود.
        return percent / 100.0 * value
    }

    /** محاسبه اینکه part چند درصد whole است. */
    fun whatPercent(part: Double, whole: Double): Double? {
        // مقدار مرجع صفر باعث تقسیم بر صفر می‌شود؛ مقادیر منفی نیز در UI فعلی پذیرفته نیستند.
        if (part < 0.0 || whole <= 0.0) return null
        // نسبت part به whole به درصد تبدیل می‌شود.
        return part / whole * 100.0
    }

    /** محاسبه درصد تغییر مقدار قبلی به مقدار جدید. */
    fun percentageChange(oldValue: Double, newValue: Double): Double? {
        // مقدار قبلی باید بزرگ‌تر از صفر باشد؛ مقدار جدید منفی در این ابزار پذیرفته نیست.
        if (oldValue <= 0.0 || newValue < 0.0) return null
        // اختلاف بر مقدار قبلی تقسیم و به درصد تبدیل می‌شود.
        return (newValue - oldValue) / oldValue * 100.0
    }

    /** محاسبه مالیات و مبلغ نهایی. */
    fun tax(amount: Double, ratePercent: Double): TaxResult? {
        // مبلغ و نرخ مالیات منفی معتبر نیستند.
        if (amount < 0.0 || ratePercent < 0.0) return null
        // مبلغ مالیات از درصد مبلغ پایه محاسبه می‌شود.
        val taxAmount = amount * ratePercent / 100.0
        // مبلغ نهایی برابر مبلغ پایه به‌علاوه مالیات است.
        val totalAmount = amount + taxAmount
        // دو خروجی به UI برگردانده می‌شوند.
        return TaxResult(taxAmount, totalAmount)
    }

    /** مقایسه قیمت واحد دو کالا با واحد مقدار یکسان. */
    fun compare(firstPrice: Double, firstQuantity: Double, secondPrice: Double, secondQuantity: Double): CompareResult? {
        // قیمت منفی یا مقدار صفر/منفی باعث نتیجه نامعتبر می‌شود.
        if (firstPrice < 0.0 || secondPrice < 0.0 || firstQuantity <= 0.0 || secondQuantity <= 0.0) return null

        // قیمت هر واحد بسته اول.
        val firstUnit = firstPrice / firstQuantity
        // قیمت هر واحد بسته دوم.
        val secondUnit = secondPrice / secondQuantity
        // گزینه با قیمت واحد کمتر برنده است؛ در برابری، اولی انتخاب می‌شود.
        val winner = if (firstUnit <= secondUnit) 1 else 2
        // قیمت واحد ارزان‌تر برای محاسبه مزیت.
        val cheaper = minOf(firstUnit, secondUnit)
        // قیمت واحد گران‌تر برای محاسبه درصد اختلاف.
        val expensive = maxOf(firstUnit, secondUnit)
        // اگر هر دو رایگان باشند، مزیت صفر است؛ در غیر این صورت اختلاف نسبی حساب می‌شود.
        val savingPercent = if (expensive > 0.0) (1.0 - cheaper / expensive) * 100.0 else 0.0

        // خروجی کامل مقایسه برگردانده می‌شود.
        return CompareResult(firstUnit, secondUnit, winner, savingPercent)
    }

    /** محاسبه حداقل تعداد فروش لازم برای رسیدن به نقطه سربه‌سر. */
    fun breakEvenUnits(fixedCost: Double, profitPerUnit: Double): Int? {
        // هزینه ثابت نمی‌تواند منفی باشد و سود هر واحد باید مثبت باشد.
        if (fixedCost < 0.0 || profitPerUnit <= 0.0) return null
        // تعداد فروش باید رو به بالا گرد شود چون فروش کسری محصول ممکن نیست.
        return ceil(fixedCost / profitPerUnit).toInt()
    }
}
