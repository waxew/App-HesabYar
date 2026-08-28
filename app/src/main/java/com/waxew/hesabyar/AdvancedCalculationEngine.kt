package com.waxew.hesabyar

import kotlin.math.max

/** نتیجه کامل قیمت‌گذاری فروشنده با هزینه‌های ثابت و درصدی. */
data class SellerPricingResult(
    val fixedCost: Double,
    val variableRatePercent: Double,
    val suggestedPrice: Double,
    val breakEvenPrice: Double,
    val expectedProfit: Double,
    val expectedMarginPercent: Double
)

/** یک سناریوی آماده برای مقایسه چند سطح قیمت‌گذاری. */
data class PricingScenario(
    val label: String,
    val targetMargin: Double,
    val salePrice: Double
)

/** نتیجه محاسبه قیمت عمده. */
data class BulkPriceResult(
    val unitPrice: Double,
    val totalPrice: Double,
    val unitProfit: Double
)

/**
 * محاسبات پیشرفته حسابیار که بین صفحه فروشنده، گزارش و Widget قابل استفاده مجدد است.
 */
object AdvancedCalculationEngine {

    /** مجموع درصد هزینه‌هایی که از مبلغ فروش کم می‌شوند. */
    private fun variableRate(
        advertisingPercent: Double,
        platformPercent: Double,
        gatewayPercent: Double,
        taxPercent: Double
    ): Double = advertisingPercent + platformPercent + gatewayPercent + taxPercent

    /**
     * قیمت فروش را طوری حل می‌کند که بعد از هزینه‌های درصدی، Margin هدف باقی بماند.
     * فرمول: P = fixedCost / (1 - fees - margin)
     */
    fun sellerPricing(
        purchaseCost: Double,
        shippingCost: Double,
        packagingCost: Double,
        otherFixedCost: Double,
        advertisingPercent: Double,
        platformPercent: Double,
        gatewayPercent: Double,
        taxPercent: Double,
        targetMarginPercent: Double
    ): SellerPricingResult? {
        val values = listOf(
            purchaseCost, shippingCost, packagingCost, otherFixedCost,
            advertisingPercent, platformPercent, gatewayPercent, taxPercent, targetMarginPercent
        )
        if (values.any { it < 0.0 }) return null

        val fixedCost = purchaseCost + shippingCost + packagingCost + otherFixedCost
        val feePercent = variableRate(advertisingPercent, platformPercent, gatewayPercent, taxPercent)
        val denominator = 1.0 - (feePercent + targetMarginPercent) / 100.0
        if (denominator <= 0.0) return null

        val suggested = fixedCost / denominator
        val breakEvenDenominator = 1.0 - feePercent / 100.0
        if (breakEvenDenominator <= 0.0) return null
        val breakEven = fixedCost / breakEvenDenominator
        val profit = suggested - fixedCost - suggested * feePercent / 100.0
        val margin = if (suggested > 0.0) profit / suggested * 100.0 else 0.0

        return SellerPricingResult(
            fixedCost = fixedCost,
            variableRatePercent = feePercent,
            suggestedPrice = suggested,
            breakEvenPrice = breakEven,
            expectedProfit = profit,
            expectedMarginPercent = margin
        )
    }

    /** بیشترین تخفیف درصدی که قیمت فعلی را زیر Break-even نمی‌برد. */
    fun maxSafeDiscount(currentPrice: Double, breakEvenPrice: Double): Double? {
        if (currentPrice <= 0.0 || breakEvenPrice < 0.0) return null
        if (breakEvenPrice >= currentPrice) return 0.0
        return ((1.0 - breakEvenPrice / currentPrice) * 100.0).coerceIn(0.0, 100.0)
    }

    /** سه سناریوی فروش سریع، متعادل و سود بیشتر بر اساس Margin تولید می‌کند. */
    fun pricingScenarios(
        purchaseCost: Double,
        shippingCost: Double,
        packagingCost: Double,
        otherFixedCost: Double,
        advertisingPercent: Double,
        platformPercent: Double,
        gatewayPercent: Double,
        taxPercent: Double
    ): List<PricingScenario> {
        val targets = listOf("فروش سریع" to 12.0, "متعادل" to 22.0, "سود بیشتر" to 32.0)
        return targets.mapNotNull { (label, margin) ->
            sellerPricing(
                purchaseCost, shippingCost, packagingCost, otherFixedCost,
                advertisingPercent, platformPercent, gatewayPercent, taxPercent, margin
            )?.let { PricingScenario(label, margin, it.suggestedPrice) }
        }
    }

    /** قیمت عمده بر اساس قیمت خرده، درصد تخفیف عمده و تعداد محاسبه می‌شود. */
    fun bulkPrice(
        retailUnitPrice: Double,
        breakEvenUnitPrice: Double,
        quantity: Double,
        wholesaleDiscountPercent: Double
    ): BulkPriceResult? {
        if (retailUnitPrice < 0.0 || breakEvenUnitPrice < 0.0 || quantity <= 0.0 || wholesaleDiscountPercent !in 0.0..100.0) {
            return null
        }
        val requestedUnit = retailUnitPrice * (1.0 - wholesaleDiscountPercent / 100.0)
        // قیمت عمده هرگز پایین‌تر از نقطه سربه‌سر پیشنهاد نمی‌شود.
        val unitPrice = max(requestedUnit, breakEvenUnitPrice)
        return BulkPriceResult(
            unitPrice = unitPrice,
            totalPrice = unitPrice * quantity,
            unitProfit = unitPrice - breakEvenUnitPrice
        )
    }
}
