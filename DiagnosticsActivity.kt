package com.jarvis.assistant

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class DiagnosticsActivity : AppCompatActivity() {
    private lateinit var report: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_diagnostics)
        report = findViewById(R.id.diagnostics_report)
        findViewById<Button>(R.id.btn_run_diagnostics).setOnClickListener { runChecks() }
        findViewById<Button>(R.id.btn_diagnostics_close).setOnClickListener { finish() }
        runChecks()
    }

    private fun runChecks() {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        val notifications = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val speech = packageManager.resolveActivity(
            android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            PackageManager.MATCH_DEFAULT_ONLY
        ) != null

        report.text = buildString {
            appendLine("گزارش سلامت جارویس")
            appendLine()
            appendLine("میکروفون: ${if (mic) "آماده" else "نیاز به اجازه"}")
            appendLine("دوربین: ${if (camera) "آماده" else "اختیاری"}")
            appendLine("هولوگرام: ${if (overlay) "آماده" else "نیاز به اجازه"}")
            appendLine("اعلان‌ها: ${if (notifications) "آماده" else "نیاز به اجازه"}")
            appendLine("تشخیص گفتار: ${if (speech) "در دسترس" else "در این دستگاه در دسترس نیست"}")
            appendLine("نسخه اندروید: ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
            appendLine()
            appendLine("این بررسی خطاهای پیکربندی رایج را قبل از اجرای سرویس مشخص می‌کند.")
        }
    }
}
