package com.chunbo.medical.util

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 结构化复诊提醒实体类
 */
data class RevisitInfo(
    val triggerMillis: Long,
    val patientName: String,
    val doctorName: String = "主治医生",
    val department: String = "全科门诊",
    val diagnosis: String = "门诊确诊",
    val prescriptionNo: String = "",
    val note: String = "遵医嘱按时复查评估病情"
) {
    val dateString: String
        get() = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(triggerMillis))

    val timeString: String
        get() = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA).format(java.util.Date(triggerMillis))

    val daysRemaining: Int
        get() {
            val diff = triggerMillis - System.currentTimeMillis()
            return if (diff > 0) ((diff / (1000 * 60 * 60 * 24)) + 1).toInt() else 0
        }

    val isPending: Boolean
        get() = triggerMillis > System.currentTimeMillis()
}

/**
 * 复诊提醒（纯手机端，定时提醒与关怀闭环）：
 * 患者就诊/开具处方后，在门诊就诊记录详情中按医生/AI 调护建议自设复诊日期，
 * 存入本地并在【我的就诊记录】列表与【系统设置】中持久化展示，
 * 到点由 [RevisitReminderReceiver] 触发本地通知，并支持一键快捷预约复诊挂号闭环。
 */
object RevisitReminderManager {

    private const val PREFS = "revisit_reminder_prefs"
    private const val KEY_NEXT_VISIT = "revisit_next_visit_time"
    private const val KEY_PATIENT = "revisit_patient"
    private const val KEY_DOCTOR = "revisit_doctor"
    private const val KEY_DEPT = "revisit_dept"
    private const val KEY_DIAGNOSIS = "revisit_diagnosis"
    private const val KEY_RX_NO = "revisit_rx_no"
    private const val KEY_NOTE = "revisit_note"

    const val ACTION_REVISIT_REMIND = "com.chunbo.medical.action.REVISIT_REMIND"
    private const val REQUEST_CODE = 3001

    /** 设置完整复诊提醒，返回触发时间戳（毫秒） */
    fun schedule(
        context: Context,
        triggerMillis: Long,
        patientName: String,
        doctorName: String = "主治医生",
        department: String = "全科门诊",
        diagnosis: String = "门诊确诊",
        prescriptionNo: String = "",
        note: String = "遵医嘱按时复查评估病情"
    ): Long {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(appContext, RevisitReminderReceiver::class.java).apply {
            action = ACTION_REVISIT_REMIND
            putExtra("patient", patientName)
            putExtra("doctor", doctorName)
            putExtra("dept", department)
            putExtra("diagnosis", diagnosis)
            putExtra("rxNo", prescriptionNo)
            putExtra("note", note)
        }
        val pending = PendingIntent.getBroadcast(
            appContext, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        // 用 set（非精确），不要求 SCHEDULE_EXACT_ALARM 权限，Doze 下允许分钟级延迟
        alarmManager.set(AlarmManager.RTC_WAKEUP, triggerMillis, pending)

        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_NEXT_VISIT, triggerMillis)
            .putString(KEY_PATIENT, patientName)
            .putString(KEY_DOCTOR, doctorName)
            .putString(KEY_DEPT, department)
            .putString(KEY_DIAGNOSIS, diagnosis)
            .putString(KEY_RX_NO, prescriptionNo)
            .putString(KEY_NOTE, note)
            .apply()
        return triggerMillis
    }

    /** 兼容旧简版调用 */
    fun schedule(context: Context, triggerMillis: Long, patientName: String, diagnosis: String): Long {
        return schedule(
            context = context,
            triggerMillis = triggerMillis,
            patientName = patientName,
            doctorName = "主治医生",
            department = "全科门诊",
            diagnosis = diagnosis,
            prescriptionNo = "",
            note = "遵医嘱按时复查评估病情"
        )
    }

    /** 获取下次复诊提醒时间戳（毫秒，0 表示未设置） */
    fun getNextVisitTime(context: Context): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong(KEY_NEXT_VISIT, 0L)

    /** 判断是否存在有效的待复诊日程（未过期） */
    fun hasActiveRevisit(context: Context): Boolean {
        val next = getNextVisitTime(context)
        return next > System.currentTimeMillis()
    }

    /** 读取结构化复诊提醒信息 */
    fun getRevisitInfo(context: Context): RevisitInfo? {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val time = sp.getLong(KEY_NEXT_VISIT, 0L)
        if (time <= 0L) return null
        return RevisitInfo(
            triggerMillis = time,
            patientName = sp.getString(KEY_PATIENT, "") ?: "",
            doctorName = sp.getString(KEY_DOCTOR, "主治医生") ?: "主治医生",
            department = sp.getString(KEY_DEPT, "全科门诊") ?: "全科门诊",
            diagnosis = sp.getString(KEY_DIAGNOSIS, "门诊确诊") ?: "门诊确诊",
            prescriptionNo = sp.getString(KEY_RX_NO, "") ?: "",
            note = sp.getString(KEY_NOTE, "遵医嘱按时复查评估病情") ?: "遵医嘱按时复查评估病情"
        )
    }

    /** 取消复诊提醒 */
    fun cancel(context: Context) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(appContext, RevisitReminderReceiver::class.java).apply {
            action = ACTION_REVISIT_REMIND
        }
        val pending = PendingIntent.getBroadcast(
            appContext, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
