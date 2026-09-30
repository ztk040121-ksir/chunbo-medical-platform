package com.chunbo.medical.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * 服药打卡/每日服药提醒（纯手机端，不依赖后端推送）：
 * 用 AlarmManager 注册每日三个时段的重复提醒，到点由 [MedicationReminderReceiver] 弹本地通知。
 * 开关状态持久化到 SharedPreferences，App 重启/开机（BOOT_COMPLETED）后由 Receiver 自动恢复调度。
 */
object MedicationReminderManager {

    private const val PREFS = "medication_reminder_prefs"
    private const val KEY_ENABLED = "medication_reminder_enabled"

    const val ACTION_MEDICATION_REMIND = "com.chunbo.medical.action.MEDICATION_REMIND"
    const val ACTION_MARK_TAKEN = "com.chunbo.medical.action.MEDICATION_MARK_TAKEN"
    const val ACTION_MARK_MISSED = "com.chunbo.medical.action.MEDICATION_MARK_MISSED"

    private const val KEY_STATUS_PREFIX = "med_status_"

    /** 默认服药提醒时段：早 08:00 / 午 12:30 / 晚 19:30，requestCode 用于区分三个闹钟 */
    private val remindTimes = listOf(
        Triple(8, 0, 100),
        Triple(12, 30, 101),
        Triple(19, 30, 102)
    )

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

    /** 开启/关闭服药提醒：持久化开关并按需调度/取消闹钟 */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) scheduleDaily(context) else cancel(context)
    }

    fun scheduleDaily(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val appContext = context.applicationContext
        val now = Calendar.getInstance()

        remindTimes.forEach { (hour, minute, requestCode) ->
            val trigger = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= now.timeInMillis) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            val pending = reminderPendingIntent(appContext, requestCode)
            // setInexactRepeating 不要求 SCHEDULE_EXACT_ALARM 精确闹钟权限
            alarmManager.setInexactRepeating(
                AlarmManager.RTC_WAKEUP,
                trigger.timeInMillis,
                AlarmManager.INTERVAL_DAY,
                pending
            )
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val appContext = context.applicationContext
        remindTimes.forEach { (_, _, requestCode) ->
            alarmManager.cancel(reminderPendingIntent(appContext, requestCode))
        }
    }

    // ── 服药打卡记录（纯本地台账，闭环「已服 / 漏服」）──

    private fun todayKey(): String =
        java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date())

    /** 记录今日已服 */
    fun markTaken(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_STATUS_PREFIX + todayKey(), "taken").apply()
    }

    /** 记录今日漏服 */
    fun markMissed(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_STATUS_PREFIX + todayKey(), "missed").apply()
    }

    /** 今日打卡状态："taken" / "missed" / null（未打卡） */
    fun getTodayStatus(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATUS_PREFIX + todayKey(), null)

    /** 最近 days 天打卡记录 [(yyyy-MM-dd, taken/missed)]，倒序（最近在前） */
    fun getRecords(context: Context, days: Int): List<Pair<String, String>> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
        val result = mutableListOf<Pair<String, String>>()
        val cal = Calendar.getInstance()
        repeat(days) {
            val dateStr = sdf.format(cal.time)
            val status = prefs.getString(KEY_STATUS_PREFIX + dateStr, null)
            if (status != null) result.add(dateStr to status)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return result
    }

    private fun reminderPendingIntent(context: Context, requestCode: Int): PendingIntent {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = ACTION_MEDICATION_REMIND
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
