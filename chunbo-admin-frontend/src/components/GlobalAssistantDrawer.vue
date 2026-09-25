<script setup>
import { ref, computed, nextTick, watch, onMounted, onUnmounted } from 'vue'
import axios from 'axios'
import { marked } from 'marked'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Clock, Delete, Picture, Microphone } from '@element-plus/icons-vue'

const props = defineProps({
  visible: Boolean,
  currentUserRole: String,
  currentUserName: String,
  currentUserStaffId: String,
  currentRoleLabel: String
})

const emit = defineEmits(['update:visible'])

const drawerVisible = computed({
  get: () => props.visible,
  set: (val) => emit('update:visible', val)
})

// ── 多会话管理与持久化记忆隔离 ──
const showHistoryModal = ref(false)
const adminSessionList = ref([])
const currentAdminSessionId = ref('')

const activeSessionTitle = computed(() => {
  const cur = adminSessionList.value.find(s => s.id === currentAdminSessionId.value)
  if (!cur) return '全中台运营调度中枢'
  return cur.title.length > 16 ? cur.title.substring(0, 16) + '...' : cur.title
})

const parseTime = (t) => {
  if (!t) return 0
  if (typeof t === 'number') return t
  let s = String(t).trim()
  if (/^\d{2}-\d{2} \d{2}:\d{2}/.test(s)) {
    s = new Date().getFullYear() + '-' + s.replace(' ', 'T')
  } else {
    s = s.replace(' ', 'T')
  }
  let ts = new Date(s).getTime()
  if (isNaN(ts)) return 0
  if (ts > Date.now() + 86400000) ts -= 365 * 86400000
  return ts
}

const groupedSessions = computed(() => {
  const groups = { today: [], week: [], month: [], earlier: [] }
  const now = new Date()
  const dayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const weekStart = dayStart - ((now.getDay() + 6) % 7) * 86400000
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime()
  for (const s of adminSessionList.value) {
    const t = parseTime(s.updatedAt || s.createdAt)
    if (t >= dayStart) groups.today.push(s)
    else if (t >= weekStart) groups.week.push(s)
    else if (t >= monthStart) groups.month.push(s)
    else groups.earlier.push(s)
  }
  return groups
})

const formatNowTime = () => {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const date = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${m}-${date} ${h}:${min}`
}

const getStorageKey = () => 'chunbo_admin_ai_sessions_' + (props.currentUserStaffId || 'ADM_0001')

const getDefaultWelcome = () => [
  {
    role: 'assistant',
    content: `您好 **${props.currentUserName || '管理员'}**！我是 **春播云管理系统 · 综合运营与商城调度指挥官**。\n当前操作权限：【${props.currentRoleLabel || '系统管理员'}】（工号: \`${props.currentUserStaffId || 'ADM_0001'}\`）。\n\n本调度中枢数据严格受限于【春播云管理系统】与【春播商城】，不涉及云门诊临床诊疗业务。您可以随时通过自然语言发起调度：\n\n` +
      `- 📦 **商城订单履约出库**：输入「商城待发货订单清单」「查询所有订单」或「给李先生发货」（严格只查商城 C 端 B2C 订单，与后台大盘完全一致）\n` +
      `- 🏷️ **商城商品与进销存**：输入「给稳健医用口罩调价到 13.5」「口罩下架」「商品补货 200 件」\n` +
      `- 👤 **商城注册用户管理**：输入「注册商城新用户」「查询陈素芬的订单」「商城用户统计」\n` +
      `- 💰 **薪酬与绩效中枢**：输入「全院薪酬发放汇总表」，一键核算全员实发工资与津贴\n` +
      `- 📋 **OA 请假与审批协同**：输入「列出未批准请假人员」「批准张小芳的请假申请」\n` +
      `- 📎 **多模态发薪与识图**：支持点击输入框下方图标上传工资表 Excel/图片`
  }
]

const chatMessages = ref([])

// 从后端同步会话历史（保证与大屏控制台及 MySQL 数据库绝对实时对齐）
const syncAdminSessionsFromBackend = async () => {
  const staffId = props.currentUserStaffId || 'ADM_0001'
  const token = localStorage.getItem('chunbo_admin_token')
  if (!staffId || !token) return
  try {
    const resp = await axios.get('/api/session/history', { params: { bizType: 'oa', userId: staffId } })
    const groups = resp.data || {}
    const backend = []
    Object.keys(groups).forEach(k => {
      (groups[k] || []).forEach(it => backend.push(it))
    })
    if (backend.length === 0) return
    let changed = false
    backend.forEach(bs => {
      const local = adminSessionList.value.find(s => s.id === bs.sessionId)
      if (local) {
        if (bs.title && local.title !== bs.title) {
          local.title = bs.title
          changed = true
        }
      } else {
        adminSessionList.value.unshift({
          id: bs.sessionId,
          title: bs.title || '历史会话',
          createdAt: bs.updateTime || formatNowTime(),
          updatedAt: bs.updateTime || formatNowTime(),
          messages: getDefaultWelcome()
        })
        changed = true
      }
    })
    if (changed) {
      saveSessions(true)
    }
  } catch (e) {}
}

const loadSessions = () => {
  try {
    const raw = localStorage.getItem(getStorageKey())
    if (raw) {
      const data = JSON.parse(raw)
      if (Array.isArray(data.sessions) && data.sessions.length > 0) {
        adminSessionList.value = data.sessions
        currentAdminSessionId.value = data.currentSessionId || data.sessions[0].id
        const active = adminSessionList.value.find(s => s.id === currentAdminSessionId.value) || adminSessionList.value[0]
        chatMessages.value = active.messages || []
        syncAdminSessionsFromBackend()
        return
      }
    }
  } catch (e) {}

  const initialSession = {
    id: 'admin_sess_' + Date.now(),
    title: '全中台运营调度主会话',
    createdAt: formatNowTime(),
    updatedAt: formatNowTime(),
    messages: getDefaultWelcome()
  }
  adminSessionList.value = [initialSession]
  currentAdminSessionId.value = initialSession.id
  chatMessages.value = initialSession.messages
  saveSessions(true)
  syncAdminSessionsFromBackend()
}

const saveSessions = (broadcast = true) => {
  try {
    const cur = adminSessionList.value.find(s => s.id === currentAdminSessionId.value)
    if (cur) {
      cur.messages = JSON.parse(JSON.stringify(chatMessages.value))
      cur.updatedAt = formatNowTime()
    }
    localStorage.setItem(getStorageKey(), JSON.stringify({
      currentSessionId: currentAdminSessionId.value,
      sessions: adminSessionList.value
    }))
    if (broadcast && typeof window !== 'undefined') {
      window.dispatchEvent(new CustomEvent('chunbo-admin-sessions-synced', {
        detail: {
          sender: 'drawer',
          currentSessionId: currentAdminSessionId.value,
          count: adminSessionList.value.length
        }
      }))
    }
  } catch (e) {}
}

// 跨组件双向无缝热同步监听（全局抽屉与大屏 AI 调度实时一致）
const handleExternalSessionsSync = (e) => {
  if (e && e.detail && e.detail.sender === 'drawer') return
  try {
    const raw = localStorage.getItem(getStorageKey())
    if (raw) {
      const data = JSON.parse(raw)
      if (Array.isArray(data.sessions)) {
        adminSessionList.value = data.sessions
        if (!adminSessionList.value.some(s => s.id === currentAdminSessionId.value)) {
          if (adminSessionList.value.length > 0) {
            currentAdminSessionId.value = adminSessionList.value[0].id
            chatMessages.value = adminSessionList.value[0].messages || []
          }
        }
      }
    }
  } catch (err) {}
}

const createNewSession = () => {
  const newSess = {
    id: 'admin_sess_' + Date.now() + '_' + Math.floor(Math.random() * 1000),
    title: '新运营调度会话',
    createdAt: formatNowTime(),
    updatedAt: formatNowTime(),
    messages: getDefaultWelcome()
  }
  adminSessionList.value.unshift(newSess)
  currentAdminSessionId.value = newSess.id
  chatMessages.value = newSess.messages
  saveSessions(true)
  showHistoryModal.value = false
  scrollChatToBottom()
  ElMessage.success('已新建全中台 AI 调度会话')
}

const switchSession = async (sessionId) => {
  saveSessions(true)
  const target = adminSessionList.value.find(s => s.id === sessionId)
  if (target) {
    currentAdminSessionId.value = target.id
    chatMessages.value = target.messages || []
    if (!target.messages || target.messages.length <= 1) {
      try {
        const res = await axios.get('/api/session/messages', { params: { sessionId: target.id } })
        if (Array.isArray(res.data) && res.data.length > 0) {
          target.messages = res.data
          chatMessages.value = res.data
          saveSessions(false)
        }
      } catch (e) {}
    }
    saveSessions(true)
    showHistoryModal.value = false
    scrollChatToBottom()
    ElMessage.success(`已切换至【${target.title}】`)
  }
}

const deleteSession = (sessionId) => {
  const idx = adminSessionList.value.findIndex(s => s.id === sessionId)
  if (idx !== -1) {
    adminSessionList.value.splice(idx, 1)
    if (adminSessionList.value.length > 0) {
      currentAdminSessionId.value = adminSessionList.value[0].id
      chatMessages.value = adminSessionList.value[0].messages || []
    } else {
      createNewSession()
    }
    saveSessions(true)
    const staffId = props.currentUserStaffId || 'ADM_0001'
    if (staffId) {
      axios.delete('/api/session/history', { params: { bizType: 'oa', sessionId, userId: staffId } }).catch(() => {})
    }
    ElMessage.success('已删除该会话记录')
  }
}

const clearAllSessions = () => {
  ElMessageBox.confirm('确定清空当前登录人员的所有 AI 运营调度会话历史？清空后不可恢复。', '清空会话历史', {
    confirmButtonText: '确认清空',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    localStorage.removeItem(getStorageKey())
    loadSessions()
    showHistoryModal.value = false
    ElMessage.success('已清空全部会话记录')
  }).catch(() => {})
}

// ── 聊天、多模态与流式通信 ──
const inputQuery = ref('')
const chatLoading = ref(false)
const chatBoxRef = ref(null)
const fileInputRef = ref(null)
const chatAttachment = ref(null) // { fileId, fileName, fileType, previewUrl, sizeLabel }
const isRecording = ref(false)
let speechRecognizer = null
let currentAssistantAbort = null

const scrollChatToBottom = () => {
  nextTick(() => {
    if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
  })
}

const renderMarkdown = (text) => {
  marked.setOptions({ breaks: true, gfm: true })
  let html = marked.parse(text || '')
  if (typeof html === 'string') {
    html = html
      .replace(/<table>/g, '<div class="md-table-scroll" style="overflow-x: auto; margin: 8px 0;"><table>')
      .replace(/<\/table>/g, '</table></div>')
  }
  return html
}

const handleQuickAsk = (txt) => {
  inputQuery.value = txt
  handleSend()
}

// 附件上传（图片 / Excel）
const triggerFileUpload = () => {
  if (fileInputRef.value) fileInputRef.value.click()
}

const handleFileSelect = async (e) => {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  const isImage = file.type.startsWith('image/')
  const isExcel = /\.(xlsx|xls)$/i.test(file.name)
  if (!isImage && !isExcel) {
    ElMessage.warning('请选择图片（jpg/png）或 Excel（xlsx/xls）文件')
    return
  }
  const previewUrl = isImage ? URL.createObjectURL(file) : ''
  const fd = new FormData()
  fd.append('file', file)
  try {
    const token = localStorage.getItem('chunbo_admin_token') || ''
    const resp = await fetch('/api/upload/file', {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + token },
      body: fd
    })
    const data = await resp.json()
    if (data && data.success) {
      chatAttachment.value = {
        fileId: data.fileId,
        fileName: file.name,
        fileType: isExcel ? 'excel' : 'image',
        previewUrl: data.url || previewUrl,
        sizeLabel: (file.size / 1024).toFixed(1) + ' KB'
      }
      ElMessage.success('附件已添加，发送后 AI 将自动解析处理')
    } else {
      ElMessage.error('上传失败：' + (data && data.message ? data.message : '接口异常'))
    }
  } catch (err) {
    ElMessage.error('上传异常：' + (err.message || '网络连接中断'))
  }
  e.target.value = ''
}

const removeAttachment = () => {
  chatAttachment.value = null
}

// ── 1. 语音识别录入 (Whisper 神经模型 + WebAudio AGC 自适应增益 + 麦克风硬件自动选麦) ──
let drawerMediaRecorder = null
let drawerAudioChunks = []
let drawerRecordStartAt = 0

// 探测麦克风设备实际输入电平
const probeMicLevel = async (deviceId) => {
  let ctx = null
  try {
    const constraints = deviceId ? { audio: { deviceId: { exact: deviceId } } } : { audio: true }
    const s = await navigator.mediaDevices.getUserMedia(constraints)
    ctx = new (window.AudioContext || window.webkitAudioContext)()
    if (ctx.state === 'suspended') { try { await ctx.resume() } catch (e) {} }
    const src = ctx.createMediaStreamSource(s)
    const an = ctx.createAnalyser()
    an.fftSize = 512
    src.connect(an)
    const buf = new Uint8Array(an.frequencyBinCount)
    let peak = 0
    const t0 = Date.now()
    while (Date.now() - t0 < 700) {
      an.getByteTimeDomainData(buf)
      for (let i = 0; i < buf.length; i++) { const v = Math.abs(buf[i] - 128); if (v > peak) peak = v }
      await new Promise(r => setTimeout(r, 60))
    }
    s.getTracks().forEach(t => t.stop())
    return peak
  } catch (e) {
    return -1
  } finally {
    if (ctx) { try { ctx.close() } catch (e2) {} }
  }
}

// 自动选麦：跳过静音的哑巴设备
const pickBestMic = async () => {
  try {
    const devs = (await navigator.mediaDevices.enumerateDevices()).filter(d => d.kind === 'audioinput')
    if (devs.length <= 1) return null
    const saved = localStorage.getItem('chunbo_mic_device_id')
    if (saved && devs.some(d => d.deviceId === saved)) {
      const p = await probeMicLevel(saved)
      if (p >= 3) return saved
    }
    let best = null, bestPeak = 0
    for (const d of devs.slice(0, 4)) {
      if (d.deviceId === saved) continue
      const p = await probeMicLevel(d.deviceId)
      if (p > bestPeak) { best = d; bestPeak = p }
    }
    if (best && bestPeak >= 3) {
      localStorage.setItem('chunbo_mic_device_id', best.deviceId)
      return best.deviceId
    }
  } catch (e) {}
  return null
}

const toggleVoiceInput = async () => {
  if (isRecording.value) {
    if (drawerMediaRecorder) { try { drawerMediaRecorder.stop() } catch (e) {} }
    return
  }
  try {
    const micId = await pickBestMic()
    const stream = await navigator.mediaDevices.getUserMedia({ audio: micId ? { deviceId: { exact: micId } } : true })
    let levelTimer = null
    let maxLevel = 0
    let recStream = stream
    let audioCtx = null
    try {
      audioCtx = new (window.AudioContext || window.webkitAudioContext)()
      if (audioCtx.state === 'suspended') { try { await audioCtx.resume() } catch (e) {} }
      const source = audioCtx.createMediaStreamSource(stream)
      const analyser = audioCtx.createAnalyser()
      analyser.fftSize = 512
      const compressor = audioCtx.createDynamicsCompressor()
      const gain = audioCtx.createGain()
      gain.gain.value = 10
      const dest = audioCtx.createMediaStreamDestination()
      source.connect(analyser)
      analyser.connect(compressor)
      compressor.connect(gain)
      gain.connect(dest)
      recStream = dest.stream
      const buf = new Uint8Array(analyser.frequencyBinCount)
      levelTimer = setInterval(() => {
        analyser.getByteTimeDomainData(buf)
        let peak = 0
        for (let i = 0; i < buf.length; i++) { const v = Math.abs(buf[i] - 128); if (v > peak) peak = v }
        maxLevel = Math.max(maxLevel, peak)
        if (peak > 3) {
          const target = Math.min(30, Math.max(6, 58 / peak))
          gain.gain.value += (target - gain.gain.value) * 0.3
        }
      }, 50)
    } catch (e) { if (audioCtx) { try { audioCtx.close() } catch (e2) {} audioCtx = null } }
    const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' : (MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : '')
    drawerMediaRecorder = mimeType ? new MediaRecorder(recStream, { mimeType }) : new MediaRecorder(recStream)
    drawerAudioChunks = []
    drawerRecordStartAt = Date.now()
    drawerMediaRecorder.ondataavailable = ev => { if (ev.data && ev.data.size) drawerAudioChunks.push(ev.data) }
    drawerMediaRecorder.onstop = async () => {
      isRecording.value = false
      const recordedMs = Date.now() - drawerRecordStartAt
      stream.getTracks().forEach(t => t.stop())
      if (levelTimer) { clearInterval(levelTimer); levelTimer = null }
      if (audioCtx) { try { audioCtx.close() } catch (e) {} audioCtx = null }
      if (recordedMs < 800) {
        ElMessage.warning('说话时间太短，请说完一句再结束')
        return
      }
      if (!drawerAudioChunks.length) {
        ElMessage.warning('录音数据为空，请重试')
        return
      }
      const blob = new Blob(drawerAudioChunks, { type: drawerMediaRecorder.mimeType || 'audio/webm' })
      if (maxLevel > 0 && maxLevel < 8) {
        ElMessage.error('未检测到有效语音，请检查麦克风设备')
        return
      }
      const fd = new FormData()
      fd.append('file', blob, 'voice.webm')
      ElMessage.info('Whisper 正在识别语音…')
      try {
        const resp = await fetch('/api/audio/asr', {
          method: 'POST',
          headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('chunbo_admin_token') || '') },
          body: fd
        })
        const data = await resp.json()
        const txt = (data && (data.text || data.result)) || ''
        if (txt && /[\u4e00-\u9fa5]/.test(txt)) {
          inputQuery.value = (inputQuery.value ? inputQuery.value + ' ' : '') + txt
          ElMessage.success('语音已识别填入指令框')
        } else {
          ElMessage.warning((data && data.message) || '未识别到清晰中文，请靠近麦克风重试')
        }
      } catch (e) {
        ElMessage.error('语音识别失败：' + (e.message || '网络错误'))
      }
    }
    drawerMediaRecorder.start()
    isRecording.value = true
    ElMessage.info('正在聆听中台调度指令，说完点击 ⏹ 结束')
  } catch (e) {
    ElMessage.error('无法访问麦克风，请检查浏览器权限')
  }
}

// ── 2. 语音合成朗读 (TTS：后端流式自然人声) ──
let drawerTtsAudio = null
const speakMessage = async (msg) => {
  if (drawerTtsAudio) {
    drawerTtsAudio.pause()
    drawerTtsAudio = null
    msg._speaking = false
    return
  }
  try {
    const plain = String(msg.content || '').replace(/[#*>`|_~-]/g, '').replace(/\n+/g, ' ').trim().slice(0, 400)
    if (!plain) return
    const resp = await fetch('/api/audio/tts-stream', {
      method: 'POST',
      headers: {
        'Authorization': 'Bearer ' + (localStorage.getItem('chunbo_admin_token') || ''),
        'Content-Type': 'text/plain'
      },
      body: plain
    })
    if (!resp.ok) throw new Error('HTTP ' + resp.status)
    const blob = await resp.blob()
    drawerTtsAudio = new Audio(URL.createObjectURL(blob))
    msg._speaking = true
    drawerTtsAudio.onended = () => { msg._speaking = false; drawerTtsAudio = null }
    drawerTtsAudio.onerror = () => { msg._speaking = false; drawerTtsAudio = null }
    drawerTtsAudio.play()
  } catch (e) {
    ElMessage.error('流式语音合成失败：' + (e.message || '网络错误'))
  }
}

// ── 3. 停止生成打断机制 ──
let assistantActiveSessionId = null
const stopAssistantGeneration = () => {
  if (assistantActiveSessionId) {
    fetch(`/api/assistant/chat/stop?sessionId=${encodeURIComponent(assistantActiveSessionId)}`, {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('chunbo_admin_token') || '') }
    }).catch(() => {})
  }
  if (currentAssistantAbort) {
    currentAssistantAbort.abort()
    currentAssistantAbort = null
  }
  assistantActiveSessionId = null
  chatLoading.value = false
  const last = [...chatMessages.value].reverse().find(m => m.role === 'assistant')
  if (last) {
    if (last.thinking) last.thinking = false
    if (!last.content) last.content = '（已停止生成）'
  }
  saveSessions(true)
  ElMessage.info('已主动停止 AI 调度生成')
}

// ── 4. AI 文字助手（通用文本模型：帮写/续写/润色/精简/联想词） ──
const showTextAssistant = ref(false)
const textTemplates = ref([
  { key: 'associationalWord', name: '联想词' },
  { key: 'helpedWrite', name: '帮写' },
  { key: 'continuedWrite', name: '续写' },
  { key: 'polish', name: '润色' },
  { key: 'streamline', name: '精简' }
])
const textAssistantType = ref('polish')
const textAssistantInput = ref('')
const textAssistantResult = ref('')
const textAssistantLoading = ref(false)

const openTextAssistant = () => {
  showTextAssistant.value = true
  textAssistantResult.value = ''
  if (inputQuery.value.trim() && !textAssistantInput.value) {
    textAssistantInput.value = inputQuery.value.trim()
  }
}

const runTextAssistant = async () => {
  if (!textAssistantInput.value.trim()) {
    ElMessage.warning('请先输入待处理文本')
    return
  }
  textAssistantLoading.value = true
  textAssistantResult.value = ''
  try {
    const res = await axios.post('/api/text/process', {
      type: textAssistantType.value,
      input: textAssistantInput.value
    }, {
      headers: { 'Authorization': 'Bearer ' + (localStorage.getItem('chunbo_admin_token') || '') }
    })
    textAssistantResult.value = (res.data && res.data.result) || '（无返回）'
  } catch (e) {
    textAssistantResult.value = '文本处理失败：' + (e.response?.data?.message || e.message || '网络错误')
  } finally {
    textAssistantLoading.value = false
  }
}

const copyTextResult = () => {
  if (!textAssistantResult.value) return
  navigator.clipboard?.writeText(textAssistantResult.value).then(() => {
    ElMessage.success('已复制到剪贴板')
  }).catch(() => {
    ElMessage.info('复制失败，请手动复制')
  })
}

const applyTextResultToInput = () => {
  if (!textAssistantResult.value) return
  inputQuery.value = textAssistantResult.value
  showTextAssistant.value = false
  ElMessage.success('已将 AI 润色文本填入调度指令框')
}

// ── 发送中台调度指令 ──
const handleSend = async () => {
  const text = inputQuery.value.trim()
  const att = chatAttachment.value ? { ...chatAttachment.value } : null
  if ((!text && !att) || chatLoading.value) return

  const sendText = text || (att.fileType === 'excel' ? '请解析我上传的表格文件并执行对应业务' : '请识别我上传的图片内容')

  // 1. 压入用户消息
  chatMessages.value.push({
    role: 'user',
    content: sendText,
    image: att && att.fileType === 'image' ? att.previewUrl : undefined,
    attachment: att ? { fileName: att.fileName, fileType: att.fileType, sizeLabel: att.sizeLabel, previewUrl: att.previewUrl } : undefined
  })
  chatAttachment.value = null
  inputQuery.value = ''

  // 2. 自动更新当前会话标题
  const curSess = adminSessionList.value.find(s => s.id === currentAdminSessionId.value)
  if (curSess && curSess.title === '新运营调度会话') {
    curSess.title = sendText.length > 14 ? sendText.slice(0, 14) + '…' : sendText
  }

  // 3. 严格压入单个 assistant 消息，思考中状态融入气泡内（坚决杜绝两个气泡）
  const assistantMsgIndex = chatMessages.value.length
  chatMessages.value.push({
    role: 'assistant',
    content: '',
    thinking: true,
    _speaking: false
  })
  chatLoading.value = true
  assistantActiveSessionId = currentAdminSessionId.value
  scrollChatToBottom()

  const abort = new AbortController()
  currentAssistantAbort = abort
  const token = localStorage.getItem('chunbo_admin_token') || ''
  const attachParam = att && att.fileId ? `&attachmentId=${encodeURIComponent(att.fileId)}&fileName=${encodeURIComponent(att.fileName || '')}` : ''
  const sseUrl = `/api/assistant/chat/stream?message=${encodeURIComponent(sendText)}&userId=${encodeURIComponent(props.currentUserStaffId || 'ADM_0001')}&userRole=${encodeURIComponent(props.currentUserRole || 'ADMIN')}&userName=${encodeURIComponent(props.currentUserName || '系统管理员')}&sessionId=${encodeURIComponent(currentAdminSessionId.value)}${attachParam}`

  try {
    const resp = await fetch(sseUrl, {
      headers: { 'Authorization': 'Bearer ' + token },
      signal: abort.signal
    })
    if (!resp.ok || !resp.body) throw new Error('HTTP ' + resp.status)

    const reader = resp.body.getReader()
    const dec = new TextDecoder('utf-8')
    let buf = ''
    let pendingText = ''

    const renderTimer = setInterval(() => {
      if (pendingText.length > 0) {
        // 首批字符到达，解除 thinking 状态
        if (chatMessages.value[assistantMsgIndex].thinking) {
          chatMessages.value[assistantMsgIndex].thinking = false
        }
        const take = Math.max(2, Math.ceil(pendingText.length / 5))
        chatMessages.value[assistantMsgIndex].content += pendingText.slice(0, take)
        pendingText = pendingText.slice(take)
        scrollChatToBottom()
      }
    }, 25)

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += dec.decode(value, { stream: true })
      const parts = buf.split('\n\n')
      buf = parts.pop()
      for (const ev of parts) {
        const dl = ev.split('\n').find(l => l.startsWith('data:'))
        if (!dl) continue
        let piece = dl.slice(5).trim()
        if (!piece) continue
        let parsed
        try { parsed = JSON.parse(piece) } catch (e2) {
          pendingText += piece
          continue
        }
        if (parsed.eventType === 1001) {
          pendingText += (parsed.eventData || '')
        }
      }
    }
    clearInterval(renderTimer)
    if (chatMessages.value[assistantMsgIndex].thinking) {
      chatMessages.value[assistantMsgIndex].thinking = false
    }
    chatMessages.value[assistantMsgIndex].content += pendingText
    pendingText = ''
  } catch (e) {
    if (e.name !== 'AbortError') {
      chatMessages.value[assistantMsgIndex].thinking = false
      chatMessages.value[assistantMsgIndex].content = '⚠️ 调度指令执行失败：' + (e.message || '网络连接中断')
    }
  } finally {
    chatLoading.value = false
    currentAssistantAbort = null
    assistantActiveSessionId = null
    saveSessions(true)
    scrollChatToBottom()
  }
}

watch(() => props.visible, (val) => {
  if (val) {
    loadSessions()
    scrollChatToBottom()
  }
})

const handleOpenDrawerEvent = (e) => {
  emit('update:visible', true)
  if (e && e.detail && e.detail.query) {
    setTimeout(() => {
      handleQuickAsk(e.detail.query)
    }, 60)
  }
}

onMounted(() => {
  loadSessions()
  window.addEventListener('chunbo-admin-sessions-synced', handleExternalSessionsSync)
  window.addEventListener('open-admin-ai-drawer', handleOpenDrawerEvent)
})

onUnmounted(() => {
  window.removeEventListener('chunbo-admin-sessions-synced', handleExternalSessionsSync)
  window.removeEventListener('open-admin-ai-drawer', handleOpenDrawerEvent)
})
</script>

<template>
  <el-drawer
    v-model="drawerVisible"
    size="620px"
    destroy-on-close
    class="global-ai-drawer"
  >
    <template #header>
      <div class="drawer-header-row">
        <div class="header-left">
          <div class="bot-avatar-badge">🤖</div>
          <div class="header-text-group">
            <div class="header-title-line">
              <span class="header-main-title">全中台 AI 调度指挥</span>
              <el-tag size="small" type="primary" effect="light" class="role-tag">{{ currentRoleLabel || '系统管理员' }}</el-tag>
            </div>
            <div class="header-sub-desc">
              <span class="active-sess-indicator"></span>
              <span class="sub-label">当前会话:</span>
              <span class="sub-title-text" :title="activeSessionTitle">{{ activeSessionTitle }}</span>
            </div>
          </div>
        </div>
        <div class="header-right-actions">
          <el-button size="small" type="primary" plain @click="createNewSession" title="新建调度会话">
            <el-icon><Plus /></el-icon> 新建
          </el-button>
          <el-button size="small" :type="showHistoryModal ? 'primary' : 'default'" @click="showHistoryModal = !showHistoryModal" title="查看会话历史记录">
            <el-icon><Clock /></el-icon> 历史 ({{ adminSessionList.length }})
          </el-button>
          <el-button size="small" type="danger" link @click="clearAllSessions" title="清空全部历史会话">
            清空
          </el-button>
        </div>
      </div>
    </template>

    <div class="drawer-main-container">
      <!-- 会话历史弹出层 -->
      <transition name="el-zoom-in-top">
        <div v-if="showHistoryModal" class="history-dropdown-panel">
          <div class="history-panel-header">
            <span>📋 历史调度会话（按工号隔离）</span>
            <el-button link size="small" @click="showHistoryModal = false">收起 ✕</el-button>
          </div>
          <div class="history-scroll-list">
            <div v-for="(sessList, grpKey) in groupedSessions" :key="grpKey">
              <div v-if="sessList.length > 0" class="history-group-title">
                {{ grpKey === 'today' ? '今天' : (grpKey === 'week' ? '本周' : (grpKey === 'month' ? '本月' : '更早')) }}
              </div>
              <div
                v-for="s in sessList"
                :key="s.id"
                class="history-item-row"
                :class="{ active: s.id === currentAdminSessionId }"
                @click="switchSession(s.id)"
              >
                <div class="hist-title-text">{{ s.title }}</div>
                <div class="hist-time-tag">{{ s.updatedAt || s.createdAt }}</div>
                <el-icon class="hist-del-btn" @click.stop="deleteSession(s.id)"><Delete /></el-icon>
              </div>
            </div>
          </div>
        </div>
      </transition>

      <!-- 快捷业务调度指令面板：多行自适应平铺，彻底去除横向滚动条，核心指令一目了然 -->
      <div class="quick-pill-panel">
        <div class="pill-panel-head">
          <div class="pill-panel-title">
            <span class="zap-icon">⚡</span>
            <span>中台核心调度指令</span>
            <span class="pill-count-tag">共 8 项快捷直达</span>
          </div>
          <span class="pill-panel-tip">点击即发 · 数据穿透 MySQL 与商城</span>
        </div>
        <div class="pill-wrap-container">
          <!-- 💰 薪酬人事专区 -->
          <button v-if="currentUserRole === 'ADMIN' || currentUserRole === 'HR'" class="pill-chip success" @click="handleQuickAsk('全院薪酬发放汇总表')" title="核算全院本月薪酬总额与医护实发台账">
            💰 全院薪酬总表
          </button>
          <button v-if="currentUserRole === 'DOCTOR'" class="pill-chip success" @click="handleQuickAsk('查我的电子工资条')" title="调阅本人当月电子工资条与津贴明细">
            💳 电子工资条
          </button>
          <button v-if="currentUserRole === 'MERCHANT'" class="pill-chip warning" @click="handleQuickAsk('查商户本人提成')" title="调阅商户本人销售提成与结算明细">
            💵 我的提成
          </button>

          <!-- 📦 商城履约与用户专区 -->
          <button class="pill-chip primary" @click="handleQuickAsk('商城待发货订单清单')" title="穿透调阅春播健康商城 B2C 待履约发货订单">
            📦 商城待发货清单
          </button>
          <button class="pill-chip primary" @click="handleQuickAsk('查询所有订单')" title="查询春播健康商城全部订单及配送状态">
            📋 查所有订单
          </button>
          <button class="pill-chip primary" @click="handleQuickAsk('商城注册了哪些用户')" title="查询春播商城注册用户总名录">
            👥 注册用户名单
          </button>

          <!-- 🚨 药房基药与营收专区 -->
          <button class="pill-chip danger" @click="handleQuickAsk('智慧药房低库存预警台账')" title="穿透底层 MySQL 调阅低库存紧缺药品">
            🚨 药房低库存预警
          </button>
          <button class="pill-chip danger" @click="handleQuickAsk('智慧药房智能补货与临期药品预警研判')" title="根据消耗速度与安全阈值测算补货量">
            💊 智能补货研判
          </button>
          <button class="pill-chip info" @click="handleQuickAsk('门诊运营大盘真实诊断')" title="综合研判接诊、流水与特色理疗创收">
            📊 门诊营收诊断
          </button>

          <!-- 📝 办公审批专区 -->
          <button class="pill-chip neutral" @click="handleQuickAsk('列出未批准请假人员')" title="统计并列出 OA 系统中所有待审批请假">
            📝 待审请假单
          </button>
        </div>
      </div>

      <!-- 消息气泡展示区 -->
      <div class="drawer-chat-box" ref="chatBoxRef">
        <div
          v-for="(msg, idx) in chatMessages"
          :key="idx"
          class="chat-bubble-row"
          :class="msg.role"
        >
          <div class="bubble-avatar">
            {{ msg.role === 'user' ? '👤' : '🤖' }}
          </div>
          <div class="bubble-body">
            <!-- 附件文件卡片 -->
            <div v-if="msg.attachment" class="msg-file-card">
              <span class="file-type-tag">{{ msg.attachment.fileType === 'excel' ? '📊 表格' : '🖼️ 图片' }}</span>
              <span class="file-name-text">{{ msg.attachment.fileName }}</span>
              <span class="file-size-text">{{ msg.attachment.sizeLabel }}</span>
            </div>
            <!-- 图片附件直接显示缩略图 -->
            <div v-if="msg.image" class="msg-img-preview">
              <img :src="msg.image" alt="附件图片" />
            </div>

            <!-- 正文或思考中动画（单气泡严谨渲染） -->
            <div v-if="msg.thinking && !msg.content" class="bubble-content thinking-pulse">
              <span class="dot-spin"></span> AI 指挥官正在穿透中台微服务执行调度…
            </div>
            <div v-else class="bubble-content" v-html="renderMarkdown(msg.content)"></div>

            <!-- 语音朗读按钮 -->
            <div v-if="msg.role === 'assistant' && msg.content && !chatLoading" class="msg-tts-bar">
              <span class="tts-btn" @click="speakMessage(msg)">
                {{ msg._speaking ? '⏹ 停止朗读' : '🔊 朗读回答' }}
              </span>
            </div>
          </div>
        </div>
      </div>

      <!-- 附件上传预览槽 -->
      <div v-if="chatAttachment" class="attachment-preview-row">
        <div class="att-card">
          <span class="att-icon">{{ chatAttachment.fileType === 'excel' ? '📊' : '🖼️' }}</span>
          <span class="att-name">{{ chatAttachment.fileName }}</span>
          <span class="att-size">{{ chatAttachment.sizeLabel }}</span>
          <span class="att-del" @click="removeAttachment" title="移除附件">✕</span>
        </div>
      </div>

      <!-- 底部输入操作栏 -->
      <div class="drawer-input-zone">
        <div class="input-media-bar">
          <button class="media-icon-btn" @click="triggerFileUpload" title="上传 Excel 工资表或商品/药盒图片">
            <el-icon><Picture /></el-icon> 附件/图片
          </button>
          <input ref="fileInputRef" type="file" accept="image/*,.xlsx,.xls" style="display: none;" @change="handleFileSelect" />

          <button class="media-icon-btn" :class="{ recording: isRecording }" @click="toggleVoiceInput" title="语音录入调度指令 (Whisper高精度神经识别)">
            <el-icon v-if="!isRecording"><Microphone /></el-icon>
            <span v-else>⏹</span> {{ isRecording ? '录音中...' : '语音录入' }}
          </button>

          <button class="media-icon-btn text-assistant-btn" @click="openTextAssistant" title="AI 帮写 / 续写 / 润色 / 精简 / 联想词">
            ✨ AI文字助手
          </button>
        </div>

        <el-input
          v-model="inputQuery"
          type="textarea"
          :rows="3"
          placeholder="输入中台调度指令，如「商城待发货订单清单」「给李先生发货」「稳健口罩调价到13.5」「批准张小芳请假」..."
          resize="none"
          @keydown.enter.prevent="handleSend"
        />
        <div class="input-actions-bar">
          <span class="send-tip">按 Enter 发送 · 具备真实写库与进销存调度</span>
          <el-button v-if="!chatLoading" type="primary" @click="handleSend">
            发送调度指令
          </el-button>
          <el-button v-else type="danger" @click="stopAssistantGeneration">
            ⏹ 停止生成
          </el-button>
        </div>
      </div>
    </div>

    <!-- AI 文字助手弹窗（通用文本模型） -->
    <el-dialog v-model="showTextAssistant" title="✨ AI 文字助手（帮写 / 续写 / 润色 / 精简 / 联想词）" width="560px" append-to-body>
      <div class="text-assistant-body">
        <div class="ta-templates">
          <span class="ta-label">选择模板：</span>
          <el-radio-group v-model="textAssistantType">
            <el-radio-button v-for="t in textTemplates" :key="t.key" :value="t.key">{{ t.name }}</el-radio-button>
          </el-radio-group>
        </div>
        <el-input
          v-model="textAssistantInput"
          type="textarea"
          :rows="5"
          placeholder="在这里输入需要处理的文本，如草拟的公文、请假理由、通知或经营总结…"
          resize="none"
        />
        <div class="ta-actions">
          <el-button type="primary" :loading="textAssistantLoading" @click="runTextAssistant">✨ 开始生成</el-button>
          <el-button v-if="textAssistantResult" size="default" @click="copyTextResult">📋 复制结果</el-button>
          <el-button v-if="textAssistantResult" size="default" type="success" plain @click="applyTextResultToInput">填入指令框 ↵</el-button>
        </div>
        <div class="ta-result" v-if="textAssistantResult">
          <div class="ta-result-title">生成结果：</div>
          <div class="ta-result-content" v-html="renderMarkdown(textAssistantResult)"></div>
        </div>
      </div>
    </el-dialog>
  </el-drawer>
</template>

<style scoped>
.drawer-header-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding-right: 4px;
}
.header-left {
  display: flex;
  align-items: center;
  gap: 10px;
  min-width: 0;
  flex: 1;
}
.bot-avatar-badge {
  font-size: 24px;
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #e0f2fe, #bae6fd);
  border: 1px solid #7dd3fc;
  border-radius: 9px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(2, 132, 199, 0.1);
}
.header-text-group {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.header-title-line {
  display: flex;
  align-items: center;
  gap: 8px;
}
.header-main-title {
  font-weight: 700;
  font-size: 15px;
  color: #0f172a;
  white-space: nowrap;
}
.role-tag {
  font-weight: 500;
  border-radius: 6px;
  padding: 0 8px;
  height: 22px;
  line-height: 20px;
}
.header-sub-desc {
  font-size: 11px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 5px;
  min-width: 0;
}
.active-sess-indicator {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #10b981;
  box-shadow: 0 0 0 2px rgba(16, 185, 129, 0.2);
  flex-shrink: 0;
  animation: pulse-green 2s infinite;
}
@keyframes pulse-green {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.5); }
  70% { transform: scale(1); box-shadow: 0 0 0 5px rgba(16, 185, 129, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
}
.sub-label {
  color: #94a3b8;
  flex-shrink: 0;
}
.sub-title-text {
  color: #334155;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 180px;
}
.header-right-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}
.drawer-main-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
}

/* 历史会话下拉层 */
.history-dropdown-panel {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  max-height: 380px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1);
  z-index: 50;
  display: flex;
  flex-direction: column;
}
.history-panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 14px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.history-scroll-list {
  padding: 8px;
  overflow-y: auto;
  max-height: 320px;
}
.history-group-title {
  font-size: 11px;
  color: #94a3b8;
  padding: 6px 8px 2px;
  font-weight: 600;
}
.history-item-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 4px;
}
.history-item-row:hover {
  background: #f1f5f9;
}
.history-item-row.active {
  background: #e0f2fe;
  color: #0284c7;
  font-weight: 600;
}
.hist-title-text {
  font-size: 12px;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-right: 8px;
}
.hist-time-tag {
  font-size: 11px;
  color: #94a3b8;
  margin-right: 8px;
}
.hist-del-btn {
  font-size: 13px;
  color: #cbd5e1;
  transition: color 0.2s;
}
.hist-del-btn:hover {
  color: #ef4444;
}

/* 快捷业务指令面板：自适应多行平铺，彻底去除横向滚动条 */
.quick-pill-panel {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px 10px 10px;
  margin-bottom: 12px;
}
.pill-panel-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 7px;
  padding: 0 2px;
}
.pill-panel-title {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 12px;
  font-weight: 600;
  color: #334155;
}
.zap-icon {
  font-size: 13px;
  color: #f59e0b;
}
.pill-count-tag {
  font-size: 10px;
  color: #64748b;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 0 5px;
  font-weight: normal;
}
.pill-panel-tip {
  font-size: 11px;
  color: #94a3b8;
}
.pill-wrap-container {
  display: flex;
  flex-wrap: wrap; /* 关键：自动换行，彻底杜绝横向滚动条！ */
  gap: 6px 8px;
}
.pill-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 5px 10px;
  border-radius: 7px;
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.18s cubic-bezier(0.4, 0, 0.2, 1);
  border: 1px solid transparent;
  user-select: none;
}
.pill-chip:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.05);
}
.pill-chip:active {
  transform: translateY(0);
}
/* 薪酬：柔和翡翠绿 */
.pill-chip.success {
  background: #ecfdf5;
  border-color: #a7f3d0;
  color: #065f46;
}
.pill-chip.success:hover {
  background: #d1fae5;
  border-color: #6ee7b7;
  color: #047857;
}
/* 商城订单：现代天蓝 */
.pill-chip.primary {
  background: #f0f9ff;
  border-color: #bae6fd;
  color: #0369a1;
}
.pill-chip.primary:hover {
  background: #e0f2fe;
  border-color: #7dd3fc;
  color: #0284c7;
}
/* 药房紧缺预警：警示橙红 */
.pill-chip.danger {
  background: #fff1f2;
  border-color: #fecdd3;
  color: #be123c;
}
.pill-chip.danger:hover {
  background: #ffe4e6;
  border-color: #fda4af;
  color: #9f1239;
}
/* 经营诊断：柔和紫罗兰 */
.pill-chip.info {
  background: #f5f3ff;
  border-color: #ddd6fe;
  color: #6d28d9;
}
.pill-chip.info:hover {
  background: #ede9fe;
  border-color: #c4b5fd;
  color: #5b21b6;
}
/* 提成：琥珀金黄 */
.pill-chip.warning {
  background: #fffbeb;
  border-color: #fde68a;
  color: #92400e;
}
.pill-chip.warning:hover {
  background: #fef3c7;
  border-color: #fcd34d;
  color: #78350f;
}
/* 协同请假：精致板岩灰 */
.pill-chip.neutral {
  background: #ffffff;
  border-color: #cbd5e1;
  color: #334155;
}
.pill-chip.neutral:hover {
  background: #f8fafc;
  border-color: #94a3b8;
  color: #0f172a;
}

/* 消息气泡区 */
.drawer-chat-box {
  flex: 1;
  overflow-y: auto;
  padding: 10px 4px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.chat-bubble-row {
  display: flex;
  gap: 10px;
  width: 100%;
}
.chat-bubble-row.user {
  flex-direction: row-reverse;
}
.bubble-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #e2e8f0;
  font-size: 16px;
  flex-shrink: 0;
}
.chat-bubble-row.assistant .bubble-avatar {
  background: linear-gradient(135deg, #0284c7, #0369a1);
}
.bubble-body {
  max-width: 86%;
}

/* 核心正文样式：彻底修复小点溢出到框外与表格排版 */
.bubble-content {
  padding: 12px 16px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  color: #1e293b;
  word-break: break-word;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.04);
}
.chat-bubble-row.user .bubble-content {
  background: linear-gradient(135deg, #0284c7, #0369a1);
  color: #ffffff;
  border: none;
}

/* 列表缩进彻底包裹在气泡内部 */
.bubble-content :deep(ul),
.bubble-content :deep(ol) {
  padding-left: 20px;
  margin: 6px 0;
}
.bubble-content :deep(li) {
  margin: 4px 0;
  line-height: 1.6;
}
.bubble-content :deep(p) {
  margin: 4px 0;
}
.bubble-content :deep(h1),
.bubble-content :deep(h2),
.bubble-content :deep(h3),
.bubble-content :deep(h4) {
  margin: 8px 0 4px;
  font-weight: 700;
}

/* 表格优雅样式 */
.bubble-content :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 8px 0;
  font-size: 12px;
  background: #ffffff;
}
.bubble-content :deep(th),
.bubble-content :deep(td) {
  border: 1px solid #cbd5e1;
  padding: 6px 10px;
  text-align: left;
}
.bubble-content :deep(th) {
  background: #f1f5f9;
  font-weight: 600;
  color: #0f172a;
}

/* 附件文件卡片 */
.msg-file-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  background: rgba(255, 255, 255, 0.2);
  border-radius: 6px;
  margin-bottom: 6px;
  font-size: 12px;
}
.msg-img-preview img {
  max-width: 220px;
  max-height: 160px;
  border-radius: 8px;
  margin-bottom: 6px;
  border: 1px solid #cbd5e1;
}

/* 朗读工具条 */
.msg-tts-bar {
  margin-top: 6px;
}
.tts-btn {
  font-size: 11px;
  color: #64748b;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
  transition: all 0.2s;
}
.tts-btn:hover {
  background: #e2e8f0;
  color: #0284c7;
}

/* 思考中呼吸动画 */
.thinking-pulse {
  color: #0284c7;
  font-style: italic;
  display: flex;
  align-items: center;
  gap: 6px;
}
.dot-spin {
  display: inline-block;
  width: 8px;
  height: 8px;
  border: 2px solid #0284c7;
  border-top-color: transparent;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 附件预览条 */
.attachment-preview-row {
  padding: 6px 4px;
}
.att-card {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 5px 12px;
  background: #e0f2fe;
  border: 1px solid #bae6fd;
  border-radius: 8px;
  font-size: 12px;
  color: #0369a1;
}
.att-del {
  cursor: pointer;
  font-weight: bold;
  margin-left: 4px;
  color: #94a3b8;
}
.att-del:hover {
  color: #ef4444;
}

/* 底部输入区 */
.drawer-input-zone {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid #e2e8f0;
}
.input-media-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 8px;
}
.media-icon-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: #f8fafc;
  border: 1px solid #cbd5e1;
  border-radius: 6px;
  font-size: 12px;
  color: #475569;
  cursor: pointer;
  transition: all 0.2s;
}
.media-icon-btn:hover {
  background: #f1f5f9;
  color: #0284c7;
  border-color: #0284c7;
}
.media-icon-btn.recording {
  background: #fee2e2;
  border-color: #ef4444;
  color: #dc2626;
  animation: pulse 1.2s infinite;
}
.input-actions-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
}
.send-tip {
  font-size: 11px;
  color: #94a3b8;
}
.media-icon-btn.text-assistant-btn {
  background: #fef3c7;
  border-color: #fde68a;
  color: #b45309;
  font-weight: 500;
  margin-left: auto;
}
.media-icon-btn.text-assistant-btn:hover {
  background: #fde68a;
  border-color: #f59e0b;
  color: #92400e;
}
.active-sess-indicator {
  color: #10b981;
  font-size: 10px;
  margin-right: 2px;
}
@keyframes pulse {
  0%, 100% { opacity: 0.6; }
  50% { opacity: 1; }
}
</style>
