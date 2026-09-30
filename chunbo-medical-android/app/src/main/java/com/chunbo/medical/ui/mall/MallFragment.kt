package com.chunbo.medical.ui.mall

import android.app.AlertDialog
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.chunbo.medical.R
import com.chunbo.medical.data.api.ApiClient
import com.chunbo.medical.data.api.ImageLoader
import com.chunbo.medical.data.api.UserManager
import com.chunbo.medical.data.model.MallProduct
import com.chunbo.medical.databinding.DialogCartBottomSheetBinding
import com.chunbo.medical.databinding.DialogCartCheckoutBinding
import com.chunbo.medical.databinding.DialogOrderCreateBinding
import com.chunbo.medical.databinding.FragmentMallBinding
import com.chunbo.medical.databinding.ItemCheckoutProductBinding
import com.chunbo.medical.ui.MainActivity
import com.chunbo.medical.ui.common.MallAuthHelper
import com.chunbo.medical.ui.common.RegionPickerHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MallFragment : Fragment() {

    private var _binding: FragmentMallBinding? = null
    private val binding get() = _binding!!

    private lateinit var productAdapter: ProductAdapter
    private val allProducts = mutableListOf<MallProduct>()
    private var currentKeyword: String = ""
    private var currentCategory: String = "ALL"

    private val userChangeListener: () -> Unit = {
        activity?.runOnUiThread {
            updateCartBar()
        }
    }

    private val cartChangeListener: () -> Unit = {
        activity?.runOnUiThread {
            updateCartBar()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMallBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initRecyclerView()
        initSearchAndFilter()
        initKingKongNav()
        initCartBar()
        initSwipeRefresh()
        updateCartBar()

        UserManager.addListener(userChangeListener)
        CartManager.addListener(cartChangeListener)
        loadProducts()
    }

    private fun initRecyclerView() {
        productAdapter = ProductAdapter(
            onCardClick = { product ->
                showBuyDialog(product)
            },
            onAddCartClick = { product ->
                val stock = product.displayStock
                val currentQty = CartManager.getItems().find { it.product.id == product.id }?.quantity ?: 0
                if (stock > 0 && currentQty >= stock) {
                    Toast.makeText(requireContext(), "该药品已达库存上限 (${stock}件)，无法继续添加", Toast.LENGTH_SHORT).show()
                } else {
                    CartManager.addToCart(product, 1)
                    Toast.makeText(requireContext(), "🛒 已将「${product.displayName}」加入购物车", Toast.LENGTH_SHORT).show()
                }
            }
        )
        val gridLayoutManager = GridLayoutManager(requireContext(), 2)
        binding.rvMallProducts.layoutManager = gridLayoutManager
        binding.rvMallProducts.adapter = productAdapter
    }

    private fun initSearchAndFilter() {
        // 搜索输入框监听
        binding.etMallSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentKeyword = s?.toString()?.trim() ?: ""
                binding.btnClearSearch.visibility = if (currentKeyword.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etMallSearch.setText("")
        }

        binding.btnDoSearch.setOnClickListener {
            applyFilters()
        }

        binding.btnResetCategory.setOnClickListener {
            resetToAllCategories()
        }
    }

    private data class KingKongItem(
        val categoryKey: String,
        val container: View,
        val iconView: View,
        val textView: TextView
    )

    private fun getKingKongItems(): List<KingKongItem> {
        return listOf(
            KingKongItem("处方购药", binding.navKingPrescription, binding.iconKingPrescription, binding.tvKingPrescription),
            KingKongItem("特色贴敷", binding.navKingPlaster, binding.iconKingPlaster, binding.tvKingPlaster),
            KingKongItem("感冒发热", binding.navKingCold, binding.iconKingCold, binding.tvKingCold),
            KingKongItem("儿科用药", binding.navKingChild, binding.iconKingChild, binding.tvKingChild),
            KingKongItem("胃肠消化", binding.navKingStomach, binding.iconKingStomach, binding.tvKingStomach),
            KingKongItem("骨伤镇痛", binding.navKingPain, binding.iconKingPain, binding.tvKingPain),
            KingKongItem("慢病常备", binding.navKingChronic, binding.iconKingChronic, binding.tvKingChronic),
            KingKongItem("滋补养生", binding.navKingNourish, binding.iconKingNourish, binding.tvKingNourish),
            KingKongItem("皮肤外用", binding.navKingSkin, binding.iconKingSkin, binding.tvKingSkin),
            KingKongItem("家庭常备", binding.navKingHousehold, binding.iconKingHousehold, binding.tvKingHousehold),
            KingKongItem("家用器械", binding.navKingDevice, binding.iconKingDevice, binding.tvKingDevice),
            KingKongItem("ALL", binding.navKingAll, binding.iconKingAll, binding.tvKingAll)
        )
    }

    /** 实时更新 12 格金刚区图标选中高亮状态（精致胶囊药丸高亮、文字加粗品牌绿、图标微跃动，去除外框生硬感） */
    private fun updateKingKongSelectedUI() {
        val ctx = context ?: return
        val selectedColor = ContextCompat.getColor(ctx, R.color.primary)
        val defaultColor = ContextCompat.getColor(ctx, R.color.text_primary)
        val activeTextBg = ContextCompat.getDrawable(ctx, R.drawable.bg_king_text_active)
        val inactiveTextBg = ContextCompat.getDrawable(ctx, R.drawable.bg_king_text_inactive)
        val padH = (ctx.resources.displayMetrics.density * 8).toInt()
        val padV = (ctx.resources.displayMetrics.density * 3).toInt()

        getKingKongItems().forEach { item ->
            val isSelected = (item.categoryKey == currentCategory)
            item.container.background = null
            if (isSelected) {
                item.textView.background = activeTextBg
                item.textView.setPadding(padH, padV, padH, padV)
                item.textView.setTextColor(selectedColor)
                item.textView.typeface = Typeface.DEFAULT_BOLD
                item.iconView.scaleX = 1.08f
                item.iconView.scaleY = 1.08f
            } else {
                item.textView.background = inactiveTextBg
                item.textView.setPadding(padH, padV, padH, padV)
                item.textView.setTextColor(defaultColor)
                item.textView.typeface = Typeface.DEFAULT
                item.iconView.scaleX = 1.0f
                item.iconView.scaleY = 1.0f
            }
        }
    }

    /** 电商金刚区 12 格特色专区点击联动（完整对齐中台与 PC 商城 11 大标准分类 + 全部分类） */
    private fun initKingKongNav() {
        binding.navKingPrescription.setOnClickListener {
            selectCategory("处方购药", "🏥 处方购药专区")
        }
        binding.navKingPlaster.setOnClickListener {
            selectCategory("特色贴敷", "🌿 特色贴敷专区")
        }
        binding.navKingCold.setOnClickListener {
            selectCategory("感冒发热", "💊 感冒发热专区")
        }
        binding.navKingChild.setOnClickListener {
            selectCategory("儿科用药", "👶 儿科用药专区")
        }
        binding.navKingStomach.setOnClickListener {
            selectCategory("胃肠消化", "🥣 胃肠消化专区")
        }
        binding.navKingPain.setOnClickListener {
            selectCategory("骨伤镇痛", "🦴 骨伤镇痛专区")
        }
        binding.navKingChronic.setOnClickListener {
            selectCategory("慢病常备", "🫀 慢病常备专区")
        }
        binding.navKingNourish.setOnClickListener {
            selectCategory("滋补养生", "🍵 滋补养生专区")
        }
        binding.navKingSkin.setOnClickListener {
            selectCategory("皮肤外用", "🧴 皮肤外用专区")
        }
        binding.navKingHousehold.setOnClickListener {
            selectCategory("家庭常备", "🩹 家庭常备专区")
        }
        binding.navKingDevice.setOnClickListener {
            selectCategory("家用器械", "🩺 家用器械专区")
        }
        binding.navKingAll.setOnClickListener {
            resetToAllCategories()
        }

        updateKingKongSelectedUI()
    }

    private fun selectCategory(categoryKey: String, categoryTitle: String) {
        // 切换分类时清除旧搜索词，防止 cross-filter 导致0结果
        if (currentKeyword.isNotEmpty()) {
            binding.etMallSearch.setText("")
            currentKeyword = ""
        }
        currentCategory = categoryKey
        binding.tvCurrentCategoryLabel.text = "✨ 正在浏览：$categoryTitle"
        binding.btnResetCategory.visibility = if (categoryKey == "ALL") View.GONE else View.VISIBLE
        updateKingKongSelectedUI()
        applyFilters()
    }

    private fun resetToAllCategories() {
        currentCategory = "ALL"
        binding.tvCurrentCategoryLabel.text = "✨ 正在浏览：全部春播药品"
        binding.btnResetCategory.visibility = View.GONE
        updateKingKongSelectedUI()
        applyFilters()
    }

    /** 底部吸底购物车悬浮条初始化与监听 */
    private fun initCartBar() {
        binding.btnOpenCart.setOnClickListener {
            showCartBottomSheet()
        }
        binding.layoutMallCartBar.setOnClickListener {
            showCartBottomSheet()
        }
        binding.btnBarCheckout.setOnClickListener {
            if (CartManager.getSelectedItems().isEmpty()) {
                if (CartManager.getTotalCount() == 0) {
                    Toast.makeText(requireContext(), "购物车还是空的，请先添加药品", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "请在购物车中勾选需要结算的药品", Toast.LENGTH_SHORT).show()
                    showCartBottomSheet()
                }
            } else {
                showCartCheckoutDialog()
            }
        }
    }

    private fun updateCartBar() {
        val totalCount = CartManager.getTotalCount()
        val selectedCount = CartManager.getSelectedCount()
        val totalPrice = CartManager.getTotalPrice()

        if (totalCount > 0) {
            binding.tvCartBadge.visibility = View.VISIBLE
            binding.tvCartBadge.text = if (totalCount > 99) "99+" else "$totalCount"
        } else {
            binding.tvCartBadge.visibility = View.GONE
        }

        binding.tvBarTotalAmount.text = String.format("¥ %.2f", totalPrice)
        binding.btnBarCheckout.text = "去结算 ($selectedCount)"

        val user = UserManager.getUser()
        if (user.isLoggedIn && user.balance >= totalPrice && totalPrice > 0) {
            binding.tvBarDiscountHint.text = String.format("体验金可全额抵扣 (余额: ¥%.2f)", user.balance)
        } else if (user.isLoggedIn && totalPrice > 0) {
            binding.tvBarDiscountHint.text = String.format("体验金可抵扣 ¥%.2f (余额: ¥%.2f)", kotlin.math.min(user.balance, totalPrice), user.balance)
        } else {
            binding.tvBarDiscountHint.text = "新客立减 · 体验金全额抵扣"
        }
    }

    /** 弹出购物车底部抽屉 BottomSheet */
    private fun showCartBottomSheet() {
        val dialog = BottomSheetDialog(requireContext())
        val dBinding = DialogCartBottomSheetBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        val cartAdapter = CartAdapter {
            updateSheetState(dBinding)
            updateCartBar()
        }
        dBinding.rvCartItems.layoutManager = LinearLayoutManager(requireContext())
        dBinding.rvCartItems.adapter = cartAdapter
        cartAdapter.submitList(CartManager.getItems().toList())
        updateSheetState(dBinding)

        // 全选复选框
        dBinding.cbCartSelectAll.setOnCheckedChangeListener { _, isChecked ->
            CartManager.selectAll(isChecked)
            cartAdapter.submitList(CartManager.getItems().toList())
            updateSheetState(dBinding)
            updateCartBar()
        }

        // 清空购物车
        dBinding.btnCartClear.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("确认清空购物车")
                .setMessage("确定要清空购物车中的所有药品吗？")
                .setPositiveButton("清空") { _, _ ->
                    CartManager.clear()
                    cartAdapter.submitList(emptyList())
                    updateSheetState(dBinding)
                    updateCartBar()
                }
                .setNegativeButton("取消", null)
                .show()
        }

        // 抽屉内结算按钮
        dBinding.btnCartSheetCheckout.setOnClickListener {
            if (CartManager.getSelectedItems().isEmpty()) {
                Toast.makeText(requireContext(), "请勾选需要结算的药品", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            dialog.dismiss()
            showCartCheckoutDialog()
        }

        dialog.show()
    }

    private fun updateSheetState(dBinding: DialogCartBottomSheetBinding) {
        val totalCount = CartManager.getTotalCount()
        val selectedCount = CartManager.getSelectedCount()
        val totalPrice = CartManager.getTotalPrice()

        dBinding.tvCartSheetCount.text = "共 $totalCount 件药品"
        dBinding.tvCartSheetTotal.text = String.format("¥ %.2f", totalPrice)
        dBinding.btnCartSheetCheckout.text = "去结算 ($selectedCount)"
        dBinding.cbCartSelectAll.isChecked = CartManager.isAllSelected() && totalCount > 0

        val isEmpty = totalCount == 0
        dBinding.layoutCartEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        dBinding.rvCartItems.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /** 购物车合并支付结算弹窗 */
    private fun showCartCheckoutDialog() {
        val user = UserManager.getUser()
        val selectedItems = CartManager.getSelectedItems()
        if (selectedItems.isEmpty()) {
            Toast.makeText(requireContext(), "请先勾选需要结算的药品", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(requireContext())
        val dBinding = DialogCartCheckoutBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        // 1. 拆解收货地址为两级：省市区与详细门牌
        val (initialRegion, initialDetail) = RegionPickerHelper.splitAddress(user.address)
        var selectedRegion = initialRegion
        dBinding.tvCheckoutSelectedRegion.text = selectedRegion
        dBinding.etCheckoutDetailAddress.setText(initialDetail)
        dBinding.etCheckoutBuyerName.setText(if (user.isLoggedIn) user.nickname else "健康居民")
        dBinding.etCheckoutBuyerPhone.setText(if (user.isLoggedIn) user.phone else "")

        dBinding.cardCheckoutSelectRegion.setOnClickListener {
            RegionPickerHelper.showRegionPicker(requireContext(), selectedRegion) { newReg ->
                selectedRegion = newReg
                dBinding.tvCheckoutSelectedRegion.text = newReg
            }
        }

        // 2. 支付方式（体验金抵扣 / 微信 / 支付宝模拟支付，微信与支付宝点击直接成功）
        var payMethod = "balance"
        dBinding.rbPayBalance.setOnClickListener { payMethod = "balance"; recalcCheckoutAmounts(dBinding, user, payMethod) }
        dBinding.rbPayWechat.setOnClickListener { payMethod = "wechat"; recalcCheckoutAmounts(dBinding, user, payMethod) }
        dBinding.rbPayAlipay.setOnClickListener { payMethod = "alipay"; recalcCheckoutAmounts(dBinding, user, payMethod) }

        // 3. 渲染药品清单卡片（图片 + 数量增减，同药自动合并，减到 0 移除并联动重算）
        fun renderCheckoutProducts() {
            val container = dBinding.llCheckoutProducts
            container.removeAllViews()
            val nowItems = CartManager.getSelectedItems()
            if (nowItems.isEmpty()) {
                Toast.makeText(requireContext(), "清单已清空，请返回重新勾选药品", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            } else {
                nowItems.forEach { item ->
                    val card = ItemCheckoutProductBinding.inflate(layoutInflater, container, false)
                    val imgUrl = item.product.imageUrl?.takeIf { it.isNotBlank() }
                        ?: CartManager.getProductImage(item.product.id)
                        ?: allProducts.firstOrNull { it.id == item.product.id }?.imageUrl
                    ImageLoader.loadImage(card.ivCheckoutItemImg, imgUrl)
                    card.tvCheckoutItemName.text = item.product.displayName
                    card.tvCheckoutItemSpec.text = item.product.specification?.takeIf { it.isNotBlank() } ?: "标准装 · 春播优选"
                    card.tvCheckoutItemPrice.text = String.format("单价: ¥ %.2f", item.product.displayPrice)
                    card.tvCheckoutItemQty.text = "${item.quantity}"
                    card.btnCheckoutItemMinus.setOnClickListener {
                        if (item.quantity > 1) {
                            CartManager.updateQuantity(item.product.id ?: 0L, item.quantity - 1)
                        } else {
                            CartManager.removeFromCart(item.product.id ?: 0L)
                        }
                        renderCheckoutProducts()
                        recalcCheckoutAmounts(dBinding, user, payMethod)
                    }
                    card.btnCheckoutItemPlus.setOnClickListener {
                        val stock = item.product.displayStock
                        if (stock > 0 && item.quantity >= stock) {
                            Toast.makeText(requireContext(), "已达该药品库存上限 (${stock}件)", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        CartManager.updateQuantity(item.product.id ?: 0L, item.quantity + 1)
                        renderCheckoutProducts()
                        recalcCheckoutAmounts(dBinding, user, payMethod)
                    }
                    container.addView(card.root)
                }
            }
        }

        renderCheckoutProducts()
        recalcCheckoutAmounts(dBinding, user, payMethod)

        // 4. 提交合并订单
        dBinding.btnSubmitCheckout.setOnClickListener {
            val selectedNow = CartManager.getSelectedItems()
            if (selectedNow.isEmpty()) {
                Toast.makeText(requireContext(), "清单为空，请返回重新勾选药品", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                return@setOnClickListener
            }
            val buyerName = dBinding.etCheckoutBuyerName.text.toString().trim()
            val buyerPhone = dBinding.etCheckoutBuyerPhone.text.toString().trim()
            val detail = dBinding.etCheckoutDetailAddress.text.toString().trim()
            val fullAddress = if (detail.isNotEmpty()) "$selectedRegion $detail" else selectedRegion

            if (buyerName.isEmpty()) {
                Toast.makeText(requireContext(), "请填写收货人姓名", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (buyerPhone.isEmpty() || buyerPhone.length < 7) {
                Toast.makeText(requireContext(), "请填写联系手机号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (fullAddress.isEmpty()) {
                Toast.makeText(requireContext(), "请完整设置收货地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalPriceNow = CartManager.getTotalPrice()
            val currentBalance = if (user.isLoggedIn) user.balance else 0.0
            val discountNow = if (payMethod == "balance") kotlin.math.min(totalPriceNow, currentBalance) else 0.0
            val finalNow = (totalPriceNow - discountNow).coerceAtLeast(0.0)
            val balanceAfterNow = (currentBalance - discountNow).coerceAtLeast(0.0)

            // 构建合并 itemsJson
            val itemsJsonList = selectedNow.map {
                mapOf(
                    "id" to (it.product.id ?: 1L),
                    "productName" to it.product.displayName,
                    "specification" to (it.product.specification ?: ""),
                    "quantity" to it.quantity,
                    "price" to it.product.displayPrice
                )
            }
            val itemsJsonStr = com.google.gson.Gson().toJson(itemsJsonList)

            val summaryName = if (selectedNow.size == 1) {
                selectedNow.first().product.displayName
            } else {
                "${selectedNow.first().product.displayName} 等${selectedNow.size}件药品"
            }

            val orderReq = mapOf(
                "username" to if (user.isLoggedIn) user.username else "",
                "productId" to (selectedNow.first().product.id ?: 1L),
                "productName" to summaryName,
                "quantity" to CartManager.getSelectedCount(),
                "unitPrice" to selectedNow.first().product.displayPrice,
                "totalAmount" to totalPriceNow,
                "discountAmount" to discountNow,
                "finalAmount" to finalNow,
                "buyerName" to buyerName,
                "buyerPhone" to buyerPhone,
                "shippingAddress" to fullAddress,
                "clinicName" to "春播网上健康大药房直发",
                "itemsJson" to itemsJsonStr,
                "useBalance" to (payMethod == "balance"),
                "payMethod" to payMethod
            )

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val order = withContext(Dispatchers.IO) {
                        ApiClient.service.createMallOrder(orderReq)
                    }

                    if (fullAddress.isNotEmpty()) {
                        UserManager.updateAddress(fullAddress)
                    }
                    // 余额以服务端为准，下单后统一 refreshBalanceFromServer 刷新，不做本地假扣
                    CartManager.clearSelected()
                    dialog.dismiss()

                    val succMsg = when (payMethod) {
                        "wechat" -> "🎉 模拟微信支付成功！\n订单号: ${order.orderNo ?: "ORD"}\n实付金额: ¥${String.format("%.2f", finalNow)}（演示环境无需真实付款）"
                        "alipay" -> "🎉 模拟支付宝支付成功！\n订单号: ${order.orderNo ?: "ORD"}\n实付金额: ¥${String.format("%.2f", finalNow)}（演示环境无需真实付款）"
                        else -> if (discountNow > 0) {
                            "🎉 合并支付成功！\n订单号: ${order.orderNo ?: "ORD"}\n已使用健康体验金成功抵扣 ¥${String.format("%.2f", discountNow)}，剩余体验金: ¥${String.format("%.2f", balanceAfterNow)}"
                        } else {
                            "🎉 下单成功！订单号: ${order.orderNo ?: "ORD"}"
                        }
                    }
                    Toast.makeText(requireContext(), succMsg, Toast.LENGTH_LONG).show()
                    refreshBalanceFromServer()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "结算支付失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    /** 结算弹窗金额重算：体验金仅在 balance 模式下抵扣，微信/支付宝为全额现金（模拟支付） */
    private fun recalcCheckoutAmounts(dBinding: DialogCartCheckoutBinding, user: com.chunbo.medical.data.model.MallUserSession, payMethod: String) {
        val totalPrice = CartManager.getTotalPrice()
        val currentBalance = if (user.isLoggedIn) user.balance else 0.0
        val discount = if (payMethod == "balance") kotlin.math.min(totalPrice, currentBalance) else 0.0
        val finalAmount = (totalPrice - discount).coerceAtLeast(0.0)
        val balanceAfter = (currentBalance - discount).coerceAtLeast(0.0)

        dBinding.tvCheckoutTotalAmount.text = String.format("¥ %.2f", totalPrice)
        dBinding.tvCheckoutDiscountAmount.text = String.format("-¥ %.2f", discount)
        dBinding.tvCheckoutFinalPay.text = String.format("¥ %.2f", finalAmount)
        dBinding.tvCheckoutBalanceBefore.text = String.format("扣除前余额: ¥%.2f", currentBalance)
        dBinding.tvCheckoutBalanceAfter.text = String.format("抵扣后结余: ¥%.2f", balanceAfter)
        dBinding.btnSubmitCheckout.text = when (payMethod) {
            "wechat" -> String.format("🟢 微信支付 (¥ %.2f)", finalAmount)
            "alipay" -> String.format("🔵 支付宝支付 (¥ %.2f)", finalAmount)
            else -> String.format("使用健康体验金确认合并支付 (¥ %.2f)", finalAmount)
        }
    }

    /** 从 AI 小药师卡片联动跳转选购特定商品 */
    fun applySearchKeyword(keyword: String) {
        val clean = keyword.replace(Regex("\\s*\\([^)]*\\)"), "").trim()
        if (_binding != null) {
            binding.etMallSearch.setText(clean)
        } else {
            currentKeyword = clean
        }
    }

    private fun initSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.primary)
        binding.swipeRefresh.setOnRefreshListener {
            loadProducts()
        }
    }

    fun refresh() {
        loadProducts()
    }

    private fun refreshBalanceFromServer() {
        if (!UserManager.isLoggedIn()) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.service.getUserInfo()
                }
                val userMap = res["user"] as? Map<*, *>
                val balance = (userMap?.get("balance") as? Number)?.toDouble()
                val points = (userMap?.get("points") as? Number)?.toInt()
                if (balance != null || points != null) {
                    UserManager.updateBalanceAndPoints(balance, points)
                }
            } catch (e: Exception) {
                // 忽略刷新异常
            }
        }
    }

    private fun loadProducts() {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val list = withContext(Dispatchers.IO) {
                    ApiClient.service.getMallProducts()
                }
                allProducts.clear()
                allProducts.addAll(list)
                CartManager.registerProducts(list)
                applyFilters()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "获取药品失败: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.layoutEmptyMall.visibility = View.VISIBLE
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    /** 分类别名智能匹配（彻底与 PC 端 matchCategory 保持 100% 绝对一致） */
    private fun matchCategory(productCategory: String?, selectedKey: String): Boolean {
        if (selectedKey.isEmpty() || selectedKey == "ALL" || selectedKey == "all") return true
        val cat = productCategory?.trim() ?: ""
        if (cat.isEmpty()) return false
        if (cat == selectedKey) return true

        val categoryAliases = mapOf(
            "处方购药" to listOf("处方", "处方购药", "处方药"),
            "特色贴敷" to listOf("特色贴敷", "贴敷", "膏药", "贴膏"),
            "感冒发热" to listOf("感冒发热", "感冒发烧", "感冒", "退热", "止咳", "发热", "咳嗽咽痛"),
            "胃肠消化" to listOf("胃肠消化", "肠胃消化", "胃肠", "肠胃", "消化", "腹泻"),
            "儿科用药" to listOf("儿科用药", "儿科健康", "儿科", "小儿健康", "小儿用药", "小儿"),
            "骨伤镇痛" to listOf("骨伤镇痛", "跌打损伤", "骨伤", "跌打", "镇痛", "外伤跌打"),
            "慢病常备" to listOf("慢病常备", "慢病用药", "慢病", "三高", "慢病专区"),
            "滋补养生" to listOf("滋补养生", "滋补调理", "滋补", "养生", "气血"),
            "皮肤外用" to listOf("皮肤外用", "皮肤用药", "皮肤", "外用"),
            "家庭常备" to listOf("家庭常备", "家庭常备药", "生活常备", "家庭药箱"),
            "家用器械" to listOf("家用器械", "医疗器械", "器械")
        )

        val aliases = categoryAliases[selectedKey] ?: listOf(selectedKey)
        return aliases.any { alias -> cat.contains(alias) || alias.contains(cat) }
    }

    private fun applyFilters() {
        val filtered = allProducts.filter { product ->
            val matchesKeyword = if (currentKeyword.isEmpty()) true else {
                val kw = currentKeyword.lowercase()
                product.searchableText.lowercase().contains(kw)
            }

            val matchesCat = matchCategory(product.category, currentCategory)

            matchesKeyword && matchesCat
        }

        productAdapter.submitList(filtered)
        binding.tvProductCount.text = "春播便民药品 (${filtered.size} 件)"
        binding.layoutEmptyMall.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        if (filtered.isEmpty()) {
            binding.tvEmptyMallText.text = if (currentKeyword.isNotEmpty()) "未找到与 \"$currentKeyword\" 相关的药品" else "该分类下暂无药品"
        }
    }

    /** 单品购买弹窗（同样支持省市区二级级联选择与体验金抵扣） */
    private fun showBuyDialog(product: MallProduct) {
        val user = UserManager.getUser()
        val dialogBinding = DialogOrderCreateBinding.inflate(layoutInflater)

        dialogBinding.dialogOrderProductTitle.text = product.displayName
        val price = product.displayPrice
        dialogBinding.dialogOrderUnitPrice.text = String.format("单价: ¥%.2f", price)
        dialogBinding.dialogOrderProductSpec.text = "规格: ${product.specification ?: "标准盒装"} · ${product.category ?: "春播正品"}"

        ImageLoader.loadImage(dialogBinding.dialogOrderProductImg, product.imageUrl)

        var currentQty = 1
        dialogBinding.dialogEditQty.setText("1")
        dialogBinding.dialogEditBuyerName.setText(if (user.isLoggedIn) user.nickname else "健康居民")
        dialogBinding.dialogEditBuyerPhone.setText(if (user.isLoggedIn) user.phone else "")

        // 拆解地址为省市区与详细门牌
        val (initialRegion, initialDetail) = RegionPickerHelper.splitAddress(user.address)
        var selectedRegion = initialRegion
        dialogBinding.dialogTvSelectedRegion.text = selectedRegion
        dialogBinding.dialogEditAddress.setText(initialDetail)

        dialogBinding.dialogCardSelectRegion.setOnClickListener {
            RegionPickerHelper.showRegionPicker(requireContext(), selectedRegion) { newReg ->
                selectedRegion = newReg
                dialogBinding.dialogTvSelectedRegion.text = newReg
            }
        }

        fun updateTotal() {
            dialogBinding.dialogTotalAmount.text = String.format("¥ %.2f", price * currentQty)
        }
        updateTotal()

        dialogBinding.btnQtyMinus.setOnClickListener {
            if (currentQty > 1) {
                currentQty--
                dialogBinding.dialogEditQty.setText(currentQty.toString())
                updateTotal()
            }
        }

        dialogBinding.btnQtyPlus.setOnClickListener {
            if (currentQty < product.displayStock) {
                currentQty++
                dialogBinding.dialogEditQty.setText(currentQty.toString())
                updateTotal()
            }
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelOrder.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSubmitOrder.setOnClickListener {
            val buyerName = dialogBinding.dialogEditBuyerName.text.toString().trim()
            val buyerPhone = dialogBinding.dialogEditBuyerPhone.text.toString().trim()
            val detail = dialogBinding.dialogEditAddress.text.toString().trim()
            val fullAddress = if (detail.isNotEmpty()) "$selectedRegion $detail" else selectedRegion

            if (buyerName.isEmpty()) {
                Toast.makeText(requireContext(), "请填写收货人姓名", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (buyerPhone.isEmpty() || buyerPhone.length < 7) {
                Toast.makeText(requireContext(), "请填写正确的联系手机号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (fullAddress.isEmpty()) {
                Toast.makeText(requireContext(), "请填写配送收货地址", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val totalAmount = price * currentQty
            val discount = if (user.isLoggedIn) kotlin.math.min(totalAmount, user.balance) else 0.0
            val finalAmount = (totalAmount - discount).coerceAtLeast(0.0)

            val req = mapOf(
                "username" to if (user.isLoggedIn) user.username else "",
                "productId" to (product.id ?: 1L),
                "productName" to product.displayName,
                "quantity" to currentQty,
                "unitPrice" to price,
                "totalAmount" to totalAmount,
                "discountAmount" to discount,
                "finalAmount" to finalAmount,
                "buyerName" to buyerName,
                "buyerPhone" to buyerPhone,
                "shippingAddress" to fullAddress,
                "clinicName" to "春播网上健康大药房直发",
                "itemsJson" to "[{\"id\": ${product.id ?: 1}, \"productName\": \"${product.displayName}\", \"quantity\": $currentQty, \"price\": $price}]",
                "useBalance" to true
            )

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val order = withContext(Dispatchers.IO) {
                        ApiClient.service.createMallOrder(req)
                    }
                    if (fullAddress.isNotEmpty()) {
                        UserManager.updateAddress(fullAddress)
                    }
                    // 余额以服务端为准，下单后统一 refreshBalanceFromServer 刷新，不做本地假扣
                    dialog.dismiss()
                    val succNotice = if (discount > 0) {
                        "🎉 下单成功！订单号: ${order.orderNo ?: "ORD"}\n已使用健康体验金抵扣 ¥${String.format("%.2f", discount)}，送药上门中"
                    } else {
                        "🎉 下单成功！订单号: ${order.orderNo ?: "ORD"}"
                    }
                    Toast.makeText(requireContext(), succNotice, Toast.LENGTH_LONG).show()
                    refreshBalanceFromServer()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "下单失败: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }

        dialog.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        UserManager.removeListener(userChangeListener)
        CartManager.removeListener(cartChangeListener)
        _binding = null
    }
}
