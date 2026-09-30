package com.chunbo.medical.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chunbo.medical.R
import com.chunbo.medical.data.api.ApiClient
import com.chunbo.medical.data.model.Registration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 就诊叫号轮询提醒（纯手机端，不依赖后端推送）：
 * 定时轮询当前用户的挂号状态，当状态从「待诊/候诊中」变为「就诊中」时弹本地通知，提示患者前往诊室。
 */
object QueueReminderManager {

    private const val CHANNEL_ID = "queue_call_channel"
    private const val POLL_INTERVAL_MS = 30_000L
    private const val NOTIFICATION_ID = 1001

    private const val PREFS = "queue_reminder_prefs"
    private const val KEY_ENABLED = "queue_reminder_enabled"

    private var pollingJob: Job? = null
    private var lastStatus: String? = null
    private val persistentScope = CoroutineScope(Dispatchers.Main + kotlinx.coroutines.SupervisorJob())

    /** 叫号提醒开关（默认开启，保持原有"有挂号即提醒"行为） */
    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)

    /** 设置叫号提醒开关：关闭时立即停止轮询 */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (!enabled) stop()
    }

    /** 创建通知渠道（Android 8+） */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "就诊叫号提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "候诊叫号与就诊状态变化提醒"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    /** 开始轮询（使用全局持久协程作用域，即使切换 Tab 也不中断，离开 App 或完诊后自动停止） */
    fun start(context: Context, scope: CoroutineScope? = null, phone: String?) {
        stop()
        if (!isEnabled(context)) return
        ensureChannel(context.applicationContext)
        val appContext = context.applicationContext
        val effectiveScope = scope ?: persistentScope
        pollingJob = effectiveScope.launch {
            while (isActive) {
                try {
                    val list = withContext(Dispatchers.IO) {
                        ApiClient.service.getRegistrationList(userPhone = phone)
                    }
                    val active = list.firstOrNull {
                        it.status == "待诊" || it.status == "候诊中" || it.status == "就诊中"
                    }
                    if (active == null) {
                        // 无生效挂号，停止轮询
                        stop()
                        break
                    }
                    val st = active.status ?: ""
                    // 仅在「从待诊/候诊 → 就诊中」变化时提醒，避免重复弹通知；
                    // 首轮若已是「就诊中」且挂号日期为今天（新鲜叫号）也提醒，历史遗留的「就诊中」脏数据不误弹
                    if (st == "就诊中") {
                        val todayStr = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
                            .format(java.util.Date())
                        val isToday = active.createTime?.startsWith(todayStr) == true
                        val shouldNotify = if (lastStatus == null) isToday else (lastStatus != "就诊中")
                        if (shouldNotify) notifyCalled(appContext, active)
                    }
                    lastStatus = st
                } catch (e: Exception) {
                    // 轮询失败忽略，下轮重试
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
        lastStatus = null
    }

    private fun notifyCalled(context: Context, reg: Registration) {
        // Android 13+ 需 POST_NOTIFICATIONS 运行时权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val dept = reg.department ?: "门诊"
        val doc = reg.doctorName ?: "坐诊医生"
        val intent = Intent(context, com.chunbo.medical.ui.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("🔔 医生已叫号，请前往就诊")
            .setContentText("您的号已被叫到，请立即前往 $dept · $doc 诊室就诊")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            // 通知失败忽略
        }
    }
}
