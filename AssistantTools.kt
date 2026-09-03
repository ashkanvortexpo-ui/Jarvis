package com.jarvis.assistant

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import java.text.SimpleDateFormat
import java.util.*

object AssistantTools {
    private const val PREFS = "jarvis_tools"
    private const val NOTES = "notes"
    private const val TASKS = "tasks"

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun addNote(c: Context, text: String) {
        val old = p(c).getString(NOTES, "") ?: ""
        val stamp = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(Date())
        p(c).edit().putString(NOTES, "$old\n[$stamp] $text").apply()
    }

    fun addTask(c: Context, text: String) {
        val old = p(c).getString(TASKS, "") ?: ""
        p(c).edit().putString(TASKS, "$old\n- $text").apply()
    }

    fun summary(c: Context): String {
        val notes = p(c).getString(NOTES, "")?.trim().orEmpty()
        val tasks = p(c).getString(TASKS, "")?.trim().orEmpty()
        return "یادداشت‌ها:\n${if (notes.isEmpty()) "موردی ثبت نشده" else notes}\n\nکارها:\n${if (tasks.isEmpty()) "موردی ثبت نشده" else tasks}"
    }

    fun clear(c: Context) {
        p(c).edit().remove(NOTES).remove(TASKS).apply()
    }

    fun setTimer(c: Context, seconds: Int, title: String = "تایمر جارویس") {
        val ms = (seconds.coerceAtLeast(1) * 1000L)
        val intent = Intent(c, ReminderReceiver::class.java).putExtra("title", title)
        val pi = PendingIntent.getBroadcast(
            c, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ms, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + ms, pi)
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val title = intent?.getStringExtra("title") ?: "جارویس"
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "jarvis_reminders"
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "یادآوری‌های جارویس", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val n = Notification.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("زمان یادآوری فرا رسید.")
            .setAutoCancel(true)
            .build()
        nm.notify(System.currentTimeMillis().toInt(), n)
    }
}
