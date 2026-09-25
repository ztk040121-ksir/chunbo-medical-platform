<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import axios from 'axios'
import { ElMessage, ElNotification, ElMessageBox } from 'element-plus'
import { makeListPager, batchDeleteRows } from '../utils/common.js'

const props = defineProps({
  currentUserRole: String,
  currentUserName: String,
  currentUserStaffId: String
})

// ── 工资发放台账导出 Excel（仅 ADMIN/HR 可见）──
const salaryExportLoading = ref(false)
const exportSalaryExcel = async () => {
  salaryExportLoading.value = true
  try {
    const res = await axios.get('/api/export/salary', { responseType: 'blob' })
    const link = document.createElement('a')
    link.href = URL.createObjectURL(res.data)
    link.download = '工资发放台账.xlsx'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    URL.revokeObjectURL(link.href)
    ElMessage.success('工资发放台账导出成功')
  } catch (e) {
    ElMessage.error('导出失败：' + (e.response?.data?.message || e.message || '网络错误'))
  } finally {
    salaryExportLoading.value = false
  }
}

const salarySlips = ref([])

// 针对医生的过滤展示
const displaySalarySlips = computed(() => {
  if (props.currentUserRole === 'DOCTOR' || props.currentUserRole === 'MERCHANT') {
    // 医生与商户只能看自己的工资条明细
    return salarySlips.value.filter(s => s.doctorName === props.currentUserName || s.doctorId === props.currentUserStaffId || (props.currentUserRole === 'MERCHANT' && (s.doctorId === 'MERCH_001' || s.doctorName.includes('商户'))))
  }
  return salarySlips.value
})

const loadSalaryData = async () => {
  try {
    const res = await axios.get('/api/assistant/salary')
    salarySlips.value = res.data || []
  } catch (e) {}
}

// ── 全员薪酬核算与发放中枢（tab 主区域，表格化批量管理）──
// 当前日期（本地时区，避免 toISOString 的 UTC 偏移导致错位）
const currentLocalDate = () => {
  const n = new Date()
  return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`
}
const salaryBoardMonth = ref(currentLocalDate())
const batchSalaryRows = ref([])
const batchSalaryExcelInput = ref(null)
const batchSalaryTableRef = ref(null)
const batchSelection = ref([])
const batchAiAllLoading = ref(false)
const distributeLoading = ref(false)
// 实发 = 基本底薪 + 门诊/电商提成 + 贴敷理疗合规奖 − 社保代扣 − 个税
const batchRowNet = (row) =>
  (Number(row.baseSalary) || 0) + (Number(row.clinicCommission) || 0) + (Number(row.plasterCommission) || 0)
  - (Number(row.deductionSocial) || 0) - (Number(row.tax) || 0)
const batchTotalAmount = computed(() => batchSalaryRows.value.reduce((s, r) => s + batchRowNet(r), 0))
const onBatchSelectionChange = (sel) => { batchSelection.value = sel }

const getCandidateRoleLabel = (role) => {
  if (role === 'MERCHANT') return '商城商户'
  if (role === 'HR') return '人事主管'
  if (role === 'DOCTOR') return '门诊医生'
  return '员工'
}

const loadBatchSalaryBoard = async () => {
  batchSalaryRows.value = []
  batchSelection.value = []
  // 发薪精确到天：发薪日期是完整日期（YYYY-MM-DD），同员工同「发放日」重发才覆盖
  const payDate = salaryBoardMonth.value
  try {
    const [candRes, slipRes] = await Promise.all([
      axios.get('/api/assistant/salary/staff-candidates'),
      axios.get('/api/assistant/salary')
    ])
    const cands = candRes.data || []
    const slips = (slipRes.data || []).filter(s => s.salaryMonth === payDate)
    for (const c of cands) {
      const slip = slips.find(s => String(s.doctorId || '').toLowerCase() === String(c.staffId || '').toLowerCase())
      batchSalaryRows.value.push({
        staffId: c.staffId,
        name: c.realName,
        role: c.role,
        roleLabel: getCandidateRoleLabel(c.role),
        baseSalary: slip ? Number(slip.baseSalary) : 0,
        clinicCommission: slip ? Number(slip.clinicCommission) : 0,
        plasterCommission: slip ? Number(slip.plasterCommission) : 0,
        deductionSocial: slip ? Number(slip.deductionSocial) : 0,
        tax: slip ? Number(slip.tax) : 0,
        hasAccount: true,
        slipId: slip ? slip.id : null,
        source: slip ? '该日已发放（重发将覆盖）' : '待发放',
        sourceType: slip ? 'success' : 'info',
        isNew: false,
        _aiLoading: false,
        _paying: false
      })
    }
  } catch (e) {
    ElMessage.error('加载全院员工列表失败')
  }
}

// 删除工资发放记录时需反查员工账号（医生表 / 员工表），子组件内自建本地数据源，避免跨组件依赖
const doctorAccounts = ref([])
const allStaffList = ref([])
const loadLocalDoctorAccounts = async () => {
  try {
    const res = await axios.get('/api/doctor/list')
    if (res.data?.success) doctorAccounts.value = res.data.data || []
  } catch (e) {}
}
const loadLocalStaffList = async () => {
  try {
    const res = await axios.get('/api/admin/staff/list')
    if (res.data?.success) allStaffList.value = res.data.data || []
  } catch (e) {}
}

// 删除工资发放记录 + 同步删除医院员工账号（工资名单与系统员工账号一一对应）
const deleteSalaryBoardRows = async (rows) => {
  if (!rows || !rows.length) {
    ElMessage.warning('请先勾选要删除的行')
    return
  }
  const accountRows = rows.filter(r => r.hasAccount !== false && r.staffId)
  const draftRows = rows.filter(r => r.hasAccount === false || !r.staffId)

  // 纯草稿行（无账号/无落库数据）：仅从本表移除
  if (!accountRows.length) {
    try {
      await ElMessageBox.confirm(
        `确定移除选中的 ${draftRows.length} 条行？（无系统账号、无落库工资数据，仅从本表移除）`,
        '移除确认',
        { type: 'warning', confirmButtonText: '确认移除', cancelButtonText: '取消' }
      )
    } catch (e) { return }
    batchSalaryRows.value = batchSalaryRows.value.filter(r => !draftRows.includes(r))
    ElMessage.success(`已移除 ${draftRows.length} 条行`)
    return
  }

  // 先拉最新账号/工资数据，确保按最新状态匹配
  try { await Promise.all([loadLocalDoctorAccounts(), loadLocalStaffList()]) } catch (e) {}
  const doctorDel = []   // { row, accountId }
  const staffDel = []
  const missAccounts = []
  for (const r of accountRows) {
    const sid = String(r.staffId)
    const doc = doctorAccounts.value.find(d => String(d.username) === sid || String(d.doctorId) === sid)
    const stf = doc ? null : allStaffList.value.find(s => String(s.staffId) === sid)
    if (doc) doctorDel.push({ row: r, accountId: doc.id })
    else if (stf) staffDel.push({ row: r, accountId: stf.id })
    else missAccounts.push(r.name || sid)
  }
  if (missAccounts.length) {
    ElMessage.warning(`以下人员在系统账号表中未找到对应账号，将只删工资记录不删账号：${missAccounts.join('、')}`)
  }
  const accNames = accountRows.map(r => `${r.name || r.staffId}(${r.staffId})`).slice(0, 6).join('、') + (accountRows.length > 6 ? ` 等 ${accountRows.length} 人` : '')
  try {
    await ElMessageBox.confirm(
      `确定删除：${accNames}？将执行——① 删除该(这些)员工的全部工资发放记录；② 同步删除医院系统员工账号。⚠️ 账号删除后该员工将无法登录系统，并从工资名单中永久移除，删除不可恢复！`,
      '删除工资记录与员工账号',
      { type: 'warning', confirmButtonText: '确认删除（含账号）', cancelButtonText: '取消' }
    )
  } catch (e) { return }

  try {
    // 1) 删该员工全部工资发放记录（含历史月份，账号删了不留孤儿数据）
    const slipRes = await axios.get('/api/assistant/salary')
    const allSlips = slipRes.data || []
    const slipIds = []
    for (const r of accountRows) {
      const sid = String(r.staffId).toLowerCase()
      for (const s of allSlips) {
        if (String(s.doctorId || '').toLowerCase() === sid) slipIds.push(s.id)
      }
    }
    if (slipIds.length) {
      const del = await axios.post('/api/assistant/salary/batch-delete', { ids: slipIds })
      if (!del.data?.success) {
        ElMessage.error(del.data?.message || '删除工资记录失败')
        return
      }
    }
    // 2) 删员工账号（复用医生/员工管理页已有删除接口，路径级 ADMIN/HR 权限保护）
    let delAcc = 0
    const failAcc = []
    for (const d of doctorDel) {
      try { await axios.delete('/api/doctor/' + d.accountId); delAcc++ } catch (e) { failAcc.push(d.row.name || d.row.staffId) }
    }
    for (const s of staffDel) {
      try { await axios.delete('/api/admin/staff/' + s.accountId); delAcc++ } catch (e) { failAcc.push(s.row.name || s.row.staffId) }
    }
    // 3) 草稿行仅从本表移除
    if (draftRows.length) batchSalaryRows.value = batchSalaryRows.value.filter(r => !draftRows.includes(r))
    ElMessage.success(`已删除 ${slipIds.length} 条工资发放记录、${delAcc} 个员工账号${failAcc.length ? '；账号删除失败：' + failAcc.join('、') : ''}`)
    await loadBatchSalaryBoard()
    loadSalaryData()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.response?.data?.message || e.message))
  }
}

const triggerBatchExcelUpload = () => {
  batchSalaryExcelInput.value && batchSalaryExcelInput.value.click()
}
const handleBatchExcelFill = async (e) => {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    ElMessage.warning('请选择 Excel 工资表（xlsx / xls）')
    e.target.value = ''
    return
  }
  const fd = new FormData()
  fd.append('file', file)
  try {
    const res = await axios.post('/api/assistant/salary/parse-excel', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    const d = res.data
    if (!d || !d.success) {
      ElMessage.error((d && d.message) || '工资表解析失败')
      e.target.value = ''
      return
    }
    let matched = 0, noAccount = 0
    for (const r of (d.rows || [])) {
      const amt = Number(String(r.amount).trim())
      if (isNaN(amt) || amt <= 0) continue
      const key = String(r.employeeId || '').trim().toLowerCase()
      const nm = String(r.employeeName || '').trim()
      const hit = batchSalaryRows.value.find(x =>
        (key && String(x.staffId || '').toLowerCase() === key) ||
        (nm && x.name && x.name === nm))
      if (hit) {
        hit.baseSalary = amt
        hit.clinicCommission = 0
        hit.plasterCommission = 0
        hit.deductionSocial = 0
        hit.tax = 0
        hit.source = '工资表填充'
        hit.sourceType = 'warning'
        matched++
      } else {
        // 工资表里出现但系统无账号的人员：红标警示、不可发放（工资必须与系统账号一一对应）
        batchSalaryRows.value.push({
          staffId: r.employeeId || '', name: nm || '', role: '', roleLabel: '表内人员',
          baseSalary: amt, clinicCommission: 0, plasterCommission: 0, deductionSocial: 0, tax: 0,
          hasAccount: false,
          source: '⚠️ 系统中无此账号，不可发放', sourceType: 'danger', isNew: false, _aiLoading: false, _paying: false
        })
        noAccount++
      }
    }
    if (noAccount > 0) {
      ElMessage.warning(`工资表填充完成：匹配到系统员工 ${matched} 人；另有 ${noAccount} 人在系统中无账号，已红标且不可发放（发放名单必须与系统员工账号一一对应）`)
    } else {
      ElMessage.success(`工资表填充完成：匹配到系统员工 ${matched} 人`)
    }
    nextTick(() => {
      if (batchSalaryTableRef.value) batchSalaryTableRef.value.setScrollTop(99999)
    })
  } catch (err) {
    ElMessage.error('解析失败：' + (err.response?.data?.message || err.message || '网络错误'))
  }
  e.target.value = ''
}
const addBatchRow = () => {
  batchSalaryRows.value.push({
    staffId: '', name: '', role: '', roleLabel: '手动添加',
    baseSalary: 0, clinicCommission: 0, plasterCommission: 0, deductionSocial: 0, tax: 0,
    source: '手动添加', sourceType: 'info', isNew: true, _aiLoading: false, _paying: false
  })
  // 自动滚动到列表底部，让新加的行立即可见
  nextTick(() => {
    if (batchSalaryTableRef.value) batchSalaryTableRef.value.setScrollTop(99999)
  })
}
const aiCalcBatchRow = async (row, silent = false) => {
  if (!row.staffId) {
    ElMessage.warning('请先填写员工工号')
    return false
  }
  row._aiLoading = true
  try {
    const res = await axios.post('/api/assistant/salary/ai-calculate', {
      staffId: row.staffId, name: row.name, role: row.role, month: String(salaryBoardMonth.value || '').slice(0, 7)
    })
    const d = res.data
    if (d && (d.success !== false) && d.netSalary != null) {
      row.baseSalary = Number(d.baseSalary) || 0
      row.clinicCommission = Number(d.clinicCommission) || 0
      row.plasterCommission = Number(d.plasterCommission) || 0
      row.deductionSocial = Number(d.deductionSocial) || 0
      row.tax = Number(d.tax) || 0
      row.source = 'AI 智能测算'
      row.sourceType = 'primary'
      // 单行测算才弹提示；全员批量测算静默，结束后统一汇总，避免通知刷屏
      if (!silent) {
        ElNotification({
          title: '🤖 AI 测算完成',
          message: `${row.name || row.staffId} 实发 ¥${batchRowNet(row).toFixed(2)}${d.aiComment ? '｜' + d.aiComment : ''}${d.metricsBasis && d.metricsBasis.formula ? '｜公式：' + d.metricsBasis.formula : ''}`,
          type: 'success', duration: 6000
        })
      }
      return true
    } else {
      if (!silent) {
        ElMessage.warning((d && d.message) || `AI 测算失败：${row.name || row.staffId} 缺少历史工资与业务量数据，请手动填写`)
      }
      return false
    }
  } catch (e) {
    if (!silent) {
      ElMessage.error('AI 测算失败：' + (e.response?.data?.message || e.message))
    }
    return false
  } finally {
    row._aiLoading = false
  }
}
// 全员 AI 测算：逐行调用（串行避免打爆接口），结束只弹一个汇总提示
const aiCalcAllRows = async () => {
  const rows = batchSalaryRows.value.filter(r => !r.isNew && r.staffId)
  if (!rows.length) {
    ElMessage.warning('没有可测算的员工')
    return
  }
  batchAiAllLoading.value = true
  let ok = 0
  const failed = []
  try {
    for (const r of rows) {
      const success = await aiCalcBatchRow(r, true)
      if (success) ok++
      else failed.push(r.name || r.staffId)
    }
    if (failed.length) {
      ElMessage.success(`全员 AI 测算完成：成功 ${ok}/${rows.length} 人；${failed.length} 人无历史工资与业务量数据（${failed.join('、')}），请手动填写`)
    } else {
      ElMessage.success(`全员 AI 测算完成：${rows.length} 人全部测算成功，实发合计 ¥${batchTotalAmount.value.toFixed(2)}`)
    }
  } finally {
    batchAiAllLoading.value = false
  }
}
const buildBatchPayRows = (list) => list
  .filter(r => r.hasAccount !== false && batchRowNet(r) > 0 && String(r.staffId || '').trim() && String(r.name || '').trim())
  .map(r => ({
    staffId: String(r.staffId).trim(), name: String(r.name).trim(),
    baseSalary: Number(r.baseSalary) || 0, clinicCommission: Number(r.clinicCommission) || 0,
    plasterCommission: Number(r.plasterCommission) || 0, deductionSocial: Number(r.deductionSocial) || 0,
    tax: Number(r.tax) || 0
  }))
const doBatchPay = async (rows) => {
  distributeLoading.value = true
  try {
    const res = await axios.post('/api/assistant/salary/batch-pay', { month: salaryBoardMonth.value, rows })
    const d = res.data
    if (d && d.success) {
      const lines = (d.details || [])
        .map(x => `<div style="margin:2px 0;">${x.success ? '✅' : '❌'} <b>${x.name || x.staffId}</b>：${x.message || ''}</div>`)
        .join('')
      ElMessageBox.alert(lines || d.message, `发放完成（成功 ${d.successCount} 人 / 失败 ${d.failCount} 人，合计 ¥${Number(d.totalAmount || 0).toFixed(2)}）`, {
        dangerouslyUseHTMLString: true, confirmButtonText: '知道了'
      }).catch(() => {})
      await loadSalaryData()
      await loadBatchSalaryBoard()
    } else {
      ElMessage.error((d && d.message) || '批量发放失败')
    }
  } catch (e) {
    ElMessage.error('批量发放失败：' + (e.response?.data?.message || e.message))
  } finally {
    distributeLoading.value = false
  }
}
const submitBatchSalary = async (selectedList) => {
  const rows = buildBatchPayRows(selectedList || [])
  if (!rows.length) {
    ElMessage.warning('没有可发放的记录（需实发>0 且工号姓名齐全）')
    return
  }
  doBatchPay(rows)
}
const paySingleRow = async (row) => {
  const one = buildBatchPayRows([row])
  if (!one.length) {
    ElMessage.warning('该行实发金额为 0 或工号/姓名未填全')
    return
  }
  row._paying = true
  try {
    await doBatchPay(one)
  } finally {
    row._paying = false
  }
}

// 发放记录弹窗：关键字搜索 + 月份筛选（salaryMonth 兼容历史"YYYY-MM"与新的精确到天"YYYY-MM-DD"两种存法，按前缀匹配）
const showSlipHistoryDialog = ref(false)
const slipSearchKey = ref('')
const slipFilterMonth = ref('')
const slipSelection = ref([])
const slipMonthOptions = computed(() => {
  const set = new Set((salarySlips.value || []).map(s => String(s.salaryMonth || '').slice(0, 7)).filter(Boolean))
  return Array.from(set).sort().reverse()
})
const filteredSalarySlips = computed(() => {
  let list = displaySalarySlips.value
  if (slipFilterMonth.value) list = list.filter(s => String(s.salaryMonth || '').startsWith(slipFilterMonth.value))
  const key = slipSearchKey.value.trim().toLowerCase()
  if (key) {
    list = list.filter(s =>
      String(s.doctorId || '').toLowerCase().includes(key) ||
      String(s.doctorName || '').toLowerCase().includes(key))
  }
  return list
})

const slipsPager = makeListPager(filteredSalarySlips, ['doctorId', 'doctorName', 'salaryMonth'])
const salaryBoardPager = makeListPager(batchSalaryRows, ['staffId', 'name', 'roleLabel', 'role', 'source'])

onMounted(() => {
  loadSalaryData()
  if (props.currentUserRole !== 'DOCTOR' && props.currentUserRole !== 'MERCHANT') {
    loadBatchSalaryBoard()
  }
})
</script>

<template>
  <!-- 2. 工资条管理 -->
  <div class="tab-pane">
    <!-- 医生/商户视角：只读个人台账 -->
    <template v-if="currentUserRole === 'DOCTOR' || currentUserRole === 'MERCHANT'">
      <div class="pane-header">
        <div>
          <h3>💼 我的月度工资条与绩效明细</h3>
          <span class="sub-desc">当前仅展示 [{{ currentUserName }}] 的核算记录</span>
        </div>
      </div>
      <div class="salary-full-card">
        <div class="sub-card-header flex-between">
          <div><span>📋</span><b>个人历史工资核算台账</b></div>
        </div>
        <el-table :data="displaySalarySlips" stripe size="small" style="width: 100%;">
          <el-table-column prop="doctorId" label="员工工号" width="120" />
          <el-table-column prop="doctorName" label="员工姓名" width="110" />
          <el-table-column prop="salaryMonth" label="归属月份" width="100" />
          <el-table-column prop="baseSalary" label="基本底薪" width="110">
            <template #default="scope"><span>¥{{ scope.row.baseSalary }}</span></template>
          </el-table-column>
          <el-table-column prop="clinicCommission" label="门诊/电商提成" width="120">
            <template #default="scope"><span class="text-success">+¥{{ scope.row.clinicCommission }}</span></template>
          </el-table-column>
          <el-table-column prop="plasterCommission" label="贴敷理疗/合规奖" width="130">
            <template #default="scope"><span class="text-success font-bold">+¥{{ scope.row.plasterCommission }}</span></template>
          </el-table-column>
          <el-table-column prop="netSalary" label="实发工资" min-width="130">
            <template #default="scope"><b class="text-danger" style="font-size: 14px;">¥{{ scope.row.netSalary }}</b></template>
          </el-table-column>
          <el-table-column prop="status" label="发放状态" width="100" align="center">
            <template #default="scope">
              <el-tag type="success" size="small" effect="dark">{{ scope.row.status || '已发放' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="发放时间" width="160">
            <template #default="scope">
              <span style="font-size: 12px; color: #64748b;">{{ (scope.row.createTime || '').replace('T', ' ').slice(0, 19) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </template>

    <!-- ADMIN/HR 视角：主区域=全员薪酬核算与发放，下方=发放记录台账 -->
    <template v-else>
      <div class="pane-header">
        <div>
          <h3>💼 全员薪酬核算与工资发放中枢</h3>
          <span class="sub-desc">自动加载全院工作人员 · 上传工资表按工号/姓名自动填充 · 支持勾选发放 / 单独发放 / 批量发放 · 同员工同月重发自动覆盖</span>
        </div>
        <div>
          <el-button v-if="currentUserRole === 'ADMIN' || currentUserRole === 'HR'" type="primary" size="small" plain :loading="salaryExportLoading" @click="exportSalaryExcel">⬇ 导出台账</el-button>
          <el-button size="small" plain @click="showSlipHistoryDialog = true">📋 发放记录</el-button>
        </div>
      </div>

      <div class="salary-full-card">
        <div class="batch-salary-toolbar">
          <span style="font-size: 13px;">发薪日期：</span>
          <el-date-picker v-model="salaryBoardMonth" type="date" value-format="YYYY-MM-DD" :clearable="false" style="width: 140px" size="small" @change="loadBatchSalaryBoard" />
          <el-button type="warning" plain size="small" @click="triggerBatchExcelUpload">⬆ 上传工资表填充</el-button>
          <input ref="batchSalaryExcelInput" type="file" accept=".xlsx,.xls" style="display:none" @change="handleBatchExcelFill" />
          <el-button size="small" plain @click="aiCalcAllRows" :loading="batchAiAllLoading">🤖 全员AI测算</el-button>
          <el-input v-model="salaryBoardPager.state.search" placeholder="搜索工号 / 姓名 / 角色…" size="small" clearable style="width: 190px; margin-left: auto;" />
          <span style="font-size: 12px; color: #64748b;">
            已勾选 <b>{{ batchSelection.length }}</b> 人 · 本表应发合计 <b class="text-danger">¥{{ batchTotalAmount.toFixed(2) }}</b>
          </span>
          <el-button type="danger" plain size="small" :disabled="!batchSelection.length" @click="deleteSalaryBoardRows(batchSelection)">🗑 删除选中</el-button>
          <el-button type="success" size="small" :disabled="!batchSelection.length" :loading="distributeLoading" @click="submitBatchSalary(batchSelection)">
            💰 发放勾选员工
          </el-button>
        </div>
        <el-table ref="batchSalaryTableRef" :data="salaryBoardPager.paged" @selection-change="onBatchSelectionChange" :row-class-name="({ row }) => (row.hasAccount === false ? 'no-account-row' : '')" size="small" max-height="420" border style="width: 100%;">
          <el-table-column type="selection" width="42" :selectable="row => row.hasAccount !== false" />
          <el-table-column label="工号" width="115">
            <template #default="{ row }">
              <span :style="row.hasAccount === false ? 'color:#dc2626;' : ''">{{ row.staffId || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="姓名" width="100">
            <template #default="{ row }">
              <span :style="row.hasAccount === false ? 'color:#dc2626;' : ''">{{ row.name || '—' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="角色" width="92">
            <template #default="{ row }">
              <el-tag size="small" effect="plain">{{ row.roleLabel || '员工' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="基本底薪" width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.baseSalary" :min="0" :step="100" size="small" style="width: 100%;" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="门诊/电商提成" width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.clinicCommission" :min="0" :step="100" size="small" style="width: 100%;" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="贴敷理疗/合规奖" width="140">
            <template #default="{ row }">
              <el-input-number v-model="row.plasterCommission" :min="0" :step="100" size="small" style="width: 100%;" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="社保代扣" width="120">
            <template #default="{ row }">
              <el-input-number v-model="row.deductionSocial" :min="0" :step="50" size="small" style="width: 100%;" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="个税" width="110">
            <template #default="{ row }">
              <el-input-number v-model="row.tax" :min="0" :step="10" size="small" style="width: 100%;" controls-position="right" />
            </template>
          </el-table-column>
          <el-table-column label="实发工资" min-width="110">
            <template #default="{ row }">
              <b class="text-danger" style="font-size: 13px;">¥{{ batchRowNet(row).toFixed(2) }}</b>
            </template>
          </el-table-column>
          <el-table-column label="来源" width="210">
            <template #default="{ row }">
              <el-tag size="small" :type="row.sourceType || 'info'" effect="light" style="white-space: normal; line-height: 1.3;">{{ row.source || '待发放' }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="252" fixed="right">
            <template #default="{ row, $index }">
              <template v-if="row.hasAccount !== false">
                <el-button size="small" text type="primary" :loading="row._aiLoading" @click="aiCalcBatchRow(row)">测算</el-button>
                <el-button size="small" text type="success" :loading="row._paying" @click="paySingleRow(row)">发放</el-button>
              </template>
              <span v-else style="font-size: 11px; color: #dc2626;">⚠️ 无账号不可发</span>
              <el-button size="small" text type="danger" @click="deleteSalaryBoardRows([row])">删除</el-button>
            </template>
          </el-table-column>
        </el-table>
        <div style="display: flex; justify-content: flex-end; padding: 8px 4px 0 0;">
          <el-pagination v-model:current-page="salaryBoardPager.state.page" v-model:page-size="salaryBoardPager.state.size" :page-sizes="[10, 20, 50]" :total="salaryBoardPager.total" layout="total, sizes, prev, pager, next" size="small" />
        </div>
      </div>
    </template>
  </div>

  <!-- 发放记录弹窗（支持自由缩放、关键字搜索、月份筛选） -->
  <el-dialog v-model="showSlipHistoryDialog" title="📋 工资发放记录台账" width="1080px" destroy-on-close class="slip-history-dialog">
    <div class="batch-salary-toolbar">
      <el-input v-model="slipSearchKey" placeholder="搜索工号 / 姓名…" size="small" clearable style="width: 240px;" />
      <el-select v-model="slipFilterMonth" clearable placeholder="全部月份" size="small" style="width: 130px;">
        <el-option v-for="m in slipMonthOptions" :key="m" :label="m" :value="m" />
      </el-select>
      <span style="font-size: 12px; color: #64748b;">
        共 {{ filteredSalarySlips.length }} 条记录
        <template v-if="filteredSalarySlips.length">，合计实发 <b class="text-danger">¥{{ filteredSalarySlips.reduce((s, x) => s + (Number(x.netSalary) || 0), 0).toFixed(2) }}</b></template>
      </span>
      <el-button size="small" type="danger" plain :disabled="!slipSelection.length" @click="batchDeleteRows('/api/assistant/salary/batch-delete', slipSelection.map(r => r.id), '工资条记录', loadSalaryData)">🗑 删除选中</el-button>
      <el-pagination style="margin-left: auto;" v-model:current-page="slipsPager.state.page" v-model:page-size="slipsPager.state.size" :page-sizes="[10, 20, 50]" :total="slipsPager.total" layout="total, sizes, prev, pager, next" size="small" />
    </div>
    <el-table :data="slipsPager.paged" stripe size="small" style="width: 100%;" max-height="460" border @selection-change="slipSelection = $event">
      <el-table-column type="selection" width="42" />
      <el-table-column prop="doctorId" label="员工工号" width="115" />
      <el-table-column prop="doctorName" label="员工姓名" width="105" />
      <el-table-column prop="salaryMonth" label="归属月份" width="95" />
      <el-table-column prop="baseSalary" label="基本底薪" width="105">
        <template #default="scope"><span>¥{{ scope.row.baseSalary }}</span></template>
      </el-table-column>
      <el-table-column prop="clinicCommission" label="门诊/电商提成" width="125">
        <template #default="scope"><span class="text-success">+¥{{ scope.row.clinicCommission }}</span></template>
      </el-table-column>
      <el-table-column prop="plasterCommission" label="贴敷理疗/合规奖" width="135">
        <template #default="scope"><span class="text-success font-bold">+¥{{ scope.row.plasterCommission }}</span></template>
      </el-table-column>
      <el-table-column prop="netSalary" label="实发工资" min-width="110">
        <template #default="scope"><b class="text-danger" style="font-size: 14px;">¥{{ scope.row.netSalary }}</b></template>
      </el-table-column>
      <el-table-column prop="status" label="发放状态" width="95" align="center">
        <template #default="scope">
          <el-tag type="success" size="small" effect="dark">{{ scope.row.status || '已发放' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="发放时间" width="155">
        <template #default="scope">
          <span style="font-size: 12px; color: #64748b;">{{ (scope.row.createTime || '').replace('T', ' ').slice(0, 19) }}</span>
        </template>
      </el-table-column>
    </el-table>
  </el-dialog>
</template>
