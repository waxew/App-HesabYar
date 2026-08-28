@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.waxew.hesabyar

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.waxew.hesabyar.data.*
import kotlin.math.abs

/** دستیار خرید: بودجه، سبد خرید و هشدار عبور از بودجه. */
@Composable
fun BuyerAssistantScreen(currency: CurrencyMode) {
    val context = LocalContext.current
    val repository = remember { ShoppingRepository(context) }
    val cartItems = remember { mutableStateListOf<CartItem>().apply { addAll(repository.loadItems()) } }
    var budgetText by rememberSaveable {
        mutableStateOf(repository.budget.takeIf { it > 0.0 }?.let { formatNumericInputForDisplay(it.clean()) } ?: "")
    }
    var name by rememberSaveable { mutableStateOf("") }
    var priceText by rememberSaveable { mutableStateOf("") }
    var quantityText by rememberSaveable { mutableStateOf("1") }

    val total = cartItems.sumOf { it.total }
    val budget = budgetText.toNumber() ?: 0.0
    val remaining = budget - total

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("دستیار خرید", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("بودجه و سبد خرید را روی همین گوشی مدیریت کن.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsCard("بودجه", Icons.Outlined.AccountBalanceWallet) {
                NumberField("بودجه (${currency.label})", budgetText) {
                    budgetText = it
                    repository.budget = it.toNumber() ?: 0.0
                }
                ResultCard(
                    if (budget <= 0.0) "جمع سبد" else if (remaining >= 0.0) "مانده بودجه" else "کسری بودجه",
                    if (budget <= 0.0) "${total.money()} ${currency.label}" else "${abs(remaining).money()} ${currency.label}",
                    listOf("جمع خرید" to "${total.money()} ${currency.label}", "تعداد اقلام" to cartItems.size.toString()),
                    if (budget > 0.0 && remaining < 0.0) "سبد فعلی از بودجه بیشتر است." else null
                )
            }
        }
        item {
            SettingsCard("افزودن کالا", Icons.Outlined.AddShoppingCart) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("نام کالا") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberField("قیمت واحد (${currency.label})", priceText) { priceText = it }
                NumberField("تعداد", quantityText, useThousandsSeparator = false) { quantityText = it }
                Button(
                    onClick = {
                        val price = priceText.toNumber()
                        val qty = quantityText.toNumber()
                        if (name.isNotBlank() && price != null && price >= 0 && qty != null && qty > 0) {
                            cartItems.add(0, CartItem(System.currentTimeMillis(), name.trim(), price, qty))
                            repository.saveItems(cartItems)
                            name = ""; priceText = ""; quantityText = "1"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("اضافه به سبد") }
            }
        }
        if (cartItems.isEmpty()) item { EmptyState(Icons.Outlined.ShoppingCart, "سبد خرید خالی است") }
        else items(items = cartItems, key = { it.id }) { item ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold)
                        Text("${item.quantity.clean()} × ${item.unitPrice.money()} = ${item.total.money()} ${currency.label}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { cartItems.remove(item); repository.saveItems(cartItems) }) {
                        Icon(Icons.Outlined.DeleteOutline, contentDescription = "حذف")
                    }
                }
            }
        }
    }
}

/** ابزار حرفه‌ای فروشنده: هزینه تمام‌شده، کارمزدها، Margin، تخفیف امن، سناریو و عمده. */
@Composable
fun SellerAssistantScreen(currency: CurrencyMode, onSave: (String, String, String) -> Unit) {
    var purchase by rememberSaveable { mutableStateOf("") }
    var shipping by rememberSaveable { mutableStateOf("") }
    var packaging by rememberSaveable { mutableStateOf("") }
    var other by rememberSaveable { mutableStateOf("") }
    var advertising by rememberSaveable { mutableStateOf("0") }
    var platform by rememberSaveable { mutableStateOf("0") }
    var gateway by rememberSaveable { mutableStateOf("0") }
    var tax by rememberSaveable { mutableStateOf("0") }
    var margin by rememberSaveable { mutableStateOf("25") }
    var currentPrice by rememberSaveable { mutableStateOf("") }
    var bulkQuantity by rememberSaveable { mutableStateOf("10") }
    var bulkDiscount by rememberSaveable { mutableStateOf("10") }

    val result = AdvancedCalculationEngine.sellerPricing(
        purchase.toNumber() ?: -1.0, shipping.toNumber() ?: 0.0, packaging.toNumber() ?: 0.0, other.toNumber() ?: 0.0,
        advertising.toNumber() ?: 0.0, platform.toNumber() ?: 0.0, gateway.toNumber() ?: 0.0, tax.toNumber() ?: 0.0,
        margin.toNumber() ?: -1.0
    )

    CalculatorLayout("دستیار فروشنده", "هزینه‌های واقعی و درصدی را وارد کن تا قیمت امن فروش محاسبه شود.") {
        NumberField("قیمت خرید (${currency.label})", purchase) { purchase = it }
        NumberField("ارسال ورودی (${currency.label})", shipping) { shipping = it }
        NumberField("بسته‌بندی (${currency.label})", packaging) { packaging = it }
        NumberField("سایر هزینه ثابت (${currency.label})", other) { other = it }
        HorizontalDivider()
        NumberField("تبلیغات", advertising, "%") { advertising = it }
        NumberField("کارمزد پلتفرم", platform, "%") { platform = it }
        NumberField("کارمزد درگاه", gateway, "%") { gateway = it }
        NumberField("مالیات/عوارض", tax, "%") { tax = it }
        NumberField("حاشیه سود هدف", margin, "%") { margin = it }

        if (result == null) {
            Text("مقادیر را بررسی کن؛ مجموع کارمزدها و Margin باید کمتر از ۱۰۰٪ باشد.", color = MaterialTheme.colorScheme.error)
        } else {
            ResultCard(
                "قیمت فروش پیشنهادی",
                "${result.suggestedPrice.money()} ${currency.label}",
                listOf(
                    "هزینه ثابت واقعی" to "${result.fixedCost.money()} ${currency.label}",
                    "هزینه‌های درصدی" to "${result.variableRatePercent.percent()}٪",
                    "سود خالص مورد انتظار" to "${result.expectedProfit.money()} ${currency.label}",
                    "Margin" to "${result.expectedMarginPercent.percent()}٪",
                    "قیمت سربه‌سر" to "${result.breakEvenPrice.money()} ${currency.label}"
                )
            )
            NumberField("قیمت فعلی برای تست تخفیف (${currency.label})", currentPrice) { currentPrice = it }
            val referencePrice = currentPrice.toNumber()?.takeIf { it > 0 } ?: result.suggestedPrice
            AdvancedCalculationEngine.maxSafeDiscount(referencePrice, result.breakEvenPrice)?.let {
                ResultStrip("حداکثر تخفیف امن", "${it.percent()}٪")
            }

            Text("سناریوهای قیمت‌گذاری", fontWeight = FontWeight.Bold)
            AdvancedCalculationEngine.pricingScenarios(
                purchase.toNumber() ?: 0.0, shipping.toNumber() ?: 0.0, packaging.toNumber() ?: 0.0, other.toNumber() ?: 0.0,
                advertising.toNumber() ?: 0.0, platform.toNumber() ?: 0.0, gateway.toNumber() ?: 0.0, tax.toNumber() ?: 0.0
            ).forEach { scenario ->
                ResultStrip("${scenario.label} • ${scenario.targetMargin.percent()}٪", "${scenario.salePrice.money()} ${currency.label}")
            }

            Text("قیمت عمده", fontWeight = FontWeight.Bold)
            NumberField("تعداد عمده", bulkQuantity, useThousandsSeparator = false) { bulkQuantity = it }
            NumberField("تخفیف عمده", bulkDiscount, "%") { bulkDiscount = it }
            AdvancedCalculationEngine.bulkPrice(
                result.suggestedPrice, result.breakEvenPrice, bulkQuantity.toNumber() ?: 0.0, bulkDiscount.toNumber() ?: -1.0
            )?.let { bulk ->
                ResultCard(
                    "قیمت هر عدد عمده", "${bulk.unitPrice.money()} ${currency.label}",
                    listOf("جمع سفارش" to "${bulk.totalPrice.money()} ${currency.label}", "سود هر واحد بالاتر از سربه‌سر" to "${bulk.unitProfit.money()} ${currency.label}")
                )
            }
            SaveButton { onSave("قیمت‌گذاری فروشنده", "هزینه ${result.fixedCost.money()} / Margin ${result.expectedMarginPercent.percent()}٪", "${result.suggestedPrice.money()} ${currency.label}") }
        }
    }
}

/** دفترچه قیمت و مقایسه چند محصول/بسته بر اساس قیمت واحد. */
@Composable
fun PriceBookScreen(currency: CurrencyMode) {
    val context = LocalContext.current
    val repository = remember { PriceBookRepository(context) }
    val records = remember { mutableStateListOf<PriceRecord>().apply { addAll(repository.load()) } }
    var name by rememberSaveable { mutableStateOf("") }
    var price by rememberSaveable { mutableStateOf("") }
    var quantity by rememberSaveable { mutableStateOf("1") }
    var unit by rememberSaveable { mutableStateOf("عدد") }
    val validSorted = records.filter { it.quantity > 0 }.sortedBy { it.unitPrice }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("دفترچه قیمت", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("قیمت‌ها را ذخیره و چند بسته را بر اساس قیمت هر واحد مقایسه کن.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsCard("ثبت قیمت", Icons.Outlined.PriceCheck) {
                OutlinedTextField(name, { name = it.take(60) }, label = { Text("نام محصول") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                NumberField("قیمت (${currency.label})", price) { price = it }
                NumberField("مقدار / وزن / تعداد", quantity, useThousandsSeparator = false) { quantity = it }
                OutlinedTextField(unit, { unit = it.take(16) }, label = { Text("واحد؛ مثال گرم/عدد/میلی‌لیتر") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(onClick = {
                    val p = price.toNumber(); val q = quantity.toNumber()
                    if (name.isNotBlank() && p != null && p >= 0 && q != null && q > 0) {
                        records.add(0, PriceRecord(System.currentTimeMillis(), name.trim(), p, q, unit.ifBlank { "عدد" }))
                        repository.save(records)
                        name = ""; price = ""; quantity = "1"
                    }
                }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Save, null); Spacer(Modifier.width(8.dp)); Text("ثبت در دفترچه") }
            }
        }
        if (validSorted.size >= 2) item {
            val cheapest = validSorted.first()
            val expensive = validSorted.last()
            val advantage = if (expensive.unitPrice > 0) (1 - cheapest.unitPrice / expensive.unitPrice) * 100 else 0.0
            ResultCard(
                "به‌صرفه‌ترین ثبت", cheapest.productName,
                listOf("قیمت هر ${cheapest.unitLabel}" to "${cheapest.unitPrice.money()} ${currency.label}", "مزیت نسبت به گران‌ترین" to "${advantage.percent()}٪")
            )
        }
        if (records.isEmpty()) item { EmptyState(Icons.Outlined.PriceChange, "هنوز قیمتی ثبت نشده") }
        else itemsIndexed(records, key = { _, record -> record.id }) { index, record ->
            val previous = records.drop(index + 1).firstOrNull { it.productName.equals(record.productName, ignoreCase = true) }
            val change = previous?.takeIf { it.price > 0 }?.let { (record.price - it.price) / it.price * 100.0 }
            ElevatedCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(record.productName, fontWeight = FontWeight.Bold)
                        Text("${record.price.money()} ${currency.label} / ${record.quantity.clean()} ${record.unitLabel}")
                        Text("هر ${record.unitLabel}: ${record.unitPrice.money()} ${currency.label}", color = MaterialTheme.colorScheme.primary)
                        change?.let { Text("تغییر نسبت به ثبت قبلی: ${if (it >= 0) "+" else ""}${it.percent()}٪", color = if (it > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary) }
                    }
                    IconButton(onClick = { records.remove(record); repository.save(records) }) { Icon(Icons.Outlined.DeleteOutline, "حذف") }
                }
            }
        }
    }
}

private enum class ScanMode { TEXT, BARCODE }

/** اسکن قیمت/فاکتور با OCR و اسکن Barcode/QR با ML Kit. */
@Composable
fun ScannerScreen() {
    var mode by rememberSaveable { mutableStateOf(ScanMode.TEXT.name) }
    var status by rememberSaveable { mutableStateOf("برای شروع یکی از حالت‌ها را انتخاب کن.") }
    var resultText by rememberSaveable { mutableStateOf("") }
    val currentMode = runCatching { ScanMode.valueOf(mode) }.getOrDefault(ScanMode.TEXT)

    fun processText(bitmap: Bitmap) {
        status = "در حال خواندن تصویر…"
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { text ->
                val numbers = extractLikelyPrices(text.text)
                resultText = buildString {
                    if (text.text.isNotBlank()) appendLine(text.text.trim())
                    if (numbers.isNotEmpty()) {
                        appendLine(); appendLine("اعداد محتمل قیمت:")
                        numbers.forEach { appendLine(it.money()) }
                    }
                }.trim()
                status = if (resultText.isBlank()) "متنی شناسایی نشد؛ تصویر واضح‌تر بگیر." else "خواندن تصویر انجام شد."
                recognizer.close()
            }
            .addOnFailureListener { error -> status = "خطا در OCR: ${error.message ?: "نامشخص"}"; recognizer.close() }
    }

    fun processBarcode(bitmap: Bitmap) {
        status = "در حال بررسی بارکد…"
        val scanner = BarcodeScanning.getClient()
        scanner.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { barcodes ->
                val values = barcodes.mapNotNull { it.rawValue }.distinct()
                resultText = values.joinToString("\n").ifBlank { "بارکدی شناسایی نشد." }
                status = if (values.isEmpty()) "بارکدی پیدا نشد؛ تصویر واضح‌تر بگیر." else "${values.size} بارکد شناسایی شد."
                scanner.close()
            }
            .addOnFailureListener { error -> status = "خطا در اسکن بارکد: ${error.message ?: "نامشخص"}"; scanner.close() }
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap == null) status = "تصویری دریافت نشد."
        else if (currentMode == ScanMode.TEXT) processText(bitmap) else processBarcode(bitmap)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("اسکن قیمت و بارکد", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("قیمت/فاکتور یا بارکد را با دوربین بخوان. پردازش اصلی روی دستگاه انجام می‌شود.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(selected = currentMode == ScanMode.TEXT, onClick = { mode = ScanMode.TEXT.name }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("قیمت / فاکتور") }
                SegmentedButton(selected = currentMode == ScanMode.BARCODE, onClick = { mode = ScanMode.BARCODE.name }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("بارکد / QR") }
            }
        }
        item { Button(onClick = { camera.launch(null) }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("باز کردن دوربین") } }
        item { Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (resultText.isNotBlank()) item { ElevatedCard(Modifier.fillMaxWidth()) { Text(resultText, Modifier.padding(16.dp)) } }
        item { Text("نکته: مدل OCR این نسخه برای اعداد و متن لاتین بهینه است؛ در فاکتور فارسی معمولاً اعداد قیمت قابل استخراج‌اند اما دقت متن فارسی محدودتر است.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** گزارش تصویری سبک از فراوانی ابزارها و آخرین نتایج. */
@Composable
fun ReportsScreen(history: List<HistoryEntry>) {
    val counts = history.groupingBy { it.title }.eachCount().toList().sortedByDescending { it.second }
    val max = counts.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("گزارش‌ها", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("نمای کلی استفاده از ابزارهای حسابیار روی همین دستگاه.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { ResultCard("تعداد محاسبات ذخیره‌شده", history.size.toString(), listOf("نوع ابزارهای استفاده‌شده" to counts.size.toString())) }
        counts.take(8).forEach { (title, count) -> item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Text(count.toString(), fontWeight = FontWeight.Bold) }
                LinearProgressIndicator(progress = { count.toFloat() / max.toFloat() }, modifier = Modifier.fillMaxWidth())
            }
        } }
        if (history.isEmpty()) item { EmptyState(Icons.Outlined.BarChart, "هنوز داده‌ای برای گزارش وجود ندارد") }
    }
}

/** خروجی PDF/CSV/XLSX و Backup/Restore اطلاعات محلی. */
@Composable
fun DataToolsScreen(
    history: List<HistoryEntry>,
    settings: SettingsRepository,
    onRestored: (RestoreResult) -> Unit
) {
    val context = LocalContext.current
    val shopping = remember { ShoppingRepository(context) }
    val priceBook = remember { PriceBookRepository(context) }
    var message by rememberSaveable { mutableStateOf("") }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val json = runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            val restored = json?.let { DataTransferManager.restoreBackup(it, settings, shopping, priceBook) }
            if (restored != null) { onRestored(restored); message = "بازیابی اطلاعات با موفقیت انجام شد." }
            else message = "فایل پشتیبان معتبر نبود یا قابل خواندن نیست."
        }
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("خروجی و پشتیبان‌گیری", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("گزارش را به PDF، CSV یا Excel بفرست و از داده‌های محلی Backup بگیر.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            SettingsCard("خروجی گزارش", Icons.Outlined.IosShare) {
                Button(onClick = { runCatching { DataTransferManager.exportPdf(context, history) }.onSuccess { DataTransferManager.shareFile(context, it, "application/pdf", "PDF حسابیار") }.onFailure { message = "ساخت PDF ناموفق بود." } }, modifier = Modifier.fillMaxWidth()) { Text("PDF") }
                Button(onClick = { runCatching { DataTransferManager.exportCsv(context, history) }.onSuccess { DataTransferManager.shareFile(context, it, "text/csv", "CSV حسابیار") }.onFailure { message = "ساخت CSV ناموفق بود." } }, modifier = Modifier.fillMaxWidth()) { Text("CSV") }
                Button(onClick = { runCatching { DataTransferManager.exportXlsx(context, history) }.onSuccess { DataTransferManager.shareFile(context, it, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel حسابیار") }.onFailure { message = "ساخت Excel ناموفق بود." } }, modifier = Modifier.fillMaxWidth()) { Text("Excel (.xlsx)") }
            }
        }
        item {
            SettingsCard("Backup / Restore", Icons.Outlined.Backup) {
                Button(onClick = { runCatching { DataTransferManager.createBackup(context, history, settings, shopping, priceBook) }.onSuccess { DataTransferManager.shareFile(context, it, "application/json", "Backup حسابیار") }.onFailure { message = "ساخت Backup ناموفق بود." } }, modifier = Modifier.fillMaxWidth()) { Text("ساخت و اشتراک Backup") }
                OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "text/*")) }, modifier = Modifier.fillMaxWidth()) { Text("بازیابی از Backup") }
                Text("Backup شامل تاریخچه، تنظیمات، پروفایل، سبد خرید و دفترچه قیمت است.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (message.isNotBlank()) item { Text(message, color = MaterialTheme.colorScheme.primary) }
    }
}

/** اعداد محتمل قیمت را از خروجی OCR جدا می‌کند. */
private fun extractLikelyPrices(text: String): List<Double> {
    val regex = Regex("[0-9۰-۹٠-٩][0-9۰-۹٠-٩,٬،._ ]{2,}")
    return regex.findAll(text).mapNotNull { it.value.toNumber() }.filter { it >= 100.0 }.distinct().sortedDescending().take(30).toList()
}
