package com.chunbo.medical.ui.mall

import android.content.Context
import android.content.SharedPreferences
import com.chunbo.medical.data.model.MallProduct
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class CartItem(
    val product: MallProduct,
    var quantity: Int,
    var isSelected: Boolean = true
)

object CartManager {

    private const val PREF_NAME = "chunbo_cart_pref"
    private const val KEY_ITEMS = "key_cart_items"

    private var prefs: SharedPreferences? = null
    private val cartItems = mutableMapOf<Long, CartItem>()
    private val listeners = mutableListOf<() -> Unit>()

    /** 药品全量档案缓存（用于自动补全购物车已有商品的图片与规格） */
    private val catalogMap = mutableMapOf<Long, MallProduct>()

    fun registerProducts(products: List<MallProduct>) {
        products.forEach { p ->
            p.id?.let { id -> catalogMap[id] = p }
        }
        var changed = false
        cartItems.keys.toList().forEach { pid ->
            val item = cartItems[pid] ?: return@forEach
            val catalog = catalogMap[pid] ?: return@forEach
            val needsImg = item.product.imageUrl.isNullOrBlank() && !catalog.imageUrl.isNullOrBlank()
            val needsCat = item.product.category.isNullOrBlank() && !catalog.category.isNullOrBlank()
            val needsSpec = item.product.specification.isNullOrBlank() && !catalog.specification.isNullOrBlank()
            if (needsImg || needsCat || needsSpec) {
                val updatedProd = item.product.copy(
                    imageUrl = if (needsImg) catalog.imageUrl else item.product.imageUrl,
                    category = if (needsCat) catalog.category else item.product.category,
                    specification = if (needsSpec) catalog.specification else item.product.specification
                )
                cartItems[pid] = item.copy(product = updatedProd)
                changed = true
            }
        }
        if (changed) {
            notifyChanged()
        }
    }

    fun getProductImage(productId: Long?): String? {
        if (productId == null) return null
        return catalogMap[productId]?.imageUrl
    }

    /** 初始化并恢复本地持久化的购物车（App 重启不丢） */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        loadFromDisk()
    }

    private fun loadFromDisk() {
        val json = prefs?.getString(KEY_ITEMS, null) ?: return
        if (json.isBlank()) return
        try {
            val type = object : TypeToken<List<CartItem>>() {}.type
            val list: List<CartItem> = Gson().fromJson(json, type) ?: emptyList()
            cartItems.clear()
            list.forEach { it.product.id?.let { id -> cartItems[id] = it } }
        } catch (e: Exception) {
            // 反序列化失败忽略，购物车从空开始
        }
    }

    private fun saveToDisk() {
        try {
            val json = Gson().toJson(cartItems.values.toList())
            prefs?.edit()?.putString(KEY_ITEMS, json)?.apply()
        } catch (e: Exception) {
            // 保存失败忽略
        }
    }

    fun addListener(listener: () -> Unit) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.forEach { it.invoke() }
        saveToDisk()
    }

    fun addToCart(product: MallProduct, quantity: Int = 1) {
        val pid = product.id ?: return
        val finalProd = if (product.imageUrl.isNullOrBlank()) {
            val catalog = catalogMap[pid]
            if (catalog != null && !catalog.imageUrl.isNullOrBlank()) {
                product.copy(imageUrl = catalog.imageUrl)
            } else product
        } else {
            catalogMap[pid] = product
            product
        }

        val current = cartItems[pid]
        if (current != null) {
            current.quantity += quantity
            if (current.product.imageUrl.isNullOrBlank() && !finalProd.imageUrl.isNullOrBlank()) {
                cartItems[pid] = current.copy(product = finalProd)
            }
        } else {
            cartItems[pid] = CartItem(finalProd, quantity, isSelected = true)
        }
        notifyChanged()
    }

    fun updateQuantity(productId: Long, qty: Int) {
        val item = cartItems[productId] ?: return
        if (qty <= 0) {
            cartItems.remove(productId)
        } else {
            item.quantity = qty
        }
        notifyChanged()
    }

    fun removeFromCart(productId: Long) {
        cartItems.remove(productId)
        notifyChanged()
    }

    fun toggleSelection(productId: Long) {
        cartItems[productId]?.let {
            it.isSelected = !it.isSelected
            notifyChanged()
        }
    }

    fun selectAll(select: Boolean) {
        cartItems.values.forEach { it.isSelected = select }
        notifyChanged()
    }

    fun clearCart() {
        cartItems.clear()
        notifyChanged()
    }

    fun clearSelected() {
        val toRemove = cartItems.filter { it.value.isSelected }.keys
        toRemove.forEach { cartItems.remove(it) }
        notifyChanged()
    }

    fun getAllItems(): List<CartItem> = cartItems.values.toList()

    fun getSelectedItems(): List<CartItem> = cartItems.values.filter { it.isSelected }

    fun getTotalCount(): Int = cartItems.values.sumOf { it.quantity }

    fun getSelectedCount(): Int = cartItems.values.filter { it.isSelected }.sumOf { it.quantity }

    fun getItems(): List<CartItem> = getAllItems()

    fun isAllSelected(): Boolean = cartItems.isNotEmpty() && cartItems.values.all { it.isSelected }

    fun clear() = clearCart()

    fun getTotalPrice(): Double = cartItems.values
        .filter { it.isSelected }
        .sumOf { it.product.displayPrice * it.quantity }
}
