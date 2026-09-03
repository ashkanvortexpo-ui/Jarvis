package com.jarvis.assistant

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PixelFormat
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures the screen after the user grants Android MediaProjection permission.
 * When Gaming AI is enabled, it periodically sends a compressed screenshot to
 * the configured AI service and reads a concise tactical report aloud.
 *
 * It intentionally does not inject touch/key events or bypass game anti-cheat.
 */
class ScreenCaptureService : Service(), TextToSpeech.OnInitListener {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val ACTION_STOP = "stop_capture"
        @Volatile var latestScreenshot: File? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val analysisExecutor = Executors.newSingleThreadExecutor()
    private val analyzing = AtomicBoolean(false)
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var lastSaved = 0L
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var lastReport = ""

    private val analysisRunnable = object : Runnable {
        override fun run() {
            if (!GamingAiManager.isEnabled(this@ScreenCaptureService)) {
                stopSelf()
                return
            }
            analyzeLatestScreen()
            handler.postDelayed(this, 8000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (projection == null) {
            val code = intent?.getIntExtra(EXTRA_RESULT_CODE, -1) ?: -1
            val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
                intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(EXTRA_RESULT_DATA)
            }
            if (code < 0 || data == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            startForegroundCompat()
            startCapture(code, data)
        }

        if (GamingAiManager.isEnabled(this)) {
            handler.removeCallbacks(analysisRunnable)
            handler.postDelayed(analysisRunnable, 1500L)
        }
        return START_STICKY
    }

    private fun startForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        "jarvis_screen",
                        "Jarvis screen analysis",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
        }
        val n = NotificationCompat.Builder(this, "jarvis_screen")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("تحلیل زنده بازی جارویس فعال است")
            .setContentText("جارویس هر چند ثانیه وضعیت قابل مشاهده بازی را تحلیل می‌کند.")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(7, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(7, n)
        }
    }

    private fun startCapture(code: Int, data: Intent) {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = mgr.getMediaProjection(code, data)
        val m = resources.displayMetrics

        // Limit memory pressure on high-resolution phones.
        val width = minOf(m.widthPixels, 1280)
        val height = (m.heightPixels.toFloat() * width / m.widthPixels).toInt().coerceAtLeast(1)

        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        }, handler)

        virtualDisplay = projection?.createVirtualDisplay(
            "JarvisScreen",
            width,
            height,
            m.densityDpi,
            0,
            reader!!.surface,
            null,
            handler
        )

        reader?.setOnImageAvailableListener({ r ->
            val now = System.currentTimeMillis()
            if (now - lastSaved < 1000L) {
                r.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }

            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride
                val rowPadding = rowStride - pixelStride * width
                val paddedWidth = width + rowPadding / pixelStride

                val raw = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                raw.copyPixelsFromBuffer(plane.buffer)
                val cropped = Bitmap.createBitmap(raw, 0, 0, width, height)
                raw.recycle()

                val file = File(cacheDir, "jarvis_latest_screen.jpg")
                FileOutputStream(file).use {
                    cropped.compress(Bitmap.CompressFormat.JPEG, 68, it)
                }
                cropped.recycle()

                latestScreenshot = file
                lastSaved = now
            } catch (_: Exception) {
            } finally {
                image.close()
            }
        }, handler)
    }

    private fun analyzeLatestScreen() {
        val file = latestScreenshot ?: return
        if (!file.exists() || !analyzing.compareAndSet(false, true)) return

        val apiKey = PrefsHelper.getApiKey(this)
        if (apiKey.isBlank()) {
            analyzing.set(false)
            speakOnce("برای تحلیل بازی، کلید هوش مصنوعی را در تنظیمات وارد کن.")
            GamingAiManager.disable(this)
            return
        }

        val bytes = try {
            file.readBytes()
        } catch (_: Exception) {
            analyzing.set(false)
            return
        }

        analysisExecutor.execute {
            try {
                val prompt = """
                    این تصویر، یک فریم از صفحه یک بازی است.
                    فقط چیزهایی را که واقعاً در تصویر قابل مشاهده‌اند تحلیل کن.
                    بازی و رابط آن را تا حد ممکن تشخیص بده.
                    وضعیت بازیکن، دشمن/تهدید قابل مشاهده، هدف فعلی و بهترین اقدام بعدی را بررسی کن.
                    اگر اطلاعات کافی نیست، حدس قطعی نزن.
                    فقط یک گزارش خیلی کوتاه فارسی، حداکثر 2 جمله بده.
                    هیچ دستور تقلب، دورزدن ضدتقلب یا سوءاستفاده ارائه نکن.
                """.trimIndent()

                val reply = GeminiClient(apiKey).askImage(prompt, bytes).trim()
                if (reply.isNotBlank() && reply != lastReport) {
                    lastReport = reply
                    handler.post { speakOnce(reply) }
                }
            } catch (_: Exception) {
            } finally {
                analyzing.set(false)
            }
        }
    }

    private fun speakOnce(text: String) {
        if (!ttsReady || text.isBlank()) return
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_game_report")
        } catch (_: Exception) {
        }
    }

    override fun onInit(status: Int) {
        ttsReady = status == TextToSpeech.SUCCESS
        if (ttsReady) {
            tts?.language = Locale("fa", "IR")
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        analysisExecutor.shutdownNow()
        reader?.close()
        virtualDisplay?.release()
        projection?.stop()
        projection = null
        latestScreenshot = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
