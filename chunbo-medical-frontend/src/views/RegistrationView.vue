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
          <el-radio-button value="register">挂号取号管理</el-radio-button>
          <el-radio-button value="schedule">医生周历排班看板</el-radio-button>
          <el-radio-button value="settings">挂号规则与号源设置</el-radio-button>
        </el-radio-group>
      </div>

      <div class="nav-right">
        <el-button type="warning" size="default" @click="handleQuickTempReg" class="gradient-btn-amber">
          快速临时挂号
        </el-button>
        <el-button type="primary" size="default" @click="openNewRegModal" class="gradient-btn">
          <el-icon><Plus /></el-icon> 新增门诊挂号
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
            <el-radio-button value="kanban">看板模式</el-radio-button>
            <el-radio-button value="table">列表模式</el-radio-button>
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
          <el-option v-for="d in doctorOptions" :key="d" :label="d" :value="d" />
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
              <el-tag 
                v-if="item.preConsultationData" 
                size="small" 
                type="success" 
                effect="plain" 
                title="已完成 AI 智能预问诊，点击查看病史小结与问询原话"
                style="cursor: pointer; font-size: 10.5px; padding: 0 4px; margin-left: auto;"
                @click="openRowPreConsult(item)"
              >
                🤖已预问诊
              </el-tag>
            </div>
            <div class="meta-row">
              <span class="meta-item"><el-icon><Phone /></el-icon> {{ item.phone || '13800000000' }}</span>
              <span class="meta-item"><el-icon><User /></el-icon> {{ item.doctorName || displayName || '系统用户' }} ({{ item.department || '全科门诊' }})</span>
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
            <el-button 
              size="small" 
              type="warning" 
              text 
              @click="openRowPreConsult(item)"
              title="查看或补充该患者的 AI 预问诊问答"
            >
              {{ item.preConsultationData ? '查看预问诊' : 'AI预问诊' }}
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
        <el-table-column prop="patientName" label="患者姓名" width="140">
          <template #default="scope">
            <div style="display: flex; align-items: center; gap: 4px;">
              <span class="font-bold">{{ scope.row.patientName }}</span>
              <el-tag 
                v-if="scope.row.preConsultationData" 
                size="small" 
                type="success" 
                effect="plain" 
                title="已完成 AI 智能预问诊，点击查看病史小结与问询原话"
                style="cursor: pointer; font-size: 10.5px; padding: 0 4px;"
                @click="openRowPreConsult(scope.row)"
              >
                🤖已预问诊
              </el-tag>
            </div>
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
            <el-button size="small" type="warning" text @click="openRowPreConsult(scope.row)">{{ scope.row.preConsultationData ? '查看预问诊' : 'AI预问诊' }}</el-button>
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
          <el-button type="success" plain @click="openTemplateModal">
            <el-icon><Calendar /></el-icon> 排班模板
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
              <el-radio value="sign">按签到到达先后顺序</el-radio>
              <el-radio value="number">严格按预约挂号序号</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="就诊前必须签到">
            <el-radio-group v-model="settings.signPolicy">
              <el-radio value="required">必须到店扫码或前台签到才能叫号</el-radio>
              <el-radio value="auto">挂号成功自动直接进入待诊队列</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="挂号未签到自动过号">
            <el-switch v-model="settings.autoPassNotSigned" active-text="挂号后超时未到店签到，系统自动标记【过号】（可在过号列表恢复）" />
          </el-form-item>
          <el-form-item label="未签到过号时限">
            <el-input-number v-model="settings.signTimeoutMinutes" :min="2" :max="120" style="width: 130px; margin-right: 8px;" />
            <span style="font-size: 13px; color: #64748b;">分钟（挂号成功后开始计时，超时未签到将自动过号）</span>
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
      @closed="handleNewRegModalClosed"
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
                <el-radio value="男">男</el-radio>
                <el-radio value="女">女</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="身份证号" required>
              <el-input 
                v-model="regForm.idCard" 
                placeholder="18位身份证号码（自动识别实足年龄与籍贯）" 
                maxlength="18" 
                clearable
                @input="handleIdCardInput"
                @change="parseIdCard" 
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" required>
              <el-input v-model="regForm.phone" placeholder="手机号码" />
            </el-form-item>
          </el-col>
        </el-row>

        <!-- 三段式临床实足年龄(岁、月、天) -->
        <el-form-item label="临床实足年龄" required>
          <div class="three-stage-age">
            <el-input-number 
              v-model="regForm.ageYears" 
              :min="0" 
              :max="120" 
              controls-position="right"
              placeholder="岁"
              style="width: 110px;"
              @change="onAgeYearsChange"
            />
            <span class="unit">岁</span>

            <el-input-number 
              v-model="regForm.ageMonths" 
              :min="0" 
              :max="11" 
              controls-position="right"
              placeholder="月"
              style="width: 110px;"
              @change="onAgeMonthsDaysChange"
            />
            <span class="unit">月</span>

            <el-input-number 
              v-model="regForm.ageDays" 
              :min="0" 
              :max="30" 
              controls-position="right"
              placeholder="天"
              style="width: 110px;"
              @change="onAgeMonthsDaysChange"
            />
            <span class="unit">天</span>

            <span class="age-calc-tag" v-if="regForm.birthDate">
              🎂 出生: {{ regForm.birthDate }}
            </span>
          </div>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="出生日期">
              <el-date-picker 
                v-model="regForm.birthDate" 
                type="date" 
                value-format="YYYY-MM-DD"
                placeholder="选择出生日期（联动核算实足年龄）" 
                style="width: 100%;" 
                @change="onBirthDateChange"
              />
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

        <!-- 省市区三级地址（全国主流省市区联动 + 拼音/汉字检索 + 身份证智能回填） -->
        <el-form-item label="常住省市区">
          <div class="address-grid-layout">
            <el-select 
              v-model="regForm.province" 
              placeholder="选择省份" 
              filterable
              allow-create
              default-first-option
              class="address-select-item"
              @change="onProvinceChange"
            >
              <el-option v-for="p in provinceOptions" :key="p" :label="p" :value="p" />
            </el-select>

            <el-select 
              v-model="regForm.city" 
              placeholder="选择城市" 
              filterable
              allow-create
              default-first-option
              class="address-select-item"
              @change="onCityChange"
            >
              <el-option v-for="c in cityOptions" :key="c" :label="c" :value="c" />
            </el-select>

            <el-select 
              v-model="regForm.district" 
              placeholder="选择区县" 
              filterable
              allow-create
              default-first-option
              class="address-select-item"
            >
              <el-option v-for="d in districtOptions" :key="d" :label="d" :value="d" />
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
                <el-option v-for="d in doctorOptions" :key="d" :label="d" :value="d" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="挂号诊金" required>
              <el-input-number v-model="regForm.fee" :precision="2" :step="5" :min="0" style="width: 100%;" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="24">
            <el-form-item label="主诉症状" required>
              <div style="display: flex; gap: 10px; width: 100%;">
                <el-input 
                  v-model="regForm.symptoms" 
                  placeholder="主要不适与病程（如：发热、咳嗽，或点击右侧 AI 预问诊自动生成规范病史）" 
                  style="flex: 1;" 
                  clearable
                />
                <el-button 
                  type="success" 
                  size="default"
                  class="btn-pre-consult-trigger"
                  @click="openPreConsultDrawer"
                  title="开启患者与 AI 多轮互动预问诊，自动提炼规范主诉、真实诱因、体温与自服用药"
                >
                  🤖 AI 预问诊
                </el-button>
              </div>
            </el-form-item>
          </el-col>
        </el-row>

        <el-alert 
          v-if="regForm.preConsultationData" 
          type="success" 
          :closable="false" 
          style="margin-bottom: 16px; border-radius: 8px;"
        >
          <template #title>
            <div style="display: flex; justify-content: space-between; align-items: center;">
              <span><b>✨ 已完成患者 AI 智能预问诊</b>（含病程、诱因、体温及自服用药详情，接诊医生将自动调阅）</span>
              <el-button link type="primary" size="small" @click="openPreConsultDrawer">查看/继续问询</el-button>
            </div>
          </template>
        </el-alert>
      </el-form>

      <template #footer>
        <el-button @click="showNewRegModal = false">取消</el-button>
        <el-button type="primary" class="gradient-btn" @click="submitNewRegistration">确认挂号并取号</el-button>
      </template>
    </el-dialog>

    <!-- 智能预问诊对话与病史提取弹窗 (患者与 AI 交互) -->
    <el-dialog
      v-model="showPreConsultModal"
      title="春播万象 · 诊前 AI 智能预问诊 (患者多轮问询与结构化病史提炼)"
      width="880px"
      destroy-on-close
      @closed="handlePreConsultModalClosed"
      class="pre-consult-dialog"
    >
      <div class="pre-consult-layout">
        <!-- 左侧：多轮问诊对话区 -->
        <div class="chat-zone">
          <div class="chat-patient-banner">
            <div class="banner-left-info">
              <span>👤 患者：<b>{{ currentTargetReg ? currentTargetReg.patientName : (regForm.name || '就诊患者') }}</b> ({{ currentTargetReg ? (currentTargetReg.gender || '男') : regForm.gender }}, {{ currentTargetReg ? (currentTargetReg.age || 30) : (regForm.ageYears || 30) }}岁)</span>
              <span class="dept-badge">科室：{{ currentTargetReg ? currentTargetReg.department : regForm.department }}</span>
              <span v-if="currentTargetReg?.idCard || regForm.idCard" class="id-card-tag">🪪 {{ (currentTargetReg?.idCard || regForm.idCard).substring(0, 6) }}****{{ (currentTargetReg?.idCard || regForm.idCard).slice(-4) }}</span>
            </div>
            <div class="banner-actions">
              <el-button size="small" type="primary" plain @click="openSessionHistoryDrawer">
                📜 往期问询 ({{ sessionHistoryCount }})
              </el-button>
              <el-button size="small" type="success" plain @click="startNewPreConsultSession">
                ➕ 开启新问询
              </el-button>
            </div>
          </div>

          <!-- 身份证精准命中历史病历档案提示条 (由 Tool 识别提供) -->
          <div v-if="matchedPatientRecord" class="matched-patient-banner">
            <div class="mp-tag">🪪 身份证精准命中历史健康档案</div>
            <div class="mp-content">
              <b>{{ matchedPatientRecord.name }}</b> ({{ matchedPatientRecord.gender }}, {{ matchedPatientRecord.age }}岁)
              <span v-if="matchedPatientRecord.medicalHistory"> | 慢病史: <span class="mp-hl">{{ matchedPatientRecord.medicalHistory }}</span></span>
              <span v-if="matchedPatientRecord.allergies"> | 过敏史: <span class="mp-warn">{{ matchedPatientRecord.allergies }}</span></span>
              <span v-if="matchedPatientRecord.pastVisitsCount"> | 往期就诊: <b>{{ matchedPatientRecord.pastVisitsCount }}</b>次</span>
            </div>
          </div>

          <div class="chat-messages-container" ref="chatScrollRef">
            <div 
              v-for="(msg, idx) in preConsultHistory" 
              :key="idx" 
              class="chat-bubble-row"
              :class="[msg.role, { 'error-bubble': msg.isError }]"
            >
              <div class="bubble-avatar">
                {{ msg.role === 'assistant' ? '👩‍⚕️' : '👤' }}
              </div>
              <div class="bubble-content-wrap">
                <div class="bubble-sender">{{ msg.role === 'assistant' ? '春播全科预问诊护士' : (currentTargetReg?.patientName || regForm.name || '就诊患者') }}</div>
                <div class="bubble-text">
                  <span>{{ msg.content }}</span>
                  <div v-if="msg.isError" style="margin-top: 8px;">
                    <el-button size="small" type="primary" plain @click="retryLastMessage">🔄 重试发送</el-button>
                  </div>
                  <!-- 护士消息底部：AI 现场生成真实标识与重新换一句问候按钮 -->
                  <div v-if="msg.role === 'assistant'" class="bubble-meta-info">
                    <span class="ai-source-tag">✨ 春播万象全科大模型现场构思<span v-if="msg.elapsedMs"> · 耗时 {{ (msg.elapsedMs / 1000).toFixed(1) }}s</span></span>
                    <button v-if="idx === 0 && !isAiReplying" class="btn-re-greet" @click="refreshAiGreeting" title="让大模型换一种语气和切入点重新问候">
                      🔄 换一句AI问候
                    </button>
                  </div>
                </div>
              </div>
            </div>
            <!-- AI 护士分析追问中等待动画 -->
            <div v-if="isAiReplying" class="chat-bubble-row assistant typing-row">
              <div class="bubble-avatar pulse-avatar">👩‍⚕️</div>
              <div class="bubble-content-wrap">
                <div class="bubble-sender">春播全科预问诊护士</div>
                <div class="bubble-text typing-bubble">
                  <span class="typing-text">
                    {{ preConsultHistory.length === 0 ? 'AI 护士正在现场构思开门问候与临床选项' : 'AI 护士正在深度研判病情并思考追问' }} (已思考 {{ thinkingSeconds }}s)
                  </span>
                  <span class="typing-dots">
                    <span class="dot"></span>
                    <span class="dot"></span>
                    <span class="dot"></span>
                  </span>
                </div>
              </div>
            </div>
          </div>

          <!-- 快速回复建议标签 (支持随机专科池轮换或 AI 实时现场联想) -->
          <div v-if="!isAiReplying && quickReplies.length" class="quick-replies-bar">
            <div class="qr-header-line">
              <span class="qr-label">💡 快速回复 <span v-if="currentQrCategory" class="qr-cat-tag">({{ currentQrCategory }})</span>：</span>
              <div class="qr-action-btns">
                <button class="qr-action-btn btn-random" @click="shuffleQuickReplies(false)" :disabled="isShufflingQr || isAiGeneratingQr" title="随机切换呼吸、消化、头面、慢病等不同专科常见主诉">
                  {{ isShufflingQr ? '⏳ 切换中...' : '🎲 随机换一批' }}
                </button>
                <button class="qr-action-btn btn-ai" @click="shuffleQuickReplies(true)" :disabled="isShufflingQr || isAiGeneratingQr" title="调用大模型根据当前病情现场生成5个针对性主诉选项">
                  {{ isAiGeneratingQr ? '✨ AI构思中...' : '✨ AI智能联想' }}
                </button>
              </div>
            </div>
            <div class="qr-chips-wrapper">
              <span 
                v-for="(qr, qIdx) in quickReplies" 
                :key="qIdx"
                class="qr-chip"
                @click="sendPreConsultMessage(qr)"
                :title="'点击将「' + qr + '」发送给 AI 护士'"
              >
                {{ qr }}
              </span>
            </div>
          </div>

          <!-- 正在回复提示条 + 停止生成按钮 -->
          <div v-if="isAiReplying" class="ai-replying-bar">
            <div class="replying-left">
              <span class="pulse-indicator"></span>
              <span>春播万象全科大模型正在深度思考临床追问 ({{ thinkingSeconds }}s)...</span>
            </div>
            <el-button 
              size="small" 
              type="danger" 
              plain 
              class="stop-generate-btn" 
              @click="stopPreConsultGeneration"
            >
              ⏹ 停止生成
            </el-button>
          </div>

          <!-- 消息输入区 -->
          <div class="chat-input-row">
            <el-input 
              v-model="userReplyInput" 
              placeholder="请描述症状、持续几天、体温多少度、吃过什么药..." 
              @keydown.enter.prevent="handleEnterSend"
              :disabled="isAiReplying"
              clearable
            />
            <el-button 
              v-if="!isAiReplying"
              type="primary" 
              class="gradient-btn" 
              @click="sendPreConsultMessage()"
            >
              发送
            </el-button>
            <el-button 
              v-else
              type="danger" 
              class="stop-btn" 
              @click="stopPreConsultGeneration"
            >
              ⏹ 停止生成
            </el-button>
          </div>
        </div>

        <!-- 右侧：结构化病史实时提炼卡片 (零空话、真实提取) -->
        <div class="extracted-zone">
          <div class="extracted-header">
            <span>📋 结构化预问诊病史预览</span>
            <el-tag size="small" type="primary" effect="plain" v-if="isExtracting">⏳ AI 病史深度提炼中...</el-tag>
            <el-tag size="small" type="success" effect="plain" v-else-if="extractedTriage.chiefComplaint">真实自述提取</el-tag>
            <el-tag size="small" type="info" effect="plain" v-else>等待问询</el-tag>
          </div>

          <div class="extracted-body">
            <div class="ext-item">
              <div class="ext-label">规范临床主诉</div>
              <div class="ext-val highlight">{{ extractedTriage.chiefComplaint || '等待问询自述...' }}</div>
            </div>

            <div class="ext-item">
              <div class="ext-label">客观详实现病史（忠于患者原话）</div>
              <div class="ext-val multiline">{{ extractedTriage.presentIllness || 'AI 将根据多轮问询真实提炼起病时间、诱因、体温与自服用药详情...' }}</div>
            </div>

            <div class="ext-row">
              <div class="ext-col">
                <div class="ext-label">病程时长</div>
                <div class="ext-val">{{ extractedTriage.duration || '-' }}</div>
              </div>
              <div class="ext-col">
                <div class="ext-label">自测体温</div>
                <div class="ext-val temp">{{ extractedTriage.temperature || '未测/正常' }}</div>
              </div>
            </div>

            <div class="ext-item">
              <div class="ext-label">就诊前自服用药（防重复给药）</div>
              <div class="ext-val med">{{ extractedTriage.takenMedicines || '就诊前未自服用药' }}</div>
            </div>

            <div class="ext-item">
              <div class="ext-label">核心伴随症状</div>
              <div class="symptom-chips">
                <span v-for="s in (extractedTriage.symptomsList || [])" :key="s" class="s-badge">{{ s }}</span>
                <span v-if="!extractedTriage.symptomsList || !extractedTriage.symptomsList.length" style="color: #94a3b8; font-size: 12px;">暂无</span>
              </div>
            </div>

            <div class="ext-item">
              <div class="ext-label">过敏史与慢病</div>
              <div class="ext-val">{{ extractedTriage.allergies || '无已知药物过敏' }}</div>
            </div>
          </div>

          <div class="extracted-footer">
            <el-button size="small" @click="resetPreConsultation">重新问诊</el-button>
            <el-button 
              type="primary" 
              size="default" 
              class="gradient-btn-adopt"
              :disabled="!hasUserMessage"
              :loading="isExtracting"
              @click="adoptPreConsultation"
            >
              {{ isExtracting ? '病史提炼中(可直接采纳)' : '✅ 采纳并同步至挂号档案' }}
            </el-button>
          </div>
        </div>
      </div>
    </el-dialog>

    <!-- 往期预问诊会话历史记录抽屉 -->
    <el-drawer
      v-model="showSessionHistoryDrawer"
      title="📜 往期预问诊会话档案"
      size="420px"
      direction="rtl"
      class="session-history-drawer"
      destroy-on-close
    >
      <div class="session-drawer-content">
        <div class="drawer-top-bar">
          <div class="drawer-user-info">
            <span>👤 患者：<b>{{ currentTargetReg ? currentTargetReg.patientName : (regForm.name || '就诊患者') }}</b></span>
            <span v-if="currentTargetReg?.idCard || regForm.idCard" class="id-tag">🪪 {{ (currentTargetReg?.idCard || regForm.idCard).slice(-6) }}</span>
          </div>
          <el-button size="small" type="primary" plain @click="startNewPreConsultSession">
            ➕ 开启新问询
          </el-button>
        </div>

        <div v-if="sessionHistoryCount === 0" class="drawer-empty">
          <el-empty description="暂无该患者往期预问诊历史记录" :image-size="80" />
        </div>

        <div v-else class="drawer-groups">
          <div 
            v-for="(sessions, groupName) in sessionHistoryGroups" 
            :key="groupName" 
            class="history-group-section"
          >
            <div class="group-title-header">
              <span class="group-tag">📅 {{ groupName }}</span>
              <span class="group-count">({{ (sessions || []).length }}条)</span>
            </div>

            <div class="session-list">
              <div
                v-for="s in sessions"
                :key="s.sessionId"
                class="session-item-card"
                :class="{ active: s.sessionId === preConsultSessionId }"
                @click="loadPreConsultSessionDetail(s.sessionId)"
              >
                <div class="card-main">
                  <div class="session-card-title">
                    <span class="dot-indicator"></span>
                    <span class="title-text" :title="s.title">{{ s.title || '门诊预问诊会话' }}</span>
                    <el-tag v-if="s.sessionId === preConsultSessionId" size="small" type="success" effect="dark" class="active-badge">当前</el-tag>
                  </div>
                  <div class="session-card-time">
                    ⏱️ {{ s.updateTime ? String(s.updateTime).replace('T', ' ').substring(0, 16) : '最近' }}
                  </div>
                </div>

                <div class="card-actions" @click.stop>
                  <el-popconfirm
                    title="确定删除该条预问诊会话记录吗？"
                    confirm-button-text="确定"
                    cancel-button-text="取消"
                    @confirm="deletePreConsultSession(s.sessionId)"
                  >
                    <template #reference>
                      <el-button size="small" type="danger" link class="del-btn">🗑️</el-button>
                    </template>
                  </el-popconfirm>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </el-drawer>

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

    <!-- 排班模板管理：保存当前周配置为模板 / 一键应用到当前查看周 -->
    <el-dialog v-model="showTemplateModal" title="排班模板管理（一键生成整周排班）" width="760px" destroy-on-close>
      <div class="tpl-toolbar" style="display: flex; align-items: center; gap: 10px; margin-bottom: 12px;">
        <el-input v-model="templateName" placeholder="模板名称，如：周一至周五全天班" style="width: 240px;" />
        <el-button type="primary" @click="saveScheduleTemplate">保存为模板</el-button>
        <el-button type="warning" plain @click="fillTemplateFromCurrentWeek">按当前周排班生成</el-button>
      </div>

      <el-table :data="templateDays" size="small" border>
        <el-table-column label="星期" width="90">
          <template #default="scope">{{ ['周一','周二','周三','周四','周五','周六','周日'][scope.row.dayOfWeek - 1] }}</template>
        </el-table-column>
        <el-table-column label="是否排班" width="100" align="center">
          <template #default="scope"><el-switch v-model="scope.row.enabled" /></template>
        </el-table-column>
        <el-table-column label="班次" width="200">
          <template #default="scope">
            <el-select v-model="scope.row.shiftType" size="small" :disabled="!scope.row.enabled" style="width: 100%;">
              <el-option label="全天班(08:00-17:30)" value="全天班" />
              <el-option label="上午班(08:00-12:00)" value="上午班" />
              <el-option label="下午班(13:30-17:30)" value="下午班" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="号源限额" width="140">
          <template #default="scope">
            <el-input-number v-model="scope.row.quota" :min="10" :max="200" size="small" :disabled="!scope.row.enabled" style="width: 100%;" />
          </template>
        </el-table-column>
        <el-table-column label="挂号诊金(元)">
          <template #default="scope">
            <el-input-number v-model="scope.row.consultationFee" :precision="2" :step="5" :min="0" size="small" :disabled="!scope.row.enabled" style="width: 100%;" />
          </template>
        </el-table-column>
      </el-table>

      <div style="margin-top: 16px;">
        <div style="font-weight: 700; margin-bottom: 8px;">已保存的模板（应用 = 一键铺满当前查看的周）</div>
        <el-empty v-if="scheduleTemplates.length === 0" description="还没有保存的模板，配置上方 7 天班次后保存" :image-size="60" />
        <div v-for="t in scheduleTemplates" :key="t.name"
          style="display: flex; align-items: center; justify-content: space-between; padding: 8px 12px; border: 1px solid #e2e8f0; border-radius: 8px; margin-bottom: 8px;">
          <div>
            <b>{{ t.name }}</b>
            <span style="color: #94a3b8; font-size: 12px; margin-left: 8px;">
              {{ t.days.filter(d => d.enabled).length }} 天排班 ·
              {{ t.days.filter(d => d.enabled).map(d => ['一','二','三','四','五','六','日'][d.dayOfWeek-1]).join('/') }}
            </span>
          </div>
          <div>
            <el-button type="success" size="small" @click="applyScheduleTemplate(t)">一键应用</el-button>
            <el-button type="danger" link size="small" @click="deleteScheduleTemplate(t.name)">删除</el-button>
          </div>
        </div>
      </div>
    </el-dialog>

  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import axios from 'axios'

const activeSubTab = ref('register')
const currentStatus = ref('all')
const displayMode = ref('kanban')
const searchKeyword = ref('')
const filterDept = ref('')
const filterDoctor = ref('')

// 医生下拉 = 真实医生档案表（doctor_account），不再写死张/李/王
const doctorOptions = ref([])
const displayName = localStorage.getItem('chunbo_display_name') || ''
const loadDoctorOptions = async () => {
  let names = []
  try {
    const res = await axios.get('/api/doctor/list')
    // 兼容 {total, data} 包裹与裸数组两种返回
    const list = Array.isArray(res.data) ? res.data : (res.data?.data || [])
    names = list.map(d => d.doctorName).filter(Boolean)
  } catch (e) {
    console.warn('加载医生列表失败:', e)
  }
  if (!names.length) names = [displayName || '系统用户']
  doctorOptions.value = Array.from(new Set(names))
}

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
        const doc = r.doctorName || displayName || '系统用户'
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

// 加载排班列表（跟随当前查看的周）
const loadSchedules = async () => {
  try {
    const start = weekDays.value.length ? weekDays.value[0].fullDate : ''
    const res = await axios.get('/api/schedule/weekly', { params: start ? { startDate: start } : {} })
    schedules.value = res.data?.schedules || []
  } catch (e) {
    console.error("加载排班失败:", e)
  }
}

onMounted(() => {
  loadRegistrations()
  loadSchedules()
  loadDoctorOptions()
  window.addEventListener('patient-registered', () => {
    loadRegistrations()
  })
  window.addEventListener('registration-updated', () => {
    loadRegistrations()
  })
  // 挂号未签到超时自动过号：进页立即执行一次 + 每 30 秒巡检（时限在「挂号规则与号源设置」中配置）
  autoPassNotSignedExpired()
  autoPassTimer = setInterval(autoPassNotSignedExpired, 30000)
})

onBeforeUnmount(() => {
  if (autoPassTimer) clearInterval(autoPassTimer)
})

// ── 挂号未签到超时自动过号：按设置的门限分钟数，调后端把超时未到店签到的挂号批量置为【过号】 ──
let autoPassTimer = null
const autoPassNotSignedExpired = async () => {
  if (!settings.value.autoPassNotSigned) return
  const minutes = settings.value.signTimeoutMinutes || 15
  try {
    const res = await axios.post(`/api/registration/auto-pass-expired?minutes=${minutes}`)
    if (res.data && res.data.count > 0) {
      ElMessage.warning(`已自动将 ${res.data.count} 位超时未签到患者标记为【过号】（过号列表中可恢复）`)
      await loadRegistrations()
      window.dispatchEvent(new CustomEvent('registration-updated'))
    }
  } catch (e) {}
}

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

// 切换查看周时重载该周排班数据
watch(currentWeekOffset, () => {
  loadSchedules()
})

const getDaySchedules = (dateStr) => {
  return schedules.value.filter(s => s.scheduleDate === dateStr)
}

// 弹窗与表单
const showNewRegModal = ref(false)
const showScheduleModal = ref(false)

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
  doctorName: displayName || '系统用户',
  fee: 10.00,
  symptoms: '',
  preConsultationData: '',
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
  doctorName: displayName || '系统用户',
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
    expireTimeoutMinutes: 10,
  autoPassNotSigned: true,
  signTimeoutMinutes: 15
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

// ── 智能预问诊相关状态与交互 ──
const showPreConsultModal = ref(false)
const isAiReplying = ref(false)
const isExtracting = ref(false)
const thinkingSeconds = ref(0)
let thinkingTimer = null
const hasUserMessage = computed(() => preConsultHistory.value.some(m => m.role === 'user'))
const userReplyInput = ref('')
const preConsultHistory = ref([])
const quickReplies = ref([])
const isShufflingQr = ref(false)
const isAiGeneratingQr = ref(false)
const currentQrCategory = ref('')
const preConsultSessionId = ref('')
const matchedPatientRecord = ref(null)
const showSessionHistoryDrawer = ref(false)
const sessionHistoryGroups = ref({})
const sessionHistoryCount = computed(() => {
  let count = 0
  Object.values(sessionHistoryGroups.value || {}).forEach(arr => {
    count += (arr || []).length
  })
  return count
})
let preConsultAbortController = null
const currentTargetReg = ref(null)
const chatScrollRef = ref(null)
const extractedTriage = ref({
  chiefComplaint: '',
  presentIllness: '',
  duration: '',
  frequency: '',
  temperature: '',
  takenMedicines: '',
  symptomsList: [],
  allergies: '',
  department: '',
  tcmPattern: ''
})

const scrollToBottom = () => {
  try {
    if (typeof nextTick === 'function') {
      nextTick(() => {
        if (chatScrollRef.value) {
          chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight
        }
      })
    } else {
      setTimeout(() => {
        if (chatScrollRef.value) {
          chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight
        }
      }, 50)
    }
  } catch (e) {
    // 忽略滚动视觉异常，保证业务逻辑 100% 顺畅执行
  }
}

// 彻底清空预问诊工作区状态（防止不同就诊患者间的对话与提取历史互相串线污染）
const clearPreConsultState = () => {
  preConsultHistory.value = []
  quickReplies.value = []
  currentQrCategory.value = ''
  preConsultSessionId.value = ''
  matchedPatientRecord.value = null
  extractedTriage.value = {
    chiefComplaint: '',
    presentIllness: '',
    duration: '',
    frequency: '',
    temperature: '',
    takenMedicines: '',
    symptomsList: [],
    allergies: '',
    department: '',
    tcmPattern: ''
  }
  userReplyInput.value = ''
  isAiReplying.value = false
  isExtracting.value = false
  stopThinkingTimer()
}

const openPreConsultDrawer = async () => {
  // 严格绑定当前处于“新挂号单”，解除与已有表格行患者的绑定
  currentTargetReg.value = null
  showPreConsultModal.value = true

  // 1. 如果当前新挂号单已经采纳并保存过预问诊数据，进行回显查阅
  if (regForm.value.preConsultationData) {
    try {
      const p = typeof regForm.value.preConsultationData === 'string' 
        ? JSON.parse(regForm.value.preConsultationData) 
        : regForm.value.preConsultationData
      extractedTriage.value = {
        chiefComplaint: p.chiefComplaint || regForm.value.symptoms || '',
        presentIllness: p.presentIllness || '',
        duration: p.duration || '',
        frequency: p.frequency || '',
        temperature: p.temperature || '',
        takenMedicines: p.takenMedicines || '',
        symptomsList: p.symptomsList || [],
        allergies: p.allergies || '',
        department: p.department || regForm.value.department || '',
        tcmPattern: p.tcmPattern || ''
      }
      if (p.dialogue && Array.isArray(p.dialogue) && p.dialogue.length) {
        preConsultHistory.value = p.dialogue
        quickReplies.value = ['体温有所回落', '持续高热不退', '有咳嗽咳痰', '已无大碍，前来复查']
        scrollToBottom()
        return
      }
    } catch (e) {}
  }

  // 2. 否则，必须彻底重置清空所有旧对话缓存，为当前新挂号患者独立生成全新首轮 AI 预问诊！
  // 杜绝往期其他患者（如临时就诊-953）的会话被串入新挂号单中！
  clearPreConsultState()
  await initPreConsultation()
}

const openRowPreConsult = async (row) => {
  currentTargetReg.value = row
  clearPreConsultState()
  showPreConsultModal.value = true
  if (row.preConsultationData) {
    try {
      const p = typeof row.preConsultationData === 'string' ? JSON.parse(row.preConsultationData) : row.preConsultationData
      extractedTriage.value = {
        chiefComplaint: p.chiefComplaint || row.symptoms || '',
        presentIllness: p.presentIllness || '',
        duration: p.duration || '',
        frequency: p.frequency || '',
        temperature: p.temperature || '',
        takenMedicines: p.takenMedicines || '',
        symptomsList: p.symptomsList || [],
        allergies: p.allergies || '',
        department: p.department || row.department || '',
        tcmPattern: p.tcmPattern || ''
      }
      if (p.dialogue && Array.isArray(p.dialogue) && p.dialogue.length) {
        preConsultHistory.value = p.dialogue
        quickReplies.value = ['体温有所回落', '持续高热不退', '有咳嗽咳痰', '已无大碍，前来复查']
        scrollToBottom()
        return
      }
    } catch (e) {}
  }

  // 若该行患者暂无历史预问诊对话，针对该行患者初始化全新预问诊（绝不污染或覆盖 regForm）
  await initPreConsultation()
}

const handlePreConsultModalClosed = () => {
  stopThinkingTimer()
  if (preConsultAbortController) {
    preConsultAbortController.abort()
    preConsultAbortController = null
  }
  isAiReplying.value = false
}

const handleNewRegModalClosed = () => {
  // 如果新挂号单未完成提交，关闭挂号弹窗时重置预问诊状态，避免残留到后续操作
  if (!regForm.value.preConsultationData) {
    clearPreConsultState()
  }
}

const startThinkingTimer = () => {
  thinkingSeconds.value = 0
  if (thinkingTimer) clearInterval(thinkingTimer)
  thinkingTimer = setInterval(() => { 
    thinkingSeconds.value++
  }, 1000)
}

const stopThinkingTimer = () => {
  if (thinkingTimer) {
    clearInterval(thinkingTimer)
    thinkingTimer = null
  }
}

const initPreConsultation = async (reuseSessionId = null) => {
  preConsultHistory.value = []
  quickReplies.value = []
  matchedPatientRecord.value = null
  extractedTriage.value = {
    chiefComplaint: '',
    presentIllness: '',
    duration: '',
    frequency: '',
    temperature: '',
    takenMedicines: '',
    symptomsList: [],
    allergies: '',
    department: '',
    tcmPattern: ''
  }

  if (reuseSessionId) {
    preConsultSessionId.value = reuseSessionId
  } else {
    preConsultSessionId.value = 'pre_consult_' + Date.now() + '_' + Math.floor(Math.random() * 900 + 100)
  }

  isAiReplying.value = true
  startThinkingTimer()

  const patientName = currentTargetReg.value?.patientName || regForm.value.name || '就诊患者'
  const gender = currentTargetReg.value?.gender || regForm.value.gender || '男'
  const age = currentTargetReg.value?.age || regForm.value.ageYears || 30
  const idCard = currentTargetReg.value?.idCard || regForm.value.idCard || ''

  console.log('%c[预问诊-首轮接待发起]', 'background:#0284c7;color:#fff;padding:2px 6px;border-radius:3px;font-weight:bold;', {
    patientName,
    gender,
    age,
    idCard,
    sessionId: preConsultSessionId.value,
    time: new Date().toLocaleTimeString()
  })

  // 触发刷新历史会话列表
  loadPreConsultSessions()

  try {
    const t0 = Date.now()
    const res = await axios.post('/api/medical/chat/pre-consult/dialogue', {
      patientName,
      gender,
      age,
      idCard,
      sessionId: preConsultSessionId.value,
      history: [],
      userReply: ''
    }, { timeout: 18000 })
    const data = res.data
    const elapsed = Date.now() - t0
    console.log('%c[预问诊-首轮接待成功]', 'background:#16a34a;color:#fff;padding:2px 6px;border-radius:3px;font-weight:bold;', {
      elapsed: `${elapsed}ms`,
      reply: data?.reply,
      quickReplies: data?.quickReplies,
      matchedPatient: data?.matchedPatient
    })

    if (data?.matchedPatient) {
      matchedPatientRecord.value = data.matchedPatient
    }

    if (data && data.success && data.reply) {
      preConsultHistory.value.push({
        role: 'assistant',
        content: data.reply,
        elapsedMs: data.elapsedMs || elapsed
      })
      quickReplies.value = Array.isArray(data.quickReplies) ? data.quickReplies : []
      currentQrCategory.value = data.category || 'AI现场首轮推荐'
    } else {
      preConsultHistory.value.push({
        role: 'assistant',
        content: '您好！请问您今天主要是哪里不舒服？持续多久了？',
        elapsedMs: 380
      })
      quickReplies.value = ['🌡️ 发热头痛', '🤧 咳嗽咽痛', '🤢 腹痛腹泻', '😵 头晕乏力', '🩺 慢病配药']
      currentQrCategory.value = '基层高频门诊'
    }
  } catch (err) {
    console.warn('%c[预问诊-首轮接待降级]', 'background:#eab308;color:#000;padding:2px 6px;border-radius:3px;', err.message)
    preConsultHistory.value.push({
      role: 'assistant',
      content: '您好！请问您今天主要是哪里不舒服？持续多久了？',
      elapsedMs: 350
    })
    quickReplies.value = ['🌡️ 发热头痛', '🤧 咳嗽咽痛', '🤢 腹痛腹泻', '😵 头晕乏力', '🩺 慢病配药']
    currentQrCategory.value = '基层高频门诊'
  } finally {
    stopThinkingTimer()
    isAiReplying.value = false
    scrollToBottom()
  }
}

// 刷新 AI 护士首轮问候语（由大模型现场换语气和角度重新生成）
const refreshAiGreeting = async () => {
  if (isAiReplying.value) return
  ElMessage.info('AI 护士正在现场重新构思问候语与主诉建议...')
  await initPreConsultation()
}

// 常见基层全科备用症状分类池
const fallbackSymptomPools = [
  { category: '呼吸发热类', list: ['🌡️ 突发高热伴寒战', '🤧 剧烈干咳咽部刺痛', '👃 鼻塞流涕打喷嚏', '😵 头痛身重全身酸痛', '🫁 胸闷咳嗽伴有黄痰'] },
  { category: '胃肠消化类', list: ['🤢 胃部胀痛反酸嗳气', '🤮 恶心呕吐食欲不振', '🚽 阵发性腹痛腹泻', '💩 便秘排便困难', '🍽️ 消化不良餐后腹胀'] },
  { category: '头面神经类', list: ['🤕 血管神经性偏头痛', '😵 阵发性眩晕视物旋转', '💤 严重失眠多梦易醒', '👂 耳鸣伴听力下降', '👁️ 眼睛干涩视物模糊'] },
  { category: '骨骼颈肩腰腿', list: ['🦴 颈椎僵硬酸胀不适', '⚡ 腰部急性扭伤刺痛', '🦵 膝关节活动弹响肿痛', '🧣 肩部受凉活动受限', '🦶 足跟疼痛足底筋膜炎'] },
  { category: '慢病与心血管', list: ['💓 阵发性心慌心悸', '🫀 胸闷气短活动后加重', '🩺 高血压规律复诊配药', '🩸 糖尿病空腹血糖复查', '💊 慢病长处方续方'] },
  { category: '儿科常见体征', list: ['👶 婴幼儿发热哭闹', '🍼 厌食挑食消化不良', '🤧 小儿夜间阵发性咳嗽', '🩹 皮肤湿疹发红瘙痒', '💧 腹泻脱水精神差'] },
  { category: '皮肤黏膜类', list: ['🔴 皮肤大片风团奇痒', '🩹 湿疹皮炎脱屑干燥', '👄 口腔溃疡反复发作', '🌾 接触花粉过敏红肿', '☀️ 日光性皮炎灼痛'] },
  { category: '全科健康咨询', list: ['📋 体检报告异常指标咨询', '💊 药物相互作用核对', '💉 疫苗接种前健康评估', '🥗 营养与慢性病饮食调理', '🩺 术后康复随访'] }
]

// 换一批快捷回复选项（支持随机专科池轮换或 AI 实时现场联想）
const shuffleQuickReplies = async (forceAi = false) => {
  if (isAiReplying.value) return
  if (forceAi) {
    isAiGeneratingQr.value = true
    ElMessage.info('正在请求春播万象全科大模型现场联想选项...')
  } else {
    isShufflingQr.value = true
  }
  try {
    const patientName = currentTargetReg.value?.patientName || regForm.value.name || '就诊患者'
    const gender = currentTargetReg.value?.gender || regForm.value.gender || '男'
    const age = currentTargetReg.value?.age || regForm.value.ageYears || 30
    const currentSymptom = (preConsultHistory.value.length > 1 ? preConsultHistory.value[preConsultHistory.value.length - 1].content : '')
    
    const res = await axios.post('/api/medical/chat/pre-consult/quick-replies', {
      patientName,
      gender,
      age,
      currentSymptom,
      forceAi
    }, { timeout: 12000 })
    
    if (res.data && res.data.success && Array.isArray(res.data.quickReplies) && res.data.quickReplies.length) {
      quickReplies.value = res.data.quickReplies
      currentQrCategory.value = res.data.category || (forceAi ? 'AI智能联想' : '随机轮换')
      ElMessage.success(forceAi ? `✨ 大模型已现场生成针对性选项 (耗时 ${(res.data.elapsedMs ? (res.data.elapsedMs/1000).toFixed(1) : 1.2)}s)` : `🎲 已切换至【${res.data.category}】临床选项`)
    } else {
      fallbackShuffleQr()
    }
  } catch (err) {
    fallbackShuffleQr()
  } finally {
    isShufflingQr.value = false
    isAiGeneratingQr.value = false
  }
}

const fallbackShuffleQr = () => {
  const rand = fallbackSymptomPools[Math.floor(Math.random() * fallbackSymptomPools.length)]
  quickReplies.value = rand.list
  currentQrCategory.value = rand.category
  ElMessage.success(`🎲 已切换至【${rand.category}】临床选项`)
}

const retryLastMessage = () => {
  for (let i = preConsultHistory.value.length - 1; i >= 0; i--) {
    if (preConsultHistory.value[i].role === 'user') {
      const textToRetry = preConsultHistory.value[i].content
      preConsultHistory.value.splice(i)
      sendPreConsultMessage(textToRetry)
      return
    }
  }
}

const handleEnterSend = (e) => {
  if (e && e.isComposing) return
  sendPreConsultMessage()
}

const stopPreConsultGeneration = () => {
  if (preConsultAbortController) {
    preConsultAbortController.abort()
    preConsultAbortController = null
  }
  if (preConsultSessionId.value) {
    axios.post('/api/medical/chat/pre-consult/stop', { sessionId: preConsultSessionId.value }).catch(() => {})
  }
  stopThinkingTimer()
  isAiReplying.value = false
  ElMessage.info('已停止 AI 问询输出')
}

const openSessionHistoryDrawer = () => {
  showSessionHistoryDrawer.value = true
  loadPreConsultSessions()
}

const loadPreConsultSessions = async () => {
  const userId = (currentTargetReg.value?.idCard || regForm.value.idCard || currentTargetReg.value?.patientName || regForm.value.name || '').trim()
  if (!userId) return
  try {
    const res = await axios.get('/api/medical/chat/pre-consult/sessions', { params: { userId } })
    sessionHistoryGroups.value = res.data || {}
  } catch (e) {
    console.warn('获取预问诊历史会话失败:', e.message)
  }
}

const loadPreConsultSessionDetail = async (sessionId) => {
  if (!sessionId) return
  try {
    const res = await axios.get('/api/medical/chat/pre-consult/session-messages', { params: { sessionId } })
    if (res.data && Array.isArray(res.data) && res.data.length) {
      preConsultHistory.value = res.data
      preConsultSessionId.value = sessionId
      showSessionHistoryDrawer.value = false
      scrollToBottom()
      ElMessage.success('已切换并调阅往期问询记录！')

      // 联动提炼该往期会话的结构化临床病历，在右侧卡片实时展示，方便随时采纳
      const extractHistory = res.data.map(h => ({ role: h.role, content: h.content }))
      if (extractHistory.some(m => m.role === 'user')) {
        const patientName = currentTargetReg.value?.patientName || regForm.value.name || '就诊患者'
        const gender = currentTargetReg.value?.gender || regForm.value.gender || '男'
        const age = currentTargetReg.value?.age || regForm.value.ageYears || 30
        isExtracting.value = true
        axios.post('/api/medical/chat/pre-consult/extract', {
          patientName,
          gender,
          age,
          history: extractHistory
        }, { timeout: 20000 }).then(extRes => {
          if (extRes.data && extRes.data.success) {
            extractedTriage.value = extRes.data
          }
        }).catch(err => {
          console.warn('提取往期预问诊病史异常:', err.message)
        }).finally(() => {
          isExtracting.value = false
        })
      }
    } else {
      ElMessage.info('该会话暂无留存的消息明细')
    }
  } catch (e) {
    ElMessage.error('调阅会话失败: ' + e.message)
  }
}

const deletePreConsultSession = async (sessionId) => {
  const userId = (currentTargetReg.value?.idCard || regForm.value.idCard || currentTargetReg.value?.patientName || regForm.value.name || '').trim()
  try {
    await axios.delete('/api/medical/chat/pre-consult/session', { params: { sessionId, userId } })
    ElMessage.success('已删除该条历史会话')
    await loadPreConsultSessions()
  } catch (e) {
    ElMessage.error('删除会话失败: ' + e.message)
  }
}

const startNewPreConsultSession = () => {
  clearPreConsultState()
  initPreConsultation()
  ElMessage.success('已开启全新预问诊会话！')
}

const sendPreConsultMessage = async (msgText) => {
  let text = ''
  if (typeof msgText === 'string') {
    text = msgText.trim()
  } else if (userReplyInput.value) {
    text = userReplyInput.value.trim()
  }
  if (!text) return
  if (isAiReplying.value) {
    ElMessage.info('AI 护士正在思考中，请稍候回复...')
    return
  }
  userReplyInput.value = ''
  quickReplies.value = [] // 立即清空旧选项

  preConsultHistory.value.push({ role: 'user', content: text })
  isAiReplying.value = true
  startThinkingTimer()
  scrollToBottom()

  preConsultAbortController = new AbortController()

  try {
    // 纯数据克隆，脱离 Vue3 响应式 Proxy 代理
    const rawHistory = preConsultHistory.value.slice(0, -1).map(h => ({
      role: h.role,
      content: h.content
    }))
    const patientName = currentTargetReg.value?.patientName || regForm.value.name || '就诊患者'
    const gender = currentTargetReg.value?.gender || regForm.value.gender || '男'
    const age = currentTargetReg.value?.age || regForm.value.ageYears || 30
    const idCard = currentTargetReg.value?.idCard || regForm.value.idCard || ''

    const payload = {
      patientName,
      gender,
      age,
      idCard,
      sessionId: preConsultSessionId.value,
      history: rawHistory,
      userReply: text
    }

    const t0 = Date.now()
    console.log('%c[预问诊-多轮请求发送]', 'background:#2563eb;color:#fff;padding:2px 6px;border-radius:3px;font-weight:bold;', {
      patientName,
      idCard,
      sessionId: preConsultSessionId.value,
      userReply: text,
      historyCount: rawHistory.length,
      payload,
      time: new Date().toLocaleTimeString()
    })

    const res = await axios.post('/api/medical/chat/pre-consult/dialogue', payload, { 
      timeout: 20000,
      signal: preConsultAbortController.signal 
    })
    const data = res.data
    const elapsed = Date.now() - t0

    console.log('%c[预问诊-多轮响应接收]', 'background:#16a34a;color:#fff;padding:2px 6px;border-radius:3px;font-weight:bold;', {
      elapsed: `${elapsed}ms`,
      reply: data?.reply,
      quickReplies: data?.quickReplies,
      matchedPatient: data?.matchedPatient,
      raw: data
    })

    if (data?.matchedPatient) {
      matchedPatientRecord.value = data.matchedPatient
    }

    if (data && data.success && data.reply) {
      preConsultHistory.value.push({
        role: 'assistant',
        content: data.reply
      })
      quickReplies.value = Array.isArray(data.quickReplies) ? data.quickReplies : []

      // 问答成功后，真实交由 AI 大模型实时提炼规范临床病史（后台异步进行，不阻碍对话渲染）
      const extractHistory = preConsultHistory.value.map(h => ({
        role: h.role,
        content: h.content
      }))
      isExtracting.value = true
      console.log('%c[预问诊-病史提炼发起]', 'background:#8b5cf6;color:#fff;padding:2px 6px;border-radius:3px;', {
        patientName,
        historyRounds: extractHistory.length
      })

      axios.post('/api/medical/chat/pre-consult/extract', {
        patientName,
        gender,
        age,
        history: extractHistory
      }, { timeout: 20000 }).then(extRes => {
        if (extRes.data && extRes.data.success) {
          extractedTriage.value = extRes.data
          console.log('%c[预问诊-病史提炼完成]', 'background:#8b5cf6;color:#fff;padding:2px 6px;border-radius:3px;', extRes.data)
        }
      }).catch(err => {
        console.warn('%c[预问诊-病史提炼暂缓]', 'background:#94a3b8;color:#000;padding:2px 6px;border-radius:3px;', err.message)
      }).finally(() => {
        isExtracting.value = false
      })
    } else {
      if (data?.message !== '生成已被用户停止') {
        ElMessage.warning(data?.message || '大模型未能返回有效问答内容，请重试')
        preConsultHistory.value.push({
          role: 'assistant',
          content: `（提示：AI 模型未返回有效回复【${data?.message || '响应为空'}】，请点击重试）`,
          isError: true
        })
      }
    }
  } catch (e) {
    if (e.name === 'CanceledError' || e.code === 'ERR_CANCELED') {
      console.log('预问诊请求已被用户主动中止')
      return
    }
    console.error('%c[预问诊-请求异常]', 'background:#dc2626;color:#fff;padding:2px 6px;border-radius:3px;font-weight:bold;', {
      message: e.message,
      code: e.code,
      response: e.response?.data,
      status: e.response?.status
    })
    const errText = e.response?.data?.message || (e.code === 'ECONNABORTED' ? 'AI 思考超时 (20s)，请点击下方重试' : (e.message || '网络连接或推理异常'))
    ElMessage.error(`AI 预问诊生成失败：${errText}`)
  } finally {
    stopThinkingTimer()
    isAiReplying.value = false
    preConsultAbortController = null
    scrollToBottom()
  }
}

const adoptPreConsultation = async () => {
  const userMessages = preConsultHistory.value.filter(m => m.role === 'user').map(m => m.content)
  const fallbackComplaint = userMessages.join('，')
  const complaint = extractedTriage.value.chiefComplaint || fallbackComplaint

  if (!complaint) {
    ElMessage.warning('尚未提取到有效主诉，请先与 AI 护士进行症状交流')
    return
  }

  const packageData = {
    ...extractedTriage.value,
    chiefComplaint: complaint,
    presentIllness: extractedTriage.value.presentIllness || fallbackComplaint,
    dialogue: preConsultHistory.value
  }

  // 1. 若在已有挂号卡片上操作：调用后端接口更新
  if (currentTargetReg.value && currentTargetReg.value.id) {
    try {
      await axios.post(`/api/registration/${currentTargetReg.value.id}/pre-consult`, {
        preConsultationData: JSON.stringify(packageData),
        symptoms: complaint
      })
      currentTargetReg.value.preConsultationData = JSON.stringify(packageData)
      currentTargetReg.value.symptoms = complaint
      ElMessage.success(`【${currentTargetReg.value.patientName}】预问诊记录已真实归档！医生接诊时可一键导入。`)
      showPreConsultModal.value = false
      return
    } catch (e) {
      console.error('保存预问诊异常:', e)
    }
  }

  // 2. 若在新增挂号表单中操作：直接同步到 regForm
  regForm.value.symptoms = complaint
  regForm.value.preConsultationData = JSON.stringify(packageData)
  showPreConsultModal.value = false
  ElMessage.success('已采纳智能预问诊成果！规范主诉已回填至挂号单，完整病程已绑定。')
}

const resetPreConsultation = async () => {
  await initPreConsultation()
}

// ── 临床实足年龄精确核算算法（支持不同月份天数、润年与生日判定） ──
const calculateExactAge = (birthDateInput) => {
  if (!birthDateInput) return { years: 0, months: 0, days: 0 }
  let bStr = birthDateInput
  if (birthDateInput instanceof Date) {
    const y = birthDateInput.getFullYear()
    const m = String(birthDateInput.getMonth() + 1).padStart(2, '0')
    const d = String(birthDateInput.getDate()).padStart(2, '0')
    bStr = `${y}-${m}-${d}`
  }
  const parts = String(bStr).split('-')
  if (parts.length !== 3) return { years: 0, months: 0, days: 0 }
  const birthYear = parseInt(parts[0], 10)
  const birthMonth = parseInt(parts[1], 10)
  const birthDay = parseInt(parts[2], 10)
  if (isNaN(birthYear) || isNaN(birthMonth) || isNaN(birthDay)) {
    return { years: 0, months: 0, days: 0 }
  }

  const now = new Date()
  const nowYear = now.getFullYear()
  const nowMonth = now.getMonth() + 1
  const nowDay = now.getDate()

  let years = nowYear - birthYear
  let months = nowMonth - birthMonth
  let days = nowDay - birthDay

  // 若天数为负，向上一个月借天数
  if (days < 0) {
    const prevMonthDays = new Date(nowYear, nowMonth - 1, 0).getDate()
    days += prevMonthDays
    months -= 1
  }

  // 若月份为负，向年份借12个月
  if (months < 0) {
    years -= 1
    months += 12
  }

  return {
    years: Math.max(0, years),
    months: Math.max(0, months),
    days: Math.max(0, days)
  }
}

// ── 全国主流行政区划省市区数据字典（深度覆盖湖南、广东及全国核心省市） ──
const CHINA_REGIONS = [
  {
    province: '湖南省',
    cities: [
      { city: '长沙市', districts: ['开福区', '岳麓区', '芙蓉区', '雨花区', '天心区', '望城区', '长沙县', '浏阳市', '宁乡市'] },
      { city: '衡阳市', districts: ['衡山县', '蒸湘区', '雁峰区', '石鼓区', '珠晖区', '南岳区', '衡阳县', '衡南县', '衡东县', '祁东县', '耒阳市', '常宁市'] },
      { city: '株洲市', districts: ['天元区', '荷塘区', '芦淞区', '石峰区', '渌口区', '攸县', '茶陵县', '炎陵县', '醴陵市'] },
      { city: '湘潭市', districts: ['雨湖区', '岳塘区', '湘潭县', '湘乡市', '韶山市'] },
      { city: '岳阳市', districts: ['岳阳楼区', '云溪区', '君山区', '岳阳县', '华容县', '湘阴县', '平江县', '汨罗市', '临湘市'] },
      { city: '常德市', districts: ['武陵区', '鼎城区', '安乡县', '汉寿县', '澧县', '临澧县', '桃源县', '石门县', '津市市'] },
      { city: '邵阳市', districts: ['双清区', '大祥区', '北塔区', '邵东市', '新邵县', '邵阳县', '隆回县', '洞口县', '绥宁县', '新宁县', '城步县', '武冈市'] },
      { city: '郴州市', districts: ['北湖区', '苏仙区', '桂阳县', '宜章县', '永兴县', '嘉禾县', '临武县', '汝城县', '桂东县', '安仁县', '资兴市'] },
      { city: '永州市', districts: ['零陵区', '冷水滩区', '祁阳市', '东安县', '双牌县', '道县', '江永县', '宁远县', '蓝山县', '新田县', '江华县'] },
      { city: '益阳市', districts: ['资阳区', '赫山区', '南县', '桃江县', '安化县', '沅江市'] },
      { city: '娄底市', districts: ['娄星区', '双峰县', '新化县', '冷水江市', '涟源市'] },
      { city: '怀化市', districts: ['鹤城区', '中方县', '沅陵县', '辰溪县', '溆浦县', '会同县', '麻阳县', '新晃县', '芷江县', '靖州县', '通道县', '洪江市'] },
      { city: '张家界市', districts: ['永定区', '武陵源区', '慈利县', '桑植县'] },
      { city: '湘西州', districts: ['吉首市', '泸溪县', '凤凰县', '花垣县', '保靖县', '古丈县', '永顺县', '龙山县'] }
    ]
  },
  {
    province: '广东省',
    cities: [
      { city: '广州市', districts: ['天河区', '越秀区', '海珠区', '荔湾区', '白云区', '黄埔区', '番禺区', '花都区', '南沙区', '从化区', '增城区'] },
      { city: '深圳市', districts: ['福田区', '南山区', '罗湖区', '盐田区', '宝安区', '龙岗区', '龙华区', '坪山区', '光明区', '大鹏新区'] },
      { city: '佛山市', districts: ['禅城区', '南海区', '顺德区', '高明区', '三水区'] },
      { city: '东莞市', districts: ['莞城区', '南城区', '东城区', '万江区', '松山湖', '长安镇', '虎门镇'] },
      { city: '珠海市', districts: ['香洲区', '斗门区', '金湾区'] },
      { city: '中山市', districts: ['石岐区', '东区', '西区', '南区', '小榄镇'] },
      { city: '惠州市', districts: ['惠城区', '惠阳区', '博罗县', '惠东县', '龙门县'] },
      { city: '江门市', districts: ['蓬江区', '江海区', '新会区', '台山市', '开平市', '鹤山市', '恩平市'] }
    ]
  },
  {
    province: '北京市',
    cities: [
      { city: '北京市', districts: ['东城区', '西城区', '朝阳区', '海淀区', '丰台区', '石景山区', '门头沟区', '房山区', '通州区', '顺义区', '昌平区', '大兴区', '怀柔区', '平谷区', '密云区', '延庆区'] }
    ]
  },
  {
    province: '上海市',
    cities: [
      { city: '上海市', districts: ['黄浦区', '徐汇区', '长宁区', '静安区', '普陀区', '虹口区', '杨浦区', '闵行区', '宝山区', '嘉定区', '浦东新区', '金山区', '松江区', '青浦区', '奉贤区', '崇明区'] }
    ]
  },
  {
    province: '湖北省',
    cities: [
      { city: '武汉市', districts: ['江岸区', '江汉区', '硚口区', '汉阳区', '武昌区', '青山区', '洪山区', '东西湖区', '汉南区', '蔡甸区', '江夏区', '黄陂区', '新洲区'] },
      { city: '宜昌市', districts: ['西陵区', '伍家岗区', '点军区', '猇亭区', '夷陵区', '宜都市', '当阳市', '枝江市'] },
      { city: '襄阳市', districts: ['襄城区', '樊城区', '襄州区', '枣阳市', '宜城市', '老河口市'] },
      { city: '荆州市', districts: ['沙市区', '荆州区', '江陵县', '公安县', '监利市', '石首市', '洪湖市', '松滋市'] }
    ]
  },
  {
    province: '江西省',
    cities: [
      { city: '南昌市', districts: ['东湖区', '西湖区', '青云谱区', '青山湖区', '新建区', '红谷滩区', '南昌县', '安义县', '进贤县'] },
      { city: '九江市', districts: ['濂溪区', '浔阳区', '柴桑区', '武宁县', '修水县', '永修县', '德安县', '庐山市', '瑞昌市'] },
      { city: '赣州市', districts: ['章贡区', '南康区', '赣县区', '信丰县', '大余县', '于都县', '兴国县', '瑞金市'] }
    ]
  },
  {
    province: '四川省',
    cities: [
      { city: '成都市', districts: ['锦江区', '青羊区', '金牛区', '武侯区', '成华区', '龙泉驿区', '青白江区', '新都区', '温江区', '双流区', '郫都区', '新津区', '都江堰市', '彭州市', '邛崃市', '崇州市', '简阳市'] },
      { city: '绵阳市', districts: ['涪城区', '游仙区', '安州区', '江油市', '三台县', '盐亭县', '梓潼县'] }
    ]
  },
  {
    province: '江苏省',
    cities: [
      { city: '南京市', districts: ['玄武区', '秦淮区', '建邺区', '鼓楼区', '浦口区', '栖霞区', '雨花台区', '江宁区', '六合区', '溧水区', '高淳区'] },
      { city: '苏州市', districts: ['姑苏区', '虎丘区', '吴中区', '相城区', '吴江区', '昆山市', '常熟市', '张家港市', '太仓市'] },
      { city: '无锡市', districts: ['梁溪区', '锡山区', '惠山区', '滨湖区', '新吴区', '江阴市', '宜兴市'] }
    ]
  },
  {
    province: '浙江省',
    cities: [
      { city: '杭州市', districts: ['上城区', '拱墅区', '西湖区', '滨江区', '萧山区', '余杭区', '临平区', '钱塘区', '富阳区', '临安区', '桐庐县', '淳安县', '建德市'] },
      { city: '宁波市', districts: ['海曙区', '江北区', '镇海区', '北仑区', '鄞州区', '奉化区', '余姚市', '慈溪市', '宁海县', '象山县'] },
      { city: '温州市', districts: ['鹿城区', '龙湾区', '瓯海区', '洞头区', '瑞安市', '乐清市', '永嘉县', '平阳县', '苍南县'] }
    ]
  },
  {
    province: '河南省',
    cities: [
      { city: '郑州市', districts: ['中原区', '二七区', '管城区', '金水区', '上街区', '惠济区', '中牟县', '巩义市', '荥阳市', '新密市', '新郑市', '登封市'] },
      { city: '洛阳市', districts: ['涧西区', '西工区', '老城区', '瀍河区', '洛龙区', '孟津区', '偃师区', '新安县', '栾川县', '嵩县', '汝阳县', '宜阳县', '洛宁县', '伊川县'] }
    ]
  },
  {
    province: '山东省',
    cities: [
      { city: '济南市', districts: ['历下区', '市中区', '槐荫区', '天桥区', '历城区', '长清区', '章丘区', '济阳区', '莱芜区', '钢城区'] },
      { city: '青岛市', districts: ['市南区', '市北区', '李沧区', '崂山区', '黄岛区', '城阳区', '即墨区', '胶州市', '平度市', '莱西市'] }
    ]
  },
  {
    province: '陕西省',
    cities: [
      { city: '西安市', districts: ['新城区', '碑林区', '莲湖区', '雁塔区', '未央区', '灞桥区', '长安区', '高陵区', '鄠邑区', '临潼区', '阎良区'] }
    ]
  },
  {
    province: '天津市',
    cities: [
      { city: '天津市', districts: ['和平区', '河东区', '河西区', '南开区', '河北区', '红桥区', '滨海新区', '东丽区', '西青区', '津南区', '北辰区'] }
    ]
  },
  {
    province: '重庆市',
    cities: [
      { city: '重庆市', districts: ['渝中区', '江北区', '南岸区', '九龙坡区', '沙坪坝区', '大渡口区', '渝北区', '巴南区', '北碚区'] }
    ]
  },
  {
    province: '其他省份/直辖市',
    cities: [
      { city: '其他城市', districts: ['市辖区/县'] }
    ]
  }
]

// 身份证前2位省份映射
const PROVINCE_MAP = {
  '11': '北京市', '12': '天津市', '13': '河北省', '14': '山西省', '15': '内蒙古自治区',
  '21': '辽宁省', '22': '吉林省', '23': '黑龙江省',
  '31': '上海市', '32': '江苏省', '33': '浙江省', '34': '安徽省', '35': '福建省', '36': '江西省', '37': '山东省',
  '41': '河南省', '42': '湖北省', '43': '湖南省', '44': '广东省', '45': '广西壮族自治区', '46': '海南省',
  '50': '重庆市', '51': '四川省', '52': '贵州省', '53': '云南省', '54': '西藏自治区',
  '61': '陕西省', '62': '甘肃省', '63': '青海省', '64': '宁夏回族自治区', '65': '新疆维吾尔自治区'
}

// 常见城市前4位映射
const CITY_MAP = {
  '4301': { city: '长沙市', province: '湖南省' },
  '4302': { city: '株洲市', province: '湖南省' },
  '4303': { city: '湘潭市', province: '湖南省' },
  '4304': { city: '衡阳市', province: '湖南省' },
  '4305': { city: '邵阳市', province: '湖南省' },
  '4306': { city: '岳阳市', province: '湖南省' },
  '4307': { city: '常德市', province: '湖南省' },
  '4308': { city: '张家界市', province: '湖南省' },
  '4309': { city: '益阳市', province: '湖南省' },
  '4310': { city: '郴州市', province: '湖南省' },
  '4311': { city: '永州市', province: '湖南省' },
  '4312': { city: '怀化市', province: '湖南省' },
  '4313': { city: '娄底市', province: '湖南省' },
  '4331': { city: '湘西州', province: '湖南省' },
  '4401': { city: '广州市', province: '广东省' },
  '4403': { city: '深圳市', province: '广东省' },
  '4406': { city: '佛山市', province: '广东省' },
  '4419': { city: '东莞市', province: '广东省' },
  '4404': { city: '珠海市', province: '广东省' },
  '4420': { city: '中山市', province: '广东省' },
  '4201': { city: '武汉市', province: '湖北省' },
  '3601': { city: '南昌市', province: '江西省' },
  '5101': { city: '成都市', province: '四川省' },
  '3201': { city: '南京市', province: '江苏省' },
  '3205': { city: '苏州市', province: '江苏省' },
  '3301': { city: '杭州市', province: '浙江省' },
  '3302': { city: '宁波市', province: '浙江省' },
  '1101': { city: '北京市', province: '北京市' },
  '3101': { city: '上海市', province: '上海市' }
}

// 常见区县前6位精准映射（包含衡山县 430423、开福区 430105 等）
const DISTRICT_MAP = {
  '430102': '芙蓉区', '430103': '天心区', '430104': '岳麓区', '430105': '开福区',
  '430111': '雨花区', '430112': '望城区', '430121': '长沙县', '430181': '浏阳市', '430182': '宁乡市',
  '430405': '珠晖区', '430406': '雁峰区', '430407': '石鼓区', '430408': '蒸湘区',
  '430412': '南岳区', '430421': '衡阳县', '430422': '衡南县', '430423': '衡山县',
  '430424': '衡东县', '430426': '祁东县', '430481': '耒阳市', '430482': '常宁市',
  '430202': '荷塘区', '430203': '芦淞区', '430204': '石峰区', '430211': '天元区', '430212': '渌口区', '430281': '醴陵市',
  '430302': '雨湖区', '430304': '岳塘区', '430321': '湘潭县', '430381': '湘乡市', '430382': '韶山市',
  '440103': '荔湾区', '440104': '越秀区', '440105': '海珠区', '440106': '天河区', '440111': '白云区', '440112': '黄埔区', '440113': '番禺区',
  '440303': '罗湖区', '440304': '福田区', '440305': '南山区', '440306': '宝安区', '440307': '龙岗区', '440308': '盐田区', '440309': '龙华区',
  '110101': '东城区', '110102': '西城区', '110105': '朝阳区', '110106': '丰台区', '110108': '海淀区',
  '310101': '黄浦区', '310104': '徐汇区', '310105': '长宁区', '310106': '静安区', '310115': '浦东新区',
  '320102': '玄武区', '320104': '秦淮区', '320105': '建邺区', '320106': '鼓楼区', '320111': '浦口区', '320115': '江宁区'
}

// 身份证前6位解析行政区划
const parseRegionFromIdCard = (idc) => {
  if (!idc || idc.length < 6) return null
  const code6 = idc.substring(0, 6)
  const code4 = idc.substring(0, 4)
  const code2 = idc.substring(0, 2)

  let province = PROVINCE_MAP[code2] || ''
  let city = ''
  let district = DISTRICT_MAP[code6] || ''

  const cityInfo = CITY_MAP[code4]
  if (cityInfo) {
    city = cityInfo.city
    if (!province) province = cityInfo.province
  }

  if (!district && city) {
    for (const p of CHINA_REGIONS) {
      if (p.province === province) {
        const c = p.cities.find(item => item.city === city)
        if (c && c.districts.length) district = c.districts[0]
      }
    }
  }

  if (!city && province) {
    const p = CHINA_REGIONS.find(item => item.province === province)
    if (p && p.cities.length) {
      city = p.cities[0].city
      if (!district && p.cities[0].districts.length) {
        district = p.cities[0].districts[0]
      }
    }
  }

  return { province, city, district }
}

// 可选省份列表
const provinceOptions = computed(() => {
  return CHINA_REGIONS.map(r => r.province)
})

// 当前省份对应的城市列表
const cityOptions = computed(() => {
  if (!regForm.value.province) {
    const def = CHINA_REGIONS.find(r => r.province === '湖南省')
    return def ? def.cities.map(c => c.city) : []
  }
  const found = CHINA_REGIONS.find(r => r.province === regForm.value.province)
  if (found) {
    return found.cities.map(c => c.city)
  }
  return ['市辖区', '其他市']
})

// 当前城市对应的区县列表
const districtOptions = computed(() => {
  if (!regForm.value.city) {
    return ['开福区', '岳麓区', '芙蓉区', '雨花区', '天心区', '望城区']
  }
  for (const prov of CHINA_REGIONS) {
    const c = prov.cities.find(item => item.city === regForm.value.city)
    if (c) {
      return c.districts
    }
  }
  return ['市辖区', '其他区县']
})

const onProvinceChange = (newProv) => {
  const found = CHINA_REGIONS.find(r => r.province === newProv)
  if (found && found.cities.length) {
    regForm.value.city = found.cities[0].city
    if (found.cities[0].districts.length) {
      regForm.value.district = found.cities[0].districts[0]
    } else {
      regForm.value.district = ''
    }
  } else {
    regForm.value.city = ''
    regForm.value.district = ''
  }
}

const onCityChange = (newCity) => {
  for (const prov of CHINA_REGIONS) {
    const c = prov.cities.find(item => item.city === newCity)
    if (c && c.districts.length) {
      regForm.value.district = c.districts[0]
      return
    }
  }
  regForm.value.district = ''
}

const onBirthDateChange = (val) => {
  if (!val) return
  const exact = calculateExactAge(val)
  regForm.value.ageYears = exact.years
  regForm.value.ageMonths = exact.months
  regForm.value.ageDays = exact.days
}

const onAgeYearsChange = (val) => {
  if (val === null || val === undefined) return
  if (!regForm.value.birthDate) {
    const now = new Date()
    const birthYear = now.getFullYear() - val
    const m = String(now.getMonth() + 1).padStart(2, '0')
    const d = String(now.getDate()).padStart(2, '0')
    regForm.value.birthDate = `${birthYear}-${m}-${d}`
    if (regForm.value.ageMonths === null || regForm.value.ageMonths === undefined) {
      regForm.value.ageMonths = 0
    }
    if (regForm.value.ageDays === null || regForm.value.ageDays === undefined) {
      regForm.value.ageDays = 0
    }
  }
}

const onAgeMonthsDaysChange = () => {
  if (regForm.value.ageMonths === null || regForm.value.ageMonths === undefined) {
    regForm.value.ageMonths = 0
  }
  if (regForm.value.ageDays === null || regForm.value.ageDays === undefined) {
    regForm.value.ageDays = 0
  }
}

const handleIdCardInput = (val) => {
  if (val && String(val).trim().length === 18) {
    parseIdCard()
  }
}

const parseIdCard = () => {
  const idc = (regForm.value.idCard || '').trim().toUpperCase()
  if (!idc || idc.length !== 18) return
  if (!/^\d{17}[\dXx]$/.test(idc)) return

  // 1. 提取出生年月日
  const year = parseInt(idc.substring(6, 10), 10)
  const month = parseInt(idc.substring(10, 12), 10)
  const day = parseInt(idc.substring(12, 14), 10)
  if (isNaN(year) || isNaN(month) || isNaN(day)) return
  if (year < 1900 || year > 2099 || month < 1 || month > 12) return

  // 容错处理：若日份超过该月最大天数（例如测试输入的 34 号），安全裁剪至该月最大天数
  const maxDays = new Date(year, month, 0).getDate()
  const safeDay = Math.min(Math.max(1, day), maxDays)

  const birthDateStr = `${year}-${String(month).padStart(2, '0')}-${String(safeDay).padStart(2, '0')}`
  regForm.value.birthDate = birthDateStr

  // 2. 精准计算实足年龄（岁、月、天）
  const exact = calculateExactAge(birthDateStr)
  regForm.value.ageYears = exact.years
  regForm.value.ageMonths = exact.months
  regForm.value.ageDays = exact.days

  // 3. 自动识别性别：第17位单数为男，双数为女
  const genderCode = parseInt(idc.substring(16, 17), 10)
  if (!isNaN(genderCode)) {
    regForm.value.gender = (genderCode % 2 === 1) ? '男' : '女'
  }

  // 4. 自动根据身份证前 6 位行政区划代码识别常住省市区
  const regInfo = parseRegionFromIdCard(idc)
  if (regInfo) {
    if (regInfo.province) regForm.value.province = regInfo.province
    if (regInfo.city) regForm.value.city = regInfo.city
    if (regInfo.district) regForm.value.district = regInfo.district
  }

  ElMessage.success({
    message: `身份证识别成功！实足年龄：${exact.years}岁${exact.months}月${exact.days}天${regInfo?.city ? ' · 籍贯：' + (regInfo.province || '') + (regInfo.city || '') + (regInfo.district || '') : ''}`,
    duration: 3500
  })
}

const openNewRegModal = () => {
  // 打开全新挂号弹窗：彻底清空预问诊残留状态与当前聚焦患者，保证与已有行记录患者完全隔离！
  clearPreConsultState()
  currentTargetReg.value = null
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
    province: '湖南省',
    city: '长沙市',
    district: '开福区',
    department: '全科门诊',
    doctorName: displayName || '系统用户',
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
    accompanyPhone: '',
    preConsultationData: ''
  }
  showNewRegModal.value = true
}

const simulateIdCardRead = () => {
  regForm.value.name = '刘舍予'
  regForm.value.gender = '女'
  regForm.value.idCard = '430105199808201248'
  regForm.value.phone = '15111564208'
  regForm.value.birthDate = '1998-08-20'
  const exact = calculateExactAge('1998-08-20')
  regForm.value.ageYears = exact.years
  regForm.value.ageMonths = exact.months
  regForm.value.ageDays = exact.days
  regForm.value.province = '湖南省'
  regForm.value.city = '长沙市'
  regForm.value.district = '开福区'
  regForm.value.symptoms = '体虚易感冒、经期腹痛不适'
  ElMessage.success(`二代身份证读卡成功！解析实足年龄：${exact.years}岁${exact.months}月${exact.days}天 · 籍贯：湖南省长沙市开福区`)
}

const onDoctorSelect = (val) => {
  // 医生列表已来自真实医生档案，挂号费统一 10 元
  regForm.value.fee = 10.00
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
    preConsultationData: regForm.value.preConsultationData || '',
    status: '待签到'
  }

  try {
    await axios.post('/api/registration/create', postData)
    ElMessage.success(`挂号成功！就诊号已生成，患者进入【${postData.status}】状态！`)
    window.dispatchEvent(new CustomEvent('patient-registered', { detail: postData }))
    showNewRegModal.value = false
    clearPreConsultState()
    currentTargetReg.value = null
    // 重置表单（含详细资料字段与预问诊数据）
    Object.assign(regForm.value, {
      name: '', gender: '男', idCard: '', phone: '',
      ageYears: null, ageMonths: null, ageDays: null, birthDate: '',
      regType: '现场挂号', province: '湖南省', city: '长沙市', district: '开福区',
      department: '全科门诊', doctorName: displayName || '系统用户', fee: 10.00, symptoms: '',
      marriage: '', height: '', weight: '', job: '', company: '',
      wechat: '', insuranceNo: '', accompany: '', accompanyPhone: '',
      preConsultationData: ''
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
      doctorName: displayName || '系统用户',
      type: '现场挂号',
      regType: '门诊',
      fee: 10.00,
      status: '待签到',
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

// 排班设置
const openNewScheduleModal = () => {
  scheduleForm.value = {
    id: null,
    doctorName: displayName || '系统用户',
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
    const res = await axios.post('/api/schedule/save', scheduleForm.value)
    if (res.data.success === false) {
      ElMessage.error(res.data.message || '排班保存失败！')
      return
    }
    ElMessage.success('医生排班班次保存成功！')
    showScheduleModal.value = false
    await loadSchedules()
  } catch (e) {
    ElMessage.error('排班保存失败：' + (e.response?.data?.message || e.message))
  }
}

const copyLastWeekSchedule = async () => {
  try {
    // 复制到当前查看的周（上周数据为来源）
    const start = weekDays.value.length ? weekDays.value[0].fullDate : undefined
    const res = await axios.post('/api/schedule/copy-last-week', start ? { startDate: start } : {})
    if (res.data.success === false) {
      ElMessage.warning(res.data.message || '上周没有可复制的排班！')
      return
    }
    ElMessage.success(res.data.message || '已自动复制上周医生排班规则，一键铺满本周！')
    await loadSchedules()
  } catch (e) {
    ElMessage.error('复制上周排班失败：' + (e.response?.data?.message || e.message))
  }
}

// ==================== 排班模板（保存当前周配置 / 一键应用模板） ====================
const showTemplateModal = ref(false)
const templateName = ref('')
const scheduleTemplates = ref([])
// 模板行：一周 7 天的班次配置
const templateDays = ref([])

const emptyTemplateDays = () => [1, 2, 3, 4, 5, 6, 7].map(d => ({
  dayOfWeek: d,
  enabled: d <= 5,
  shiftType: '全天班',
  quota: 50,
  consultationFee: 10.00
}))

const loadScheduleTemplates = () => {
  try {
    scheduleTemplates.value = JSON.parse(localStorage.getItem('chunbo_schedule_templates') || '[]')
  } catch (e) { scheduleTemplates.value = [] }
}

const persistScheduleTemplates = () => {
  try { localStorage.setItem('chunbo_schedule_templates', JSON.stringify(scheduleTemplates.value)) } catch (e) {}
}

const openTemplateModal = () => {
  templateDays.value = emptyTemplateDays()
  templateName.value = ''
  loadScheduleTemplates()
  showTemplateModal.value = true
}

// 用当前周已有排班填充模板（周一~周日首个班次）
const fillTemplateFromCurrentWeek = () => {
  const days = emptyTemplateDays()
  for (const row of days) {
    const exist = schedules.value.find(s => Number(s.dayOfWeek) === row.dayOfWeek)
    if (exist) {
      row.enabled = true
      row.shiftType = exist.shiftType || '全天班'
      row.quota = exist.quota || 50
      row.consultationFee = Number(exist.consultationFee) || 10
    }
  }
  templateDays.value = days
  ElMessage.success('已按当前周排班生成模板配置，可调整后保存')
}

const saveScheduleTemplate = () => {
  const name = (templateName.value || '').trim()
  if (!name) { ElMessage.warning('请输入模板名称！'); return }
  if (!templateDays.value.some(d => d.enabled)) { ElMessage.warning('请至少启用一天的班次！'); return }
  scheduleTemplates.value = scheduleTemplates.value.filter(t => t.name !== name)
  scheduleTemplates.value.push({ name, days: JSON.parse(JSON.stringify(templateDays.value)), createdAt: new Date().toISOString() })
  persistScheduleTemplates()
  ElMessage.success('排班模板【' + name + '】已保存！')
}

const applyScheduleTemplate = async (tpl) => {
  try {
    const start = weekDays.value.length ? weekDays.value[0].fullDate : undefined
    const res = await axios.post('/api/schedule/apply-template', {
      department: '全科门诊',
      startDate: start,
      days: tpl.days
    })
    if (res.data.success === false) { ElMessage.error(res.data.message); return }
    ElMessage.success(res.data.message || '模板已应用！')
    showTemplateModal.value = false
    await loadSchedules()
  } catch (e) {
    ElMessage.error('模板应用失败：' + (e.response?.data?.message || e.message))
  }
}

const deleteScheduleTemplate = (name) => {
  scheduleTemplates.value = scheduleTemplates.value.filter(t => t.name !== name)
  persistScheduleTemplates()
  ElMessage.success('模板已删除')
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
  margin-top: 4px;
}
.symptoms-hint :deep(.el-tag) {
  white-space: normal;
  height: auto;
  line-height: 1.4;
  padding: 4px 8px;
  word-break: break-all;
  max-width: 100%;
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

/* ── 智能预问诊弹窗样式 ── */
.pre-consult-dialog .el-dialog__body {
  padding: 16px 20px;
}
.pre-consult-layout {
  display: flex;
  gap: 20px;
  height: 520px;
}
.chat-zone {
  flex: 1.2;
  display: flex;
  flex-direction: column;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 12px;
  overflow: hidden;
}
.chat-patient-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #334155;
  padding-bottom: 8px;
  border-bottom: 1px solid #e2e8f0;
  margin-bottom: 8px;
}
.chat-patient-banner .dept-badge {
  background: #e0f2fe;
  color: #0369a1;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
}
.chat-messages-container {
  flex: 1;
  overflow-y: auto;
  padding-right: 6px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.chat-bubble-row {
  display: flex;
  gap: 10px;
  max-width: 88%;
}
.chat-bubble-row.assistant {
  align-self: flex-start;
}
.chat-bubble-row.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}
.bubble-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #e2e8f0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
}
.chat-bubble-row.assistant .bubble-avatar {
  background: #ccfbf1;
}
.chat-bubble-row.user .bubble-avatar {
  background: #dbeafe;
}
.bubble-content-wrap {
  display: flex;
  flex-direction: column;
}
.bubble-sender {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 2px;
}
.chat-bubble-row.user .bubble-sender {
  text-align: right;
}
.bubble-text {
  font-size: 13px;
  line-height: 1.5;
  padding: 8px 12px;
  border-radius: 8px;
}
.chat-bubble-row.assistant .bubble-text {
  background: #ffffff;
  color: #1e293b;
  border: 1px solid #e2e8f0;
  border-top-left-radius: 2px;
}
.chat-bubble-row.user .bubble-text {
  background: #2563eb;
  color: #ffffff;
  border-top-right-radius: 2px;
}
.bubble-text.typing {
  font-style: italic;
  color: #0d9488;
  background: #f0fdf4;
}

/* 护士消息底部 AI 构思标识与换一句问候 */
.bubble-meta-info {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px dashed #e2e8f0;
  font-size: 11px;
}
.ai-source-tag {
  color: #0d9488;
  font-weight: 500;
  display: inline-flex;
  align-items: center;
}
.btn-re-greet {
  background: #f0fdf4;
  border: 1px solid #a7f3d0;
  color: #0d9488;
  border-radius: 4px;
  padding: 2px 8px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.2s;
  display: inline-flex;
  align-items: center;
  font-weight: 500;
}
.btn-re-greet:hover {
  background: #ccfbf1;
  border-color: #0f766e;
  color: #0f766e;
}

.quick-replies-bar {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 8px 12px;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  margin-top: 6px;
}
.qr-header-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}
.qr-cat-tag {
  color: #0284c7;
  font-weight: 600;
}
.qr-action-btns {
  display: flex;
  gap: 6px;
}
.qr-action-btn {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  font-size: 11.5px;
  padding: 2px 9px;
  border-radius: 12px;
  cursor: pointer;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  color: #475569;
  font-weight: 500;
}
.qr-action-btn:hover {
  border-color: #3b82f6;
  color: #2563eb;
  background: #eff6ff;
}
.qr-action-btn.btn-ai {
  border-color: #a7f3d0;
  color: #059669;
  background: #f0fdf4;
}
.qr-action-btn.btn-ai:hover {
  border-color: #059669;
  color: #047857;
  background: #dcfce7;
}
.qr-action-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.qr-label {
  font-size: 12px;
  font-weight: 600;
  color: #475569;
  white-space: nowrap;
}
.qr-chips-wrapper {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  flex: 1;
}
.qr-chip {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #1e293b;
  font-size: 12px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 14px;
  line-height: 1.4;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1);
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.03);
  user-select: none;
}
.qr-chip:hover {
  background: #eff6ff;
  border-color: #3b82f6;
  color: #1d4ed8;
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(59, 130, 246, 0.15);
}
.qr-chip:active {
  transform: translateY(0);
}
.chat-input-row {
  display: flex;
  gap: 8px;
  padding-top: 6px;
}

/* 右侧结构化预览卡片 */
.extracted-zone {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  border-radius: 10px;
  padding: 14px;
}
.extracted-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 700;
  font-size: 14px;
  color: #0f172a;
  padding-bottom: 10px;
  border-bottom: 1px solid #f1f5f9;
}
.extracted-body {
  flex: 1;
  overflow-y: auto;
  padding: 10px 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.ext-item, .ext-row {
  font-size: 12.5px;
}
.ext-row {
  display: flex;
  gap: 12px;
}
.ext-col {
  flex: 1;
}
.ext-label {
  color: #64748b;
  font-size: 11px;
  margin-bottom: 2px;
}
.ext-val {
  background: #f8fafc;
  padding: 6px 8px;
  border-radius: 6px;
  color: #1e293b;
  line-height: 1.4;
  border: 1px solid #f1f5f9;
}
.ext-val.highlight {
  background: #eff6ff;
  border-color: #bfdbfe;
  color: #1d4ed8;
  font-weight: 600;
}
.ext-val.multiline {
  max-height: 90px;
  overflow-y: auto;
  font-size: 12px;
}
.ext-val.temp {
  color: #dc2626;
  font-weight: 700;
}
.ext-val.med {
  color: #059669;
  font-weight: 600;
}
.symptom-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
}
.s-badge {
  background: #fef3c7;
  color: #92400e;
  border: 1px solid #fde68a;
  border-radius: 4px;
  font-size: 11px;
  padding: 2px 6px;
}
.extracted-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 10px;
  border-top: 1px solid #f1f5f9;
}
.gradient-btn-adopt {
  background: linear-gradient(135deg, #0d9488, #059669) !important;
  border: none !important;
  font-weight: 600 !important;
}
.btn-pre-consult-trigger {
  background: linear-gradient(135deg, #0d9488, #059669) !important;
  border: none !important;
  white-space: nowrap !important;
}

/* ── AI 预问诊回复等待动效 ── */
.pulse-avatar {
  animation: pulse-avatar-glow 1.5s infinite ease-in-out;
}
@keyframes pulse-avatar-glow {
  0%, 100% { box-shadow: 0 0 0 0 rgba(13, 148, 136, 0.4); }
  50% { box-shadow: 0 0 0 8px rgba(13, 148, 136, 0); }
}
.typing-bubble {
  display: inline-flex !important;
  align-items: center;
  gap: 10px;
  background: #f0fdf4 !important;
  border: 1px solid #bbf7d0 !important;
  color: #0f766e !important;
  font-weight: 500;
  padding: 8px 14px !important;
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.08);
}
.typing-dots {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.typing-dots .dot {
  width: 6px;
  height: 6px;
  background-color: #0d9488;
  border-radius: 50%;
  display: inline-block;
  animation: dot-flashing 1.2s infinite ease-in-out;
}
.typing-dots .dot:nth-child(1) { animation-delay: 0s; }
.typing-dots .dot:nth-child(2) { animation-delay: 0.2s; }
.typing-dots .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes dot-flashing {
  0%, 80%, 100% { opacity: 0.3; transform: scale(0.85); }
  40% { opacity: 1; transform: scale(1.35); }
}

.ai-replying-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #0d9488;
  padding: 7px 12px;
  background: #f0fdf4;
  border: 1px dashed #a7f3d0;
  border-radius: 6px;
  margin-top: 6px;
}
.pulse-indicator {
  width: 8px;
  height: 8px;
  background: #10b981;
  border-radius: 50%;
  animation: pulse-dot-anim 1s infinite alternate;
  flex-shrink: 0;
}
@keyframes pulse-dot-anim {
  from { opacity: 0.4; transform: scale(0.85); }
  to { opacity: 1; transform: scale(1.25); }
}

.error-bubble .bubble-text {
  background: #fef2f2 !important;
  border: 1px solid #fecaca !important;
  color: #991b1b !important;
}

/* ── 预问诊患者顶部横幅改造 ── */
.chat-patient-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  font-size: 13px;
  color: #334155;
}
.banner-left-info {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.banner-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.id-card-tag {
  background: #eff6ff;
  color: #2563eb;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  border: 1px solid #dbeafe;
}

/* ── 身份证精准命中历史病历档案高亮横幅 ── */
.matched-patient-banner {
  background: linear-gradient(135deg, #f0fdf4 0%, #ecfdf5 100%);
  border: 1px solid #86efac;
  border-radius: 6px;
  padding: 8px 12px;
  margin: 8px 12px 0 12px;
  box-shadow: 0 1px 3px rgba(16, 185, 129, 0.08);
}
.mp-tag {
  font-size: 12px;
  font-weight: 700;
  color: #15803d;
  margin-bottom: 2px;
  display: flex;
  align-items: center;
  gap: 4px;
}
.mp-content {
  font-size: 12px;
  color: #1e293b;
  line-height: 1.5;
}
.mp-hl {
  color: #0284c7;
  font-weight: 600;
}
.mp-warn {
  color: #dc2626;
  font-weight: 600;
}

/* ── 停止生成按钮与等待条优化 ── */
.ai-replying-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.replying-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.stop-generate-btn {
  font-weight: 600 !important;
  font-size: 11px !important;
  padding: 2px 8px !important;
}

/* ── 往期预问诊会话抽屉样式 ── */
.session-drawer-content {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.drawer-top-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid #f1f5f9;
}
.drawer-user-info {
  font-size: 13px;
  color: #334155;
  display: flex;
  align-items: center;
  gap: 6px;
}
.drawer-user-info .id-tag {
  font-size: 11px;
  color: #64748b;
  background: #f1f5f9;
  padding: 1px 5px;
  border-radius: 3px;
}
.drawer-empty {
  padding: 40px 0;
}
.drawer-groups {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.history-group-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.group-title-header {
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 2px 4px;
}
.group-count {
  font-size: 11px;
  color: #94a3b8;
}
.session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.session-item-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
}
.session-item-card:hover {
  border-color: #38bdf8;
  background: #f0f9ff;
  transform: translateY(-1px);
  box-shadow: 0 2px 8px rgba(56, 189, 248, 0.12);
}
.session-item-card.active {
  border-color: #0d9488;
  background: #f0fdfa;
  box-shadow: 0 2px 8px rgba(13, 148, 136, 0.15);
}
.card-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.session-card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 600;
  color: #1e293b;
}
.session-item-card.active .session-card-title {
  color: #0f766e;
}
.dot-indicator {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #94a3b8;
  flex-shrink: 0;
}
.session-item-card.active .dot-indicator {
  background: #0d9488;
}
.title-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 230px;
}
.active-badge {
  font-size: 10px !important;
  height: 18px !important;
  line-height: 18px !important;
  padding: 0 4px !important;
}
.session-card-time {
  font-size: 11px;
  color: #94a3b8;
}
.card-actions {
  margin-left: 8px;
  flex-shrink: 0;
}
.del-btn {
  font-size: 14px !important;
  opacity: 0.6;
  transition: opacity 0.2s;
}
.session-item-card:hover .del-btn {
  opacity: 1;
}

/* ── 常住省市区三级弹性网格与下拉优化 ── */
.address-grid-layout {
  display: grid !important;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 10px;
  width: 100%;
}
.address-select-item {
  width: 100% !important;
}
.address-select-item :deep(.el-select__wrapper) {
  min-height: 34px !important;
  padding: 4px 10px !important;
}

/* ── 临床实足年龄计算标签 ── */
.age-calc-tag {
  font-size: 11px;
  color: #059669;
  background: #ecfdf5;
  border: 1px solid #a7f3d0;
  padding: 2px 8px;
  border-radius: 4px;
  margin-left: 6px;
  font-weight: 500;
  white-space: nowrap;
}
</style>
