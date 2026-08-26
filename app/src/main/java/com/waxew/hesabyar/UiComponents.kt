package com.waxew.hesabyar

// Layoutهای پایه Compose برای چینش عناصر.
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** قالب مشترک تمام ماشین‌حساب‌ها. */
@Composable
fun CalculatorLayout(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    // LazyColumn باعث می‌شود روی نمایشگر کوچک نیز تمام فیلدها قابل دسترسی باشند.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)
    ) {
        // سربرگ محتوایی هر ماشین‌حساب.
        item {
            Text(text = title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(text = subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // بدنه‌ای که صفحه ابزار ارسال کرده است.
        item {
            Column(verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp), content = content)
        }
        // فاصله انتهایی.
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/** فیلد عددی استاندارد حسابیار که اعداد فارسی/عربی/انگلیسی را می‌پذیرد. */
@Composable
fun NumberField(label: String, value: String, suffix: String? = null, onValue: (String) -> Unit) {
    // فیلد ورودی Material 3 رسم می‌شود.
    OutlinedTextField(
        value = value,
        onValueChange = { raw ->
            // ورودی کاربر به فرمت استاندارد عددی تبدیل می‌شود.
            val normalized = raw.sanitizeNumericInput()
            // فقط وقتی حداکثر یک ممیز وجود داشته باشد، State به‌روزرسانی می‌شود.
            if (normalized.count { it == '.' } <= 1) onValue(normalized)
        },
        label = { Text(label) },
        suffix = suffix?.let { suffixText -> { Text(suffixText) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/** نوار کوچک نتیجه برای محاسبه سریع صفحه خانه. */
@Composable
fun ResultStrip(label: String, value: String) {
    // Surface با رنگ Primary Container نتیجه را از فیلدها جدا می‌کند.
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // عنوان نتیجه.
            Text(label)
            // مقدار نتیجه برجسته‌تر است.
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** کارت نتیجه کامل با مقدار اصلی، جزئیات و هشدار اختیاری. */
@Composable
fun ResultCard(primaryLabel: String, primaryValue: String, rows: List<Pair<String, String>>, warning: String? = null) {
    // Card نتیجه با رنگ ملایم نمایش داده می‌شود.
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)
        ) {
            // عنوان خروجی اصلی.
            Text(primaryLabel, color = MaterialTheme.colorScheme.onPrimaryContainer)
            // مقدار اصلی.
            Text(primaryValue, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            // ردیف‌های جزئیات.
            rows.forEach { (label, value) ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                    Text(label)
                    Text(value, fontWeight = FontWeight.SemiBold)
                }
            }
            // هشدار فقط در صورت وجود نمایش داده می‌شود.
            warning?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
        }
    }
}

/** دکمه ذخیره نتیجه در تاریخچه. */
@Composable
fun SaveButton(onClick: () -> Unit) {
    // Button تمام عرض صفحه را می‌گیرد.
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.BookmarkAdd, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("ذخیره در تاریخچه")
    }
}

/** کارت استاندارد بخش‌های تنظیمات. */
@Composable
fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    // ElevatedCard مرزبندی بصری تنظیمات را ایجاد می‌کند.
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            // محتوای اختصاصی تنظیم.
            content()
        }
    }
}

/** عنوان استاندارد بخش‌های صفحه خانه. */
@Composable
fun SectionTitle(text: String) {
    Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold)
}

/** حالت خالی برای تاریخچه یا لیست‌های بدون داده. */
@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** اعداد فارسی/عربی را استاندارد و جداکننده هزارگان را حذف می‌کند. */
fun String.sanitizeNumericInput(): String = buildString {
    // هر کاراکتر ورودی جداگانه بررسی می‌شود.
    this@sanitizeNumericInput.forEach { character ->
        when (character) {
            // ارقام فارسی.
            '۰' -> append('0'); '۱' -> append('1'); '۲' -> append('2'); '۳' -> append('3'); '۴' -> append('4')
            '۵' -> append('5'); '۶' -> append('6'); '۷' -> append('7'); '۸' -> append('8'); '۹' -> append('9')
            // ارقام عربی.
            '٠' -> append('0'); '١' -> append('1'); '٢' -> append('2'); '٣' -> append('3'); '٤' -> append('4')
            '٥' -> append('5'); '٦' -> append('6'); '٧' -> append('7'); '٨' -> append('8'); '٩' -> append('9')
            // ممیز فارسی/عربی.
            '٫' -> append('.')
            // جداکننده‌های هزارگان حذف می‌شوند.
            ',', '٬', '،', ' ', '_' -> Unit
            // نقطه و رقم انگلیسی نگه داشته می‌شوند.
            '.' -> append('.'); in '0'..'9' -> append(character)
            // سایر حروف و نمادها نادیده گرفته می‌شوند.
            else -> Unit
        }
    }
}

/** رشته ورودی را پس از پاک‌سازی به Double تبدیل می‌کند. */
fun String.toNumber(): Double? = sanitizeNumericInput().toDoubleOrNull()

// Symbols ثابت US برای جلوگیری از وابستگی DecimalFormat به Locale دستگاه.
private val decimalSymbols = DecimalFormatSymbols(Locale.US)
// قالب مبالغ بدون اعشار و با جداکننده سه‌رقمی.
private val moneyFormat = DecimalFormat("#,###", decimalSymbols)
// قالب درصدها و خروجی‌های عمومی با حداکثر دو رقم اعشار.
private val decimalFormat = DecimalFormat("#,##0.##", decimalSymbols)

/** عدد مالی را با جداکننده هزارگان نمایش می‌دهد. */
fun Double.money(): String = moneyFormat.format(this)
/** درصد را با حداکثر دو رقم اعشار نمایش می‌دهد. */
fun Double.percent(): String = decimalFormat.format(this)
/** عدد عمومی را بدون صفرهای اعشاری اضافی نمایش می‌دهد. */
fun Double.clean(): String = decimalFormat.format(this)

/** timestamp تاریخچه را به یک تاریخ/ساعت کوتاه تبدیل می‌کند. */
fun Long.shortDate(): String {
    // Formatter در هر فراخوانی ساخته می‌شود تا مشکل Thread Safety نداشته باشد.
    val formatter = SimpleDateFormat("MM/dd  HH:mm", Locale.getDefault())
    // timestamp به Date و سپس String تبدیل می‌شود.
    return formatter.format(Date(this))
}
