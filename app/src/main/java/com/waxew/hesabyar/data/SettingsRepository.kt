package com.waxew.hesabyar.data

import android.content.Context

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class CurrencyMode(val label: String) { TOMAN("تومان"), RIAL("ریال") }

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("hesabyar_settings", Context.MODE_PRIVATE)

    var themeMode: ThemeMode
        get() = runCatching { ThemeMode.valueOf(prefs.getString("theme", ThemeMode.SYSTEM.name)!!) }
            .getOrDefault(ThemeMode.SYSTEM)
        set(value) { prefs.edit().putString("theme", value.name).apply() }

    var currencyMode: CurrencyMode
        get() = runCatching { CurrencyMode.valueOf(prefs.getString("currency", CurrencyMode.TOMAN.name)!!) }
            .getOrDefault(CurrencyMode.TOMAN)
        set(value) { prefs.edit().putString("currency", value.name).apply() }
}
