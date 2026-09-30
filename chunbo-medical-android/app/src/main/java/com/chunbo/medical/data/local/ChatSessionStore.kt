package com.chunbo.medical.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File

/** 本地会话内一条消息 */
data class LocalChatMessage(
    val role: String = "",        // "user" / "assistant"
    val content: String = "",
    val time: Long = 0L,
    val quickReplies: List<String>? = null   // 快捷胶囊，回放时恢复
)

/** 本地会话（AI 导医对话记忆与历史） */
data class LocalChatSession(
    val id: String = "",
    var title: String = "",
    val createTime: Long = 0L,
    var updateTime: Long = 0L,
    val messages: MutableList<LocalChatMessage> = mutableListOf()
)

/**
 * AI 导医「会话记忆与历史」本地持久化仓库。
 * 后端 /api/mall/chat 无状态（不落会话、不存记忆），故在手机端本地实现完整闭环：
 * 会话列表、新建、历史回放、删除、多轮消息留存，全部落盘到 App 私有目录。
 */
object ChatSessionStore {

    private const val FILE_NAME = "chunbo_copilot_sessions.json"

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun loadAll(context: Context): MutableList<LocalChatSession> {
        return try {
            val f = file(context)
            if (!f.exists()) return mutableListOf()
            val json = f.readText()
            val type = object : TypeToken<MutableList<LocalChatSession>>() {}.type
            Gson().fromJson<MutableList<LocalChatSession>>(json, type) ?: mutableListOf()
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    @Synchronized
    fun saveAll(context: Context, sessions: List<LocalChatSession>) {
        try {
            file(context).writeText(Gson().toJson(sessions))
        } catch (e: Exception) {
            // 保存失败静默忽略，不阻塞对话
        }
    }

    fun newId(): String = "S_" + System.currentTimeMillis() + "_" + kotlin.random.Random.nextInt(1000, 10000)
}
