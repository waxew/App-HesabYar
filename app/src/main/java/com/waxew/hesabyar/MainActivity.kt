package com.waxew.hesabyar

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat

/**
 * Activity اصلی حسابیار.
 * علاوه بر اجرای Compose، مقصدی که از Widget آمده را به Shell منتقل می‌کند.
 */
class MainActivity : ComponentActivity() {

    // نام ابزار ورودی در State نگه داشته می‌شود تا Intent جدید هم بدون ساخت معماری ناوبری جدا اعمال شود.
    private var initialToolName by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // محتوای برنامه اجازه دارد زیر System Barها رسم شود و SafeDrawing داخل Compose فاصله لازم را می‌دهد.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // اگر برنامه از Widget باز شده باشد، ابزار مقصد از Intent خوانده می‌شود.
        initialToolName = intent.getStringExtra(EXTRA_TOOL)

        // Root UI برنامه با مقصد اختیاری Widget اجرا می‌شود.
        setContent { HesabYarApp(initialToolName = initialToolName) }
    }

    /** وقتی Activity موجود دوباره از Widget باز شود مقصد تازه اعمال می‌شود. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        initialToolName = intent.getStringExtra(EXTRA_TOOL)
    }

    companion object {
        // کلید عمومی Intent برای باز کردن مستقیم ماشین‌حساب از Widget.
        const val EXTRA_TOOL = "hesabyar_tool"
    }
}
