package com.chunbo.medical.ui.patient

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chunbo.medical.R
import com.chunbo.medical.data.api.ApiClient
import com.chunbo.medical.data.api.ChatStreamEvent
import com.chunbo.medical.data.api.MedicalChatStreamClient
import com.chunbo.medical.data.api.UserManager
import com.chunbo.medical.databinding.DialogPreConsultBinding
import com.chunbo.medical.ui.common.ChatHistoryBottomSheetHelper
import com.chunbo.medical.ui.common.SessionHistoryCardData
import com.chunbo.medical.util.VoiceInputHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

object PreConsultDialogHelper {

    fun showPreConsultDialog(
        context: Context,
        scope: LifecycleCoroutineScope,
        patientName: String,
        gender: String,
        age: String,
        idCard: String,
        onPickImage: ((onImagePicked: (String, String?) -> Unit) -> Unit)? = null,
        onExtractSuccess: (dept: String, doctor: String, complaint: String, fullSummary: String, preConsultJson: String) -> Unit
    ) {
        val binding = DialogPreConsultBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        // 绝不使用 stackFromEnd，第一条消息紧贴顶部渲染
        val layoutManager = LinearLayoutManager(context)
        binding.rvPreConsultChat.layoutManager = layoutManager
        // sendMessage 先声明（lateinit），供 adapter 回调前向引用；真正实现在下方赋值（Kotlin 局部函数不能前向引用）
        lateinit var sendMessage: (String, String?, String?) -> Unit
        val adapter = PreConsultChatAdapter(
            patientName = patientName,
            // 快捷追问选项点击即作为用户消息发送（选项随对话流展示，不再固定在输入框上方）
            onQuickReplyClick = { text -> sendMessage(text, null, null) }
        )
        binding.rvPreConsultChat.adapter = adapter
        binding.rvPreConsultChat.itemAnimator = null

        var currentSessionId = "preconsult_app_" + UUID.randomUUID().toString().replace("-", "").take(12)
        val historyList = mutableListOf<Map<String, String>>()

        val user = UserManager.getUser()
        val userId = user.phone.ifBlank { user.username.ifBlank { "mobile_guest" } }

        sendMessage = { userText, attachmentId, imageUri ->
            if (userText.isBlank() && attachmentId.isNullOrBlank()) {
                // 空消息忽略
            } else {
                binding.editPreConsultInput.setText("")

                val displayText = if (userText.isBlank()) "📎 [图片] 请结合我上传的图片帮我看一下" else userText
                adapter.addMessage(PreConsultChatMessage("user", displayText, imageUri))
                binding.rvPreConsultChat.scrollToPosition(adapter.itemCount - 1)
                historyList.add(mapOf("role" to "user", "content" to displayText))

                // 添加 AI 助手打字气泡占位
                val assistantMsg = PreConsultChatMessage("assistant", "正在构思追问...")
                adapter.addMessage(assistantMsg)
                val targetIndex = adapter.itemCount - 1
                binding.rvPreConsultChat.scrollToPosition(targetIndex)

                scope.launch {
                    val fullSb = StringBuilder()
                    var receivedChips: List<String> = emptyList()
                    var isFirstToken = true
                    try {
                        val replyText = if (userText.isBlank()) "我上传了一张图片，请帮我看看图片里的情况" else userText
                        MedicalChatStreamClient.streamPreConsult(
                            patientName = patientName,
                            gender = gender,
                            age = age,
                            idCard = idCard,
                            sessionId = currentSessionId,
                            userReply = replyText,
                            userId = userId,
                            attachmentId = attachmentId
                        ).collect { event ->
                            when (event) {
                                is ChatStreamEvent.Token -> {
                                    if (isFirstToken) {
                                        fullSb.clear()
                                        isFirstToken = false
                                    }
                                    fullSb.append(event.text)
                                    assistantMsg.content = fullSb.toString()
                                    adapter.notifyItemChanged(targetIndex, "TOKEN")
                                    binding.rvPreConsultChat.scrollToPosition(targetIndex)
                                }
                                is ChatStreamEvent.QuickReplies -> {
                                    receivedChips = event.quickReplies
                                }
                                is ChatStreamEvent.Complete -> {
                                    if (receivedChips.isEmpty()) {
                                        receivedChips = listOf("体温正常未发热", "低热乏力37.8℃", "突发高热38.5℃以上", "无过敏与慢病史", "病程已有2-3天")
                                    }
                                    assistantMsg.quickReplies = receivedChips
                                    adapter.notifyItemChanged(targetIndex)
                                    binding.rvPreConsultChat.scrollToPosition(targetIndex)
                                    historyList.add(mapOf("role" to "assistant", "content" to fullSb.toString()))
                                }
                                is ChatStreamEvent.Error -> {
                                    if (fullSb.isEmpty()) {
                                        val fallback = "已记录您的自述「${userText.take(15)}」。请问症状持续多久了？伴有发热或自服药物吗？"
                                        assistantMsg.content = fallback
                                        assistantMsg.quickReplies = listOf("起病1-2天", "起病3天以上", "体温38.5℃", "未自服药", "已服退烧药")
                                        adapter.notifyItemChanged(targetIndex)
                                        historyList.add(mapOf("role" to "assistant", "content" to fallback))
                                    }
                                    Unit
                                }
                                else -> {}
                            }
                        }
                    } catch (e: Exception) {
                        if (fullSb.isEmpty()) {
                            val fallback = "已记录您的自述「${userText.take(15)}」。请问症状持续多久了？伴有发热或自服药物吗？"
                            assistantMsg.content = fallback
                            assistantMsg.quickReplies = listOf("起病1-2天", "起病3天以上", "体温38.5℃", "未自服药", "已服退烧药")
                            adapter.notifyItemChanged(targetIndex)
                            historyList.add(mapOf("role" to "assistant", "content" to fallback))
                        }
                    }
                }
            }
        }

        // ── 开启全新预问诊会话（重置会话 ID，调用真实大模型真流式生成个性化问候与快捷选项） ──
        fun startNewPreConsultSession() {
            currentSessionId = "preconsult_app_" + UUID.randomUUID().toString().replace("-", "").take(12)
            historyList.clear()
            adapter.setMessages(emptyList())
            binding.tvPreSessionTitle.text = "新问诊 · $patientName"

            val welcomeMsg = PreConsultChatMessage("assistant", "AI 智能护士正在根据【$patientName】的档案构思开门问候...")
            adapter.addMessage(welcomeMsg)
            val welcomeIndex = 0
            binding.btnSendPreConsult.isEnabled = false
            binding.editPreConsultInput.isEnabled = false

            scope.launch {
                val fullSb = StringBuilder()
                var receivedChips: List<String> = emptyList()
                var isFirstToken = true
                try {
                    MedicalChatStreamClient.streamPreConsult(
                        patientName = patientName,
                        gender = gender,
                        age = age,
                        idCard = idCard,
                        sessionId = currentSessionId,
                        userReply = "",
                        userId = userId
                    ).collect { event ->
                        when (event) {
                            is ChatStreamEvent.Token -> {
                                if (isFirstToken) {
                                    fullSb.clear()
                                    isFirstToken = false
                                }
                                fullSb.append(event.text)
                                welcomeMsg.content = fullSb.toString()
                                adapter.notifyItemChanged(welcomeIndex, "TOKEN")
                                binding.rvPreConsultChat.scrollToPosition(welcomeIndex)
                            }
                            is ChatStreamEvent.QuickReplies -> {
                                receivedChips = event.quickReplies
                            }
                            is ChatStreamEvent.Complete -> {
                                if (receivedChips.isEmpty()) {
                                    receivedChips = listOf("🤒 突发高热伴寒战", "🤧 咳嗽咽痛伴咳痰", "🤢 胃痛腹泻胃胀气", "🤕 头痛头晕全身无力", "🦵 颈肩腰腿酸痛", "📋 慢病定期配药")
                                }
                                welcomeMsg.quickReplies = receivedChips
                                adapter.notifyItemChanged(welcomeIndex)
                                historyList.add(mapOf("role" to "assistant", "content" to fullSb.toString()))
                            }
                            is ChatStreamEvent.Error -> {
                                if (fullSb.isEmpty()) {
                                    val fallback = "您好 $patientName！我是春播全科门诊预问诊护士。请问您今天主要是身体哪个部位感觉不舒服呢？"
                                    welcomeMsg.content = fallback
                                    welcomeMsg.quickReplies = listOf("🤒 发热头痛", "🤧 咳嗽咽痛", "🦵 膝关节肿痛", "🤢 胃肠不适", "📋 慢病配药")
                                    adapter.notifyItemChanged(welcomeIndex)
                                    historyList.add(mapOf("role" to "assistant", "content" to fallback))
                                }
                                Unit
                            }
                            else -> {}
                        }
                    }
                } catch (e: Exception) {
                    if (fullSb.isEmpty()) {
                        val fallback = "您好 $patientName！我是春播全科门诊预问诊护士。请问您今天主要是身体哪个部位感觉不舒服呢？"
                        welcomeMsg.content = fallback
                        welcomeMsg.quickReplies = listOf("🤒 发热头痛", "🤧 咳嗽咽痛", "🦵 膝关节肿痛", "🤢 胃肠不适", "📋 慢病配药")
                        adapter.notifyItemChanged(welcomeIndex)
                        historyList.add(mapOf("role" to "assistant", "content" to fallback))
                    }
                } finally {
                    binding.btnSendPreConsult.isEnabled = true
                    binding.editPreConsultInput.isEnabled = true
                }
            }
        }

        // 绑定新建会话按钮
        binding.btnNewSession.setOnClickListener {
            startNewPreConsultSession()
            Toast.makeText(context, "已开启全新预问诊会话", Toast.LENGTH_SHORT).show()
        }

        // ── 往期问诊历史按钮（现代化无感卡片底栏） ──
        binding.btnViewHistory.setOnClickListener {
            ChatHistoryBottomSheetHelper.show(
                context = context,
                scope = scope,
                sheetTitle = "往期 AI 预问诊档案",
                newSessionTitle = "开启全新预问诊",
                newSessionSub = "重新发起就诊人【$patientName】的症状问询",
                loadSessions = {
                    val sessions = ApiClient.service.getPreConsultSessions(userId = userId, patientName = patientName, idCard = idCard)
                    val allSessions = sessions.values.flatten()
                    allSessions.map { s ->
                        val title = s["title"]?.toString() ?: "往期预问诊"
                        val timeRaw = s["updateTime"]?.toString() ?: s["createdAt"]?.toString()
                        val friendlyTime = ChatHistoryBottomSheetHelper.formatFriendlyTime(timeRaw)
                        val sid = s["sessionId"]?.toString() ?: ""
                        SessionHistoryCardData(
                            sessionId = sid,
                            title = title,
                            timeDisplay = friendlyTime,
                            badge = "预问诊档案",
                            rawData = s
                        )
                    }
                },
                onNewSession = {
                    startNewPreConsultSession()
                    Toast.makeText(context, "已开启全新预问诊会话", Toast.LENGTH_SHORT).show()
                },
                onSessionSelect = { card ->
                    val sessId = card.sessionId
                    if (sessId.isBlank()) return@show
                    val sessTitle = card.title
                    scope.launch {
                        try {
                            val msgs = withContext(Dispatchers.IO) {
                                ApiClient.service.getPreConsultSessionMessages(sessId)
                            }
                            currentSessionId = sessId
                            binding.tvPreSessionTitle.text = sessTitle
                            adapter.setMessages(emptyList())
                            historyList.clear()
                            msgs.forEach { m ->
                                val role = m["role"] ?: "user"
                                val content = m["content"] ?: ""
                                adapter.addMessage(PreConsultChatMessage(role, content))
                                historyList.add(mapOf("role" to role, "content" to content))
                            }
                            if (adapter.itemCount > 0) {
                                binding.rvPreConsultChat.scrollToPosition(adapter.itemCount - 1)
                            }
                            Toast.makeText(context, "已恢复会话「$sessTitle」", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "加载历史记录失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                onSessionDelete = { card ->
                    ApiClient.service.deletePreConsultSession(card.sessionId, userId)
                    Unit
                }
            )
        }

        // 初始化启动第一轮真实 LLM 接诊问候
        startNewPreConsultSession()

        binding.btnSendPreConsult.setOnClickListener {
            sendMessage(binding.editPreConsultInput.text.toString().trim(), null, null)
        }

        // 按住说话：按下开始录音，松开识别并填入输入框
        binding.btnPreConsultVoice.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    if (!VoiceInputHelper.hasRecordPermission(context)) {
                        Toast.makeText(context, "请先在系统设置中授予麦克风权限", Toast.LENGTH_SHORT).show()
                    } else if (VoiceInputHelper.startRecording(context)) {
                        v.alpha = 0.5f
                        Toast.makeText(context, "正在录音，请说话，松开识别", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "无法启动录音，请检查麦克风权限", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    if (VoiceInputHelper.isRecording()) {
                        VoiceInputHelper.stopAndTranscribe(
                            context,
                            scope,
                            onResult = { text -> binding.editPreConsultInput.append(text) },
                            onError = { err -> Toast.makeText(context, err, Toast.LENGTH_SHORT).show() }
                        )
                    }
                    true
                }
                else -> false
            }
        }

        // 图片上传：患处照片/化验单/药盒拍照或相册选择 → 上传得 fileId → 作为图片消息发送（后端多模态识别）
        binding.btnPreConsultImage.setOnClickListener {
            if (onPickImage == null) {
                Toast.makeText(context, "当前环境暂不支持图片上传", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            onPickImage { fileId, imageUri ->
                sendMessage("", fileId, imageUri)
            }
        }

        binding.btnClosePreConsult.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnExtractAndFill.setOnClickListener {
            val userMsgCount = historyList.count { it["role"] == "user" }
            if (userMsgCount == 0) {
                Toast.makeText(context, "请先自述您的主要不适症状或点击症状标签", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            scope.launch {
                try {
                    Toast.makeText(context, "AI 大模型正在结构化提炼主诉、现病史与对症科室...", Toast.LENGTH_SHORT).show()
                    val req = mapOf(
                        "patientName" to patientName,
                        "gender" to gender,
                        "age" to age,
                        "history" to historyList,
                        "dialogue" to historyList
                    )
                    val resp = withContext(Dispatchers.IO) {
                        ApiClient.service.preConsultExtract(req)
                    }
                    val dataMap = (resp["data"] as? Map<*, *>) ?: resp

                    val rawChief = dataMap["chiefComplaint"]?.toString()
                    val chief = if (!rawChief.isNullOrBlank()) rawChief else historyList.filter { it["role"] == "user" }.joinToString("，") { it["content"] ?: "" }
                    val dept = dataMap["recommendedDepartment"]?.toString() ?: dataMap["department"]?.toString() ?: "全科门诊"
                    val doctor = dataMap["recommendedDoctor"]?.toString() ?: ""
                    val tcm = dataMap["tcmPattern"]?.toString() ?: ""
                    val present = dataMap["presentIllness"]?.toString() ?: ""

                    val summary = "AI预问诊建议就诊【$dept】· 主诉: $chief" +
                            (if (tcm.isNotEmpty()) " · 证候: $tcm" else "") +
                            (if (present.isNotEmpty()) " · 现病史: $present" else "")

                    // 结构化 JSON：与 PC 端"复用预问诊问答"的解析字段完全对齐
                    // （PC 端按 chiefComplaint/presentIllness/duration/tcmPattern 等字段导入；
                    //   dialogue + sessionId 供 PC 端「查阅预问诊原话」还原完整多轮对话）
                    val preConsultJson = org.json.JSONObject().apply {
                        put("chiefComplaint", chief)
                        if (present.isNotEmpty()) put("presentIllness", present)
                        if (tcm.isNotEmpty()) put("tcmPattern", tcm)
                        put("recommendedDepartment", dept)
                        if (doctor.isNotEmpty()) put("recommendedDoctor", doctor)
                        put("sessionId", currentSessionId)
                        put("dialogue", org.json.JSONArray(historyList))
                        put("source", "手机端AI预问诊")
                        put("patientName", patientName)
                        put("gender", gender)
                        put("age", age)
                    }.toString()

                    dialog.dismiss()
                    onExtractSuccess(dept, doctor, chief, summary, preConsultJson)
                } catch (e: Exception) {
                    val userReplies = historyList.filter { it["role"] == "user" }.mapNotNull { it["content"] }
                    val combined = userReplies.joinToString("，")
                    val matchedDept = when {
                        combined.contains("耳") || combined.contains("咽") || combined.contains("鼻") -> "耳鼻喉科"
                        combined.contains("骨") || combined.contains("关节") || combined.contains("扭伤") -> "骨伤科"
                        combined.contains("儿") || combined.contains("宝宝") || combined.contains("小儿") -> "儿科"
                        combined.contains("胃") || combined.contains("脾") || combined.contains("失眠") -> "中医内科"
                        else -> "全科门诊"
                    }
                    val fallbackChief = combined.ifEmpty { "身体不适初诊" }
                    val fallbackJson = org.json.JSONObject().apply {
                        put("chiefComplaint", fallbackChief)
                        put("recommendedDepartment", matchedDept)
                        put("source", "手机端AI预问诊(本地降级)")
                        put("patientName", patientName)
                        put("gender", gender)
                        put("age", age)
                    }.toString()
                    dialog.dismiss()
                    onExtractSuccess(matchedDept, "", fallbackChief, "AI预问诊建议挂【$matchedDept】", fallbackJson)
                }
            }
        }

        dialog.show()
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            val dm = context.resources.displayMetrics
            window.setLayout((dm.widthPixels * 0.96).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }
}
