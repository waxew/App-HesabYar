@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.waxew.hesabyar

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.waxew.hesabyar.data.*
import com.waxew.hesabyar.ui.theme.HesabYarTheme
import com.waxew.hesabyar.update.UpdateChecker
import com.waxew.hesabyar.update.UpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** مقصدهای اصلی خارج از ماشین‌حساب‌ها. */
enum class RootPage {
    HOME, BUYER, SELLER, PRICE_BOOK, SCANNER, REPORTS, DATA_TOOLS,
    HISTORY, SETTINGS, ABOUT_US, CONTACT_US, ABOUT_APP
}

/** ابزارهای محاسبات پایه؛ Widget هم از همین enum استفاده می‌کند. */
enum class CalculatorKind { DISCOUNT, PROFIT, TARGET_PRICE, PERCENTAGE, CHANGE, TAX, COMPARE, BREAK_EVEN }

private data class ToolCard(val kind: CalculatorKind, val title: String, val subtitle: String, val icon: ImageVector)

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

/** Root برنامه؛ Repositoryهای محلی، Theme و بررسی نسخه جدید را نگه می‌دارد. */
@Composable
fun HesabYarApp(initialToolName: String? = null) {
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val historyRepository = remember { HistoryRepository(context) }

    var themeMode by remember { mutableStateOf(settings.themeMode) }
    var currency by remember { mutableStateOf(settings.currencyMode) }
    var notificationsEnabled by remember { mutableStateOf(settings.notificationsEnabled) }
    var profileName by remember { mutableStateOf(settings.profileName) }
    var profileImageUri by remember { mutableStateOf(settings.profileImageUri) }
    val history = remember { mutableStateListOf<HistoryEntry>().apply { addAll(historyRepository.load()) } }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }

    LaunchedEffect(notificationsEnabled) {
        updateInfo = if (notificationsEnabled) withContext(Dispatchers.IO) { UpdateChecker.check() } else null
    }

    HesabYarTheme(themeMode) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            HesabYarShell(
                history = history,
                currency = currency,
                onHistoryChanged = { historyRepository.save(history) },
                themeMode = themeMode,
                onThemeModeChanged = { themeMode = it; settings.themeMode = it },
                onCurrencyChanged = { currency = it; settings.currencyMode = it },
                notificationsEnabled = notificationsEnabled,
                onNotificationsChanged = { notificationsEnabled = it; settings.notificationsEnabled = it },
                settingsRepository = settings,
                profileName = profileName,
                profileImageUri = profileImageUri,
                onProfileNameChanged = { profileName = it; settings.profileName = it },
                onProfileImageChanged = { profileImageUri = it; settings.profileImageUri = it },
                initialToolName = initialToolName
            )

            updateInfo?.let { info ->
                AlertDialog(
                    onDismissRequest = { updateInfo = null },
                    icon = { Icon(Icons.Outlined.SystemUpdateAlt, null) },
                    title = { Text("نسخه ${info.versionName} آماده است") },
                    text = { Text(info.notes) },
                    confirmButton = {
                        TextButton(onClick = {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.downloadUrl))) }
                            updateInfo = null
                        }) { Text("دریافت آپدیت") }
                    },
                    dismissButton = { TextButton(onClick = { updateInfo = null }) { Text("بعداً") } }
                )
            }
        }
    }
}

/** Shell مرکزی؛ Drawer، BottomBar، ابزار فعال و Back سیستم را کنترل می‌کند. */
@Composable
private fun HesabYarShell(
    history: SnapshotStateList<HistoryEntry>,
    currency: CurrencyMode,
    onHistoryChanged: () -> Unit,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onCurrencyChanged: (CurrencyMode) -> Unit,
    notificationsEnabled: Boolean,
    onNotificationsChanged: (Boolean) -> Unit,
    settingsRepository: SettingsRepository,
    profileName: String,
    profileImageUri: String,
    onProfileNameChanged: (String) -> Unit,
    onProfileImageChanged: (String) -> Unit,
    initialToolName: String?
) {
    var pageName by rememberSaveable { mutableStateOf(RootPage.HOME.name) }
    var selectedToolName by rememberSaveable { mutableStateOf(initialToolName?.takeIf { runCatching { CalculatorKind.valueOf(it) }.isSuccess }) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val page = runCatching { RootPage.valueOf(pageName) }.getOrDefault(RootPage.HOME)
    val activeTool = selectedToolName?.let { runCatching { CalculatorKind.valueOf(it) }.getOrNull() }
    val title = activeTool?.let { kind -> tools.firstOrNull { it.kind == kind }?.title } ?: rootPageTitle(page)

    LaunchedEffect(initialToolName) {
        initialToolName?.takeIf { runCatching { CalculatorKind.valueOf(it) }.isSuccess }?.let {
            pageName = RootPage.HOME.name
            selectedToolName = it
        }
    }

    fun saveHistory(titleValue: String, details: String, result: String) {
        history.add(0, HistoryEntry(System.currentTimeMillis(), titleValue, details, result))
        while (history.size > 100) history.removeAt(history.lastIndex)
        onHistoryChanged()
    }

    fun navigate(destination: RootPage) {
        selectedToolName = null
        pageName = destination.name
    }

    BackHandler(enabled = drawerState.isOpen || activeTool != null || page != RootPage.HOME) {
        when {
            drawerState.isOpen -> scope.launch { drawerState.close() }
            activeTool != null -> selectedToolName = null
            else -> pageName = RootPage.HOME.name
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = activeTool == null,
        drawerContent = {
            AppDrawer(
                currentPage = page,
                profileName = profileName,
                profileImageUri = profileImageUri,
                onProfileNameChanged = onProfileNameChanged,
                onProfileImageChanged = onProfileImageChanged,
                onNavigate = { navigate(it); scope.launch { drawerState.close() } },
                onShare = { shareApp(LocalContext.current); scope.launch { drawerState.close() } }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        if (activeTool != null) {
                            IconButton(onClick = { selectedToolName = null }) { Icon(Icons.Outlined.ArrowForward, "بازگشت") }
                        } else {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(Icons.Outlined.Menu, "منوی همبرگری") }
                        }
                    }
                )
            },
            bottomBar = {
                if (activeTool == null && page in setOf(RootPage.HOME, RootPage.REPORTS, RootPage.HISTORY, RootPage.SETTINGS)) {
                    NavigationBar {
                        NavigationBarItem(page == RootPage.HOME, { navigate(RootPage.HOME) }, { Icon(Icons.Outlined.Home, null) }, label = { Text("خانه") })
                        NavigationBarItem(page == RootPage.REPORTS, { navigate(RootPage.REPORTS) }, { Icon(Icons.Outlined.BarChart, null) }, label = { Text("گزارش") })
                        NavigationBarItem(page == RootPage.HISTORY, { navigate(RootPage.HISTORY) }, { Icon(Icons.Outlined.History, null) }, label = { Text("تاریخچه") })
                        NavigationBarItem(page == RootPage.SETTINGS, { navigate(RootPage.SETTINGS) }, { Icon(Icons.Outlined.Settings, null) }, label = { Text("تنظیمات") })
                    }
                }
            }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (activeTool != null) CalculatorScreen(activeTool, currency, ::saveHistory)
                else when (page) {
                    RootPage.HOME -> HomeScreen(currency, { selectedToolName = it.name }, ::navigate)
                    RootPage.BUYER -> BuyerAssistantScreen(currency)
                    RootPage.SELLER -> SellerAssistantScreen(currency, ::saveHistory)
                    RootPage.PRICE_BOOK -> PriceBookScreen(currency)
                    RootPage.SCANNER -> ScannerScreen()
                    RootPage.REPORTS -> ReportsScreen(history)
                    RootPage.DATA_TOOLS -> DataToolsScreen(history, settingsRepository) { restored ->
                        history.clear(); history.addAll(restored.history); onHistoryChanged()
                        onThemeModeChanged(restored.themeMode)
                        onCurrencyChanged(restored.currencyMode)
                        onNotificationsChanged(restored.notificationsEnabled)
                        onProfileNameChanged(restored.profileName)
                        onProfileImageChanged(restored.profileImageUri)
                    }
                    RootPage.HISTORY -> HistoryScreen(history, onHistoryChanged)
                    RootPage.SETTINGS -> SettingsScreen(themeMode, currency, notificationsEnabled, onThemeModeChanged, onCurrencyChanged, onNotificationsChanged)
                    RootPage.ABOUT_US -> AboutUsScreen()
                    RootPage.CONTACT_US -> ContactUsScreen()
                    RootPage.ABOUT_APP -> AboutAppScreen()
                }
            }
        }
    }
}

/** Drawer راست‌به‌چپ: پروفایل، ابزارهای اختصاصی و اطلاعات/ارتباط. */
@Composable
private fun AppDrawer(
    currentPage: RootPage,
    profileName: String,
    profileImageUri: String,
    onProfileNameChanged: (String) -> Unit,
    onProfileImageChanged: (String) -> Unit,
    onNavigate: (RootPage) -> Unit,
    onShare: () -> Unit
) {
    val context = LocalContext.current
    var showNameDialog by rememberSaveable { mutableStateOf(false) }
    var nameDraft by rememberSaveable(profileName) { mutableStateOf(profileName) }
    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            onProfileImageChanged(uri.toString())
        }
    }
    val profileBitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, profileImageUri) {
        value = if (profileImageUri.isBlank()) null else withContext(Dispatchers.IO) {
            runCatching { context.contentResolver.openInputStream(Uri.parse(profileImageUri))?.use { BitmapFactory.decodeStream(it)?.asImageBitmap() } }.getOrNull()
        }
    }

    ModalDrawerSheet {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
            Column(
                Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(92.dp).clip(CircleShape).clickable { imageLauncher.launch(arrayOf("image/*")) }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (profileBitmap != null) Image(profileBitmap!!, "تصویر پروفایل", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Outlined.Person, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
                Text("برای تغییر عکس لمس کن", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { nameDraft = profileName; showNameDialog = true }) {
                    Icon(Icons.Outlined.AccountCircle, null); Spacer(Modifier.width(6.dp)); Text(profileName, fontWeight = FontWeight.Bold)
                }
            }
            HorizontalDivider()

            DrawerDestination("خانه", Icons.Outlined.Home, currentPage == RootPage.HOME) { onNavigate(RootPage.HOME) }
            DrawerDestination("دستیار خرید", Icons.Outlined.ShoppingCart, currentPage == RootPage.BUYER) { onNavigate(RootPage.BUYER) }
            DrawerDestination("دستیار فروشنده", Icons.Outlined.Storefront, currentPage == RootPage.SELLER) { onNavigate(RootPage.SELLER) }
            DrawerDestination("دفترچه قیمت", Icons.Outlined.PriceChange, currentPage == RootPage.PRICE_BOOK) { onNavigate(RootPage.PRICE_BOOK) }
            DrawerDestination("اسکن قیمت و بارکد", Icons.Outlined.DocumentScanner, currentPage == RootPage.SCANNER) { onNavigate(RootPage.SCANNER) }
            DrawerDestination("گزارش‌ها", Icons.Outlined.BarChart, currentPage == RootPage.REPORTS) { onNavigate(RootPage.REPORTS) }
            DrawerDestination("پشتیبان‌گیری و خروجی", Icons.Outlined.Backup, currentPage == RootPage.DATA_TOOLS) { onNavigate(RootPage.DATA_TOOLS) }
            DrawerDestination("تنظیمات", Icons.Outlined.Settings, currentPage == RootPage.SETTINGS) { onNavigate(RootPage.SETTINGS) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            NavigationDrawerItem(label = { Text("معرفی به دوستان") }, icon = { Icon(Icons.Outlined.Share, null) }, selected = false, onClick = onShare)
            DrawerDestination("درباره ما", Icons.Outlined.Groups, currentPage == RootPage.ABOUT_US) { onNavigate(RootPage.ABOUT_US) }
            DrawerDestination("تماس با ما", Icons.Outlined.ContactSupport, currentPage == RootPage.CONTACT_US) { onNavigate(RootPage.CONTACT_US) }
            DrawerDestination("درباره نرم‌افزار", Icons.Outlined.Info, currentPage == RootPage.ABOUT_APP) { onNavigate(RootPage.ABOUT_APP) }
        }
    }

    if (showNameDialog) {
        AlertDialog(
            onDismissRequest = { showNameDialog = false },
            title = { Text("نام نمایشی") },
            text = { OutlinedTextField(nameDraft, { nameDraft = it.take(40) }, singleLine = true) },
            confirmButton = { TextButton(onClick = { onProfileNameChanged(nameDraft.ifBlank { "کاربر حسابیار" }); showNameDialog = false }) { Text("ذخیره") } },
            dismissButton = { TextButton(onClick = { showNameDialog = false }) { Text("لغو") } }
        )
    }
}

@Composable
private fun DrawerDestination(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(label = { Text(label) }, icon = { Icon(icon, null) }, selected = selected, onClick = onClick, modifier = Modifier.padding(horizontal = 10.dp))
}

/** صفحه خانه با محاسبه سریع، ابزارهای پایه و دسترسی مستقیم قابلیت‌های نسخه ۲. */
@Composable
private fun HomeScreen(currency: CurrencyMode, onTool: (CalculatorKind) -> Unit, onNavigate: (RootPage) -> Unit) {
    var quickPrice by rememberSaveable { mutableStateOf("") }
    var quickDiscount by rememberSaveable { mutableStateOf("") }
    val price = quickPrice.toNumber()
    val discount = quickDiscount.toNumber()
    val finalPrice = if (price != null && discount != null && discount in 0.0..100.0) price * (1 - discount / 100.0) else null

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(52.dp)) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Calculate, null, tint = MaterialTheme.colorScheme.onPrimary) }
                }
                Spacer(Modifier.width(12.dp))
                Column { Text("حسابیار", fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("دستیار خرید، سود و قیمت‌گذاری", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        item {
            ElevatedCard {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.Bolt, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(8.dp)); Text("محاسبه سریع تخفیف", fontWeight = FontWeight.Bold, fontSize = 18.sp) }
                    NumberField("قیمت اصلی (${currency.label})", quickPrice) { quickPrice = it }
                    NumberField("درصد تخفیف", quickDiscount, "%") { quickDiscount = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(10, 20, 30, 50).forEach { p -> FilterChip(selected = quickDiscount == p.toString(), onClick = { quickDiscount = p.toString() }, label = { Text("$p٪") }, modifier = Modifier.weight(1f)) }
                    }
                    finalPrice?.let { ResultStrip("قیمت نهایی", "${it.money()} ${currency.label}") }
                }
            }
        }

        item { SectionTitle("دستیارهای حرفه‌ای") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureCard("خرید", "بودجه و سبد", Icons.Outlined.ShoppingCart, Modifier.weight(1f)) { onNavigate(RootPage.BUYER) }
                FeatureCard("فروشنده", "قیمت‌گذاری امن", Icons.Outlined.Storefront, Modifier.weight(1f)) { onNavigate(RootPage.SELLER) }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FeatureCard("دفترچه قیمت", "ثبت و مقایسه", Icons.Outlined.PriceChange, Modifier.weight(1f)) { onNavigate(RootPage.PRICE_BOOK) }
                FeatureCard("اسکن", "قیمت و بارکد", Icons.Outlined.DocumentScanner, Modifier.weight(1f)) { onNavigate(RootPage.SCANNER) }
            }
        }

        item { SectionTitle("ماشین‌حساب‌ها") }
        items(tools.chunked(2)) { rowTools ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                rowTools.forEach { tool ->
                    ElevatedCard(onClick = { onTool(tool.kind) }, modifier = Modifier.weight(1f).height(142.dp)) {
                        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                            Icon(tool.icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                            Column { Text(tool.title, fontWeight = FontWeight.Bold, fontSize = 17.sp); Text(tool.subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                if (rowTools.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        item { AssistChip(onClick = { onNavigate(RootPage.DATA_TOOLS) }, label = { Text("نسخه ${BuildConfig.VERSION_NAME} • Widget • Backup • PDF/Excel") }, leadingIcon = { Icon(Icons.Outlined.Verified, null) }) }
    }
}

@Composable
private fun FeatureCard(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    ElevatedCard(onClick = onClick, modifier = modifier.height(110.dp)) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
            Column { Text(title, fontWeight = FontWeight.Bold); Text(subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

/** تاریخچه محلی با تأیید قبل از حذف کامل. */
@Composable
private fun HistoryScreen(history: SnapshotStateList<HistoryEntry>, onChanged: () -> Unit) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column { Text("تاریخچه", fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("آخرین محاسبات روی همین گوشی ذخیره می‌شوند.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (history.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("پاک کردن") }
            }
        }
        if (history.isEmpty()) item { EmptyState(Icons.Outlined.History, "هنوز محاسبه‌ای ذخیره نشده") }
        else items(history, key = { it.id }) { entry ->
            ElevatedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(entry.title, fontWeight = FontWeight.Bold); Text(entry.createdAt.shortDate(), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    Text(entry.details, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(entry.result, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("پاک کردن تاریخچه؟") },
        text = { Text("تمام محاسبات ذخیره‌شده حذف می‌شوند.") },
        confirmButton = { TextButton(onClick = { history.clear(); onChanged(); confirmClear = false }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("لغو") } }
    )
}

/** تنظیمات ظاهر، واحد پول و بررسی نسخه جدید. */
@Composable
private fun SettingsScreen(
    themeMode: ThemeMode,
    currency: CurrencyMode,
    notificationsEnabled: Boolean,
    onTheme: (ThemeMode) -> Unit,
    onCurrency: (CurrencyMode) -> Unit,
    onNotifications: (Boolean) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("تنظیمات", fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("ظاهر، واحد پول و آپدیت حسابیار", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        item { SettingsCard("ظاهر", Icons.Outlined.DarkMode) {
            ThemeMode.entries.forEach { mode -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(themeMode == mode, { onTheme(mode) }); Text(when (mode) { ThemeMode.SYSTEM -> "هماهنگ با گوشی"; ThemeMode.LIGHT -> "روشن"; ThemeMode.DARK -> "تاریک" }) } }
        } }
        item { SettingsCard("واحد پول", Icons.Outlined.Payments) {
            CurrencyMode.entries.forEach { mode -> Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { RadioButton(currency == mode, { onCurrency(mode) }); Text(mode.label) } }
        } }
        item { SettingsCard("نسخه‌های جدید", Icons.Outlined.Notifications) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Switch(notificationsEnabled, onNotifications); Spacer(Modifier.width(10.dp)); Text("بررسی خودکار وجود آپدیت") }
            Text("نسخه فعلی: ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
    }
}

@Composable
private fun AboutUsScreen() = InfoPage("درباره ما", Icons.Outlined.Groups) {
    Text("حسابیار با هدف ساده‌تر کردن تصمیم‌های روزمره خرید، سود و قیمت‌گذاری توسعه داده می‌شود.")
    Text("تمرکز پروژه روی عملکرد آفلاین، رابط فارسی و امکان توسعه بدون از دست رفتن اطلاعات کاربر است.")
}

@Composable
private fun ContactUsScreen() {
    val context = LocalContext.current
    InfoPage("تماس با ما", Icons.Outlined.ContactSupport) {
        Text("برای پیشنهاد، گزارش خطا یا ارتباط با تیم توسعه می‌توانی از ایمیل پشتیبانی استفاده کنی.")
        Button(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:as.team.support@gmail.com"))) } }) { Icon(Icons.Outlined.Email, null); Spacer(Modifier.width(8.dp)); Text("as.team.support@gmail.com") }
        Spacer(Modifier.height(48.dp))
        HorizontalDivider()
        Text("گروه توسعه فناوری و نرم افزاری as Team", fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("as.team.support@gmail.com", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AboutAppScreen() = InfoPage("درباره نرم‌افزار", Icons.Outlined.Info) {
    Text("حسابیار دستیار محاسبات خرید، تخفیف، سود، قیمت‌گذاری و مدیریت سبک قیمت‌هاست.")
    Text("محاسبات و اطلاعات اصلی به‌صورت محلی روی دستگاه نگهداری می‌شوند و برای استفاده روزمره به حساب کاربری نیاز نیست.")
    Text("نسخه ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun InfoPage(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        content()
    }
}

private fun rootPageTitle(page: RootPage): String = when (page) {
    RootPage.HOME -> "حسابیار"; RootPage.BUYER -> "دستیار خرید"; RootPage.SELLER -> "دستیار فروشنده"
    RootPage.PRICE_BOOK -> "دفترچه قیمت"; RootPage.SCANNER -> "اسکن"; RootPage.REPORTS -> "گزارش‌ها"
    RootPage.DATA_TOOLS -> "خروجی و پشتیبان"; RootPage.HISTORY -> "تاریخچه"; RootPage.SETTINGS -> "تنظیمات"
    RootPage.ABOUT_US -> "درباره ما"; RootPage.CONTACT_US -> "تماس با ما"; RootPage.ABOUT_APP -> "درباره نرم‌افزار"
}

/** Sharesheet سیستم برای معرفی برنامه. */
private fun shareApp(context: Context) {
    val text = "حسابیار؛ دستیار خرید، سود و قیمت‌گذاری\nhttps://github.com/waxew/App-HesabYar"
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "معرفی حسابیار"))
    }
}
