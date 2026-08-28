package com.waxew.hesabyar

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material3.*
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

/** قالب مشترک صفحات محاسباتی؛ عنوان، توضیح و بدنه قابل اسکرول را یکدست نگه می‌دارد. */
@Composable
fun CalculatorLayout(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

/**
 * فیلد عددی استاندارد حسابیار.
 * مبالغ هنگام تایپ سه‌رقم سه‌رقم جدا می‌شوند؛ مثال 12000000 -> 12,000,000.
 * مقدار واقعی با [toNumber] بدون کاما برای محاسبه خوانده می‌شود.
 */
@Composable
fun NumberField(
    label: String,
    value: String,
    suffix: String? = null,
    useThousandsSeparator: Boolean = suffix != "%",
    onValue: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValue(formatNumericInputForDisplay(raw, useThousandsSeparator)) },
        label = { Text(label) },
        suffix = suffix?.let { text -> { Text(text) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
}

/** نوار کوچک نتیجه برای محاسبات سریع. */
@Composable
fun ResultStrip(label: String, value: String) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.large) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/** کارت نتیجه کامل با خروجی اصلی، جزئیات و هشدار اختیاری. */
@Composable
fun ResultCard(
    primaryLabel: String,
    primaryValue: String,
    rows: List<Pair<String, String>>,
    warning: String? = null
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(primaryLabel, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(primaryValue, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            rows.forEach { (label, value) ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .2f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label)
                    Text(value, fontWeight = FontWeight.SemiBold)
                }
            }
            warning?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
        }
    }
}

/** دکمه استاندارد ذخیره نتیجه در تاریخچه. */
@Composable
fun SaveButton(onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.BookmarkAdd, contentDescription = null)
        Spacer(Modifier.width(8.dp))
        Text("ذخیره در تاریخچه")
    }
}

/** کارت مشترک تنظیمات/ورودی‌های گروه‌بندی‌شده. */
@Composable
fun SettingsCard(title: String, icon: ImageVector, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
            content()
        }
    }
}

/** تیتر ساده بخش‌ها. */
@Composable
fun SectionTitle(text: String) = Text(text, fontSize = 19.sp, fontWeight = FontWeight.Bold)

/** حالت خالی برای لیست‌ها. */
@Composable
fun EmptyState(icon: ImageVector, text: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * ورودی عدد را برای نمایش فرمت می‌کند. رقم‌های فارسی/عربی پذیرفته می‌شوند و جداکننده قبلی حذف می‌شود.
 * نقطه اعشار حفظ می‌شود؛ بنابراین 12345.5 به 12,345.5 تبدیل می‌شود.
 */
fun formatNumericInputForDisplay(raw: String, useThousandsSeparator: Boolean = true): String {
    val sanitized = raw.sanitizeNumericInput()
    if (sanitized.isEmpty()) return ""

    val firstDot = sanitized.indexOf('.')
    val integerRaw = if (firstDot >= 0) sanitized.substring(0, firstDot) else sanitized
    val fractionRaw = if (firstDot >= 0) sanitized.substring(firstDot + 1).replace(".", "") else ""
    val hasDecimal = firstDot >= 0
    val integerNormalized = integerRaw.trimStart('0').ifEmpty { "0" }
    val integerDisplay = if (useThousandsSeparator) {
        integerNormalized.reversed().chunked(3).joinToString(",").reversed()
    } else integerNormalized

    return if (hasDecimal) "$integerDisplay.$fractionRaw" else integerDisplay
}

/** تبدیل تمام رقم‌های قابل انتظار به فرم عددی انگلیسی بدون جداکننده هزارگان. */
fun String.sanitizeNumericInput(): String = buildString {
    this@sanitizeNumericInput.forEach { ch ->
        when (ch) {
            '۰' -> append('0'); '۱' -> append('1'); '۲' -> append('2'); '۳' -> append('3'); '۴' -> append('4')
            '۵' -> append('5'); '۶' -> append('6'); '۷' -> append('7'); '۸' -> append('8'); '۹' -> append('9')
            '٠' -> append('0'); '١' -> append('1'); '٢' -> append('2'); '٣' -> append('3'); '٤' -> append('4')
            '٥' -> append('5'); '٦' -> append('6'); '٧' -> append('7'); '٨' -> append('8'); '٩' -> append('9')
            '٫' -> append('.')
            ',', '٬', '،', ' ', '_' -> Unit
            '.' -> append('.')
            in '0'..'9' -> append(ch)
            else -> Unit
        }
    }
}

/** رشته نمایشی را به مقدار واقعی Double تبدیل می‌کند. */
fun String.toNumber(): Double? = sanitizeNumericInput().toDoubleOrNull()

private val symbols = DecimalFormatSymbols(Locale.US)
private val moneyFormat = DecimalFormat("#,###", symbols)
private val decimalFormat = DecimalFormat("#,##0.##", symbols)

/** نمایش مبلغ با جداکننده هزارگان. */
fun Double.money(): String = moneyFormat.format(this)
/** نمایش درصد با حداکثر دو رقم اعشار. */
fun Double.percent(): String = decimalFormat.format(this)
/** نمایش عدد عمومی بدون صفرهای اضافی. */
fun Double.clean(): String = decimalFormat.format(this)
/** نمایش زمان کوتاه برای تاریخچه. */
fun Long.shortDate(): String = SimpleDateFormat("MM/dd  HH:mm", Locale.getDefault()).format(Date(this))
