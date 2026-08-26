package com.waxew.hesabyar

// assertEquals برای مقایسه خروجی عددی با مقدار مورد انتظار استفاده می‌شود.
import org.junit.Assert.assertEquals
// assertNull اعتبارسنجی می‌کند ورودی نامعتبر نتیجه تولید نکند.
import org.junit.Assert.assertNull
// Test هر تابع را به یک تست JUnit تبدیل می‌کند.
import org.junit.Test

/** تست‌های Regression فرمول‌های اصلی حسابیار. */
class CalculationEngineTest {
    /** دو تخفیف ۳۰٪ و ۱۰٪ باید در مجموع ۳۷٪ تخفیف واقعی بسازند. */
    @Test
    fun stackedDiscount_isCalculatedSequentially() {
        // محاسبه برای قیمت پایه ۱۰۰۰ انجام می‌شود.
        val result = CalculationEngine.discount(1000.0, 30.0, 10.0)!!
        // قیمت نهایی باید ۶۳۰ باشد.
        assertEquals(630.0, result.finalPrice, 0.0001)
        // صرفه‌جویی باید ۳۷۰ باشد.
        assertEquals(370.0, result.savedAmount, 0.0001)
        // تخفیف واقعی باید ۳۷٪ باشد، نه ۴۰٪.
        assertEquals(37.0, result.effectiveDiscount, 0.0001)
    }

    /** سود، Markup و Margin باید از مبناهای متفاوت محاسبه شوند. */
    @Test
    fun profit_reportsMarkupAndMarginSeparately() {
        // خرید ۸۰۰ + هزینه جانبی ۵۰ و فروش ۱۲۰۰.
        val result = CalculationEngine.profit(800.0, 50.0, 1200.0)!!
        // هزینه واقعی ۸۵۰ است.
        assertEquals(850.0, result.totalCost, 0.0001)
        // سود خالص ۳۵۰ است.
        assertEquals(350.0, result.profit, 0.0001)
        // Markup نسبت به هزینه حدود ۴۱.۱۷٪ است.
        assertEquals(41.1764705, result.markupPercent!!, 0.0001)
        // Margin نسبت به فروش حدود ۲۹.۱۷٪ است.
        assertEquals(29.1666666, result.marginPercent!!, 0.0001)
    }

    /** قیمت لازم برای Margin سی درصد با هزینه ۱۰۰۰ حدود ۱۴۲۸.۵۷ است. */
    @Test
    fun targetPrice_marginUsesCorrectFormula() {
        // حالت useMargin=true فرمول Margin را فعال می‌کند.
        val result = CalculationEngine.targetPrice(1000.0, 0.0, 30.0, true)!!
        // خروجی با مقدار نظری مقایسه می‌شود.
        assertEquals(1428.5714285, result, 0.0001)
    }

    /** Margin صد درصد از نظر ریاضی قیمت محدود ندارد و باید رد شود. */
    @Test
    fun targetPrice_rejectsHundredPercentMargin() {
        // این ورودی قبلاً در UI به‌اشتباه مثل Markup محاسبه می‌شد.
        val result = CalculationEngine.targetPrice(1000.0, 0.0, 100.0, true)
        // رفتار صحیح، ندادن نتیجه است.
        assertNull(result)
    }

    /** افزایش ۸۰۰ به ۱۰۰۰ باید ۲۵٪ باشد. */
    @Test
    fun percentageChange_isCorrect() {
        // درصد تغییر محاسبه می‌شود.
        val result = CalculationEngine.percentageChange(800.0, 1000.0)!!
        // نتیجه باید دقیقاً ۲۵ باشد.
        assertEquals(25.0, result, 0.0001)
    }

    /** مالیات ده درصد روی ۱۰۰۰ باید ۱۰۰ و مجموع ۱۱۰۰ بسازد. */
    @Test
    fun tax_isCorrect() {
        // محاسبه مالیات اجرا می‌شود.
        val result = CalculationEngine.tax(1000.0, 10.0)!!
        // مبلغ مالیات بررسی می‌شود.
        assertEquals(100.0, result.taxAmount, 0.0001)
        // مبلغ نهایی بررسی می‌شود.
        assertEquals(1100.0, result.totalAmount, 0.0001)
    }

    /** نقطه سربه‌سر باید تعداد فروش را رو به بالا گرد کند. */
    @Test
    fun breakEven_roundsUp() {
        // هزینه ثابت ۵۰ میلیون و سود واحد ۲۶۰ هزار نیازمند ۱۹۳ فروش است.
        val result = CalculationEngine.breakEvenUnits(50_000_000.0, 260_000.0)
        // ۱۹۲ فروش کافی نیست، بنابراین ceil باید ۱۹۳ بدهد.
        assertEquals(193, result)
    }
}
