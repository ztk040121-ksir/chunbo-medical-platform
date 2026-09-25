<template>
  <div class="ai-config-panel">
    <!-- 顶部状态大卡 -->
    <div class="panel-header-card">
      <div class="header-left">
        <div class="header-badge">🤖 Spring AI Gateway</div>
        <h2 class="header-title">全平台 AI 大模型网关与热切换中枢</h2>
        <p class="header-desc">
          基于 Spring AI 标准架构构建，支持动态热加载模型客户端，实时落库持久化（sys_ai_config表）。支持在线大模型、私有化 Ollama 及离线应急引擎秒级切换，服务重启永不丢失配置。
        </p>
      </div>
      <div class="header-right">
        <div class="active-model-box">
          <div class="box-label">当前运行中的主模型</div>
          <div class="box-model-name">
            <span class="pulse-indicator"></span>
            {{ currentConfig.modelName || '加载中...' }}
          </div>
          <div class="box-provider">
            渠道：<b>{{ currentConfig.provider || '默认' }}</b>
            <span v-if="currentConfig.mockEnabled" class="mock-tag">离线模式</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 快捷切换预设渠道与测速 -->
    <div class="models-grid-section">
      <div class="section-title-row">
        <div class="title-text">✨ 已注册模型通道列表 (支持一键热切换)</div>
        <div class="title-actions">
          <el-button size="small" type="primary" plain @click="loadData" :loading="loading">
            🔄 刷新列表
          </el-button>
          <el-button size="small" type="success" @click="testActiveModel" :loading="testing">
            ⚡ 测试当前模型连通性
          </el-button>
        </div>
      </div>

      <div class="model-cards-container" v-loading="loading">
        <div 
          v-for="item in modelList" 
          :key="item.id" 
          class="model-card"
          :class="{ active: item.isActive === 1 }"
        >
          <div class="card-top">
            <div class="model-badge">
              <span class="provider-name">{{ item.providerName }}</span>
              <el-tag size="small" :type="item.isActive === 1 ? 'success' : 'info'" effect="dark">
                {{ item.isActive === 1 ? '当前激活' : '备用' }}
              </el-tag>
            </div>
            <h3 class="model-display-name">{{ item.modelName }}</h3>
            <div class="model-url" :title="item.baseUrl">{{ item.baseUrl }}</div>
          </div>

          <div class="card-meta">
            <div class="meta-item">
              <span class="meta-k">采样温度:</span>
              <span class="meta-v">{{ item.temperature }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-k">更新时间:</span>
              <span class="meta-v">{{ item.updateTime ? item.updateTime.substring(0, 16).replace('T', ' ') : '-' }}</span>
            </div>
          </div>

          <div class="card-bottom">
            <el-button 
              v-if="item.isActive !== 1" 
              type="primary" 
              size="default" 
              class="switch-btn"
              :loading="switchingId === item.id"
              @click="handleSwitchModel(item)"
            >
              🚀 切换为当前模型
            </el-button>
            <el-tag v-else type="success" size="large" class="active-badge-tag">
              ✅ 正在服务全院问诊与中台
            </el-tag>
          </div>
        </div>
      </div>
    </div>

    <!-- 自定义模型配置热更新表单 -->
    <div class="custom-edit-section">
      <div class="section-title-row">
        <div class="title-text">⚙️ 自定义参数调优与持久化保存</div>
      </div>

      <el-card shadow="never" class="edit-card">
        <el-form :model="editForm" label-position="top">
          <el-row :gutter="24">
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="服务商标识 (Provider Name)">
                <el-input v-model="editForm.provider" placeholder="如 OhMyGPT, Ollama, DashScope" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="大模型端点 (Base URL)">
                <el-input v-model="editForm.baseUrl" placeholder="如 https://api.ohmygpt.com" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="模型名称 (Model Identifier)">
                <el-input v-model="editForm.modelName" placeholder="如 deepseek-v4-flash, qwen-plus" />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="16">
              <el-form-item label="访问密钥 (API Key)">
                <el-input v-model="editForm.apiKey" show-password placeholder="sk-..." />
              </el-form-item>
            </el-col>
            <el-col :xs="24" :sm="12" :md="8">
              <el-form-item label="采样温度 (Temperature: 0.0 ~ 1.0)">
                <el-slider v-model="editForm.temperature" :min="0" :max="1" :step="0.05" show-input />
              </el-form-item>
            </el-col>
          </el-row>

          <div class="form-actions">
            <el-button type="info" plain @click="testCustomForm" :loading="testingCustom">
              🧪 测试此配置网络连通性
            </el-button>
            <el-button type="primary" @click="saveAndApplyConfig" :loading="saving">
              💾 保存并立即热应用（真实落库）
            </el-button>
          </div>
        </el-form>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElNotification } from 'element-plus'

const loading = ref(false)
const testing = ref(false)
const testingCustom = ref(false)
const saving = ref(false)
const switchingId = ref(null)

const currentConfig = ref({
  provider: '',
  baseUrl: '',
  apiKey: '',
  modelName: '',
  temperature: 0.3,
  mockEnabled: false
})

const editForm = ref({
  provider: '',
  baseUrl: '',
  apiKey: '',
  modelName: '',
  temperature: 0.3
})

const modelList = ref([])

const loadData = async () => {
  loading.value = true
  try {
    const [cfgRes, listRes] = await Promise.all([
      axios.get('/api/settings/ai'),
      axios.get('/api/settings/ai/list')
    ])
    if (cfgRes.data) {
      currentConfig.value = cfgRes.data
      editForm.value = {
        provider: cfgRes.data.provider || 'OhMyGPT',
        baseUrl: cfgRes.data.baseUrl || 'https://api.ohmygpt.com',
        apiKey: cfgRes.data.apiKey || '',
        modelName: cfgRes.data.modelName || 'deepseek-v4-flash',
        temperature: cfgRes.data.temperature || 0.3
      }
    }
    if (listRes.data && Array.isArray(listRes.data)) {
      modelList.value = listRes.data
    }
  } catch (e) {
    ElMessage.error('加载模型网关配置失败：' + (e.message || '网络异常'))
  } finally {
    loading.value = false
  }
}

const handleSwitchModel = async (item) => {
  switchingId.value = item.id
  try {
    const res = await axios.post(`/api/settings/ai/switch/${item.id}`)
    ElNotification({
      title: '🚀 大模型热切换成功！',
      message: `已平滑热切换至【${item.modelName}】（渠道：${item.providerName}），全院问诊与中台调度立即生效！`,
      type: 'success',
      duration: 5000
    })
    await loadData()
  } catch (e) {
    ElMessage.error('切换模型失败：' + (e.message || '网络异常'))
  } finally {
    switchingId.value = null
  }
}

const testActiveModel = async () => {
  testing.value = true
  try {
    const res = await axios.post('/api/settings/ai/test', currentConfig.value)
    if (res.data?.success) {
      ElNotification({
        title: '⚡ 连通性测试通过',
        message: `${res.data.message} (耗时 ${res.data.latency}ms)`,
        type: 'success',
        duration: 4000
      })
    } else {
      ElMessage.error(res.data?.message || '测试连接超时或未响应')
    }
  } catch (e) {
    ElMessage.error('测试异常：' + (e.message || '服务不可达'))
  } finally {
    testing.value = false
  }
}

const testCustomForm = async () => {
  if (!editForm.value.baseUrl || !editForm.value.modelName) {
    ElMessage.warning('请填写端点 URL 与模型名称！')
    return
  }
  testingCustom.value = true
  try {
    const res = await axios.post('/api/settings/ai/test', editForm.value)
    if (res.data?.success) {
      ElMessage.success(`测试连通成功！延迟 ${res.data.latency}ms`)
    } else {
      ElMessage.error(res.data?.message || '测试失败')
    }
  } catch (e) {
    ElMessage.error('测试异常：' + (e.message || '网络错误'))
  } finally {
    testingCustom.value = false
  }
}

const saveAndApplyConfig = async () => {
  if (!editForm.value.baseUrl || !editForm.value.modelName) {
    ElMessage.warning('请补充完整的大模型端点与模型型号！')
    return
  }
  saving.value = true
  try {
    await axios.post('/api/settings/ai', editForm.value)
    ElNotification({
      title: '💾 模型配置持久化成功！',
      message: `已真实落库并热重载 Spring AI Client，当前服务模型已更新为【${editForm.value.modelName}】！`,
      type: 'success',
      duration: 5000
    })
    await loadData()
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '网络异常'))
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.ai-config-panel {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 8px;
}

.panel-header-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(135deg, #1e293b 0%, #0f172a 100%);
  border-radius: 12px;
  padding: 24px;
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.header-badge {
  display: inline-block;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.5px;
  text-transform: uppercase;
  background: rgba(59, 130, 246, 0.2);
  color: #60a5fa;
  padding: 4px 10px;
  border-radius: 6px;
  border: 1px solid rgba(59, 130, 246, 0.3);
  margin-bottom: 8px;
}

.header-title {
  margin: 0 0 8px 0;
  font-size: 20px;
  font-weight: 700;
  color: #f8fafc;
}

.header-desc {
  margin: 0;
  font-size: 13px;
  line-height: 1.6;
  color: #94a3b8;
  max-width: 680px;
}

.active-model-box {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: 10px;
  padding: 14px 20px;
  text-align: right;
  min-width: 220px;
}

.box-label {
  font-size: 12px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.box-model-name {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  font-size: 17px;
  font-weight: 700;
  color: #38bdf8;
  margin-bottom: 4px;
}

.pulse-indicator {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 10px #10b981;
}

.box-provider {
  font-size: 12px;
  color: #cbd5e1;
}

.mock-tag {
  background: #f59e0b;
  color: #000;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 4px;
  margin-left: 6px;
  font-weight: bold;
}

.section-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.title-text {
  font-size: 15px;
  font-weight: 700;
  color: #1e293b;
}

.model-cards-container {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 16px;
}

.model-card {
  background: #ffffff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 18px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  transition: all 0.25s ease;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.model-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.08);
}

.model-card.active {
  border-color: #3b82f6;
  background: linear-gradient(180deg, #eff6ff 0%, #ffffff 100%);
  box-shadow: 0 0 0 1px #3b82f6;
}

.card-top {
  margin-bottom: 14px;
}

.model-badge {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.provider-name {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
}

.model-display-name {
  margin: 0 0 6px 0;
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.model-url {
  font-size: 12px;
  color: #94a3b8;
  font-family: monospace;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.card-meta {
  border-top: 1px dashed #e2e8f0;
  padding-top: 10px;
  margin-bottom: 16px;
  display: flex;
  justify-content: space-between;
  font-size: 12px;
}

.meta-k {
  color: #64748b;
  margin-right: 4px;
}

.meta-v {
  color: #0f172a;
  font-weight: 600;
}

.switch-btn {
  width: 100%;
}

.active-badge-tag {
  width: 100%;
  text-align: center;
  justify-content: center;
  font-weight: bold;
}

.edit-card {
  border-radius: 10px;
  border-color: #e2e8f0;
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 10px;
}
</style>
