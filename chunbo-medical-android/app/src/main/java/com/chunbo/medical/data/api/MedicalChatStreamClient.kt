package com.chunbo.medical.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException
import java.net.URLEncoder

sealed class ChatStreamEvent {
    /** 过程事件 (1004)：多智能体路由、MCP 工具调用 (Tool Call)、RAG 检索进度 */
    data class ProcessSteps(val steps: List<String>) : ChatStreamEvent()

    /** 参数事件 (1003)：RAG 命中知识库标题、推荐处方/科室、春播商城对症正品药品等结构化数据 */
    data class KnowledgeBases(val kbTitles: List<String>) : ChatStreamEvent()
    data class RxCards(val rxItems: List<String>) : ChatStreamEvent()
    data class MallRecommendations(val products: List<com.chunbo.medical.data.model.MallProductRecommendation>) : ChatStreamEvent()
    data class QuickReplies(val quickReplies: List<String>) : ChatStreamEvent()

    /** 数据事件 (1001)：大模型流式打字 Token 碎片 */
    data class Token(val text: String) : ChatStreamEvent()

    /** 停止事件 (1002)：流式输出完成 */
    object Complete : ChatStreamEvent()

    /** 异常事件 */
    data class Error(val throwable: Throwable) : ChatStreamEvent()
}

object MedicalChatStreamClient {

    /** 智能预问诊 SSE 真流式问答 */
    fun streamPreConsult(
        patientName: String,
        gender: String = "男",
        age: String = "30",
        idCard: String = "",
        sessionId: String = "",
        userReply: String = "",
        userId: String = "",
        attachmentId: String? = null
    ): Flow<ChatStreamEvent> {
        val baseUrl = ApiClient.getBaseUrl()
        val encName = URLEncoder.encode(patientName, "UTF-8")
        val encGender = URLEncoder.encode(gender, "UTF-8")
        val encAge = URLEncoder.encode(age, "UTF-8")
        val encIdCard = URLEncoder.encode(idCard, "UTF-8")
        val encSession = URLEncoder.encode(sessionId, "UTF-8")
        val encReply = URLEncoder.encode(userReply, "UTF-8")
        val encUser = URLEncoder.encode(userId, "UTF-8")
        val attachParam = if (!attachmentId.isNullOrBlank()) "&attachmentId=" + URLEncoder.encode(attachmentId, "UTF-8") else ""
        val url = "${baseUrl}api/medical/chat/pre-consult/stream?patientName=$encName&gender=$encGender&age=$encAge&idCard=$encIdCard&sessionId=$encSession&userReply=$encReply&userId=$encUser$attachParam"
        return streamInternal(url)
    }

    /** 临床问诊 CDSS 流式对话（预问诊/分诊场景） */
    fun streamChat(
        sessionId: String,
        message: String,
        doctorId: String = "mobile_user",
        patientId: Long? = null
    ): Flow<ChatStreamEvent> {
        val baseUrl = ApiClient.getBaseUrl()
        val encSession = URLEncoder.encode(sessionId, "UTF-8")
        val encMsg = URLEncoder.encode(message, "UTF-8")
        val encDoc = URLEncoder.encode(doctorId, "UTF-8")
        val pidParam = if (patientId != null) "&patientId=$patientId" else ""
        val url = "${baseUrl}api/medical/chat/stream?sessionId=$encSession&message=$encMsg&doctorId=$encDoc$pidParam"
        return streamInternal(url)
    }

    /** 春播小药师（商城）流式对话：支持图片附件识药 → 查商城库存 → 推荐下单卡片 */
    fun streamMallChat(
        sessionId: String,
        message: String,
        phone: String = "",
        userName: String = "",
        attachmentId: String? = null
    ): Flow<ChatStreamEvent> {
        val baseUrl = ApiClient.getBaseUrl()
        val encSession = URLEncoder.encode(sessionId, "UTF-8")
        val encMsg = URLEncoder.encode(message, "UTF-8")
        val encPhone = URLEncoder.encode(phone, "UTF-8")
        val encName = URLEncoder.encode(userName, "UTF-8")
        var url = "${baseUrl}api/mall/chat/stream?message=$encMsg&role=consumer&sessionId=$encSession&phone=$encPhone&userName=$encName"
        if (!attachmentId.isNullOrBlank()) {
            url += "&attachmentId=" + URLEncoder.encode(attachmentId, "UTF-8")
        }
        return streamInternal(url)
    }

    private fun streamInternal(url: String): Flow<ChatStreamEvent> = callbackFlow {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "text/event-stream")
            .build()

        val call = ApiClient.streamOkHttpClient.newCall(request)

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                trySend(ChatStreamEvent.Error(e))
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    val err = IOException("HTTP ${response.code}: ${response.message}")
                    trySend(ChatStreamEvent.Error(err))
                    close(err)
                    return
                }

                val body = response.body
                if (body == null) {
                    val err = IOException("Empty response body")
                    trySend(ChatStreamEvent.Error(err))
                    close(err)
                    return
                }

                try {
                    val reader = BufferedReader(body.charStream())
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val curLine = line?.trim() ?: continue
                        if (curLine.isEmpty() || curLine.startsWith(":")) continue

                        if (curLine.startsWith("data:")) {
                            val dataStr = curLine.substring(5).trim()
                            if (dataStr.isEmpty()) continue

                            try {
                                val json = JSONObject(dataStr)
                                val eventType = json.optInt("eventType", 1001)

                                when (eventType) {
                                    1004 -> { // PROCESS 事件：MCP 工具核验、多智能体协同、RAG 检索
                                        val eventData = json.optJSONObject("eventData")
                                        val stepsArr = eventData?.optJSONArray("steps")
                                        if (stepsArr != null) {
                                            val steps = mutableListOf<String>()
                                            for (i in 0 until stepsArr.length()) {
                                                steps.add(stepsArr.getString(i))
                                            }
                                            trySend(ChatStreamEvent.ProcessSteps(steps))
                                        }
                                    }
                                    1003 -> { // PARAM 事件：RAG 知识库与结构化推荐
                                        val eventData = json.optJSONObject("eventData")
                                        if (eventData != null) {
                                            val kbArr = eventData.optJSONArray("kbTitles")
                                            if (kbArr != null) {
                                                val kbs = mutableListOf<String>()
                                                for (i in 0 until kbArr.length()) {
                                                    kbs.add(kbArr.getString(i))
                                                }
                                                trySend(ChatStreamEvent.KnowledgeBases(kbs))
                                            }
                                            val rxArr = eventData.optJSONArray("rxItems")
                                            if (rxArr != null) {
                                                val rxs = mutableListOf<String>()
                                                for (i in 0 until rxArr.length()) {
                                                    val obj = rxArr.optJSONObject(i)
                                                    if (obj != null) {
                                                        // 后端下发对象数组 {category,name,specification,dose,unitPrice}
                                                        val name = obj.optString("name", "")
                                                        val spec = obj.optString("specification", "")
                                                        val dose = obj.optString("dose", "")
                                                        val price = obj.optDouble("unitPrice", 0.0)
                                                        val sb = StringBuilder(if (name.isNotBlank()) name else "处方药品")
                                                        if (spec.isNotBlank()) sb.append("（").append(spec).append("）")
                                                        if (dose.isNotBlank()) sb.append(" · ").append(dose)
                                                        if (price > 0) sb.append(" · ¥").append(String.format(java.util.Locale.CHINA, "%.2f", price))
                                                        rxs.add(sb.toString())
                                                    } else {
                                                        // 兼容纯字符串数组
                                                        rxs.add(rxArr.getString(i))
                                                    }
                                                }
                                                if (rxs.isNotEmpty()) trySend(ChatStreamEvent.RxCards(rxs))
                                            }
                                            // 解析春播商城对症正品在售药品卡片
                                            val recArr = eventData.optJSONArray("recommendations")
                                            if (recArr != null && recArr.length() > 0) {
                                                val prods = mutableListOf<com.chunbo.medical.data.model.MallProductRecommendation>()
                                                for (i in 0 until recArr.length()) {
                                                    val obj = recArr.optJSONObject(i)
                                                    if (obj != null) {
                                                        prods.add(
                                                            com.chunbo.medical.data.model.MallProductRecommendation(
                                                                id = obj.optLong("id", 0L),
                                                                productName = obj.optString("productName", ""),
                                                                specification = obj.optString("specification", ""),
                                                                price = obj.optString("price", "0.00"),
                                                                category = obj.optString("category", ""),
                                                                csPitch = obj.optString("csPitch", ""),
                                                                imageUrl = obj.optString("imageUrl", "")
                                                            )
                                                        )
                                                    }
                                                }
                                                if (prods.isNotEmpty()) {
                                                    trySend(ChatStreamEvent.MallRecommendations(prods))
                                                }
                                            }
                                            // 解析预问诊快捷追问选项
                                            val qrArr = eventData.optJSONArray("quickReplies")
                                            if (qrArr != null && qrArr.length() > 0) {
                                                val qrs = mutableListOf<String>()
                                                for (i in 0 until qrArr.length()) {
                                                    qrs.add(qrArr.getString(i))
                                                }
                                                if (qrs.isNotEmpty()) {
                                                    trySend(ChatStreamEvent.QuickReplies(qrs))
                                                }
                                            }
                                        }
                                    }
                                    1001 -> { // DATA 事件：流式文字 Token
                                        val token = json.optString("eventData", "")
                                        if (token.isNotEmpty()) {
                                            trySend(ChatStreamEvent.Token(token))
                                        }
                                    }
                                    1002 -> { // STOP 事件
                                        trySend(ChatStreamEvent.Complete)
                                    }
                                    else -> {
                                        val raw = json.optString("eventData", "")
                                        if (raw.isNotEmpty()) {
                                            trySend(ChatStreamEvent.Token(raw))
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                // 兼容纯文本 chunk
                                trySend(ChatStreamEvent.Token(dataStr))
                            }
                        }
                    }
                    trySend(ChatStreamEvent.Complete)
                    close()
                } catch (e: Exception) {
                    trySend(ChatStreamEvent.Error(e))
                    close(e)
                } finally {
                    body.close()
                }
            }
        })

        awaitClose {
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)
}
