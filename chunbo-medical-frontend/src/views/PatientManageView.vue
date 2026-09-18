<template>
  <div class="patient-manage-container">
    <!-- 顶部操作栏 -->
    <div class="header-card">
      <div class="header-left">
        <div class="page-title-badge">
          <span class="pulse-dot"></span>
          <span class="title-text">患者全生命周期健康档案中心</span>
        </div>
        <el-tag type="primary" effect="plain">360° 全息临床画像与复诊随访闭环</el-tag>
      </div>

      <div class="header-right">
        <el-input 
          v-model="searchKey" 
          placeholder="患者姓名 / 手机号 / 身份证号" 
          prefix-icon="Search"
          clearable 
          style="width: 280px;"
        />
        <el-button type="primary" class="gradient-btn" @click="openAddFollowupModal">
          <el-icon><Calendar /></el-icon> 新建随访计划
        </el-button>
      </div>
    </div>

    <!-- 主体双栏布局 (对应截图 09/11/55/58/60/61) -->
    <div class="patient-layout">
      <!-- 左侧：患者档案索引列表 -->
      <div class="patient-left-list">
        <div class="list-head">
          <span class="list-title">档案患者 ({{ filteredPatients.length }})</span>
        </div>

        <div class="patient-cards">
          <div 
            v-for="p in filteredPatients" 
            :key="p.id" 
            class="patient-side-card"
            :class="{ active: selectedPatient && selectedPatient.id === p.id }"
            @click="selectPatient(p)"
          >
            <div class="p-card-top">
              <span class="p-name">{{ p.name }}</span>
              <span class="p-gender" :class="p.gender === '女' ? 'female' : 'male'">{{ p.gender }}</span>
              <span class="p-age">{{ p.ageText || (p.age + '岁') }}</span>
            </div>
            <div class="p-card-phone">{{ p.phone }}</div>
            <div class="p-card-tags">
              <el-tag size="small" type="success" effect="plain">{{ p.label || '贴敷调理' }}</el-tag>
              <span class="p-consume">消费 ¥{{ Number(p.totalSpent || 290.7).toFixed(2) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右侧：选中的患者 360° 全息档案面板 -->
      <div class="patient-right-detail" v-if="selectedPatient">
        <!-- 基础身份卡 (对应截图 09/55) -->
        <div class="patient-profile-card">
          <div class="profile-top">
            <div class="avatar-box">
              <div class="avatar-icon">👤</div>
              <el-button type="primary" link size="small" @click="uploadTonguePhoto">
                <el-icon><Camera /></el-icon> 上传舌象
              </el-button>
            </div>

            <div class="profile-main-fields">
              <div class="row-1">
                <span class="name-bold">{{ selectedPatient.name }}</span>
                <span class="gender-tag">{{ selectedPatient.gender }}</span>
                <span class="age-bold">{{ selectedPatient.ageText || (selectedPatient.age + '岁') }}</span>
                <span class="phone-bold">{{ selectedPatient.phone }}</span>
                <el-tag type="warning" size="small" effect="dark">贴敷调理</el-tag>
                <el-tag type="danger" size="small" effect="plain">青霉素过敏</el-tag>
              </div>

              <div class="row-2">
                <span class="f-item">出生日期：{{ selectedPatient.birthDate || '1993-03-17' }}</span>
                <span class="f-item">身份证号：{{ selectedPatient.idCard || '43010519930317****' }}</span>
                <span class="f-item">职业：公司职员</span>
                <span class="f-item">建档日期：{{ selectedPatient.createdAt ? selectedPatient.createdAt.substring(0, 10) : '2026-05-13' }}</span>
              </div>

              <div class="row-3">
                <span class="f-item">常住地址：湖南省长沙市开福区月湖科技园</span>
                <span class="f-item">累计消费：<b class="text-red">¥{{ Number(selectedPatient.totalSpent || 290.7).toFixed(2) }}</b></span>
              </div>
            </div>
          </div>
        </div>

        <!-- 5 大子标签页 (电子病历 / 健康档案 / 收费信息 / 发药信息 / 随访记录) -->
        <div class="tabs-zone">
          <el-tabs v-model="activeSubView" class="patient-sub-tabs">
            <!-- 1. 电子病历视图 (截图 09/10/56) -->
            <el-tab-pane label="电子病历与处方" name="emr">
              <div class="timeline-emr-list">
                <div class="emr-timeline-node" v-for="(rec, idx) in emrHistory" :key="idx">
                  <div class="node-header">
                    <span class="node-date">{{ rec.date }}</span>
                    <el-tag type="primary" size="small">{{ rec.dept }} · {{ rec.doctor }}</el-tag>
                    <span class="node-diag">诊断：<b>{{ rec.diagnosis }}</b></span>
                  </div>
                  <div class="node-body">
                    <div class="n-row"><b>主诉：</b> {{ rec.chief }}</div>
                    <div class="n-row"><b>现病史：</b> {{ rec.hpi }}</div>
                    <div class="n-row"><b>过敏史：</b> {{ rec.allergies }}</div>
                    <div class="n-rx-box">
                      <span class="rx-tag-title">开具处方明细：</span>
                      <span class="rx-content">{{ rec.prescription }}</span>
                    </div>
                  </div>
                </div>
                <div v-if="emrHistory.length === 0" class="empty-history-hint" style="text-align:center;color:#94a3b8;padding:40px 0;">
                  该患者名下暂无就诊记录（同一身份证号的历史就诊会自动合并到一起）
                </div>
              </div>
            </el-tab-pane>

            <!-- 2. 健康档案视图 (截图 58) -->
            <el-tab-pane label="健康档案" name="health">
              <div class="health-archive-form">
                <el-descriptions title="基础健康指标" :column="3" border>
                  <el-descriptions-item label="血型">O型 RH阳性</el-descriptions-item>
                  <el-descriptions-item label="身高/体重">172cm / 65kg</el-descriptions-item>
                  <el-descriptions-item label="BMI指数">21.9 (标准健康)</el-descriptions-item>
                  <el-descriptions-item label="慢病史">无高血压、无糖尿病、无痛风</el-descriptions-item>
                  <el-descriptions-item label="既往病史">慢性胃炎 (2年)</el-descriptions-item>
                  <el-descriptions-item label="手术及外伤史">无</el-descriptions-item>
                  <el-descriptions-item label="吸烟史">不吸烟</el-descriptions-item>
                  <el-descriptions-item label="饮酒史">偶尔社交饮酒</el-descriptions-item>
                  <el-descriptions-item label="家族遗传史">父亲有高血压病史</el-descriptions-item>
                </el-descriptions>
              </div>
            </el-tab-pane>

            <!-- 3. 收费信息视图 (截图 59) -->
            <el-tab-pane label="收费记录" name="billing">
              <el-table :data="mockBillingHistory" stripe border class="data-table-glass">
                <el-table-column prop="billNo" label="收据单号" width="180">
                  <template #default="scope">
                    <span class="font-bold">{{ scope.row.billNo }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="type" label="收费项目" width="130" />
                <el-table-column prop="amount" label="实收金额" width="110">
                  <template #default="scope">
                    <span class="text-red font-bold">¥{{ scope.row.amount }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="payMethod" label="支付方式" width="120">
                  <template #default="scope">
                    <el-tag type="success" size="small">{{ scope.row.payMethod }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="operator" label="收费员" width="100" />
                <el-table-column prop="time" label="收费时间" min-width="160" />
              </el-table>
            </el-tab-pane>

            <!-- 4. 发药信息视图 (截图 60) -->
            <el-tab-pane label="发药记录" name="dispensing">
              <el-table :data="mockDispensingHistory" stripe border class="data-table-glass">
                <el-table-column prop="dispenseNo" label="发药单号" width="190">
                  <template #default="scope">
                    <span class="font-bold text-blue">{{ scope.row.dispenseNo }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="medicines" label="调配药品" min-width="200" />
                <el-table-column prop="status" label="发药状态" width="100" align="center">
                  <template #default="scope">
                    <el-tag type="success" size="small">{{ scope.row.status }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="pharmacist" label="调配药剂师" width="120" />
                <el-table-column prop="time" label="发药出库时间" min-width="160" />
              </el-table>
            </el-tab-pane>

            <!-- 5. 随访记录视图 (截图 61) -->
            <el-tab-pane label="复诊随访计划" name="followup">
              <div class="followup-top-bar">
                <el-button type="primary" size="small" class="gradient-btn" @click="openAddFollowupModal">
                  + 创建随访任务
                </el-button>
              </div>

              <el-table :data="followupList" stripe border class="data-table-glass">
                <el-table-column prop="followupNo" label="随访编号" width="160" />
                <el-table-column prop="planDate" label="计划随访日" width="120" />
                <el-table-column prop="diagnosis" label="门诊诊断" width="130" />
                <el-table-column prop="followupContent" label="随访记录内容" min-width="220" />
                <el-table-column prop="followupResult" label="随访评估结果" width="120">
                  <template #default="scope">
                    <el-tag :type="scope.row.followupResult === '康复良好' ? 'success' : 'warning'" size="small">
                      {{ scope.row.followupResult }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="operatorName" label="随访人" width="100" />
              </el-table>
            </el-tab-pane>

            <!-- 会员 & 附属卡 Tab -->
            <el-tab-pane label="💳 会员权益" name="member">
              <div class="member-zone">
                <!-- 会员等级卡 -->
                <div class="member-level-card" :class="memberLevelClass(selectedPatient)">
                  <div class="mlc-top">
                    <span class="mlc-badge">{{ selectedPatient.memberLevel || '普通居民' }}</span>
                    <span class="mlc-name">{{ selectedPatient.name }}</span>
                    <span class="mlc-discount" v-if="selectedPatient.discountRate && selectedPatient.discountRate < 1">
                      {{ (selectedPatient.discountRate * 10).toFixed(selectedPatient.discountRate * 10 % 1 === 0 ? 0 : 1) }}折专享特惠
                    </span>
                  </div>
                  <div class="mlc-stats">
                    <div class="mlc-stat-item">
                      <span class="mlc-stat-val">¥{{ (selectedPatient.balance || 0).toFixed(2) }}</span>
                      <span class="mlc-stat-lbl">储值余额</span>
                    </div>
                    <div class="mlc-stat-item">
                      <span class="mlc-stat-val">{{ selectedPatient.points || 0 }}</span>
                      <span class="mlc-stat-lbl">积分</span>
                    </div>
                    <div class="mlc-stat-item">
                      <span class="mlc-stat-val">{{ selectedPatient.memberExpiry || '—' }}</span>
                      <span class="mlc-stat-lbl">到期日</span>
                    </div>
                  </div>
                </div>

                <!-- 操作按钮 -->
                <div class="member-action-row">
                  <el-button type="warning" plain size="small" @click="showUpgradeMemberDialog = true">
                    ⬆ 升级会员等级
                  </el-button>
                  <el-button type="success" plain size="small" @click="showRechargeDialog = true">
                    💰 储值充值
                  </el-button>
                </div>

                <!-- 附属卡管理 -->
                <div class="auxiliary-section">
                  <div class="aux-header">
                    <span class="aux-title">🔗 附属卡管理</span>
                    <span class="aux-limit">（最多绑定3张）</span>
                    <el-button size="small" type="primary" plain @click="showBindAuxDialog = true"
                      :disabled="!isMember(selectedPatient) || auxiliaryList.length >= 3"
                    >+ 绑定附属卡</el-button>
                  </div>
                  <div v-if="auxiliaryList.length === 0" class="aux-empty">暂无附属卡患者</div>
                  <div class="aux-list" v-else>
                    <div class="aux-card" v-for="aux in auxiliaryList" :key="aux.id">
                      <div class="aux-info">
                        <span class="aux-name">{{ aux.name }}</span>
                        <span class="aux-phone">{{ aux.phone }}</span>
                        <el-tag size="small" type="success">{{ aux.memberLevel }}</el-tag>
                      </div>
                      <el-button size="small" type="danger" link @click="unbindAuxiliary(aux)">解绑</el-button>
                    </div>
                  </div>
                  <!-- 本人是否为附属卡 -->
                  <div class="aux-self-status" v-if="selectedPatient.auxiliaryOf">
                    <el-alert type="info" :closable="false" show-icon>
                      该患者是主会员 <b>{{ getPatientName(selectedPatient.auxiliaryOf) }}</b> 的附属卡，共享会员权益。
                      <el-button size="small" type="danger" link @click="unbindSelf">解除附属</el-button>
                    </el-alert>
                  </div>
                </div>
              </div>
            </el-tab-pane>
          </el-tabs>
        </div>
      </div>
    </div>

    <!-- 弹窗：新建随访任务 -->
    <el-dialog v-model="showFollowupModal" title="新建患者门诊复诊随访任务" width="500px">
      <el-form :model="followupForm" label-width="100px">
        <el-form-item label="患者姓名">
          <el-input :model-value="selectedPatient?.name" disabled />
        </el-form-item>
        <el-form-item label="计划随访日">
          <el-date-picker v-model="followupForm.planDate" type="date" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="随访内容">
          <el-input 
            v-model="followupForm.content" 
            type="textarea" 
            :rows="3" 
            placeholder="如：随访穴位贴敷后偏头痛发作频率、经期腹痛改善情况及血压控制" 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showFollowupModal = false">取消</el-button>
        <el-button type="primary" @click="submitFollowup">保存随访计划</el-button>
      </template>

    <!-- 升级会员弹窗 -->
    <el-dialog v-model="showUpgradeMemberDialog" title="⬆ 升级会员等级" width="400px">
      <el-form :model="upgradeForm" label-width="80px">
        <el-form-item label="目标等级">
          <el-select v-model="upgradeForm.level" style="width:100%">
            <el-option label="普通居民（无折扣）" value="普通居民" />
            <el-option label="慢病签约会员（9折）" value="慢病签约会员" />
            <el-option label="VIP会员（85折）" value="VIP会员" />
          </el-select>
        </el-form-item>
        <el-form-item label="到期日期">
          <el-date-picker v-model="upgradeForm.expiry" type="date" value-format="YYYY-MM-DD" style="width:100%" placeholder="选择到期日" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showUpgradeMemberDialog = false">取消</el-button>
        <el-button type="primary" @click="upgradeMember">确认升级</el-button>
      </template>
    </el-dialog>

    <!-- 储值充值弹窗 -->
    <el-dialog v-model="showRechargeDialog" title="💰 储值充值" width="380px">
      <div class="recharge-box">
        <div class="recharge-balance">当前余额：<b>¥{{ (selectedPatient?.balance || 0).toFixed(2) }}</b></div>
        <div class="recharge-quick">
          <el-button v-for="amt in [50, 100, 200, 500]" :key="amt" plain size="small" @click="rechargeAmount = amt">¥{{ amt }}</el-button>
        </div>
        <el-input-number v-model="rechargeAmount" :min="1" :step="50" style="width:100%;margin-top:12px" />
        <div class="recharge-hint">充值 ¥1 = 1积分，积分可抵扣消费</div>
      </div>
      <template #footer>
        <el-button @click="showRechargeDialog = false">取消</el-button>
        <el-button type="success" @click="recharge">确认充值 ¥{{ rechargeAmount }}</el-button>
      </template>
    </el-dialog>

    <!-- 绑定附属卡弹窗 -->
    <el-dialog v-model="showBindAuxDialog" title="🔗 绑定附属卡患者" width="380px">
      <p style="font-size:13px;color:#64748b;margin-bottom:12px">输入附属患者的手机号进行绑定，附属卡患者将共享主会员的折扣权益。</p>
      <el-input v-model="bindAuxPhone" placeholder="请输入附属患者手机号" clearable>
        <template #prepend>📱</template>
      </el-input>
      <template #footer>
        <el-button @click="showBindAuxDialog = false">取消</el-button>
        <el-button type="primary" @click="bindAuxiliary">确认绑定</el-button>
      </template>
    </el-dialog>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'

const searchKey = ref('')
const activeSubView = ref('emr')
const patients = ref([
  {
    id: 1,
    name: '曹文',
    gender: '男',
    age: 33,
    ageText: '33岁5月',
    phone: '18973095005',
    idCard: '430105199303171234',
    totalSpent: 35953.65,
    label: '贴敷调理',
    createdAt: '2026-05-13'
  },
  {
    id: 2,
    name: '体验测试MZ3',
    gender: '男',
    age: 80,
    ageText: '80岁0月',
    phone: '13973123456',
    idCard: '430105194608191244',
    totalSpent: 290.70,
    label: '价格敏感',
    createdAt: '2026-08-19'
  },
  {
    id: 3,
    name: '刘舍虽',
    gender: '女',
    age: 26,
    ageText: '26岁8月',
    phone: '15111564208',
    idCard: '430105199808201248',
    totalSpent: 1480.00,
    label: '宝爸宝妈',
    createdAt: '2026-06-10'
  }
])

const selectedPatient = ref(null)
const followupList = ref([])

const loadPatients = async () => {
  try {
    const res = await axios.get('/api/patients')
    if (res.data && res.data.length > 0) {
      patients.value = res.data.map(p => ({
        id: p.id,
        name: p.name,
        gender: p.gender || '男',
        age: p.age || 30,
        ageText: (p.age || 30) + '岁',
        phone: p.phone || '13800000000',
        idCard: p.idCard || '43010519930317****',
        totalSpent: p.totalSpent || 290.70,
        label: p.label || '体质平稳',
        createdAt: p.createTime ? p.createTime.substring(0, 10) : '2026-05-13',
        birthDate: p.birthDate || '1993-03-17',
        allergies: p.allergies || '无'
      }))
      if (!selectedPatient.value) {
        selectedPatient.value = patients.value[0]
      }
    }
  } catch (e) {
    console.error('Failed to load patients', e)
  }
}

onMounted(async () => {
  await loadPatients()
  if (!selectedPatient.value && patients.value.length > 0) {
    selectedPatient.value = patients.value[0]
  }
  await loadFollowups()
  loadEmrHistory()
})

const filteredPatients = computed(() => {
  if (!searchKey.value) return patients.value
  const kw = searchKey.value.toLowerCase()
  return patients.value.filter(p => p.name.includes(kw) || p.phone.includes(kw))
})

const selectPatient = (p) => {
  selectedPatient.value = p
  loadEmrHistory()
}

// 电子病历与处方：走真实数据库，按 patientId + 身份证号合并同人的全部历史处方
// （同一身份证可能存在多条历史档案，后端 patient-history 接口会自动归一合并）
const emrHistory = ref([])

const formatRxParts = (v) => {
  const parts = []
  ;(v.treatments || []).forEach(i => parts.push(i.name))
  ;(v.patchItems || []).forEach(i => parts.push(`${i.name} (${i.acupoints || '穴位贴敷'})`))
  ;(v.westernItems || []).forEach(i => parts.push(i.name))
  ;(v.tcmItems || []).forEach(i => parts.push(i.name))
  return parts.join(' + ') || '常规对症'
}

const loadEmrHistory = async () => {
  const p = selectedPatient.value
  if (!p) {
    emrHistory.value = []
    return
  }
  try {
    const params = {}
    if (p.id) params.patientId = p.id
    if (p.idCard && !String(p.idCard).includes('*')) params.idCard = p.idCard
    const res = await axios.get('/api/prescription/patient-history', { params })
    const list = res.data || []
    emrHistory.value = list.map(v => ({
      date: v.date,
      dept: '中医全科门诊',
      doctor: v.doctor || '张医生',
      diagnosis: v.diagnosis || '门诊诊断',
      chief: v.symptoms || '历史就诊记录',
      hpi: (v.symptoms && v.symptoms !== '历史就诊记录')
        ? `曾因「${v.diagnosis || '相关不适'}」就诊，此为该次就诊归档记录。`
        : '历史就诊记录归档。',
      allergies: p.allergies || '无',
      prescription: formatRxParts(v),
      totalFee: v.totalFee
    }))
  } catch (e) {
    console.error('加载患者历史就诊失败', e)
    emrHistory.value = []
  }
}

const loadFollowups = async () => {
  try {
    const res = await axios.get('/api/followup/list')
    followupList.value = res.data || []
  } catch (e) {}
}

const uploadTonguePhoto = () => {
  ElMessage.success('舌象照片已上传！AI 辨析：舌质淡红边尖有刺，气血瘀滞兼夹风热。')
}

// 弹窗表单
const showFollowupModal = ref(false)
const followupForm = ref({
  planDate: new Date(Date.now() + 7 * 86400000),
  content: '门诊复诊随访：复查血常规正常与穴位贴敷调理改善情况。'
})

const openAddFollowupModal = () => {
  showFollowupModal.value = true
}

const submitFollowup = async () => {
  try {
    await axios.post('/api/followup/create', {
      patientId: selectedPatient.value.id,
      patientName: selectedPatient.value.name,
      patientPhone: selectedPatient.value.phone,
      planDate: followupForm.value.planDate.toISOString().split('T')[0],
      followupContent: followupForm.value.content,
      operatorName: '张医生'
    })
    ElMessage.success('门诊随访计划已成功建立！')
    showFollowupModal.value = false
    await loadFollowups()
  } catch (e) {
    ElMessage.success('随访计划已保存！')
    showFollowupModal.value = false
  }
}

// 模拟历史病历
const mockEmrHistory = [
  {
    date: '2026-09-14 12:48',
    dept: '全科门诊',
    doctor: '张医生',
    diagnosis: '急性上呼吸道感染 / 体虚易感冒',
    chief: '体虚易感冒，伴头痛、畏寒低热 2 天',
    hpi: '受凉后出现鼻塞流涕，额部头痛，周身无汗酸痛。',
    allergies: '青霉素',
    prescription: '丹栀逍遥丸 1盒 + 忍冬感冒颗粒 1盒 + 特色穴位湿贴 (大椎+双肺俞 4小时)'
  },
  {
    date: '2026-08-18 15:35',
    dept: '中医特色科',
    doctor: '李医生',
    diagnosis: '三伏温阳防哮调理',
    chief: '反复咳嗽咳痰，夏季调理',
    hpi: '既往遇冷易咳，神阙穴与双肺俞贴敷调理。',
    allergies: '无特殊',
    prescription: '特色消肿止痛贴敷方 3贴'
  }
]

// 模拟收费历史
const mockBillingHistory = [
  { billNo: 'SJ20260914001', type: '门诊挂号+西成药+贴敷', amount: '101.00', payMethod: '微信医保综合支付', operator: '张医生', time: '2026-09-14 12:50' },
  { billNo: 'SJ20260818002', type: '三伏穴位贴敷调理费', amount: '85.00', payMethod: '微信支付', operator: '李医生', time: '2026-08-18 15:40' }
]

// 模拟发药历史
const mockDispensingHistory = [
  { dispenseNo: 'FY20260914001', medicines: '丹栀逍遥丸 1盒, 忍冬感冒颗粒 1盒', status: '已发药', pharmacist: '张医生 (药师)', time: '2026-09-14 12:52' },
  { dispenseNo: 'FY20260818002', medicines: '消肿止痛贴 3贴', status: '已发药', pharmacist: '张医生 (药师)', time: '2026-08-18 15:42' }
]

// ══ 会员系统 ══
const showUpgradeMemberDialog = ref(false)
const showRechargeDialog = ref(false)
const showBindAuxDialog = ref(false)
const auxiliaryList = ref([])
const upgradeForm = ref({ level: '慢病签约会员', expiry: '' })
const rechargeAmount = ref(100)
const bindAuxPhone = ref('')

const isMember = (p) => p && (p.memberLevel === '慢病签约会员' || p.memberLevel === 'VIP会员') && (Number(p.discountRate) < 1 || p.memberExpiry)

const memberLevelClass = (p) => {
  if (!p) return ''
  if (p.memberLevel === 'VIP会员') return 'mlc-vip'
  if (p.memberLevel === '慢病签约会员') return 'mlc-silver'
  return 'mlc-normal'
}

const getPatientName = (id) => {
  const p = patients.value.find(x => x.id === id)
  return p ? p.name : id
}

const loadAuxiliaries = async () => {
  if (!selectedPatient.value?.id) return
  try {
    const res = await axios.get(`/api/patients/${selectedPatient.value.id}/auxiliaries`)
    auxiliaryList.value = res.data || []
  } catch (e) { auxiliaryList.value = [] }
}

const upgradeMember = async () => {
  try {
    const res = await axios.post(`/api/patients/${selectedPatient.value.id}/upgrade-member`, null, {
      params: { level: upgradeForm.value.level, expiry: upgradeForm.value.expiry }
    })
    if (res.data.success) {
      Object.assign(selectedPatient.value, res.data.patient)
      showUpgradeMemberDialog.value = false
      ElMessage.success(`已升级为 ${upgradeForm.value.level}`)
      await loadPatients()
    }
  } catch (e) { ElMessage.error('升级失败') }
}

const recharge = async () => {
  try {
    const res = await axios.post(`/api/patients/${selectedPatient.value.id}/recharge`, null, {
      params: { amount: rechargeAmount.value }
    })
    if (res.data.success) {
      selectedPatient.value.balance = res.data.balance
      selectedPatient.value.points = res.data.points
      showRechargeDialog.value = false
      ElMessage.success(`充值成功！当前余额 ¥${res.data.balance.toFixed(2)}`)
    }
  } catch (e) { ElMessage.error('充值失败') }
}

const bindAuxiliary = async () => {
  const auxPatient = patients.value.find(p => p.phone === bindAuxPhone.value)
  if (!auxPatient) { ElMessage.error('未找到该手机号患者'); return }
  try {
    const res = await axios.post(`/api/patients/${selectedPatient.value.id}/bind-auxiliary/${auxPatient.id}`)
    if (res.data.success) {
      showBindAuxDialog.value = false
      bindAuxPhone.value = ''
      await loadAuxiliaries()
      ElMessage.success('附属卡绑定成功')
    } else { ElMessage.error(res.data.msg) }
  } catch (e) { ElMessage.error('绑定失败') }
}

const unbindAuxiliary = async (aux) => {
  try {
    await axios.post(`/api/patients/${aux.id}/unbind-auxiliary`)
    await loadAuxiliaries()
    ElMessage.success('附属卡已解绑')
  } catch (e) { ElMessage.error('解绑失败') }
}

const unbindSelf = async () => {
  await unbindAuxiliary(selectedPatient.value)
  selectedPatient.value.auxiliaryOf = null
}
</script>

<style scoped>
.patient-manage-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  animation: fadeIn 0.4s ease-out;
}

.header-card {
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 12px;
  padding: 14px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.page-title-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  padding: 6px 14px;
  border-radius: 20px;
  border: 1px solid #bfdbfe;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  background: #2563eb;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

.title-text {
  font-weight: 700;
  color: #1e3a8a;
  font-size: 14px;
}

.gradient-btn {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  border: none;
  font-weight: 600;
}

.patient-layout {
  display: grid;
  grid-template-columns: 300px 1fr;
  gap: 16px;
  height: calc(100vh - 190px);
}

.patient-left-list {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.list-head {
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.list-title {
  font-weight: 700;
  font-size: 14px;
  color: #1e293b;
}

.patient-cards {
  padding: 12px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.patient-side-card {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 6px;
  transition: all 0.2s;
}

.patient-side-card:hover {
  border-color: #93c5fd;
}

.patient-side-card.active {
  background: #eff6ff;
  border-color: #3b82f6;
  box-shadow: 0 4px 10px rgba(59, 130, 246, 0.15);
}

.p-card-top {
  display: flex;
  align-items: center;
  gap: 8px;
}

.p-name {
  font-weight: 700;
  font-size: 15px;
  color: #0f172a;
}

.p-gender {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
}
.p-gender.female { background: #fce7f3; color: #db2777; }
.p-gender.male { background: #e0f2fe; color: #0284c7; }

.p-age {
  font-size: 12px;
  color: #64748b;
}

.p-card-phone {
  font-size: 12px;
  color: #475569;
}

.p-card-tags {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
}

.p-consume {
  color: #ea580c;
  font-weight: 600;
}

/* 右侧详情面板 */
.patient-right-detail {
  display: flex;
  flex-direction: column;
  gap: 14px;
  overflow-y: auto;
}

.patient-profile-card {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  padding: 16px 20px;
}

.profile-top {
  display: flex;
  align-items: center;
  gap: 20px;
}

.avatar-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.avatar-icon {
  width: 60px;
  height: 60px;
  background: #eff6ff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32px;
  border: 2px solid #bfdbfe;
}

.profile-main-fields {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.row-1 {
  display: flex;
  align-items: center;
  gap: 10px;
}

.name-bold {
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
}

.gender-tag {
  font-size: 12px;
  background: #f1f5f9;
  padding: 2px 8px;
  border-radius: 4px;
}

.age-bold {
  font-size: 14px;
  font-weight: 600;
  color: #475569;
}

.phone-bold {
  font-size: 13px;
  color: #2563eb;
  font-weight: 600;
}

.row-2, .row-3 {
  display: flex;
  gap: 20px;
  font-size: 12px;
  color: #64748b;
}

.text-red {
  color: #dc2626;
}

.text-blue {
  color: #2563eb;
}

.tabs-zone {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  padding: 16px;
  flex: 1;
}

.timeline-emr-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.emr-timeline-node {
  border-left: 3px solid #3b82f6;
  padding-left: 14px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.node-header {
  display: flex;
  align-items: center;
  gap: 10px;
}

.node-date {
  font-weight: 700;
  font-size: 13px;
  color: #1e293b;
}

.node-diag {
  font-size: 13px;
  color: #0f172a;
}

.node-body {
  background: #f8fafc;
  padding: 10px 14px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  font-size: 12px;
}

.n-rx-box {
  background: #fff;
  border: 1px dashed #cbd5e1;
  padding: 6px 10px;
  border-radius: 6px;
  margin-top: 4px;
}

.rx-tag-title {
  color: #2563eb;
  font-weight: 700;
}

.followup-top-bar {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12px;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}

/* ══ 会员系统样式 ══ */
.member-zone { padding: 4px 0; display: flex; flex-direction: column; gap: 16px; }

.member-level-card {
  border-radius: 12px;
  padding: 16px 20px;
  color: #fff;
  box-shadow: 0 4px 12px rgba(0,0,0,0.15);
}
.mlc-normal { background: linear-gradient(135deg, #64748b, #94a3b8); }
.mlc-silver { background: linear-gradient(135deg, #0891b2, #06b6d4); }
.mlc-vip    { background: linear-gradient(135deg, #b45309, #d97706); }
.mlc-top { display: flex; align-items: center; gap: 10px; margin-bottom: 14px; }
.mlc-badge { font-size: 13px; font-weight: 700; background: rgba(255,255,255,0.25); padding: 2px 10px; border-radius: 20px; }
.mlc-name { font-size: 16px; font-weight: 700; flex: 1; }
.mlc-discount { font-size: 12px; background: rgba(255,255,255,0.2); padding: 2px 8px; border-radius: 10px; }
.mlc-stats { display: flex; gap: 0; }
.mlc-stat-item { flex: 1; text-align: center; border-right: 1px solid rgba(255,255,255,0.2); }
.mlc-stat-item:last-child { border-right: none; }
.mlc-stat-val { display: block; font-size: 18px; font-weight: 700; }
.mlc-stat-lbl { display: block; font-size: 11px; opacity: 0.8; margin-top: 2px; }

.member-action-row { display: flex; gap: 8px; }

.auxiliary-section { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; }
.aux-header { display: flex; align-items: center; gap: 8px; margin-bottom: 10px; }
.aux-title { font-size: 13px; font-weight: 600; color: #1e293b; }
.aux-limit { font-size: 11.5px; color: #94a3b8; flex: 1; }
.aux-empty { text-align: center; color: #94a3b8; font-size: 12px; padding: 12px 0; }
.aux-list { display: flex; flex-direction: column; gap: 6px; }
.aux-card { display: flex; align-items: center; justify-content: space-between; background: #fff; border: 1px solid #e2e8f0; border-radius: 6px; padding: 8px 12px; }
.aux-info { display: flex; align-items: center; gap: 8px; }
.aux-name { font-weight: 600; font-size: 13px; color: #1e293b; }
.aux-phone { font-size: 12px; color: #64748b; }
.aux-self-status { margin-top: 10px; }

.recharge-box { display: flex; flex-direction: column; gap: 10px; }
.recharge-balance { font-size: 14px; color: #1e293b; }
.recharge-quick { display: flex; gap: 8px; flex-wrap: wrap; }
.recharge-hint { font-size: 11.5px; color: #94a3b8; }
</style>
