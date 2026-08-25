@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.waxew.hesabyar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection
import com.waxew.hesabyar.data.*
import com.waxew.hesabyar.ui.theme.HesabYarTheme
import com.waxew.hesabyar.update.UpdateChecker
import com.waxew.hesabyar.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

private enum class RootTab { HOME, HISTORY, SETTINGS }
private enum class CalculatorKind { DISCOUNT, PROFIT, TARGET_PRICE, PERCENTAGE, CHANGE, TAX, COMPARE, BREAK_EVEN }
private data class ToolCard(val kind: CalculatorKind, val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tools = listOf(
    ToolCard(CalculatorKind.DISCOUNT, "تخفیف", "قیمت نهایی و صرفه‌جویی", Icons.Outlined.LocalOffer),
    ToolCard(CalculatorKind.PROFIT, "سود", "سود خالص و حاشیه سود", Icons.Outlined.TrendingUp),
    ToolCard(CalculatorKind.TARGET_PRICE, "قیمت فروش", "قیمت مناسب برای سود هدف", Icons.Outlined.Paid),
    ToolCard(CalculatorKind.PERCENTAGE, "درصد", "محاسبات روزمره درصد", Icons.Outlined.Percent),
    ToolCard(CalculatorKind.CHANGE, "کم / زیاد", "درصد افزایش یا کاهش", Icons.Outlined.SwapVert),
    ToolCard(CalculatorKind.TAX, "مالیات", "مالیات و مبلغ نهایی", Icons.Outlined.ReceiptLong),
    ToolCard(CalculatorKind.COMPARE, "مقایسه خرید", "کدام بسته به‌صرفه‌تر است؟", Icons.Outlined.CompareArrows),
    ToolCard(CalculatorKind.BREAK_EVEN, "سربه‌سر", "حداقل فروش بدون ضرر", Icons.Outlined.Balance)
)

@Composable
fun HesabYarApp() {
    val context = LocalContext.current
    val settingsRepo = remember { SettingsRepository(context) }
    val historyRepo = remember { HistoryRepository(context) }
    var themeMode by remember { mutableStateOf(settingsRepo.themeMode) }
    var currency by remember { mutableStateOf(settingsRepo.currencyMode) }
    val history = remember { mutableStateListOf<HistoryEntry>().apply { addAll(historyRepo.load()) } }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(Unit) { updateInfo = withContext(Dispatchers.IO) { UpdateChecker.check() } }

    HesabYarTheme(themeMode) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HesabYarShell(
                history = history,
                currency = currency,
                onHistoryChanged = { historyRepo.save(history) },
                themeMode = themeMode,
                onThemeModeChanged = { themeMode = it; settingsRepo.themeMode = it },
                onCurrencyChanged = { currency = it; settingsRepo.currencyMode = it }
            )
            updateInfo?.let { info ->
                AlertDialog(
                    onDismissRequest = { updateInfo = null },
                    icon = { Icon(Icons.Outlined.SystemUpdateAlt, null) },
                    title = { Text("نسخه ${info.versionName} آماده است") },
                    text = { Text(info.notes) },
                    confirmButton = { TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))); updateInfo = null }) { Text("دریافت آپدیت") } },
                    dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("بعداً") } }
                )
            }
        }
    }
}

@Composable
private fun HesabYarShell(history: SnapshotStateList<HistoryEntry>, currency: CurrencyMode, onHistoryChanged: () -> Unit, themeMode: ThemeMode, onThemeModeChanged: (ThemeMode) -> Unit, onCurrencyChanged: (CurrencyMode) -> Unit) {
    var tab by rememberSaveable { mutableStateOf(RootTab.HOME.name) }
    var selectedTool by rememberSaveable { mutableStateOf<String?>(null) }
    val activeTool = selectedTool?.let { runCatching { CalculatorKind.valueOf(it) }.getOrNull() }
    fun saveHistory(title: String, details: String, result: String) {
        history.add(0, HistoryEntry(System.currentTimeMillis(), title, details, result))
        while (history.size > 100) history.removeLast()
        onHistoryChanged()
    }
    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { if (activeTool != null) TopAppBar(title = { Text(tools.first { it.kind == activeTool }.title) }, navigationIcon = { IconButton(onClick = { selectedTool = null }) { Icon(Icons.Outlined.ArrowForward, contentDescription = "بازگشت") } }) },
        bottomBar = { if (activeTool == null) NavigationBar {
            NavigationBarItem(selected = tab == RootTab.HOME.name, onClick = { tab = RootTab.HOME.name }, icon = { Icon(Icons.Outlined.Home, null) }, label = { Text("خانه") })
            NavigationBarItem(selected = tab == RootTab.HISTORY.name, onClick = { tab = RootTab.HISTORY.name }, icon = { Icon(Icons.Outlined.History, null) }, label = { Text("تاریخچه") })
            NavigationBarItem(selected = tab == RootTab.SETTINGS.name, onClick = { tab = RootTab.SETTINGS.name }, icon = { Icon(Icons.Outlined.Settings, null) }, label = { Text("تنظیمات") })
        } }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (activeTool != null) CalculatorScreen(activeTool, currency, ::saveHistory)
            else when (RootTab.valueOf(tab)) {
                RootTab.HOME -> HomeScreen(currency) { selectedTool = it.name }
                RootTab.HISTORY -> HistoryScreen(history) { history.clear(); onHistoryChanged() }
                RootTab.SETTINGS -> SettingsScreen(themeMode, currency, onThemeModeChanged, onCurrencyChanged)
            }
        }
    }
}

@Composable
private fun HomeScreen(currency: CurrencyMode, onTool: (CalculatorKind) -> Unit) {
    var quickPrice by rememberSaveable { mutableStateOf("") }; var quickDiscount by rememberSaveable { mutableStateOf("") }
    val price = quickPrice.toNumber(); val discount = quickDiscount.toNumber(); val finalPrice = if (price != null && discount != null) price * (1 - discount / 100.0) else null
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp)) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Calculate, null, tint = MaterialTheme.colorScheme.onPrimary) } }
            Spacer(Modifier.width(12.dp)); Column { Text("حسابیار", fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("دستیار سریع خرید، سود و قیمت‌گذاری", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } }
        item { ElevatedCard { Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("محاسبه سریع تخفیف", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            NumberField("قیمت اصلی (${currency.label})", quickPrice) { quickPrice = it }; NumberField("درصد تخفیف", quickDiscount, "%") { quickDiscount = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) { listOf(10,20,30,50).forEach { p -> FilterChip(selected = quickDiscount == p.toString(), onClick = { quickDiscount = p.toString() }, label = { Text("$p٪") }, modifier = Modifier.weight(1f)) } }
            finalPrice?.let { ResultStrip("قیمت نهایی", "${it.money()} ${currency.label}") }
        } } }
        item { SectionTitle("ابزارهای حسابیار") }
        items(tools.chunked(2)) { rowTools -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            rowTools.forEach { tool -> ElevatedCard(onClick = { onTool(tool.kind) }, modifier = Modifier.weight(1f).height(146.dp)) { Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) { Icon(tool.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)); Column { Text(tool.title, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(tool.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } } } }
            if (rowTools.size == 1) Spacer(Modifier.weight(1f))
        } }
        item { AssistChip(onClick = {}, label = { Text("نسخه ۱.۰ • آفلاین برای محاسبات • بررسی آنلاین آپدیت") }, leadingIcon = { Icon(Icons.Outlined.Verified, null) }) }
    }
}

@Composable
private fun CalculatorScreen(kind: CalculatorKind, currency: CurrencyMode, onSave: (String,String,String)->Unit) { when(kind) {
    CalculatorKind.DISCOUNT -> DiscountCalculator(currency,onSave); CalculatorKind.PROFIT -> ProfitCalculator(currency,onSave); CalculatorKind.TARGET_PRICE -> TargetPriceCalculator(currency,onSave); CalculatorKind.PERCENTAGE -> PercentageCalculator(onSave); CalculatorKind.CHANGE -> ChangeCalculator(onSave); CalculatorKind.TAX -> TaxCalculator(currency,onSave); CalculatorKind.COMPARE -> CompareCalculator(currency,onSave); CalculatorKind.BREAK_EVEN -> BreakEvenCalculator(currency,onSave)
} }

@Composable
private fun DiscountCalculator(currency: CurrencyMode, onSave: (String,String,String)->Unit) {
    var priceText by rememberSaveable { mutableStateOf("") }; var firstText by rememberSaveable { mutableStateOf("") }; var secondText by rememberSaveable { mutableStateOf("") }
    val price=priceText.toNumber(); val d1=firstText.toNumber(); val d2=secondText.toNumber()?:0.0; val final=if(price!=null&&d1!=null) price*(1-d1/100.0)*(1-d2/100.0) else null; val saved=if(price!=null&&final!=null) price-final else null; val effective=if(price!=null&&price>0&&final!=null)(1-final/price)*100 else null
    CalculatorLayout("تخفیف ساده یا چندمرحله‌ای","تخفیف دوم اختیاری است.") { NumberField("قیمت اصلی (${currency.label})",priceText){priceText=it}; NumberField("تخفیف اول",firstText,"%"){firstText=it}; NumberField("تخفیف دوم (اختیاری)",secondText,"%"){secondText=it}; if(final!=null&&saved!=null&&effective!=null){ ResultCard("قیمت نهایی","${final.money()} ${currency.label}",listOf("صرفه‌جویی" to "${saved.money()} ${currency.label}","تخفیف واقعی" to "${effective.percent()}٪")); SaveButton{onSave("تخفیف","${price!!.money()} با ${effective.percent()}٪ تخفیف","${final.money()} ${currency.label}")} } }
}

@Composable
private fun ProfitCalculator(currency: CurrencyMode, onSave: (String,String,String)->Unit) {
    var costText by rememberSaveable{mutableStateOf("")}; var extraText by rememberSaveable{mutableStateOf("")}; var saleText by rememberSaveable{mutableStateOf("")}; val cost=costText.toNumber(); val extra=extraText.toNumber()?:0.0; val sale=saleText.toNumber(); val total=cost?.plus(extra); val profit=if(total!=null&&sale!=null)sale-total else null; val markup=if(profit!=null&&total!=null&&total!=0.0)profit/total*100 else null; val margin=if(profit!=null&&sale!=null&&sale!=0.0)profit/sale*100 else null
    CalculatorLayout("سود واقعی","هزینه‌های جانبی را هم وارد کن تا نتیجه دقیق‌تر باشد."){ NumberField("قیمت خرید (${currency.label})",costText){costText=it}; NumberField("هزینه جانبی (${currency.label})",extraText){extraText=it}; NumberField("قیمت فروش (${currency.label})",saleText){saleText=it}; if(profit!=null&&markup!=null&&margin!=null&&total!=null){ResultCard("سود خالص","${profit.money()} ${currency.label}",listOf("هزینه واقعی" to "${total.money()} ${currency.label}","درصد سود روی هزینه" to "${markup.percent()}٪","حاشیه سود" to "${margin.percent()}٪"),if(profit<0)"این قیمت فروش باعث ضرر می‌شود." else null); SaveButton{onSave("سود","خرید ${total.money()} / فروش ${sale!!.money()}","سود ${profit.money()} ${currency.label}")} } }
}

@Composable
private fun TargetPriceCalculator(currency: CurrencyMode,onSave:(String,String,String)->Unit){ var costText by rememberSaveable{mutableStateOf("")}; var extraText by rememberSaveable{mutableStateOf("")}; var targetText by rememberSaveable{mutableStateOf("")}; var useMargin by rememberSaveable{mutableStateOf(false)}; val cost=costText.toNumber(); val extra=extraText.toNumber()?:0.0; val target=targetText.toNumber(); val total=cost?.plus(extra); val targetPrice=if(total!=null&&target!=null){if(useMargin&&target<100)total/(1-target/100.0) else total*(1+target/100.0)}else null
    CalculatorLayout("قیمت فروش پیشنهادی","مشخص کن درصد را به‌صورت سود روی هزینه می‌خواهی یا حاشیه سود."){NumberField("هزینه خرید (${currency.label})",costText){costText=it}; NumberField("هزینه جانبی (${currency.label})",extraText){extraText=it}; NumberField(if(useMargin)"حاشیه سود هدف" else "سود هدف",targetText,"%"){targetText=it}; Row(verticalAlignment=Alignment.CenterVertically){Switch(useMargin,{useMargin=it});Spacer(Modifier.width(8.dp));Text(if(useMargin)"محاسبه با حاشیه سود" else "محاسبه سود روی هزینه")}; if(targetPrice!=null&&total!=null){ResultCard("قیمت پیشنهادی","${targetPrice.money()} ${currency.label}",listOf("هزینه واقعی" to "${total.money()} ${currency.label}"));SaveButton{onSave("قیمت فروش","هزینه ${total.money()} / هدف ${target!!.percent()}٪","${targetPrice.money()} ${currency.label}")}} }
}

@Composable
private fun PercentageCalculator(onSave:(String,String,String)->Unit){var mode by rememberSaveable{mutableIntStateOf(0)};var aText by rememberSaveable{mutableStateOf("")};var bText by rememberSaveable{mutableStateOf("")};val a=aText.toNumber();val b=bText.toNumber();val result=when(mode){0->if(a!=null&&b!=null)a/100*b else null;1->if(a!=null&&b!=null&&b!=0.0)a/b*100 else null;else->if(a!=null&&b!=null&&a!=0.0)(b-a)/a*100 else null};CalculatorLayout("محاسبه درصد","نوع محاسبه را انتخاب کن."){SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()){listOf("X٪ از Y","X چند٪ Y؟","تغییر٪").forEachIndexed{index,label->SegmentedButton(selected=mode==index,onClick={mode=index},shape=SegmentedButtonDefaults.itemShape(index,3)){Text(label,fontSize=12.sp)}}};NumberField(if(mode==0)"درصد X" else if(mode==2)"مقدار قبلی" else "مقدار X",aText,if(mode==0)"%" else null){aText=it};NumberField(if(mode==0)"عدد Y" else if(mode==2)"مقدار جدید" else "مقدار Y",bText){bText=it};result?.let{val unit=if(mode==0)"" else "٪";ResultCard("نتیجه","${it.clean()}$unit",emptyList());SaveButton{onSave("درصد","$a و $b","${it.clean()}$unit")}}}}

@Composable
private fun ChangeCalculator(onSave:(String,String,String)->Unit){var oldText by rememberSaveable{mutableStateOf("")};var newText by rememberSaveable{mutableStateOf("")};val old=oldText.toNumber();val new=newText.toNumber();val change=if(old!=null&&new!=null&&old!=0.0)(new-old)/old*100 else null;CalculatorLayout("افزایش / کاهش","تغییر مقدار قبلی تا مقدار جدید را به درصد ببین."){NumberField("مقدار قبلی",oldText){oldText=it};NumberField("مقدار جدید",newText){newText=it};change?.let{ResultCard(if(it>=0)"افزایش" else "کاهش","${kotlin.math.abs(it).percent()}٪",listOf("اختلاف عددی" to (new!!-old!!).clean()));SaveButton{onSave("تغییر درصدی","$old ← $new","${it.percent()}٪")}}}}

@Composable
private fun TaxCalculator(currency:CurrencyMode,onSave:(String,String,String)->Unit){var amountText by rememberSaveable{mutableStateOf("")};var rateText by rememberSaveable{mutableStateOf("10")};val amount=amountText.toNumber();val rate=rateText.toNumber();val tax=if(amount!=null&&rate!=null)amount*rate/100 else null;val total=if(amount!=null&&tax!=null)amount+tax else null;CalculatorLayout("مالیات","درصد مالیات قابل تغییر است."){NumberField("مبلغ پایه (${currency.label})",amountText){amountText=it};NumberField("درصد مالیات",rateText,"%"){rateText=it};if(tax!=null&&total!=null){ResultCard("مبلغ نهایی","${total.money()} ${currency.label}",listOf("مالیات" to "${tax.money()} ${currency.label}"));SaveButton{onSave("مالیات","${amount!!.money()} + ${rate!!.percent()}٪","${total.money()} ${currency.label}")}}}}

@Composable
private fun CompareCalculator(currency:CurrencyMode,onSave:(String,String,String)->Unit){var p1 by rememberSaveable{mutableStateOf("")};var q1 by rememberSaveable{mutableStateOf("")};var p2 by rememberSaveable{mutableStateOf("")};var q2 by rememberSaveable{mutableStateOf("")};val price1=p1.toNumber();val qty1=q1.toNumber();val price2=p2.toNumber();val qty2=q2.toNumber();val unit1=if(price1!=null&&qty1!=null&&qty1>0)price1/qty1 else null;val unit2=if(price2!=null&&qty2!=null&&qty2>0)price2/qty2 else null;CalculatorLayout("مقایسه دو کالا","قیمت و مقدار هر بسته را وارد کن؛ واحد مقدار برای هر دو باید یکسان باشد."){Text("کالای اول",fontWeight=FontWeight.Bold);NumberField("قیمت (${currency.label})",p1){p1=it};NumberField("مقدار / وزن / تعداد",q1){q1=it};HorizontalDivider();Text("کالای دوم",fontWeight=FontWeight.Bold);NumberField("قیمت (${currency.label})",p2){p2=it};NumberField("مقدار / وزن / تعداد",q2){q2=it};if(unit1!=null&&unit2!=null){val winner=if(unit1<=unit2)"کالای اول" else "کالای دوم";val cheaper=minOf(unit1,unit2);val expensive=maxOf(unit1,unit2);val saving=if(expensive>0)(1-cheaper/expensive)*100 else 0.0;ResultCard("$winner به‌صرفه‌تر است","${cheaper.money()} ${currency.label} / واحد",listOf("کالای اول / واحد" to "${unit1.money()} ${currency.label}","کالای دوم / واحد" to "${unit2.money()} ${currency.label}","مزیت تقریبی" to "${saving.percent()}٪"));SaveButton{onSave("مقایسه خرید","بسته ۱ و ۲","$winner حدود ${saving.percent()}٪ به‌صرفه‌تر")}}}}

@Composable
private fun BreakEvenCalculator(currency:CurrencyMode,onSave:(String,String,String)->Unit){var fixedText by rememberSaveable{mutableStateOf("")};var profitText by rememberSaveable{mutableStateOf("")};val fixed=fixedText.toNumber();val perUnit=profitText.toNumber();val units=if(fixed!=null&&perUnit!=null&&perUnit>0)ceil(fixed/perUnit).toInt() else null;CalculatorLayout("نقطه سربه‌سر","هزینه ثابت و سود خالص هر فروش را وارد کن."){NumberField("هزینه ثابت (${currency.label})",fixedText){fixedText=it};NumberField("سود خالص هر محصول (${currency.label})",profitText){profitText=it};units?.let{ResultCard("حداقل فروش برای سربه‌سر","$it محصول",listOf("فروش بعد از این نقطه" to "وارد محدوده سود می‌شود"));SaveButton{onSave("نقطه سربه‌سر","هزینه ${fixed!!.money()} / سود واحد ${perUnit!!.money()}","$it محصول")}}}}

@Composable
private fun HistoryScreen(history:List<HistoryEntry>,onClear:()->Unit){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Column{Text("تاریخچه",fontSize=26.sp,fontWeight=FontWeight.Bold);Text("آخرین محاسبات روی همین گوشی ذخیره می‌شوند.",color=MaterialTheme.colorScheme.onSurfaceVariant)};if(history.isNotEmpty())TextButton(onClick=onClear){Text("پاک کردن")}}};if(history.isEmpty())item{EmptyState(Icons.Outlined.History,"هنوز محاسبه‌ای ذخیره نشده")}else items(history,key={it.id}){entry->ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(entry.title,fontWeight=FontWeight.Bold);Text(entry.createdAt.shortDate(),fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)};Text(entry.details,fontSize=13.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text(entry.result,fontSize=18.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)}}}}}

@Composable
private fun SettingsScreen(themeMode:ThemeMode,currency:CurrencyMode,onThemeModeChanged:(ThemeMode)->Unit,onCurrencyChanged:(CurrencyMode)->Unit){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){item{Text("تنظیمات",fontSize=26.sp,fontWeight=FontWeight.Bold);Text("ظاهر و واحد پول حسابیار",color=MaterialTheme.colorScheme.onSurfaceVariant)};item{SettingsCard("ظاهر",Icons.Outlined.DarkMode){ThemeMode.entries.forEach{mode->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){RadioButton(selected=themeMode==mode,onClick={onThemeModeChanged(mode)});Text(when(mode){ThemeMode.SYSTEM->"هماهنگ با گوشی";ThemeMode.LIGHT->"روشن";ThemeMode.DARK->"تاریک"})}}}};item{SettingsCard("واحد پول",Icons.Outlined.Payments){CurrencyMode.entries.forEach{mode->Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){RadioButton(selected=currency==mode,onClick={onCurrencyChanged(mode)});Text(mode.label)}}}};item{SettingsCard("درباره حسابیار",Icons.Outlined.Info){Text("نسخه ${BuildConfig.VERSION_NAME}");Text("بسته: ${BuildConfig.APPLICATION_ID}",fontSize=12.sp,color=MaterialTheme.colorScheme.onSurfaceVariant);Text("برای آپدیت‌های بعدی، versionCode افزایش می‌یابد و برنامه از Update Manifest نسخه جدید را تشخیص می‌دهد.",fontSize=13.sp)}}}}

@Composable private fun CalculatorLayout(title:String,subtitle:String,content:@Composable ColumnScope.()->Unit){LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){item{Text(title,fontSize=24.sp,fontWeight=FontWeight.Bold);Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant)};item{Column(verticalArrangement=Arrangement.spacedBy(12.dp),content=content)};item{Spacer(Modifier.height(16.dp))}}}
@Composable private fun NumberField(label:String,value:String,suffix:String?=null,onValue:(String)->Unit){OutlinedTextField(value=value,onValueChange={raw->val normalized=raw.normalizeDigits().filter{it.isDigit()||it=='.'};if(normalized.count{it=='.'}<=1)onValue(normalized)},label={Text(label)},suffix=suffix?.let{{Text(it)}},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true,modifier=Modifier.fillMaxWidth())}
@Composable private fun ResultStrip(label:String,value:String){Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=MaterialTheme.shapes.large){Row(Modifier.fillMaxWidth().padding(14.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){Text(label);Text(value,fontSize=18.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary)}}}
@Composable private fun ResultCard(primaryLabel:String,primaryValue:String,rows:List<Pair<String,String>>,warning:String?=null){Card(colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.fillMaxWidth().padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(primaryLabel,color=MaterialTheme.colorScheme.onPrimaryContainer);Text(primaryValue,fontSize=28.sp,fontWeight=FontWeight.Bold,color=MaterialTheme.colorScheme.primary);rows.forEach{(label,value)->HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.2f));Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(label);Text(value,fontWeight=FontWeight.SemiBold)}};warning?.let{Text(it,color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold)}}}}
@Composable private fun SaveButton(onClick:()->Unit){Button(onClick=onClick,modifier=Modifier.fillMaxWidth()){Icon(Icons.Outlined.BookmarkAdd,null);Spacer(Modifier.width(8.dp));Text("ذخیره در تاریخچه")}}
@Composable private fun SettingsCard(title:String,icon:androidx.compose.ui.graphics.vector.ImageVector,content:@Composable ColumnScope.()->Unit){ElevatedCard(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Row(verticalAlignment=Alignment.CenterVertically){Icon(icon,null,tint=MaterialTheme.colorScheme.primary);Spacer(Modifier.width(8.dp));Text(title,fontWeight=FontWeight.Bold,fontSize=18.sp)};content()}}}
@Composable private fun SectionTitle(text:String){Text(text,fontSize=19.sp,fontWeight=FontWeight.Bold)}
@Composable private fun EmptyState(icon:androidx.compose.ui.graphics.vector.ImageVector,text:String){Box(Modifier.fillMaxWidth().padding(vertical=64.dp),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(10.dp)){Icon(icon,null,modifier=Modifier.size(48.dp),tint=MaterialTheme.colorScheme.outline);Text(text,color=MaterialTheme.colorScheme.onSurfaceVariant)}}}

private fun String.normalizeDigits():String=buildString{this@normalizeDigits.forEach{ch->append(when(ch){'۰'->'0';'۱'->'1';'۲'->'2';'۳'->'3';'۴'->'4';'۵'->'5';'۶'->'6';'۷'->'7';'۸'->'8';'۹'->'9';'٠'->'0';'١'->'1';'٢'->'2';'٣'->'3';'٤'->'4';'٥'->'5';'٦'->'6';'٧'->'7';'٨'->'8';'٩'->'9';'٫',','->'.';else->ch})}}
private fun String.toNumber():Double?=normalizeDigits().toDoubleOrNull();private val moneyFormat=DecimalFormat("#,###");private val decimalFormat=DecimalFormat("#,##0.##");private fun Double.money():String=moneyFormat.format(this);private fun Double.percent():String=decimalFormat.format(this);private fun Double.clean():String=decimalFormat.format(this);private fun Long.shortDate():String=SimpleDateFormat("MM/dd  HH:mm",Locale.getDefault()).format(Date(this))
