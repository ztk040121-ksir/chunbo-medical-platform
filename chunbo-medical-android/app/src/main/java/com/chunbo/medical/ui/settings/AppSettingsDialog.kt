package com.chunbo.medical.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.chunbo.medical.data.api.ApiClient
import com.chunbo.medical.data.api.UserManager
import com.chunbo.medical.databinding.DialogAppSettingsBinding
import com.chunbo.medical.ui.common.RegionPickerHelper
import com.chunbo.medical.util.AvatarHelper
import com.chunbo.medical.util.MedicationReminderManager
import com.chunbo.medical.util.QueueReminderManager
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 真实的系统设置中心：
 * 包含账号与安全、默认就医收货地址、消息与用药提醒开关、缓存清理、版本信息、以及折叠式网络诊断与退出登录。
 */
object AppSettingsDialog {

    private const val NOTIFY_PREFS = "notify_prefs"
    private const val KEY_NOTIFY_RX = "notify_rx_enabled"

    fun show(
        context: Context,
        onProfileEditRequested: () -> Unit = {},
        onAddressUpdated: (String) -> Unit = {},
        onStateChanged: () -> Unit = {}
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = DialogAppSettingsBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        val user = UserManager.getUser()

        // 1. 用户信息渲染
        if (user.isLoggedIn) {
            binding.tvSettingsName.text = user.nickname.ifBlank { user.username }
            binding.tvSettingsPhone.text = "账号: ${user.username} | 手机: ${user.phone.ifBlank { "未绑定" }}"
            binding.btnSettingsLogout.text = "退出当前登录账号"
            binding.btnSettingsLogout.setTextColor(context.getColor(com.chunbo.medical.R.color.status_red))
            binding.btnSettingsLogout.backgroundTintList = android.content.res.ColorStateList.valueOf(context.getColor(com.chunbo.medical.R.color.status_red_bg))
            AvatarHelper.loadAvatar(binding.ivSettingsAvatar, user.avatar)
        } else {
            binding.tvSettingsName.text = "游客模式（未登录）"
            binding.tvSettingsPhone.text = "登录后享受就医档案同步与健康体验金抵扣"
            binding.btnSettingsLogout.text = "立即登录 / 注册"
            binding.btnSettingsLogout.setTextColor(context.getColor(com.chunbo.medical.R.color.text_on_primary))
            binding.btnSettingsLogout.backgroundTintList = android.content.res.ColorStateList.valueOf(context.getColor(com.chunbo.medical.R.color.primary))
            binding.ivSettingsAvatar.setImageResource(com.chunbo.medical.R.drawable.avatar_resident_1)
        }

        // 2. 常用地址管理
        if (user.address.isNotBlank()) {
            binding.tvSettingsCurrentAddress.text = user.address
        } else {
            binding.tvSettingsCurrentAddress.text = "尚未设置，点击右侧修改添加"
        }

        binding.cardSettingsAddress.setOnClickListener {
            val (initialRegion, initialDetail) = RegionPickerHelper.splitAddress(user.address)
            RegionPickerHelper.showRegionPicker(context, initialRegion) { newRegion ->
                val newFull = if (initialDetail.isNotEmpty()) "$newRegion $initialDetail" else newRegion
                UserManager.updateAddress(newFull)
                binding.tvSettingsCurrentAddress.text = newFull
                onAddressUpdated(newFull)
                // 同步到云端（与个人中心编辑地址口径一致）
                kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                    try {
                        withContext(Dispatchers.IO) {
                            ApiClient.service.updateAddress(mapOf("address" to newFull))
                        }
                        Toast.makeText(context, "常用收货与就诊地址已更新并同步云端", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "地址已本地保存（云端同步失败）", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        binding.cardSettingsAccount.setOnClickListener {
            if (user.isLoggedIn) {
                dialog.dismiss()
                onProfileEditRequested()
            }
        }

        binding.btnSettingsEditProfile.setOnClickListener {
            if (user.isLoggedIn) {
                dialog.dismiss()
                onProfileEditRequested()
            }
        }

        // 3. 消息与提醒开关（先按存储状态回显，再绑定监听）
        binding.switchNotifyRx.isChecked = context.getSharedPreferences(NOTIFY_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_NOTIFY_RX, false)
        binding.switchNotifyMed.isChecked = MedicationReminderManager.isEnabled(context)
        binding.switchNotifyQueue.isChecked = QueueReminderManager.isEnabled(context)

        binding.switchNotifyRx.setOnCheckedChangeListener { _, isChecked ->
            context.getSharedPreferences(NOTIFY_PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_NOTIFY_RX, isChecked).apply()
            Toast.makeText(
                context,
                if (isChecked) "已开启门诊处方流转与药房调配状态推送" else "已关闭门诊处方通知",
                Toast.LENGTH_SHORT
            ).show()
        }
        binding.switchNotifyMed.setOnCheckedChangeListener { _, isChecked ->
            MedicationReminderManager.setEnabled(context, isChecked)
            Toast.makeText(
                context,
                if (isChecked) "已开启每日服药提醒（08:00 / 12:30 / 19:30）" else "已关闭服药提醒",
                Toast.LENGTH_SHORT
            ).show()
        }

        // 今日服药打卡：回显状态 + 手动标记已服（通知里也可直接打卡）
        fun refreshMedStatus() {
            binding.tvMedTodayStatus.text = when (MedicationReminderManager.getTodayStatus(context)) {
                "taken" -> "今日服药打卡：✅ 已服"
                "missed" -> "今日服药打卡：⚠️ 漏服"
                else -> "今日服药打卡：未打卡"
            }
        }
        refreshMedStatus()
        binding.btnMedMarkTaken.setOnClickListener {
            MedicationReminderManager.markTaken(context)
            refreshMedStatus()
            Toast.makeText(context, "已记录今日服药打卡 ✅", Toast.LENGTH_SHORT).show()
        }
        binding.switchNotifyQueue.setOnCheckedChangeListener { _, isChecked ->
            QueueReminderManager.setEnabled(context, isChecked)
            Toast.makeText(
                context,
                if (isChecked) "已开启门诊智能叫号弹窗提醒" else "已关闭叫号弹窗提醒",
                Toast.LENGTH_SHORT
            ).show()
        }

        // 门诊待复诊日程状态回显与管理
        fun refreshRevisitStatus() {
            val revisitInfo = com.chunbo.medical.util.RevisitReminderManager.getRevisitInfo(context)
            if (revisitInfo != null && revisitInfo.isPending) {
                binding.tvSettingRevisitHint.text = "下次复诊：${revisitInfo.dateString}（${revisitInfo.patientName} · ${revisitInfo.doctorName}）剩余${revisitInfo.daysRemaining}天"
                binding.btnSettingManageRevisit.text = "管理 >"
            } else {
                binding.tvSettingRevisitHint.text = "暂无待复诊日程"
                binding.btnSettingManageRevisit.text = "已无待办"
            }
        }
        refreshRevisitStatus()

        binding.layoutSettingRevisit.setOnClickListener {
            val info = com.chunbo.medical.util.RevisitReminderManager.getRevisitInfo(context)
            if (info != null && info.isPending) {
                AlertDialog.Builder(context)
                    .setTitle("⏰ 门诊待复诊日程")
                    .setMessage("就诊患者：${info.patientName}\n接诊医生：${info.doctorName} (${info.department})\n初步诊断：${info.diagnosis}\n复诊日期：${info.dateString}\n倒计时：剩余 ${info.daysRemaining} 天\n复诊医嘱：${info.note}")
                    .setPositiveButton("我知道了", null)
                    .setNeutralButton("取消该复诊提醒") { _, _ ->
                        com.chunbo.medical.util.RevisitReminderManager.cancel(context)
                        refreshRevisitStatus()
                        onStateChanged()
                        Toast.makeText(context, "已取消该门诊复诊提醒", Toast.LENGTH_SHORT).show()
                    }
                    .show()
            } else {
                Toast.makeText(context, "当前暂无待复诊日程，可在就诊记录查看电子处方并设置", Toast.LENGTH_SHORT).show()
            }
        }

        // 4. 清理缓存（真实清空内存图片缓存）
        binding.btnClearCache.setOnClickListener {
            com.chunbo.medical.data.api.ImageLoader.clearCache()
            binding.tvCacheSize.text = "0.0 MB (已清理)"
            Toast.makeText(context, "🧹 本地图片缓存已清空", Toast.LENGTH_SHORT).show()
        }

        // 5. 高级网络服务配置（折叠收起）
        binding.etSettingsServerUrl.setText(ApiClient.getBaseUrl())
        var isNetworkExpanded = false
        binding.btnToggleNetworkConfig.setOnClickListener {
            isNetworkExpanded = !isNetworkExpanded
            binding.layoutAdvancedNetworkOptions.visibility = if (isNetworkExpanded) View.VISIBLE else View.GONE
            binding.btnToggleNetworkConfig.text = if (isNetworkExpanded) "🛠️ 高级服务器与网络诊断 ▴" else "🛠️ 高级服务器与局域网网络诊断 ▾"
        }

        binding.btnSetWifi.setOnClickListener {
            binding.etSettingsServerUrl.setText(ApiClient.DEFAULT_BASE_URL)
        }

        binding.btnSetUsb.setOnClickListener {
            binding.etSettingsServerUrl.setText("http://127.0.0.1:8080")
        }

        binding.btnSaveNetwork.setOnClickListener {
            val url = binding.etSettingsServerUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                ApiClient.updateBaseUrl(url)
                Toast.makeText(context, "后端地址已生效: ${ApiClient.getBaseUrl()}", Toast.LENGTH_SHORT).show()
                onStateChanged()
            }
        }

        // 6. 退出登录 / 登录按钮
        binding.btnSettingsLogout.setOnClickListener {
            if (user.isLoggedIn) {
                AlertDialog.Builder(context)
                    .setTitle("确认退出登录")
                    .setMessage("退出当前账号后将恢复为游客状态，就医档案将暂时保存在本地。")
                    .setPositiveButton("确定退出") { _, _ ->
                        UserManager.logout()
                        Toast.makeText(context, "已安全退出当前账号", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                        onStateChanged()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            } else {
                dialog.dismiss()
                onProfileEditRequested()
            }
        }

        binding.btnCloseSettings.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
