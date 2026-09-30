package com.chunbo.medical.ui.common

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import com.chunbo.medical.data.api.ApiClient
import com.chunbo.medical.data.api.UserManager
import com.chunbo.medical.databinding.DialogMallLoginBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 商城会员登录 / 注册公共弹窗。
 * 抽取自 MallFragment 与 ProfileFragment 重复的登录逻辑，消除数百行重复。
 * 登录/注册成功后回调 onAuthSuccess，由调用方刷新各自的 UI。
 */
object MallAuthHelper {

    fun showLoginDialog(
        context: Context,
        scope: LifecycleCoroutineScope,
        onAuthSuccess: () -> Unit
    ) {
        val dialogBinding = DialogMallLoginBinding.inflate(LayoutInflater.from(context))
        var isLoginMode = true

        dialogBinding.btnModeLogin.setOnClickListener {
            isLoginMode = true
            dialogBinding.layoutRegisterExtra.visibility = View.GONE
            dialogBinding.tvLoginDialogTitle.text = "春播健康商城会员登录"
            dialogBinding.btnDoAuth.text = "立即登录"
        }

        dialogBinding.btnModeRegister.setOnClickListener {
            isLoginMode = false
            dialogBinding.layoutRegisterExtra.visibility = View.VISIBLE
            dialogBinding.tvLoginDialogTitle.text = "新用户极速注册"
            dialogBinding.btnDoAuth.text = "立即注册并领取体验金"
        }

        val dialog = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelLogin.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnDoAuth.setOnClickListener {
            val account = dialogBinding.etLoginUsername.text.toString().trim()
            val password = dialogBinding.etLoginPassword.text.toString().trim()

            if (account.isEmpty() || password.isEmpty()) {
                Toast.makeText(context, "请输入账号和密码", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            scope.launch {
                try {
                    if (isLoginMode) {
                        val body = mapOf("username" to account, "password" to password)
                        val res = withContext(Dispatchers.IO) { ApiClient.service.login(body) }
                        if (res["success"] == true) {
                            val token = res["token"]?.toString() ?: ""
                            val userMap = res["user"] as? Map<*, *>
                            val nickname = userMap?.get("nickname")?.toString() ?: account
                            val phone = userMap?.get("phone")?.toString() ?: ""
                            val address = userMap?.get("address")?.toString() ?: ""
                            val balance = (userMap?.get("balance") as? Number)?.toDouble() ?: UserManager.DEFAULT_BALANCE
                            val points = (userMap?.get("points") as? Number)?.toInt() ?: UserManager.DEFAULT_POINTS
                            val avatar = userMap?.get("avatar")?.toString() ?: "avatar_resident_1"

                            UserManager.saveUser(token, account, nickname, phone, address, balance, points, avatar)

                            dialog.dismiss()
                            Toast.makeText(context, "欢迎回来，${nickname}！", Toast.LENGTH_SHORT).show()
                            onAuthSuccess()
                        } else {
                            val msg = res["message"]?.toString() ?: "登录失败"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        val nickname = dialogBinding.etRegNickname.text.toString().trim()
                        val regPhone = dialogBinding.etRegPhone.text.toString().trim()
                        val address = dialogBinding.etRegAddress.text.toString().trim()
                        if (regPhone.isEmpty() || !regPhone.matches(Regex("1\\d{10}"))) {
                            Toast.makeText(context, "请填写正确的 11 位联系手机号", Toast.LENGTH_SHORT).show()
                            return@launch
                        }
                        val body = mutableMapOf(
                            "username" to account,
                            "password" to password,
                            "phone" to regPhone,
                            "nickname" to if (nickname.isNotEmpty()) nickname else "春播会员",
                            "address" to address
                        )
                        val res = withContext(Dispatchers.IO) { ApiClient.service.register(body) }
                        if (res["success"] == true) {
                            val token = res["token"]?.toString() ?: ""
                            val userMap = res["user"] as? Map<*, *>
                            val dName = userMap?.get("nickname")?.toString() ?: nickname
                            val dPhone = userMap?.get("phone")?.toString() ?: ""
                            val dAddress = userMap?.get("address")?.toString() ?: address
                            val dAvatar = userMap?.get("avatar")?.toString() ?: "avatar_resident_1"
                            // 余额/积分以后端真实返回为准（新用户一般送 200 体验金），仅在字段缺失时兜底默认值
                            val balance = (userMap?.get("balance") as? Number)?.toDouble() ?: UserManager.DEFAULT_BALANCE
                            val points = (userMap?.get("points") as? Number)?.toInt() ?: UserManager.DEFAULT_POINTS

                            UserManager.saveUser(
                                token = token,
                                username = account,
                                nickname = dName,
                                phone = dPhone,
                                address = dAddress,
                                balance = balance,
                                points = points,
                                avatar = dAvatar
                            )

                            dialog.dismiss()
                            Toast.makeText(context, "注册成功！已获得${balance.toInt()}元健康体验金！", Toast.LENGTH_SHORT).show()
                            onAuthSuccess()
                        } else {
                            val msg = res["message"]?.toString() ?: "注册失败"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "请求失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }
}
