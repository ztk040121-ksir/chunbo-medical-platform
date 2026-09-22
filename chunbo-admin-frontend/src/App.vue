<template>
  <div id="admin-app">
    <!-- ============================================== -->
    <!-- 1. 未登录状态：全屏高质感中台登录卡片 -->
    <!-- ============================================== -->
    <div v-if="!isLoggedIn" class="admin-login-screen">
      <div class="login-bg-glow glow-1"></div>
      <div class="login-bg-glow glow-2"></div>

      <div class="login-modal-box">
        <div class="login-brand">
          <div class="brand-logo-icon">🏛️</div>
          <div>
            <h2 class="brand-main-title">春播云综合运营与人事OA中台</h2>
            <p class="brand-sub-title">医院运营 · 人事薪酬 · OA独立审批 · 商城进销存</p>
          </div>
        </div>

        <div class="login-tab-title">
          <h3>中台统一身份认证</h3>
          <span class="version-tag">RBAC 安全系统 v3.0.0</span>
        </div>


        <el-form :model="loginForm" class="login-form" @submit.prevent="handleAdminLogin">
          <el-form-item>
            <el-input 
              v-model="loginForm.username" 
              placeholder="请输入管理员 / 人事 / 医护工号" 
              size="large"
              prefix-icon="User"
              clearable
              class="custom-login-input"
            />
          </el-form-item>
          <el-form-item>
            <el-input 
              v-model="loginForm.password" 
              type="password" 
              placeholder="请输入登录密码" 
              size="large"
              prefix-icon="Lock"
              show-password
              class="custom-login-input"
              @keyup.enter="handleAdminLogin"
            />
          </el-form-item>

          <div v-if="loginError" class="login-error-tip">
            <span>⚠️ {{ loginError }}</span>
          </div>

          <el-button 
            type="primary" 
            size="large" 
            class="admin-login-btn" 
            :loading="loginLoading"
            @click="handleAdminLogin"
          >
            {{ loginLoading ? '正在核验权限身份...' : '登 录 综 合 中 台' }}
          </el-button>
        </el-form>

        <div class="login-bottom-info">
          <span>春播万象全栈医疗中台 · BCrypt 安全加密通道</span>
          <el-link type="info" underline="never" @click="openSystem('http://localhost:5173/login')">打开医生工作台 →</el-link>
        </div>
      </div>
    </div>

    <!-- ============================================== -->
    <!-- 2. 已登录状态：中台主工作台 -->
    <!-- ============================================== -->
    <template v-else>
      <!-- 顶栏导航 -->
      <header class="admin-header">
        <div class="brand-zone">
          <div class="logo-box">🏛️</div>
          <div>
            <div class="brand-title">春播云管理系统 · 医院综合运营与人事OA中台</div>
            <div class="brand-sub">MySQL 8.0 & Spring AI · 全平台 RBAC 授权架构 (当前权限: {{ currentRoleLabel }})</div>
          </div>
        </div>



        <!-- 登录个人工牌与退出 -->
        <div class="user-header-group">
          <div class="user-badge" @click="showProfileDialog = true">
            <div class="badge-avatar">{{ currentUserRole === 'DOCTOR' ? '👨‍⚕️' : (currentUserRole === 'HR' ? '💼' : '👑') }}</div>
            <div class="badge-info">
              <div class="badge-name">
                {{ currentUserName }}
                <el-tag size="small" :type="currentUserRole === 'ADMIN' ? 'danger' : (currentUserRole === 'HR' ? 'warning' : 'success')" effect="dark">
                  {{ currentRoleLabel }}
                </el-tag>
              </div>
              <div class="badge-role">{{ currentUserDept }} · {{ currentUserTitle }}</div>
            </div>
          </div>

          <button class="header-logout-btn" @click="handleLogoutConfirm" title="安全退出系统">
            <el-icon><SwitchButton /></el-icon>
            <span>退出</span>
          </button>
        </div>
      </header>

      <!-- 主体区域：左侧导航 + 右侧内容 -->
      <div class="admin-main">
        <aside class="admin-sidebar">
          <!-- 1. 经营分析大屏（按角色中台权限配置动态显示） -->
          <div
            v-if="canSeeTab('analytics')"
            class="menu-item"
            :class="{ active: currentTab === 'analytics' }"
            @click="currentTab = 'analytics'"
          >
            <span class="menu-icon">📊</span>
            <span class="menu-label">诊所经营分析大屏</span>
          </div>

          <!-- 2. 商城订单履约与发货 -->
          <div
            v-if="canSeeTab('mall-orders')"
            class="menu-item"
            :class="{ active: currentTab === 'mall-orders' }"
            @click="currentTab = 'mall-orders'"
          >
            <span class="menu-icon">📦</span>
            <span class="menu-label">商城订单履约与发货</span>
          </div>

          <!-- 3. 商城商品管理与进销存 -->
          <div
            v-if="canSeeTab('mall-products')"
            class="menu-item"
            :class="{ active: currentTab === 'mall-products' }"
            @click="currentTab = 'mall-products'"
          >
            <span class="menu-icon">🛍️</span>
            <span class="menu-label">商城商品与进销存</span>
          </div>

          <!-- 4. 商城注册用户管理 -->
          <div
            v-if="canSeeTab('mall-users')"
            class="menu-item"
            :class="{ active: currentTab === 'mall-users' }"
            @click="currentTab = 'mall-users'"
          >
            <span class="menu-icon">👤</span>
            <span class="menu-label">商城注册用户管理</span>
          </div>

          <!-- 5. 工资条管理 (医生/商户看自己，人事/管理员看全院并能发放) -->
          <div
            v-if="canSeeTab('salary')"
            class="menu-item"
            :class="{ active: currentTab === 'salary' }"
            @click="currentTab = 'salary'"
          >
            <span class="menu-icon">💼</span>
            <span class="menu-label">{{ (currentUserRole === 'DOCTOR' || currentUserRole === 'MERCHANT') ? '我的工资条明细' : '工资条发放与核算' }}</span>
          </div>

          <!-- 6. OA 请假独立审批中心 (医生发起，人事/管理员审批) -->
          <div
            v-if="canSeeTab('approval')"
            class="menu-item"
            :class="{ active: currentTab === 'approval' }"
            @click="currentTab = 'approval'"
          >
            <span class="menu-icon">📑</span>
            <span class="menu-label">{{ currentUserRole === 'DOCTOR' ? '我的OA请假申请' : 'OA 请假独立审批中心' }}</span>
          </div>

          <!-- 7. 医护账号与权限管理 (仅人事、管理员) -->
          <div
            v-if="canSeeTab('doctors')"
            class="menu-item"
            :class="{ active: currentTab === 'doctors' }"
            @click="currentTab = 'doctors'"
          >
            <span class="menu-icon">👨‍⚕️</span>
            <span class="menu-label">医护账号与权限管理</span>
          </div>

          <!-- 9. 人事与商户账号管理已并入「医护账号与权限管理」 -->
        </aside>

        <main class="admin-content">
          <!-- 1. 经营分析大屏 -->
          <div v-if="currentTab === 'analytics'" class="tab-pane">
            <div class="pane-header">
              <div>
                <h3>📊 基层诊所进销存与营业运营大屏</h3>
                <span class="sub-desc">支持实时流水明细监控、多统计周期切换（今日/本月/年度）与进销存台账</span>
              </div>
              <div style="display: flex; gap: 12px; align-items: center;">
                <el-radio-group v-model="analyticsPeriod" size="small" @change="loadAnalytics">
                  <el-radio-button value="today">今日实时</el-radio-button>
                  <el-radio-button value="month">本月统计 (近30天)</el-radio-button>
                  <el-radio-button value="year">年度统计 (2026年)</el-radio-button>
                </el-radio-group>
                <el-button type="primary" size="small" @click="loadAnalytics">刷新数据</el-button>
              </div>
            </div>

            <!-- 四大指标卡片（根据今日/本月/年度动态切换） -->
            <div class="metrics-grid">
              <div class="metric-card bg-blue">
                <div class="m-label">{{ analytics.labelRegTitle || '门诊接诊人数' }}</div>
                <div class="m-val">{{ analytics.activeRegistrations !== undefined ? analytics.activeRegistrations : (analytics.todayRegistrations || 0) }} <span class="unit">人次</span></div>
                <div class="m-sub">{{ analytics.labelRegSub || '门诊患者接待与建档' }}</div>
              </div>
              <div class="metric-card bg-green">
                <div class="m-label">{{ analytics.labelFeeTitle || '门诊挂号费流水' }}</div>
                <div class="m-val">¥{{ analytics.activeRegFeeRevenue !== undefined ? analytics.activeRegFeeRevenue : (analytics.todayRegFeeRevenue || 0) }}</div>
                <div class="m-sub">{{ analytics.labelFeeSub || '实收标准 ¥10/人次' }}</div>
              </div>
              <div class="metric-card bg-purple">
                <div class="m-label">{{ analytics.labelMedTitle || '处方药品销售额' }}</div>
                <div class="m-val">¥{{ analytics.activeMedicineRevenue !== undefined ? analytics.activeMedicineRevenue : (analytics.todayMedicineRevenue || 0) }}</div>
                <div class="m-sub">{{ analytics.labelMedSub || '门诊处方药房销售实收' }}</div>
              </div>
              <div class="metric-card bg-orange">
                <div class="m-label">{{ analytics.labelPlasterTitle || '特色中药贴敷理疗创收' }}</div>
                <div class="m-val">¥{{ analytics.activePlasterRevenue !== undefined ? analytics.activePlasterRevenue : (analytics.totalPlasterRevenue || 0) }}</div>
                <div class="m-sub">{{ analytics.labelPlasterSub || '特色中医理疗专案 (综合毛利率 46.8%)' }}</div>
              </div>
            </div>

            <!-- 台账与预警双卡 -->
            <div class="grid-2col mt-16">
              <div class="sub-card">
                <div class="sub-card-header">
                  <span>📦</span>
                  <b>最新药品出入库台账流水 (进销存)</b>
                </div>
                <el-table :data="inventoryRecords" stripe size="small" max-height="320">
                  <el-table-column prop="refOrderNo" label="单据号" width="185">
                    <template #default="scope">
                      <span style="font-family: monospace; font-size: 11px; white-space: nowrap;">{{ scope.row.refOrderNo }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="medicineName" label="药品通用名" min-width="130" show-overflow-tooltip />
                  <el-table-column prop="recordType" label="类型" width="115" align="center">
                    <template #default="scope">
                      <el-tag 
                        :type="scope.row.recordType && scope.row.recordType.includes('入库') ? 'success' : 'danger'" 
                        size="small"
                        style="white-space: nowrap; padding: 0 6px; font-weight: 500;"
                      >
                        {{ scope.row.recordType }}
                      </el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="changeQty" label="变动量" width="80" align="center">
                    <template #default="scope">
                      <span :style="{ color: scope.row.changeQty > 0 ? '#10b981' : '#ef4444', fontWeight: 'bold' }">
                        {{ scope.row.changeQty > 0 ? '+' + scope.row.changeQty : scope.row.changeQty }}
                      </span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="afterStock" label="结余库存" width="85" align="center" />
                  <el-table-column prop="operator" label="经办人" width="140" show-overflow-tooltip />
                </el-table>
              </div>

              <div class="sub-card">
                <div class="sub-card-header">
                  <span>⚠️</span>
                  <b>智慧药房基药低库存预警台账</b>
                  <el-tag type="danger" size="small" class="ml-auto">{{ stockWarnings.length }} 种紧缺</el-tag>
                </div>
                <el-table :data="stockWarnings" stripe size="small" max-height="320">
                  <el-table-column label="药品名称" min-width="130">
                    <template #default="scope">
                      <span class="font-bold">{{ scope.row.medicineName || scope.row.name }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column prop="stock" label="当前库存" width="100">
                    <template #default="scope">
                      <b class="text-danger">{{ scope.row.stock }} {{ scope.row.unit || '盒' }}</b>
                    </template>
                  </el-table-column>
                  <el-table-column label="预警阈值" width="100">
                    <template #default="scope">
                      <span>{{ scope.row.minStock || scope.row.warningStock || 50 }} {{ scope.row.unit || '盒' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="建议直供药企" min-width="140">
                    <template #default="scope">
                      <el-tag size="small" type="info">{{ scope.row.supplier || scope.row.manufacturer || '春播特约药企' }}</el-tag>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </div>

            <!-- 3. 春播全中台 AI 智能调度与运营指挥控制台 (全屏大宽屏) -->
            <div class="analytics-ai-card mt-16">
              <div class="sub-card-header flex-between">
                <div style="display: flex; align-items: center; gap: 8px;">
                  <span style="font-size: 20px;">🤖</span>
                  <div>
                    <span style="font-size: 15px; font-weight: bold; color: #0f172a;">春播云管理系统 · 全中台 AI 智能调度与运营指挥中枢</span>
                    <span style="font-size: 12px; color: #64748b; margin-left: 8px;">基于 MySQL 8.0 真实数据穿透 · 具备 RBAC 权限隔离、进销存出库调度与结构化 Markdown 表格</span>
                  </div>
                </div>
                <div style="display: flex; gap: 8px; align-items: center; flex-wrap: wrap;">
                  <el-tag size="small" type="success" effect="plain">⚡ 全双工流式推理就绪</el-tag>
                  <el-tag size="small" type="primary" effect="dark">当前操作权限: {{ currentRoleLabel }} ({{ currentUserName }})</el-tag>
                  <el-button size="small" type="primary" class="gradient-btn" @click="createNewAdminSession" title="新建中台调度会话">
                    <el-icon><Plus /></el-icon> 新建会话
                  </el-button>
                  <el-button size="small" @click="showAdminHistoryDrawer = true" title="查看会话历史记录">
                    <el-icon><Clock /></el-icon> 会话历史 ({{ adminSessionList.length }})
                  </el-button>
                  <span class="csb-session-tag" :title="activeAdminSessionTitle">
                    <span class="csb-dot"></span> {{ activeAdminSessionTitle }}
                  </span>
                  <el-button type="danger" size="small" plain link @click="clearAllAdminSessions">
                    🧹 清空历史
                  </el-button>
                </div>
              </div>

              <!-- 快捷胶囊技能池 -->
              <div class="quick-pills-bar">
                <div class="skill-category-row">
                  <span class="skill-cat-tag">💰 薪酬绩效:</span>
                  <el-button v-if="currentUserRole === 'ADMIN' || currentUserRole === 'HR'" size="small" round @click="askAssistant('全院薪酬发放汇总表')">全院薪酬汇总表</el-button>
                  <el-button v-if="currentUserRole === 'DOCTOR'" size="small" round type="primary" plain @click="askAssistant('查我的电子工资条')">查我的电子工资条</el-button>
                  <el-button v-if="currentUserRole === 'MERCHANT'" size="small" round type="warning" plain @click="askAssistant('查商户本人提成')">查我的商户提成</el-button>
                  <el-button v-if="currentUserRole === 'ADMIN' || currentUserRole === 'HR'" size="small" round @click="askAssistant('查康主任工资')">查康主任工资</el-button>
                  <el-button v-if="currentUserRole === 'ADMIN' || currentUserRole === 'HR'" size="small" round @click="askAssistant('查王商户提成')">查王商户提成</el-button>
                  <el-button v-if="currentUserRole === 'ADMIN' || currentUserRole === 'HR'" size="small" round @click="askAssistant('查李文华工资')">查李文华工资</el-button>
                </div>
                <div class="skill-category-row">
                  <span class="skill-cat-tag">📦 运营管理:</span>
                  <el-button v-if="currentUserRole !== 'DOCTOR'" size="small" round @click="askAssistant('查春播商城待发货订单')">商城订单履约 (便民速递)</el-button>
                  <el-button size="small" round @click="askAssistant('智慧药房低库存预警')">药房低库存预警台账</el-button>
                  <el-button v-if="currentUserRole !== 'MERCHANT'" size="small" round @click="askAssistant('门诊运营大盘真实诊断')">门诊大盘真实营收诊断</el-button>
                  <el-button size="small" round @click="askAssistant('特色中药贴敷提成与创收')">特色中药贴敷理疗创收</el-button>
                  <el-button size="small" round @click="askAssistant('医院综合请假审批规范')">OA请假审批代班规范</el-button>
                </div>
              </div>

              <!-- 问答展示流式对话框 -->
              <div class="chat-box" ref="chatBoxRef" style="height: 560px;">
                <div 
                  v-for="(msg, idx) in chatMessages" 
                  :key="idx" 
                  class="chat-msg"
                  :class="msg.role"
                >
                  <div class="msg-sender">{{ msg.role === 'user' ? ('👤 提问 (' + currentUserName + ')') : '🤖 春播中台AI调度指挥助手' }}</div>
                  <!-- 用户发送的附件：文件卡片（图标 + 文件名 + 大小），与主流 AI 对话样式一致 -->
                  <div v-if="msg.attachment" class="msg-file-card">
                    <img v-if="msg.attachment.fileType === 'image' && msg.attachment.previewUrl" :src="msg.attachment.previewUrl" class="file-card-icon-img" alt="" />
                    <div v-else class="file-card-icon">{{ (msg.attachment.fileType || 'file') === 'excel' ? 'XLSX' : '文件' }}</div>
                    <div class="file-card-info">
                      <div class="file-card-name">{{ msg.attachment.fileName }}</div>
                      <div class="file-card-size">{{ msg.attachment.sizeLabel || '' }}</div>
                    </div>
                  </div>
                  <!-- 用户发送的图片附件直接在气泡内显示 -->
                  <div v-if="msg.image" class="msg-image"><img :src="msg.image" alt="上传的图片" /></div>
                  <div class="msg-content" v-html="renderMarkdown(msg.content)"></div>
                  <div class="msg-tts-line" v-if="msg.role === 'assistant' && msg.content && !chatLoading">
                    <span class="tts-link" @click="speakAdminMessage(msg)">{{ msg._speaking ? '⏹ 停止朗读' : '🔊 朗读回答' }}</span>
                  </div>
                </div>
              </div>

              <!-- 附件预览：文件卡片样式（图标 + 文件名 + 大小 + 删除），独立一行不挤占输入栏 -->
              <div v-if="chatAttachment" class="chat-attachment-row">
                <div class="file-card">
                  <img v-if="chatAttachment.fileType === 'image' && chatAttachment.previewUrl" :src="chatAttachment.previewUrl" class="file-card-icon-img" alt="" />
                  <div v-else class="file-card-icon">XLSX</div>
                  <div class="file-card-info">
                    <div class="file-card-name">{{ chatAttachment.fileName }}</div>
                    <div class="file-card-size">{{ chatAttachment.sizeLabel || (chatAttachment.fileType === 'excel' ? 'Excel' : '图片') }}</div>
                  </div>
                  <span class="file-card-remove" @click="removeAttachment">✕</span>
                </div>
              </div>

              <!-- 智能指令输入栏 -->
              <div class="chat-input-bar">
                <div class="mic-btn-admin" @click="triggerFileUpload" title="上传工资表（图片或 Excel），AI 提取表格并发工资">
                  <el-icon :size="17"><Upload /></el-icon>
                </div>
                <input ref="chatFileInput" type="file" accept="image/*,.xlsx,.xls" style="display:none" @change="handleFileSelect" />
                <div class="mic-btn-admin" :class="{ recording: isRecordingAdmin }" @click="toggleVoiceInputAdmin"
                     :title="isRecordingAdmin ? '点击结束语音录入' : '语音录入（AI 识别转文字）'">
                  <el-icon v-if="!isRecordingAdmin" :size="17"><Microphone /></el-icon>
                  <span v-else style="font-size: 13px;">⏹</span>
                </div>
                <el-input
                  v-model="inputQuery"
                  placeholder="请输入中台调度指令：例如「生成全院工资表」、「查商城待发货订单」、「药房低库存预警」、「发货订单 B2C2026...」"
                  size="default"
                  clearable
                  @keyup.enter="sendAssistantQuery"
                >
                  <template #prepend>
                    <span>AI 指令</span>
                  </template>
                </el-input>
                <el-button v-if="!chatLoading" type="primary" size="default" @click="sendAssistantQuery">发送指令</el-button>
                <el-button v-else type="danger" size="default" @click="stopAssistantGeneration">⏹ 停止</el-button>
                <el-button type="warning" size="default" plain @click="openTextAssistant" title="AI 帮写/续写/润色/精简">✨ AI文字助手</el-button>
              </div>

              <!-- AI 文字助手弹窗（通用文本模型） -->
              <el-dialog v-model="showTextAssistant" title="✨ AI 文字助手（帮写 / 续写 / 润色 / 精简 / 联想词）" width="640px">
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
                    placeholder="在这里输入需要处理的文本…"
                    resize="none"
                  />
                  <div class="ta-actions">
                    <el-button type="primary" :loading="textAssistantLoading" @click="runTextAssistant">✨ 生成</el-button>
                    <el-button v-if="textAssistantResult" size="default" @click="copyTextResult">📋 复制结果</el-button>
                  </div>
                  <div class="ta-result" v-if="textAssistantResult">
                    <div class="ta-result-title">生成结果：</div>
                    <div class="ta-result-content" v-html="renderMarkdown(textAssistantResult)"></div>
                  </div>
                </div>
              </el-dialog>

              <!-- 全中台 AI 调度会话历史抽屉 -->
              <el-drawer
                v-model="showAdminHistoryDrawer"
                title="📋 全中台 AI 调度会话历史记录"
                size="380px"
                direction="rtl"
                class="admin-history-drawer"
              >
                <div class="ah-drawer-body">
                  <div class="ah-drawer-header">
                    <span class="ah-count">共 {{ adminSessionList.length }} 个调度会话</span>
                    <el-button size="small" type="primary" plain @click="createNewAdminSession">
                      <el-icon><Plus /></el-icon> 新建会话
                    </el-button>
                  </div>
                  <div class="ah-session-list" v-if="adminSessionList.length > 0">
                    <template v-for="g in ADMIN_SESSION_GROUPS" :key="g.key">
                      <div v-if="groupedAdminSessions[g.key].length" class="ah-session-group">
                        <div class="ah-group-label">{{ g.label }} · {{ groupedAdminSessions[g.key].length }}</div>
                        <div
                          v-for="sess in groupedAdminSessions[g.key]"
                          :key="sess.id"
                          class="ah-session-card"
                          :class="{ active: currentAdminSessionId === sess.id }"
                          @click="switchAdminSession(sess.id)"
                        >
                          <div class="ah-card-top">
                            <span class="ah-sess-title">
                              <span class="ah-active-dot" v-if="currentAdminSessionId === sess.id"></span>
                              {{ sess.title }}
                            </span>
                            <el-button
                              size="small"
                              type="danger"
                              link
                              @click.stop="deleteAdminSession(sess.id)"
                              title="删除会话"
                            >
                              <el-icon><Delete /></el-icon>
                            </el-button>
                          </div>
                          <div class="ah-card-bottom">
                            <span class="ah-sess-time">🕒 {{ sess.updatedAt || sess.createdAt }}</span>
                            <el-tag size="small" :type="currentAdminSessionId === sess.id ? 'success' : 'info'">
                              {{ currentAdminSessionId === sess.id ? '当前调度中' : ((sess.messages ? sess.messages.length : 0) + '条') }}
                            </el-tag>
                          </div>
                        </div>
                      </div>
                    </template>
                  </div>
                  <div class="ah-empty" v-else>
                    暂无历史会话记录
                  </div>
                </div>
              </el-drawer>
            </div>
          </div>

                    <!-- 2. 商城订单履约与发货工作台 -->
          <div v-else-if="currentTab === 'mall-orders'" class="tab-pane">
            <div class="module-hero-banner">
              <div>
                <h2 class="hero-title">📦 春播商城订单履约与进销存出库中心</h2>
                <p class="hero-sub">查看用户在线购药订单，商户或管理员手动核准并点击【📦 一键发货出库】，系统将自动扣减药品库存、录入春播健康便民速递单号并生成出库审计台账（非自动发货，保障药品出库合规）。列表自动加载，刷新浏览器即可同步最新订单。</p>
              </div>
            </div>

            <!-- 指标卡片 -->
            <div class="metrics-grid">
              <div class="metric-card bg-blue">
                <div class="m-label">商城总订单量</div>
                <div class="m-val">{{ mallOrdersList.length }} <span class="unit">单</span></div>
                <div class="m-sub">生活便民购药总订单</div>
              </div>
              <div class="metric-card bg-orange">
                <div class="m-label">待发货配货中</div>
                <div class="m-val">{{ pendingShipCount }} <span class="unit">单</span></div>
                <div class="m-sub">待商户发货出库</div>
              </div>
              <div class="metric-card bg-green">
                <div class="m-label">已发货运输中</div>
                <div class="m-val">{{ shippedCount }} <span class="unit">单</span></div>
                <div class="m-sub">春播便民速递直达</div>
              </div>
              <div class="metric-card bg-purple">
                <div class="m-label">线上实收流水总额</div>
                <div class="m-val">¥{{ totalMallOrderRevenue.toFixed(2) }}</div>
                <div class="m-sub">商户分润核算基数</div>
              </div>
            </div>

            <!-- 订单状态过滤栏与手动发货说明 -->
            <div style="margin: 14px 0 10px; display: flex; justify-content: space-between; align-items: center; background: #f8fafc; padding: 10px 14px; border-radius: 8px; border: 1px solid #e2e8f0;">
              <el-radio-group v-model="orderFilterStatus" size="small">
                <el-radio-button value="ALL">全部订单 ({{ mallOrdersList.length }})</el-radio-button>
                <el-radio-button value="PENDING">待发货出库 ({{ pendingShipCount }})</el-radio-button>
                <el-radio-button value="SHIPPED">已发货运输中 ({{ shippedCount }})</el-radio-button>
              </el-radio-group>
              <span style="font-size: 13px; color: #475569;">
                💡 发货说明：待发货订单请在右侧操作栏点击 <b style="color: #16a34a;">【📦 一键发货出库】</b> 手动出库（非自动发货）
              </span>
            </div>

            <!-- 搜索 / 删除 / 分页工具栏 -->
            <div class="batch-salary-toolbar">
              <el-input v-model="ordersPager.state.search" placeholder="搜索订单号 / 收货人 / 状态…" size="small" clearable style="width: 240px;" />
              <el-button size="small" type="danger" plain :disabled="!orderSelection.length" @click="batchDeleteRows('/api/admin/mall/order/batch-delete', orderSelection.map(r => r.id), '商城订单', loadMallOrders)">🗑 删除选中</el-button>
              <el-pagination style="margin-left: auto;" v-model:current-page="ordersPager.state.page" v-model:page-size="ordersPager.state.size" :page-sizes="[10, 20, 50]" :total="ordersPager.total" layout="total, sizes, prev, pager, next" size="small" />
            </div>

            <!-- 订单表格 -->
            <div class="admin-table-card">
              <el-table :data="ordersPager.paged" stripe size="small" max-height="560" @selection-change="orderSelection = $event">
                <el-table-column type="selection" width="42" />
                <el-table-column prop="orderNo" label="订单号" width="180">
                  <template #default="scope">
                    <span style="font-family: monospace; font-weight: bold; color: #0284c7;">{{ scope.row.orderNo }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="buyerName" label="购药客户" width="140" />
                <el-table-column prop="clinicName" label="配送收货地址" min-width="180" show-overflow-tooltip />
                <el-table-column label="购药清单明细" min-width="200">
                  <template #default="scope">
                    <div v-for="(item, idx) in parseOrderItems(scope.row.itemsJson)" :key="idx" class="order-item-chip">
                      <span>💊 {{ item.productName }}</span>
                      <span class="item-qty">x{{ item.quantity || 1 }}盒</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="finalAmount" label="实付金额" width="100" align="center">
                  <template #default="scope">
                    <b class="text-danger">¥{{ scope.row.finalAmount }}</b>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="履约状态" width="180">
                  <template #default="scope">
                    <el-tag :type="scope.row.status && scope.row.status.includes('已发货') ? 'success' : 'warning'" size="small">
                      {{ scope.row.status || '待发货' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="createTime" label="下单时间" width="150">
                  <template #default="scope">
                    <span>{{ formatTime(scope.row.createTime) }}</span>
                  </template>
                </el-table-column>
                <el-table-column label="发货履约操作" width="240" fixed="right" align="center">
                  <template #default="scope">
                    <!-- 1. 待发货出库 -->
                    <el-button
                      v-if="!scope.row.status || (!scope.row.status.includes('已发货') && !scope.row.status.includes('已送达'))"
                      type="success"
                      size="small"
                      style="font-weight: bold; box-shadow: 0 2px 4px rgba(22, 163, 74, 0.2);"
                      @click="openShipConfirmModal(scope.row)"
                    >
                      📦 一键发货出库
                    </el-button>
                    <!-- 2. 已送达 / 居民已签收 (终态) -->
                    <div v-else-if="scope.row.status && scope.row.status.includes('已送达')" style="display: flex; align-items: center; justify-content: center; gap: 6px;">
                      <el-tag type="success" size="small" effect="dark" style="font-weight: bold; background: #059669; border-color: #059669;">
                        ✅ 已送达 / 居民已签收
                      </el-tag>
                    </div>
                    <!-- 3. 已发货运输中 -> 一行横排：状态 + 确认送达 -->
                    <div v-else style="display: flex; align-items: center; justify-content: center; gap: 6px;">
                      <el-tag type="success" size="small" effect="plain" style="font-weight: bold;">
                        🚚 运输中
                      </el-tag>
                      <el-button
                        type="primary"
                        size="small"
                        plain
                        style="font-size: 11px; padding: 2px 8px; height: 24px; font-weight: bold;"
                        @click="confirmDeliverOrder(scope.row)"
                      >
                        ✅ 确认送达
                      </el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
<!-- 2. 工资条管理 -->
          <div v-else-if="currentTab === 'salary'" class="tab-pane">
            <!-- 医生/商户视角：只读个人台账 -->
            <template v-if="currentUserRole === 'DOCTOR' || currentUserRole === 'MERCHANT'">
              <div class="pane-header">
                <div>
                  <h3>💼 我的月度工资条与绩效明细</h3>
                  <span class="sub-desc">当前仅展示 [{{ currentUserName }}] 的核算记录</span>
                </div>
              </div>
              <div class="salary-full-card">
                <div class="sub-card-header flex-between">
                  <div><span>📋</span><b>个人历史工资核算台账</b></div>
                </div>
                <el-table :data="displaySalarySlips" stripe size="small" style="width: 100%;">
                  <el-table-column prop="doctorId" label="员工工号" width="120" />
                  <el-table-column prop="doctorName" label="员工姓名" width="110" />
                  <el-table-column prop="salaryMonth" label="归属月份" width="100" />
                  <el-table-column prop="baseSalary" label="基本底薪" width="110">
                    <template #default="scope"><span>¥{{ scope.row.baseSalary }}</span></template>
                  </el-table-column>
                  <el-table-column prop="clinicCommission" label="门诊/电商提成" width="120">
                    <template #default="scope"><span class="text-success">+¥{{ scope.row.clinicCommission }}</span></template>
                  </el-table-column>
                  <el-table-column prop="plasterCommission" label="贴敷理疗/合规奖" width="130">
                    <template #default="scope"><span class="text-success font-bold">+¥{{ scope.row.plasterCommission }}</span></template>
                  </el-table-column>
                  <el-table-column prop="netSalary" label="实发工资" min-width="130">
                    <template #default="scope"><b class="text-danger" style="font-size: 14px;">¥{{ scope.row.netSalary }}</b></template>
                  </el-table-column>
                  <el-table-column prop="status" label="发放状态" width="100" align="center">
                    <template #default="scope">
                      <el-tag type="success" size="small" effect="dark">{{ scope.row.status || '已发放' }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column prop="createTime" label="发放时间" width="160">
                    <template #default="scope">
                      <span style="font-size: 12px; color: #64748b;">{{ (scope.row.createTime || '').replace('T', ' ').slice(0, 19) }}</span>
                    </template>
                  </el-table-column>
                </el-table>
              </div>
            </template>

            <!-- ADMIN/HR 视角：主区域=全员薪酬核算与发放，下方=发放记录台账 -->
            <template v-else>
              <div class="pane-header">
                <div>
                  <h3>💼 全员薪酬核算与工资发放中枢</h3>
                  <span class="sub-desc">自动加载全院工作人员 · 上传工资表按工号/姓名自动填充 · 支持勾选发放 / 单独发放 / 批量发放 · 同员工同月重发自动覆盖</span>
                </div>
                <div>
                  <el-button size="small" plain @click="showSlipHistoryDialog = true">📋 发放记录</el-button>
                </div>
              </div>

              <div class="salary-full-card">
                <div class="batch-salary-toolbar">
                  <span style="font-size: 13px;">发薪日期：</span>
                  <el-date-picker v-model="salaryBoardMonth" type="date" value-format="YYYY-MM-DD" :clearable="false" style="width: 140px" size="small" @change="loadBatchSalaryBoard" />
                  <el-button type="warning" plain size="small" @click="triggerBatchExcelUpload">⬆ 上传工资表填充</el-button>
                  <input ref="batchSalaryExcelInput" type="file" accept=".xlsx,.xls" style="display:none" @change="handleBatchExcelFill" />
                  <el-button size="small" plain @click="aiCalcAllRows" :loading="batchAiAllLoading">🤖 全员AI测算</el-button>
                  <el-input v-model="salaryBoardPager.state.search" placeholder="搜索工号 / 姓名 / 角色…" size="small" clearable style="width: 190px; margin-left: auto;" />
                  <span style="font-size: 12px; color: #64748b;">
                    已勾选 <b>{{ batchSelection.length }}</b> 人 · 本表应发合计 <b class="text-danger">¥{{ batchTotalAmount.toFixed(2) }}</b>
                  </span>
                  <el-button type="danger" plain size="small" :disabled="!batchSelection.length" @click="deleteSalaryBoardRows(batchSelection)">🗑 删除选中</el-button>
                  <el-button type="success" size="small" :disabled="!batchSelection.length" :loading="distributeLoading" @click="submitBatchSalary(batchSelection)">
                    💰 发放勾选员工
                  </el-button>
                </div>
                <el-table ref="batchSalaryTableRef" :data="salaryBoardPager.paged" @selection-change="onBatchSelectionChange" :row-class-name="({ row }) => (row.hasAccount === false ? 'no-account-row' : '')" size="small" max-height="420" border style="width: 100%;">
                  <el-table-column type="selection" width="42" :selectable="row => row.hasAccount !== false" />
                  <el-table-column label="工号" width="115">
                    <template #default="{ row }">
                      <span :style="row.hasAccount === false ? 'color:#dc2626;' : ''">{{ row.staffId || '—' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="姓名" width="100">
                    <template #default="{ row }">
                      <span :style="row.hasAccount === false ? 'color:#dc2626;' : ''">{{ row.name || '—' }}</span>
                    </template>
                  </el-table-column>
                  <el-table-column label="角色" width="92">
                    <template #default="{ row }">
                      <el-tag size="small" effect="plain">{{ row.roleLabel || '员工' }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="基本底薪" width="140">
                    <template #default="{ row }">
                      <el-input-number v-model="row.baseSalary" :min="0" :step="100" size="small" style="width: 100%;" controls-position="right" />
                    </template>
                  </el-table-column>
                  <el-table-column label="门诊/电商提成" width="140">
                    <template #default="{ row }">
                      <el-input-number v-model="row.clinicCommission" :min="0" :step="100" size="small" style="width: 100%;" controls-position="right" />
                    </template>
                  </el-table-column>
                  <el-table-column label="贴敷理疗/合规奖" width="140">
                    <template #default="{ row }">
                      <el-input-number v-model="row.plasterCommission" :min="0" :step="100" size="small" style="width: 100%;" controls-position="right" />
                    </template>
                  </el-table-column>
                  <el-table-column label="社保代扣" width="120">
                    <template #default="{ row }">
                      <el-input-number v-model="row.deductionSocial" :min="0" :step="50" size="small" style="width: 100%;" controls-position="right" />
                    </template>
                  </el-table-column>
                  <el-table-column label="个税" width="110">
                    <template #default="{ row }">
                      <el-input-number v-model="row.tax" :min="0" :step="10" size="small" style="width: 100%;" controls-position="right" />
                    </template>
                  </el-table-column>
                  <el-table-column label="实发工资" min-width="110">
                    <template #default="{ row }">
                      <b class="text-danger" style="font-size: 13px;">¥{{ batchRowNet(row).toFixed(2) }}</b>
                    </template>
                  </el-table-column>
                  <el-table-column label="来源" width="210">
                    <template #default="{ row }">
                      <el-tag size="small" :type="row.sourceType || 'info'" effect="light" style="white-space: normal; line-height: 1.3;">{{ row.source || '待发放' }}</el-tag>
                    </template>
                  </el-table-column>
                  <el-table-column label="操作" width="252" fixed="right">
                    <template #default="{ row, $index }">
                      <template v-if="row.hasAccount !== false">
                        <el-button size="small" text type="primary" :loading="row._aiLoading" @click="aiCalcBatchRow(row)">测算</el-button>
                        <el-button size="small" text type="success" :loading="row._paying" @click="paySingleRow(row)">发放</el-button>
                      </template>
                      <span v-else style="font-size: 11px; color: #dc2626;">⚠️ 无账号不可发</span>
                      <el-button size="small" text type="danger" @click="deleteSalaryBoardRows([row])">删除</el-button>
                    </template>
                  </el-table-column>
                </el-table>
                <div style="display: flex; justify-content: flex-end; padding: 8px 4px 0 0;">
                  <el-pagination v-model:current-page="salaryBoardPager.state.page" v-model:page-size="salaryBoardPager.state.size" :page-sizes="[10, 20, 50]" :total="salaryBoardPager.total" layout="total, sizes, prev, pager, next" size="small" />
                </div>
              </div>
            </template>
          </div>

          <!-- 3. OA 独立审批中心 -->
          <div v-else-if="currentTab === 'approval'" class="tab-pane">
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

          <!-- 4. 医生账号注册与管理 (人事、管理员) -->
          <div v-else-if="currentTab === 'doctors'" class="tab-pane">
            <div class="pane-header">
              <div>
                <h3>👨‍⚕️ 医护账号与权限管理中心</h3>
                <span class="sub-desc">统一注册医生/护士/人事/商户账号，按角色自动生效对应系统权限</span>
              </div>
            </div>

            <!-- 统计指标 -->
            <div class="metrics-grid mb-16">
              <div class="metric-card bg-blue">
                <div class="m-label">全院在册账号总数</div>
                <div class="m-val">{{ unifiedStaffTotal }} <span class="unit">个</span></div>
                <div class="m-sub">医生 + 员工 + 商户等系统账号统一统计</div>
              </div>
              <div class="metric-card bg-green">
                <div class="m-label">正常启用账号</div>
                <div class="m-val">{{ activeStaffCount }} <span class="unit">个</span></div>
                <div class="m-sub">当前可正常登录系统的账号</div>
              </div>
              <div class="metric-card bg-purple">
                <div class="m-label">覆盖部门/科室</div>
                <div class="m-val">{{ departmentCount }} <span class="unit">个</span></div>
                <div class="m-sub">门诊、药房、人事、商城运营等</div>
              </div>
            </div>

            <!-- 全院医护与员工统一账号总台账（医生表 + 员工表去重合并，一张表管理） -->
            <el-card>
              <template #header>
                <div class="flex-between">
                  <b>👥 全院医护与员工账号总台账（真实查库 · 角色 / 状态 / 密码统一管理）</b>
                  <div style="display: flex; gap: 8px; align-items: center;">
                    <el-tag type="info" size="small">默认测试工号: kzt / 密码: 123456</el-tag>
                    <el-button type="success" size="small" @click="openRegisterUnified">
                      ➕ 注册账号
                    </el-button>
                  </div>
                </div>
              </template>
              <div class="batch-salary-toolbar">
                <el-input v-model="unifiedPager.state.search" placeholder="搜索账号 / 工号 / 姓名 / 科室 / 角色…" size="small" clearable style="width: 260px;" />
                <el-button size="small" type="danger" plain :disabled="!unifiedSelection.length" @click="deleteUnifiedAccounts(unifiedSelection)">🗑 删除选中</el-button>
                <el-pagination style="margin-left: auto;" v-model:current-page="unifiedPager.state.page" v-model:page-size="unifiedPager.state.size" :page-sizes="[10, 20, 50]" :total="unifiedPager.total" layout="total, sizes, prev, pager, next" size="small" />
              </div>
              <el-table :data="unifiedPager.paged" stripe size="small" @selection-change="unifiedSelection = $event">
                <el-table-column type="selection" width="42" />
                <el-table-column prop="username" label="登录账号" width="120">
                  <template #default="scope">
                    <el-tag effect="plain" type="primary" class="font-mono"><b>{{ scope.row.username }}</b></el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="name" label="姓名" width="110">
                  <template #default="scope"><b>{{ scope.row.name }}</b></template>
                </el-table-column>
                <el-table-column prop="staffId" label="数字工号" width="110">
                  <template #default="scope">
                    <el-tag type="success" size="small">{{ scope.row.staffId }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="department" label="所属部门/科室" width="150" />
                <el-table-column prop="title" label="岗位职称" width="140" />
                <el-table-column label="系统角色（可编辑）" width="170">
                  <template #default="scope">
                    <el-select :model-value="scope.row.role" size="small" style="width: 135px;" @change="(v) => handleStaffRoleRowChange(scope.row, v)">
                      <el-option v-for="r in roleOptions" :key="r.value" :label="r.label" :value="r.value" />
                    </el-select>
                  </template>
                </el-table-column>
                <el-table-column prop="permissions" label="功能权限范围（按角色动态配置）" min-width="220" />
                <el-table-column prop="status" label="账号状态" width="90">
                  <template #default="scope">
                    <el-tag :type="scope.row.status === 'ENABLE' ? 'success' : 'danger'" size="small">
                      {{ scope.row.status === 'ENABLE' ? '正常' : '停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="200" fixed="right">
                  <template #default="scope">
                    <el-button
                      size="small"
                      :type="scope.row.status === 'ENABLE' ? 'warning' : 'success'"
                      link
                      @click="toggleUnifiedStatus(scope.row)"
                    >
                      {{ scope.row.status === 'ENABLE' ? '停用' : '启用' }}
                    </el-button>
                    <el-button size="small" type="primary" link @click="resetUnifiedPassword(scope.row)">重置密码</el-button>
                    <el-button size="small" type="danger" link @click="deleteUnifiedAccounts([scope.row])">删除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>

            <!-- 角色权限范围动态配置：分「云诊所」「管理中台」两套系统（写入 sys_role_permission，前端菜单按此动态渲染） -->
            <el-card style="margin-top: 16px;">
              <template #header>
                <div class="flex-between">
                  <b>🔐 角色权限范围配置（云诊所与管理中台两套系统独立配置，保存后重新登录生效）</b>
                  <el-tag type="info" size="small">切换系统视角查看和编辑对应权限</el-tag>
                </div>
              </template>
            <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 12px;">
              <span style="font-size: 13px; font-weight: 500;">切换系统视角：</span>
              <el-radio-group v-model="permScopeView">
                <el-radio-button value="CLINIC">🏥 云诊所权限 (5173)</el-radio-button>
                <el-radio-button value="ADMIN">🏛️ 管理系统权限 (5174)</el-radio-button>
              </el-radio-group>
            </div>
            <el-alert v-if="permScopeView === 'CLINIC'" type="info" :closable="false" style="margin-bottom: 10px;"
              title="控制各角色登录春播万象云诊所后可见的模块。系统最高管理员天然拥有全部权限无需配置；人事/商户默认无云诊所权限（仅使用管理中台），如需开通可点击该行的「编辑」。" />
            <el-alert v-else type="info" :closable="false" style="margin-bottom: 10px;"
              title="控制各角色登录春播云管理系统中台后左侧菜单可见的功能：医生/护士 = 经营分析大屏 + 我的工资条 + 请假申请；商户额外可见商城三模块；人事/管理员 = 全部。" />
            <el-table :data="permRowsForView" stripe size="small">
              <el-table-column prop="roleLabel" label="角色" width="170" />
              <el-table-column :label="permScopeView === 'CLINIC' ? '云诊所模块权限' : '管理系统模块权限'" min-width="380">
                <template #default="scope">
                  <div v-if="scope.row.role === 'ADMIN'" class="perm-full-text">✅ 拥有{{ permScopeView === 'CLINIC' ? '云诊所' : '管理系统' }}全部模块权限（系统最高管理员，无需勾选配置）</div>
                  <div v-else-if="permScopeView === 'CLINIC' && !scope.row.modules.length && !scope.row._editing" class="perm-none-text">
                    ⛔ 无云诊所权限（该角色仅使用管理系统）
                    <el-button size="small" text type="primary" @click="scope.row._editing = true">如需开通请点此编辑</el-button>
                  </div>
                  <el-checkbox-group v-else v-model="scope.row.modules">
                    <el-checkbox v-for="m in (permScopeView === 'CLINIC' ? roleModuleOptions : adminModuleOptions)" :key="m.key" :value="m.key" style="margin-right: 10px;">{{ m.label }}</el-checkbox>
                  </el-checkbox-group>
                </template>
              </el-table-column>
              <el-table-column label="权限范围描述" min-width="200">
                <template #default="scope">
                  <el-input v-if="permRowsEditable(scope.row)" v-model="scope.row.description" size="small" :placeholder="permScopeView === 'CLINIC' ? '该角色在云诊所的权限范围描述' : '该角色在管理系统的权限范围描述'" />
                  <span v-else style="color: #94a3b8; font-size: 12px;">{{ scope.row.description || '—' }}</span>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="130" fixed="right">
                <template #default="scope">
                  <template v-if="permRowsEditable(scope.row)">
                    <el-button v-if="scope.row._editing && !scope.row.modules.length" size="small" text @click="scope.row._editing = false">取消</el-button>
                    <el-button type="primary" size="small" :loading="scope.row._saving" @click="saveRolePerm(scope.row)">保存</el-button>
                  </template>
                  <span v-else style="color: #94a3b8; font-size: 12px;">无需配置</span>
                </template>
              </el-table-column>
            </el-table>
            </el-card>
          </div>

          <!-- 6. 商城商品管理与进销存 (管理员最高权限专属) -->
          <div v-else-if="currentTab === 'mall-products'" class="tab-pane">
            <div class="pane-header">
              <div>
                <h3>🛍️ 春播健康商城商品档案、价格调优与进销存管理中心</h3>
                <span class="sub-desc">在此对便民商城商品进行上下架控制、价格修改及补货入库流水记录</span>
              </div>
              <div style="display: flex; gap: 8px;">
                <el-button type="success" size="small" @click="showAddProductDialog = true">
                  + 新增商品档案
                </el-button>
                <el-button type="warning" size="small" plain @click="triggerProductImport">
                  ⬆ 表格新增商品
                </el-button>
                <input ref="productImportInput" type="file" accept=".xlsx,.xls" style="display:none" @change="handleProductImport" />
              </div>
            </div>

            <!-- 指标卡 -->
            <div class="metrics-grid mb-16">
              <div class="metric-card bg-blue">
                <div class="m-label">在售药品与生活健康品</div>
                <div class="m-val">{{ mallProducts.filter(p => p.status !== 'OFF_SALE').length }} <span class="unit">款</span></div>
                <div class="m-sub">面向大众消费者提供在线采购</div>
              </div>
              <div class="metric-card bg-orange">
                <div class="m-label">已下架暂停销售品</div>
                <div class="m-val">{{ mallProducts.filter(p => p.status === 'OFF_SALE').length }} <span class="unit">款</span></div>
                <div class="m-sub">商城前台自动隐去不展示</div>
              </div>
              <div class="metric-card bg-purple">
                <div class="m-label">库存量充足品</div>
                <div class="m-val">{{ mallProducts.filter(p => (p.stock || p.stockQty || 0) > 50).length }} <span class="unit">款</span></div>
                <div class="m-sub">支持便民同城极速达</div>
              </div>
              <div class="metric-card bg-green">
                <div class="m-label">全栈进销存关联</div>
                <div class="m-val">{{ inventoryRecords.length }} <span class="unit">条</span></div>
                <div class="m-sub">出入库台账流水（补货/发货自动生成）</div>
              </div>
            </div>

            <!-- 搜索 / 删除 / 分页工具栏 -->
            <div class="batch-salary-toolbar">
              <el-input v-model="productsPager.state.search" placeholder="搜索商品名 / 类别 / 厂家 / 状态…" size="small" clearable style="width: 260px;" />
              <el-button size="small" type="danger" plain :disabled="!productSelection.length" @click="batchDeleteRows('/api/admin/mall/product/batch-delete', productSelection.map(r => r.id), '商品档案', loadMallAdminProducts)">🗑 删除选中</el-button>
              <el-pagination style="margin-left: auto;" v-model:current-page="productsPager.state.page" v-model:page-size="productsPager.state.size" :page-sizes="[10, 20, 50]" :total="productsPager.total" layout="total, sizes, prev, pager, next" size="small" />
            </div>

            <!-- 商品表格 -->
            <el-card>
              <el-table :data="productsPager.paged" stripe size="small" v-loading="mallLoading" @selection-change="productSelection = $event">
                <el-table-column type="selection" width="42" />
                <el-table-column prop="id" label="ID" width="55" />
                <el-table-column label="商品图" width="92">
                  <template #default="scope">
                    <el-upload
                      :show-file-list="false"
                      accept="image/*"
                      :http-request="(o) => handleRowImageUpload(scope.row, o)"
                    >
                      <el-image
                        v-if="scope.row.imageUrl"
                        :src="scope.row.imageUrl"
                        :preview-src-list="[scope.row.imageUrl]"
                        preview-teleported
                        fit="cover"
                        style="width: 52px; height: 52px; border-radius: 6px; border: 1px solid #e2e8f0;"
                      />
                      <div v-else class="row-img-upload-placeholder">传图</div>
                    </el-upload>
                  </template>
                </el-table-column>
                <el-table-column prop="productName" label="商品名称" min-width="140">
                  <template #default="scope">
                    <b>{{ scope.row.productName }}</b>
                  </template>
                </el-table-column>
                <el-table-column prop="category" label="分类" width="100">
                  <template #default="scope">
                    <el-tag size="small">{{ scope.row.category }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="specification" label="规格" width="120" />
                <el-table-column prop="retailGuidePrice" label="零售指导价" width="110">
                  <template #default="scope">
                    <b class="text-danger">¥{{ Number(scope.row.retailGuidePrice || 0).toFixed(2) }}</b>
                  </template>
                </el-table-column>
                <el-table-column prop="wholesalePrice" label="进货成本价" width="100">
                  <template #default="scope">
                    <span>¥{{ Number(scope.row.wholesalePrice || 0).toFixed(2) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="stock" label="当前库存" width="90">
                  <template #default="scope">
                    <b :class="(scope.row.stock || scope.row.stockQty || 0) < 50 ? 'text-danger' : 'text-success'">
                      {{ scope.row.stock || scope.row.stockQty || 0 }}
                    </b>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="在售状态" width="90">
                  <template #default="scope">
                    <el-tag :type="scope.row.status === 'OFF_SALE' ? 'danger' : 'success'" size="small">
                      {{ scope.row.status === 'OFF_SALE' ? '已下架' : '在售中' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="中台进销存与销售操作" width="220" fixed="right">
                  <template #default="scope">
                    <el-button 
                      size="small" 
                      :type="scope.row.status === 'OFF_SALE' ? 'success' : 'warning'" 
                      link
                      @click="handleToggleProductStatus(scope.row)"
                    >
                      {{ scope.row.status === 'OFF_SALE' ? '上架' : '下架' }}
                    </el-button>
                    <el-button size="small" type="primary" link @click="openPriceDialog(scope.row)">
                      调价
                    </el-button>
                    <el-button size="small" type="success" link @click="openInboundDialog(scope.row)">
                      入库补货
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>

            <!-- 价格调整弹窗 -->
            <el-dialog v-model="showPriceDialog" title="💰 调整商品商城售价" width="400px">
              <el-form label-width="100px">
                <el-form-item label="商品名称">
                  <b>{{ editingProduct.productName }}</b>
                </el-form-item>
                <el-form-item label="零售指导价" required>
                  <el-input-number v-model="priceForm.retailGuidePrice" :precision="2" :step="1" :min="1" />
                </el-form-item>
                <el-form-item label="批发进价">
                  <el-input-number v-model="priceForm.wholesalePrice" :precision="2" :step="1" :min="0.5" />
                </el-form-item>
              </el-form>
              <template #footer>
                <el-button @click="showPriceDialog = false">取消</el-button>
                <el-button type="primary" @click="submitPriceUpdate">确认修改售价</el-button>
              </template>
            </el-dialog>

            <!-- 补货入库弹窗 -->
            <el-dialog v-model="showInboundDialog" title="📦 进销存商品补货入库" width="420px">
              <el-alert title="补货入库将自动累加商品库存，并生成一笔出入库进销存台账记录。" type="info" :closable="false" class="mb-16" />
              <el-form label-width="100px">
                <el-form-item label="补货商品">
                  <b>{{ inboundProduct.productName }}</b>
                </el-form-item>
                <el-form-item label="当前在库">
                  <span>{{ inboundProduct.stock || inboundProduct.stockQty || 0 }} 盒/瓶</span>
                </el-form-item>
                <el-form-item label="入库补货量" required>
                  <el-input-number v-model="inboundQty" :min="10" :step="50" :max="5000" />
                </el-form-item>
              </el-form>
              <template #footer>
                <el-button @click="showInboundDialog = false">取消</el-button>
                <el-button type="success" @click="submitInbound">确认入库并更新台账</el-button>
              </template>
            </el-dialog>

            <!-- 新增商品档案弹窗 -->
            <el-dialog v-model="showAddProductDialog" title="➕ 新增商城商品档案" width="500px">
              <el-form :model="newProductForm" label-width="100px">
                <el-form-item label="商品名称" required>
                  <el-input v-model="newProductForm.productName" placeholder="如：连花清瘟胶囊" />
                </el-form-item>
                <el-form-item label="商品分类">
                  <el-select v-model="newProductForm.category" style="width: 100%">
                    <el-option label="感冒发热" value="感冒发热" />
                    <el-option label="咳嗽咽痛" value="咳嗽咽痛" />
                    <el-option label="肠胃消化" value="肠胃消化" />
                    <el-option label="皮肤外用" value="皮肤外用" />
                    <el-option label="家庭常备" value="家庭常备" />
                    <el-option label="慢病用药" value="慢病用药" />
                  </el-select>
                </el-form-item>
                <el-form-item label="规格">
                  <el-input v-model="newProductForm.specification" placeholder="如：0.35g*24粒/盒" />
                </el-form-item>
                <el-form-item label="生产厂家">
                  <el-input v-model="newProductForm.manufacturer" placeholder="如：北京同仁堂科技发展股份有限公司" />
                </el-form-item>
                <el-form-item label="零售单价" required>
                  <el-input-number v-model="newProductForm.retailGuidePrice" :precision="2" :step="1" :min="1" />
                </el-form-item>
                <el-form-item label="进货成本价">
                  <el-input-number v-model="newProductForm.wholesalePrice" :precision="2" :step="1" :min="0.5" />
                </el-form-item>
                <el-form-item label="初始库存">
                  <el-input-number v-model="newProductForm.stock" :min="10" :step="50" />
                </el-form-item>
                <el-form-item label="商品图片">
                  <div class="upload-image-zone">
                    <el-upload
                      class="product-img-uploader"
                      :show-file-list="false"
                      accept="image/*"
                      :http-request="handleProductImageUpload"
                    >
                      <img v-if="newProductForm.imageUrl" :src="newProductForm.imageUrl" class="upload-preview-img" />
                      <el-icon v-else class="upload-placeholder-icon"><Plus /></el-icon>
                    </el-upload>
                    <div class="upload-tips">
                      <div class="tip-main">点击上传商品实拍图</div>
                      <div class="tip-sub">jpg/png/webp，不超过 5MB；上传后立即在便民商城展示</div>
                      <el-button v-if="newProductForm.imageUrl" link type="danger" size="small" @click="newProductForm.imageUrl = ''">移除图片</el-button>
                    </div>
                  </div>
                </el-form-item>
              </el-form>
              <template #footer>
                <el-button @click="showAddProductDialog = false">取消</el-button>
                <el-button type="primary" @click="submitAddProduct">保存商品档案</el-button>
              </template>
            </el-dialog>
          </div>

          <!-- 7. 商城注册用户管理 (管理员专属) -->
          <div v-else-if="currentTab === 'mall-users'" class="tab-pane">
            <div class="pane-header">
              <div>
                <h3>👤 春播健康商城注册用户档案与账户管理</h3>
                <span class="sub-desc">实时查看居民注册账号、消费余额、积分及账号状态</span>
              </div>
              <el-button type="primary" size="small" @click="openAddMallUserDialog">➕ 新增商城用户</el-button>
            </div>

            <!-- 指标卡 -->
            <div class="metrics-grid mb-16">
              <div class="metric-card bg-blue">
                <div class="m-label">商城注册用户总数</div>
                <div class="m-val">{{ mallUsers.length }} <span class="unit">位</span></div>
                <div class="m-sub">自主注册便民就医购药居民</div>
              </div>
              <div class="metric-card bg-green">
                <div class="m-label">正常活跃账户</div>
                <div class="m-val">{{ mallUsers.filter(u => u.status === 'ENABLE').length }} <span class="unit">位</span></div>
                <div class="m-sub">支持在线选药与自动配送</div>
              </div>
              <div class="metric-card bg-orange">
                <div class="m-label">已冻结账户</div>
                <div class="m-val">{{ mallUsers.filter(u => u.status === 'DISABLE').length }} <span class="unit">位</span></div>
                <div class="m-sub">违规或风险账号管控</div>
              </div>
              <div class="metric-card bg-purple">
                <div class="m-label">已发放新人购药金</div>
                <div class="m-val">¥{{ mallUsers.reduce((s, u) => s + (Number(u.balance) || 0), 0).toFixed(2) }}</div>
                <div class="m-sub">每位新注册居民预赠 200 元体验金（注册自动入账）</div>
              </div>
            </div>

            <!-- 搜索 / 删除 / 分页工具栏 -->
            <div class="batch-salary-toolbar">
              <el-input v-model="usersPager.state.search" placeholder="搜索账号 / 手机号 / 状态…" size="small" clearable style="width: 240px;" />
              <el-button size="small" type="danger" plain :disabled="!userSelection.length" @click="batchDeleteRows('/api/admin/mall/user/batch-delete', userSelection.map(r => r.id), '商城注册用户', loadMallUsers)">🗑 删除选中</el-button>
              <el-pagination style="margin-left: auto;" v-model:current-page="usersPager.state.page" v-model:page-size="usersPager.state.size" :page-sizes="[10, 20, 50]" :total="usersPager.total" layout="total, sizes, prev, pager, next" size="small" />
            </div>

            <!-- 用户表格 -->
            <el-card>
              <el-table :data="usersPager.paged" stripe size="small" v-loading="mallUsersLoading" @selection-change="userSelection = $event">
                <el-table-column type="selection" width="42" />
                <el-table-column prop="id" label="用户ID" width="70" />
                <el-table-column prop="username" label="登录账号/手机" width="140">
                  <template #default="scope">
                    <el-tag effect="plain">{{ scope.row.username }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="nickname" label="会员昵称" width="120">
                  <template #default="scope">
                    <b>{{ scope.row.nickname }}</b>
                  </template>
                </el-table-column>
                <el-table-column prop="phone" label="联系电话" width="120" />
                <el-table-column prop="address" label="默认就医配送地址" min-width="220" />
                <el-table-column prop="balance" label="账户余额" width="110">
                  <template #default="scope">
                    <span class="text-success font-bold">¥{{ Number(scope.row.balance || 0).toFixed(2) }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="points" label="健康积分" width="90" />
                <el-table-column prop="status" label="账号状态" width="100">
                  <template #default="scope">
                    <el-tag :type="scope.row.status === 'ENABLE' ? 'success' : 'danger'" size="small">
                      {{ scope.row.status === 'ENABLE' ? '正常' : '已冻结' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="用户管控" width="180" fixed="right">
                  <template #default="scope">
                    <el-button 
                      size="small" 
                      :type="scope.row.status === 'ENABLE' ? 'danger' : 'success'" 
                      link
                      @click="handleToggleMallUserStatus(scope.row)"
                    >
                      {{ scope.row.status === 'ENABLE' ? '冻结账户' : '解除冻结' }}
                    </el-button>
                    <el-button size="small" type="primary" link @click="viewUserOrders(scope.row)">
                      查看订单
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>

            <!-- 订单查看抽屉 -->
            <el-drawer v-model="showUserOrderDrawer" title="📦 用户购药订单历史记录" size="500px">
              <div v-if="userOrders.length === 0" style="padding: 20px; color: #94a3b8; text-align: center;">
                暂无购药订单记录
              </div>
              <div v-for="order in userOrders" :key="order.id" class="user-order-card">
                <div class="flex-between">
                  <b>单号: {{ order.orderNo }}</b>
                  <el-tag type="success" size="small">{{ order.status }}</el-tag>
                </div>
                <div class="order-sub-info">
                  <span>总额: ¥{{ order.finalAmount || order.totalAmount }}</span>
                  <span>下单时间: {{ order.createTime }}</span>
                </div>
                <div v-if="parseOrderItems(order.itemsJson).length" class="order-items-box">
                  <div v-for="(it, i) in parseOrderItems(order.itemsJson)" :key="i" class="order-item-line">
                    <span class="oi-name">{{ it.productName || '商品' }}</span>
                    <span class="oi-spec">{{ it.specification || '' }}</span>
                    <span class="oi-qty">×{{ it.quantity || 1 }}</span>
                    <span class="oi-price">¥{{ (Number(it.unitPrice || 0) * (Number(it.quantity) || 1)).toFixed(2) }}</span>
                  </div>
                </div>
                <div v-else class="order-json-box">
                  {{ order.itemsJson }}
                </div>
              </div>
            </el-drawer>

            <!-- 新增商城用户弹窗（注册后可直接在春播商城登录） -->
            <el-dialog v-model="showAddMallUserDialog" title="➕ 新增春播商城用户" width="480px">
              <el-form :model="newMallUserForm" label-width="100px">
                <el-form-item label="登录账号" required>
                  <el-input v-model="newMallUserForm.username" placeholder="商城登录用户名，如 chenmin2026" />
                </el-form-item>
                <el-form-item label="登录密码" required>
                  <el-input v-model="newMallUserForm.password" placeholder="至少 6 位，默认 123456" show-password />
                </el-form-item>
                <el-form-item label="姓名/称呼" required>
                  <el-input v-model="newMallUserForm.nickname" placeholder="真实姓名或称呼，如 张女士 / 李先生" />
                </el-form-item>
                <el-form-item label="手机号" required>
                  <el-input v-model="newMallUserForm.phone" placeholder="1 开头的 11 位手机号" maxlength="11" />
                </el-form-item>
                <el-form-item label="收货地址">
                  <el-input v-model="newMallUserForm.address" type="textarea" :rows="2" placeholder="选填，默认就医配送地址" />
                </el-form-item>
              </el-form>
              <div style="font-size: 12px; color: #64748b;">注册成功自动发放 ¥200 新人健康体验金 + 200 健康积分，账号立即可在春播商城登录。</div>
              <template #footer>
                <el-button @click="showAddMallUserDialog = false">取消</el-button>
                <el-button type="primary" :loading="addMallUserLoading" @click="submitAddMallUser">注册用户</el-button>
              </template>
            </el-dialog>
          </div>

          <!-- 8. 人事账号注册与管理已并入「医护账号与权限管理」统一总台账 -->
        </main>
      </div>

      <!-- 个人工牌弹窗 -->
      <el-dialog v-model="showProfileDialog" title="👨‍⚕️ 综合中台执业人员工牌与档案" width="460px">
        <div class="profile-card">
          <div class="p-row"><b>员工工号:</b> <el-tag size="small" type="success">{{ currentUserStaffId }}</el-tag></div>
          <div class="p-row"><b>真实姓名:</b> {{ currentUserName }}</div>
          <div class="p-row"><b>安全角色:</b> <el-tag size="small" :type="currentUserRole === 'ADMIN' ? 'danger' : 'primary'">{{ currentRoleLabel }}</el-tag></div>
          <div class="p-row"><b>所属部门:</b> {{ currentUserDept }}</div>
          <div class="p-row"><b>岗位职称:</b> {{ currentUserTitle }}</div>
          <div class="p-row"><b>签约机构:</b> 春播万象基层医疗服务平台</div>
        </div>
        <template #footer>
          <div class="flex-between">
            <el-button type="danger" plain size="small" @click="handleLogoutConfirm">退出登录</el-button>
            <el-button type="primary" size="small" @click="showProfileDialog = false">关闭工牌</el-button>
          </div>
        </template>
      </el-dialog>

    <!-- 统一注册账号弹窗（选角色即定权限，全局任意 tab 可打开） -->
    <el-dialog v-model="showRegisterUnifiedDialog" title="➕ 注册系统账号（按角色自动生效权限）" width="500px">
      <el-alert type="info" :closable="false" style="margin-bottom: 14px;"
        title="医生/护士可登录云诊所与管理中台（按角色权限范围），人事/商户仅登录管理中台。注册成功后自动获得该角色的权限范围。" />
      <el-form :model="newRegisterForm" label-width="100px">
        <el-form-item label="系统角色" required>
          <el-select v-model="newRegisterForm.role" style="width: 100%">
            <el-option v-for="r in registerRoleOptions" :key="r.value" :label="r.label" :value="r.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="登录账号" required>
          <el-input v-model="newRegisterForm.username" placeholder="登录用户名，如 doctor_wang / hr_li" />
        </el-form-item>
        <el-form-item label="初始密码" required>
          <el-input v-model="newRegisterForm.password" placeholder="默认 123456" show-password />
        </el-form-item>
        <el-form-item label="真实姓名" required>
          <el-input v-model="newRegisterForm.realName" placeholder="如 王文清 / 王文静" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="newRegisterForm.phone" placeholder="1 开头的 11 位手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="部门/科室" required>
          <el-select v-model="newRegisterForm.department" style="width: 100%" filterable allow-create default-first-option placeholder="选择或输入">
            <el-option label="全科门诊" value="全科门诊" />
            <el-option label="全科慢病门诊" value="全科慢病门诊" />
            <el-option label="中医理疗特色门诊" value="中医理疗特色门诊" />
            <el-option label="儿科综合门诊" value="儿科综合门诊" />
            <el-option label="智慧药房" value="智慧药房" />
            <el-option label="人事行政科" value="人事行政科" />
            <el-option label="综合行政人事部" value="综合行政人事部" />
            <el-option label="医院院办" value="医院院办" />
            <el-option label="春播商城运营部" value="春播商城运营部" />
            <el-option label="信息中心" value="信息中心" />
          </el-select>
        </el-form-item>
        <el-form-item label="岗位职称" required>
          <el-select v-model="newRegisterForm.title" style="width: 100%" filterable allow-create default-first-option placeholder="选择或输入">
            <el-option label="主任医师" value="主任医师" />
            <el-option label="副主任医师" value="副主任医师" />
            <el-option label="主治医师" value="主治医师" />
            <el-option label="执业医师" value="执业医师" />
            <el-option label="护士" value="护士" />
            <el-option label="人事主管" value="人事主管" />
            <el-option label="人事专员" value="人事专员" />
            <el-option label="供应链主管" value="供应链主管" />
            <el-option label="运营主管" value="运营主管" />
            <el-option label="行政助理" value="行政助理" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRegisterUnifiedDialog = false">取消</el-button>
        <el-button type="success" :loading="registerUnifiedLoading" @click="submitRegisterUnified">确认注册</el-button>
      </template>
    </el-dialog>

    <!-- 发放记录弹窗（支持自由缩放、关键字搜索、月份筛选） -->
    <el-dialog v-model="showSlipHistoryDialog" title="📋 工资发放记录台账" width="1080px" destroy-on-close class="slip-history-dialog">
      <div class="batch-salary-toolbar">
        <el-input v-model="slipSearchKey" placeholder="搜索工号 / 姓名…" size="small" clearable style="width: 240px;" />
        <el-select v-model="slipFilterMonth" clearable placeholder="全部月份" size="small" style="width: 130px;">
          <el-option v-for="m in slipMonthOptions" :key="m" :label="m" :value="m" />
        </el-select>
        <span style="font-size: 12px; color: #64748b;">
          共 {{ filteredSalarySlips.length }} 条记录
          <template v-if="filteredSalarySlips.length">，合计实发 <b class="text-danger">¥{{ filteredSalarySlips.reduce((s, x) => s + (Number(x.netSalary) || 0), 0).toFixed(2) }}</b></template>
        </span>
        <el-button size="small" type="danger" plain :disabled="!slipSelection.length" @click="batchDeleteRows('/api/assistant/salary/batch-delete', slipSelection.map(r => r.id), '工资条记录', loadSalaryData)">🗑 删除选中</el-button>
        <el-pagination style="margin-left: auto;" v-model:current-page="slipsPager.state.page" v-model:page-size="slipsPager.state.size" :page-sizes="[10, 20, 50]" :total="slipsPager.total" layout="total, sizes, prev, pager, next" size="small" />
      </div>
      <el-table :data="slipsPager.paged" stripe size="small" style="width: 100%;" max-height="460" border @selection-change="slipSelection = $event">
        <el-table-column type="selection" width="42" />
        <el-table-column prop="doctorId" label="员工工号" width="115" />
        <el-table-column prop="doctorName" label="员工姓名" width="105" />
        <el-table-column prop="salaryMonth" label="归属月份" width="95" />
        <el-table-column prop="baseSalary" label="基本底薪" width="105">
          <template #default="scope"><span>¥{{ scope.row.baseSalary }}</span></template>
        </el-table-column>
        <el-table-column prop="clinicCommission" label="门诊/电商提成" width="125">
          <template #default="scope"><span class="text-success">+¥{{ scope.row.clinicCommission }}</span></template>
        </el-table-column>
        <el-table-column prop="plasterCommission" label="贴敷理疗/合规奖" width="135">
          <template #default="scope"><span class="text-success font-bold">+¥{{ scope.row.plasterCommission }}</span></template>
        </el-table-column>
        <el-table-column prop="netSalary" label="实发工资" min-width="110">
          <template #default="scope"><b class="text-danger" style="font-size: 14px;">¥{{ scope.row.netSalary }}</b></template>
        </el-table-column>
        <el-table-column prop="status" label="发放状态" width="95" align="center">
          <template #default="scope">
            <el-tag type="success" size="small" effect="dark">{{ scope.row.status || '已发放' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="发放时间" width="155">
          <template #default="scope">
            <span style="font-size: 12px; color: #64748b;">{{ (scope.row.createTime || '').replace('T', ' ').slice(0, 19) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 弹窗：商城订单发货出库与顺丰物流生成 -->
    <el-dialog 
      v-model="showShipDialog" 
      title="📦 商城订单发货出库与春播便民速递面单核验" 
      width="520px"
    >
      <div v-if="currentShippingOrder">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="订单单号">{{ currentShippingOrder.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="购药客户">{{ currentShippingOrder.buyerName }}</el-descriptions-item>
          <el-descriptions-item label="配送收货地址">{{ currentShippingOrder.clinicName }}</el-descriptions-item>
          <el-descriptions-item label="订单实付总额">¥{{ currentShippingOrder.finalAmount }}</el-descriptions-item>
        </el-descriptions>

        <div class="mt-4">
          <label style="display: block; font-weight: bold; margin-bottom: 6px; font-size: 13px;">春播便民速递运单号：</label>
          <el-input v-model="shippingTrackingNo" placeholder="系统已自动生成春播速递单号 (CB前缀)" size="large" />
        </div>

        <el-alert 
          type="warning" 
          :closable="false" 
          class="mt-4"
          title="发货出库后，系统将真实扣减对应商品库存，并生成出库台账流水计入进销存大屏！" 
        />
      </div>
      <template #footer>
        <el-button @click="showShipDialog = false">取消</el-button>
        <el-button type="success" :loading="shippingLoading" @click="confirmShipOrder">
          确认发货并扣减库存
        </el-button>
      </template>
    </el-dialog>

    <!-- 弹窗：注册商户账号 -->
    <el-dialog v-model="showAddMerchantModal" title="🏪 注册春播商城商户账号" width="460px">
      <el-form label-width="100px">
        <el-form-item label="登录账号 *" required>
          <el-input v-model="merchantForm.username" placeholder="如 merchant_02" />
        </el-form-item>
        <el-form-item label="登录密码 *" required>
          <el-input v-model="merchantForm.password" type="password" show-password placeholder="默认 123456" />
        </el-form-item>
        <el-form-item label="商户负责人 *" required>
          <el-input v-model="merchantForm.realName" placeholder="如 李商户" />
        </el-form-item>
        <el-form-item label="联系手机号">
          <el-input v-model="merchantForm.phone" placeholder="11位手机号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddMerchantModal = false">取消</el-button>
        <el-button type="success" :loading="merchantSubmitting" @click="submitRegisterMerchant">
          确认注册商户
        </el-button>
      </template>
    </el-dialog>


    </template>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed, nextTick , watch } from 'vue'
import axios from 'axios'
import { marked } from 'marked'
import { ElMessage, ElNotification, ElMessageBox } from 'element-plus'
import { SwitchButton } from '@element-plus/icons-vue'

// ==============================================
// 认证与 RBAC 状态
// ==============================================
const isLoggedIn = ref(!!localStorage.getItem('chunbo_admin_token'))
const currentUserRole = ref(localStorage.getItem('chunbo_admin_role') || 'ADMIN')
const currentUserName = ref(localStorage.getItem('chunbo_admin_name') || '系统管理员')
const currentUserStaffId = ref(localStorage.getItem('chunbo_admin_staff_id') || 'ADM_0001')
const currentUserDept = ref(localStorage.getItem('chunbo_admin_dept') || '医院院办 / 信息中心')
const currentUserTitle = ref(localStorage.getItem('chunbo_admin_title') || '总院管理员')

const currentRoleLabel = computed(() => {
  if (currentUserRole.value === 'ADMIN') return '最高管理员'
  if (currentUserRole.value === 'HR') return '人事主管'
  if (currentUserRole.value === 'MERCHANT') return '商城商户店长'
  if (currentUserRole.value === 'DOCTOR') return '门诊医生'
  return '中台员工'
})

// 登录表单
const loginForm = ref({ username: 'admin', password: '123456' })
const loginLoading = ref(false)
const loginError = ref('')


const handleAdminLogin = async () => {
  if (!loginForm.value.username || !loginForm.value.password) {
    loginError.value = '请输入工号和密码'
    return
  }
  loginLoading.value = true
  loginError.value = ''
  try {
    const res = await axios.post('/api/auth/login', loginForm.value)
    if (res.data && res.data.success) {
      localStorage.setItem('chunbo_admin_token', res.data.token)
      localStorage.setItem('chunbo_admin_role', res.data.role || 'ADMIN')
      localStorage.setItem('chunbo_admin_name', res.data.displayName || res.data.username)
      localStorage.setItem('chunbo_admin_staff_id', res.data.staffId || 'STAFF_001')
      localStorage.setItem('chunbo_admin_dept', res.data.department || '全科门诊')
      localStorage.setItem('chunbo_admin_title', res.data.title || '主治医师')

      currentUserRole.value = res.data.role || 'ADMIN'
      currentUserName.value = res.data.displayName || res.data.username
      currentUserStaffId.value = res.data.staffId || 'STAFF_001'
      currentUserDept.value = res.data.department || '全科门诊'
      currentUserTitle.value = res.data.title || '主治医师'
      isLoggedIn.value = true

      // 登录后按当前角色重新拉取管理系统权限配置（scope=ADMIN），替代页面初始加载时可能拉到的旧角色配置
      allowedAdminTabs.value = null
      await loadAdminRolePermissions()

      // 根据角色智能跳转默认首屏
      if (currentUserRole.value === 'DOCTOR') {
        currentTab.value = 'analytics'
      } else if (currentUserRole.value === 'MERCHANT') {
        currentTab.value = 'mall-orders'
      } else {
        currentTab.value = 'analytics'
      }

      ElNotification({
        title: '登录成功',
        message: `欢迎进入中台，您的当前权限为: 【${currentRoleLabel.value}】`,
        type: 'success'
      })
      loadAllData()
    } else {
      loginError.value = res.data?.message || '登录失败，请核对工号密码'
    }
  } catch (err) {
    if (err.response?.status === 401) {
      loginError.value = err.response.data?.message || '工号或密码错误'
    } else {
      loginError.value = '无法连接到后端(8080端口)，请确保后端服务正常运行'
    }
  } finally {
    loginLoading.value = false
  }
}

const handleLogoutConfirm = () => {
  ElMessageBox.confirm('您确定要退出春播综合中台系统吗？', '退出确认', {
    confirmButtonText: '确定退出',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    localStorage.removeItem('chunbo_admin_token')
    localStorage.removeItem('chunbo_admin_role')
    localStorage.removeItem('chunbo_admin_name')
    localStorage.removeItem('chunbo_admin_staff_id')
    localStorage.removeItem('chunbo_admin_dept')
    localStorage.removeItem('chunbo_admin_title')
    allowedAdminTabs.value = null
    isLoggedIn.value = false
    showProfileDialog.value = false
    ElMessage.success('已安全注销退出')
  }).catch(() => {})
}

// ==============================================
// 业务数据状态
// ==============================================
// 当前主标签：刷新后保持在原标签页（localStorage 持久化）
const currentTab = ref(localStorage.getItem('chunbo_admin_tab') || 'analytics')
// 员工角色矩阵、人事与商户中台管理均已并入「医护账号与权限管理」(doctors)，纠正历史缓存
if (currentTab.value === 'roles' || currentTab.value === 'hr-management') {
  currentTab.value = 'doctors'
}

// ── 角色允许的中台模块（sys_role_permission scope=ADMIN 动态配置，替代写死的 v-if）──
const allowedAdminTabs = ref(null)
const defaultAdminTabs = (role) => {
  const r = (role || '').toUpperCase()
  if (r === 'MERCHANT') return ['analytics', 'salary', 'approval', 'mall-orders', 'mall-products', 'mall-users']
  if (r === 'HR') return ['analytics', 'salary', 'approval', 'doctors']
  if (r === 'DOCTOR' || r === 'NURSE') return ['analytics', 'salary', 'approval']
  return ['analytics', 'mall-orders', 'mall-products', 'mall-users', 'salary', 'approval', 'doctors']
}
const loadAdminRolePermissions = async () => {
  try {
    const res = await axios.get('/api/role-permissions')
    const cfg = (res.data || []).find(r =>
      String(r.role || '').toUpperCase() === (currentUserRole.value || '').toUpperCase()
      && String(r.scope || '').toUpperCase() === 'ADMIN')
    if (cfg) {
      const mods = JSON.parse(cfg.modulesJson || '[]')
      allowedAdminTabs.value = Array.isArray(mods) ? mods : []
    }
  } catch (e) {}
}
const canSeeTab = (key) => allowedAdminTabs.value
  ? allowedAdminTabs.value.includes(key)
  : defaultAdminTabs(currentUserRole.value).includes(key)
watch(allowedAdminTabs, () => {
  if (allowedAdminTabs.value && allowedAdminTabs.value.length && !allowedAdminTabs.value.includes(currentTab.value)) {
    const first = defaultAdminTabs(currentUserRole.value).find(k => allowedAdminTabs.value.includes(k))
    if (first) currentTab.value = first
  }
}, { immediate: true })
loadAdminRolePermissions()
// 切换标签页时自动刷新该页数据（每次进入都拉最新，不再依赖登录时的一次性加载）
const tabLoaders = {
  analytics: () => { loadAnalytics(); loadSalaryData() },
  'mall-orders': () => loadMallOrders(),
  'mall-products': () => { loadMallAdminProducts(); loadAnalytics() },
  'mall-users': () => loadMallUsers(),
  salary: () => {
    loadSalaryData()
    if (currentUserRole.value !== 'DOCTOR' && currentUserRole.value !== 'MERCHANT') loadBatchSalaryBoard()
  },
  approval: () => loadApprovals(),
  doctors: () => { loadStaffRoles(); loadDoctorAccounts(); loadRolePermRows() },
  'hr-management': () => loadStaffList()
}
watch(currentTab, (v) => {
  try { localStorage.setItem('chunbo_admin_tab', v) } catch (e) {}
  // 进入工资条管理 tab（ADMIN/HR）时自动加载全员薪酬核算表
  if (v === 'salary' && currentUserRole.value !== 'DOCTOR' && currentUserRole.value !== 'MERCHANT') {
    loadBatchSalaryBoard()
  }
  // 按当前角色权限过滤后刷新对应页面数据
  const loader = tabLoaders[v]
  if (loader && canSeeTab(v)) loader()
})
// 商户无经营大屏权限：恢复到不可见标签时自动纠正
if (currentUserRole.value === 'MERCHANT' && currentTab.value === 'analytics') {
  currentTab.value = 'mall-orders'
}
const showProfileDialog = ref(false)

const analytics = ref({})
const inventoryRecords = ref([])
const stockWarnings = ref([])
const salarySlips = ref([])
const approvals = ref([])
const staffRoles = ref([])

// 针对医生的过滤展示
const displaySalarySlips = computed(() => {
  if (currentUserRole.value === 'DOCTOR' || currentUserRole.value === 'MERCHANT') {
    // 医生与商户只能看自己的工资条明细
    return salarySlips.value.filter(s => s.doctorName === currentUserName.value || s.doctorId === currentUserStaffId.value || (currentUserRole.value === 'MERCHANT' && (s.doctorId === 'MERCH_001' || s.doctorName.includes('商户'))))
  }
  return salarySlips.value
})

const displayApprovals = computed(() => {
  if (currentUserRole.value === 'DOCTOR') {
    return approvals.value.filter(a => a.applicantName === currentUserName.value)
  }
  return approvals.value
})

// 医生账号管理
const doctorAccounts = ref([])
const doctorLoading = ref(false)
// 指标卡改为统一台账口径（staffRoles = 医生表+员工表去重合并的真实账号）
const unifiedStaffTotal = computed(() => staffRoles.value.length)
const activeStaffCount = computed(() => staffRoles.value.filter(s => s.status === 'ENABLE').length)
const departmentCount = computed(() => new Set(staffRoles.value.map(s => s.department).filter(d => d && d !== '—')).size)
const activeDoctorCount = computed(() => doctorAccounts.value.filter(d => d.status === 'ENABLE').length)

// 商城商品管理
const mallProducts = ref([])
const mallLoading = ref(false)
const showPriceDialog = ref(false)
const editingProduct = ref({})
const priceForm = ref({ retailGuidePrice: 10, wholesalePrice: 5 })
const showInboundDialog = ref(false)
const inboundProduct = ref({})
const inboundQty = ref(100)
const showAddProductDialog = ref(false)
const newProductForm = ref({
  productName: '',
  category: '',
  specification: '',
  manufacturer: '',
  retailGuidePrice: null,
  wholesalePrice: null,
  stock: null,
  imageUrl: ''
})

// 自定义上传：把商品图片 POST 到后端，成功后回填 imageUrl
const uploadingProductImg = ref(false)
const handleProductImageUpload = async (options) => {
  const formData = new FormData()
  formData.append('file', options.file)
  uploadingProductImg.value = true
  try {
    const res = await axios.post('/api/admin/mall/product/upload-image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (res.data?.success && res.data.url) {
      newProductForm.value.imageUrl = res.data.url
      ElMessage.success('商品图片上传成功')
    } else {
      ElMessage.error(res.data?.message || '图片上传失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '图片上传失败')
  } finally {
    uploadingProductImg.value = false
  }
}

// 已有商品行内换图：上传后仅更新该商品 imageUrl
const handleRowImageUpload = async (row, options) => {
  const formData = new FormData()
  formData.append('file', options.file)
  try {
    const res = await axios.post('/api/admin/mall/product/upload-image', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (res.data?.success && res.data.url) {
      await axios.post('/api/admin/mall/product/image', { id: row.id, imageUrl: res.data.url })
      ElMessage.success('商品图片已更新')
      loadMallAdminProducts()
    } else {
      ElMessage.error(res.data?.message || '图片上传失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '图片上传失败')
  }
}

// 商城用户管理
const mallUsers = ref([])
const mallUsersLoading = ref(false)
const showUserOrderDrawer = ref(false)
const userOrders = ref([])

// 人事管理 (Admin 专属)
const allStaffList = ref([])
const staffLoading = ref(false)

// OA 请假
const approvalViewRole = ref('DEAN')
const leaveForm = ref({
  type: '年假',
  days: 1.0,
  reason: '参加全科医学学术高峰论坛培训'
})

// ── 全中台 AI 智能调度助手：多会话历史与持久化记忆隔离引擎 (支持新建、历史记录切换、持久化防刷新丢失) ──
const showAdminHistoryDrawer = ref(false)
const adminSessionList = ref([])
const currentAdminSessionId = ref('')

const activeAdminSessionTitle = computed(() => {
  const cur = adminSessionList.value.find(s => s.id === currentAdminSessionId.value)
  if (!cur) return '全中台运营调度中枢'
  return cur.title.length > 20 ? cur.title.substring(0, 20) + '...' : cur.title
})

// ── 会话历史按时间分组：今天 / 本周 / 本月 / 今年 / 更早 ──
const ADMIN_SESSION_GROUPS = [
  { key: 'today', label: '今天' },
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' },
  { key: 'year', label: '今年' },
  { key: 'earlier', label: '更早' }
]

const parseAdminSessionTime = (t) => {
  if (!t) return 0
  if (typeof t === 'number') return t
  let s = String(t).trim()
  if (/^\d{2}-\d{2} \d{2}:\d{2}/.test(s)) {
    // "MM-DD HH:mm" 缺年份 → 补当前年
    s = new Date().getFullYear() + '-' + s.replace(' ', 'T')
  } else {
    s = s.replace(' ', 'T')
  }
  let ts = new Date(s).getTime()
  if (isNaN(ts)) return 0
  // 跨年旧会话被解析到未来超过1天时回退一年
  if (ts > Date.now() + 86400000) ts -= 365 * 86400000
  return ts
}

const groupedAdminSessions = computed(() => {
  const groups = { today: [], week: [], month: [], year: [], earlier: [] }
  const now = new Date()
  const dayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const weekStart = dayStart - ((now.getDay() + 6) % 7) * 86400000 // 周一为一周起点
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime()
  const yearStart = new Date(now.getFullYear(), 0, 1).getTime()
  for (const s of adminSessionList.value) {
    const t = parseAdminSessionTime(s.updatedAt || s.createdAt)
    if (t >= dayStart) groups.today.push(s)
    else if (t >= weekStart) groups.week.push(s)
    else if (t >= monthStart) groups.month.push(s)
    else if (t >= yearStart) groups.year.push(s)
    else groups.earlier.push(s)
  }
  return groups
})

const formatAdminNowTime = () => {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const date = String(d.getDate()).padStart(2, '0')
  const h = String(d.getHours()).padStart(2, '0')
  const min = String(d.getMinutes()).padStart(2, '0')
  return `${m}-${date} ${h}:${min}`
}

const getAdminSessionsStorageKey = () => 'chunbo_admin_ai_sessions_' + (currentUserStaffId.value || 'ADM_0001')

const getDefaultAdminWelcome = () => [
  {
    role: 'assistant',
    content: `您好 **${currentUserName.value || '系统管理员'}**！我是 **春播云医院全中台 AI 智能调度与运营指挥官**。\n当前操作权限：【${currentRoleLabel.value}】（工号: \`${currentUserStaffId.value}\`）。\n\n系统已为您载入**完全独立的运营会话记忆空间**，杜绝跨角色跨账号的信息越权与历史泄露。您可以随时通过自然语言或下方快捷胶囊指令进行全中台调度：\n\n` +
      `- 📊 **薪酬与绩效中枢**：输入「全院薪酬汇总表」，一键核算全院本月薪酬总额、医护实发与奖金台账\n` +
      `- 📦 **商城订单履约出库**：输入「商城待发货订单」，穿透调阅春播健康商城 B2C 待履约订单列表与配送节点\n` +
      `- 🚨 **智慧药房低库存预警**：输入「药房低库存预警」，穿透 MySQL 真实数据表研判严重短缺药品\n` +
      `- 🚚 **便民速运状态调度**：输入「发货订单 B2C2026...」，一键通过 MCP 工具推进物流状态至顺丰揽收与送达\n` +
      `- 📋 **OA 请假与审批协同**：输入「OA请假审批」，智能汇总全院待审批流程与规范核验`
  }
]

const chatMessages = ref([])

// 从 localStorage 全量加载当前人员的会话记忆
const loadAdminSessions = () => {
  try {
    const key = getAdminSessionsStorageKey()
    const raw = localStorage.getItem(key)
    if (raw) {
      const data = JSON.parse(raw)
      if (Array.isArray(data.sessions) && data.sessions.length > 0) {
        adminSessionList.value = data.sessions
        currentAdminSessionId.value = data.currentSessionId || data.sessions[0].id
        const active = adminSessionList.value.find(s => s.id === currentAdminSessionId.value) || adminSessionList.value[0]
        chatMessages.value = active.messages || []
        syncAdminSessionsFromBackend()
        // 进入页面自动滚到最新消息，不用手动往下翻
        nextTick(() => {
          if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
        })
        return
      }
    }
  } catch (e) {
    console.warn('加载中台会话记忆异常', e)
  }

  // 默认初始调度会话
  const initialSession = {
    id: 'admin_sess_' + Date.now(),
    title: '全中台运营调度主会话',
    createdAt: formatAdminNowTime(),
    updatedAt: formatAdminNowTime(),
    messages: getDefaultAdminWelcome()
  }

  adminSessionList.value = [initialSession]
  currentAdminSessionId.value = initialSession.id
  chatMessages.value = initialSession.messages
  saveAdminSessions()
  syncAdminSessionsFromBackend()
}

// 从后端同步会话历史标题（数据来源切换：会话列表标题以后端 AI 提炼为准）
const syncAdminSessionsFromBackend = async () => {
  const staffId = currentUserStaffId.value
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
    backend.forEach(bs => {
      const local = adminSessionList.value.find(s => s.id === bs.sessionId)
      if (local) {
        if (bs.title) local.title = bs.title
      } else {
        adminSessionList.value.unshift({
          id: bs.sessionId,
          title: bs.title || '历史会话',
          createdAt: bs.updateTime || formatAdminNowTime(),
          updatedAt: bs.updateTime || formatAdminNowTime(),
          messages: getDefaultAdminWelcome()
        })
      }
    })
  } catch (e) {}
}

// 自动持久化保存到 localStorage
const saveAdminSessions = () => {
  try {
    const cur = adminSessionList.value.find(s => s.id === currentAdminSessionId.value)
    if (cur) {
      cur.messages = JSON.parse(JSON.stringify(chatMessages.value))
      cur.updatedAt = formatAdminNowTime()
    }
    localStorage.setItem(getAdminSessionsStorageKey(), JSON.stringify({
      currentSessionId: currentAdminSessionId.value,
      sessions: adminSessionList.value
    }))
  } catch (e) {
    console.warn('保存中台会话记忆异常', e)
  }
}

// 新建会话
const createNewAdminSession = () => {
  const newSess = {
    id: 'admin_sess_' + Date.now() + '_' + Math.floor(Math.random() * 1000),
    title: '新运营调度会话',
    createdAt: formatAdminNowTime(),
    updatedAt: formatAdminNowTime(),
    messages: getDefaultAdminWelcome()
  }
  adminSessionList.value.unshift(newSess)
  currentAdminSessionId.value = newSess.id
  chatMessages.value = newSess.messages
  saveAdminSessions()
  scrollChatToBottom()
  ElMessage.success('已新建专属全中台 AI 调度会话')
}

// 切换历史会话
const switchAdminSession = (sessionId) => {
  saveAdminSessions()
  const target = adminSessionList.value.find(s => s.id === sessionId)
  if (target) {
    currentAdminSessionId.value = target.id
    chatMessages.value = target.messages || []
    saveAdminSessions()
    showAdminHistoryDrawer.value = false
    scrollChatToBottom()
    ElMessage.success(`已切换至会话【${target.title}】`)
  }
}

// 删除单个会话
const deleteAdminSession = (sessionId) => {
  const idx = adminSessionList.value.findIndex(s => s.id === sessionId)
  if (idx !== -1) {
    adminSessionList.value.splice(idx, 1)
    if (adminSessionList.value.length > 0) {
      currentAdminSessionId.value = adminSessionList.value[0].id
      chatMessages.value = adminSessionList.value[0].messages || []
      scrollChatToBottom()
    } else {
      createNewAdminSession()
    }
    saveAdminSessions()
    // 同步后端删除（DB + Redis 记忆）
    const staffId = currentUserStaffId.value
    if (staffId) {
      axios.delete('/api/session/history', { params: { bizType: 'oa', sessionId, userId: staffId } }).catch(() => {})
    }
    ElMessage.success('已删除该会话记录')
  }
}

// 清空当前人员全部会话历史
const clearAllAdminSessions = () => {
  ElMessageBox.confirm('确定清空当前登录人员的所有 AI 运营调度会话历史？清空后不可恢复。', '清空会话历史', {
    confirmButtonText: '确认清空',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    localStorage.removeItem(getAdminSessionsStorageKey())
    loadAdminSessions()
    ElMessage.success(`已清空当前工号【${currentUserStaffId.value}】的全部 AI 调度记录`)
  }).catch(() => {})
}

// 兼容旧方法名
const clearUserChatHistory = clearAllAdminSessions
const loadUserChatHistory = loadAdminSessions
const saveUserChatHistory = saveAdminSessions

// 组件 setup 阶段立即恢复会话，防止刷新页面内容空白
try {
  loadAdminSessions()
} catch (e) {}

// 监听登录用户切换
watch(currentUserStaffId, () => {
  loadAdminSessions()
})

const inputQuery = ref('')
const chatLoading = ref(false)
const chatBoxRef = ref(null)
// 聊天区滚动到底（新消息/切换会话/进入页面统一调用）
const scrollChatToBottom = () => {
  nextTick(() => {
    if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
  })
}
// 附件（工资表图片 / Excel）→ AI 提取表格后 function-calling 发工资
const chatAttachment = ref(null)   // { fileId, fileName, fileType, previewUrl }
const chatFileInput = ref(null)

onMounted(() => {
  if (isLoggedIn.value) {
    loadAllData()
    loadAdminSessions()
  }
})

const openSystem = (url) => window.open(url, '_blank')
const renderMarkdown = (text) => {
  marked.setOptions({ breaks: true, gfm: true })
  let html = marked.parse(text || '')
  if (typeof html === 'string') {
    html = html
      .replace(/<table>/g, '<div class="md-table-scroll"><table>')
      .replace(/<\/table>/g, '</table></div>')
  }
  return html
}

const loadAllData = async () => {
  loadAnalytics()
  loadSalaryData()
  loadApprovals()
  loadStaffRoles()
  loadDoctorAccounts()
  loadStaffCandidates()
  loadRolePermRows()
  if (currentUserRole.value === 'ADMIN' || currentUserRole.value === 'MERCHANT') {
    loadMallOrders()
    loadMallAdminProducts()
    loadMallUsers()
    loadAnalytics()
  }
  if (currentUserRole.value === 'ADMIN' || currentUserRole.value === 'HR') {
    loadStaffList()
  }
}

const analyticsPeriod = ref('today')

const loadAnalytics = async () => {
  try {
    const resSummary = await axios.get('/api/analytics/summary', {
      params: { period: analyticsPeriod.value }
    })
    analytics.value = resSummary.data || {}
    const resInv = await axios.get('/api/pharmacy/inventory-records')
    inventoryRecords.value = resInv.data || []
    const resWarn = await axios.get('/api/pharmacy/warnings')
    stockWarnings.value = resWarn.data || []
  } catch (e) {}
}

const formatTime = (val) => {
  if (!val) return '-'
  const d = new Date(val)
  if (isNaN(d.getTime())) return String(val).replace('T', ' ').substring(0, 19)
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const loadSalaryData = async () => {
  try {
    const res = await axios.get('/api/assistant/salary')
    salarySlips.value = res.data || []
  } catch (e) {}
}

// ── 全员薪酬核算与发放中枢（tab 主区域，表格化批量管理）──
// 当前日期（本地时区，避免 toISOString 的 UTC 偏移导致错位）
const currentLocalDate = () => {
  const n = new Date()
  return `${n.getFullYear()}-${String(n.getMonth() + 1).padStart(2, '0')}-${String(n.getDate()).padStart(2, '0')}`
}
const salaryBoardMonth = ref(currentLocalDate())
const batchSalaryRows = ref([])
const batchSalaryExcelInput = ref(null)
const batchSalaryTableRef = ref(null)
const batchSelection = ref([])
const batchAiAllLoading = ref(false)
// 实发 = 基本底薪 + 门诊/电商提成 + 贴敷理疗合规奖 − 社保代扣 − 个税
const batchRowNet = (row) =>
  (Number(row.baseSalary) || 0) + (Number(row.clinicCommission) || 0) + (Number(row.plasterCommission) || 0)
  - (Number(row.deductionSocial) || 0) - (Number(row.tax) || 0)
const batchTotalAmount = computed(() => batchSalaryRows.value.reduce((s, r) => s + batchRowNet(r), 0))
const onBatchSelectionChange = (sel) => { batchSelection.value = sel }

const loadBatchSalaryBoard = async () => {
  batchSalaryRows.value = []
  batchSelection.value = []
  // 发薪精确到天：发薪日期是完整日期（YYYY-MM-DD），同员工同「发放日」重发才覆盖
  const payDate = salaryBoardMonth.value
  try {
    const [candRes, slipRes] = await Promise.all([
      axios.get('/api/assistant/salary/staff-candidates'),
      axios.get('/api/assistant/salary')
    ])
    const cands = candRes.data || []
    const slips = (slipRes.data || []).filter(s => s.salaryMonth === payDate)
    for (const c of cands) {
      const slip = slips.find(s => String(s.doctorId || '').toLowerCase() === String(c.staffId || '').toLowerCase())
      batchSalaryRows.value.push({
        staffId: c.staffId,
        name: c.realName,
        role: c.role,
        roleLabel: getCandidateRoleLabel(c.role),
        baseSalary: slip ? Number(slip.baseSalary) : 0,
        clinicCommission: slip ? Number(slip.clinicCommission) : 0,
        plasterCommission: slip ? Number(slip.plasterCommission) : 0,
        deductionSocial: slip ? Number(slip.deductionSocial) : 0,
        tax: slip ? Number(slip.tax) : 0,
        hasAccount: true,
        slipId: slip ? slip.id : null,
        source: slip ? '该日已发放（重发将覆盖）' : '待发放',
        sourceType: slip ? 'success' : 'info',
        isNew: false,
        _aiLoading: false,
        _paying: false
      })
    }
  } catch (e) {
    ElMessage.error('加载全院员工列表失败')
  }
}

// 删除工资发放记录 + 同步删除医院员工账号（工资名单与系统员工账号一一对应）
const deleteSalaryBoardRows = async (rows) => {
  if (!rows || !rows.length) {
    ElMessage.warning('请先勾选要删除的行')
    return
  }
  const accountRows = rows.filter(r => r.hasAccount !== false && r.staffId)
  const draftRows = rows.filter(r => r.hasAccount === false || !r.staffId)

  // 纯草稿行（无账号/无落库数据）：仅从本表移除
  if (!accountRows.length) {
    try {
      await ElMessageBox.confirm(
        `确定移除选中的 ${draftRows.length} 条行？（无系统账号、无落库工资数据，仅从本表移除）`,
        '移除确认',
        { type: 'warning', confirmButtonText: '确认移除', cancelButtonText: '取消' }
      )
    } catch (e) { return }
    batchSalaryRows.value = batchSalaryRows.value.filter(r => !draftRows.includes(r))
    ElMessage.success(`已移除 ${draftRows.length} 条行`)
    return
  }

  // 先拉最新账号/工资数据，确保按最新状态匹配
  try { await Promise.all([loadDoctorAccounts(), loadStaffList()]) } catch (e) {}
  const doctorDel = []   // { row, accountId }
  const staffDel = []
  const missAccounts = []
  for (const r of accountRows) {
    const sid = String(r.staffId)
    const doc = doctorAccounts.value.find(d => String(d.username) === sid || String(d.doctorId) === sid)
    const stf = doc ? null : allStaffList.value.find(s => String(s.staffId) === sid)
    if (doc) doctorDel.push({ row: r, accountId: doc.id })
    else if (stf) staffDel.push({ row: r, accountId: stf.id })
    else missAccounts.push(r.name || sid)
  }
  if (missAccounts.length) {
    ElMessage.warning(`以下人员在系统账号表中未找到对应账号，将只删工资记录不删账号：${missAccounts.join('、')}`)
  }
  const accNames = accountRows.map(r => `${r.name || r.staffId}(${r.staffId})`).slice(0, 6).join('、') + (accountRows.length > 6 ? ` 等 ${accountRows.length} 人` : '')
  try {
    await ElMessageBox.confirm(
      `确定删除：${accNames}？将执行——① 删除该(这些)员工的全部工资发放记录；② 同步删除医院系统员工账号。⚠️ 账号删除后该员工将无法登录系统，并从工资名单中永久移除，删除不可恢复！`,
      '删除工资记录与员工账号',
      { type: 'warning', confirmButtonText: '确认删除（含账号）', cancelButtonText: '取消' }
    )
  } catch (e) { return }

  try {
    // 1) 删该员工全部工资发放记录（含历史月份，账号删了不留孤儿数据）
    const slipRes = await axios.get('/api/assistant/salary')
    const allSlips = slipRes.data || []
    const slipIds = []
    for (const r of accountRows) {
      const sid = String(r.staffId).toLowerCase()
      for (const s of allSlips) {
        if (String(s.doctorId || '').toLowerCase() === sid) slipIds.push(s.id)
      }
    }
    if (slipIds.length) {
      const del = await axios.post('/api/assistant/salary/batch-delete', { ids: slipIds })
      if (!del.data?.success) {
        ElMessage.error(del.data?.message || '删除工资记录失败')
        return
      }
    }
    // 2) 删员工账号（复用医生/员工管理页已有删除接口，路径级 ADMIN/HR 权限保护）
    let delAcc = 0
    const failAcc = []
    for (const d of doctorDel) {
      try { await axios.delete('/api/doctor/' + d.accountId); delAcc++ } catch (e) { failAcc.push(d.row.name || d.row.staffId) }
    }
    for (const s of staffDel) {
      try { await axios.delete('/api/admin/staff/' + s.accountId); delAcc++ } catch (e) { failAcc.push(s.row.name || s.row.staffId) }
    }
    // 3) 草稿行仅从本表移除
    if (draftRows.length) batchSalaryRows.value = batchSalaryRows.value.filter(r => !draftRows.includes(r))
    ElMessage.success(`已删除 ${slipIds.length} 条工资发放记录、${delAcc} 个员工账号${failAcc.length ? '；账号删除失败：' + failAcc.join('、') : ''}`)
    await loadBatchSalaryBoard()
    if (typeof loadSalaryData === 'function') loadSalaryData()
  } catch (e) {
    ElMessage.error('删除失败：' + (e.response?.data?.message || e.message))
  }
}

const triggerBatchExcelUpload = () => {
  batchSalaryExcelInput.value && batchSalaryExcelInput.value.click()
}
const handleBatchExcelFill = async (e) => {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    ElMessage.warning('请选择 Excel 工资表（xlsx / xls）')
    e.target.value = ''
    return
  }
  const fd = new FormData()
  fd.append('file', file)
  try {
    const res = await axios.post('/api/assistant/salary/parse-excel', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    const d = res.data
    if (!d || !d.success) {
      ElMessage.error((d && d.message) || '工资表解析失败')
      e.target.value = ''
      return
    }
    let matched = 0, noAccount = 0
    for (const r of (d.rows || [])) {
      const amt = Number(String(r.amount).trim())
      if (isNaN(amt) || amt <= 0) continue
      const key = String(r.employeeId || '').trim().toLowerCase()
      const nm = String(r.employeeName || '').trim()
      const hit = batchSalaryRows.value.find(x =>
        (key && String(x.staffId || '').toLowerCase() === key) ||
        (nm && x.name && x.name === nm))
      if (hit) {
        hit.baseSalary = amt
        hit.clinicCommission = 0
        hit.plasterCommission = 0
        hit.deductionSocial = 0
        hit.tax = 0
        hit.source = '工资表填充'
        hit.sourceType = 'warning'
        matched++
      } else {
        // 工资表里出现但系统无账号的人员：红标警示、不可发放（工资必须与系统账号一一对应）
        batchSalaryRows.value.push({
          staffId: r.employeeId || '', name: nm || '', role: '', roleLabel: '表内人员',
          baseSalary: amt, clinicCommission: 0, plasterCommission: 0, deductionSocial: 0, tax: 0,
          hasAccount: false,
          source: '⚠️ 系统中无此账号，不可发放', sourceType: 'danger', isNew: false, _aiLoading: false, _paying: false
        })
        noAccount++
      }
    }
    if (noAccount > 0) {
      ElMessage.warning(`工资表填充完成：匹配到系统员工 ${matched} 人；另有 ${noAccount} 人在系统中无账号，已红标且不可发放（发放名单必须与系统员工账号一一对应）`)
    } else {
      ElMessage.success(`工资表填充完成：匹配到系统员工 ${matched} 人`)
    }
    nextTick(() => {
      if (batchSalaryTableRef.value) batchSalaryTableRef.value.setScrollTop(99999)
    })
  } catch (err) {
    ElMessage.error('解析失败：' + (err.response?.data?.message || err.message || '网络错误'))
  }
  e.target.value = ''
}
const addBatchRow = () => {
  batchSalaryRows.value.push({
    staffId: '', name: '', role: '', roleLabel: '手动添加',
    baseSalary: 0, clinicCommission: 0, plasterCommission: 0, deductionSocial: 0, tax: 0,
    source: '手动添加', sourceType: 'info', isNew: true, _aiLoading: false, _paying: false
  })
  // 自动滚动到列表底部，让新加的行立即可见
  nextTick(() => {
    if (batchSalaryTableRef.value) batchSalaryTableRef.value.setScrollTop(99999)
  })
}
const aiCalcBatchRow = async (row, silent = false) => {
  if (!row.staffId) {
    ElMessage.warning('请先填写员工工号')
    return false
  }
  row._aiLoading = true
  try {
    const res = await axios.post('/api/assistant/salary/ai-calculate', {
      staffId: row.staffId, name: row.name, role: row.role, month: String(salaryBoardMonth.value || '').slice(0, 7)
    })
    const d = res.data
    if (d && (d.success !== false) && d.netSalary != null) {
      row.baseSalary = Number(d.baseSalary) || 0
      row.clinicCommission = Number(d.clinicCommission) || 0
      row.plasterCommission = Number(d.plasterCommission) || 0
      row.deductionSocial = Number(d.deductionSocial) || 0
      row.tax = Number(d.tax) || 0
      row.source = 'AI 智能测算'
      row.sourceType = 'primary'
      // 单行测算才弹提示；全员批量测算静默，结束后统一汇总，避免通知刷屏
      if (!silent) {
        ElNotification({
          title: '🤖 AI 测算完成',
          message: `${row.name || row.staffId} 实发 ¥${batchRowNet(row).toFixed(2)}${d.aiComment ? '｜' + d.aiComment : ''}${d.metricsBasis && d.metricsBasis.formula ? '｜公式：' + d.metricsBasis.formula : ''}`,
          type: 'success', duration: 6000
        })
      }
      return true
    } else {
      if (!silent) {
        ElMessage.warning((d && d.message) || `AI 测算失败：${row.name || row.staffId} 缺少历史工资与业务量数据，请手动填写`)
      }
      return false
    }
  } catch (e) {
    if (!silent) {
      ElMessage.error('AI 测算失败：' + (e.response?.data?.message || e.message))
    }
    return false
  } finally {
    row._aiLoading = false
  }
}
// 全员 AI 测算：逐行调用（串行避免打爆接口），结束只弹一个汇总提示
const aiCalcAllRows = async () => {
  const rows = batchSalaryRows.value.filter(r => !r.isNew && r.staffId)
  if (!rows.length) {
    ElMessage.warning('没有可测算的员工')
    return
  }
  batchAiAllLoading.value = true
  let ok = 0
  const failed = []
  try {
    for (const r of rows) {
      const success = await aiCalcBatchRow(r, true)
      if (success) ok++
      else failed.push(r.name || r.staffId)
    }
    if (failed.length) {
      ElMessage.success(`全员 AI 测算完成：成功 ${ok}/${rows.length} 人；${failed.length} 人无历史工资与业务量数据（${failed.join('、')}），请手动填写`)
    } else {
      ElMessage.success(`全员 AI 测算完成：${rows.length} 人全部测算成功，实发合计 ¥${batchTotalAmount.value.toFixed(2)}`)
    }
  } finally {
    batchAiAllLoading.value = false
  }
}
const buildBatchPayRows = (list) => list
  .filter(r => r.hasAccount !== false && batchRowNet(r) > 0 && String(r.staffId || '').trim() && String(r.name || '').trim())
  .map(r => ({
    staffId: String(r.staffId).trim(), name: String(r.name).trim(),
    baseSalary: Number(r.baseSalary) || 0, clinicCommission: Number(r.clinicCommission) || 0,
    plasterCommission: Number(r.plasterCommission) || 0, deductionSocial: Number(r.deductionSocial) || 0,
    tax: Number(r.tax) || 0
  }))
const doBatchPay = async (rows) => {
  distributeLoading.value = true
  try {
    const res = await axios.post('/api/assistant/salary/batch-pay', { month: salaryBoardMonth.value, rows })
    const d = res.data
    if (d && d.success) {
      const lines = (d.details || [])
        .map(x => `<div style="margin:2px 0;">${x.success ? '✅' : '❌'} <b>${x.name || x.staffId}</b>：${x.message || ''}</div>`)
        .join('')
      ElMessageBox.alert(lines || d.message, `发放完成（成功 ${d.successCount} 人 / 失败 ${d.failCount} 人，合计 ¥${Number(d.totalAmount || 0).toFixed(2)}）`, {
        dangerouslyUseHTMLString: true, confirmButtonText: '知道了'
      }).catch(() => {})
      await loadSalaryData()
      await loadBatchSalaryBoard()
    } else {
      ElMessage.error((d && d.message) || '批量发放失败')
    }
  } catch (e) {
    ElMessage.error('批量发放失败：' + (e.response?.data?.message || e.message))
  } finally {
    distributeLoading.value = false
  }
}
const submitBatchSalary = async (selectedList) => {
  const rows = buildBatchPayRows(selectedList || [])
  if (!rows.length) {
    ElMessage.warning('没有可发放的记录（需实发>0 且工号姓名齐全）')
    return
  }
  doBatchPay(rows)
}
const paySingleRow = async (row) => {
  const one = buildBatchPayRows([row])
  if (!one.length) {
    ElMessage.warning('该行实发金额为 0 或工号/姓名未填全')
    return
  }
  row._paying = true
  try {
    await doBatchPay(one)
  } finally {
    row._paying = false
  }
}

// 发放记录弹窗：关键字搜索 + 月份筛选（salaryMonth 兼容历史"YYYY-MM"与新的精确到天"YYYY-MM-DD"两种存法，按前缀匹配）
const showSlipHistoryDialog = ref(false)
const slipSearchKey = ref('')
const slipFilterMonth = ref('')
const slipMonthOptions = computed(() => {
  const set = new Set((salarySlips.value || []).map(s => String(s.salaryMonth || '').slice(0, 7)).filter(Boolean))
  return Array.from(set).sort().reverse()
})
const filteredSalarySlips = computed(() => {
  let list = displaySalarySlips.value
  if (slipFilterMonth.value) list = list.filter(s => String(s.salaryMonth || '').startsWith(slipFilterMonth.value))
  const key = slipSearchKey.value.trim().toLowerCase()
  if (key) {
    list = list.filter(s =>
      String(s.doctorId || '').toLowerCase().includes(key) ||
      String(s.doctorName || '').toLowerCase().includes(key))
  }
  return list
})

// 页面刷新后直接落在工资 tab 时 watch 不会触发，这里补一次初始加载（修复"进来空白，选月份才出数据"）
if (currentTab.value === 'salary' && currentUserRole.value !== 'DOCTOR' && currentUserRole.value !== 'MERCHANT') {
  loadBatchSalaryBoard()
}


const loadApprovals = async () => {
  try {
    const res = await axios.get('/api/assistant/approvals')
    approvals.value = res.data || []
  } catch (e) {}
}

const loadStaffRoles = async () => {
  try {
    const res = await axios.get('/api/analytics/staff-roles')
    staffRoles.value = res.data || []
  } catch (e) {}
}

// ── 角色与权限动态管理（医护账号与权限管理页）──
const roleOptions = [
  { value: 'ADMIN', label: 'ADMIN 管理员' },
  { value: 'HR', label: 'HR 人事' },
  { value: 'DOCTOR', label: 'DOCTOR 医生' },
  { value: 'NURSE', label: 'NURSE 护士' },
  { value: 'MERCHANT', label: 'MERCHANT 商户' }
]
const roleModuleOptions = [
  { key: 'registration', label: '📋 门诊挂号' },
  { key: 'clinic', label: '🩺 门诊接诊' },
  { key: 'billing', label: '💰 划价收费' },
  { key: 'treatment', label: '💉 特色执行站' },
  { key: 'pharmacy', label: '💊 智慧药房' },
  { key: 'patient', label: '📁 患者档案' },
  { key: 'ai-settings', label: '⚙️ 设置' }
]
// 管理中台模块（5174 侧栏菜单，与云诊所模块是两套独立权限）
const adminModuleOptions = [
  { key: 'analytics', label: '📊 经营分析大屏' },
  { key: 'mall-orders', label: '📦 商城订单履约' },
  { key: 'mall-products', label: '🛍️ 商城商品与进销存' },
  { key: 'mall-users', label: '👤 商城注册用户' },
  { key: 'salary', label: '💼 工资条管理' },
  { key: 'approval', label: '📑 OA请假审批' },
  { key: 'doctors', label: '👨‍⚕️ 医护账号与权限' }
]
const permRowsByScope = (list, scope) =>
  list.filter(r => String(r.scope || 'CLINIC').toUpperCase() === scope).map(r => ({
    role: r.role,
    roleLabel: r.roleLabel || r.role,
    scope: String(r.scope || 'CLINIC').toUpperCase(),
    modules: (() => { try { return JSON.parse(r.modulesJson || '[]') } catch (e) { return [] } })(),
    description: r.description || '',
    _saving: false
  }))
const rolePermRows = ref([])
const clinicPermRows = computed(() => permRowsByScope(rolePermRows.value, 'CLINIC'))
const adminPermRows = computed(() => permRowsByScope(rolePermRows.value, 'ADMIN'))
// 权限面板系统视角切换：同一份角色列表，切到云诊所/管理系统分别显示各自的模块配置
const permScopeView = ref('CLINIC')
const permRowsForView = computed(() => {
  // 云诊所视角下不展示无云诊所权限的角色（人事/商户等空配置行），只列真正使用云诊所的角色
  if (permScopeView.value === 'CLINIC') return clinicPermRows.value.filter(r => r.modules.length > 0)
  return adminPermRows.value
})
const permRowsEditable = (row) => {
  if (row.role === 'ADMIN') return false
  if (permScopeView.value === 'ADMIN') return true
  return Boolean(row.modules.length || row._editing)
}
const loadRolePermRows = async () => {
  try {
    const res = await axios.get('/api/role-permissions')
    rolePermRows.value = res.data || []
  } catch (e) {}
}
const saveRolePerm = async (row) => {
  row._saving = true
  try {
    const res = await axios.post('/api/admin/role-permissions/save', {
      role: row.role, roleLabel: row.roleLabel, scope: row.scope, modules: row.modules, description: row.description
    })
    if (res.data?.success) {
      ElMessage.success(res.data.message || '已保存')
      loadRolePermRows()
      loadStaffRoles()
      loadAdminRolePermissions()
    } else {
      ElMessage.error(res.data?.message || '保存失败')
    }
  } catch (e) {
    ElMessage.error('保存失败：' + (e.response?.data?.message || e.message))
  } finally { row._saving = false }
}
const handleStaffRoleRowChange = async (row, newRole) => {
  try {
    const res = await axios.post('/api/admin/staff/update-role', { staffId: row.staffId, role: newRole })
    if (res.data?.success) {
      ElMessage.success(res.data.message || '角色已更新')
    } else {
      ElMessage.error(res.data?.message || '角色更新失败')
    }
  } catch (e) {
    ElMessage.error('角色更新失败：' + (e.response?.data?.message || e.message))
  }
  loadStaffRoles()
}

// ── 统一注册账号（选角色即定权限，医生/护士/人事/商户/管理员一张表全收）──
const showRegisterUnifiedDialog = ref(false)
const registerUnifiedLoading = ref(false)
const newRegisterForm = ref({ role: 'DOCTOR', username: '', password: '123456', realName: '', phone: '', department: '', title: '' })
const registerRoleOptions = [
  { value: 'DOCTOR', label: '医生（云诊所全部模块 + 中台：大屏/工资条/请假）' },
  { value: 'NURSE', label: '护士（云诊所：挂号/划价/执行/药房/患者档案 + 中台：大屏/工资条/请假）' },
  { value: 'HR', label: '人事（仅管理中台：大屏/工资发放/审批/医护账号管理）' },
  { value: 'MERCHANT', label: '商户（仅管理中台：商城三模块 + 工资条/请假）' },
  { value: 'ADMIN', label: '系统最高管理员（两套系统全部权限）' }
]
const openRegisterUnified = () => {
  newRegisterForm.value = { role: 'DOCTOR', username: '', password: '123456', realName: '', phone: '', department: '', title: '' }
  showRegisterUnifiedDialog.value = true
}
const submitRegisterUnified = async () => {
  const f = newRegisterForm.value
  if (!f.username.trim() || !f.realName.trim()) {
    ElMessage.warning('登录账号与真实姓名为必填项')
    return
  }
  if (!f.phone.trim() || !/^1\d{10}$/.test(f.phone.trim())) {
    ElMessage.warning('请输入 1 开头的 11 位手机号')
    return
  }
  if (!f.department.trim() || !f.title.trim()) {
    ElMessage.warning('请选择部门/科室与岗位职称')
    return
  }
  registerUnifiedLoading.value = true
  try {
    const res = await axios.post('/api/admin/staff/register', {
      role: f.role, username: f.username.trim(), password: f.password.trim() || '123456',
      realName: f.realName.trim(), phone: f.phone.trim(), department: f.department.trim(), title: f.title.trim()
    })
    if (res.data?.success) {
      ElNotification({
        title: '账号注册成功！',
        message: res.data.message || `账号已创建，角色 ${f.role}，自动获得对应权限`,
        type: 'success'
      })
      showRegisterUnifiedDialog.value = false
      loadStaffRoles()
      loadStaffList()
      loadDoctorAccounts()
    } else {
      ElMessage.error(res.data?.message || '注册失败')
    }
  } catch (e) {
    ElMessage.error('注册失败：' + (e.response?.data?.message || e.message))
  } finally { registerUnifiedLoading.value = false }
}

// 医生管理
const loadDoctorAccounts = async () => {
  doctorLoading.value = true
  try {
    const res = await axios.get('/api/doctor/list')
    if (res.data?.success) doctorAccounts.value = res.data.data || []
  } catch (e) {} finally { doctorLoading.value = false }
}

const loadMallAdminProducts = async () => {
  mallLoading.value = true
  try {
    const res = await axios.get('/api/admin/mall/products')
    if (res.data?.success) mallProducts.value = res.data.data || []
  } catch (e) {} finally { mallLoading.value = false }
}

// ── 进销存：Excel 商品表批量导入（新商品建档上架；已存在同价库存累加；异价拒绝）──
const productImportInput = ref(null)
const triggerProductImport = () => {
  productImportInput.value && productImportInput.value.click()
}
const handleProductImport = async (e) => {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  if (!/\.(xlsx|xls)$/i.test(file.name)) {
    ElMessage.warning('请选择 Excel 商品表（xlsx / xls）')
    e.target.value = ''
    return
  }
  const fd = new FormData()
  fd.append('file', file)
  try {
    const res = await axios.post('/api/admin/mall/product/batch-import', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    const d = res.data
    if (d && d.success) {
      const lines = (d.details || [])
        .map(x => `<div style="margin:2px 0;">${x.success ? '✅' : '⛔'} <b>${x.productName || ''}</b>：${x.message || ''}</div>`)
        .join('')
      ElMessageBox.alert(lines || d.message, `商品表导入完成（新增 ${d.addedCount} / 累加 ${d.mergedCount} / 失败 ${d.failCount}）`, {
        dangerouslyUseHTMLString: true, confirmButtonText: '知道了'
      }).catch(() => {})
      loadMallAdminProducts()
    } else {
      ElMessage.error((d && d.message) || '商品表导入失败')
    }
  } catch (err) {
    ElMessage.error('导入失败：' + (err.response?.data?.message || err.message || '网络错误'))
  }
  e.target.value = ''
}

const handleToggleProductStatus = async (row) => {
  const nextStatus = row.status === 'OFF_SALE' ? 'ON_SALE' : 'OFF_SALE'
  try {
    const res = await axios.post('/api/admin/mall/product/status', { id: row.id, status: nextStatus })
    row.status = nextStatus
    ElMessage.success(res.data?.message || '状态已更新')
  } catch (e) { ElMessage.error('修改失败') }
}

const openPriceDialog = (row) => {
  editingProduct.value = row
  priceForm.value.retailGuidePrice = Number(row.retailGuidePrice) || 10
  priceForm.value.wholesalePrice = Number(row.wholesalePrice) || 5
  showPriceDialog.value = true
}

const submitPriceUpdate = async () => {
  try {
    await axios.post('/api/admin/mall/product/update-price', {
      id: editingProduct.value.id,
      retailGuidePrice: priceForm.value.retailGuidePrice,
      wholesalePrice: priceForm.value.wholesalePrice
    })
    ElMessage.success('售价调整成功')
    showPriceDialog.value = false
    loadMallAdminProducts()
  } catch (e) { ElMessage.error('调价失败') }
}

const openInboundDialog = (row) => {
  inboundProduct.value = row
  inboundQty.value = 100
  showInboundDialog.value = true
}

const submitInbound = async () => {
  try {
    const res = await axios.post('/api/admin/mall/product/inbound', {
      id: inboundProduct.value.id,
      quantity: inboundQty.value,
      operator: currentUserName.value
    })
    ElMessage.success(res.data?.message || '入库补货成功')
    showInboundDialog.value = false
    loadMallAdminProducts()
    loadAnalytics()
  } catch (e) { ElMessage.error('入库失败') }
}

const submitAddProduct = async () => {
  if (!newProductForm.value.productName.trim()) {
    ElMessage.warning('请输入商品名称')
    return
  }
  try {
    await axios.post('/api/admin/mall/product/save', newProductForm.value)
    ElMessage.success('商品档案新增成功，已上架至便民商城')
    showAddProductDialog.value = false
    newProductForm.value = {
      productName: '',
      category: '',
      specification: '',
      manufacturer: '',
      retailGuidePrice: null,
      wholesalePrice: null,
      stock: null,
      imageUrl: ''
    }
    loadMallAdminProducts()
  } catch (e) { ElMessage.error(e.response?.data?.message || '新增失败') }
}

// 商城用户管理
const loadMallUsers = async () => {
  mallUsersLoading.value = true
  try {
    const res = await axios.get('/api/admin/mall/users')
    if (res.data?.success) mallUsers.value = res.data.data || []
  } catch (e) {} finally { mallUsersLoading.value = false }
}

const handleToggleMallUserStatus = async (row) => {
  const nextStatus = row.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
  try {
    await axios.post('/api/admin/mall/user/status', { id: row.id, status: nextStatus })
    row.status = nextStatus
    ElMessage.success(nextStatus === 'ENABLE' ? '用户账号已解冻恢复' : '用户账号已冻结')
  } catch (e) { ElMessage.error('操作失败') }
}

const viewUserOrders = async (row) => {
  try {
    // 优先传登录账号（唯一），后端据此定位用户后按 昵称+手机号 like 匹配订单
    const kw = row.username || row.nickname || ''
    const res = await axios.get('/api/admin/mall/user/orders?buyerName=' + encodeURIComponent(kw))
    userOrders.value = res.data?.data || []
    showUserOrderDrawer.value = true
  } catch (e) { ElMessage.error('获取订单失败') }
}

// ── 新增商城用户（注册即可在春播商城登录，自动发 ¥200 体验金）──
const showAddMallUserDialog = ref(false)
const addMallUserLoading = ref(false)
const newMallUserForm = ref({ username: '', password: '123456', nickname: '', phone: '', address: '' })
const openAddMallUserDialog = () => {
  newMallUserForm.value = { username: '', password: '123456', nickname: '', phone: '', address: '' }
  showAddMallUserDialog.value = true
}
const submitAddMallUser = async () => {
  const f = newMallUserForm.value
  if (!f.username.trim() || !f.password.trim() || !f.nickname.trim() || !f.phone.trim()) {
    ElMessage.warning('登录账号、密码、姓名/称呼、手机号均为必填项')
    return
  }
  if (f.password.trim().length < 6) {
    ElMessage.warning('登录密码长度至少 6 位')
    return
  }
  if (!/^1\d{10}$/.test(f.phone.trim())) {
    ElMessage.warning('请输入 1 开头的 11 位手机号')
    return
  }
  addMallUserLoading.value = true
  try {
    const res = await axios.post('/api/mall/user/register', {
      username: f.username.trim(),
      password: f.password.trim(),
      nickname: f.nickname.trim(),
      phone: f.phone.trim(),
      address: f.address.trim()
    })
    if (res.data?.success) {
      ElNotification({
        title: '商城用户注册成功！',
        message: `账号 [${f.username.trim()}] 已创建，自动发放 ¥200 新人体验金，可直接登录春播商城。`,
        type: 'success'
      })
      showAddMallUserDialog.value = false
      loadMallUsers()
    } else {
      ElMessage.error(res.data?.message || '注册失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '注册失败')
  } finally { addMallUserLoading.value = false }
}

// 人事管理 (Admin 专属)
const loadStaffList = async () => {
  staffLoading.value = true
  try {
    const res = await axios.get('/api/admin/staff/list')
    if (res.data?.success) allStaffList.value = res.data.data || []
  } catch (e) {} finally { staffLoading.value = false }
}

const askAssistant = (query) => {
  inputQuery.value = query
  sendAssistantQuery()
}

// ── 附件上传（工资表图片 / Excel）→ AI 提取表格 + function-calling 发工资 ──
const triggerFileUpload = () => {
  chatFileInput.value && chatFileInput.value.click()
}
const handleFileSelect = async (e) => {
  const file = e.target.files && e.target.files[0]
  if (!file) return
  const isImage = file.type.startsWith('image/')
  const isExcel = /\.(xlsx|xls)$/i.test(file.name)
  if (!isImage && !isExcel) {
    ElMessage.warning('请选择图片（jpg/png）或 Excel（xlsx/xls）工资表')
    return
  }
  const previewUrl = isImage ? URL.createObjectURL(file) : ''
  const fd = new FormData()
  fd.append('file', file)
  try {
    const resp = await fetch('/api/upload/file', { method: 'POST', headers: { 'Authorization': 'Bearer ' + localStorage.getItem('chunbo_admin_token') }, body: fd })
    const data = await resp.json()
    if (data && data.success) {
      // 图片用持久 URL（/uploads/attachments/xxx，重启后历史图片不裂）；Excel 带后端解析好的 Markdown 表格预览
      const persistentUrl = data.url || previewUrl
      if (previewUrl) URL.revokeObjectURL(previewUrl)
      chatAttachment.value = {
        fileId: data.fileId, fileName: file.name, fileType: data.fileType,
        previewUrl: persistentUrl, excelPreview: data.excelPreview || '',
        sizeLabel: formatFileSize(file.size)
      }
      ElMessage.success(isExcel ? 'Excel 已上传，发送后将提取表格并发工资' : '图片已上传，发送后将识别表格')
    } else {
      if (previewUrl) URL.revokeObjectURL(previewUrl)
      ElMessage.error('上传失败：' + (data && data.message ? data.message : '未知错误'))
    }
  } catch (err) {
    if (previewUrl) URL.revokeObjectURL(previewUrl)
    ElMessage.error('上传失败：' + (err.message || '网络错误'))
  }
  e.target.value = ''
}
const removeAttachment = () => {
  chatAttachment.value = null
}
const formatFileSize = (bytes) => {
  const b = Number(bytes) || 0
  if (b >= 1024 * 1024) return (b / 1024 / 1024).toFixed(2) + 'MB'
  if (b >= 1024) return (b / 1024).toFixed(2) + 'KB'
  return b + 'B'
}

const sendAssistantQuery = async () => {
  const text = inputQuery.value.trim()
  // 附件先取出（原先在发请求前就被 removeAttachment 清掉，导致 attachmentId 永远传不到后端）
  const att = chatAttachment.value
    ? { fileId: chatAttachment.value.fileId, fileName: chatAttachment.value.fileName, fileType: chatAttachment.value.fileType, previewUrl: chatAttachment.value.previewUrl, excelPreview: chatAttachment.value.excelPreview || '', sizeLabel: chatAttachment.value.sizeLabel || '' }
    : null
  if ((!text && !att) || chatLoading.value) return
  const sendText = text || '请识别并处理我上传的工资表文件'

  // 压入用户消息：附件用文件卡片渲染（气泡顶部），不再重复展示提取出的表格数据（发放结果里 AI 会汇总）
  chatMessages.value.push({
    role: 'user',
    content: sendText,
    image: att && att.fileType === 'image' ? att.previewUrl : undefined,
    attachment: att ? { fileName: att.fileName, fileType: att.fileType, sizeLabel: att.sizeLabel, previewUrl: att.previewUrl } : undefined
  })
  chatAttachment.value = null
  // 首个用户问题自动生成会话标题
  const curSess = adminSessionList.value.find(s => s.id === currentAdminSessionId.value)
  if (curSess) {
    const hadUserMsg = (curSess.messages || []).some(m => m.role === 'user')
    if (!hadUserMsg || curSess.title === '新建调度会话' || curSess.title === '全中台运营调度主会话') {
      curSess.title = sendText.length > 14 ? sendText.slice(0, 14) + '…' : sendText
    }
  }
  saveUserChatHistory()
  inputQuery.value = ''
  chatLoading.value = true

  // 压入一条空白的 assistant 消息用于流式拼接
  const assistantMsgIndex = chatMessages.value.length
  chatMessages.value.push({ role: 'assistant', content: '' })

  nextTick(() => {
    if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
  })

  // 建立 SSE 流式连接（fetch + 鉴权 + 可中断）
  const abort = new AbortController()
  assistantAbort = abort
  assistantActiveSessionId = currentAdminSessionId.value || ('OA_S_' + Date.now())
  const attachParam = att && att.fileId ? `&attachmentId=${encodeURIComponent(att.fileId)}&fileName=${encodeURIComponent(att.fileName || '')}` : ''
  const sseUrl = `/api/assistant/chat/stream?message=${encodeURIComponent(sendText)}&userId=${encodeURIComponent(currentUserStaffId.value)}&userRole=${encodeURIComponent(currentUserRole.value)}&userName=${encodeURIComponent(currentUserName.value)}&sessionId=${encodeURIComponent(assistantActiveSessionId)}${attachParam}`
  try {
    const resp = await fetch(sseUrl, {
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('chunbo_admin_token') },
      signal: abort.signal
    })
    if (!resp.ok || !resp.body) throw new Error('HTTP ' + resp.status)
    const reader = resp.body.getReader()
    const dec = new TextDecoder('utf-8')
    let buf = ''
    // 平滑流式渲染：token 先入缓冲，固定间隔吐出
    let pendingText = ''
    const renderTimer = setInterval(() => {
      if (pendingText.length > 0) {
        const take = Math.max(2, Math.ceil(pendingText.length / 6))
        chatMessages.value[assistantMsgIndex].content += pendingText.slice(0, take)
        pendingText = pendingText.slice(take)
        nextTick(() => {
          if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
        })
      }
    }, 30)
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
        }
        // eventType 1002 (STOP) / 1003 (PARAM) / 1004 (PROCESS) 中台助手无卡片，忽略
      }
    }
    clearInterval(renderTimer)
    chatMessages.value[assistantMsgIndex].content += pendingText
    pendingText = ''
  } catch (e) {
    if (e.name !== 'AbortError' && !chatMessages.value[assistantMsgIndex].content) {
      // 流式失败且无内容，使用普通 POST 接口兜底
      try {
        const res = await axios.post('/api/assistant/chat', {
          message: text,
          doctorId: currentUserStaffId.value,
          userId: currentUserStaffId.value,
          userRole: currentUserRole.value,
          userName: currentUserName.value
        })
        chatMessages.value[assistantMsgIndex].content = res.data
      } catch (e2) {
        chatMessages.value[assistantMsgIndex].content = '服务响应异常，请稍后重试。'
      }
    }
  } finally {
    if (assistantAbort === abort) assistantAbort = null
    chatLoading.value = false
    saveUserChatHistory()
    // 工资类指令执行完后自动刷新工资条台账，让发放结果立即在「工资条管理」页面可见
    if (/(工资|薪资|薪酬|发薪|工资条)/.test(sendText)) loadSalaryData()
    nextTick(() => {
      if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
    })
  }
}

// ── 停止 AI 调度生成（后端终止 Flux 流 + 前端断开 SSE，参照《SpringAI》笔记标准实现） ──
let assistantAbort = null
let assistantActiveSessionId = null
const stopAssistantGeneration = () => {
  // 先通知后端终止 Flux 输出（takeWhile 检测标记后中断）
  if (assistantActiveSessionId) {
    fetch(`/api/assistant/chat/stop?sessionId=${encodeURIComponent(assistantActiveSessionId)}`, {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('chunbo_admin_token') }
    }).catch(() => {})
  }
  if (assistantAbort) {
    assistantAbort.abort()
    assistantAbort = null
  }
  assistantActiveSessionId = null
  chatLoading.value = false
  const last = [...chatMessages.value].reverse().find(m => m.role === 'assistant')
  if (last && !last.content) last.content = '（已停止生成）'
  saveUserChatHistory()
}

// ── 语音能力：语音录入（ASR）与朗读回答（TTS） ──
const isRecordingAdmin = ref(false)
let adminMediaRecorder = null
let adminAudioChunks = []
let adminRecordStartAt = 0

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

const toggleVoiceInputAdmin = async () => {
  if (isRecordingAdmin.value) {
    if (adminMediaRecorder) { try { adminMediaRecorder.stop() } catch (e) {} }
    return
  }
  try {
    // 先自动选麦（跳过无信号的"哑巴"设备），再开正式录音流
    const micId = await pickBestMic()
    const stream = await navigator.mediaDevices.getUserMedia({ audio: micId ? { deviceId: { exact: micId } } : true })
    // WebAudio 处理链：音量放大（自适应AGC）+压限器，解决耳机麦克风采集音量过低
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
    const mimeType = MediaRecorder.isTypeSupported('audio/webm;codecs=opus') ? 'audio/webm;codecs=opus' : (MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : '')
    adminMediaRecorder = mimeType ? new MediaRecorder(recStream, { mimeType }) : new MediaRecorder(recStream)
    adminAudioChunks = []
    adminRecordStartAt = Date.now()
    adminMediaRecorder.ondataavailable = ev => { if (ev.data && ev.data.size) adminAudioChunks.push(ev.data) }
    adminMediaRecorder.onstop = async () => {
      isRecordingAdmin.value = false
      const recordedMs = Date.now() - adminRecordStartAt
      stream.getTracks().forEach(t => t.stop())
      if (levelTimer) { clearInterval(levelTimer); levelTimer = null }
      if (audioCtx) { try { audioCtx.close() } catch (e) {} audioCtx = null }
      if (recordedMs < 800) {
        ElMessage.warning('说话时间太短，请说完一句再结束')
        return
      }
      if (!adminAudioChunks.length) {
        ElMessage.warning('录音数据为空，请重试')
        return
      }
      const blob = new Blob(adminAudioChunks, { type: adminMediaRecorder.mimeType || 'audio/webm' })
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
      const fd = new FormData()
      fd.append('file', blob, 'voice.webm')
      ElMessage.info('正在识别语音…')
      try {
        const resp = await fetch('/api/audio/asr', {
          method: 'POST',
          headers: { 'Authorization': 'Bearer ' + localStorage.getItem('chunbo_admin_token') },
          body: fd
        })
        const data = await resp.json()
        const txt = (data && (data.text || data.result)) || ''
        if (txt && /[\u4e00-\u9fa5]/.test(txt)) { inputQuery.value += txt; ElMessage.success('语音识别完成，已填入输入框') }
        else ElMessage.warning((data && data.message) || '未识别到清晰的中文语音，请靠近麦克风大声说一句再结束')
      } catch (e) {
        ElMessage.error('语音识别失败：' + (e.message || '网络错误'))
      }
    }
    adminMediaRecorder.start()
    isRecordingAdmin.value = true
    ElMessage.info('开始录音，说完点击 ⏹ 结束')
  } catch (e) {
    ElMessage.error('无法访问麦克风，请检查浏览器权限')
  }
}

let adminTtsAudio = null
const speakAdminMessage = async (msg) => {
  if (adminTtsAudio) { adminTtsAudio.pause(); adminTtsAudio = null; msg._speaking = false; return }
  try {
    const plain = String(msg.content || '').replace(/[#*>`|_~-]/g, '').replace(/\n+/g, ' ').trim().slice(0, 400)
    if (!plain) return
    const resp = await fetch('/api/audio/tts-stream', {
      method: 'POST',
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('chunbo_admin_token'), 'Content-Type': 'text/plain' },
      body: plain
    })
    if (!resp.ok) throw new Error('HTTP ' + resp.status)
    const blob = await resp.blob()
    adminTtsAudio = new Audio(URL.createObjectURL(blob))
    msg._speaking = true
    adminTtsAudio.onended = () => { msg._speaking = false; adminTtsAudio = null }
    adminTtsAudio.play()
  } catch (e) {
    ElMessage.error('语音合成失败：' + (e.message || '网络错误'))
  }
}

// ── AI 文字助手（通用文本模型：帮写/续写/润色/精简/联想词） ──
const showTextAssistant = ref(false)
const textTemplates = ref([])
const textAssistantType = ref('polish')
const textAssistantInput = ref('')
const textAssistantResult = ref('')
const textAssistantLoading = ref(false)

const openTextAssistant = async () => {
  showTextAssistant.value = true
  textAssistantResult.value = ''
  // 后端 5 个模板 type 键（与 /api/text/process 的 type 参数一致）
  textTemplates.value = [
    { key: 'associationalWord', name: '联想词' },
    { key: 'helpedWrite', name: '帮写' },
    { key: 'continuedWrite', name: '续写' },
    { key: 'polish', name: '润色' },
    { key: 'streamline', name: '精简' }
  ]
}

const runTextAssistant = async () => {
  if (!textAssistantInput.value.trim()) { ElMessage.warning('请先输入待处理文本'); return }
  textAssistantLoading.value = true
  textAssistantResult.value = ''
  try {
    const res = await axios.post('/api/text/process', {
      type: textAssistantType.value,
      input: textAssistantInput.value
    }, {
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('chunbo_admin_token') }
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

const submitLeave = async () => {
  try {
    const payload = {
      applicantName: currentUserName.value,
      applicantId: currentUserStaffId.value,
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
      approver: currentUserName.value + ' (' + currentRoleLabel.value + ')',
      comment: action === '已通过' ? '准假，已同步门诊排班。' : '当前门诊高峰，暂缓休假。'
    })
    ElMessage.success(`已标记为【${action}】`)
    loadApprovals()
  } catch (e) { ElMessage.error('审批失败') }
}



// ==============================================
// 商城订单管理与发货状态
// ==============================================
const mallOrdersList = ref([])
const showShipDialog = ref(false)
const currentShippingOrder = ref(null)
const shippingTrackingNo = ref('')
const shippingLoading = ref(false)
const orderFilterStatus = ref('ALL')

const filteredMallOrders = computed(() => {
  if (orderFilterStatus.value === 'PENDING') {
    return mallOrdersList.value.filter(o => !o.status || !o.status.includes('已发货'))
  }
  if (orderFilterStatus.value === 'SHIPPED') {
    return mallOrdersList.value.filter(o => o.status && o.status.includes('已发货'))
  }
  return mallOrdersList.value
})

const loadMallOrders = async () => {
  try {
    const res = await axios.get('/api/admin/mall/user/orders')
    mallOrdersList.value = res.data?.data || []
  } catch (e) {    console.error("加载商城订单失败", e)
  }
}

const pendingShipCount = computed(() => {
  return mallOrdersList.value.filter(o => !o.status || !o.status.includes('已发货')).length
})

const shippedCount = computed(() => {
  return mallOrdersList.value.filter(o => o.status && o.status.includes('已发货')).length
})

const totalMallOrderRevenue = computed(() => {
  return mallOrdersList.value.reduce((sum, o) => sum + Number(o.finalAmount || 0), 0)
})

const parseOrderItems = (itemsJson) => {
  if (!itemsJson) return []
  try {
    return JSON.parse(itemsJson)
  } catch (e) {
    return [{ productName: '生活药品组合', quantity: 1 }]
  }
}

const openShipConfirmModal = (order) => {
  currentShippingOrder.value = order
  shippingTrackingNo.value = 'CB' + Date.now().toString().slice(-10)
  showShipDialog.value = true
}

const confirmShipOrder = async () => {
  if (!currentShippingOrder.value) return
  shippingLoading.value = true
  try {
    const res = await axios.post('/api/admin/mall/order/ship', {
      orderNo: currentShippingOrder.value.orderNo,
      trackingNo: shippingTrackingNo.value,
      operator: currentUserName.value + ' (' + currentRoleLabel.value + ')'
    })
    if (res.data && res.data.success) {
      ElNotification({
        title: '🎉 订单发货出库成功',
        message: `春播便民速递运单号【${res.data.trackingNo}】，库存已实时扣减并生成出库台账！`,
        type: 'success',
        duration: 5000
      })
      showShipDialog.value = false
      await loadMallOrders()
      await loadMallAdminProducts()
      await loadAnalytics()
    } else {
      ElMessage.error(res.data?.message || '发货失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '发货出库遇到错误')
  } finally {
    shippingLoading.value = false
  }
}

// ==============================================
// 全员薪酬发放与 AI 智能测算状态
// ==============================================
const showIssueSalaryDialog = ref(false)
const staffCandidates = ref([])
const selectedCandidateStaffId = ref('')
const selectedCandidateRole = ref('DOCTOR')
const aiCalcLoading = ref(false)
const distributeLoading = ref(false)
const aiMetricsBasis = ref(null)
const commLabel1 = ref('门诊诊疗与开方提成')
const commLabel2 = ref('特色穴位贴敷理疗绩效')

const salaryDistForm = ref({
  staffId: '',
  name: '',
  role: '',
  month: '',
  baseSalary: 0,
  clinicCommission: 0,
  plasterCommission: 0,
  deductionSocial: 0,
  tax: 0,
  netSalary: 0,
  aiComment: ''
})

const computedNetSalary = computed(() => {
  const f = salaryDistForm.value
  return (Number(f.baseSalary || 0) + Number(f.clinicCommission || 0) + Number(f.plasterCommission || 0) - Number(f.deductionSocial || 0) - Number(f.tax || 0))
})

const recalcNetSalary = () => {
  salaryDistForm.value.netSalary = computedNetSalary.value
}

const getCandidateRoleLabel = (role) => {
  if (role === 'MERCHANT') return '商城商户'
  if (role === 'HR') return '人事主管'
  if (role === 'DOCTOR') return '门诊医生'
  return '员工'
}

const loadStaffCandidates = async () => {
  try {
    const res = await axios.get('/api/assistant/salary/staff-candidates')
    staffCandidates.value = res.data || []
  } catch (e) {}
}

const handleSelectSalaryCandidate = (staffId) => {
  const c = staffCandidates.value.find(item => item.staffId === staffId)
  if (c) {
    salaryDistForm.value.staffId = c.staffId
    salaryDistForm.value.name = c.realName
    salaryDistForm.value.role = c.role
    selectedCandidateRole.value = c.role
    if (c.role === 'MERCHANT') {
      commLabel1.value = '商城电商订单履约提成'
      commLabel2.value = '供应链出库与运营奖励'
    } else if (c.role === 'HR') {
      commLabel1.value = '全院人事考勤与绩效管理'
      commLabel2.value = 'OA审批闭环与制度合规奖'
    } else {
      commLabel1.value = '门诊诊疗与开方提成'
      commLabel2.value = '特色穴位贴敷理疗绩效'
    }
    // 自动触发基于真实业务数据的 AI 智能测算
    handleAiCalculateSalary()
  }
}

const handleAiCalculateSalary = async () => {
  if (!salaryDistForm.value.staffId) {
    ElMessage.warning('请先选择发薪员工！')
    return
  }
  aiCalcLoading.value = true
  try {
    const res = await axios.post('/api/assistant/salary/ai-calculate', {
      staffId: salaryDistForm.value.staffId,
      name: salaryDistForm.value.name,
      role: salaryDistForm.value.role,
      month: salaryDistForm.value.month || '2026-09'
    })
    const d = res.data
    salaryDistForm.value.baseSalary = d.baseSalary
    salaryDistForm.value.clinicCommission = d.clinicCommission
    salaryDistForm.value.plasterCommission = d.plasterCommission
    salaryDistForm.value.deductionSocial = d.deductionSocial
    salaryDistForm.value.tax = d.tax
    salaryDistForm.value.netSalary = d.netSalary
    salaryDistForm.value.aiComment = d.aiComment
    aiMetricsBasis.value = d.metricsBasis || null
    if (d.commLabel1) commLabel1.value = d.commLabel1
    if (d.commLabel2) commLabel2.value = d.commLabel2

    ElNotification({
      title: '🤖 AI 智能测算完成',
      message: `已结合真实业务数据完成 ${salaryDistForm.value.name} (${getCandidateRoleLabel(salaryDistForm.value.role)}) 薪酬与绩效综合核准！`,
      type: 'success',
      duration: 4500
    })
  } catch (e) {
    ElMessage.error('AI 测算失败，请重试！')
  } finally {
    aiCalcLoading.value = false
  }
}


const confirmDeliverOrder = async (order) => {
  if (!order || !order.orderNo) return
  try {
    const res = await axios.post('/api/admin/mall/order/deliver', { orderNo: order.orderNo })
    if (res.data && res.data.success) {
      ElNotification({
        title: '🎉 订单已送达妥投',
        message: res.data.message || `订单【${order.orderNo}】已确认由居民成功签收！`,
        type: 'success',
        duration: 4500
      })
      await loadMallOrders()
    } else {
      ElMessage.error(res.data?.message || '确认送达操作失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '确认送达操作遇到异常')
  }
}

const submitDistributeSalary = async () => {
  if (!salaryDistForm.value.staffId) {
    ElMessage.warning('请选择发薪员工')
    return
  }
  recalcNetSalary()
  distributeLoading.value = true
  try {
    const payload = {
      staffId: salaryDistForm.value.staffId,
      name: salaryDistForm.value.name,
      month: salaryDistForm.value.month,
      baseSalary: salaryDistForm.value.baseSalary,
      clinicCommission: salaryDistForm.value.clinicCommission,
      plasterCommission: salaryDistForm.value.plasterCommission,
      deductionSocial: salaryDistForm.value.deductionSocial,
      tax: salaryDistForm.value.tax,
      netSalary: salaryDistForm.value.netSalary,
      remark: salaryDistForm.value.aiComment
    }
    const res = await axios.post('/api/assistant/salary/distribute', payload)
    if (res.data && res.data.success) {
      ElNotification({
        title: '🎉 工资条正式发放成功',
        message: res.data.message,
        type: 'success',
        duration: 5000
      })
      showIssueSalaryDialog.value = false
      await loadSalaryData()
    } else {
      ElMessage.error(res.data?.message || '发放失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '发放工资条遇到错误')
  } finally {
    distributeLoading.value = false
  }
}


const showAddMerchantModal = ref(false)
const merchantSubmitting = ref(false)
const merchantForm = ref({
  username: '',
  password: 'password123',
  realName: '',
  phone: ''
})

const openAddMerchantDialog = () => {
  merchantForm.value = {
    username: 'merchant_' + Date.now().toString().slice(-4),
    password: 'password123',
    realName: '',
    phone: ''
  }
  showAddMerchantModal.value = true
}

const submitRegisterMerchant = async () => {
  if (!merchantForm.value.username || !merchantForm.value.realName) {
    ElMessage.warning('请填写账号和商户真实姓名')
    return
  }
  merchantSubmitting.value = true
  try {
    const res = await axios.post('/api/admin/merchant/register', merchantForm.value)
    if (res.data && res.data.success) {
      ElNotification({
        title: '注册成功',
        message: res.data.message,
        type: 'success'
      })
      showAddMerchantModal.value = false
      await loadStaffList()
      await loadStaffCandidates()
    } else {
      ElMessage.error(res.data?.message || '注册失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '注册商户遇到错误')
  } finally {
    merchantSubmitting.value = false
  }
}

// ── 通用列表搜索/分页工厂（客户端分页，叠加在既有筛选之上）──
const makeListPager = (sourceRef, fields) => {
  const state = reactive({ search: '', page: 1, size: 10 })
  const searched = computed(() => {
    const key = state.search.trim().toLowerCase()
    const list = sourceRef.value || []
    if (!key) return list
    return list.filter(item => fields.some(f => String(item[f] ?? '').toLowerCase().includes(key)))
  })
  const paged = computed(() => searched.value.slice((state.page - 1) * state.size, state.page * state.size))
  const total = computed(() => searched.value.length)
  watch(() => state.search, () => { state.page = 1 })
  return reactive({ state, searched, paged, total })
}

const ordersPager = makeListPager(filteredMallOrders, ['orderNo', 'buyerName', 'status', 'clinicName'])
const productsPager = makeListPager(mallProducts, ['productName', 'category', 'specification', 'manufacturer', 'status'])
const usersPager = makeListPager(mallUsers, ['username', 'phone', 'status'])
const doctorsPager = makeListPager(doctorAccounts, ['username', 'doctorName', 'department', 'title', 'phone'])
const staffRolesPager = makeListPager(staffRoles, ['staffId', 'name', 'title', 'department', 'role'])
const approvalsPager = makeListPager(approvals, ['applicantName', 'leaveType', 'status', 'reason'])
const myApprovalsPager = makeListPager(displayApprovals, ['applicantName', 'approvalType', 'status', 'reason'])
const slipsPager = makeListPager(filteredSalarySlips, ['doctorId', 'doctorName', 'salaryMonth'])
const staffListPager = makeListPager(allStaffList, ['staffId', 'realName', 'role', 'department', 'status'])
const salaryBoardPager = makeListPager(batchSalaryRows, ['staffId', 'name', 'roleLabel', 'role', 'source'])
// 医护账号统一总台账（后端 /staff-roles 已做医生表+员工表去重合并）
const unifiedPager = makeListPager(staffRoles, ['staffId', 'username', 'name', 'department', 'title', 'role', 'permissions'])
const unifiedSelection = ref([])
// 统一行操作：按 source 分派到医生表/员工表接口
const toggleUnifiedStatus = async (row) => {
  const nextStatus = row.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
  try {
    if (row.source === 'doctor') {
      await axios.post('/api/doctor/update-status', { id: row.id, status: nextStatus })
    } else {
      await axios.post('/api/admin/staff/status', { id: row.id, status: nextStatus })
    }
    row.status = nextStatus
    ElMessage.success(nextStatus === 'ENABLE' ? '账号已启用' : '账号已停用')
  } catch (e) { ElMessage.error('操作失败') }
}
const resetUnifiedPassword = async (row) => {
  ElMessageBox.confirm(`确定将 [${row.name}] 的密码重置为 123456 吗？`, '重置确认', { type: 'warning' })
    .then(async () => {
      const res = row.source === 'doctor'
        ? await axios.post('/api/doctor/reset-password', { id: row.id, newPassword: '123456' })
        : await axios.post('/api/admin/staff/reset-password', { id: row.id, newPassword: '123456' })
      ElMessage.success(res.data?.message || '密码已重置为 123456')
    }).catch(() => {})
}
const deleteUnifiedAccounts = async (rows) => {
  if (!rows || !rows.length) {
    ElMessage.warning('请先勾选要删除的账号')
    return
  }
  const names = rows.map(r => `${r.name || r.username}(${r.staffId})`).join('、')
  try {
    await ElMessageBox.confirm(
      `确定删除：${names}？删除后该账号将无法登录对应系统，操作不可恢复。`,
      '删除账号确认',
      { type: 'danger', confirmButtonText: '确认删除', cancelButtonText: '取消' }
    )
  } catch (e) { return }
  let ok = 0
  const fail = []
  for (const r of rows) {
    try {
      if (r.source === 'doctor') await axios.delete('/api/doctor/' + r.id)
      else await axios.delete('/api/admin/staff/' + r.id)
      ok++
    } catch (e) { fail.push(r.name || r.staffId) }
  }
  ElMessage.success(`已删除 ${ok} 个账号${fail.length ? '；失败：' + fail.join('、') : ''}`)
  loadStaffRoles()
  loadDoctorAccounts()
  loadStaffList()
}

// 各页多选勾选
const orderSelection = ref([])
const productSelection = ref([])
const userSelection = ref([])
const doctorSelection = ref([])
const staffRoleSelection = ref([])
const approvalSelection = ref([])
const slipSelection = ref([])
const staffListSelection = ref([])

// 通用批量删除（singleMode=true 时按 url/{id} 逐条删除）
const batchDeleteRows = async (url, ids, label, reload, singleMode = false) => {
  if (!ids || !ids.length) {
    ElMessage.warning('请先勾选要删除的记录')
    return
  }
  ElMessageBox.confirm(`确定删除选中的 ${ids.length} 条${label}？删除后不可恢复。`, '删除确认', {
    type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消'
  }).then(async () => {
    try {
      if (singleMode) {
        for (const id of ids) await axios.delete(url + '/' + id)
        ElMessage.success(`已删除 ${ids.length} 条${label}`)
      } else {
        const res = await axios.post(url, { ids })
        if (!res.data?.success) {
          ElMessage.error(res.data?.message || '删除失败')
          return
        }
        ElMessage.success(res.data.message || `已删除 ${ids.length} 条${label}`)
      }
      if (reload) reload()
    } catch (e) {
      ElMessage.error('删除失败：' + (e.response?.data?.message || e.message))
    }
  }).catch(() => {})
}

// OA 审批：单条批准/驳回
const processApprovalRow = async (row, status) => {
  try {
    const res = await axios.post('/api/assistant/approvals/process', {
      id: row.id,
      status,
      approver: currentUserName.value,
      comment: status === '已驳回' ? '请假事由不符合规范，请调整后重新申请' : '情况属实，同意请假'
    })
    if (res.data?.success) {
      ElMessage.success(res.data.message)
      loadApprovals()
    } else {
      ElMessage.error(res.data?.message || '审批处理失败')
    }
  } catch (e) {
    ElMessage.error('审批处理失败：' + (e.response?.data?.message || e.message))
  }
}

// 员工角色矩阵页删除：RBAC 聚合视图无主键，按 staffId 回查员工账号表主键后删除
const deleteStaffRoles = async () => {
  if (!staffRoleSelection.value.length) {
    ElMessage.warning('请先勾选要删除的记录')
    return
  }
  ElMessageBox.confirm(`确定删除选中的 ${staffRoleSelection.value.length} 名员工账号？删除后不可恢复。`, '删除确认', {
    type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消'
  }).then(async () => {
    try {
      let n = 0
      for (const row of staffRoleSelection.value) {
        const hit = allStaffList.value.find(s => String(s.staffId || '').toLowerCase() === String(row.staffId || '').toLowerCase())
        if (hit) {
          await axios.delete('/api/admin/staff/' + hit.id)
          n++
        }
      }
      ElMessage.success(`已删除 ${n} 个员工账号`)
      loadStaffList()
      loadStaffRoles()
    } catch (e) {
      ElMessage.error('删除失败：' + (e.response?.data?.message || e.message))
    }
  }).catch(() => {})
}
</script>

<style>
* { box-sizing: border-box; margin: 0; padding: 0; }
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
  background-color: #0f172a;
  color: #334155;
}

#admin-app {
  display: flex;
  flex-direction: column;
  height: 100vh;
  overflow: hidden;
  background: #f8fafc;
}

/* 登录大屏 */
.admin-login-screen {
  min-height: 100vh;
  background: radial-gradient(circle at 10% 20%, #064e3b 0%, #022c22 45%, #051311 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: 20px;
}
.login-bg-glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(100px);
  opacity: 0.3;
  pointer-events: none;
}
.glow-1 { width: 500px; height: 500px; background: #10b981; top: -100px; left: -100px; }
.glow-2 { width: 450px; height: 450px; background: #0ea5e9; bottom: -80px; right: -80px; }

.login-modal-box {
  width: 100%;
  max-width: 460px;
  background: rgba(15, 34, 28, 0.85);
  backdrop-filter: blur(30px);
  -webkit-backdrop-filter: blur(30px);
  border: 1px solid rgba(52, 211, 153, 0.25);
  border-radius: 24px;
  padding: 44px 38px 30px;
  box-shadow: 0 30px 70px rgba(0,0,0,0.6), 0 0 30px rgba(16,185,129,0.15);
  position: relative;
  z-index: 10;
}

.login-brand { display: flex; align-items: center; gap: 14px; margin-bottom: 24px; }
.brand-logo-icon { font-size: 38px; }
.brand-main-title { font-size: 19px; font-weight: 700; color: #ecfdf5; margin-bottom: 4px; }
.brand-sub-title { font-size: 12px; color: #a7f3d0; opacity: 0.85; }

.login-tab-title { display: flex; align-items: center; justify-content: space-between; margin-bottom: 20px; }
.login-tab-title h3 { color: #fff; font-size: 20px; margin: 0; }
.version-tag { background: rgba(16, 185, 129, 0.2); border: 1px solid #10b981; color: #a7f3d0; font-size: 11px; padding: 2px 8px; border-radius: 6px; }

.role-quick-selector {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  background: rgba(2, 44, 34, 0.6);
  border: 1px dashed rgba(52, 211, 153, 0.4);
  padding: 8px 12px;
  border-radius: 10px;
  margin-bottom: 20px;
}
.quick-label { font-size: 12px; color: #94a3b8; }
.quick-tag { cursor: pointer; transition: transform 0.15s; }
.quick-tag:hover { transform: scale(1.05); }

.custom-login-input :deep(.el-input__wrapper) {
  background: rgba(2, 44, 34, 0.6) !important;
  border: 1px solid rgba(52, 211, 153, 0.3) !important;
  box-shadow: none !important;
  border-radius: 12px !important;
  height: 44px;
}
.custom-login-input :deep(.el-input__inner) { color: #fff !important; }

.login-error-tip {
  background: rgba(239, 68, 68, 0.2);
  border: 1px solid #f87171;
  color: #fca5a5;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
  margin-bottom: 16px;
}

.admin-login-btn {
  width: 100%;
  height: 46px;
  border-radius: 12px;
  background: linear-gradient(135deg, #10b981, #059669) !important;
  border: none !important;
  font-size: 15px;
  font-weight: 600;
  box-shadow: 0 8px 24px rgba(16,185,129,0.4) !important;
}

.login-bottom-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 24px;
  font-size: 12px;
  color: #6ee7b7;
  opacity: 0.7;
}

/* 顶栏 */
.admin-header {
  height: 64px;
  background: #0f172a;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
  z-index: 50;
}

.brand-zone { display: flex; align-items: center; gap: 12px; }
.logo-box { font-size: 28px; }
.brand-title { font-size: 16px; font-weight: 800; letter-spacing: 0.5px; }
.brand-sub { font-size: 11px; color: #94a3b8; }
.cross-jump-bar { display: flex; gap: 10px; }

.user-header-group { display: flex; align-items: center; gap: 12px; }

.user-badge {
  display: flex;
  align-items: center;
  gap: 10px;
  background: #1e293b;
  padding: 6px 14px;
  border-radius: 20px;
  cursor: pointer;
  transition: all 0.2s;
  border: 1px solid #334155;
}
.user-badge:hover { border-color: #3b82f6; background: #334155; }
.badge-avatar { font-size: 20px; }
.badge-name { font-size: 12px; font-weight: 700; color: #f8fafc; display: flex; align-items: center; gap: 6px; }
.badge-role { font-size: 10px; color: #94a3b8; }

.header-logout-btn {
  display: flex;
  align-items: center;
  gap: 4px;
  background: rgba(239, 68, 68, 0.18);
  border: 1px solid #ef4444;
  color: #fca5a5;
  padding: 6px 12px;
  border-radius: 16px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}
.header-logout-btn:hover { background: #ef4444; color: #fff; }

/* 侧边栏与主体 */
.admin-main { flex: 1; display: flex; overflow: hidden; }
.admin-sidebar {
  width: 232px;
  background: #ffffff;
  border-right: 1px solid #e2e8f0;
  display: flex;
  flex-direction: column;
  padding: 14px 10px;
  gap: 6px;
  flex-shrink: 0;
}

.menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 11px 14px;
  font-size: 13.5px;
  font-weight: 500;
  color: #475569;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}
.menu-item:hover { background: #f1f5f9; color: #0f172a; }
.menu-item.active { background: #eff6ff; color: #2563eb; font-weight: 600; }
.menu-icon { font-size: 16px; }

.admin-content { flex: 1; min-width: 0; padding: 20px 24px; overflow-y: auto; background: #f1f5f9; }
.tab-pane { display: flex; flex-direction: column; width: 100%; }
.tab-pane > .el-card { width: 100%; }

.pane-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.pane-header h3 { margin: 0; font-size: 17px; color: #1e293b; }
.sub-desc { font-size: 12px; color: #64748b; margin-top: 3px; display: block; }

.metrics-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14px; }
.metric-card { padding: 16px; border-radius: 10px; color: #ffffff; }
.metric-card.bg-blue { background: linear-gradient(135deg, #2563eb, #1d4ed8); }
.metric-card.bg-green { background: linear-gradient(135deg, #10b981, #059669); }
.metric-card.bg-purple { background: linear-gradient(135deg, #8b5cf6, #6d28d9); }
.metric-card.bg-orange { background: linear-gradient(135deg, #f59e0b, #d97706); }

.m-label { font-size: 11.5px; opacity: 0.9; margin-bottom: 6px; }
.m-val { font-size: 24px; font-weight: 800; margin-bottom: 4px; }
.m-val .unit { font-size: 12px; font-weight: normal; }
.m-sub { font-size: 11px; opacity: 0.85; }

.grid-2col { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
.sub-card { background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; }
.sub-card-header { display: flex; align-items: center; gap: 8px; font-size: 13.5px; font-weight: 700; color: #1e293b; margin-bottom: 12px; }

.mt-16 { margin-top: 16px; }
.mb-16 { margin-bottom: 16px; }
.ml-auto { margin-left: auto; }
.text-success { color: #10b981; }
.text-danger { color: #ef4444; }
.text-muted { color: #94a3b8; }
.font-bold { font-weight: 700; }
.font-mono { font-family: monospace; }
.flex-between { display: flex; justify-content: space-between; align-items: center; }

.salary-grid { display: grid; grid-template-columns: 1.25fr 1fr; gap: 16px; }
.salary-table-card, .salary-chat-card {
  background: #ffffff; border: 1px solid #e2e8f0; border-radius: 10px; padding: 16px; display: flex; flex-direction: column;
}
.quick-pills { display: flex; gap: 8px; margin-bottom: 10px; }
.chat-box {
  height: 380px; overflow-y: auto; border: 1px solid #f1f5f9; border-radius: 8px; padding: 12px; background: #f8fafc; display: flex; flex-direction: column; gap: 12px;
}
.chat-msg { padding: 10px 12px; border-radius: 8px; max-width: 85%; font-size: 12.5px; line-height: 1.5; }
.chat-msg.user { align-self: flex-end; background: #eff6ff; border: 1px solid #bfdbfe; }
.chat-msg.assistant { align-self: flex-start; background: #ffffff; border: 1px solid #e2e8f0; }

/* ── AI 回复 Markdown 排版（列表/表格/代码不再溢出气泡） ── */
.chat-msg .msg-content { overflow-wrap: break-word; }
.chat-msg .msg-content p { margin: 4px 0; }
.chat-msg .msg-content ul,
.chat-msg .msg-content ol { margin: 4px 0; padding-left: 18px; }
.chat-msg .msg-content li { margin: 2px 0; }
.chat-msg .msg-content h1,
.chat-msg .msg-content h2,
.chat-msg .msg-content h3,
.chat-msg .msg-content h4 { margin: 8px 0 4px; font-size: 13px; font-weight: 800; }
.chat-msg .msg-content blockquote {
  margin: 6px 0; padding: 5px 10px; border-left: 3px solid #93c5fd;
  background: #f8fafc; border-radius: 0 6px 6px 0; color: #475569;
}
.chat-msg .msg-content code {
  background: #e2e8f0; color: #0f172a; padding: 1px 4px;
  border-radius: 4px; font-size: 11.5px;
}
.chat-msg .msg-content strong { font-weight: 800; }
.md-table-scroll { overflow-x: auto; margin: 6px 0; border: 1px solid #e2e8f0; border-radius: 6px; }
.chat-msg .msg-content table { border-collapse: collapse; width: 100%; font-size: 11.5px; line-height: 1.5; }
.chat-msg .msg-content th,
.chat-msg .msg-content td { border: 1px solid #e2e8f0; padding: 5px 8px; text-align: left; white-space: nowrap; }
.chat-msg .msg-content th { background: #f1f5f9; font-weight: 700; color: #334155; }
.msg-sender { font-size: 10.5px; color: #64748b; margin-bottom: 4px; font-weight: 700; }
.chat-input-bar { display: flex; gap: 8px; margin-top: 12px; }

.mic-btn-admin {
  flex: 0 0 auto;
  width: 40px; height: 40px;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px;
  background: #f1f5f9; border: 1px solid #e2e8f0; border-radius: 50%;
  cursor: pointer; transition: all 0.15s;
}
.mic-btn-admin:hover { background: #e0f2f1; border-color: #0f766e; }
.mic-btn-admin.recording { background: #fee2e2; border-color: #ef4444; animation: mic-pulse 1s ease-in-out infinite; }
@keyframes mic-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.35); }
  50% { box-shadow: 0 0 0 6px rgba(239, 68, 68, 0); }
}
/* 附件预览（工资表图片 / Excel） */
/* 附件独立一行容器：不挤占输入栏 */
.batch-salary-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 10px 0;
}
/* 工资表里存在但系统无账号的人员行：整行红底警示，不可发放 */
:deep(.no-account-row) {
  background: #fef2f2 !important;
}
/* 发放记录弹窗：右下角可自由拖拽缩放（双保险：class 可能落在 overlay 或 dialog 元素上） */
.slip-history-dialog {
  resize: both;
  overflow: auto;
}
.slip-history-dialog .el-dialog {
  resize: both;
  overflow: auto;
  max-width: 96vw;
}
/* 附件文件卡片（上传预览与气泡内同款，对齐主流 AI 对话样式） */
.file-card {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
}
.file-card-icon {
  width: 38px;
  height: 38px;
  border-radius: 9px;
  background: #22c55e;
  color: #fff;
  font-size: 10px;
  font-weight: 700;
  letter-spacing: 0.5px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 auto;
}
.file-card-icon-img {
  width: 38px;
  height: 38px;
  border-radius: 9px;
  object-fit: cover;
  flex: 0 0 auto;
}
.file-card-info {
  min-width: 0;
}
.file-card-name {
  font-size: 13px;
  font-weight: 600;
  color: #0f172a;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.file-card-size {
  font-size: 11px;
  color: #94a3b8;
  margin-top: 2px;
}
.file-card-remove {
  cursor: pointer;
  color: #94a3b8;
  font-size: 14px;
  padding: 2px 4px;
  border-radius: 50%;
  flex: 0 0 auto;
}
.file-card-remove:hover {
  color: #ef4444;
  background: #fee2e2;
}
.msg-file-card {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 14px;
  background: #fff;
  border: 1px solid #e2e8f0;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(15, 23, 42, 0.06);
  margin-bottom: 8px;
}
.chat-attachment-row {
  display: flex;
  padding: 0 2px;
}
.chat-attachment-row .chat-attachment-preview {
  flex: 1;
  max-width: 420px;
}
/* 用户气泡里的图片预览 */
.msg-image { margin: 4px 0 6px; }
.msg-image img { max-width: 180px; max-height: 140px; object-fit: cover; border-radius: 8px; border: 1px solid #e2e8f0; display: block; }
.chat-attachment-preview {
  display: flex; align-items: center; gap: 8px;
  padding: 6px 10px; margin: 6px 0;
  background: #f0fdfa; border: 1px dashed #0f766e; border-radius: 8px;
}
.chat-attachment-thumb { width: 36px; height: 36px; object-fit: cover; border-radius: 6px; border: 1px solid #e2e8f0; flex: 0 0 auto; }
.chat-attachment-excel { font-size: 20px; flex: 0 0 auto; }
.chat-attachment-name { flex: 1; font-size: 12px; color: #0f766e; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chat-attachment-remove { cursor: pointer; color: #ef4444; font-size: 14px; padding: 2px 6px; }

.msg-tts-line { margin-top: 4px; text-align: right; }
.tts-link { font-size: 12px; color: #0f766e; cursor: pointer; user-select: none; }
.tts-link:hover { text-decoration: underline; }

.text-assistant-body { display: flex; flex-direction: column; gap: 12px; }
.ta-templates { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.ta-label { font-weight: 600; color: #334155; font-size: 13px; }
.ta-actions { display: flex; gap: 10px; }
.ta-result { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; }
.ta-result-title { font-weight: 700; color: #334155; margin-bottom: 6px; font-size: 13px; }
.ta-result-content { font-size: 13px; line-height: 1.7; color: #1e293b; white-space: pre-wrap; }

.user-order-card {
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; margin-bottom: 12px; font-size: 12.5px;
}
.order-sub-info { display: flex; justify-content: space-between; color: #64748b; font-size: 11.5px; margin: 6px 0; }
.order-json-box { background: #ffffff; border: 1px dashed #cbd5e1; border-radius: 6px; padding: 8px; font-family: monospace; font-size: 11px; color: #334155; word-break: break-all; }
.order-items-box { background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px; padding: 4px 8px; }
.perm-full-text { font-size: 12.5px; color: #166534; font-weight: 500; }
.perm-none-text { font-size: 12.5px; color: #9a3412; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.order-item-line { display: flex; align-items: center; gap: 8px; padding: 5px 0; font-size: 12px; color: #334155; }
.order-item-line + .order-item-line { border-top: 1px dashed #e2e8f0; }
.order-item-line .oi-name { font-weight: 500; color: #1e293b; }
.order-item-line .oi-spec { color: #94a3b8; font-size: 11px; }
.order-item-line .oi-qty { margin-left: auto; color: #64748b; }
.order-item-line .oi-price { min-width: 64px; text-align: right; font-weight: 500; color: #dc2626; }

.profile-card { font-size: 13px; line-height: 2.2; }

.analytics-ai-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.salary-full-card {
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 16px;
  display: flex;
  flex-direction: column;
}

/* AI 对话气泡样式全面升级：消除浏览器圆点溢出，支持 Markdown 表格 */
.msg-content :deep(ul),
.msg-content :deep(ol),
.msg-content ul,
.msg-content ol {
  list-style: none !important;
  list-style-type: none !important;
  padding: 0 !important;
  padding-left: 0 !important;
  margin: 6px 0 !important;
}

.msg-content :deep(li),
.msg-content li {
  list-style: none !important;
  list-style-type: none !important;
  padding-left: 0 !important;
  margin: 3px 0 !important;
  line-height: 1.6 !important;
}

.msg-content :deep(table),
.msg-content table {
  width: 100%;
  border-collapse: collapse;
  margin: 10px 0;
  font-size: 12px;
  background: #ffffff;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid #cbd5e1;
}

.msg-content :deep(th),
.msg-content :deep(td),
.msg-content th,
.msg-content td {
  border: 1px solid #cbd5e1;
  padding: 6px 10px;
  text-align: left;
}

.msg-content :deep(th),
.msg-content th {
  background: #f1f5f9;
  font-weight: 700;
  color: #1e293b;
}

.msg-content :deep(tr:nth-child(even) td),
.msg-content tr:nth-child(even) td {
  background: #f8fafc;
}

.msg-content :deep(code),
.msg-content code {
  background: #f1f5f9;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: monospace;
  color: #0284c7;
  font-size: 11.5px;
}

.msg-content :deep(blockquote),
.msg-content blockquote {
  margin: 8px 0;
  padding: 8px 12px;
  background: #f0fdf4;
  border-left: 4px solid #16a34a;
  border-radius: 4px;
  color: #166534;
  font-size: 12px;
}

/* 商城订单与履约发货样式 */
.order-item-chip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f8fafc;
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 3px;
  font-size: 11.5px;
  border: 1px solid #e2e8f0;
}
.item-qty {
  color: #059669;
  font-weight: bold;
}
.salary-calc-box {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 16px;
  margin-top: 10px;
}
.calc-row {
  display: flex;
  gap: 16px;
}
.calc-row :deep(.el-form-item) {
  flex: 1;
}
.net-salary-highlight {
  font-size: 20px;
  font-weight: 800;
  color: #dc2626;
  line-height: 32px;
}
.ai-calc-btn {
  background: linear-gradient(135deg, #3b82f6, #6366f1);
  border: none;
  font-weight: bold;
}

.ai-reasoning-card {
  background: linear-gradient(135deg, #f0fdf4 0%, #f0f9ff 100%);
  border: 1px solid #bbf7d0;
  border-radius: 8px;
  padding: 12px 14px;
  margin-bottom: 16px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.02);
}
.ai-reasoning-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 700;
  color: #166534;
  margin-bottom: 8px;
  font-size: 13px;
}
.ai-reasoning-item {
  font-size: 12px;
  line-height: 1.6;
  margin-bottom: 4px;
}
.ai-k {
  color: #475569;
  font-weight: 600;
}
.ai-v {
  color: #1e293b;
}
.ai-reasoning-tags {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  flex-wrap: wrap;
}
.ai-reasoning-tags .badge {
  font-size: 11px;
  background: #ffffff;
  color: #0369a1;
  border: 1px solid #bae6fd;
  padding: 2px 8px;
  border-radius: 12px;
  font-weight: 500;
}


/* ── 中台 AI 调度会话历史抽屉与标签样式 ── */
.csb-session-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #0284c7;
  background: #e0f2fe;
  padding: 2px 10px;
  border-radius: 9999px;
  border: 1px solid #bae6fd;
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.csb-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #0284c7;
}
.ah-drawer-body {
  display: flex;
  flex-direction: column;
  height: 100%;
}
.ah-drawer-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 12px;
  margin-bottom: 12px;
  border-bottom: 1px solid #e2e8f0;
}
.ah-count {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}
.ah-session-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  max-height: calc(100vh - 160px);
}
.ah-session-card {
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 10px 12px;
  background: #f8fafc;
  cursor: pointer;
  transition: all 0.2s;
}
.ah-session-card:hover {
  border-color: #3b82f6;
  background: #eff6ff;
}
.ah-session-card.active {
  border-color: #2563eb;
  background: #dbeafe;
  box-shadow: 0 2px 6px rgba(37, 99, 235, 0.12);
}
.ah-card-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.ah-sess-title {
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  display: flex;
  align-items: center;
  gap: 6px;
}
.ah-active-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #2563eb;
}
.ah-card-bottom {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: #64748b;
}
.ah-empty {
  text-align: center;
  color: #94a3b8;
  padding: 40px 0;
  font-size: 13px;
}

/* 会话历史时间分组 */
.ah-session-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 10px;
}
.ah-group-label {
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
  padding-bottom: 3px;
  border-bottom: 1px dashed #e2e8f0;
  position: sticky;
  top: 0;
  background: #ffffff;
  z-index: 1;
}

/* 商品图片上传区 */
.upload-image-zone {
  display: flex;
  align-items: center;
  gap: 14px;
}
.product-img-uploader :deep(.el-upload) {
  border: 1px dashed #cbd5e1;
  border-radius: 8px;
  cursor: pointer;
  overflow: hidden;
  width: 90px;
  height: 90px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f8fafc;
  transition: border-color 0.2s;
}
.product-img-uploader :deep(.el-upload:hover) {
  border-color: #2563eb;
}
.upload-placeholder-icon {
  font-size: 24px;
  color: #94a3b8;
}
.upload-preview-img {
  width: 90px;
  height: 90px;
  object-fit: cover;
  display: block;
}
.upload-tips .tip-main {
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}
.upload-tips .tip-sub {
  font-size: 11px;
  color: #94a3b8;
  margin: 4px 0 6px;
}
.no-img-tip {
  font-size: 11px;
  color: #cbd5e1;
}
.row-img-upload-placeholder {
  width: 52px;
  height: 52px;
  border: 1px dashed #cbd5e1;
  border-radius: 6px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
  color: #94a3b8;
  cursor: pointer;
  transition: all 0.2s;
}
.row-img-upload-placeholder:hover {
  border-color: #2563eb;
  color: #2563eb;
}

</style>
