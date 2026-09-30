package com.chunbo.medical.data.api

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val PREFS_NAME = "chunbo_medical_prefs"
    private const val KEY_BASE_URL = "key_base_url"
    private const val KEY_AUTH_TOKEN = "key_auth_token"

    // 首次联调默认后端地址（用户电脑的实际 WLAN IP，真机调试必须指向局域网 IP）。
    // 该地址仅为「首次默认值」：可在「设置 → 高级服务器与网络诊断」中修改并持久化，换网后无需改代码。
    const val DEFAULT_BASE_URL = "http://192.168.0.238:8080/"

    private var currentBaseUrl: String = DEFAULT_BASE_URL
    private var cachedToken: String? = null
    private var prefs: SharedPreferences? = null

    private val authInterceptor = Interceptor { chain ->
        val original = chain.request()
        val path = original.url.encodedPath

        // 登录/注册接口直接放行，不附加 Token 避免死循环
        if (path.contains("/api/auth/login") ||
            path.contains("/api/mall/user/login") ||
            path.contains("/api/mall/user/register")
        ) {
            return@Interceptor chain.proceed(original)
        }

        // 仅在已登录（持有 token）时附加 Authorization，游客态不自动登录、不伪造身份。
        // 手机端所有接口均已列入后端 JwtFilter 白名单，无 token 也能正常访问。
        val token = cachedToken
        val requestBuilder = original.newBuilder()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }
        chain.proceed(requestBuilder.build())
    }

    val okHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** 专用于 SSE 流式对话的客户端（严禁添加 Level.BODY，防止全量缓冲破坏流式打字效果） */
    val streamOkHttpClient by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.HEADERS
        }
        OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(180, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    private var retrofit: Retrofit? = null
    private var _service: MedicalApiService? = null
    val service: MedicalApiService
        get() {
            if (_service == null) {
                buildRetrofit()
            }
            return _service!!
        }

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        currentBaseUrl = prefs?.getString(KEY_BASE_URL, DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL
        cachedToken = prefs?.getString(KEY_AUTH_TOKEN, null)
        if (!currentBaseUrl.endsWith("/")) {
            currentBaseUrl += "/"
        }
        buildRetrofit()
    }

    fun getBaseUrl(): String = currentBaseUrl

    fun updateBaseUrl(newUrl: String) {
        var formatted = newUrl.trim()
        if (!formatted.startsWith("http://") && !formatted.startsWith("https://")) {
            formatted = "http://$formatted"
        }
        if (!formatted.endsWith("/")) {
            formatted += "/"
        }
        currentBaseUrl = formatted
        cachedToken = null
        prefs?.edit()
            ?.putString(KEY_BASE_URL, currentBaseUrl)
            ?.remove(KEY_AUTH_TOKEN)
            ?.apply()
        buildRetrofit()
    }

    fun setAuthToken(token: String?) {
        cachedToken = token
        if (token != null) {
            prefs?.edit()?.putString(KEY_AUTH_TOKEN, token)?.apply()
        } else {
            prefs?.edit()?.remove(KEY_AUTH_TOKEN)?.apply()
        }
    }

    private fun buildRetrofit() {
        retrofit = Retrofit.Builder()
            .baseUrl(currentBaseUrl)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        _service = retrofit!!.create(MedicalApiService::class.java)
    }
}
