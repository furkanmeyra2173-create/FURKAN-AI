package com.furkanai.assistant

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra("title") ?: "FURKAN AI hatırlatıcısı"
        val id = intent.getIntExtra("reminder_id", 0)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "FURKAN AI hatırlatıcıları",
                    NotificationManager.IMPORTANCE_HIGH
                )
            )
        }
        val openApp = PendingIntent.getActivity(
            context,
            id,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            android.app.Notification.Builder(context)
        }
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("FURKAN AI")
            .setContentText(title)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .build()
        manager.notify(id, notification)
        LocalData.deleteReminder(context, id)
    }

    companion object {
        const val CHANNEL_ID = "furkan_ai_reminders"
    }
}

object ReminderScheduler {
    fun schedule(context: Context, title: String, delayMinutes: Int): LocalData.Reminder {
        require(delayMinutes in 1..10080) { "Hatırlatma 1 dakika ile 7 gün arasında olmalı." }
        val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
        val at = System.currentTimeMillis() + delayMinutes * 60_000L
        val intent = Intent(context, ReminderReceiver::class.java)
            .putExtra("title", title)
            .putExtra("reminder_id", id)
        val pending = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        val reminder = LocalData.Reminder(id, title, at)
        LocalData.addReminder(context, reminder)
        return reminder
    }

    fun cancel(context: Context, id: Int) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending)
        pending.cancel()
    }
}
