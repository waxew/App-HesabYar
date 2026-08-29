package com.waxew.hesabyar

import org.junit.Assert.*
import org.junit.Test

class V3EngineTest {
    @Test fun marketplaceComparisonAccountsForFees() {
        val a = MarketplaceProfile(1, "A", listOf(FeeRule("fee", FeeRuleType.PERCENT, 10.0)))
        val b = MarketplaceProfile(2, "B", listOf(FeeRule("fee", FeeRuleType.PERCENT, 5.0)))
        val quotes = V3Engine.compareMarketplaces(listOf(a, b), 700.0, 1000.0)
        assertEquals("B", quotes.first().marketplaceName)
        assertEquals(250.0, quotes.first().netProfit, 0.001)
    }

    @Test fun installmentShowsTrueExtraCost() {
        val r = V3Engine.installment(1000.0, 200.0, 100.0, 10)!!
        assertEquals(1200.0, r.totalInstallmentCost, 0.001)
        assertEquals(20.0, r.extraPercentVsCash, 0.001)
    }

    @Test fun shrinkflationDetected() {
        val r = V3Engine.shrinkflation(100.0, 1000.0, 110.0, 900.0)!!
        assertTrue(r.isShrinkflation)
        assertTrue(r.unitPriceChangePercent > 20.0)
    }

    @Test fun smartCommandFindsCostFeeAndMargin() {
        val p = V3Engine.parseSmartCommand("850000 خریدم 7 درصد کارمزد دارم 30 درصد سود میخوام")
        assertEquals(850000.0, p.cost!!, 0.001)
        assertEquals(7.0, p.feePercent!!, 0.001)
        assertEquals(30.0, p.targetMarginPercent!!, 0.001)
        val suggested = V3Engine.smartSuggestedPrice(p)!!
        assertEquals(1349206.349, suggested, 0.1)
    }

    @Test fun whatIfFlagsLoss() {
        val r = V3Engine.whatIf(1000.0, 850.0, 5.0, 20.0)!!
        assertTrue(r.isLoss)
    }

    @Test fun unitConversionWorks() {
        assertEquals(1500.0, V3Engine.convertUnit(1.5, "kg", "g")!!, 0.001)
        assertEquals(2.0, V3Engine.convertUnit(2000.0, "ml", "l")!!, 0.001)
    }
}
