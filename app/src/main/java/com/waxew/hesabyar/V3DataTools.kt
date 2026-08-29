package com.waxew.hesabyar

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.waxew.hesabyar.data.PriceRecord
import com.waxew.hesabyar.data.SavedInvoice
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.SecureRandom
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** ابزارهای انتقال/امنیت داده نسخه 3. */
object V3DataTools {
    private val random = SecureRandom()
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val MAGIC = "HYB3"

    /** Backup JSON را با AES-256-GCM و کلید مشتق‌شده از رمز کاربر رمزگذاری می‌کند. */
    fun encryptBackup(context: Context, json: String, password: CharArray): File? = runCatching {
        require(password.size >= 4) { "Password too short" }
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(json.toByteArray(Charsets.UTF_8))
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        File(directory, "HesabYar-secure-backup.hybak").apply {
            outputStream().use { out ->
                out.write(MAGIC.toByteArray(Charsets.US_ASCII)); out.write(salt); out.write(iv); out.write(encrypted)
            }
        }
    }.getOrNull()

    /** Backup رمزدار را باز می‌کند؛ رمز اشتباه یا فایل مخدوش null برمی‌گرداند. */
    fun decryptBackup(bytes: ByteArray, password: CharArray): String? = runCatching {
        require(bytes.size > 32)
        val input = ByteArrayInputStream(bytes)
        val magic = ByteArray(4).also { input.read(it) }.toString(Charsets.US_ASCII)
        require(magic == MAGIC)
        val salt = ByteArray(16).also { input.read(it) }
        val iv = ByteArray(12).also { input.read(it) }
        val encrypted = input.readBytes()
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
        cipher.doFinal(encrypted).toString(Charsets.UTF_8)
    }.getOrNull()

    /** CSV محصولات با ستون‌های نام، قیمت، مقدار و واحد را به PriceRecord تبدیل می‌کند. */
    fun parsePriceCsv(text: String): List<PriceRecord> {
        val lines = text.removePrefix("\uFEFF").lineSequence().filter { it.isNotBlank() }.toList()
        return lines.dropWhile { line ->
            val lower = line.lowercase()
            lower.contains("name") || lower.contains("product") || lower.contains("نام")
        }.mapNotNull { line ->
            val cols = parseCsvLine(line)
            val name = cols.getOrNull(0)?.trim().orEmpty()
            val price = cols.getOrNull(1)?.sanitizeNumericInput()?.toDoubleOrNull()
            val quantity = cols.getOrNull(2)?.sanitizeNumericInput()?.toDoubleOrNull() ?: 1.0
            val unit = cols.getOrNull(3)?.trim().orEmpty().ifBlank { "عدد" }
            if (name.isBlank() || price == null || price < 0.0 || quantity <= 0.0) null
            else PriceRecord(System.currentTimeMillis() + name.hashCode(), name, price, quantity, unit)
        }.take(1000)
    }

    /** XLSX ساده را از sheet1.xml می‌خواند؛ برای Import فایل‌های چهارستونه کافی است. */
    fun parsePriceXlsx(bytes: ByteArray): List<PriceRecord> = runCatching {
        var sheetXml = ""
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "xl/worksheets/sheet1.xml") {
                    sheetXml = zip.readBytes().toString(Charsets.UTF_8)
                    break
                }
            }
        }
        val rows = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL).findAll(sheetXml).map { it.groupValues[1] }
        rows.mapNotNull { row ->
            val values = Regex("<t[^>]*>(.*?)</t>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .findAll(row).map { xmlUnescape(it.groupValues[1]) }.toList()
            if (values.size < 2) return@mapNotNull null
            val name = values[0].trim()
            val price = values[1].sanitizeNumericInput().toDoubleOrNull()
            val quantity = values.getOrNull(2)?.sanitizeNumericInput()?.toDoubleOrNull() ?: 1.0
            val unit = values.getOrNull(3)?.trim().orEmpty().ifBlank { "عدد" }
            if (name.contains("نام") || name.lowercase().contains("name") || name.isBlank() || price == null || price < 0.0 || quantity <= 0.0) null
            else PriceRecord(System.currentTimeMillis() + name.hashCode(), name, price, quantity, unit)
        }.toList().take(1000)
    }.getOrDefault(emptyList())

    /** PDF فاکتور آفلاین با اطلاعات اصلی مشتری و اقلام. */
    fun exportInvoicePdf(context: Context, invoice: SavedInvoice): File? = runCatching {
        val totals = V3Engine.invoiceTotals(invoice.lines, invoice.discountPercent, invoice.taxPercent) ?: return@runCatching null
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, "HesabYar-invoice-${invoice.id}.pdf")
        val document = PdfDocument()
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD; textAlign = Paint.Align.RIGHT }
        val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; textAlign = Paint.Align.RIGHT }
        var lineIndex = 0
        var pageIndex = 1
        while (lineIndex < invoice.lines.size || pageIndex == 1) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageIndex).create())
            val canvas = page.canvas
            var y = 50f
            canvas.drawText("HesabYar Invoice", 555f, y, title); y += 24f
            canvas.drawText(invoice.title.take(45), 555f, y, body); y += 18f
            if (invoice.customerName.isNotBlank()) { canvas.drawText("Customer: ${invoice.customerName.take(45)}", 555f, y, body); y += 18f }
            if (pageIndex == 1) { canvas.drawText("Items: ${invoice.lines.size}", 555f, y, body); y += 25f }
            var count = 0
            while (lineIndex < invoice.lines.size && count < 28) {
                val l = invoice.lines[lineIndex]
                canvas.drawText("${l.title.take(28)}   ${l.quantity.clean()} x ${l.unitPrice.money()} = ${l.total.money()}", 555f, y, body)
                y += 22f; lineIndex++; count++
            }
            if (lineIndex >= invoice.lines.size) {
                y += 12f
                canvas.drawText("Subtotal: ${totals.subtotal.money()}", 555f, y, body); y += 18f
                canvas.drawText("Discount: ${totals.discountAmount.money()}", 555f, y, body); y += 18f
                canvas.drawText("Tax: ${totals.taxAmount.money()}", 555f, y, body); y += 18f
                canvas.drawText("Final: ${totals.finalTotal.money()}", 555f, y, title)
            }
            document.finishPage(page); pageIndex++
        }
        file.outputStream().use(document::writeTo); document.close(); file
    }.getOrNull()

    /** OCR فاکتور را به ردیف‌های احتمالی محصول/مبلغ تبدیل می‌کند. */
    fun parseStructuredInvoiceText(text: String): List<Pair<String, Double>> {
        return text.lineSequence().mapNotNull { raw ->
            val line = raw.trim()
            if (line.length < 2) return@mapNotNull null
            val matches = Regex("[۰-۹٠-٩0-9][۰-۹٠-٩0-9,٬،.]*").findAll(line).toList()
            val last = matches.lastOrNull() ?: return@mapNotNull null
            val value = last.value.sanitizeNumericInput().toDoubleOrNull() ?: return@mapNotNull null
            val title = line.substring(0, last.range.first).trim(' ', '-', ':', '|')
            if (title.isBlank() || value <= 0.0) null else title.take(60) to value
        }.take(100).toList()
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val bytes = factory.generateSecret(PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)).encoded
        return SecretKeySpec(bytes, "AES")
    }

    private fun parseCsvLine(line: String): List<String> {
        val out = mutableListOf<String>(); val current = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && i + 1 < line.length && line[i + 1] == '"' -> { current.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> { out += current.toString(); current.clear() }
                else -> current.append(c)
            }
            i++
        }
        out += current.toString(); return out
    }

    private fun xmlUnescape(value: String): String = value
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
}
