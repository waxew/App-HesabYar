package com.waxew.hesabyar

import android.content.Intent
import android.service.quicksettings.TileService

/**
 * Tile پنل Quick Settings اندروید.
 * با لمس آن ماشین‌حساب تخفیف حسابیار مستقیماً باز می‌شود؛ ابزار سریع و بدون نیاز به ورود به Home.
 */
class HesabYarTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_TOOL, CalculatorKind.DISCOUNT.name)
        }
        @Suppress("DEPRECATION")
        startActivityAndCollapse(intent)
    }
}
