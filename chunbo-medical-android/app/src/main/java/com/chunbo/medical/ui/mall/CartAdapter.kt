package com.chunbo.medical.ui.mall

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.chunbo.medical.data.api.ImageLoader
import com.chunbo.medical.databinding.ItemCartProductBinding

class CartAdapter(
    private val onItemChanged: () -> Unit
) : RecyclerView.Adapter<CartAdapter.CartViewHolder>() {

    private val items = mutableListOf<CartItem>()

    fun submitList(newList: List<CartItem>) {
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartProductBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class CartViewHolder(private val binding: ItemCartProductBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CartItem) {
            val product = item.product
            binding.tvCartItemName.text = product.displayName
            val specText = buildString {
                product.specification?.let { append(it) }
                val mfg = product.manufacturer ?: product.brand
                if (!mfg.isNullOrBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(mfg)
                }
            }
            binding.tvCartItemSpec.text = if (specText.isNotBlank()) specText else "标准装 · 春播优选"
            binding.tvCartItemPrice.text = String.format("¥ %.2f", product.displayPrice)
            binding.tvCartItemQty.text = "${item.quantity}"

            binding.cbCartItemSelect.isChecked = item.isSelected

            val imgUrl = product.imageUrl?.takeIf { it.isNotBlank() } ?: CartManager.getProductImage(product.id)
            ImageLoader.loadImage(binding.ivCartItemImg, imgUrl)

            binding.cbCartItemSelect.setOnCheckedChangeListener { _, isChecked ->
                item.isSelected = isChecked
                onItemChanged()
            }

            binding.btnCartItemMinus.setOnClickListener {
                if (item.quantity > 1) {
                    // CartItem 与 CartManager 内是同一对象，updateQuantity 已更新 quantity，勿再手动改（否则 ±2）
                    CartManager.updateQuantity(product.id ?: 0L, item.quantity - 1)
                    binding.tvCartItemQty.text = "${item.quantity}"
                    onItemChanged()
                } else {
                    CartManager.removeFromCart(product.id ?: 0L)
                    items.removeAt(adapterPosition)
                    notifyItemRemoved(adapterPosition)
                    onItemChanged()
                }
            }

            binding.btnCartItemPlus.setOnClickListener {
                val stock = product.displayStock
                if (stock > 0 && item.quantity >= stock) {
                    android.widget.Toast.makeText(binding.root.context, "已达该药品库存上限 (${stock}件)", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                CartManager.updateQuantity(product.id ?: 0L, item.quantity + 1)
                binding.tvCartItemQty.text = "${item.quantity}"
                onItemChanged()
            }

            binding.btnCartItemDelete.setOnClickListener {
                CartManager.removeFromCart(product.id ?: 0L)
                items.removeAt(adapterPosition)
                notifyItemRemoved(adapterPosition)
                onItemChanged()
            }
        }
    }
}
