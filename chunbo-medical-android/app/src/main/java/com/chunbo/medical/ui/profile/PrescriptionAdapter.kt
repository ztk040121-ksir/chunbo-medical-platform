package com.chunbo.medical.ui.profile

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chunbo.medical.R
import com.chunbo.medical.data.model.PrescriptionWrapper
import com.chunbo.medical.databinding.ItemPrescriptionBinding

class PrescriptionAdapter(
    private val onItemClick: ((PrescriptionWrapper) -> Unit)? = null,
    private val onAiAnalyzeClick: ((PrescriptionWrapper) -> Unit)? = null
) : RecyclerView.Adapter<PrescriptionAdapter.PrescriptionViewHolder>() {

    private val items = mutableListOf<PrescriptionWrapper>()

    fun submitList(newList: List<PrescriptionWrapper>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PrescriptionViewHolder {
        val binding = ItemPrescriptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return PrescriptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PrescriptionViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class PrescriptionViewHolder(private val binding: ItemPrescriptionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(wrapper: PrescriptionWrapper) {
            val rx = wrapper.prescription ?: return
            val context = binding.root.context

            // 就诊人姓名与基本信息
            val pName = rx.patientName ?: "就诊患者"
            val gender = rx.gender ?: ""
            val age = if (rx.age != null && rx.age > 0) "${rx.age}岁" else ""
            val info = listOf(gender, age).filter { it.isNotBlank() }.joinToString(" ")
            binding.tvRxPatientName.text = if (info.isNotBlank()) "$pName ($info)" else pName

            // 状态徽章判定：区分是纯处方还是门诊就医档案
            val isPrescription = !rx.prescriptionNo.isNullOrBlank() && rx.prescriptionNo.startsWith("RX")
            val statusRaw = rx.status ?: ""
            val statusDisplay = when {
                statusRaw == "COMPLETED" || statusRaw == "已诊" || statusRaw == "已完诊" -> "已就诊 · 完诊"
                statusRaw == "CONSULTING" || statusRaw == "就诊中" -> "门诊 · 就诊中"
                statusRaw == "WAITING" || statusRaw == "候诊中" -> "门诊 · 候诊中"
                statusRaw == "REGISTERED" || statusRaw == "已预约" || statusRaw == "待签到" -> "便民预约 · 待签到"
                statusRaw == "EXPIRED" || statusRaw == "过号" -> "门诊 · 已过号"
                isPrescription -> "电子处方 · 已审核"
                else -> if (statusRaw.isNotBlank()) statusRaw else "门诊就医档案"
            }
            binding.tvRxStatus.text = statusDisplay
            if (statusDisplay.contains("已过号")) {
                binding.tvRxStatus.setBackgroundResource(R.drawable.bg_badge_amber)
            } else {
                binding.tvRxStatus.setBackgroundResource(R.drawable.bg_badge_emerald)
            }

            // 诊断与医生
            val diag = rx.diagnosis?.ifBlank { rx.symptoms ?: "常规门诊就医对症调护" } ?: (rx.symptoms ?: "常规门诊就医对症调护")
            binding.tvRxDiagnosis.text = if (isPrescription) "临床诊断: $diag" else "主诉与病症: $diag"

            val rxNo = rx.prescriptionNo ?: ("REG" + (rx.id ?: System.currentTimeMillis()))
            val doc = rx.doctorName?.ifBlank { "全科主治医师" } ?: "全科主治医师"
            val noPrefix = if (isPrescription) "处方单号" else "就诊单号"
            binding.tvRxNo.text = "$noPrefix: $rxNo | 接诊医生: $doc"

            // 药品明细排版
            val medItems = wrapper.items
            if (!medItems.isNullOrEmpty()) {
                val medText = medItems.mapIndexed { idx, it ->
                    val name = it.medicineName ?: "药品"
                    val spec = if (!it.specification.isNullOrBlank()) " (${it.specification})" else ""
                    val qty = "${it.quantity ?: 1} 盒"
                    val usage = it.displayUsage
                    val usagePart = if (usage.isNotBlank()) " · $usage" else ""
                    "${idx + 1}. $name$spec$usagePart × $qty"
                }.joinToString("\n")
                binding.tvRxMedicines.text = medText
                binding.tvRxAiAnalyze.visibility = View.VISIBLE
            } else {
                val advice = rx.aiAdvice?.ifBlank { null } ?: "已由医生接诊处置完成，遵医嘱按时调护与复诊。"
                binding.tvRxMedicines.text = "🏥 门诊诊查处置：$advice"
                binding.tvRxAiAnalyze.visibility = View.GONE
            }

            // 开方/就诊时间与金额
            val time = rx.createTime?.replace("T", " ")?.take(16) ?: "今日就诊"
            binding.tvRxDate.text = "就诊时间: $time"
            val price = rx.displayPrice
            binding.tvRxPrice.text = if (price > 0) String.format("费用: ¥%.2f", price) else "普通医保定额统筹"

            binding.root.setOnClickListener {
                onItemClick?.invoke(wrapper)
            }
            binding.tvRxAiAnalyze.setOnClickListener {
                onAiAnalyzeClick?.invoke(wrapper)
            }
        }
    }
}
