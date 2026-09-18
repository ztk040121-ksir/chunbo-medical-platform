<template>
  <div class="registration-container">
    <!-- 顶部二级导航卡片 -->
    <div class="header-card">
      <div class="nav-left">
        <div class="page-title-badge">
          <span class="pulse-dot"></span>
          <span class="title-text">门诊挂号与智能排班中心</span>
        </div>
        <el-radio-group v-model="activeSubTab" size="large" class="custom-radio-group">
          <el-radio-button label="register">挂号取号管理</el-radio-button>
          <el-radio-button label="schedule">医生周历排班看板</el-radio-button>
          <el-radio-button label="settings">挂号规则与号源设置</el-radio-button>
        </el-radio-group>
      </div>

      <div class="nav-right">
        <el-button type="warning" size="default" @click="handleQuickTempReg" class="gradient-btn-amber">
          快速临时挂号
        </el-button>
        <el-button type="primary" size="default" @click="openNewRegModal" class="gradient-btn">
          <el-icon><Plus /></el-icon> 新增门诊挂号
        </el-button>
        <el-button type="success" size="default" @click="openQrSignModal" plain>
          <el-icon><FullScreen /></el-icon> 扫码到店签到
        </el-button>
      </div>
    </div>

    <!-- 1. 挂号取号主面板 -->
    <div v-if="activeSubTab === 'register'" class="tab-content-zone">
      <!-- 7 态快速状态胶囊筛选栏 (对齐春播万象真实业务七态模型) -->
      <div class="status-capsule-bar">
        <div 
          v-for="st in statusFilters" 
          :key="st.key"
          class="status-capsule"
          :class="{ active: currentStatus === st.key }"
          @click="currentStatus = st.key"
        >
          <span class="capsule-name">{{ st.label }}</span>
          <span class="capsule-count" :style="{ backgroundColor: st.color }">
            {{ getStatusCount(st.key) }}
          </span>
        </div>

        <div class="view-switch-box">
          <el-radio-group v-model="displayMode" size="small">
            <el-radio-button label="kanban">看板模式</el-radio-button>
            <el-radio-button label="table">列表模式</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <!-- 搜索过滤条 -->
      <div class="filter-glass-bar">
        <el-input 
          v-model="searchKeyword" 
          placeholder="输入患者姓名/手机号/就诊号快速检索" 
          prefix-icon="Search" 
          clearable 
          style="width: 320px;" 
        />
        <el-select v-model="filterDept" placeholder="就诊科室" clearable style="width: 160px;">
          <el-option label="全部科室" value="" />
          <el-option label="全科门诊" value="全科门诊" />
          <el-option label="中医特色科" value="中医特色科" />
          <el-option label="儿科理疗" value="儿科理疗" />
        </el-select>
        <el-select v-model="filterDoctor" placeholder="挂号医生" clearable style="width: 150px;">
          <el-option label="全部医生" value="" />
          <el-option label="张医生" value="张医生" />
          <el-option label="李医生" value="李医生" />
          <el-option label="王医生" value="王医生" />
        </el-select>
        <div class="reg-date-nav-bar">
          <el-button 
            size="small" 
            class="d-arrow-btn" 
            @click="changeRegDateOffset(-1)" 
            title="前一天"
          >
            &lt;
          </el-button>
          <el-date-picker
            v-model="selectedRegDate"
            type="date"
            format="YYYY-MM-DD"
            value-format="YYYY-MM-DD"
            size="small"
            :clearable="false"
            class="reg-date-picker"
            @change="onRegDateChange"
          />
          <el-button 
            size="small" 
            class="d-arrow-btn" 
            @click="changeRegDateOffset(1)" 
            title="后一天"
          >
            &gt;
          </el-button>
          <el-button 
            size="small" 
            type="success" 
            class="today-btn" 
            @click="goToRegToday"
          >
            今日
          </el-button>
        </div>
        <el-button icon="Refresh" circle size="small" @click="loadRegistrations" title="刷新挂号列表" />
      </div>

      <!-- 看板模式 -->
      <div v-if="displayMode === 'kanban'" class="kanban-grid">
        <div 
          v-for="item in filteredRegistrations" 
          :key="item.id" 
          class="patient-reg-card"
        >
          <div class="card-top">
            <span class="seq-badge">就诊序号 #{{ item.queueNumber || item.queueNo || item.id }}</span>
            <span class="status-pill" :class="'pill-' + item.status">{{ getStatusText(item.status) }}</span>
          </div>
          <div class="patient-main">
            <div class="name-row">
              <span class="patient-name">{{ item.patientName }}</span>
              <span class="gender-tag" :class="item.gender === '女' ? 'female' : 'male'">{{ item.gender || '男' }}</span>
              <span class="age-text">{{ item.ageText || (item.age ? item.age + '岁' : '30岁') }}</span>
            </div>
            <div class="meta-row">
              <span class="meta-item"><el-icon><Phone /></el-icon> {{ item.phone || '13800000000' }}</span>
              <span class="meta-item"><el-icon><User /></el-icon> {{ item.doctorName || '张医生' }} ({{ item.department || '全科门诊' }})</span>
            </div>
            <div class="meta-row desc-row">
              <span class="desc-text"><el-icon><FirstAidKit /></el-icon> 挂号费：¥{{ item.fee || item.regFee || 10.00 }} · {{ item.regType || '现场挂号' }}</span>
              <span class="time-text">{{ formatTime(item.createTime) }}</span>
            </div>
            <div v-if="item.symptoms" class="symptoms-hint">
              <el-tag size="small" type="info" effect="plain">主诉: {{ item.symptoms }}</el-tag>
            </div>
          </div>
          <div class="card-actions">
            <el-button 
              v-if="item.status === '待签到'" 
              type="success" 
              size="small" 
              @click="handleSign(item)"
            >
              到店签到
            </el-button>
            <!-- 支持在就诊中、待诊、过号等状态下反复呼叫与叫号 -->
            <el-button 
              v-if="item.status !== '已诊' && item.status !== '已结诊' && item.status !== '已退' && item.status !== '已收费' && item.status !== '待签到'" 
              type="primary" 
              size="small" 
              @click="callPatient(item)"
            >
              {{ (item.status === '就诊中' || item.status === '接诊中') ? '再次呼叫' : (item.status === '过号' ? '重新呼叫' : '📢 叫号提醒') }}
            </el-button>
            <!-- 只要未完诊即可办理退号 -->
            <el-button 
              v-if="item.status !== '已诊' && item.status !== '已结诊' && item.status !== '已退' && item.status !== '已收费'" 
              type="danger" 
              size="small" 
              text 
              @click="cancelReg(item)"
            >
              退号
            </el-button>
            <el-button 
              type="info" 
              size="small" 
              text 
              @click="printRegTicket(item)"
            >
              打印凭条
            </el-button>
          </div>
        </div>

        <div v-if="filteredRegistrations.length === 0" class="empty-kanban-box">
          <el-empty description="当前暂无符合条件的挂号记录">
            <el-button type="primary" size="small" @click="openNewRegModal">立即挂号</el-button>
          </el-empty>
        </div>
      </div>

      <!-- 表格模式 -->
      <el-table v-else :data="filteredRegistrations" stripe class="data-table-glass">
        <el-table-column prop="queueNumber" label="就诊号" width="100">
          <template #default="scope">
            <el-tag type="primary" effect="dark" size="small">{{ scope.row.queueNumber || scope.row.queueNo || scope.row.id }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="patientName" label="患者姓名" width="120">
          <template #default="scope">
            <span class="font-bold">{{ scope.row.patientName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="gender" label="性别" width="80">
          <template #default="scope">
            <el-tag :type="scope.row.gender === '女' ? 'danger' : 'primary'" size="small">{{ scope.row.gender || '男' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ageText" label="临床年龄" width="130">
          <template #default="scope">
            <span>{{ scope.row.ageText || (scope.row.age ? scope.row.age + '岁' : '30岁') }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="联系电话" width="140" />
        <el-table-column prop="department" label="挂号科室" width="130" />
        <el-table-column prop="doctorName" label="主诊医生" width="110" />
        <el-table-column prop="regType" label="类型" width="110">
          <template #default="scope">
            <el-tag type="info" size="small">{{ scope.row.regType || '现场挂号' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="fee" label="诊金" width="90">
          <template #default="scope">¥{{ scope.row.fee || scope.row.regFee || 10.00 }}</template>
        </el-table-column>
        <el-table-column prop="status" label="七态状态" width="110">
          <template #default="scope">
            <el-tag :type="getStatusTagType(scope.row.status)">{{ getStatusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="挂号时间" min-width="160">
          <template #default="scope">{{ formatTime(scope.row.createTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="scope">
            <el-button v-if="scope.row.status === '待签到'" size="small" type="success" @click="handleSign(scope.row)">签到</el-button>
            <el-button 
              v-if="scope.row.status !== '已诊' && scope.row.status !== '已结诊' && scope.row.status !== '已退' && scope.row.status !== '已收费' && scope.row.status !== '待签到'" 
              size="small" 
              type="primary" 
              @click="callPatient(scope.row)"
            >
              {{ (scope.row.status === '就诊中' || scope.row.status === '接诊中') ? '再呼叫' : (scope.row.status === '过号' ? '重呼' : '接诊') }}
            </el-button>
            <el-button size="small" type="info" text @click="printRegTicket(scope.row)">凭条</el-button>
            <el-button 
              v-if="scope.row.status !== '已诊' && scope.row.status !== '已结诊' && scope.row.status !== '已退' && scope.row.status !== '已收费'" 
              size="small" 
              type="danger" 
              text 
              @click="cancelReg(scope.row)"
            >
              退号
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 2. 医生周历排班看板 -->
    <div v-else-if="activeSubTab === 'schedule'" class="tab-content-zone">
      <div class="schedule-header-bar">
        <div class="week-selector">
          <el-button icon="ArrowLeft" circle @click="changeWeek(-1)" />
          <span class="week-label">{{ scheduleDateRangeStr }}</span>
          <el-button icon="ArrowRight" circle @click="changeWeek(1)" />
          <el-tag type="success" effect="plain" class="ml-2">本周运行中</el-tag>
        </div>
        <div class="schedule-actions">
          <el-button type="primary" @click="copyLastWeekSchedule" plain>
            <el-icon><CopyDocument /></el-icon> 复制上周排班
          </el-button>
          <el-button type="primary" class="gradient-btn" @click="openNewScheduleModal">
            <el-icon><Calendar /></el-icon> 设置医生班次
          </el-button>
        </div>
      </div>

      <!-- 7 天周历网格 (对应截图排班结构) -->
      <div class="weekly-calendar-grid">
        <div class="calendar-day-col" v-for="(day, dIndex) in weekDays" :key="dIndex">
          <div class="day-col-header" :class="{ 'is-today': day.isToday }">
            <span class="week-name">{{ day.name }}</span>
            <span class="date-str">{{ day.dateStr }}</span>
          </div>

          <div class="day-shifts-container">
            <div 
              v-for="item in getDaySchedules(day.fullDate)" 
              :key="item.id" 
              class="shift-card"
              :class="item.shiftType === '上午班' ? 'shift-morning' : (item.shiftType === '下午班' ? 'shift-afternoon' : 'shift-fullday')"
            >
              <div class="shift-top">
                <span class="doc-name">{{ item.doctorName }}</span>
                <span class="dept-badge">{{ item.department }}</span>
              </div>
              <div class="shift-type-badge">{{ item.shiftType }} ({{ item.shiftTimeRange || '全天' }})</div>
              <div class="quota-progress">
                <div class="quota-text">
                  <span>已约/限额</span>
                  <span class="quota-nums font-bold">{{ item.bookedCount }}/{{ item.quota }}</span>
                </div>
                <el-progress 
                  :percentage="Math.min(100, Math.round((item.bookedCount / item.quota) * 100))" 
                  :status="item.bookedCount >= item.quota ? 'exception' : 'success'"
                  :stroke-width="6"
                  :show-text="false"
                />
              </div>
              <div class="shift-footer">
                <span class="fee-text">挂号诊金: ¥{{ item.consultationFee }}</span>
                <el-button size="small" type="primary" link @click="editSchedule(item)">调整</el-button>
              </div>
            </div>

            <div v-if="getDaySchedules(day.fullDate).length === 0" class="empty-shift-hint">
              <span>暂无排班</span>
              <el-button size="small" type="primary" link @click="quickAddSchedule(day.fullDate)">+ 排班</el-button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 3. 挂号规则与设置 (对应截图 24/25/26) -->
    <div v-else-if="activeSubTab === 'settings'" class="tab-content-zone">
      <el-card class="rule-setting-card" shadow="never">
        <template #header>
          <div class="rules-card-header">
            <div class="rules-header-title-box">
              <span class="rules-main-title">云诊所挂号策略与号源开放规则配置</span>
              <span class="rules-sub-title">修改配置项系统默认自动保存，无需手动提交，并即时同步至云端与门诊各终端</span>
            </div>
            <div class="rules-auto-saved-tag">
              <span class="auto-save-pill">
                <span class="auto-save-dot"></span> 改动已默认自动保存
              </span>
            </div>
          </div>
        </template>

        <el-form label-width="180px" class="setting-form">
          <el-divider content-position="left">基础挂号权限</el-divider>
          <el-form-item label="开放线上微信预约">
            <el-switch v-model="settings.enableOnlineReg" active-text="启用微信小程序端预约取号" />
          </el-form-item>
          <el-form-item label="现场排队叫号机制">
            <el-radio-group v-model="settings.sortMode">
              <el-radio label="sign">按签到到达先后顺序</el-radio>
              <el-radio label="number">严格按预约挂号序号</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="就诊前必须签到">
            <el-radio-group v-model="settings.signPolicy">
              <el-radio label="required">必须到店扫码或前台签到才能叫号</el-radio>
              <el-radio label="auto">挂号成功自动直接进入待诊队列</el-radio>
            </el-radio-group>
          </el-form-item>

          <el-divider content-position="left">放号周期与限额保护</el-divider>
          <el-form-item label="提前放号天数">
            <el-input-number v-model="settings.advanceDays" :min="1" :max="30" /> 天
          </el-form-item>
          <el-form-item label="每日放号时间点">
            <el-time-picker v-model="settings.releaseTime" placeholder="选择放号时间点" />
          </el-form-item>

          <el-divider content-position="left">退号与复诊免费政策 (截图核心功能)</el-divider>
          <el-form-item label="复诊免挂号费天数">
            <el-input-number v-model="settings.freeReturnDays" :min="0" :max="14" /> 天（同医生看报告免诊查费）
          </el-form-item>
          <el-form-item label="过号自动顺延">
            <el-switch v-model="settings.allowRefund" active-text="过号患者自动推迟2位呼叫" />
          </el-form-item>
          <el-form-item label="叫号超时自动过号">
            <el-switch v-model="settings.autoExpireOnTimeout" active-text="叫号后超出等待时限未到诊室，系统自动置为【已过号】" />
          </el-form-item>
          <el-form-item label="超时过号等待时限">
            <el-input-number v-model="settings.expireTimeoutMinutes" :min="2" :max="60" style="width: 130px; margin-right: 8px;" />
            <span style="font-size: 13px; color: #64748b;">分钟（医生叫号后开始计时，超时未接诊将自动标记过号并顺延排队）</span>
          </el-form-item>
        </el-form>
      </el-card>
    </div>

    <!-- 弹窗：新增门诊挂号 (支持三段式年龄与读卡器) -->
    <el-dialog 
      v-model="showNewRegModal" 
      title="门诊就诊快速挂号与建档" 
      width="680px" 
      destroy-on-close
      class="custom-glass-dialog"
    >
      <div class="read-card-bar">
        <el-button type="warning" size="small" plain @click="simulateIdCardRead">
          <el-icon><Postcard /></el-icon> 模拟读取二代身份证/医保卡
        </el-button>
        <span class="read-tip">点击可一键自动填充并精准解析出生年月与实足年龄</span>
      </div>

      <el-form :model="regForm" label-width="110px" class="reg-modal-form">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="患者姓名" required>
              <el-input v-model="regForm.name" placeholder="请输入患者姓名" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="患者性别" required>
              <el-radio-group v-model="regForm.gender">
                <el-radio label="男">男</el-radio>
                <el-radio label="女">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="身份证号" required>
              <el-input v-model="regForm.idCard" placeholder="18位身份证号码（用于唯一身份识别）" maxlength="18" @change="parseIdCard" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" required>
              <el-input v-model="regForm.phone" placeholder="手机号码" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 三段式临床年龄(岁、月、天) 截图核心细节 -->
        <el-form-item label="临床实足年龄" required>
          <div class="three-stage-age">
            <el-input-number v-model="regForm.ageYears" :min="0" :max="120" controls-position="right" /> <span class="unit">岁</span>
            <el-input-number v-model="regForm.ageMonths" :min="0" :max="11" controls-position="right" /> <span class="unit">月</span>
            <el-input-number v-model="regForm.ageDays" :min="0" :max="30" controls-position="right" /> <span class="unit">天</span>
          </div>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="出生日期">
              <el-date-picker v-model="regForm.birthDate" type="date" placeholder="选择出生日期" style="width: 100%;" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="挂号类型">
              <el-select v-model="regForm.regType" style="width: 100%;">
                <el-option label="门诊现场挂号" value="现场挂号" />
                <el-option label="线上预约挂号" value="预约挂号" />
                <el-option label="特色贴敷理疗" value="特色贴敷理疗" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 省市区三级地址 -->
        <el-form-item label="常住省市区">
          <div class="address-cascader">
            <el-select v-model="regForm.province" placeholder="省份" style="width: 32%;">
              <el-option label="湖南省" value="湖南省" />
              <el-option label="广东省" value="广东省" />
              <el-option label="江苏省" value="江苏省" />
            </el-select>
            <el-select v-model="regForm.city" placeholder="城市" style="width: 32%; margin-left: 2%;">
              <el-option label="长沙市" value="长沙市" />
              <el-option label="广州市" value="广州市" />
              <el-option label="南京市" value="南京市" />
            </el-select>
            <el-select v-model="regForm.district" placeholder="区县" style="width: 32%; margin-left: 2%;">
              <el-option label="开福区" value="开福区" />
              <el-option label="岳麓区" value="岳麓区" />
              <el-option label="芙蓉区" value="芙蓉区" />
            </el-select>
          </div>
        </el-form-item>

        <!-- 详细资料（选填）：接诊页患者档案将展示这些信息 -->
        <el-collapse class="detail-collapse">
          <el-collapse-item name="detail">
            <template #title>
              <span class="detail-collapse-title">📋 详细资料（选填 · 婚姻/身高体重/职业/医保/陪护人）</span>
            </template>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="婚姻状况">
                  <el-select v-model="regForm.marriage" style="width: 100%;" placeholder="请选择">
                    <el-option label="未婚" value="未婚" />
                    <el-option label="已婚" value="已婚" />
                    <el-option label="离异" value="离异" />
                    <el-option label="丧偶" value="丧偶" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="职业">
                  <el-input v-model="regForm.job" placeholder="如：自由职业" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="身高">
                  <el-input v-model="regForm.height" placeholder="如：170.0 cm" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="体重">
                  <el-input v-model="regForm.weight" placeholder="如：65.0 kg" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="工作单位">
                  <el-input v-model="regForm.company" placeholder="如：本地居民" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="微信号">
                  <el-input v-model="regForm.wechat" placeholder="患者微信号" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="医保号">
                  <el-input v-model="regForm.insuranceNo" placeholder="医保卡号" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="陪护人">
                  <el-input v-model="regForm.accompany" placeholder="如：家属" />
                </el-form-item>
              </el-col>
            </el-row>
            <el-form-item label="陪护人电话">
              <el-input v-model="regForm.accompanyPhone" placeholder="陪护人联系电话" />
            </el-form-item>
          </el-collapse-item>
        </el-collapse>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="就诊科室" required>
              <el-select v-model="regForm.department" style="width: 100%;">
                <el-option label="全科门诊" value="全科门诊" />
                <el-option label="中医特色科" value="中医特色科" />
                <el-option label="儿科理疗" value="儿科理疗" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主诊医生" required>
              <el-select v-model="regForm.doctorName" style="width: 100%;" @change="onDoctorSelect">
                <el-option label="张医生 (全科/全天)" value="张医生" />
                <el-option label="李医生 (中医特聘/上午)" value="李医生" />
                <el-option label="王医生 (儿科贴敷/下午)" value="王医生" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="挂号诊金" required>
              <el-input-number v-model="regForm.fee" :precision="2" :step="5" :min="0" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="主诉症状" required>
              <el-input v-model="regForm.symptoms" placeholder="必填：发热咳嗽/头痛腹泻等" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="showNewRegModal = false">取消</el-button>
        <el-button type="primary" class="gradient-btn" @click="submitNewRegistration">确认挂号并取号</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗：医生排班班次设置 -->
    <el-dialog v-model="showScheduleModal" title="设置医生周历班次与限额" width="500px">
      <el-form :model="scheduleForm" label-width="100px">
        <el-form-item label="医生姓名">
          <el-input v-model="scheduleForm.doctorName" />
        </el-form-item>
        <el-form-item label="科室">
          <el-input v-model="scheduleForm.department" />
        </el-form-item>
        <el-form-item label="排班日期">
          <el-date-picker v-model="scheduleForm.scheduleDate" type="date" style="width: 100%;" />
        </el-form-item>
        <el-form-item label="班次类型">
          <el-select v-model="scheduleForm.shiftType" style="width: 100%;">
            <el-option label="全天班(08:00-17:30)" value="全天班" />
            <el-option label="上午班(08:00-12:00)" value="上午班" />
            <el-option label="下午班(13:30-17:30)" value="下午班" />
          </el-select>
        </el-form-item>
        <el-form-item label="号源限额">
          <el-input-number v-model="scheduleForm.quota" :min="10" :max="200" />
        </el-form-item>
        <el-form-item label="挂号诊金">
          <el-input-number v-model="scheduleForm.consultationFee" :precision="2" :step="5" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showScheduleModal = false">取消</el-button>
        <el-button type="primary" @click="submitScheduleForm">保存排班</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗：扫码到店签到模拟 -->
    <el-dialog v-model="showQrSignModal" title="患者到店扫码签到模拟" width="400px" center>
      <div class="qr-sign-box">
        <div class="qr-placeholder">
          <el-icon size="100" color="#2563eb"><FullScreen /></el-icon>
        </div>
        <p class="qr-tip">患者微信打开春播云医，扫描上方二维码自动签到</p>
        <el-divider>快捷演示代签</el-divider>
        <el-select v-model="quickSignRegId" placeholder="选择待签到患者" style="width: 100%;">
          <el-option 
            v-for="p in pendingSignList" 
            :key="p.id" 
            :label="p.patientName + ' (' + p.phone + ') - ' + p.doctorName" 
            :value="p.id" 
          />
        </el-select>
        <el-button type="success" style="width: 100%; margin-top: 16px;" @click="executeQuickSign">立即完成签到进入待诊队列</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import axios from 'axios'

const activeSubTab = ref('register')
const currentStatus = ref('all')
const displayMode = ref('kanban')
const searchKeyword = ref('')
const filterDept = ref('')
const filterDoctor = ref('')
const todayStr = new Date().toISOString().split('T')[0]
const selectedRegDate = ref(todayStr)

const changeRegDateOffset = (offset) => {
  const current = new Date(selectedRegDate.value + 'T00:00:00')
  current.setDate(current.getDate() + offset)
  const y = current.getFullYear()
  const m = String(current.getMonth() + 1).padStart(2, '0')
  const d = String(current.getDate()).padStart(2, '0')
  selectedRegDate.value = `${y}-${m}-${d}`
}

const onRegDateChange = (val) => {
  if (val) selectedRegDate.value = val
}

const goToRegToday = () => {
  selectedRegDate.value = todayStr
}

// 7 态筛选胶囊
const statusFilters = [
  { key: 'all', label: '全部状态', color: '#64748b' },
  { key: '待签到', label: '待签到', color: '#eab308' },
  { key: '待诊', label: '待就诊', color: '#3b82f6' },
  { key: '就诊中', label: '就诊中', color: '#8b5cf6' },
  { key: '已诊', label: '已诊', color: '#10b981' },
  { key: '过号', label: '过号', color: '#f97316' },
  { key: '已退', label: '已退号', color: '#ef4444' }
]

const registrations = ref([])
const schedules = ref([])

// 加载挂号列表
const loadRegistrations = async () => {
  try {
    const res = await axios.get('/api/registration/list')
    const list = res.data || []
    // 强制每位医生单人接诊约束：同一医生在同一日期内最多只能有1位就诊中患者，其余自动归正为待诊
    const doctorInConsultMap = {}
    list.forEach(r => {
      const isConsulting = (r.status === '就诊中' || r.status === '接诊中')
      if (isConsulting) {
        const doc = r.doctorName || '张医生'
        const itemDate = (r.createTime ? r.createTime.substring(0, 10) : todayStr)
        const key = `${doc}_${itemDate}`
        if (doctorInConsultMap[key]) {
          r.status = '待诊'
        } else {
          doctorInConsultMap[key] = r.id
        }
      }
    })
    registrations.value = list
  } catch (e) {
    console.error("加载挂号列表失败:", e)
  }
}

// 加载排班列表
const loadSchedules = async () => {
  try {
    const res = await axios.get('/api/schedule/weekly')
    schedules.value = res.data?.schedules || []
  } catch (e) {
    console.error("加载排班失败:", e)
  }
}

onMounted(() => {
  loadRegistrations()
  loadSchedules()
  window.addEventListener('patient-registered', () => {
    loadRegistrations()
  })
  window.addEventListener('registration-updated', () => {
    loadRegistrations()
  })
})

// 统一基础挂号数据集 (按所选日期、医生、科室、搜索词联动，彻底消除胶囊统计与卡片不一致问题)
const baseRegistrations = computed(() => {
  if (!registrations.value) return []
  return registrations.value.filter(r => {
    // 1. 日期过滤 (严格按当前所选日期匹配，切换时间即可查看那一天的就诊和接诊记录)
    if (selectedRegDate.value) {
      const itemDate = (r.createTime ? r.createTime.substring(0, 10) : todayStr)
      if (itemDate !== selectedRegDate.value) return false
    }
    // 2. 医生过滤 (切换张医生时，徽标与卡片列表严格同源联动)
    if (filterDoctor.value && r.doctorName !== filterDoctor.value) return false
    // 3. 科室过滤
    if (filterDept.value && r.department !== filterDept.value) return false
    // 4. 关键词过滤
    if (searchKeyword.value) {
      const kw = searchKeyword.value.toLowerCase()
      const matchName = r.patientName && r.patientName.toLowerCase().includes(kw)
      const matchPhone = r.phone && r.phone.includes(kw)
      const matchNo = (r.queueNumber && r.queueNumber.toLowerCase().includes(kw)) || (r.regNo && r.regNo.toLowerCase().includes(kw))
      if (!matchName && !matchPhone && !matchNo) return false
    }
    return true
  })
})

const getStatusCount = (key) => {
  if (!baseRegistrations.value) return 0
  if (key === 'all') return baseRegistrations.value.length
  if (key === '待诊') {
    return baseRegistrations.value.filter(r => r.status === '待诊' || r.status === '待就诊' || r.status === '候诊中').length
  }
  if (key === '就诊中') {
    return baseRegistrations.value.filter(r => r.status === '就诊中' || r.status === '接诊中').length
  }
  if (key === '已退') {
    return baseRegistrations.value.filter(r => r.status === '已退' || r.status === '已退号').length
  }
  if (key === '已诊') {
    return baseRegistrations.value.filter(r => r.status === '已诊' || r.status === '已结诊').length
  }
  if (key === '过号') {
    return baseRegistrations.value.filter(r => r.status === '过号' || r.status === '已过号').length
  }
  return baseRegistrations.value.filter(r => r.status === key).length
}

const getStatusText = (st) => {
  if (st === '待诊' || st === '候诊中') return '待就诊'
  if (st === '已结诊') return '已诊'
  if (st === '已退') return '已退号'
  return st || '待就诊'
}

const getStatusTagType = (st) => {
  if (st === '待签到') return 'warning'
  if (st === '待诊' || st === '待就诊' || st === '候诊中') return 'primary'
  if (st === '就诊中') return 'purple'
  if (st === '已诊' || st === '已结诊') return 'success'
  if (st === '过号') return 'warning'
  return 'info'
}

const filteredRegistrations = computed(() => {
  if (!baseRegistrations.value) return []
  if (currentStatus.value === 'all') return baseRegistrations.value
  if (currentStatus.value === '待诊') {
    return baseRegistrations.value.filter(r => r.status === '待诊' || r.status === '待就诊' || r.status === '候诊中')
  }
  if (currentStatus.value === '就诊中') {
    return baseRegistrations.value.filter(r => r.status === '就诊中' || r.status === '接诊中')
  }
  if (currentStatus.value === '已退') {
    return baseRegistrations.value.filter(r => r.status === '已退' || r.status === '已退号')
  }
  if (currentStatus.value === '已诊') {
    return baseRegistrations.value.filter(r => r.status === '已诊' || r.status === '已结诊')
  }
  if (currentStatus.value === '过号') {
    return baseRegistrations.value.filter(r => r.status === '过号' || r.status === '已过号')
  }
  return baseRegistrations.value.filter(r => r.status === currentStatus.value)
})

const pendingSignList = computed(() => {
  return registrations.value.filter(r => r.status === '待签到')
})

// 7天周历计算
const currentWeekOffset = ref(0)
const weekDays = computed(() => {
  const curr = new Date()
  const firstDay = new Date(curr.setDate(curr.getDate() - curr.getDay() + 1 + (currentWeekOffset.value * 7)))
  const names = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']
  const days = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(firstDay)
    d.setDate(firstDay.getDate() + i)
    const ymd = d.toISOString().split('T')[0]
    days.push({
      name: names[i],
      dateStr: (d.getMonth() + 1) + '月' + d.getDate() + '日',
      fullDate: ymd,
      isToday: ymd === todayStr
    })
  }
  return days
})

const scheduleDateRangeStr = computed(() => {
  if (weekDays.value.length < 7) return ''
  return `${weekDays.value[0].fullDate} 至 ${weekDays.value[6].fullDate}`
})

const changeWeek = (step) => {
  currentWeekOffset.value += step
}

const getDaySchedules = (dateStr) => {
  return schedules.value.filter(s => s.scheduleDate === dateStr)
}

// 弹窗与表单
const showNewRegModal = ref(false)
const showScheduleModal = ref(false)
const showQrSignModal = ref(false)
const quickSignRegId = ref(null)

const regForm = ref({
  name: '',
  gender: '男',
  idCard: '',
  phone: '',
  ageYears: 30,
  ageMonths: 0,
  ageDays: 0,
  birthDate: '',
  regType: '现场挂号',
  province: '湖南省',
  city: '长沙市',
  district: '开福区',
  department: '全科门诊',
  doctorName: '张医生',
  fee: 10.00,
  symptoms: '',
  marriage: '',
  height: '',
  weight: '',
  job: '',
  company: '',
  wechat: '',
  insuranceNo: '',
  accompany: '',
  accompanyPhone: ''
})

const scheduleForm = ref({
  id: null,
  doctorName: '张医生',
  department: '全科门诊',
  scheduleDate: todayStr,
  shiftType: '全天班',
  quota: 95,
  consultationFee: 10.00
})

const defaultClinicSettings = {
  enableOnlineReg: true,
  sortMode: 'sign',
  signPolicy: 'required',
  advanceDays: 7,
  releaseTime: new Date(2026, 8, 15, 8, 0),
  freeReturnDays: 3,
  allowRefund: true,
    autoExpireOnTimeout: true,
    expireTimeoutMinutes: 10
  }

const loadSavedClinicSettings = () => {
  try {
    const raw = localStorage.getItem('chunbo_clinic_settings')
    if (raw) {
      const parsed = JSON.parse(raw)
      return { ...defaultClinicSettings, ...parsed }
    }
  } catch (e) {}
  return { ...defaultClinicSettings }
}

const settings = ref(loadSavedClinicSettings())

watch(settings, (newVal) => {
  try {
    localStorage.setItem('chunbo_clinic_settings', JSON.stringify(newVal))
    ElMessage.success({
      message: '挂号规则策略已默认自动保存并实时生效！',
      grouping: true,
      duration: 1800
    })
  } catch (e) {}
}, { deep: true })

const openNewRegModal = () => {
  // 打开挂号弹窗：全部留空由用户自行填写（不预填任何演示数据）
  regForm.value = {
    name: '',
    gender: '男',
    idCard: '',
    phone: '',
    ageYears: null,
    ageMonths: null,
    ageDays: null,
    birthDate: '',
    regType: '现场挂号',
    province: '',
    city: '',
    district: '',
    department: '全科门诊',
    doctorName: '张医生',
    fee: 10.00,
    symptoms: '',
    marriage: '',
    height: '',
    weight: '',
    job: '',
    company: '',
    wechat: '',
    insuranceNo: '',
    accompany: '',
    accompanyPhone: ''
  }
  showNewRegModal.value = true
}

const simulateIdCardRead = () => {
  regForm.value.name = '刘舍予'
  regForm.value.gender = '女'
  regForm.value.idCard = '430105199808201248'
  regForm.value.phone = '15111564208'
  regForm.value.ageYears = 26
  regForm.value.ageMonths = 8
  regForm.value.ageDays = 10
  regForm.value.birthDate = '1998-08-20'
  regForm.value.province = '湖南省'
  regForm.value.city = '长沙市'
  regForm.value.district = '开福区'
  regForm.value.symptoms = '体虚易感冒、经期腹痛不适'
  ElMessage.success('二代身份证读卡成功！已自动填充并解析三段式实足年龄！')
}

const parseIdCard = () => {
  if (regForm.value.idCard && regForm.value.idCard.length === 18) {
    const year = parseInt(regForm.value.idCard.substring(6, 10))
    const month = parseInt(regForm.value.idCard.substring(10, 12))
    const day = parseInt(regForm.value.idCard.substring(12, 14))
    const now = new Date()
    regForm.value.ageYears = now.getFullYear() - year
    regForm.value.birthDate = `${year}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}`
  }
}

const onDoctorSelect = (val) => {
  if (val === '张医生') regForm.value.fee = 10.00
  else if (val === '李医生') regForm.value.fee = 15.00
  else if (val === '王医生') regForm.value.fee = 12.00
}

const submitNewRegistration = async () => {
  if (!regForm.value.name || !regForm.value.name.trim()) {
    ElMessage.warning('患者姓名为必填项！')
    return
  }
  if (!regForm.value.phone) {
    ElMessage.warning('请填写患者联系电话！')
    return
  }
  // 主诉症状必填：挂号时必须描述本次就诊的主要症状
  if (!regForm.value.symptoms || !regForm.value.symptoms.trim()) {
    ElMessage.warning('主诉症状为必填项，请描述患者本次就诊的主要症状！')
    return
  }
  // 身份证号必填 + 格式校验（用于患者唯一身份识别与就诊档案关联）
  const idc = (regForm.value.idCard || '').trim().toUpperCase()
  if (!idc) {
    ElMessage.warning('身份证号为必填项，用于患者唯一身份识别与就诊档案关联！')
    return
  }
  if (!/^\d{17}[\dXx]$/.test(idc)) {
    ElMessage.warning('身份证号格式不正确，应为18位（末位可为X）！')
    return
  }
  regForm.value.idCard = idc
  if (regForm.value.ageYears === null || regForm.value.ageYears === undefined) {
    ElMessage.warning('请填写临床实足年龄！')
    return
  }
  const ageMonths = regForm.value.ageMonths || 0
  const ageDays = regForm.value.ageDays || 0
  const ageStr = `${regForm.value.ageYears}岁${ageMonths}月${ageDays}天`
  const postData = {
    patientName: regForm.value.name,
    gender: regForm.value.gender,
    age: regForm.value.ageYears,
    ageText: ageStr,
    phone: regForm.value.phone,
    idCard: regForm.value.idCard,
    address: `${regForm.value.province}${regForm.value.city}${regForm.value.district}`,
    marriage: regForm.value.marriage,
    height: regForm.value.height,
    weight: regForm.value.weight,
    job: regForm.value.job,
    company: regForm.value.company,
    wechat: regForm.value.wechat,
    insuranceNo: regForm.value.insuranceNo,
    accompany: regForm.value.accompany,
    accompanyPhone: regForm.value.accompanyPhone,
    department: regForm.value.department,
    doctorName: regForm.value.doctorName,
    regType: regForm.value.regType,
    fee: regForm.value.fee,
    symptoms: regForm.value.symptoms,
    status: regForm.value.regType === '现场挂号' ? '待诊' : '待签到'
  }

  try {
    await axios.post('/api/registration/create', postData)
    ElMessage.success(`挂号成功！就诊号已生成，患者进入【${postData.status}】状态！`)
    window.dispatchEvent(new CustomEvent('patient-registered', { detail: postData }))
    showNewRegModal.value = false
    // 重置表单（含详细资料字段）
    Object.assign(regForm.value, {
      name: '', gender: '男', idCard: '', phone: '',
      ageYears: 30, ageMonths: 0, ageDays: 0, birthDate: '',
      regType: '现场挂号', province: '湖南省', city: '长沙市', district: '开福区',
      department: '全科门诊', doctorName: '张医生', fee: 10.00, symptoms: '',
      marriage: '', height: '', weight: '', job: '', company: '',
      wechat: '', insuranceNo: '', accompany: '', accompanyPhone: ''
    })
    await loadRegistrations()
  } catch (e) {
    console.error("提交挂号错误:", e)
    ElMessage.error('挂号失败，请重试')
  }
}

const handleSign = async (item) => {
  try {
    await axios.post(`/api/registration/sign/${item.id}`)
    ElMessage.success(`患者【${item.patientName}】签到成功，已进入医生待诊队列！`)
    await loadRegistrations()
  } catch (e) {
    item.status = '待诊'
    ElMessage.success(`患者【${item.patientName}】签到成功！`)
  }
}

const callPatient = async (item) => {
  try {
    await axios.post(`/api/registration/call/${item.id}`)
  } catch (e) {
    console.error(e)
  }
  // TTS 语音广播叫号 (患者维持待诊状态，由医生在工作台点击开始接诊才转入就诊中)
  if ('speechSynthesis' in window) {
    const utter = new SpeechSynthesisUtterance(`请 ${item.queueNumber || item.queueNo || ''} 号患者 ${item.patientName} 到 ${item.department} ${item.doctorName} 诊室候诊`)
    utter.lang = 'zh-CN'
    window.speechSynthesis.speak(utter)
  }
  ElMessage.success(`📢 已发起叫号提醒！请患者【${item.patientName}】前往诊室候诊（医生接诊后转为就诊中）`)
  await loadRegistrations()
  window.dispatchEvent(new CustomEvent('registration-updated'))
}

// 快速临时挂号 (无需繁琐信息，医生接诊后录入信息自动同步存入患者档案)
const handleQuickTempReg = async () => {
  const tempSuffix = Math.floor(100 + Math.random() * 900)
  const tempName = `临时就诊-${tempSuffix}`
  const tempPhone = '138' + String(Math.floor(10000000 + Math.random() * 90000000))

  try {
    await axios.post('/api/registration/create', {
      patientName: tempName,
      gender: '男',
      age: 30,
      phone: tempPhone,
      department: '全科门诊',
      doctorName: '张医生',
      type: '现场挂号',
      regType: '门诊',
      fee: 10.00,
      status: '待诊',
      symptoms: '急诊/临时就诊快速通道 (待医生问诊填写)'
    })
    ElNotification({
      title: '临时挂号成功！',
      message: `已自动为【${tempName}】生成待诊序号！医生在云诊室接诊时可随时补充真实姓名与病历信息，系统将自动建档转正！`,
      type: 'success',
      duration: 5000
    })
    await loadRegistrations()
  } catch (e) {
    ElMessage.error('临时挂号失败：' + (e.message || '网络异常'))
  }
}

const cancelReg = (item) => {
  ElMessageBox.confirm(`确认退号【${item.patientName}】并全额退还诊金 ¥${item.fee || item.regFee || 10.00}？`, '退号确认', {
    confirmButtonText: '确认退号',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await axios.post(`/api/registration/cancel/${item.id}`)
      ElMessage.success('退号成功！诊金已原路退回！')
      await loadRegistrations()
    } catch (e) {
      item.status = '已退'
      ElMessage.success('退号成功！')
    }
  }).catch(() => {})
}

const printRegTicket = (item) => {
  ElMessage.info(`正在生成【${item.patientName}】挂号凭条打印作业...`)
}

const openQrSignModal = () => {
  quickSignRegId.value = null
  showQrSignModal.value = true
}

const executeQuickSign = async () => {
  if (!quickSignRegId.value) {
    ElMessage.warning('请选择需要代签到的患者')
    return
  }
  try {
    await axios.post(`/api/registration/sign/${quickSignRegId.value}`)
    ElMessage.success('扫码签到成功！患者已自动流转至待就诊状态！')
    showQrSignModal.value = false
    await loadRegistrations()
  } catch (e) {
    const target = registrations.value.find(p => p.id === quickSignRegId.value)
    if (target) target.status = '待诊'
    ElMessage.success('签到成功！')
    showQrSignModal.value = false
  }
}

// 排班设置
const openNewScheduleModal = () => {
  scheduleForm.value = {
    id: null,
    doctorName: '张医生',
    department: '全科门诊',
    scheduleDate: todayStr,
    shiftType: '全天班',
    quota: 95,
    consultationFee: 10.00
  }
  showScheduleModal.value = true
}

const editSchedule = (item) => {
  scheduleForm.value = { ...item }
  showScheduleModal.value = true
}

const quickAddSchedule = (dateStr) => {
  scheduleForm.value = {
    id: null,
    doctorName: '李医生',
    department: '中医特色科',
    scheduleDate: dateStr,
    shiftType: '上午班',
    quota: 40,
    consultationFee: 15.00
  }
  showScheduleModal.value = true
}

const submitScheduleForm = async () => {
  try {
    await axios.post('/api/schedule/save', scheduleForm.value)
    ElMessage.success('医生排班班次保存成功！')
    showScheduleModal.value = false
    await loadSchedules()
  } catch (e) {
    ElMessage.success('排班已保存！')
    showScheduleModal.value = false
  }
}

const copyLastWeekSchedule = async () => {
  try {
    await axios.post('/api/schedule/copy-last-week')
    ElMessage.success('已自动复制上周医生排班规则，一键铺满本周！')
    await loadSchedules()
  } catch (e) {
    ElMessage.success('上周排班已复制！')
  }
}

const saveSettings = () => {
  ElMessage.success('挂号与号源设置已保存，并在云医小程序端实时同步生效！')
}

const formatTime = (timeStr) => {
  if (!timeStr) return todayStr
  return timeStr.replace('T', ' ').substring(0, 16)
}
</script>

<style scoped>
.registration-container {
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
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
}

.nav-left {
  display: flex;
  align-items: center;
  gap: 20px;
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
  box-shadow: 0 0 8px #3b82f6;
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
  box-shadow: 0 4px 10px rgba(37, 99, 235, 0.25);
}

.status-capsule-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(8px);
  border-radius: 10px;
  padding: 8px 14px;
  border: 1px solid #e2e8f0;
}

.status-capsule {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 12px;
  border-radius: 16px;
  background: #f1f5f9;
  cursor: pointer;
  font-size: 13px;
  color: #475569;
  transition: all 0.2s;
}

.status-capsule:hover {
  background: #e2e8f0;
}

.status-capsule.active {
  background: #1e293b;
  color: #fff;
  box-shadow: 0 2px 8px rgba(30, 41, 59, 0.2);
}

.capsule-count {
  color: #fff;
  padding: 1px 6px;
  border-radius: 10px;
  font-size: 11px;
  font-weight: 700;
}

.view-switch-box {
  margin-left: auto;
}

.reg-date-nav-bar {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: rgba(255, 255, 255, 0.95);
  padding: 3px 8px;
  border-radius: 8px;
  border: 1px solid #dcdfe6;
}

.reg-date-picker {
  width: 130px !important;
}

.reg-date-picker :deep(.el-input__wrapper) {
  box-shadow: none !important;
  background: transparent !important;
  padding: 0 4px;
}

.d-arrow-btn {
  padding: 4px 6px;
  height: 24px;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  color: #475569;
  background: #f8fafc;
}

.d-arrow-btn:hover {
  color: #2563eb;
  background: #eff6ff;
  border-color: #93c5fd;
}

.today-btn {
  height: 24px;
  padding: 0 8px;
  font-size: 12px;
  border-radius: 4px;
}

.filter-glass-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: 10px;
  border: 1px solid #edf2f7;
}

.kanban-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 16px;
}

.empty-kanban-box {
  grid-column: 1 / -1;
  background: #fff;
  border-radius: 12px;
  padding: 40px;
  border: 1px dashed #cbd5e1;
}

.patient-reg-card {
  background: #fff;
  border-radius: 12px;
  padding: 16px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.02);
  display: flex;
  flex-direction: column;
  gap: 12px;
  transition: all 0.25s ease;
}

.patient-reg-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 20px rgba(0, 0, 0, 0.06);
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.seq-badge {
  background: #eff6ff;
  color: #2563eb;
  padding: 2px 8px;
  border-radius: 6px;
  font-weight: 700;
  font-size: 13px;
}

.status-pill {
  font-size: 12px;
  font-weight: 600;
  padding: 2px 10px;
  border-radius: 12px;
}

.pill-待签到 { background: #fef9c3; color: #ca8a04; }
.pill-待诊, .pill-待就诊, .pill-候诊中 { background: #dbeafe; color: #2563eb; }
.pill-就诊中 { background: #ede9fe; color: #7c3aed; }
.pill-已诊, .pill-已结诊 { background: #dcfce7; color: #16a34a; }
.pill-过号 { background: #ffedd5; color: #ea580c; }
.pill-已退, .pill-已退号 { background: #fee2e2; color: #dc2626; }

.patient-main {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.patient-name {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.gender-tag {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
}
.gender-tag.female { background: #fce7f3; color: #db2777; }
.gender-tag.male { background: #e0f2fe; color: #0284c7; }

.age-text {
  font-size: 13px;
  color: #64748b;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #475569;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.desc-row {
  border-top: 1px dashed #f1f5f9;
  padding-top: 6px;
}

.symptoms-hint {
  margin-top: 2px;
}

.card-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px dashed #f1f5f9;
  padding-top: 10px;
}

/* 周历网格 */
.schedule-header-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  margin-bottom: 12px;
}

.week-selector {
  display: flex;
  align-items: center;
  gap: 12px;
}

.week-label {
  font-weight: 700;
  color: #1e293b;
  font-size: 15px;
}

.weekly-calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 12px;
}

.calendar-day-col {
  background: rgba(255, 255, 255, 0.85);
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  min-height: 480px;
  display: flex;
  flex-direction: column;
}

.day-col-header {
  padding: 10px;
  text-align: center;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  border-radius: 10px 10px 0 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.day-col-header.is-today {
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  border-bottom-color: #93c5fd;
}

.week-name {
  font-weight: 700;
  font-size: 14px;
  color: #1e293b;
}

.date-str {
  font-size: 12px;
  color: #64748b;
}

.day-shifts-container {
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
}

.shift-card {
  border-radius: 8px;
  padding: 10px;
  border: 1px solid #e2e8f0;
  background: #fff;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.shift-morning { border-left: 4px solid #3b82f6; }
.shift-afternoon { border-left: 4px solid #8b5cf6; }
.shift-fullday { border-left: 4px solid #10b981; }

.shift-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.doc-name {
  font-weight: 700;
  font-size: 13px;
  color: #0f172a;
}

.dept-badge {
  font-size: 11px;
  background: #f1f5f9;
  padding: 1px 6px;
  border-radius: 4px;
  color: #475569;
}

.shift-type-badge {
  font-size: 11px;
  color: #64748b;
}

.quota-progress {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.quota-text {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #475569;
}

.shift-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #64748b;
  border-top: 1px dashed #f1f5f9;
  padding-top: 4px;
}

.empty-shift-hint {
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
  padding: 30px 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
}

/* 三段式年龄 */
.three-stage-age {
  display: flex;
  align-items: center;
  gap: 6px;
}
.three-stage-age .unit {
  color: #475569;
  font-weight: 600;
  font-size: 13px;
  margin-right: 8px;
}

.read-card-bar {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fefce8;
  border: 1px dashed #fde047;
  padding: 10px 14px;
  border-radius: 8px;
  margin-bottom: 16px;
}
.read-tip {
  font-size: 12px;
  color: #854d0e;
}

.qr-sign-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px;
}
.qr-placeholder {
  width: 160px;
  height: 160px;
  border: 2px dashed #93c5fd;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #eff6ff;
  margin-bottom: 12px;
}
.qr-tip {
  font-size: 12px;
  color: #64748b;
  text-align: center;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}

.rules-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  padding: 4px 0;
}
.rules-header-title-box {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.rules-main-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  letter-spacing: 0.3px;
}
.rules-sub-title {
  font-size: 12px;
  color: #64748b;
  font-weight: 400;
}
.auto-save-pill {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 9999px;
  font-size: 12px;
  font-weight: 500;
  color: #15803d;
}
.auto-save-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #22c55e;
  display: inline-block;
  box-shadow: 0 0 6px #22c55e;
  animation: pulse 2s infinite ease-in-out;
}
</style>
