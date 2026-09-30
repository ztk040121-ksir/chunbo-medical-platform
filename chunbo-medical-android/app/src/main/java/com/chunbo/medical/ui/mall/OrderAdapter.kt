package com.chunbo.medical.ui.mall

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chunbo.medical.data.api.ImageLoader
import com.chunbo.medical.data.model.MallOrder
import com.chunbo.medical.databinding.ItemMallOrderBinding

class OrderAdapter(
    private val onDetailClick: (MallOrder) -> Unit = {},
    private val onReorderClick: (MallOrder) -> Unit = {},
    /** 商品图片解析器：按 itemsJson 里的商品 id 取真实图片 url（由调用方注入商品映射） */
    private val productImageResolver: ((Long?) -> String?)? = null
) : RecyclerView.Adapter<OrderAdapter.OrderViewHolder>() {

    private val items = mutableListOf<MallOrder>()

    fun submitList(newList: List<MallOrder>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OrderViewHolder {
        val binding = ItemMallOrderBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OrderViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OrderViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class OrderViewHolder(private val binding: ItemMallOrderBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(order: MallOrder) {
            binding.tvOrderNo.text = "单号: ${order.orderNo ?: ("ORD" + (order.id ?: 1))}"
            binding.tvOrderStatus.text = when {
                order.status?.contains("已送达") == true -> "已送达"
                order.status?.contains("已发货") == true -> "已发货"
                else -> "待发货"
            }
            val prodName = parseOrderItems(order.productName, order.itemsJson)
            binding.tvOrderProductTitle.text = prodName
            // 首件商品真实图片（按 itemsJson 的商品 id 解析）
            val firstItemId = parseFirstItemId(order.itemsJson)
            val imgUrl = productImageResolver?.invoke(firstItemId)
            ImageLoader.loadImage(binding.ivOrderProductImg, imgUrl)
            val qty = parseOrderQty(order.quantity, order.itemsJson)
            binding.tvOrderQty.text = "共 $qty 件 · "
            val amount = order.finalAmount ?: order.totalAmount ?: 0.0
            binding.tvOrderAmount.text = String.format("实付: ¥%.2f", amount)
            val buyer = order.buyerName ?: "春播健康居民"
            val phone = if (!order.buyerPhone.isNullOrBlank()) " (${order.buyerPhone})" else ""
            val addr = if (!order.shippingAddress.isNullOrBlank()) " · ${order.shippingAddress}" else ""
            binding.tvOrderRecipient.text = "收件: $buyer$phone$addr"

            // 点击卡片或查看详情按钮
            binding.root.setOnClickListener {
                onDetailClick(order)
            }
            binding.btnItemViewDetail.setOnClickListener {
                onDetailClick(order)
            }
            binding.btnItemReorder.setOnClickListener {
                onReorderClick(order)
            }
        }

        private fun parseOrderItems(prodName: String?, itemsJson: String?): String {
            if (!prodName.isNullOrBlank()) return prodName
            if (itemsJson.isNullOrBlank()) return "春播精选健康药品"
            try {
                val listType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                val items: List<Map<String, Any>> = com.google.gson.Gson().fromJson(itemsJson, listType)
                if (!items.isNullOrEmpty()) {
                    // 每个药品两行：第一行药品名（规格），第二行数量与单价；药与药之间空行分隔，避免挤在一起
                    return items.joinToString("\n\n") { item ->
                        val name = item["productName"] ?: item["name"] ?: "药品"
                        val spec = item["specification"] ?: item["spec"] ?: ""
                        val qty = (item["quantity"] as? Number)?.toInt() ?: 1
                        val price = item["price"] ?: ""
                        buildString {
                            append("$name")
                            if (spec.toString().isNotBlank()) append("（$spec）")
                            append("\n× $qty 盒")
                            if (price.toString().isNotBlank()) append(" · ¥$price/盒")
                        }
                    }
                }
            } catch (e: Exception) {
                val match = Regex("\"productName\"\\s*:\\s*\"([^\"]+)\"").find(itemsJson)
                if (match != null) return match.groupValues[1]
            }
            return "春播健康药品"
        }

        /** 从订单明细 json 取第一件商品 id（用于解析商品图片） */
        private fun parseFirstItemId(itemsJson: String?): Long? {
            if (itemsJson.isNullOrBlank()) return null
            return try {
                val listType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                val items: List<Map<String, Any>> = com.google.gson.Gson().fromJson(itemsJson, listType)
                (items.firstOrNull()?.get("id") as? Number)?.toLong()
            } catch (e: Exception) {
                null
            }
        }

        /** 从订单明细 json 统计商品总件数 */
        private fun parseOrderQty(qty: Int?, itemsJson: String?): Int {
            if (qty != null && qty > 0) return qty
            if (itemsJson.isNullOrBlank()) return 1
            return try {
                val listType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                val items: List<Map<String, Any>> = com.google.gson.Gson().fromJson(itemsJson, listType)
                if (items.isEmpty()) 1 else items.sumOf { ((it["quantity"] as? Number)?.toInt() ?: 1) }
            } catch (e: Exception) {
                1
            }
        }
    }
}
