package com.chunbo.medical.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.chunbo.medical.data.api.ApiClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * 语音录入工具：MediaRecorder 录音 → 上传后端 ASR（/api/audio/asr）→ 回调识别文字。
 * 纯手机端实现，复用后端现成的语音转文字能力。
 */
object VoiceInputHelper {

    private var recorder: MediaRecorder? = null
    private var recordFile: File? = null

    fun hasRecordPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    fun isRecording(): Boolean = recorder != null

    /** 开始录音（m4a/AAC），成功返回 true */
    fun startRecording(context: Context): Boolean {
        if (!hasRecordPermission(context)) return false
        stop()
        return try {
            val file = File(context.cacheDir, "voice_" + System.currentTimeMillis() + ".m4a")
            recordFile = file
            recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            recorder?.release()
            recorder = null
            recordFile = null
            false
        }
    }

    /** 停止录音并上传识别，识别文字通过 onResult 回调 */
    fun stopAndTranscribe(
        context: Context,
        scope: CoroutineScope,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        val rec = recorder
        val file = recordFile
        recorder = null
        recordFile = null
        if (rec == null || file == null || !file.exists() || file.length() == 0L) {
            try { rec?.release() } catch (_: Exception) {}
            onError("未捕获到有效录音，请靠近麦克风重新说话")
            return
        }
        try { rec.stop() } catch (_: Exception) {}
        try { rec.release() } catch (_: Exception) {}

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val body = file.asRequestBody("audio/mp4".toMediaType())
                    val part = MultipartBody.Part.createFormData("file", file.name, body)
                    ApiClient.service.asr(part)
                }
                val success = result["success"] as? Boolean ?: false
                val text = result["text"]?.toString()?.trim().orEmpty()
                if (success && text.isNotEmpty()) {
                    onResult(text)
                } else {
                    onError(result["message"]?.toString() ?: "未识别到清晰语音，请重试")
                }
            } catch (e: Exception) {
                onError("语音识别失败：${e.message}")
            } finally {
                file.delete()
            }
        }
    }

    fun stop() {
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        recordFile?.delete()
        recordFile = null
    }
}
