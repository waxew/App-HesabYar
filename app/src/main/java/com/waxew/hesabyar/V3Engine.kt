package com.waxew.hesabyar

import kotlin.math.ceil
import kotlin.math.pow

/** نوع قانون هزینه در پروفایل فروشنده/مارکت‌پلیس. */
enum class FeeRuleType { FIXED, PERCENT, TIERED_PERCENT }

/** قانون هزینه قابل ترکیب؛ برای کارمزد ثابت، درصدی و پلکانی. */
data class FeeRule(
    val label: String,
    val type: FeeRuleType,
    val value: Double,
    val threshold: Double = 0.0,
    val maxValue: Double? = null
)

/** پروفایل یک کانال فروش؛ کاربر می‌تواند برای هر فروشگاه/پلتفرم قوانین مستقل داشته باشد. */
data class MarketplaceProfile(val id: Long, val name: String, val rules: List<FeeRule>)

/** خروجی مقایسه قیمت‌گذاری یک کانال فروش. */
data class MarketplaceQuote(
    val marketplaceName: String,
    val salePrice: Double,
    val totalFees: Double,
    val netProfit: Double,
    val marginPercent: Double
)

data class InstallmentResult(
    val cashPrice: Double,
    val downPayment: Double,
    val installmentAmount: Double,
    val count: Int,
    val totalInstallmentCost: Double,
    val extraCostVsCash: Double,
    val extraPercentVsCash: Double,
    val approximateMonthlyRatePercent: Double
)

data class PersonalInflationResult(
    val oldBasketCost: Double,
    val newBasketCost: Double,
    val inflationPercent: Double,
    val purchasingPowerChangePercent: Double
)

data class ShrinkflationResult(
    val oldQuantity: Double,
    val newQuantity: Double,
    val oldPrice: Double,
    val newPrice: Double,
    val unitPriceChangePercent: Double,
    val quantityDropPercent: Double,
    val isShrinkflation: Boolean
)

data class WhatIfResult(
    val customerPrice: Double,
    val netProfit: Double,
    val marginPercent: Double,
    val isLoss: Boolean
)

data class CashCheckResult(
    val requiredUnitsForBreakEven: Int,
    val revenueForBreakEven: Double,
    val unitsForTargetProfit: Int,
    val revenueForTargetProfit: Double
)

data class InvoiceLine(val title: String, val quantity: Double, val unitPrice: Double) {
    val total: Double get() = quantity * unitPrice
}

data class InvoiceTotals(
    val subtotal: Double,
    val discountAmount: Double,
    val taxAmount: Double,
    val finalTotal: Double
)

data class ParsedSmartCommand(
    val cost: Double?,
    val feePercent: Double?,
    val targetMarginPercent: Double?,
    val discountPercent: Double?
)

/** موتور امکانات حرفه‌ای حسابیار 3؛ بدون Android API تا با Unit Test مستقل بررسی شود. */
object V3Engine {
    fun feeForRule(rule: FeeRule, salePrice: Double): Double {
        if (salePrice < 0.0 || rule.value < 0.0) return 0.0
        val raw = when (rule.type) {
            FeeRuleType.FIXED -> rule.value
            FeeRuleType.PERCENT -> salePrice * rule.value / 100.0
            FeeRuleType.TIERED_PERCENT -> if (salePrice >= rule.threshold) salePrice * rule.value / 100.0 else 0.0
        }
        return rule.maxValue?.let { raw.coerceAtMost(it.coerceAtLeast(0.0)) } ?: raw
    }

    fun marketplaceQuote(profile: MarketplaceProfile, landedCost: Double, salePrice: Double): MarketplaceQuote? {
        if (landedCost < 0.0 || salePrice <= 0.0) return null
        val fees = profile.rules.sumOf { feeForRule(it, salePrice) }
        val profit = salePrice - landedCost - fees
        return MarketplaceQuote(profile.name, salePrice, fees, profit, profit / salePrice * 100.0)
    }

    fun compareMarketplaces(profiles: List<MarketplaceProfile>, landedCost: Double, salePrice: Double): List<MarketplaceQuote> =
        profiles.mapNotNull { marketplaceQuote(it, landedCost, salePrice) }.sortedByDescending { it.netProfit }

    fun installment(cashPrice: Double, downPayment: Double, installmentAmount: Double, count: Int): InstallmentResult? {
        if (cashPrice <= 0.0 || downPayment < 0.0 || installmentAmount < 0.0 || count <= 0) return null
        val total = downPayment + installmentAmount * count
        val extra = total - cashPrice
        val financed = (cashPrice - downPayment).coerceAtLeast(1.0)
        val monthly = if (extra <= 0.0) 0.0 else extra / financed / count * 100.0
        return InstallmentResult(cashPrice, downPayment, installmentAmount, count, total, extra, extra / cashPrice * 100.0, monthly)
    }

    fun personalInflation(oldBasketCost: Double, newBasketCost: Double): PersonalInflationResult? {
        if (oldBasketCost <= 0.0 || newBasketCost < 0.0) return null
        val inflation = (newBasketCost - oldBasketCost) / oldBasketCost * 100.0
        val purchasingPower = if (newBasketCost > 0.0) oldBasketCost / newBasketCost * 100.0 - 100.0 else 0.0
        return PersonalInflationResult(oldBasketCost, newBasketCost, inflation, purchasingPower)
    }

    fun shrinkflation(oldPrice: Double, oldQuantity: Double, newPrice: Double, newQuantity: Double): ShrinkflationResult? {
        if (oldPrice < 0.0 || newPrice < 0.0 || oldQuantity <= 0.0 || newQuantity <= 0.0) return null
        val oldUnit = oldPrice / oldQuantity
        val newUnit = newPrice / newQuantity
        val unitChange = if (oldUnit > 0.0) (newUnit - oldUnit) / oldUnit * 100.0 else 0.0
        val quantityDrop = (oldQuantity - newQuantity) / oldQuantity * 100.0
        return ShrinkflationResult(oldQuantity, newQuantity, oldPrice, newPrice, unitChange, quantityDrop, quantityDrop > 0.0 && unitChange > 0.0)
    }

    fun marginAtSalePrice(salePrice: Double, cost: Double, variableFeePercent: Double = 0.0): Double? {
        if (salePrice <= 0.0 || cost < 0.0 || variableFeePercent !in 0.0..<100.0) return null
        val profit = salePrice - cost - salePrice * variableFeePercent / 100.0
        return profit / salePrice * 100.0
    }

    fun whatIf(baseSalePrice: Double, landedCost: Double, feePercent: Double, discountPercent: Double): WhatIfResult? {
        if (baseSalePrice <= 0.0 || landedCost < 0.0 || feePercent < 0.0 || discountPercent !in 0.0..100.0) return null
        val customerPrice = baseSalePrice * (1.0 - discountPercent / 100.0)
        val fees = customerPrice * feePercent / 100.0
        val profit = customerPrice - landedCost - fees
        val margin = if (customerPrice > 0.0) profit / customerPrice * 100.0 else -100.0
        return WhatIfResult(customerPrice, profit, margin, profit < 0.0)
    }

    fun invoiceTotals(lines: List<InvoiceLine>, discountPercent: Double, taxPercent: Double): InvoiceTotals? {
        if (lines.any { it.quantity < 0.0 || it.unitPrice < 0.0 } || discountPercent !in 0.0..100.0 || taxPercent < 0.0) return null
        val subtotal = lines.sumOf { it.total }
        val discount = subtotal * discountPercent / 100.0
        val taxable = subtotal - discount
        val tax = taxable * taxPercent / 100.0
        return InvoiceTotals(subtotal, discount, tax, taxable + tax)
    }

    fun cashCheck(monthlyFixedCost: Double, contributionPerUnit: Double, unitSalePrice: Double, targetProfit: Double): CashCheckResult? {
        if (monthlyFixedCost < 0.0 || contributionPerUnit <= 0.0 || unitSalePrice < 0.0 || targetProfit < 0.0) return null
        val breakEvenUnits = ceil(monthlyFixedCost / contributionPerUnit).toInt()
        val targetUnits = ceil((monthlyFixedCost + targetProfit) / contributionPerUnit).toInt()
        return CashCheckResult(breakEvenUnits, breakEvenUnits * unitSalePrice, targetUnits, targetUnits * unitSalePrice)
    }

    fun convertUnit(value: Double, from: String, to: String): Double? {
        if (value < 0.0) return null
        val weight = mapOf("mg" to 0.001, "g" to 1.0, "kg" to 1000.0)
        val length = mapOf("mm" to 0.001, "cm" to 0.01, "m" to 1.0, "km" to 1000.0)
        val volume = mapOf("ml" to 1.0, "l" to 1000.0)
        val group = listOf(weight, length, volume).firstOrNull { it.containsKey(from) && it.containsKey(to) } ?: return null
        return value * group.getValue(from) / group.getValue(to)
    }

    fun convertCurrency(amount: Double, sourceToTargetRate: Double): Double? {
        if (amount < 0.0 || sourceToTargetRate <= 0.0) return null
        return amount * sourceToTargetRate
    }

    fun parseSmartCommand(text: String): ParsedSmartCommand {
        val normalized = text
            .replace('۰','0').replace('۱','1').replace('۲','2').replace('۳','3').replace('۴','4')
            .replace('۵','5').replace('۶','6').replace('۷','7').replace('۸','8').replace('۹','9')
            .replace('٠','0').replace('١','1').replace('٢','2').replace('٣','3').replace('٤','4')
            .replace('٥','5').replace('٦','6').replace('٧','7').replace('٨','8').replace('٩','9')
            .replace(",", "").replace("٬", "").replace("،", "")
        val numbers = Regex("\\d+(?:\\.\\d+)?").findAll(normalized).mapNotNull { it.value.toDoubleOrNull() }.toList()
        val cost = numbers.firstOrNull()
        val percentages = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:%|درصد)").findAll(normalized).mapNotNull { it.groupValues[1].toDoubleOrNull() }.toList()
        val fee = if ("کارمزد" in normalized) percentages.firstOrNull() else null
        val margin = if ("سود" in normalized || "مارجین" in normalized || "margin" in normalized.lowercase()) percentages.getOrNull(if (fee != null) 1 else 0) else null
        val discount = if ("تخفیف" in normalized) percentages.lastOrNull() else null
        return ParsedSmartCommand(cost, fee, margin, discount)
    }

    fun smartSuggestedPrice(command: ParsedSmartCommand): Double? {
        val cost = command.cost ?: return null
        val fee = command.feePercent ?: 0.0
        val margin = command.targetMarginPercent ?: return null
        if (cost < 0.0 || fee < 0.0 || margin < 0.0 || fee + margin >= 100.0) return null
        return cost / (1.0 - (fee + margin) / 100.0)
    }

    fun effectiveAnnualRate(monthlyRatePercent: Double): Double {
        if (monthlyRatePercent <= 0.0) return 0.0
        return ((1.0 + monthlyRatePercent / 100.0).pow(12.0) - 1.0) * 100.0
    }
}
