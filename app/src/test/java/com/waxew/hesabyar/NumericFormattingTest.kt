package com.waxew.hesabyar

import org.junit.Assert.assertEquals
import org.junit.Test

/** تست جداکننده سه‌رقمی و تبدیل اعداد فارسی. */
class NumericFormattingTest {
    @Test
    fun groupsLargeIntegerWhileTyping() {
        assertEquals("12,000,000", formatNumericInputForDisplay("12000000"))
    }

    @Test
    fun pastedGroupedNumberRemainsValid() {
        assertEquals(12_000_000.0, "12,000,000".toNumber()!!, 0.001)
    }

    @Test
    fun persianDigitsAreGrouped() {
        assertEquals("12,345,678", formatNumericInputForDisplay("۱۲۳۴۵۶۷۸"))
    }

    @Test
    fun percentFieldCanDisableGrouping() {
        assertEquals("1200", formatNumericInputForDisplay("1200", false))
    }
}
