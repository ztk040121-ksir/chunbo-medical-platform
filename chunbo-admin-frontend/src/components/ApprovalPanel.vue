<template>
  <div class="tab-pane">
    <div class="pane-header">
      <div>
        <h3>📑 医院综合 OA 请假审批中心</h3>
        <span class="sub-desc" v-if="currentUserRole === 'DOCTOR'">门诊医生专用申请通道 · 提交后由人事与院办在线审批</span>
        <span class="sub-desc" v-else>人事与院办审批平台 · 支持请假审核、同意与驳回</span>
      </div>
      <el-radio-group v-if="currentUserRole !== 'DOCTOR'" v-model="approvalViewRole" size="small">
        <el-radio-button value="DEAN">🏛️ 人事/院办审批视角</el-radio-button>
        <el-radio-button value="APPLICANT">👨‍⚕️ 员工申请发起视角</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 申请人视角：医生或员工发起请假 -->
    <div v-if="approvalViewRole === 'APPLICANT' || currentUserRole === 'DOCTOR'" class="applicant-box">
      <el-card class="mb-16">
        <template #header>
          <b>📝 发起请假 / 调休申请 (申请人: {{ currentUserName }} · 工号: {{ currentUserStaffId }})</b>
        </template>
        <el-form :inline="true" size="small">
          <el-form-item label="假别类型">
            <el-select v-model="leaveForm.type" style="width: 120px">
              <el-option label="年假" value="年假" />
              <el-option label="事假" value="事假" />
              <el-option label="学术休假" value="学术休假" />
            </el-select>
          </el-form-item>
          <el-form-item label="申请天数">
            <el-input-number v-model="leaveForm.days" :min="0.5" :step="0.5" :max="5" />
          </el-form-item>
          <el-form-item label="请假事由">
            <el-input v-model="leaveForm.reason" placeholder="如：参加全科医学学术高峰论坛" style="width: 260px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="submitLeave">提交申请至人事</el-button>
          </el-form-item>
        </el-form>
      </el-card>

      <el-card>
        <template #header><b>📋 我的请假记录与审批进度跟踪</b></template>
        <div class="batch-salary-toolbar">
          <el-input v-model="myApprovalsPager.state.search" placeholder="搜索假别 / 状态 / 事由…" size="small" clearable style="width: 220px;" />
          <el-pagination style="margin-left: auto;" v-model:current-page="myApprovalsPager.state.page" v-model:page-size="myApprovalsPager.state.size" :page-sizes="[10, 20, 50]" :total="myApprovalsPager.total" layout="total, sizes, prev, pager, next" size="small" />
        </div>
        <el-table :data="myApprovalsPager.paged" stripe size="small">
          <el-table-column prop="id" label="单号" width="70" />
          <el-table-column prop="applicantName" label="申请人" width="100" />
          <el-table-column prop="approvalType" label="假别" width="100" />
          <el-table-column prop="reason" label="请假事由" min-width="180" />
          <el-table-column prop="durationDays" label="天数" width="70" />
          <el-table-column prop="status" label="当前状态" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.status === '已通过' ? 'success' : (scope.row.status === '已驳回' ? 'danger' : 'warning')">
                {{ scope.row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="approverName" label="审批人" width="140" />
          <el-table-column prop="comment" label="审批批注" min-width="160" />
        </el-table>
      </el-card>
    </div>

    <!-- 人事/院办审批视角 -->
    <div v-else class="dean-box">
      <el-card>
        <template #header>
          <div class="flex-between">
            <b>🏛️ 人事与院办审批工作台 (当前审批人: {{ currentUserName }})</b>
            <el-tag type="warning">具备独立批准与驳回权限</el-tag>
          </div>
        </template>
        <div class="batch-salary-toolbar">
          <el-input v-model="approvalsPager.state.search" placeholder="搜索申请人 / 假别 / 状态…" size="small" clearable style="width: 240px;" />
          <el-button size="small" type="danger" plain :disabled="!approvalSelection.length" @click="batchDeleteRows('/api/assistant/approvals/batch-delete', approvalSelection.map(r => r.id), '审批单', loadApprovals)">🗑 删除选中</el-button>
          <el-pagination style="margin-left: auto;" v-model:current-page="approvalsPager.state.page" v-model:page-size="approvalsPager.state.size" :page-sizes="[10, 20, 50]" :total="approvalsPager.total" layout="total, sizes, prev, pager, next" size="small" />
        </div>
        <el-table :data="approvalsPager.paged" stripe size="small" @selection-change="approvalSelection = $event">
          <el-table-column type="selection" width="42" />
          <el-table-column prop="id" label="单据号" width="80" />
          <el-table-column prop="applicantName" label="申请医师" width="100" />
          <el-table-column prop="approvalType" label="假别类型" width="100" />
          <el-table-column prop="durationDays" label="请假天数" width="80" />
          <el-table-column prop="reason" label="申请事由" min-width="200" />
          <el-table-column prop="status" label="当前状态" width="90">
            <template #default="scope">
              <el-tag :type="scope.row.status === '已通过' ? 'success' : (scope.row.status === '已驳回' ? 'danger' : 'warning')">
                {{ scope.row.status }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="人事/院办审批操作" width="220">
            <template #default="scope">
              <div v-if="scope.row.status !== '已通过' && scope.row.status !== '已驳回'">
                <el-button type="success" size="small" @click="handleDeanApprove(scope.row, '已通过')">批准同意</el-button>
                <el-button type="danger" size="small" @click="handleDeanApprove(scope.row, '已驳回')">驳回申请</el-button>
              </div>
              <div v-else>
                <span class="text-muted">{{ scope.row.comment || '已归档' }}</span>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElNotification } from 'element-plus'
import { makeListPager, batchDeleteRows } from '../utils/common.js'

const props = defineProps({
  currentUserRole: { type: String, default: '' },
  currentUserName: { type: String, default: '' },
  currentUserStaffId: { type: String, default: '' },
  currentRoleLabel: { type: String, default: '' }
})

const approvals = ref([])

// 医生只看自己的申请记录
const displayApprovals = computed(() => {
  if (props.currentUserRole === 'DOCTOR') {
    return approvals.value.filter(a => a.applicantName === props.currentUserName)
  }
  return approvals.value
})

const approvalViewRole = ref('DEAN')
const leaveForm = ref({
  type: '年假',
  days: 1.0,
  reason: '参加全科医学学术高峰论坛培训'
})

const approvalsPager = makeListPager(approvals, ['applicantName', 'leaveType', 'status', 'reason'])
const myApprovalsPager = makeListPager(displayApprovals, ['applicantName', 'approvalType', 'status', 'reason'])
const approvalSelection = ref([])

const loadApprovals = async () => {
  try {
    const res = await axios.get('/api/assistant/approvals')
    approvals.value = res.data || []
  } catch (e) {}
}

const submitLeave = async () => {
  try {
    const payload = {
      applicantName: props.currentUserName,
      applicantId: props.currentUserStaffId,
      approvalType: leaveForm.value.type,
      reason: leaveForm.value.reason,
      durationDays: leaveForm.value.days
    }
    const res = await axios.post('/api/assistant/approvals/create', payload)
    if (!res.data?.success) {
      ElMessage.error(res.data?.message || '提交失败')
      return
    }
    ElNotification({
      title: '请假申请提交成功',
      message: res.data.message || '已提交至人事/院办审批待办',
      type: 'success'
    })
    loadApprovals()
  } catch (e) { ElMessage.error(e.response?.data?.message || '提交失败') }
}

const handleDeanApprove = async (row, action) => {
  try {
    await axios.post('/api/assistant/approvals/process', {
      id: row.id,
      status: action,
      approver: props.currentUserName + ' (' + props.currentRoleLabel + ')',
      comment: action === '已通过' ? '准假，已同步门诊排班。' : '当前门诊高峰，暂缓休假。'
    })
    ElMessage.success(`已标记为【${action}】`)
    loadApprovals()
  } catch (e) { ElMessage.error('审批失败') }
}

onMounted(() => {
  loadApprovals()
})
</script>
