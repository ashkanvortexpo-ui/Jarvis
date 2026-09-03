package com.jarvis.assistant

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo

/**
 * این کلاس مسئول پیدا کردن و باز کردن برنامه‌های نصب‌شده روی گوشی است.
 * هم اسم فارسی و هم اسم انگلیسی برنامه‌های معروف را می‌شناسد،
 * و برای بقیه‌ی برنامه‌ها اسم دقیق نصب‌شده روی گوشی را جستجو می‌کند.
 */
class AppLauncher(private val context: Context) {

    // نگاشت اسم‌های رایج فارسی/انگلیسی به بخشی از نام پکیج
    private val knownApps = mapOf(
        "اینستاگرام" to "instagram",
        "instagram" to "instagram",
        "تلگرام" to "telegram",
        "telegram" to "telegram",
        "واتساپ" to "whatsapp",
        "whatsapp" to "whatsapp",
        "یوتیوب" to "youtube",
        "youtube" to "youtube",
        "کروم" to "chrome",
        "chrome" to "chrome",
        "دوربین" to "camera",
        "camera" to "camera",
        "تنظیمات" to "settings",
        "settings" to "settings",
        "گالری" to "gallery",
        "gallery" to "gallery",
        "پیامک" to "messaging",
        "messages" to "messaging",
        "مخاطبین" to "contacts",
        "contacts" to "contacts",
        "پلی استور" to "vending",
        "play store" to "vending",
        "توییتر" to "twitter",
        "ایکس" to "twitter",
        "twitter" to "twitter",
        "x" to "twitter",
        "اسنپ" to "snapp",
        "snapp" to "snapp",
        "دیجی کالا" to "digikala",
        "digikala" to "digikala",
        "بله" to "bale",
        "روبیکا" to "rubika",
        "ایتا" to "eitaa"
    )

    /**
     * تلاش می‌کند اپلیکیشنی که نامش شبیه spokenName است را باز کند.
     * خروجی: true اگر باز شد، false اگر پیدا نشد.
     */
    fun openApp(spokenName: String): Boolean {
        val query = spokenName.trim().lowercase()
        val pm = context.packageManager
        val installedApps: List<ApplicationInfo> = pm.getInstalledApplications(0)

        // 1) اول در نگاشت اسم‌های شناخته‌شده جستجو کن
        val mappedKeyword = knownApps.entries.firstOrNull { (key, _) ->
            query.contains(key.lowercase())
        }?.value

        if (mappedKeyword != null) {
            val found = installedApps.firstOrNull { appInfo ->
                appInfo.packageName.lowercase().contains(mappedKeyword)
            }
            if (found != null) {
                launch(found.packageName)
                return true
            }
        }

        // 2) اگر پیدا نشد، بین اسم نمایشی برنامه‌های نصب‌شده جستجو کن
        val byLabel = installedApps.firstOrNull { appInfo ->
            val label = pm.getApplicationLabel(appInfo).toString().lowercase()
            label.contains(query) || query.contains(label)
        }
        if (byLabel != null) {
            launch(byLabel.packageName)
            return true
        }

        return false
    }

    private fun launch(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        launchIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (launchIntent != null) {
            context.startActivity(launchIntent)
        }
    }
}
