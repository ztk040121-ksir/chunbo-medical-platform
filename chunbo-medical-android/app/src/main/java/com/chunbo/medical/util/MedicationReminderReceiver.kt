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
 * 服药提醒广播接收器：
 * 1) 收到 [MedicationReminderManager.ACTION_MEDICATION_REMIND] 时弹本地通知；
 * 2) 收到系统开机广播 BOOT_COMPLETED 时，若开关开启则恢复每日调度（闹钟在重启后会丢失）。
 */
class MedicationReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                if (MedicationReminderManager.isEnabled(context)) {
                    MedicationReminderManager.scheduleDaily(context)
                }
            }
            MedicationReminderManager.ACTION_MEDICATION_REMIND -> showReminder(context)
            MedicationReminderManager.ACTION_MARK_TAKEN -> {
                MedicationReminderManager.markTaken(context)
                showRecordedFeedback(context, taken = true)
            }
            MedicationReminderManager.ACTION_MARK_MISSED -> {
                MedicationReminderManager.markMissed(context)
                showRecordedFeedback(context, taken = false)
            }
        }
    }

    private fun showReminder(context: Context) {
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

        // 「已服 / 漏服」打卡动作（点击后直接落台账，无需进 App）
        val takenPending = PendingIntent.getBroadcast(
            context, 2002,
            Intent(context, MedicationReminderReceiver::class.java).apply {
                action = MedicationReminderManager.ACTION_MARK_TAKEN
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val missedPending = PendingIntent.getBroadcast(
            context, 2003,
            Intent(context, MedicationReminderReceiver::class.java).apply {
                action = MedicationReminderManager.ACTION_MARK_MISSED
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("💊 服药提醒")
            .setContentText("该服药啦，请按医嘱按时用药，注意用量与频次")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .addAction(0, "✅ 已服", takenPending)
            .addAction(0, "⏰ 漏服", missedPending)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // 通知失败忽略
        }
    }

    /** 打卡后的简短反馈通知（替换原提醒通知） */
    private fun showRecordedFeedback(context: Context, taken: Boolean) {
        ensureChannel(context)
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(NOTIFICATION_ID)
        val text = if (taken) {
            "已记录：今日服药打卡完成，继续保持 🌿"
        } else {
            "已记录：今日漏服，请尽快补服或咨询医生 ⚠️"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("服药打卡")
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
            // 忽略
        }
    }

    companion object {
        private const val CHANNEL_ID = "medication_reminder_channel"
        private const val NOTIFICATION_ID = 2001

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "服药提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "慢病服药打卡与每日关怀提醒"
                }
                context.getSystemService(NotificationManager::class.java)
                    ?.createNotificationChannel(channel)
            }
        }
    }
}
