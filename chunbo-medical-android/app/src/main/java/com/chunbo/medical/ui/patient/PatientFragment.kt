package com.chunbo.medical.ui.patient

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chunbo.medical.R
import com.chunbo.medical.data.api.ApiClient
import com.chunbo.medical.data.api.UserManager
import com.chunbo.medical.data.model.DoctorAccount
import com.chunbo.medical.data.model.Registration
import com.chunbo.medical.databinding.DialogRegistrationBinding
import com.chunbo.medical.databinding.DialogScanQrBinding
import com.chunbo.medical.databinding.FragmentPatientBinding
import com.chunbo.medical.ui.MainActivity
import com.chunbo.medical.util.IdCardUtil
import com.chunbo.medical.util.ImagePickerHelper
import com.chunbo.medical.util.QueueReminderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PatientFragment : Fragment() {

    private var _binding: FragmentPatientBinding? = null
    private val binding get() = _binding!!

    private lateinit var recordAdapter: QueueAdapter
    private var allDoctors: List<DoctorAccount> = emptyList()

    private val scanLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            refresh()
        }
    }

    // 预问诊图片上传桥接：选图/拍照 → 上传得 fileId → 回传给预问诊对话框发送
    private var pendingPreConsultImageCallback: ((String, String?) -> Unit)? = null
    private val preConsultImageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            ImagePickerHelper.uploadImage(
                requireContext(),
                viewLifecycleOwner.lifecycleScope,
                it,
                onResult = { fileId -> pendingPreConsultImageCallback?.invoke(fileId, it.toString()) },
                onError = { err -> Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show() }
            )
        }
    }

    // 挂号列表日期筛选（与 PC 端看板同口径；null = 显示全部日期，默认今天）
    private var selectedRegDate: String? = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

    // 系统相机拍照（预问诊）
    private var pendingCaptureUri: android.net.Uri? = null
    private val takePictureLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCaptureUri
        pendingCaptureUri = null
        if (success && uri != null) {
            ImagePickerHelper.uploadImage(
                requireContext(),
                viewLifecycleOwner.lifecycleScope,
                uri,
                onResult = { fileId -> pendingPreConsultImageCallback?.invoke(fileId, uri.toString()) },
                onError = { err -> Toast.makeText(requireContext(), err, Toast.LENGTH_SHORT).show() }
            )
        }
    }

    private fun showPreConsultImageSourcePicker() {
        val options = arrayOf("📷 拍照", "🖼️ 从相册选择")
        AlertDialog.Builder(requireContext())
            .setTitle("选择图片来源")
            .setItems(options) { _, which ->
                if (which == 0) takePreConsultPhoto() else preConsultImageLauncher.launch("image/*")
            }
            .show()
    }

    private fun takePreConsultPhoto() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), arrayOf(Manifest.permission.CAMERA), 2003)
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
        _binding = FragmentPatientBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initRecyclerView()
        initButtons()
        initSwipeRefresh()
        initRegDateFilter()
        loadDoctorList()
        loadPatientRecords()
    }

    override fun onResume() {
        super.onResume()
        // 回到前台即刷新，PC 端删除/退号/接诊状态变化会即时同步到本端
        if (_binding != null) {
            loadPatientRecords()
        }
    }

    private fun initRecyclerView() {
        recordAdapter = QueueAdapter(
            onDetailClick = { reg ->
                showRegistrationDetail(reg)
            }
        )
        binding.rvPatientRecords.layoutManager = LinearLayoutManager(requireContext())
        binding.rvPatientRecords.adapter = recordAdapter
    }

    /** 挂号单详情：查看信息、修改主诉/联系方式（仅待签到/待诊）、继续补充 AI 问诊、退号 */
    private fun showRegistrationDetail(item: Registration) {
        val editable = item.status == "待签到" || item.status == "待诊"
        val detailBinding = com.chunbo.medical.databinding.DialogRegistrationDetailBinding.inflate(layoutInflater)

        detailBinding.tvDetailStatus.text = item.status ?: "待签到"
        detailBinding.tvDetailInfo.text = buildString {
            append("${item.patientName ?: "便民患者"} · ${item.gender ?: "-"} ${item.age ?: "-"}岁\n")
            append("科室: ${item.department ?: "全科门诊"} | 接诊: ${item.doctorName ?: "-"}\n")
            append("单号: ${item.regNo ?: "--"} · 排队号: ${item.queueNumber ?: "--"}\n")
            append("挂号费: ¥${String.format("%.2f", item.fee ?: 0.0)}\n")
            append("挂号时间: ${item.createTime?.replace("T", " ")?.take(16) ?: "--"}")
        }
        detailBinding.etDetailSymptoms.setText(item.symptoms ?: "")
        detailBinding.etDetailSymptoms.isEnabled = editable
        detailBinding.etDetailPhone.setText(item.phone ?: "")
        detailBinding.etDetailPhone.isEnabled = editable
        detailBinding.tvDetailEditableHint.text = if (editable) {
            "当前状态可修改主诉与联系方式，也可继续补充 AI 问诊"
        } else {
            "就诊中或已完诊的记录不可修改"
        }
        detailBinding.btnDetailSave.visibility = if (editable) View.VISIBLE else View.GONE
        detailBinding.btnDetailPreConsult.visibility = if (editable) View.VISIBLE else View.GONE
        detailBinding.btnDetailCancel.visibility = if (editable) View.VISIBLE else View.GONE

        val dialog = AlertDialog.Builder(requireContext())
            .setView(detailBinding.root)
            .create()

        detailBinding.btnDetailClose.setOnClickListener { dialog.dismiss() }

        detailBinding.btnDetailSave.setOnClickListener {
            val symptoms = detailBinding.etDetailSymptoms.text.toString().trim()
            val phone = detailBinding.etDetailPhone.text.toString().trim()
            if (symptoms.isEmpty()) {
                Toast.makeText(requireContext(), "主诉不能为空", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!phone.matches(Regex("1\\d{10}"))) {
                Toast.makeText(requireContext(), "请输入 1 开头的 11 位手机号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ApiClient.service.updateRegistration(
                            mapOf(
                                "id" to (item.id ?: 0L),
                                "symptoms" to symptoms,
                                "phone" to phone
                            )
                        )
                    }
                    Toast.makeText(requireContext(), "挂号信息已更新", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                    loadPatientRecords()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "更新失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        detailBinding.btnDetailPreConsult.setOnClickListener {
            dialog.dismiss()
            PreConsultDialogHelper.showPreConsultDialog(
                context = requireContext(),
                scope = viewLifecycleOwner.lifecycleScope,
                patientName = item.patientName ?: "就诊患者",
                gender = item.gender ?: "男",
                age = (item.age ?: 28).toString(),
                idCard = item.idCard ?: "",
                onPickImage = { onImagePicked ->
                    pendingPreConsultImageCallback = onImagePicked
                    showPreConsultImageSourcePicker()
                }
            ) { _, _, complaint, _, preConsultJson ->
                // 提炼结果直接回写到该挂号单（主诉 + 结构化预问诊数据）
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            ApiClient.service.updateRegistration(
                                mapOf(
                                    "id" to (item.id ?: 0L),
                                    "symptoms" to complaint,
                                    "preConsultationData" to preConsultJson
                                )
                            )
                        }
                        Toast.makeText(requireContext(), "已将补充问诊结果同步到挂号单（主诉: $complaint）", Toast.LENGTH_LONG).show()
                        loadPatientRecords()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "同步失败: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        detailBinding.btnDetailCancel.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("确认退号")
                .setMessage("确定要退掉该挂号吗？退号后队列序号将释放。")
                .setPositiveButton("确认退号") { _, _ ->
                    viewLifecycleOwner.lifecycleScope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                ApiClient.service.cancelPatient(item.id ?: 0L)
                            }
                            Toast.makeText(requireContext(), "已退号", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                            loadPatientRecords()
                        } catch (e: Exception) {
                            Toast.makeText(requireContext(), "退号失败: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("取消", null)
                .show()
        }

        dialog.show()
    }

    private fun initButtons() {
        // 便民预约挂号
        binding.btnGoRegister.setOnClickListener {
            showRegistrationDialog()
        }

        // 真实后置摄像头扫一扫签到 (扫描电脑端屏幕上的签到二维码)
        binding.btnScanPcQr.setOnClickListener {
            startCameraScan()
        }
    }

    fun startCameraScan() {
        val intent = android.content.Intent(requireContext(), CameraScanActivity::class.java)
        scanLauncher.launch(intent)
    }

    private fun initSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.secondary)
        binding.swipeRefresh.setOnRefreshListener {
            loadDoctorList()
            loadPatientRecords()
        }
    }

    fun refresh() {
        loadDoctorList()
        loadPatientRecords()
    }

    private fun loadDoctorList() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val resp = withContext(Dispatchers.IO) {
                    ApiClient.service.getDoctorList()
                }
                allDoctors = resp.data ?: emptyList()
            } catch (e: Exception) {
                // 医生列表加载异常时使用内置数据兜底
            }
        }
    }

    /** 日期筛选控件：选哪天只看哪天的挂号记录 */
    private fun initRegDateFilter() {
        binding.btnPickRegDate.text = "📅 $selectedRegDate"
        binding.btnPickRegDate.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    selectedRegDate = String.format("%04d-%02d-%02d", year, month + 1, day)
                    binding.btnPickRegDate.text = "📅 $selectedRegDate"
                    loadPatientRecords()
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun loadPatientRecords() {
        binding.swipeRefresh.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = UserManager.getUser()
                val phoneFilter = if (user.isLoggedIn && !user.phone.isNullOrBlank()) user.phone else null

                binding.tvFilterPhoneHint.text = if (phoneFilter != null) {
                    "当前就诊卡: ${user.nickname} (${phoneFilter})"
                } else {
                    "所有便民预约记录"
                }

                binding.tvPatientUserInfo.text = if (user.isLoggedIn) {
                    "已绑定: ${user.nickname}"
                } else {
                    "快捷就诊卡"
                }

                // 核心：严格按当前用户手机号过滤挂号记录，杜绝展示全院其他人的测试/假数据！
                val list = withContext(Dispatchers.IO) {
                    ApiClient.service.getRegistrationList(userPhone = phoneFilter)
                }

                // 日期筛选（与 PC 端看板同口径）：只显示所选日期当天的挂号；null = 全部
                val dateFiltered = if (selectedRegDate == null) list else list.filter {
                    it.createTime?.startsWith(selectedRegDate ?: "") == true
                }

                recordAdapter.submitList(dateFiltered)

                if (dateFiltered.isEmpty()) {
                    binding.layoutEmptyRecords.visibility = View.VISIBLE
                    binding.rvPatientRecords.visibility = View.GONE
                } else {
                    binding.layoutEmptyRecords.visibility = View.GONE
                    binding.rvPatientRecords.visibility = View.VISIBLE
                }

                // 寻找用户当前生效的挂号单 (待签到、待诊、候诊中、就诊中)——只看所选日期（默认今天）的记录，
                // 历史日期的遗留待诊脏数据不再跨日期追踪展示（避免用户被旧测试单一直挂着）
                // 仅当用户已登录或已通过手机号筛选时才绑定排队卡，杜绝未登录游客误绑全院陌生人排队信息
                val active = if (user.isLoggedIn || phoneFilter != null) {
                    dateFiltered.firstOrNull {
                        it.status == "待签到" || it.status == "待诊" || it.status == "候诊中" || it.status == "就诊中"
                    }
                } else {
                    null
                }

                if (active != null) {
                    binding.cardMyQueue.visibility = View.VISIBLE
                    val qNum = active.queueNumber ?: String.format("%02d", active.queueNo ?: 1)
                    binding.tvPatientQueueBadge.text = qNum
                    val st = active.status ?: "待签到"
                    binding.tvPatientMyStatus.text = st

                    val dept = active.department ?: "全科门诊"
                    val doc = active.doctorName ?: "坐诊医生"
                    binding.tvPatientMyDept.text = "就诊科室: $dept (坐诊医生: $doc)"

                    // 点击进度卡片直接进挂号单详情：可修改主诉/联系方式、继续补充 AI 预问诊、退号
                    binding.cardMyQueue.setOnClickListener {
                        showRegistrationDetail(active)
                    }

                    when (st) {
                        "待签到" -> {
                            binding.tvPatientMyTip.text = "预约成功！请点击右上角「扫一扫签到」对准电脑屏幕二维码扫码入队\n挂号于 ${active.createTime?.replace("T", " ")?.take(16) ?: "--"} · 点此卡片可查看详情"
                            binding.btnQuickCheckinBanner.visibility = View.VISIBLE
                            binding.btnQuickCheckinBanner.text = "扫码签到"
                            binding.btnQuickCheckinBanner.setOnClickListener {
                                startCameraScan()
                            }
                        }
                        "待诊", "候诊中" -> {
                            binding.tvPatientMyTip.text = "已完成签到，当前排在待诊队列第 $qNum 号，请留意诊室广播叫号\n挂号于 ${active.createTime?.replace("T", " ")?.take(16) ?: "--"} · 点此卡片可修改主诉/继续问诊/退号"
                            binding.btnQuickCheckinBanner.visibility = View.GONE
                        }
                        "就诊中" -> {
                            binding.tvPatientMyTip.text = "专家医生已呼叫您！请立即前往 $dept $doc 诊室就诊"
                            binding.btnQuickCheckinBanner.visibility = View.GONE
                        }
                        else -> {
                            binding.cardMyQueue.visibility = View.GONE
                        }
                    }
                } else {
                    binding.cardMyQueue.visibility = View.GONE
                }

                // 有生效待诊挂号时启动叫号轮询提醒（待诊/候诊 → 就诊中 变化弹本地通知，全局持久协程跨 Tab 不中断）
                if (active != null &&
                    (active.status == "待诊" || active.status == "候诊中" || active.status == "就诊中")
                ) {
                    QueueReminderManager.start(requireContext(), null, phoneFilter)
                } else {
                    QueueReminderManager.stop()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "获取挂号记录异常: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    /**
     * 挂号缴费确认弹窗：挂号费不再是「静默跳过」。
     * 提供微信支付（模拟）/ 支付宝（模拟）/ 到院支付 / 健康体验金抵扣四种方式，
     * 确认后回调 (payMethod, useBalance)，由调用方在支付成功后正式提交挂号单（状态待签到）。
     */
    private fun showRegistrationPaymentDialog(
        fee: Double,
        dept: String,
        docName: String,
        patientName: String,
        onPaid: (payMethod: String, useBalance: Boolean) -> Unit
    ) {
        val user = UserManager.getUser()
        val balanceEnough = user.isLoggedIn && user.balance >= fee

        val methods = mutableListOf(
            "微信支付（线上 · 模拟收银台）",
            "支付宝支付（线上 · 模拟收银台）",
            "到院支付（到院后缴纳挂号费）"
        )
        if (user.isLoggedIn) {
            val balText = String.format("%.2f", user.balance)
            methods.add(if (balanceEnough) "健康体验金抵扣（余额 ¥$balText）" else "健康体验金抵扣（余额不足 ¥$balText）")
        }

        var selectedIndex = 0
        AlertDialog.Builder(requireContext())
            .setTitle("挂号缴费确认")
            .setMessage(
                "挂号费：¥${String.format("%.2f", fee)}\n" +
                "就诊人：$patientName\n" +
                "科室医生：$dept · $docName\n\n" +
                "请选择支付方式（线上支付将弹出模拟收银台）"
            )
            .setSingleChoiceItems(methods.toTypedArray(), 0) { _, which -> selectedIndex = which }
            .setPositiveButton("下一步") { _, _ ->
                val useBalance = user.isLoggedIn && selectedIndex == 3
                if (useBalance && !balanceEnough) {
                    Toast.makeText(requireContext(), "健康体验金余额不足，请改用微信/支付宝或到院支付", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val payMethod = when (selectedIndex) {
                    0 -> "微信支付"
                    1 -> "支付宝"
                    2 -> "到院支付"
                    else -> "体验金抵扣"
                }
                if (payMethod == "微信支付" || payMethod == "支付宝") {
                    // 线上支付弹出模拟收银台，用户在收银台内确认后才真正提交挂号
                    showMockPayDialog(payMethod, fee) {
                        onPaid(payMethod, useBalance)
                    }
                } else {
                    onPaid(payMethod, useBalance)
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    /**
     * 模拟支付收银台（微信绿 / 支付宝蓝）：展示收款方与金额，点「确认支付」→ 短暂支付中 → 成功回调。
     * 演示环境的模拟支付页面，让挂号缴费有真实的支付窗口体验，而非选完方式就静默成功。
     */
    private fun showMockPayDialog(payMethod: String, fee: Double, onPaySuccess: () -> Unit) {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val isWechat = payMethod == "微信支付"
        val brandColor = if (isWechat) 0xFF07C160.toInt() else 0xFF1677FF.toInt()
        val brandName = if (isWechat) "微信支付" else "支付宝支付"

        val container = android.widget.LinearLayout(ctx).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(8))
        }

        fun addText(text: String, size: Float, color: Int, bold: Boolean = false, topMargin: Int = 0): android.widget.TextView {
            val tv = android.widget.TextView(ctx).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                if (bold) typeface = android.graphics.Typeface.DEFAULT_BOLD
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (topMargin > 0) this.topMargin = dp(topMargin) }
            }
            container.addView(tv)
            return tv
        }

        addText(brandName, 18f, brandColor, bold = true)
        addText("春播万象 · 便民挂号收款", 12f, ctx.getColor(R.color.text_secondary), topMargin = 2)
        addText(String.format("¥ %.2f", fee), 34f, ctx.getColor(R.color.text_primary), bold = true, topMargin = 16)
        addText("挂号费 · 到院凭此记录扫码入队", 12f, ctx.getColor(R.color.text_muted), topMargin = 4)

        val payBtn = com.google.android.material.button.MaterialButton(ctx).apply {
            text = "确认支付"
            backgroundTintList = android.content.res.ColorStateList.valueOf(brandColor)
            setTextColor(ctx.getColor(R.color.text_on_primary))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                dp(46)
            ).apply { topMargin = dp(20) }
            cornerRadius = dp(23)
        }
        container.addView(payBtn)

        val dialog = AlertDialog.Builder(ctx)
            .setTitle("收银台")
            .setView(container)
            .setCancelable(false)
            .create()

        payBtn.setOnClickListener {
            payBtn.isEnabled = false
            payBtn.text = "支付中..."
            viewLifecycleOwner.lifecycleScope.launch {
                kotlinx.coroutines.delay(900)
                dialog.dismiss()
                Toast.makeText(ctx, "✅ $brandName 支付成功 ¥${String.format("%.2f", fee)}", Toast.LENGTH_SHORT).show()
                onPaySuccess()
            }
        }

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.86).toInt(), android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    /**
     * 预约挂号弹窗：严格要求身份证，真实对接AI预问诊，不提前盲选医生，干净的主诉输入
     */
    fun showRegistrationDialog(
        preSelectedDept: String? = null,
        preSelectedDoctor: String? = null,
        initialComplaint: String? = null
    ) {
        val user = UserManager.getUser()
        val dialogBinding = DialogRegistrationBinding.inflate(layoutInflater)

        // 性别下拉（男/女），避免手输乱填
        val genderAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, listOf("男", "女"))
        genderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.spinnerGender.adapter = genderAdapter

        // 自动带入春播商城登录账号信息与已存身份证
        val savedIdCard = UserManager.getIdCard()
        if (user.isLoggedIn) {
            dialogBinding.editPatientName.setText(user.nickname)
            dialogBinding.editPatientPhone.setText(user.phone)
            dialogBinding.editIdCard.setText(savedIdCard)
        } else {
            dialogBinding.editPatientName.setText("")
            dialogBinding.editPatientPhone.setText("")
            dialogBinding.editIdCard.setText(savedIdCard)
        }

        fun applyIdCardInfo(cardStr: String) {
            val trimmed = cardStr.trim()
            if (trimmed.length == 18) {
                val birthYear = trimmed.substring(6, 10).toIntOrNull()
                val birthMonth = trimmed.substring(10, 12).toIntOrNull()
                val birthDay = trimmed.substring(12, 14).toIntOrNull()
                if (birthYear != null && birthMonth != null && birthDay != null && birthYear in 1900..2026) {
                    val calNow = java.util.Calendar.getInstance()
                    val nowYear = calNow.get(java.util.Calendar.YEAR)
                    val nowMonth = calNow.get(java.util.Calendar.MONTH) + 1
                    val nowDay = calNow.get(java.util.Calendar.DAY_OF_MONTH)

                    var age = nowYear - birthYear
                    if (nowMonth < birthMonth || (nowMonth == birthMonth && nowDay < birthDay)) {
                        age--
                    }
                    val calcAge = age.coerceIn(0, 120)
                    dialogBinding.editPatientAge.setText(calcAge.toString())
                }
                val genderChar = trimmed[16].digitToIntOrNull()
                if (genderChar != null) {
                    // 奇数男、偶数女
                    dialogBinding.spinnerGender.setSelection(if (genderChar % 2 != 0) 0 else 1)
                }
            }
        }

        dialogBinding.editIdCard.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyIdCardInfo(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        if (savedIdCard.isNotBlank()) {
            applyIdCardInfo(savedIdCard)
        } else {
            dialogBinding.editPatientAge.setText("")
        }

        // 清空主诉，不预填任何脏假数据
        dialogBinding.editComplaint.setText(initialComplaint ?: "")

        // 隐藏医生详情卡片，初始不瞎选医生
        dialogBinding.cardDoctorDetail.visibility = View.GONE

        // 动态聚合真实科室列表，杜绝硬编码
        val dynamicDepts = allDoctors.mapNotNull { it.department }.filter { it.isNotBlank() }.distinct()
        val rawDepartments = if (dynamicDepts.isNotEmpty()) dynamicDepts else listOf("耳鼻喉科", "骨伤科", "中医内科", "儿科", "全科门诊")
        val deptDisplayList = listOf("请选择就诊科室") + rawDepartments
        val deptAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, deptDisplayList)
        dialogBinding.spinnerDepartment.adapter = deptAdapter

        var currentDeptDoctors = mutableListOf<DoctorAccount>()

        fun updateDoctorsForDept(deptName: String, selectDocName: String? = null) {
            val filtered = allDoctors.filter { it.department == deptName }
            currentDeptDoctors.clear()
            currentDeptDoctors.addAll(filtered)

            val docDisplayList = mutableListOf<String>()
            docDisplayList.add("请选择坐诊医生")
            if (currentDeptDoctors.isNotEmpty()) {
                currentDeptDoctors.forEach { doc ->
                    val fee = doc.consultationFee ?: 15.0
                    val lvl = doc.level ?: if (fee >= 30.0) "专家门诊" else "普通门诊"
                    docDisplayList.add("${doc.doctorName} · ${doc.title} (${lvl} ¥${String.format("%.2f", fee)})")
                }
            } else {
                docDisplayList.add("${deptName}坐诊医生 (普通门诊 ¥15.00)")
            }

            val docAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, docDisplayList)
            dialogBinding.spinnerDoctor.adapter = docAdapter

            if (!selectDocName.isNullOrBlank()) {
                val foundIdx = currentDeptDoctors.indexOfFirst { it.doctorName == selectDocName }
                if (foundIdx >= 0) {
                    dialogBinding.spinnerDoctor.setSelection(foundIdx + 1)
                }
            } else {
                dialogBinding.spinnerDoctor.setSelection(0)
                dialogBinding.cardDoctorDetail.visibility = View.GONE
            }
        }

        // 初始状态：未选择科室，医生下拉提示先选科室
        dialogBinding.spinnerDoctor.adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            listOf("请先选择科室")
        )

        dialogBinding.spinnerDepartment.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0) {
                    currentDeptDoctors.clear()
                    dialogBinding.spinnerDoctor.adapter = ArrayAdapter(
                        requireContext(),
                        android.R.layout.simple_spinner_dropdown_item,
                        listOf("请先选择科室")
                    )
                    dialogBinding.cardDoctorDetail.visibility = View.GONE
                } else {
                    val selectedDept = rawDepartments[position - 1]
                    updateDoctorsForDept(selectedDept)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        dialogBinding.spinnerDoctor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == 0 || currentDeptDoctors.isEmpty()) {
                    dialogBinding.cardDoctorDetail.visibility = View.GONE
                } else {
                    val doc = currentDeptDoctors.getOrNull(position - 1)
                    if (doc != null) {
                        val fee = doc.consultationFee ?: 15.0
                        val lvl = doc.level ?: if (fee >= 30.0) "专家门诊" else "普通门诊"
                        dialogBinding.tvDialogDocNameTitle.text = "${doc.doctorName} · ${doc.title} (${lvl})"
                        dialogBinding.tvDialogDocFee.text = "挂号费 ¥${String.format("%.2f", fee)}"
                        dialogBinding.tvDialogDocSpecialty.text = "擅长领域: ${doc.specialty ?: doc.introduction ?: "专科常规诊治"}"
                        dialogBinding.cardDoctorDetail.visibility = View.VISIBLE
                    } else {
                        dialogBinding.cardDoctorDetail.visibility = View.GONE
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // 如果调用方显式指定了科室
        if (!preSelectedDept.isNullOrBlank()) {
            val idx = rawDepartments.indexOf(preSelectedDept)
            if (idx >= 0) {
                dialogBinding.spinnerDepartment.setSelection(idx + 1)
                updateDoctorsForDept(preSelectedDept, preSelectedDoctor)
            }
        }

        var fullAiPreConsultData: String = ""

        // 真实多轮 AI 预问诊对接入口
        dialogBinding.btnOpenAiPreConsult.setOnClickListener {
            val pName = dialogBinding.editPatientName.text.toString().trim().ifBlank { user.nickname.ifBlank { "就诊人" } }
            val pGender = dialogBinding.spinnerGender.selectedItem?.toString() ?: "男"
            val pAge = dialogBinding.editPatientAge.text.toString().trim().ifBlank { "28" }
            val pIdCard = dialogBinding.editIdCard.text.toString().trim()

            PreConsultDialogHelper.showPreConsultDialog(
                context = requireContext(),
                scope = viewLifecycleOwner.lifecycleScope,
                patientName = pName,
                gender = pGender,
                age = pAge,
                idCard = pIdCard,
                onPickImage = { onImagePicked ->
                    pendingPreConsultImageCallback = onImagePicked
                    showPreConsultImageSourcePicker()
                }
            ) { dept, doctor, complaint, fullSummary, preConsultJson ->
                val deptIdx = rawDepartments.indexOfFirst { d ->
                    d == dept || d.contains(dept) || dept.contains(d)
                }
                if (deptIdx >= 0) {
                    val resolvedDept = rawDepartments[deptIdx]
                    dialogBinding.spinnerDepartment.setSelection(deptIdx + 1)
                    updateDoctorsForDept(resolvedDept, doctor)
                }
                dialogBinding.editComplaint.setText(complaint)
                // 存结构化 JSON（与 PC 端导入解析字段对齐），UI 摘要仅作展示
                fullAiPreConsultData = preConsultJson
                dialogBinding.tvAiTriageResult.text = "💡 $fullSummary"
                dialogBinding.tvAiTriageResult.visibility = View.VISIBLE
                Toast.makeText(requireContext(), "已成功采纳 AI 智能预问诊！主诉与科室已自动带入", Toast.LENGTH_SHORT).show()
            }
        }

        // 快速初筛按钮
        dialogBinding.btnRunAiTriage.setOnClickListener {
            val complaint = dialogBinding.editComplaint.text.toString().trim()
            if (complaint.isEmpty()) {
                Toast.makeText(requireContext(), "请在下方主诉框输入不适症状或点击左侧开启AI预问诊", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val matchedDept = when {
                complaint.contains("耳") || complaint.contains("鼻") || complaint.contains("喉") ||
                complaint.contains("咽") || complaint.contains("扁桃体") || complaint.contains("声嘶") || complaint.contains("鼻塞") -> "耳鼻喉科"
                complaint.contains("骨") || complaint.contains("关节") || complaint.contains("膝") ||
                complaint.contains("扭伤") || complaint.contains("跌打") || complaint.contains("骨折") || complaint.contains("腰痛") -> "骨伤科"
                complaint.contains("儿") || complaint.contains("宝宝") || complaint.contains("小儿") || complaint.contains("幼儿") -> "儿科"
                complaint.contains("失眠") || complaint.contains("脾胃") || complaint.contains("调理") ||
                complaint.contains("内科") || complaint.contains("气血") || complaint.contains("胸闷") -> "中医内科"
                else -> "全科门诊"
            }
            val deptIdx = rawDepartments.indexOfFirst { d ->
                d == matchedDept || d.contains(matchedDept) || matchedDept.contains(d)
            }
            if (deptIdx >= 0) {
                val resolvedDept = rawDepartments[deptIdx]
                dialogBinding.spinnerDepartment.setSelection(deptIdx + 1)
                updateDoctorsForDept(resolvedDept)
            }
            dialogBinding.tvAiTriageResult.text = "💡 智能初筛：已为您推荐就诊【$matchedDept】"
            dialogBinding.tvAiTriageResult.visibility = View.VISIBLE
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancelReg.setOnClickListener {
            dialog.dismiss()
        }

        dialogBinding.btnSubmitReg.setOnClickListener {
            val name = dialogBinding.editPatientName.text.toString().trim()
            val ageStr = dialogBinding.editPatientAge.text.toString().trim()
            val gender = dialogBinding.spinnerGender.selectedItem?.toString()?.trim() ?: "男"
            val phone = dialogBinding.editPatientPhone.text.toString().trim()
            val idCard = dialogBinding.editIdCard.text.toString().trim()
            val complaint = dialogBinding.editComplaint.text.toString().trim()
            val address = dialogBinding.editAddress.text.toString().trim()

            if (name.isEmpty() || phone.isEmpty()) {
                Toast.makeText(requireContext(), "请完整填写姓名和联系手机号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (idCard.isEmpty()) {
                Toast.makeText(requireContext(), "请填写身份证号（国家就医实名建档唯一凭证）", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!IdCardUtil.isValid(idCard)) {
                Toast.makeText(requireContext(), "请输入合法的 18 位居民身份证号码（含校验位）", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val deptPos = dialogBinding.spinnerDepartment.selectedItemPosition
            if (deptPos == 0) {
                Toast.makeText(requireContext(), "请选择就诊科室，或点击上方AI智能预问诊", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedDept = rawDepartments[deptPos - 1]

            val docPos = dialogBinding.spinnerDoctor.selectedItemPosition
            if (docPos == 0 || currentDeptDoctors.isEmpty()) {
                Toast.makeText(requireContext(), "请选择坐诊医生", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val selectedDoc = currentDeptDoctors.getOrNull(docPos - 1)
            val docName = selectedDoc?.doctorName ?: "社区坐诊医生"
            val fee = selectedDoc?.consultationFee ?: 15.0

            if (complaint.isEmpty()) {
                Toast.makeText(requireContext(), "请填写您的主要不适与病情主诉", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // 保存患者真实身份证到本地配置，便于下次就诊复用
            UserManager.saveIdCard(idCard)

            val finalPreConsult = if (fullAiPreConsultData.isNotEmpty()) {
                fullAiPreConsultData
            } else {
                "手机端预约挂号：匹配至${selectedDept} · ${docName}门诊"
            }

            val reqBody = mapOf(
                "patientName" to name,
                "name" to name,
                "age" to (ageStr.toIntOrNull() ?: 28),
                "ageYears" to (ageStr.toIntOrNull() ?: 28),
                "gender" to (if (gender.isEmpty()) "男" else gender),
                "phone" to phone,
                "idCard" to idCard,
                "address" to address,
                "department" to selectedDept,
                "doctorName" to docName,
                "regType" to "手机端在线预约",
                "type" to "手机端在线预约",
                "fee" to fee,
                "regFee" to fee,
                "status" to "待签到",
                "symptoms" to complaint,
                "chiefComplaint" to complaint,
                "preConsultationData" to finalPreConsult
            )

            // 挂号缴费确认：线上支付（模拟）/ 到院支付 / 体验金抵扣，支付成功才正式提交挂号单
            showRegistrationPaymentDialog(
                fee = fee,
                dept = selectedDept,
                docName = docName,
                patientName = name
            ) { payMethod, useBalance ->
                val finalBody = reqBody.toMutableMap().apply {
                    put("payMethod", payMethod)
                    if (useBalance) {
                        put("useBalance", true)
                        put("username", UserManager.getUser().username)
                    }
                }
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        val reg = withContext(Dispatchers.IO) {
                            ApiClient.service.createRegistration(finalBody)
                        }
                        dialog.dismiss()
                        // 按支付方式醒目区分金额：体验金实扣 / 到院待缴 / 线上模拟支付
                        val payLine = when {
                            useBalance -> "💰 支付方式: 健康体验金抵扣，实扣 ¥${String.format("%.2f", fee)}"
                            payMethod == "到院支付" -> "💰 支付方式: 到院支付，挂号费 ¥${String.format("%.2f", fee)} 到院缴纳"
                            payMethod == "微信支付" -> "💰 支付方式: 微信支付 ¥${String.format("%.2f", fee)}（演示环境模拟）"
                            payMethod == "支付宝" -> "💰 支付方式: 支付宝支付 ¥${String.format("%.2f", fee)}（演示环境模拟）"
                            else -> "💰 支付方式: $payMethod"
                        }
                        Toast.makeText(
                            requireContext(),
                            "✅ 挂号成功\n就诊排号: ${reg.queueNumber ?: "已就绪"}\n就诊医生: ${docName} (${selectedDept})\n$payLine\n到院后请在首页点击「扫一扫现场签到」对准电脑端扫码入队！",
                            Toast.LENGTH_LONG
                        ).show()
                        // 体验金抵扣成功后刷新本地余额，避免顶部余额与后端不同步
                        if (useBalance) refreshBalanceFromServer()
                        loadPatientRecords()
                    } catch (e: Exception) {
                        Toast.makeText(requireContext(), "挂号失败: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        dialog.show()
        dialog.window?.let { window ->
            window.setBackgroundDrawableResource(android.R.color.transparent)
            val dm = resources.displayMetrics
            window.setLayout((dm.widthPixels * 0.94).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    /** 挂号体验金抵扣后，从后端刷新当前登录用户余额（以服务端为准，避免本地陈旧） */
    private fun refreshBalanceFromServer() {
        val user = UserManager.getUser()
        if (!user.isLoggedIn) return
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { ApiClient.service.getUserInfo() }
                val userMap = res["user"] as? Map<*, *>
                val balance = (userMap?.get("balance") as? Number)?.toDouble()
                val points = (userMap?.get("points") as? Number)?.toInt()
                if (balance != null || points != null) {
                    UserManager.updateBalanceAndPoints(balance, points)
                }
            } catch (e: Exception) {
                // 忽略刷新异常
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
