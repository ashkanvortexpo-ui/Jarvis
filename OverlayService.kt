package com.jarvis.assistant

import android.animation.ObjectAnimator
import android.app.*
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.core.app.NotificationCompat
import java.util.Locale

/**
 * سرویس جارویس:
 * - در حالت آماده‌باش برای «جارویس» گوش می‌دهد.
 * - «جارویس روشن» یا «جارویس» => جارویس فعال و هولوگرام روی صفحه می‌ماند.
 * - تا وقتی «خاموش» یا «جارویس خاموش» گفته نشود، هولوگرام حذف نمی‌شود.
 * - بعد از هر پاسخ، دوباره آماده‌ی دریافت دستور بعدی می‌شود.
 */
class OverlayService : Service(), RecognitionListener {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    private lateinit var speechRecognizer: SpeechRecognizer
    private lateinit var recognizerIntent: Intent
    private lateinit var tts: TextToSpeech
    private lateinit var commandProcessor: CommandProcessor

    private val handler = Handler(mainLooper)

    private enum class Mode {
        STANDBY,
        ACTIVE
    }

    private var mode = Mode.STANDBY
    private var isListening = false
    private var restartScheduled = false
    private var destroyed = false
    private var handlingCommand = false
    private var ignoreFinalUntil = 0L

    private val wakeWords = listOf("جارویس", "جارویز", "jarvis")
    private val offWords = listOf("خاموش", "خاموش شو", "غیرفعال", "غیرفعال شو", "خواب")

    companion object {
        const val CHANNEL_ID = "jarvis_channel"
        const val NOTIF_ID = 1
    }

    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        commandProcessor = CommandProcessor(this)

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                try {
                    tts.language = Locale("fa", "IR")
                } catch (_: Exception) {
                    tts.language = Locale.US
                }
            }
        }

        startForeground(NOTIF_ID, buildNotification())
        setupSpeechRecognizer()
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Jarvis",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("جارویس آماده‌باش است")
            .setContentText("بگو «جارویس روشن»")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }

    private fun setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer.setRecognitionListener(this)

        recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fa-IR")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
    }

    /**
     * SpeechRecognizer فقط یک جلسه را هم‌زمان می‌پذیرد.
     * قبل از شروع دوباره، جلسه‌ی قبلی را cancel می‌کنیم و کمی صبر می‌کنیم.
     */
    private fun startListening() {
        if (destroyed || isListening || restartScheduled || handlingCommand) return

        try {
            isListening = true
            speechRecognizer.startListening(recognizerIntent)
        } catch (_: Exception) {
            isListening = false
            scheduleRestart(1000)
        }
    }

    private fun scheduleRestart(delay: Long = 700) {
        if (destroyed || restartScheduled || handlingCommand) return

        restartScheduled = true
        isListening = false

        try {
            speechRecognizer.cancel()
        } catch (_: Exception) {
        }

        handler.postDelayed({
            restartScheduled = false
            if (!destroyed && !handlingCommand) {
                startListening()
            }
        }, delay)
    }

    override fun onPartialResults(partialResults: Bundle?) {
        if (destroyed || handlingCommand) return

        val text = partialResults
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.joinToString(" ")
            ?.lowercase(Locale.ROOT)
            ?: return

        if (mode == Mode.STANDBY && containsWakeWord(text)) {
            activateJarvis()
        } else if (mode == Mode.ACTIVE && containsOffWord(text)) {
            deactivateJarvis()
        }
    }

    override fun onResults(results: Bundle?) {
        isListening = false
        if (destroyed || handlingCommand) return
        if (System.currentTimeMillis() < ignoreFinalUntil) {
            scheduleRestart(500)
            return
        }

        val text = results
            ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.joinToString(" ")
            ?.lowercase(Locale.ROOT)
            ?.trim()
            ?: ""

        handleRecognizedText(text)
    }

    private fun handleRecognizedText(text: String) {
        if (text.isBlank()) {
            scheduleRestart(500)
            return
        }

        when (mode) {
            Mode.STANDBY -> {
                if (containsWakeWord(text)) {
                    activateJarvis()
                } else {
                    scheduleRestart(500)
                }
            }

            Mode.ACTIVE -> {
                if (containsOffWord(text)) {
                    deactivateJarvis()
                    return
                }

                val cleaned = stripWakeWord(text)

                // «جارویس» یا «جارویس روشن» فقط فعال‌سازی است و دستور دیگری ندارد.
                if (cleaned.isBlank() || isOnPhrase(cleaned)) {
                    updateStatusText("جارویس فعال است — بگو چه کاری انجام دهم")
                    speak("فعال شدم")
                    scheduleRestart(900)
                    return
                }

                handlingCommand = true
                updateStatusText("در حال پردازش...")

                commandProcessor.process(cleaned) { reply ->
                    if (destroyed) return@process

                    handlingCommand = false

                    // هولوگرام عمداً پنهان نمی‌شود.
                    // فقط با «خاموش» از صفحه می‌رود.
                    updateStatusText("جارویس فعال است — آماده‌ام")

                    speak(reply)

                    scheduleRestart(1100)
                }
            }
        }
    }

    private fun isOnPhrase(text: String): Boolean {
        val normalized = text
            .replace("‌", " ")
            .replace("  ", " ")
            .trim()

        return normalized == "روشن" ||
            normalized == "فعال" ||
            normalized == "روشن شو" ||
            normalized == "فعال شو"
    }

    private fun containsWakeWord(text: String): Boolean =
        wakeWords.any { text.contains(it) }

    private fun containsOffWord(text: String): Boolean =
        offWords.any { text.contains(it) }

    private fun stripWakeWord(text: String): String {
        var result = text
        wakeWords.forEach {
            result = result.replace(it, "", ignoreCase = true)
        }
        return result.trim()
    }

    private fun activateJarvis() {
        if (destroyed || mode == Mode.ACTIVE) return

        ignoreFinalUntil = System.currentTimeMillis() + 1400L
        mode = Mode.ACTIVE
        showOverlay()
        updateStatusText("جارویس فعال است — آماده‌ام")
        speak("جارویس فعال شد")

        try {
            speechRecognizer.stopListening()
        } catch (_: Exception) {
        }

        isListening = false
        scheduleRestart(1100)
    }

    private fun deactivateJarvis() {
        if (destroyed) return

        ignoreFinalUntil = System.currentTimeMillis() + 1400L
        mode = Mode.STANDBY
        handlingCommand = false
        updateStatusText("جارویس در حالت آماده‌باش است")
        speak("جارویس خاموش شد")
        hideOverlay()
        scheduleRestart(1100)
    }

    override fun onError(error: Int) {
        isListening = false
        if (destroyed || handlingCommand) return

        // خطاهای رایج مثل سکوت/timeout طبیعی‌اند.
        scheduleRestart(900)
    }

    override fun onReadyForSpeech(params: Bundle?) {}
    override fun onBeginningOfSpeech() {}
    override fun onRmsChanged(rmsdB: Float) {}
    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onEndOfSpeech() {
        isListening = false
    }

    override fun onEvent(eventType: Int, params: Bundle?) {}

    private fun speak(text: String) {
        if (destroyed) return

        try {
            tts.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "jarvis_utterance"
            )
        } catch (_: Exception) {
        }
    }

    private fun showOverlay() {
        if (destroyed || overlayView != null) return

        overlayView = LayoutInflater.from(this)
            .inflate(R.layout.overlay_jarvis, null)

        val layoutFlag =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        params.y = 48

        try {
            windowManager.addView(overlayView, params)
        } catch (_: Exception) {
            overlayView = null
            return
        }

        // ورود نرم و هولوگرافیک
        overlayView?.scaleX = 0.35f
        overlayView?.scaleY = 0.35f
        overlayView?.alpha = 0f
        overlayView?.animate()
            ?.scaleX(1f)
            ?.scaleY(1f)
            ?.alpha(1f)
            ?.setDuration(420)
            ?.setInterpolator(DecelerateInterpolator())
            ?.start()
    }

    private fun hideOverlay() {
        val view = overlayView ?: return

        overlayView = null

        view.animate()
            .scaleX(0.35f)
            .scaleY(0.35f)
            .alpha(0f)
            .setDuration(280)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                try {
                    windowManager.removeView(view)
                } catch (_: Exception) {
                }
            }
            .start()
    }

    private fun updateStatusText(text: String) {
        overlayView
            ?.findViewById<TextView>(R.id.jarvis_status_text)
            ?.text = text
    }

    override fun onDestroy() {
        destroyed = true
        handler.removeCallbacksAndMessages(null)

        try {
            speechRecognizer.cancel()
            speechRecognizer.destroy()
        } catch (_: Exception) {
        }

        hideOverlay()

        try {
            tts.stop()
            tts.shutdown()
        } catch (_: Exception) {
        }

        super.onDestroy()
    }
}


class JarvisOrbView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val startNanos = System.nanoTime()

    init { setLayerType(View.LAYER_TYPE_SOFTWARE, null) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) * 0.36f
        val t = (System.nanoTime() - startNanos) / 1_000_000_000f
        val pulse = (kotlin.math.sin(t * 2.4f) + 1f) / 2f

        val glow = RadialGradient(cx, cy, r * 0.95f,
            intArrayOf(Color.argb(210, 0, 220, 255), Color.argb(70, 0, 180, 255), Color.TRANSPARENT),
            floatArrayOf(0f, .42f, 1f), Shader.TileMode.CLAMP)
        fill.shader = glow
        canvas.drawCircle(cx, cy, r * (.82f + pulse * .04f), fill)
        fill.shader = null

        ring(canvas, cx, cy, r * 1.20f, 2.5f, 190)
        ring(canvas, cx, cy, r * 1.06f, 1.6f, 135)
        ring(canvas, cx, cy, r * .91f, 1.2f, 95)

        orbit(canvas, cx, cy, r * 1.10f, r * .38f, t * 42f, 2f, 215)
        orbit(canvas, cx, cy, r * .98f, r * .30f, -t * 30f, 1.5f, 160)
        orbit(canvas, cx, cy, r * .80f, r * .25f, t * 56f, 1.1f, 120)

        for (i in 0 until 8) {
            val angle = Math.toRadians((i * 22.5 + t * 18.0) % 180.0)
            val xScale = kotlin.math.abs(kotlin.math.cos(angle)).toFloat()
            paint.color = Color.argb((80 + 70 * xScale).toInt(), 0, 210, 255)
            paint.strokeWidth = 1.0f
            canvas.drawOval(cx - r * xScale, cy - r, cx + r * xScale, cy + r, paint)
        }

        for (i in -2..2) {
            val y = i * r * .26f
            val half = kotlin.math.sqrt(kotlin.math.max(0f, r * r - y * y))
            paint.color = Color.argb(90, 0, 210, 255)
            paint.strokeWidth = 1f
            canvas.drawOval(cx - half, cy + y - half * .18f, cx + half, cy + y + half * .18f, paint)
        }

        val core = r * (.18f + pulse * .025f)
        val coreGlow = RadialGradient(cx, cy, core * 2.2f,
            intArrayOf(Color.WHITE, Color.rgb(0, 220, 255), Color.TRANSPARENT),
            floatArrayOf(0f, .25f, 1f), Shader.TileMode.CLAMP)
        fill.shader = coreGlow
        canvas.drawCircle(cx, cy, core * 2.2f, fill)
        fill.shader = null
        fill.color = Color.rgb(0, 205, 255)
        canvas.drawCircle(cx, cy, core, fill)

        val a = t * 1.9f
        val px = cx + kotlin.math.cos(a) * r * 1.20f
        val py = cy + kotlin.math.sin(a) * r * .38f
        fill.color = Color.WHITE
        fill.setShadowLayer(12f, 0f, 0f, Color.CYAN)
        canvas.drawCircle(px.toFloat(), py.toFloat(), 3.2f, fill)
        fill.clearShadowLayer()
        postInvalidateOnAnimation()
    }

    private fun ring(canvas: Canvas, cx: Float, cy: Float, radius: Float, width: Float, alpha: Int) {
        paint.color = Color.argb(alpha, 0, 210, 255)
        paint.strokeWidth = width
        canvas.drawCircle(cx, cy, radius, paint)
    }

    private fun orbit(canvas: Canvas, cx: Float, cy: Float, rx: Float, ry: Float,
                      rotation: Float, width: Float, alpha: Int) {
        canvas.save()
        canvas.rotate(rotation, cx, cy)
        paint.color = Color.argb(alpha, 0, 220, 255)
        paint.strokeWidth = width
        canvas.drawOval(cx - rx, cy - ry, cx + rx, cy + ry, paint)
        canvas.restore()
    }
}
