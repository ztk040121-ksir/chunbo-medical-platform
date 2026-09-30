package com.chunbo.medical.data.api

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object ImageLoader {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 8

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    /** 清空内存图片缓存（供「清理缓存」真实调用） */
    fun clearCache() {
        memoryCache.evictAll()
    }

    fun loadImage(imageView: ImageView, rawUrl: String?, placeholderResId: Int? = null) {
        if (rawUrl.isNullOrBlank()) {
            placeholderResId?.let { imageView.setImageResource(it) }
            return
        }

        // 格式化图片地址：如果以 / 开头，自动拼装 BaseUrl
        val fullUrl = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl
        } else {
            val base = ApiClient.getBaseUrl().trimEnd('/')
            val path = rawUrl.trimStart('/')
            "$base/$path"
        }

        imageView.tag = fullUrl

        val cached = memoryCache.get(fullUrl)
        if (cached != null) {
            imageView.setImageBitmap(cached)
            return
        }

        placeholderResId?.let { imageView.setImageResource(it) }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(fullUrl).build()
                val response = httpClient.newCall(request).execute()
                if (response.isSuccessful) {
                    val bytes = response.body?.bytes()
                    val bitmap = bytes?.let { decodeSampledBitmap(it, 1024, 1024) }
                    if (bitmap != null) {
                        memoryCache.put(fullUrl, bitmap)
                        withContext(Dispatchers.Main) {
                            if (imageView.tag == fullUrl) {
                                imageView.setImageBitmap(bitmap)
                            }
                        }
                    }
                }
            } catch (ignored: Exception) {
                // 网络或解析异常时保持占位图
            }
        }
    }

    /** 加载本地图片（相册选图/拍照的 content:// 或 file:// uri），不进内存缓存 */
    fun loadLocal(imageView: ImageView, uriStr: String?, placeholderResId: Int? = null) {
        if (uriStr.isNullOrBlank()) {
            placeholderResId?.let { imageView.setImageResource(it) }
            return
        }
        try {
            val uri = android.net.Uri.parse(uriStr)
            val ctx = imageView.context
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    val bitmap = bytes?.let { decodeSampledBitmap(it, 1024, 1024) }
                    withContext(Dispatchers.Main) {
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                        } else {
                            placeholderResId?.let { imageView.setImageResource(it) }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        placeholderResId?.let { imageView.setImageResource(it) }
                    }
                }
            }
        } catch (e: Exception) {
            placeholderResId?.let { imageView.setImageResource(it) }
        }
    }

    /** 按目标尺寸降采样解码，避免大图 OOM */
    private fun decodeSampledBitmap(data: ByteArray, reqWidth: Int, reqHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val opts = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds, reqWidth, reqHeight)
        }
        return try {
            BitmapFactory.decodeByteArray(data, 0, data.size, opts)
        } catch (e: Exception) {
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
