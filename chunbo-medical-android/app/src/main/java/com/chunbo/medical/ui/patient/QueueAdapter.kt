package com.chunbo.medical.ui.patient

import android.app.AlertDialog
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chunbo.medical.R
import com.chunbo.medical.data.model.Registration
import com.chunbo.medical.databinding.ItemQueuePatientBinding
import com.chunbo.medical.util.qrcode.QrCodeBitmapHelper

class QueueAdapter(
    private val onCallClick: ((Registration) -> Unit)? = null,
    private val onFinishClick: ((Registration) -> Unit)? = null,
    private val onDetailClick: ((Registration) -> Unit)? = null
) : RecyclerView.Adapter<QueueAdapter.QueueViewHolder>() {

    private val items = mutableListOf<Registration>()

    fun submitList(newList: List<Registration>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QueueViewHolder {
        val binding = ItemQueuePatientBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return QueueViewHolder(binding)
    }

    override fun onBindViewHolder(holder: QueueViewHolder, position: Int) {
        holder.bind(items[position])
        // 点击卡片查看详情 / 修改信息 / 继续补充问诊
        val item = items[position]
        holder.itemView.setOnClickListener { onDetailClick?.invoke(item) }
    }

    override fun getItemCount(): Int = items.size

    inner class QueueViewHolder(private val binding: ItemQueuePatientBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Registration) {
            val context = binding.root.context
            val qNum = item.queueNumber ?: String.format("%02d", item.queueNo ?: (bindingAdapterPosition + 1))
            binding.tvQueueNo.text = qNum

            binding.tvPatientName.text = item.patientName ?: "便民患者"
            binding.tvPatientGenderAge.text = "${item.gender ?: "未知"} ${item.age ?: "--"}岁"
            binding.tvRegNo.text = "单号: ${item.regNo ?: "--"}"

            // 挂号来源区分：手机端在线预约 vs 现场挂号
            val regType = item.regType ?: "手机端在线预约"
            binding.tvSourceTag.text = if (regType.contains("手机") || regType.contains("在线") || regType.contains("预约")) {
                "📱 手机在线预约"
            } else {
                "🏥 现场挂号"
            }
            if (binding.tvSourceTag.text.toString().contains("手机")) {
                binding.tvSourceTag.setBackgroundResource(R.drawable.bg_badge_emerald)
                binding.tvSourceTag.setTextColor(ContextCompat.getColor(context, R.color.primary))
            } else {
                binding.tvSourceTag.setBackgroundResource(R.drawable.bg_badge_blue)
                binding.tvSourceTag.setTextColor(ContextCompat.getColor(context, R.color.status_blue))
            }

            // 主诉与病症
            val complaint = item.symptoms ?: item.chiefComplaint
            binding.tvComplaint.text = if (!complaint.isNullOrBlank()) "主诉: $complaint" else "主诉: 常规复诊与开方"

            // 科室与医生
            val dept = item.department ?: "全科门诊"
            val doc = item.doctorName ?: "社区坐诊专家"
            binding.tvDeptDoctor.text = "科室: $dept | 接诊: $doc"

            // 门诊层级与诊费
            val fee = item.fee
            if (fee != null && fee > 0) {
                val level = if (fee >= 30.0) "专家门诊" else "普通门诊"
                binding.tvFeeBadge.text = "$level ¥${String.format("%.2f", fee)}"
                binding.tvFeeBadge.visibility = View.VISIBLE
            } else {
                binding.tvFeeBadge.visibility = View.GONE
            }

            // AI 预问诊导医摘要 (格式化过滤，杜绝原始 JSON 乱码与代码括号展示)
            val preConsult = formatPreConsult(item.preConsultationData, complaint)
            if (!preConsult.isNullOrBlank()) {
                binding.tvPreConsultSummary.text = preConsult
                binding.tvPreConsultSummary.visibility = View.VISIBLE
            } else {
                binding.tvPreConsultSummary.visibility = View.GONE
            }

            // 挂号状态处理
            val status = item.status ?: "待签到"
            binding.tvStatusBadge.text = status

            when (status) {
                "待签到" -> {
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_amber)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_amber))

                    // 待签到状态下显示签到二维码（患者到院后扫电脑端屏幕上的二维码签到，不可自助一键签到）
                    binding.layoutQrCheckin.visibility = View.VISIBLE
                    val signToken = "CHUNBO_SIGN:${item.id}:${item.regNo ?: ""}"
                    try {
                        val qrBitmap = QrCodeBitmapHelper.createQrBitmap(signToken, 180)
                        binding.ivQrCode.setImageBitmap(qrBitmap)
                    } catch (e: Exception) {
                        binding.ivQrCode.setImageBitmap(null)
                    }

                    // 点击二维码弹出放大对话框便于现场出示
                    binding.ivQrCode.setOnClickListener {
                        showBigQrDialog(context, item, signToken)
                    }
                }
                "待诊" -> {
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_emerald)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.primary))
                    binding.layoutQrCheckin.visibility = View.GONE
                }
                "就诊中" -> {
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_blue)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_blue))
                    binding.layoutQrCheckin.visibility = View.GONE
                }
                "已完成", "已诊", "已结诊", "已收费" -> {
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_emerald)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.primary))
                    binding.layoutQrCheckin.visibility = View.GONE
                }
                "已退", "已取消" -> {
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_red)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_red))
                    binding.layoutQrCheckin.visibility = View.GONE
                }
                else -> {
                    binding.tvStatusBadge.setBackgroundResource(R.drawable.bg_badge_amber)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.status_amber))
                    binding.layoutQrCheckin.visibility = View.GONE
                }
            }
        }

        private fun formatPreConsult(raw: String?, complaint: String?): String? {
            if (raw.isNullOrBlank()) return null
            val trimmed = raw.trim()
            if (trimmed.startsWith("{")) {
                try {
                    val obj = com.google.gson.JsonParser.parseString(trimmed).asJsonObject
                    val dept = obj.get("department")?.asString ?: ""
                    val chief = obj.get("chiefComplaint")?.asString ?: ""
                    val tcm = obj.get("tcmPattern")?.asString ?: ""
                    val present = obj.get("presentIllness")?.asString ?: ""

                    val sb = StringBuilder("🤖 AI预问诊分诊建议：")
                    if (dept.isNotEmpty()) sb.append("建议就诊【$dept】· ")
                    if (tcm.isNotEmpty()) sb.append("证候建议: $tcm · ")

                    val text = if (chief.isNotEmpty() && chief != complaint) chief else present
                    if (text.isNotEmpty() && text != complaint) {
                        sb.append(text.take(45))
                        if (text.length > 45) sb.append("...")
                    } else {
                        sb.append("已完成AI病史采集与初步分诊，供坐诊医生参考")
                    }
                    return sb.toString()
                } catch (e: Exception) {
                    val deptMatch = Regex("\"department\"\\s*:\\s*\"([^\"]+)\"").find(trimmed)?.groupValues?.get(1)
                    if (!deptMatch.isNullOrEmpty()) {
                        return "🤖 AI预问诊建议：推荐就诊【$deptMatch】，已完成初步症状采集"
                    }
                    return "🤖 AI预问诊建议：已完成AI症状初筛与智能分诊"
                }
            } else {
                return if (trimmed.length > 60) trimmed.take(60) + "..." else trimmed
            }
        }

        private fun showBigQrDialog(context: android.content.Context, item: Registration, token: String) {
            val iv = ImageView(context).apply {
                setPadding(40, 40, 40, 40)
            }
            try {
                val bmp = QrCodeBitmapHelper.createQrBitmap(token, 300)
                iv.setImageBitmap(bmp)
            } catch (e: Exception) {
                // ignore
            }
            AlertDialog.Builder(context)
                .setTitle("排队签到二维码 · ${item.patientName ?: "患者"}")
                .setMessage("就诊排号: ${item.queueNumber ?: "--"} (${item.department ?: "全科"})\n可出示给诊室医生扫码或在诊室扫码机打卡入队")
                .setView(iv)
                .setPositiveButton("已知悉", null)
                .show()
        }
    }
}
