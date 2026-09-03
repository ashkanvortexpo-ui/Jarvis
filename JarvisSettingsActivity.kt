package com.jarvis.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class JarvisSettingsActivity : AppCompatActivity() {
    private lateinit var profileText: TextView
    private lateinit var themeText: TextView
    private lateinit var hologramSwitch: Switch
    private lateinit var responseBar: SeekBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_jarvis_settings)

        findViewById<android.widget.Button>(com.jarvis.assistant.R.id.gaming_ai_button)
            ?.setOnClickListener {
                GamingAiManager.enable(this)
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_START_GAMING_CAPTURE, true)
                )
                Toast.makeText(
                    this,
                    "حالت بازی هوشمند فعال شد؛ اجازه تحلیل صفحه را تأیید کن.",
                    Toast.LENGTH_SHORT
                ).show()
            }


        profileText = findViewById(R.id.profile_text)
        themeText = findViewById(R.id.theme_text)
        hologramSwitch = findViewById(R.id.switch_hologram)
        responseBar = findViewById(R.id.seek_response)

        hologramSwitch.isChecked = JarvisSettings.getBool(this, "hologram_enabled", true)
        responseBar.progress = JarvisSettings.getInt(this, "response_length", 1)
        profileText.text = "پروفایل فعلی: ${JarvisSettings.profile(this)}"
        themeText.text = "ظاهر: ${JarvisSettings.get(this, "theme", "فیروزه‌ای")}"

        hologramSwitch.setOnCheckedChangeListener { _, checked ->
            JarvisSettings.putBool(this, "hologram_enabled", checked)
        }
        responseBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) JarvisSettings.putInt(this@JarvisSettingsActivity, "response_length", progress)
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        findViewById<Button>(R.id.btn_profiles).setOnClickListener { chooseProfile() }
        findViewById<Button>(R.id.btn_theme).setOnClickListener { chooseTheme() }
        findViewById<Button>(R.id.btn_voice).setOnClickListener { chooseVoiceSettings() }
        findViewById<Button>(R.id.btn_android_settings).setOnClickListener {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        findViewById<Button>(R.id.btn_permissions).setOnClickListener { showPermissions() }
        findViewById<Button>(R.id.btn_device_info).setOnClickListener { showDeviceInfo() }
        findViewById<Button>(R.id.btn_editor).setOnClickListener {
            startActivity(Intent(this, SettingsEditorActivity::class.java))
        }
        findViewById<Button>(R.id.btn_gaming).setOnClickListener { showGaming() }
        findViewById<Button>(R.id.btn_automation).setOnClickListener { showAutomation() }
        findViewById<Button>(R.id.btn_diagnostics).setOnClickListener { startActivity(Intent(this, DiagnosticsActivity::class.java)) }
        findViewById<Button>(R.id.btn_backup).setOnClickListener { backupSettings() }
        findViewById<Button>(R.id.btn_personal_assistant).setOnClickListener {
            AlertDialogBuilder.message(
                this,
                "دستیار شخصی",
                "فرمان‌های نمونه:\n\n" +
                    "یادداشت کن فردا تماس بگیر\n" +
                    "کار خرید را ثبت کن\n" +
                    "یادداشت‌ها را نشان بده\n" +
                    "تایمر 5 دقیقه\n\n" +
                    "یادداشت‌ها و کارهای این بخش به‌صورت محلی روی گوشی ذخیره می‌شوند."
            )
        }
        findViewById<Button>(R.id.btn_accessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.btn_battery).setOnClickListener {
            startActivity(Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS))
        }
        findViewById<Button>(R.id.btn_network).setOnClickListener {
            startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        }
    }

    private fun chooseProfile() {
        val items = arrayOf("عادی", "بازی", "کار", "شب")
        AlertDialogBuilder.simple(this, "انتخاب پروفایل", items) { selected ->
            JarvisSettings.applyProfile(this, selected)
            refresh()
            Toast.makeText(this, "پروفایل «$selected» فعال شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseTheme() {
        val items = arrayOf("فیروزه‌ای", "آبی", "بنفش", "سبز", "قرمز", "سفید")
        AlertDialogBuilder.simple(this, "انتخاب ظاهر", items) { selected ->
            JarvisSettings.put(this, "theme", selected)
            themeText.text = "ظاهر: $selected"
            Toast.makeText(this, "ظاهر ذخیره شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun chooseVoiceSettings() {
        val items = arrayOf("آرام", "عادی", "سریع")
        AlertDialogBuilder.simple(this, "سرعت پاسخ صوتی", items) { selected ->
            JarvisSettings.put(this, "voice_speed", selected)
            Toast.makeText(this, "سرعت «$selected» ذخیره شد", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPermissions() {
        val mic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val camera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        val overlay = Settings.canDrawOverlays(this)
        val notifications = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        AlertDialogBuilder.message(
            this,
            "مرکز مجوزها",
            "میکروفون: ${if (mic) "فعال" else "نیازمند اجازه"}\n" +
                    "دوربین: ${if (camera) "فعال" else "نیازمند اجازه"}\n" +
                    "نمایش روی برنامه‌ها: ${if (overlay) "فعال" else "نیازمند اجازه"}\n" +
                    "اعلان‌ها: ${if (notifications) "فعال" else "نیازمند اجازه"}"
        )
    }

    private fun showDeviceInfo() {
        val metrics = resources.displayMetrics
        val text = """
            مدل: ${Build.MANUFACTURER} ${Build.MODEL}
            اندروید: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
            صفحه: ${metrics.widthPixels}×${metrics.heightPixels}
            تراکم: ${metrics.density}
            معماری: ${Build.SUPPORTED_ABIS.joinToString()}
        """.trimIndent()
        AlertDialogBuilder.message(this, "اطلاعات دستگاه", text)
    }

    private fun showGaming() {
        val text = "پروفایل بازی آماده است.\n\n" +
                "• حالت بازی را از بخش پروفایل «بازی» فعال کن.\n" +
                "• برای تحلیل موقعیت بگو: «جارویس آنالیز صفحه».\n" +
                "• جارویس فقط تحلیل و پیشنهاد تاکتیکی می‌دهد و کنترل خودکار بازی انجام نمی‌دهد."
        AlertDialogBuilder.message(this, "مرکز بازی", text)
    }

    private fun showAutomation() {
        val items = arrayOf(
            "با ورود به پروفایل بازی، هولوگرام فعال باشد",
            "در پروفایل شب، هولوگرام خاموش باشد",
            "با اجرای بازی، پروفایل بازی پیشنهاد شود"
        )
        AlertDialogBuilder.simple(this, "اتوماسیون‌های آماده", items) { selected ->
            JarvisSettings.put(this, "automation_last", selected)
            Toast.makeText(this, "قانون ذخیره شد: $selected", Toast.LENGTH_SHORT).show()
        }
    }

    private fun backupSettings() {
        val text = buildString {
            appendLine("JARVIS_SETTINGS")
            appendLine("profile=${JarvisSettings.profile(this@JarvisSettingsActivity)}")
            appendLine("theme=${JarvisSettings.get(this@JarvisSettingsActivity, "theme", "فیروزه‌ای")}")
            appendLine("hologram=${JarvisSettings.getBool(this@JarvisSettingsActivity, "hologram_enabled", true)}")
            appendLine("response_length=${JarvisSettings.getInt(this@JarvisSettingsActivity, "response_length", 1)}")
            appendLine("voice_speed=${JarvisSettings.get(this@JarvisSettingsActivity, "voice_speed", "عادی")}")
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, "ارسال نسخه پشتیبان تنظیمات"))
    }

    private fun refresh() {
        profileText.text = "پروفایل فعلی: ${JarvisSettings.profile(this)}"
        themeText.text = "ظاهر: ${JarvisSettings.get(this, "theme", "فیروزه‌ای")}"
        hologramSwitch.isChecked = JarvisSettings.getBool(this, "hologram_enabled", true)
        responseBar.progress = JarvisSettings.getInt(this, "response_length", 1)
    }
}

object AlertDialogBuilder {
    fun simple(
        activity: android.app.Activity,
        title: String,
        items: Array<String>,
        onSelected: (String) -> Unit
    ) {
        android.app.AlertDialog.Builder(activity)
            .setTitle(title)
            .setItems(items) { _, which -> onSelected(items[which]) }
            .setNegativeButton("لغو", null)
            .show()
    }

    fun message(activity: android.app.Activity, title: String, message: String) {
        android.app.AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("باشه", null)
            .show()
    }
}
