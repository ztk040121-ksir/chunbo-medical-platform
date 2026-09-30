package com.chunbo.medical.ui.patient

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chunbo.medical.R
import com.chunbo.medical.databinding.ItemPreConsultMessageBinding

data class PreConsultChatMessage(
    val role: String, // "assistant" or "user"
    var content: String,
    val imageUri: String? = null,
    /** 助手消息附带的快捷追问选项（展示在气泡下方，点击即发送） */
    var quickReplies: List<String> = emptyList()
)

class PreConsultChatAdapter(
    /** 就诊人姓名，用于在用户气泡上方显示真实姓名而非固定"就诊人" */
    var patientName: String = "就诊人",
    /** 快捷选项点击回调（由弹窗注入，点击即作为用户消息发送） */
    var onQuickReplyClick: ((String) -> Unit)? = null
) : RecyclerView.Adapter<PreConsultChatAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<PreConsultChatMessage>()

    fun setMessages(newMessages: List<PreConsultChatMessage>) {
        messages.clear()
        messages.addAll(newMessages)
        notifyDataSetChanged()
    }

    fun addMessage(msg: PreConsultChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun getMessages(): List<PreConsultChatMessage> = messages

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val binding = ItemPreConsultMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return MessageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isNotEmpty()) {
            holder.updateText(messages[position].content)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = messages.size

    inner class MessageViewHolder(private val binding: ItemPreConsultMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun updateText(content: String) {
            binding.tvAssistantText.text = com.chunbo.medical.ui.common.MarkdownFormatter.format(content, binding.tvAssistantText.context)
        }

        fun bind(msg: PreConsultChatMessage) {
            if (msg.role == "assistant") {
                binding.layoutAssistantMsg.visibility = View.VISIBLE
                binding.layoutUserMsg.visibility = View.GONE
                updateText(msg.content)
                // 快捷追问选项渲染进对话流（气泡下方）：ChipGroup 自动流式折行，绝不横向挤压变形
                binding.layoutPreQuickReplies.removeAllViews()
                if (msg.quickReplies.isNotEmpty()) {
                    binding.layoutPreQuickReplies.visibility = View.VISIBLE
                    val density = binding.root.context.resources.displayMetrics.density
                    msg.quickReplies.forEach { text ->
                        val chip = TextView(binding.root.context).apply {
                            this.text = text
                            textSize = 12f
                            setTextColor(context.getColor(R.color.on_primary_container))
                            setBackgroundResource(R.drawable.bg_chip_green)
                            setPadding(
                                (12 * density).toInt(), (6 * density).toInt(),
                                (12 * density).toInt(), (6 * density).toInt()
                            )
                            typeface = Typeface.DEFAULT_BOLD
                            layoutParams = ViewGroup.MarginLayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            setOnClickListener { onQuickReplyClick?.invoke(text) }
                        }
                        binding.layoutPreQuickReplies.addView(chip)
                    }
                } else {
                    binding.layoutPreQuickReplies.visibility = View.GONE
                }
            } else {
                binding.layoutAssistantMsg.visibility = View.GONE
                binding.layoutUserMsg.visibility = View.VISIBLE
                binding.tvUserText.text = msg.content
                // 动态显示真实就诊人姓名（替换默认"就诊人"占位）
                binding.tvUserLabel.text = patientName
                // 用户上传的图片：显示真实图片，无图时隐藏
                if (!msg.imageUri.isNullOrBlank()) {
                    binding.ivUserImage.visibility = View.VISIBLE
                    com.chunbo.medical.data.api.ImageLoader.loadLocal(binding.ivUserImage, msg.imageUri)
                } else {
                    binding.ivUserImage.visibility = View.GONE
                }
            }
        }
    }
}
