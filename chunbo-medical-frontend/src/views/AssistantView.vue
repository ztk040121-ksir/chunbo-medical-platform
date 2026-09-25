<template>
  <div class="assistant-full-container">
    <!-- 顶部综合标题与子系统切换 -->
    <div class="oa-header-bar">
      <div class="header-main">
        <div class="brand-icon">💼</div>
        <div>
          <h2>春播助手 · 基层诊所职能与薪资智能体 (OA SaaS)</h2>
          <span class="sub">基于 Spring AI Tool Calling + MyBatis-Plus 领域模型持久化，打通薪资明细、贴敷营收、OA审批与 Token 成本审计</span>
        </div>
      </div>

      <!-- 快捷数据指标卡片 -->
      <div class="header-metrics">
        <div class="metric-item">
          <span class="m-label">当月实发薪资</span>
          <span class="m-val text-success">¥{{ currentNetSalary }}</span>
        </div>
        <div class="metric-item">
          <span class="m-label">贴敷专项营收</span>
          <span class="m-val text-primary">¥{{ plasterSummary.totalRevenue || 0 }}</span>
        </div>
        <div class="metric-item">
          <span class="m-label">待办审批</span>
          <span class="m-val text-warning">{{ pendingApprovalsCount }} 件</span>
        </div>
        <div class="metric-item">
          <span class="m-label">Token 成本累计</span>
          <span class="m-val text-danger">¥{{ tokenStats.totalCostCny ? Number(tokenStats.totalCostCny).toFixed(4) : '0.0000' }}</span>
        </div>
      </div>
    </div>

    <!-- 主体区域：左侧导航与右侧内容面板 -->
    <div class="oa-content-grid">
      <!-- 5大职能卡片切换 -->
      <div class="oa-subnav">
        <div 
          class="subnav-item" 
          :class="{ active: currentTab === 'chat' }" 
          @click="currentTab = 'chat'"
        >
          <el-icon><ChatDotRound /></el-icon>
          <span>💬 对话智能体 (Agent)</span>
        </div>
        <div 
          class="subnav-item" 
          :class="{ active: currentTab === 'salary' }" 
          @click="currentTab = 'salary'"
        >
          <el-icon><Money /></el-icon>
          <span>💵 医生工资条明细</span>
        </div>
        <div 
          class="subnav-item" 
          :class="{ active: currentTab === 'plaster' }" 
          @click="currentTab = 'plaster'"
        >
          <el-icon><DataLine /></el-icon>
          <span>🌿 贴敷疗程营收大屏</span>
        </div>
        <div 
          class="subnav-item" 
          :class="{ active: currentTab === 'approval' }" 
          @click="currentTab = 'approval'"
        >
          <el-icon><DocumentChecked /></el-icon>
          <span>📋 OA 审批与考勤流转</span>
          <el-badge v-if="pendingApprovalsCount > 0" :value="pendingApprovalsCount" class="badge-mini" />
        </div>
        <div 
          class="subnav-item" 
          :class="{ active: currentTab === 'token' }" 
          @click="currentTab = 'token'"
        >
          <el-icon><Cpu /></el-icon>
          <span>📊 Token 成本监控审计</span>
        </div>
      </div>

      <!-- 右侧面板 -->
      <div class="oa-panel-body">
        <!-- 1. 对话智能体 -->
        <div v-if="currentTab === 'chat'" class="chat-workspace">
          <div class="quick-oa-queries">
            <span class="label">💡 常用自然语言问询:</span>
            <el-button size="small" round @click="askQuery('查询我上个月的工资条明细')">查询我上个月的工资条明细</el-button>
            <el-button size="small" round @click="askQuery('查看本月贴敷治疗疗程与业绩')">查看本月贴敷治疗疗程与业绩</el-button>
            <el-button size="small" round @click="askQuery('申请本周五下午调休半天，事由为参加基层全科医学学术会议')">申请周五调休半天</el-button>
            <el-button size="small" round @click="askQuery('查询诊所值班排班表')">查询诊所值班排班表</el-button>
          </div>

          <div class="chat-messages-scroll" ref="scrollRef">
            <div v-for="(item, idx) in messageList" :key="idx" class="oa-bubble" :class="item.role">
              <div class="avatar">{{ item.role === 'user' ? '👨‍⚕️' : '💼' }}</div>
              <div class="content">
                <div class="sender">{{ item.role === 'user' ? `${currentDoctorName} (${currentDoctorTitle})` : '春播助手 AI 职能专家' }}</div>
                <div class="markdown-body" v-html="renderMarkdown(item.content)"></div>
              </div>
            </div>
            <div v-if="loading" class="oa-bubble assistant">
              <div class="avatar">💼</div>
              <div class="content loading-box">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>Spring AI @Tool 工具自主拆解与数据检索中...</span>
              </div>
            </div>
          </div>

          <div class="chat-input-bar">
            <el-input 
              v-model="queryInput" 
              :placeholder="`例如：查询${currentDoctorName}的上个月工资条明细、查询特色贴敷分成、发起调休申请`" 
              @keyup.enter="sendQuery"
            />
            <el-button type="primary" :loading="loading" @click="sendQuery" icon="Promotion">发送问询</el-button>
          </div>
        </div>

        <!-- 2. 工资条明细 -->
        <div v-else-if="currentTab === 'salary'" class="data-panel">
          <div class="panel-top">
            <h3>💵 全科医生薪资与明细账单库</h3>
            <span class="sub-text">支持底薪、门诊诊疗绩效、中药贴敷专项分成及五险一金明细核算</span>
          </div>

          <el-table :data="salarySlips" stripe style="width: 100%">
            <el-table-column prop="salaryMonth" label="考勤发薪月份" width="120">
              <template #default="scope">
                <b>{{ scope.row.salaryMonth }}</b>
              </template>
            </el-table-column>
            <el-table-column prop="doctorName" label="医生姓名" width="100" />
            <el-table-column prop="baseSalary" label="基本工资(元)" width="120">
              <template #default="scope">¥{{ scope.row.baseSalary }}</template>
            </el-table-column>
            <el-table-column prop="clinicCommission" label="门诊提成(元)" width="120">
              <template #default="scope">¥{{ scope.row.clinicCommission }}</template>
            </el-table-column>
            <el-table-column prop="plasterCommission" label="贴敷专项分成(元)" width="150">
              <template #default="scope">
                <span class="highlight-green">¥{{ scope.row.plasterCommission }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="deductionSocial" label="五险一金扣除" width="120">
              <template #default="scope">-¥{{ scope.row.deductionSocial }}</template>
            </el-table-column>
            <el-table-column prop="tax" label="个税扣除" width="100">
              <template #default="scope">-¥{{ scope.row.tax }}</template>
            </el-table-column>
            <el-table-column prop="netSalary" label="实发到手薪资" min-width="140">
              <template #default="scope">
                <span class="salary-net">¥{{ scope.row.netSalary }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="发放状态" width="100">
              <template #default="scope">
                <el-tag type="success" size="small">{{ scope.row.status }}</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 3. 贴敷疗程营收大屏 -->
        <div v-else-if="currentTab === 'plaster'" class="data-panel">
          <div class="panel-top">
            <h3>🌿 春播万象 · 特色中药贴敷专项运营分析大屏</h3>
            <span class="sub-text">实时聚合各病种贴敷疗程量、单价与营业额，自动关联医生分成</span>
          </div>

          <div class="stats-cards-row">
            <div class="stat-card">
              <div class="sc-title">累计贴敷治疗量</div>
              <div class="sc-val">{{ plasterSummary.totalPasteCount || 0 }} <span class="unit">贴</span></div>
              <div class="sc-tip">环比上月 +18.5%</div>
            </div>
            <div class="stat-card">
              <div class="sc-title">贴敷总营业收入</div>
              <div class="sc-val text-primary">¥{{ plasterSummary.totalRevenue || 0 }}</div>
              <div class="sc-tip">技术操作提成占比 30%</div>
            </div>
            <div class="stat-card">
              <div class="sc-title">受益就诊患者人次</div>
              <div class="sc-val">{{ plasterSummary.recordCount || 0 }} <span class="unit">人</span></div>
              <div class="sc-tip">慢病与理疗回访率 92%</div>
            </div>
          </div>

          <div class="table-title-bar">贴敷临床治疗就诊流水明细：</div>
          <el-table :data="plasterSummary.records" stripe style="width: 100%" size="small">
            <el-table-column prop="therapyDate" label="治疗日期" width="110" />
            <el-table-column prop="patientName" label="患者姓名" width="100" />
            <el-table-column prop="plasterType" label="贴敷治疗品类" min-width="180">
              <template #default="scope">
                <b>{{ scope.row.plasterType }}</b>
              </template>
            </el-table-column>
            <el-table-column prop="pasteCount" label="疗程贴数" width="90">
              <template #default="scope">{{ scope.row.pasteCount }} 贴</template>
            </el-table-column>
            <el-table-column prop="unitPrice" label="单价" width="80">
              <template #default="scope">¥{{ scope.row.unitPrice }}</template>
            </el-table-column>
            <el-table-column prop="totalAmount" label="总额" width="90">
              <template #default="scope">
                <span class="text-danger">¥{{ scope.row.totalAmount }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="doctorName" label="主治医生" width="90" />
            <el-table-column prop="clinicName" label="所属社区卫生站" min-width="160" />
          </el-table>
        </div>

        <!-- 4. OA 审批流转中心 -->
        <div v-else-if="currentTab === 'approval'" class="data-panel">
          <div class="panel-top">
            <h3>📋 诊所 OA 请假与考勤审批中心</h3>
            <span class="sub-text">支持对话式自然语言发起请假/调班，主管在线即时核准</span>
          </div>

          <el-table :data="approvals" stripe style="width: 100%">
            <el-table-column prop="id" label="审批单号" width="90">
              <template #default="scope">OA{{ scope.row.id }}</template>
            </el-table-column>
            <el-table-column prop="applicantName" label="申请人" width="110" />
            <el-table-column prop="approvalType" label="审批类型" width="110">
              <template #default="scope">
                <el-tag size="small">{{ scope.row.approvalType }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reason" label="申请事由" min-width="200" />
            <el-table-column prop="durationDays" label="天数" width="80">
              <template #default="scope">{{ scope.row.durationDays }}天</template>
            </el-table-column>
            <el-table-column prop="status" label="审批状态" width="100">
              <template #default="scope">
                <el-tag :type="scope.row.status === '已通过' ? 'success' : (scope.row.status === '待审批' ? 'warning' : 'danger')" size="small">
                  {{ scope.row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="approverName" label="审批人" width="110" />
            <el-table-column label="快捷流转操作" width="150">
              <template #default="scope">
                <div v-if="scope.row.status === '待审批'">
                  <el-button type="success" size="small" @click="handleApprove(scope.row.id, '已通过')">通过</el-button>
                  <el-button type="danger" size="small" @click="handleApprove(scope.row.id, '已驳回')">驳回</el-button>
                </div>
                <span v-else class="text-muted">审批已办结</span>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <!-- 5. Token 成本监控审计 -->
        <div v-else-if="currentTab === 'token'" class="data-panel">
          <div class="panel-top">
            <h3>📊 大模型 Token 用量核算与成本审计看板</h3>
            <span class="sub-text">基于 SimpleLoggerAdvisor 拦截器采集单次 Prompt/Completion Token 及折算成本（¥）</span>
          </div>

          <div class="stats-cards-row">
            <div class="stat-card">
              <div class="sc-title">累计调用次数</div>
              <div class="sc-val">{{ tokenStats.totalCalls || 0 }} <span class="unit">次</span></div>
            </div>
            <div class="stat-card">
              <div class="sc-title">累计 Token 消耗</div>
              <div class="sc-val text-primary">{{ tokenStats.totalTokens || 0 }}</div>
              <div class="sc-tip">Prompt + Completion</div>
            </div>
            <div class="stat-card">
              <div class="sc-title">平均往返延迟</div>
              <div class="sc-val text-success">{{ tokenStats.avgLatencyMs || 0 }} <span class="unit">ms</span></div>
            </div>
            <div class="stat-card">
              <div class="sc-title">累计费用折算</div>
              <div class="sc-val text-danger">¥{{ tokenStats.totalCostCny ? Number(tokenStats.totalCostCny).toFixed(5) : '0.00000' }}</div>
            </div>
          </div>

          <div class="table-title-bar">最近大模型调用流水审计记录：</div>
          <el-table :data="tokenStats.recentLogs" stripe style="width: 100%" size="small">
            <el-table-column prop="id" label="流水ID" width="70" />
            <el-table-column prop="modelName" label="模型名称" width="120" />
            <el-table-column prop="promptTokens" label="输入 Tokens" width="110" />
            <el-table-column prop="completionTokens" label="输出 Tokens" width="110" />
            <el-table-column prop="totalTokens" label="总 Tokens" width="100" />
            <el-table-column prop="latencyMs" label="调用耗时" width="110">
              <template #default="scope">{{ scope.row.latencyMs }} ms</template>
            </el-table-column>
            <el-table-column prop="costCny" label="估算费用" width="120">
              <template #default="scope">¥{{ scope.row.costCny ? Number(scope.row.costCny).toFixed(5) : '0.00000' }}</template>
            </el-table-column>
            <el-table-column prop="createTime" label="记录时间" min-width="160" />
          </el-table>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { marked } from 'marked'
import axios from 'axios'
import { ElMessage } from 'element-plus'

// 防 XSS：转义 LLM/用户输出中的原始 HTML
const escapeHtml = (s) => String(s ?? '')
  .replace(/&/g, '&amp;')
  .replace(/</g, '&lt;')
  .replace(/>/g, '&gt;')
  .replace(/"/g, '&quot;')
  .replace(/'/g, '&#39;')
marked.use({ renderer: { html(token) { return escapeHtml(token.text) } } })

const currentTab = ref('chat')
const messageList = ref([
  {
    role: 'assistant',
    content: '您好！我是基层医生专属的「春播助手」。我可以为您查询：\n- 💵 **个人工资条明细与绩效提成**\n- 🌿 **诊所特色贴敷治疗疗程营收统计**\n- 📋 **在线发起调休、请假 OA 审批**\n- 📅 **门诊医生与护士值班排班安排**\n请点击上方快捷问询或直接在下方输入您的问题。'
  }
])
const currentDoctorName = ref(localStorage.getItem('chunbo_display_name') || localStorage.getItem('chunbo_username') || '门诊医生')
const currentDoctorId = ref(localStorage.getItem('chunbo_doctor_id') || 'DOC_1001')
const currentDoctorTitle = ref(localStorage.getItem('chunbo_title') || '全科医生')

const queryInput = ref('')
const loading = ref(false)
const scrollRef = ref(null)

const salarySlips = ref([])
const plasterSummary = ref({})
const approvals = ref([])
const tokenStats = ref({})

const currentNetSalary = computed(() => {
  return salarySlips.value.length > 0 ? salarySlips.value[0].netSalary : 0.00
})

const pendingApprovalsCount = computed(() => {
  return approvals.value.filter(a => a.status === '待审批').length
})

onMounted(() => {
  fetchAllOaData()
})

const fetchAllOaData = async () => {
  try {
    const resSal = await axios.get('/api/assistant/salary')
    salarySlips.value = resSal.data || []

    const resPlas = await axios.get('/api/assistant/plaster')
    plasterSummary.value = resPlas.data || {}

    const resAppr = await axios.get('/api/assistant/approvals')
    approvals.value = resAppr.data || []

    const resTok = await axios.get('/api/assistant/token-stats')
    tokenStats.value = resTok.data || {}
  } catch (e) {
    console.error('加载 OA 数据失败:', e)
  }
}

const renderMarkdown = (text) => {
  return marked.parse(text || '')
}

const scrollToBottom = () => {
  nextTick(() => {
    if (scrollRef.value) {
      scrollRef.value.scrollTop = scrollRef.value.scrollHeight
    }
  })
}

const askQuery = (q) => {
  queryInput.value = q
  sendQuery()
}

const sendQuery = async () => {
  const text = queryInput.value.trim()
  if (!text || loading.value) return

  messageList.value.push({ role: 'user', content: text })
  queryInput.value = ''
  scrollToBottom()
  loading.value = true

  try {
    const res = await axios.post('/api/assistant/chat', { 
      message: text, 
      doctorId: currentDoctorId.value || 'DOC_1001' 
    })
    messageList.value.push({ role: 'assistant', content: res.data })
    await fetchAllOaData() // 刷新最新审批或 Token 数据
  } catch (e) {
    messageList.value.push({ role: 'assistant', content: '检索服务发生异常: ' + e.message })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

const handleApprove = async (id, status) => {
  try {
    await axios.post('/api/assistant/approval/process', {
      id,
      status,
      approver: `${currentDoctorName.value} (${currentDoctorTitle.value})`,
      comment: status === '已通过' ? '审核通过，已协调门诊轮值' : '不同意，请重新提交'
    })
    ElMessage.success(`审批单 OA${id} 已变更为【${status}】！`)
    await fetchAllOaData()
  } catch (e) {
    ElMessage.error('审批操作失败: ' + e.message)
  }
}
</script>

<style scoped>
.assistant-full-container {
  height: calc(100vh - 64px);
  background: #f1f5f9;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.oa-header-bar {
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  padding: 12px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-main {
  display: flex;
  align-items: center;
  gap: 12px;
}

.brand-icon {
  font-size: 32px;
}

.header-main h2 {
  font-size: 17px;
  color: #0f172a;
  margin: 0 0 4px 0;
}

.header-main .sub {
  font-size: 11.5px;
  color: #64748b;
}

.header-metrics {
  display: flex;
  gap: 20px;
}

.metric-item {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.m-label {
  font-size: 11px;
  color: #64748b;
}

.m-val {
  font-size: 16px;
  font-weight: 800;
}

.text-success { color: #10b981; }
.text-primary { color: #3b82f6; }
.text-warning { color: #f59e0b; }
.text-danger { color: #ef4444; }

.oa-content-grid {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.oa-subnav {
  width: 220px;
  background: #ffffff;
  border-right: 1px solid #e2e8f0;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.subnav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 14px;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.subnav-item:hover {
  background: #f8fafc;
  color: #3b82f6;
}

.subnav-item.active {
  background: #eff6ff;
  color: #2563eb;
  box-shadow: inset 0 0 0 1px #bfdbfe;
}

.badge-mini {
  position: absolute;
  right: 12px;
}

.oa-panel-body {
  flex: 1;
  background: #f8fafc;
  overflow-y: auto;
  padding: 16px;
}

.chat-workspace {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.quick-oa-queries {
  padding: 10px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.quick-oa-queries .label {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
}

.chat-messages-scroll {
  flex: 1;
  padding: 16px 20px;
  overflow-y: auto;
}

.oa-bubble {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.oa-bubble.user {
  flex-direction: row-reverse;
}

.oa-bubble .avatar {
  font-size: 24px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
}

.oa-bubble .content {
  max-width: 80%;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px 14px;
}

.oa-bubble.user .content {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.oa-bubble .sender {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 4px;
  font-weight: 600;
}

.loading-box {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #3b82f6;
  font-size: 12px;
}

.chat-input-bar {
  padding: 12px 16px;
  border-top: 1px solid #e2e8f0;
  display: flex;
  gap: 12px;
  background: #ffffff;
}

.data-panel {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 18px;
  min-height: 100%;
}

.panel-top {
  margin-bottom: 16px;
}

.panel-top h3 {
  font-size: 16px;
  color: #0f172a;
  margin: 0 0 4px 0;
}

.sub-text {
  font-size: 12px;
  color: #64748b;
}

.highlight-green {
  color: #10b981;
  font-weight: 700;
}

.salary-net {
  color: #dc2626;
  font-weight: 800;
  font-size: 14px;
}

.stats-cards-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14px;
  margin-bottom: 20px;
}

.stat-card {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 14px;
}

.sc-title {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.sc-val {
  font-size: 22px;
  font-weight: 800;
  color: #0f172a;
}

.sc-val .unit {
  font-size: 12px;
  font-weight: 500;
  color: #64748b;
}

.sc-tip {
  font-size: 11px;
  color: #10b981;
  margin-top: 4px;
}

.table-title-bar {
  font-weight: 700;
  font-size: 13px;
  color: #334155;
  margin-bottom: 10px;
}

.text-muted {
  color: #94a3b8;
  font-size: 12px;
}
</style>