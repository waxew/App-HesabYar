package com.waxew.hesabyar

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.waxew.hesabyar.data.CurrencyMode
import com.waxew.hesabyar.data.HistoryEntry
import com.waxew.hesabyar.data.PriceBookRepository
import com.waxew.hesabyar.data.ProRepository
import com.waxew.hesabyar.data.SettingsRepository
import com.waxew.hesabyar.data.ShoppingRepository
import com.waxew.hesabyar.data.ThemeMode
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** نتیجه Restore برای Refresh کردن State رابط کاربری. */
data class RestoreResult(
    val history: List<HistoryEntry>,
    val themeMode: ThemeMode,
    val currencyMode: CurrencyMode,
    val notificationsEnabled: Boolean,
    val profileName: String,
    val profileImageUri: String
)

/** خروجی گرفتن و Backup/Restore حسابیار بدون سرویس آنلاین. */
object DataTransferManager {

    /** تاریخچه را به CSV UTF-8 سازگار با Excel تبدیل می‌کند. */
    fun exportCsv(context: Context, history: List<HistoryEntry>): File {
        val file = exportFile(context, "HesabYar-history.csv")
        file.outputStream().use { output ->
            output.write(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()))
            OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                writer.appendLine("عنوان,جزئیات,نتیجه,زمان")
                history.forEach { entry ->
                    writer.appendLine(listOf(entry.title, entry.details, entry.result, entry.createdAt.toString()).joinToString(",") { csvEscape(it) })
                }
            }
        }
        return file
    }

    /** یک PDF چندصفحه‌ای سبک از تاریخچه می‌سازد. */
    fun exportPdf(context: Context, history: List<HistoryEntry>): File {
        val file = exportFile(context, "HesabYar-report.pdf")
        val document = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 20f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.RIGHT
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 11f; textAlign = Paint.Align.RIGHT }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 9f; textAlign = Paint.Align.RIGHT }
        val rowsPerPage = 24
        val pages = maxOf(1, (history.size + rowsPerPage - 1) / rowsPerPage)

        for (pageIndex in 0 until pages) {
            val page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageIndex + 1).create())
            val canvas = page.canvas
            canvas.drawText("HesabYar ${BuildConfig.VERSION_NAME}", 555f, 48f, titlePaint)
            canvas.drawText("Records: ${history.size}", 555f, 70f, bodyPaint)
            var y = 105f
            history.drop(pageIndex * rowsPerPage).take(rowsPerPage).forEach { entry ->
                canvas.drawText(entry.title.take(28), 555f, y, bodyPaint)
                canvas.drawText(entry.result.take(46), 555f, y + 15f, bodyPaint)
                canvas.drawText(entry.details.take(75), 555f, y + 29f, smallPaint)
                y += 30f
            }
            document.finishPage(page)
        }
        file.outputStream().use(document::writeTo)
        document.close()
        return file
    }

    /** XLSX مینیمال OpenXML می‌سازد تا کتابخانه سنگین Office وارد APK نشود. */
    fun exportXlsx(context: Context, history: List<HistoryEntry>): File {
        val file = exportFile(context, "HesabYar-history.xlsx")
        ZipOutputStream(file.outputStream().buffered()).use { zip ->
            zipXml(zip, "[Content_Types].xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                  <Default Extension="xml" ContentType="application/xml"/>
                  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
                  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
                </Types>
            """.trimIndent())
            zipXml(zip, "_rels/.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
                </Relationships>
            """.trimIndent())
            zipXml(zip, "xl/workbook.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
                  <sheets><sheet name="History" sheetId="1" r:id="rId1"/></sheets>
                </workbook>
            """.trimIndent())
            zipXml(zip, "xl/_rels/workbook.xml.rels", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
                </Relationships>
            """.trimIndent())
            val rows = buildString {
                append(xlsxRow(1, listOf("عنوان", "جزئیات", "نتیجه", "زمان")))
                history.forEachIndexed { index, entry -> append(xlsxRow(index + 2, listOf(entry.title, entry.details, entry.result, entry.createdAt.toString()))) }
            }
            zipXml(zip, "xl/worksheets/sheet1.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>$rows</sheetData></worksheet>
            """.trimIndent())
        }
        return file
    }

    /** Backup کامل داده‌های مهم کاربر را به JSON تبدیل می‌کند. */
    fun createBackup(
        context: Context,
        history: List<HistoryEntry>,
        settings: SettingsRepository,
        shopping: ShoppingRepository,
        priceBook: PriceBookRepository,
        pro: ProRepository? = null
    ): File {
        val historyArray = JSONArray()
        history.forEach { entry ->
            historyArray.put(JSONObject().put("id", entry.id).put("title", entry.title).put("details", entry.details).put("result", entry.result).put("createdAt", entry.createdAt))
        }
        val root = JSONObject()
            .put("schemaVersion", 3)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("createdAt", System.currentTimeMillis())
            .put("history", historyArray)
            .put("settings", JSONObject()
                .put("theme", settings.themeMode.name)
                .put("currency", settings.currencyMode.name)
                .put("notificationsEnabled", settings.notificationsEnabled)
                .put("profileName", settings.profileName)
                .put("profileImageUri", settings.profileImageUri))
            .put("shopping", shopping.exportJson())
            .put("priceBook", priceBook.exportJson())
        pro?.let { root.put("proV3", it.exportJson()) }
        return exportFile(context, "HesabYar-backup.json").apply { writeText(root.toString(2), Charsets.UTF_8) }
    }

    /** Backup JSON را بازیابی و تنظیمات/Repositoryها را به‌روزرسانی می‌کند. */
    fun restoreBackup(
        json: String,
        settings: SettingsRepository,
        shopping: ShoppingRepository,
        priceBook: PriceBookRepository,
        pro: ProRepository? = null
    ): RestoreResult? = runCatching {
        val root = JSONObject(json)
        val historyArray = root.optJSONArray("history") ?: JSONArray()
        val history = buildList {
            for (index in 0 until historyArray.length()) {
                val item = historyArray.getJSONObject(index)
                add(HistoryEntry(
                    id = item.getLong("id"),
                    title = item.optString("title", "محاسبه"),
                    details = item.optString("details", ""),
                    result = item.optString("result", ""),
                    createdAt = item.optLong("createdAt", item.getLong("id"))
                ))
            }
        }.take(100)

        val settingsJson = root.optJSONObject("settings") ?: JSONObject()
        settings.themeMode = runCatching { ThemeMode.valueOf(settingsJson.optString("theme")) }.getOrDefault(ThemeMode.SYSTEM)
        settings.currencyMode = runCatching { CurrencyMode.valueOf(settingsJson.optString("currency")) }.getOrDefault(CurrencyMode.TOMAN)
        settings.notificationsEnabled = settingsJson.optBoolean("notificationsEnabled", true)
        settings.profileName = settingsJson.optString("profileName", "کاربر حسابیار")
        settings.profileImageUri = settingsJson.optString("profileImageUri", "")
        root.optJSONObject("shopping")?.let(shopping::importJson)
        root.optJSONObject("priceBook")?.let(priceBook::importJson)
        root.optJSONObject("proV3")?.let { pro?.importJson(it) }

        RestoreResult(history, settings.themeMode, settings.currencyMode, settings.notificationsEnabled, settings.profileName, settings.profileImageUri)
    }.getOrNull()

    /** فایل cache را با Android Sharesheet ارسال می‌کند. */
    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, title))
    }

    private fun exportFile(context: Context, name: String): File {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(directory, name).apply { if (exists()) delete() }
    }

    private fun csvEscape(value: String): String = "\"${value.replace("\"", "\"\"")}\""

    private fun zipXml(zip: ZipOutputStream, path: String, xml: String) {
        zip.putNextEntry(ZipEntry(path)); zip.write(xml.toByteArray(Charsets.UTF_8)); zip.closeEntry()
    }

    private fun xlsxRow(rowNumber: Int, values: List<String>): String {
        val columns = values.mapIndexed { index, value ->
            val column = ('A'.code + index).toChar()
            "<c r=\"$column$rowNumber\" t=\"inlineStr\"><is><t>${xmlEscape(value)}</t></is></c>"
        }.joinToString("")
        return "<row r=\"$rowNumber\">$columns</row>"
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;")
}
