package com.waxew.hesabyar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.waxew.hesabyar.data.HistoryEntry
import com.waxew.hesabyar.data.HistoryRepository

/**
 * Widget صفحه اصلی حسابیار.
 * آخرین نتیجه را نشان می‌دهد و سه میانبر مستقیم برای تخفیف، سود و درصد دارد.
 */
class HesabYarWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val last = HistoryRepository(context).load().firstOrNull()
        appWidgetIds.forEach { id -> manager.updateAppWidget(id, buildViews(context, last)) }
    }

    companion object {
        /** بعد از ذخیره تاریخچه، تمام Widgetهای نصب‌شده بدون انتظار برای Update دوره‌ای تازه می‌شوند. */
        fun updateAll(context: Context, last: HistoryEntry?) {
            val manager = AppWidgetManager.getInstance(context)
            val component = ComponentName(context, HesabYarWidgetProvider::class.java)
            manager.getAppWidgetIds(component).forEach { id -> manager.updateAppWidget(id, buildViews(context, last)) }
        }

        /** RemoteViews به‌دلیل محدودیت Widget از layout XML ساده استفاده می‌کند. */
        private fun buildViews(context: Context, last: HistoryEntry?): RemoteViews {
            return RemoteViews(context.packageName, R.layout.widget_hesabyar).apply {
                setTextViewText(R.id.widget_version, "حسابیار ${BuildConfig.VERSION_NAME}")
                setTextViewText(R.id.widget_last_title, last?.title ?: "آخرین نتیجه")
                setTextViewText(R.id.widget_last_result, last?.result ?: "هنوز نتیجه‌ای ذخیره نشده")
                setOnClickPendingIntent(R.id.widget_discount, toolIntent(context, CalculatorKind.DISCOUNT, 101))
                setOnClickPendingIntent(R.id.widget_profit, toolIntent(context, CalculatorKind.PROFIT, 102))
                setOnClickPendingIntent(R.id.widget_percent, toolIntent(context, CalculatorKind.PERCENTAGE, 103))
                setOnClickPendingIntent(R.id.widget_root, homeIntent(context))
            }
        }

        private fun toolIntent(context: Context, tool: CalculatorKind, requestCode: Int): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivity.EXTRA_TOOL, tool.name)
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        private fun homeIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            return PendingIntent.getActivity(context, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
