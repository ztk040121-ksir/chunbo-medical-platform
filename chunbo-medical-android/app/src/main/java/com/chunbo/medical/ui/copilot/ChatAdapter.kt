package com.chunbo.medical.ui.copilot

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.chunbo.medical.R
import com.chunbo.medical.data.model.ChatMessage
import com.chunbo.medical.databinding.ItemChatMessageBinding
import com.google.android.material.button.MaterialButton

class ChatAdapter(
    private val onChipClick: (String) -> Unit,
    private val onBuyProductClick: ((com.chunbo.medical.data.model.MallProductRecommendation) -> Unit)? = null,
    private val onAddToCart: ((com.chunbo.medical.data.model.MallProductRecommendation) -> Unit)? = null
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    private val messages = mutableListOf<ChatMessage>()

    fun addMessage(msg: ChatMessage) {
        messages.add(msg)
        notifyItemInserted(messages.size - 1)
    }

    fun removeLast() {
        if (messages.isNotEmpty()) {
            val idx = messages.size - 1
            messages.removeAt(idx)
            notifyItemRemoved(idx)
        }
    }

    fun updateLast(updater: (ChatMessage) -> Unit) {
        if (messages.isNotEmpty()) {
            val idx = messages.size - 1
            updater(messages[idx])
            notifyItemChanged(idx)
        }
    }

    fun submitList(list: List<ChatMessage>) {
        messages.clear()
        messages.addAll(list)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatMessageBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(messages[position])
    }

    override fun getItemCount(): Int = messages.size

    inner class ChatViewHolder(private val binding: ItemChatMessageBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(message: ChatMessage) {
            val context = binding.root.context
            if (message.role == "user") {
                binding.layoutUserMsg.visibility = View.VISIBLE
                binding.layoutAiMsg.visibility = View.GONE
                binding.tvUserBubble.text = message.content
                val user = com.chunbo.medical.data.api.UserManager.getUser()
                com.chunbo.medical.util.AvatarHelper.loadAvatar(binding.ivUserAvatar, user.avatar)
                // 用户上传的图片：显示真实图片，无图时隐藏
                if (!message.imageUri.isNullOrBlank()) {
                    binding.ivUserImage.visibility = View.VISIBLE
                    com.chunbo.medical.data.api.ImageLoader.loadLocal(binding.ivUserImage, message.imageUri)
                } else {
                    binding.ivUserImage.visibility = View.GONE
                }
                // 订单咨询等场景：用户消息附带的商品卡片（含真实图片）
                renderProductCards(binding.layoutUserProducts, message.mallProducts)
                binding.layoutUserProducts.visibility =
                    if (message.mallProducts.isNotEmpty()) View.VISIBLE else View.GONE
            } else {
                binding.layoutUserMsg.visibility = View.GONE
                binding.layoutAiMsg.visibility = View.VISIBLE

                // 思考占位文案（防止首个 Token 到达前气泡坍缩）
                if (message.content.isBlank() && message.isStreaming) {
                    binding.tvAiBubble.text = "⚡ 正在思考中..."
                    binding.tvAiBubble.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                } else {
                    // 应用专为移动端打造的 Markdown 与医学富文本排版器
                    binding.tvAiBubble.text = com.chunbo.medical.ui.common.MarkdownFormatter.format(message.content, context)
                    binding.tvAiBubble.setTextColor(ContextCompat.getColor(context, R.color.chat_ai_text))
                }

                // 处理过程展示（步骤内容由后端真实事件下发，实际调用了什么就展示什么）：
                // 【核心要求】：仅在生成中 (isStreaming == true) 显示实时展开过程卡片；
                // 生成结束后，自动收起，仅保留一个小巧折叠标签，点击可切换展开/收起！
                if (message.isStreaming && message.processSteps.isNotEmpty()) {
                    binding.layoutProcess.visibility = View.VISIBLE
                    binding.tvProcessSummary.visibility = View.GONE
                    binding.tvProcessSteps.text = message.processSteps.joinToString("\n")
                } else if (!message.isStreaming && message.processSteps.isNotEmpty()) {
                    if (message.isProcessExpanded) {
                        binding.layoutProcess.visibility = View.VISIBLE
                        binding.tvProcessSummary.visibility = View.VISIBLE
                        binding.tvProcessSummary.text = "✓ 处理过程 ▴"
                        binding.tvProcessSteps.text = message.processSteps.joinToString("\n")
                    } else {
                        binding.layoutProcess.visibility = View.GONE
                        binding.tvProcessSummary.visibility = View.VISIBLE
                        binding.tvProcessSummary.text = "✓ 处理过程 ▾"
                    }
                    binding.tvProcessSummary.setOnClickListener {
                        message.isProcessExpanded = !message.isProcessExpanded
                        notifyItemChanged(bindingAdapterPosition)
                    }
                } else {
                    binding.layoutProcess.visibility = View.GONE
                    binding.tvProcessSummary.visibility = View.GONE
                }

                // RAG 基层知识库命中标签展示
                if (message.ragKnowledgeBases.isNotEmpty()) {
                    binding.tvRagTags.visibility = View.VISIBLE
                    binding.tvRagTags.text = "📚 基层诊疗与合理用药规范参考: " + message.ragKnowledgeBases.joinToString("、")
                } else {
                    binding.tvRagTags.visibility = View.GONE
                }

                // 推荐方剂参考卡片（AI 开具的处方/方剂建议）
                binding.layoutRxItems.removeAllViews()
                if (message.rxItems.isNotEmpty()) {
                    binding.layoutRxCards.visibility = View.VISIBLE
                    for (rx in message.rxItems) {
                        val tv = android.widget.TextView(context).apply {
                            text = "• $rx"
                            setTextColor(ContextCompat.getColor(context, R.color.chat_ai_text))
                            textSize = 13f
                            setLineSpacing(2f, 1.0f)
                        }
                        binding.layoutRxItems.addView(tv)
                    }
                } else {
                    binding.layoutRxCards.visibility = View.GONE
                }

                // 用户消息附带的商品卡片容器：AI 消息下强制清空隐藏（仅用户订单消息使用）
                binding.layoutUserProducts.removeAllViews()
                binding.layoutUserProducts.visibility = View.GONE

                // 春播便民健康商城对症药品推荐卡片（AI 回复附带）
                renderProductCards(binding.layoutMallProducts, message.mallProducts)
                binding.layoutMallProducts.visibility =
                    if (message.mallProducts.isNotEmpty()) View.VISIBLE else View.GONE

                // 快捷选项胶囊
                binding.layoutQuickChips.removeAllViews()
                if (message.quickReplies.isNotEmpty()) {
                    binding.layoutQuickChips.visibility = View.VISIBLE
                    for (chipText in message.quickReplies) {
                        val btn = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                            text = chipText
                            textSize = 12f
                            setPadding(20, 6, 20, 6)
                            setTextColor(ContextCompat.getColor(context, R.color.primary))
                            strokeColor = ContextCompat.getColorStateList(context, R.color.primary_light)
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            ).apply {
                                topMargin = 8
                            }
                            setOnClickListener { onChipClick(chipText) }
                        }
                        binding.layoutQuickChips.addView(btn)
                    }
                } else {
                    binding.layoutQuickChips.visibility = View.GONE
                }
            }
        }

        /** 渲染商品卡片列表（带真实商品图；空字段对应控件隐藏），AI 推荐与用户订单消息共用 */
        private fun renderProductCards(container: LinearLayout, products: List<com.chunbo.medical.data.model.MallProductRecommendation>) {
            container.removeAllViews()
            if (products.isEmpty()) return
            val inflater = LayoutInflater.from(container.context)
            val context = container.context
            for (prod in products) {
                val cardBinding = com.chunbo.medical.databinding.ItemChatMallProductBinding.inflate(inflater, container, false)
                // 商品真实图片（无图时保留占位图）
                com.chunbo.medical.data.api.ImageLoader.loadImage(cardBinding.ivMallImg, prod.imageUrl)
                cardBinding.tvMallProductName.text = prod.productName
                cardBinding.tvMallSpec.text = prod.specification
                if (prod.category.isNotBlank()) {
                    cardBinding.tvMallCategory.visibility = View.VISIBLE
                    cardBinding.tvMallCategory.text = prod.category
                } else {
                    cardBinding.tvMallCategory.visibility = View.GONE
                }
                if (prod.csPitch.isNotBlank()) {
                    cardBinding.tvMallPitch.visibility = View.VISIBLE
                    cardBinding.tvMallPitch.text = prod.csPitch
                } else {
                    cardBinding.tvMallPitch.visibility = View.GONE
                }
                cardBinding.tvMallPrice.text = "¥ " + prod.price
                cardBinding.btnMallAddCart.setOnClickListener {
                    onAddToCart?.invoke(prod)
                }
                cardBinding.btnMallBuy.setOnClickListener {
                    onBuyProductClick?.invoke(prod)
                }
                cardBinding.root.setOnClickListener {
                    onBuyProductClick?.invoke(prod)
                }
                container.addView(cardBinding.root)
            }
        }
    }
}
