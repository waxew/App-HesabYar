package com.waxew.hesabyar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Regression testهای قابلیت‌های جدید نسخه ۲. */
class AdvancedCalculationEngineTest {

    @Test
    fun sellerPricing_accountsForPercentageFeesAndMargin() {
        val result = AdvancedCalculationEngine.sellerPricing(
            purchaseCost = 800_000.0,
            shippingCost = 40_000.0,
            packagingCost = 20_000.0,
            otherFixedCost = 0.0,
            advertisingPercent = 5.0,
            platformPercent = 5.0,
            gatewayPercent = 1.0,
            taxPercent = 0.0,
            targetMarginPercent = 25.0
        )
        assertNotNull(result)
        assertEquals(860_000.0 / 0.64, result!!.suggestedPrice, 0.01)
        assertEquals(25.0, result.expectedMarginPercent, 0.01)
    }

    @Test
    fun sellerPricing_rejectsImpossibleFeeAndMarginCombination() {
        val result = AdvancedCalculationEngine.sellerPricing(
            100.0, 0.0, 0.0, 0.0,
            20.0, 20.0, 10.0, 0.0, 50.0
        )
        assertNull(result)
    }

    @Test
    fun maxSafeDiscount_neverGoesBelowBreakEven() {
        assertEquals(20.0, AdvancedCalculationEngine.maxSafeDiscount(1_000.0, 800.0)!!, 0.001)
        assertEquals(0.0, AdvancedCalculationEngine.maxSafeDiscount(700.0, 800.0)!!, 0.001)
    }

    @Test
    fun bulkPrice_clampsAtBreakEven() {
        val result = AdvancedCalculationEngine.bulkPrice(1_000.0, 850.0, 20.0, 30.0)
        assertNotNull(result)
        assertEquals(850.0, result!!.unitPrice, 0.001)
        assertEquals(17_000.0, result.totalPrice, 0.001)
    }
}
