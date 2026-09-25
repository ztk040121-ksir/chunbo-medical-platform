<template>
  <div class="ai-settings-container">
    <div class="settings-box">
      <!-- 顶部系统设置中心头部与大 Tab 切换 -->
      <div class="settings-header-card">
        <div class="shc-main">
          <div class="shc-title-group">
            <h2>⚙️ 春播万象云诊所 · 系统设置与中枢配置</h2>
            <span class="sub">集中管理 AI 大模型智能体网关、门诊会员权益体系、以及号源与过号排队调度规则</span>
          </div>
          <div class="shc-tabs">
            <div 
              class="shc-tab-btn" 
              :class="{ active: activeTab === 'ai' }"
              @click="activeTab = 'ai'"
            >
              🤖 AI 大模型网关
            </div>
            <div 
              class="shc-tab-btn" 
              :class="{ active: activeTab === 'member' }"
              @click="activeTab = 'member'"
            >
              💎 会员体系与折扣
            </div>
            <div 
              class="shc-tab-btn" 
              :class="{ active: activeTab === 'registration' }"
              @click="activeTab = 'registration'"
            >
              📋 挂号与过号规则
            </div>
          </div>
        </div>
      </div>

      <!-- ══════════════ TAB 1: AI 大模型服务网关与自定义模型 ══════════════ -->
      <div v-if="activeTab === 'ai'" class="settings-content-pane">
        <div class="pane-top-bar">
          <div>
            <div class="pane-title">AI 模型服务网关与动态管理中枢</div>
            <div class="pane-sub">支持在线大模型、开源本地模型（Ollama）、通义千问，以及添加自定义任何兼容 OpenAI 协议的模型接口</div>
          </div>
          <el-button type="primary" icon="Plus" @click="openAddCustomModelDialog">
            接入自定义大模型
          </el-button>
        </div>

        <!-- 当前可用模型列表（内置 + 自定义，可动态添加多个） -->
        <el-table :data="allProviders" stripe border class="model-list-table">
          <el-table-column label="模型" min-width="220">
            <template #default="scope">
              <div class="ml-name-cell">
                <span class="ml-icon">{{ scope.row.icon || '🤖' }}</span>
                <div>
                  <div class="ml-name">
                    {{ scope.row.name }}
                    <el-tag v-if="scope.row.isCustom" size="small" type="warning" effect="light">自定义</el-tag>
                    <el-tag v-if="scope.row.mock" size="small" type="info" effect="light">离线</el-tag>
                  </div>
                  <div class="ml-desc">{{ scope.row.desc || 'OpenAI 协议兼容接口' }}</div>
                </div>
              </div>
            </template>
          </el-table-column>
          <el-table-column prop="modelName" label="模型标识" width="180" />
          <el-table-column prop="baseUrl" label="服务端点" min-width="230" show-overflow-tooltip />
          <el-table-column label="状态" width="100" align="center">
            <template #default="scope">
              <el-tag v-if="configForm.provider === scope.row.id" type="success" size="small">当前启用</el-tag>
              <span v-else class="ml-inactive">未启用</span>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="170" align="center">
            <template #default="scope">
              <el-button
                v-if="configForm.provider !== scope.row.id"
                type="success"
                size="small"
                :loading="switchingId === scope.row.id"
                @click="enableProvider(scope.row)"
              >启用</el-button>
              <el-button
                v-if="scope.row.isCustom"
                type="danger"
                link
                size="small"
                @click="deleteCustomModel(scope.row.id)"
              >删除</el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 参数配置表单 -->
        <el-card class="form-card" shadow="never">
          <template #header>
            <div class="card-title">
              <div class="ct-left">
                <span>引擎连接参数设置</span>
                <span class="ct-current-name">（当前选中：{{ currentProviderDisplayName }}）</span>
              </div>
              <el-tag :type="configForm.mockEnabled ? 'info' : 'primary'">
                {{ configForm.mockEnabled ? '内网离线模式' : '真实大模型网关模式' }}
              </el-tag>
            </div>
          </template>

          <el-form :model="configForm" label-width="160px">
            <el-form-item label="API 服务端点 (BaseURL)">
              <el-input
                v-model="configForm.baseUrl"
                placeholder="如：https://api.ohmygpt.com 或 https://api.deepseek.com"
                style="width: 520px;"
              />
              <div class="field-hint">兼容标准 OpenAI 协议服务端点，例如 deepseek、moonshot、openrouter、ollama 等</div>
            </el-form-item>

            <el-form-item v-if="!isLocalProvider" label="访问密钥 (API Key)">
              <el-input
                v-model="configForm.apiKey"
                type="password"
                show-password
                placeholder="sk-..."
                style="width: 520px;"
              />
            </el-form-item>
            <el-form-item v-else label="访问密钥 (API Key)">
              <el-tag type="info">本地模型无需配置密钥，留空即可</el-tag>
            </el-form-item>

            <el-form-item label="模型名称 (Model)">
              <el-input 
                v-model="configForm.modelName" 
                placeholder="如：gpt-4o-mini、deepseek-chat、qwen-plus" 
                style="width: 360px;"
              />
            </el-form-item>

            <el-form-item label="温度系数 (Temperature)">
              <el-slider v-model="configForm.temperature" :min="0" :max="1" :step="0.1" show-input style="width: 360px;" />
            </el-form-item>

            <!-- 修复按钮排布：一键连通性测试 与 保存配置并立即热重载 横向水平并排 -->
            <el-form-item label="操作与生效">
              <div class="form-actions-horizontal">
                <el-button type="warning" size="large" icon="Connection" :loading="testing" @click="testConnection">
                  一键连通性测试
                </el-button>
                <el-button type="primary" size="large" icon="Check" :loading="saving" @click="saveConfig">
                  保存配置并立即热重载
                </el-button>
                <div v-if="testResult" class="test-result-badge" :class="{ ok: testResult.success, fail: !testResult.success }">
                  {{ testResult.message }} (延迟: {{ testResult.latencyMs }}ms)
                </div>
              </div>
            </el-form-item>
          </el-form>
        </el-card>

        <!-- 弹窗：接入自定义大模型 -->
        <el-dialog 
          v-model="showAddCustomModal" 
          title="接入自定义 AI 大模型" 
          width="560px" 
          destroy-on-close
          class="custom-glass-dialog"
        >
          <div class="custom-modal-tip">
            支持接入任何兼容 OpenAI 协议的国内外大语言模型，配置后将自动同步至门诊 AI 临床助手的模型切换菜单。
          </div>
          <el-form :model="customModelForm" label-width="140px" style="margin-top: 16px;">
            <el-form-item label="模型展示名称" required>
              <el-input v-model="customModelForm.name" placeholder="如：DeepSeek-V3 / Kimi / Claude 3.5" />
            </el-form-item>
            <el-form-item label="服务端点 (BaseURL)" required>
              <el-input v-model="customModelForm.baseUrl" placeholder="如：https://api.deepseek.com" />
            </el-form-item>
            <el-form-item label="访问密钥 (API Key)">
              <el-input v-model="customModelForm.apiKey" type="password" show-password placeholder="sk-...（本地 Ollama 等无需密钥可留空）" />
            </el-form-item>
            <el-form-item label="模型标识 (Model)" required>
              <el-input v-model="customModelForm.modelName" placeholder="如：deepseek-chat、moonshot-v1-8k" />
            </el-form-item>
            <el-form-item label="描述说明">
              <el-input v-model="customModelForm.desc" placeholder="如：深度求索通用推理与对话大模型" />
            </el-form-item>
          </el-form>
          <template #footer>
            <div class="dialog-footer">
              <el-button @click="showAddCustomModal = false">取消</el-button>
              <el-button type="primary" @click="confirmAddCustomModel">确认添加并启用</el-button>
            </div>
          </template>
        </el-dialog>
      </div>

      <!-- ══════════════ TAB 2: 会员体系与折扣设置 ══════════════ -->
      <div v-else-if="activeTab === 'member'" class="settings-content-pane">
        <div class="pane-top-bar">
          <div>
            <div class="pane-title">💎 诊所会员体系与折扣权益管理</div>
            <div class="pane-sub">配置各等级会员折扣率、储值门槛与专属权益，调整后全局门诊开方、特色治疗与划价收银实时生效</div>
          </div>
          <div class="pane-top-actions">
            <el-button type="primary" icon="Plus" @click="openAddTierDialog">
              + 添加会员类型
            </el-button>
            <el-button type="success" icon="Check" @click="saveMemberTiersConfig">
              保存会员折扣设置
            </el-button>
          </div>
        </div>

        <!-- 会员类型卡片列表 -->
        <div class="tiers-grid">
          <div 
            v-for="tier in memberTiers" 
            :key="tier.id" 
            class="tier-card"
            :class="{ 'tier-card-system': !tier.isCustom }"
          >
            <div class="tier-card-header">
              <div class="tier-title-wrap">
                <span class="tier-icon">{{ tier.icon || '🏅' }}</span>
                <span class="tier-name">{{ tier.name }}</span>
              </div>
              <div class="tier-header-tags">
                <el-tag v-if="tier.isCustom" size="small" type="warning" effect="plain">自定义类型</el-tag>
                <el-tag v-else size="small" type="info" effect="plain">系统预设</el-tag>
                <el-button 
                  v-if="tier.isCustom" 
                  type="danger" 
                  link 
                  size="small" 
                  @click="deleteMemberTier(tier.id)"
                  title="删除该会员类型"
                >
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </div>

            <div class="tier-card-body">
              <div class="tier-field-row">
                <span class="tfr-label">门诊全单折扣：</span>
                <div class="tfr-val discount-val-wrap">
                  <el-input-number 
                    v-model="tier.discount" 
                    :min="0.1" 
                    :max="1.0" 
                    :step="0.05" 
                    :precision="2"
                    size="small" 
                    style="width: 110px;"
                    @change="onDiscountChange(tier)"
                  />
                  <span class="discount-preview-chip">
                    {{ formatDiscountChip(tier.discount) }}
                  </span>
                </div>
              </div>

              <div class="tier-field-row">
                <span class="tfr-label">建议储值门槛：</span>
                <div class="tfr-val">
                  <el-input-number 
                    v-model="tier.minRecharge" 
                    :min="0" 
                    :max="50000" 
                    :step="100" 
                    size="small" 
                    style="width: 120px;" 
                  /> 元
                </div>
              </div>

              <div class="tier-field-row align-top">
                <span class="tfr-label">专属权益说明：</span>
                <div class="tfr-val">
                  <el-input 
                    v-model="tier.desc" 
                    type="textarea" 
                    :rows="2" 
                    placeholder="说明享受的项目折扣及服务权益" 
                    size="small" 
                  />
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 弹窗：新增会员类型 -->
        <el-dialog 
          v-model="showAddTierModal" 
          title="新增诊所会员类型" 
          width="520px" 
          destroy-on-close
          class="custom-glass-dialog"
        >
          <el-form :model="newTierForm" label-width="120px">
            <el-form-item label="会员类型名称" required>
              <el-input v-model="newTierForm.name" placeholder="如：儿科关爱会员、员工亲友卡" />
            </el-form-item>
            <el-form-item label="享受折扣率" required>
              <div style="display: flex; align-items: center; gap: 8px;">
                <el-input-number v-model="newTierForm.discount" :min="0.1" :max="1.0" :step="0.05" :precision="2" />
                <span class="discount-preview-chip">{{ formatDiscountChip(newTierForm.discount) }}</span>
              </div>
            </el-form-item>
            <el-form-item label="建议储值门槛">
              <el-input-number v-model="newTierForm.minRecharge" :min="0" :max="50000" :step="100" /> 元
            </el-form-item>
            <el-form-item label="权益说明">
              <el-input v-model="newTierForm.desc" type="textarea" :rows="3" placeholder="如：包含儿科贴敷全单85折，每年免费中药体质辨识1次" />
            </el-form-item>
          </el-form>
          <template #footer>
            <div class="dialog-footer">
              <el-button @click="showAddTierModal = false">取消</el-button>
              <el-button type="primary" @click="confirmAddTier">确认新增并保存</el-button>
            </div>
          </template>
        </el-dialog>
      </div>

      <!-- ══════════════ TAB 3: 挂号策略与过号规则设置 ══════════════ -->
      <div v-else-if="activeTab === 'registration'" class="settings-content-pane">
        <div class="pane-top-bar">
          <div>
            <div class="pane-title">📋 门诊挂号策略与排队过号规则</div>
            <div class="pane-sub">修改后系统自动即时保存，无需手动提交，并即时同步至云端与门诊接诊排队中心</div>
          </div>
        </div>

        <el-card class="form-card" shadow="never">
          <el-form :model="clinicSettings" label-width="180px">
            <div class="section-divider-title">⏰ 核心过号与超时规则（叫号机制调度）</div>

            <el-form-item label="叫号超时自动过号">
              <el-switch 
                v-model="clinicSettings.autoExpireOnTimeout" 
                active-text="叫号后超出等待时限未到诊室，系统自动标记为【已过号】" 
              />
            </el-form-item>

            <el-form-item label="叫号超时等待时限">
              <el-input-number 
                v-model="clinicSettings.expireTimeoutMinutes" 
                :min="2" 
                :max="60" 
                style="width: 140px; margin-right: 8px;" 
              />
              <span class="unit-desc">分钟（推荐 5~15 分钟，医生叫号后开始计时，超时未接诊将自动释放叫号并转入过号名单）</span>
            </el-form-item>

            <el-form-item label="过号自动顺延规则">
              <el-switch 
                v-model="clinicSettings.allowRefund" 
                active-text="过号患者前来就诊时，自动顺延推迟 2 位呼叫" 
              />
            </el-form-item>

            <el-form-item label="过号患者重新排队">
              <el-radio-group v-model="clinicSettings.expireRequeueMode">
                <el-radio value="scan">允许扫码或自助机重新激活排队</el-radio>
                <el-radio value="desk">必须到分诊前台人工激活</el-radio>
              </el-radio-group>
            </el-form-item>

            <div class="section-divider-title" style="margin-top: 24px;">🩺 基础挂号与号源开放</div>

            <el-form-item label="开放线上微信预约">
              <el-switch v-model="clinicSettings.enableOnlineReg" active-text="启用微信小程序端预约挂号" />
            </el-form-item>

            <el-form-item label="现场排队叫号机制">
              <el-radio-group v-model="clinicSettings.sortMode">
                <el-radio value="sign">按签到到达先后顺序</el-radio>
                <el-radio value="appointment">严格按预约挂号序号</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="就诊前必须签到">
              <el-radio-group v-model="clinicSettings.signPolicy">
                <el-radio value="required">必须到店扫码或前台签到才能叫号</el-radio>
                <el-radio value="auto">挂号成功自动直接进入待诊队列</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="提前放号天数">
              <el-input-number v-model="clinicSettings.advanceDays" :min="1" :max="30" /> 天
            </el-form-item>

            <el-form-item label="复诊免挂号费天数">
              <el-input-number v-model="clinicSettings.freeReturnDays" :min="0" :max="14" /> 天（同医生看报告免诊查费）
            </el-form-item>
          </el-form>
        </el-card>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Delete, Check, Plus, Connection } from '@element-plus/icons-vue'
import { getAiConfig, updateAiConfig, testAiConfig } from '../api/medical'

// ── Tab 切换 ──
const activeTab = ref('ai')

// ── TAB 1: AI 模型管理 ──
const builtInProviders = [
  {
    id: 'ohmygpt',
    name: 'OhMyGPT (在线真实大模型)',
    desc: '已接入用户提供的真实 OpenAI 兼容网关，搭载 gpt-4o-mini',
    icon: '🌐',
    baseUrl: 'https://api.ohmygpt.com',
    modelName: 'gpt-4o-mini',
    mock: false
  },
  {
    id: 'ollama',
    name: '本地 Ollama (开源私有化部署)',
    desc: '支持内网本地部署的 Qwen2.5、Llama3 等本地大模型',
    icon: '🦙',
    baseUrl: 'http://localhost:11434',
    modelName: 'qwen2.5:7b',
    mock: false
  },
  {
    id: 'dashscope',
    name: '阿里 DashScope (通义千问)',
    desc: '阿里云百炼平台兼容模式，搭载 qwen-plus 医疗问诊模型',
    icon: '☁️',
    baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode',
    modelName: 'qwen-plus',
    mock: false
  },
  {
    id: 'mock',
    name: '春播万象内网离线 Mock',
    desc: '断网或演示模式，使用内置医疗诊疗专家规则库',
    icon: '⚡',
    baseUrl: 'http://localhost:8080/mock',
    modelName: 'chunbo-med-rule-engine',
    mock: true
  }
]

const customProviders = ref([])

const loadCustomProviders = () => {
  try {
    const raw = localStorage.getItem('chunbo_custom_ai_models')
    if (raw) {
      customProviders.value = JSON.parse(raw)
    }
  } catch (e) {}
}

const saveCustomProviders = () => {
  try {
    localStorage.setItem('chunbo_custom_ai_models', JSON.stringify(customProviders.value))
    window.dispatchEvent(new CustomEvent('ai-models-updated'))
  } catch (e) {}
}

const allProviders = computed(() => {
  return [...builtInProviders, ...customProviders.value]
})

const configForm = ref({
  provider: 'ohmygpt',
  baseUrl: 'https://api.ohmygpt.com',
  apiKey: 'sk-1FEAUBAdC6ee71Eaf9a3T3BLbkFJ6756Bd2A1B6B40B8aa77',
  modelName: 'gpt-4o-mini',
  temperature: 0.3,
  mockEnabled: false
})

const currentProviderDisplayName = computed(() => {
  const p = allProviders.value.find(x => x.id === configForm.value.provider)
  return p ? p.name : configForm.value.provider
})

const isCurrentCustom = computed(() => {
  return customProviders.value.some(x => x.id === configForm.value.provider)
})

// 本地/离线模型（Ollama、Mock）无需 API Key
const isLocalProvider = computed(() => {
  const id = configForm.value.provider
  return id === 'ollama' || id === 'mock' || configForm.value.mockEnabled === true
})

const testing = ref(false)
const saving = ref(false)
const testResult = ref(null)
// 正在执行「启用切换」的模型 id（按钮 loading）
const switchingId = ref(null)

const selectProvider = (provider) => {
  configForm.value.provider = provider.id
  configForm.value.baseUrl = provider.baseUrl
  configForm.value.modelName = provider.modelName
  if (provider.apiKey) configForm.value.apiKey = provider.apiKey
  else if (provider.id === 'ollama' || provider.id === 'mock') configForm.value.apiKey = ''
  configForm.value.mockEnabled = !!provider.mock
  testResult.value = null
}

/** 启用模型 = 填充参数 + 立即保存热重载（真正完成后端模型切换） */
const enableProvider = async (provider) => {
  selectProvider(provider)
  switchingId.value = provider.id
  try {
    await saveConfigInternal()
    ElMessage.success('已启用并切换至【' + provider.name + '】模型，网关热重载生效！')
  } finally {
    switchingId.value = null
  }
}

const testConnection = async () => {
  testing.value = true
  testResult.value = null
  try {
    const res = await testAiConfig(configForm.value)
    testResult.value = res.data
    if (res.data && res.data.success) {
      ElMessage.success('通道测试成功，模型连接正常！')
    } else {
      ElMessage.error('测试失败: ' + (res.data ? res.data.message : '连接超时'))
    }
  } catch (e) {
    testResult.value = { success: false, message: e.message, latencyMs: 0 }
    ElMessage.error('测试失败: ' + e.message)
  } finally {
    testing.value = false
  }
}

const saveConfigInternal = async () => {
  await updateAiConfig(configForm.value)
  // 如果当前选中的是自定义模型，同步更新其在 customProviders 中的字段
  const customIdx = customProviders.value.findIndex(x => x.id === configForm.value.provider)
  if (customIdx !== -1) {
    customProviders.value[customIdx].baseUrl = configForm.value.baseUrl
    customProviders.value[customIdx].apiKey = configForm.value.apiKey
    customProviders.value[customIdx].modelName = configForm.value.modelName
    saveCustomProviders()
  }
  window.dispatchEvent(new CustomEvent('ai-models-updated'))
}

const saveConfig = async () => {
  saving.value = true
  try {
    await saveConfigInternal()
    ElMessage.success('AI 模型中枢配置已热重载生效！已同步至门诊助手')
  } catch (e) {
    ElMessage.error('保存失败: ' + e.message)
  } finally {
    saving.value = false
  }
}

// 添加自定义模型
const showAddCustomModal = ref(false)
const customModelForm = ref({
  name: '',
  baseUrl: '',
  apiKey: '',
  modelName: '',
  desc: ''
})

const openAddCustomModelDialog = () => {
  customModelForm.value = {
    name: '',
    baseUrl: 'https://api.deepseek.com',
    apiKey: '',
    modelName: 'deepseek-chat',
    desc: '自定义 OpenAI 兼容接口'
  }
  showAddCustomModal.value = true
}

const confirmAddCustomModel = async () => {
  if (!customModelForm.value.name || !customModelForm.value.baseUrl || !customModelForm.value.modelName) {
    ElMessage.warning('请填写完整的模型名称、BaseURL与模型标识')
    return
  }
  const id = 'custom_' + Date.now().toString().slice(-6)
  const newModel = {
    id,
    name: customModelForm.value.name,
    desc: customModelForm.value.desc || '自定义大语言模型接口',
    icon: '🔮',
    baseUrl: customModelForm.value.baseUrl,
    apiKey: customModelForm.value.apiKey,
    modelName: customModelForm.value.modelName,
    mock: false,
    isCustom: true
  }
  customProviders.value.push(newModel)
  saveCustomProviders()
  showAddCustomModal.value = false
  selectProvider(newModel)
  await saveConfig()
  ElMessage.success('已成功接入自定义大模型【' + newModel.name + '】，并已自动设为当前模型！')
}

const deleteCustomModel = (id) => {
  ElMessageBox.confirm('确认删除该自定义 AI 模型配置？', '提示', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  }).then(() => {
    customProviders.value = customProviders.value.filter(x => x.id !== id)
    saveCustomProviders()
    if (configForm.value.provider === id) {
      selectProvider(builtInProviders[0])
      saveConfig()
    }
    ElMessage.success('自定义模型已删除')
  })
}

// ── TAB 2: 会员体系与折扣设置 ──
const defaultMemberTiers = [
  {
    id: 'tier_chronic',
    name: '慢病签约会员',
    discount: 0.90,
    minRecharge: 200,
    desc: '签约家庭医生、慢病长程管理患者，享受特色穴位贴敷与中西药配伍专属优惠',
    icon: '🩺',
    isCustom: false
  },
  {
    id: 'tier_vip',
    name: 'VIP尊享会员',
    discount: 0.80,
    minRecharge: 1000,
    desc: '高净值健康管理专属会员，全院特色理疗与药品全单8折尊享',
    icon: '👑',
    isCustom: false
  },
  {
    id: 'tier_elder',
    name: '银龄颐养会员',
    discount: 0.85,
    minRecharge: 500,
    desc: '65周岁以上老年长者专享，重点倾斜骨痛贴敷、中药养生调理',
    icon: '👵',
    isCustom: false
  },
  {
    id: 'tier_family',
    name: '家庭附属卡',
    discount: 0.85,
    minRecharge: 0,
    desc: '主卡直系亲属共享账户余额与会员专属折扣',
    icon: '👨‍👩‍👧',
    isCustom: false
  },
  {
    id: 'tier_regular',
    name: '普通居民',
    discount: 1.00,
    minRecharge: 0,
    desc: '标准挂号与常规诊疗收费，不享受会员额外折扣',
    icon: '👤',
    isCustom: false
  }
]

const memberTiers = ref([])

const loadMemberTiers = () => {
  try {
    const raw = localStorage.getItem('chunbo_member_tiers_config')
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed) && parsed.length > 0) {
        memberTiers.value = parsed
        return
      }
    }
  } catch (e) {}
  memberTiers.value = JSON.parse(JSON.stringify(defaultMemberTiers))
}

const saveMemberTiersConfig = () => {
  try {
    localStorage.setItem('chunbo_member_tiers_config', JSON.stringify(memberTiers.value))
    window.dispatchEvent(new CustomEvent('member-tiers-updated', { detail: memberTiers.value }))
    ElMessage.success('会员折扣体系已保存并全局生效！门诊工作台与收银台已同步更新')
  } catch (e) {
    ElMessage.error('保存失败: ' + e.message)
  }
}

const formatDiscountChip = (val) => {
  const n = Number(val) || 1.0
  if (n >= 1.0) return '无折扣 (10折)'
  const fold = (n * 10).toFixed(1).replace('.0', '')
  return fold + '折 专享'
}

const onDiscountChange = (tier) => {
  // discount change callback
}

// 新增会员类型
const showAddTierModal = ref(false)
const newTierForm = ref({
  name: '',
  discount: 0.85,
  minRecharge: 300,
  desc: ''
})

const openAddTierDialog = () => {
  newTierForm.value = {
    name: '',
    discount: 0.85,
    minRecharge: 300,
    desc: ''
  }
  showAddTierModal.value = true
}

const confirmAddTier = () => {
  if (!newTierForm.value.name) {
    ElMessage.warning('请输入会员类型名称')
    return
  }
  const id = 'tier_custom_' + Date.now().toString().slice(-6)
  memberTiers.value.push({
    id,
    name: newTierForm.value.name,
    discount: Number(newTierForm.value.discount) || 0.9,
    minRecharge: Number(newTierForm.value.minRecharge) || 0,
    desc: newTierForm.value.desc || '自定义会员专属特惠',
    icon: '✨',
    isCustom: true
  })
  saveMemberTiersConfig()
  showAddTierModal.value = false
  ElMessage.success('已成功新增会员类型【' + newTierForm.value.name + '】！')
}

const deleteMemberTier = (id) => {
  ElMessageBox.confirm('确认删除该会员类型？', '提示', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  }).then(() => {
    memberTiers.value = memberTiers.value.filter(x => x.id !== id)
    saveMemberTiersConfig()
    ElMessage.success('会员类型已删除')
  })
}

// ── TAB 3: 挂号与过号规则设置 ──
const defaultClinicSettings = {
  enableOnlineReg: true,
  sortMode: 'sign',
  signPolicy: 'required',
  advanceDays: 7,
  freeReturnDays: 3,
  allowRefund: true,
  autoExpireOnTimeout: true,
  expireTimeoutMinutes: 10,
  expireRequeueMode: 'scan'
}

const loadClinicSettings = () => {
  try {
    const raw = localStorage.getItem('chunbo_clinic_settings')
    if (raw) {
      return { ...defaultClinicSettings, ...JSON.parse(raw) }
    }
  } catch (e) {}
  return { ...defaultClinicSettings }
}

const clinicSettings = ref(loadClinicSettings())

watch(clinicSettings, (newVal) => {
  try {
    localStorage.setItem('chunbo_clinic_settings', JSON.stringify(newVal))
    window.dispatchEvent(new CustomEvent('clinic-settings-updated', { detail: newVal }))
    ElMessage.success({
      message: '挂号与过号调度规则已默认自动保存并实时生效！',
      grouping: true,
      duration: 1800
    })
  } catch (e) {}
}, { deep: true })

onMounted(async () => {
  loadCustomProviders()
  loadMemberTiers()
  try {
    const res = await getAiConfig()
    if (res.data) {
      configForm.value = { ...configForm.value, ...res.data }
    }
  } catch (e) {}
})
</script>

<style scoped>
.ai-settings-container {
  padding: 24px;
  background: #f1f5f9;
  min-height: calc(100vh - 64px);
  display: flex;
  justify-content: center;
}

.settings-box {
  width: 100%;
  max-width: 1080px;
}

/* 顶部卡片与 Tab */
.settings-header-card {
  background: #ffffff;
  border-radius: 14px;
  padding: 20px 24px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.04);
  border: 1px solid #e2e8f0;
  margin-bottom: 20px;
}

.shc-title-group h2 {
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 6px 0;
}

.sub {
  font-size: 13px;
  color: #64748b;
}

.shc-tabs {
  display: flex;
  gap: 12px;
  margin-top: 18px;
  border-bottom: 1px solid #f1f5f9;
  padding-bottom: 10px;
}

.shc-tab-btn {
  padding: 9px 20px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 700;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
}

.shc-tab-btn:hover {
  background: #ecfdf5;
  color: #059669;
  border-color: #a7f3d0;
}

.shc-tab-btn.active {
  background: #10b981;
  color: #ffffff;
  border-color: #10b981;
  box-shadow: 0 4px 10px rgba(16, 185, 129, 0.25);
}

/* 面板通用 */
.settings-content-pane {
  animation: fadeIn 0.25s ease-out;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

.pane-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding: 0 4px;
}

.pane-title {
  font-size: 17px;
  font-weight: 800;
  color: #1e293b;
}

.pane-sub {
  font-size: 12.5px;
  color: #64748b;
  margin-top: 3px;
}

.pane-top-actions {
  display: flex;
  gap: 10px;
}

/* AI 网关模型列表 */
.model-list-table {
  margin-bottom: 20px;
  border-radius: 10px;
}

.ml-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.ml-icon {
  font-size: 24px;
  line-height: 1;
}

.ml-name {
  font-weight: 700;
  font-size: 13.5px;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 6px;
}

.ml-desc {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.ml-inactive {
  font-size: 12px;
  color: #94a3b8;
}

.form-card {
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
}

.card-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 700;
  color: #1e293b;
}

.ct-left {
  display: flex;
  align-items: center;
  gap: 6px;
}

.ct-current-name {
  font-size: 13px;
  color: #059669;
  font-weight: 600;
}

.field-hint {
  font-size: 11.5px;
  color: #94a3b8;
  margin-top: 4px;
}

/* 横向并排按钮 */
.form-actions-horizontal {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.test-result-badge {
  font-size: 12.5px;
  padding: 6px 14px;
  border-radius: 6px;
  font-weight: 600;
}

.test-result-badge.ok {
  background: #ecfdf5;
  color: #059669;
  border: 1px solid #a7f3d0;
}

.test-result-badge.fail {
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

.custom-modal-tip {
  font-size: 13px;
  color: #64748b;
  background: #f8fafc;
  padding: 10px 14px;
  border-radius: 6px;
  border-left: 3px solid #3b82f6;
}

/* 会员体系卡片 */
.tiers-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}

.tier-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  padding: 16px 20px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  transition: all 0.2s;
}

.tier-card:hover {
  border-color: #3b82f6;
  box-shadow: 0 4px 14px rgba(59, 130, 246, 0.08);
}

.tier-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
  padding-bottom: 10px;
  border-bottom: 1px dashed #e2e8f0;
}

.tier-title-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tier-icon {
  font-size: 20px;
}

.tier-name {
  font-size: 15px;
  font-weight: 800;
  color: #1e293b;
}

.tier-header-tags {
  display: flex;
  align-items: center;
  gap: 6px;
}

.tier-field-row {
  display: flex;
  align-items: center;
  margin-bottom: 10px;
}

.tier-field-row.align-top {
  align-items: flex-start;
}

.tfr-label {
  width: 105px;
  font-size: 13px;
  color: #64748b;
  font-weight: 600;
}

.tfr-val {
  flex: 1;
  font-size: 13px;
  color: #1e293b;
}

.discount-val-wrap {
  display: flex;
  align-items: center;
  gap: 10px;
}

.discount-preview-chip {
  background: #fef2f2;
  color: #dc2626;
  font-size: 12px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 4px;
  border: 1px solid #fecaca;
}

/* 挂号规则表单 */
.section-divider-title {
  font-size: 15px;
  font-weight: 800;
  color: #0f172a;
  padding-bottom: 8px;
  margin-bottom: 16px;
  border-bottom: 2px solid #f1f5f9;
}

.unit-desc {
  font-size: 12px;
  color: #64748b;
}
</style>
