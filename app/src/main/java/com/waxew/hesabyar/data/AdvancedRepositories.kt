package com.waxew.hesabyar.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** یک قلم در سبد خرید کاربر. */
data class CartItem(
    val id: Long,
    val name: String,
    val unitPrice: Double,
    val quantity: Double
) {
    /** مبلغ کل این قلم. */
    val total: Double get() = unitPrice * quantity
}

/** یک رکورد دفترچه قیمت برای مقایسه قیمت خرید در زمان‌های مختلف. */
data class PriceRecord(
    val id: Long,
    val productName: String,
    val price: Double,
    val quantity: Double,
    val unitLabel: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    /** قیمت هر واحد برای مقایسه بسته‌هایی با اندازه متفاوت. */
    val unitPrice: Double get() = if (quantity > 0.0) price / quantity else price
}

/** ذخیره بودجه و اقلام سبد خرید به‌صورت محلی و مقاوم در برابر Update. */
class ShoppingRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** بودجه فعلی کاربر؛ صفر یعنی هنوز بودجه‌ای تعیین نشده است. */
    var budget: Double
        get() = java.lang.Double.longBitsToDouble(prefs.getLong(KEY_BUDGET, 0L))
        set(value) = prefs.edit().putLong(KEY_BUDGET, java.lang.Double.doubleToRawLongBits(value.coerceAtLeast(0.0))).apply()

    /** اقلام سبد را از JSON داخلی می‌خواند. */
    fun loadItems(): List<CartItem> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ITEMS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    CartItem(
                        id = item.getLong("id"),
                        name = item.getString("name"),
                        unitPrice = item.getDouble("unitPrice"),
                        quantity = item.getDouble("quantity")
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    /** سبد فعلی را با سقف منطقی ۲۰۰ قلم ذخیره می‌کند. */
    fun saveItems(items: List<CartItem>) {
        val array = JSONArray()
        items.take(MAX_ITEMS).forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", item.name)
                    .put("unitPrice", item.unitPrice)
                    .put("quantity", item.quantity)
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    /** داده خام این بخش برای Backup برگردانده می‌شود. */
    fun exportJson(): JSONObject = JSONObject()
        .put("budget", budget)
        .put("items", JSONArray(prefs.getString(KEY_ITEMS, "[]")))

    /** داده Backup را با اعتبارسنجی پایه جایگزین می‌کند. */
    fun importJson(root: JSONObject) {
        budget = root.optDouble("budget", 0.0).coerceAtLeast(0.0)
        val array = root.optJSONArray("items") ?: JSONArray()
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "hesabyar_shopping"
        const val KEY_BUDGET = "budget_bits"
        const val KEY_ITEMS = "items"
        const val MAX_ITEMS = 200
    }
}

/** مخزن دفترچه قیمت؛ همه رکوردها روی خود دستگاه ذخیره می‌شوند. */
class PriceBookRepository(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** فهرست قیمت‌های قبلی را از جدید به قدیم برمی‌گرداند. */
    fun load(): List<PriceRecord> = runCatching {
        val array = JSONArray(prefs.getString(KEY_ITEMS, "[]"))
        buildList {
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    PriceRecord(
                        id = item.getLong("id"),
                        productName = item.getString("productName"),
                        price = item.getDouble("price"),
                        quantity = item.optDouble("quantity", 1.0).takeIf { it > 0.0 } ?: 1.0,
                        unitLabel = item.optString("unitLabel", "عدد"),
                        createdAt = item.optLong("createdAt", item.getLong("id"))
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    /** تا ۵۰۰ ثبت آخر نگهداری می‌شود تا روند قیمت قابل مشاهده بماند. */
    fun save(items: List<PriceRecord>) {
        val array = JSONArray()
        items.take(MAX_ITEMS).forEach { item ->
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("productName", item.productName)
                    .put("price", item.price)
                    .put("quantity", item.quantity)
                    .put("unitLabel", item.unitLabel)
                    .put("createdAt", item.createdAt)
            )
        }
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    /** JSON خام برای Backup. */
    fun exportJson(): JSONObject = JSONObject()
        .put("items", JSONArray(prefs.getString(KEY_ITEMS, "[]")))

    /** بازیابی JSON دفترچه قیمت. */
    fun importJson(root: JSONObject) {
        val array = root.optJSONArray("items") ?: JSONArray()
        prefs.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "hesabyar_price_book"
        const val KEY_ITEMS = "items"
        const val MAX_ITEMS = 500
    }
}
