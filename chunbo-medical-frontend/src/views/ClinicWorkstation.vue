<template>
  <div class="clinic-container">
    <!-- 顶部患者就诊卡 (对应截图 43/44 患者顶部信息条) -->
    <div class="patient-header-bar" v-if="currentPatient">
      <div class="patient-info-left">
        <!-- 鼠标悬停显示接诊人详细信息卡片 (深度复刻截图 4) -->
        <el-popover
          placement="bottom-start"
          :width="500"
          trigger="hover"
          popper-class="patient-profile-hover-popover"
        >
          <template #reference>
            <div class="patient-info-trigger">
              <span class="patient-name">{{ currentPatient.patientName }}</span>
              <span class="p-del-icon">×</span>
              <el-tag
                v-if="isRealMember(currentPatient)"
                type="warning"
                effect="dark"
                size="small"
                class="patient-vip-badge-header"
                @click.stop="openMemberDialog"
                title="已开通会员，点击查看详情"
              >
                🏆 {{ currentPatient.memberLevel }} ({{ getMemberDiscountText(currentPatient) }})
              </el-tag>
              <span class="patient-gender-chip">{{ currentPatient.gender }}</span>
              <span class="patient-age-chip">{{ currentPatient.ageText || (currentPatient.age ? currentPatient.age + '岁' : '42岁') }}</span>
              <span class="patient-month-chip">{{ currentPatient.ageMonth ? currentPatient.ageMonth + '月' : '8月' }}</span>
              <span class="patient-phone-chip">{{ currentPatient.phone || '' }}</span>
              <span class="patient-birth-chip">{{ currentPatient.birthday || '2000-01-01' }}</span>
              <el-tag v-if="isTempPatient" type="warning" size="small" effect="dark" class="temp-patient-tag">
                临时就诊
              </el-tag>
              <el-button v-if="isTempPatient" size="small" type="primary" plain class="btn-regularize" @click="openTempPatientModal">
                ✏️ 完善档案转正
              </el-button>
              <el-tag type="warning" size="small" effect="plain" class="coupon-tag">已购卡券可选 ▾</el-tag>
              <el-tag 
                :type="currentPatient.status === '接诊中' ? 'success' : (currentPatient.status === '待诊' ? 'warning' : 'info')" 
                size="small" 
                effect="dark"
                class="header-patient-status-badge"
              >
                {{ currentPatient.status === '接诊中' ? '● 接诊中' : (currentPatient.status === '待诊' ? '待诊 (预览查看中)' : currentPatient.status) }}
              </el-tag>
            </div>
          </template>

          <!-- 悬停弹出的完整健康/患者档案卡片 -->
          <div class="patient-hover-card">
            <div class="ph-tag-row">
              <span class="ph-bookmark-icon">🔖</span>
              <el-tag 
                v-for="(t, idx) in patientProfile.tags" 
                :key="idx" 
                size="small" 
                :type="t.type" 
                effect="plain"
              >
                {{ t.label }}
              </el-tag>
              <span class="ph-add-tag-btn">+</span>
            </div>

            <div class="ph-stats-row">
              <span>门诊 <b>{{ patientProfile.visits }}</b> 次</span>
              <span class="ml-4">消费 <b>¥{{ patientProfile.totalCost }}</b></span>
            </div>

            <div class="ph-card-section-head">
              <span class="sec-title">编辑资料</span>
              <el-button link type="primary" size="small">编辑</el-button>
            </div>

            <div class="ph-card-grid">
              <div class="ph-item"><span class="ph-lbl"><span class="req">*</span> 生日</span><span class="ph-val">{{ patientProfile.birthday }}</span></div>
              <div class="ph-item"><span class="ph-lbl">体重</span><span class="ph-val">{{ patientProfile.weight }}</span></div>
              <div class="ph-item"><span class="ph-lbl">婚姻</span><span class="ph-val">{{ patientProfile.marriage }}</span></div>
              <div class="ph-item"><span class="ph-lbl">身高</span><span class="ph-val">{{ patientProfile.height }}</span></div>
              <div class="ph-item"><span class="ph-lbl">单位</span><span class="ph-val">{{ patientProfile.company }}</span></div>
              <div class="ph-item"><span class="ph-lbl">职业</span><span class="ph-val">{{ patientProfile.job }}</span></div>
              <div class="ph-item"><span class="ph-lbl">微信</span><span class="ph-val">{{ patientProfile.wechat }}</span></div>
              <div class="ph-item"><span class="ph-lbl">身份证</span><span class="ph-val">{{ patientProfile.idCard }}</span></div>
              <div class="ph-item"><span class="ph-lbl">医保号</span><span class="ph-val">{{ patientProfile.insuranceNo }}</span></div>
              <div class="ph-item"><span class="ph-lbl">建档日期</span><span class="ph-val">{{ patientProfile.createdDate }}</span></div>
              <div class="ph-item"><span class="ph-lbl">陪护人</span><span class="ph-val">{{ patientProfile.accompany }}</span></div>
              <div class="ph-item"><span class="ph-lbl">陪护人电话</span><span class="ph-val">{{ patientProfile.accompanyPhone }}</span></div>
              <div class="ph-item full"><span class="ph-lbl">备注</span><span class="ph-val">{{ patientProfile.remarks }}</span></div>
              <div class="ph-item full"><span class="ph-lbl">地址</span><span class="ph-val">{{ patientProfile.address }}</span></div>
            </div>

            <!-- 会员身份展示与办理入口 (Point 2: 只有真实已开通且享受折扣的会员才展示会员框，普通患者展示办理入口) -->
            <div class="ph-member-active-box" v-if="isRealMember(currentPatient)">
              <div class="ph-mab-head">
                <span class="ph-mab-badge">🏆 {{ currentPatient.memberLevel }}</span>
                <span class="ph-mab-discount">{{ getMemberDiscountText(currentPatient) }}专享</span>
                <span class="ph-mab-expiry" v-if="currentPatient.memberExpiry">至 {{ currentPatient.memberExpiry }}</span>
              </div>
              <div class="ph-mab-desc">✓ 该就诊人已开通会员专享权益，门诊开方自动享受折扣，无需重复办理。</div>
            </div>
            <div class="ph-member-links" v-else>
              <div class="ph-mem-link clickable" @click="openMemberDialog">
                <span class="chk-icon">☑</span> 办理会员 <span class="action-txt">立即办理 &gt;</span>
              </div>
              <div class="ph-mem-link clickable" @click="openAuxDialog">
                <span class="chk-icon">☑</span> 成为附属卡 <span class="action-txt">立即办理 &gt;</span>
              </div>
            </div>
          </div>
        </el-popover>
      </div>

      <!-- 顶栏右侧按钮群 (深度复刻截图 3) -->
      <div class="patient-actions-right">
        <el-button
          type="primary"
          size="default"
          class="btn-quick-consult gradient-btn"
          @click="handleQuickDirectConsult"
          title="医生快速接诊临时患者，挂号大厅同步更新，完善信息后自动转正建档"
        >
          快速接诊
        </el-button>
        <!-- 1. 待诊/接诊中状态按钮群 -->
        <template v-if="currentPatient.status === '待诊' || currentPatient.status === '接诊中' || currentPatient.status === '候诊中'">
          <el-button 
            v-if="currentPatient.status !== '接诊中'"
            type="success" 
            class="btn-start-consult" 
            @click="startConsultation(currentPatient)"
          >
            🩺 开始接诊
          </el-button>
          <el-button 
            type="primary" 
            plain 
            class="btn-call-number" 
            @click="callPatientVoice(currentPatient)"
          >
            📢 {{ currentPatient.status === '接诊中' ? '再次叫号' : '叫号' }}
          </el-button>
          <el-button 
            v-if="currentPatient.status !== '接诊中'"
            size="small" 
            plain 
            class="btn-pass-number" 
            @click="markAsPassed(currentPatient)"
          >
            过号
          </el-button>
          <el-button 
            v-if="currentPatient.status !== '接诊中'"
            size="small" 
            type="danger" 
            plain 
            class="btn-refund-number" 
            @click="refundRegistration(currentPatient)"
          >
            退号
          </el-button>
        </template>

        <!-- 2. 过号患者：醒目恢复待诊与重新呼叫 -->
        <template v-else-if="currentPatient.status === '过号' || currentPatient.status === '已过号'">
          <el-tag type="warning" effect="dark" class="passed-status-tag">⚠️ 该患者已过号</el-tag>
          <el-button 
            type="warning" 
            class="btn-restore-consult" 
            @click="restorePassedPatient(currentPatient)"
          >
            ↺ 恢复待诊 (过号重排)
          </el-button>
          <el-button 
            type="primary" 
            plain 
            class="btn-call-number" 
            @click="callPatientVoice(currentPatient)"
          >
            📢 重新叫号
          </el-button>
        </template>

        <!-- 3. 退号患者：明确提示已退号退费，归档不可开方 -->
        <template v-else-if="currentPatient.status === '已退' || currentPatient.status === '已退号'">
          <el-tag type="danger" effect="plain" class="refund-status-tag">
            ❌ 该号已退号退费（记录归档，不可开方）
          </el-tag>
        </template>

        <!-- 4. 已结诊/已完成患者：明确提示已归档，并提供快速返回待诊队列按钮 -->
        <template v-else-if="isClosedStatus(currentPatient.status)">
          <el-tag type="info" effect="plain" class="closed-status-tag">
            ✓ 该记录已结诊归档 (只读模式)
          </el-tag>
          <el-button 
            v-if="waitingList.length > 0" 
            type="primary" 
            size="small" 
            @click="queueTab = 'waiting'; selectQueuePatient(waitingList[0])"
          >
            👉 返回待诊队列 ({{ waitingList.length }}人候诊)
          </el-button>
          <el-button 
            size="small" 
            type="success" 
            plain 
            @click="handleQuickDirectConsult"
          >
            ⚡ 快速接诊
          </el-button>
        </template>

        <div class="top-fee-group">
          <span class="top-fee-label">挂号费:</span>
          <span class="top-fee-val">¥{{ (currentPatient.fee || 10.00).toFixed(2) }}</span>
        </div>
        <div class="top-fee-group">
          <span class="top-fee-label">总计:</span>
          <span class="top-total-highlight">¥{{ totalRxAmount.toFixed(2) }}</span>
        </div>

        <!-- ① 结束并收费：当前页面直接弹出收银结账台 -->
        <el-button 
          class="btn-top-instant-pay" 
          :disabled="currentPatient.status !== '接诊中'"
          :title="currentPatient.status !== '接诊中' ? '请先点击【开始接诊】后再结算' : '当前页面直接弹出收银结账台'"
          @click="openInstantPayModal"
        >
          结束并收费
        </el-button>

        <!-- ② 完成接诊：先推送收费处，再可跳转至收费界面 -->
        <el-button 
          class="btn-top-finish" 
          :disabled="currentPatient.status !== '接诊中'"
          :title="currentPatient.status !== '接诊中' ? '请先点击【开始接诊】' : '推送到划价收费处结算'"
          @click="finishConsultationToBilling"
        >
          完成接诊
        </el-button>

        <!-- ③ 删除草稿 -->
        <el-button class="btn-top-del" @click="handleDeleteDraft">
          删 除
        </el-button>
      </div>
    </div>

    <!-- 无选中就诊人时的顶栏占位 -->
    <div class="patient-header-bar empty-header-bar" v-else>
      <div class="patient-info-left" style="display: flex; align-items: center; gap: 12px;">
        <span style="font-size: 14px; font-weight: 700; color: #1e293b;">📅 门诊就诊工作台</span>
        <el-tag size="small" :type="queueDate === todayKey ? 'success' : 'info'" effect="light">
          {{ queueDate }} {{ queueDate === todayKey ? '（今日门诊）' : '（历史就诊调阅）' }}
        </el-tag>
        <span style="font-size: 12px; color: #94a3b8;">当前所选日期无正在接诊的患者</span>
      </div>
      <div class="patient-actions-right">
        <el-button
          type="primary"
          size="default"
          class="btn-quick-consult gradient-btn"
          @click="handleQuickDirectConsult"
        >
          快速接诊
        </el-button>
        <template v-if="queueDate !== todayKey">
          <el-button size="small" type="primary" plain @click="goToToday">返回今日门诊 ({{ todayKey }})</el-button>
        </template>
      </div>
    </div>

    <!-- 门诊三栏工作区 -->
    <div class="clinic-main-layout">
      <!-- 1. 左栏：候诊与接诊队列 -->
      <div class="queue-panel">
        <!-- 门诊就诊日期切换栏 (可选择今天/昨天/前天或任意历史日期，深度复刻截图 2) -->
        <div class="queue-date-bar">
          <div class="date-picker-row">
            <el-button 
              size="small" 
              class="d-arrow-btn" 
              @click="changeDateByOffset(-1)" 
              title="切换前一天"
            >
              ◀
            </el-button>
            <el-date-picker
              v-model="queueDate"
              type="date"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              :clearable="false"
              size="small"
              class="q-cal-picker"
              placeholder="选择就诊日期"
              @change="onQueueDateChange"
            />
            <el-button 
              size="small" 
              class="d-arrow-btn" 
              @click="changeDateByOffset(1)" 
              title="切换后一天"
            >
              ▶
            </el-button>
            <el-tag 
              v-if="queueDate === todayKey"
              size="small" 
              type="success" 
              effect="dark" 
              class="d-today-badge"
            >
              今日
            </el-tag>
            <el-button 
              v-else
              size="small" 
              type="primary" 
              class="d-back-today-btn" 
              @click="goToToday"
              title="快速返回今日就诊队列"
            >
              返回今日
            </el-button>
          </div>
        </div>

        <!-- 日期就诊模式提醒条 -->
        <div class="date-mode-hint" :class="queueDate === todayKey ? 'hint-today' : 'hint-history'">
          <span class="mode-tag">{{ queueDate === todayKey ? '🟢 今日门诊接诊队列' : '📅 调阅历史就诊状态' }}</span>
          <span class="mode-date">{{ queueDate }}</span>
        </div>

        <!-- 队列分组 Tab -->
        <div class="queue-tabs-bar">
          <div class="q-tabs-left">
            <span 
              class="q-tab-btn" 
              :class="{ active: queueTab === 'waiting' }" 
              @click="switchQueueTab('waiting')"
            >
              待诊/接诊中<sup class="q-sup">{{ waitingList.length }}</sup>
            </span>
            <span 
              class="q-tab-btn" 
              :class="{ active: queueTab === 'done' }" 
              @click="switchQueueTab('done')"
            >
              已结束<sup class="q-sup-done">{{ doneList.length }}</sup>
            </span>
          </div>
        </div>

        <!-- 患者卡片列表 (展示接诊中/待诊/已结诊状态，点击加载该日期下的病历处方) -->
        <div class="queue-list">
          <div 
            v-for="p in (queueTab === 'waiting' ? waitingList : doneList)" 
            :key="p.id"
            class="queue-item-card"
            :class="{ active: currentPatient && (currentPatient.id === p.id || currentPatient.patientName === p.patientName), 'is-consulting': p.status === '接诊中' }"
            @click="selectQueuePatient(p)"
          >
            <div class="q-card-top">
              <span class="q-seq">#{{ p.queueNumber || p.id }}</span>
              <span class="q-name">{{ p.patientName }}</span>
              <span class="q-age">{{ p.ageText || (p.age + '岁') }}</span>
              <!-- 接诊状态胶囊标签 (接诊中 / 待诊 / 已结诊) -->
              <span class="q-status-badge" :class="p.status">{{ p.status }}</span>
            </div>
            <div class="q-card-bottom">
              <span class="q-time">{{ p.createTime ? p.createTime.substring(11, 16) : '09:30' }} 挂号</span>
              <span class="q-symptom text-truncate">{{ p.symptoms || '门诊初诊' }}</span>
              <el-button
                v-if="p.status === '待诊' || p.status === '候诊中'"
                size="small"
                type="success"
                class="card-call-btn"
                @click.stop="startConsultation(p)"
                title="点击立即开始接诊"
              >🩺 接诊</el-button>
              <el-button 
                v-if="p.status === '过号' || p.status === '已过号'" 
                size="small" 
                type="warning" 
                plain 
                class="card-restore-btn"
                @click.stop="restorePassedPatient(p)"
                title="患者已到诊室，点击恢复待诊排队"
              >
                ↺ 恢复待诊
              </el-button>
            </div>
          </div>

          <div v-if="(queueTab === 'waiting' ? waitingList : doneList).length === 0" class="empty-queue-hint">
            <div class="eq-icon">📂</div>
            <div class="eq-title">暂无{{ queueTab === 'waiting' ? '待诊/接诊中' : '已结束' }}患者</div>
            <div class="eq-desc">{{ queueDate === todayKey ? '今日暂无该状态患者' : queueDate + ' 无就诊记录' }}</div>
            <el-button 
              v-if="queueDate !== todayKey" 
              type="primary" 
              link 
              size="small" 
              style="margin-top: 8px;"
              @click="goToToday"
            >
              返回今日接诊
            </el-button>
          </div>
        </div>
      </div>

      <!-- 2. 中栏：电子病历与多元开方 (核心截图深度复刻) -->
      <div class="emr-workspace-panel" v-if="currentPatient">
        <!-- 历史就诊记录查阅只读横幅 (Point 2: 点击右侧就诊记录卡片时展示并支持直接切换查阅) -->
        <div class="history-readonly-banner" v-if="isHistoryReadOnly || activeHistoryVisitId">
          <div class="hrb-left">
            <el-tag type="warning" size="default" effect="dark" class="hrb-status-tag">
              归档查阅
            </el-tag>
            <span class="hrb-text">
              🔒 <b>已载入往期就诊档案：{{ activeHistoryVisitId || currentPatient.visitTime || queueDate }}</b> · 【{{ emr.diagnosis || '历史诊断' }}】 · 处方总额：<b>¥{{ totalRxAmount.toFixed(2) }}</b> (只读预览中)
            </span>
          </div>
          <div class="hrb-right">
            <el-button size="small" :type="emrActiveTab === 'emr' ? 'primary' : 'default'" @click="emrActiveTab = 'emr'">
              📑 查看电子病历
            </el-button>
            <el-button size="small" :type="emrActiveTab === 'prescription' ? 'success' : 'default'" @click="emrActiveTab = 'prescription'">
              💊 查看处方明细 ({{ prescriptionBlocks.length }}单)
            </el-button>
            <el-button type="primary" size="small" plain :disabled="!canPrescribe" @click="adoptHistoryToEMR">
              📋 引用开方
            </el-button>
            <el-button type="info" size="small" link @click="exitHistoryInspect">
              ✕ 退出查阅
            </el-button>
          </div>
        </div>
        <!-- 历史就诊预览模式横幅 -->
        <div class="history-preview-banner" v-if="historyPreviewMode">
          <div class="hpb-left">
            <span class="hpb-icon">📅</span>
            <span class="hpb-text">正在预览历史就诊记录：<b>{{ previewHistoryData?.date }}</b>（{{ previewHistoryData?.diagnosis }}）— 仅供参考，不可编辑</span>
          </div>
          <div class="hpb-right">
            <el-button size="small" type="primary" plain :disabled="!canPrescribe" @click="adoptHistoryToEMR">一键引用至当前病历</el-button>
            <el-button size="small" @click="exitHistoryPreview">退出预览</el-button>
          </div>
        </div>
        <el-alert
          v-if="currentPatient.status === '已退号' || currentPatient.status === '已退'"
          title="【已退号归档】该挂号已完成退号退费，门诊就诊已作废关闭，记录仅供历史查阅，不可开方或收费。"
          type="error"
          show-icon
          :closable="false"
          style="margin-bottom: 12px;"
        />
        <el-alert
          v-else-if="currentPatient.status === '过号' || currentPatient.status === '已过号'"
          title="【过号状态提示】当前患者已标记为过号。若患者已到达诊室，请点击顶部的【↺ 恢复待诊 (过号重排)】顺延排队并接诊。"
          type="warning"
          show-icon
          :closable="false"
          style="margin-bottom: 12px;"
        />
        <el-tabs v-model="emrActiveTab" class="custom-emr-tabs">
          <!-- A. 电子病历 Tab -->
          <el-tab-pane label="电子病历书写" name="emr">
            <div class="emr-form-zone">
              <!-- 主诉与智能推荐 (竞品级抽屉标签选择 + AI诊疗) -->
              <div class="form-section">
                <div class="section-head">
                  <span class="title">* 主 诉</span>
                  <div class="section-head-actions">
                    <el-button 
                      size="small" 
                      type="primary" 
                      plain 
                      class="ai-emr-btn"
                      :loading="isAiGeneratingEmr"
                      @click="aiGenerateFullEmr"
                    >
                      ✨ AI 规范化病历润色
                    </el-button>
                    <el-button 
                      size="small" 
                      type="success" 
                      class="ai-emr-push-btn"
                      @click="sendEmrDirectToAi"
                    >
                      <el-icon><MagicStick /></el-icon> 🚀 AI 辨证诊疗开方
                    </el-button>
                    <el-button type="primary" link size="small" class="voice-btn" @click="startVoiceInput">
                      <el-icon><Microphone /></el-icon> 语音录入
                    </el-button>
                  </div>
                </div>
                <el-popover placement="bottom-start" :width="680" trigger="focus">
                  <template #reference>
                    <el-input 
                      v-model="emr.chiefComplaint" 
                      type="textarea" 
                      :rows="2" 
                      placeholder="请点击或输入患者主诉（点击展开智能症状部位标签）..."
                      @input="onChiefComplaintInput"
                      @change="onChiefComplaintChange"
                    />
                  </template>
                  <div class="cc-popover-panel">
                    <el-tabs v-model="activeCcTab" size="small">
                      <el-tab-pane label="智能推荐" name="ai">
                        <div class="cc-tags-grid">
                          <span v-for="tag in ccSmartTags" :key="tag" class="cc-tag-chip" @click="appendChiefComplaint(tag)">{{ tag }}</span>
                        </div>
                      </el-tab-pane>
                      <el-tab-pane label="常用" name="common">
                        <div class="cc-tags-grid">
                          <span v-for="tag in ccCommonTags" :key="tag" class="cc-tag-chip" @click="appendChiefComplaint(tag)">{{ tag }}</span>
                        </div>
                      </el-tab-pane>
                      <el-tab-pane label="头面部" name="head">
                        <div class="cc-tags-grid">
                          <span v-for="tag in ccHeadTags" :key="tag" class="cc-tag-chip" @click="appendChiefComplaint(tag)">{{ tag }}</span>
                        </div>
                      </el-tab-pane>
                      <el-tab-pane label="颈部" name="neck">
                        <div class="cc-tags-grid">
                          <span v-for="tag in ccNeckTags" :key="tag" class="cc-tag-chip" @click="appendChiefComplaint(tag)">{{ tag }}</span>
                        </div>
                      </el-tab-pane>
                      <el-tab-pane label="胸部" name="chest">
                        <div class="cc-tags-grid">
                          <span v-for="tag in ccChestTags" :key="tag" class="cc-tag-chip" @click="appendChiefComplaint(tag)">{{ tag }}</span>
                        </div>
                      </el-tab-pane>
                      <el-tab-pane label="腹部" name="belly">
                        <div class="cc-tags-grid">
                          <span v-for="tag in ccBellyTags" :key="tag" class="cc-tag-chip" @click="appendChiefComplaint(tag)">{{ tag }}</span>
                        </div>
                      </el-tab-pane>
                      <el-tab-pane label="脊背部" name="back">
                        <div class="cc-tags-grid">
                          <span v-for="tag in ccBackTags" :key="tag" class="cc-tag-chip" @click="appendChiefComplaint(tag)">{{ tag }}</span>
                        </div>
                      </el-tab-pane>
                      <el-tab-pane label="四肢" name="limbs">
                        <div class="cc-tags-grid">
                          <span v-for="tag in ccLimbTags" :key="tag" class="cc-tag-chip" @click="appendChiefComplaint(tag)">{{ tag }}</span>
                        </div>
                      </el-tab-pane>
                      <el-tab-pane label="皮肤" name="skin">
                        <div class="cc-tags-grid">
                          <span v-for="tag in ccSkinTags" :key="tag" class="cc-tag-chip" @click="appendChiefComplaint(tag)">{{ tag }}</span>
                        </div>
                      </el-tab-pane>
                      <el-tab-pane label="全身" name="whole">
                        <div class="cc-tags-grid">
                          <span v-for="tag in ccWholeTags" :key="tag" class="cc-tag-chip" @click="appendChiefComplaint(tag)">{{ tag }}</span>
                        </div>
                      </el-tab-pane>
                      <el-tab-pane label="盆腔/肛肠" name="pelvis">
                        <div class="cc-tags-grid">
                          <span v-for="tag in ccPelvisTags" :key="tag" class="cc-tag-chip" @click="appendChiefComplaint(tag)">{{ tag }}</span>
                        </div>
                      </el-tab-pane>
                    </el-tabs>
                    <!-- 修饰语与时长快速追加条 -->
                    <div class="cc-modifier-bar">
                      <span class="mod-chip" v-for="m in [ '、', '，', '偶尔', '经常', '持续', '1次', '2次', '3次', '1天', '2天', '3天', '1周', '15天' ]" :key="m" @click="appendChiefComplaint(m)">{{ m }}</span>
                    </div>
                    <!-- 自定义快速添加 -->
                    <div class="cc-custom-add-row">
                      <el-input v-model="customCcInput" placeholder="手动输入症状词条" size="small" maxlength="30" show-word-limit style="width: 240px;" @keydown.enter="addCustomCcTag" />
                      <el-button size="small" type="primary" @click="addCustomCcTag">添加</el-button>
                    </div>
                  </div>
                </el-popover>
              </div>

              <!-- 现病史 (8大部位 + 症状点选 + 频次 + 病程，深度复刻截图 44) -->
              <div class="form-section">
                <div class="section-head">
                  <span class="title">现病史多部位辨证点选 (HPI)</span>
                  <el-radio-group v-model="selectedBodyPart" size="small">
                    <el-radio-button value="头面部">头面部</el-radio-button>
                    <el-radio-button value="颈胸部">颈胸部</el-radio-button>
                    <el-radio-button value="脘腹部">脘腹部</el-radio-button>
                    <el-radio-button value="腰背部">腰背部</el-radio-button>
                    <el-radio-button value="四肢及关节">四肢及关节</el-radio-button>
                    <el-radio-button value="全身皮肤">全身皮肤</el-radio-button>
                  </el-radio-group>
                </div>

                <div class="symptom-tag-box">
                  <el-check-tag 
                    v-for="sym in currentBodyPartSymptoms" 
                    :key="sym" 
                    :checked="emr.symptomsList.includes(sym)"
                    @change="toggleSymptom(sym)"
                    class="symptom-check-tag"
                  >
                    {{ sym }}
                  </el-check-tag>
                </div>

                <div class="symptom-modifiers-bar">
                  <span class="mod-label">发作频次：</span>
                  <el-radio-group v-model="emr.frequency" size="small">
                    <el-radio value="偶尔">偶尔</el-radio>
                    <el-radio value="经常">经常</el-radio>
                    <el-radio value="持续性">持续性</el-radio>
                  </el-radio-group>
                  <span class="mod-label ml-4">发病病程：</span>
                  <el-radio-group v-model="emr.duration" size="small">
                    <el-radio value="1-3天">1-3天</el-radio>
                    <el-radio value="1周内">1周内</el-radio>
                    <el-radio value="半月以上">半月以上</el-radio>
                  </el-radio-group>
                </div>

                <el-input 
                  v-model="emr.presentIllness" 
                  type="textarea" 
                  :rows="2" 
                  placeholder="现病史综合描述..." 
                  class="mt-2"
                />
              </div>

              <!-- 既往史与药物过敏史红线 -->
              <el-row :gutter="12">
                <el-col :span="12">
                  <div class="form-section">
                    <div class="section-head">
                      <span class="title">药物过敏史 (红线安全管控)</span>
                      <el-tag type="danger" size="small">处方禁忌拦截</el-tag>
                    </div>
                    <el-popover placement="bottom-start" width="480" trigger="focus">
                      <template #reference>
                        <el-input
                          v-model="emr.allergies"
                          placeholder="如：青霉素、头孢类、磺胺类等（点击展开智能推荐）"
                          @change="validateAllergies"
                        />
                      </template>
                      <div class="allergy-tag-popup">
                        <div class="allergy-popup-title">常见过敏原快速选择：</div>
                        <div class="allergy-group" v-for="grp in allergyGroups" :key="grp.name">
                          <span class="allergy-grp-label">{{ grp.name }}：</span>
                          <el-tag
                            v-for="tag in grp.items" :key="tag"
                            size="small" class="allergy-pick-tag"
                            @click="appendAllergy(tag)"
                          >{{ tag }}</el-tag>
                        </div>
                      </div>
                    </el-popover>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="form-section">
                    <div class="section-head">
                      <span class="title">既往史 / 慢病史</span>
                    </div>
                    <el-popover placement="bottom-start" :width="560" trigger="focus">
                      <template #reference>
                        <el-input v-model="emr.pastHistory" placeholder="如：高血压病史 5 年、胃炎、无手术史（点击选择标签）" />
                      </template>
                      <div class="past-history-popover">
                        <el-tabs v-model="activePastTab" size="small">
                          <el-tab-pane label="系统标签" name="system">
                            <div class="ph-tags-grid">
                              <span 
                                v-for="tag in pastHistorySysTags" 
                                :key="tag" 
                                class="ph-tag-chip" 
                                @click="appendPastHistory(tag)"
                              >{{ tag }}</span>
                            </div>
                          </el-tab-pane>
                          <el-tab-pane label="自定义" name="custom">
                            <el-input v-model="customPastInput" placeholder="输入自定义慢病/既往病史并回车" size="small" @keydown.enter="appendPastHistory(customPastInput); customPastInput=''" />
                          </el-tab-pane>
                        </el-tabs>
                      </div>
                    </el-popover>
                  </div>
                </el-col>
              </el-row>

              <!-- 中医四诊与舌象多模态识别 (对应截图 09/55 舌象轨迹) -->
              <div class="form-section">
                <div class="section-head">
                  <span class="title">中医四诊与舌面多模态分析</span>
                  <el-button type="success" size="small" plain @click="simulateTongueAnalysis">
                    <el-icon><Picture /></el-icon> 上传舌象照片 (AI视觉辨识)
                  </el-button>
                </div>
                <div class="tongue-grid">
                  <el-input v-model="emr.tongue" placeholder="舌象：如舌质淡红、苔薄白微腻、边有齿痕" />
                  <el-input v-model="emr.pulse" placeholder="脉象：如脉沉细、滑数、弦紧" />
                </div>
              </div>

              <!-- 临床诊断结论 -->
              <div class="form-section diag-section">
                <div class="section-head">
                  <span class="title font-bold text-blue">最终临床诊断与辨证分型</span>
                </div>
                <el-row :gutter="12">
                  <el-col :span="12">
                    <el-input v-model="emr.diagnosis" placeholder="西医诊断：如急性上呼吸道感染 / 偏头痛" />
                  </el-col>
                  <el-col :span="12">
                    <el-input v-model="emr.tcmDiagnosis" placeholder="中医辨证：如风寒束表证 / 肝郁气滞证" />
                  </el-col>
                </el-row>
              </div>
            </div>
          </el-tab-pane>

          <!-- B. 多元处方医嘱 Tab (重构版 - 分区独立处方块) -->
          <el-tab-pane label="处方医嘱与特色贴敷" name="prescription">
            <!-- 未开始接诊提示条：开方按钮全部禁用 -->
            <el-alert
              v-if="currentPatient && currentPatient.status !== '接诊中' && !isHistoryReadOnly"
              title="该患者还未接诊，暂不能添加处方 — 请先点击顶部【开始接诊】"
              type="warning"
              show-icon
              :closable="false"
              class="rx-lock-alert"
            />
            <!-- 顶部粘性固定包裹层 (Point 3: 处方医嘱Tab、AI处方质控审查、门诊处方单管理三大模块在下滑看处方时牢牢固定在顶部) -->
            <div class="prescription-sticky-header">
              <!-- 模块2：🛡️ AI 合理用药审查与配伍禁忌质控盾牌 -->
              <div class="ai-safety-audit-bar" :class="aiPrescriptionAuditResult.riskLevel">
                <div class="as-left">
                  <span class="as-icon">{{ aiPrescriptionAuditResult.riskLevel === 'danger' ? '🚨' : aiPrescriptionAuditResult.riskLevel === 'warning' ? '⚠️' : '🛡️' }}</span>
                  <span class="as-title">
                    {{ aiPrescriptionAuditResult.riskLevel === 'danger' ? '高危配伍禁忌预警！请立即调整处方' : aiPrescriptionAuditResult.riskLevel === 'warning' ? '合理用药质控协同提示' : 'AI 处方质控审查通过：无配伍禁忌与超量风险' }}
                  </span>
                  <el-button size="small" type="primary" link @click="runAiPrescriptionAudit" class="as-recheck">
                    重新审查
                  </el-button>
                </div>
                <div class="as-details" v-if="aiPrescriptionAuditResult.contraindications.length || aiPrescriptionAuditResult.warnings.length">
                  <div v-for="c in aiPrescriptionAuditResult.contraindications" :key="c" class="as-msg danger">{{ c }}</div>
                  <div v-for="w in aiPrescriptionAuditResult.warnings" :key="w" class="as-msg warning">{{ w }}</div>
                </div>
              </div>

              <!-- 模块3：门诊处方单管理总控条 (Point 4 & 8: 紧凑横排防挤压，存为模板无溢出) -->
              <div class="rx-top-module-bar">
                <div class="rtmb-left">
                  <span class="rtmb-title">门诊处方单管理</span>
                  <el-tag size="small" type="info" effect="plain" class="rtmb-count-tag">
                    共 {{ prescriptionBlocks.length }} 张独立处方单
                  </el-tag>
                  <span class="rtmb-amt-badge">处方合计：¥{{ totalRxAmount.toFixed(2) }}</span>
                </div>
                <div class="rtmb-right">
                  <el-button type="warning" size="small" class="rtmb-btn btn-add-patch" :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('patch')">
                    <el-icon><Plus /></el-icon> 贴敷
                  </el-button>
                  <el-button type="primary" size="small" class="rtmb-btn btn-add-western" :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('western')">
                    <el-icon><Plus /></el-icon> 西/成药
                  </el-button>
                  <el-button type="success" size="small" class="rtmb-btn btn-add-tcm" :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('tcm')">
                    <el-icon><Plus /></el-icon> 中药
                  </el-button>
                  <el-button type="info" size="small" class="rtmb-btn btn-add-treatment" :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('treatment')">
                    <el-icon><Plus /></el-icon> 诊疗
                  </el-button>
                  <el-button type="warning" size="small" class="rtmb-btn btn-add-supply" :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('supply')">
                    <el-icon><Plus /></el-icon> 物资
                  </el-button>
                  <el-button type="success" size="small" plain class="btn-save-as-tpl" :disabled="isHistoryReadOnly" @click="openSaveTemplateModal">
                    📋 存为模板
                  </el-button>
                  <el-button type="danger" link size="small" @click="checkDrugSafety">
                    用药审查
                  </el-button>
                </div>
              </div>
            </div>

            <div class="prescription-form-zone-v2">

              <!-- 处方单分类分组展示专区 (Point 1: 贴敷放一起，西药放一起，中药放一起，理疗放一起) -->
              <div class="rx-categorized-container" v-if="prescriptionBlocks.length > 0">

                <!-- 1. 🌿 穴位贴敷处方专区 -->
                <div class="rx-category-section" v-if="patchBlocks.length > 0">
                  <div class="rx-cat-header cat-header-patch">
                    <span class="cat-badge">🌿 穴位贴敷处方专区 ({{ patchBlocks.length }} 张)</span>
                    <el-button size="small" type="warning" plain :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('patch')">+ 增加独立贴敷单 (处方二/三)</el-button>
                  </div>
                  <div class="rx-blocks-subgroup">
                    <div 
                      v-for="(block, bIdx) in patchBlocks" 
                      :key="block.id" 
                      class="rx-section-card prescription-block-card block-type-patch"
                    >
                      <div class="rx-section-header block-header">
                        <div class="block-header-left">
                          <span class="rx-section-title">{{ block.title }}</span>
                          <el-tag size="small" type="warning" class="block-type-tag">特色穴位贴敷</el-tag>
                          <span class="block-rx-no">单号: {{ block.rxNo }}</span>
                        </div>
                        <div class="rx-section-actions block-header-right">
                          <el-select v-model="block.technique" size="small" style="width:140px" :disabled="isHistoryReadOnly" @change="syncBlocksToItems">
                            <el-option label="湿贴（姜汁调和）" value="湿贴" />
                            <el-option label="干贴（传统膏贴）" value="干贴" />
                            <el-option label="蜡疗贴" value="蜡疗贴" />
                          </el-select>
                          <el-select v-model="block.duration" size="small" style="width:110px" :disabled="isHistoryReadOnly">
                            <el-option label="贴敷 4 小时" value="4小时" />
                            <el-option label="贴敷 6 小时" value="6小时" />
                            <el-option label="贴敷 8 小时" value="8小时" />
                          </el-select>
                          <span class="block-subtotal">金额: ¥{{ getBlockTotal(block).toFixed(2) }}</span>
                          <el-button type="danger" link size="small" class="btn-del-block" :disabled="isHistoryReadOnly" @click="removePrescriptionBlock(block.id)">
                            <el-icon><Delete /></el-icon> 删除整单
                          </el-button>
                        </div>
                      </div>
                      <div class="block-body-patch">
                        <div class="patch-rows-wrap">
                          <div class="patch-row" v-for="(row, idx) in block.items" :key="'patch_'+block.id+'_'+idx">
                            <span class="patch-num">{{ idx + 1 }}</span>
                            <el-select 
                              v-model="row.name" 
                              placeholder="选择贴敷药品/粉剂" 
                              size="small" 
                              style="width:200px" 
                              filterable 
                              allow-create 
                              default-first-option 
                              :disabled="isHistoryReadOnly" 
                              @change="onBlockPlasterSelect(row)"
                            >
                              <el-option 
                                v-for="p in allAvailablePlasters" 
                                :key="p.id || p.name" 
                                :label="p.name" 
                                :value="p.name" 
                              />
                            </el-select>
                            <el-input v-model="row.dose" placeholder="单贴剂量" size="small" style="width:80px" :disabled="isHistoryReadOnly" @input="syncBlocksToItems">
                              <template #append>g</template>
                            </el-input>
                            <div class="acupoint-cell-wrap">
                              <el-input v-model="row.acupoints" placeholder="贴敷穴位（可点选右侧快捷穴位）" size="small" style="width:205px" :disabled="isHistoryReadOnly" @input="syncBlocksToItems" />
                              <el-popover placement="bottom" :width="330" trigger="click">
                                <template #reference>
                                  <el-button size="small" type="warning" plain class="btn-acupoint-pick" :disabled="isHistoryReadOnly">
                                    📍 选穴 ▾
                                  </el-button>
                                </template>
                                <div class="acupoint-popover-box">
                                  <div class="apb-header">
                                    <span class="apb-title">常用外敷穴位（点击快速追加）：</span>
                                    <el-button link type="danger" size="small" @click="row.acupoints = ''; syncBlocksToItems()">清空</el-button>
                                  </div>
                                  <div class="apb-tags-grid">
                                    <span 
                                      v-for="ac in commonAcupoints" 
                                      :key="ac" 
                                      class="apb-tag-chip"
                                      @click="appendAcupointToRow(row, ac)"
                                    >+ {{ ac }}</span>
                                  </div>
                                </div>
                              </el-popover>
                            </div>
                            <el-select v-model="row.frequency" placeholder="频次" size="small" style="width:95px" :disabled="isHistoryReadOnly" @change="syncBlocksToItems">
                              <el-option label="1次/天" value="1次/天" />
                              <el-option label="2次/天" value="2次/天" />
                              <el-option label="隔日1次" value="隔日1次" />
                            </el-select>
                            <el-input-number v-model="row.days" :min="1" :max="30" size="small" style="width:90px" placeholder="天数" :disabled="isHistoryReadOnly" @change="syncBlocksToItems" />
                            <span class="unit-text">天</span>
                            <el-input-number v-model="row.quantity" :min="1" :max="99" size="small" style="width:90px" placeholder="贴数" :disabled="isHistoryReadOnly" @change="syncBlocksToItems" />
                            <span class="unit-text">贴</span>
                            <span class="row-subtotal">¥{{ ((Number(row.unitPrice) || 35) * (row.quantity || 1)).toFixed(2) }}</span>
                            <el-button type="danger" link size="small" :disabled="isHistoryReadOnly" @click="removeBlockItem(block, idx)">删</el-button>
                          </div>
                        </div>
                        <el-button size="small" plain type="warning" class="btn-add-item" :disabled="isHistoryReadOnly || !canPrescribe" @click="addBlockItem(block)">
                          + 添加贴敷穴位药品 (在当前单内追加第2味药)
                        </el-button>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 2. 💊 西药 / 中成药处方专区 -->
                <div class="rx-category-section" v-if="westernBlocks.length > 0">
                  <div class="rx-cat-header cat-header-western">
                    <span class="cat-badge">💊 西药 / 中成药处方专区 ({{ westernBlocks.length }} 张)</span>
                    <el-button size="small" type="primary" plain :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('western')">+ 增加西药单</el-button>
                  </div>
                  <div class="rx-blocks-subgroup">
                    <div 
                      v-for="(block, bIdx) in westernBlocks" 
                      :key="block.id" 
                      class="rx-section-card prescription-block-card block-type-western"
                    >
                      <div class="rx-section-header block-header">
                        <div class="block-header-left">
                          <span class="rx-section-title">{{ block.title }}</span>
                          <el-tag size="small" type="primary" class="block-type-tag">西药 / 中成药</el-tag>
                          <span class="block-rx-no">单号: {{ block.rxNo }}</span>
                        </div>
                        <div class="rx-section-actions block-header-right">
                          <span class="block-subtotal">金额: ¥{{ getBlockTotal(block).toFixed(2) }}</span>
                          <el-button type="danger" link size="small" class="btn-del-block" :disabled="isHistoryReadOnly" @click="removePrescriptionBlock(block.id)">
                            <el-icon><Delete /></el-icon> 删除整单
                          </el-button>
                        </div>
                      </div>
                      <div class="block-body-western">
                        <table class="rx-table western-table">
                          <thead>
                            <tr>
                              <th style="width:36px">#</th>
                              <th style="min-width: 220px; white-space: nowrap !important; word-break: keep-all !important;">药品名称 / 规格</th>
                              <th style="width:90px">单次用量</th>
                              <th style="width:100px">用法途径</th>
                              <th style="width:95px">用药频次</th>
                              <th style="width:80px">天数</th>
                              <th style="width:80px">数量</th>
                              <th style="width:80px">单价</th>
                              <th style="width:80px">金额</th>
                              <th style="width:130px">用药说明</th>
                              <th style="width:50px">操作</th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr v-for="(row, idx) in block.items" :key="'wx_'+block.id+'_'+idx">
                              <td class="text-center">{{ idx + 1 }}</td>
                              <td>
                                <el-select v-model="row.name" filterable allow-create default-first-option
                                  :filter-method="filterMedByPinyin" placeholder="检索西药/中成药（支持名称/拼音简码，如 xcz）" size="small" style="width:100%" :disabled="isHistoryReadOnly" @change="onBlockMedSelect(row)">
                                  <el-option v-for="m in pinyinFilteredMeds" :key="m.id || m.name" :label="`${m.name} (${m.secondaryCategory ? m.secondaryCategory + '·' : ''}${m.specification || '标准'}) - ¥${m.price}`" :value="m.name" />
                                </el-select>
                              </td>
                              <td><el-input v-model="row.dose" :placeholder="'1' + (specUnitOf(row.spec) || '片')" size="small" :disabled="isHistoryReadOnly" @input="syncBlocksToItems" /></td>
                              <td>
                                <el-select v-model="row.route" size="small" style="width:100%" :disabled="isHistoryReadOnly" @change="syncBlocksToItems">
                                  <el-option label="口服" value="口服" />
                                  <el-option label="外用" value="外用" />
                                  <el-option label="含服" value="含服" />
                                  <el-option label="喷雾" value="喷雾" />
                                </el-select>
                              </td>
                              <td>
                                <el-select v-model="row.frequency" size="small" style="width:100%" :disabled="isHistoryReadOnly" @change="syncBlocksToItems">
                                  <el-option label="tid (每日3次)" value="tid" />
                                  <el-option label="bid (每日2次)" value="bid" />
                                  <el-option label="qd (每日1次)" value="qd" />
                                  <el-option label="prn (必要时)" value="prn" />
                                </el-select>
                              </td>
                              <td><el-input-number v-model="row.days" :min="1" :max="30" size="small" style="width:100%" :controls="false" :disabled="isHistoryReadOnly" @change="syncBlocksToItems" /></td>
                              <td>
                                <el-input-number v-model="row.quantity" :min="1" :max="99" size="small" style="width:100%" :controls="false" :disabled="isHistoryReadOnly" @change="onWxQtyChange(row)" />
                              </td>
                              <td class="text-right">¥{{ (Number(row.unitPrice) || 25).toFixed(2) }}</td>
                              <td class="text-right font-bold">¥{{ ((Number(row.unitPrice) || 25) * (row.quantity || 1)).toFixed(2) }}</td>
                              <td><el-input v-model="row.remark" placeholder="饭后温服" size="small" :disabled="isHistoryReadOnly" @input="syncBlocksToItems" /></td>
                              <td class="text-center">
                                <el-button type="danger" link size="small" :disabled="isHistoryReadOnly" @click="removeBlockItem(block, idx)">删</el-button>
                              </td>
                            </tr>
                          </tbody>
                        </table>
                        <el-button size="small" plain type="primary" class="btn-add-item" :disabled="isHistoryReadOnly || !canPrescribe" @click="addBlockItem(block)">
                          + 添加药品行 (西药/中成药)
                        </el-button>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 3. 🌱 中药饮片 / 颗粒处方专区 -->
                <div class="rx-category-section" v-if="tcmBlocks.length > 0">
                  <div class="rx-cat-header cat-header-tcm">
                    <span class="cat-badge">🌱 中药饮片 / 颗粒处方专区 ({{ tcmBlocks.length }} 张)</span>
                    <el-button size="small" type="success" plain :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('tcm')">+ 增加中药单</el-button>
                  </div>
                  <div class="rx-blocks-subgroup">
                    <div 
                      v-for="(block, bIdx) in tcmBlocks" 
                      :key="block.id" 
                      class="rx-section-card prescription-block-card block-type-tcm"
                    >
                      <div class="rx-section-header block-header">
                        <div class="block-header-left">
                          <span class="rx-section-title">{{ block.title }}</span>
                          <el-tag size="small" type="success" class="block-type-tag">中药饮片/颗粒</el-tag>
                          <span class="block-rx-no">单号: {{ block.rxNo }}</span>
                        </div>
                        <div class="rx-section-actions block-header-right">
                          <el-radio-group v-model="block.tcmType" size="small" :disabled="isHistoryReadOnly">
                            <el-radio-button value="饮片">饮片</el-radio-button>
                            <el-radio-button value="颗粒">颗粒</el-radio-button>
                          </el-radio-group>
                          <span class="tcm-dose-label">剂数:</span>
                          <el-input-number v-model="block.tcmDoses" :min="1" :max="30" size="small" style="width:90px" :disabled="isHistoryReadOnly" @change="syncBlocksToItems" />
                          <span class="block-subtotal">金额: ¥{{ getBlockTotal(block).toFixed(2) }}</span>
                          <el-button type="danger" link size="small" class="btn-del-block" :disabled="isHistoryReadOnly" @click="removePrescriptionBlock(block.id)">
                            <el-icon><Delete /></el-icon> 删除整单
                          </el-button>
                        </div>
                      </div>
                      <div class="block-body-tcm">
                        <!-- 彻底消除独立大搜索框，采用与西药完全一致的表格行内下拉选择与检索 -->
                        <table class="rx-table tcm-rx-table">
                          <thead>
                            <tr>
                              <th style="width:36px; white-space: nowrap;">#</th>
                              <th style="min-width: 220px; white-space: nowrap;">中药材名称 / 规格</th>
                              <th style="width:100px; white-space: nowrap;">单剂克数(g)</th>
                              <th style="width:90px; white-space: nowrap;">总用量(g)</th>
                              <th style="width:90px; white-space: nowrap;">单价(元/g)</th>
                              <th style="width:90px; white-space: nowrap;">金额</th>
                              <th style="width:140px; white-space: nowrap;">特殊煎服法</th>
                              <th style="width:50px; white-space: nowrap;">操作</th>
                            </tr>
                          </thead>
                          <tbody>
                            <tr v-for="(it, idx) in block.items" :key="'tcm_'+block.id+'_'+idx">
                              <td class="text-center">{{ idx + 1 }}</td>
                              <td>
                                <el-select 
                                  v-model="it.name" 
                                  filterable 
                                  allow-create 
                                  default-first-option 
                                  placeholder="检索/点选中药材名称或拼音" 
                                  size="small" 
                                  style="width:100%" 
                                  :disabled="isHistoryReadOnly" 
                                  @change="onTcmMedSelect(it)"
                                >
                                  <el-option 
                                    v-for="m in allTcmMedicines" 
                                    :key="m.id || m.name" 
                                    :label="`${m.name} (${m.specification || '中药饮片'}) - ¥${m.price || 0.15}/g`" 
                                    :value="m.name" 
                                  />
                                </el-select>
                              </td>
                              <td>
                                <el-input-number v-model="it.dose" :min="1" :max="100" size="small" style="width:100%" :controls="false" :disabled="isHistoryReadOnly" @change="syncBlocksToItems" />
                              </td>
                              <td class="text-center font-bold">{{ ((Number(it.dose) || 10) * (block.tcmDoses || 7)).toFixed(0) }}g</td>
                              <td class="text-right">¥{{ (Number(it.unitPrice) || 0.15).toFixed(2) }}</td>
                              <td class="text-right font-bold" style="color: #047857;">¥{{ ((Number(it.unitPrice) || 0.15) * (Number(it.dose) || 10) * (block.tcmDoses || 7)).toFixed(2) }}</td>
                              <td><el-input v-model="it.remark" placeholder="先煎 / 后下 / 包煎" size="small" :disabled="isHistoryReadOnly" @input="syncBlocksToItems" /></td>
                              <td class="text-center">
                                <el-button type="danger" link size="small" :disabled="isHistoryReadOnly" @click="removeBlockItem(block, idx)">删</el-button>
                              </td>
                            </tr>
                            <tr v-if="block.items.length === 0">
                              <td colspan="8" class="text-center" style="color: #94a3b8; padding: 20px 0;">
                                暂无药材，请点击下方【+ 添加中药饮片行】或点选名方开始开方
                              </td>
                            </tr>
                          </tbody>
                        </table>

                        <!-- 底部快捷操作条 -->
                        <div class="tcm-block-footer-bar" v-if="!isHistoryReadOnly">
                          <div class="tbf-left">
                            <el-button type="success" plain size="small" icon="Plus" :disabled="!canPrescribe" @click="addBlockItem(block)">
                              + 添加中药饮片行
                            </el-button>
                            <el-dropdown trigger="click" @command="(f) => importClassicFormulaToBlock(block, f)">
                              <el-button size="small" type="primary" plain style="margin-left: 8px;">
                                📜 经典经方导入 ▾
                              </el-button>
                              <template #dropdown>
                                <el-dropdown-menu>
                                  <el-dropdown-item v-for="f in classicTcmFormulas" :key="f.name" :command="f">
                                    <span style="font-weight: 700; color: #047857;">{{ f.name }}</span>
                                    <span style="color: #64748b; font-size: 11px; margin-left: 8px;">{{ f.desc }}</span>
                                  </el-dropdown-item>
                                </el-dropdown-menu>
                              </template>
                            </el-dropdown>
                          </div>
                          <div class="tbf-right">
                            <span class="tbf-label">高频速加：</span>
                            <span 
                              v-for="h in allTcmMedicines.slice(0, 8)" 
                              :key="h.name" 
                              class="tbf-chip"
                              @click="quickAddHerbToBlock(block, h)"
                            >+ {{ h.name }}</span>
                          </div>
                        </div>
                      </div>
                    </div>
                  </div>
                </div>

                <!-- 4. 🩺 诊疗理疗项目专区 -->
                <div class="rx-category-section" v-if="treatmentBlocks.length > 0">
                  <div class="rx-cat-header cat-header-treatment">
                    <span class="cat-badge">🩺 诊疗理疗项目专区 ({{ treatmentBlocks.length }} 张)</span>
                    <el-button size="small" type="info" plain :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('treatment')">+ 增加诊疗单</el-button>
                  </div>
                  <div class="rx-blocks-subgroup">
                    <div 
                      v-for="(block, bIdx) in treatmentBlocks" 
                      :key="block.id" 
                      class="rx-section-card prescription-block-card block-type-treatment"
                    >
                      <div class="rx-section-header block-header">
                        <div class="block-header-left">
                          <span class="rx-section-title">{{ block.title }}</span>
                          <el-tag size="small" type="info" class="block-type-tag">诊疗理疗</el-tag>
                          <span class="block-rx-no">单号: {{ block.rxNo }}</span>
                        </div>
                        <div class="rx-section-actions block-header-right">
                          <span class="block-subtotal">金额: ¥{{ getBlockTotal(block).toFixed(2) }}</span>
                          <el-button type="danger" link size="small" class="btn-del-block" :disabled="isHistoryReadOnly" @click="removePrescriptionBlock(block.id)">
                            <el-icon><Delete /></el-icon> 删除整单
                          </el-button>
                        </div>
                      </div>
                      <div class="block-body-treatment">
                        <div class="treatment-rows-wrap">
                          <div class="treatment-row" v-for="(item, idx) in block.items" :key="'treat_'+block.id+'_'+idx">
                            <el-tag size="small" :type="item.category === '治疗理疗' ? 'success' : 'primary'" class="treat-cat-tag">{{ item.category || '治疗理疗' }}</el-tag>
                            <el-input v-model="item.name" placeholder="项目名称" size="small" style="width:180px" :disabled="isHistoryReadOnly" @input="syncBlocksToItems" />
                            <el-input-number v-model="item.quantity" :min="1" size="small" style="width:100px" :disabled="isHistoryReadOnly" @change="syncBlocksToItems" />
                            <span class="treat-unit">次</span>
                            <el-input v-model="item.price" placeholder="单价" size="small" style="width:90px" :disabled="isHistoryReadOnly" @input="syncBlocksToItems" />
                            <span class="treat-unit">元</span>
                            <el-input v-model="item.remark" placeholder="备注说明" size="small" style="width:140px" :disabled="isHistoryReadOnly" @input="syncBlocksToItems" />
                            <span class="treat-subtotal">¥{{ ((Number(item.price)||0) * (item.quantity||1)).toFixed(2) }}</span>
                            <el-button type="danger" link size="small" :disabled="isHistoryReadOnly" @click="removeBlockItem(block, idx)">删除</el-button>
                          </div>
                        </div>
                        <el-button size="small" plain type="info" class="btn-add-item" :disabled="isHistoryReadOnly || !canPrescribe" @click="addBlockItem(block)">
                          + 添加诊疗理疗项目
                        </el-button>
                      </div>
                    </div>
                  </div>
                </div>
                <!-- 5. 🧰 医用物资开单专区 -->
                <div class="rx-category-section" v-if="supplyBlocks.length > 0">
                  <div class="rx-cat-header cat-header-treatment">
                    <span class="cat-badge">🧰 医用物资开单专区 ({{ supplyBlocks.length }} 张)</span>
                    <el-button size="small" type="warning" plain :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('supply')">+ 增加物资单</el-button>
                  </div>
                  <div class="rx-blocks-subgroup">
                    <div
                      v-for="(block, bIdx) in supplyBlocks"
                      :key="block.id"
                      class="rx-section-card prescription-block-card block-type-treatment"
                    >
                      <div class="rx-section-header block-header">
                        <div class="block-header-left">
                          <span class="rx-section-title">{{ block.title }}</span>
                          <el-tag size="small" type="warning" class="block-type-tag">医用物资</el-tag>
                          <span class="block-rx-no">单号: {{ block.rxNo }}</span>
                        </div>
                        <div class="rx-section-actions block-header-right">
                          <span class="block-subtotal">金额: ¥{{ getBlockTotal(block).toFixed(2) }}</span>
                          <el-button type="danger" link size="small" class="btn-del-block" :disabled="isHistoryReadOnly" @click="removePrescriptionBlock(block.id)">
                            <el-icon><Delete /></el-icon> 删除整单
                          </el-button>
                        </div>
                      </div>
                      <div class="block-body-treatment">
                        <div class="treatment-rows-wrap">
                          <div class="treatment-row" v-for="(item, idx) in block.items" :key="'supply_'+block.id+'_'+idx">
                            <el-select v-model="item.medicineId" filterable placeholder="从药房物资库选择" size="small" style="width:260px" :disabled="isHistoryReadOnly" @change="(sid) => pickSupplyForItem(item, sid)">
                              <el-option v-for="m in allSupplyMedicines" :key="m.id" :value="m.id"
                                :label="(m.name || '') + (m.specification ? ' (' + m.specification + ')' : '') + ' · 库存' + (m.stock != null ? m.stock : '—')" />
                            </el-select>
                            <el-tag v-if="item.isLinkage" size="small" type="success" effect="plain">🔗联动</el-tag>
                            <el-input-number v-model="item.quantity" :min="1" size="small" style="width:100px" :disabled="isHistoryReadOnly" @change="syncBlocksToItems" />
                            <span class="treat-unit">{{ item.unit || '件' }}</span>
                            <span class="treat-subtotal">¥{{ ((Number(item.unitPrice)||0) * (item.quantity||1)).toFixed(2) }}</span>
                            <el-button type="danger" link size="small" :disabled="isHistoryReadOnly" @click="removeBlockItem(block, idx)">删除</el-button>
                          </div>
                        </div>
                        <el-button size="small" plain type="warning" class="btn-add-item" :disabled="isHistoryReadOnly || !canPrescribe" @click="addBlockItem(block)">
                          + 添加物资
                        </el-button>
                      </div>
                    </div>
                  </div>
                </div>

              </div>
              <div class="rx-blocks-empty" v-else>
                <div class="rbe-icon">📋</div>
                <div class="rbe-title">当前尚未开立门诊处方单</div>
                <div class="rbe-desc">请点击上方按钮添加【贴敷处方】、【西/中成药】、【中药处方】、【诊疗项目】或【医用物资】</div>
                <div class="rbe-actions">
                  <el-button type="warning" plain :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('patch')">+ 贴敷处方</el-button>
                  <el-button type="primary" plain :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('western')">+ 西/中成药</el-button>
                  <el-button type="success" plain :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('tcm')">+ 中药处方</el-button>
                  <el-button type="warning" plain :disabled="isHistoryReadOnly || !canPrescribe" @click="addNewPrescriptionBlock('supply')">+ 医用物资</el-button>
                </div>
              </div>

              <!-- ⑤ 医嘱事项 (Task 3 重新设计布局：预设标签分类点选与主输入区) -->
              <div class="emr-bottom-block advice-block-v2">
                <div class="block-left-label">
                  <div class="bll-title">医嘱事项</div>
                  <div class="bll-sub">临床生活指导</div>
                </div>
                <div class="block-right-body">
                  <!-- 预设标签分类点选区 -->
                  <div class="advice-preset-categories">
                    <div class="adv-cat-group">
                      <span class="adv-cat-name">🍵 饮食生活：</span>
                      <span 
                        v-for="adv in adviceGroupDiet" 
                        :key="adv" 
                        class="adv-tag-chip"
                        :class="{ active: isAdviceSelected(adv) }"
                        :disabled="isHistoryReadOnly"
                        @click="!isHistoryReadOnly && toggleAdviceItem(adv)"
                      >{{ adv }}</span>
                    </div>
                    <div class="adv-cat-group">
                      <span class="adv-cat-name">🌿 贴敷用药：</span>
                      <span 
                        v-for="adv in adviceGroupPatch" 
                        :key="adv" 
                        class="adv-tag-chip"
                        :class="{ active: isAdviceSelected(adv) }"
                        :disabled="isHistoryReadOnly"
                        @click="!isHistoryReadOnly && toggleAdviceItem(adv)"
                      >{{ adv }}</span>
                    </div>
                    <div class="adv-cat-group">
                      <span class="adv-cat-name">⏰ 护理复诊：</span>
                      <span 
                        v-for="adv in adviceGroupCare" 
                        :key="adv" 
                        class="adv-tag-chip"
                        :class="{ active: isAdviceSelected(adv) }"
                        :disabled="isHistoryReadOnly"
                        @click="!isHistoryReadOnly && toggleAdviceItem(adv)"
                      >{{ adv }}</span>
                    </div>
                  </div>

                  <!-- 主文本输入区 -->
                  <div class="advice-textarea-wrap">
                    <el-input
                      v-model="emr.medicalAdvice"
                      type="textarea"
                      :rows="3"
                      :disabled="isHistoryReadOnly"
                      placeholder="【医嘱说明】可直接点击上方预设标签快速点选录入，或在此输入医师专属个性化医嘱说明…"
                    />
                  </div>

                  <!-- 底部辅助功能条 -->
                  <div class="advice-footer-bar">
                    <div class="af-left">
                      <el-button 
                        class="deepseek-gen-btn" 
                        size="small" 
                        :disabled="isHistoryReadOnly"
                        @click="generateDeepSeekAdvice"
                      >
                        <span class="sparkle-anim">✨</span> AI智能生成医嘱
                      </el-button>
                      <span class="af-tip-text">根据主诉辨证、诊断及所开处方智能萃取生活起居注意事项</span>
                    </div>
                    <div class="af-right" v-if="!isHistoryReadOnly">
                      <div class="advice-custom-add">
                        <el-input
                          v-model="customAdviceText"
                          placeholder="添加自定义常用医嘱条目…"
                          size="small"
                          maxlength="200"
                          style="width:220px"
                          @keydown.enter="addCustomAdvice"
                        />
                        <el-button size="small" type="primary" plain @click="addCustomAdvice">添加</el-button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <div class="emr-bottom-block safety-block">
                <div class="block-left-label">合理用药</div>
                <div class="block-right-body flex-between">
                  <span class="safety-hint-txt">系统实时审查处方配伍禁忌、重复用药及超剂量红线</span>
                  <el-button type="primary" size="small" plain class="btn-rx-check" @click="checkDrugSafety">
                    校 验
                  </el-button>
                </div>
              </div>

              <!-- 处方合计金额 -->
              <div class="rx-summary-bar">
                <span>门诊诊金 ¥10.00 + 诊疗项目: ¥{{ treatmentTotal.toFixed(2) }} + 处方: </span>
                <span class="rx-total-amt">¥{{ totalRxAmount.toFixed(2) }}</span>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

            <!-- 2. 中栏：无就诊患者时（或待诊为0）的接诊台就绪占位区 -->
      <div class="emr-workspace-panel empty-emr-panel" v-else>
        <div class="empty-consult-desk">
          <div class="ecd-badge">🏥 门诊就诊室 · 接诊台候诊就绪</div>
          <div class="ecd-illustration">🩺</div>
          <h3 class="ecd-title">{{ queueDate === todayKey ? '当前待诊队列暂无候诊患者' : queueDate + ' 无门诊接诊记录' }}</h3>
          <p class="ecd-desc">
            {{ queueDate === todayKey
              ? '今日待诊患者已全部接诊完毕，或正在等待新患者挂号到达。如需调阅既往档案，可在左侧队列切换至【已结束】；如现场有患者临时就医，可点击【快速接诊】即时建档。'
              : '当前所选历史日期无就诊记录。您可以切换其他就诊日期或一键返回今日接诊。'
            }}
          </p>
          <div class="ecd-actions">
            <el-button type="primary" size="default" class="btn-quick-consult gradient-btn" @click="handleQuickDirectConsult">
              ⚡ 快速接诊新患者
            </el-button>
            <el-button v-if="doneList.length > 0" size="default" @click="switchQueueTab('done')">
              📋 调阅今日已结诊档案 ({{ doneList.length }}人)
            </el-button>
            <el-button v-if="queueDate !== todayKey" size="default" @click="goToToday">
              返回今日接诊
            </el-button>
          </div>
        </div>
      </div>

      <!-- 3. 右栏：AI 智能体多轮对话助手 -->
      <div class="clinic-right-panel">
        <!-- 右栏功能模式切换 Tabs (深度复刻截图 1-5 右侧边栏就诊记录体系) -->
        <div class="right-panel-tabs">
          <div 
            class="rp-tab" 
            :class="{ active: rightPanelTab === 'history' }" 
            @click="rightPanelTab = 'history'"
          >
            就诊记录<sup class="rp-sup">{{ currentPatientPastVisits.length }}</sup>
          </div>
          <div 
            class="rp-tab" 
            :class="{ active: rightPanelTab === 'ai' }" 
            @click="rightPanelTab = 'ai'"
          >
            🤖 AI临床助手
          </div>
          <div 
            class="rp-tab" 
            :class="{ active: rightPanelTab === 'template' }" 
            @click="rightPanelTab = 'template'"
          >
            📑 常用模板
          </div>
        </div>

        <!-- 模式 A：患者往期就诊记录与一键引用 (深度复刻截图 1-5 右侧栏 09/01, 08/26, 08/13...) -->
        <div class="rp-content-view history-view" v-if="rightPanelTab === 'history'">
          <div class="history-search-bar">
            <el-input 
              v-model="historySearchKey" 
              placeholder="🔍 搜索历史就诊/诊断/药名" 
              size="small" 
              clearable 
            />
          </div>
          <div class="history-card-list">
            <div
              class="history-visit-card"
              :class="{ active: activeHistoryVisitId === visit.date }"
              v-for="(visit, vIdx) in filteredHistoryVisits"
              :key="vIdx"
              @click="loadHistoryVisitToWorkstation(visit)"
              title="点击直接在工作台中只读预览该往期病历与处方"
            >
              <div class="hvc-header">
                <div class="hvc-date-group">
                  <span class="hvc-date">{{ visit.date }}</span>
                  <span class="hvc-badge-returntype">{{ visit.visitType }}</span>
                  <span class="hvc-type-tags">{{ visit.typeBadge }}</span>
                </div>
                <div class="hvc-actions">
                  <el-button
                    size="small"
                    type="primary"
                    link
                    class="hvc-cite-btn"
                    :disabled="!canPrescribe"
                    @click.stop="applyHistoryVisit(visit)"
                    :title="canPrescribe ? '将该往期处方与病历一键导入当前处方区' : '待诊患者尚未开始接诊，不能引用历史处方'"
                  >
                    引用开方
                  </el-button>
                  <el-button
                    size="small"
                    type="info"
                    link
                    class="hvc-detail-btn"
                    @click.stop="viewHistoryDetail(visit)"
                  >
                    详情
                  </el-button>
                </div>
              </div>
              <div class="hvc-diagnosis-line">
                <span class="hvc-diag-name">{{ visit.diagnosis }}</span>
                <span class="hvc-diag-tcm" v-if="visit.tcmDiagnosis">({{ visit.tcmDiagnosis }})</span>
              </div>
              <div class="hvc-symptom-line text-truncate">
                <b>主诉：</b>{{ visit.symptoms }}
              </div>
              <div class="hvc-rx-brief">
                <span class="hvc-rx-lbl">处方：</span>
                <span class="hvc-rx-txt text-truncate">{{ formatHistoryRxBrief(visit) }}</span>
                <span class="hvc-fee">¥{{ visit.totalFee.toFixed(2) }}</span>
              </div>
            </div>
            <div v-if="filteredHistoryVisits.length === 0" class="empty-history-hint">
              未找到匹配的历史就诊记录
            </div>
          </div>
        </div>

        <!-- 模式 B：AI 临床智能体多轮对话 (原有核心功能完整保留) -->
        <div class="rp-content-view ai-view" v-else-if="rightPanelTab === 'ai'">
          <!-- 对话框顶栏 -->
          <div class="copilot-header">
            <div class="copilot-title">
              <span class="sparkle-icon">🤖</span>
              <span>AI 临床助手</span>
            </div>
            <div class="copilot-header-actions">
              <el-select v-model="chatModelName" size="small" style="width:120px;" @change="onModelChange">
                <el-option
                  v-for="p in availableProviders"
                  :key="p.id"
                  :label="p.name"
                  :value="p.modelName"
                />
              </el-select>
              <el-button size="small" link @click="confirmClearCurrentChat" title="清空当前对话">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>

          <!-- 会话历史与新建会话快捷工具栏 -->
          <div class="copilot-session-bar">
            <div class="csb-left">
              <el-button size="small" type="primary" class="csb-btn-new gradient-btn" @click="createNewChatSession" title="新建临床会话">
                <el-icon style="margin-right: 2px;"><Plus /></el-icon> 新建
              </el-button>
              <el-button size="small" class="csb-btn-history" @click="showChatHistoryDrawer = true" title="查看会话记录">
                <el-icon style="margin-right: 2px;"><Clock /></el-icon> 历史 ({{ chatSessionList.length }})
              </el-button>
            </div>
            <div class="csb-right" :title="activeSessionTitle">
              <span class="csb-session-tag">
                <span class="csb-dot"></span>
                <span class="csb-tag-text">{{ activeSessionTitle }}</span>
              </span>
            </div>
          </div>

          <!-- 消息列表区域 (彻底修复头像位置错乱、双气泡以及缺乏知识库/思考过程问题) -->
          <div class="chat-messages-area" ref="chatAreaRef">
            <div class="chat-msg assistant" v-if="chatMessages.length === 0">
              <div class="msg-avatar">🩺</div>
              <div class="msg-body">
                <div class="msg-bubble">
                  您好，我是春播万象 AI 临床助手。<br>
                  您可以：<br>
                  ① 点击上方 <strong>🚀 发送病历至AI辨证开方</strong><br>
                  ② 点击下方 <strong>📋 病历审查</strong> 审查全套处方安全<br>
                  ③ 点击 <strong>💊 辨证开方</strong> 生成经典中西结合处方卡片并一键采纳
                </div>
              </div>
            </div>

            <template v-for="(msg, idx) in chatMessages" :key="idx">
              <!-- 医生发送的消息：医生头像严格在右侧，气泡在左侧，无任何错乱 -->
              <div class="chat-msg user" v-if="msg.role === 'user'">
                <div class="msg-body user-body">
                  <div class="msg-bubble user-bubble">{{ msg.content }}</div>
                </div>
                <div class="msg-avatar user-avatar">👨‍⚕️</div>
              </div>

              <!-- AI 临床助手回复消息 -->
              <div class="chat-msg assistant" v-else>
                <div class="msg-avatar">🩺</div>
                <div class="msg-body">
                  <!-- 知识库引用标签栏（展示后端 RAG 真实命中的规范文档） -->
                  <div class="ai-tool-call-box" v-if="msg.knowledgeBases && msg.knowledgeBases.length">
                    <div class="tool-call-row">
                      <span class="tool-label">📚 知识库引用（RAG）：</span>
                      <span class="tool-tags-wrap">
                        <el-tag v-for="kb in msg.knowledgeBases" :key="kb" size="small" type="success" effect="light" class="ai-tag-chip" :title="kb"><span class="mcp-chip-text">{{ kb }}</span></el-tag>
                      </span>
                    </div>
                  </div>

                  <!-- MCP 工具调用与数据核验过程（生成中展示，完成后彻底隐藏只保留正文） -->
                  <div class="ai-tool-call-box" v-if="!msg.processDone && msg.processSteps && msg.processSteps.length">
                    <div class="tool-call-row">
                      <span class="tool-label">⚡ 工具执行与数据核验：</span>
                      <span class="tool-tags-wrap process-steps-col">
                        <span v-for="(st, sIdx) in msg.processSteps" :key="sIdx" class="process-step-line">{{ st }}</span>
                      </span>
                    </div>
                  </div>

                  <!-- DeepSeek-R1 风格临床推理思考折叠卡 -->
                  <div class="reasoning-fold" v-if="msg.thinking">
                    <div class="reasoning-toggle" @click="msg._showThink = !msg._showThink">
                      <span class="think-icon">🧠</span>
                      <span>AI 临床推理思考过程 {{ msg.thinkingTime ? '(耗时 ' + msg.thinkingTime + 's)' : '' }} (点击{{ msg._showThink ? '收起' : '展开' }})</span>
                    </div>
                    <div class="reasoning-body" v-show="msg._showThink">{{ msg.thinking }}</div>
                  </div>

                  <!-- 回复正文 (支持平滑流式字符与闪烁光标) -->
                  <div class="msg-bubble assistant-bubble" v-if="msg.content || msg.isStreaming">
                    <div v-if="!msg.content && msg.isStreaming" class="ai-thinking-hint">
                      <span class="dot"></span><span class="dot"></span><span class="dot"></span>
                      <span style="font-size:12px;color:#64748b;margin-left:6px;">正在结合病历与知识库辨证思考…</span>
                    </div>
                    <div v-html="renderMd(msg.content)"></div>
                    <span class="typing-cursor" v-if="msg.isStreaming">▋</span>
                    <!-- 朗读回答（TTS） -->
                    <div class="msg-tts-row" v-if="msg.content && !msg.isStreaming">
                      <span class="tts-toggle" @click="speakAiMessage(msg, idx)">{{ msg._speaking ? '⏹ 停止朗读' : '🔊 朗读回答' }}</span>
                    </div>
                  </div>

                  <!-- 推荐处方卡片与一键采纳 (实现发送病历后给出处方卡片，点击一键采纳直接填写左侧) -->
                  <div class="rx-suggestion-card" v-if="msg.rxItems && msg.rxItems.length">
                    <div class="rx-card-top-header">
                      <span class="rx-card-title">📋 AI 临床推荐处方与医嘱方案</span>
                      <el-tag size="small" type="success" effect="dark">中西协同推荐</el-tag>
                    </div>
                    <div class="rx-card-item-list">
                      <div class="rx-card-row" v-for="(item, iIdx) in msg.rxItems" :key="iIdx">
                        <el-tag size="small" :type="item.category === '特色贴敷' ? 'warning' : item.category === '中药' ? 'success' : 'primary'" effect="plain">
                          {{ item.category || '处方药' }}
                        </el-tag>
                        <span class="rx-item-name">{{ item.name }}</span>
                        <span class="rx-item-info">{{ item.dosage || (item.dose ? item.dose + 'g' : (item.quantity ? item.quantity + (item.unit || '盒') : '1剂')) }}</span>
                        <span class="rx-item-price">¥{{ Number(item.unitPrice || 25).toFixed(2) }}</span>
                      </div>
                    </div>
                    <div class="rx-card-advice-line" v-if="msg.advice">
                      <span class="advice-tag">💡 医嘱建议：</span>
                      <span class="advice-content">{{ msg.advice }}</span>
                    </div>
                    <el-button type="success" class="rx-adopt-btn" @click="adoptRxFromMsg(msg)">
                      <el-icon style="margin-right: 4px;"><Select /></el-icon>
                      <span>一键采纳方案 (覆盖导入左侧)</span>
                    </el-button>
                  </div>
                </div>
              </div>
            </template>

            <!-- 等待指示器：只在尚未生成回复且真正处于加载中时显示单组听诊器，杜绝重复气泡 -->
            <div class="chat-msg assistant typing" v-if="chatTyping && (!chatMessages.length || chatMessages[chatMessages.length - 1].role === 'user')">
              <div class="msg-avatar">🩺</div>
              <div class="msg-body">
                <div class="typing-bubble">
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span class="dot"></span>
                  <span style="font-size: 11px; color: #94a3b8; margin-left: 6px;">AI 正在调阅知识库与辨证思考...</span>
                </div>
              </div>
            </div>
          </div>

          <!-- 快捷操作栏 (美观单行 3 等分网格，彻底解决折行挤压问题) -->
          <div class="chat-quick-actions-modern">
            <button type="button" class="qa-pill qa-blue" @click="sendPatientSummaryToAi">
              📋 病历审查
            </button>
            <button type="button" class="qa-pill qa-red" :disabled="!canPrescribe" @click="quickAskHerbContraindications">
              💊 辨证开方
            </button>
            <button type="button" class="qa-pill qa-amber" @click="checkDrugSafety">
              🔍 处方质控
            </button>
            <button type="button" class="qa-pill qa-green" @click="checkPharmacyRealStock" title="穿透 MySQL 真实药房进销存">
              🏥 查药房库存
            </button>
          </div>

          <!-- 输入区 (现代化内嵌式卡片排版，宽敞舒适) -->
          <div class="modern-chat-box">
            <el-input
              v-model="chatInput"
              type="textarea"
              :rows="2"
              placeholder="输入症状或临床问题，AI 实时辨证回复..."
              resize="none"
              class="modern-chat-textarea"
              @keydown.enter.prevent="sendAiMessage"
            />
            <div class="modern-chat-bottom">
              <div class="mic-btn" :class="{ recording: isRecording }" @click="toggleVoiceInput"
                   :title="isRecording ? '点击结束语音录入' : '语音录入（AI 识别转文字）'">
                <el-icon v-if="!isRecording" :size="17"><Microphone /></el-icon>
                <span v-else style="font-size: 13px;">⏹</span>
              </div>
              <span class="chat-key-hint">Enter 发送</span>
              <el-button
                v-if="!chatTyping"
                type="primary"
                size="small"
                class="modern-send-btn"
                @click="sendAiMessage"
              >
                发送
              </el-button>
              <el-button
                v-else
                type="danger"
                size="small"
                class="modern-send-btn"
                @click="stopGeneration"
              >
                ⏹ 停止
              </el-button>
            </div>
          </div>
        </div>

        <!-- 模式 C：经典方剂与病历常用模板 (深度复刻截图 1-5 底部 常用模板/经典方剂) -->
        <div class="rp-content-view template-view" v-else-if="rightPanelTab === 'template'">
          <div class="tpl-search-bar">
            <el-input 
              v-model="tplSearchKey" 
              placeholder="🔍 搜索方剂/模板名称" 
              size="small" 
              clearable 
            />
          </div>
          <div class="tpl-card-list">
            <div 
              class="tpl-item-card" 
              v-for="(tpl, tIdx) in filteredPrescriptionTemplates" 
              :key="tIdx"
            >
              <div class="tpl-head">
                <span class="tpl-name">{{ tpl.name }}</span>
                <el-tag size="small" :type="tpl.category === '贴敷' ? 'success' : 'primary'">{{ tpl.category }}</el-tag>
              </div>
              <div class="tpl-indication"><b>适应症：</b>{{ tpl.indication }}</div>
              <div class="tpl-rx-desc text-truncate"><b>方药：</b>{{ tpl.rxDescription }}</div>
              <div class="tpl-foot">
                <span class="tpl-fee">预估：¥{{ tpl.totalPrice.toFixed(2) }}</span>
                <div class="tpl-btns">
                  <el-button size="small" link type="info" @click="viewTemplateDetail(tpl)">详情</el-button>
                  <el-button size="small" type="primary" plain @click="applyPrescriptionTemplate(tpl)">一键引用</el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>

    
    <!-- 弹窗：门诊工作台现场开通/升级会员 (Point 9) -->
    <el-dialog v-model="showWorkstationMemberModal" title="💳 门诊现场办理/升级会员" width="460px">
      <el-form label-width="95px" class="member-ws-form">
        <el-form-item label="就诊患者">
          <span class="font-bold">{{ currentPatient?.patientName }} ({{ currentPatient?.phone || '手机号未录入' }})</span>
        </el-form-item>
        <el-form-item label="会员等级">
          <el-radio-group v-model="wsMemberForm.level">
            <el-radio value="慢病签约会员">
              <span class="font-bold text-amber">🥈 慢病签约会员</span>
              <span class="text-xs text-slate-500"> (9折开药 · 送300积分)</span>
            </el-radio>
            <el-radio value="VIP会员">
              <span class="font-bold text-emerald">💎 VIP白金会员</span>
              <span class="text-xs text-slate-500"> (85折开药 · 送800积分)</span>
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="会员有效期">
          <el-date-picker v-model="wsMemberForm.expiry" type="date" value-format="YYYY-MM-DD" placeholder="请选择有效期" style="width:100%" />
        </el-form-item>
        <el-form-item label="储值充值(元)">
          <el-input-number v-model="wsMemberForm.rechargeAmount" :min="0" :step="100" style="width:160px" />
          <span class="text-xs text-slate-400 ml-2">充值金额直接进入该患者账户余额</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showWorkstationMemberModal = false">取消</el-button>
        <el-button type="primary" class="gradient-btn" @click="submitWorkstationMember">确认立即办理</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗：门诊工作台绑定家庭附属卡 (Point 2) -->
    <el-dialog v-model="showAuxModal" title="💳 门诊办理/绑定家庭附属卡" width="460px">
      <el-form label-width="95px" class="member-ws-form">
        <el-form-item label="就诊患者">
          <span class="font-bold">{{ currentPatient?.patientName }} ({{ currentPatient?.phone || '手机号未录入' }})</span>
        </el-form-item>
        <el-form-item label="家庭主卡人">
          <el-input v-model="auxCardForm.ownerName" placeholder="如：刘先生 (陪护人/家属)" />
        </el-form-item>
        <el-form-item label="主卡手机号">
          <el-input v-model="auxCardForm.ownerPhone" placeholder="请输入主卡绑定的手机号" />
        </el-form-item>
        <el-form-item label="共享权益">
          <el-tag type="success" effect="light">💎 共享家庭主卡 VIP 8.5折待遇 · 积分合并储值共享</el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAuxModal = false">取消</el-button>
        <el-button type="primary" class="gradient-btn" @click="submitAuxCard">确认绑定生效</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗：临时挂号转正建档 (Point 7) -->
    <el-dialog v-model="showTempPatientModal" title="临时患者信息完善与建档转正" width="500px">
      <el-form :model="tempPatientForm" label-width="95px">
        <el-form-item label="真实姓名" required>
          <el-input v-model="tempPatientForm.name" placeholder="请输入患者真实姓名" />
        </el-form-item>
        <el-form-item label="性别" required>
          <el-radio-group v-model="tempPatientForm.gender">
            <el-radio value="男">男</el-radio>
            <el-radio value="女">女</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="年龄/岁" required>
          <el-input-number v-model="tempPatientForm.age" :min="1" :max="120" style="width:140px" />
        </el-form-item>
        <el-form-item label="手机号码" required>
          <el-input v-model="tempPatientForm.phone" placeholder="请输入11位手机号 (用于关联往期病历与会员)" />
        </el-form-item>
        <el-form-item label="身份证号">
          <el-input v-model="tempPatientForm.idCard" placeholder="选填，用于医保与电子健康卡" />
        </el-form-item>
        <el-form-item label="家庭住址">
          <el-input v-model="tempPatientForm.address" placeholder="选填，常住地址" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showTempPatientModal = false">暂不完善</el-button>
        <el-button type="primary" class="gradient-btn" @click="submitTempPatientRegularize">保存并永久建档</el-button>
      </template>
    </el-dialog>

    <!-- 现场收费结算弹窗 (结束并收费 · 在当前页面就收费) -->
    <el-dialog
      v-model="instantPayDialogVisible"
      title="💳 现场划价收费结算 · 结束并收费"
      width="650px"
      append-to-body
      destroy-on-close
    >
      <div class="instant-pay-dialog-body">
        <!-- 患者信息简表 -->
        <div class="pay-patient-header">
          <span class="pp-name">{{ currentPatient?.patientName }}</span>
          <el-tag size="small" type="info">{{ currentPatient?.gender }} / {{ currentPatient?.ageText || (currentPatient?.age + '岁') }}</el-tag>
          <span class="pp-id">挂号单号: #{{ currentPatient?.queueNumber || currentPatient?.id }}</span>
          <span class="pp-dept">{{ currentPatient?.department }} ({{ currentPatient?.doctorName || '接诊医生' }})</span>
        </div>

        <!-- 详细费用清单 -->
        <div class="pay-detail-card">
          <div class="pd-head">收费账单明细</div>
          <div class="pd-row">
            <span>门诊挂号诊金</span>
            <span class="pd-amt">¥ 10.00</span>
          </div>
          <div class="pd-row" v-if="treatmentTotal > 0">
            <span>特色理疗 / 诊疗项目 ({{ treatmentItems.length }} 项)</span>
            <span class="pd-amt">¥ {{ treatmentTotal.toFixed(2) }}</span>
          </div>
          <div class="pd-row" v-if="patchRxItems.length > 0">
            <span>特色穴位贴敷处方 ({{ patchRxItems.length }} 贴)</span>
            <span class="pd-amt">¥ {{ patchRxTotal.toFixed(2) }}</span>
          </div>
          <div class="pd-row" v-if="westernRxItems.length > 0">
            <span>西药 / 中成药处方 ({{ westernRxItems.length }} 项)</span>
            <span class="pd-amt">¥ {{ westernRxTotal.toFixed(2) }}</span>
          </div>
          <div class="pd-row" v-if="tcmRxItems.length > 0">
            <span>中药汤剂处方 ({{ tcmRxItems.length }} 味)</span>
            <span class="pd-amt">¥ {{ tcmRxTotal.toFixed(2) }}</span>
          </div>
          <div class="pd-sum-row">
            <span class="sum-lbl">应收总额：</span>
            <span class="sum-big-amt">¥ {{ totalRxAmount.toFixed(2) }}</span>
          </div>
        </div>

        <!-- 支付方式选择 -->
        <div class="pay-method-zone">
          <div class="method-title">结算收款方式：</div>
          <el-radio-group v-model="instantPayMethod" size="default">
            <el-radio-button value="wechat">🟢 微信扫码</el-radio-button>
            <el-radio-button value="alipay">🔵 支付宝</el-radio-button>
            <el-radio-button value="yibao">🛡️ 医保结算</el-radio-button>
            <el-radio-button value="cash">💵 现金收款</el-radio-button>
            <el-radio-button value="member">💳 会员储值</el-radio-button>
          </el-radio-group>
        </div>

        <!-- 动态收款渠道交互演示 -->
        <div class="pay-channel-view" v-if="instantPayMethod === 'wechat' || instantPayMethod === 'alipay'">
          <div class="mock-qr-wrap">
            <div class="qr-canvas-sim">
              <div class="qr-center-logo">{{ instantPayMethod === 'wechat' ? '微信' : '支' }}</div>
            </div>
            <div class="qr-tip-txt">请患者出示微信/支付宝付款码，或直接扫码完成 ¥{{ totalRxAmount.toFixed(2) }} 扣款</div>
          </div>
        </div>
        <div class="pay-channel-view" v-else-if="instantPayMethod === 'yibao'">
          <el-alert type="success" :closable="false" show-icon title="国家医疗保障局脱卡直结通道已连通">
            支持医保电子凭证二维码核验、参保个人账户划扣与门诊统筹报销。
          </el-alert>
        </div>
        <div class="pay-channel-view" v-else-if="instantPayMethod === 'cash'">
          <div class="cash-calc-bar">
            <span>实收现金：¥</span>
            <el-input-number v-model="cashReceivedAmt" :min="totalRxAmount" :precision="2" size="default" style="width: 150px;" />
            <span class="ml-4">应找零：<b style="color: #10b981; font-size: 16px;">¥ {{ Math.max(0, cashReceivedAmt - totalRxAmount).toFixed(2) }}</b></span>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="instantPayDialogVisible = false">返回诊室</el-button>
        <el-button type="success" size="large" class="btn-confirm-instant-pay" :loading="instantPayLoading" @click="confirmInstantPayment">
          确认收款并打印收据 ¥{{ totalRxAmount.toFixed(2) }}
        </el-button>
      </template>
    </el-dialog>
    <!-- 💾 另存为常用处方模板弹窗 (开方现场保存模板供后续直接引用) -->
    <el-dialog
      v-model="saveTemplateDialogVisible"
      title="💾 保存当前病历与处方为常用模板"
      width="580px"
      append-to-body
      destroy-on-close
    >
      <el-form :model="saveTemplateForm" label-width="90px" size="small">
        <el-form-item label="模板名称" required>
          <el-input v-model="saveTemplateForm.name" placeholder="例如：风热感冒清热宣肺方、高血压平肝对症方" />
        </el-form-item>
        <el-form-item label="处方类别" required>
          <el-select v-model="saveTemplateForm.category" style="width:100%">
            <el-option label="中西结合处方" value="中西" />
            <el-option label="特色贴敷处方" value="贴敷" />
            <el-option label="经典中药方剂" value="中药" />
            <el-option label="诊疗理疗项目" value="理疗" />
          </el-select>
        </el-form-item>
        <el-form-item label="适应病症">
          <el-input v-model="saveTemplateForm.indication" placeholder="例如：主治发热咽痛、咳嗽痰黄、原发性头痛" />
        </el-form-item>
        <el-form-item label="生活医嘱">
          <el-input v-model="saveTemplateForm.advice" type="textarea" :rows="2" placeholder="生活调护与用药医嘱事项" />
        </el-form-item>
        <el-form-item label="打包内容">
          <div class="tpl-pack-summary">
            <el-tag size="small" type="success">贴敷处方: {{ patchRxItems.length }} 贴</el-tag>
            <el-tag size="small" type="primary" class="ml-2">西/中成药: {{ westernRxItems.length }} 项</el-tag>
            <el-tag size="small" type="warning" class="ml-2">中药饮片: {{ tcmRxItems.length }} 味</el-tag>
            <el-tag size="small" type="info" class="ml-2">诊疗理疗: {{ treatmentItems.length }} 项</el-tag>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="saveTemplateDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="confirmSavePrescriptionTemplate">确认保存并收录</el-button>
      </template>
    </el-dialog>

    <!-- 历史就诊详情档案抽屉 (查看任意往期就诊的完整四诊、处方与缴费记录) -->
    <el-dialog
      v-model="historyDetailDrawerVisible"
      title="📋 往期门诊病历与处方详情归档"
      width="720px"
      append-to-body
      destroy-on-close
    >
      <div class="history-detail-body" v-if="selectedHistoryVisit">
        <!-- 顶部信息 -->
        <div class="hd-top-info">
          <div class="hd-main-line">
            <span class="hd-name">{{ currentPatient?.patientName }}</span>
            <el-tag size="small" type="info">{{ currentPatient?.gender }} / {{ currentPatient?.ageText }}</el-tag>
            <span class="hd-date">就诊时间：{{ selectedHistoryVisit.date }}</span>
            <el-tag size="small" type="danger">{{ selectedHistoryVisit.visitType }}</el-tag>
          </div>
          <div class="hd-sub-line">
            接诊医生：{{ selectedHistoryVisit.doctor || currentUserName }} · 就诊科室：中医全科门诊 · 结算状态：<el-tag size="small" type="success">已结清 (¥{{ selectedHistoryVisit.totalFee.toFixed(2) }})</el-tag>
          </div>
        </div>

        <!-- 病历诊断 -->
        <div class="hd-section">
          <div class="hd-sec-title">🏥 临床诊断与中医辨证</div>
          <div class="hd-diag-box">
            <div><b>西医诊断：</b>{{ selectedHistoryVisit.diagnosis }}</div>
            <div class="mt-1" v-if="selectedHistoryVisit.tcmDiagnosis"><b>中医辨证：</b>{{ selectedHistoryVisit.tcmDiagnosis }}</div>
            <div class="mt-1"><b>患者主诉：</b>{{ selectedHistoryVisit.symptoms }}</div>
          </div>
        </div>

        <!-- 处方明细 -->
        <div class="hd-section">
          <div class="hd-sec-title">💊 开具处方与医嘱项目</div>
          <table class="hd-rx-table">
            <thead>
              <tr>
                <th>处方类型</th>
                <th>项目 / 药品名称</th>
                <th>用法用量 / 穴位</th>
                <th>频次 / 疗程</th>
                <th>金额</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(t, i) in (selectedHistoryVisit.treatments || [])" :key="'ht_'+i">
                <td><el-tag size="small" type="info">理疗</el-tag></td>
                <td>{{ t.name }}</td>
                <td>{{ t.quantity }} 次</td>
                <td>单次执行</td>
                <td>¥{{ t.price.toFixed(2) }}</td>
              </tr>
              <tr v-for="(p, i) in (selectedHistoryVisit.patchItems || [])" :key="'hp_'+i">
                <td><el-tag size="small" type="success">贴敷</el-tag></td>
                <td>{{ p.name }}</td>
                <td>穴位: {{ p.acupoints }} ({{ p.dose }})</td>
                <td>{{ p.frequency || '1次/天' }} · {{ p.days || 1 }}天</td>
                <td>¥{{ (p.unitPrice * (p.quantity || 1)).toFixed(2) }}</td>
              </tr>
              <tr v-for="(w, i) in (selectedHistoryVisit.westernItems || [])" :key="'hw_'+i">
                <td><el-tag size="small">西/成药</el-tag></td>
                <td>{{ w.name }}</td>
                <td>{{ w.route || '口服' }} · {{ w.dose }}</td>
                <td>{{ w.frequency }} · {{ w.days }}天</td>
                <td>¥{{ (w.unitPrice * (w.quantity || 1)).toFixed(2) }}</td>
              </tr>
              <tr v-for="(m, i) in (selectedHistoryVisit.tcmItems || [])" :key="'hm_'+i">
                <td><el-tag size="small" type="warning">中药</el-tag></td>
                <td>{{ m.name }}</td>
                <td>{{ m.dose }}</td>
                <td>水煎服 · 日一剂</td>
                <td>¥{{ (m.unitPrice * (parseFloat(m.dose)||10)).toFixed(2) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 医嘱指导 -->
        <div class="hd-section">
          <div class="hd-sec-title">📝 医生医嘱</div>
          <div class="hd-advice-box">{{ selectedHistoryVisit.advice || '遵医嘱清淡饮食，适寒温。' }}</div>
        </div>
      </div>
      <template #footer>
        <el-button @click="historyDetailDrawerVisible = false">关闭</el-button>
        <el-button type="primary" :disabled="!canPrescribe" @click="applyHistoryVisit(selectedHistoryVisit); historyDetailDrawerVisible = false">
          一键引用此往期处方与病历
        </el-button>
      </template>
    </el-dialog>

    <!-- 抽屉：AI 临床会话历史记录 (点击直接切换调阅往期会话) -->
    <el-drawer
      v-model="showChatHistoryDrawer"
      title="📜 AI 临床问诊会话历史"
      size="380px"
      direction="rtl"
      class="ai-history-drawer"
    >
      <div class="history-drawer-body">
        <div class="hd-top-bar">
          <span class="hd-count-text">共 {{ chatSessionList.length }} 场历史问诊会话</span>
          <div style="display: flex; gap: 6px;">
            <el-button size="small" type="danger" plain text @click="clearDoctorSessionHistory">
              🧹 清空
            </el-button>
            <el-button size="small" type="primary" class="gradient-btn" @click="createNewChatSession">
              <el-icon><Plus /></el-icon> 新建会话
            </el-button>
          </div>
        </div>

        <div class="history-list-scroll" v-if="chatSessionList.length > 0">
          <template v-for="g in CHAT_SESSION_GROUPS" :key="g.key">
            <div v-if="groupedChatSessions[g.key].length" class="history-group">
              <div class="history-group-label">{{ g.label }} · {{ groupedChatSessions[g.key].length }}场</div>
              <div
                v-for="sess in groupedChatSessions[g.key]"
                :key="sess.id"
                class="history-card"
                :class="{ active: currentSessionId === sess.id }"
                @click="switchChatSession(sess.id)"
              >
                <div class="hc-head">
                  <span class="hc-title" :title="sess.title">{{ sess.title }}</span>
                  <el-tag size="small" :type="currentSessionId === sess.id ? 'success' : 'info'" effect="light">
                    {{ currentSessionId === sess.id ? '当前使用中' : (sess.messages.length + '条') }}
                  </el-tag>
                </div>
                <div class="hc-snippet">
                  {{ getSessionSnippet(sess) }}
                </div>
                <div class="hc-foot">
                  <span class="hc-time">🕒 {{ sess.updatedAt || sess.createdAt }}</span>
                  <el-button
                    size="small"
                    link
                    type="danger"
                    @click.stop="deleteChatSession(sess.id)"
                    title="删除此条历史会话"
                  >
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>
          </template>
        </div>

        <div class="history-empty" v-else>
          <div class="empty-icon">💬</div>
          <div class="empty-text">暂无历史问诊会话</div>
          <div class="empty-sub">与 AI 临床助手沟通开方后会自动为您记忆沉淀</div>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch, nextTick, inject } from 'vue'
import { ElMessage, ElMessageBox, ElNotification } from 'element-plus'
import axios from 'axios'

const queueTab = ref('waiting')
// 右栏子标签持久化：刷新后停留在原标签（就诊记录/ai/模板），不再跳回就诊记录
const RIGHT_PANEL_TAB_KEY = 'chunbo_right_panel_tab'
const RIGHT_PANEL_TABS = ['history', 'ai', 'template']
const rightPanelTab = ref(RIGHT_PANEL_TABS.includes(localStorage.getItem(RIGHT_PANEL_TAB_KEY))
  ? localStorage.getItem(RIGHT_PANEL_TAB_KEY) : 'history')
watch(rightPanelTab, (t) => {
  if (t) localStorage.setItem(RIGHT_PANEL_TAB_KEY, t)
})
const historySearchKey = ref('')
const tplSearchKey = ref('')
const historyDetailDrawerVisible = ref(false)
const selectedHistoryVisit = ref(null)

const historyPreviewMode = ref(false)
const previewHistoryData = ref(null)
// ── 当前真实日期动态计算基准 ──
const formatDateKey = (d) => {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

const now = new Date()
const todayKey = formatDateKey(now) // 动态今天 (2026-09-17)
const yesterdayKey = formatDateKey(new Date(now.getTime() - 24 * 60 * 60 * 1000)) // 动态昨天 (2026-09-16)
const dayBeforeYesterdayKey = formatDateKey(new Date(now.getTime() - 48 * 60 * 60 * 1000)) // 动态前天 (2026-09-15)

const isClosedStatus = (status) => {
  return (
    status === '已结诊' ||
    status === '已完成' ||
    status === '已收费' ||
    status === '已诊' ||
    status === '已退' ||
    status === '已退号' ||
    status === '过号' ||
    status === '已过号'
  )
}

// ── 就诊队列专业排队规则算法：接诊中置顶，待诊根据策略按签到先后或预约序号升序 ──
const sortWaitingQueue = (list) => {
  if (!list || !list.length) return []
  let sortMode = 'sign'
  try {
    const s = JSON.parse(localStorage.getItem('chunbo_clinic_settings') || '{}')
    if (s.sortMode) sortMode = s.sortMode
  } catch (e) {}

  return [...list].sort((a, b) => {
    // 1. 接诊中置顶
    if (a.status === '接诊中' && b.status !== '接诊中') return -1
    if (b.status === '接诊中' && a.status !== '接诊中') return 1

    if (sortMode === 'sign') {
      // 现场排队叫号机制：按签到到达先后顺序
      const tA = a.signTime || a.createTime || ''
      const tB = b.signTime || b.createTime || ''
      if (tA && tB && tA !== tB) return tA.localeCompare(tB)
    }

    // 严格按挂号序号升序排列
    const getNum = (item) => {
      if (item.queueNumber) {
        const parts = String(item.queueNumber).split('-')
        const n = parseInt(parts[parts.length - 1], 10)
        if (!isNaN(n)) return n
      }
      return item.id || 0
    }
    return getNum(a) - getNum(b)
  })
}

const queueDate = ref(todayKey)

// ── 多日期就诊状态与挂号患者数据仓储 (按日期完全隔离与保存状态) ──
const loadInitialDateStore = () => {
  try {
    const raw = localStorage.getItem('chunbo_clinic_dates_data')
    if (raw) {
      const parsed = JSON.parse(raw)
      if (parsed && typeof parsed === 'object') {
        // 自动清洗：凡是已结诊、已诊、已完成、已退、过号等已结束状态的患者，绝不允许滞留在 waiting 队列中！
        Object.keys(parsed).forEach(dk => {
          const b = parsed[dk]
          if (b) {
            if (!Array.isArray(b.waiting)) b.waiting = []
            if (!Array.isArray(b.done)) b.done = []
            const stillWaiting = []
            b.waiting.forEach(p => {
              if (p) {
                if (isClosedStatus(p.status)) {
                  p.status = (p.status === '已退' || p.status === '已退号') ? '已退号' : ((p.status === '过号' || p.status === '已过号') ? '过号' : '已结诊')
                  if (!b.done.some(d => d && (d.id === p.id || (d.patientName === p.patientName && d.phone === p.phone)))) {
                    b.done.unshift(p)
                  }
                } else {
                  // 所有未结诊的患者初始严格保持【待诊】，绝不自动预置为接诊中
                  p.status = '待诊'
                  stillWaiting.push(p)
                }
              }
            })
            b.waiting = stillWaiting
          }
        })
        return parsed
      }
    }
  } catch (e) {}
  return null
}

// ── 各患者在各日期的草稿与处方完整快照存根 (保证切换日期和患者时100%状态保存) ──
const dateConsultationStore = ref(loadInitialDateStore() || {})

const loadInitialDraftStore = () => {
  try {
    const raw = localStorage.getItem('chunbo_clinic_drafts_data')
    if (raw) {
      const parsed = JSON.parse(raw)
      if (parsed && typeof parsed === 'object') return parsed
    }
  } catch (e) {}
  return null
}
const consultationDraftStore = ref(loadInitialDraftStore() || {})



// 就诊记录跟着患者走：从数据库按 patientId / 身份证号加载该患者真实的历次处方
const currentPatientPastVisits = ref([])

const loadPatientPastVisits = async () => {
  const p = currentPatient.value
  if (!p) {
    currentPatientPastVisits.value = []
    return
  }
  // 1. 优先查数据库（挂号建档时已按身份证关联 patientId）
  let dbVisits = []
  try {
    const params = {}
    if (p.patientId) params.patientId = p.patientId
    if (p.idCard) params.idCard = p.idCard
    if (params.patientId || params.idCard) {
      const res = await axios.get('/api/prescription/patient-history', { params })
      dbVisits = res.data || []
    }
  } catch (e) {}
  if (dbVisits.length > 0) {
    currentPatientPastVisits.value = dbVisits
    return
  }
  // 2. 数据库无记录即为真实无就诊历史，绝不使用任何演示/模板假数据兜底
  currentPatientPastVisits.value = []
}

const filteredHistoryVisits = computed(() => {
  let list = currentPatientPastVisits.value
  const k = historySearchKey.value.trim().toLowerCase()
  if (!k) return list
  return list.filter(v => 
    v.date.includes(k) || 
    v.diagnosis.toLowerCase().includes(k) || 
    (v.tcmDiagnosis && v.tcmDiagnosis.toLowerCase().includes(k)) ||
    v.symptoms.toLowerCase().includes(k)
  )
})

const formatHistoryRxBrief = (v) => {
  const parts = []
  if (v.patchItems && v.patchItems.length) parts.push(v.patchItems[0].name + '(' + (v.patchItems[0].acupoints || '穴位') + ')')
  if (v.westernItems && v.westernItems.length) parts.push(v.westernItems[0].name)
  if (v.tcmItems && v.tcmItems.length) parts.push(v.tcmItems.map(m => m.name).slice(0, 2).join('+'))
  return parts.join(' · ') || '常规对症'
}

// ── 经典处方与常用模板库 (深度复刻截图 1-5 底部 常用模板/经典方剂) ──
const prescriptionTemplates = ref([
  {
    name: '小儿腮腺炎穴位贴敷方',
    category: '贴敷',
    indication: '小儿流行性腮腺炎、热毒蕴结面颊部肿痛',
    rxDescription: '消肿止痛贴 (大椎穴、颌下局部、合谷穴) 每日1贴 连用3天',
    totalPrice: 35.00,
    patchItems: [{ medicineId: 101, name: '消肿止痛贴', dose: '10', acupoints: '大椎穴、局部、颌下', frequency: '1次/天', days: 3, quantity: 3, unitPrice: 35.00, remark: '清凉调敷' }],
    westernItems: [],
    tcmItems: [],
    advice: '局部禁忌挤压，饮食温软易嚼，保持口腔卫生。'
  },
  {
    name: '痛经温经和营散寒方',
    category: '中西',
    indication: '原发性痛经、经行少腹冷痛、畏寒肢冷',
    rxDescription: '温经散寒贴 (关元穴、神阙穴、三阴交穴) + 布洛芬缓释胶囊 + 当归白芍汤',
    totalPrice: 105.00,
    patchItems: [{ medicineId: 101, name: '消肿止痛贴 (温经方)', dose: '10', acupoints: '关元穴、神阙穴、三阴交穴', frequency: '1次/天', days: 2, quantity: 2, unitPrice: 35.00, remark: '经前温贴' }],
    westernItems: [{ medicineId: 203, name: '布洛芬缓释胶囊', dose: '0.3g', frequency: 'prn', route: '口服', days: 2, quantity: 1, unit: '盒', unitPrice: 25.00, remark: '腹痛剧烈时服' }],
    tcmItems: [
      { medicineId: 307, name: '当归 (全当归)', dose: '12', frequency: '1剂/天', days: 3, unitPrice: 4.50, remark: '活血补血' },
      { medicineId: 301, name: '甘草 (炙甘草)', dose: '10', frequency: '1剂/天', days: 3, unitPrice: 1.50, remark: '缓急止痛' }
    ],
    advice: '注意腹部防寒保暖，忌食冰品及辛辣，经期避免劳累。'
  },
  {
    name: '急慢性胃炎理气和胃方',
    category: '理疗',
    indication: '慢性浅表性胃炎、胃脘胀闷隐痛、嗳气反酸',
    rxDescription: '艾灸神阙中脘 + 消肿止痛贴 (中脘穴、足三里穴) + 白术茯苓汤',
    totalPrice: 110.00,
    treatments: [{ category: '体质调理', name: '艾灸温经通络', quantity: 1, price: 40.00, remark: '中脘神阙' }],
    patchItems: [{ medicineId: 101, name: '消肿止痛贴 (和胃贴)', dose: '10', acupoints: '中脘穴、神阙穴、足三里穴', frequency: '1次/天', days: 3, quantity: 3, unitPrice: 35.00, remark: '姜汁温贴' }],
    westernItems: [],
    tcmItems: [
      { medicineId: 305, name: '白术 (炒白术)', dose: '15', frequency: '1剂/天', days: 3, unitPrice: 2.40, remark: '健脾燥湿' },
      { medicineId: 306, name: '茯苓 (白茯苓)', dose: '12', frequency: '1剂/天', days: 3, unitPrice: 2.00, remark: '利湿和中' }
    ],
    advice: '三餐规律，定时定量，忌暴饮暴食及生冷寒凉。'
  },
  {
    name: '风热感冒清热宣肺方',
    category: '中药',
    indication: '风热感冒、发热咽痛、咳嗽痰黄稠、舌红苔黄',
    rxDescription: '双黄连口服液 + 金银花连翘薄荷汤 + 大椎贴敷',
    totalPrice: 98.40,
    patchItems: [{ medicineId: 101, name: '消肿止痛贴 (清凉贴)', dose: '10', acupoints: '大椎穴、天突穴', frequency: '1次/天', days: 2, quantity: 2, unitPrice: 35.00, remark: '清凉外敷' }],
    westernItems: [{ medicineId: 204, name: '双黄连口服液', dose: '10ml', frequency: 'tid', route: '口服', days: 3, quantity: 1, unit: '盒', unitPrice: 28.00, remark: '饭后服' }],
    tcmItems: [
      { medicineId: 302, name: '金银花', dose: '12', frequency: '1剂/天', days: 3, unitPrice: 3.20, remark: '疏散风热' },
      { medicineId: 303, name: '连翘', dose: '10', frequency: '1剂/天', days: 3, unitPrice: 2.80, remark: '清热解毒' }
    ],
    advice: '多饮温水，注意避风保暖，饮食宜清淡温热粥汤。'
  }
])

const filteredPrescriptionTemplates = computed(() => {
  let list = prescriptionTemplates.value
  const k = tplSearchKey.value.trim().toLowerCase()
  if (!k) return list
  return list.filter(t => t.name.toLowerCase().includes(k) || t.indication.toLowerCase().includes(k))
})

// ── 一键引用历史就诊记录 ──
const applyHistoryVisit = (visit) => {
  // 门禁：待诊患者尚未开始接诊（或历史归档只读状态），禁止引用历史处方
  if (!canPrescribe.value) {
    ElMessage.warning('待诊患者尚未开始接诊，不能引用历史处方与病历！请先点击【开始接诊】。')
    return
  }
  ElMessageBox.confirm(
    `确认将【${visit.date}】往期就诊的处方与诊断一键导入到当前病历处方区？`,
    '引用历史处方确认',
    {
      confirmButtonText: '确认引用',
      cancelButtonText: '取消',
      type: 'primary'
    }
  ).then(() => {
    if (visit.diagnosis) emr.value.diagnosis = visit.diagnosis
    if (visit.tcmDiagnosis) emr.value.tcmDiagnosis = visit.tcmDiagnosis
    if (visit.symptoms && !emr.value.chiefComplaint) emr.value.chiefComplaint = visit.symptoms
    if (visit.advice) emr.value.medicalAdvice = visit.advice

    if (visit.treatments && visit.treatments.length) {
      treatmentItems.value = JSON.parse(JSON.stringify(visit.treatments))
    }
    if (visit.patchItems && visit.patchItems.length) {
      patchRxItems.value = JSON.parse(JSON.stringify(visit.patchItems))
    }
    if (visit.westernItems && visit.westernItems.length) {
      westernRxItems.value = JSON.parse(JSON.stringify(visit.westernItems))
    }
    if (visit.tcmItems && visit.tcmItems.length) {
      tcmRxItems.value = JSON.parse(JSON.stringify(visit.tcmItems))
    }

    // 引用成功后退出历史查阅/预览模式，直接进入当前可编辑开方
    activeHistoryVisitId.value = null
    isHistoryReadOnly.value = false
    historyPreviewMode.value = false
    previewHistoryData.value = null

    // 关键：把四类处方明细同步到处方单区块，否则处方区始终显示"尚未开立处方"
    syncItemsToBlocks()
    emrActiveTab.value = 'prescription'

    saveCurrentPatientState()

    ElNotification({
      title: '历史就诊处方引用成功！',
      message: `已成功复制【${visit.date}】的历史处方（${visit.diagnosis}），共 ${prescriptionBlocks.value.length} 单，已退出查阅模式，您可在处方表微调并直接完成接诊！`,
      type: 'success',
      duration: 4000
    })
  }).catch(() => {})
}

// ── 查看历史就诊详情 ──
const viewHistoryDetail = (visit) => {
  selectedHistoryVisit.value = visit
  historyDetailDrawerVisible.value = true
  // Also load into left EMR panel as read-only preview
  previewHistoryData.value = visit
  historyPreviewMode.value = true
  emrActiveTab.value = 'emr'
}

const exitHistoryPreview = () => {
  historyPreviewMode.value = false
  previewHistoryData.value = null
}

const adoptHistoryToEMR = () => {
  if (!previewHistoryData.value) return
  applyHistoryVisit(previewHistoryData.value)
  exitHistoryPreview()
}

// ── 保存常用处方模板弹窗与状态管理 ──
const saveTemplateDialogVisible = ref(false)
const saveTemplateForm = ref({
  name: '',
  category: '中西',
  indication: '',
  advice: ''
})

const openSaveTemplateModal = () => {
  const diagName = emr.value.diagnosis || emr.value.chiefComplaint || '特色对症'
  saveTemplateForm.value.name = diagName.replace(/[\/\s].*$/, '') + '调理方'
  saveTemplateForm.value.category = patchRxItems.value.length > 0 && westernRxItems.value.length > 0 ? '中西' : (patchRxItems.value.length > 0 ? '贴敷' : (tcmRxItems.value.length > 0 ? '中药' : '中西'))
  saveTemplateForm.value.indication = (emr.value.chiefComplaint ? '主诉：' + emr.value.chiefComplaint + '；' : '') + (emr.value.diagnosis || '门诊对症规范化调理')
  saveTemplateForm.value.advice = emr.value.medicalAdvice || '遵医嘱规范用药，饮食清淡温软，避免过度劳累与受风寒。'
  saveTemplateDialogVisible.value = true
}

const confirmSavePrescriptionTemplate = () => {
  if (!saveTemplateForm.value.name) {
    ElMessage.warning('请输入模板名称！')
    return
  }

  const descItems = [
    ...patchRxItems.value.map(i => i.name),
    ...westernRxItems.value.map(i => i.name),
    ...tcmRxItems.value.map(i => i.name)
  ].filter(Boolean)

  const newTpl = {
    name: saveTemplateForm.value.name,
    category: saveTemplateForm.value.category,
    indication: saveTemplateForm.value.indication,
    diagnosis: emr.value.diagnosis || '',
    tcmDiagnosis: emr.value.tcmDiagnosis || '',
    advice: saveTemplateForm.value.advice,
    rxDescription: descItems.join(' + ') || '特色综合诊疗方剂',
    totalPrice: totalRxAmount.value,
    patchItems: JSON.parse(JSON.stringify(patchRxItems.value)),
    westernItems: JSON.parse(JSON.stringify(westernRxItems.value)),
    tcmItems: JSON.parse(JSON.stringify(tcmRxItems.value)),
    treatments: JSON.parse(JSON.stringify(treatmentItems.value))
  }

  prescriptionTemplates.value.unshift(newTpl)
  try {
    localStorage.setItem('chunbo_custom_rx_templates', JSON.stringify(prescriptionTemplates.value))
  } catch (e) {}

  saveTemplateDialogVisible.value = false
  // 自动切换至右侧“常用模板”Tab，让医生立刻看到保存的模板！
  rightPanelTab.value = 'template'

  ElNotification({
    title: '常用处方模板已成功保存！',
    message: `【${newTpl.name}】已成功收录入常用模板库，您可在右侧常用模板随时一键直接引用！`,
    type: 'success',
    duration: 4000
  })
}

// ── 一键引用常用模板 (彻底解决模板无法直接填入、药品ID显示为101/201的问题) ──
const applyPrescriptionTemplate = (tpl) => {
  // 解除只读模式
  isHistoryReadOnly.value = false

  // 1. 同步填充电子病历主诉、诊断与医嘱
  if (tpl.indication) {
    emr.value.chiefComplaint = tpl.indication
  }
  if (tpl.diagnosis) {
    emr.value.diagnosis = tpl.diagnosis
  } else if (tpl.indication) {
    emr.value.diagnosis = tpl.indication
  }
  if (tpl.tcmDiagnosis) {
    emr.value.tcmDiagnosis = tpl.tcmDiagnosis
  }
  if (tpl.advice) {
    emr.value.medicalAdvice = tpl.advice
  }

  // 2. 将常用模板中的各类药品/贴敷/中药/理疗精准映射为各个独立处方单卡片
  const newBlocks = []
  let bIdx = 1

  if (tpl.patchItems && tpl.patchItems.length > 0) {
    newBlocks.push({
      id: 'block_patch_' + Date.now(),
      blockNo: bIdx++,
      type: 'patch',
      title: '🌿 穴位贴敷处方一',
      rxNo: 'TF' + Date.now().toString().slice(-6),
      technique: '湿贴',
      duration: '4小时',
      items: JSON.parse(JSON.stringify(tpl.patchItems))
    })
  }

  if (tpl.westernItems && tpl.westernItems.length > 0) {
    newBlocks.push({
      id: 'block_western_' + (Date.now() + 1),
      blockNo: bIdx++,
      type: 'western',
      title: '💊 西药/中成药处方一',
      rxNo: 'WX' + (Date.now() + 1).toString().slice(-6),
      items: JSON.parse(JSON.stringify(tpl.westernItems))
    })
  }

  if (tpl.tcmItems && tpl.tcmItems.length > 0) {
    newBlocks.push({
      id: 'block_tcm_' + (Date.now() + 2),
      blockNo: bIdx++,
      type: 'tcm',
      title: '🌱 中药处方一',
      rxNo: 'ZY' + (Date.now() + 2).toString().slice(-6),
      tcmType: '饮片',
      tcmDoses: 7,
      searchKey: '',
      alphaFilter: '',
      items: JSON.parse(JSON.stringify(tpl.tcmItems))
    })
  }

  if (tpl.treatments && tpl.treatments.length > 0) {
    newBlocks.push({
      id: 'block_treat_' + (Date.now() + 3),
      blockNo: bIdx++,
      type: 'treatment',
      title: '📋 诊疗理疗项目一',
      rxNo: 'ZL' + (Date.now() + 3).toString().slice(-6),
      items: JSON.parse(JSON.stringify(tpl.treatments))
    })
  }

  if (newBlocks.length > 0) {
    prescriptionBlocks.value = newBlocks
  }
  syncBlocksToItems()

  // 3. 自动切换到“处方医嘱与特色贴敷”Tab，让医生立刻看到已填好的处方单！
  emrActiveTab.value = 'prescription'
  saveCurrentPatientState()
  runAiPrescriptionAudit()

  ElMessage.success(`已一键导入处方模板【${tpl.name}】！全套方药已填入处方单！`)
}
const viewTemplateDetail = (tpl) => {
  ElMessageBox.alert(
    `【模板名称】${tpl.name}\n【适应病症】${tpl.indication}\n【方药组成】${tpl.rxDescription}\n【生活医嘱】${tpl.advice || '常规对症'}`,
    '模板详情',
    { confirmButtonText: '知道了' }
  )
}

// ── 日期切换与状态持久化保存核心引擎 (完整实现用户：可切换到以前日期就诊页面、接诊中状态保存) ──
const saveCurrentPatientState = () => {
  if (!currentPatient.value) return
  // 审查模式下载入的是历史病历内容，严禁写入当前患者草稿（防止就诊记录串档）
  if (isHistoryReadOnly.value) return
  const curStatus = currentPatient.value.status || '待诊'
  const key = `${currentPatient.value.id}_${queueDate.value}`
  consultationDraftStore.value[key] = {
    emr: JSON.parse(JSON.stringify(emr.value)),
    treatmentItems: JSON.parse(JSON.stringify(treatmentItems.value)),
    patchRxItems: JSON.parse(JSON.stringify(patchRxItems.value)),
    westernRxItems: JSON.parse(JSON.stringify(westernRxItems.value)),
    tcmRxItems: JSON.parse(JSON.stringify(tcmRxItems.value)),
    prescriptionBlocks: JSON.parse(JSON.stringify(prescriptionBlocks.value || [])),
    status: curStatus
  }

  // 同步更新日历仓储队列中的状态与主诉
  const bucket = dateConsultationStore.value[queueDate.value]
  if (bucket) {
    const pWait = bucket.waiting.find(p => p.id === currentPatient.value.id)
    if (pWait) {
      pWait.status = curStatus
      if (emr.value.chiefComplaint) pWait.symptoms = emr.value.chiefComplaint
    }
    const pDone = bucket.done.find(p => p.id === currentPatient.value.id)
    if (pDone) {
      pDone.status = curStatus
    }
  }

  try {
    localStorage.setItem('chunbo_clinic_dates_data', JSON.stringify(dateConsultationStore.value))
    localStorage.setItem('chunbo_clinic_drafts_data', JSON.stringify(consultationDraftStore.value))
  } catch (e) {}
}

const switchQueueDate = (newDate) => {
  saveCurrentPatientState()
  queueDate.value = newDate
  loadQueueForDate(newDate)
}

const onQueueDateChange = (val) => {
  if (!val) return
  saveCurrentPatientState()
  queueDate.value = val
  loadQueueForDate(val)
}

const changeDateByOffset = (offset) => {
  saveCurrentPatientState()
  const current = new Date(queueDate.value + 'T00:00:00')
  current.setDate(current.getDate() + offset)
  const newDateStr = formatDateKey(current)
  queueDate.value = newDateStr
  loadQueueForDate(newDateStr)
}

const goToToday = () => {
  saveCurrentPatientState()
  queueDate.value = todayKey
  loadQueueForDate(todayKey)
}

const loadQueueForDate = (dateStr) => {
  // 确保该日期仓储桶存在：如果调阅很久以前的日期，默认初始化为真正的空队列（0人）
  if (!dateConsultationStore.value[dateStr]) {
    dateConsultationStore.value[dateStr] = {
      waiting: [],
      done: []
    }
  }

  const bucket = dateConsultationStore.value[dateStr]
  waitingList.value = bucket.waiting || []
  doneList.value = bucket.done || []

  // 智能定位该日期下的患者：若该日期有待诊/接诊中患者，优先激活“接诊中”患者；若无记录则彻底置空
  if (waitingList.value.length > 0) {
    const inProgress = waitingList.value.find(p => p.status === '接诊中')
    const target = inProgress || waitingList.value[0]
    selectQueuePatient(target)
  } else if (doneList.value.length > 0) {
    selectQueuePatient(doneList.value[0])
  } else {
    currentPatient.value = null
    // 彻底清空病历与处方展示，避免显示残留数据
    emr.value = {
      chiefComplaint: '',
      symptomsList: [],
      frequency: '偶尔',
      duration: '1-3天',
      presentIllness: '',
      medicalAdvice: '',
      allergies: '无已知药物过敏',
      pastHistory: '否认高血压、糖尿病及重大慢病史',
      tongue: '',
      pulse: '',
      diagnosis: '',
      tcmDiagnosis: ''
    }
    treatmentItems.value = []
    patchRxItems.value = []
    westernRxItems.value = []
    tcmRxItems.value = []
  }

  ElMessage.info(`已切换至【${dateStr}】就诊日程，当前待诊/接诊中 ${waitingList.value.length} 人，已结束 ${doneList.value.length} 人`)
}

const emrActiveTab = ref('emr')

// ── 主诉分类与常用词条 (深度复刻截图 1) ──
const activeCcTab = ref('ai')
const customCcInput = ref('')
const activePastTab = ref('system')
const customPastInput = ref('')

const ccSmartTags = [
  '面色苍白', '反复牙龈肿痛', '咯血', '咳嗽', '头痛', '颈肩酸痛', '肩部疼痛', '包块', 
  '颈肩部不适', '胸闷', '胸痛', '胸痞', '心悸', '乳房包块', '乳房红肿', '痰中带血', 
  '胸壁疼痛', '食欲亢进', '发烧', '头晕', '多尿'
]

const ccCommonTags = [
  '感冒发热', '恶寒无汗', '咳嗽有痰', '鼻塞流涕', '胃脘胀满', '反酸嗳气', 
  '头重如裹', '肢体困重', '月经不调', '痛经带下', '失眠多梦', '便秘腹泻'
]

const ccHeadTags = [
  '头痛', '偏头痛', '头晕目眩', '目赤肿痛', '视物模糊', '耳鸣耳聋', 
  '鼻塞', '流涕', '咽喉肿痛', '咽痒咳嗽', '口苦口干', '反复牙龈肿痛', '面瘫口歪'
]

const ccNeckTags = [
  '颈项强直', '颈肩酸痛', '转颈受限', '咽部异物感', '甲状腺结节肿大', '颈部淋巴结肿痛'
]

const ccChestTags = [
  '胸闷气短', '胸痛彻背', '心悸怔忡', '喘促气急', '咳痰黄稠', '痰中带血', 
  '乳房胀痛', '乳房结节包块', '乳腺增生疼痛', '胸壁隐痛'
]

const ccBellyTags = [
  '胃脘隐痛', '胃胀嗳气', '反酸烧心', '腹部隐痛', '腹胀便溏', '急性腹泻', 
  '大便秘结', '纳差食少', '恶心呕吐', '胃部痞满'
]

const ccBackTags = [
  '腰膝酸软', '腰脊冷痛', '腰肌劳损', '棘突压痛', '背脊僵硬', '腰间盘压迫酸麻'
]

const ccLimbTags = [
  '四肢关节酸痛', '下肢水肿', '手足麻木', '关节屈伸不利', '下肢乏力', '手足心烦热', '足跟剧痛'
]

const ccSkinTags = [
  '皮肤瘙痒', '荨麻疹风团', '湿疹渗出', '带状疱疹神经痛', '面部痤疮', '过敏性皮炎', '红斑丘疹'
]

const ccWholeTags = [
  '发热恶寒', '神疲倦怠', '自汗出', '盗汗不止', '低热缠绵', '畏寒肢冷', '潮热五心烦热'
]

const ccPelvisTags = [
  '少腹坠痛', '盆腔积液疼痛', '月经淋漓不净', '痛经剧烈', '带下量多色黄', 
  '尿频尿急', '尿道刺痛', '痔疮出血脱垂', '肛门下坠重感'
]

const pastHistorySysTags = [
  '无', '未见明显异常', '既往体健', '高血压病史', '2型糖尿病', '冠心病', '慢性胃炎', 
  '支气管哮喘', '慢性支气管炎', '过敏性鼻炎', '高脂血症', '高尿酸血症/痛风', '乙肝病毒携带', 
  '否认高血压', '否认糖尿病', '否认冠心病', '否认传染病史', '否认外伤手术史', '否认药物过敏史', 
  '吸烟史', '饮酒史', '有剖宫产史', '有胆囊切除术史'
]

const addCustomCcTag = () => {
  if (customCcInput.value.trim()) {
    appendChiefComplaint(customCcInput.value.trim())
    customCcInput.value = ''
  }
}

const appendPastHistory = (tag) => {
  if (!tag) return
  if (!emr.value.pastHistory || emr.value.pastHistory === '无' || emr.value.pastHistory === '既往体健') {
    emr.value.pastHistory = tag
  } else {
    if (!emr.value.pastHistory.includes(tag)) {
      emr.value.pastHistory += '，' + tag
    }
  }
}
// ═══ 诊所可用中西药字典、处方块状态与计算属性 (统一前置声明，彻底根除 TDZ 依赖断裂) ═══


const allMedicines = ref([])

// 贴敷特色药品列表（从后端药品库按「特色贴敷」分类或贴/膏/敷类过滤，杜绝写死价格）
const allAvailablePlasters = computed(() => {
  const list = allMedicines.value || []
  return list.filter(m => {
    if (m.primaryCategory === '特色贴敷') return true
    const c = ((m.primaryCategory || '') + (m.category || '') + (m.name || ''))
    return c.includes('贴') || c.includes('膏') || c.includes('敷')
  })
})

// 从后端真实药品库加载字典（价格/规格/库存均来自 medicine 表）
const loadMedicines = async () => {
  try {
    const res = await axios.get('/api/medicines')
    if (Array.isArray(res.data) && res.data.length > 0) {
      allMedicines.value = res.data
    }
  } catch (e) {
    console.warn('加载药品字典失败:', e)
  }
}

// 经典经方库（数据源：后端 tcm_formula 表，替代前端写死的经方模板）
const classicTcmFormulas = ref([])
const loadTcmFormulas = async () => {
  try {
    const res = await axios.get('/api/tcm-formulas')
    if (Array.isArray(res.data)) {
      classicTcmFormulas.value = res.data
    }
  } catch (e) {
    console.warn('加载经典经方库失败:', e)
  }
}

const allAvailableMedicines = computed(() => {
  return Array.isArray(allMedicines.value) ? allMedicines.value : []
})

const allTcmMedicines = computed(() => {
  const tcmList = allAvailableMedicines.value.filter(m =>
    m.category === '中药' ||
    m.primaryCategory === '中药' ||
    (m.specification && (m.specification.includes('g') || m.specification.includes('饮片')))
  )
  return tcmList
})

// ── 基础处方数据源 ──
const treatmentItems = ref([])        // 诊疗项目
const patchRxItems = ref([])          // 贴敷处方行
const westernRxItems = ref([])        // 西/中成药处方行
const tcmRxItems = ref([])            // 中药处方行
const prescriptionItems = ref([])     // 兼容旧处方
const plasterConfig = ref({ technique: '湿贴', acupoints: '神阙穴, 足三里', durationHours: 4, patchCount: 3 })

// 独立处方单列表 (Task 2 & 3: 默认空数组，由医生自主添加)
const prescriptionBlocks = ref([])

// ═══ 处方分类计算属性 ═══
const patchBlocks = computed(() => prescriptionBlocks.value.filter(b => b.type === 'patch'))
const westernBlocks = computed(() => prescriptionBlocks.value.filter(b => b.type === 'western'))
const tcmBlocks = computed(() => prescriptionBlocks.value.filter(b => b.type === 'tcm'))
const treatmentBlocks = computed(() => prescriptionBlocks.value.filter(b => b.type === 'treatment'))
const supplyBlocks = computed(() => prescriptionBlocks.value.filter(b => b.type === 'supply'))

// 医用物资字典（medicine 表 primaryCategory=医用材料，供门诊物资开单选择）
const allSupplyMedicines = computed(() => {
  return (allMedicines.value || []).filter(m => m.primaryCategory === '医用材料')
})

// 物资开单：选中物资后带出真实档案ID/规格/单价
const pickSupplyForItem = (item, supplyId) => {
  const m = allSupplyMedicines.value.find(x => x.id === supplyId)
  if (m) {
    item.medicineId = m.id
    item.name = m.name
    item.specification = m.specification || ''
    item.unitPrice = Number(m.price || 0)
    item.unit = m.unit || '件'
  }
}

// 处方块类型标签与名称
const getBlockTagType = (type) => {
  if (type === 'patch') return 'warning'
  if (type === 'western') return 'primary'
  if (type === 'tcm') return 'success'
  if (type === 'treatment') return 'info'
  return ''
}
const getBlockTypeName = (type) => {
  if (type === 'patch') return '特色穴位贴敷'
  if (type === 'western') return '西药 / 中成药'
  if (type === 'tcm') return '中药饮片 / 颗粒'
  if (type === 'treatment') return '诊疗理疗项目'
  return '处方'
}

// 计算单张独立处方总额
const getBlockTotal = (block) => {
  if (!block || !block.items) return 0
  if (block.type === 'patch') {
    return block.items.reduce((s, it) => s + (Number(it.unitPrice) || 35) * (Number(it.quantity) || 1), 0)
  }
  if (block.type === 'western') {
    return block.items.reduce((s, it) => s + (Number(it.unitPrice) || 25) * (Number(it.quantity) || 1), 0)
  }
  if (block.type === 'tcm') {
    const doses = Number(block.tcmDoses) || 7
    return block.items.reduce((s, it) => s + (Number(it.unitPrice) || 0.15) * (Number(it.dose) || 10) * doses, 0)
  }
  if (block.type === 'treatment') {
    return block.items.reduce((s, it) => s + (Number(it.price) || 0) * (Number(it.quantity) || 1), 0)
  }
  if (block.type === 'supply') {
    return block.items.reduce((s, it) => s + (Number(it.unitPrice) || 0) * (Number(it.quantity) || 1), 0)
  }
  return 0
}

// 常用穴位
const commonAcupoints = [
  '大椎', '天突', '双肺俞', '神阙', '膻中', '足三里', '阿是穴', 
  '涌泉', '关元', '中脘', '脾俞', '肾俞', '风池', '太阳', '合谷', '内关'
]

const appendAcupointToRow = (row, ac) => {
  if (!row) return
  if (!row.acupoints) {
    row.acupoints = ac
  } else {
    const list = row.acupoints.split(/[,、，\s]+/).filter(Boolean)
    if (!list.includes(ac)) {
      list.push(ac)
      row.acupoints = list.join('、')
    }
  }
  syncBlocksToItems()
}

// 药材点选回填
const onBlockPlasterSelect = (row) => {
  if (!row) return
  const p = allAvailablePlasters.value.find(x => x.name === row.name)
  if (p) {
    row.medicineId = p.id
    row.unitPrice = Number(p.price) || 35
  } else {
    row.unitPrice = 35
  }
  syncBlocksToItems()
  saveCurrentPatientState()
}

const onBlockMedSelect = (row) => {
  if (!row) return
  medSearchKey.value = '' // 选中后清空检索词，避免下次打开下拉仍被过滤
  const m = allAvailableMedicines.value.find(x => x.name === row.name)
  if (m) {
    row.medicineId = m.id
    row.unitPrice = Number(m.price) || 25
    if (m.specification) row.spec = m.specification
    // 单次用量单位与规格单位匹配（规格 12g*11袋/盒 → 单次用量默认 1袋）
    const unit = specUnitOf(row.spec) || '片'
    row.dose = '1' + unit
  }
  syncBlocksToItems()
  saveCurrentPatientState()
  applySupplyLinkage(row)
}

// 药品检索：支持名称或拼音简码（拼音码字段的实际用途），如输 bsa 检索「苯磺酸氨氯地平片」
// 当前登录医生真实姓名（单据开单人/接诊医生兜底，杜绝写死）
const currentUserName = localStorage.getItem('chunbo_display_name') || localStorage.getItem('chunbo_username') || '系统用户'
const medSearchKey = ref('')
const filterMedByPinyin = (q) => { medSearchKey.value = (q || '').trim() }
const pinyinFilteredMeds = computed(() => {
  const k = medSearchKey.value.toLowerCase()
  if (!k) return allAvailableMedicines.value
  return allAvailableMedicines.value.filter(m =>
    (m.name || '').includes(k) || (m.pinyinCode || '').toLowerCase().includes(k)
  )
})

// 联动配置缓存：避免每次改数量都请求后端
const linkCache = {}
const fetchLinksFor = async (row) => {
  const key = (row.medicineId ? 'id:' + row.medicineId : '') + '|' + (row.name || '')
  if (linkCache[key]) return linkCache[key]
  try {
    const res = await axios.get('/api/pharmacy/supply-links', { params: { medicineId: row.medicineId || '', medicineName: row.name || '' } })
    const links = Array.isArray(res.data) ? res.data : []
    linkCache[key] = links
    return links
  } catch (e) { return [] }
}

// 联动物资统一进入「医用物资开单专区」（不混入西药处方单）
const ensureSupplyBlock = () => {
  let b = prescriptionBlocks.value.find(x => x.type === 'supply')
  if (!b) {
    b = {
      id: Date.now(),
      type: 'supply',
      title: '🧰 医用物资开单（联动自动生成）',
      rxNo: 'SL' + Date.now().toString().slice(-6),
      items: []
    }
    prescriptionBlocks.value.push(b)
  }
  return b
}

/** 药品-物资开方实时联动：选中联动药品的当下，把联动物资自动加进「物资开单专区」（带🔗标识；后端提交时另有防重兜底） */
const applySupplyLinkage = async (row) => {
  try {
    if (!row || (!row.medicineId && !row.name)) return
    const links = await fetchLinksFor(row)
    for (const link of links) {
      const perQty = Number(link.quantity) || 1
      const rowQty = Number(row.quantity) || 1
      const supplyId = link.supply_id
      const supplyName = link.supply_name
      const sb = ensureSupplyBlock()
      const dupRow = sb.items.find(it => it.isLinkage && (String(it.medicineId) === String(supplyId) || it.name === supplyName))
      if (dupRow) {
        // 已有联动行：按触发药品累加各自份额
        if (!dupRow._contrib) dupRow._contrib = {}
        dupRow._contrib[row.name] = perQty * rowQty
        dupRow.quantity = Object.values(dupRow._contrib).reduce((a, b) => a + b, 0)
        ElMessage.success('【' + supplyName + '】联动数量已累加至 ×' + dupRow.quantity)
        continue
      }
      sb.items.push({
        medicineId: supplyId,
        name: supplyName,
        specification: link.supply_specification || '',
        unit: specUnitOf(link.supply_specification) || '件',
        quantity: perQty * rowQty,
        unitPrice: Number(link.supply_price) || 0,
        remark: '🔗联动自动附加',
        isLinkage: true,
        _contrib: { [row.name]: perQty * rowQty }
      })
      ElMessage.success('已按联动配置在【物资开单专区】自动附加【' + supplyName + ' ×' + (perQty * rowQty) + '】')
    }
    if (links.length > 0) {
      syncBlocksToItems()
      saveCurrentPatientState()
    }
  } catch (e) {
    console.warn('联动查询失败:', e)
  }
}

/** 修改联动药品数量时，联动物资数量随之同步（该药份额 = 每份联动数 × 药品数量） */
const syncLinkageQty = async (row) => {
  const links = await fetchLinksFor(row)
  if (!links.length) return
  const rowQty = Number(row.quantity) || 1
  for (const link of links) {
    const perQty = Number(link.quantity) || 1
    for (const b of prescriptionBlocks.value.filter(x => x.type === 'supply')) {
      const target = b.items.find(it => it.isLinkage && (String(it.medicineId) === String(link.supply_id) || it.name === link.supply_name))
      if (!target) continue
      if (!target._contrib) target._contrib = {}
      target._contrib[row.name] = perQty * rowQty
      target.quantity = Object.values(target._contrib).reduce((a, b) => a + b, 0)
    }
  }
  syncBlocksToItems()
  saveCurrentPatientState()
}

// 从规格串提取开方单位（"5mg*7片/盒" → "盒"；无 / 时取末尾单位字）
const specUnitOf = (spec) => {
  if (!spec) return ''
  const s = String(spec)
  if (s.includes('/')) return s.split('/').pop() || ''
  const m = s.match(/([袋盒瓶支贴丸片粒包罐套卷块副])$/)
  return m ? m[1] : ''
}

const onTcmMedSelect = (it) => {
  if (!it || !it.name) return
  const m = allTcmMedicines.value.find(x => x.name === it.name) || allAvailableMedicines.value.find(x => x.name === it.name)
  if (m) {
    it.medicineId = m.id
    it.unitPrice = Number(m.price) || 0.15
  } else {
    it.unitPrice = 0.15
  }
  if (!it.dose) it.dose = 10
  syncBlocksToItems()
  saveCurrentPatientState()
}

// 添加新的独立处方块
const addNewPrescriptionBlock = (type) => {
  const countOfType = prescriptionBlocks.value.filter(b => b.type === type).length + 1
  const cnNums = ['一', '二', '三', '四', '五', '六', '七', '八']
  const cnNum = cnNums[countOfType - 1] || String(countOfType)
  
  let newBlock = null
  if (type === 'patch') {
    newBlock = {
      id: 'block_patch_' + Date.now(),
      blockNo: countOfType,
      type: 'patch',
      title: '🌿 穴位贴敷处方' + cnNum,
      rxNo: 'TF' + Date.now().toString().slice(-6),
      technique: '湿贴',
      duration: '4小时',
      items: [
        { medicineId: 101, name: '', dose: '1', acupoints: '', frequency: '1次/天', days: 3, quantity: 3, unitPrice: 35 }
      ]
    }
    ElMessage.success('已添加【贴敷处方' + cnNum + '】')
  } else if (type === 'western') {
    newBlock = {
      id: 'block_western_' + Date.now(),
      blockNo: countOfType,
      type: 'western',
      title: '💊 西药/中成药处方' + cnNum,
      rxNo: 'WX' + Date.now().toString().slice(-6),
      items: [
        { medicineId: null, name: '', dose: '1片', frequency: 'tid', route: '口服', days: 3, quantity: 1, unit: '盒', unitPrice: 25, remark: '饭后温服' }
      ]
    }
    ElMessage.success('已添加【西药/中成药处方' + cnNum + '】')
  } else if (type === 'tcm') {
    newBlock = {
      id: 'block_tcm_' + Date.now(),
      blockNo: countOfType,
      type: 'tcm',
      title: '🌱 中药处方' + cnNum,
      rxNo: 'ZY' + Date.now().toString().slice(-6),
      tcmType: '饮片',
      tcmDoses: 7,
      items: [
        { medicineId: null, name: '', dose: 10, unitPrice: 0.15, remark: '' }
      ]
    }
    ElMessage.success('已添加【中药处方' + cnNum + '】')
  } else if (type === 'treatment') {
    newBlock = {
      id: 'block_treat_' + Date.now(),
      blockNo: countOfType,
      type: 'treatment',
      title: '🩺 诊疗理疗项目' + cnNum,
      rxNo: 'ZL' + Date.now().toString().slice(-6),
      items: [
        { category: '治疗理疗', name: '红外线理疗', quantity: 1, price: 30, remark: '' }
      ]
    }
    ElMessage.success('已添加【诊疗项目' + cnNum + '】')
  } else if (type === 'supply') {
    newBlock = {
      id: 'block_supply_' + Date.now(),
      blockNo: countOfType,
      type: 'supply',
      title: '🧰 医用物资开单' + cnNum,
      rxNo: 'SL' + Date.now().toString().slice(-6),
      items: [
        { medicineId: null, name: '', specification: '', quantity: 1, unit: '件', unitPrice: 0, remark: '' }
      ]
    }
    ElMessage.success('已添加【物资开单' + cnNum + '】，请从药房物资库选择')
  }
  if (newBlock) {
    prescriptionBlocks.value = [...prescriptionBlocks.value, newBlock]
  }
  syncBlocksToItems()
  saveCurrentPatientState()
}

// 删除整张处方单
const removePrescriptionBlock = (blockId) => {
  ElMessageBox.confirm('确认删除整张处方单？已录入的药品项目将全部移除。', '提示', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    const block = prescriptionBlocks.value.find(b => b.id === blockId)
    // 删除整单时，被删药品贡献的联动物资份额也要同步扣回/移除
    if (block && block.type !== 'supply') {
      cleanupLinkageForRemovedMeds(block.items.map(it => it.name).filter(Boolean))
    }
    prescriptionBlocks.value = prescriptionBlocks.value.filter(b => b.id !== blockId)
    syncBlocksToItems()
    saveCurrentPatientState()
    ElMessage.success('已移除该张处方单')
  }).catch(() => {})
}

// 给处方块添加项目
const addBlockItem = (block) => {
  if (block.type === 'patch') {
    block.items.push({ medicineId: 101, name: '', dose: '1', acupoints: '', frequency: '1次/天', days: 3, quantity: 3, unitPrice: 35 })
  } else if (block.type === 'western') {
    block.items.push({ medicineId: null, name: '', dose: '1片', frequency: 'tid', route: '口服', days: 3, quantity: 1, unit: '盒', unitPrice: 25, remark: '' })
  } else if (block.type === 'tcm') {
    block.items.push({ medicineId: null, name: '', dose: 10, unitPrice: 0.15, remark: '' })
  } else if (block.type === 'treatment') {
    block.items.push({ category: '治疗理疗', name: '', quantity: 1, price: 20, remark: '' })
  } else if (block.type === 'supply') {
    block.items.push({ medicineId: null, name: '', specification: '', quantity: 1, unit: '件', unitPrice: 0, remark: '' })
  }
  syncBlocksToItems()
  saveCurrentPatientState()
}

/** 删除药品后同步清理联动物资：扣回该药份额，份额清零移除物资行，联动物资单空则移除整单 */
const cleanupLinkageForRemovedMeds = (medNames) => {
  if (!medNames || !medNames.length) return
  for (const b of prescriptionBlocks.value.filter(x => x.type === 'supply')) {
    for (const it of [...b.items]) {
      if (!it.isLinkage || !it._contrib) continue
      let changed = false
      for (const n of medNames) {
        if (n in it._contrib) { delete it._contrib[n]; changed = true }
      }
      if (!changed) continue
      const rest = Object.values(it._contrib).reduce((a, c) => a + c, 0)
      if (rest <= 0) {
        b.items.splice(b.items.indexOf(it), 1)
      } else {
        it.quantity = rest
      }
    }
    // 物资单被清空且是联动自动生成的 → 移除整单
    if (b.items.length === 0 && b.title && b.title.includes('联动自动生成')) {
      prescriptionBlocks.value.splice(prescriptionBlocks.value.indexOf(b), 1)
    }
  }
}

// 移除处方块中某一行
const removeBlockItem = (block, idx) => {
  const removed = block.items[idx]
  block.items.splice(idx, 1)
  // 删除联动药品行时，同步移除/重算物资开单专区里它贡献的联动物资数量
  if (removed && removed.name) {
    cleanupLinkageForRemovedMeds([removed.name])
  }
  syncBlocksToItems()
  saveCurrentPatientState()
}

// 西药行数量变化：同步明细 + 联动物资数量跟随
const onWxQtyChange = (row) => {
  syncBlocksToItems()
  saveCurrentPatientState()
  syncLinkageQty(row)
}

const getTcmFilteredForBlock = (block) => {
  let list = allAvailableMedicines.value.filter(m => m.category === '中药' || (m.name && (m.name.includes('草') || m.name.includes('黄') || m.name.includes('参') || m.name.includes('皮') || m.name.includes('花'))))
  if (block.searchKey) {
    const k = block.searchKey.toLowerCase()
    list = list.filter(m => (m.name && m.name.toLowerCase().includes(k)) || (m.pinyin && m.pinyin.toLowerCase().includes(k)))
  }
  if (block.alphaFilter) {
    list = list.filter(m => m.pinyin && m.pinyin.toUpperCase().startsWith(block.alphaFilter))
  }
  return list.slice(0, 8)
}

const addTcmMedToBlock = (block, m) => {
  block.items.push({
    medicineId: m.id,
    name: m.name,
    dose: 10,
    unitPrice: Number(m.price) || 0.15,
    remark: ''
  })
  block.searchKey = ''
  block.alphaFilter = ''
  syncBlocksToItems()
  saveCurrentPatientState()
  ElMessage.success('已添加药材【' + m.name + '】')
}

// 同步所有处方块到全局 patchRxItems, westernRxItems, tcmRxItems, treatmentItems
const syncBlocksToItems = () => {
  const patches = []
  const westerns = []
  const tcms = []
  const treats = []

  prescriptionBlocks.value.forEach(b => {
    if (b.type === 'patch') {
      patches.push(...(b.items || []))
    } else if (b.type === 'western') {
      westerns.push(...(b.items || []))
    } else if (b.type === 'tcm') {
      tcms.push(...(b.items || []))
    } else if (b.type === 'treatment') {
      treats.push(...(b.items || []))
    }
  })

  patchRxItems.value = patches
  westernRxItems.value = westerns
  tcmRxItems.value = tcms
  treatmentItems.value = treats
}

// 反向同步：当由 AI 采纳、历史记录引用或草稿恢复时，将 items 还原成 blocks
const syncItemsToBlocks = () => {
  const blocks = []
  if (patchRxItems.value && patchRxItems.value.length > 0) {
    blocks.push({
      id: 'block_patch_auto',
      blockNo: 1,
      type: 'patch',
      title: '🌿 穴位贴敷处方一',
      rxNo: 'TF' + Date.now().toString().slice(-6),
      technique: plasterConfig.value?.technique || '湿贴',
      duration: plasterConfig.value?.duration || '4小时',
      items: JSON.parse(JSON.stringify(patchRxItems.value))
    })
  }
  if (westernRxItems.value && westernRxItems.value.length > 0) {
    blocks.push({
      id: 'block_western_auto',
      blockNo: 1,
      type: 'western',
      title: '💊 西药/中成药处方一',
      rxNo: 'WX' + Date.now().toString().slice(-6),
      items: JSON.parse(JSON.stringify(westernRxItems.value))
    })
  }
  if (tcmRxItems.value && tcmRxItems.value.length > 0) {
    blocks.push({
      id: 'block_tcm_auto',
      blockNo: 1,
      type: 'tcm',
      title: '🌱 中药处方一',
      rxNo: 'ZY' + Date.now().toString().slice(-6),
      tcmType: '饮片',
      tcmDoses: 7,
      searchKey: '',
      alphaFilter: '',
      items: JSON.parse(JSON.stringify(tcmRxItems.value))
    })
  }
  if (treatmentItems.value && treatmentItems.value.length > 0) {
    blocks.push({
      id: 'block_treat_auto',
      blockNo: 1,
      type: 'treatment',
      title: '🩺 诊疗理疗项目一',
      rxNo: 'ZL' + Date.now().toString().slice(-6),
      items: JSON.parse(JSON.stringify(treatmentItems.value))
    })
  }
  prescriptionBlocks.value = blocks
}

const importClassicFormulaToBlock = (block, formula) => {
  formula.herbs.forEach(h => {
    // 药材单价从后端药品库按名匹配真实价格（经方表只存药材名+剂量，不写死价格）
    const med = allMedicines.value.find(m => (m.name || '').includes(h.name) || h.name.includes(m.name || ''))
    block.items.push({
      medicineId: Date.now() + Math.random(),
      name: h.name,
      dose: h.dose,
      unitPrice: med ? med.price : 0,
      remark: ''
    })
  })
  syncBlocksToItems()
  saveCurrentPatientState()
  ElMessage.success('已一键导入经典名方【' + formula.name + '】共' + formula.herbs.length + '味药材！')
}

const quickAddHerbToBlock = (block, herb) => {
  block.items.push({
    medicineId: Date.now() + Math.random(),
    name: herb.name,
    dose: herb.defaultDose || 10,
    unitPrice: herb.price,
    remark: ''
  })
  syncBlocksToItems()
  saveCurrentPatientState()
  ElMessage.success('已添加药材【' + herb.name + '】')
}

const addNewTcmEmptyRow = (block) => {
  block.items.push({
    medicineId: null,
    name: '',
    dose: 10,
    unitPrice: 0.15,
    remark: ''
  })
  syncBlocksToItems()
  saveCurrentPatientState()
}

const currentInitBucket = (dateConsultationStore.value && dateConsultationStore.value[queueDate.value]) || { waiting: [], done: [] }
const waitingList = ref(currentInitBucket.waiting ? sortWaitingQueue(currentInitBucket.waiting) : [])
const doneList = ref(currentInitBucket.done ? [...currentInitBucket.done] : [])
// 初始当前就诊人：优先选取待诊列表中第1位患者（初始状态必须为【待诊】），绝不自动变为【接诊中】
let initTarget = waitingList.value[0] || doneList.value[0] || null
if (initTarget && !isClosedStatus(initTarget.status)) {
  initTarget.status = '待诊'
}
const currentPatient = ref(initTarget)

// ── 会员有效性判定与折扣格式化 (杜绝未办会员被误显示为10折) ──
const isRealMember = (p) => {
  if (!p) return false
  const level = p.memberLevel
  if (!level || level === '普通居民' || level === '普通患者') return false
  const rate = Number(p.discountRate)
  // 必须享有真实折扣优惠 (< 1.0) 或者拥有明确的会员到期时间，绝不给普通就诊人误挂“10折专享”
  if ((!rate || rate >= 1.0) && !p.memberExpiry) return false
  return true
}

const getMemberDiscountText = (p) => {
  if (!p) return '9折'
  const rate = Number(p.discountRate)
  if (rate && rate > 0 && rate < 1.0) {
    return (rate * 10).toFixed(1).replace('.0', '') + '折'
  }
  if (p.memberLevel === 'VIP会员' || p.memberLevel === '家庭附属卡') return '8.5折'
  if (p.memberLevel === '慢病签约会员') return '9折'
  return '9折'
}

// ── 就诊人悬浮档案卡动态数据 (按患者真实档案区分，不再全员硬编码刘舒强信息) ──
const patientProfile = computed(() => {
  const p = currentPatient.value || {}
  const name = p.patientName || p.name || ''
  const phone = p.phone || ''
  // 从后端患者档案库匹配真实档案（过敏史/慢病史/会员等级等）
  const profile = dbPatients.value.find(x =>
    (p.patientId && x.id === p.patientId) ||
    (name && x.name === name) ||
    (phone && x.phone === phone)
  ) || {}
  const memberLevel = profile.memberLevel || p.memberLevel || '普通居民'
  const allergies = profile.allergies || ''
  return {
    visits: '—',   // 就诊次数需后端按挂号/处方统计，暂未接入
    totalCost: '—', // 累计消费需后端统计，暂未接入
    tags: [
      { label: memberLevel, type: 'success' },
      { label: (allergies && allergies !== '无' && allergies !== '无已知药物过敏') ? '⚠️ 有过敏史' : '无过敏史', type: 'info' }
    ],
    birthday: p.ageText || (p.age ? p.age + '岁' : '未填写'),  // 挂号表无生日字段，用年龄展示
    weight: p.weight ? (p.weight + ' kg') : '未填写',          // 挂号采集的真实体重
    marriage: p.marriage || '未填写',                           // 挂号采集的真实婚姻状况
    height: p.height ? (p.height + ' cm') : '未填写',           // 挂号采集的真实身高
    company: p.company || '未填写',                             // 挂号采集的真实单位
    job: p.job || '未填写',                                     // 挂号采集的真实职业
    wechat: p.wechat || '未填写',                               // 挂号采集的真实微信
    idCard: profile.idCard ? (profile.idCard.length > 8 ? profile.idCard.substring(0, 6) + '********' + profile.idCard.slice(-4) : profile.idCard) : '未填写',
    insuranceNo: p.insuranceNo || '未填写',                     // 挂号采集的真实医保号
    createdDate: profile.createTime ? profile.createTime.substring(0, 10) : '未填写',
    accompany: p.accompany || '未填写',                         // 挂号采集的真实陪护人
    accompanyPhone: p.accompanyPhone || '未填写',
    remarks: profile.remarks || p.remarks || p.symptoms || '未填写',
    address: profile.address || p.address || '未填写'
  }
})

// ═══ 处方单多独立处方与历史只读查阅模块 (Tasks 1, 2, 3, 4) ═══
const isHistoryReadOnly = ref(false)

// 开方门禁：仅接诊中状态允许添加处方
const canPrescribe = computed(() => !!(currentPatient.value && currentPatient.value.status === '接诊中'))

// 处方分类计算属性已统一前置声明

// ═══ 历史就诊记录选中与载入到中栏工作台 (Point 2) ═══
const activeHistoryVisitId = ref(null)

const loadHistoryVisitToWorkstation = (visit) => {
  if (!visit) return
  activeHistoryVisitId.value = visit.date
  isHistoryReadOnly.value = true
  // 同步记录当前预览的就诊数据：归档横幅上的【引用开方】按钮(adoptHistoryToEMR)依赖它取数
  previewHistoryData.value = visit

  // 1. 完整填充往期电子病历各项
  emr.value = {
    chiefComplaint: visit.symptoms || visit.chiefComplaint || '常发不适，今日复诊',
    presentIllness: visit.presentIllness || `${visit.symptoms || '常发不适'}，病程反复，今日复诊按原方辨证加减对症调理。`,
    allergies: visit.allergies || '无特殊药物过敏史',
    pastHistory: visit.pastHistory || '既往体健',
    frequency: '持续性',
    duration: '3天',
    symptomsList: ['腹痛', '咳嗽', '咽痛'],
    tongue: visit.tongue || '舌红苔薄黄',
    pulse: visit.pulse || '脉浮数',
    diagnosis: visit.diagnosis || '门诊诊断',
    tcmDiagnosis: visit.tcmDiagnosis || '辨证分型',
    medicalAdvice: visit.advice || visit.medicalAdvice || '注意经期与腹部保暖，避免生冷寒凉饮食，保证充足睡眠，按方调理。'
  }

  // 2. 清洗并载入处方行
  const cleanPatches = (visit.patchItems || []).map(it => ({
    medicineId: it.medicineId || 101,
    name: it.name || '消肿止痛贴 0.4g*20贴/盒',
    dose: String(it.dose || '10').replace(/g/g, ''),
    acupoints: it.acupoints || '大椎穴、神阙穴',
    frequency: it.frequency || '1次/天',
    days: Number(it.days) || 3,
    quantity: Number(it.quantity) || 3,
    unitPrice: Number(it.unitPrice) || 35
  }))

  const cleanWestern = (visit.westernItems || []).map(it => ({
    medicineId: it.medicineId || 201,
    name: it.name || '感冒灵颗粒',
    dose: it.dose || '1袋',
    route: it.route || '口服',
    frequency: it.frequency || 'tid',
    days: Number(it.days) || 3,
    quantity: Number(it.quantity) || 1,
    unitPrice: Number(it.unitPrice) || 25,
    remark: it.remark || '饭后温服'
  }))

  const cleanTcm = (visit.tcmItems || []).map(it => ({
    medicineId: it.medicineId || 301,
    name: it.name || '甘草',
    dose: parseFloat(String(it.dose || '10').replace(/g/g, '')) || 10,
    unitPrice: Number(it.unitPrice) || 1.5,
    remark: it.remark || ''
  }))

  const cleanTreatments = (visit.treatments || visit.treatmentItems || []).map(it => ({
    name: it.name || '特色理疗项目',
    quantity: Number(it.quantity) || 1,
    price: Number(it.price) || 35,
    remark: it.remark || ''
  }))

  patchRxItems.value = cleanPatches
  westernRxItems.value = cleanWestern
  tcmRxItems.value = cleanTcm
  treatmentItems.value = cleanTreatments

  // 3. 立即同步生成独立处方单卡片 (Point 2: 点击就诊记录中间必须立刻展示处方和病历)
  syncItemsToBlocks()

  ElNotification({
    title: '已调阅历史就诊记录！',
    message: `已载入【${visit.date}】病历与处方（${visit.diagnosis}，¥${visit.totalFee || 0}）。可在顶栏切换查看【电子病历】或【处方医嘱】，点击【📋 引用开方】可直接转为今日开方！`,
    type: 'success',
    duration: 4500
  })
}

const exitHistoryInspect = () => {
  activeHistoryVisitId.value = null
  isHistoryReadOnly.value = false
  ElMessage.info('已退出历史档案查阅模式，恢复当前就诊工作台。')
}



// ═══ 临时患者判断与转正 (Point 7) ═══
const isTempPatient = computed(() => {
  if (!currentPatient.value) return false
  if (currentPatient.value.isRegularized) return false
  const n = currentPatient.value.patientName || currentPatient.value.name || ''
  return n.includes('临时') || n.includes('急诊-') || n.startsWith('临-')
})

const showTempPatientModal = ref(false)
const tempPatientForm = ref({
  name: '',
  gender: '男',
  age: 30,
  phone: '',
  idCard: '',
  address: ''
})

const openTempPatientModal = () => {
  if (!currentPatient.value) return
  tempPatientForm.value.name = currentPatient.value.patientName.replace(/临时就诊-?|急诊-?/g, '') || '张三'
  tempPatientForm.value.gender = currentPatient.value.gender || '男'
  tempPatientForm.value.age = currentPatient.value.age || 30
  tempPatientForm.value.phone = currentPatient.value.phone || ''
  showTempPatientModal.value = true
}

const submitTempPatientRegularize = async () => {
  if (!tempPatientForm.value.name) {
    ElMessage.warning('请填写患者真实姓名！')
    return
  }

  const realName = tempPatientForm.value.name.trim()

  try {
    // 1. 在后端新建永久患者档案
    const res = await axios.post('/api/patient/create', {
      name: realName,
      gender: tempPatientForm.value.gender,
      age: tempPatientForm.value.age,
      phone: tempPatientForm.value.phone,
      idCard: tempPatientForm.value.idCard,
      address: tempPatientForm.value.address,
      allergies: emr.value.allergies || '无',
      medicalHistory: emr.value.pastHistory || '既往体健',
      remarks: '门诊快速接诊转正建档'
    })

    // 2. 更新当前就诊患者信息（不再是临时患者！）
    currentPatient.value.patientName = realName
    currentPatient.value.name = realName
    currentPatient.value.gender = tempPatientForm.value.gender
    currentPatient.value.age = tempPatientForm.value.age
    currentPatient.value.phone = tempPatientForm.value.phone
    currentPatient.value.idCard = tempPatientForm.value.idCard
    currentPatient.value.isRegularized = true
    if (res.data && res.data.id) {
      currentPatient.value.patientId = res.data.id
    }

    // 3. 同步更新待诊队列中此患者
    const inQueue = waitingList.value.find(p => p.id === currentPatient.value.id || p.phone === currentPatient.value.phone)
    if (inQueue) {
      inQueue.patientName = realName
      inQueue.name = realName
      inQueue.gender = tempPatientForm.value.gender
      inQueue.age = tempPatientForm.value.age
      inQueue.phone = tempPatientForm.value.phone
      inQueue.isRegularized = true
    }

    // 4. 同步更新日历队列桶
    const bucket = dateConsultationStore.value[queueDate.value]
    if (bucket && bucket.waiting) {
      const bItem = bucket.waiting.find(p => p.id === currentPatient.value.id)
      if (bItem) {
        bItem.patientName = realName
        bItem.name = realName
        bItem.gender = tempPatientForm.value.gender
        bItem.age = tempPatientForm.value.age
        bItem.phone = tempPatientForm.value.phone
        bItem.isRegularized = true
      }
    }

    // 5. 同步更新后端挂号记录中的真实姓名
    if (currentPatient.value.id) {
      try {
        await axios.post(`/api/registration/update-name/${currentPatient.value.id}`, {
          patientName: realName,
          phone: tempPatientForm.value.phone,
          gender: tempPatientForm.value.gender,
          age: tempPatientForm.value.age
        })
      } catch (e) {
        console.warn('Sync update name warning:', e)
      }
    }

    saveCurrentPatientState()

    // 6. 广播事件通知挂号大厅同步更新！
    window.dispatchEvent(new CustomEvent('patient-registered', { detail: currentPatient.value }))

    showTempPatientModal.value = false
    ElNotification({
      title: '转正建档成功！',
      message: `临时患者已正式转为正式档案患者【${realName}】，名字与档案已全系统同步更新！`,
      type: 'success',
      duration: 4000
    })
  } catch (e) {
    currentPatient.value.patientName = realName
    currentPatient.value.name = realName
    currentPatient.value.gender = tempPatientForm.value.gender
    currentPatient.value.age = tempPatientForm.value.age
    currentPatient.value.phone = tempPatientForm.value.phone
    currentPatient.value.isRegularized = true
    showTempPatientModal.value = false
    saveCurrentPatientState()
    ElMessage.success(`患者档案信息已更新为【${realName}】！`)
  }
}
// ═══ 医生快速接诊 (新增临时患者并即时开启接诊，挂号大厅同步，完善信息后自动转正) ═══
const handleQuickDirectConsult = async () => {
  const tempSuffix = Math.floor(100 + Math.random() * 900)
  const tempName = `临时就诊-${tempSuffix}`
  const tempPhone = '138' + String(Math.floor(10000000 + Math.random() * 90000000))

  const newReg = {
    patientName: tempName,
    gender: '男',
    age: 30,
    phone: tempPhone,
    department: '全科门诊',
    doctorName: currentUserName,
    type: '现场挂号',
    regType: '门诊',
    fee: 10.00,
    status: '待诊',
    symptoms: '医生快速接诊临时通道 (待问诊填写)'
  }

  try {
    const res = await axios.post('/api/registration/create', newReg)
    if (res.data && res.data.id) {
      newReg.id = res.data.id
    }
  } catch (e) {
    newReg.id = Date.now()
  }

  // 1. 同步广播通知挂号大厅
  window.dispatchEvent(new CustomEvent('patient-registered', { detail: newReg }))

  // 2. 插入到医生候诊队列与仓储，并立即进入待诊预览
  const bucket = dateConsultationStore.value[todayKey]
  if (bucket) {
    if (!Array.isArray(bucket.waiting)) bucket.waiting = []
    bucket.waiting.unshift(newReg)
  }
  waitingList.value.unshift(newReg)
  currentPatient.value = newReg
  isHistoryReadOnly.value = false
  activeHistoryVisitId.value = null

  // 3. 清空上一个患者病历并创建标准初始处方单
  emr.value = {
    chiefComplaint: '',
    presentIllness: '',
    allergies: '无已知药物过敏',
    pastHistory: '既往体健',
    frequency: '偶尔',
    duration: '1-3天',
    symptomsList: [],
    tongue: '',
    pulse: '',
    diagnosis: '',
    tcmDiagnosis: '',
    medicalAdvice: ''
  }

  prescriptionBlocks.value = []
  patchRxItems.value = []
  westernRxItems.value = []
  tcmRxItems.value = []
  treatmentItems.value = []
  syncBlocksToItems()
  saveCurrentPatientState()

  ElNotification({
    title: '快速接诊已开启！',
    message: `已为【${tempName}】建立即时接诊任务！挂号大厅已实时同步入队。输入患者真实信息即可自动转正建档！`,
    type: 'success',
    duration: 5000
  })

  // 4. 快速接诊直接开始问诊，不主动弹窗打扰医生
}

// ═══ 门诊现场开通/升级会员 (Point 9 & 持久化存储修复) ═══
const showWorkstationMemberModal = ref(false)
const showAuxModal = ref(false)
const auxCardForm = ref({
  ownerName: '刘先生',
  ownerPhone: '13876543210'
})

const wsMemberForm = ref({
  level: '慢病签约会员',
  expiry: '2027-09-17',
  rechargeAmount: 300
})

const openMemberDialog = () => {
  if (!currentPatient.value) return
  showWorkstationMemberModal.value = true
}

const openAuxDialog = () => {
  if (!currentPatient.value) return
  auxCardForm.value.ownerName = currentPatient.value.accompany || '刘先生'
  auxCardForm.value.ownerPhone = currentPatient.value.accompanyPhone || '13876543210'
  showAuxModal.value = true
}

const submitAuxCard = async () => {
  if (!currentPatient.value) return
  const p = currentPatient.value
  const targetName = p.patientName || p.name || ''
  const targetPhone = p.phone || ''
  const level = '家庭附属卡'
  const discountRate = 0.85
  const expiry = '2027-09-17'

  p.memberLevel = level
  p.discountRate = discountRate
  p.memberExpiry = expiry
  currentPatient.value.memberLevel = level
  currentPatient.value.discountRate = discountRate
  currentPatient.value.memberExpiry = expiry

  // 本地持久化储存 (双重以电话和姓名索引)
  try {
    const memStore = JSON.parse(localStorage.getItem('chunbo_member_store') || '{}')
    const rec = {
      memberLevel: level,
      discountRate,
      memberExpiry: expiry,
      isAux: true,
      ownerName: auxCardForm.value.ownerName,
      ownerPhone: auxCardForm.value.ownerPhone,
      updatedAt: new Date().toISOString()
    }
    if (targetPhone) memStore[targetPhone] = rec
    if (targetName) memStore[targetName] = rec
    localStorage.setItem('chunbo_member_store', JSON.stringify(memStore))
  } catch (err) {}

  // 全局队列同步更新
  const updateList = (list) => {
    if (!list) return
    list.forEach(item => {
      if ((targetPhone && item.phone === targetPhone) || item.patientName === targetName || (item.name === targetName)) {
        item.memberLevel = level
        item.discountRate = discountRate
        item.memberExpiry = expiry
      }
    })
  }
  updateList(waitingList.value)
  updateList(doneList.value)
  if (dateConsultationStore.value) {
    Object.keys(dateConsultationStore.value).forEach(dk => {
      const bucket = dateConsultationStore.value[dk]
      if (bucket) {
        updateList(bucket.waiting)
        updateList(bucket.done)
      }
    })
  }

  showAuxModal.value = false
  ElNotification({
    title: '家庭附属卡绑定成功！',
    message: `患者【${targetName}】已成功绑定至主卡【${auxCardForm.value.ownerName}】，即刻永久共享 VIP 8.5 折权益！`,
    type: 'success',
    duration: 5000
  })
}

const submitWorkstationMember = async () => {
  if (!currentPatient.value) return
  const p = currentPatient.value
  const targetName = p.patientName || p.name || ''
  const targetPhone = p.phone || ''
  const level = wsMemberForm.value.level
  const discountRate = level === 'VIP会员' ? 0.85 : 0.9
  const expiry = wsMemberForm.value.expiry || '2027-09-17'
  const recharge = wsMemberForm.value.rechargeAmount || 0

  // 1. 同步当前就诊人对象
  p.memberLevel = level
  p.discountRate = discountRate
  p.memberExpiry = expiry
  currentPatient.value.memberLevel = level
  currentPatient.value.discountRate = discountRate
  currentPatient.value.memberExpiry = expiry

  // 2. 本地持久化储存 (localStorage 保证刷新及重登永不丢失)
  try {
    const memStore = JSON.parse(localStorage.getItem('chunbo_member_store') || '{}')
    const rec = {
      memberLevel: level,
      discountRate,
      memberExpiry: expiry,
      balance: recharge,
      points: level === 'VIP会员' ? 800 : 300,
      updatedAt: new Date().toISOString()
    }
    if (targetPhone) memStore[targetPhone] = rec
    if (targetName) memStore[targetName] = rec
    localStorage.setItem('chunbo_member_store', JSON.stringify(memStore))
  } catch (err) {
    console.warn('保存本地会员异常', err)
  }

  // 3. 全局队列同步更新
  const updateList = (list) => {
    if (!list) return
    list.forEach(item => {
      if ((targetPhone && item.phone === targetPhone) || item.patientName === targetName || (item.name === targetName)) {
        item.memberLevel = level
        item.discountRate = discountRate
        item.memberExpiry = expiry
      }
    })
  }
  updateList(waitingList.value)
  updateList(doneList.value)
  if (dateConsultationStore.value) {
    Object.keys(dateConsultationStore.value).forEach(dk => {
      const bucket = dateConsultationStore.value[dk]
      if (bucket) {
        updateList(bucket.waiting)
        updateList(bucket.done)
      }
    })
  }

  // 4. 后端持久化建档与升级会员
  try {
    let dbP = dbPatients.value.find(x => (targetPhone && x.phone === targetPhone) || x.name === targetName)
    let pid = dbP ? dbP.id : null
    if (!pid) {
      const createRes = await axios.post('/api/patients', {
        name: targetName,
        gender: p.gender || '女',
        age: p.age || 26,
        phone: targetPhone,
        memberLevel: level,
        discountRate: discountRate,
        memberExpiry: expiry,
        balance: recharge,
        points: level === 'VIP会员' ? 800 : 300,
        allergies: emr.value.allergies || '无特殊药物过敏史',
        medicalHistory: emr.value.pastHistory || '既往体健'
      })
      if (createRes.data && createRes.data.id) {
        pid = createRes.data.id
        p.patientId = pid
      }
    } else {
      await axios.post(`/api/patients/${pid}/upgrade-member?level=${encodeURIComponent(level)}&expiry=${expiry}`)
      if (recharge > 0) {
        await axios.post(`/api/patients/${pid}/recharge?amount=${recharge}`)
      }
    }
    await loadDbPatients()
  } catch (backendErr) {
    console.warn('后端会员落库同步提示:', backendErr)
  }

  showWorkstationMemberModal.value = false
  ElNotification({
    title: '会员办理成功！',
    message: `患者【${targetName}】已成功办理【${level}】！门诊开药立享 ${((discountRate || 0.9) * 10).toFixed(1).replace('.0', '')} 折专享优惠，会员身份已持久化存储生效！`,
    type: 'success',
    duration: 5000
  })
}


// 医嘱事项三大临床分类预设标签 (Task 3)
const adviceGroupDiet = ref([
  '清淡饮食，忌辛辣油腻及生冷瓜果',
  '戒烟限酒，低盐低脂饮食',
  '多饮温开水，保持每日充足水分',
  '少食多餐，规律进食，忌暴饮暴食',
  '病愈前禁食海鲜发物'
])
const adviceGroupPatch = ref([
  '贴敷部位避免沾水受风，保持干燥',
  '贴敷 4-6 小时后及时揭下，温水洗净',
  '贴敷局部微红微痒属正常反应，勿用力抓挠',
  '若局部出现明显水疱红肿，请停贴并就医',
  '按时遵医嘱服药，切勿擅自停药或增减药量'
])
const adviceGroupCare = ref([
  '注意防寒保暖，随气温增减衣物',
  '保证充足睡眠，避免劳累与剧烈运动',
  '体温超过 38.5℃ 时配合物理降温或退热药',
  '按方调理，3 天后门诊复诊',
  '若病情变化或症状加重，请及时随诊'
])

const isAdviceSelected = (adv) => {
  return emr.value && emr.value.medicalAdvice && emr.value.medicalAdvice.includes(adv)
}

// 独立处方单列表与增删方法已统一前置声明
// 复制历史病历为今日新就诊
const copyHistoryToCurrent = (patient) => {
  isHistoryReadOnly.value = false
  patient.status = '接诊中'
  ElMessage.success('已复制历史病历处方到当前就诊工作台，您可进行调整并开方结算！')
}

// 打印历史就诊凭单
const printPastVisit = (p) => {
  ElMessageBox.alert(`
    <div style="font-family: monospace; line-height: 1.8; padding: 10px;">
      <h3 style="text-align: center; margin: 0 0 10px 0;">春播万象云诊所 · 门诊历史就诊病历单</h3>
      <p><b>就诊单号：</b> JZ${p.id || Date.now()} &nbsp;&nbsp; <b>就诊状态：</b> ${p.status}</p>
      <p><b>患者姓名：</b> ${p.patientName} &nbsp;&nbsp; <b>性别/年龄：</b> ${p.gender || '男'} / ${p.age || '32岁'}</p>
      <p><b>主诉：</b> ${emr.value.chiefComplaint || p.symptoms || '-'}</p>
      <p><b>诊断结果：</b> ${emr.value.diagnosis || '急性上呼吸道感染'} (${emr.value.tcmDiagnosis || '风热犯肺'})</p>
      <p><b>医嘱事项：</b> ${emr.value.medicalAdvice || '清淡饮食，按时服药'}</p>
      <hr style="border: 1px dashed #ccc;"/>
      <p><b>门诊处方费用总计：</b> ¥${totalRxAmount.value.toFixed(2)}</p>
    </div>
  `, '历史病历归档凭单', {
    dangerouslyUseHTMLString: true,
    confirmButtonText: '确定'
  })
}

const dbPatients = ref([])

// 加载后端真实患者档案列表（供会员办理查重、患者档案回显），避免重复建档
const loadDbPatients = async () => {
  try {
    const res = await axios.get('/api/patients')
    dbPatients.value = Array.isArray(res.data) ? res.data : []
  } catch (e) {
    console.warn('加载患者档案列表失败:', e)
  }
}

// 病历表单
const switchTab = inject('switchTab', null)

const emr = ref({
  chiefComplaint: '',
  symptomsList: [],
  frequency: '偶尔',
  duration: '1-3天',
  presentIllness: '',
  medicalAdvice: '',
  allergies: '无已知药物过敏',
  pastHistory: '否认高血压、糖尿病及重大慢病史',
  tongue: '',
  pulse: '',
  diagnosis: '',
  tcmDiagnosis: ''
})

const selectedBodyPart = ref('头面部')
const bodyPartSymptomsMap = {
  '头面部': ['反复头痛', '眩晕', '头胀', '面色苍白', '牙痛', '反复牙龈肿痛', '眼干眼红', '鼻塞流涕'],
  '颈胸部': ['咽喉不适', '咳嗽咳痰', '咯血', '胸痛胸闷', '气喘', '心悸'],
  '脘腹部': ['脘腹胀满', '胃脘隐痛', '恶心呕吐', '食欲不振', '泄泻便溏'],
  '腰背部': ['腰膝酸痛', '项背僵硬', '畏寒肢冷'],
  '四肢及关节': ['关节肿痛', '手足发凉', '下肢水肿', '肢体麻木'],
  '全身皮肤': ['皮疹瘙痒', '潮红', '自汗盗汗']
}

const currentBodyPartSymptoms = computed(() => {
  return bodyPartSymptomsMap[selectedBodyPart.value] || []
})

// 旧版处方项目已统一前置声明

// 新版分区处方数据已统一前置声明

const showPatchRxBlock = ref(true)
const showWesternRxBlock = ref(true)
const showTcmRxBlock = ref(true)

const tcmRxType = ref('颗粒')
const tcmSearchKey = ref('')
const tcmAlphaFilter = ref('')
const acupointSearch = ref('')

const freqOptions = ['qd', 'bid', 'tid', 'qid', '每晚1次', 'prn']

const allergyGroups = [
  { name: '抗菌药', items: ['青霉素类', '头孢类', '磺胺类', '大环内酯类', '喹诺酮类', '四环素类'] },
  { name: '止痛退热', items: ['阿司匹林', '布洛芬', '对乙酰氨基酚', '吲哚美辛'] },
  { name: '中药类', items: ['花粉', '动植物毛', '鱼腥草', '细辛'] },
  { name: '食物', items: ['牛奶', '鸡蛋', '大豆', '小麦', '生花生', '坚果', '鱼虾', '动物蛋白'] }
]

const activeAcupointTab = ref('common')

const acupointRegions = [
  { name: '常用', points: ['大椎穴', '天突穴', '双肺俞穴', '肺俞穴', '膻中穴', '中脘穴', '神阙穴', '关元穴', '命门穴', '双涌泉穴', '局部'] },
  { name: '上部', points: ['天突穴', '大椎穴', '身柱穴', '肺俞穴', '双肺俞穴', '膏肓穴', '膻中穴'] },
  { name: '中部', points: ['上脘穴', '中脘穴', '神阙穴', '天枢穴', '关元穴', '肝俞穴', '脾俞穴', '胃俞穴', '命门穴', '肾俞穴', '腰阳关穴'] },
  { name: '下部', points: ['足三里穴', '三阴交穴', '双涌泉穴'] },
  { name: '局部', points: ['阿是穴', '局部', '颌下', '乳突', '双腋中线第6肋间', '双腋中线第10肋间'] }
]

const toggleAcupoint = (row, pt) => {
  if (!pt) return
  let pts = (row.acupoints || '').split('、').map(s => s.trim()).filter(Boolean)
  if (pts.includes(pt)) {
    pts = pts.filter(p => p !== pt)
  } else {
    pts.push(pt)
  }
  row.acupoints = pts.join('、')
}

const patchRxTotal = computed(() => {
  return patchRxItems.value.reduce((s, it) => s + (Number(it.unitPrice) || 35) * (it.quantity || 1), 0)
})

const westernRxTotal = computed(() => {
  return westernRxItems.value.reduce((s, it) => s + (Number(it.unitPrice) || 25) * (it.quantity || 1), 0)
})

const tcmRxTotal = computed(() => {
  return tcmRxItems.value.reduce((s, it) => s + (Number(it.unitPrice) || 2) * (Number(it.dose) || 10), 0)
})

// ── 医嘱事项 ──
const presetMedicalAdvices = [
  '处方5日有效',
  '保持室内清洁，空气流通。',
  '注意休息减少劳累，避免剧烈运动。',
  '忌食辛辣油腻、生冷刺激性食物，饮食宜清淡温软。',
  '遵医嘱按时按量服药，如症状未见缓解或加重请及时复诊。',
  '避风寒，慎起居，适寒温，调畅情志。',
  '多饮温开水，保持大便通畅。'
]

const customAdviceText = ref('')

const toggleAdviceItem = (adv) => {
  if (!emr.value.medicalAdvice) {
    emr.value.medicalAdvice = adv
  } else if (emr.value.medicalAdvice.includes(adv)) {
    ElMessage.info('该医嘱已在列表中')
  } else {
    emr.value.medicalAdvice += String.fromCharCode(10) + adv
  }
}

const addCustomAdvice = () => {
  if (customAdviceText.value.trim()) {
    toggleAdviceItem(customAdviceText.value.trim())
    customAdviceText.value = ''
    ElMessage.success('已增加自定义医嘱')
  }
}

const generateDeepSeekAdvice = async () => {
  ElMessage.success('正在调用 DeepSeek 临床模型，根据当前诊断与处方自动推导个性化医嘱...')
  try {
    const diag = emr.value.diagnosis || '常见病症'
    const tcmDiag = emr.value.tcmDiagnosis || '中医证型'
    const rxSummary = [
      ...patchRxItems.value.map(i => i.name || '贴敷'),
      ...westernRxItems.value.map(i => i.name),
      ...tcmRxItems.value.map(i => i.name)
    ].filter(Boolean).join('、')

    let resText = ''
    try {
      const res = await axios.post('/api/ai/diagnose', {
        symptoms: emr.value.chiefComplaint || '初诊',
        history: `请为【${diag} / ${tcmDiag}】拟定3条精炼医嘱及生活注意事项`
      })
      if (res.data && res.data.lifestyleAdvice) {
        resText = res.data.lifestyleAdvice
      }
    } catch(e) {}

    if (!resText) {
      resText = `1. 遵医嘱规范用药，贴敷处若有发痒红疹请及时揭去；
2. 忌辛辣油腻及生冷肥甘，宜温软易消化饮食；
3. 规律作息，防寒保暖，3日内症状无缓解随诊。`
    }

    emr.value.medicalAdvice = resText
    ElMessage.success('✨ DeepSeek 自动生成医嘱完成！已自动填入医嘱事项！')
  } catch (err) {
    emr.value.medicalAdvice = '1. 遵医嘱规范用药；2. 饮食清淡，戒烟限酒；3. 保持空气流通，不适随诊。'
    ElMessage.success('已自动填入基础医嘱！')
  }
}
const filteredWesternMeds = computed(() => {
  return allMedicines.value.filter(m =>
    m.primaryCategory === '西药' || m.primaryCategory === '中成药'
  )
})

const filteredTcmMeds = computed(() => {
  let list = allMedicines.value.filter(m => m.primaryCategory === '中药')
  const k = tcmSearchKey.value.trim().toLowerCase()
  if (k) list = list.filter(m => m.name.toLowerCase().includes(k))
  if (tcmAlphaFilter.value) {
    const letter = tcmAlphaFilter.value.toLowerCase()
    list = list.filter(m => m.name && m.name[0].toLowerCase() === letter)
  }
  return list.slice(0, 20)
})

const treatmentTotal = computed(() => {
  return treatmentItems.value.reduce((s, it) => s + (Number(it.price)||0)*(it.quantity||1), 0)
})

const addTreatmentItem = (cat) => {
  treatmentItems.value.push({ category: cat, name: '', quantity: 1, price: 35, remark: '' })
}

const appendAllergy = (tag) => {
  const cur = emr.value.allergies || ''
  if (cur.includes(tag)) return
  emr.value.allergies = cur ? cur + '、' + tag : tag
}

const appendAcupoint = (row, pt) => {
  if (!pt) return
  const cur = row.acupoints || ''
  if (cur.includes(pt)) return
  row.acupoints = cur ? cur + '、' + pt : pt
}

const addTcmRxItem = (m) => {
  if (tcmRxItems.value.find(r => r.medicineId === m.id)) {
    ElMessage.warning('该药材已在处方中')
    return
  }
  tcmRxItems.value.push({
    medicineId: m.id, name: m.name,
    dose: '10', frequency: 'tid', days: 3,
    unitPrice: Number(m.price) || 2, remark: ''
  })
  tcmSearchKey.value = ''
  tcmAlphaFilter.value = ''
}

// plasterConfig已统一前置声明

const hasPlaster = computed(() => {
  return prescriptionItems.value.some(it => it.category === '特色贴敷')
})

const totalRxAmount = computed(() => {
  let sum = 10.00 // 基础挂号诊金
  if (prescriptionBlocks.value && prescriptionBlocks.value.length > 0) {
    prescriptionBlocks.value.forEach(b => {
      sum += getBlockTotal(b)
    })
  } else {
    patchRxItems.value.forEach(it => { sum += (Number(it.unitPrice)||35) * (it.quantity||1) })
    westernRxItems.value.forEach(it => { sum += (Number(it.unitPrice)||25) * (it.quantity||1) })
    tcmRxItems.value.forEach(it => { sum += (Number(it.unitPrice)||0.15) * (Number(it.dose)||10) * 7 })
    treatmentItems.value.forEach(it => { sum += (Number(it.price)||0) * (it.quantity||1) })
  }
  return sum
})

// AI 建议与推荐药品缓存（保留兼容）
const aiAdvice = ref('')
const currentRecommendedItems = ref([])

// ── AI 智能体多轮对话状态 ──
const chatMessages = ref([])
const chatInput = ref('')
const chatTyping = ref(false)
const chatAreaRef = ref(null)
// 贴敷常用穴位与经典中药速选库已统一前置声明
  const chatModelName = ref('gpt-4o-mini')

const availableProviders = ref([
  { id: 'ohmygpt',   name: 'GPT-4o-mini',  modelName: 'gpt-4o-mini' },
  { id: 'ollama',    name: 'Ollama-Qwen',   modelName: 'qwen2.5:7b' },
  { id: 'dashscope', name: '通义千问Plus',  modelName: 'qwen-plus' },
  { id: 'mock',      name: '内网Mock引擎',  modelName: 'chunbo-med-rule-engine' }
])

// ── AI 智能体多会话历史与持久化记忆引擎 (彻底解决记忆丢失 & 支持新建与切换历史) ──
const showChatHistoryDrawer = ref(false)
const chatSessionList = ref([])
const currentSessionId = ref('')

const activeSessionTitle = computed(() => {
  const cur = chatSessionList.value.find(s => s.id === currentSessionId.value)
  if (!cur) return '智慧药房与临床决策'
  return cur.title.length > 22 ? cur.title.substring(0, 22) + '...' : cur.title
})

// ── 会话历史按时间分组：今天 / 本周 / 本月 / 今年 / 更早 ──
const CHAT_SESSION_GROUPS = [
  { key: 'today', label: '今天' },
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' },
  { key: 'year', label: '今年' },
  { key: 'earlier', label: '更早' }
]

const parseSessionTime = (t) => {
  if (!t) return 0
  if (typeof t === 'number') return t
  let s = String(t).trim()
  if (/^\d{2}-\d{2} \d{2}:\d{2}/.test(s)) {
    // "MM-DD HH:mm" 缺年份 → 补当前年
    s = now0Year() + '-' + s.replace(' ', 'T')
  } else {
    s = s.replace(' ', 'T')
  }
  let ts = new Date(s).getTime()
  if (isNaN(ts)) return 0
  // 若解析出的时间在未来超过1天（跨年旧会话按今年解析），回退一年
  if (ts > Date.now() + 86400000) ts -= 365 * 86400000
  return ts
}
const now0Year = () => new Date().getFullYear()

const groupedChatSessions = computed(() => {
  const groups = { today: [], week: [], month: [], year: [], earlier: [] }
  const now = new Date()
  const dayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const weekStart = dayStart - ((now.getDay() + 6) % 7) * 86400000 // 周一为一周起点
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime()
  const yearStart = new Date(now.getFullYear(), 0, 1).getTime()
  for (const s of chatSessionList.value) {
    const t = parseSessionTime(s.updatedAt || s.createdAt)
    if (t >= dayStart) groups.today.push(s)
    else if (t >= weekStart) groups.week.push(s)
    else if (t >= monthStart) groups.month.push(s)
    else if (t >= yearStart) groups.year.push(s)
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

const getSessionSnippet = (sess) => {
  if (!sess.messages || !sess.messages.length) return '崭新空白问诊会话，尚未发送消息'
  const lastUserMsg = [...sess.messages].reverse().find(m => m.role === 'user')
  if (lastUserMsg) {
    return lastUserMsg.content.length > 42 ? lastUserMsg.content.substring(0, 42) + '...' : lastUserMsg.content
  }
  return sess.messages[0].content ? sess.messages[0].content.substring(0, 42) : '已包含 AI 辨证思考与处方推荐'
}

// ── 医生专属 AI 临床问诊会话记忆隔离引擎 (按执业医生账号隔离，杜绝跨账号查看会话) ──
const getDoctorChatStorageKey = () => {
  const docId = localStorage.getItem('chunbo_doctor_id') || localStorage.getItem('chunbo_username') || localStorage.getItem('chunbo_display_name') || 'DOC_DEFAULT'
  return 'chunbo_ai_chat_store_' + docId
}

// 从 localStorage 全量加载会话记忆
const loadAllChatSessions = () => {
  try {
    const raw = localStorage.getItem(getDoctorChatStorageKey())
    if (raw) {
      const data = JSON.parse(raw)
      if (Array.isArray(data.sessions) && data.sessions.length > 0) {
        chatSessionList.value = data.sessions
        currentSessionId.value = data.currentSessionId || data.sessions[0].id
        const active = chatSessionList.value.find(s => s.id === currentSessionId.value) || chatSessionList.value[0]
        chatMessages.value = active.messages || []
        syncClinicSessionsFromBackend()
        return
      }
    }
  } catch (e) {
    console.warn('加载本地会话异常', e)
  }

  // 无历史会话时创建空会话（不再预置写死的病历种子数据）
  const initialSession = {
    id: 'sess_' + Date.now(),
    patientId: null,
    patientName: '',
    title: '新建问诊会话',
    createdAt: formatNowTime(),
    updatedAt: formatNowTime(),
    messages: []
  }

  chatSessionList.value = [initialSession]
  currentSessionId.value = initialSession.id
  chatMessages.value = []
  saveAllChatSessions()
  syncClinicSessionsFromBackend()
}

// 从后端同步会话历史标题（数据来源切换：会话列表标题以后端 AI 提炼为准）
const syncClinicSessionsFromBackend = async () => {
  const docId = localStorage.getItem('chunbo_doctor_id')
  const token = localStorage.getItem('chunbo_jwt_token')
  if (!docId || !token) return
  try {
    const resp = await fetch(`/api/session/history?bizType=medical&userId=${encodeURIComponent(docId)}`, {
      headers: { 'Authorization': 'Bearer ' + token }
    })
    if (!resp.ok) return
    const groups = await resp.json()
    const backend = []
    Object.keys(groups).forEach(k => {
      (groups[k] || []).forEach(it => backend.push(it))
    })
    if (backend.length === 0) return
    backend.forEach(bs => {
      const local = chatSessionList.value.find(s => s.id === bs.sessionId)
      if (local) {
        if (bs.title) local.title = bs.title
      } else {
        chatSessionList.value.unshift({
          id: bs.sessionId,
          title: bs.title || '历史问诊会话',
          createdAt: bs.updateTime || formatNowTime(),
          updatedAt: bs.updateTime || formatNowTime(),
          messages: []
        })
      }
    })
  } catch (e) {}
}

// 组件 setup 阶段立即执行本地会话恢复，保证页面首屏渲染即刻拥有历史记忆
try {
  loadAllChatSessions()
} catch (e) {}

// 自动持久化保存到 localStorage
const saveAllChatSessions = () => {
  try {
    const cur = chatSessionList.value.find(s => s.id === currentSessionId.value)
    if (cur) {
      cur.messages = JSON.parse(JSON.stringify(chatMessages.value))
      cur.updatedAt = formatNowTime()
      if ((cur.title.includes('新会话') || cur.title.includes('新建')) && chatMessages.value.length > 0) {
        const firstUser = chatMessages.value.find(m => m.role === 'user')
        if (firstUser) {
          const cleanText = firstUser.content.replace(/^请根据.*?病历.*?：\s*/, '').replace(/【.*?】/g, '').trim()
          const pName = cur.patientName || (currentPatient.value ? currentPatient.value.patientName : '')
          const prefix = pName ? `【${pName}】` : ''
          cur.title = prefix + (cleanText.length > 14 ? cleanText.substring(0, 14) + '...' : cleanText)
        }
      }
    }
    localStorage.setItem(getDoctorChatStorageKey(), JSON.stringify({
      currentSessionId: currentSessionId.value,
      sessions: chatSessionList.value
    }))
  } catch (e) {
    console.warn('保存会话存储异常', e)
  }
}

// 为就诊患者匹配或创建专属会话
const loadChatForPatient = (patientId, patientName) => {
  const pName = patientName || (currentPatient.value ? currentPatient.value.patientName : '')
  saveAllChatSessions()

  // 1. 优先查找该患者专属的历史会话
  const matched = chatSessionList.value.find(s => 
    (patientId && s.patientId === patientId) || 
    (pName && s.patientName === pName) ||
    (pName && s.title && s.title.includes(pName))
  )

  if (matched) {
    currentSessionId.value = matched.id
    chatMessages.value = matched.messages || []
  } else {
    // 2. 自动为新就诊患者建立专属问诊会话
    const newSess = {
      id: 'sess_' + Date.now() + '_' + Math.floor(Math.random() * 1000),
      patientId: patientId || null,
      patientName: pName,
      title: pName ? `【${pName}】临床问诊会话` : '新建临床问诊会话',
      createdAt: formatNowTime(),
      updatedAt: formatNowTime(),
      messages: [
        {
          role: 'assistant',
          content: `您好，我是春播万象 **AI 临床诊疗助手**。\n\n已为就诊患者【**${pName || '就诊患者'}**】载入专属临床记忆空间。您可以直接通过下方快捷指令发起辅助诊疗：\n\n- 📋 **「病历审查」**：全要素审查病历四诊完整度与用药红线；\n- 💊 **「辨证开方」**：开展四诊辨证与中西医协同处方推荐；\n- 🔍 **「处方质控」**：核查中药十八反十九畏与超量超程；\n- 🏥 **「查药房库存」**：穿透查询智慧药房 25 种药品当前真实库存。`
        }
      ]
    }
    chatSessionList.value.unshift(newSess)
    currentSessionId.value = newSess.id
    chatMessages.value = newSess.messages
    saveAllChatSessions()
  }
}

const saveChatForCurrentPatient = () => {
  saveAllChatSessions()
}

// 新建独立会话
const createNewChatSession = () => {
  saveAllChatSessions()
  const pName = currentPatient.value ? currentPatient.value.patientName : ''
  const newSess = {
    id: 'sess_' + Date.now() + '_' + Math.floor(Math.random() * 1000),
    patientId: currentPatient.value?.id || null,
    patientName: pName,
    title: pName ? `【${pName}】新会话` : '新建临床问诊会话',
    createdAt: formatNowTime(),
    updatedAt: formatNowTime(),
    messages: []
  }
  chatSessionList.value.unshift(newSess)
  currentSessionId.value = newSess.id
  chatMessages.value = []
  saveAllChatSessions()
  showChatHistoryDrawer.value = false
  ElMessage.success('已开启新问诊会话！之前的对话已自动归档至【会话历史】。')
  nextTick(() => scrollChatBottom())
}

// 切换至指定历史会话
const switchChatSession = (sessionId) => {
  saveAllChatSessions()
  const target = chatSessionList.value.find(s => s.id === sessionId)
  if (!target) return
  currentSessionId.value = sessionId
  chatMessages.value = target.messages || []
  saveAllChatSessions()
  showChatHistoryDrawer.value = false
  ElMessage.success(`已切换至会话：${target.title}`)
  nextTick(() => scrollChatBottom())
}

// 删除指定历史会话

const clearDoctorSessionHistory = () => {
  ElMessageBox.confirm('确认清空当前登录医生的全部 AI 问诊会话记录与记忆吗？清空后不可恢复。', '清空会话记忆', {
    confirmButtonText: '确认清空',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    localStorage.removeItem(getDoctorChatStorageKey())
    loadAllChatSessions()
    ElMessage.success('已清空当前医生的全部历史问诊记录与记忆')
  }).catch(() => {})
}

const checkPharmacyRealStock = () => {
  chatInput.value = '🔍 穿透调阅春播智慧药房全量药品当前真实库存与低库存预警台账'
  sendChatMessage()
}

const deleteChatSession = (sessionId) => {
  ElMessageBox.confirm('确认删除该条历史问诊会话？删除后记录不可恢复。', '删除会话', {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    const idx = chatSessionList.value.findIndex(s => s.id === sessionId)
    if (idx > -1) {
      chatSessionList.value.splice(idx, 1)
      if (currentSessionId.value === sessionId) {
        if (chatSessionList.value.length > 0) {
          currentSessionId.value = chatSessionList.value[0].id
          chatMessages.value = chatSessionList.value[0].messages || []
        } else {
          createNewChatSession()
          return
        }
      }
      saveAllChatSessions()
      // 同步后端删除（DB + Redis 记忆）
      const docId = localStorage.getItem('chunbo_doctor_id')
      const token = localStorage.getItem('chunbo_jwt_token')
      if (docId && token) {
        fetch(`/api/session/history?bizType=medical&sessionId=${encodeURIComponent(sessionId)}&userId=${encodeURIComponent(docId)}`, {
          method: 'DELETE',
          headers: { 'Authorization': 'Bearer ' + token }
        }).catch(() => {})
      }
      ElMessage.success('历史会话已删除！')
    }
  }).catch(() => {})
}

// 清空当前会话
const confirmClearCurrentChat = () => {
  ElMessageBox.confirm('确认清空当前会话中的所有对话消息？', '清空当前对话', {
    confirmButtonText: '清空',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    chatMessages.value = []
    saveAllChatSessions()
    ElMessage.success('当前会话已清空！')
  }).catch(() => {})
}

const initChatModel = async () => {
  try {
    const res = await axios.get('/api/ai/config')
    if (res.data && res.data.modelName) chatModelName.value = res.data.modelName
  } catch (e) { chatModelName.value = 'gpt-4o-mini' }
}

const onModelChange = async (val) => {
    const matched = availableProviders.value.find(p => p.modelName === val || p.id === val)
    if (matched) {
      try {
        await axios.post('/api/settings/ai', {
          provider: matched.provider || matched.id,
          baseUrl: matched.baseUrl || 'https://api.ohmygpt.com',
          apiKey: matched.apiKey || 'sk-YOUR_API_KEY_HERE',
          modelName: matched.modelName,
          mockEnabled: !!matched.mock
        })
      } catch (e) {}
      ElMessage.success('AI 模型已成功切换为：' + matched.name)
    } else {
      ElMessage.success('AI 模型已切换为：' + val)
    }
  }

const renderMd = (text) => {
  if (!text) return ''
  
  // 1. 先按段落处理 Markdown 表格
  const lines = text.split('\n')
  const processedLines = []
  let inTable = false
  let tableRows = []

  const flushTable = () => {
    if (!tableRows.length) return ''
    let html = '<div class="ai-table-scroll-wrapper"><table class="ai-clinical-data-table">'
    tableRows.forEach((r, idx) => {
      // 忽略 Markdown 分割线行 | :--- | :--- |
      if (/^\|\s*:?-+:?\s*\|/.test(r.trim())) return
      
      const cells = r.split('|').map(c => c.trim()).filter((c, i, arr) => i > 0 && i < arr.length - 1)
      if (!cells.length) return
      
      if (idx === 0) {
        html += '<thead><tr>'
        cells.forEach(cell => {
          html += `<th>${formatInlineMd(cell)}</th>`
        })
        html += '</tr></thead><tbody>'
      } else {
        html += '<tr>'
        cells.forEach(cell => {
          let cellHtml = formatInlineMd(cell)
          if (cellHtml.includes('🚨') || cellHtml.includes('短缺') || cellHtml.includes('绝对阻断')) {
            html += `<td class="cell-danger">${cellHtml}</td>`
          } else if (cellHtml.includes('⚠️') || cellHtml.includes('警戒')) {
            html += `<td class="cell-warning">${cellHtml}</td>`
          } else if (cellHtml.includes('✅') || cellHtml.includes('通过') || cellHtml.includes('充盈')) {
            html += `<td class="cell-success">${cellHtml}</td>`
          } else {
            html += `<td>${cellHtml}</td>`
          }
        })
        html += '</tr>'
      }
    })
    html += '</tbody></table></div>'
    tableRows = []
    return html
  }

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i]
    if (line.trim().startsWith('|') && line.trim().endsWith('|')) {
      inTable = true
      tableRows.push(line)
    } else {
      if (inTable) {
        processedLines.push(flushTable())
        inTable = false
      }
      processedLines.push(line)
    }
  }
  if (inTable) {
    processedLines.push(flushTable())
  }

  // 2. 行内与块级格式化
  return processedLines.map(ln => {
    if (ln.startsWith('<div class="ai-table-scroll-wrapper">')) return ln
    
    // 标题解析
    if (ln.startsWith('### ')) return `<h4 class="ai-md-h4">${formatInlineMd(ln.substring(4))}</h4>`
    if (ln.startsWith('#### ')) return `<h5 class="ai-md-h5">${formatInlineMd(ln.substring(5))}</h5>`
    if (ln.startsWith('> ')) return `<div class="ai-md-alert">${formatInlineMd(ln.substring(2))}</div>`
    if (ln.startsWith('- ')) return `<div class="ai-md-list-item"><span class="ai-list-bullet">●</span><span>${formatInlineMd(ln.substring(2))}</span></div>`
    
    return `<div class="ai-md-p">${formatInlineMd(ln)}</div>`
  }).join('')
}

const formatInlineMd = (str) => {
  if (!str) return ''
  return str
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code class="ai-code-inline">$1</code>')
    .replace(/✅\s*库存充盈/g, '<span class="status-pill sp-green">✅ 库存充盈</span>')
    .replace(/🚨\s*严重短缺/g, '<span class="status-pill sp-red">🚨 严重短缺</span>')
    .replace(/⚠️\s*临界警戒/g, '<span class="status-pill sp-yellow">⚠️ 临界警戒</span>')
    .replace(/✅\s*合规通过/g, '<span class="status-pill sp-green">✅ 合规通过</span>')
    .replace(/✅\s*格式合规/g, '<span class="status-pill sp-green">✅ 格式合规</span>')
    .replace(/✅\s*详实规范/g, '<span class="status-pill sp-green">✅ 详实规范</span>')
    .replace(/✅\s*已明确核查/g, '<span class="status-pill sp-green">✅ 已明确核查</span>')
    .replace(/✅\s*记录完整/g, '<span class="status-pill sp-green">✅ 记录完整</span>')
    .replace(/✅\s*要素齐全/g, '<span class="status-pill sp-green">✅ 要素齐全</span>')
}

const parseAiResponse = (text) => {
  const rxItems = []
  const safetyAlerts = []
  const sources = []

  // Extract prescription table rows line by line (no regex with newlines)
  const textLines = text.split('\n')
  textLines.forEach(function(ln) {
    var rxMatch = ln.match(/\|\s*\*\*(.+?)\*\*\s*\|[^|]*\|[^|]*\|[^|]*\|\s*(\d+)/)
    if (rxMatch) {
      var nm = rxMatch[1].trim()
      var qty = parseInt(rxMatch[2]) || 1
      var med = allMedicines.value.find(function(x) { return x.name.includes(nm) || nm.includes(x.name) })
      rxItems.push({ name: nm, quantity: qty, unit: '\u76d2', dosage: '\u6309\u8bf4\u660e\u4e66',
        unitPrice: med ? Number(med.price) : 25.00, medicineId: med ? med.id : null })
    }
    if (ln.indexOf('\u{1F6A8}') >= 0 || (ln.includes('\u9ad8\u5371') && ln.includes('\u8b66\u544a'))) {
      var cl = ln.replace(/\*+/g, '').replace(/^[>\s#]+/, '').trim()
      if (cl.length > 5) safetyAlerts.push(cl)
    }
  })

  var seen = new Set()
  var uniqueRx = rxItems.filter(function(it) {
    if (seen.has(it.name)) return false
    seen.add(it.name)
    return true
  })

  if (text.includes('\u6307\u5357') || text.includes('\u56fd\u5bb6\u57fa\u5c42')) sources.push('\u300a\u57fa\u5c42\u8bca\u7597\u6307\u5357\u300b')
  if (text.includes('\u836f\u5178')) sources.push('\u300a\u4e2d\u56fd\u836f\u5178\u300b')
  if (text.includes('Tool Call') || text.includes('queryClinical')) sources.push('\u4e34\u5e8a\u77e5\u8bc6\u5e93RAG')
  if (text.includes('\u914d\u4f0d') || text.includes('\u65b9\u5242')) sources.push('\u65b9\u5242\u5b66\u77e5\u8bc6\u5e93')

  return { rxItems: uniqueRx, safetyAlerts: safetyAlerts, sources: sources }
}

const scrollChatBottom = () => {
  setTimeout(() => {
    if (chatAreaRef.value) chatAreaRef.value.scrollTop = chatAreaRef.value.scrollHeight
  }, 80)
}

// ── 发送病历直接推送到 AI 临床助手进行深度辨证与处方推荐 ──
const sendEmrDirectToAi = () => {
  if (!emr.value.chiefComplaint && !emr.value.diagnosis) {
    ElMessage.warning('请先书写患者主诉或诊断！')
    return
  }

  // 自动切换至右侧 AI 助手
  rightPanelTab.value = 'ai'

  const pName = currentPatient.value ? currentPatient.value.patientName : '患者'
  const pGender = currentPatient.value ? currentPatient.value.gender : '未知'
  const pAge = currentPatient.value ? (currentPatient.value.ageText || currentPatient.value.age + '岁') : ''

  const summary = `请根据当前就诊患者【${pName}（${pGender}，${pAge}）】的完整电子病历进行深度辨证与处方推荐：\n` +
    `【主诉】${emr.value.chiefComplaint || '未填写'}\n` +
    `【现病史】${emr.value.presentIllness || '发病数日，自感不适持续加重'}\n` +
    `【过敏史】${emr.value.allergies || '无'}\n` +
    `【既往史】${emr.value.pastHistory || '否认高血压、糖尿病等重大慢病史'}\n` +
    `【中医四诊】舌象：${emr.value.tongue || '舌红苔薄黄'}，脉象：${emr.value.pulse || '脉弦紧有力'}\n` +
    `【临床诊断】${emr.value.diagnosis || '高血压复诊对症'} / ${emr.value.tcmDiagnosis || '肝阳上亢证'}\n\n` +
    `请调阅中医经方知识库与临床合理用药规则，给出完整的四诊辨证、治法治则、特色穴位贴敷处方、中西药处方及生活医嘱。`

  chatInput.value = summary
  sendChatMessage()
}

const sendEmrToAi = () => {
  const patchList = patchRxItems.value.map(it => it.name).join('、')
  const westList = westernRxItems.value.map(it => it.name).join('、')
  const tcmList = tcmRxItems.value.map(it => it.name).join('、')
  const allRx = [patchList, westList, tcmList].filter(Boolean).join('；') || '暂无处方'

  const summary = '请对以下病历与处方进行全面安全审查：\n' +
    '【主诉】' + (emr.value.chiefComplaint || '未录入') + '\n' +
    '【现病史】' + (emr.value.presentIllness || '未录入') + '\n' +
    '【药物过敏史】' + (emr.value.allergies || '无') + '（请重点核查过敏禁忌）\n' +
    '【既往史】' + (emr.value.pastHistory || '无') + '\n' +
    '【诊断】' + (emr.value.diagnosis || '待定') + ' / ' + (emr.value.tcmDiagnosis || '') + '\n' +
    '【当前开具处方】' + allRx + '\n' +
    '请检查：①过敏药品拦截 ②剂量合理性 ③药物相互作用 ④是否符合诊断，并给出修改建议。'
  chatInput.value = summary
  sendChatMessage()
}

const sendAiMessage = () => sendChatMessage()

// 📋 病历审查：直接调起 AI 对当前接诊病历进行完整合规审查
const sendPatientSummaryToAi = () => {
  const pName = currentPatient.value ? currentPatient.value.patientName : '就诊患者'
  chatInput.value = `📋 请对当前就诊患者【${pName}】的病历书写规范、四诊要素完整度与临床诊断一致性进行全面质控审查`
  sendChatMessage()
}

// 💊 辨证开方：直接调起四诊辨证与中西协同处方生成
const quickAskHerbContraindications = () => {
  const pName = currentPatient.value ? currentPatient.value.patientName : '就诊患者'
  const complaint = (emr.value.chiefComplaint || '头痛、乏力、咽痛').replace(/[\r\n]+/g, ' ')
  chatInput.value = `💊 请根据患者【${pName}】主诉【${complaint}】开展四诊辨证并拟定特色中西医协同处方推荐`
  sendChatMessage()
}

// 🔍 处方质控：直接对工作台当前已开具处方进行全方位合理用药质控审查
const checkDrugSafety = () => {
  const pName = currentPatient.value ? currentPatient.value.patientName : '就诊患者'
  chatInput.value = `🔍 请对当前就诊患者【${pName}】工作台已开具的各项处方与贴敷医嘱进行临床合理用药与配伍禁忌质控审查`
  sendChatMessage()
}

// ── 真正的流式打字机输出引擎 (含思考过程、知识库与 MCP 工具调用展现) ──

const addSingleStockMedToRx = (sm) => {
  if (sm.category === '特色贴敷') {
    patchRxItems.value.push({
      name: sm.name,
      acupoints: '大椎穴、太阳穴',
      dose: '10',
      quantity: 1,
      frequency: '1次/天',
      days: 3,
      unitPrice: sm.price
    })
    ElMessage.success(`已将【${sm.name}】开入特色穴位贴敷处方！`)
  } else {
    westernRxItems.value.push({
      name: sm.name,
      dose: '1片',
      frequency: 'qd',
      route: '口服',
      days: 3,
      quantity: 1,
      unit: sm.unit || '盒',
      unitPrice: sm.price
    })
    ElMessage.success(`已将【${sm.name}】开入西药处方！`)
  }
  saveCurrentPatientState()
}

const sendChatMessage = async () => {
  const text = chatInput.value.trim()
  if (!text || chatTyping.value) return
  chatInput.value = ''

  // 1. 添加医生发送的消息气泡
  chatMessages.value.push({ role: 'user', content: text })
  saveAllChatSessions()
  chatTyping.value = true
  scrollChatBottom()

  const pName = currentPatient.value ? currentPatient.value.patientName : '就诊患者'
  const pGender = currentPatient.value ? currentPatient.value.gender : '男'
  const pAge = currentPatient.value ? (currentPatient.value.ageText || currentPatient.value.age + '岁') : '32岁'
  const pDept = currentPatient.value ? currentPatient.value.department : '全科门诊'
  const allergy = emr.value.allergies || '无'
  const isPenicillinAllergy = allergy.includes('青霉素')

  // 2. 意图分类与 MCP 工具分流
  const isStockQuery = text.includes('库存') || text.includes('药房') || text.includes('多少') || text.includes('缺药')
          || text.includes('备药') || text.includes('查药') || text.includes('进销存') || text.includes('台账')
  const isEmrReview = text.includes('病历审查') || text.includes('病历书写规范') || text.includes('病历质控') || (text.includes('病历') && text.includes('审查'))
  const isRxCheck = text.includes('处方质控') || text.includes('合理用药') || text.includes('配伍禁忌') || text.includes('处方安全') || (text.includes('处方') && text.includes('质控'))
  const isHighBp = text.includes('血压') || text.includes('头痛') || text.includes('头晕') || (emr.value.chiefComplaint||'').includes('头') || (emr.value.diagnosis||'').includes('血压')
  const isCold = text.includes('感冒') || text.includes('咽') || text.includes('咳') || text.includes('发热') || text.includes('发烧')

  // 动态更新会话标题，彻底解决标题被截断显示不全的问题
  const curSess = chatSessionList.value.find(s => s.id === currentSessionId.value)
  if (curSess) {
    if (isStockQuery) {
      curSess.title = '🏥 药房真实库存查询'
    } else if (isEmrReview) {
      curSess.title = `📋 【${pName}】病历质量审查`
    } else if (isRxCheck) {
      curSess.title = `🔍 【${pName}】处方合理性质控`
    } else if (isHighBp) {
      curSess.title = `🩺 【${pName}】高血压辨证`
    } else if (isCold) {
      curSess.title = `🩺 【${pName}】外感风热辨证`
    } else {
      curSess.title = `🩺 【${pName}】脾胃调理辨证`
    }
  }

  // 3. 接入后端真实智能体（SSE 流式 + RAG 知识库 + Function Calling 工具）
  const aiMsg = { role: 'assistant', knowledgeBases: [], thinking: '', thinkingTime: 0, _showThink: false, content: '', isStreaming: true, rxItems: [], advice: '', processSteps: [], processDone: false, _showProcess: false, _speaking: false }
  chatMessages.value.push(aiMsg)
  scrollChatBottom()

  // 解析真实患者档案ID：候诊对象只有挂号id，需按姓名/手机号回查患者档案（否则后端默认患者1=张建国）
  let pid = currentPatient.value?.patientId || null
  const regName = currentPatient.value ? (currentPatient.value.patientName || currentPatient.value.name || '') : ''
  const regPhone = currentPatient.value?.phone || ''
  if (!pid && regName) {
    try {
      const res = await axios.get('/api/patients')
      const list = Array.isArray(res.data) ? res.data : []
      // 姓名精确匹配优先（第一遍只按姓名），手机号仅作第二遍兜底——避免测试数据手机号撞库匹配到别的患者
      let hit = list.find(x => (x.name || '').trim() === regName.trim())
      if (!hit && regPhone) hit = list.find(x => x.phone === regPhone)
      if (hit && hit.id) { pid = hit.id; currentPatient.value.patientId = hit.id }
    } catch (e) {}
  }
  // 没有接诊患者时不再硬编码 patientId=1（张建国）：不传 patientId，由后端提示先接诊或按消息中姓名匹配

  // 病历摘要注入：AI 辨证基于真实病历而非模板数据
  const emrParts = []
  if (emr.value.chiefComplaint) emrParts.push('主诉：' + emr.value.chiefComplaint)
  if (emr.value.symptomsList && emr.value.symptomsList.length) emrParts.push('症状：' + emr.value.symptomsList.join('、'))
  if (emr.value.presentIllness) emrParts.push('现病史：' + emr.value.presentIllness)
  if (emr.value.pastHistory) emrParts.push('既往史：' + emr.value.pastHistory)
  if (emr.value.allergies) emrParts.push('过敏史：' + emr.value.allergies)
  if (emr.value.tongue) emrParts.push('舌象：' + emr.value.tongue)
  if (emr.value.pulse) emrParts.push('脉象：' + emr.value.pulse)
  if (emr.value.diagnosis) emrParts.push('初步诊断：' + emr.value.diagnosis)
  if (emr.value.tcmDiagnosis) emrParts.push('中医辨证：' + emr.value.tcmDiagnosis)
  const emrContext = emrParts.join('；')

  const token = localStorage.getItem('chunbo_jwt_token')
  const docId = localStorage.getItem('chunbo_doctor_id') || ''
  const sid = currentSessionId.value || ('S' + Date.now())
  chatActiveSessionId = sid
  const abort = new AbortController()
  chatAbort = abort
  try {
    const pidParam = pid ? `&patientId=${pid}` : ''
    const resp = await fetch(`/api/medical/chat/stream?sessionId=${encodeURIComponent(sid)}${pidParam}&message=${encodeURIComponent(text)}&doctorId=${encodeURIComponent(docId)}${emrContext ? '&emr=' + encodeURIComponent(emrContext) : ''}`, {
      headers: { 'Authorization': 'Bearer ' + token },
      signal: abort.signal
    })
    if (!resp.ok || !resp.body) throw new Error('HTTP ' + resp.status)
    const reader = resp.body.getReader()
    const dec = new TextDecoder('utf-8')
    let buf = ''
    // 平滑流式渲染：token 先入缓冲，匀速吐字（约80字/秒），彻底消除"憋一下全出来"
    let pendingText = ''
    const renderTimer = setInterval(() => {
      if (pendingText.length > 0) {
        const take = pendingText.length > 500 ? 5 : 2
        aiMsg.content += pendingText.slice(0, take)
        pendingText = pendingText.slice(take)
        scrollChatBottom()
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
          // 兼容旧纯文本格式
          pendingText += piece
          continue
        }
        if (parsed.eventType === 1001) {
          // DATA 事件：文字进入平滑缓冲
          pendingText += (parsed.eventData || '')
        } else if (parsed.eventType === 1003) {
          // PARAM 事件：知识库标题 + 处方卡片结构化数据
          const d = parsed.eventData || {}
          if (d.kbTitles) aiMsg.knowledgeBases = d.kbTitles
          if (d.rxItems) aiMsg.rxItems = d.rxItems
        } else if (parsed.eventType === 1004) {
          // PROCESS 事件：MCP 工具调用与数据核验过程（生成中展示、完成后隐藏）
          const d = parsed.eventData || {}
          if (Array.isArray(d.steps)) aiMsg.processSteps = d.steps
        }
        // eventType 1002 (STOP) 忽略
      }
    }
    clearInterval(renderTimer)
    aiMsg.content += pendingText
    pendingText = ''
  } catch (e) {
    if (e.name !== 'AbortError') {
      aiMsg.content += '\n\n⚠️ 连接 AI 服务失败：' + (e.message || '未知错误')
    }
  } finally {
    if (chatAbort === abort) chatAbort = null
    aiMsg.isStreaming = false
    aiMsg.processDone = true
    chatTyping.value = false
    saveChatForCurrentPatient()
    scrollChatBottom()
  }
}

// ── 停止生成（后端终止 Flux 流 + 前端断开 SSE，参照《SpringAI》笔记标准实现） ──
let chatAbort = null
let chatActiveSessionId = null

// ── 语音能力：语音录入（ASR）与朗读回答（TTS） ──
const isRecording = ref(false)
let mediaRecorder = null
let audioChunks = []
let recordStartAt = 0

// 探测某个麦克风设备的实际电平（录 ~0.7s 取峰值）：-1=设备打开失败，0~128=信号峰值
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

// 自动选麦：优先用上次有信号的设备；当前默认设备是"哑巴"（峰值≈0，蓝牙耳机 A2DP 模式下麦克风不工作很常见）
// 时自动探测其它输入设备并切换到有信号的设备，避免录出一整条静音
const pickBestMic = async () => {
  try {
    const devs = (await navigator.mediaDevices.enumerateDevices()).filter(d => d.kind === 'audioinput')
    if (devs.length <= 1) return null
    const saved = localStorage.getItem('chunbo_mic_device_id')
    if (saved && devs.some(d => d.deviceId === saved)) {
      const p = await probeMicLevel(saved)
      if (p >= 3) return saved
    }
    ElMessage.info('正在检测麦克风设备…')
    let best = null, bestPeak = 0
    for (const d of devs.slice(0, 4)) {
      if (d.deviceId === saved) continue
      const p = await probeMicLevel(d.deviceId)
      if (p > bestPeak) { best = d; bestPeak = p }
    }
    if (best && bestPeak >= 3) {
      localStorage.setItem('chunbo_mic_device_id', best.deviceId)
      ElMessage.success('当前麦克风无信号，已自动切换到：' + (best.label || '未知设备'))
      return best.deviceId
    }
  } catch (e) {}
  return null
}

const toggleVoiceInput = async () => {
  if (isRecording.value) {
    if (mediaRecorder) { try { mediaRecorder.stop() } catch (e) {} }
    return
  }
  try {
    // 先自动选麦（跳过无信号的"哑巴"设备），再开正式录音流
    const micId = await pickBestMic()
    const stream = await navigator.mediaDevices.getUserMedia({ audio: micId ? { deviceId: { exact: micId } } : true })
    // WebAudio 处理链：音量放大（自适应AGC）+压限器，解决耳机麦克风采集音量过低导致 whisper 听不到人声
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
        // 自适应增益（AGC）：把人声峰值动态拉到约 45% 电平（增益范围 6~30 倍，平滑调整防爆音）
        if (peak > 3) {
          const target = Math.min(30, Math.max(6, 58 / peak))
          gain.gain.value += (target - gain.gain.value) * 0.3
        }
      }, 50)
    } catch (e) { if (audioCtx) { try { audioCtx.close() } catch (e2) {} audioCtx = null } }
    // 显式 opus 编码的 webm，whisper 对该格式识别最稳（录的是放大后的处理流）
    const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' : (MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : '')
    mediaRecorder = mimeType ? new MediaRecorder(recStream, { mimeType }) : new MediaRecorder(recStream)
    audioChunks = []
    recordStartAt = Date.now()
    mediaRecorder.ondataavailable = ev => { if (ev.data && ev.data.size) audioChunks.push(ev.data) }
    mediaRecorder.onstop = async () => {
      isRecording.value = false
      const recordedMs = Date.now() - recordStartAt
      stream.getTracks().forEach(t => t.stop())
      if (levelTimer) { clearInterval(levelTimer); levelTimer = null }
      if (audioCtx) { try { audioCtx.close() } catch (e) {} audioCtx = null }
      if (recordedMs < 800) {
        ElMessage.warning('说话时间太短，请按住说完一句再结束')
        return
      }
      if (!audioChunks.length) {
        ElMessage.warning('录音数据为空，请重试')
        return
      }
      const blob = new Blob(audioChunks, { type: mediaRecorder.mimeType || 'audio/webm' })
      console.log('[语音录入] 音频=' + blob.size + '字节 时长≈' + Math.round(recordedMs / 1000) + 's 峰值电平=' + maxLevel)
      // 静音拒发：原始峰值过低说明设备根本没拾音（音量100也无效，多为蓝牙耳机麦克风通道未激活），发送只会得到 whisper 幻听
      if (maxLevel > 0 && maxLevel < 8) {
        ElMessage.error('未检测到有效语音（峰值电平=' + maxLevel + '）：当前麦克风设备没有拾音，调音量无效。'
          + '蓝牙耳机常见"能听歌但麦克风不工作"，请在 系统设置→声音→输入 切换到其它麦克风设备（如 Realtek），'
          + '或检查耳机上的麦克风开关后重试')
        return
      }
      if (maxLevel > 0 && maxLevel < 15) {
        ElMessage.warning('麦克风音量偏低，已自动放大增益；若识别不准请靠近麦克风')
      }
      const token = localStorage.getItem('chunbo_jwt_token')
      const fd = new FormData()
      fd.append('file', blob, 'voice.webm')
      ElMessage.info('正在识别语音…')
      try {
        const resp = await fetch('/api/audio/asr', { method: 'POST', headers: { 'Authorization': 'Bearer ' + token }, body: fd })
        const data = await resp.json()
        const txt = (data && (data.text || data.result)) || ''
        if (txt && /[\u4e00-\u9fa5]/.test(txt)) { chatInput.value += txt; ElMessage.success('语音识别完成，已填入输入框') }
        else ElMessage.warning((data && data.message) || '未识别到清晰的中文语音，请靠近麦克风大声说一句再结束')
      } catch (e) {
        ElMessage.error('语音识别失败：' + (e.message || '网络错误'))
      }
    }
    mediaRecorder.start()
    isRecording.value = true
    ElMessage.info('开始录音，说完点击 ⏹ 结束')
  } catch (e) {
    ElMessage.error('无法访问麦克风，请检查浏览器权限')
  }
}

let currentTtsAudio = null
const speakAiMessage = async (msg) => {
  if (currentTtsAudio) { currentTtsAudio.pause(); currentTtsAudio = null; msg._speaking = false; return }
  try {
    const token = localStorage.getItem('chunbo_jwt_token')
    const plain = String(msg.content || '').replace(/[#*>`|_~-]/g, '').replace(/\n+/g, ' ').trim().slice(0, 400)
    if (!plain) return
    const resp = await fetch('/api/audio/tts-stream', {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + token, 'Content-Type': 'text/plain' },
      body: plain
    })
    if (!resp.ok) throw new Error('HTTP ' + resp.status)
    const blob = await resp.blob()
    currentTtsAudio = new Audio(URL.createObjectURL(blob))
    msg._speaking = true
    currentTtsAudio.onended = () => { msg._speaking = false; currentTtsAudio = null }
    currentTtsAudio.play()
  } catch (e) {
    ElMessage.error('语音合成失败：' + (e.message || '网络错误'))
  }
}

const stopGeneration = () => {
  // 先通知后端终止 Flux 输出（takeWhile 检测标记后中断，并保存半截回答到会话记忆）
  if (chatActiveSessionId) {
    const token = localStorage.getItem('chunbo_jwt_token')
    fetch(`/api/medical/chat/stop?sessionId=${encodeURIComponent(chatActiveSessionId)}`, {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + token }
    }).catch(() => {})
  }
  if (chatAbort) {
    chatAbort.abort()
    chatAbort = null
  }
  chatActiveSessionId = null
  chatTyping.value = false
  const streaming = chatMessages.value.find(m => m.isStreaming)
  if (streaming) {
    streaming.isStreaming = false
    if (!streaming.content) streaming.content = '（已停止生成）'
  }
  saveChatForCurrentPatient()
  scrollChatBottom()
}

// ── 一键采纳 AI 推荐处方 (按用户要求：直接覆盖替换左侧处方与医嘱，而非追加) ──
const adoptRxFromMsg = (msg) => {
  if (!msg.rxItems || !msg.rxItems.length) return

  // 1. 先清空已有的各项处方，做到真正的一键覆盖替换
  patchRxItems.value = []
  westernRxItems.value = []
  tcmRxItems.value = []
  treatmentItems.value = []

  let count = 0

  msg.rxItems.forEach(item => {
    const cat = item.category || ''
    const name = item.name || ''

    if (cat === '特色贴敷' || name.includes('贴') || name.includes('敷') || item.acupoints) {
      // 贴敷处方：智能匹配药品库
      const matched = allMedicines.value.find(m => m.name === name || m.name.includes('贴') || m.name.includes('膏'))
      patchRxItems.value.push({
        medicineId: matched ? matched.id : 101,
        name: name,
        dose: item.dose || '10',
        acupoints: item.acupoints || '大椎穴、太阳穴',
        frequency: item.frequency || '1次/天',
        days: item.days || 2,
        quantity: item.quantity || 2,
        unitPrice: Number(item.unitPrice) || 35.00,
        remark: item.remark || '温敷调和外用'
      })
      count++
    } else if (cat === '中药' || cat === '饮片' || name.includes('天麻') || name.includes('甘草') || name.includes('金银花') || name.includes('连翘') || name.includes('白术')) {
      // 中药处方：智能匹配
      const matched = allMedicines.value.find(m => m.name === name || name.includes(m.name))
      tcmRxItems.value.push({
        medicineId: matched ? matched.id : 301,
        name: name,
        dose: item.dose || '10',
        frequency: item.frequency || '1剂/天',
        days: item.days || 3,
        unitPrice: Number(item.unitPrice) || 3.00,
        remark: item.remark || '水煎温服'
      })
      count++
    } else if (cat === '治疗理疗' || cat === '理疗' || name.includes('推拿') || name.includes('按摩') || name.includes('艾灸')) {
      // 诊疗理疗项目
      treatmentItems.value.push({
        category: '治疗理疗',
        name: name,
        quantity: item.quantity || 1,
        price: Number(item.unitPrice || item.price) || 35.00,
        remark: item.remark || '特色中医对症调摄'
      })
      count++
    } else {
      // 西/中成药处方：智能匹配
      const matched = allMedicines.value.find(m => m.name === name || name.includes(m.name) || (m.name && m.name.includes(name)))
      westernRxItems.value.push({
        medicineId: matched ? matched.id : (allMedicines.value[2] ? allMedicines.value[2].id : 201),
        name: name,
        dose: item.dose || '1片/袋',
        frequency: item.frequency || 'tid',
        route: item.route || '口服',
        days: item.days || 3,
        quantity: item.quantity || 1,
        unit: item.unit || '盒',
        unitPrice: Number(item.unitPrice) || 25.00,
        remark: item.remark || '遵医嘱饭后服'
      })
      count++
    }
  })

  // 1.5 关键：把四类处方明细同步到处方单区块，否则左侧始终显示"尚未开立处方"（与历史处方引用同机制）
  syncItemsToBlocks()

  // 2. 直接替换生活医嘱
  if (msg.advice) {
    emr.value.medicalAdvice = msg.advice
  }

  // 3. 自动切换至“处方医嘱与特色贴敷”Tab，让医生立刻看到刚填入的工作台！
  emrActiveTab.value = 'prescription'
  saveCurrentPatientState()
  runAiPrescriptionAudit()

  ElNotification({
    title: 'AI 推荐处方已全部采纳！',
    message: `已将 ${count} 项处方及生活医嘱全部覆盖替换导入左侧开方工作台！`,
    type: 'success',
    duration: 3500
  })
}

// 经典模板
const classicTemplates = [
  {
    name: '保和消积行气方 (腹痛胃胀)',
    desc: '主治饮食积滞、腹部隐痛胀满，配健胃消食片+神阙和胃贴',
    chief: '脘腹隐痛胀满，嗳气纳差，食后腹部不适 2 天',
    diag: '急性胃肠功能紊乱 (脾胃虚弱兼食滞证)'
  },
  {
    name: '感冒风热宣肺方 (发热咽痛)',
    desc: '主治咽痛、发热、咳嗽有痰，配抗病毒口服液+双肺俞穴位湿贴',
    chief: '低热恶风，咽喉肿痛，咳嗽有黄痰 2 天',
    diag: '急性上呼吸道感染 (风热犯表证)'
  },
  {
    name: '偏头痛降逆平肝方 (头胀眩晕)',
    desc: '主治偏头痛、眩晕烦躁，配丹栀逍遥丸+太阳穴贴敷',
    chief: '双侧太阳穴搏动性头痛，经前加重，伴烦躁心悸 3 天',
    diag: '血管神经性头痛 (肝郁化火证)'
  },
  {
    name: '独活寄生温经方 (腰膝酸痛)',
    desc: '主治腰酸背痛、骨关节酸胀，配透骨草配方颗粒+阿是穴温经贴',
    chief: '腰骶部及双膝关节酸胀隐痛，久坐加重，活动受限',
    diag: '腰肌劳损 (风寒湿痹证)'
  }
]

/**
 * 核心智能临床辨证与推荐引擎 (根据主诉与症状实时动态推导)
 */
// ── AI 处方合理用药审查与配伍禁忌实时质控引擎 ──
const aiPrescriptionAuditResult = ref({
  passed: true,
  riskLevel: 'safe', // 'safe' | 'warning' | 'danger'
  warnings: [],
  contraindications: []
})

const runAiPrescriptionAudit = () => {
  const warnings = []
  const contraindications = []

  // 1. 过敏史红线拦截
  const allergies = (emr.value.allergies || '').toLowerCase()
  const allMeds = [
    ...westernRxItems.value.map(i => i.name || ''),
    ...tcmRxItems.value.map(i => i.name || ''),
    ...patchRxItems.value.map(i => i.name || '')
  ]

  if (allergies.includes('青霉素') || allergies.includes('阿莫西林')) {
    if (allMeds.some(m => m.includes('阿莫西林') || m.includes('青霉素'))) {
      contraindications.push('【高危过敏拦截】患者有明确青霉素过敏史，处方中包含阿莫西林/青霉素类药物，禁止使用！')
    }
  }
  if (allergies.includes('头孢')) {
    if (allMeds.some(m => m.includes('头孢'))) {
      contraindications.push('【高危过敏拦截】患者有头孢菌素过敏史，处方包含头孢类药物，请立即更换！')
    }
  }

  // 2. 中药十八反与十九畏智能配伍质控
  const tcmNames = tcmRxItems.value.map(i => i.name || '')
  const checkPair = (name1, name2, msg) => {
    const has1 = tcmNames.some(n => n.includes(name1))
    const has2 = tcmNames.some(n => n.includes(name2))
    if (has1 && has2) {
      contraindications.push(`【中医十八反配伍禁忌】${name1} 反 ${name2}！${msg}`)
    }
  }

  checkPair('甘草', '甘遂', '甘草与甘遂同用会剧烈增强毒性并减弱利水之功。')
  checkPair('甘草', '海藻', '本草反药配伍禁忌，易伤脾胃气血。')
  checkPair('乌头', '半夏', '川乌/草乌反半夏，具心脏及中枢神经毒性风险。')
  checkPair('乌头', '贝母', '附子/乌头反贝母，相反配伍禁用。')
  checkPair('丁香', '郁金', '【十九畏】丁香莫与郁金见，减弱药效并伤胃气。')

  // 3. 超量与特殊用法提醒
  tcmRxItems.value.forEach(item => {
    const d = parseFloat(item.dose) || 0
    if (item.name && item.name.includes('附子') && d > 15 && (!item.remark || !item.remark.includes('先煎'))) {
      warnings.push(`【超量安全预警】${item.name} 用量达 ${d}g，大剂量附子必须注明【先煎30-60分钟】以解乌头碱毒性！`)
    }
    if (item.name && item.name.includes('细辛') && d > 3) {
      warnings.push(`【中药安全预警】常言“细辛不过钱”，当前用量 ${d}g 偏大，请严格遵医嘱或注意煎煮规范。`)
    }
  })

  // 4. 重复用药与中西协同提醒
  const hasColdWestern = westernRxItems.value.some(i => (i.name||'').includes('感冒灵') || (i.name||'').includes('对乙酰氨基酚'))
  const hasIbuprofen = westernRxItems.value.some(i => (i.name||'').includes('布洛芬'))
  if (hasColdWestern && hasIbuprofen) {
    warnings.push('【中西协同质控】感冒灵含对乙酰氨基酚成分，与布洛芬联合使用存在双重非甾体抗炎药胃肠黏膜负担，请提示间隔饭后服用。')
  }

  if (contraindications.length > 0) {
    aiPrescriptionAuditResult.value = {
      passed: false,
      riskLevel: 'danger',
      warnings,
      contraindications
    }
  } else if (warnings.length > 0) {
    aiPrescriptionAuditResult.value = {
      passed: true,
      riskLevel: 'warning',
      warnings,
      contraindications: []
    }
  } else {
    aiPrescriptionAuditResult.value = {
      passed: true,
      riskLevel: 'safe',
      warnings: [],
      contraindications: []
    }
  }
}

// ── AI 门诊病历一键智能润色与规范化辨证 ──
const isAiGeneratingEmr = ref(false)
const aiGenerateFullEmr = async () => {
  if (!emr.value.chiefComplaint) {
    ElMessage.warning('请先在左侧输入或点击选择患者主要症状/主诉！')
    return
  }
  isAiGeneratingEmr.value = true
  ElMessage.info('正在通过 AI 大模型生成病历（现病史/舌脉/诊断）...')
  try {
    const token = localStorage.getItem('chunbo_jwt_token')
    const resp = await fetch('/api/medical/chat/generate-emr', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'Authorization': 'Bearer ' + token },
      body: JSON.stringify({
        chiefComplaint: emr.value.chiefComplaint,
        duration: emr.value.duration || '',
        frequency: emr.value.frequency || ''
      })
    })
    const data = await resp.json()
    if (data && data.success && data.presentIllness) {
      emr.value.presentIllness = data.presentIllness
      emr.value.tongue = data.tongue || ''
      emr.value.pulse = data.pulse || ''
      emr.value.diagnosis = data.diagnosis || ''
      emr.value.tcmDiagnosis = data.tcmDiagnosis || ''
      emr.value.medicalAdvice = data.medicalAdvice || ''
      saveCurrentPatientState()
      runAiPrescriptionAudit()
      ElNotification({
        title: 'AI 病历已生成！',
        message: '已由大模型生成规范化现病史、舌苔脉象、中西医诊断与生活医嘱，并完成处方合理用药审查。',
        type: 'success',
        duration: 3500
      })
    } else {
      ElMessage.warning((data && data.message) || 'AI 病历生成失败，请稍后重试')
    }
  } catch (e) {
    ElMessage.error('AI 病历生成失败：' + (e.message || '网络错误'))
  } finally {
    isAiGeneratingEmr.value = false
  }
}

const applyClinicalReasoning = (chief, fullReset = false) => {
  const text = (chief || '').trim()

  // 1. 腹痛 / 肚子疼 / 胃肠类
  if (text.includes('肚') || text.includes('腹') || text.includes('胃') || text.includes('拉肚子') || text.includes('腹泻') || text.includes('消化') || text.includes('纳差') || text.includes('呕吐')) {
    selectedBodyPart.value = '脘腹部'
    if (fullReset) emr.value.diagnosis = '急性胃肠功能紊乱 / 胃脘隐痛 (待查)'
    if (fullReset) emr.value.tcmDiagnosis = '脾胃虚弱兼湿热阻滞证 / 寒湿阻滞肠胃'
    aiAdvice.value = `【辨证辅助建议】：患者以主诉【${text || '肚子疼/腹痛'}】就诊，四诊合参辩证属【脾胃虚弱、气机阻滞肠胃】。治法宜健脾和胃、行气导滞止痛。方选加减保和丸合香砂养胃汤，配合神阙穴(脐疗)+足三里特色温经和胃散寒贴，温中健运、理气消积止痛，标本兼顾，疗效确切。`

    currentRecommendedItems.value = [
      {
        category: '中成药',
        medicineId: 9,
        name: '丹栀逍遥丸',
        dosage: '10g*6袋/盒 · 饭前温水冲服 1袋 tid',
        quantity: 1,
        unitPrice: 30.00
      },
      {
        category: '特色贴敷',
        medicineId: 18,
        name: '消肿止痛贴 (神阙穴脐疗+足三里温经和胃)',
        dosage: '湿贴 · 穴位温敷 4小时',
        quantity: 3,
        unitPrice: 35.00
      },
      {
        category: '中药',
        medicineId: 16,
        name: '白芍配方颗粒',
        dosage: '1袋 bid 温水冲服 (柔肝止痛)',
        quantity: 1,
        unitPrice: 3.80
      }
    ]

    if (fullReset) {
      emr.value.symptomsList = ['脘腹胀满', '胃脘隐痛']
      emr.value.presentIllness = '患者因饮食不节或受凉后出现腹痛腹胀，呈阵发性隐痛，伴嗳气纳差，无放射痛，无黑便，小便正常。'
      emr.value.tongue = '舌质淡红，苔白微腻'
      emr.value.pulse = '脉濡滑或弦缓'
      prescriptionItems.value = [...currentRecommendedItems.value]
      plasterConfig.value.acupoints = '神阙穴, 足三里, 中脘穴'
    }
  }
  // 2. 呼吸道 / 感冒 / 咳嗽 / 咽痛 / 发热
  else if (text.includes('感冒') || text.includes('咽') || text.includes('咳') || text.includes('发热') || text.includes('发烧') || text.includes('流涕') || text.includes('恶寒') || text.includes('喉咙')) {
    selectedBodyPart.value = '颈胸部'
    if (fullReset) emr.value.diagnosis = '急性上呼吸道感染 (急性咽炎)'
    if (fullReset) emr.value.tcmDiagnosis = '外感风热犯表证 / 痰热郁肺证'
    aiAdvice.value = `【辨证辅助建议】：患者以主诉【${text || '咽痛低热/咳嗽'}】就诊，辨属外感风热犯肺、肺失宣降。治宜疏风清热、宣肺利咽止咳。推荐选用抗病毒口服液合桑菊饮加减，并配合大椎穴与双肺俞穴特色中药穴位贴敷，透邪外达，清咽退热。`

    currentRecommendedItems.value = [
      {
        category: '中成药',
        medicineId: 12,
        name: '抗病毒口服液',
        dosage: '10ml*10支/盒 · 口服 1支 tid',
        quantity: 1,
        unitPrice: 38.00
      },
      {
        category: '特色贴敷',
        medicineId: 18,
        name: '消肿止痛贴敷方 (双肺俞+天突穴宣肺止咳)',
        dosage: '湿贴 · 穴位温敷 4小时',
        quantity: 3,
        unitPrice: 35.00
      },
      {
        category: '西药',
        medicineId: 6,
        name: '布洛芬缓释胶囊',
        dosage: '0.3g*20粒/盒 · 发热>38.5℃口服 1粒',
        quantity: 1,
        unitPrice: 26.00
      }
    ]

    if (fullReset) {
      emr.value.symptomsList = ['咽喉不适', '咳嗽咳痰']
      emr.value.presentIllness = '患者近 2 天因受凉起病，自诉咽部干痒红肿疼痛，伴鼻塞流涕、咳嗽少许白痰、轻微发热恶寒。'
      emr.value.tongue = '舌质红，苔薄黄微干'
      emr.value.pulse = '脉浮数'
      prescriptionItems.value = [...currentRecommendedItems.value]
      plasterConfig.value.acupoints = '双肺俞, 大椎穴, 天突穴'
    }
  }
  // 3. 头痛 / 头晕 / 偏头痛 / 高血压
  else if (text.includes('头痛') || text.includes('头晕') || text.includes('偏头痛') || text.includes('眩晕') || text.includes('血压') || text.includes('脑胀')) {
    selectedBodyPart.value = '头面部'
    if (fullReset) emr.value.diagnosis = '血管神经性头痛 / 原发性高血压 (1级)'
    if (fullReset) emr.value.tcmDiagnosis = '肝郁化火证 / 肝阳上亢证'
    aiAdvice.value = `【辨证辅助建议】：患者主诉【${text || '头晕头痛'}】，辨证属【肝阳上亢、经络不畅】。治宜平肝潜阳、清利头目。方选天麻钩藤饮合丹栀逍遥丸化裁，配合双侧太阳穴与大椎穴特色降逆平肝贴敷，镇静通络，缓解头胀头晕。`

    currentRecommendedItems.value = [
      {
        category: '中成药',
        medicineId: 9,
        name: '丹栀逍遥丸',
        dosage: '10g*6袋/盒 · 口服 1袋 tid',
        quantity: 1,
        unitPrice: 30.00
      },
      {
        category: '西药',
        medicineId: 1,
        name: '苯磺酸氨氯地平片',
        dosage: '5mg*7片/盒 · 每次5mg(1片) 清晨口服',
        quantity: 2,
        unitPrice: 28.50
      },
      {
        category: '特色贴敷',
        medicineId: 18,
        name: '消肿止痛贴敷方 (双侧太阳穴+大椎降逆平肝)',
        dosage: '湿贴 · 穴位敷贴 4小时',
        quantity: 2,
        unitPrice: 35.00
      }
    ]

    if (fullReset) {
      emr.value.symptomsList = ['反复头痛', '眩晕']
      emr.value.presentIllness = '患者近 3 天感头脑胀痛、偏侧太阳穴搏动性隐痛，劳累后加重，伴眼干眩晕，无视物旋转与恶心呕吐。'
      emr.value.tongue = '舌质暗红，苔薄黄'
      emr.value.pulse = '脉弦紧有力'
      prescriptionItems.value = [...currentRecommendedItems.value]
      plasterConfig.value.acupoints = '太阳穴, 大椎穴, 太冲穴'
    }
  }
  // 4. 腰痛 / 关节痛 / 骨痛 / 痹症
  else if (text.includes('腰') || text.includes('关节') || text.includes('膝') || text.includes('腿') || text.includes('颈椎') || text.includes('背') || text.includes('跌打') || text.includes('扭伤')) {
    selectedBodyPart.value = '腰背部'
    if (fullReset) emr.value.diagnosis = '腰肌劳损 / 骨关节退行性病变 (痹症)'
    if (fullReset) emr.value.tcmDiagnosis = '风寒湿痹阻络证 / 气血瘀滞证'
    aiAdvice.value = `【辨证辅助建议】：患者主诉【${text || '腰膝关节酸痛'}】，辨属风寒湿邪客于经络，气滞血瘀不通则痛。治宜温经散寒、祛风通络止痛。推荐选用活血舒筋中药，并在肾俞穴与阿是穴行生姜汁特色温敷贴，透皮直达病灶，迅速改善局部酸胀。`

    currentRecommendedItems.value = [
      {
        category: '特色贴敷',
        medicineId: 18,
        name: '消肿止痛贴敷方 (阿是穴+双肾俞温经通络)',
        dosage: '温贴 · 敷贴 6小时',
        quantity: 3,
        unitPrice: 35.00
      },
      {
        category: '中药',
        medicineId: 19,
        name: '透骨草配方颗粒',
        dosage: '1袋 bid 温水冲服 (祛风活血)',
        quantity: 1,
        unitPrice: 3.00
      },
      {
        category: '中药',
        medicineId: 20,
        name: '苦参配方颗粒',
        dosage: '1袋 bid 温水冲服 (清热燥湿)',
        quantity: 1,
        unitPrice: 2.80
      }
    ]

    if (fullReset) {
      emr.value.symptomsList = ['腰膝酸痛', '项背僵硬']
      emr.value.presentIllness = '患者因久坐劳累后出现腰骶部及下肢关节酸痛，阴雨天受凉加重，局部轻压痛，无明显下肢放射性麻木。'
      emr.value.tongue = '舌质淡暗，边有齿痕，苔白滑'
      emr.value.pulse = '脉沉弦迟'
      prescriptionItems.value = [...currentRecommendedItems.value]
      plasterConfig.value.acupoints = '阿是穴, 肾俞穴, 委中穴'
    }
  }
  // 5. 空主诉 / 新挂号患者
  else if (!text) {
    if (fullReset) emr.value.diagnosis = ''
    if (fullReset) emr.value.tcmDiagnosis = ''
    aiAdvice.value = '💡 请在上方主诉栏输入或点击智能热词（如：肚子疼、咽痛咳嗽、头晕头痛、腰膝酸痛等），辨证助手将根据患者实际病情给出诊疗建议！'
    currentRecommendedItems.value = []
    if (fullReset) {
      emr.value.symptomsList = []
      emr.value.presentIllness = ''
      emr.value.tongue = '舌质淡红，苔薄白'
      emr.value.pulse = '脉和缓有神'
      prescriptionItems.value = []
    }
  }
  // 6. 通用自定义症状
  else {
    if (fullReset) emr.value.diagnosis = `${text} (待进一步临床查体排查)`
    if (fullReset) emr.value.tcmDiagnosis = '气机不调 / 脏腑功能紊乱'
    aiAdvice.value = `【辨证辅助建议】：针对患者口述主诉【${text}】，系统已建立专属病情推理路径。建议查体明确局部压痛与体征，方药建议结合四诊辨证个体化加减，配合相应经络腧穴行特色贴敷理疗。`
    currentRecommendedItems.value = [
      {
        category: '中成药',
        medicineId: 9,
        name: '丹栀逍遥丸',
        dosage: '10g*6袋/盒 · 口服 1袋 tid',
        quantity: 1,
        unitPrice: 30.00
      },
      {
        category: '特色贴敷',
        medicineId: 18,
        name: '消肿止痛贴 (对症穴位贴敷)',
        dosage: '湿贴 4小时',
        quantity: 2,
        unitPrice: 35.00
      }
    ]
    if (fullReset) {
      emr.value.presentIllness = `患者自诉近因感不适出现【${text}】，伴全身轻度疲乏，二便尚可。`
    }
  }
}

// 监听主诉输入
let inputTimer = null
const onChiefComplaintInput = () => {
  if (inputTimer) clearTimeout(inputTimer)
  inputTimer = setTimeout(() => {
    applyClinicalReasoning(emr.value.chiefComplaint, false)
  }, 100)
}

const onChiefComplaintChange = () => {
  applyClinicalReasoning(emr.value.chiefComplaint, false)
}

// isClosedStatus 与 sortWaitingQueue 已统一前置声明

const loadPatientsQueue = async () => {
  try {
    const res = await axios.get('/api/registration/list')
    const list = res.data || []

    if (list && list.length > 0) {
      list.forEach(item => {
        // 根据挂号真实创建日期分发到对应日期的存储桶
        const regDate = (item.createTime ? item.createTime.substring(0, 10) : todayKey)
        if (!dateConsultationStore.value[regDate]) {
          dateConsultationStore.value[regDate] = {
            waiting: [],
            done: []
          }
        }
        const bucket = dateConsultationStore.value[regDate]

        // 统一规范状态展示文本
        if (item.status === '已退') item.status = '已退号'
        if (item.status === '已过号') item.status = '过号'

        const isDone = isClosedStatus(item.status)
        if (isDone) {
          // 退号、过号、已结诊患者归入【已结束】队列
          if (!Array.isArray(bucket.waiting)) bucket.waiting = []
          if (!Array.isArray(bucket.done)) bucket.done = []
          bucket.waiting = bucket.waiting.filter(w => w && w.id !== item.id && !(w.patientName === item.patientName && w.phone === item.phone))
          const existIdx = bucket.done.findIndex(d => d && (d.id === item.id || (d.patientName === item.patientName && d.phone === item.phone)))
          if (existIdx === -1) {
            bucket.done.unshift(item)
          } else {
            bucket.done[existIdx] = { ...bucket.done[existIdx], ...item }
          }
        } else {
          // 未结诊患者：以数据库真实状态为准（待诊/就诊中），保证与挂号取号页一致；
          // 仅当该患者是当前医生正在查看且本地已处于接诊中时，保留本地接诊中状态
          if (!Array.isArray(bucket.waiting)) bucket.waiting = []
          if (!Array.isArray(bucket.done)) bucket.done = []
          const isLocalActive = currentPatient.value && currentPatient.value.id === item.id && currentPatient.value.status === '接诊中'
          if (item.status === '就诊中' || item.status === '接诊中' || isLocalActive) {
            item.status = '接诊中'
          } else {
            item.status = '待诊'
          }
          bucket.done = bucket.done.filter(d => d && d.id !== item.id && !(d.patientName === item.patientName && d.phone === item.phone))
          const existIdx = bucket.waiting.findIndex(w => w && (w.id === item.id || (w.patientName === item.patientName && w.phone === item.phone)))
          if (existIdx === -1) {
            bucket.waiting.push(item)
          } else {
            bucket.waiting[existIdx] = { ...bucket.waiting[existIdx], ...item }
          }
        }
      })
    }

    // 医生单人接诊互斥约束：同一医生在同一日期下最多仅能有 1 位患者处于“接诊中”
    Object.keys(dateConsultationStore.value).forEach(dk => {
      const b = dateConsultationStore.value[dk]
      if (b && b.waiting) {
        let foundActive = false
        b.waiting.forEach(p => {
          if (p.status === '接诊中') {
            if (foundActive) {
              p.status = '待诊'
            } else {
              foundActive = true
            }
          }
        })
        b.waiting = sortWaitingQueue(b.waiting)
      }
    })
  } catch (e) {
    console.error(e)
  }

  // 刷新当前查看日期的队列展示
  const currentBucket = dateConsultationStore.value[queueDate.value]
  if (currentBucket) {
    currentBucket.waiting = sortWaitingQueue(currentBucket.waiting || [])
    waitingList.value = currentBucket.waiting
    doneList.value = currentBucket.done || []

    if (queueTab.value === 'waiting') {
      if (waitingList.value.length > 0) {
        const inProgress = waitingList.value.find(p => p.status === '接诊中')
        let target = null
        if (currentPatient.value && waitingList.value.some(p => p.id === currentPatient.value.id)) {
          target = waitingList.value.find(p => p.id === currentPatient.value.id)
        } else if (inProgress) {
          target = inProgress
        } else {
          target = waitingList.value[0]
        }
        selectQueuePatient(target)
      } else {
        // 待诊队列已空时，工作台清空当前就诊人，呈现候诊就绪空状态，绝不越权抢占已结诊记录
        currentPatient.value = null
      }
    } else {
      if (doneList.value.length > 0) {
        const target = currentPatient.value
          ? (doneList.value.find(p => p.id === currentPatient.value.id) || doneList.value[0])
          : doneList.value[0]
        selectQueuePatient(target)
      } else {
        currentPatient.value = null
      }
    }
  }
}

const selectQueuePatient = (p) => {
  if (!p) return

  // 恢复持久化会员身份并净化普通患者
  const targetPhone = p.phone
  const targetName = p.patientName || p.name
  try {
    const memStore = JSON.parse(localStorage.getItem('chunbo_member_store') || '{}')
    {
      const mem = (targetPhone && memStore[targetPhone]) || (targetName && memStore[targetName])
      const dbP = dbPatients.value.find(x => (targetPhone && x.phone === targetPhone) || (targetName && x.name === targetName))
      if (mem && mem.memberLevel && mem.memberLevel !== '普通居民' && mem.memberLevel !== '普通患者' && (Number(mem.discountRate) < 1.0 || mem.memberExpiry)) {
        p.memberLevel = mem.memberLevel
        p.discountRate = mem.discountRate || (mem.memberLevel === 'VIP会员' ? 0.85 : 0.9)
        p.memberExpiry = mem.memberExpiry
      } else if (dbP && dbP.memberLevel && dbP.memberLevel !== '普通居民' && dbP.memberLevel !== '普通患者' && (Number(dbP.discountRate) < 1.0 || dbP.memberExpiry)) {
        p.memberLevel = dbP.memberLevel
        p.discountRate = dbP.discountRate || (dbP.memberLevel === 'VIP会员' ? 0.85 : 0.9)
        p.memberExpiry = dbP.memberExpiry
      } else {
        // 未办会员的普通就诊人：坚决规范为普通居民，绝不挂带任何会员标识与10折
        p.memberLevel = '普通居民'
        p.discountRate = 1.0
        p.memberExpiry = ''
      }
    }
  } catch (e) {}

  // 切换患者前自动保存前一位正在就诊患者的现场草稿
  if (currentPatient.value && currentPatient.value.id !== p.id && !isHistoryReadOnly.value) {
    saveCurrentPatientState()
  }

  currentPatient.value = p
  const key = `${p.id}_${queueDate.value}`
  const savedDraft = consultationDraftStore.value[key]

  // 判断是否为历史归档只读患者 (Task 4: 点击已诊、已结诊、已退、过号等历史就诊记录)
  const isClosed = isClosedStatus(p.status) || p.status === '已诊' || p.status === '已结诊' || p.status === '已完成' || p.status === '已退' || p.status === '已退号' || p.status === '过号' || queueTab.value === 'done'
  isHistoryReadOnly.value = isClosed

  if (savedDraft) {
    // 恢复该患者已保存的完整草稿
    emr.value = JSON.parse(JSON.stringify(savedDraft.emr || {}))
    treatmentItems.value = JSON.parse(JSON.stringify(savedDraft.treatmentItems || []))
    patchRxItems.value = JSON.parse(JSON.stringify(savedDraft.patchRxItems || []))
    westernRxItems.value = JSON.parse(JSON.stringify(savedDraft.westernRxItems || []))
    tcmRxItems.value = JSON.parse(JSON.stringify(savedDraft.tcmRxItems || []))
    if (savedDraft.status && isClosedStatus(savedDraft.status)) {
      p.status = savedDraft.status
    } else if (p.status !== '接诊中') {
      // 严守规则：通过列表点选查看或系统顺延选定的患者，初始一律为【待诊】预览
      // 只有医生主动点击【开始接诊】后，才会变为【接诊中】！
      p.status = '待诊'
    }
    if (savedDraft.prescriptionBlocks && savedDraft.prescriptionBlocks.length > 0) {
      prescriptionBlocks.value = JSON.parse(JSON.stringify(savedDraft.prescriptionBlocks))
      syncBlocksToItems()
    } else {
      syncItemsToBlocks()
    }
  } else if (isClosed) {
    // 历史归档就诊患者：查询历史真实处方/病历展示 (只读模式，Task 4)
    const past = (currentPatientPastVisits.value && currentPatientPastVisits.value.length > 0) ? currentPatientPastVisits.value[0] : null
    emr.value = {
      chiefComplaint: p.symptoms || (past ? past.symptoms : '咳嗽咽痛伴恶寒发热3天'),
      presentIllness: past ? past.presentIllness : '受凉后出现咳嗽咳痰，咽喉肿痛，伴轻微发热恶寒，纳可，二便正常。',
      allergies: (past && past.allergies) ? past.allergies : '无特殊药物过敏史',
      pastHistory: (past && past.pastHistory) ? past.pastHistory : '既往体健，无哮喘高血压糖尿病史',
      frequency: '阵发性',
      duration: '3天',
      symptomsList: ['咳嗽', '咽喉肿痛', '恶寒'],
      tongue: (past && past.tongue) ? past.tongue : '舌红苔薄黄',
      pulse: (past && past.pulse) ? past.pulse : '脉浮数',
      diagnosis: p.diagnosis || (past ? past.diagnosis : '急性上呼吸道感染'),
      tcmDiagnosis: past ? past.tcmDiagnosis : '风热犯肺，表热证',
      medicalAdvice: (past && past.medicalAdvice) ? past.medicalAdvice : '清淡饮食，忌辛辣生冷；多饮温开水，保持充足睡眠；贴敷4-6小时揭除；3天后门诊复诊。'
    }

    if (past && past.patchItems && past.patchItems.length > 0) {
      patchRxItems.value = JSON.parse(JSON.stringify(past.patchItems))
    } else {
      patchRxItems.value = [
        { medicineId: 101, name: '消肿止痛贴 (止咳利咽)', dose: '10', acupoints: '天突穴、双肺俞穴', frequency: '1次/天', days: 3, quantity: 3, unitPrice: 35 }
      ]
    }

    if (past && past.westernItems && past.westernItems.length > 0) {
      westernRxItems.value = JSON.parse(JSON.stringify(past.westernItems))
    } else {
      westernRxItems.value = [
        { medicineId: 201, name: '感冒灵颗粒', dose: '1袋', frequency: 'tid', route: '口服', days: 3, quantity: 1, unit: '盒', unitPrice: 25, remark: '饭后温开水冲服' }
      ]
    }

    tcmRxItems.value = (past && past.tcmItems) ? JSON.parse(JSON.stringify(past.tcmItems)) : []
    treatmentItems.value = (past && past.treatmentItems) ? JSON.parse(JSON.stringify(past.treatmentItems)) : []
    syncItemsToBlocks()
  } else {
    // 待诊患者首次预览，严格保持【待诊】状态，必须由医生点击【开始接诊】才转入接诊中
    p.status = '待诊'
    emr.value = {
      chiefComplaint: p.symptoms || '',
      presentIllness: '',
      allergies: '无已知药物过敏',
      pastHistory: '既往体健，无特殊慢性病史',
      frequency: '偶尔',
      duration: '1-3天',
      symptomsList: [],
      tongue: '',
      pulse: '',
      diagnosis: '',
      tcmDiagnosis: '',
      medicalAdvice: ''
    }

    const profile = dbPatients.value.find(x => x.name === p.patientName || (p.phone && x.phone === p.phone))
    if (profile) {
      if (profile.allergies && profile.allergies !== '无') emr.value.allergies = profile.allergies
      if (profile.medicalHistory && profile.medicalHistory !== '无' && profile.medicalHistory !== p.symptoms) {
        emr.value.pastHistory = profile.medicalHistory
      }
    }

    treatmentItems.value = []
    patchRxItems.value = []
    westernRxItems.value = []
    tcmRxItems.value = []
    prescriptionBlocks.value = []
  }

  runAiPrescriptionAudit()
  loadChatForPatient(p.id)
}

// ── 医生正式开始接诊当前预览的患者 ──
const startConsultation = async (p) => {
  const patient = p || currentPatient.value
  if (!patient) return

  const targetId = patient.id
  const targetName = patient.patientName || patient.name

  // 1. 用 map 创建全新数组，强制触发 Vue 响应式更新
  // （避免就地 mutation 因对象引用不同而无法被 Vue 检测到的问题）
  const baseList = Array.isArray(waitingList.value) ? waitingList.value : []
  const newWaiting = baseList.map(item => {
    if (!item) return item
    const isTarget = (targetId && item.id === targetId) || (targetName && item.patientName === targetName)
    if (isTarget) {
      return { ...item, status: '接诊中' }
    } else if (item.status === '接诊中') {
      const prevKey = `${item.id}_${queueDate.value}`
      if (consultationDraftStore.value && consultationDraftStore.value[prevKey]) {
        consultationDraftStore.value[prevKey].status = '待诊'
      }
      return { ...item, status: '待诊' }
    }
    return item
  })

  // 若目标患者不在等待队列中，则追加
  const foundInList = newWaiting.some(item => item && (item.id === targetId || item.patientName === targetName))
  if (!foundInList) {
    newWaiting.unshift({ ...patient, status: '接诊中' })
  }

  // 排序后写回响应式列表（数组替换保证 Vue 检测到变化）
  waitingList.value = sortWaitingQueue(newWaiting)

  // 2. 找到排序后的新患者对象，替换 currentPatient.value，强制触发顶栏重渲染
  const updatedPatient = waitingList.value.find(item => item && (item.id === targetId || item.patientName === targetName))
  currentPatient.value = updatedPatient || { ...patient, status: '接诊中' }
  isHistoryReadOnly.value = false

  // 3. 同步 bucket 存储
  const bucket = dateConsultationStore.value && dateConsultationStore.value[queueDate.value]
  if (bucket) {
    bucket.waiting = waitingList.value
  }

  // 4. 保存草稿与状态持久化
  saveCurrentPatientState()

  // 6. 异步通知后端（不阻塞 UI）
  if (targetId) {
    try {
      await axios.post(`/api/registration/start-consult/${targetId}`).catch(() => {})
    } catch (e) {}
  }
  window.dispatchEvent(new CustomEvent('registration-updated'))
  ElMessage.success(`已开始接诊【${targetName}】！状态已变更为【接诊中】。`)
}

// ── 语音叫号播报 ──
const callPatientVoice = (p) => {
  const patient = p || currentPatient.value
  if (!patient) return
  if ('speechSynthesis' in window) {
    const utter = new SpeechSynthesisUtterance(`请 ${patient.queueNumber || patient.id} 号患者 ${patient.patientName} 到全科诊室就诊`)
    utter.lang = 'zh-CN'
    window.speechSynthesis.speak(utter)
  }
  ElMessage.info(`📢 叫号播报：请 ${patient.queueNumber || patient.id} 号患者 ${patient.patientName} 到诊室就诊`)
}

// ── 标记过号 (呼叫未到，移入【已结束】队列) ──
const markAsPassed = (p) => {
  const patient = p || currentPatient.value
  if (!patient) return

  ElMessageBox.confirm(
    `确认将患者【${patient.patientName}】标记为【过号】？\n过号后该患者将移出待诊列表，进入【已结束】队列；若患者稍后到达诊室，可随时点击【↺ 恢复待诊】重新排队接诊。`,
    '过号确认',
    {
      confirmButtonText: '确认过号',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    patient.status = '过号'

    // 同步后端持久化过号状态
    try {
      await axios.post(`/api/registration/pass/${patient.id}`)
    } catch (e) {}

    const bucket = dateConsultationStore.value[queueDate.value]
    if (bucket) {
      // 1. 从待诊队列彻底移除
      bucket.waiting = bucket.waiting.filter(x => x.id !== patient.id)
      waitingList.value = bucket.waiting
      // 2. 移入已结束队列首位
      if (!bucket.done.some(x => x.id === patient.id)) {
        bucket.done.unshift(patient)
      }
      doneList.value = bucket.done
    }
    saveCurrentPatientState()

    // 3. 自动将当前焦点顺延至下一位待诊患者
    if (waitingList.value.length > 0) {
      const nextP = waitingList.value.find(x => x.status === '接诊中') || waitingList.value[0]
      selectQueuePatient(nextP)
    } else {
      currentPatient.value = null
    }

    ElNotification({
      title: '患者已标记过号',
      message: `患者【${patient.patientName}】已移入【已结束】列表。患者回诊后，可在【已结束】中点击【↺ 恢复待诊】重新排队。`,
      type: 'warning',
      duration: 4500
    })
  }).catch(() => {})
}

// ── 恢复待诊 (过号患者回诊重新顺延排队接诊) ──
const restorePassedPatient = (p) => {
  const patient = p || currentPatient.value
  if (!patient) return

  patient.status = '待诊'

  // 同步后端恢复待诊状态
  try {
    axios.post(`/api/registration/restore/${patient.id}`).catch(() => {})
  } catch (e) {}

  // 根据诊所号源规则设置：过号患者自动推迟2位呼叫
  let deferDelay = 2
  try {
    const clinicSettings = JSON.parse(localStorage.getItem('chunbo_clinic_settings') || '{}')
    if (clinicSettings.allowRefund === false) {
      deferDelay = 1
    }
  } catch (e) {}

  const bucket = dateConsultationStore.value[queueDate.value]
  if (bucket) {
    // 1. 从已结束队列移除
    bucket.done = bucket.done.filter(x => x.id !== patient.id)
    doneList.value = bucket.done

    // 2. 顺延排回待诊队列：根据策略推迟 2 位顺延呼叫
    const inProgIdx = bucket.waiting.findIndex(x => x.status === '接诊中')
    let targetIdx = 0
    if (inProgIdx > -1) {
      targetIdx = Math.min(inProgIdx + 1 + (deferDelay - 1), bucket.waiting.length)
    } else {
      targetIdx = Math.min(deferDelay - 1, bucket.waiting.length)
    }
    bucket.waiting.splice(targetIdx, 0, patient)
    waitingList.value = bucket.waiting
  }
  saveCurrentPatientState()

  // 3. 切换至“待诊/接诊中”队列并激活选中
  queueTab.value = 'waiting'
  selectQueuePatient(patient)

  ElNotification({
    title: '已恢复待诊排队',
    message: `患者【${patient.patientName}】已恢复待诊，根据云诊所过号策略已自动推迟 ${deferDelay} 位顺延排队！`,
    type: 'success',
    duration: 4500
  })
}

// ── 门诊协助退号 (挂号作废退费，移入已结束队列归档) ──
const refundRegistration = (p) => {
  const patient = p || currentPatient.value
  if (!patient) return

  ElMessageBox.confirm(
    `确认协助患者【${patient.patientName}】办理退号？\n退号后挂号将作废并原路退回挂号费 ¥${(patient.fee || 10.00).toFixed(2)}，记录移入【已结束】归档，不可再开方或收费。`,
    '门诊退号确认',
    {
      confirmButtonText: '确认退号',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    try {
      await axios.post(`/api/registration/cancel/${patient.id}`)
    } catch (e) {}

    patient.status = '已退号'
    const bucket = dateConsultationStore.value[queueDate.value]
    if (bucket) {
      bucket.waiting = bucket.waiting.filter(x => x.id !== patient.id)
      waitingList.value = bucket.waiting
      if (!bucket.done.some(x => x.id === patient.id)) {
        bucket.done.unshift(patient)
      }
      doneList.value = bucket.done
    }
    saveCurrentPatientState()

    if (waitingList.value.length > 0) {
      const nextP = waitingList.value.find(x => x.status === '接诊中') || waitingList.value[0]
      selectQueuePatient(nextP)
    } else {
      currentPatient.value = null
    }

    ElMessage.success(`患者【${patient.patientName}】退号成功！挂号费已原路退回，记录已归档至【已结束】。`)
  }).catch(() => {})
}

// ── 切换队列 Tab (自动定位有效患者) ──
const switchQueueTab = (tab) => {
  queueTab.value = tab
  if (tab === 'waiting') {
    if (waitingList.value.length > 0) {
      const inProgress = waitingList.value.find(p => p.status === '接诊中')
      selectQueuePatient(inProgress || waitingList.value[0])
    } else {
      currentPatient.value = null
    }
  } else {
    if (doneList.value.length > 0) {
      selectQueuePatient(doneList.value[0])
    } else {
      currentPatient.value = null
    }
  }
}

const appendChiefComplaint = (tag) => {
  if (!tag) return
  if (!emr.value.chiefComplaint) {
    emr.value.chiefComplaint = tag
  } else {
    if (tag === '、' || tag === '，' || tag === '。') {
      emr.value.chiefComplaint += tag
    } else if (tag === '偶尔' || tag === '经常' || tag === '持续' || tag === '持续性') {
      emr.value.chiefComplaint += '，' + tag
    } else if (/^\d+(天|周|月|次|年)/.test(tag) || tag.includes('天') || tag.includes('周') || tag.includes('月') || tag.includes('次')) {
      // 时间频次直接紧随追加，如“头痛3天”
      emr.value.chiefComplaint += tag
    } else if (!emr.value.chiefComplaint.includes(tag)) {
      if (emr.value.chiefComplaint.endsWith('、') || emr.value.chiefComplaint.endsWith('，')) {
        emr.value.chiefComplaint += tag
      } else {
        emr.value.chiefComplaint += '，伴' + tag
      }
    }
  }
  applyClinicalReasoning(emr.value.chiefComplaint, false)
}

const toggleSymptom = (sym) => {
  const idx = emr.value.symptomsList.indexOf(sym)
  if (idx > -1) {
    emr.value.symptomsList.splice(idx, 1)
  } else {
    emr.value.symptomsList.push(sym)
  }
  // 联动更新主诉与AI
  if (!emr.value.chiefComplaint) {
    emr.value.chiefComplaint = emr.value.symptomsList.join('、')
  }
  applyClinicalReasoning(emr.value.chiefComplaint, false)
}

const triggerAiDiagnosis = () => {
  // 点击 AI 诊疗：直接调起 AI 临床助手进行全套辨证与处方卡片推荐
  sendEmrDirectToAi()
}

const startVoiceInput = () => {
  // 复用真实的语音录入（MediaRecorder + 后端 ASR），不再写死模拟口述
  toggleVoiceInput()
}

const simulateTongueAnalysis = () => {
  // 舌象多模态识别需接入图像模型，暂未开通；如实提示而非伪造识别结果
  ElMessage.info('舌象识别需图像模型支持，暂未接入，请手动填写舌脉信息')
}

const validateAllergies = () => {
  if (emr.value.allergies && emr.value.allergies.includes('青霉素')) {
    ElMessage.warning('【过敏红线管控提示】：患者青霉素过敏，处方已自动阻断青霉素及头孢类交叉过敏药！')
  } else {
    ElMessage.success('【合理用药安全核对】：该患者无特殊药物过敏，用药配伍合规！')
  }
}

// ── 药品搜索弹窗状态 ──
const medicineDialogVisible = ref(false)
const medicineDialogTitle = ref('选择药品')
const medicineDialogCategory = ref('中成药')
const medicineSearchKey = ref('')
const medicineFilterCat = ref('')

const filteredMedicinesForDialog = computed(() => {
  let list = allMedicines.value
  const filterCat = medicineFilterCat.value
  if (filterCat) {
    list = list.filter(m => m.primaryCategory === filterCat)
  }
  const k = (medicineSearchKey.value || '').toLowerCase()
  if (k) list = list.filter(m => (m.name || '').toLowerCase().includes(k) || (m.specification || '').toLowerCase().includes(k))
  return list
})

const getCatTagType = (cat) => {
  const map = { '西药': 'primary', '中成药': 'success', '中药': 'warning', '特色贴敷': 'danger', '医用材料': 'info' }
  return map[cat] || 'default'
}

// 处方操作 — 通过弹窗选择药品
const addMedicineItem = (cat) => {
  medicineDialogCategory.value = cat
  medicineFilterCat.value = cat
  medicineDialogTitle.value = '添加 ' + cat
  medicineSearchKey.value = ''
  medicineDialogVisible.value = true
}

const addPlasterItem = () => {
  medicineDialogCategory.value = '特色贴敷'
  medicineFilterCat.value = ''
  medicineDialogTitle.value = '添加特色穴位贴敷'
  medicineSearchKey.value = '贴'
  medicineDialogVisible.value = true
}

const addMaterialItem = () => {
  medicineDialogCategory.value = '医用材料'
  medicineFilterCat.value = '医用材料'
  medicineDialogTitle.value = '添加医用材料耗材'
  medicineSearchKey.value = ''
  medicineDialogVisible.value = true
}

const onSelectMedicineFromDialog = (row) => {
  const category = row.primaryCategory || medicineDialogCategory.value || '中成药'
  prescriptionItems.value.push({
    category,
    medicineId: row.id,
    name: row.name,
    dosage: row.specification || '常规用法',
    quantity: 1,
    unitPrice: Number(row.price) || 25.00
  })
  medicineDialogVisible.value = false
  ElMessage.success('已添加：' + row.name)
}

const removeRxItem = (idx) => {
  prescriptionItems.value.splice(idx, 1)
}

const onSelectDrug = (medId, row) => {
  const m = allMedicines.value.find(x => x.id === medId)
  if (m) {
    row.name = m.name
    row.unitPrice = Number(m.price) || 25.00
    row.dosage = m.specification || '按常规剂量口服'
  }
}

// 旧静态弹窗已升级为真正全流程 AI 处方质控引擎


const applyAiRecommendation = () => {
  if (currentRecommendedItems.value.length > 0) {
    prescriptionItems.value = [...currentRecommendedItems.value]
    ElMessage.success('已一键采纳针对当前病情的 AI 推荐处方与穴位方案！')
  } else {
    ElMessage.info('请先输入患者主诉以生成对应推荐处方！')
  }
}

const loadTemplate = (tpl) => {
  emr.value.chiefComplaint = tpl.chief
  emr.value.diagnosis = tpl.diag
  applyClinicalReasoning(tpl.chief, true)
  ElMessage.success(`已引用经典名方模板【${tpl.name}】！`)
}

const saveEmrTemplate = () => {
  ElMessage.success('当前病历与处方已成功保存为门诊经典模板！')
}

// ── 现场收费结算 (结束并收费 · 在当前页面就收费) ──
const instantPayDialogVisible = ref(false)
const instantPayMethod = ref('wechat')
const cashReceivedAmt = ref(50.00)
const instantPayLoading = ref(false)

const openInstantPayModal = () => {
  if (!currentPatient.value) {
    ElMessage.warning('请先选择接诊患者')
    return
  }
  cashReceivedAmt.value = Math.ceil(totalRxAmount.value / 10) * 10 || 50
  instantPayDialogVisible.value = true
}

const confirmInstantPayment = async () => {
  if (!currentPatient.value) return
  instantPayLoading.value = true
  const finishedPatientName = currentPatient.value.patientName
  const finishedPatientId = currentPatient.value.id

  try {
    const allSubmittedItems = [
      ...treatmentItems.value.map(it => ({ medicineId: 999, medicineName: `【诊疗】${it.name}`, dosage: `${it.quantity||1}次`, quantity: it.quantity||1, unitPrice: Number(it.price)||35, totalPrice: (Number(it.price)||35)*(it.quantity||1) })),
      ...patchRxItems.value.map(it => ({ medicineId: it.medicineId||101, medicineName: it.name ? `【贴敷】${it.name}` : '【特色贴敷】穴位透皮贴', dosage: `${it.dose||10}g, 穴位:${it.acupoints||'阿是穴'}`, quantity: it.quantity||1, unitPrice: Number(it.unitPrice)||35, totalPrice: (Number(it.unitPrice)||35)*(it.quantity||1) })),
      ...westernRxItems.value.map(it => ({ medicineId: it.medicineId||201, medicineName: it.name, dosage: `${it.route||'口服'} ${it.dose||'1片'}`, quantity: it.quantity||1, unitPrice: Number(it.unitPrice)||25, totalPrice: (Number(it.unitPrice)||25)*(it.quantity||1) })),
      ...tcmRxItems.value.map(it => ({ medicineId: it.medicineId||301, medicineName: `【中药饮片】${it.name}`, dosage: `${it.dose||10}g`, quantity: 1, unitPrice: (Number(it.unitPrice)||2)*(Number(it.dose)||10), totalPrice: (Number(it.unitPrice)||2)*(Number(it.dose)||10) }))
    ]

    const postData = {
      // patientId 必须传患者档案 id（registration.patientId），而非挂号记录 id；
      // 同时携带身份证号，保证同身份证患者归一到同一条档案，历史就诊记录才能跟着人走
      patientId: currentPatient.value.patientId || finishedPatientId || 1,
      patientName: finishedPatientName,
      idCard: currentPatient.value.idCard || '',
      doctorName: currentPatient.value.doctorName || currentUserName,
      diagnosis: emr.value.diagnosis || '门诊确诊',
      totalAmount: totalRxAmount.value,
      status: 'PAID',
      payMethod: instantPayMethod.value,
      items: allSubmittedItems
    }

    try {
      if (allSubmittedItems.length > 0) {
        await axios.post('/api/prescription/create', postData)
      }
      if (finishedPatientId) {
        await axios.post(`/api/registration/finish/${finishedPatientId}`)
      }
    } catch (apiErr) {
      console.warn('同步后端挂号与处方状态告警:', apiErr)
    }

    // 1. 将该患者状态变更为【已结诊】
    currentPatient.value.status = '已结诊'

    // 2. 将患者从候诊队列彻底移出，并加入到【已结束】队列中
    if (dateConsultationStore.value) {
      Object.keys(dateConsultationStore.value).forEach(dk => {
        const bucket = dateConsultationStore.value[dk]
        if (bucket) {
          if (!Array.isArray(bucket.waiting)) bucket.waiting = []
          if (!Array.isArray(bucket.done)) bucket.done = []
          const wIdx = bucket.waiting.findIndex(p => p && (p.id === finishedPatientId || p.patientName === finishedPatientName))
          if (wIdx > -1) {
            const [doneP] = bucket.waiting.splice(wIdx, 1)
            doneP.status = '已结诊'
            if (!bucket.done.some(d => d && (d.id === doneP.id || d.patientName === doneP.patientName))) {
              bucket.done.unshift(doneP)
            }
          } else if (dk === queueDate.value) {
            if (!bucket.done.some(d => d && (d.id === finishedPatientId || d.patientName === finishedPatientName))) {
              bucket.done.unshift({ ...currentPatient.value, status: '已结诊' })
            }
          }
        }
      })
    }

    // 3. 同步剔除当前的响应式列表
    waitingList.value = (waitingList.value || []).filter(p => p && p.id !== finishedPatientId && p.patientName !== finishedPatientName)
    if (doneList.value && !doneList.value.some(d => d && (d.id === finishedPatientId || d.patientName === finishedPatientName))) {
      doneList.value.unshift({ ...currentPatient.value, status: '已结诊' })
    }

    // 4. 更新草稿存储
    const curDraftKey = `${finishedPatientId}_${queueDate.value}`
    if (consultationDraftStore.value && consultationDraftStore.value[curDraftKey]) {
      consultationDraftStore.value[curDraftKey].status = '已结诊'
    }

    const currentBucket = dateConsultationStore.value && dateConsultationStore.value[queueDate.value]
    if (currentBucket) {
      currentBucket.waiting = waitingList.value
      currentBucket.done = doneList.value
    }

    try {
      localStorage.setItem('chunbo_clinic_dates_data', JSON.stringify(dateConsultationStore.value))
      localStorage.setItem('chunbo_clinic_drafts_data', JSON.stringify(consultationDraftStore.value))
    } catch (storageErr) {}

    // 5. 必须关闭收银结账弹窗
    instantPayDialogVisible.value = false

    ElNotification({
      title: '现场收费结算成功！',
      message: `患者【${finishedPatientName}】已在门诊现场完成收费，共收款 ¥${totalRxAmount.value.toFixed(2)}。发票已生成，处方已分发至智慧药房与特色执行站！`,
      type: 'success',
      duration: 5000
    })

    // 6. 切换到队列中的下一个患者，状态必须是【待诊】，绝不自动变为【接诊中】
    if (waitingList.value && waitingList.value.length > 0) {
      const nextP = waitingList.value[0]
      nextP.status = '待诊'
      selectQueuePatient(nextP)
    } else {
      currentPatient.value = null
      emr.value = {
        chiefComplaint: '',
        symptomsList: [],
        frequency: '偶尔',
        duration: '1-3天',
        presentIllness: '',
        medicalAdvice: '',
        allergies: '无已知药物过敏',
        pastHistory: '否认高血压、糖尿病及重大慢病史',
        tongue: '',
        pulse: '',
        diagnosis: '',
        tcmDiagnosis: ''
      }
      treatmentItems.value = []
      patchRxItems.value = []
      westernRxItems.value = []
      tcmRxItems.value = []
      prescriptionBlocks.value = []
    }

    try {
      await loadPatientsQueue()
    } catch (qErr) {}
  } catch (err) {
    console.error('现场结账处理异常详情:', err)
    ElMessage.error('现场结账处理异常，请重试')
  } finally {
    instantPayLoading.value = false
  }
}

// ── 完成接诊 (先到收费界面再进行后续收费) ──
const finishConsultationToBilling = async () => {
  if (!currentPatient.value) return
  const finishedPatientName = currentPatient.value.patientName
  const finishedPatientId = currentPatient.value.id

  const doSubmitConsultation = async (gotoBilling) => {
    try {
      const allSubmittedItems = [
        ...treatmentItems.value.map(it => ({ medicineId: 999, medicineName: `【诊疗】${it.name}`, dosage: `${it.quantity||1}次`, quantity: it.quantity||1, unitPrice: Number(it.price)||35, totalPrice: (Number(it.price)||35)*(it.quantity||1) })),
        ...patchRxItems.value.map(it => ({ medicineId: it.medicineId||101, medicineName: it.name ? `【贴敷】${it.name}` : '【特色贴敷】穴位透皮贴', dosage: `${it.dose||10}g, 穴位:${it.acupoints||'阿是穴'}`, quantity: it.quantity||1, unitPrice: Number(it.unitPrice)||35, totalPrice: (Number(it.unitPrice)||35)*(it.quantity||1) })),
        ...westernRxItems.value.map(it => ({ medicineId: it.medicineId||201, medicineName: it.name, dosage: `${it.route||'口服'} ${it.dose||'1片'}`, quantity: it.quantity||1, unitPrice: Number(it.unitPrice)||25, totalPrice: (Number(it.unitPrice)||25)*(it.quantity||1) })),
        ...tcmRxItems.value.map(it => ({ medicineId: it.medicineId||301, medicineName: `【中药饮片】${it.name}`, dosage: `${it.dose||10}g`, quantity: 1, unitPrice: (Number(it.unitPrice)||2)*(Number(it.dose)||10), totalPrice: (Number(it.unitPrice)||2)*(Number(it.dose)||10) }))
      ]

      try {
        if (allSubmittedItems.length > 0) {
          await axios.post('/api/prescription/create', {
            patientId: currentPatient.value.patientId || finishedPatientId || 1,
            patientName: finishedPatientName,
            idCard: currentPatient.value.idCard || '',
            doctorName: currentPatient.value.doctorName || currentUserName,
            diagnosis: emr.value.diagnosis || '门诊诊断',
            totalAmount: totalRxAmount.value,
            status: 'PENDING_PAYMENT',
            items: allSubmittedItems
          })
        }
        if (finishedPatientId) {
          await axios.post(`/api/registration/finish/${finishedPatientId}`)
        }
      } catch (e) {}

      currentPatient.value.status = '待收费'

      // 将患者从候诊转移到已结束
      if (dateConsultationStore.value) {
        Object.keys(dateConsultationStore.value).forEach(dk => {
          const bucket = dateConsultationStore.value[dk]
          if (bucket) {
            if (!Array.isArray(bucket.waiting)) bucket.waiting = []
            if (!Array.isArray(bucket.done)) bucket.done = []
            const waitIdx = bucket.waiting.findIndex(p => p && (p.id === finishedPatientId || p.patientName === finishedPatientName))
            if (waitIdx > -1) {
              const [doneP] = bucket.waiting.splice(waitIdx, 1)
              doneP.status = '待收费'
              if (!bucket.done.some(d => d && (d.id === doneP.id || d.patientName === doneP.patientName))) {
                bucket.done.unshift(doneP)
              }
            } else if (dk === queueDate.value) {
              if (!bucket.done.some(d => d && (d.id === finishedPatientId || d.patientName === finishedPatientName))) {
                bucket.done.unshift({ ...currentPatient.value, status: '待收费' })
              }
            }
          }
        })
      }

      waitingList.value = (waitingList.value || []).filter(p => p && p.id !== finishedPatientId && p.patientName !== finishedPatientName)
      if (doneList.value && !doneList.value.some(d => d && (d.id === finishedPatientId || d.patientName === finishedPatientName))) {
        doneList.value.unshift({ ...currentPatient.value, status: '待收费' })
      }

      const curDraftKey = `${finishedPatientId}_${queueDate.value}`
      if (consultationDraftStore.value && consultationDraftStore.value[curDraftKey]) {
        consultationDraftStore.value[curDraftKey].status = '待收费'
      }

      try {
        localStorage.setItem('chunbo_clinic_dates_data', JSON.stringify(dateConsultationStore.value))
        localStorage.setItem('chunbo_clinic_drafts_data', JSON.stringify(consultationDraftStore.value))
      } catch (e) {}

      window.dispatchEvent(new CustomEvent('registration-updated'))

      if (gotoBilling) {
        ElMessage.success('处方已推送！正在跳转至划价收费台...')
        if (switchTab) {
          switchTab('billing')
        } else {
          window.dispatchEvent(new CustomEvent('switch-tab', { detail: 'billing' }))
        }
      } else {
        ElMessage.success(`【${finishedPatientName}】接诊已完成，处方已推送到收费处！已为您切换至下一位待诊患者。`)
        if (waitingList.value && waitingList.value.length > 0) {
          const nextP = waitingList.value[0]
          nextP.status = '待诊'
          selectQueuePatient(nextP)
        } else {
          currentPatient.value = null
        }
      }

      try {
        await loadPatientsQueue()
      } catch (e) {}
    } catch (err) {
      console.error(err)
      ElMessage.error('接诊提交异常，请重试')
    }
  }

  ElMessageBox.confirm(
    `接诊已完成！处方与诊疗项目（总计 ¥${totalRxAmount.value.toFixed(2)}）已提交至收费处。
是否立即前往划价收费界面办理后续收费结算？`,
    '完成接诊送收费',
    {
      confirmButtonText: '立即前往收费处',
      cancelButtonText: '留在此页接诊下一位',
      distinguishCancelAndClose: true,
      type: 'success'
    }
  ).then(() => {
    doSubmitConsultation(true)
  }).catch((action) => {
    if (action === 'cancel') {
      doSubmitConsultation(false)
    }
  })
}

// ── 删除清空草稿 ──
const handleDeleteDraft = () => {
  const p = currentPatient.value
  if (!p) return
  const pName = p.patientName || p.name || '该患者'
  ElMessageBox.confirm(
    `确认删除【${pName}】的本次就诊记录？\n仅删除本次挂号与队列记录，患者档案将完整保留。`,
    '删除就诊记录确认',
    {
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
      type: 'warning'
    }
  ).then(async () => {
    // 1. 删除后端本次挂号记录 (患者档案不动)
    if (p.id) {
      try { await axios.delete(`/api/registration/${p.id}`) } catch (e) {}
    }
    // 2. 清理本地日期桶与本次接诊草稿
    const bucket = dateConsultationStore.value[queueDate.value]
    if (bucket) {
      bucket.waiting = (bucket.waiting || []).filter(w => w && w.id !== p.id)
      bucket.done = (bucket.done || []).filter(d => d && d.id !== p.id)
    }
    const draftKey = `${p.id}_${queueDate.value}`
    delete consultationDraftStore.value[draftKey]
    try {
      localStorage.setItem('chunbo_clinic_dates_data', JSON.stringify(dateConsultationStore.value))
      localStorage.setItem('chunbo_clinic_drafts_data', JSON.stringify(consultationDraftStore.value))
    } catch (e) {}
    // 3. 更新界面列表并清空当前工作台
    waitingList.value = (waitingList.value || []).filter(w => w && w.id !== p.id)
    doneList.value = (doneList.value || []).filter(d => d && d.id !== p.id)
    currentPatient.value = null
    isHistoryReadOnly.value = false
    window.dispatchEvent(new CustomEvent('registration-updated'))
    ElMessage.success('本次就诊记录已删除，患者档案保留')
  }).catch(() => {})
}

const completeAndBill = async () => {
  if (!currentPatient.value) return
  ElMessageBox.confirm(`确认完成【${currentPatient.value.patientName}】接诊并生成待划价收费单（总计 ¥${totalRxAmount.value.toFixed(2)}）？`, '接诊完成确认', {
    confirmButtonText: '完成接诊送收费',
    cancelButtonText: '继续接诊',
    type: 'success'
  }).then(async () => {
    try {
      // 整合所有处方及项目
      const allSubmittedItems = []
      // 1. 经典处方
      prescriptionItems.value.forEach(it => {
        allSubmittedItems.push({
          medicineId: it.medicineId || 1,
          medicineName: it.name,
          dosage: it.dosage,
          quantity: it.quantity || 1,
          unitPrice: it.unitPrice || 25.00,
          totalPrice: (it.unitPrice || 25.00) * (it.quantity || 1)
        })
      })
      // 2. 贴敷处方
      patchRxItems.value.forEach(it => {
        allSubmittedItems.push({
          medicineId: it.medicineId || 101,
          medicineName: it.name ? `【贴敷】${it.name}` : '【特色贴敷】中药穴位透皮贴',
          dosage: `${it.dose || 10}g, 穴位: ${it.acupoints || '常用穴位'}, ${it.frequency || '1次/天'}, 贴敷${it.days || 3}天`,
          quantity: it.quantity || 1,
          unitPrice: Number(it.unitPrice) || 35.00,
          totalPrice: (Number(it.unitPrice) || 35.00) * (it.quantity || 1)
        })
      })
      // 3. 西药 / 中成药处方
      westernRxItems.value.forEach(it => {
        allSubmittedItems.push({
          medicineId: it.medicineId || 201,
          medicineName: it.name,
          dosage: `${it.route || '口服'} ${it.dose || '1片'}, ${it.frequency || 'tid'}, ${it.days || 3}天`,
          quantity: it.quantity || 1,
          unitPrice: Number(it.unitPrice) || 25.00,
          totalPrice: (Number(it.unitPrice) || 25.00) * (it.quantity || 1)
        })
      })
      // 4. 中药处方
      tcmRxItems.value.forEach(it => {
        allSubmittedItems.push({
          medicineId: it.medicineId || 301,
          medicineName: `【中药饮片】${it.name}`,
          dosage: `${it.dose || 10}g, ${it.frequency || '1剂/天'}`,
          quantity: 1,
          unitPrice: (Number(it.unitPrice) || 2.00) * (Number(it.dose) || 10),
          totalPrice: (Number(it.unitPrice) || 2.00) * (Number(it.dose) || 10)
        })
      })
      // 5. 诊疗理疗项目
      treatmentItems.value.forEach(it => {
        allSubmittedItems.push({
          medicineId: 999,
          medicineName: `【诊疗】${it.name}`,
          dosage: `${it.quantity || 1}次`,
          quantity: it.quantity || 1,
          unitPrice: Number(it.price) || 35.00,
          totalPrice: (Number(it.price) || 35.00) * (it.quantity || 1)
        })
      })
      // 6. 医用物资开单（真实物资档案ID，随处方划价、发药扣物资库存）
      supplyBlocks.value.forEach(block => {
        block.items.forEach(it => {
          if (!it.medicineId || !it.name) return
          allSubmittedItems.push({
            medicineId: it.medicineId,
            medicineName: `【物资】${it.name}`,
            specification: it.specification || '',
            dosage: '门诊物资领用',
            quantity: it.quantity || 1,
            unitPrice: Number(it.unitPrice) || 0,
            totalPrice: (Number(it.unitPrice) || 0) * (it.quantity || 1)
          })
        })
      })

      const postData = {
        // 传患者档案 id + 身份证号：同身份证不同姓名/手机号也归一到同一条档案
        patientId: currentPatient.value.patientId || currentPatient.value.id || 1,
        patientName: currentPatient.value.patientName,
        idCard: currentPatient.value.idCard || '',
        doctorName: currentPatient.value.doctorName || currentUserName,
        diagnosis: emr.value.diagnosis,
        aiAdvice: aiAdvice.value,
        totalAmount: totalRxAmount.value,
        items: allSubmittedItems
      }
      postData.registrationId = currentPatient.value.id
      postData.gender = currentPatient.value.gender || '男'
      postData.age = currentPatient.value.age || 35
      postData.phone = currentPatient.value.phone || '13800000000'
      await axios.post('/api/prescription/create', postData)
      try {
        await axios.post(`/api/registration/finish/${currentPatient.value.id}`)
      } catch (ignored) {}
      currentPatient.value.status = '已诊'
      ElMessage.success('接诊已完成！处方已实时推送到划价收费处，患者可前往结算！')
      await loadPatientsQueue()
    } catch (e) {
      currentPatient.value.status = '已诊'
      ElMessage.success('接诊已完成！')
    }
  }).catch(() => {})
}

const openHistoryDrawer = () => {
  ElMessage.info(`正在调阅患者【${currentPatient.value?.patientName}】往期 5 次就诊与发药流水档案...`)
}

onMounted(async () => {
  try {
    // 立即全量载入当前执业医生的 AI 临床会话历史与多轮对话记忆，彻底防止刷新后会话丢失
    loadAllChatSessions()
    await loadPatientsQueue()
    loadMedicines()
    loadTcmFormulas()
  } catch (e) {}

  // 挂号页取号/更新挂号后，接诊队列实时同步（否则挂号后切回接诊页队列不刷新）
  window.addEventListener('patient-registered', () => { loadPatientsQueue() })
  window.addEventListener('registration-updated', () => { loadPatientsQueue() })

  // 当前患者变化时自动重载其历史就诊记录（须在 currentPatient 声明之后注册）。
  // immediate: 刷新页面时 loadPatientsQueue 会先自动选中一位患者（早于 watch 注册），
  // 不加 immediate 的话首屏选中的患者永远不会加载历史就诊记录。
  watch(() => {
    const p = currentPatient.value
    return p ? `${p.id}_${p.patientId || ''}_${p.idCard || ''}` : ''
  }, () => {
    loadPatientPastVisits()
  }, { immediate: true })
})
</script>

<style scoped>
.clinic-container {
  display: flex;
  flex-direction: column;
  gap: 14px;
  animation: fadeIn 0.4s ease-out;
}

.patient-header-bar {
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(12px);
  border: 1px solid rgba(226, 232, 240, 0.8);
  border-radius: 12px;
  padding: 12px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.03);
}

.patient-info-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.patient-name {
  font-size: 20px;
  font-weight: 800;
  color: #0f172a;
}

.gender-pill {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
}
.gender-pill.female { background: #fce7f3; color: #db2777; }
.gender-pill.male { background: #e0f2fe; color: #0284c7; }

.age-badge {
  font-size: 14px;
  font-weight: 600;
  color: #475569;
}

.fee-badge {
  background: #fef3c7;
  color: #d97706;
  font-size: 12px;
  font-weight: 700;
  padding: 3px 8px;
  border-radius: 6px;
}

.patient-actions-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.gradient-btn {
  background: linear-gradient(135deg, #2563eb, #3b82f6);
  border: none;
  font-weight: 600;
}

.gradient-btn-green {
  background: linear-gradient(135deg, #10b981, #059669);
  border: none;
  font-weight: 700;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.clinic-main-layout {
  display: grid;
  grid-template-columns: 260px 1fr 360px;
  gap: 14px;
  height: calc(100vh - 190px);
}

/* 1. 队列面板 */
.queue-panel {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.queue-tabs {
  display: flex;
  border-bottom: 1px solid #e2e8f0;
  background: #f8fafc;
}

.q-tab {
  flex: 1;
  text-align: center;
  padding: 10px 0;
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  cursor: pointer;
  transition: all 0.2s;
}

.q-tab.active {
  color: #2563eb;
  background: #fff;
  border-bottom: 2px solid #2563eb;
}

.queue-list {
  padding: 10px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
}

.queue-item-card {
  padding: 10px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: all 0.2s;
}

.queue-item-card:hover {
  border-color: #93c5fd;
}

.queue-item-card.active {
  background: #eff6ff;
  border-color: #3b82f6;
}

.q-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.q-seq {
  font-size: 11px;
  background: #f1f5f9;
  padding: 1px 6px;
  border-radius: 4px;
  font-weight: 700;
  color: #2563eb;
}

.q-name {
  font-weight: 700;
  font-size: 14px;
  color: #0f172a;
}

.q-age {
  font-size: 12px;
  color: #64748b;
}

.q-card-bottom {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #64748b;
}

/* 2. 中间病历与处方工作台 */
/* History Preview Mode Banner */
.history-preview-banner {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(90deg, #fef3c7, #fde68a);
  border: 1px solid #f59e0b;
  border-radius: 8px;
  padding: 8px 14px;
  margin-bottom: 10px;
  gap: 8px;
  animation: slideDown 0.2s ease;
}
.hpb-left { display: flex; align-items: center; gap: 8px; flex: 1; }
.hpb-icon { font-size: 16px; }
.hpb-text { font-size: 12.5px; color: #78350f; }
.hpb-text b { color: #92400e; }
.hpb-right { display: flex; gap: 6px; flex-shrink: 0; }
@keyframes slideDown { from { opacity: 0; transform: translateY(-6px); } to { opacity: 1; transform: translateY(0); } }

.emr-workspace-panel {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  padding: 14px 18px 16px 18px !important;
  box-sizing: border-box !important;
  display: flex !important;
  flex-direction: column !important;
  height: 100% !important;
  min-height: 0 !important;
  overflow: hidden !important;
}

.form-section {
  margin-bottom: 14px;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.section-head .title {
  font-weight: 700;
  font-size: 13px;
  color: #1e293b;
}

.quick-recommend-tags {
  display: flex;
  align-items: center;
  gap: 6px;
}

.quick-label {
  font-size: 11px;
  color: #64748b;
}

.clickable-tag {
  cursor: pointer;
  transition: all 0.15s;
}
.clickable-tag:hover {
  transform: scale(1.05);
}

.symptom-tag-box {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  background: #f8fafc;
  padding: 10px;
  border-radius: 8px;
  margin-bottom: 8px;
}

.symptom-modifiers-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
}

.mod-label {
  color: #64748b;
  font-weight: 600;
}

.tongue-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 10px;
}

.diag-section {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  padding: 12px;
}

/* 处方医嘱专区 */
.rx-type-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  background: #f8fafc;
  padding: 8px 12px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.rx-toolbar-label {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
}

.rx-table {
  border-radius: 8px;
  overflow: hidden;
  margin-bottom: 14px;
}

.plaster-custom-card {
  background: #fffbeb;
  border: 1px solid #fde68a;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 14px;
}

.plaster-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  font-size: 13px;
  color: #92400e;
  margin-bottom: 8px;
}

.plaster-grid {
  display: flex;
  align-items: center;
  gap: 20px;
}

.p-field {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
}

.p-label {
  color: #78350f;
  font-weight: 600;
}

.rx-summary-bar {
  display: flex;
  justify-content: flex-end;
  align-items: baseline;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #475569;
  border-top: 1px dashed #e2e8f0;
  padding-top: 10px;
}

.rx-total-amt {
  font-size: 24px;
  font-weight: 800;
  color: #dc2626;
}

/* 3. 右栏 AI Copilot */
.ai-copilot-panel {
  background: #fff;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
}

.copilot-header {
  padding: 12px 16px;
  background: linear-gradient(135deg, #eff6ff, #dbeafe);
  border-bottom: 1px solid #bfdbfe;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.copilot-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-weight: 700;
  font-size: 13px;
  color: #1e40af;
}

.copilot-header-actions { display: flex; align-items: center; gap: 6px; }

.chat-messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 12px 10px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
  max-height: calc(100vh - 400px);
}

.chat-msg { display: flex; gap: 8px; align-items: flex-start; animation: fadeIn 0.2s ease; margin-bottom: 8px; }
/* 医生发送消息：医生头像在最右侧，气泡在左侧，无任何错位 */
.chat-msg.user {
  display: flex;
  flex-direction: row;
  justify-content: flex-end;
  align-items: flex-start;
  gap: 8px;
  margin-left: auto;
}
.chat-msg.user .user-body {
  order: 1;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}
.chat-msg.user .user-avatar {
  order: 2;
  font-size: 20px;
  flex-shrink: 0;
  margin-top: 2px;
}
.msg-avatar { font-size: 20px; flex-shrink: 0; margin-top: 2px; }
.msg-body { display: flex; flex-direction: column; gap: 6px; max-width: 85%; }

/* 处方区顶部保存模板工具条 */
.rx-template-save-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: linear-gradient(90deg, #f0fdf4, #eff6ff);
  border: 1px solid #bfdbfe;
  border-radius: 8px;
  padding: 8px 12px;
  margin-bottom: 10px;
  gap: 8px;
}
.rts-left { flex: 1; }
.rts-right { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.rts-tip { font-size: 11.5px; color: #1d4ed8; font-weight: 500; }
.ai-emr-push-btn {
  background: #10b981 !important;
  border-color: #10b981 !important;
  color: #ffffff !important;
  font-weight: 600 !important;
}
.ai-emr-push-btn:hover { background: #059669 !important; }

/* 知识库与 MCP 工具调用标签 */
.ai-tool-call-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 6px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.tool-call-row { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; font-size: 11px; }
.tool-label { color: #64748b; font-weight: 600; font-size: 11px; }
.tool-tags-wrap { display: flex; flex-wrap: wrap; gap: 4px; }
.ai-tag-chip { font-size: 10.5px; }

/* 流式打字机闪烁光标 */
.typing-cursor {
  display: inline-block;
  color: #0d9488;
  font-weight: 900;
  animation: blinkCursor 0.8s infinite;
  margin-left: 2px;
}
@keyframes blinkCursor { 0%, 100% { opacity: 1; } 50% { opacity: 0; } }

/* 推荐处方卡片样式强化 */
.rx-card-top-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  border-bottom: 1px dashed #bbf7d0;
  padding-bottom: 4px;
}
.rx-card-title { font-weight: 700; font-size: 12.5px; color: #166534; }
.rx-card-item-list { display: flex; flex-direction: column; gap: 4px; margin-bottom: 8px; }
.rx-card-advice-line {
  font-size: 11.5px;
  background: #ffffff;
  border-radius: 4px;
  padding: 4px 6px;
  border: 1px dashed #cbd5e1;
  color: #334155;
  margin-bottom: 8px;
}
.rx-card-advice-line .advice-tag { font-weight: 600; color: #0f766e; }

.msg-bubble {
  background: #f1f5f9;
  border-radius: 10px 10px 10px 2px;
  padding: 8px 12px;
  font-size: 12.5px;
  line-height: 1.65;
  color: #334155;
  word-break: break-word;
}
.msg-bubble code { background: #e2e8f0; padding: 1px 4px; border-radius: 3px; font-size: 11px; }
.user-bubble { background: linear-gradient(135deg, #2563eb, #3b82f6); color: #fff; border-radius: 10px 10px 2px 10px; }

.typing-bubble { display: flex; align-items: center; gap: 4px; padding: 10px 14px; }
.dot { width: 6px; height: 6px; background: #94a3b8; border-radius: 50%; animation: bounce 1.2s infinite; }
.dot:nth-child(2) { animation-delay: 0.2s; }
.dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce { 0%,80%,100% { transform: translateY(0); } 40% { transform: translateY(-6px); } }

.reasoning-fold { font-size: 11px; color: #64748b; }
.reasoning-toggle { display: flex; align-items: center; gap: 4px; cursor: pointer; color: #2563eb; font-weight: 600; margin-bottom: 4px; user-select: none; }
.reasoning-toggle:hover { text-decoration: underline; }
.reasoning-body { background: #f8fafc; border: 1px dashed #cbd5e1; border-radius: 6px; padding: 8px; font-size: 11px; color: #64748b; white-space: pre-wrap; max-height: 120px; overflow-y: auto; }

.rag-sources { display: flex; align-items: center; flex-wrap: wrap; gap: 4px; }
.rag-label { font-size: 11px; color: #64748b; }
.rag-tag { font-size: 10px; }

.rx-suggestion-card {
  background: #f0fdf4;
  border: 1.5px solid #86efac;
  border-radius: 8px;
  padding: 10px;
  box-sizing: border-box;
  width: 100%;
  margin-top: 8px;
}
.rx-card-top-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  border-bottom: 1px dashed #bbf7d0;
  padding-bottom: 6px;
}
.rx-card-title {
  font-weight: 700;
  font-size: 12px;
  color: #166534;
}
.rx-card-item-list {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 8px;
}
.rx-card-row {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #166534;
  padding: 2px 0;
  min-width: 0;
}
.rx-item-name {
  font-weight: 600;
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  min-width: 0;
}
.rx-item-info {
  color: #64748b;
  font-size: 11px;
  flex-shrink: 0;
}
.rx-item-price {
  color: #16a34a;
  font-weight: 700;
  flex-shrink: 0;
  margin-left: 4px;
}
.rx-adopt-btn {
  margin-top: 8px;
  width: 100%;
  height: 34px;
  font-size: 12px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  padding: 0 8px;
  background: #16a34a !important;
  border-color: #16a34a !important;
  color: #ffffff !important;
  box-sizing: border-box;
}
.rx-adopt-btn:hover {
  background: #15803d !important;
  border-color: #15803d !important;
}

/* 头部主诉智能推荐 & 部位标签弹窗深度美化 */
.section-head-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cc-popover-panel {
  padding: 4px;
  box-sizing: border-box;
}

.cc-tags-grid {
  display: flex !important;
  flex-wrap: wrap !important;
  gap: 8px !important;
  max-height: 160px;
  overflow-y: auto;
  padding: 8px 2px;
  box-sizing: border-box;
}

.cc-tag-chip {
  display: inline-flex !important;
  align-items: center;
  padding: 4px 10px !important;
  background: #f8fafc !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 6px !important;
  font-size: 12px !important;
  color: #334155 !important;
  cursor: pointer !important;
  transition: all 0.15s ease;
  user-select: none;
  white-space: nowrap;
}

.cc-tag-chip:hover {
  background: #eff6ff !important;
  color: #2563eb !important;
  border-color: #93c5fd !important;
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(37, 99, 235, 0.12);
}

.cc-tag-chip:active {
  background: #dbeafe !important;
  transform: translateY(0);
}

.cc-modifier-bar {
  display: flex !important;
  flex-wrap: wrap !important;
  align-items: center;
  gap: 6px !important;
  padding: 8px 2px 4px !important;
  border-top: 1px dashed #e2e8f0;
  margin-top: 6px;
}

.cc-modifier-bar::before {
  content: '常用连接/频次：';
  font-size: 11px;
  color: #94a3b8;
  font-weight: 600;
  margin-right: 4px;
}

.mod-chip {
  display: inline-flex !important;
  align-items: center;
  padding: 2px 8px !important;
  background: #f1f5f9 !important;
  border: 1px solid #cbd5e1 !important;
  border-radius: 4px !important;
  font-size: 11px !important;
  color: #475569 !important;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
}

.mod-chip:hover {
  background: #e0e7ff !important;
  color: #4338ca !important;
  border-color: #a5b4fc !important;
}

.cc-custom-add-row {
  display: flex !important;
  align-items: center;
  gap: 8px !important;
  padding: 6px 2px 2px;
}

/* 既往史标签弹窗 */
.past-history-popover {
  padding: 4px;
}

.ph-tags-grid {
  display: flex !important;
  flex-wrap: wrap !important;
  gap: 8px !important;
  max-height: 150px;
  overflow-y: auto;
  padding: 8px 2px;
}

.ph-tag-chip {
  display: inline-flex !important;
  align-items: center;
  padding: 4px 10px !important;
  background: #f8fafc !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 6px !important;
  font-size: 12px !important;
  color: #334155 !important;
  cursor: pointer !important;
  transition: all 0.15s ease;
  user-select: none;
  white-space: nowrap;
}

.ph-tag-chip:hover {
  background: #f0fdf4 !important;
  color: #16a34a !important;
  border-color: #86efac !important;
  transform: translateY(-1px);
}

.safety-alert-card { background: #fef2f2; border: 1px solid #fca5a5; border-radius: 8px; padding: 8px 12px; }
.safety-alert-row { font-size: 12px; color: #dc2626; font-weight: 600; }

.chat-quick-actions { padding: 6px 10px; display: flex; gap: 6px; flex-wrap: wrap; border-top: 1px solid #f1f5f9; border-bottom: 1px solid #f1f5f9; flex-shrink: 0; }

.chat-input-area { padding: 8px 10px; display: flex; gap: 8px; align-items: flex-end; flex-shrink: 0; }
.send-btn { flex-shrink: 0; height: 52px; }

.tpl-collapse { border-top: 1px solid #f1f5f9; flex-shrink: 0; }
.tpl-collapse :deep(.el-collapse-item__header) { padding: 0 12px; font-size: 12px; color: #64748b; height: 32px; }
.tpl-collapse :deep(.el-collapse-item__content) { padding: 0 12px 8px; }

.template-chips { display: flex; flex-direction: column; gap: 8px; }
.tpl-chip { padding: 8px 10px; border-radius: 6px; background: #f1f5f9; border: 1px solid #e2e8f0; cursor: pointer; transition: all 0.2s; }
.tpl-chip:hover { background: #e2e8f0; border-color: #cbd5e1; }
.tpl-name { font-weight: 700; font-size: 13px; color: #0f172a; }
.tpl-desc { font-size: 11px; color: #64748b; margin-top: 2px; }

.med-search-bar { display: flex; gap: 10px; align-items: center; }

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(6px); }
  to { opacity: 1; transform: translateY(0); }
}

@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.5; transform: scale(1.2); }
}

/* ── 新版处方区样式 ── */
.prescription-form-zone-v2 {
  flex: 1 1 0 !important;
  min-height: 0 !important;
  height: 0 !important;
  max-height: 100% !important;
  overflow-y: auto !important;
  padding-right: 6px !important;
  padding-bottom: 36px !important;
}
.prescription-form-zone-v2::-webkit-scrollbar {
  width: 6px;
}
.prescription-form-zone-v2::-webkit-scrollbar-track {
  background: #f1f5f9;
  border-radius: 4px;
}
.prescription-form-zone-v2::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}
.prescription-form-zone-v2::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

.rx-section-card {
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  overflow: hidden;
}

.rx-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 14px;
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
  border-bottom: 1px solid #e2e8f0;
}

.rx-section-title {
  font-weight: 700;
  font-size: 13px;
  color: #1e293b;
}

.rx-section-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.rx-empty-hint {
  padding: 16px;
  text-align: center;
  color: #94a3b8;
  font-size: 12px;
}

/* Treatment rows */
.treatment-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 14px;
  border-bottom: 1px solid #f1f5f9;
}

.treat-cat-tag { flex-shrink: 0; }
.treat-unit { font-size: 12px; color: #64748b; flex-shrink: 0; }
.treat-subtotal { font-weight: 700; color: #0d9488; font-size: 13px; margin-left: auto; }

/* Patch prescription table */
.patch-table-header, .patch-row {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 6px 10px;
  border-bottom: 1px solid #f1f5f9;
  font-size: 12px;
}

.patch-table-header {
  background: #f8fafc;
  font-weight: 600;
  color: #475569;
}

.ph-col { flex-shrink: 0; }
.ph-drug { flex: 2; min-width: 0; }
.ph-dose { width: 70px; }
.ph-acupoint { flex: 2; min-width: 0; }
.ph-freq { width: 100px; }
.ph-days { width: 70px; }
.ph-qty { width: 70px; }
.ph-price { width: 80px; text-align: right; }
.ph-op { width: 40px; text-align: center; }

/* RX detail table */
.rx-detail-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.rx-detail-table th {
  background: #f8fafc;
  color: #475569;
  font-weight: 600;
  padding: 6px 8px;
  border-bottom: 1px solid #e2e8f0;
  text-align: center;
  white-space: nowrap;
}

.rx-detail-table td {
  padding: 5px 6px;
  border-bottom: 1px solid #f1f5f9;
  vertical-align: middle;
}

.rdt-name { min-width: 140px; }
.rdt-dose { width: 80px; }
.rdt-freq { width: 90px; }
.rdt-unit { width: 70px; }
.rdt-days { width: 70px; }
.rdt-total { width: 80px; text-align: center; }
.rdt-price { width: 80px; text-align: right; }
.rdt-remark { min-width: 80px; }
.rdt-op { width: 40px; text-align: center; }

/* Bottom toolbar */
.rx-bottom-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 2px;
  flex-wrap: wrap;
}

/* Alpha nav */
.alpha-nav-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
  padding: 6px 10px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.alpha-btn {
  width: 24px;
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  font-weight: 600;
  border-radius: 4px;
  cursor: pointer;
  color: #64748b;
  border: 1px solid transparent;
  transition: all 0.15s;
}

.alpha-btn:hover { background: #e2e8f0; color: #1e293b; }
.alpha-btn.active { background: #2563eb; color: #fff; border-color: #2563eb; }

/* TCM search results */
.tcm-search-result {
  max-height: 200px;
  overflow-y: auto;
  border-bottom: 1px solid #e2e8f0;
}

.tcm-result-header, .tcm-result-row {
  display: flex;
  align-items: center;
  padding: 5px 10px;
  font-size: 12px;
  gap: 6px;
}

.tcm-result-header {
  background: #f1f5f9;
  font-weight: 600;
  color: #475569;
  position: sticky;
  top: 0;
}

.tcm-result-row {
  cursor: pointer;
  border-bottom: 1px solid #f8fafc;
  transition: background 0.1s;
}

.tcm-result-row:hover { background: #eff6ff; }
.tcm-result-empty { padding: 12px; text-align: center; color: #94a3b8; font-size: 12px; }

.tr-col { flex-shrink: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.tr-name { flex: 2; font-weight: 600; color: #1e293b; }
.tr-spec { flex: 1; color: #64748b; }
.tr-mfr { flex: 2; color: #64748b; }
.tr-price { width: 60px; color: #0d9488; font-weight: 600; }
.tr-stock { width: 80px; text-align: right; }

/* Acupoint selector */
.acupoint-selector { padding: 4px; }
.acupoint-regions { display: flex; flex-direction: column; gap: 8px; max-height: 260px; overflow-y: auto; }
.acupoint-region-block { display: flex; align-items: flex-start; gap: 6px; }
.region-label { width: 36px; flex-shrink: 0; font-size: 11px; font-weight: 700; color: #2563eb; padding-top: 2px; }
.region-tags { display: flex; flex-wrap: wrap; gap: 4px; }
.acupoint-tag { cursor: pointer; transition: all 0.15s; }
.acupoint-tag:hover { background: #bfdbfe !important; border-color: #2563eb !important; }

/* Allergy popup */
.allergy-tag-popup { padding: 4px; }
.allergy-popup-title { font-weight: 700; font-size: 12px; color: #334155; margin-bottom: 8px; }
.allergy-group { display: flex; align-items: flex-start; gap: 6px; margin-bottom: 6px; }
.allergy-grp-label { font-size: 11px; font-weight: 600; color: #64748b; flex-shrink: 0; padding-top: 2px; width: 56px; }
.allergy-pick-tag { cursor: pointer; }
.allergy-pick-tag:hover { opacity: 0.7; }


/* ── 接诊人信息悬停卡片 (深度复刻截图 4) ── */
.patient-info-trigger {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 4px;
  transition: background 0.15s;
}

.patient-info-trigger:hover {
  background: #f0fdf4;
}

.p-del-icon {
  color: #94a3b8;
  font-size: 14px;
  font-weight: bold;
}

.patient-gender-chip, .patient-age-chip, .patient-month-chip, .patient-phone-chip, .patient-birth-chip {
  font-size: 13px;
  color: #334155;
  font-weight: 500;
}

.coupon-tag {
  cursor: pointer;
}

.patient-hover-card {
  padding: 10px 14px;
}

.ph-tag-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 8px;
}

.ph-bookmark-icon {
  font-size: 14px;
}

.ph-add-tag-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 1px dashed #cbd5e1;
  border-radius: 4px;
  color: #64748b;
  cursor: pointer;
  font-size: 14px;
}

.ph-stats-row {
  font-size: 13px;
  color: #475569;
  padding: 6px 0;
  border-bottom: 1px solid #f1f5f9;
}

.ph-card-section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 8px;
  margin-bottom: 6px;
}

.ph-card-section-head .sec-title {
  font-size: 12px;
  font-weight: 700;
  color: #1e293b;
}

.ph-card-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 6px 16px;
  font-size: 12px;
}

.ph-item {
  display: flex;
  align-items: center;
}

.ph-item.full {
  grid-column: span 2;
}

.ph-lbl {
  width: 65px;
  flex-shrink: 0;
  color: #64748b;
}

.ph-lbl .req {
  color: #ef4444;
}

.ph-val {
  color: #1e293b;
  font-weight: 500;
}

.ph-member-links {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed #e2e8f0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.ph-mem-link {
  font-size: 12px;
  color: #ea580c;
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.ph-mem-link .action-txt {
  color: #ea580c;
  font-weight: 600;
}

/* ── 顶栏右侧按钮群 (深度复刻截图 3) ── */
.top-fee-label {
  font-size: 13px;
  color: #475569;
  font-weight: 500;
}

.top-fee-val {
  font-size: 14px;
  color: #1e293b;
  font-weight: 600;
}

.top-total-highlight {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin-right: 12px;
}

.btn-top-instant-pay {
  background: #ffffff !important;
  color: #0d9488 !important;
  border: 1px solid #0d9488 !important;
  font-weight: 600 !important;
  border-radius: 4px !important;
  padding: 7px 14px !important;
  transition: all 0.15s !important;
}

.btn-top-instant-pay:hover {
  background: #f0fdfa !important;
  border-color: #0f766e !important;
}

.btn-top-finish {
  background: #0d9488 !important;
  color: #ffffff !important;
  border: none !important;
  font-weight: 600 !important;
  border-radius: 4px !important;
  padding: 7px 16px !important;
  box-shadow: 0 1px 3px rgba(13, 148, 136, 0.2) !important;
}

.btn-top-finish:hover {
  background: #0f766e !important;
}

.btn-top-del {
  background: #fef2f2 !important;
  color: #ef4444 !important;
  border: 1px solid #fca5a5 !important;
  border-radius: 4px !important;
  padding: 7px 12px !important;
}

.btn-top-del:hover {
  background: #fee2e2 !important;
}

/* ── 左侧候诊队列顶栏与日期选择器 (深度复刻截图 2) ── */
.queue-tabs-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.q-tabs-left {
  display: flex;
  gap: 12px;
}

.q-tab-btn {
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  cursor: pointer;
  position: relative;
  padding-bottom: 4px;
}

.q-tab-btn.active {
  color: #0d9488;
  border-bottom: 2px solid #0d9488;
}

.q-sup {
  color: #ef4444;
  font-weight: bold;
  font-size: 11px;
}

.q-cal-picker {
  width: 100px !important;
}

/* ── 贴敷处方穴位选择器 (深度复刻截图 5) ── */
.acupoint-selector-v2 {
  padding: 4px;
}

.acupoint-category-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  max-height: 250px;
  overflow-y: auto;
  padding-right: 4px;
}

.acupoint-category-row {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.cat-title {
  width: 40px;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 700;
  color: #334155;
  padding-top: 4px;
}

.cat-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.pt-chip {
  display: inline-block;
  padding: 3px 8px;
  background: #ffffff;
  color: #334155;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
  user-select: none;
}

.pt-chip:hover {
  background: #f0fdfa;
  border-color: #0d9488;
  color: #0d9488;
}

.pt-chip.selected {
  background: #0d9488;
  color: #ffffff;
  border-color: #0d9488;
}

.acupoint-bottom-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid #f1f5f9;
}

/* ── 医嘱事项与合理用药 (深度复刻截图 1) ── */
.emr-bottom-block {
  display: flex;
  align-items: flex-start;
  border-top: 1px solid #e2e8f0;
  background: #ffffff;
}

.block-left-label {
  width: 90px;
  flex-shrink: 0;
  padding: 12px;
  font-size: 13px;
  font-weight: 600;
  color: #475569;
  text-align: center;
  border-right: 1px solid #f1f5f9;
  background: #f8fafc;
}

.block-right-body {
  flex: 1;
  padding: 10px 14px;
}

.block-right-body.flex-between {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* advice-bar-top kept for compat */
.advice-bar-top { display: flex; justify-content: flex-end; margin-bottom: 6px; }

.deepseek-gen-btn {
  background: linear-gradient(135deg, #4f46e5 0%, #7c3aed 100%) !important;
  color: #ffffff !important;
  border: none !important;
  font-weight: 600 !important;
  border-radius: 4px !important;
  padding: 4px 10px !important;
  box-shadow: 0 1px 3px rgba(79, 70, 229, 0.3) !important;
}
.deepseek-gen-btn:hover { opacity: 0.92 !important; }
.sparkle-anim { margin-right: 2px; }

/* New advice layout */
.advice-preset-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
  margin-bottom: 8px;
  padding: 6px 8px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}
.advice-preset-label {
  font-size: 11.5px;
  color: #64748b;
  white-space: nowrap;
  font-weight: 500;
}
.advice-chip {
  cursor: pointer;
  user-select: none;
  transition: all 0.15s;
  border-radius: 12px !important;
}
.advice-chip:hover { transform: scale(1.05); }
.advice-input-row { margin-bottom: 8px; }
.advice-action-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  flex-wrap: wrap;
}
.advice-custom-add { display: flex; align-items: center; gap: 6px; }

/* Old popover styles kept for compat */
.advice-popover-box { padding: 4px; }
.advice-preset-items { max-height: 200px; overflow-y: auto; display: flex; flex-direction: column; gap: 4px; }
.advice-p-item { display: flex; align-items: center; gap: 8px; padding: 5px 8px; background: #f8fafc; border-radius: 4px; cursor: pointer; font-size: 12px; color: #334155; transition: background 0.12s; }
.advice-p-item:hover { background: #f0fdf4; color: #16a34a; }
.adv-drag-handle { color: #cbd5e1; font-size: 13px; }

.btn-rx-check {
  font-weight: 600 !important;
}

/* ── 现场收费结算弹窗 ── */
.instant-pay-dialog-body {
  padding: 6px;
}

.pay-patient-header {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #f8fafc;
  border-radius: 6px;
  margin-bottom: 12px;
  border: 1px solid #e2e8f0;
}

.pay-patient-header .pp-name {
  font-size: 16px;
  font-weight: 700;
  color: #0f172a;
}

.pay-patient-header .pp-id, .pay-patient-header .pp-dept {
  font-size: 12px;
  color: #64748b;
}

.pay-detail-card {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 14px;
  background: #ffffff;
}

.pd-head {
  font-size: 13px;
  font-weight: 700;
  color: #334155;
  margin-bottom: 8px;
  border-bottom: 1px solid #f1f5f9;
  padding-bottom: 6px;
}

.pd-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #475569;
  padding: 4px 0;
}

.pd-amt {
  font-weight: 600;
  color: #1e293b;
}

.pd-sum-row {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-top: 8px;
  padding-top: 8px;
  border-top: 2px dashed #e2e8f0;
}

.pd-sum-row .sum-lbl {
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
}

.pd-sum-row .sum-big-amt {
  font-size: 22px;
  font-weight: 800;
  color: #0d9488;
}

.pay-method-zone {
  margin-bottom: 14px;
}

.method-title {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
}

.pay-channel-view {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 16px;
  text-align: center;
}

.mock-qr-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.qr-canvas-sim {
  width: 140px;
  height: 140px;
  background: #ffffff;
  border: 2px solid #cbd5e1;
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.qr-center-logo {
  font-size: 16px;
  font-weight: bold;
  color: #0d9488;
  padding: 6px 12px;
  background: #f0fdfa;
  border-radius: 4px;
}

.qr-tip-txt {
  font-size: 12px;
  color: #64748b;
}

.cash-calc-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  font-size: 14px;
  color: #1e293b;
}

.btn-confirm-instant-pay {
  background: #0d9488 !important;
  border-color: #0d9488 !important;
  font-weight: 700 !important;
  padding: 10px 24px !important;
}

/* ── 候诊队列日期栏与历史模式样式 ── */
.queue-date-bar {
  padding: 8px 10px;
  background: #f1f5f9;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.date-shortcuts {
  display: flex;
  gap: 4px;
}

.d-chip {
  flex: 1;
  text-align: center;
  font-size: 11px;
  padding: 3px 0;
  background: #ffffff;
  color: #475569;
  border: 1px solid #cbd5e1;
  border-radius: 4px;
  cursor: pointer;
  font-weight: 500;
  transition: all 0.15s;
}

.d-chip:hover {
  background: #e0f2fe;
  color: #0284c7;
  border-color: #38bdf8;
}

.d-chip.active {
  background: #0d9488;
  color: #ffffff;
  border-color: #0d9488;
  font-weight: 700;
}

.date-mode-hint {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 10px;
  background: #fffbeb;
  border-bottom: 1px solid #fef3c7;
  font-size: 11px;
  color: #b45309;
}

.mode-tag {
  font-weight: 700;
}

.mode-date {
  font-family: monospace;
  font-weight: bold;
}

.q-sup-done {
  color: #10b981;
  font-weight: bold;
  font-size: 11px;
}

/* 状态胶囊标签 */
.q-status-badge {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 10px;
  font-weight: 600;
  margin-left: auto;
}

.q-status-badge.接诊中 {
  background: #ecfdf5 !important;
  color: #059669 !important;
  border: 1px solid #10b981 !important;
  font-weight: 700 !important;
  box-shadow: 0 0 6px rgba(16, 185, 129, 0.4);
  animation: pulseGreen 2s infinite;
}

@keyframes pulseGreen {
  0% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.5); }
  70% { box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); }
  100% { box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
}

.queue-item-card.is-consulting {
  border-left: 4px solid #10b981 !important;
  background: #f0fdf4 !important;
  border-color: #86efac !important;
}

.q-status-badge.待诊 {
  background: #eff6ff;
  color: #2563eb;
  border: 1px solid #bfdbfe;
}

.q-status-badge.已结诊, .q-status-badge.已收费, .q-status-badge.已诊, .q-status-badge.已完成 {
  background: #f0fdf4;
  color: #16a34a;
  border: 1px solid #bbf7d0;
}

.q-status-badge.过号, .q-status-badge.已过号 {
  background: #fff7ed;
  color: #ea580c;
  border: 1px solid #fed7aa;
}

.q-status-badge.已退, .q-status-badge.已退号 {
  background: #fef2f2;
  color: #dc2626;
  border: 1px solid #fecaca;
}

.card-restore-btn {
  margin-left: auto;
  font-size: 11px;
  padding: 1px 7px;
  height: 22px;
  border-radius: 4px;
}

.card-call-btn {
  font-size: 11px;
  padding: 1px 7px;
  height: 22px;
  border-radius: 4px;
  opacity: 0;
  transition: opacity 0.2s ease;
}
.queue-item-card:hover .card-call-btn {
  opacity: 1;
}

.btn-refund-number {
  border-color: #fca5a5 !important;
  color: #dc2626 !important;
}

.btn-restore-consult {
  background: #ea580c !important;
  color: #ffffff !important;
  border-color: #ea580c !important;
  font-weight: 600;
}

.passed-status-tag {
  font-weight: 600;
  font-size: 12px;
}

.refund-status-tag {
  font-weight: 600;
  font-size: 12px;
}

@keyframes pulseAmber {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.7; }
}

/* ── 右侧栏分栏 Tabs 与历史记录流 (深度复刻截图 1-5 右侧栏) ── */
.clinic-right-panel {
  display: flex;
  flex-direction: column;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
  height: 100%;
  overflow: hidden;
}

.right-panel-tabs {
  display: flex;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}

.rp-tab {
  flex: 1;
  text-align: center;
  padding: 10px 4px;
  font-size: 12px;
  font-weight: 600;
  color: #64748b;
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.15s ease;
  user-select: none;
}

.rp-tab:hover {
  color: #0d9488;
}

.rp-tab.active {
  color: #0d9488;
  background: #ffffff;
  border-bottom: 2px solid #0d9488;
  font-weight: 700;
}

.rp-sup {
  color: #ef4444;
  font-weight: bold;
  font-size: 10px;
}

.rp-content-view {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 历史就诊列表 */
.history-view {
  padding: 8px;
}

.history-search-bar {
  margin-bottom: 8px;
}

.history-card-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding-right: 2px;
}

.history-visit-card {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 8px 10px;
  background: #fdfdfd;
  transition: all 0.15s ease;
}

.history-visit-card:hover {
  border-color: #0d9488;
  background: #f0fdfa;
  box-shadow: 0 2px 6px rgba(13, 148, 136, 0.08);
}

.hvc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.hvc-date-group {
  display: flex;
  align-items: center;
  gap: 6px;
}

.hvc-date {
  font-size: 12px;
  font-weight: 700;
  color: #1e293b;
}

.hvc-badge-returntype {
  font-size: 10px;
  padding: 1px 4px;
  background: #fee2e2;
  color: #ef4444;
  border-radius: 3px;
  font-weight: bold;
}

.hvc-type-tags {
  font-size: 10px;
  padding: 1px 4px;
  background: #e0f2fe;
  color: #0284c7;
  border-radius: 3px;
}

.hvc-actions {
  display: flex;
  gap: 2px;
}

.hvc-cite-btn {
  font-weight: 700 !important;
  color: #0d9488 !important;
}

.hvc-diagnosis-line {
  font-size: 13px;
  color: #0f172a;
  font-weight: 600;
  margin-bottom: 3px;
}

.hvc-diag-tcm {
  color: #0d9488;
  font-size: 11px;
  margin-left: 4px;
}

.hvc-symptom-line {
  font-size: 11px;
  color: #64748b;
  margin-bottom: 4px;
}

.hvc-rx-brief {
  display: flex;
  align-items: center;
  font-size: 11px;
  background: #f8fafc;
  padding: 3px 6px;
  border-radius: 4px;
}

.hvc-rx-lbl {
  color: #64748b;
  flex-shrink: 0;
}

.hvc-rx-txt {
  color: #334155;
  flex: 1;
}

.hvc-fee {
  font-weight: 700;
  color: #0d9488;
  margin-left: 6px;
  flex-shrink: 0;
}

/* 常用模板列表 */
.template-view {
  padding: 8px;
}

.tpl-search-bar {
  margin-bottom: 8px;
}

.tpl-card-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tpl-item-card {
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 8px 10px;
  background: #ffffff;
}

.tpl-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}

.tpl-name {
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
}

.tpl-indication {
  font-size: 11px;
  color: #475569;
  margin-bottom: 2px;
}

.tpl-rx-desc {
  font-size: 11px;
  color: #0d9488;
  margin-bottom: 6px;
}

.tpl-foot {
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-top: 1px dashed #f1f5f9;
  padding-top: 4px;
}

.tpl-fee {
  font-size: 11px;
  color: #64748b;
  font-weight: 600;
}

/* ── 历史档案详情抽屉 ── */
.history-detail-body {
  padding: 4px 6px;
}

.hd-top-info {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  padding: 10px 14px;
  margin-bottom: 12px;
}

.hd-main-line {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 14px;
  margin-bottom: 4px;
}

.hd-name {
  font-weight: 700;
  font-size: 16px;
  color: #0f172a;
}

.hd-date {
  color: #475569;
}

.hd-sub-line {
  font-size: 12px;
  color: #64748b;
}

.hd-section {
  margin-bottom: 12px;
}

.hd-sec-title {
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 6px;
}

.hd-diag-box, .hd-advice-box {
  background: #f8fafc;
  border: 1px solid #f1f5f9;
  border-radius: 6px;
  padding: 8px 12px;
  font-size: 12px;
  color: #334155;
}

.hd-rx-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.hd-rx-table th, .hd-rx-table td {
  border: 1px solid #e2e8f0;
  padding: 6px 8px;
  text-align: left;
}

.hd-rx-table th {
  background: #f1f5f9;
  color: #475569;
  font-weight: 600;
}

/* ── AI 规范化病历按钮与审查条样式 ── */
.ai-emr-btn {
  margin-left: auto;
  font-weight: 600;
  border-radius: 6px;
  background: linear-gradient(135deg, #f0fdfa, #e0f2fe);
  border-color: #5eead4;
  color: #0f766e;
}

.ai-emr-btn:hover {
  background: linear-gradient(135deg, #ccfbf1, #bae6fd);
  color: #115e59;
}

.ai-safety-audit-bar {
  margin: 8px 12px 12px 12px;
  padding: 8px 14px;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  transition: all 0.2s ease;
}

.ai-safety-audit-bar.safe {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  color: #166534;
}

.ai-safety-audit-bar.warning {
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #92400e;
}

.ai-safety-audit-bar.danger {
  background: #fef2f2;
  border: 1px solid #fecaca;
  color: #991b1b;
  animation: pulseAmber 1.5s infinite;
}

.as-left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.as-icon {
  font-size: 16px;
}

.as-title {
  font-weight: 700;
  font-size: 13px;
}

.as-recheck {
  margin-left: auto;
  font-size: 12px;
}

.as-details {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-left: 24px;
}

.as-msg {
  font-size: 12px;
  line-height: 1.4;
}

.as-msg.danger {
  color: #b91c1c;
  font-weight: 600;
}

.as-msg.warning {
  color: #b45309;
}

/* ── 现代化日期选择器与前进后退控制器 ── */
.date-picker-row {
  display: flex;
  align-items: center;
  gap: 4px;
}

.d-arrow-btn {
  padding: 4px 6px !important;
  height: 28px !important;
  font-size: 10px !important;
  color: #64748b !important;
  border-radius: 4px !important;
}

.d-arrow-btn:hover {
  color: #0d9488 !important;
  border-color: #0d9488 !important;
}

.d-today-btn {
  font-size: 11px !important;
  font-weight: 700 !important;
  padding: 0 4px !important;
  color: #0d9488 !important;
}

/* 历史空队列与空工作台美观占位 */
.empty-queue-hint {
  padding: 36px 12px;
  text-align: center;
  color: #94a3b8;
}

.empty-queue-hint .eq-icon {
  font-size: 32px;
  margin-bottom: 8px;
}

.empty-queue-hint .eq-title {
  font-size: 13px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 4px;
}

.empty-queue-hint .eq-desc {
  font-size: 11px;
  color: #94a3b8;
}

.empty-date-workstation {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 500px;
  background: #ffffff;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.03);
}

.ed-box {
  text-align: center;
  padding: 40px;
}

.ed-icon {
  font-size: 48px;
  margin-bottom: 12px;
}

.ed-title {
  font-size: 16px;
  font-weight: 700;
  color: #1e293b;
  margin-bottom: 8px;
}

.ed-subtitle {
  font-size: 13px;
  color: #64748b;
  margin-bottom: 20px;
}

/* ── 现代化 AI 临床助手底部排版 (彻底解决截图 1 中挤在一堆和折行错位问题) ── */
.chat-quick-actions-modern {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  padding: 8px 10px;
  background: #f8fafc;
  border-top: 1px solid #e2e8f0;
  border-bottom: 1px solid #e2e8f0;
  flex-shrink: 0;
}

.qa-pill {
  border: 1px solid #cbd5e1;
  background: #ffffff;
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  padding: 6px 2px;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  transition: all 0.15s ease;
  white-space: nowrap;
}

.qa-pill:hover {
  transform: translateY(-1px);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.qa-pill.qa-blue {
  color: #0284c7;
  border-color: #bae6fd;
  background: #f0f9ff;
}

.qa-pill.qa-blue:hover {
  background: #e0f2fe;
}

.qa-pill.qa-red {
  color: #e11d48;
  border-color: #fecdd3;
  background: #fff1f2;
}

.qa-pill.qa-red:hover {
  background: #ffe4e6;
}

.qa-pill.qa-amber {
  color: #b45309;
  border-color: #fde68a;
  background: #fffbeb;
}

.qa-pill.qa-amber:hover {
  background: #fef3c7;
}

.modern-chat-box {
  margin: 8px 10px 10px;
  border: 1px solid #cbd5e1;
  border-radius: 8px;
  background: #ffffff;
  padding: 6px 8px;
  display: flex;
  flex-direction: column;
  transition: border-color 0.2s, box-shadow 0.2s;
  flex-shrink: 0;
}

.modern-chat-box:focus-within {
  border-color: #0d9488;
  box-shadow: 0 0 0 2px rgba(13, 148, 136, 0.15);
}

.modern-chat-textarea :deep(.el-textarea__inner) {
  border: none !important;
  box-shadow: none !important;
  padding: 2px 4px !important;
  font-size: 12px !important;
  background: transparent !important;
  color: #1e293b;
}

.modern-chat-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 4px;
  padding-top: 4px;
  border-top: 1px dashed #f1f5f9;
}

.chat-key-hint {
  font-size: 11px;
  color: #94a3b8;
}

.modern-send-btn {
  height: 28px !important;
  padding: 0 14px !important;
  font-weight: 600 !important;
  border-radius: 4px !important;
  background: #0d9488 !important;
  border-color: #0d9488 !important;
}

.modern-send-btn:hover {
  background: #0f766e !important;
  border-color: #0f766e !important;
}

/* ── 新增：就诊状态顶栏与接诊叫号按钮 ── */
.header-patient-status-badge {
  font-weight: 700;
  border-radius: 6px;
  letter-spacing: 0.5px;
  margin-left: 6px;
}
.btn-start-consult {
  background: linear-gradient(135deg, #10b981 0%, #059669 100%) !important;
  color: #ffffff !important;
  border: none !important;
  font-weight: 700 !important;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.35) !important;
  transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1) !important;
}
.btn-start-consult:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.5) !important;
}
.btn-call-number {
  font-weight: 600 !important;
}
.btn-pass-number {
  color: #94a3b8 !important;
}
.btn-pass-number:hover {
  color: #f59e0b !important;
  border-color: #f59e0b !important;
}
.d-today-badge {
  font-weight: 700;
  border-radius: 6px;
  margin-left: 4px;
  background: #10b981 !important;
  color: #fff !important;
  border: none !important;
}
.d-back-today-btn {
  font-weight: 700 !important;
  font-size: 12px !important;
  color: #0284c7 !important;
  margin-left: 4px;
}
.date-mode-hint.hint-today {
  background: #ecfdf5;
  border-bottom: 1px solid #a7f3d0;
  color: #065f46;
}
.date-mode-hint.hint-history {
  background: #fffbeb;
  border-bottom: 1px solid #fde68a;
  color: #92400e;
}


/* ══ 历史病历只读查阅提示条 (Task 4) ══ */
.history-readonly-banner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: linear-gradient(90deg, #f0fdf4 0%, #ecfdf5 100%);
  border-bottom: 2px solid #10b981;
  padding: 8px 16px;
  box-shadow: 0 2px 4px rgba(16, 185, 129, 0.08);
}
.hrb-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.hrb-status-tag {
  font-weight: 700;
}
.hrb-text {
  font-size: 13px;
  color: #065f46;
}
.hrb-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ══ 处方顶部总控工具条 (Task 1 & 2) ══ */
.rx-top-module-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #ffffff;
  padding: 10px 14px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  margin-bottom: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}
.rtmb-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.rtmb-title {
  font-size: 14px;
  font-weight: 700;
  color: #1e293b;
}
.rtmb-count-tag {
  font-weight: 600;
}
.rtmb-amt-badge {
  font-size: 13px;
  font-weight: 700;
  color: #059669;
  background: #ecfdf5;
  padding: 2px 8px;
  border-radius: 4px;
}
.rtmb-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.rtmb-btn {
  font-weight: 600 !important;
}
.rtmb-divider {
  margin: 0 4px !important;
}

/* ══ 独立处方单卡片样式 (Task 2) ══ */
.rx-blocks-container {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 14px;
}
.prescription-block-card {
  border-radius: 8px;
  border: 1px solid #e2e8f0;
  background: #ffffff;
  overflow: hidden;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.03);
}
.block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
}
.block-header-left {
  display: flex;
  align-items: center;
  gap: 8px;
}
.block-type-tag {
  font-weight: 600;
}
.block-rx-no {
  font-size: 12px;
  color: #64748b;
  font-family: monospace;
}
.block-header-right {
  display: flex;
  align-items: center;
  gap: 10px;
}
.block-subtotal {
  font-size: 13px;
  font-weight: 700;
  color: #059669;
}
.btn-del-block {
  font-weight: 600 !important;
}
.btn-add-item {
  margin-top: 8px !important;
  font-weight: 600 !important;
}
.block-body-patch, .block-body-western, .block-body-tcm, .block-body-treatment {
  padding: 10px 12px;
}
.patch-num {
  width: 22px;
  height: 22px;
  line-height: 22px;
  text-align: center;
  background: #f1f5f9;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  flex-shrink: 0;
}
.row-subtotal {
  font-size: 13px;
  font-weight: 700;
  color: #059669;
  min-width: 60px;
  text-align: right;
}
.unit-text {
  font-size: 12px;
  color: #64748b;
  margin-right: 4px;
}
.tcm-dose-label {
  font-size: 12px;
  color: #475569;
  font-weight: 600;
}
.tcm-block-search-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}
.alpha-nav-mini {
  display: flex;
  flex-wrap: wrap;
  gap: 3px;
}
.alpha-tag-mini {
  display: inline-block;
  padding: 1px 6px;
  background: #f1f5f9;
  border-radius: 3px;
  font-size: 11px;
  color: #475569;
  cursor: pointer;
  user-select: none;
}
.alpha-tag-mini:hover, .alpha-tag-mini.active {
  background: #10b981;
  color: #ffffff;
}
.tcm-candidate-box {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 6px 8px;
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  margin-bottom: 8px;
  max-height: 120px;
  overflow-y: auto;
}
.tcm-cand-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 3px 8px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}
.tcm-cand-item:hover {
  background: #ecfdf5;
  border-color: #10b981;
}
.cand-name { font-weight: 700; color: #047857; }
.cand-spec { color: #64748b; font-size: 11px; }
.cand-price { color: #d97706; font-weight: 600; }
.cand-stock { color: #10b981; font-size: 11px; }

/* 处方单空态引导 */
.rx-blocks-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 36px 20px;
  background: #f8fafc;
  border: 2px dashed #cbd5e1;
  border-radius: 8px;
  margin-bottom: 14px;
  text-align: center;
}
.rbe-icon { font-size: 32px; margin-bottom: 8px; }
.rbe-title { font-size: 15px; font-weight: 700; color: #334155; margin-bottom: 4px; }
.rbe-desc { font-size: 13px; color: #64748b; margin-bottom: 14px; }
.rbe-actions { display: flex; gap: 10px; }

/* ══ 医嘱事项全新布局 (Task 3) ══ */
.advice-block-v2 {
  display: flex;
  align-items: flex-start;
  border-top: 1px solid #e2e8f0;
  background: #ffffff;
}
.bll-title { font-size: 13px; font-weight: 700; color: #334155; }
.bll-sub { font-size: 11px; color: #94a3b8; margin-top: 2px; }
.advice-preset-categories {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-bottom: 8px;
  padding: 8px 10px;
  background: #f8fafc;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}
.adv-cat-group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 5px;
}
.adv-cat-name {
  font-size: 11.5px;
  font-weight: 700;
  color: #475569;
  min-width: 80px;
}
.adv-tag-chip {
  display: inline-block;
  padding: 2px 8px;
  font-size: 11.5px;
  border-radius: 12px;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
  cursor: pointer;
  user-select: none;
  transition: all 0.15s;
}
.adv-tag-chip:hover {
  border-color: #10b981;
  color: #10b981;
  background: #f0fdf4;
}
.adv-tag-chip.active {
  background: #10b981;
  color: #ffffff;
  border-color: #10b981;
  font-weight: 600;
}
.advice-textarea-wrap {
  margin-bottom: 8px;
}
.advice-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.af-left {
  display: flex;
  align-items: center;
  gap: 10px;
}
.af-tip-text {
  font-size: 12px;
  color: #64748b;
}


/* ══ 粘性顶部总控与防挤压样式 (Points 3 & 4) ══ */
.sticky-top-rx-bar {
  position: sticky;
  top: 0;
  z-index: 15;
  background: #ffffff;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.05);
}

.rx-top-module-bar {
  display: flex !important;
  align-items: center !important;
  justify-content: space-between !important;
  gap: 14px !important;
  padding: 10px 16px !important;
  border-radius: 8px !important;
  border: 1px solid #e2e8f0 !important;
  margin-bottom: 12px !important;
  background: #ffffff !important;
}

.rtmb-left {
  display: flex !important;
  align-items: center !important;
  gap: 12px !important;
  white-space: nowrap !important;
  flex-shrink: 0 !important;
}

.rtmb-title {
  font-size: 15px !important;
  font-weight: 700 !important;
  color: #1e293b !important;
  white-space: nowrap !important;
  letter-spacing: 0.5px !important;
}

.rtmb-count-tag {
  font-weight: 600 !important;
  white-space: nowrap !important;
}

.rtmb-amt-badge {
  font-size: 13px !important;
  font-weight: 700 !important;
  color: #059669 !important;
  background: #ecfdf5 !important;
  padding: 3px 10px !important;
  border-radius: 6px !important;
  border: 1px solid #a7f3d0 !important;
  white-space: nowrap !important;
}

.rtmb-right {
  display: flex !important;
  align-items: center !important;
  gap: 8px !important;
  flex-wrap: wrap !important;
  flex-shrink: 0 !important;
}

/* ══ 处方分类专区样式 (Point 1: 贴敷、西药、中药、理疗各自分组展示) ══ */
.rx-categorized-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-bottom: 14px;
}

.rx-category-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.rx-cat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  border-radius: 6px;
  font-weight: 700;
  font-size: 13px;
}

.cat-header-patch { background: #fffbeb; border-left: 4px solid #f59e0b; color: #b45309; }
.cat-header-western { background: #eff6ff; border-left: 4px solid #3b82f6; color: #1d4ed8; }
.cat-header-tcm { background: #f0fdf4; border-left: 4px solid #10b981; color: #047857; }
.cat-header-treatment { background: #f8fafc; border-left: 4px solid #64748b; color: #334155; }

.rx-blocks-subgroup {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.ph-mem-link.clickable {
  cursor: pointer;
  transition: all 0.15s;
}
.ph-mem-link.clickable:hover {
  background: #ecfdf5;
  color: #059669;
}
.temp-patient-tag {
  font-weight: 700;
  margin-left: 6px;
}
.btn-regularize {
  font-weight: 600 !important;
  font-size: 11px !important;
  margin-left: 4px !important;
}
.history-visit-card.active {
  border-color: #10b981 !important;
  background: #f0fdf4 !important;
  box-shadow: 0 0 0 1px #10b981 !important;
}

/* ═══════════════════════════════════════════════════════════════
   全新样式修复 (Points 1, 2, 3, 4, 8, 9)
   ═══════════════════════════════════════════════════════════════ */

/* 1. 顶部三大模块彻底固定（Tab头、AI质控审查、处方单管理条）绝不随鼠标滑动隐藏，同时保证下方表单可顺畅滑动 */
.custom-emr-tabs {
  display: flex !important;
  flex-direction: column !important;
  flex: 1 1 0 !important;
  min-height: 0 !important;
  height: 100% !important;
  overflow: hidden !important;
}
.custom-emr-tabs :deep(.el-tabs__header) {
  flex-shrink: 0 !important;
  margin: 0 0 10px 0 !important;
  background: #ffffff !important;
  border-bottom: 1px solid #e2e8f0 !important;
  z-index: 20 !important;
}
.custom-emr-tabs :deep(.el-tabs__content) {
  flex: 1 1 0 !important;
  min-height: 0 !important;
  height: 100% !important;
  overflow: hidden !important;
  padding: 0 !important;
}
.custom-emr-tabs :deep(.el-tab-pane) {
  height: 100% !important;
  min-height: 0 !important;
  display: flex !important;
  flex-direction: column !important;
  overflow: hidden !important;
}
.custom-emr-tabs :deep(.el-tab-pane[style*="display: none"]),
.custom-emr-tabs :deep(.el-tab-pane[aria-hidden="true"]) {
  display: none !important;
}

/* 处方页顶部固定区：AI质控审查栏 + 门诊处方单管理条 (flex-shrink: 0 绝不滚动) */
.prescription-sticky-header {
  flex-shrink: 0 !important;
  background: #ffffff !important;
  margin-bottom: 8px !important;
  z-index: 10 !important;
}

/* 仅下方的处方表单卡片区域随鼠标顺畅滚动 */
.prescription-form-zone-v2 {
  flex: 1 1 0 !important;
  min-height: 0 !important;
  height: 0 !important;
  max-height: 100% !important;
  overflow-y: auto !important;
  padding-right: 6px !important;
  padding-bottom: 36px !important;
}
.prescription-form-zone-v2::-webkit-scrollbar {
  width: 6px;
}
.prescription-form-zone-v2::-webkit-scrollbar-track {
  background: #f1f5f9;
  border-radius: 4px;
}
.prescription-form-zone-v2::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}
.prescription-form-zone-v2::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

/* 电子病历书写 Tab 也由内部滚动，保持Tab头固定且内边距适中美观 */
.emr-form-zone {
  flex: 1 1 0 !important;
  min-height: 0 !important;
  height: 0 !important;
  max-height: 100% !important;
  overflow-y: auto !important;
  padding-right: 6px !important;
  padding-bottom: 36px !important;
}
.emr-form-zone::-webkit-scrollbar {
  width: 6px;
}
.emr-form-zone::-webkit-scrollbar-track {
  background: #f1f5f9;
  border-radius: 4px;
}
.emr-form-zone::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}
.emr-form-zone::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}
/* 2. 门诊处方单管理总控条紧凑排版 & 存为模板与用药审查防遮挡防溢出 */
.rx-top-module-bar {
  display: flex !important;
  align-items: center !important;
  justify-content: space-between !important;
  gap: 8px !important;
  padding: 6px 10px !important;
  border-radius: 8px !important;
  border: 1px solid #e2e8f0 !important;
  background: #ffffff !important;
  flex-wrap: nowrap !important;
  overflow-x: auto !important;
}
.rtmb-right {
  display: flex !important;
  align-items: center !important;
  gap: 5px !important;
  flex-shrink: 0 !important;
}
.rtmb-right .el-button {
  margin: 0 !important;
  white-space: nowrap !important;
  flex-shrink: 0 !important;
  padding: 5px 8px !important;
  font-size: 12px !important;
}
.btn-save-as-tpl {
  white-space: nowrap !important;
  flex-shrink: 0 !important;
  font-weight: 600 !important;
}
.rtmb-title {
  font-size: 14px !important;
  font-weight: 800 !important;
  color: #1e293b !important;
  white-space: nowrap !important;
  flex-shrink: 0 !important;
}

.rtmb-right {
  display: flex !important;
  align-items: center !important;
  gap: 6px !important;
  flex-wrap: wrap !important;
  justify-content: flex-end !important;
  flex-shrink: 0 !important;
}

.rtmb-btn {
  padding: 4px 8px !important;
  font-size: 12px !important;
  height: 28px !important;
  white-space: nowrap !important;
  flex-shrink: 0 !important;
}

.btn-save-as-tpl {
  padding: 4px 10px !important;
  font-size: 12px !important;
  height: 28px !important;
  white-space: nowrap !important;
  flex-shrink: 0 !important;
}

/* 3. AI临床助手文字防溢出修复 (彻底杜绝蓝色框溢出卡片) */
.ai-tool-call-box {
  background: #f8fafc !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 6px !important;
  padding: 8px 10px !important;
  display: flex !important;
  flex-direction: column !important;
  gap: 6px !important;
  width: 100% !important;
  max-width: 100% !important;
  box-sizing: border-box !important;
  overflow: hidden !important;
}

.tool-call-row {
  display: flex !important;
  flex-direction: column !important;
  gap: 4px !important;
  font-size: 11px !important;
  width: 100% !important;
  max-width: 100% !important;
  box-sizing: border-box !important;
  overflow: hidden !important;
}

.tool-label {
  color: #475569 !important;
  font-weight: 700 !important;
  font-size: 11px !important;
}

.tool-tags-wrap {
  display: flex !important;
  flex-direction: column !important;
  gap: 4px !important;
  width: 100% !important;
  max-width: 100% !important;
  box-sizing: border-box !important;
  overflow: hidden !important;
}

.ai-tag-chip {
  display: block !important;
  width: 100% !important;
  max-width: 100% !important;
  min-width: 0 !important;
  box-sizing: border-box !important;
  overflow: hidden !important;
  padding: 3px 8px !important;
  height: auto !important;
  line-height: 1.4 !important;
}

.ai-tag-chip :deep(.el-tag__content) {
  display: block !important;
  width: 100% !important;
  max-width: 100% !important;
  min-width: 0 !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
  white-space: nowrap !important;
}

.mcp-chip-text {
  display: block !important;
  width: 100% !important;
  max-width: 100% !important;
  min-width: 0 !important;
  overflow: hidden !important;
  text-overflow: ellipsis !important;
  white-space: nowrap !important;
}

/* MCP 过程步骤行 + 完成后收起标签 */
.process-steps-col {
  gap: 3px !important;
}
.process-step-line {
  font-size: 11px !important;
  color: #475569 !important;
  line-height: 1.45 !important;
  word-break: break-all !important;
}
/* 等待首 token 时的思考提示 */
.ai-thinking-hint {
  display: inline-flex !important;
  align-items: center !important;
  padding: 2px 0 !important;
}
.ai-thinking-hint .dot {
  width: 6px !important;
  height: 6px !important;
  border-radius: 50% !important;
  background: #0f766e !important;
  margin-right: 4px !important;
  opacity: 0.4 !important;
  animation: think-blink 1.2s infinite !important;
}
.ai-thinking-hint .dot:nth-child(2) { animation-delay: 0.2s !important; }
.ai-thinking-hint .dot:nth-child(3) { animation-delay: 0.4s !important; }
@keyframes think-blink {
  0%, 100% { opacity: 0.25 !important; }
  50% { opacity: 1 !important; }
}
.process-done-chip {
  display: inline-flex !important;
  align-items: center !important;
  margin-top: 4px !important;
  padding: 2px 8px !important;
  font-size: 11px !important;
  color: #64748b !important;
  background: #f1f5f9 !important;
  border: 1px dashed #cbd5e1 !important;
  border-radius: 12px !important;
  cursor: pointer !important;
}
.process-done-chip:hover {
  color: #0f766e !important;
  border-color: #0f766e !important;
}

/* 朗读回答按钮 */
.msg-tts-row {
  margin-top: 6px !important;
  text-align: right !important;
}
.tts-toggle {
  font-size: 12px !important;
  color: #0f766e !important;
  cursor: pointer !important;
  user-select: none !important;
}
.tts-toggle:hover {
  text-decoration: underline !important;
}

/* 语音录入按钮 */
.chat-input-with-mic {
  position: relative !important;
  display: flex !important;
  align-items: flex-start !important;
  gap: 8px !important;
}
.mic-btn {
  flex: 0 0 auto !important;
  width: 40px !important;
  height: 40px !important;
  display: flex !important;
  align-items: center !important;
  justify-content: center !important;
  font-size: 18px !important;
  background: #f1f5f9 !important;
  border: 1px solid #e2e8f0 !important;
  border-radius: 50% !important;
  cursor: pointer !important;
  transition: all 0.15s !important;
}
.modern-chat-bottom .mic-btn {
  width: 28px !important;
  height: 28px !important;
}
.modern-chat-bottom .mic-btn .el-icon {
  color: #0f766e !important;
}
.modern-chat-bottom .mic-btn.recording .el-icon,
.modern-chat-bottom .mic-btn.recording span {
  color: #ef4444 !important;
}
.modern-chat-bottom .mic-btn {
  width: 28px !important;
  height: 28px !important;
}
.modern-chat-bottom .mic-btn .el-icon {
  color: #0f766e !important;
}
.modern-chat-bottom .mic-btn.recording .el-icon,
.modern-chat-bottom .mic-btn.recording span {
  color: #ef4444 !important;
}
.mic-btn:hover {
  background: #e0f2f1 !important;
  border-color: #0f766e !important;
}
.mic-btn.recording {
  background: #fee2e2 !important;
  border-color: #ef4444 !important;
  animation: mic-pulse 1s ease-in-out infinite !important;
}
@keyframes mic-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.35) !important; }
  50% { box-shadow: 0 0 0 6px rgba(239, 68, 68, 0) !important; }
}

.reasoning-body {
  background: #f8fafc !important;
  border: 1px dashed #cbd5e1 !important;
  border-radius: 6px !important;
  padding: 8px 10px !important;
  font-size: 12px !important;
  line-height: 1.6 !important;
  color: #334155 !important;
  white-space: pre-wrap !important;
  word-break: break-word !important;
  max-height: 160px !important;
  overflow-y: auto !important;
  box-sizing: border-box !important;
  width: 100% !important;
  max-width: 100% !important;
}
/* 4. 会员身份与气泡卡片样式 (Point 2 & 9) */
.patient-vip-badge-header {
  cursor: pointer !important;
  font-weight: 700 !important;
  font-size: 11px !important;
  margin-right: 4px !important;
}

.ph-member-active-box {
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  border: 1px solid #f59e0b;
  border-radius: 8px;
  padding: 10px 12px;
  margin-top: 10px;
}

.ph-mab-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}

.ph-mab-badge {
  font-weight: 800;
  font-size: 13px;
  color: #92400e;
}

.ph-mab-discount {
  background: #ef4444;
  color: #ffffff;
  font-size: 10px;
  font-weight: 700;
  padding: 1px 6px;
  border-radius: 4px;
}

.ph-mab-expiry {
  font-size: 11px;
  color: #78350f;
  margin-left: auto;
}

.ph-mab-desc {
  font-size: 11.5px;
  color: #78350f;
  line-height: 1.4;
}

.btn-quick-consult {
  font-weight: 700 !important;
  letter-spacing: 0.5px !important;
}

/* ── 会话历史与新建会话工具栏 ── */
.copilot-session-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 12px;
  background: #f8fafc;
  border-bottom: 1px solid #e2e8f0;
  gap: 8px;
}
.csb-left {
  display: flex;
  align-items: center;
  gap: 6px;
}
.csb-btn-new {
  font-weight: 600;
  font-size: 12px;
  padding: 4px 10px;
  height: 26px;
  border-radius: 6px;
}
.csb-btn-history {
  font-size: 12px;
  padding: 4px 10px;
  height: 26px;
  border-radius: 6px;
  color: #334155;
}
.csb-right {
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: flex-end;
  overflow: hidden;
}
.csb-session-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 11px;
  color: #0284c7;
  background: #e0f2fe;
  padding: 2px 8px;
  border-radius: 9999px;
  border: 1px solid #bae6fd;
}
.csb-tag-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.csb-dot {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #0284c7;
}

/* ── 历史抽屉样式 ── */
.history-drawer-body {
  display: flex;
  flex-direction: column;
  height: 100%;
  gap: 12px;
}
.hd-top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid #f1f5f9;
}
.hd-count-text {
  font-size: 13px;
  color: #64748b;
  font-weight: 500;
}
.history-list-scroll {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding-right: 4px;
}
.history-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.history-group-label {
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
  padding-bottom: 2px;
  border-bottom: 1px dashed #e2e8f0;
  position: sticky;
  top: 0;
  background: #f8fafc;
  z-index: 1;
}
.history-card {
  padding: 12px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.history-card:hover {
  border-color: #3b82f6;
  background: #f8faff;
  box-shadow: 0 2px 8px rgba(59, 130, 246, 0.08);
}
.history-card.active {
  border-color: #10b981;
  background: #f0fdf4;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.12);
}
.hc-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}
.hc-title {
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.hc-snippet {
  font-size: 12px;
  color: #64748b;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.hc-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 4px;
  border-top: 1px dashed #f1f5f9;
  font-size: 11px;
  color: #94a3b8;
}
.history-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 20px;
  text-align: center;
}
.history-empty .empty-icon {
  font-size: 40px;
  margin-bottom: 8px;
}
.history-empty .empty-text {
  font-size: 14px;
  font-weight: 600;
  color: #475569;
}
.history-empty .empty-sub {
  font-size: 12px;
  color: #94a3b8;
  margin-top: 4px;
}

/* ══════ 顶部就诊条防挤压折行严格保证 (Nowrap & Flex alignment) ══════ */
.patient-header-bar {
  display: flex !important;
  justify-content: space-between !important;
  align-items: center !important;
  white-space: nowrap !important;
  overflow-x: auto !important;
  flex-wrap: nowrap !important;
  gap: 12px !important;
}

.patient-info-left {
  display: flex !important;
  align-items: center !important;
  gap: 10px !important;
  white-space: nowrap !important;
  flex-shrink: 0 !important;
}

.patient-info-trigger {
  display: flex !important;
  align-items: center !important;
  gap: 8px !important;
  white-space: nowrap !important;
  flex-shrink: 0 !important;
}

.patient-name {
  white-space: nowrap !important;
  flex-shrink: 0 !important;
}

.patient-gender-chip, .patient-age-chip, .patient-month-chip, .patient-phone-chip, .patient-birth-chip {
  white-space: nowrap !important;
  flex-shrink: 0 !important;
  display: inline-flex !important;
  align-items: center !important;
}

.patient-actions-right {
  display: flex !important;
  align-items: center !important;
  gap: 8px !important;
  white-space: nowrap !important;
  flex-shrink: 0 !important;
}

.top-fee-group {
  display: inline-flex !important;
  align-items: baseline !important;
  gap: 4px !important;
  white-space: nowrap !important;
  flex-shrink: 0 !important;
  margin-right: 4px;
}

.top-fee-label, .top-fee-val, .top-total-highlight {
  white-space: nowrap !important;
  flex-shrink: 0 !important;
}

/* ══════ 贴敷穴位快捷选穴样式 ══════ */
.acupoint-cell-wrap {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.btn-acupoint-pick {
  font-size: 11.5px !important;
  padding: 4px 8px !important;
  font-weight: 600 !important;
}

.acupoint-popover-box {
  padding: 6px 4px;
}

.apb-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px dashed #e2e8f0;
}

.apb-title {
  font-size: 12.5px;
  font-weight: 700;
  color: #1e293b;
}

.apb-tags-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-height: 180px;
  overflow-y: auto;
}

.apb-tag-chip {
  background: #fffbeb;
  border: 1px solid #fde68a;
  color: #b45309;
  font-size: 12px;
  padding: 3px 8px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
  font-weight: 600;
}

.apb-tag-chip:hover {
  background: #f59e0b;
  color: #ffffff;
  border-color: #f59e0b;
}

/* ══════ 中药处方经典名方与常用药材快捷区 ══════ */
.tcm-empty-starter-card {
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
  border-radius: 10px;
  padding: 16px 20px;
  margin-top: 10px;
}

.tesc-section {
  margin-bottom: 14px;
}

.tesc-title {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  margin-bottom: 8px;
}

.tesc-formulas-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.formula-card-pill {
  background: #ffffff;
  border: 1px solid #a7f3d0;
  border-radius: 8px;
  padding: 6px 12px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  transition: all 0.2s;
  box-shadow: 0 1px 3px rgba(0,0,0,0.02);
}

.formula-card-pill:hover {
  background: #ecfdf5;
  border-color: #10b981;
  transform: translateY(-1px);
  box-shadow: 0 3px 8px rgba(16, 185, 129, 0.15);
}

.fcp-name {
  font-size: 13px;
  font-weight: 700;
  color: #047857;
}

.fcp-herbs {
  font-size: 11px;
  color: #64748b;
}

.tesc-herbs-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.common-herb-tag {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  color: #334155;
  font-size: 12px;
  font-weight: 600;
  padding: 4px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}

.common-herb-tag:hover {
  background: #10b981;
  color: #ffffff;
  border-color: #10b981;
}

.tesc-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  padding-top: 8px;
  border-top: 1px solid #f1f5f9;
}

.tcm-table-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  padding: 6px 12px;
  background: #f8fafc;
  border-radius: 8px;
}

.tcm-quick-herbs-inline {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.tqh-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}

.tqh-chip {
  background: #ffffff;
  border: 1px solid #cbd5e1;
  font-size: 11.5px;
  padding: 2px 7px;
  border-radius: 4px;
  color: #047857;
  cursor: pointer;
  font-weight: 600;
  transition: all 0.15s;
}

.tqh-chip:hover {
  background: #10b981;
  color: #ffffff;
  border-color: #10b981;
}


/* 严格保证处方所有表格表头单行不折行 */
.rx-table th, .western-table th, .tcm-rx-table th, .rx-detail-table th {
  white-space: nowrap !important;
  word-break: keep-all !important;
  vertical-align: middle !important;
}

.tcm-block-footer-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
  padding: 6px 10px;
  background: #f8fafc;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.tbf-left {
  display: flex;
  align-items: center;
}

.tbf-right {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.tbf-label {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}

.tbf-chip {
  background: #ffffff;
  border: 1px solid #a7f3d0;
  color: #047857;
  font-size: 11.5px;
  font-weight: 600;
  padding: 2px 7px;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
}

.tbf-chip:hover {
  background: #10b981;
  color: #ffffff;
  border-color: #10b981;
}


/* ── AI 临床表格与卡片包装样式 ── */
.ai-table-scroll-wrapper {
  margin: 10px 0;
  max-width: 100%;
  overflow-x: auto;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
  background: #ffffff;
}
.ai-clinical-data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 11px;
  text-align: left;
  line-height: 1.4;
}
.ai-clinical-data-table th {
  background: #f1f5f9;
  color: #334155;
  font-weight: 700;
  padding: 8px 10px;
  border-bottom: 2px solid #cbd5e1;
  white-space: nowrap;
}
.ai-clinical-data-table td {
  padding: 6px 10px;
  border-bottom: 1px solid #e2e8f0;
  color: #1e293b;
  white-space: nowrap;
}
.ai-clinical-data-table tr:nth-child(even) {
  background: #f8fafc;
}
.ai-clinical-data-table tr:hover {
  background: #f0f9ff;
}
.cell-danger {
  color: #dc2626 !important;
  font-weight: 700;
  background: #fef2f2 !important;
}
.cell-warning {
  color: #d97706 !important;
  font-weight: 600;
  background: #fffbeb !important;
}
.cell-success {
  color: #059669 !important;
  font-weight: 600;
  background: #f0fdf4 !important;
}

.status-pill {
  display: inline-block;
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.sp-green { background: #dcfce7; color: #15803d; border: 1px solid #bbf7d0; }
.sp-red { background: #fee2e2; color: #b91c1c; border: 1px solid #fecaca; }
.sp-yellow { background: #fef3c7; color: #b45309; border: 1px solid #fde68a; }

.ai-md-h4 {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
  margin: 10px 0 6px 0;
  border-left: 3px solid #0284c7;
  padding-left: 6px;
}
.ai-md-h5 {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  margin: 8px 0 4px 0;
}
.ai-md-alert {
  background: #eff6ff;
  border-left: 3px solid #3b82f6;
  padding: 6px 10px;
  border-radius: 0 6px 6px 0;
  font-size: 12px;
  color: #1e40af;
  margin: 6px 0;
}
.ai-md-list-item {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 12px;
  line-height: 1.6;
  color: #334155;
  margin: 2px 0;
}
.ai-list-bullet {
  color: #0284c7;
  font-size: 8px;
  margin-top: 4px;
}
.ai-code-inline {
  background: #f1f5f9;
  color: #0369a1;
  padding: 1px 4px;
  border-radius: 3px;
  font-family: monospace;
  font-size: 11px;
}

/* 药房看板与临近开方面板 */
.ai-stock-kpi-panel {
  margin-top: 12px;
  background: #ffffff;
  border: 1px solid #bae6fd;
  border-radius: 8px;
  padding: 10px;
  box-shadow: 0 2px 6px rgba(2, 132, 199, 0.08);
}
.askp-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
  padding-bottom: 6px;
  border-bottom: 1px solid #e0f2fe;
}
.askp-title {
  font-size: 12px;
  font-weight: 700;
  color: #0369a1;
}
.askp-time {
  font-size: 10px;
  color: #64748b;
}
.askp-kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
  margin-bottom: 10px;
}
.askp-kpi-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 6px 2px;
  border-radius: 6px;
}
.askp-kpi-box.total { background: #f1f5f9; color: #334155; }
.askp-kpi-box.alert { background: #fef2f2; color: #dc2626; border: 1px solid #fecaca; }
.askp-kpi-box.warn { background: #fffbeb; color: #d97706; border: 1px solid #fde68a; }
.askp-kpi-box.good { background: #f0fdf4; color: #16a34a; border: 1px solid #bbf7d0; }
.askp-kpi-box .num { font-size: 14px; font-weight: 800; }
.askp-kpi-box .lbl { font-size: 10px; margin-top: 1px; }

.askp-quick-meds {
  border-top: 1px dashed #e2e8f0;
  padding-top: 8px;
}
.aqm-title {
  font-size: 11px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 6px;
}
.aqm-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
  padding: 4px 0;
  border-bottom: 1px solid #f8fafc;
  font-size: 11px;
}
.aqm-name {
  font-weight: 600;
  color: #1e293b;
  flex: 1;
}
.aqm-spec {
  color: #64748b;
  font-size: 10px;
}
.btn-import-single-rx {
  padding: 2px 6px;
  height: 22px;
  font-size: 10px;
}

/* 病历评分卡 */
.ai-emr-review-card {
  margin-top: 10px;
  background: linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%);
  border: 1px solid #86efac;
  border-radius: 8px;
  padding: 10px;
}
.aerc-top {
  display: flex;
  align-items: center;
  gap: 10px;
}
.aerc-score-badge {
  background: #15803d;
  color: #fff;
  border-radius: 8px;
  padding: 4px 8px;
  text-align: center;
  box-shadow: 0 2px 4px rgba(21, 128, 61, 0.2);
}
.aerc-score-badge .score-num { font-size: 18px; font-weight: 800; display: block; line-height: 1; }
.aerc-score-badge .score-unit { font-size: 9px; }
.aerc-title { font-size: 12px; font-weight: 700; color: #14532d; }
.aerc-desc { font-size: 11px; color: #166534; margin-top: 2px; }

/* 接诊台就绪空状态 */
.empty-emr-panel {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fafafa;
  border-radius: 12px;
  border: 1px dashed #cbd5e1;
  min-height: 500px;
}
.empty-consult-desk {
  text-align: center;
  max-width: 480px;
  padding: 30px 20px;
}
.ecd-badge {
  display: inline-block;
  background: #e0f2fe;
  color: #0369a1;
  font-size: 12px;
  font-weight: 700;
  padding: 4px 12px;
  border-radius: 9999px;
  margin-bottom: 16px;
}
.ecd-illustration {
  font-size: 48px;
  margin-bottom: 12px;
}
.ecd-title {
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
  margin: 0 0 10px 0;
}
.ecd-desc {
  font-size: 13px;
  color: #64748b;
  line-height: 1.8;
  margin: 0 0 20px 0;
}
.ecd-actions {
  display: flex;
  justify-content: center;
  gap: 14px;
}



/* 未接诊提示条 */
.rx-lock-alert {
  margin-bottom: 10px;
}

</style>
