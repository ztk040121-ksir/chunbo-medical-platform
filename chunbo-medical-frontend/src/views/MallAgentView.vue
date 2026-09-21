<template>
  <div class="mall-agent-container">
    <!-- 顶部标题栏 -->
    <div class="mall-header-bar">
      <div class="header-left">
        <div class="mall-logo">🛒</div>
        <div>
          <h2>春播商城 · 基于 RAG + 状态图的多智能体医药电商运营中台 (B2B)</h2>
          <span class="sub">LangGraph 状态图编排：Router Agent 意图分派 ➔ 角色差异化推荐 ➔ 自动阶梯议价 ➔ 订单一键落库</span>
        </div>
      </div>

      <!-- 角色画像切换器 -->
      <div class="role-selector-bar">
        <span class="role-label">当前咨询角色画像:</span>
        <el-radio-group v-model="selectedRole" size="small" @change="onRoleChange">
          <el-radio-button value="director">👨‍⚕️ 诊所主任 (看疗效/学术)</el-radio-button>
          <el-radio-button value="buyer">💼 采购主管 (看政策/返利)</el-radio-button>
          <el-radio-button value="cs">👩‍💼 门诊客服 (看资质/物流)</el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- 主体：左侧多智能体协同对话工作台，右侧选品货架与采购订单 -->
    <div class="mall-main-grid">
      <!-- 左侧：多智能体状态机协同问答 -->
      <section class="agent-chat-section">
        <div class="chat-card">
          <!-- 智能体状态流转动态看板 -->
          <div class="state-graph-banner">
            <div class="graph-title">
              <el-icon><Share /></el-icon>
              <span>LangGraph 状态图协同链路:</span>
            </div>
            <div class="node-steps">
              <div class="node-chip" :class="{ active: currentActiveAgent === 'ROUTER' || currentActiveAgent === '' }">
                🔀 Router Agent
              </div>
              <span class="arrow">➔</span>
              <div class="node-chip" :class="{ active: currentActiveAgent === 'RECOMMEND_AGENT' }">
                🌿 Recommend Agent
              </div>
              <span class="arrow">/</span>
              <div class="node-chip" :class="{ active: currentActiveAgent === 'CS_AGENT' }">
                👩‍💼 CS Agent
              </div>
              <span class="arrow">/</span>
              <div class="node-chip" :class="{ active: currentActiveAgent === 'BARGAIN_AGENT' }">
                🤝 Bargain Agent
              </div>
            </div>
          </div>

          <!-- 快捷协同问答 -->
          <div class="quick-prompts">
            <span class="label">💡 典型协同问答:</span>
            <el-button size="small" round @click="askQuery('我是诊所主任，推荐适合秋冬季节开展的特色贴敷控销产品')">
              🍂 选品推荐 (主任学术视角)
            </el-button>
            <el-button size="small" round @click="askQuery('我是采购主管，订购300盒通络贴能给多少阶梯折扣？帮我申请议价')">
              🤝 阶梯议价 (采购300盒9折)
            </el-button>
            <el-button size="small" round @click="askQuery('请问这些特色贴敷与基药的 GSP 首营资质齐全吗？支持退换货吗？')">
              📋 药事合规与退换资质
            </el-button>
          </div>

          <!-- 对话消息列表 -->
          <div class="chat-flow-scroll" ref="chatScrollRef">
            <div v-for="(msg, index) in messages" :key="index" class="msg-bubble-wrap" :class="msg.role">
              <div class="avatar">{{ msg.role === 'user' ? (selectedRole === 'director' ? '👨‍⚕️' : (selectedRole === 'buyer' ? '💼' : '👩‍💼')) : '🤖' }}</div>
              <div class="bubble-main">
                <div class="bubble-meta">
                  <span class="sender">{{ msg.role === 'user' ? '采购方咨询' : (msg.agentName || '春播商城多智能体编排中枢') }}</span>
                  <span class="time">{{ msg.time }}</span>
                </div>
                <div class="markdown-body" v-html="renderMarkdown(msg.content)"></div>

                <!-- 若包含议价方案草案，呈现采纳生成订单卡片 -->
                <div v-if="msg.bargainDraft" class="bargain-card-widget">
                  <div class="bc-header">
                    <span>🎉 议价方案已生成 (特批阶梯采购折扣)</span>
                    <el-tag size="small" type="success">特批核准</el-tag>
                  </div>
                  <div class="bc-body">
                    <div>采购商品：<b>{{ msg.bargainDraft.productName }}</b> × {{ msg.bargainDraft.quantity }} 盒</div>
                    <div>标价总计：¥{{ msg.bargainDraft.totalAmount }}，优惠减免：<span class="text-danger">-¥{{ msg.bargainDraft.discountAmount }}</span></div>
                    <div class="bc-final">最终实付金额：<span class="final-price">¥{{ msg.bargainDraft.finalAmount }}</span></div>
                    <div class="bc-notes">赠送方案：{{ msg.bargainDraft.notes }}</div>
                  </div>
                  <div class="bc-footer">
                    <el-button 
                      type="success" 
                      size="small" 
                      icon="ShoppingCart" 
                      :loading="creatingOrder"
                      @click="acceptBargainAndCreateOrder(msg.bargainDraft)"
                    >
                      采纳议价方案并一键生成正式采购订单 (入库 MySQL 8.0)
                    </el-button>
                  </div>
                </div>
              </div>
            </div>

            <div v-if="loading" class="msg-bubble-wrap assistant">
              <div class="avatar">🤖</div>
              <div class="bubble-main loading-main">
                <el-icon class="is-loading"><Loading /></el-icon>
                <span>LangGraph 状态图多智能体路由与 RAG 知识库检索中...</span>
              </div>
            </div>
          </div>

          <!-- 输入栏 -->
          <div class="chat-input-row">
            <el-input 
              v-model="inputQuery" 
              placeholder="输入药品选品咨询、药事法规问询或批量采购议价要求..." 
              @keyup.enter="sendChat"
            />
            <el-button type="primary" :loading="loading" @click="sendChat" icon="Promotion">协同咨询</el-button>
          </div>
        </div>
      </section>

      <!-- 右侧：B2B 控销选品货架与采购订单中心 -->
      <section class="mall-side-section">
        <!-- 控销药品货架 -->
        <div class="side-card mb-16">
          <div class="side-title">
            <el-icon><Goods /></el-icon>
            <span>B2B 控销选品货架 (MySQL 8.0 实时底账)</span>
          </div>

          <el-table :data="products" stripe style="width: 100%" size="small" max-height="240">
            <el-table-column prop="productName" label="控销商品名称" min-width="160">
              <template #default="scope">
                <b>{{ scope.row.productName }}</b>
                <div class="spec-text">{{ scope.row.specification }} · {{ scope.row.category }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="wholesalePrice" label="供货价" width="85">
              <template #default="scope">
                <span class="text-danger font-bold">¥{{ scope.row.wholesalePrice }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="profitRate" label="毛利率" width="80">
              <template #default="scope">
                <el-tag size="small" type="success">{{ scope.row.profitRate }}%</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="stockQty" label="控销库存" width="90">
              <template #default="scope">{{ scope.row.stockQty }} 盒</template>
            </el-table-column>
          </el-table>
        </div>

        <!-- B2B 采购订单履约流转 -->
        <div class="side-card">
          <div class="side-title">
            <el-icon><List /></el-icon>
            <span>B2B 采购订单履约中心</span>
            <el-button type="primary" link size="small" @click="loadMallData" class="ml-auto">刷新订单</el-button>
          </div>

          <el-table :data="orders" stripe style="width: 100%" size="small" max-height="250">
            <el-table-column prop="orderNo" label="采购单号" width="140" />
            <el-table-column prop="clinicName" label="采购诊所/机构" min-width="140" />
            <el-table-column prop="finalAmount" label="实付金额" width="95">
              <template #default="scope">
                <b class="text-danger">¥{{ scope.row.finalAmount }}</b>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="履约状态" width="85">
              <template #default="scope">
                <el-tag :type="scope.row.status === '已出库' ? 'info' : 'success'" size="small">
                  {{ scope.row.status }}
                </el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { marked } from 'marked'
import axios from 'axios'
import { ElMessage, ElNotification } from 'element-plus'

const selectedRole = ref('director')
const currentActiveAgent = ref('')
const inputQuery = ref('')
const loading = ref(false)
const creatingOrder = ref(false)
const chatScrollRef = ref(null)

const products = ref([])
const orders = ref([])

const messages = ref([
  {
    role: 'assistant',
    agentName: 'LangGraph 多智能体编排总线',
    time: '19:15',
    content: '尊敬的诊所伙伴！我是春播商城 B2B 多智能体电商运营中台。系统已接入 **LangGraph 状态图与 Spring AI 药品知识库 (RAG)**：\n- **🔀 Router Agent**：自主语义解析您的业务意图\n- **🌿 Recommend Agent**：面向【主任 / 采购 / 客服】差异化定制选品推荐话术\n- **🤝 Bargain Agent**：大宗采购阶梯买赠与智能议价\n- **👩‍💼 CS Agent**：GSP 首营资质与冷链物流答疑\n请切换上方角色画像或点击下方快捷问答体验协同工作流！'
  }
])

onMounted(() => {
  loadMallData()
})

const loadMallData = async () => {
  try {
    const resProd = await axios.get('/api/mall/products')
    products.value = resProd.data || []

    const resOrd = await axios.get('/api/mall/orders')
    orders.value = resOrd.data || []
  } catch (e) {
    console.error('加载商城数据失败:', e)
  }
}

const onRoleChange = () => {
  ElMessage.info(`已切换为【${selectedRole.value === 'director' ? '诊所主任' : (selectedRole.value === 'buyer' ? '采购主管' : '门诊客服')}】角色画像视角！`)
}

const renderMarkdown = (text) => {
  return marked.parse(text || '')
}

const scrollToBottom = () => {
  nextTick(() => {
    if (chatScrollRef.value) {
      chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight
    }
  })
}

const askQuery = (q) => {
  inputQuery.value = q
  sendChat()
}

const sendChat = async () => {
  const text = inputQuery.value.trim()
  if (!text || loading.value) return

  messages.value.push({
    role: 'user',
    content: text,
    time: new Date().toLocaleTimeString()
  })
  inputQuery.value = ''
  scrollToBottom()
  loading.value = true

  try {
    const res = await axios.post('/api/mall/chat', {
      message: text,
      role: selectedRole.value,
      sessionId: 'SESSION_MALL_' + Date.now()
    })

    if (res.data) {
      currentActiveAgent.value = res.data.targetAgent || 'RECOMMEND_AGENT'
      messages.value.push({
        role: 'assistant',
        agentName: res.data.targetAgent === 'BARGAIN_AGENT' ? '🤝 Bargain Agent (阶梯议价智能体)' : (res.data.targetAgent === 'CS_AGENT' ? '👩‍💼 Customer Service Agent (客服合规智能体)' : '🌿 Recommend Agent (角色选品智能体)'),
        time: new Date().toLocaleTimeString(),
        content: res.data.content,
        bargainDraft: res.data.bargainDraft
      })
    }
  } catch (e) {
    messages.value.push({
      role: 'assistant',
      agentName: '系统异常提示',
      time: new Date().toLocaleTimeString(),
      content: '多智能体协作流发生异常: ' + e.message
    })
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

const acceptBargainAndCreateOrder = async (draft) => {
  creatingOrder.value = true
  try {
    const payload = {
      clinicName: '春播第001社区卫生服务站',
      buyerName: selectedRole.value === 'director' ? '李文华 (全科主任)' : '陈建民 (采购主管)',
      totalAmount: draft.totalAmount,
      discountAmount: draft.discountAmount,
      finalAmount: draft.finalAmount,
      itemsJson: JSON.stringify([{ productName: draft.productName, quantity: draft.quantity, price: draft.wholesalePrice }]),
      notes: draft.notes
    }
    const res = await axios.post('/api/mall/order/create', payload)
    ElNotification({
      title: '采购订单生成成功',
      message: `单号: ${res.data.orderNo}，实付金额: ¥${res.data.finalAmount}，数据已永久写入 MySQL 8.0！`,
      type: 'success',
      duration: 6000
    })
    await loadMallData()
  } catch (e) {
    ElMessage.error('创建订单失败: ' + e.message)
  } finally {
    creatingOrder.value = false
  }
}
</script>

<style scoped>
.mall-agent-container {
  height: calc(100vh - 64px);
  background: #f1f5f9;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.mall-header-bar {
  background: #0f172a;
  color: #ffffff;
  padding: 12px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.1);
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mall-logo {
  font-size: 32px;
}

.header-left h2 {
  font-size: 16.5px;
  margin: 0 0 4px 0;
}

.header-left .sub {
  font-size: 11.5px;
  color: #94a3b8;
}

.role-selector-bar {
  display: flex;
  align-items: center;
  gap: 10px;
}

.role-label {
  font-size: 12px;
  color: #cbd5e1;
}

.mall-main-grid {
  flex: 1;
  display: grid;
  grid-template-columns: 1.15fr 1fr;
  gap: 16px;
  padding: 16px;
  overflow: hidden;
}

.agent-chat-section, .mall-side-section {
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.chat-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.state-graph-banner {
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  padding: 8px 16px;
  display: flex;
  align-items: center;
  gap: 12px;
}

.graph-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #334155;
}

.node-steps {
  display: flex;
  align-items: center;
  gap: 6px;
}

.node-chip {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 12px;
  background: #e2e8f0;
  color: #475569;
  font-weight: 600;
  transition: all 0.2s;
}

.node-chip.active {
  background: #3b82f6;
  color: #ffffff;
  box-shadow: 0 2px 6px rgba(59, 130, 246, 0.3);
}

.arrow {
  color: #94a3b8;
  font-size: 11px;
}

.quick-prompts {
  padding: 8px 16px;
  background: #ffffff;
  border-bottom: 1px solid #f1f5f9;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.quick-prompts .label {
  font-size: 11.5px;
  font-weight: 600;
  color: #64748b;
}

.chat-flow-scroll {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}

.msg-bubble-wrap {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}

.msg-bubble-wrap.user {
  flex-direction: row-reverse;
}

.avatar {
  font-size: 24px;
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #f1f5f9;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bubble-main {
  max-width: 82%;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px 14px;
}

.msg-bubble-wrap.user .bubble-main {
  background: #eff6ff;
  border-color: #bfdbfe;
}

.bubble-meta {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #64748b;
  margin-bottom: 4px;
}

.sender {
  font-weight: 700;
}

.markdown-body {
  font-size: 13px;
  line-height: 1.6;
  color: #1e293b;
}

.markdown-body :deep(h3), .markdown-body :deep(h4) {
  margin: 6px 0 4px 0;
  font-size: 13.5px;
}

.markdown-body :deep(p) {
  margin: 4px 0;
}

.loading-main {
  display: flex;
  align-items: center;
  gap: 8px;
  color: #3b82f6;
  font-size: 12px;
}

.bargain-card-widget {
  margin-top: 12px;
  background: #f0fdf4;
  border: 1px solid #86efac;
  border-radius: 8px;
  padding: 12px;
}

.bc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 700;
  font-size: 12.5px;
  color: #15803d;
  margin-bottom: 8px;
}

.bc-body {
  font-size: 12px;
  line-height: 1.6;
  color: #166534;
}

.bc-final {
  margin-top: 4px;
  font-size: 13px;
  font-weight: 700;
}

.final-price {
  font-size: 16px;
  color: #dc2626;
  font-weight: 800;
}

.bc-notes {
  font-size: 11px;
  color: #059669;
  margin-top: 2px;
}

.bc-footer {
  margin-top: 10px;
}

.chat-input-row {
  padding: 12px 16px;
  border-top: 1px solid #e2e8f0;
  display: flex;
  gap: 10px;
  background: #ffffff;
}

.side-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 14px;
}

.mb-16 {
  margin-bottom: 16px;
}

.side-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13.5px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 12px;
}

.ml-auto {
  margin-left: auto;
}

.spec-text {
  font-size: 11px;
  color: #94a3b8;
}

.text-danger { color: #ef4444; }
.font-bold { font-weight: 700; }
</style>