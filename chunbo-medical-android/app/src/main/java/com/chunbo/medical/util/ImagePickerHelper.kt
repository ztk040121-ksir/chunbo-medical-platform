package com.chunbo.medical.util

import android.content.Context
import android.net.Uri
import com.chunbo.medical.data.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * 图片上传工具：读取相册/拍照的 Uri → 上传后端 /api/upload/file → 回调 fileId。
 * 选图由调用方用 ActivityResultContracts 处理，这里只负责上传。
 */
object ImagePickerHelper {

    /** 创建系统相机拍照的输出 uri（经 FileProvider 共享，拍完照回调 success 后可直接读取上传） */
    fun createCaptureOutputUri(context: Context): Uri {
        val dir = java.io.File(context.cacheDir, "camera").apply { mkdirs() }
        val file = java.io.File(dir, "capture_${System.currentTimeMillis()}.jpg")
        return androidx.core.content.FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file
        )
    }

    fun uploadImage(
        context: Context,
        scope: CoroutineScope,
        uri: Uri,
        onResult: (fileId: String) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val fileId = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: throw IOException("无法读取所选图片")
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val reqBody = bytes.toRequestBody(mime.toMediaType())
                    val part = MultipartBody.Part.createFormData("file", "chat_image.jpg", reqBody)
                    val resp = ApiClient.service.uploadFile(part)
                    if (resp["success"] == true) resp["fileId"]?.toString() else null
                }
                if (!fileId.isNullOrBlank()) {
                    onResult(fileId)
                } else {
                    onError("图片上传失败，请重试")
                }
            } catch (e: Exception) {
                onError("图片上传失败：${e.message}")
            }
        }
    }
}
