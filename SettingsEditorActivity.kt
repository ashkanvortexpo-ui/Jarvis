package com.jarvis.assistant

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingsEditorActivity : AppCompatActivity() {
    private lateinit var info: TextView
    private lateinit var value: EditText
    private lateinit var keySpinner: Spinner

    private val keys = listOf(
        "screen_brightness",
        "screen_off_timeout",
        "accelerometer_rotation",
        "font_scale"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings_editor)

        info = findViewById(R.id.editor_info)
        value = findViewById(R.id.editor_value)
        keySpinner = findViewById(R.id.editor_key)

        keySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            keys
        )

        findViewById<Button>(R.id.btn_read_setting).setOnClickListener { readSetting() }
        findViewById<Button>(R.id.btn_write_permission).setOnClickListener {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                })
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
        findViewById<Button>(R.id.btn_write_setting).setOnClickListener { writeSetting() }
        findViewById<Button>(R.id.btn_back_editor).setOnClickListener { finish() }
    }

    private fun selectedKey(): String = keySpinner.selectedItem?.toString() ?: keys.first()

    private fun readSetting() {
        val key = selectedKey()
        val result = try {
            when (key) {
                "screen_brightness" -> Settings.System.getInt(contentResolver, key).toString()
                "screen_off_timeout" -> Settings.System.getInt(contentResolver, key).toString()
                "accelerometer_rotation" -> Settings.System.getInt(contentResolver, key).toString()
                "font_scale" -> Settings.System.getFloat(contentResolver, key).toString()
                else -> "خوانده نشد"
            }
        } catch (e: Exception) {
            "دسترسی یا مقدار قابل خواندن نیست"
        }
        value.setText(result)
        info.text = "کلید: $key\nمقدار فعلی: $result"
    }

    private fun writeSetting() {
        val key = selectedKey()
        val raw = value.text.toString().trim()
        if (!Settings.System.canWrite(this)) {
            info.text = "ابتدا اجازه «تغییر تنظیمات سیستم» را فعال کن."
            return
        }
        try {
            val ok = when (key) {
                "screen_brightness", "screen_off_timeout", "accelerometer_rotation" ->
                    Settings.System.putInt(contentResolver, key, raw.toInt())
                "font_scale" ->
                    Settings.System.putFloat(contentResolver, key, raw.toFloat())
                else -> false
            }
            info.text = if (ok) "تنظیم با موفقیت ذخیره شد." else "ذخیره انجام نشد."
        } catch (_: Exception) {
            info.text = "مقدار نامعتبر است."
        }
    }
}
