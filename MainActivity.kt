package com.jarvis.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    companion object { const val REQ_PERMISSIONS=100; const val REQ_CAPTURE=200
        const val EXTRA_START_GAMING_CAPTURE="start_gaming_capture" }
    private lateinit var toggle: Button
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.btn_grant_overlay).setOnClickListener { requestOverlay() }
        findViewById<Button>(R.id.btn_grant_mic).setOnClickListener { requestPermissionsIfNeeded() }
        toggle=findViewById(R.id.btn_toggle_jarvis); status=findViewById(R.id.tv_status)
        findViewById<Button>(R.id.btn_api_mode).setOnClickListener { showApiSettings() }
        findViewById<Button>(R.id.btn_screen_analysis).setOnClickListener { requestCapture() }
        if (intent.getBooleanExtra(EXTRA_START_GAMING_CAPTURE, false)) requestCapture()
        findViewById<Button>(R.id.btn_advanced_settings).setOnClickListener { startActivity(Intent(this, JarvisSettingsActivity::class.java)) }
        toggle.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) { requestOverlay(); return@setOnClickListener }
            if (ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) { requestPermissionsIfNeeded(); return@setOnClickListener }
            val running=getPreferences(0).getBoolean("running",false)
            if (!running) { ContextCompat.startForegroundService(this,Intent(this,OverlayService::class.java)); getPreferences(0).edit().putBoolean("running",true).apply(); toggle.text=getString(R.string.stop_jarvis); status.text="جارویس آماده‌باش است — بگو «جارویس روشن»" }
            else { stopService(Intent(this,OverlayService::class.java)); getPreferences(0).edit().putBoolean("running",false).apply(); toggle.text=getString(R.string.start_jarvis); status.text="گوش‌به‌زنگ خاموش است" }
        }
    }
    private fun requestOverlay() { if (Build.VERSION.SDK_INT>=23 && !Settings.canDrawOverlays(this)) startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:$packageName"))) else toast("اجازه نمایش فعال است") }
    private fun requestPermissionsIfNeeded() {
        val list=mutableListOf<String>(); if(ContextCompat.checkSelfPermission(this,Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.RECORD_AUDIO)
        if(Build.VERSION.SDK_INT>=33 && ContextCompat.checkSelfPermission(this,Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) list.add(Manifest.permission.POST_NOTIFICATIONS)
        if(list.isEmpty()) toast("مجوزها فعال هستند") else ActivityCompat.requestPermissions(this,list.toTypedArray(),REQ_PERMISSIONS)
    }
    private fun requestCapture(){ val m=getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager; startActivityForResult(m.createScreenCaptureIntent(),REQ_CAPTURE) }
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?){ super.onActivityResult(requestCode,resultCode,data); if(requestCode==REQ_CAPTURE){ if(resultCode==Activity.RESULT_OK && data!=null){ val i=Intent(this,ScreenCaptureService::class.java).apply{putExtra(ScreenCaptureService.EXTRA_RESULT_CODE,resultCode);putExtra(ScreenCaptureService.EXTRA_RESULT_DATA,data)}; ContextCompat.startForegroundService(this,i); toast(if (GamingAiManager.isEnabled(this)) "تحلیل زنده بازی فعال شد" else "تحلیل صفحه فعال شد؛ داخل بازی بگو «جارویس آنالیز صفحه»") } else { if (GamingAiManager.isEnabled(this)) GamingAiManager.disable(this); toast("مجوز تحلیل صفحه لغو شد") } } }
    private fun showApiSettings(){ val input=EditText(this).apply{hint="کلید API";inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD;setSingleLine(true);setText(PrefsHelper.getApiKey(this@MainActivity));setSelection(text.length)}; val box=FrameLayout(this).apply{val p=(20*resources.displayMetrics.density).toInt();setPadding(p,0,p,0);addView(input,ViewGroup.LayoutParams(-1,-2))}; AlertDialog.Builder(this).setTitle("حالت API").setMessage("برای پاسخ آنلاین لازم است؛ کلید روی همین گوشی ذخیره می‌شود.").setView(box).setPositiveButton("ذخیره"){_,_->PrefsHelper.saveApiKey(this,input.text.toString());toast("ذخیره شد")}.setNeutralButton("پاک کردن"){_,_->PrefsHelper.saveApiKey(this,"");toast("پاک شد")}.setNegativeButton("لغو",null).show() }
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
}
