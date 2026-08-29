@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.waxew.hesabyar

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waxew.hesabyar.data.*
import java.io.File

/** ابزارهای مرکز حرفه‌ای نسخه 3. */
private enum class ProTool(val title: String, val subtitle: String, val icon: ImageVector) {
    MARKETPLACE("مقایسه مارکت‌پلیس", "پروفایل کارمزد و سود خالص", Icons.Outlined.Storefront),
    INSTALLMENT("نقد یا اقساط", "هزینه واقعی خرید قسطی", Icons.Outlined.Payments),
    PRICE_ANALYTICS("تحلیل قیمت", "نمودار، بهترین قیمت و Shrinkflation", Icons.Outlined.Insights),
    WHAT_IF("شبیه‌ساز What-if", "تخفیف، سود و Margin زنده", Icons.Outlined.Tune),
    INVOICE("فاکتور آفلاین", "ثبت اقلام و خروجی PDF", Icons.Outlined.ReceiptLong),
    BUSINESS("پروفایل کاری", "چند فروشگاه روی یک گوشی", Icons.Outlined.Business),
    IMPORT("Import محصولات", "CSV و Excel به دفترچه قیمت", Icons.Outlined.UploadFile),
    SMART_COMMAND("فرمان متنی", "محاسبه با جمله فارسی", Icons.Outlined.SmartToy),
    CONVERTER("تبدیل واحد و ارز", "واحدهای روزمره و نرخ دستی", Icons.Outlined.Straighten),
    SECURE_BACKUP("Backup رمزدار", "AES-256 و Restore امن", Icons.Outlined.Security),
    CASH_CHECK("Cash Check", "سربه‌سر ماهانه و سود هدف", Icons.Outlined.AccountBalanceWallet)
}

/** مرکز حرفه‌ای؛ جستجو و Favorite دارد و هر ابزار داخل همین صفحه باز می‌شود. */
@Composable
fun ProToolsScreen(currency: CurrencyMode, history: List<HistoryEntry>, settings: SettingsRepository) {
    val context = LocalContext.current
    val repository = remember { ProRepository(context) }
    var selectedName by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var favorites by remember { mutableStateOf(repository.loadFavoriteTools()) }
    val selected = selectedName?.let { runCatching { ProTool.valueOf(it) }.getOrNull() }

    BackHandler(enabled = selected != null) { selectedName = null }

    if (selected != null) {
        Column(Modifier.fillMaxSize()) {
            Surface(tonalElevation = 2.dp) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    TextButton(onClick = { selectedName = null }) { Icon(Icons.Outlined.ArrowForward, null); Spacer(Modifier.width(6.dp)); Text("مرکز حرفه‌ای") }
                }
            }
            Box(Modifier.weight(1f)) {
                when (selected) {
                    ProTool.MARKETPLACE -> MarketplaceTool(currency, repository)
                    ProTool.INSTALLMENT -> InstallmentTool(currency)
                    ProTool.PRICE_ANALYTICS -> PriceAnalyticsTool(currency)
                    ProTool.WHAT_IF -> WhatIfTool(currency)
                    ProTool.INVOICE -> InvoiceTool(currency, repository)
                    ProTool.BUSINESS -> BusinessProfilesTool(currency, repository)
                    ProTool.IMPORT -> PriceImportTool(currency)
                    ProTool.SMART_COMMAND -> SmartCommandTool(currency)
                    ProTool.CONVERTER -> ConverterTool(currency, repository)
                    ProTool.SECURE_BACKUP -> SecureBackupTool(history, settings, repository)
                    ProTool.CASH_CHECK -> CashCheckTool(currency, repository)
                }
            }
        }
        return
    }

    val visible = ProTool.entries.filter {
        query.isBlank() || it.title.contains(query, true) || it.subtitle.contains(query, true)
    }.sortedByDescending { it.name in favorites }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("مرکز ابزارهای حرفه‌ای", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("ابزارهای فروش، خرید، تحلیل و پشتیبان‌گیری پیشرفته.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(query, { query = it.take(60) }, label = { Text("جستجوی ابزار") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        }
        items(visible, key = { it.name }) { tool ->
            ElevatedCard(onClick = { selectedName = tool.name }, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(tool.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                    Column(Modifier.weight(1f)) { Text(tool.title, fontWeight = FontWeight.Bold); Text(tool.subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
                    IconButton(onClick = {
                        favorites = favorites.toMutableSet().apply { if (!add(tool.name)) remove(tool.name) }
                        repository.saveFavoriteTools(favorites)
                    }) { Icon(if (tool.name in favorites) Icons.Outlined.Star else Icons.Outlined.StarBorder, "علاقه‌مندی") }
                }
            }
        }
    }
}

@Composable
private fun MarketplaceTool(currency: CurrencyMode, repo: ProRepository) {
    val profiles = remember { mutableStateListOf<MarketplaceProfile>().apply { addAll(repo.loadMarketplaces()) } }
    var cost by rememberSaveable { mutableStateOf("") }; var sale by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }; var fee by rememberSaveable { mutableStateOf("") }
    var fixedFee by rememberSaveable { mutableStateOf("") }; var threshold by rememberSaveable { mutableStateOf("") }
    val quotes = V3Engine.compareMarketplaces(profiles, cost.toNumber() ?: -1.0, sale.toNumber() ?: -1.0)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ToolHeader("مقایسه مارکت‌پلیس", "برای هر کانال فروش پروفایل کارمزد بساز و سود خالص را مقایسه کن.") }
        item { SettingsCard("محصول", Icons.Outlined.Inventory2) { NumberField("هزینه تمام‌شده", cost) { cost = it }; NumberField("قیمت فروش", sale) { sale = it } } }
        if (quotes.isNotEmpty()) item {
            val best = quotes.first()
            ResultCard("بهترین کانال", best.marketplaceName, listOf("سود خالص" to "${best.netProfit.money()} ${currency.label}", "Margin" to "${best.marginPercent.percent()}٪", "کارمزد" to "${best.totalFees.money()} ${currency.label}"))
        }
        items(quotes) { q -> ElevatedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(q.marketplaceName, fontWeight = FontWeight.Bold); Text("کارمزد ${q.totalFees.money()} • سود ${q.netProfit.money()} • Margin ${q.marginPercent.percent()}٪") } } }
        item {
            SettingsCard("پروفایل جدید", Icons.Outlined.AddBusiness) {
                OutlinedTextField(name, { name = it.take(50) }, label = { Text("نام کانال / فروشگاه") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                NumberField("کارمزد درصدی", fee, "%", useThousandsSeparator = false) { fee = it }
                NumberField("کارمزد ثابت (${currency.label})", fixedFee) { fixedFee = it }
                NumberField("آستانه قانون پلکانی - اختیاری", threshold) { threshold = it }
                Button(onClick = {
                    if (name.isNotBlank()) {
                        val rules = buildList {
                            fee.toNumber()?.takeIf { it > 0 }?.let { add(FeeRule("کارمزد درصدی", if ((threshold.toNumber() ?: 0.0) > 0) FeeRuleType.TIERED_PERCENT else FeeRuleType.PERCENT, it, threshold.toNumber() ?: 0.0)) }
                            fixedFee.toNumber()?.takeIf { it > 0 }?.let { add(FeeRule("هزینه ثابت", FeeRuleType.FIXED, it)) }
                        }
                        profiles.add(MarketplaceProfile(System.currentTimeMillis(), name.trim(), rules)); repo.saveMarketplaces(profiles); name = ""; fee = ""; fixedFee = ""; threshold = ""
                    }
                }, modifier = Modifier.fillMaxWidth()) { Text("ذخیره پروفایل") }
            }
        }
        items(profiles) { p -> ListItem(headlineContent = { Text(p.name) }, supportingContent = { Text("${p.rules.size} قانون هزینه") }, trailingContent = { IconButton(onClick = { profiles.remove(p); repo.saveMarketplaces(profiles) }) { Icon(Icons.Outlined.DeleteOutline, null) } }) }
    }
}

@Composable
private fun InstallmentTool(currency: CurrencyMode) {
    var cash by rememberSaveable { mutableStateOf("") }; var down by rememberSaveable { mutableStateOf("") }; var installment by rememberSaveable { mutableStateOf("") }; var count by rememberSaveable { mutableStateOf("12") }
    val result = V3Engine.installment(cash.toNumber() ?: -1.0, down.toNumber() ?: -1.0, installment.toNumber() ?: -1.0, count.toNumber()?.toInt() ?: 0)
    CalculatorLayout("نقد یا اقساط", "هزینه واقعی قسط را با قیمت نقدی مقایسه کن.") {
        NumberField("قیمت نقدی", cash) { cash = it }; NumberField("پیش‌پرداخت", down) { down = it }; NumberField("مبلغ هر قسط", installment) { installment = it }; NumberField("تعداد اقساط", count, useThousandsSeparator = false) { count = it }
        result?.let { ResultCard("کل پرداخت اقساطی", "${it.totalInstallmentCost.money()} ${currency.label}", listOf("اضافه نسبت به نقد" to "${it.extraCostVsCash.money()} ${currency.label}", "درصد اضافه" to "${it.extraPercentVsCash.percent()}٪", "نرخ ماهانه تقریبی" to "${it.approximateMonthlyRatePercent.percent()}٪", "نرخ موثر سالانه تقریبی" to "${V3Engine.effectiveAnnualRate(it.approximateMonthlyRatePercent).percent()}٪"), warning = if (it.extraCostVsCash > 0) "قسطی ${it.extraPercentVsCash.percent()}٪ گران‌تر از نقد است." else "این طرح از قیمت نقدی گران‌تر نیست.") }
    }
}

@Composable
private fun PriceAnalyticsTool(currency: CurrencyMode) {
    val context = LocalContext.current; val repo = remember { PriceBookRepository(context) }; val records = remember { mutableStateListOf<PriceRecord>().apply { addAll(repo.load()) } }
    var product by rememberSaveable { mutableStateOf("") }
    var salePrice by rememberSaveable { mutableStateOf("") }
    var feePercent by rememberSaveable { mutableStateOf("0") }
    val names = records.map { it.productName }.distinct().sorted(); val selectedRecords = records.filter { product.isBlank() || it.productName.equals(product, true) }.sortedBy { it.createdAt }
    val best = selectedRecords.minByOrNull { it.unitPrice }
    val lastTwo = selectedRecords.takeLast(2); val shrink = if (lastTwo.size == 2) V3Engine.shrinkflation(lastTwo[0].price, lastTwo[0].quantity, lastTwo[1].price, lastTwo[1].quantity) else null
    val oldBasket = records.groupBy { it.productName }.values.sumOf { group -> group.minByOrNull { it.createdAt }?.unitPrice ?: 0.0 }
    val newBasket = records.groupBy { it.productName }.values.sumOf { group -> group.maxByOrNull { it.createdAt }?.unitPrice ?: 0.0 }
    val inflation = V3Engine.personalInflation(oldBasket, newBasket)
    val previousMargin = lastTwo.getOrNull(0)?.let { V3Engine.marginAtSalePrice(salePrice.toNumber() ?: -1.0, it.price, feePercent.toNumber() ?: 0.0) }
    val currentMargin = lastTwo.getOrNull(1)?.let { V3Engine.marginAtSalePrice(salePrice.toNumber() ?: -1.0, it.price, feePercent.toNumber() ?: 0.0) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ToolHeader("تحلیل دفترچه قیمت", "روند، بهترین قیمت، تورم شخصی و Shrinkflation.") }
        item { OutlinedTextField(product, { product = it.take(60) }, label = { Text("نام محصول برای فیلتر؛ خالی = همه") }, modifier = Modifier.fillMaxWidth(), singleLine = true) }
        if (names.isNotEmpty()) item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { names.take(4).forEach { n -> AssistChip(onClick = { product = n }, label = { Text(n.take(12)) }) } } }
        if (selectedRecords.size >= 2) item { PriceTrendChart(selectedRecords.map { it.unitPrice }) }
        item { SettingsCard("هشدار Margin", Icons.Outlined.WarningAmber) { NumberField("قیمت فروش ثابت", salePrice) { salePrice = it }; NumberField("کارمزد درصدی", feePercent, "%", useThousandsSeparator = false) { feePercent = it }; if (previousMargin != null && currentMargin != null) { val diff = currentMargin - previousMargin; ResultStrip("تغییر Margin", "${if (diff >= 0) "+" else ""}${diff.percent()}٪"); if (diff < -3.0) Text("هشدار: با قیمت خرید جدید، Margin بیش از 3 واحد درصد افت کرده است.", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) } } }
        best?.let { item { ResultCard("بهترین قیمت ثبت‌شده", "${best.unitPrice.money()} ${currency.label} / ${best.unitLabel}", listOf("محصول" to best.productName, "قیمت بسته" to "${best.price.money()} ${currency.label}")) } }
        shrink?.let { item { ResultCard("تحلیل بسته", if (it.isShrinkflation) "Shrinkflation شناسایی شد" else "تغییر عادی", listOf("تغییر قیمت واحد" to "${it.unitPriceChangePercent.percent()}٪", "کاهش مقدار" to "${it.quantityDropPercent.percent()}٪"), warning = if (it.isShrinkflation) "مقدار بسته کمتر شده و قیمت هر واحد بالاتر رفته است." else null) } }
        inflation?.let { item { ResultCard("تورم شخصی Price Book", "${it.inflationPercent.percent()}٪", listOf("سبد قدیمی" to it.oldBasketCost.money(), "سبد جدید" to it.newBasketCost.money(), "تغییر قدرت خرید" to "${it.purchasingPowerChangePercent.percent()}٪")) } }
        if (records.isEmpty()) item { EmptyState(Icons.Outlined.PriceChange, "برای تحلیل، ابتدا چند قیمت در دفترچه قیمت ثبت کن") }
    }
}

@Composable
private fun PriceTrendChart(values: List<Double>) {
    val lineColor = MaterialTheme.colorScheme.primary
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("روند قیمت واحد", fontWeight = FontWeight.Bold)
            Canvas(Modifier.fillMaxWidth().height(150.dp)) {
                if (values.size < 2) return@Canvas
                val min = values.minOrNull() ?: 0.0; val max = values.maxOrNull() ?: 1.0; val range = (max - min).takeIf { it > 0.0 } ?: 1.0
                val path = Path()
                values.forEachIndexed { index, value ->
                    val x = size.width * index / (values.size - 1).toFloat(); val y = size.height - ((value - min) / range * size.height).toFloat()
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f))
                values.forEachIndexed { index, value -> val x = size.width * index / (values.size - 1).toFloat(); val y = size.height - ((value - min) / range * size.height).toFloat(); drawCircle(lineColor, 5f, Offset(x, y)) }
            }
        }
    }
}

@Composable
private fun WhatIfTool(currency: CurrencyMode) {
    var price by rememberSaveable { mutableStateOf("") }; var cost by rememberSaveable { mutableStateOf("") }; var fee by rememberSaveable { mutableStateOf("0") }; var discount by rememberSaveable { mutableFloatStateOf(10f) }
    val result = V3Engine.whatIf(price.toNumber() ?: -1.0, cost.toNumber() ?: -1.0, fee.toNumber() ?: 0.0, discount.toDouble())
    CalculatorLayout("شبیه‌ساز What-if", "تخفیف را حرکت بده و اثر آن روی سود و Margin را همان لحظه ببین.") {
        NumberField("قیمت پایه فروش", price) { price = it }; NumberField("هزینه تمام‌شده", cost) { cost = it }; NumberField("کارمزد کل", fee, "%", useThousandsSeparator = false) { fee = it }
        Text("تخفیف: ${discount.toDouble().percent()}٪", fontWeight = FontWeight.Bold); Slider(discount, { discount = it }, valueRange = 0f..70f)
        result?.let { ResultCard("قیمت مشتری", "${it.customerPrice.money()} ${currency.label}", listOf("سود خالص" to "${it.netProfit.money()} ${currency.label}", "Margin" to "${it.marginPercent.percent()}٪"), warning = if (it.isLoss) "این تخفیف باعث زیان می‌شود." else null) }
    }
}

@Composable
private fun InvoiceTool(currency: CurrencyMode, repo: ProRepository) {
    val context = LocalContext.current; val invoices = remember { mutableStateListOf<SavedInvoice>().apply { addAll(repo.loadInvoices()) } }
    val lines = remember { mutableStateListOf<InvoiceLine>() }
    var customer by rememberSaveable { mutableStateOf("") }; var title by rememberSaveable { mutableStateOf("فاکتور فروش") }; var itemName by rememberSaveable { mutableStateOf("") }; var qty by rememberSaveable { mutableStateOf("1") }; var unitPrice by rememberSaveable { mutableStateOf("") }; var discount by rememberSaveable { mutableStateOf("0") }; var tax by rememberSaveable { mutableStateOf("0") }
    val totals = V3Engine.invoiceTotals(lines, discount.toNumber() ?: 0.0, tax.toNumber() ?: 0.0)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { ToolHeader("فاکتور آفلاین", "اقلام را ثبت کن، جمع را ببین و PDF بساز.") }
        item { SettingsCard("مشخصات", Icons.Outlined.Person) { OutlinedTextField(customer, { customer = it.take(60) }, label = { Text("نام مشتری") }, modifier = Modifier.fillMaxWidth(), singleLine = true); OutlinedTextField(title, { title = it.take(60) }, label = { Text("عنوان") }, modifier = Modifier.fillMaxWidth(), singleLine = true) } }
        item { SettingsCard("افزودن قلم", Icons.Outlined.AddShoppingCart) { OutlinedTextField(itemName, { itemName = it.take(80) }, label = { Text("نام قلم") }, modifier = Modifier.fillMaxWidth(), singleLine = true); NumberField("تعداد", qty, useThousandsSeparator = false) { qty = it }; NumberField("قیمت واحد", unitPrice) { unitPrice = it }; Button(onClick = { val q=qty.toNumber(); val p=unitPrice.toNumber(); if(itemName.isNotBlank()&&q!=null&&q>0&&p!=null&&p>=0){ lines.add(InvoiceLine(itemName.trim(),q,p)); itemName=""; qty="1"; unitPrice="" } }, modifier=Modifier.fillMaxWidth()){ Text("افزودن") } } }
        items(lines) { l -> ListItem(headlineContent={Text(l.title)}, supportingContent={Text("${l.quantity.clean()} × ${l.unitPrice.money()} = ${l.total.money()} ${currency.label}")}, trailingContent={IconButton(onClick={lines.remove(l)}){Icon(Icons.Outlined.DeleteOutline,null)}}) }
        item { NumberField("تخفیف", discount, "%", useThousandsSeparator = false) { discount = it }; NumberField("مالیات", tax, "%", useThousandsSeparator = false) { tax = it } }
        totals?.let { item { ResultCard("جمع نهایی", "${it.finalTotal.money()} ${currency.label}", listOf("جمع اقلام" to it.subtotal.money(), "تخفیف" to it.discountAmount.money(), "مالیات" to it.taxAmount.money())) } }
        if (lines.isNotEmpty()) item { Button(onClick = {
            val inv = SavedInvoice(System.currentTimeMillis(), customer.trim(), title.trim().ifBlank { "فاکتور" }, lines.toList(), discount.toNumber() ?: 0.0, tax.toNumber() ?: 0.0)
            invoices.add(0, inv); repo.saveInvoices(invoices)
            V3DataTools.exportInvoicePdf(context, inv)?.let { DataTransferManager.shareFile(context, it, "application/pdf", "فاکتور حسابیار") }
        }, modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.PictureAsPdf,null); Spacer(Modifier.width(8.dp)); Text("ذخیره و اشتراک PDF") } }
        if(invoices.isNotEmpty()) item { Text("فاکتورهای ذخیره‌شده: ${invoices.size}", fontWeight=FontWeight.Bold) }
    }
}

@Composable
private fun BusinessProfilesTool(currency: CurrencyMode, repo: ProRepository) {
    val businesses = remember { mutableStateListOf<BusinessProfile>().apply { addAll(repo.loadBusinesses()) } }; var name by rememberSaveable { mutableStateOf("") }; var fixed by rememberSaveable { mutableStateOf("") }; var margin by rememberSaveable { mutableStateOf("20") }; var active by remember { mutableLongStateOf(repo.activeBusinessId) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding=PaddingValues(20.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { ToolHeader("پروفایل‌های کاری", "اطلاعات چند فروشگاه/کسب‌وکار را جدا نگه دار.") }
        items(businesses) { b -> ElevatedCard(onClick={active=b.id;repo.activeBusinessId=b.id}, modifier=Modifier.fillMaxWidth()){Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement=Arrangement.SpaceBetween){Column{Text(b.name,fontWeight=FontWeight.Bold);Text("هزینه ثابت ماهانه ${b.monthlyFixedCost.money()} ${currency.label} • Margin پیش‌فرض ${b.defaultTargetMargin.percent()}٪",fontSize=12.sp)};RadioButton(active==b.id,{active=b.id;repo.activeBusinessId=b.id})}} }
        item { SettingsCard("پروفایل جدید", Icons.Outlined.AddBusiness) { OutlinedTextField(name,{name=it.take(50)},label={Text("نام")},modifier=Modifier.fillMaxWidth(),singleLine=true);NumberField("هزینه ثابت ماهانه",fixed){fixed=it};NumberField("Margin پیش‌فرض",margin,"%",useThousandsSeparator=false){margin=it};Button(onClick={if(name.isNotBlank()){businesses.add(BusinessProfile(System.currentTimeMillis(),name.trim(),fixed.toNumber()?:0.0,margin.toNumber()?:20.0));repo.saveBusinesses(businesses);name="";fixed=""}},modifier=Modifier.fillMaxWidth()){Text("ساخت پروفایل")}} }
    }
}

@Composable
private fun PriceImportTool(currency: CurrencyMode) {
    val context=LocalContext.current; val repo=remember{PriceBookRepository(context)}; var status by rememberSaveable{mutableStateOf("فایل CSV یا XLSX چهارستونه انتخاب کن: نام، قیمت، مقدار، واحد")}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null){runCatching{val bytes=context.contentResolver.openInputStream(uri)!!.use{it.readBytes()};val name=uri.toString().lowercase();val isZip = bytes.size >= 2 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte();val items=if(name.endsWith(".xlsx")||isZip) V3DataTools.parsePriceXlsx(bytes) else V3DataTools.parsePriceCsv(bytes.toString(Charsets.UTF_8));val merged=(items+repo.load()).distinctBy{it.id}.take(500);repo.save(merged);status="${items.size} ردیف وارد دفترچه قیمت شد."}.onFailure{status="خطا در Import: ${it.message?:"نامشخص"}"}}
    }
    CalculatorLayout("Import محصولات", "ورود گروهی اطلاعات به دفترچه قیمت.") { Text(status); Button(onClick={picker.launch(arrayOf("text/csv","text/comma-separated-values","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet","application/octet-stream"))},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.UploadFile,null);Spacer(Modifier.width(8.dp));Text("انتخاب فایل")};Text("برای CSV نمونه: نام محصول,120000,500,گرم",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant) }
}

@Composable
private fun SmartCommandTool(currency: CurrencyMode) {
    var text by rememberSaveable{mutableStateOf("850000 خریدم، 7 درصد کارمزد دارم و 30 درصد سود می‌خوام")}; val parsed=remember(text){V3Engine.parseSmartCommand(text)};val result=remember(parsed){V3Engine.smartSuggestedPrice(parsed)}
    CalculatorLayout("فرمان متنی", "یک جمله ساده فارسی بنویس تا پارامترهای اصلی استخراج شوند.") { OutlinedTextField(text,{text=it.take(240)},label={Text("مثال: 850000 خریدم 7 درصد کارمزد 30 درصد سود")},modifier=Modifier.fillMaxWidth(),minLines=3);ResultCard("تشخیص",result?.let{"${it.money()} ${currency.label}"}?:"اطلاعات کافی نیست",listOf("هزینه" to (parsed.cost?.money()?:"—"),"کارمزد" to (parsed.feePercent?.let{"${it.percent()}٪"}?:"—"),"Margin هدف" to (parsed.targetMarginPercent?.let{"${it.percent()}٪"}?:"—"))) }
}

@Composable
private fun ConverterTool(currency: CurrencyMode, repo: ProRepository) {
    var amount by rememberSaveable{mutableStateOf("")}; var rate by rememberSaveable{mutableStateOf("")}; var fromUnit by rememberSaveable{mutableStateOf("kg")}; var toUnit by rememberSaveable{mutableStateOf("g")}; var unitValue by rememberSaveable{mutableStateOf("")}
    val currencyResult=V3Engine.convertCurrency(amount.toNumber()?:-1.0,rate.toNumber()?:-1.0);val unitResult=V3Engine.convertUnit(unitValue.toNumber()?:-1.0,fromUnit,toUnit)
    CalculatorLayout("تبدیل واحد و ارز", "نرخ ارز را خودت وارد می‌کنی؛ برنامه نرخ آنلاین ادعا نمی‌کند.") { SettingsCard("ارز",Icons.Outlined.CurrencyExchange){NumberField("مبلغ",amount){amount=it};NumberField("نرخ تبدیل 1 واحد مبدا",rate){rate=it};currencyResult?.let{ResultStrip("نتیجه",it.money())}};SettingsCard("واحد",Icons.Outlined.Straighten){NumberField("مقدار",unitValue,useThousandsSeparator=false){unitValue=it};Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(fromUnit,{fromUnit=it.lowercase().take(4)},label={Text("از: kg/g/m/cm/ml/l")},modifier=Modifier.weight(1f),singleLine=true);OutlinedTextField(toUnit,{toUnit=it.lowercase().take(4)},label={Text("به")},modifier=Modifier.weight(1f),singleLine=true)};unitResult?.let{ResultStrip("نتیجه",it.clean())}} }
}

@Composable
private fun SecureBackupTool(history: List<HistoryEntry>, settings: SettingsRepository, pro: ProRepository) {
    val context=LocalContext.current;val shopping=remember{ShoppingRepository(context)};val priceBook=remember{PriceBookRepository(context)};var password by rememberSaveable{mutableStateOf("")};var status by rememberSaveable{mutableStateOf("رمز حداقل 4 کاراکتر وارد کن.")}
    val restorePicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null&&password.length>=4){runCatching{val bytes=context.contentResolver.openInputStream(uri)!!.use{it.readBytes()};val json=V3DataTools.decryptBackup(bytes,password.toCharArray())?:error("رمز اشتباه یا فایل نامعتبر");val restored=DataTransferManager.restoreBackup(json,settings,shopping,priceBook,pro)?:error("Backup نامعتبر");HistoryRepository(context).save(restored.history);status="Restore کامل انجام شد؛ برای Refresh کامل برنامه را دوباره باز کن."}.onFailure{status="Restore ناموفق: ${it.message?:"خطا"}"}}}
    CalculatorLayout("Backup رمزدار", "نسخه رمزگذاری‌شده با AES-256-GCM؛ رمز فقط دست خودت می‌ماند.") { OutlinedTextField(password,{password=it.take(80)},label={Text("رمز Backup")},modifier=Modifier.fillMaxWidth(),singleLine=true);Text(status,color=MaterialTheme.colorScheme.onSurfaceVariant);Button(onClick={if(password.length>=4){runCatching{val plain=DataTransferManager.createBackup(context,history,settings,shopping,priceBook,pro);val secure=V3DataTools.encryptBackup(context,plain.readText(),password.toCharArray())?:error("Encryption failed");DataTransferManager.shareFile(context,secure,"application/octet-stream","Backup رمزدار حسابیار");status="Backup رمزدار ساخته شد."}.onFailure{status="خطا: ${it.message}"}}},modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.Lock,null);Spacer(Modifier.width(8.dp));Text("ساخت Backup رمزدار")};OutlinedButton(onClick={restorePicker.launch(arrayOf("application/octet-stream","*/*"))},modifier=Modifier.fillMaxWidth()){Text("Restore فایل .hybak")};Text("رمز فراموش‌شده قابل بازیابی نیست.",fontSize=12.sp,color=MaterialTheme.colorScheme.error) }
}

@Composable
private fun CashCheckTool(currency: CurrencyMode, repo: ProRepository) {
    val active=repo.loadBusinesses().firstOrNull{it.id==repo.activeBusinessId};var fixed by rememberSaveable{mutableStateOf(active?.monthlyFixedCost?.takeIf{it>0}?.money()?:"")};var contribution by rememberSaveable{mutableStateOf("")};var sale by rememberSaveable{mutableStateOf("")};var target by rememberSaveable{mutableStateOf("")};val r=V3Engine.cashCheck(fixed.toNumber()?:-1.0,contribution.toNumber()?:-1.0,sale.toNumber()?:-1.0,target.toNumber()?:-1.0)
    CalculatorLayout("Cash Check ماهانه", "برای پوشش هزینه ثابت و رسیدن به سود هدف چند واحد باید بفروشی؟") { NumberField("هزینه ثابت ماهانه",fixed){fixed=it};NumberField("سهم سود هر واحد بعد از هزینه متغیر",contribution){contribution=it};NumberField("قیمت فروش هر واحد",sale){sale=it};NumberField("سود هدف ماهانه",target){target=it};r?.let{ResultCard("فروش لازم برای سربه‌سر","${it.requiredUnitsForBreakEven} واحد",listOf("درآمد سربه‌سر" to "${it.revenueForBreakEven.money()} ${currency.label}","تعداد برای سود هدف" to "${it.unitsForTargetProfit} واحد","درآمد برای سود هدف" to "${it.revenueForTargetProfit.money()} ${currency.label}"))} }
}

@Composable
private fun ToolHeader(title:String, subtitle:String){Column(verticalArrangement=Arrangement.spacedBy(4.dp)){Text(title,fontSize=25.sp,fontWeight=FontWeight.Bold);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
