package com.jarvis.assistant

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * این کلاس درخواست را به مدل Gemini گوگل (که نسخه‌ی رایگانش از طریق
 * Google AI Studio قابل دریافت است) می‌فرستد.
 *
 * نکته‌ی مهم: پاسخی که کاربر می‌بیند/می‌شنود همیشه با شخصیت "جارویس" است.
 * دستورالعمل سیستمی زیر مشخص می‌کند که هیچ‌وقت اسم شرکت سازنده‌ی مدل
 * (گوگل/Gemini) در پاسخ گفته نشود، و به‌جایش خودش را به‌عنوان دستیار
 * هوشمند "جارویس" معرفی کند.
 *
 * این تابع باید حتماً از یک ترد پس‌زمینه صدا زده شود (نه ترد اصلی UI).
 */
class GeminiClient(private val apiKey: String) {

    // مدل رایگان و سریع گوگل (نسخه‌ی معتبر و در دسترس API).
    // نکته: "gemini-3.7-flash" وجود خارجی ندارد و همیشه خطای 404 برمی‌گرداند؛
    // به همین دلیل بخش هوش مصنوعی هیچ‌وقت جواب نمی‌داد. به gemini-2.5-flash اصلاح شد.
    private val modelEndpoint =
        "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

    private val jarvisPersonaInstruction = """
        نامت "جارویس" است، یک دستیار هوشمند صوتی روی گوشی اندروید، با روحیه و لحن
        جارویس در فیلم‌های مرد آهنی: مؤدب، باهوش، کمی رسمی ولی دوستانه، و همیشه آماده‌ی کمک.

        تو باید در **هر زمینه‌ای** بتوانی دقیق و مفید کمک کنی: سوالات عمومی، علمی،
        فنی، برنامه‌نویسی، زندگی روزمره، و به‌خصوص **صنعت گیمینگ**:
        - تنظیمات حرفه‌ای بازی‌ها (سنسیتیویتی موس، DPI، کیبایند، کانفیگ‌های شناخته‌شده)
        - راهنمای چیدن تنظیمات گرافیکی برای بهترین فریم‌ریت یا بهترین کیفیت
        - آموزش ساخت و ادیت کانفیگ/ست‌آپ برای بازی‌های رقابتی (مثل CS2، Valorant، PUBG، فورتنایت و مشابه)
        - توصیه‌ی سخت‌افزار، تنظیمات مانیتور (Hz، رزولوشن)، و بهینه‌سازی سیستم برای گیم
        - هر سوال دیگری درباره‌ی گیمینگ که پرسیده شود

        همیشه دقیق، مشخص، و با جزئیات کاربردی جواب بده (مثلاً اگر عددی مثل سنسیتیویتی
        یا تنظیم گرافیکی خواستند، یک عدد/مقدار مشخص و منطقی پیشنهاد بده، نه جواب کلی و مبهم).
        پاسخ‌هایت را کوتاه و مناسب خواندن با صدای بلند (Text-to-Speech) بده، مگر
        کاربر صریحاً خواسته باشد توضیح کامل و طولانی بدهی.
        به فارسی پاسخ بده مگر اینکه کاربر انگلیسی صحبت کند.

        قانون بسیار مهم: در هیچ جوابی، تحت هیچ شرایطی، کلمات "Gemini"، "جمنای"،
        "Google"، "گوگل"، یا هر اشاره‌ای به شرکت یا مدل زیرساختی سازنده‌ات نیاور،
        حتی اگر مستقیم پرسیده شود کی تو را ساخته یا بر پایه‌ی چه چیزی کار می‌کنی.
        در آن حالت فقط بگو: "من جارویس هستم، دستیار هوشمند همین برنامه، و جزئیات
        فنی داخلی‌ام مهم نیست؛ بگو چه کاری از دستم برمی‌آید."
    """.trimIndent()

    // اگر با وجود دستورالعمل بالا، مدل باز هم اسم خودش را لو بدهد،
    // این فیلتر آخرین خط دفاعی است: به‌جای پاک کردن کل جواب (که بخش مفیدش هم از بین می‌رود)،
    // فقط همان کلمه/عبارت لورفته را با نسخه‌ی بی‌خطر جایگزین می‌کند.
    private val identityReplacements = listOf(
        "gemini" to "جارویس",
        "جمنای" to "جارویس",
        "جمینای" to "جارویس",
        "google ai" to "جارویس",
        "google" to "این برنامه",
        "گوگل" to "این برنامه",
        "large language model" to "دستیار هوشمند",
        "مدل زبانی بزرگ" to "دستیار هوشمند",
        "trained by google" to "ساخته‌شده برای این برنامه",
        "ساخته‌ی گوگل" to "ساخته‌شده برای این برنامه",
        "developed by google" to "ساخته‌شده برای این برنامه",
        "an ai model" to "جارویس",
        "i am an ai" to "من جارویس هستم"
    )

    private fun sanitizeIdentityLeak(text: String): String {
        var result = text
        for ((pattern, replacement) in identityReplacements) {
            result = Regex(Regex.escape(pattern), RegexOption.IGNORE_CASE).replace(result, replacement)
        }
        return result
    }

    /**
     * @return پاسخ متنی جارویس، یا پیام خطا اگر مشکلی پیش آمد
     */
    fun askImage(userText: String, jpegBytes: ByteArray): String {
        if (apiKey.isBlank()) return "برای تحلیل تصویر، اول کلید API را وارد کن"
        return try {
            val root = JSONObject()
            root.put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", jarvisPersonaInstruction))))
            val parts = JSONArray()
            parts.put(JSONObject().put("text", userText))
            parts.put(JSONObject().put("inlineData", JSONObject().put("mimeType", "image/jpeg").put("data", Base64.encodeToString(jpegBytes, Base64.NO_WRAP))))
            root.put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            root.put("generationConfig", JSONObject().put("maxOutputTokens", 220).put("thinkingConfig", JSONObject().put("thinkingLevel", "low")))
            sanitizeIdentityLeak(post(root))
        } catch (_: Exception) { "تحلیل تصویر انجام نشد" }
    }

    fun ask(userText: String): String {
        if (apiKey.isBlank()) {
            return "برای استفاده از هوش مصنوعی، اول باید کلید API رایگان را در تنظیمات وارد کنی"
        }

        return try {
            val url = URL("$modelEndpoint?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 15000
            connection.readTimeout = 20000

            val requestBody = buildRequestBody(userText)

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(requestBody.toString()) }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }

            if (responseCode !in 200..299) {
                return "متأسفم، الان نمی‌توانم پاسخ بدهم (ارتباط با سرور برقرار نشد)"
            }

            val finalReply = extractReplyText(responseText) ?: return "متوجه سوالت نشدم، دوباره بپرس"
            sanitizeIdentityLeak(finalReply)
        } catch (e: Exception) {
            "الان به اینترنت دسترسی ندارم یا اتصال برقرار نشد"
        }
    }

    private fun post(body: JSONObject): String {
        return try {
            val url = URL("$modelEndpoint?key=$apiKey")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 7000
            connection.readTimeout = 12000
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            connection.disconnect()
            if (code !in 200..299) "ارتباط با سرویس هوش مصنوعی برقرار نشد" else extractReplyText(text) ?: "پاسخ دریافت نشد"
        } catch (_: Exception) { "اتصال برقرار نشد" }
    }

    private fun buildRequestBody(userText: String): JSONObject {
        val root = JSONObject()

        // دستورالعمل شخصیت جارویس (System Instruction)
        val systemInstruction = JSONObject()
        val sysParts = JSONArray()
        sysParts.put(JSONObject().put("text", jarvisPersonaInstruction))
        systemInstruction.put("parts", sysParts)
        root.put("systemInstruction", systemInstruction)

        // پیام کاربر
        val contents = JSONArray()
        val userContent = JSONObject()
        userContent.put("role", "user")
        val userParts = JSONArray()
        userParts.put(JSONObject().put("text", userText))
        userContent.put("parts", userParts)
        contents.put(userContent)
        root.put("contents", contents)

        // پاسخ کوتاه‌تر و مناسب صدا
        val generationConfig = JSONObject()
        generationConfig.put("maxOutputTokens", 200)
        root.put("generationConfig", generationConfig)

        return root
    }

    private fun extractReplyText(responseJson: String): String? {
        return try {
            val json = JSONObject(responseJson)
            val candidates = json.optJSONArray("candidates") ?: return null
            if (candidates.length() == 0) return null
            val content = candidates.getJSONObject(0).optJSONObject("content") ?: return null
            val parts = content.optJSONArray("parts") ?: return null
            if (parts.length() == 0) return null
            parts.getJSONObject(0).optString("text").trim().ifBlank { null }
        } catch (e: Exception) {
            null
        }
    }
}
