@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.waxew.hesabyar

// Compose state/layout imports used by all calculator screens.
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waxew.hesabyar.data.CurrencyMode
import kotlin.math.abs

/**
 * Router مربوط به ابزارهای محاسباتی.
 * Shell فقط نوع ابزار را می‌فرستد و این تابع صفحه مناسب را انتخاب می‌کند.
 */
@Composable
fun CalculatorScreen(kind: CalculatorKind, currency: CurrencyMode, onSave: (String, String, String) -> Unit) {
    // هر enum دقیقاً به Composable خودش نگاشت می‌شود.
    when (kind) {
        CalculatorKind.DISCOUNT -> DiscountCalculator(currency, onSave)
        CalculatorKind.PROFIT -> ProfitCalculator(currency, onSave)
        CalculatorKind.TARGET_PRICE -> TargetPriceCalculator(currency, onSave)
        CalculatorKind.PERCENTAGE -> PercentageCalculator(onSave)
        CalculatorKind.CHANGE -> ChangeCalculator(onSave)
        CalculatorKind.TAX -> TaxCalculator(currency, onSave)
        CalculatorKind.COMPARE -> CompareCalculator(currency, onSave)
        CalculatorKind.BREAK_EVEN -> BreakEvenCalculator(currency, onSave)
    }
}

/** تخفیف ساده یا دو تخفیف متوالی را محاسبه می‌کند. */
@Composable
private fun DiscountCalculator(currency: CurrencyMode, onSave: (String, String, String) -> Unit) {
    // State ورودی‌ها با rememberSaveable در بازسازی Activity حفظ می‌شود.
    var priceText by rememberSaveable { mutableStateOf("") }
    var firstText by rememberSaveable { mutableStateOf("") }
    var secondText by rememberSaveable { mutableStateOf("") }
    // تبدیل رشته‌های پاک‌سازی‌شده به عدد.
    val price = priceText.toNumber()
    val firstDiscount = firstText.toNumber()
    val secondDiscount = secondText.toNumber() ?: 0.0
    // فرمول فقط با ورودی ضروری معتبر اجرا می‌شود.
    val result = if (price != null && firstDiscount != null) CalculationEngine.discount(price, firstDiscount, secondDiscount) else null

    CalculatorLayout("تخفیف ساده یا چندمرحله‌ای", "تخفیف دوم اختیاری است و روی مبلغ باقی‌مانده اعمال می‌شود.") {
        // قیمت اصلی کالا.
        NumberField("قیمت اصلی (${currency.label})", priceText) { priceText = it }
        // درصد تخفیف اول.
        NumberField("تخفیف اول", firstText, "%") { firstText = it }
        // درصد تخفیف دوم.
        NumberField("تخفیف دوم (اختیاری)", secondText, "%") { secondText = it }
        // درصد خارج از محدوده به‌جای ساخت قیمت منفی، خطای قابل فهم نشان می‌دهد.
        if (price != null && firstDiscount != null && result == null) {
            Text("درصد تخفیف باید بین ۰ تا ۱۰۰ باشد.", color = MaterialTheme.colorScheme.error)
        }
        result?.let { discount ->
            // کارت خروجی قیمت نهایی و تخفیف واقعی.
            ResultCard(
                "قیمت نهایی",
                "${discount.finalPrice.money()} ${currency.label}",
                listOf(
                    "صرفه‌جویی" to "${discount.savedAmount.money()} ${currency.label}",
                    "تخفیف واقعی" to "${discount.effectiveDiscount.percent()}٪"
                )
            )
            // ذخیره همین محاسبه در History.
            SaveButton {
                onSave(
                    "تخفیف",
                    "${price!!.money()} با ${discount.effectiveDiscount.percent()}٪ تخفیف واقعی",
                    "${discount.finalPrice.money()} ${currency.label}"
                )
            }
        }
    }
}

/** سود/زیان، Markup و Profit Margin را جداگانه نشان می‌دهد. */
@Composable
private fun ProfitCalculator(currency: CurrencyMode, onSave: (String, String, String) -> Unit) {
    // ورودی‌های قیمت خرید، هزینه جانبی و فروش.
    var costText by rememberSaveable { mutableStateOf("") }
    var extraText by rememberSaveable { mutableStateOf("") }
    var saleText by rememberSaveable { mutableStateOf("") }
    // تبدیل ورودی‌ها به عدد؛ هزینه جانبی خالی صفر است.
    val cost = costText.toNumber()
    val extra = extraText.toNumber() ?: 0.0
    val sale = saleText.toNumber()
    // موتور محاسبه از UI جداست تا تست‌پذیر و قابل استفاده در Widget باشد.
    val result = if (cost != null && sale != null) CalculationEngine.profit(cost, extra, sale) else null

    CalculatorLayout("سود واقعی", "هزینه‌های جانبی را هم وارد کن تا نتیجه دقیق‌تر باشد.") {
        NumberField("قیمت خرید (${currency.label})", costText) { costText = it }
        NumberField("هزینه جانبی (${currency.label})", extraText) { extraText = it }
        NumberField("قیمت فروش (${currency.label})", saleText) { saleText = it }
        result?.let { profitResult ->
            // تقسیم بر صفر به‌جای Crash با متن تعریف‌نشده نمایش داده می‌شود.
            val markupText = profitResult.markupPercent?.let { "${it.percent()}٪" } ?: "تعریف‌نشده"
            val marginText = profitResult.marginPercent?.let { "${it.percent()}٪" } ?: "تعریف‌نشده"
            // سود منفی به‌عنوان زیان و با هشدار نمایش داده می‌شود.
            val warning = if (profitResult.profit < 0.0) "این قیمت فروش باعث ضرر می‌شود." else null
            ResultCard(
                if (profitResult.profit >= 0.0) "سود خالص" else "زیان خالص",
                "${abs(profitResult.profit).money()} ${currency.label}",
                listOf(
                    "هزینه واقعی" to "${profitResult.totalCost.money()} ${currency.label}",
                    "درصد سود روی هزینه" to markupText,
                    "حاشیه سود" to marginText
                ),
                warning
            )
            SaveButton {
                onSave(
                    "سود",
                    "هزینه ${profitResult.totalCost.money()} / فروش ${sale!!.money()}",
                    "${if (profitResult.profit >= 0) "سود" else "زیان"} ${abs(profitResult.profit).money()} ${currency.label}"
                )
            }
        }
    }
}

/** قیمت فروش لازم برای Markup یا Margin هدف را محاسبه می‌کند. */
@Composable
private fun TargetPriceCalculator(currency: CurrencyMode, onSave: (String, String, String) -> Unit) {
    // ورودی‌های هزینه و درصد هدف.
    var costText by rememberSaveable { mutableStateOf("") }
    var extraText by rememberSaveable { mutableStateOf("") }
    var targetText by rememberSaveable { mutableStateOf("") }
    // false=Markup روی هزینه، true=Profit Margin.
    var useMargin by rememberSaveable { mutableStateOf(false) }
    val cost = costText.toNumber()
    val extra = extraText.toNumber() ?: 0.0
    val target = targetText.toNumber()
    val totalCost = cost?.plus(extra)
    // موتور محاسبه Margin>=100 را نامعتبر می‌داند؛ این باگ نسخه قبلی را رفع می‌کند.
    val targetPrice = if (cost != null && target != null) CalculationEngine.targetPrice(cost, extra, target, useMargin) else null

    CalculatorLayout("قیمت فروش پیشنهادی", "سود روی هزینه یا حاشیه سود واقعی را هدف‌گذاری کن.") {
        NumberField("هزینه خرید (${currency.label})", costText) { costText = it }
        NumberField("هزینه جانبی (${currency.label})", extraText) { extraText = it }
        NumberField(if (useMargin) "حاشیه سود هدف" else "سود هدف روی هزینه", targetText, "%") { targetText = it }
        // Switch بین دو تعریف متفاوت سود جابه‌جا می‌شود.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = useMargin, onCheckedChange = { useMargin = it })
            Spacer(Modifier.width(8.dp))
            Text(if (useMargin) "محاسبه با حاشیه سود" else "محاسبه سود روی هزینه")
        }
        // Margin صد درصد یا بیشتر از نظر ریاضی قیمت محدود ندارد.
        if (useMargin && target != null && target >= 100.0) {
            Text("حاشیه سود هدف باید کمتر از ۱۰۰٪ باشد.", color = MaterialTheme.colorScheme.error)
        }
        if (targetPrice != null && totalCost != null) {
            ResultCard(
                "قیمت پیشنهادی",
                "${targetPrice.money()} ${currency.label}",
                listOf(
                    "هزینه واقعی" to "${totalCost.money()} ${currency.label}",
                    "روش محاسبه" to if (useMargin) "حاشیه سود" else "سود روی هزینه"
                )
            )
            SaveButton { onSave("قیمت فروش", "هزینه ${totalCost.money()} / هدف ${target!!.percent()}٪", "${targetPrice.money()} ${currency.label}") }
        }
    }
}

/** سه حالت رایج درصد را در یک صفحه ارائه می‌کند. */
@Composable
private fun PercentageCalculator(onSave: (String, String, String) -> Unit) {
    // 0=X٪ از Y، 1=X چند٪ Y، 2=درصد تغییر.
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var firstText by rememberSaveable { mutableStateOf("") }
    var secondText by rememberSaveable { mutableStateOf("") }
    val first = firstText.toNumber()
    val second = secondText.toNumber()
    // فرمول متناسب با Mode انتخاب می‌شود.
    val result = when (mode) {
        0 -> if (first != null && second != null) CalculationEngine.percentageOf(first, second) else null
        1 -> if (first != null && second != null) CalculationEngine.whatPercent(first, second) else null
        else -> if (first != null && second != null) CalculationEngine.percentageChange(first, second) else null
    }

    CalculatorLayout("محاسبه درصد", "نوع محاسبه را از سه حالت انتخاب کن.") {
        // SegmentedButton نوع فرمول را شفاف نمایش می‌دهد.
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("X٪ از Y", "X چند٪ Y؟", "تغییر٪").forEachIndexed { index, label ->
                SegmentedButton(selected = mode == index, onClick = { mode = index }, shape = SegmentedButtonDefaults.itemShape(index, 3)) {
                    Text(label, fontSize = 12.sp)
                }
            }
        }
        // Labelها بر اساس نوع محاسبه عوض می‌شوند.
        NumberField(when (mode) { 0 -> "درصد X"; 2 -> "مقدار قبلی"; else -> "مقدار X" }, firstText, if (mode == 0) "%" else null) { firstText = it }
        NumberField(when (mode) { 0 -> "عدد Y"; 2 -> "مقدار جدید"; else -> "مقدار Y" }, secondText) { secondText = it }
        result?.let { value ->
            val unit = if (mode == 0) "" else "٪"
            ResultCard("نتیجه", "${value.clean()}$unit", emptyList())
            SaveButton { onSave("درصد", "${first?.clean()} و ${second?.clean()}", "${value.clean()}$unit") }
        }
    }
}

/** درصد افزایش یا کاهش بین دو مقدار را محاسبه می‌کند. */
@Composable
private fun ChangeCalculator(onSave: (String, String, String) -> Unit) {
    // ورودی مقدار قبلی و جدید.
    var oldText by rememberSaveable { mutableStateOf("") }
    var newText by rememberSaveable { mutableStateOf("") }
    val oldValue = oldText.toNumber()
    val newValue = newText.toNumber()
    val change = if (oldValue != null && newValue != null) CalculationEngine.percentageChange(oldValue, newValue) else null

    CalculatorLayout("افزایش / کاهش", "تغییر مقدار قبلی تا مقدار جدید را به درصد ببین.") {
        NumberField("مقدار قبلی", oldText) { oldText = it }
        NumberField("مقدار جدید", newText) { newText = it }
        if (change != null && oldValue != null && newValue != null) {
            // قدرمطلق برای نمایش درصد و علامت برای تعیین عنوان استفاده می‌شود.
            ResultCard(
                if (change >= 0.0) "افزایش" else "کاهش",
                "${abs(change).percent()}٪",
                listOf("اختلاف عددی" to (newValue - oldValue).clean())
            )
            SaveButton { onSave("تغییر درصدی", "${oldValue.clean()} ← ${newValue.clean()}", "${change.percent()}٪") }
        }
    }
}

/** مالیات و مبلغ نهایی را با نرخ قابل تغییر محاسبه می‌کند. */
@Composable
private fun TaxCalculator(currency: CurrencyMode, onSave: (String, String, String) -> Unit) {
    // نرخ اولیه ۱۰٪ است اما محدود به آن نیست.
    var amountText by rememberSaveable { mutableStateOf("") }
    var rateText by rememberSaveable { mutableStateOf("10") }
    val amount = amountText.toNumber()
    val rate = rateText.toNumber()
    val result = if (amount != null && rate != null) CalculationEngine.tax(amount, rate) else null

    CalculatorLayout("مالیات", "درصد مالیات قابل تغییر است.") {
        NumberField("مبلغ پایه (${currency.label})", amountText) { amountText = it }
        NumberField("درصد مالیات", rateText, "%") { rateText = it }
        result?.let { tax ->
            ResultCard("مبلغ نهایی", "${tax.totalAmount.money()} ${currency.label}", listOf("مالیات" to "${tax.taxAmount.money()} ${currency.label}"))
            SaveButton { onSave("مالیات", "${amount!!.money()} + ${rate!!.percent()}٪", "${tax.totalAmount.money()} ${currency.label}") }
        }
    }
}

/** قیمت هر واحد دو بسته را مقایسه و گزینه به‌صرفه‌تر را مشخص می‌کند. */
@Composable
private fun CompareCalculator(currency: CurrencyMode, onSave: (String, String, String) -> Unit) {
    // چهار ورودی قیمت/مقدار دو کالا.
    var firstPriceText by rememberSaveable { mutableStateOf("") }
    var firstQuantityText by rememberSaveable { mutableStateOf("") }
    var secondPriceText by rememberSaveable { mutableStateOf("") }
    var secondQuantityText by rememberSaveable { mutableStateOf("") }
    val firstPrice = firstPriceText.toNumber()
    val firstQuantity = firstQuantityText.toNumber()
    val secondPrice = secondPriceText.toNumber()
    val secondQuantity = secondQuantityText.toNumber()
    // مقدار صفر/منفی توسط Engine رد می‌شود تا تقسیم بر صفر رخ ندهد.
    val result = if (firstPrice != null && firstQuantity != null && secondPrice != null && secondQuantity != null) {
        CalculationEngine.compare(firstPrice, firstQuantity, secondPrice, secondQuantity)
    } else null

    CalculatorLayout("مقایسه دو کالا", "واحد مقدار برای هر دو بسته باید یکسان باشد؛ مثلاً هر دو گرم یا هر دو عدد.") {
        // اطلاعات کالای اول.
        Text("کالای اول", fontWeight = FontWeight.Bold)
        NumberField("قیمت (${currency.label})", firstPriceText) { firstPriceText = it }
        NumberField("مقدار / وزن / تعداد", firstQuantityText) { firstQuantityText = it }
        HorizontalDivider()
        // اطلاعات کالای دوم.
        Text("کالای دوم", fontWeight = FontWeight.Bold)
        NumberField("قیمت (${currency.label})", secondPriceText) { secondPriceText = it }
        NumberField("مقدار / وزن / تعداد", secondQuantityText) { secondQuantityText = it }
        result?.let { compare ->
            // Winner=1 یعنی بسته اول، Winner=2 یعنی بسته دوم.
            val winnerText = if (compare.winner == 1) "کالای اول" else "کالای دوم"
            val winnerUnit = if (compare.winner == 1) compare.firstUnitPrice else compare.secondUnitPrice
            ResultCard(
                "$winnerText به‌صرفه‌تر است",
                "${winnerUnit.money()} ${currency.label} / واحد",
                listOf(
                    "کالای اول / واحد" to "${compare.firstUnitPrice.money()} ${currency.label}",
                    "کالای دوم / واحد" to "${compare.secondUnitPrice.money()} ${currency.label}",
                    "مزیت تقریبی" to "${compare.savingPercent.percent()}٪"
                )
            )
            SaveButton { onSave("مقایسه خرید", "مقایسه قیمت واحد دو بسته", "$winnerText حدود ${compare.savingPercent.percent()}٪ به‌صرفه‌تر") }
        }
    }
}

/** حداقل تعداد فروش لازم برای پوشش هزینه ثابت را محاسبه می‌کند. */
@Composable
private fun BreakEvenCalculator(currency: CurrencyMode, onSave: (String, String, String) -> Unit) {
    // هزینه ثابت و سود خالص هر محصول.
    var fixedText by rememberSaveable { mutableStateOf("") }
    var profitPerUnitText by rememberSaveable { mutableStateOf("") }
    val fixedCost = fixedText.toNumber()
    val profitPerUnit = profitPerUnitText.toNumber()
    val units = if (fixedCost != null && profitPerUnit != null) CalculationEngine.breakEvenUnits(fixedCost, profitPerUnit) else null

    CalculatorLayout("نقطه سربه‌سر", "هزینه ثابت و سود خالص هر فروش را وارد کن.") {
        NumberField("هزینه ثابت (${currency.label})", fixedText) { fixedText = it }
        NumberField("سود خالص هر محصول (${currency.label})", profitPerUnitText) { profitPerUnitText = it }
        units?.let { count ->
            // تعداد فروش به بالا گرد شده تا کسری محصول در نتیجه نباشد.
            ResultCard("حداقل فروش برای سربه‌سر", "$count محصول", listOf("فروش بعد از این نقطه" to "وارد محدوده سود می‌شود"))
            SaveButton { onSave("نقطه سربه‌سر", "هزینه ${fixedCost!!.money()} / سود واحد ${profitPerUnit!!.money()}", "$count محصول") }
        }
    }
}
