package com.waxew.hesabyar.data

import android.content.Context
import com.waxew.hesabyar.FeeRule
import com.waxew.hesabyar.FeeRuleType
import com.waxew.hesabyar.InvoiceLine
import com.waxew.hesabyar.MarketplaceProfile
import org.json.JSONArray
import org.json.JSONObject

/** یک پروفایل کاری مستقل برای فروشگاه یا کسب‌وکار. */
data class BusinessProfile(
    val id: Long,
    val name: String,
    val monthlyFixedCost: Double = 0.0,
    val defaultTargetMargin: Double = 20.0
)

/** فاکتور ذخیره‌شده روی دستگاه. */
data class SavedInvoice(
    val id: Long,
    val customerName: String,
    val title: String,
    val lines: List<InvoiceLine>,
    val discountPercent: Double,
    val taxPercent: Double,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * مخزن امکانات حرفه‌ای نسخه 3؛ داده‌ها محلی هستند و با Update برنامه حفظ می‌شوند.
 */
class ProRepository(context: Context) {
    private val prefs = context.getSharedPreferences("hesabyar_pro_v3", Context.MODE_PRIVATE)

    /** پروفایل‌های کاری؛ اگر خالی باشد یک پروفایل پیش‌فرض ساخته می‌شود. */
    fun loadBusinesses(): List<BusinessProfile> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_BUSINESSES, "[]"))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(BusinessProfile(o.getLong("id"), o.getString("name"), o.optDouble("fixed", 0.0), o.optDouble("margin", 20.0)))
            }
        }
    }.getOrDefault(emptyList()).ifEmpty { listOf(BusinessProfile(1L, "فروشگاه من")) }

    fun saveBusinesses(items: List<BusinessProfile>) {
        val arr = JSONArray()
        items.take(20).forEach { b -> arr.put(JSONObject().put("id", b.id).put("name", b.name).put("fixed", b.monthlyFixedCost).put("margin", b.defaultTargetMargin)) }
        prefs.edit().putString(KEY_BUSINESSES, arr.toString()).apply()
    }

    var activeBusinessId: Long
        get() = prefs.getLong(KEY_ACTIVE_BUSINESS, 1L)
        set(value) = prefs.edit().putLong(KEY_ACTIVE_BUSINESS, value).apply()

    /** پروفایل‌های کارمزد کانال‌های فروش. */
    fun loadMarketplaces(): List<MarketplaceProfile> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_MARKETPLACES, "[]"))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val rulesArray = o.optJSONArray("rules") ?: JSONArray()
                val rules = buildList {
                    for (j in 0 until rulesArray.length()) {
                        val r = rulesArray.getJSONObject(j)
                        add(FeeRule(
                            label = r.optString("label", "کارمزد"),
                            type = runCatching { FeeRuleType.valueOf(r.optString("type")) }.getOrDefault(FeeRuleType.PERCENT),
                            value = r.optDouble("value", 0.0),
                            threshold = r.optDouble("threshold", 0.0),
                            maxValue = if (r.has("max")) r.optDouble("max") else null
                        ))
                    }
                }
                add(MarketplaceProfile(o.getLong("id"), o.getString("name"), rules))
            }
        }
    }.getOrDefault(emptyList()).ifEmpty {
        listOf(
            MarketplaceProfile(1L, "فروش مستقیم", emptyList()),
            MarketplaceProfile(2L, "مارکت‌پلیس نمونه", listOf(FeeRule("کارمزد", FeeRuleType.PERCENT, 10.0)))
        )
    }

    fun saveMarketplaces(items: List<MarketplaceProfile>) {
        val arr = JSONArray()
        items.take(30).forEach { p ->
            val rules = JSONArray()
            p.rules.take(20).forEach { r ->
                val o = JSONObject().put("label", r.label).put("type", r.type.name).put("value", r.value).put("threshold", r.threshold)
                r.maxValue?.let { o.put("max", it) }
                rules.put(o)
            }
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("rules", rules))
        }
        prefs.edit().putString(KEY_MARKETPLACES, arr.toString()).apply()
    }

    /** فاکتورها. */
    fun loadInvoices(): List<SavedInvoice> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_INVOICES, "[]"))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val linesArray = o.optJSONArray("lines") ?: JSONArray()
                val lines = buildList {
                    for (j in 0 until linesArray.length()) {
                        val l = linesArray.getJSONObject(j)
                        add(InvoiceLine(l.optString("title", "قلم"), l.optDouble("quantity", 1.0), l.optDouble("unitPrice", 0.0)))
                    }
                }
                add(SavedInvoice(
                    id = o.getLong("id"), customerName = o.optString("customer", ""), title = o.optString("title", "فاکتور"),
                    lines = lines, discountPercent = o.optDouble("discount", 0.0), taxPercent = o.optDouble("tax", 0.0),
                    createdAt = o.optLong("createdAt", o.getLong("id"))
                ))
            }
        }
    }.getOrDefault(emptyList())

    fun saveInvoices(items: List<SavedInvoice>) {
        val arr = JSONArray()
        items.take(200).forEach { inv ->
            val lines = JSONArray()
            inv.lines.take(100).forEach { l -> lines.put(JSONObject().put("title", l.title).put("quantity", l.quantity).put("unitPrice", l.unitPrice)) }
            arr.put(JSONObject()
                .put("id", inv.id).put("customer", inv.customerName).put("title", inv.title)
                .put("discount", inv.discountPercent).put("tax", inv.taxPercent).put("createdAt", inv.createdAt).put("lines", lines))
        }
        prefs.edit().putString(KEY_INVOICES, arr.toString()).apply()
    }

    /** Favoriteهای ابزارها با نام پایدار Route ذخیره می‌شوند. */
    fun loadFavoriteTools(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()
    fun saveFavoriteTools(items: Set<String>) { prefs.edit().putStringSet(KEY_FAVORITES, items).apply() }

    /** شناسه محاسبات ستاره‌دار تاریخچه. */
    fun loadFavoriteHistoryIds(): Set<Long> = (prefs.getStringSet(KEY_HISTORY_FAVORITES, emptySet()) ?: emptySet()).mapNotNull { it.toLongOrNull() }.toSet()
    fun saveFavoriteHistoryIds(items: Set<Long>) { prefs.edit().putStringSet(KEY_HISTORY_FAVORITES, items.map { it.toString() }.toSet()).apply() }

    /** نرخ‌های دستی ارز به‌صورت نام/نرخ. */
    fun loadRates(): Map<String, Double> = runCatching {
        val root = JSONObject(prefs.getString(KEY_RATES, "{}"))
        root.keys().asSequence().associateWith { root.optDouble(it, 0.0) }.filterValues { it > 0.0 }
    }.getOrDefault(emptyMap())
    fun saveRates(rates: Map<String, Double>) {
        val root = JSONObject(); rates.forEach { (k, v) -> if (k.isNotBlank() && v > 0.0) root.put(k, v) }
        prefs.edit().putString(KEY_RATES, root.toString()).apply()
    }

    /** Export همه داده‌های حرفه‌ای برای Backup. */
    fun exportJson(): JSONObject = JSONObject()
        .put("businesses", JSONArray(prefs.getString(KEY_BUSINESSES, "[]")))
        .put("activeBusinessId", activeBusinessId)
        .put("marketplaces", JSONArray(prefs.getString(KEY_MARKETPLACES, "[]")))
        .put("invoices", JSONArray(prefs.getString(KEY_INVOICES, "[]")))
        .put("favorites", JSONArray(loadFavoriteTools().toList()))
        .put("rates", JSONObject(prefs.getString(KEY_RATES, "{}")))

    /** Restore داده‌های حرفه‌ای؛ داده‌های ناشناخته نادیده گرفته می‌شوند. */
    fun importJson(root: JSONObject) {
        root.optJSONArray("businesses")?.let { prefs.edit().putString(KEY_BUSINESSES, it.toString()).apply() }
        activeBusinessId = root.optLong("activeBusinessId", 1L)
        root.optJSONArray("marketplaces")?.let { prefs.edit().putString(KEY_MARKETPLACES, it.toString()).apply() }
        root.optJSONArray("invoices")?.let { prefs.edit().putString(KEY_INVOICES, it.toString()).apply() }
        root.optJSONArray("favorites")?.let { arr ->
            val set = buildSet { for (i in 0 until arr.length()) add(arr.optString(i)) }.filter { it.isNotBlank() }.toSet()
            saveFavoriteTools(set)
        }
        root.optJSONObject("rates")?.let { prefs.edit().putString(KEY_RATES, it.toString()).apply() }
    }

    private companion object {
        const val KEY_BUSINESSES = "businesses"
        const val KEY_ACTIVE_BUSINESS = "active_business"
        const val KEY_MARKETPLACES = "marketplaces"
        const val KEY_INVOICES = "invoices"
        const val KEY_FAVORITES = "favorites"
        const val KEY_RATES = "rates"
        const val KEY_HISTORY_FAVORITES = "history_favorites"
    }
}
