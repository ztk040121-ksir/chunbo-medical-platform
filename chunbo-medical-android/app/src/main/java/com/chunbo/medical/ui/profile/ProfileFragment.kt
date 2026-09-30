package com.chunbo.medical.ui.profile

import android.app.AlertDialog
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.chunbo.medical.data.api.ApiClient
import com.chunbo.medical.data.api.UserManager
import com.chunbo.medical.databinding.FragmentProfileBinding
import com.chunbo.medical.ui.common.MallAuthHelper
import com.chunbo.medical.ui.mall.OrderAdapter
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.chunbo.medical.data.model.MallOrder
import com.chunbo.medical.data.model.PrescriptionWrapper
import com.chunbo.medical.data.model.PrescriptionDetail
import com.chunbo.medical.data.model.PrescriptionItem
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!

    private lateinit var orderAdapter: OrderAdapter
    private lateinit var prescriptionAdapter: PrescriptionAdapter
    // 商品 id → 图片 url 映射（订单卡片首件商品图）
    private val productImageMap = mutableMapOf<Long, String>()

    // 就诊记录日期筛选：选哪天只看哪天的就诊记录（默认今天，可切换任意日期）
    private var selectedRxDate: String = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())

    private val userChangeListener: () -> Unit = {
        activity?.runOnUiThread {
            renderUserState()
            loadPrescriptions()
            loadOrders()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initRecyclerViews()
        initListeners()
        initRxDateFilter()
        renderUserState()
        updateActiveRevisitCard()

        UserManager.addListener(userChangeListener)
        loadPrescriptions()
        loadOrders()
    }

    override fun onResume() {
        super.onResume()
        updateActiveRevisitCard()
    }

    private fun initRecyclerViews() {
        // 门诊处方与病历列表（点击看详情，AI 分析按钮跳转春播小药师）
        prescriptionAdapter = PrescriptionAdapter(
            onItemClick = { wrapper ->
                showPrescriptionDetail(wrapper)
            },
            onAiAnalyzeClick = { wrapper ->
                analyzePrescriptionWithAi(wrapper)
            }
        )
        binding.rvProfilePrescriptions.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProfilePrescriptions.adapter = prescriptionAdapter

        // 商城订单列表（带商品图片解析：按 itemsJson 的商品 id 匹配商城商品图）
        orderAdapter = OrderAdapter(
            onDetailClick = { order ->
                showOrderDetailDialog(order)
            },
            onReorderClick = { order ->
                handleReorder(order)
            },
            productImageResolver = { productId ->
                productImageMap[productId]
            }
        )
        binding.rvProfileOrders.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProfileOrders.adapter = orderAdapter
    }

    private fun initListeners() {
        binding.swipeRefreshProfile.setOnRefreshListener {
            renderUserState()
            loadPrescriptions()
            loadOrders()
        }

        binding.cardUserHeader.setOnClickListener {
            if (!UserManager.isLoggedIn()) {
                showLoginDialog()
            } else {
                showEditProfileDialog()
            }
        }

        binding.layoutAvatarWrapper.setOnClickListener {
            if (!UserManager.isLoggedIn()) {
                showLoginDialog()
            } else {
                showAvatarPickerDialog()
            }
        }

        binding.btnRefreshOrders.setOnClickListener {
            loadPrescriptions()
            loadOrders()
        }

        // 切换【我的就诊处方】与【购药订单】
        binding.toggleProfileTab.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    com.chunbo.medical.R.id.btn_tab_prescriptions -> {
                        binding.layoutPrescriptionsContainer.visibility = View.VISIBLE
                        binding.layoutOrdersContainer.visibility = View.GONE
                        loadPrescriptions()
                    }
                    com.chunbo.medical.R.id.btn_tab_orders -> {
                        binding.layoutPrescriptionsContainer.visibility = View.GONE
                        binding.layoutOrdersContainer.visibility = View.VISIBLE
                        loadOrders()
                    }
                }
            }
        }

        binding.btnEditProfileAddress.setOnClickListener {
            showEditAddressDialog()
        }
        // 系统设置与退出登录统一收口到右上角设置图标（AppSettingsDialog），此处不再重复实现
    }

    fun refresh() {
        renderUserState()
        loadPrescriptions()
        loadOrders()
    }

    private fun renderUserState() {
        val user = UserManager.getUser()
        if (user.isLoggedIn) {
            binding.tvProfileName.text = user.nickname
            binding.tvProfilePhone.text = "账号: ${user.username} | 手机: ${if (user.phone.isNotEmpty()) user.phone else "未绑定"}"
            binding.tvProfileActionHint.text = "编辑资料 >"

            binding.tvStatBalance.text = String.format("¥%.2f", user.balance)
            binding.tvStatPoints.text = "${user.points}"

            if (user.address.isNotBlank()) {
                binding.tvProfileAddress.text = user.address
            } else {
                binding.tvProfileAddress.text = "尚未设置收货地址，点击右侧修改添加"
            }

            binding.ivAvatarEditBadge.visibility = View.VISIBLE
            com.chunbo.medical.util.AvatarHelper.loadAvatar(binding.ivProfileAvatar, user.avatar)
        } else {
            binding.tvProfileName.text = "点击登录 / 注册"
            binding.tvProfilePhone.text = "新用户注册送200元健康体验金，支持送药到家"
            binding.tvProfileActionHint.text = "去登录 >"

            binding.tvStatBalance.text = "¥0.00"
            binding.tvStatPoints.text = "0"
            binding.tvProfileAddress.text = "登录后查看与编辑常用收货地址"

            binding.ivAvatarEditBadge.visibility = View.GONE
            binding.ivProfileAvatar.setImageResource(com.chunbo.medical.R.drawable.avatar_resident_1)
        }
        binding.swipeRefreshProfile.isRefreshing = false
    }

    /** 就诊记录日期筛选控件：选哪天只看哪天的就诊记录 */
    private fun initRxDateFilter() {
        binding.btnPickRxDate.text = "📅 $selectedRxDate"
        binding.btnPickRxDate.setOnClickListener {
            val cal = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                requireContext(),
                { _, year, month, day ->
                    selectedRxDate = String.format("%04d-%02d-%02d", year, month + 1, day)
                    binding.btnPickRxDate.text = "📅 $selectedRxDate"
                    loadPrescriptions()
                },
                cal.get(java.util.Calendar.YEAR),
                cal.get(java.util.Calendar.MONTH),
                cal.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun loadPrescriptions() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = UserManager.getUser()
                val phone = if (user.isLoggedIn && !user.phone.isNullOrBlank()) user.phone else null
                val pName = if (user.isLoggedIn && !user.nickname.isNullOrBlank()) user.nickname else null

                // 1. 同时拉取处方与门诊挂号档案（双轨合璧，彻底避免就诊后无记录）
                val rxList = try {
                    withContext(Dispatchers.IO) {
                        ApiClient.service.getPrescriptionList(phone = phone, patientName = pName)
                    }
                } catch (e: Exception) {
                    emptyList()
                }

                val regList = try {
                    withContext(Dispatchers.IO) {
                        ApiClient.service.getRegistrationList(userPhone = phone)
                    }
                } catch (e: Exception) {
                    emptyList()
                }

                // 2. 将挂号记录转换包装成统一的就诊档案
                val visitWrappers = mutableListOf<PrescriptionWrapper>()

                // 先把门诊挂号就医档案纳入（包含已诊、就诊中、预约记录）
                regList.forEach { reg ->
                    val matchedRx = rxList.firstOrNull { rx ->
                        val rxPatient = rx.prescription?.patientName ?: ""
                        val regPatient = reg.patientName ?: ""
                        val samePatient = rxPatient == regPatient || rxPatient == user.username || rxPatient == user.nickname
                        val rxDate = rx.prescription?.createTime?.replace("T", " ")?.take(10) ?: ""
                        val regDate = reg.createTime?.replace("T", " ")?.take(10) ?: ""
                        samePatient && rxDate.isNotEmpty() && rxDate == regDate
                    }

                    val detail = PrescriptionDetail(
                        id = reg.id,
                        prescriptionNo = matchedRx?.prescription?.prescriptionNo ?: reg.regNo,
                        patientId = reg.patientId,
                        patientName = reg.patientName ?: user.nickname,
                        gender = reg.gender,
                        age = reg.age,
                        doctorName = reg.doctorName ?: "门诊主治医师",
                        diagnosis = matchedRx?.prescription?.diagnosis ?: reg.symptoms ?: "常规门诊就医",
                        symptoms = reg.symptoms,
                        status = reg.status,
                        totalPrice = matchedRx?.prescription?.totalPrice,
                        totalAmount = matchedRx?.prescription?.totalAmount ?: reg.fee,
                        aiAdvice = matchedRx?.prescription?.aiAdvice ?: reg.preConsultationData,
                        createTime = reg.createTime ?: matchedRx?.prescription?.createTime
                    )

                    visitWrappers.add(
                        PrescriptionWrapper(
                            prescription = detail,
                            items = matchedRx?.items ?: emptyList()
                        )
                    )
                }

                // 再将未在挂号单中匹配到的独立处方也加入列表
                rxList.forEach { rx ->
                    val rxNo = rx.prescription?.prescriptionNo
                    if (visitWrappers.none { it.prescription?.prescriptionNo == rxNo }) {
                        visitWrappers.add(rx)
                    }
                }

                // 3. 排序：按就诊时间倒序
                visitWrappers.sortByDescending { it.prescription?.createTime ?: "" }

                // 4. 按选定日期过滤；若选定日期无数据但用户历史有数据，自动展示历史数据并在上方提示
                val dateFiltered = visitWrappers.filter {
                    val t = it.prescription?.createTime?.replace("T", " ") ?: ""
                    t.startsWith(selectedRxDate)
                }

                if (dateFiltered.isNotEmpty()) {
                    prescriptionAdapter.submitList(dateFiltered)
                    binding.tvNoPrescriptions.visibility = View.GONE
                    binding.tvRxDateHint.text = "已筛选 $selectedRxDate 就诊档案"
                } else if (visitWrappers.isNotEmpty()) {
                    // 当日无数据，但历史有数据：贴心展示历史就诊，不再让用户面对空空白板！
                    prescriptionAdapter.submitList(visitWrappers)
                    binding.tvNoPrescriptions.visibility = View.GONE
                    binding.tvRxDateHint.text = "当日无记录，显示全部就诊档案(${visitWrappers.size}条)"
                } else {
                    prescriptionAdapter.submitList(emptyList())
                    binding.tvNoPrescriptions.text = "📅 $selectedRxDate 暂无就诊记录，可点上方日期切换查看"
                    binding.tvNoPrescriptions.visibility = View.VISIBLE
                    binding.tvRxDateHint.text = "按就诊日期查看记录"
                }
            } catch (e: Exception) {
                binding.tvNoPrescriptions.visibility = View.VISIBLE
            }
        }
    }

    /** 把就诊记录（诊断+药品明细+价格）发给春播小药师做用药分析与指导 */
    private fun analyzePrescriptionWithAi(wrapper: PrescriptionWrapper) {
        val rx = wrapper.prescription ?: return
        val items = wrapper.items ?: emptyList()
        val medLines = if (items.isNotEmpty()) {
            items.mapIndexed { idx, it ->
                val price = it.unitPrice ?: it.price ?: 0.0
                val subtotal = it.totalPrice ?: it.subtotal ?: (price * (it.quantity ?: 1))
                "${idx + 1}. ${it.medicineName ?: "药品"}${if (!it.specification.isNullOrBlank()) " (${it.specification})" else ""}" +
                        " · ${it.displayUsage.ifBlank { "遵医嘱" }} × ${it.quantity ?: 1} 盒 · 单价¥${String.format("%.2f", price)} · 小计¥${String.format("%.2f", subtotal)}"
            }.joinToString("\n")
        } else {
            "（处方未含药品明细）"
        }

        val ask = buildString {
            append("请以执业药师身份为我分析这份门诊就诊记录，用通俗易懂的语言：\n")
            append("【临床诊断】${rx.diagnosis ?: "常规门诊"}\n")
            append("【开具药品】\n$medLines\n")
            append("【处方金额】¥${String.format("%.2f", rx.displayPrice)}\n\n")
            append("请依次给出：1) 每种药的作用与服用注意事项；2) 药品之间有没有相互作用或配伍禁忌；3) 服药期间的饮食与生活建议；4) 出现哪些情况需要及时复诊。")
        }
        (activity as? com.chunbo.medical.ui.MainActivity)?.switchToCopilotAndAsk(ask)
    }

    /** 展示专业门诊电子处方签与就诊档案详情（告别纯文本堆砌，严格遵守云诊所与春播商城隔离红线） */
    private fun showPrescriptionDetail(wrapper: PrescriptionWrapper) {
        val rx = wrapper.prescription ?: return
        val items = wrapper.items ?: emptyList()
        val ctx = requireContext()

        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
        val dBinding = com.chunbo.medical.databinding.DialogPrescriptionDetailBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        // 1. 顶部状态与处方单号
        val rxNo = rx.prescriptionNo ?: ("RX" + rx.id)
        dBinding.tvRxDetailNo.text = "处方号: $rxNo"
        val statusText = rx.status ?: "已诊"
        dBinding.tvRxDetailBadge.text = when {
            statusText.contains("完诊") || statusText.contains("已诊") || statusText.contains("已审核") -> "院内调剂 · 已审核"
            statusText.contains("待") -> "院内调剂 · 待审核"
            else -> "门诊处方 · $statusText"
        }

        // 2. 患者与就诊医师信息
        val user = UserManager.getUser()
        val defaultName = if (user.isLoggedIn) user.nickname.ifBlank { user.username } else "就诊人"
        val patientName = rx.patientName?.ifBlank { defaultName } ?: defaultName
        dBinding.tvRxPatientName.text = patientName
        val genderStr = rx.gender?.ifBlank { "男" } ?: "男"
        val ageStr = if ((rx.age ?: 0) > 0) "${rx.age}岁" else "--"
        dBinding.tvRxPatientGenderAge.text = "$genderStr · $ageStr"
        dBinding.tvRxVisitTime.text = rx.createTime?.replace("T", " ")?.take(16) ?: "--"
        val doctor = rx.doctorName?.ifBlank { "门诊主治医师" } ?: "门诊主治医师"
        val dept = rx.department?.ifBlank { "全科门诊" } ?: "全科门诊"
        dBinding.tvRxDoctorName.text = "$doctor ($dept)"

        // 3. 临床初步诊断与问诊主诉
        val diag = rx.diagnosis?.ifBlank { "门诊确诊" } ?: "门诊确诊"
        dBinding.tvRxDiagnosis.text = diag
        val symptoms = rx.symptoms?.trim().orEmpty()
        if (symptoms.isNotEmpty()) {
            dBinding.layoutRxSymptoms.visibility = View.VISIBLE
            dBinding.tvRxSymptoms.text = symptoms
        } else {
            dBinding.layoutRxSymptoms.visibility = View.GONE
        }

        // 4. RP 处方用药清单（院内药房专供，绝非春播商城配货）
        dBinding.layoutRxMedicines.removeAllViews()
        val totalAmount = rx.displayPrice
        dBinding.tvRxTotalAmount.text = String.format("处方总额: ¥%.2f", totalAmount)

        if (items.isNotEmpty()) {
            items.forEach { item ->
                val medBinding = com.chunbo.medical.databinding.ItemPrescriptionDetailMedBinding.inflate(layoutInflater, dBinding.layoutRxMedicines, false)
                medBinding.tvMedName.text = item.medicineName ?: "处方药品"
                val spec = item.specification?.ifBlank { "标准盒装" } ?: "标准盒装"
                medBinding.tvMedSpec.text = "规格: $spec"
                val qty = item.quantity ?: 1
                val unitPrice = item.unitPrice ?: item.price ?: 0.0
                val subtotal = unitPrice * qty
                medBinding.tvMedPriceQty.text = String.format("¥%.2f × %d 盒", unitPrice, qty)
                medBinding.tvMedSubtotal.text = String.format("小计: ¥%.2f", subtotal)
                medBinding.tvMedUsage.text = "用法: ${item.displayUsage}"
                dBinding.layoutRxMedicines.addView(medBinding.root)
            }
        } else {
            val emptyMedView = TextView(ctx).apply {
                text = "遵医嘱进行门诊常规诊疗与健康观察，未开具外带处方药品。"
                setTextColor(ContextCompat.getColor(ctx, com.chunbo.medical.R.color.text_secondary))
                textSize = 13f
                setPadding(0, 10, 0, 10)
            }
            dBinding.layoutRxMedicines.addView(emptyMedView)
        }

        // 5. 医生与 AI 联合调护医嘱
        val advice = rx.aiAdvice?.trim().orEmpty()
        if (advice.isNotEmpty()) {
            dBinding.cardRxAdvice.visibility = View.VISIBLE
            dBinding.tvRxAdvice.text = advice
        } else {
            dBinding.tvRxAdvice.text = "遵医嘱按时规律用药，清淡饮食，多饮温水，注意体温监测与充分休息。"
        }

        // 6. 门诊复诊关怀日程卡片联动
        fun refreshDialogRevisitCard() {
            val revisitInfo = com.chunbo.medical.util.RevisitReminderManager.getRevisitInfo(ctx)
            if (revisitInfo != null && revisitInfo.isPending) {
                dBinding.tvRxRevisitBadge.visibility = View.VISIBLE
                dBinding.tvRxRevisitBadge.text = "剩余 ${revisitInfo.daysRemaining} 天"
                dBinding.tvRxRevisitInfo.text = "已设置复诊提醒：${revisitInfo.dateString} 09:00\n接诊医生：${revisitInfo.doctorName} (${revisitInfo.department}) · 备注：${revisitInfo.note}"
                dBinding.btnRxScheduleRevisit.text = "🔔 修改复诊提醒"
            } else {
                dBinding.tvRxRevisitBadge.visibility = View.GONE
                dBinding.tvRxRevisitInfo.text = "尚未设置复诊日程，建议按医嘱预约复诊评估病情"
                dBinding.btnRxScheduleRevisit.text = "🔔 设置复诊提醒"
            }
        }
        refreshDialogRevisitCard()

        // 7. 按钮事件监听
        dBinding.btnCloseRxDetail.setOnClickListener {
            dialog.dismiss()
        }

        dBinding.btnRxScheduleRevisit.setOnClickListener {
            showRevisitScheduleDialog(
                patientName = patientName,
                doctorName = doctor,
                deptName = dept,
                diagnosis = diag,
                prescriptionNo = rxNo,
                defaultNote = if (symptoms.isNotEmpty()) "${symptoms}好转情况复查评估" else "遵医嘱常规门诊复诊评估",
                onSaved = {
                    refreshDialogRevisitCard()
                    updateActiveRevisitCard()
                }
            )
        }

        // 预约复诊挂号：一键跳转至便民挂号页（自动关联门诊医生），完成业务完整闭环
        dBinding.btnRxQuickRegister.setOnClickListener {
            dialog.dismiss()
            (activity as? com.chunbo.medical.ui.MainActivity)?.switchToPatient()
            Toast.makeText(ctx, "📅 已为您转至便民就医中心，可直接预约 $doctor 复诊号", Toast.LENGTH_LONG).show()
        }

        dialog.show()
    }

    /** 专业的门诊复诊关怀提醒设置弹窗：支持快捷周期与自定义日期，形成真正的日程闭环 */
    private fun showRevisitScheduleDialog(
        patientName: String,
        doctorName: String,
        deptName: String,
        diagnosis: String,
        prescriptionNo: String,
        defaultNote: String,
        onSaved: () -> Unit
    ) {
        val ctx = requireContext()
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(ctx)
        val dBinding = com.chunbo.medical.databinding.DialogRevisitReminderPickerBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        dBinding.tvPickerDoctorPatient.text = "接诊医生：$doctorName ($deptName) | 患者：$patientName | 诊断：$diagnosis"

        val cal = java.util.Calendar.getInstance()
        var selectedMillis = cal.apply {
            add(java.util.Calendar.DAY_OF_YEAR, 7)
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
        }.timeInMillis

        fun updateDateDisplay() {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.CHINA)
            val diffDays = ((selectedMillis - System.currentTimeMillis()) / (1000 * 60 * 60 * 24) + 1).toInt()
            dBinding.tvSelectedRevisitDate.text = "建议复诊时间：${fmt.format(java.util.Date(selectedMillis))} (约 ${diffDays.coerceAtLeast(1)} 天后)"
        }
        updateDateDisplay()

        dBinding.etRevisitNote.setText(defaultNote)

        // 快捷选项单选
        dBinding.chipDays3.setOnClickListener {
            val c = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 3)
                set(java.util.Calendar.HOUR_OF_DAY, 9)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
            }
            selectedMillis = c.timeInMillis
            updateDateDisplay()
        }
        dBinding.chipDays7.setOnClickListener {
            val c = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 7)
                set(java.util.Calendar.HOUR_OF_DAY, 9)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
            }
            selectedMillis = c.timeInMillis
            updateDateDisplay()
        }
        dBinding.chipDays14.setOnClickListener {
            val c = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 14)
                set(java.util.Calendar.HOUR_OF_DAY, 9)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
            }
            selectedMillis = c.timeInMillis
            updateDateDisplay()
        }
        dBinding.chipDays30.setOnClickListener {
            val c = java.util.Calendar.getInstance().apply {
                add(java.util.Calendar.DAY_OF_YEAR, 30)
                set(java.util.Calendar.HOUR_OF_DAY, 9)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
            }
            selectedMillis = c.timeInMillis
            updateDateDisplay()
        }
        dBinding.chipCustomDate.setOnClickListener {
            val now = java.util.Calendar.getInstance()
            android.app.DatePickerDialog(
                ctx,
                { _, year, month, dayOfMonth ->
                    val pickCal = java.util.Calendar.getInstance().apply {
                        set(year, month, dayOfMonth, 9, 0, 0)
                    }
                    selectedMillis = pickCal.timeInMillis
                    updateDateDisplay()
                },
                now.get(java.util.Calendar.YEAR),
                now.get(java.util.Calendar.MONTH),
                now.get(java.util.Calendar.DAY_OF_MONTH)
            ).show()
        }

        // 检查之前是否已经有提醒，允许一键清除
        val existingInfo = com.chunbo.medical.util.RevisitReminderManager.getRevisitInfo(ctx)
        if (existingInfo != null && existingInfo.isPending) {
            dBinding.btnClearRevisit.visibility = View.VISIBLE
            dBinding.btnClearRevisit.setOnClickListener {
                com.chunbo.medical.util.RevisitReminderManager.cancel(ctx)
                Toast.makeText(ctx, "已取消该门诊复诊日程提醒", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onSaved()
            }
        } else {
            dBinding.btnClearRevisit.visibility = View.GONE
        }

        dBinding.btnCancelPicker.setOnClickListener { dialog.dismiss() }
        dBinding.btnClosePicker.setOnClickListener { dialog.dismiss() }

        dBinding.btnConfirmRevisit.setOnClickListener {
            val note = dBinding.etRevisitNote.text.toString().trim().ifBlank { "遵医嘱按时复查" }
            com.chunbo.medical.util.RevisitReminderManager.schedule(
                context = ctx,
                triggerMillis = selectedMillis,
                patientName = patientName,
                doctorName = doctorName,
                department = deptName,
                diagnosis = diagnosis,
                prescriptionNo = prescriptionNo,
                note = note
            )
            val dateFmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA).format(java.util.Date(selectedMillis))
            Toast.makeText(ctx, "🔔 已成功设置门诊复诊提醒：$dateFmt 09:00", Toast.LENGTH_LONG).show()
            dialog.dismiss()
            onSaved()
        }

        dialog.show()
    }

    /** 刷新个人中心【我的就诊记录】列表顶部的待复诊日程卡片（业务闭环） */
    private fun updateActiveRevisitCard() {
        val ctx = context ?: return
        if (_binding == null) return
        val info = com.chunbo.medical.util.RevisitReminderManager.getRevisitInfo(ctx)
        if (info != null && info.isPending) {
            binding.cardActiveRevisit.visibility = View.VISIBLE
            binding.tvActiveRevisitCountdown.text = "剩余 ${info.daysRemaining} 天"
            binding.tvActiveRevisitDesc.text = "建议于 ${info.dateString} 前往 ${info.department}（${info.doctorName}）复查\n医嘱备注：${info.note}"

            binding.btnActiveRevisitRegister.setOnClickListener {
                (activity as? com.chunbo.medical.ui.MainActivity)?.switchToPatient()
                Toast.makeText(ctx, "📅 已为您定位至便民挂号，可直接预约 ${info.doctorName} 复诊号", Toast.LENGTH_LONG).show()
            }

            binding.btnActiveRevisitManage.setOnClickListener {
                showRevisitScheduleDialog(
                    patientName = info.patientName,
                    doctorName = info.doctorName,
                    deptName = info.department,
                    diagnosis = info.diagnosis,
                    prescriptionNo = info.prescriptionNo,
                    defaultNote = info.note,
                    onSaved = { updateActiveRevisitCard() }
                )
            }
        } else {
            binding.cardActiveRevisit.visibility = View.GONE
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap != null) {
            uploadAvatarBitmap(bitmap)
        }
    }

    private val pickImageLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadAvatarUri(uri)
        }
    }

    private fun uploadAvatarBitmap(bitmap: android.graphics.Bitmap) {
        val stream = java.io.ByteArrayOutputStream()
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, stream)
        val byteArray = stream.toByteArray()
        val reqBody = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull())
        val part = okhttp3.MultipartBody.Part.createFormData("file", "avatar_${System.currentTimeMillis()}.jpg", reqBody)
        doUploadAvatar(part)
    }

    private fun uploadAvatarUri(uri: android.net.Uri) {
        try {
            val inputStream = requireContext().contentResolver.openInputStream(uri)
            val bytes = inputStream?.readBytes()
            inputStream?.close()
            if (bytes != null && bytes.isNotEmpty()) {
                val reqBody = bytes.toRequestBody("image/*".toMediaTypeOrNull())
                val part = okhttp3.MultipartBody.Part.createFormData("file", "avatar_${System.currentTimeMillis()}.jpg", reqBody)
                doUploadAvatar(part)
            } else {
                Toast.makeText(requireContext(), "所选图片读取失败，请重新选择", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "读取照片失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun doUploadAvatar(part: okhttp3.MultipartBody.Part) {
        val user = UserManager.getUser()
        if (!user.isLoggedIn) {
            Toast.makeText(requireContext(), "请先登录后再上传个人头像", Toast.LENGTH_SHORT).show()
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                Toast.makeText(requireContext(), "正在上传头像至云端数据库...", Toast.LENGTH_SHORT).show()
                val res = withContext(Dispatchers.IO) {
                    ApiClient.service.uploadAvatarFile(part, user.username)
                }
                val url = res["url"] as? String ?: res["avatar"] as? String
                if (!url.isNullOrBlank()) {
                    UserManager.updateAvatar(url)
                    com.chunbo.medical.util.AvatarHelper.loadAvatar(binding.ivProfileAvatar, url)
                    renderUserState()
                    Toast.makeText(requireContext(), "🎉 头像已真实上传并持久化保存至数据库！", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "头像上传返回异常: ${res["message"]}", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "头像上传失败: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun loadOrders() {
        binding.swipeRefreshProfile.isRefreshing = true
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val user = UserManager.getUser()
                // 同步拉取商城商品，建立 商品id → 图片 映射供订单卡片显示首件商品图
                val ordersAndProducts = withContext(Dispatchers.IO) {
                    val orders = ApiClient.service.getMallOrders(
                        username = if (user.isLoggedIn) user.username else null,
                        phone = if (user.isLoggedIn) user.phone else null
                    )
                    val products = try { ApiClient.service.getMallProducts() } catch (e: Exception) { emptyList() }
                    orders to products
                }
                val orders = ordersAndProducts.first
                productImageMap.clear()
                ordersAndProducts.second.forEach { p ->
                    p.id?.let { id -> p.imageUrl?.let { img -> productImageMap[id] = img } }
                }
                orderAdapter.submitList(orders)
                binding.tvStatOrdersCount.text = "${orders.size}"
                binding.tvNoOrders.visibility = if (orders.isEmpty()) View.VISIBLE else View.GONE
            } catch (e: Exception) {
                binding.tvNoOrders.visibility = View.VISIBLE
            } finally {
                binding.swipeRefreshProfile.isRefreshing = false
            }
        }
    }

    data class OrderItemPreview(
        val id: Long?,
        val name: String,
        val spec: String,
        val quantity: Int,
        val price: Double
    )

    private fun parseOrderItemsList(
        prodName: String?,
        itemsJson: String?,
        unitPrice: Double?,
        quantity: Int?
    ): List<OrderItemPreview> {
        if (!itemsJson.isNullOrBlank()) {
            try {
                val listType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                val items: List<Map<String, Any>> = com.google.gson.Gson().fromJson(itemsJson, listType)
                if (!items.isNullOrEmpty()) {
                    return items.map { item ->
                        val id = (item["id"] as? Number)?.toLong()
                        val name = (item["productName"] ?: item["name"] ?: "药品").toString()
                        val spec = (item["specification"] ?: item["spec"] ?: "").toString()
                        val qty = (item["quantity"] as? Number)?.toInt() ?: 1
                        val price = (item["price"] as? Number)?.toDouble() ?: (unitPrice ?: 0.0)
                        OrderItemPreview(id, name, spec, qty, price)
                    }
                }
            } catch (e: Exception) {}
        }
        val name = prodName?.ifBlank { "春播精选健康药品" } ?: "春播精选健康药品"
        return listOf(OrderItemPreview(null, name, "国药准字 · 标准装", quantity ?: 1, unitPrice ?: 0.0))
    }

    private fun showOrderDetailDialog(order: MallOrder) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val dBinding = com.chunbo.medical.databinding.DialogOrderDetailBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        val orderNo = order.orderNo ?: ("ORD" + (order.id ?: 1))
        dBinding.tvDetailOrderNo.text = "订单编号: $orderNo"
        dBinding.tvDetailOrderTime.text = "下单时间: ${order.createTime?.replace("T", " ")?.take(19) ?: "今日"}"

        // 订单状态进度展示（待发货 / 已发货 / 已送达）
        val status = order.status ?: "待发货"
        val isDelivered = status.contains("已送达")
        val isShipped = status.contains("已发货")
        dBinding.tvDetailStatusTitle.text = when {
            isDelivered -> "✅ 已送达 · 居民已签收"
            isShipped -> "🚚 已发货 · 春播便民速递运输中"
            else -> "⏳ 已支付 · 待商户发货出库"
        }
        dBinding.tvDetailStatusHint.text = when {
            isDelivered -> "药品已妥投签收，感谢使用春播便民健康服务！"
            isShipped -> "您的药品正在配送途中，签收后请点击下方「确认已送达」。"
            else -> "执业药师已复核通过，仓库正在调配拣货，发货后我们会第一时间通知您。"
        }
        // 仅在「已发货（未签收）」状态下显示确认送达按钮
        dBinding.btnDetailConfirmDeliver.visibility = if (isShipped && !isDelivered) View.VISIBLE else View.GONE
        dBinding.btnDetailConfirmDeliver.setOnClickListener {
            confirmDelivered(orderNo, dBinding)
        }

        // 物流跟踪时间线
        renderOrderTrack(dBinding, isShipped, isDelivered, order.createTime)

        val user = UserManager.getUser()
        val buyer = if (!order.buyerName.isNullOrBlank()) order.buyerName else (user.nickname.ifBlank { "春播会员" })
        val phone = if (!order.buyerPhone.isNullOrBlank()) order.buyerPhone else user.phone
        dBinding.tvDetailRecipient.text = "$buyer $phone".trim()
        val addr = if (!order.shippingAddress.isNullOrBlank()) order.shippingAddress else (user.address.ifBlank { "春播万象便民服务站自提" })
        dBinding.tvDetailAddress.text = addr

        // 渲染药品明细清单：加载真实商品图；数量为已支付订单的历史快照，不可修改
        dBinding.layoutDetailItems.removeAllViews()
        val items = parseOrderItemsList(order.productName, order.itemsJson, order.unitPrice, order.quantity)
        val originalFinalAmount = order.finalAmount ?: 0.0

        // 金额汇总：药品总额 = Σ 数量×单价；实付保持订单原值，差额由体验金抵扣吸收（与历史口径一致）
        fun recalcTotals() {
            val totalCalc = items.sumOf { it.price * it.quantity }
            if (totalCalc > 0) {
                val discount = if (totalCalc > originalFinalAmount) totalCalc - originalFinalAmount else totalCalc
                dBinding.tvDetailTotalProd.text = String.format("¥%.2f", totalCalc)
                dBinding.tvDetailDiscount.text = String.format("-¥%.2f", discount)
                dBinding.tvDetailFinalAmount.text = String.format("¥%.2f", originalFinalAmount)
            } else {
                val fallbackTotal = order.totalAmount ?: order.finalAmount ?: 0.0
                dBinding.tvDetailTotalProd.text = String.format("¥%.2f", fallbackTotal)
                dBinding.tvDetailDiscount.text = String.format("-¥%.2f", fallbackTotal - originalFinalAmount)
                dBinding.tvDetailFinalAmount.text = String.format("¥%.2f", originalFinalAmount)
            }
        }

        items.forEach { item ->
            val itemView = layoutInflater.inflate(com.chunbo.medical.R.layout.item_cart_product, dBinding.layoutDetailItems, false)
            val ivImg = itemView.findViewById<com.google.android.material.imageview.ShapeableImageView>(com.chunbo.medical.R.id.iv_cart_item_img)
            val tvTitle = itemView.findViewById<android.widget.TextView>(com.chunbo.medical.R.id.tv_cart_item_name)
            val tvSpec = itemView.findViewById<android.widget.TextView>(com.chunbo.medical.R.id.tv_cart_item_spec)
            val tvPrice = itemView.findViewById<android.widget.TextView>(com.chunbo.medical.R.id.tv_cart_item_price)
            val tvQty = itemView.findViewById<android.widget.TextView>(com.chunbo.medical.R.id.tv_cart_item_qty)
            val cbCheck = itemView.findViewById<android.widget.CheckBox>(com.chunbo.medical.R.id.cb_cart_item_select)
            val btnMinus = itemView.findViewById<View>(com.chunbo.medical.R.id.btn_cart_item_minus)
            val btnPlus = itemView.findViewById<View>(com.chunbo.medical.R.id.btn_cart_item_plus)
            val btnDel = itemView.findViewById<View>(com.chunbo.medical.R.id.btn_cart_item_delete)

            // 首件商品真实图片（按 itemsJson 的商品 id 匹配商品图；无匹配时保留占位图）
            com.chunbo.medical.data.api.ImageLoader.loadImage(ivImg, productImageMap[item.id])
            tvTitle?.text = item.name
            tvSpec?.text = if (item.spec.isNotBlank()) item.spec else "国药正品 · 标准规格"
            tvPrice?.text = String.format("¥%.2f", item.price)
            tvQty?.text = "${item.quantity}"
            cbCheck?.visibility = View.GONE
            // 已支付的订单明细为历史快照，数量不可修改，隐藏加减与删除控件
            btnMinus?.visibility = View.GONE
            btnPlus?.visibility = View.GONE
            btnDel?.visibility = View.GONE

            dBinding.layoutDetailItems.addView(itemView)
        }
        recalcTotals()

        dBinding.btnDetailContactCopilot.setOnClickListener {
            dialog.dismiss()
            // 把整笔订单发给春播小药师：提问文字 + 商品卡片（含真实图片）
            val products = items.map { i ->
                com.chunbo.medical.data.model.MallProductRecommendation(
                    id = i.id ?: 0L,
                    productName = i.name,
                    specification = i.spec,
                    price = String.format("%.2f", i.price),
                    category = "我的订单",
                    csPitch = "本单购买 × ${i.quantity} 盒",
                    imageUrl = productImageMap[i.id] ?: ""
                )
            }
            val ask = buildString {
                append("请以执业药师身份分析我这笔购药订单（${orderNo}），药品清单见下方卡片：\n")
                append("【实付金额】¥${String.format("%.2f", originalFinalAmount)}\n\n")
                append("请依次给出：1) 每种药的作用与适应症；2) 这些药联合使用有无相互作用或配伍禁忌；3) 用法用量与服药注意事项；4) 服药期间的饮食与生活建议；5) 出现哪些情况需要停药并及时就医。")
            }
            (activity as? com.chunbo.medical.ui.MainActivity)?.switchToCopilotAndAskOrder(ask, products)
        }

        dBinding.btnDetailReorder.setOnClickListener {
            dialog.dismiss()
            addItemsToCart(items)
        }

        dBinding.btnCloseDetail.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    /** 渲染订单物流跟踪时间线（已下单 → 支付成功 → 已发货 → 已签收） */
    private fun renderOrderTrack(
        dBinding: com.chunbo.medical.databinding.DialogOrderDetailBinding,
        isShipped: Boolean,
        isDelivered: Boolean,
        createTime: String?
    ) {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val activeColor = ContextCompat.getColor(ctx, com.chunbo.medical.R.color.primary)
        val mutedColor = ContextCompat.getColor(ctx, com.chunbo.medical.R.color.text_muted)
        val lineColor = ContextCompat.getColor(ctx, com.chunbo.medical.R.color.divider)

        val timeText = createTime?.replace("T", " ")?.take(16) ?: ""
        val steps = listOf(
            arrayOf("已下单", "订单已提交" + if (timeText.isNotEmpty()) " · $timeText" else "", "done"),
            arrayOf("支付成功", "支付完成，等待药师复核出库", "done"),
            arrayOf("已发货", if (isShipped || isDelivered) "药品已出库，顺丰医药配送中" else "待商户发货出库", if (isShipped || isDelivered) "done" else "todo"),
            arrayOf("已签收", if (isDelivered) "药品已妥投签收，感谢您的信任" else "配送完成后即可签收", if (isDelivered) "done" else "todo")
        )

        dBinding.layoutDetailTrack.removeAllViews()
        steps.forEachIndexed { index, node ->
            val active = node[2] == "done"
            val color = if (active) activeColor else mutedColor

            val row = LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { if (index > 0) topMargin = dp(10) }
            }

            // 左侧圆点 + 竖线
            val dotColumn = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(dp(14), LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            dotColumn.addView(View(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(dp(10), dp(10))
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(color)
                }
            })
            if (index < steps.size - 1) {
                dotColumn.addView(View(ctx).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(2), dp(26)).apply { topMargin = dp(2) }
                    background = GradientDrawable().apply { setColor(lineColor) }
                })
            }
            row.addView(dotColumn)

            // 右侧标题 + 描述
            val textColumn = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(10) }
            }
            textColumn.addView(TextView(ctx).apply {
                text = node[0]
                setTextColor(color)
                textSize = 14f
                typeface = if (active) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            })
            textColumn.addView(TextView(ctx).apply {
                text = node[1]
                setTextColor(mutedColor)
                textSize = 11f
            })
            row.addView(textColumn)

            dBinding.layoutDetailTrack.addView(row)
        }
    }

    /** 患者确认送达（居民签收），状态 已发货 → 已送达，PC 端同步可见 */
    private fun confirmDelivered(orderNo: String, dBinding: com.chunbo.medical.databinding.DialogOrderDetailBinding) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    ApiClient.service.confirmOrderDelivered(mapOf("orderNo" to orderNo))
                }
                if (res["success"] == true) {
                    Toast.makeText(requireContext(), "🎉 已确认送达，感谢您的签收！", Toast.LENGTH_LONG).show()
                    dBinding.tvDetailStatusTitle.text = "✅ 已送达 · 居民已签收"
                    dBinding.tvDetailStatusHint.text = "药品已妥投签收，感谢使用春播便民健康服务！"
                    dBinding.btnDetailConfirmDeliver.visibility = View.GONE
                    loadOrders()
                } else {
                    Toast.makeText(requireContext(), res["message"]?.toString() ?: "确认送达失败", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "确认送达失败: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleReorder(order: MallOrder) {
        val items = parseOrderItemsList(order.productName, order.itemsJson, order.unitPrice, order.quantity)
        addItemsToCart(items)
    }

    /** 把（订单详情中可调整数量后的）药品清单加入购物车并跳转商城 */
    private fun addItemsToCart(items: List<OrderItemPreview>) {
        var added = 0
        items.forEach { item ->
            // 仅当从 itemsJson 解析出真实商品 id 时才加入购物车，避免拿 order.productId（恒 null）兜底成 1L 扣错库存
            val pid = item.id ?: return@forEach
            val dummyProd = com.chunbo.medical.data.model.MallProduct(
                id = pid,
                productName = item.name,
                specification = item.spec,
                price = item.price,
                retailGuidePrice = item.price,
                category = "春播正品",
                stock = 500,
                // 带上真实商品图，商城结算弹窗/购物车才能显示图片
                imageUrl = productImageMap[pid]
            )
            com.chunbo.medical.ui.mall.CartManager.addToCart(dummyProd, item.quantity)
            added++
        }
        if (added == 0) {
            Toast.makeText(requireContext(), "该订单未关联到在售商品，请到商城按药名选购", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(requireContext(), "🛒 已按清单 $added 种药品加入购物车，为您跳转至商城", Toast.LENGTH_LONG).show()
        }
        (activity as? com.chunbo.medical.ui.MainActivity)?.switchToMallTab()
    }

    private fun showAvatarPickerDialog() {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val dBinding = com.chunbo.medical.databinding.DialogAvatarPickerBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        // 拍照上传
        dBinding.btnAvatarCamera.setOnClickListener {
            dialog.dismiss()
            takePhotoLauncher.launch(null)
        }

        // 相册选取
        dBinding.btnAvatarGallery.setOnClickListener {
            dialog.dismiss()
            pickImageLauncher.launch("image/*")
        }

        val avatarMap = mapOf(
            dBinding.itemAvatar1 to Pair("avatar_resident_1", "健康居民"),
            dBinding.itemAvatar2 to Pair("avatar_resident_2", "知性青年"),
            dBinding.itemAvatar3 to Pair("avatar_resident_3", "康养长者"),
            dBinding.itemAvatar4 to Pair("avatar_resident_4", "调理居民"),
            dBinding.itemAvatar5 to Pair("avatar_resident_5", "活力少年"),
            dBinding.itemAvatar6 to Pair("avatar_resident_6", "全科医生")
        )

        avatarMap.forEach { (view, pair) ->
            view.setOnClickListener {
                val (key, name) = pair
                UserManager.updateAvatar(key)
                com.chunbo.medical.util.AvatarHelper.loadAvatar(binding.ivProfileAvatar, key)
                viewLifecycleOwner.lifecycleScope.launch {
                    try {
                        withContext(Dispatchers.IO) {
                            ApiClient.service.updateAvatar(mapOf("avatar" to key))
                        }
                    } catch (ignored: Exception) {}
                }
                Toast.makeText(requireContext(), "已选用「$name」头像，已同步至云端", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dBinding.btnCancelPicker.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showEditProfileDialog() {
        val user = UserManager.getUser()
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val dBinding = com.chunbo.medical.databinding.DialogEditProfileBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        dBinding.etEditNickname.setText(user.nickname)
        dBinding.etEditPhone.setText(user.phone)

        // 拆解地址为省市区与详细门牌
        val (initialRegion, initialDetail) = com.chunbo.medical.ui.common.RegionPickerHelper.splitAddress(user.address)
        var selectedRegion = initialRegion
        dBinding.tvProfileSelectedRegion.text = selectedRegion
        dBinding.etEditAddressDetail.setText(initialDetail)

        dBinding.cardProfileSelectRegion.setOnClickListener {
            com.chunbo.medical.ui.common.RegionPickerHelper.showRegionPicker(requireContext(), selectedRegion) { newReg ->
                selectedRegion = newReg
                dBinding.tvProfileSelectedRegion.text = newReg
            }
        }

        dBinding.btnCancelEdit.setOnClickListener { dialog.dismiss() }

        dBinding.btnSaveEdit.setOnClickListener {
            val nick = dBinding.etEditNickname.text.toString().trim()
            val phone = dBinding.etEditPhone.text.toString().trim()
            val detail = dBinding.etEditAddressDetail.text.toString().trim()
            val fullAddr = if (detail.isNotEmpty()) "$selectedRegion $detail" else selectedRegion

            if (phone.isNotEmpty() && !phone.matches(Regex("1\\d{10}"))) {
                Toast.makeText(requireContext(), "请输入合法的 11 位手机号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val req = mutableMapOf<String, String>()
                    if (nick.isNotEmpty()) req["nickname"] = nick
                    if (phone.isNotEmpty()) req["phone"] = phone
                    req["address"] = fullAddr
                    req["avatar"] = user.avatar

                    withContext(Dispatchers.IO) {
                        ApiClient.service.updateProfile(req)
                    }

                    UserManager.updateProfile(nick, phone, fullAddr, user.avatar)
                    renderUserState()
                    Toast.makeText(requireContext(), "个人健康资料及收货地址已同步保存", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "保存失败: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        dialog.show()
    }

    private fun showEditAddressDialog() {
        val user = UserManager.getUser()
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(requireContext())
        val dBinding = com.chunbo.medical.databinding.DialogEditAddressBinding.inflate(layoutInflater)
        dialog.setContentView(dBinding.root)

        // 拆解地址为省市区与详细门牌
        val (initialRegion, initialDetail) = com.chunbo.medical.ui.common.RegionPickerHelper.splitAddress(user.address)
        var selectedRegion = initialRegion
        dBinding.tvSelectedRegion.text = selectedRegion
        dBinding.etEditAddressDetail.setText(initialDetail)

        dBinding.cardSelectRegion.setOnClickListener {
            com.chunbo.medical.ui.common.RegionPickerHelper.showRegionPicker(requireContext(), selectedRegion) { newReg ->
                selectedRegion = newReg
                dBinding.tvSelectedRegion.text = newReg
            }
        }

        dBinding.btnCancelAddr.setOnClickListener { dialog.dismiss() }

        dBinding.btnSaveAddr.setOnClickListener {
            val detail = dBinding.etEditAddressDetail.text.toString().trim()
            val newFullAddr = if (detail.isNotEmpty()) "$selectedRegion $detail" else selectedRegion

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        ApiClient.service.updateAddress(mapOf("address" to newFullAddr))
                    }
                    UserManager.updateAddress(newFullAddr)
                    binding.tvProfileAddress.text = newFullAddr
                    Toast.makeText(requireContext(), "收货地址已保存至云端数据库", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                } catch (e: Exception) {
                    UserManager.updateAddress(newFullAddr)
                    binding.tvProfileAddress.text = newFullAddr
                    Toast.makeText(requireContext(), "已本地保存收货地址", Toast.LENGTH_SHORT).show()
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun showLoginDialog() {
        MallAuthHelper.showLoginDialog(requireContext(), viewLifecycleOwner.lifecycleScope) {
            renderUserState()
            loadOrders()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        UserManager.removeListener(userChangeListener)
        _binding = null
    }
}
