<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElNotification, ElMessageBox } from 'element-plus'
import { makeListPager } from '../utils/common.js'

const emit = defineEmits(['perm-saved'])

const staffRoles = ref([])

// 指标卡改为统一台账口径（staffRoles = 医生表+员工表去重合并的真实账号）
const unifiedStaffTotal = computed(() => staffRoles.value.length)
const activeStaffCount = computed(() => staffRoles.value.filter(s => s.status === 'ENABLE').length)
const departmentCount = computed(() => new Set(staffRoles.value.map(s => s.department).filter(d => d && d !== '—')).size)

const loadStaffRoles = async () => {
  try {
    const res = await axios.get('/api/analytics/staff-roles')
    staffRoles.value = res.data || []
  } catch (e) {}
}

// ── 角色与权限动态管理（医护账号与权限管理页）──
const roleOptions = [
  { value: 'ADMIN', label: 'ADMIN 管理员' },
  { value: 'HR', label: 'HR 人事' },
  { value: 'DOCTOR', label: 'DOCTOR 医生' },
  { value: 'NURSE', label: 'NURSE 护士' },
  { value: 'MERCHANT', label: 'MERCHANT 商户' }
]
const roleModuleOptions = [
  { key: 'registration', label: '📋 门诊挂号' },
  { key: 'clinic', label: '🩺 门诊接诊' },
  { key: 'billing', label: '💰 划价收费' },
  { key: 'treatment', label: '💉 特色执行站' },
  { key: 'pharmacy', label: '💊 智慧药房' },
  { key: 'patient', label: '📁 患者档案' },
  { key: 'ai-settings', label: '⚙️ 设置' }
]
// 管理中台模块（5174 侧栏菜单，与云诊所模块是两套独立权限）
const adminModuleOptions = [
  { key: 'analytics', label: '📊 经营分析大屏' },
  { key: 'mall-orders', label: '📦 商城订单履约' },
  { key: 'mall-products', label: '🛍️ 商城商品与进销存' },
  { key: 'mall-users', label: '👤 商城注册用户' },
  { key: 'salary', label: '💼 工资条管理' },
  { key: 'approval', label: '📑 OA请假审批' },
  { key: 'doctors', label: '👨‍⚕️ 医护账号与权限' }
]
const permRowsByScope = (list, scope) =>
  list.filter(r => String(r.scope || 'CLINIC').toUpperCase() === scope).map(r => ({
    role: r.role,
    roleLabel: r.roleLabel || r.role,
    scope: String(r.scope || 'CLINIC').toUpperCase(),
    modules: (() => { try { return JSON.parse(r.modulesJson || '[]') } catch (e) { return [] } })(),
    description: r.description || '',
    _saving: false
  }))
const rolePermRows = ref([])
const clinicPermRows = computed(() => permRowsByScope(rolePermRows.value, 'CLINIC'))
const adminPermRows = computed(() => permRowsByScope(rolePermRows.value, 'ADMIN'))
// 权限面板系统视角切换：同一份角色列表，切到云诊所/管理系统分别显示各自的模块配置
const permScopeView = ref('CLINIC')
const permRowsForView = computed(() => {
  // 云诊所视角下不展示无云诊所权限的角色（人事/商户等空配置行），只列真正使用云诊所的角色
  if (permScopeView.value === 'CLINIC') return clinicPermRows.value.filter(r => r.modules.length > 0)
  return adminPermRows.value
})
const permRowsEditable = (row) => {
  if (row.role === 'ADMIN') return false
  if (permScopeView.value === 'ADMIN') return true
  return Boolean(row.modules.length || row._editing)
}
const loadRolePermRows = async () => {
  try {
    const res = await axios.get('/api/role-permissions')
    rolePermRows.value = res.data || []
  } catch (e) {}
}
const saveRolePerm = async (row) => {
  row._saving = true
  try {
    const res = await axios.post('/api/admin/role-permissions/save', {
      role: row.role, roleLabel: row.roleLabel, scope: row.scope, modules: row.modules, description: row.description
    })
    if (res.data?.success) {
      ElMessage.success(res.data.message || '已保存')
      loadRolePermRows()
      loadStaffRoles()
      emit('perm-saved')
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e.response?.data?.message || e.message))
  } finally { row._saving = false }
}
const handleStaffRoleRowChange = async (row, newRole) => {
  try {
    const res = await axios.post('/api/admin/staff/update-role', { staffId: row.staffId, role: newRole })
    if (res.data?.success) {
      ElMessage.success(res.data.message || '角色已更新')
    } else {
      ElMessage.error(res.data?.message || '角色更新失败')
    }
  } catch (e) {
    ElMessage.error('角色更新失败：' + (e.response?.data?.message || e.message))
  }
  loadStaffRoles()
}

// ── 统一注册账号（选角色即定权限，医生/护士/人事/商户/管理员一张表全收）──
const showRegisterUnifiedDialog = ref(false)
const registerUnifiedLoading = ref(false)
const newRegisterForm = ref({ role: 'DOCTOR', username: '', password: '123456', realName: '', phone: '', department: '', title: '' })
const registerRoleOptions = [
  { value: 'DOCTOR', label: '医生（云诊所全部模块 + 中台：大屏/工资条/请假）' },
  { value: 'NURSE', label: '护士（云诊所：挂号/划价/执行/药房/患者档案 + 中台：大屏/工资条/请假）' },
  { value: 'HR', label: '人事（仅管理中台：大屏/工资发放/审批/医护账号管理）' },
  { value: 'MERCHANT', label: '商户（仅管理中台：商城三模块 + 工资条/请假）' },
  { value: 'ADMIN', label: '系统最高管理员（两套系统全部权限）' }
]
const openRegisterUnified = () => {
  newRegisterForm.value = { role: 'DOCTOR', username: '', password: '123456', realName: '', phone: '', department: '', title: '' }
  showRegisterUnifiedDialog.value = true
}
const submitRegisterUnified = async () => {
  const f = newRegisterForm.value
  if (!f.username.trim() || !f.realName.trim()) {
    ElMessage.warning('登录账号与真实姓名为必填项')
    return
  }
  if (!f.phone.trim() || !/^1\d{10}$/.test(f.phone.trim())) {
    ElMessage.warning('请输入 1 开头的 11 位手机号')
    return
  }
  if (!f.department.trim() || !f.title.trim()) {
    ElMessage.warning('请选择部门/科室与岗位职称')
    return
  }
  registerUnifiedLoading.value = true
  try {
    const res = await axios.post('/api/admin/staff/register', {
      role: f.role, username: f.username.trim(), password: f.password.trim() || '123456',
      realName: f.realName.trim(), phone: f.phone.trim(), department: f.department.trim(), title: f.title.trim()
    })
    if (res.data?.success) {
      ElNotification({
        title: '账号注册成功！',
        message: res.data.message || `账号已创建，角色 ${f.role}，自动获得对应权限`,
        type: 'success'
      })
      showRegisterUnifiedDialog.value = false
      loadStaffRoles()
    } else {
      ElMessage.error(res.data?.message || '注册失败')
    }
  } catch (e) {
    ElMessage.error('注册失败：' + (e.response?.data?.message || e.message))
  } finally { registerUnifiedLoading.value = false }
}

// 医护账号统一总台账（后端 /staff-roles 已做医生表+员工表去重合并）
const unifiedPager = makeListPager(staffRoles, ['staffId', 'username', 'name', 'department', 'title', 'role', 'permissions'])
const unifiedSelection = ref([])
// 统一行操作：按 source 分派到医生表/员工表接口
const toggleUnifiedStatus = async (row) => {
  const nextStatus = row.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
  try {
    if (row.source === 'doctor') {
      await axios.post('/api/doctor/update-status', { id: row.id, status: nextStatus })
    } else {
      await axios.post('/api/admin/staff/status', { id: row.id, status: nextStatus })
    }
    row.status = nextStatus
    ElMessage.success(nextStatus === 'ENABLE' ? '账号已启用' : '账号已停用')
  } catch (e) { ElMessage.error('操作失败') }
}
const resetUnifiedPassword = async (row) => {
  ElMessageBox.confirm(`确定将 [${row.name}] 的密码重置为 123456 吗？`, '重置确认', { type: 'warning' })
    .then(async () => {
      const res = row.source === 'doctor'
        ? await axios.post('/api/doctor/reset-password', { id: row.id, newPassword: '123456' })
        : await axios.post('/api/admin/staff/reset-password', { id: row.id, newPassword: '123456' })
      ElMessage.success(res.data?.message || '密码已重置为 123456')
    }).catch(() => {})
}
const deleteUnifiedAccounts = async (rows) => {
  if (!rows || !rows.length) {
    ElMessage.warning('请先勾选要删除的账号')
    return
  }
  const names = rows.map(r => `${r.name || r.username}(${r.staffId})`).join('、')
  try {
    await ElMessageBox.confirm(
      `确定删除：${names}？删除后该账号将无法登录对应系统，操作不可恢复。`,
      '删除账号确认',
      { type: 'danger', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch (e) { return }
  let ok = 0
  const fail = []
  for (const r of rows) {
    try {
      if (r.source === 'doctor') await axios.delete('/api/doctor/' + r.id)
      else await axios.delete('/api/admin/staff/' + r.id)
      ok++
    } catch (e) { fail.push(r.name || r.staffId) }
  }
  ElMessage.success(`已删除 ${ok} 个账号${fail.length ? '；失败：' + fail.join('、') : ''}`)
  loadStaffRoles()
}

onMounted(() => {
  loadStaffRoles()
  loadRolePermRows()
})
</script>

<template>
  <div class="tab-pane">
    <div class="pane-header">
      <div>
        <h3>👨‍⚕️ 医护账号与权限管理中心</h3>
        <span class="sub-desc">统一注册医生/护士/人事/商户账号，按角色自动生效对应系统权限</span>
      </div>
    </div>

    <!-- 统计指标 -->
    <div class="metrics-grid mb-16">
      <div class="metric-card bg-blue">
        <div class="m-label">全院在册账号总数</div>
        <div class="m-val">{{ unifiedStaffTotal }} <span class="unit">个</span></div>
        <div class="m-sub">医生 + 员工 + 商户等系统账号统一统计</div>
      </div>
      <div class="metric-card bg-green">
        <div class="m-label">正常启用账号</div>
        <div class="m-val">{{ activeStaffCount }} <span class="unit">个</span></div>
        <div class="m-sub">当前可正常登录系统的账号</div>
      </div>
      <div class="metric-card bg-purple">
        <div class="m-label">覆盖部门/科室</div>
        <div class="m-val">{{ departmentCount }} <span class="unit">个</span></div>
        <div class="m-sub">门诊、药房、人事、商城运营等</div>
      </div>
    </div>

    <!-- 全院医护与员工统一账号总台账（医生表 + 员工表去重合并，一张表管理） -->
    <el-card>
      <template #header>
        <div class="flex-between">
          <b>👥 全院医护与员工账号总台账（真实查库 · 角色 / 状态 / 密码统一管理）</b>
          <div style="display: flex; gap: 8px; align-items: center;">
            <el-tag type="info" size="small">默认测试工号: kzt / 密码: 123456</el-tag>
            <el-button type="success" size="small" @click="openRegisterUnified">
              ➕ 注册账号
            </el-button>
          </div>
        </div>
      </template>
      <div class="batch-salary-toolbar">
        <el-input v-model="unifiedPager.state.search" placeholder="搜索账号 / 工号 / 姓名 / 科室 / 角色…" size="small" clearable style="width: 260px;" />
        <el-button size="small" type="danger" plain :disabled="!unifiedSelection.length" @click="deleteUnifiedAccounts(unifiedSelection)">🗑 删除选中</el-button>
        <el-pagination style="margin-left: auto;" v-model:current-page="unifiedPager.state.page" v-model:page-size="unifiedPager.state.size" :page-sizes="[10, 20, 50]" :total="unifiedPager.total" layout="total, sizes, prev, pager, next" size="small" />
      </div>
      <el-table :data="unifiedPager.paged" stripe size="small" @selection-change="unifiedSelection = $event">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="username" label="登录账号" width="120">
          <template #default="scope">
            <el-tag effect="plain" type="primary" class="font-mono"><b>{{ scope.row.username }}</b></el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="姓名" width="110">
          <template #default="scope"><b>{{ scope.row.name }}</b></template>
        </el-table-column>
        <el-table-column prop="staffId" label="数字工号" width="110">
          <template #default="scope">
            <el-tag type="success" size="small">{{ scope.row.staffId }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="所属部门/科室" width="150" />
        <el-table-column prop="title" label="岗位职称" width="140" />
        <el-table-column label="系统角色（可编辑）" width="170">
          <template #default="scope">
            <el-select :model-value="scope.row.role" size="small" style="width: 135px;" @change="(v) => handleStaffRoleRowChange(scope.row, v)">
              <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column prop="permissions" label="功能权限范围（按角色动态配置）" min-width="220" />
        <el-table-column prop="status" label="账号状态" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'ENABLE' ? 'success' : 'danger'" size="small">
              {{ scope.row.status === 'ENABLE' ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="scope">
            <el-button
              size="small"
              :type="scope.row.status === 'ENABLE' ? 'warning' : 'success'"
              link
              @click="toggleUnifiedStatus(scope.row)"
            >
              {{ scope.row.status === 'ENABLE' ? '停用' : '启用' }}
            </el-button>
            <el-button size="small" type="primary" link @click="resetUnifiedPassword(scope.row)">重置密码</el-button>
            <el-button size="small" type="danger" link @click="deleteUnifiedAccounts([scope.row])">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 角色权限范围动态配置：分「云诊所」「管理中台」两套系统（写入 sys_role_permission，前端菜单按此动态渲染） -->
    <el-card style="margin-top: 16px;">
      <template #header>
        <div class="flex-between">
          <b>🔐 角色权限范围配置（云诊所与管理中台两套系统独立配置，保存后重新登录生效）</b>
          <el-tag type="info" size="small">切换系统视角查看和编辑对应权限</el-tag>
        </div>
      </template>
      <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 12px;">
        <span style="font-size: 13px; font-weight: 500;">切换系统视角：</span>
        <el-radio-group v-model="permScopeView">
          <el-radio-button value="CLINIC">🏥 云诊所权限 (5173)</el-radio-button>
          <el-radio-button value="ADMIN">🏛️ 管理系统权限 (5174)</el-radio-button>
        </el-radio-group>
      </div>
      <el-alert v-if="permScopeView === 'CLINIC'" type="info" :closable="false" style="margin-bottom: 10px;"
        title="控制各角色登录春播万象云诊所后可见的模块。系统最高管理员天然拥有全部权限无需配置；人事/商户默认无云诊所权限（仅使用管理中台），如需开通可点击该行的「编辑」。" />
      <el-alert v-else type="info" :closable="false" style="margin-bottom: 10px;"
        title="控制各角色登录春播云管理系统中台后左侧菜单可见的功能：医生/护士 = 经营分析大屏 + 我的工资条 + 请假申请；商户额外可见商城三模块；人事/管理员 = 全部。" />
      <el-table :data="permRowsForView" stripe size="small">
        <el-table-column prop="roleLabel" label="角色" width="170" />
        <el-table-column :label="permScopeView === 'CLINIC' ? '云诊所模块权限' : '管理系统模块权限'" min-width="380">
          <template #default="scope">
            <div v-if="scope.row.role === 'ADMIN'" class="perm-full-text">✅ 拥有{{ permScopeView === 'CLINIC' ? '云诊所' : '管理系统' }}全部模块权限（系统最高管理员，无需勾选配置）</div>
            <div v-else-if="permScopeView === 'CLINIC' && !scope.row.modules.length && !scope.row._editing" class="perm-none-text">
              ⛔ 无云诊所权限（该角色仅使用管理系统）
              <el-button size="small" text type="primary" @click="scope.row._editing = true">如需开通请点此编辑</el-button>
            </div>
            <el-checkbox-group v-else v-model="scope.row.modules">
              <el-checkbox v-for="m in (permScopeView === 'CLINIC' ? roleModuleOptions : adminModuleOptions)" :key="m.key" :value="m.key" style="margin-right: 10px;">{{ m.label }}</el-checkbox>
            </el-checkbox-group>
          </template>
        </el-table-column>
        <el-table-column label="权限范围描述" min-width="200">
          <template #default="scope">
            <el-input v-if="permRowsEditable(scope.row)" v-model="scope.row.description" size="small" :placeholder="permScopeView === 'CLINIC' ? '该角色在云诊所的权限范围描述' : '该角色在管理系统的权限范围描述'" />
            <span v-else style="color: #94a3b8; font-size: 12px;">{{ scope.row.description || '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="scope">
            <template v-if="permRowsEditable(scope.row)">
              <el-button v-if="scope.row._editing && !scope.row.modules.length" size="small" text @click="scope.row._editing = false">取消</el-button>
              <el-button type="primary" size="small" :loading="scope.row._saving" @click="saveRolePerm(scope.row)">保存</el-button>
            </template>
            <span v-else style="color: #94a3b8; font-size: 12px;">无需配置</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>

  <!-- 统一注册账号弹窗（选角色即定权限，全局任意 tab 可打开） -->
  <el-dialog v-model="showRegisterUnifiedDialog" title="➕ 注册系统账号（按角色自动生效权限）" width="500px">
    <el-alert type="info" :closable="false" style="margin-bottom: 14px;"
      title="医生/护士可登录云诊所与管理中台（按角色权限范围），人事/商户仅登录管理中台。注册成功后自动获得该角色的权限范围。" />
    <el-form :model="newRegisterForm" label-width="100px">
      <el-form-item label="系统角色" required>
        <el-select v-model="newRegisterForm.role" style="width: 100%">
          <el-option v-for="r in registerRoleOptions" :key="r.value" :label="r.label" :value="r.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="登录账号" required>
        <el-input v-model="newRegisterForm.username" placeholder="登录用户名，如 doctor_wang / hr_li" />
      </el-form-item>
      <el-form-item label="初始密码" required>
        <el-input v-model="newRegisterForm.password" placeholder="默认 123456" show-password />
      </el-form-item>
      <el-form-item label="真实姓名" required>
        <el-input v-model="newRegisterForm.realName" placeholder="如 王文清 / 王文静" />
      </el-form-item>
      <el-form-item label="手机号" required>
        <el-input v-model="newRegisterForm.phone" placeholder="1 开头的 11 位手机号" maxlength="11" />
      </el-form-item>
      <el-form-item label="部门/科室" required>
        <el-select v-model="newRegisterForm.department" style="width: 100%" filterable allow-create default-first-option placeholder="选择或输入">
          <el-option label="全科门诊" value="全科门诊" />
          <el-option label="全科慢病门诊" value="全科慢病门诊" />
          <el-option label="中医理疗特色门诊" value="中医理疗特色门诊" />
          <el-option label="儿科综合门诊" value="儿科综合门诊" />
          <el-option label="智慧药房" value="智慧药房" />
          <el-option label="人事行政科" value="人事行政科" />
          <el-option label="综合行政人事部" value="综合行政人事部" />
          <el-option label="医院院办" value="医院院办" />
          <el-option label="春播商城运营部" value="春播商城运营部" />
          <el-option label="信息中心" value="信息中心" />
        </el-select>
      </el-form-item>
      <el-form-item label="岗位职称" required>
        <el-select v-model="newRegisterForm.title" style="width: 100%" filterable allow-create default-first-option placeholder="选择或输入">
          <el-option label="主任医师" value="主任医师" />
          <el-option label="副主任医师" value="副主任医师" />
          <el-option label="主治医师" value="主治医师" />
          <el-option label="执业医师" value="执业医师" />
          <el-option label="护士" value="护士" />
          <el-option label="人事主管" value="人事主管" />
          <el-option label="人事专员" value="人事专员" />
          <el-option label="供应链主管" value="供应链主管" />
          <el-option label="运营主管" value="运营主管" />
          <el-option label="行政助理" value="行政助理" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="showRegisterUnifiedDialog = false">取消</el-button>
      <el-button type="success" :loading="registerUnifiedLoading" @click="submitRegisterUnified">确认注册</el-button>
    </template>
  </el-dialog>
</template>
