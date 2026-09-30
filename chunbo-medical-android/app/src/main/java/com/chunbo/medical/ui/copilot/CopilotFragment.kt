package com.chunbo.medical.ui.copilot

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chunbo.medical.R
import com.chunbo.medical.data.api.ApiClient
import com.chunbo.medical.data.api.ChatStreamEvent
import com.chunbo.medical.data.api.MedicalChatStreamClient
import com.chunbo.medical.data.api.UserManager
import com.chunbo.medical.data.model.ChatMessage
import com.chunbo.medical.data.model.MallProduct
import com.chunbo.medical.data.model.MallProductRecommendation
import com.chunbo.medical.databinding.FragmentCopilotBinding
import com.chunbo.medical.ui.MainActivity
import com.chunbo.medical.ui.common.ChatHistoryBottomSheetHelper
import com.chunbo.medical.ui.common.SessionHistoryCardData
import com.chunbo.medical.ui.mall.CartManager
import com.chunbo.medical.util.ImagePickerHelper
import com.chunbo.medical.util.VoiceInputHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class CopilotFragment : Fragment() {

    private var _binding: FragmentCopilotBinding? = null
    private val binding get() = _binding!!

    private lateinit var chatAdapter: ChatAdapter

    // 当前服务端会话 ID与标题
    private var currentSessionId: String = ""
    private var currentSessionTitle: String = "新对话"
    private var isSending = false

    // 最近一次推荐的商品（用于自然语言「加入购物车/购买」指令匹配）
    private var lastRecommendations: List<MallProductRecommendation> = emptyList()

    // 外部跳转携带的待发送提问（视图未就绪时暂存）
    private var pendingAskQuestion: String? = null

    // 相册选图（识药）
    private val imagePickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { uploadAndSendImage(it) }
    }

    // 系统相机拍照（识药）
    private var pendingCaptureUri: android.net.Uri? = null
    private val takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (success && uri != null) {
            uploadAndSendImage(uri)
        }
    }

    private fun uploadAndSendImage(uri: android.net.Uri) {
        ImagePickerHelper.uploadImage(
            requireContext(),
            viewLifecycleOwner.lifecycleScope,
            uri,
            onResult = { fileId -> sendImageMessage(fileId, uri.toString()) },
            onError = { err -> Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show() }
        )
    }

    private fun showImageSourcePicker() {
        val options = arrayOf("📷 拍照", "🖼️ 从相册选择")
        AlertDialog.Builder(requireContext())
            .setTitle("选择图片来源")
            .setItems(options) { _, which ->
                if (which == 0) takePhoto() else imagePickerLauncher.launch("image/*")
            }
            .show()
    }

    private fun takePhoto() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.CAMERA), 2002)
            return
        }
        pendingCaptureUri = ImagePickerHelper.createCaptureOutputUri(requireContext())
        pendingCaptureUri?.let { takePictureLauncher.launch(it) }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCopilotBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initRecyclerView()
        initChips()
        initInputBar()
        initSessionBar()
        startNewSession()

        // 处理外部跳转携带的提问（如就诊记录 AI 分析）：视图就绪后才真正发送
        pendingAskQuestion?.let { q ->
            pendingAskQuestion = null
            val products = pendingAskProducts
            pendingAskProducts = emptyList()
            sendMessage(q, attachedProducts = products)
        }
    }

    /** 供外部直接发起提问（视图未就绪时暂存， onViewCreated 后发送） */
    fun askFromExternal(question: String) {
        if (_binding != null) {
            sendMessage(question)
        } else {
            pendingAskQuestion = question
        }
    }

    // 外部跳转携带的待发送提问（附带的商品卡片，如订单咨询）
    private var pendingAskProducts: List<MallProductRecommendation> = emptyList()

    /** 供外部发起「带商品卡片的提问」（如订单咨询：用户气泡下方展示真实商品图卡） */
    fun askOrderFromExternal(question: String, products: List<MallProductRecommendation>) {
        if (_binding != null) {
            sendMessage(question, attachedProducts = products)
        } else {
            pendingAskQuestion = question
            pendingAskProducts = products
        }
    }

    private fun initRecyclerView() {
        chatAdapter = ChatAdapter(
            onChipClick = { chipText ->
                if (chipText.contains("便民挂号") || chipText.contains("挂号") || chipText.contains("预约")) {
                    (activity as? MainActivity)?.switchTab(R.id.nav_patient)
                } else if (chipText.contains("春播商城") || chipText.contains("商城")) {
                    (activity as? MainActivity)?.switchToMallTab()
                } else {
                    sendMessage(chipText)
                }
            },
            onBuyProductClick = { prod ->
                buyNow(prod)
            },
            onAddToCart = { prod ->
                addProductToCart(prod)
            }
        )
        val layoutManager = LinearLayoutManager(requireContext())
        binding.rvChatMessages.layoutManager = layoutManager
        binding.rvChatMessages.adapter = chatAdapter
    }

    private fun initSessionBar() {
        binding.btnChatHistory.setOnClickListener { showHistoryDialog() }
        binding.btnChatNew.setOnClickListener { startNewSession() }
        // 底部固定快捷 chips 栏已移除：快捷问题统一收敛到欢迎语消息内的随机 quickReplies，避免重复
    }

    private fun getEffectiveUserId(): String {
        val user = UserManager.getUser()
        return if (user.phone.isNotBlank()) user.phone else if (user.username.isNotBlank()) user.username else "mobile_guest"
    }

    /** 快捷问题池：每次新会话随机抽取，不再固定同一批问题 */
    private val quickQuestionPool = listOf(
        "喉咙痛吞咽困难挂哪个科？",
        "儿童布洛芬按体重怎么吃？",
        "小儿咳嗽痰多贴什么好？",
        "春播商城有哪些常备药？",
        "感冒了能吃头孢吗？",
        "失眠多梦怎么调理？",
        "高血压日常要注意什么？",
        "肠胃不适可以贴敷调理吗？",
        "腰腿疼痛该挂哪个科？",
        "抗生素不能和什么一起吃？",
        "家中老人补钙怎么选？",
        "崴脚肿胀48小时内怎么处理？",
        "春天反复过敏怎么办？"
    )

    /** 从问题池随机抽 count 条不重复问题 */
    private fun pickRandomQuestions(count: Int): List<String> =
        quickQuestionPool.shuffled().take(count)

    private fun startNewSession() {
        currentSessionId = "sess_app_" + UUID.randomUUID().toString().replace("-", "").take(12)
        currentSessionTitle = "新对话"
        updateSessionTitleBar()
        chatAdapter.submitList(emptyList())
        addWelcomeMessage()
    }

    private fun updateSessionTitleBar() {
        binding.tvChatSessionTitle.text = if (currentSessionTitle.isBlank()) "新对话" else currentSessionTitle
    }

    private fun addWelcomeMessage() {
        val user = UserManager.getUser()
        val displayName = if (user.isLoggedIn) user.nickname else "您好"
        val welcome = "${displayName}！我是春播便民健康小药师 🌿\n\n" +
                "可以为你：\n" +
                "🩺 智能分诊挂号\n" +
                "💊 用药指导与禁忌核对\n" +
                "🛒 对症选品 · 商城直购\n\n" +
                "点下方问题直接开始，或输入你的症状。"

        val welcomeMsg = ChatMessage(
            role = "assistant",
            content = welcome,
            quickReplies = pickRandomQuestions(4),
            processSteps = emptyList()
        )
        chatAdapter.addMessage(welcomeMsg)
    }

    private fun initChips() {
        // 已废弃：底部固定快捷 chips 栏移除（与欢迎语内随机 quickReplies 重复），保留空实现避免历史调用报错
    }

    private fun initInputBar() {
        binding.btnSendChat.setOnClickListener {
            val text = binding.editChatInput.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
                binding.editChatInput.setText("")
            }
        }
        // 按住说话：按下开始录音，松开识别并填入输入框
        binding.btnVoiceInput.setOnTouchListener { v, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    if (!VoiceInputHelper.hasRecordPermission(requireContext())) {
                        ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.RECORD_AUDIO), 2001)
                    } else if (VoiceInputHelper.startRecording(requireContext())) {
                        v.alpha = 0.5f
                        Toast.makeText(requireContext(), "正在录音，请说话，松开识别", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "无法启动录音，请检查麦克风权限", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1f
                    if (VoiceInputHelper.isRecording()) {
                        VoiceInputHelper.stopAndTranscribe(
                            requireContext(),
                            viewLifecycleOwner.lifecycleScope,
                            onResult = { text -> binding.editChatInput.append(text) },
                            onError = { err -> Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show() }
                        )
                    }
                    true
                }
                else -> false
            }
        }
        binding.btnImageInput.setOnClickListener { showImageSourcePicker() }
    }

    private fun sendMessage(
        userText: String,
        attachmentId: String? = null,
        imageUri: String? = null,
        attachedProducts: List<MallProductRecommendation> = emptyList()
    ) {
        if (isSending || userText.isBlank()) return
        // 自然语言「加入购物车/购买」指令：本地匹配最近推荐商品直接执行，不再发起对话
        if (attachmentId == null && handleCartIntent(userText)) return
        isSending = true

        // 1. 添加用户消息（图片消息带本地预览 uri；订单咨询可附带商品卡片，气泡下方展示真实商品图卡）
        chatAdapter.addMessage(
            ChatMessage(
                role = "user",
                content = userText,
                imageUri = imageUri,
                mallProducts = attachedProducts
            )
        )
        binding.rvChatMessages.smoothScrollToPosition(chatAdapter.itemCount - 1)

        // 2. 添加 AI 流式占位卡片（初始只显示真实状态「正在连接」，实际调用了什么由后端事件如实下发）
        val aiMsg = ChatMessage(
            role = "assistant",
            content = "",
            processSteps = listOf("[连接] 正在连接 AI 智能体服务..."),
            isStreaming = true
        )
        chatAdapter.addMessage(aiMsg)
        val aiMsgPos = chatAdapter.itemCount - 1
        binding.rvChatMessages.smoothScrollToPosition(aiMsgPos)

        val userId = getEffectiveUserId()
        val sid = currentSessionId

        // 3. 发起真实 SSE 多智能体流式对话
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = UserManager.getUser()
                MedicalChatStreamClient.streamMallChat(
                    sessionId = sid,
                    message = userText,
                    phone = user.phone,
                    userName = user.username,
                    attachmentId = attachmentId
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.ProcessSteps -> {
                            chatAdapter.updateLast { msg ->
                                msg.processSteps = event.steps
                            }
                        }
                        is ChatStreamEvent.KnowledgeBases -> {
                            chatAdapter.updateLast { msg ->
                                msg.ragKnowledgeBases = event.kbTitles
                            }
                        }
                        is ChatStreamEvent.Token -> {
                            chatAdapter.updateLast { msg ->
                                msg.content += event.text
                            }
                            binding.rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
                        }
                        is ChatStreamEvent.MallRecommendations -> {
                            lastRecommendations = event.products
                            chatAdapter.updateLast { msg ->
                                msg.mallProducts = event.products
                            }
                        }
                        is ChatStreamEvent.RxCards -> {
                            chatAdapter.updateLast { msg ->
                                msg.rxItems = event.rxItems
                            }
                        }
                        is ChatStreamEvent.Complete -> {
                            chatAdapter.updateLast { msg ->
                                msg.isStreaming = false
                                msg.processDone = true
                                val quicks = mutableListOf<String>()
                                if (msg.content.contains("挂号") || msg.content.contains("医生") || msg.content.contains("科室")) {
                                    quicks.add("🏥 立即前往「便民挂号」预约专家")
                                }
                                if (msg.mallProducts.isNotEmpty() || msg.content.contains("药") || msg.content.contains("贴敷")) {
                                    quicks.add("🛒 前往「春播商城」选购在售药品")
                                }
                                quicks.add("再咨询一个身体症状")
                                quicks.add("查看春播特色贴敷调理")
                                msg.quickReplies = quicks
                            }
                            binding.rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
                            isSending = false
                            // 延时从后端同步 AI 独立大模型提炼的标题
                            fetchAiSessionTitle(sid, userId)
                        }
                        is ChatStreamEvent.Error -> {
                            chatAdapter.updateLast { msg ->
                                msg.isStreaming = false
                                if (msg.content.isBlank()) {
                                    msg.content = "⚠️ AI 智能体问诊服务连接受阻: ${event.throwable.message}\n请确认后端服务运行正常 (当前: ${ApiClient.getBaseUrl()})"
                                }
                            }
                            isSending = false
                        }
                        is ChatStreamEvent.QuickReplies -> {}
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                chatAdapter.updateLast { msg ->
                    msg.isStreaming = false
                    if (msg.content.isBlank()) {
                        msg.content = "⚠️ 连接服务异常: ${e.message}"
                    }
                }
                isSending = false
            }
        }
    }

    /** 商品推荐 → 购物车商品对象（字段对齐） */
    private fun recommendationToProduct(rec: MallProductRecommendation): MallProduct {
        val price = rec.price.toDoubleOrNull() ?: 0.0
        val realImg = rec.imageUrl.takeIf { it.isNotBlank() } ?: CartManager.getProductImage(rec.id)
        return MallProduct(
            id = rec.id,
            productName = rec.productName,
            specification = rec.specification,
            category = rec.category,
            price = price,
            retailGuidePrice = price,
            imageUrl = realImg
        )
    }

    private fun addProductToCart(rec: MallProductRecommendation) {
        CartManager.addToCart(recommendationToProduct(rec), 1)
        Toast.makeText(requireContext(), "已将【${rec.productName}】加入购物车", Toast.LENGTH_SHORT).show()
        (activity as? MainActivity)?.switchToMallTab()
    }

    private fun buyNow(rec: MallProductRecommendation) {
        CartManager.addToCart(recommendationToProduct(rec), 1)
        Toast.makeText(requireContext(), "已为【${rec.productName}】准备下单，请在商城结算支付", Toast.LENGTH_LONG).show()
        (activity as? MainActivity)?.switchToMallTab()
    }

    /** 自然语言「加入购物车/购买」意图：有最近推荐商品时直接执行 */
    private fun handleCartIntent(userText: String): Boolean {
        val wantCart = userText.contains("加入购物车") || userText.contains("加购") || userText.contains("加购物车")
        val wantBuy = userText.contains("购买") || userText.contains("下单") || userText.contains("买")
        if (!wantCart && !wantBuy) return false
        val prod = lastRecommendations.firstOrNull() ?: return false
        if (wantCart) addProductToCart(prod) else buyNow(prod)
        return true
    }

    private fun sendImageMessage(fileId: String, imageUri: String) {
        val text = "我上传了一张药品图片，请帮我识别这是什么药，并查询春播商城是否有同款在售"
        sendMessage(text, attachmentId = fileId, imageUri = imageUri)
    }

    /** 从后端获取 AI 大模型（titleChatClient）异步总结生成的会话标题 */
    private fun fetchAiSessionTitle(sessionId: String, userId: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            delay(1500) // 等待后端异步 titleChatClient 执行完毕
            try {
                val hist = withContext(Dispatchers.IO) {
                    ApiClient.service.getSessionHistory("mall", userId)
                }
                val all = hist.values.flatten()
                val found = all.find { it["sessionId"]?.toString() == sessionId }
                val title = found?.get("title")?.toString()
                if (!title.isNullOrBlank()) {
                    currentSessionTitle = title
                    updateSessionTitleBar()
                }
            } catch (_: Exception) {}
        }
    }

    /** 历史会话底栏：读取服务端真实会话历史（按时间段分组，由 AI 提炼标题），绝不弹打扰用户的 Toast */
    private fun showHistoryDialog() {
        val userId = getEffectiveUserId()
        ChatHistoryBottomSheetHelper.show(
            context = requireContext(),
            scope = viewLifecycleOwner.lifecycleScope,
            sheetTitle = "春播小药师 · 问诊档案",
            newSessionTitle = "开启全新问诊会话",
            newSessionSub = "开始新的症状问询、导医分诊或用药咨询",
            loadSessions = {
                val histMap = ApiClient.service.getSessionHistory("mall", userId)
                val cardList = mutableListOf<SessionHistoryCardData>()
                histMap.forEach { (timeGroup, list) ->
                    list.forEach { item ->
                        val title = item["title"]?.toString() ?: "问诊咨询"
                        val timeRaw = item["updateTime"]?.toString() ?: item["createTime"]?.toString()
                        val friendlyTime = ChatHistoryBottomSheetHelper.formatFriendlyTime(timeRaw)
                        val sid = item["sessionId"]?.toString() ?: ""
                        if (sid.isNotEmpty()) {
                            cardList.add(
                                SessionHistoryCardData(
                                    sessionId = sid,
                                    title = title,
                                    timeDisplay = friendlyTime,
                                    badge = if (timeGroup == "当天") "今天" else timeGroup,
                                    rawData = item
                                )
                            )
                        }
                    }
                }
                cardList
            },
            onNewSession = {
                startNewSession()
            },
            onSessionSelect = { card ->
                restoreSessionFromCloud(card.sessionId, card.title)
            },
            onSessionDelete = { card ->
                ApiClient.service.deleteSession("mall", card.sessionId, userId)
                Unit
            }
        )
    }

    /** 从云端 Redis ChatMemory 取回历史消息还原界面 */
    private fun restoreSessionFromCloud(sessionId: String, title: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val msgs = withContext(Dispatchers.IO) {
                    ApiClient.service.getSessionMessages(sessionId)
                }
                currentSessionId = sessionId
                currentSessionTitle = title
                updateSessionTitleBar()

                val chatMsgs = msgs.map { m ->
                    val role = m["role"] ?: "assistant"
                    val content = m["content"] ?: ""
                    ChatMessage(
                        role = role,
                        content = content,
                        processDone = true
                    )
                }
                if (chatMsgs.isEmpty()) {
                    // 云端消息可能已过期（Redis 重启/窗口清理），如实告知，绝不假装加载成功
                    chatAdapter.submitList(
                        listOf(
                            ChatMessage(
                                role = "assistant",
                                content = "📭 会话「$title」的云端消息记录已过期或为空（服务端聊天记忆被清理）。\n\n" +
                                        "标题已恢复，你可以直接在本会话继续提问，新消息将重新记录。",
                                processDone = true
                            )
                        )
                    )
                    Toast.makeText(requireContext(), "该会话云端消息已过期，仅恢复标题", Toast.LENGTH_SHORT).show()
                } else {
                    chatAdapter.submitList(chatMsgs)
                    binding.rvChatMessages.scrollToPosition(chatAdapter.itemCount - 1)
                    Toast.makeText(requireContext(), "已加载云端会话记忆「$title」", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "还原历史消息失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
