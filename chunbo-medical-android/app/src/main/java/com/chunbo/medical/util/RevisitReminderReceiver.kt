package com.chunbo.medical.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chunbo.medical.R

/**
 * 复诊提醒广播接收器：收到 [RevisitReminderManager.ACTION_REVISIT_REMIND] 时弹本地通知，
 * 引导患者按时复诊；开机广播时若有未到期的提醒则恢复调度（单次闹钟重启会丢失）。
 */
class RevisitReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                val next = RevisitReminderManager.getNextVisitTime(context)
                if (next > System.currentTimeMillis()) {
                    val prefs = context.getSharedPreferences("revisit_reminder_prefs", Context.MODE_PRIVATE)
                    RevisitReminderManager.schedule(
                        context,
                        next,
                        prefs.getString("revisit_patient", "") ?: "",
                        prefs.getString("revisit_diagnosis", "") ?: ""
                    )
                }
            }
            RevisitReminderManager.ACTION_REVISIT_REMIND -> {
                val patient = intent.getStringExtra("patient") ?: ""
                val diagnosis = intent.getStringExtra("diagnosis") ?: ""
                showNotification(context, patient, diagnosis)
            }
        }
    }

    private fun showNotification(context: Context, patient: String, diagnosis: String) {
        ensureChannel(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val intent = Intent(context, com.chunbo.medical.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = buildString {
            if (patient.isNotBlank()) append("就诊人 $patient ")
            if (diagnosis.isNotBlank()) append("· $diagnosis")
            append("，建议按时复诊复查，请在首页「便民挂号」预约")
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🏥 复诊提醒")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // 通知失败忽略
        }
    }

    companion object {
        private const val CHANNEL_ID = "revisit_reminder_channel"
        private const val NOTIFICATION_ID = 2002

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "复诊提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "慢病复诊复查提醒"
                }
                context.getSystemService(NotificationManager::class.java)
                    ?.createNotificationChannel(channel)
            }
        }
    }
}
