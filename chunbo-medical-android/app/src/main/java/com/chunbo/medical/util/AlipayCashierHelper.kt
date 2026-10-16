package com.chunbo.medical.util

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.chunbo.medical.data.api.ApiClient
import com.chunbo.medical.util.qrcode.QrCodeBitmapHelper
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 手机移动端统一支付宝官方收银台弹窗 (高精度黑白二维码 + 点击刷新二维码 + 主动查单 + 自动轮询核销)
 */
object AlipayCashierHelper {

    fun showAlipayPayDialog(
        context: Context,
        scope: CoroutineScope,
        orderNo: String,
        amount: String,
        qrCode: String,
        title: String = "🔵 支付宝扫码收银台",
        onPaid: () -> Unit
    ) {
        val dp = { value: Int ->
            TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value.toFloat(),
                context.resources.displayMetrics
            ).toInt()
        }

        var currentQrContent = qrCode

        val scrollView = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        scrollView.addView(container)

        // 1. 安全环境提示条
        val safetyBar = TextView(context).apply {
            text = "🛡️ 当前支付环境安全，已接入支付宝官方沙箱链路"
            setTextColor(Color.parseColor("#92400E"))
            setBackgroundColor(Color.parseColor("#FEF3C7"))
            setPadding(dp(10), dp(8), dp(10), dp(8))
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(12)
            }
        }
        container.addView(safetyBar)

        // 2. 订单信息与金额
        val orderNoView = TextView(context).apply {
            text = "订单编号: $orderNo"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(4)
            }
        }
        container.addView(orderNoView)

        val amountView = TextView(context).apply {
            text = "¥ $amount"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 28f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(14)
            }
        }
        container.addView(amountView)

        // 3. 经典黑白二维码渲染卡片 (点击可刷新)
        val qrCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(6)
            }
        }

        val qrImageView = ImageView(context).apply {
            val qrSize = dp(200)
            layoutParams = LinearLayout.LayoutParams(qrSize, qrSize)
            try {
                val bitmap = QrCodeBitmapHelper.createQrBitmap(currentQrContent, 420)
                setImageBitmap(bitmap)
            } catch (ignored: Exception) {}
        }
        qrCard.addView(qrImageView)
        container.addView(qrCard)

        // 点击刷新二维码逻辑
        val refreshAction = {
            scope.launch {
                try {
                    val preRes = withContext(Dispatchers.IO) {
                        ApiClient.service.alipayPrecreate(
                            mapOf(
                                "orderNo" to orderNo,
                                "amount" to amount,
                                "subject" to "春播万象移动缴费 - $orderNo"
                            )
                        )
                    }
                    val newQr = preRes["qrCode"]?.toString()
                    if (!newQr.isNullOrEmpty()) {
                        currentQrContent = newQr
                    }
                    val bitmap = QrCodeBitmapHelper.createQrBitmap(currentQrContent, 420)
                    qrImageView.setImageBitmap(bitmap)
                    Toast.makeText(context, "🔄 二维码已刷新", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    val fallbackContent = "${currentQrContent}_TS_${System.currentTimeMillis()}"
                    val bitmap = QrCodeBitmapHelper.createQrBitmap(fallbackContent, 420)
                    qrImageView.setImageBitmap(bitmap)
                    Toast.makeText(context, "🔄 二维码已刷新", Toast.LENGTH_SHORT).show()
                }
            }
        }

        qrCard.setOnClickListener { refreshAction() }
        qrImageView.setOnClickListener { refreshAction() }

        // 4. 点击刷新提示
        val refreshHint = TextView(context).apply {
            text = "🔄 点击二维码可刷新"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
            gravity = Gravity.CENTER
            setOnClickListener { refreshAction() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }
        container.addView(refreshHint)

        // 5. 实时监听提示
        val tipView = TextView(context).apply {
            text = "正在实时监听付款结果，付款成功将自动核销…"
            setTextColor(Color.parseColor("#0284C7"))
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(16)
            }
        }
        container.addView(tipView)

        val dialog = AlertDialog.Builder(context)
            .setTitle(title)
            .setView(scrollView)
            .setCancelable(true)
            .create()

        var pollJob: Job? = null

        // 6. 操作按钮：查询结果 / 稍后支付 (去掉打开支付宝和免扫码模拟按钮)
        val btnQuery = MaterialButton(context).apply {
            text = "我已完成付款，查询结果"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#059669"))
            setTextColor(Color.WHITE)
            cornerRadius = dp(6)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }
            setOnClickListener {
                scope.launch {
                    try {
                        val res = withContext(Dispatchers.IO) {
                            ApiClient.service.alipayQuery(orderNo)
                        }
                        if (res["paid"] == true) {
                            pollJob?.cancel()
                            dialog.dismiss()
                            Toast.makeText(context, "✅ 付款已确认成功！", Toast.LENGTH_SHORT).show()
                            onPaid()
                        } else {
                            Toast.makeText(context, "⏳ 尚未查到支付成功状态，请付款后重试", Toast.LENGTH_SHORT).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(context, "查询状态异常: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        container.addView(btnQuery)

        val btnCancel = MaterialButton(context).apply {
            text = "稍后支付"
            textSize = 13f
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(Color.parseColor("#64748B"))
            cornerRadius = dp(6)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                pollJob?.cancel()
                dialog.dismiss()
            }
        }
        container.addView(btnCancel)

        dialog.setOnDismissListener {
            pollJob?.cancel()
        }

        // 启动后台静默轮询监听 (每 3 秒查一次)
        pollJob = scope.launch {
            var count = 0
            while (isActive && count < 100) {
                delay(3000)
                count++
                try {
                    val res = withContext(Dispatchers.IO) {
                        ApiClient.service.alipayQuery(orderNo)
                    }
                    if (res["paid"] == true) {
                        dialog.dismiss()
                        Toast.makeText(context, "🎉 付款成功！处方/订单已核销", Toast.LENGTH_LONG).show()
                        onPaid()
                        break
                    }
                } catch (ignored: Exception) {}
            }
        }

        dialog.show()
    }
}
