package com.chunbo.medical.ui.mall

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chunbo.medical.data.api.ImageLoader
import com.chunbo.medical.data.model.MallProduct
import com.chunbo.medical.databinding.ItemMallProductCardBinding

class ProductAdapter(
    private val onCardClick: (MallProduct) -> Unit,
    private val onAddCartClick: (MallProduct) -> Unit
) : RecyclerView.Adapter<ProductAdapter.ProductViewHolder>() {

    private val items = mutableListOf<MallProduct>()

    fun submitList(newList: List<MallProduct>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ProductViewHolder {
        val binding = ItemMallProductCardBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ProductViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ProductViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ProductViewHolder(private val binding: ItemMallProductCardBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(product: MallProduct) {
            binding.tvCardProductName.text = product.displayName
            val specText = buildString {
                product.specification?.let { append(it) }
                val mfg = product.manufacturer ?: product.brand
                if (!mfg.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(mfg)
                }
            }
            binding.tvCardSpec.text = if (specText.isNotBlank()) specText else "标准盒装 · 春播优选"

            val pitch = product.displayPitch
            binding.tvCardPitch.text = if (!pitch.isNullOrBlank()) pitch else "执业药师质检 · 正品溯源保障"

            val price = product.displayPrice
            binding.tvCardPrice.text = String.format("%.2f", price)

            // 原价划线显示（约1.25倍）
            val origPrice = (price * 1.25).coerceAtLeast(price + 5.0)
            binding.tvCardOriginalPrice.text = String.format("¥%.1f", origPrice)
            binding.tvCardOriginalPrice.paintFlags = binding.tvCardOriginalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG

            binding.tvCardStock.text = "库存 ${product.displayStock}"
            binding.tvCardCategoryBadge.text = product.displayCategory

            ImageLoader.loadImage(binding.ivProductImage, product.imageUrl)

            // 点击卡片进入详情/单品购买
            binding.root.setOnClickListener {
                onCardClick(product)
            }

            // 点击加购物车悬浮按钮
            binding.btnCardAddCart.setOnClickListener {
                onAddCartClick(product)
            }
        }
    }
}
