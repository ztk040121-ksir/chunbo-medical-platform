package com.chunbo.medical.data.api

import android.content.Context
import android.content.SharedPreferences
import com.chunbo.medical.data.model.MallUserSession

object UserManager {
    private const val PREF_NAME = "chunbo_user_pref"
    private const val KEY_LOGGED_IN = "key_logged_in"
    private const val KEY_TOKEN = "key_token"
    private const val KEY_USERNAME = "key_username"
    private const val KEY_NICKNAME = "key_nickname"
    private const val KEY_AVATAR = "key_avatar"
    private const val KEY_PHONE = "key_phone"
    private const val KEY_ADDRESS = "key_address"
    private const val KEY_BALANCE = "key_balance"
    private const val KEY_POINTS = "key_points"

    private var prefs: SharedPreferences? = null
    private val listeners = mutableListOf<() -> Unit>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val token = prefs?.getString(KEY_TOKEN, null)
        if (!token.isNullOrEmpty() && isLoggedIn()) {
            ApiClient.setAuthToken(token)
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

    private fun notifyListeners() {
        listeners.forEach { it.invoke() }
    }

    fun isLoggedIn(): Boolean = prefs?.getBoolean(KEY_LOGGED_IN, false) ?: false

    fun saveUser(
        token: String,
        username: String,
        nickname: String?,
        phone: String?,
        address: String?,
        balance: Double?,
        points: Int?,
        avatar: String? = null
    ) {
        prefs?.edit()?.apply {
            putBoolean(KEY_LOGGED_IN, true)
            putString(KEY_TOKEN, token)
            putString(KEY_USERNAME, username)
            putString(KEY_NICKNAME, nickname ?: username)
            putString(KEY_AVATAR, if (!avatar.isNullOrBlank()) avatar else (prefs?.getString(KEY_AVATAR, null) ?: "avatar_resident_1"))
            putString(KEY_PHONE, phone ?: "")
            putString(KEY_ADDRESS, address ?: "")
            putString(KEY_BALANCE, formatMoney(balance ?: DEFAULT_BALANCE))
            putInt(KEY_POINTS, points ?: DEFAULT_POINTS)
            apply()
        }
        ApiClient.setAuthToken(token)
        notifyListeners()
    }

    fun updateAddress(newAddress: String) {
        prefs?.edit()?.putString(KEY_ADDRESS, newAddress)?.apply()
        notifyListeners()
    }

    fun updateAvatar(newAvatar: String) {
        prefs?.edit()?.putString(KEY_AVATAR, newAvatar)?.apply()
        notifyListeners()
    }

    fun updateProfile(nickname: String?, phone: String?, address: String?, avatar: String?) {
        val editor = prefs?.edit()
        if (!nickname.isNullOrBlank()) editor?.putString(KEY_NICKNAME, nickname)
        if (!phone.isNullOrBlank()) editor?.putString(KEY_PHONE, phone)
        if (address != null) editor?.putString(KEY_ADDRESS, address)
        if (!avatar.isNullOrBlank()) editor?.putString(KEY_AVATAR, avatar)
        editor?.apply()
        notifyListeners()
    }

    /** 用服务端返回的最新余额/积分刷新本地缓存（避免本地假扣导致账户展示失真） */
    fun updateBalanceAndPoints(balance: Double?, points: Int?) {
        val editor = prefs?.edit()
        if (balance != null) editor?.putString(KEY_BALANCE, formatMoney(balance))
        if (points != null) editor?.putInt(KEY_POINTS, points)
        editor?.apply()
        notifyListeners()
    }

    fun getIdCard(): String {
        return prefs?.getString("key_id_card", "") ?: ""
    }

    fun saveIdCard(idCard: String) {
        prefs?.edit()?.putString("key_id_card", idCard.trim())?.apply()
    }

    fun getUser(): MallUserSession {
        val loggedIn = isLoggedIn()
        return MallUserSession(
            isLoggedIn = loggedIn,
            token = prefs?.getString(KEY_TOKEN, "") ?: "",
            username = prefs?.getString(KEY_USERNAME, "游客") ?: "游客",
            nickname = prefs?.getString(KEY_NICKNAME, if (loggedIn) "春播会员" else "点击登录") ?: "点击登录",
            avatar = prefs?.getString(KEY_AVATAR, "avatar_resident_1") ?: "avatar_resident_1",
            phone = prefs?.getString(KEY_PHONE, "") ?: "",
            address = prefs?.getString(KEY_ADDRESS, "") ?: "",
            balance = if (loggedIn) (prefs?.getString(KEY_BALANCE, null)?.toDoubleOrNull() ?: DEFAULT_BALANCE) else 0.0,
            points = if (loggedIn) (prefs?.getInt(KEY_POINTS, DEFAULT_POINTS) ?: DEFAULT_POINTS) else 0
        )
    }

    fun logout() {
        prefs?.edit()?.clear()?.apply()
        ApiClient.setAuthToken(null)
        notifyListeners()
    }

    /** 金额统一以两位小数字符串落盘，避免 Float 精度漂移 */
    private fun formatMoney(value: Double): String =
        String.format(java.util.Locale.US, "%.2f", value)

    /** 新人健康体验金默认值（与后端注册口径一致） */
    const val DEFAULT_BALANCE = 200.0
    const val DEFAULT_POINTS = 200
}
