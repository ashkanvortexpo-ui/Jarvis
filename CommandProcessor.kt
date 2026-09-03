package com.jarvis.assistant

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * متن تشخیص داده شده از گفتار کاربر را می‌گیرد و تصمیم می‌گیرد چه کاری انجام شود:
 * 1) اگر دستور محلی شناخته‌شده باشد (مثل باز کردن یک اپ) → همان‌جا و بدون اینترنت انجام می‌شود.
 * 2) هر سوال یا دستور دیگری (عمومی، تخصصی، گیمینگ و ...) → به هوش مصنوعی سپرده می‌شود
 *    (در پس‌زمینه، بدون قفل کردن رابط کاربری) و با شخصیت "جارویس" جواب داده می‌شود.
 *
 * برای افزودن دستور محلیِ جدید (که نیاز به اینترنت ندارد) فقط کافیست
 * یک "if" جدید در تابع process اضافه کنی.
 */
class CommandProcessor(private val context: Context) {

    private val appLauncher = AppLauncher(context)
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private val openWords = listOf("باز کن", "باز کردن", "برو", "open", "launch")

    /**
     * @param command متنی که کاربر گفته
     * @param callback همیشه روی ترد اصلی (UI) صدا زده می‌شود، تا مستقیم بشود صحبتش کرد یا UI را آپدیت کرد
     */

    private fun normalize(input: String): String {
        return input.trim()
            .replace('ي', 'ی')
            .replace('ك', 'ک')
            .replace("‌", " ")
            .replace(Regex("\\s+"), " ")
            .lowercase()
    }

    fun process(command: String, callback: (String) -> Unit) {
        val text = command.trim()
        if (text.isEmpty()) {
            callback("متوجه نشدم، دوباره بگو")
            return
        }

        val lower = normalize(text)

        // حالت بازی هوشمند: فقط با فرمان صریح کاربر فعال می‌شود.
        if (lower.contains("خودت بازی کن") || lower.contains("خودت بازی رو کن") ||
            lower.contains("حالت بازی هوشمند")) {
            GamingAiManager.enable(context)
            val intent = android.content.Intent(context, MainActivity::class.java)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_START_GAMING_CAPTURE, true)
            context.startActivity(intent)
            callback("حالت بازی هوشمند فعال شد؛ اجازه تحلیل صفحه را تأیید کن.")
            return
        }

        if (lower.contains("بس کن") || lower.contains("توقف بازی") ||
            lower.contains("حالت بازی هوشمند خاموش") ||
            lower.contains("بازی رو متوقف کن") ||
            lower.contains("خودم بازی میکنم") || lower.contains("خودم بازی می‌کنم") ||
            lower.contains("من خودم بازی میکنم") || lower.contains("من خودم بازی می‌کنم") ||
            lower.contains("دیگه خودم بازی میکنم") || lower.contains("دیگه خودم بازی می‌کنم")) {
            GamingAiManager.disable(context)
            callback("حالت بازی هوشمند متوقف شد.")
            return
        }

        // 1) دستور محلی: باز کردن یک برنامه (سریع، بدون نیاز به اینترنت)
        val isOpenCommand = openWords.any { lower.contains(it) }
        if (isOpenCommand) {
            var appName = text
            for (w in openWords) {
                appName = appName.replace(w, "", ignoreCase = true)
            }
            appName = appName.trim()

            val opened = appLauncher.openApp(appName)
            callback(if (opened) "$appName را باز کردم" else "برنامه‌ی «$appName» پیدا نشد")
            return
        }

        if (lower.startsWith("نصب ") || lower.contains("نصب کن") || lower.contains("دانلود و نصب")) {
            val name = lower.replace("دانلود و نصب", "").replace("نصب کن", "").replace("نصب", "").trim()
            if (name.isNotBlank()) {
                val ok = InstallerManager.openPlayStoreSearch(context, name)
                callback(if (ok) "صفحه نصب «$name» را باز کردم؛ تأیید نهایی با خودت است." else "نتوانستم صفحه نصب را باز کنم")
                return
            }
        }

        if (lower.contains("وی پی ان") || lower.contains("vpn") || lower.contains("فیلتر شکن")) {
            val name = lower.replace("وی پی ان", "").replace("vpn", "").replace("فیلتر شکن", "").replace("روشن کن", "").trim()
            val ok = VpnManager.openVpn(context, name)
            callback(if (ok) "برنامه یا تنظیمات VPN را باز کردم؛ روشن‌کردن نهایی ممکن است تأیید خودت را بخواهد." else "نتوانستم VPN را باز کنم")
            return
        }


        // تنظیمات و مرکز کنترل جارویس
        if (lower.contains("تنظیمات جارویس") || lower.contains("مرکز کنترل جارویس") ||
            lower.contains("تنظیمات پیشرفته جارویس")) {
            context.startActivity(android.content.Intent(context, JarvisSettingsActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            callback("مرکز کنترل تنظیمات جارویس را باز کردم")
            return
        }

        if (lower.contains("حالت بازی") || lower.contains("پروفایل بازی")) {
            JarvisSettings.applyProfile(context, "بازی")
            callback("پروفایل بازی فعال شد")
            return
        }

        if (lower.contains("حالت شب") || lower.contains("پروفایل شب")) {
            JarvisSettings.applyProfile(context, "شب")
            callback("پروفایل شب فعال شد")
            return
        }

        if (lower.contains("تنظیمات وای فای") || lower.contains("تنظیمات وای‌فای")) {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            callback("تنظیمات وای‌فای را باز کردم")
            return
        }

        if (lower.contains("تنظیمات بلوتوث")) {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            callback("تنظیمات بلوتوث را باز کردم")
            return
        }

        if (lower.contains("تنظیمات باتری")) {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_BATTERY_SAVER_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            callback("تنظیمات باتری را باز کردم")
            return
        }

        if (lower.contains("تنظیمات نمایشگر") || lower.contains("تنظیمات صفحه نمایش")) {
            context.startActivity(android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            callback("تنظیمات نمایشگر را باز کردم")
            return
        }


        // دستیار شخصی: یادداشت، کار و تایمر
        if (lower.startsWith("یادداشت ") || lower.startsWith("یادداشت کن ")) {
            val text = lower.removePrefix("یادداشت کن ").removePrefix("یادداشت ").trim()
            if (text.isNotEmpty()) {
                AssistantTools.addNote(context, text)
                callback("یادداشت ذخیره شد")
            } else callback("متن یادداشت را بگو")
            return
        }

        if (lower.startsWith("کار ") || lower.startsWith("کارم را ثبت کن ")) {
            val text = lower.removePrefix("کارم را ثبت کن ").removePrefix("کار ").trim()
            if (text.isNotEmpty()) {
                AssistantTools.addTask(context, text)
                callback("کار ذخیره شد")
            } else callback("متن کار را بگو")
            return
        }

        if (lower.contains("یادداشت‌ها") || lower.contains("کارهای من") || lower.contains("فهرست کارها")) {
            callback(AssistantTools.summary(context))
            return
        }

        val timerMatch = Regex("""(?:تایمر|یادآوری)\s*(\d+)\s*(ثانیه|دقیقه)?""").find(lower)
        if (timerMatch != null) {
            val number = timerMatch.groupValues[1].toIntOrNull() ?: 0
            val unit = timerMatch.groupValues[2]
            val seconds = if (unit == "دقیقه") number * 60 else number
            if (seconds > 0) {
                AssistantTools.setTimer(context, seconds)
                callback("تایمر تنظیم شد")
            } else callback("زمان تایمر را مشخص کن")
            return
        }

        if (lower.contains("فایل‌ها") || lower.contains("مدیریت فایل")) {
            FileManagerHelper.openFiles(context)
            callback("مدیریت فایل را باز کردم")
            return
        }

        if (lower.contains("حافظه گوشی") || lower.contains("فضای ذخیره سازی")) {
            FileManagerHelper.openStorageSettings(context)
            callback("تنظیمات حافظه را باز کردم")
            return
        }

        if (lower.contains("آنالیز صفحه") || lower.contains("تحلیل صفحه") || lower.contains("آنالیز بازی") || lower.contains("تحلیل بازی") || lower.contains("حریف را تحلیل") || lower.contains("حریف رو تحلیل")) {
            val f = ScreenCaptureService.latestScreenshot
            if (f == null || !f.exists()) { callback("اول مجوز تحلیل صفحه را فعال کن، بعد بگو آنالیز صفحه."); return }
            askAiWithImage("این تصویر صفحه بازی است. وضعیت، حریف یا تهدید اصلی و بهترین حرکت بعدی را فقط بر اساس چیزهای قابل مشاهده تحلیل کن. سه نکته کوتاه بده.", f, callback)
            return
        }

        // 2) هر چیز دیگری (سوال عمومی، تخصصی، تنظیمات گیم، هرچی) → از هوش مصنوعی بپرس
        askAi(text, callback)
    }

    private fun askAiWithImage(prompt: String, file: java.io.File, callback: (String) -> Unit) {
        val apiKey = PrefsHelper.getApiKey(context)
        if (apiKey.isBlank()) { callback("برای تحلیل تصویر، اول کلید API را در تنظیمات وارد کن"); return }
        executor.execute {
            val bytes = try { file.readBytes() } catch (_: Exception) { null }
            val reply = if (bytes == null) "تصویر خوانده نشد" else GeminiClient(apiKey).askImage(prompt, bytes)
            mainHandler.post { callback(reply) }
        }
    }

    private fun askAi(text: String, callback: (String) -> Unit) {
        val apiKey = PrefsHelper.getApiKey(context)
        if (apiKey.isBlank()) {
            callback("برای پاسخ به این سوال به کلید هوش مصنوعی نیاز دارم؛ یک کلید رایگان در تنظیمات وارد کن")
            return
        }

        executor.execute {
            val client = GeminiClient(apiKey)
            val reply = client.ask(text)
            mainHandler.post { callback(reply) }
        }
    }
}
