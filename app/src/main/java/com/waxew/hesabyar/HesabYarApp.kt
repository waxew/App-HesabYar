@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.waxew.hesabyar

// Android Intent/URI برای Share، ایمیل و لینک آپدیت.
import android.content.Context
import android.content.Intent
import android.net.Uri

// BackHandler دکمه Back سیستم را داخل ناوبری Compose مدیریت می‌کند.
import androidx.activity.compose.BackHandler

// Layout و LazyColumn برای ساخت صفحه‌ها و Grid ابزارها.
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

// آیکون‌های Material مرتبط با محاسبات و منوی برنامه.
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*

// کامپوننت‌های Material 3 شامل Drawer، TopAppBar، NavigationBar و Dialog.
import androidx.compose.material3.*

// Stateهای Compose و rememberSaveable برای حفظ ناوبری/ورودی‌ها در بازسازی Activity.
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList

// ابزارهای UI، RTL، تایپوگرافی و اندازه‌ها.
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Data layer برنامه؛ تنظیمات و تاریخچه روی دستگاه باقی می‌مانند.
import com.waxew.hesabyar.data.*

// Theme و UpdateChecker حسابیار.
import com.waxew.hesabyar.ui.theme.HesabYarTheme
import com.waxew.hesabyar.update.UpdateChecker
import com.waxew.hesabyar.update.UpdateInfo

// Coroutine برای IO شبکه و باز/بسته کردن Drawer.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** صفحه‌های سطح اصلی که از منوی همبرگری یا Bottom Navigation باز می‌شوند. */
enum class RootPage { HOME, HISTORY, SETTINGS, ABOUT_US, CONTACT_US, ABOUT_APP }

/** ابزارهای محاسباتی موجود در نسخه فعلی. */
enum class CalculatorKind { DISCOUNT, PROFIT, TARGET_PRICE, PERCENTAGE, CHANGE, TAX, COMPARE, BREAK_EVEN }

/** مدل نمایشی هر کارت ابزار در صفحه خانه. */
private data class ToolCard(
    val kind: CalculatorKind,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

/** فهرست ابزارهای نسخه فعلی حسابیار. */
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

// لینک فعلی برای گزینه «معرفی به دوستان»؛ بعداً می‌تواند به کافه‌بازار تغییر کند.
private const val APP_SHARE_URL = "https://github.com/waxew/App-HesabYar"

// ایمیل رسمی پشتیبانی AS Team.
private const val SUPPORT_EMAIL = "as.team.support@gmail.com"

/** ریشه برنامه؛ Repositoryها، Theme، RTL و بررسی آپدیت را راه‌اندازی می‌کند. */
@Composable
fun HesabYarApp() {
    // Context برای SharedPreferences و Intentها.
    val context = LocalContext.current
    // Repository تنظیمات فقط یک‌بار در Composition ساخته می‌شود.
    val settingsRepository = remember { SettingsRepository(context) }
    // Repository تاریخچه نیز فقط یک‌بار ساخته می‌شود.
    val historyRepository = remember { HistoryRepository(context) }

    // Stateهای اولیه از حافظه محلی خوانده می‌شوند تا Update آن‌ها را از بین نبرد.
    var themeMode by remember { mutableStateOf(settingsRepository.themeMode) }
    var currency by remember { mutableStateOf(settingsRepository.currencyMode) }
    var notificationsEnabled by remember { mutableStateOf(settingsRepository.notificationsEnabled) }
    val history = remember { mutableStateListOf<HistoryEntry>().apply { addAll(historyRepository.load()) } }

    // اگر نسخه جدید پیدا شود، اطلاعات Dialog در این State قرار می‌گیرد.
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    // بررسی نسخه فقط در صورت فعال بودن گزینه اعلان‌ها اجرا می‌شود.
    LaunchedEffect(notificationsEnabled) {
        if (!notificationsEnabled) {
            updateInfo = null
            return@LaunchedEffect
        }
        // شبکه خارج از Main Thread اجرا می‌شود تا UI قفل نشود.
        updateInfo = withContext(Dispatchers.IO) { UpdateChecker.check() }
    }

    // Theme انتخاب‌شده روی تمام UI اعمال می‌شود.
    HesabYarTheme(themeMode) {
        // جهت کلی فارسی/RTL است؛ Drawer نیز در نتیجه از سمت راست باز می‌شود.
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HesabYarShell(
                history = history,
                currency = currency,
                onHistoryChanged = { historyRepository.save(history) },
                themeMode = themeMode,
                onThemeModeChanged = { newMode ->
                    themeMode = newMode
                    settingsRepository.themeMode = newMode
                },
                onCurrencyChanged = { newCurrency ->
                    currency = newCurrency
                    settingsRepository.currencyMode = newCurrency
                },
                notificationsEnabled = notificationsEnabled,
                onNotificationsChanged = { enabled ->
                    notificationsEnabled = enabled
                    settingsRepository.notificationsEnabled = enabled
                }
            )

            // Dialog آپدیت فقط وقتی UpdateChecker نسخه جدیدتری پیدا کرده باشد نمایش داده می‌شود.
            updateInfo?.let { info ->
                AlertDialog(
                    onDismissRequest = { updateInfo = null },
                    icon = { Icon(Icons.Outlined.SystemUpdateAlt, contentDescription = null) },
                    title = { Text("نسخه ${info.versionName} آماده است") },
                    text = { Text(info.notes) },
                    confirmButton = {
                        TextButton(onClick = {
                            // لینک انتشار/APK با برنامه مناسب Android باز می‌شود؛ خطا باعث Crash نمی‌شود.
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))) }
                            updateInfo = null
                        }) { Text("دریافت آپدیت") }
                    },
                    dismissButton = {
                        TextButton(onClick = { updateInfo = null }) { Text("بعداً") }
                    }
                )
            }
        }
    }
}

/** Shell مرکزی؛ Drawer، TopAppBar، Bottom Navigation و رفتار Back در اینجا کنترل می‌شوند. */
@Composable
private fun HesabYarShell(
    history: SnapshotStateList<HistoryEntry>,
    currency: CurrencyMode,
    onHistoryChanged: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onCurrencyChanged: (CurrencyMode) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsChanged: (Boolean) -> Unit
) {
    // Context برای Share Sheet منوی همبرگری.
    val context = LocalContext.current
    // صفحه سطح اصلی با Saveable در Rotation/بازسازی Activity حفظ می‌شود.
    var pageName by rememberSaveable { mutableStateOf(RootPage.HOME.name) }
    // نام ابزار فرعی؛ null یعنی صفحه سطح اصلی باز است.
    var selectedToolName by rememberSaveable { mutableStateOf<String?>(null) }
    // Drawer در شروع بسته است.
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    // Scope برای اجرای suspend functionهای open/close Drawer.
    val scope = rememberCoroutineScope()

    // Stringهای Saveable با محافظت به enum تبدیل می‌شوند تا State خراب باعث Crash نشود.
    val currentPage = runCatching { RootPage.valueOf(pageName) }.getOrDefault(RootPage.HOME)
    val activeTool = selectedToolName?.let { saved -> runCatching { CalculatorKind.valueOf(saved) }.getOrNull() }

    // عنوان TopAppBar از ابزار فعال یا صفحه اصلی استخراج می‌شود.
    val topBarTitle = activeTool?.let { kind -> tools.firstOrNull { it.kind == kind }?.title } ?: rootPageTitle(currentPage)

    /** نتیجه جدید را ابتدای تاریخچه اضافه و سقف ۱۰۰ مورد را حفظ می‌کند. */
    fun saveHistory(title: String, details: String, result: String) {
        // timestamp هم شناسه و هم زمان ایجاد رکورد است.
        history.add(0, HistoryEntry(System.currentTimeMillis(), title, details, result))
        // قدیمی‌ترین موارد بعد از ۱۰۰ رکورد حذف می‌شوند.
        while (history.size > 100) history.removeAt(history.lastIndex)
        // داده جدید روی دستگاه ذخیره می‌شود.
        onHistoryChanged()
    }

    /** ناوبری به صفحه اصلی؛ ابزار فرعی را نیز می‌بندد. */
    fun navigateTo(page: RootPage) {
        selectedToolName = null
        pageName = page.name
    }

    // Back فقط وقتی مقصد داخلی برای برگشت وجود دارد Intercept می‌شود.
    BackHandler(enabled = drawerState.isOpen || selectedToolName != null || currentPage != RootPage.HOME) {
        when {
            // اول Drawer بسته می‌شود تا Back رفتار طبیعی منو را داشته باشد.
            drawerState.isOpen -> scope.launch { drawerState.close() }
            // سپس از هر ابزار به صفحه قبلی برمی‌گردیم و برنامه خارج نمی‌شود.
            selectedToolName != null -> selectedToolName = null
            // از تنظیمات/تاریخچه/درباره‌ها نیز به خانه برمی‌گردیم.
            currentPage != RootPage.HOME -> pageName = RootPage.HOME.name
        }
    }

    // ModalNavigationDrawer در LayoutDirection.Rtl از سمت راست نمایش داده می‌شود.
    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = activeTool == null,
        drawerContent = {
            AppDrawer(
                currentPage = currentPage,
                onNavigate = { destination ->
                    navigateTo(destination)
                    scope.launch { drawerState.close() }
                },
                onShare = {
                    shareApp(context)
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        // Scaffold ساختار ثابت Top/Bottom/Content را نگه می‌دارد.
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text(topBarTitle) },
                    navigationIcon = {
                        if (activeTool != null) {
                            // داخل ابزار، آیکون Back در سمت راست TopAppBar قرار می‌گیرد.
                            IconButton(onClick = { selectedToolName = null }) {
                                Icon(Icons.Outlined.ArrowForward, contentDescription = "بازگشت")
                            }
                        } else {
                            // روی صفحات سطح اصلی، آیکون سه‌خط منوی همبرگری نمایش داده می‌شود.
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Outlined.Menu, contentDescription = "منوی همبرگری")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                // Bottom Navigation فقط برای سه صفحه پرتکرار نمایش داده می‌شود.
                if (activeTool == null && currentPage in setOf(RootPage.HOME, RootPage.HISTORY, RootPage.SETTINGS)) {
                    NavigationBar {
                        NavigationBarItem(
                            selected = currentPage == RootPage.HOME,
                            onClick = { navigateTo(RootPage.HOME) },
                            icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                            label = { Text("خانه") }
                        )
                        NavigationBarItem(
                            selected = currentPage == RootPage.HISTORY,
                            onClick = { navigateTo(RootPage.HISTORY) },
                            icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                            label = { Text("تاریخچه") }
                        )
                        NavigationBarItem(
                            selected = currentPage == RootPage.SETTINGS,
                            onClick = { navigateTo(RootPage.SETTINGS) },
                            icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                            label = { Text("تنظیمات") }
                        )
                    }
                }
            }
        ) { padding ->
            // padding خود Scaffold مانع قرار گرفتن محتوا زیر Barها می‌شود.
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (activeTool != null) {
                    CalculatorScreen(activeTool, currency, ::saveHistory)
                } else {
                    when (currentPage) {
                        RootPage.HOME -> HomeScreen(currency) { tool -> selectedToolName = tool.name }
                        RootPage.HISTORY -> HistoryScreen(history) {
                            history.clear()
                            onHistoryChanged()
                        }
                        RootPage.SETTINGS -> SettingsScreen(
                            themeMode,
                            currency,
                            notificationsEnabled,
                            onThemeModeChanged,
                            onCurrencyChanged,
                            onNotificationsChanged
                        )
                        RootPage.ABOUT_US -> AboutUsScreen()
                        RootPage.CONTACT_US -> ContactUsScreen()
                        RootPage.ABOUT_APP -> AboutAppScreen()
                    }
                }
            }
        }
    }
}

/** محتوای Drawer سمت راست مطابق ساختار منوی عمومی پروژه‌های Android. */
@Composable
private fun AppDrawer(currentPage: RootPage, onNavigate: (RootPage) -> Unit, onShare: () -> Unit) {
    // Drawer Sheet پس‌زمینه و اندازه استاندارد Material 3 را فراهم می‌کند.
    ModalDrawerSheet {
        // هدر برنامه.
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)) {
            Text("حسابیار", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("محاسبات سریع خرید، سود و قیمت‌گذاری", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider()

        // گزینه‌های Drawer؛ هرکدام آیکون مرتبط دارند.
        DrawerItem("خانه", Icons.Outlined.Home, currentPage == RootPage.HOME) { onNavigate(RootPage.HOME) }
        DrawerItem("تنظیمات", Icons.Outlined.Settings, currentPage == RootPage.SETTINGS) { onNavigate(RootPage.SETTINGS) }
        DrawerItem("معرفی به دوستان", Icons.Outlined.Share, false, onShare)
        DrawerItem("درباره ما", Icons.Outlined.Person, currentPage == RootPage.ABOUT_US) { onNavigate(RootPage.ABOUT_US) }
        DrawerItem("تماس با ما", Icons.Outlined.ContactMail, currentPage == RootPage.CONTACT_US) { onNavigate(RootPage.CONTACT_US) }
        DrawerItem("درباره نرم افزار", Icons.Outlined.Info, currentPage == RootPage.ABOUT_APP) { onNavigate(RootPage.ABOUT_APP) }
    }
}

/** آیتم قابل استفاده مجدد Drawer. */
@Composable
private fun DrawerItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}

/** داشبورد اصلی شامل محاسبه سریع و کارت ابزارها. */
@Composable
private fun HomeScreen(currency: CurrencyMode, onTool: (CalculatorKind) -> Unit) {
    // ورودی‌های محاسبه سریع تخفیف.
    var quickPrice by rememberSaveable { mutableStateOf("") }
    var quickDiscount by rememberSaveable { mutableStateOf("") }
    val price = quickPrice.toNumber()
    val discount = quickDiscount.toNumber()
    // Quick Calculator همان Engine تست‌شده را استفاده می‌کند.
    val quickResult = if (price != null && discount != null) CalculationEngine.discount(price, discount) else null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // هویت بصری برنامه.
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(52.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Calculate, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("حسابیار", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("دستیار سریع خرید، سود و قیمت‌گذاری", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // کارت محاسبه سریع تخفیف.
        item {
            ElevatedCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Bolt, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("محاسبه سریع تخفیف", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    NumberField("قیمت اصلی (${currency.label})", quickPrice) { quickPrice = it }
                    NumberField("درصد تخفیف", quickDiscount, "%") { quickDiscount = it }
                    // درصدهای پرکاربرد انتخاب یک‌لمسی دارند.
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(10, 20, 30, 50).forEach { percent ->
                            FilterChip(
                                selected = quickDiscount == percent.toString(),
                                onClick = { quickDiscount = percent.toString() },
                                label = { Text("$percent٪") },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    // نتیجه بدون دکمه محاسبه و همزمان با ورودی ظاهر می‌شود.
                    quickResult?.let { ResultStrip("قیمت نهایی", "${it.finalPrice.money()} ${currency.label}") }
                }
            }
        }

        // عنوان Grid ابزارها.
        item { SectionTitle("ابزارهای حسابیار") }
        // دو کارت در هر ردیف.
        items(tools.chunked(2)) { rowTools ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                rowTools.forEach { tool ->
                    ElevatedCard(
                        onClick = { onTool(tool.kind) },
                        modifier = Modifier.weight(1f).height(146.dp)
                    ) {
                        Column(
                            Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Icon(tool.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                            Column {
                                Text(tool.title, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                Text(tool.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                if (rowTools.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        // وضعیت نسخه و Offline بودن محاسبات.
        item {
            AssistChip(
                onClick = {},
                label = { Text("نسخه ${BuildConfig.VERSION_NAME} • محاسبات آفلاین • بررسی آنلاین آپدیت") },
                leadingIcon = { Icon(Icons.Outlined.Verified, contentDescription = null) }
            )
        }
    }
}

/** تاریخچه نتایج ذخیره‌شده؛ حذف همه موارد نیازمند تایید است. */
@Composable
private fun HistoryScreen(history: List<HistoryEntry>, onClear: () -> Unit) {
    // Dialog تایید حذف فقط پس از لمس «پاک کردن» باز می‌شود.
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("تاریخچه", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("آخرین محاسبات روی همین گوشی ذخیره می‌شوند.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (history.isNotEmpty()) TextButton(onClick = { showClearDialog = true }) { Text("پاک کردن") }
            }
        }

        // خالی بودن یا نمایش کارت نتایج.
        if (history.isEmpty()) {
            item { EmptyState(Icons.Outlined.History, "هنوز محاسبه‌ای ذخیره نشده") }
        } else {
            items(history, key = { entry -> entry.id }) { entry ->
                ElevatedCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(entry.title, fontWeight = FontWeight.Bold)
                            Text(entry.createdAt.shortDate(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(entry.details, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(entry.result, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }

    // محافظت در برابر پاک شدن تصادفی تاریخچه.
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("پاک کردن تاریخچه؟") },
            text = { Text("تمام محاسبات ذخیره‌شده از این گوشی حذف می‌شوند.") },
            confirmButton = {
                TextButton(onClick = {
                    onClear()
                    showClearDialog = false
                }) { Text("پاک کردن") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("انصراف") } }
        )
    }
}

/** تنظیمات ظاهر، واحد پول و اعلان نسخه جدید. */
@Composable
private fun SettingsScreen(
    themeMode: ThemeMode,
    currency: CurrencyMode,
    notificationsEnabled: Boolean,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onCurrencyChanged: (CurrencyMode) -> Unit,
    onNotificationsChanged: (Boolean) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // هدر تنظیمات.
        item {
            Text("تنظیمات", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("ظاهر، واحد پول و اعلان‌های حسابیار", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // انتخاب تم.
        item {
            SettingsCard("ظاهر", Icons.Outlined.DarkMode) {
                ThemeMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = themeMode == mode, onClick = { onThemeModeChanged(mode) })
                        Text(when (mode) {
                            ThemeMode.SYSTEM -> "هماهنگ با گوشی"
                            ThemeMode.LIGHT -> "روشن"
                            ThemeMode.DARK -> "تاریک"
                        })
                    }
                }
            }
        }
        // انتخاب واحد پول.
        item {
            SettingsCard("واحد پول", Icons.Outlined.Payments) {
                CurrencyMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = currency == mode, onClick = { onCurrencyChanged(mode) })
                        Text(mode.label)
                    }
                }
            }
        }
        // بخش اعلان‌ها طبق ساختار عمومی تنظیمات برنامه.
        item {
            SettingsCard("اعلان‌ها", Icons.Outlined.Notifications) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("اطلاع از نسخه جدید")
                        Text("در شروع برنامه وجود آپدیت جدید بررسی شود.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = notificationsEnabled, onCheckedChange = onNotificationsChanged)
                }
            }
        }
    }
}

/** صفحه درباره تیم توسعه. */
@Composable
private fun AboutUsScreen() {
    // متن‌ها عمداً وسط‌چین هستند.
    CenteredInfoPage(Icons.Outlined.Person, "گروه توسعه و برنامه نویسی AS Team") {
        Text("تمامی حقوق مربوط به این برنامه انحصاری میباشد", textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** صفحه تماس با پشتیبانی. */
@Composable
private fun ContactUsScreen() {
    // Context برای باز کردن برنامه ایمیل.
    val context = LocalContext.current
    CenteredInfoPage(Icons.Outlined.Mail, "گروه توسعه و برنامه نویسی AS Team") {
        Text("ایمیل پشتیبانی", fontWeight = FontWeight.Bold)
        Text(SUPPORT_EMAIL, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.primary)
        TextButton(onClick = { openSupportEmail(context) }) { Text("ارسال ایمیل") }
    }
}

/** صفحه درباره نرم‌افزار؛ عمداً نام بسته/Application ID در این UI نمایش داده نمی‌شود. */
@Composable
private fun AboutAppScreen() {
    CenteredInfoPage(Icons.Outlined.Calculate, "حسابیار") {
        // فقط چند خط توضیح کاربردی و نسخه مطابق درخواست نمایش داده می‌شود.
        Text(
            "حسابیار برای محاسبه سریع تخفیف، سود، حاشیه سود، قیمت فروش، درصد، مالیات و مقایسه خرید ساخته شده است.\n\nمحاسبات اصلی به‌صورت آفلاین انجام می‌شوند و تاریخچه روی همان دستگاه ذخیره می‌شود.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("نسخه ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
    }
}

/** قالب مشترک صفحه‌های اطلاعاتی منوی همبرگری. */
@Composable
private fun CenteredInfoPage(icon: ImageVector, title: String, content: @Composable () -> Unit) {
    // کل محتوا در مرکز صفحه قرار می‌گیرد.
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            content()
        }
    }
}

/** عنوان TopAppBar برای هر صفحه سطح اصلی. */
private fun rootPageTitle(page: RootPage): String = when (page) {
    RootPage.HOME -> "حسابیار"
    RootPage.HISTORY -> "تاریخچه"
    RootPage.SETTINGS -> "تنظیمات"
    RootPage.ABOUT_US -> "درباره ما"
    RootPage.CONTACT_US -> "تماس با ما"
    RootPage.ABOUT_APP -> "درباره نرم افزار"
}

/** Share Sheet سیستم را برای معرفی حسابیار باز می‌کند. */
private fun shareApp(context: Context) {
    // Intent متنی با لینک فعلی برنامه ساخته می‌شود.
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "حسابیار؛ ابزار محاسبه تخفیف، سود، درصد و قیمت‌گذاری\n$APP_SHARE_URL")
    }
    // Chooser به کاربر اجازه می‌دهد پیام‌رسان/برنامه مقصد را انتخاب کند.
    runCatching { context.startActivity(Intent.createChooser(shareIntent, "معرفی حسابیار")) }
}

/** برنامه ایمیل دستگاه را با آدرس پشتیبانی باز می‌کند. */
private fun openSupportEmail(context: Context) {
    // mailto باعث می‌شود فقط برنامه‌های سازگار با ایمیل پیشنهاد شوند.
    val emailIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$SUPPORT_EMAIL")).apply {
        putExtra(Intent.EXTRA_SUBJECT, "پشتیبانی حسابیار")
    }
    // نبودن Mail Client باعث Crash نمی‌شود.
    runCatching { context.startActivity(emailIntent) }
}
