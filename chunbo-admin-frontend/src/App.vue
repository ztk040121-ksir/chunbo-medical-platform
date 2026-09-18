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
          <span class="version-tag">RBAC 安全系统 v1.0</span>
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
          <el-link type="info" :underline="false" @click="openSystem('http://localhost:5173/login')">打开医生工作台 →</el-link>
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
          <!-- 1. 经营分析大屏 (医生/人事/管理员可见，商户不可见) -->
          <div 
            v-if="currentUserRole !== 'MERCHANT'"
            class="menu-item" 
            :class="{ active: currentTab === 'analytics' }"
            @click="currentTab = 'analytics'"
          >
            <span class="menu-icon">📊</span>
            <span class="menu-label">诊所经营分析大屏</span>
          </div>

          <!-- 2. 商城订单履约与发货 (商户、最高管理员专属) -->
          <div 
            v-if="currentUserRole === 'ADMIN' || currentUserRole === 'MERCHANT'"
            class="menu-item" 
            :class="{ active: currentTab === 'mall-orders' }"
            @click="currentTab = 'mall-orders'"
          >
            <span class="menu-icon">📦</span>
            <span class="menu-label">商城订单履约与发货</span>
          </div>

          <!-- 3. 商城商品管理与进销存 (商户、最高管理员可见) -->
          <div 
            v-if="currentUserRole === 'ADMIN' || currentUserRole === 'MERCHANT'"
            class="menu-item" 
            :class="{ active: currentTab === 'mall-products' }"
            @click="currentTab = 'mall-products'"
          >
            <span class="menu-icon">🛍️</span>
            <span class="menu-label">商城商品与进销存</span>
          </div>

          <!-- 4. 商城注册用户管理 (商户、最高管理员可见) -->
          <div 
            v-if="currentUserRole === 'ADMIN' || currentUserRole === 'MERCHANT'"
            class="menu-item" 
            :class="{ active: currentTab === 'mall-users' }"
            @click="currentTab = 'mall-users'"
          >
            <span class="menu-icon">👤</span>
            <span class="menu-label">商城注册用户管理</span>
          </div>

          <!-- 5. 工资条管理 (全员可见：医生/商户看自己，人事/管理员看全院并能发放) -->
          <div 
            class="menu-item" 
            :class="{ active: currentTab === 'salary' }"
            @click="currentTab = 'salary'"
          >
            <span class="menu-icon">💼</span>
            <span class="menu-label">{{ (currentUserRole === 'DOCTOR' || currentUserRole === 'MERCHANT') ? '我的工资条明细' : '工资条发放与核算' }}</span>
          </div>

          <!-- 6. OA 请假独立审批中心 (商户不可见；医生发起，人事/管理员审批) -->
          <div 
            v-if="currentUserRole !== 'MERCHANT'"
            class="menu-item" 
            :class="{ active: currentTab === 'approval' }"
            @click="currentTab = 'approval'"
          >
            <span class="menu-icon">📑</span>
            <span class="menu-label">{{ currentUserRole === 'DOCTOR' ? '我的OA请假申请' : 'OA 请假独立审批中心' }}</span>
          </div>

          <!-- 7. 医生账号注册与授权 (仅人事、管理员可见) -->
          <div 
            v-if="currentUserRole === 'ADMIN' || currentUserRole === 'HR'"
            class="menu-item" 
            :class="{ active: currentTab === 'doctors' }"
            @click="currentTab = 'doctors'"
          >
            <span class="menu-icon">👨‍⚕️</span>
            <span class="menu-label">医生账号注册与授权</span>
          </div>

          <!-- 8. 员工角色与权限矩阵 (仅人事、管理员可见) -->
          <div 
            v-if="currentUserRole === 'ADMIN' || currentUserRole === 'HR'"
            class="menu-item" 
            :class="{ active: currentTab === 'roles' }"
            @click="currentTab = 'roles'"
          >
            <span class="menu-icon">👥</span>
            <span class="menu-label">员工角色与权限矩阵</span>
          </div>

          <!-- 9. 人事与商户账号管理 (仅最高管理员专属) -->
          <div 
            v-if="currentUserRole === 'ADMIN'"
            class="menu-item" 
            :class="{ active: currentTab === 'hr-management' }"
            @click="currentTab = 'hr-management'"
          >
            <span class="menu-icon">🏛️</span>
            <span class="menu-label">人事与商户中台管理</span>
          </div>
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
                  <el-radio-button label="today">今日实时</el-radio-button>
                  <el-radio-button label="month">本月统计 (近30天)</el-radio-button>
                  <el-radio-button label="year">年度统计 (2026年)</el-radio-button>
                </el-radio-group>
                <el-button type="primary" size="small" @click="loadAnalytics">刷新数据</el-button>
              </div>
            </div>

            <!-- 四大指标卡片（根据今日/本月/年度动态切换） -->
            <div class="metrics-grid">
              <div class="metric-card bg-blue">
                <div class="m-label">{{ analytics.labelRegTitle || '门诊接诊人数' }}</div>
                <div class="m-val">{{ analytics.activeRegistrations !== undefined ? analytics.activeRegistrations : (analytics.todayRegistrations || 1) }} <span class="unit">人次</span></div>
                <div class="m-sub">{{ analytics.labelRegSub || '门诊患者接待与建档' }}</div>
              </div>
              <div class="metric-card bg-green">
                <div class="m-label">{{ analytics.labelFeeTitle || '门诊挂号费流水' }}</div>
                <div class="m-val">¥{{ analytics.activeRegFeeRevenue !== undefined ? analytics.activeRegFeeRevenue : (analytics.todayRegFeeRevenue || 10) }}</div>
                <div class="m-sub">{{ analytics.labelFeeSub || '实收标准 ¥10/人次' }}</div>
              </div>
              <div class="metric-card bg-purple">
                <div class="m-label">{{ analytics.labelMedTitle || '处方药品销售额' }}</div>
                <div class="m-val">¥{{ analytics.activeMedicineRevenue !== undefined ? analytics.activeMedicineRevenue : (analytics.todayMedicineRevenue || 105.0) }}</div>
                <div class="m-sub">{{ analytics.labelMedSub || '门诊处方药房销售实收' }}</div>
              </div>
              <div class="metric-card bg-orange">
                <div class="m-label">{{ analytics.labelPlasterTitle || '特色中药贴敷理疗创收' }}</div>
                <div class="m-val">¥{{ analytics.activePlasterRevenue !== undefined ? analytics.activePlasterRevenue : (analytics.totalPlasterRevenue || 1488) }}</div>
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
              <div class="chat-box" ref="chatBoxRef" style="height: 360px;">
                <div 
                  v-for="(msg, idx) in chatMessages" 
                  :key="idx" 
                  class="chat-msg"
                  :class="msg.role"
                >
                  <div class="msg-sender">{{ msg.role === 'user' ? ('👤 提问 (' + currentUserName + ')') : '🤖 春播中台AI调度指挥助手' }}</div>
                  <div class="msg-content" v-html="renderMarkdown(msg.content)"></div>
                </div>
              </div>

              <!-- 智能指令输入栏 -->
              <div class="chat-input-bar">
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
              </div>

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
                <p class="hero-sub">查看用户在线购药订单，商户或管理员手动核准并点击【📦 一键发货出库】，系统将自动扣减药品库存、录入春播健康便民速递单号并生成出库审计台账（非自动发货，保障药品出库合规）。</p>
              </div>
              <div class="hero-actions">
                <el-button type="primary" size="small" @click="loadMallOrders">🔄 刷新订单列表</el-button>
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
                <el-radio-button label="ALL">全部订单 ({{ mallOrdersList.length }})</el-radio-button>
                <el-radio-button label="PENDING">待发货出库 ({{ pendingShipCount }})</el-radio-button>
                <el-radio-button label="SHIPPED">已发货运输中 ({{ shippedCount }})</el-radio-button>
              </el-radio-group>
              <span style="font-size: 13px; color: #475569;">
                💡 发货说明：待发货订单请在右侧操作栏点击 <b style="color: #16a34a;">【📦 一键发货出库】</b> 手动出库（非自动发货）
              </span>
            </div>

            <!-- 订单表格 -->
            <div class="admin-table-card">
              <el-table :data="filteredMallOrders" stripe size="small" max-height="560">
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
                <el-table-column label="发货履约操作" width="180" fixed="right" align="center">
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
                    <div v-else-if="scope.row.status && scope.row.status.includes('已送达')" style="display: flex; flex-direction: column; align-items: center; gap: 2px;">
                      <el-tag type="success" size="small" effect="dark" style="font-weight: bold; background: #059669; border-color: #059669;">
                        ✅ 已送达 / 居民已签收
                      </el-tag>
                      <span style="font-size: 11px; color: #15803d; font-family: monospace; font-weight: 600;">妥投完成</span>
                    </div>
                    <!-- 3. 已发货运输中 -> 支持一键点击确认送达 -->
                    <div v-else style="display: flex; flex-direction: column; align-items: center; gap: 4px;">
                      <el-tag type="success" size="small" effect="plain" style="font-weight: bold;">
                        🚚 春播便民速递运输中
                      </el-tag>
                      <el-button 
                        type="primary" 
                        size="small" 
                        plain
                        style="font-size: 11px; padding: 2px 8px; height: 24px; font-weight: bold;"
                        @click="confirmDeliverOrder(scope.row)"
                      >
                        ✅ 确认已送达
                      </el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
<!-- 2. 工资条管理 -->
          <div v-else-if="currentTab === 'salary'" class="tab-pane">
            <div class="pane-header">
              <div>
                <h3>💼 {{ currentUserRole === 'DOCTOR' ? '我的月度工资条与绩效明细' : '诊所医护员工资条核算中枢' }}</h3>
                <span class="sub-desc" v-if="currentUserRole === 'DOCTOR'">当前仅展示医生本人 [{{ currentUserName }}] 的核算记录</span>
                <span class="sub-desc" v-else>支持按月核算发放、贴敷绩效提成与中台 AI 报表调度</span>
              </div>
              <div v-if="currentUserRole === 'ADMIN' || currentUserRole === 'HR'">
                <el-button type="success" size="small" @click="showIssueSalaryDialog = true">
                  + 核算并发放新工资条
                </el-button>
              </div>
            </div>

            <div class="salary-full-card">
              <div class="sub-card-header flex-between">
                <div>
                  <span>📋</span>
                  <b>{{ currentUserRole === 'DOCTOR' ? '个人历史工资核算台账' : '全院医护员工资发放台账' }}</b>
                  <span style="font-size: 12px; color: #64748b; margin-left: 10px;">由人事与院办依据临床工时、门诊开方、特色理疗及商城履约真实提成核发</span>
                </div>
                <div v-if="currentUserRole === 'ADMIN' || currentUserRole === 'HR'">
                  <el-button type="primary" size="small" plain @click="askAssistant('全院薪酬发放汇总表'); currentTab = 'analytics'">
                    🤖 前往大屏调取 AI 薪资报表
                  </el-button>
                </div>
              </div>
              <el-table :data="displaySalarySlips" stripe size="small" style="width: 100%;">
                <el-table-column prop="doctorId" label="员工工号" width="120" />
                <el-table-column prop="doctorName" label="员工姓名" width="110" />
                <el-table-column prop="salaryMonth" label="归属月份" width="100" />
                <el-table-column prop="baseSalary" label="基本底薪" width="110">
                  <template #default="scope">
                    <span>¥{{ scope.row.baseSalary }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="clinicCommission" label="门诊/电商提成" width="120">
                  <template #default="scope">
                    <span class="text-success">+¥{{ scope.row.clinicCommission }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="plasterCommission" label="贴敷理疗/合规奖" width="130">
                  <template #default="scope">
                    <span class="text-success font-bold">+¥{{ scope.row.plasterCommission }}</span>
                  </template>
                </el-table-column>
                <el-table-column prop="netSalary" label="实发工资" min-width="130">
                  <template #default="scope">
                    <b class="text-danger" style="font-size: 14px;">¥{{ scope.row.netSalary }}</b>
                  </template>
                </el-table-column>
                <el-table-column prop="status" label="发放状态" width="100" align="center">
                  <template #default="scope">
                    <el-tag type="success" size="small" effect="dark">{{ scope.row.status }}</el-tag>
                  </template>
                </el-table-column>
              </el-table>
            </div>
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
                <el-radio-button label="DEAN">🏛️ 人事/院办审批视角</el-radio-button>
                <el-radio-button label="APPLICANT">👨‍⚕️ 员工申请发起视角</el-radio-button>
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
                <el-table :data="displayApprovals" stripe size="small">
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
                <el-table :data="approvals" stripe size="small">
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
                      <div v-if="scope.row.status === '待审批'">
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
                <h3>👨‍⚕️ 基层诊所医护人员账号注册与管理中心</h3>
                <span class="sub-desc">在此注册并授权的医生工号，可直接在基层医生工作台 (http://localhost:5173/login) 登录</span>
              </div>
              <div style="display: flex; gap: 8px;">
                <el-button type="success" size="small" @click="showRegisterDoctorDialog = true">
                  ➕ 注册新医生账号
                </el-button>
                <el-button type="primary" size="small" plain @click="loadDoctorAccounts">
                  🔄 刷新列表
                </el-button>
              </div>
            </div>

            <!-- 统计指标 -->
            <div class="metrics-grid mb-16">
              <div class="metric-card bg-blue">
                <div class="m-label">在册医护账号总数</div>
                <div class="m-val">{{ doctorAccounts.length }} <span class="unit">位</span></div>
                <div class="m-sub">已建档数字化执业医师与药师</div>
              </div>
              <div class="metric-card bg-green">
                <div class="m-label">在岗执业与已启用</div>
                <div class="m-val">{{ activeDoctorCount }} <span class="unit">位</span></div>
                <div class="m-sub">已颁发 CA 数字证书与处方权</div>
              </div>
              <div class="metric-card bg-purple">
                <div class="m-label">覆盖门诊科室</div>
                <div class="m-val">{{ departmentCount }} <span class="unit">个</span></div>
                <div class="m-sub">全科门诊、慢病专科、特色贴敷等</div>
              </div>
              <div class="metric-card bg-orange">
                <div class="m-label">处方与调剂授权率</div>
                <div class="m-val">100%</div>
                <div class="m-sub">符合卫健委基层医疗执业规范</div>
              </div>
            </div>

            <!-- 表格 -->
            <el-card>
              <template #header>
                <div class="flex-between">
                  <b>📋 在册医生执业工号与工作台账号矩阵</b>
                  <el-tag type="info" size="small">默认测试工号: kzt / 密码: 123456</el-tag>
                </div>
              </template>
              <el-table :data="doctorAccounts" stripe size="small" v-loading="doctorLoading">
                <el-table-column prop="id" label="序号" width="60" />
                <el-table-column prop="username" label="登录账号/工号" width="130">
                  <template #default="scope">
                    <el-tag effect="plain" type="primary" class="font-mono"><b>{{ scope.row.username }}</b></el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="doctorName" label="医生真实姓名" width="120">
                  <template #default="scope">
                    <b>{{ scope.row.doctorName }}</b>
                  </template>
                </el-table-column>
                <el-table-column prop="doctorId" label="数字工牌号" width="120">
                  <template #default="scope">
                    <el-tag type="success" size="small">{{ scope.row.doctorId }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="department" label="所属科室" width="160" />
                <el-table-column prop="title" label="执业职称" width="140">
                  <template #default="scope">
                    <el-tag :type="scope.row.title && scope.row.title.includes('主任') ? 'danger' : 'info'" size="small">
                      {{ scope.row.title }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="phone" label="联系电话" width="120">
                  <template #default="scope">{{ scope.row.phone || '-' }}</template>
                </el-table-column>
                <el-table-column prop="status" label="账号状态" width="100">
                  <template #default="scope">
                    <el-tag :type="scope.row.status === 'ENABLE' ? 'success' : 'danger'" size="small">
                      {{ scope.row.status === 'ENABLE' ? '正常启用' : '已停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="人事管理操作" width="210" fixed="right">
                  <template #default="scope">
                    <el-button 
                      size="small" 
                      :type="scope.row.status === 'ENABLE' ? 'warning' : 'success'" 
                      link
                      @click="handleToggleDoctorStatus(scope.row)"
                    >
                      {{ scope.row.status === 'ENABLE' ? '停用' : '启用' }}
                    </el-button>
                    <el-button size="small" type="primary" link @click="handleResetDoctorPassword(scope.row)">
                      重置密码
                    </el-button>
                    <el-button size="small" type="danger" link @click="handleDeleteDoctor(scope.row)">
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>

            <!-- 注册新医生账号弹窗 -->
            <el-dialog 
              v-model="showRegisterDoctorDialog" 
              title="👨‍⚕️ 注册新医生工作台账号 (对应医生端登录)" 
              width="520px"
              destroy-on-close
            >
              <el-alert 
                title="提示：在此注册的工号和密码，可在基层医生工作台 (http://localhost:5173/login) 立即验证登录。"
                type="info" 
                :closable="false"
                style="margin-bottom: 16px;"
              />
              <el-form :model="newDoctorForm" label-width="110px" size="default">
                <el-form-item label="登录账号/工号" required>
                  <el-input v-model="newDoctorForm.username" placeholder="如 doc_1005 或 kzt (医生登录用)" />
                </el-form-item>
                <el-form-item label="医生真实姓名" required>
                  <el-input v-model="newDoctorForm.doctorName" placeholder="如 王文清、赵医生" />
                </el-form-item>
                <el-form-item label="初始登录密码" required>
                  <el-input v-model="newDoctorForm.password" placeholder="默认 123456" show-password />
                </el-form-item>
                <el-form-item label="数字工牌号">
                  <el-input v-model="newDoctorForm.doctorId" placeholder="留空则系统自动分配 (如 DOC_1005)" />
                </el-form-item>
                <el-form-item label="所属执业科室">
                  <el-select v-model="newDoctorForm.department" style="width: 100%">
                    <el-option label="全科门诊 / 中医特色专科" value="全科门诊 / 中医特色专科" />
                    <el-option label="全科门诊 / 智慧药房" value="全科门诊 / 智慧药房" />
                    <el-option label="全科慢病门诊" value="全科慢病门诊" />
                    <el-option label="中医理疗特色门诊" value="中医理疗特色门诊" />
                    <el-option label="儿科综合门诊" value="儿科综合门诊" />
                  </el-select>
                </el-form-item>
                <el-form-item label="岗位职称">
                  <el-select v-model="newDoctorForm.title" style="width: 100%">
                    <el-option label="主任医师" value="主任医师" />
                    <el-option label="副主任医师" value="副主任医师" />
                    <el-option label="主治医师" value="主治医师" />
                    <el-option label="执业医师" value="执业医师" />
                    <el-option label="主治医师 / 调剂药师" value="主治医师 / 调剂药师" />
                  </el-select>
                </el-form-item>
                <el-form-item label="联系电话">
                  <el-input v-model="newDoctorForm.phone" placeholder="医生手机号码" />
                </el-form-item>
              </el-form>
              <template #footer>
                <el-button @click="showRegisterDoctorDialog = false">取消</el-button>
                <el-button type="success" :loading="registerLoading" @click="submitRegisterDoctor">
                  确认注册并授权
                </el-button>
              </template>
            </el-dialog>
          </div>

          <!-- 5. 员工角色与权限矩阵 (人事、管理员) -->
          <div v-else-if="currentTab === 'roles'" class="tab-pane">
            <div class="pane-header">
              <div>
                <h3>👥 基层诊所员工角色体系与权限控制矩阵 (RBAC)</h3>
                <span class="sub-desc">由人事主管统一配置全院人员岗位权限</span>
              </div>
            </div>
            <el-card>
              <el-table :data="staffRoles" stripe size="small">
                <el-table-column prop="staffId" label="员工工号" width="110">
                  <template #default="scope">
                    <el-tag type="info">{{ scope.row.staffId }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="name" label="姓名" width="100" />
                <el-table-column prop="title" label="岗位职称" width="140" />
                <el-table-column prop="department" label="所属部门/科室" width="140" />
                <el-table-column prop="role" label="安全角色" width="120">
                  <template #default="scope">
                    <el-tag :type="scope.row.role === 'ADMIN' ? 'danger' : (scope.row.role === 'HR' ? 'warning' : 'primary')">{{ scope.row.role }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="permissions" label="系统功能与操作权限范围" min-width="260" />
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
                <el-button type="primary" size="small" plain @click="loadMallAdminProducts">
                  🔄 刷新商品列表
                </el-button>
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
                <div class="m-val">100%</div>
                <div class="m-sub">补货入库自动生成出入库台账</div>
              </div>
            </div>

            <!-- 商品表格 -->
            <el-card>
              <el-table :data="mallProducts" stripe size="small" v-loading="mallLoading">
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
              <el-button type="primary" size="small" plain @click="loadMallUsers">🔄 刷新用户列表</el-button>
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
                <div class="m-val">¥{{ (mallUsers.length * 200).toFixed(2) }}</div>
                <div class="m-sub">每位新注册居民预赠 200 元体验金</div>
              </div>
            </div>

            <!-- 用户表格 -->
            <el-card>
              <el-table :data="mallUsers" stripe size="small" v-loading="mallUsersLoading">
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
                <div class="order-json-box">
                  {{ order.itemsJson }}
                </div>
              </div>
            </el-drawer>
          </div>

          <!-- 8. 人事账号注册与管理 (管理员专属) -->
          <div v-else-if="currentTab === 'hr-management'" class="tab-pane">
            <div class="pane-header">
              <div>
                <h3>🏛️ 医院综合中台人事与全员账号授权管理 (最高权限管理员专属)</h3>
                <span class="sub-desc">超级管理员可在此注册授权新人事账号，人事账号拥有工资发放、请假审批与医生注册权限</span>
              </div>
              <div style="display: flex; gap: 8px;">
                <el-button type="danger" size="small" @click="showRegisterHrDialog = true">
                  ➕ 注册新人事账号
                </el-button>
                <el-button type="primary" size="small" plain @click="loadStaffList">
                  🔄 刷新员工列表
                </el-button>
              </div>
            </div>

            <!-- 全员表格 -->
            <el-card>
              <template #header>
                <div class="flex-between">
                  <b>📋 全院管理人员与医护员工权限总台账</b>
                  <el-tag type="danger">最高管理级别管控</el-tag>
                </div>
              </template>
              <el-table :data="allStaffList" stripe size="small" v-loading="staffLoading">
                <el-table-column prop="id" label="ID" width="55" />
                <el-table-column prop="username" label="登录工号/账号" width="130">
                  <template #default="scope">
                    <el-tag effect="plain" type="primary" class="font-mono"><b>{{ scope.row.username }}</b></el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="realName" label="真实姓名" width="120">
                  <template #default="scope">
                    <b>{{ scope.row.realName }}</b>
                  </template>
                </el-table-column>
                <el-table-column prop="staffId" label="员工编号" width="110">
                  <template #default="scope">
                    <el-tag type="info" size="small">{{ scope.row.staffId }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="role" label="安全角色" width="110">
                  <template #default="scope">
                    <el-tag :type="scope.row.role === 'ADMIN' ? 'danger' : (scope.row.role === 'HR' ? 'warning' : 'success')" effect="dark">
                      {{ scope.row.role === 'ADMIN' ? '管理员' : (scope.row.role === 'HR' ? '人事主管' : '门诊医生') }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="department" label="所属部门/科室" width="160" />
                <el-table-column prop="title" label="岗位职称" width="130" />
                <el-table-column prop="phone" label="联系电话" width="120" />
                <el-table-column prop="status" label="状态" width="90">
                  <template #default="scope">
                    <el-tag :type="scope.row.status === 'ENABLE' ? 'success' : 'danger'" size="small">
                      {{ scope.row.status === 'ENABLE' ? '正常' : '已停用' }}
                    </el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="管理员操作" width="200" fixed="right">
                  <template #default="scope">
                    <el-button 
                      size="small" 
                      :type="scope.row.status === 'ENABLE' ? 'warning' : 'success'" 
                      link
                      @click="handleToggleStaffStatus(scope.row)"
                    >
                      {{ scope.row.status === 'ENABLE' ? '停用' : '启用' }}
                    </el-button>
                    <el-button size="small" type="primary" link @click="handleResetStaffPassword(scope.row)">
                      重置密码
                    </el-button>
                    <el-button 
                      v-if="scope.row.username !== 'admin'" 
                      size="small" 
                      type="danger" 
                      link 
                      @click="handleDeleteStaff(scope.row)"
                    >
                      删除
                    </el-button>
                  </template>
                </el-table-column>
              </el-table>
            </el-card>

            <!-- 注册人事弹窗 -->
            <el-dialog v-model="showRegisterHrDialog" title="🏛️ 注册新人事主管账号 (最高管理员专属)" width="480px">
              <el-alert title="人事角色拥有审核全院请假、核发医生工资条、注册授权医生账号等核心权限。" type="warning" :closable="false" class="mb-16" />
              <el-form :model="newHrForm" label-width="100px">
                <el-form-item label="人事登录工号" required>
                  <el-input v-model="newHrForm.username" placeholder="如 hr_wang 或 2001" />
                </el-form-item>
                <el-form-item label="人事真实姓名" required>
                  <el-input v-model="newHrForm.realName" placeholder="如 王文静" />
                </el-form-item>
                <el-form-item label="初始密码" required>
                  <el-input v-model="newHrForm.password" placeholder="默认 123456" show-password />
                </el-form-item>
                <el-form-item label="联系电话">
                  <el-input v-model="newHrForm.phone" placeholder="手机号码" />
                </el-form-item>
                <el-form-item label="所属部门">
                  <el-input v-model="newHrForm.department" />
                </el-form-item>
                <el-form-item label="职级岗位">
                  <el-input v-model="newHrForm.title" />
                </el-form-item>
              </el-form>
              <template #footer>
                <el-button @click="showRegisterHrDialog = false">取消</el-button>
                <el-button type="danger" :loading="registerHrLoading" @click="submitRegisterHr">确认注册人事账号</el-button>
              </template>
            </el-dialog>
          </div>
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
          <div class="p-row"><b>签约机构:</b> 春播第001社区卫生服务中心 / 春播万象云诊所</div>
        </div>
        <template #footer>
          <div class="flex-between">
            <el-button type="danger" plain size="small" @click="handleLogoutConfirm">退出登录</el-button>
            <el-button type="primary" size="small" @click="showProfileDialog = false">关闭工牌</el-button>
          </div>
        </template>
      </el-dialog>

    <!-- 核算并发放全员工资条 (支持门诊医生、商城商户及人事 + AI 智能测算绩效与工资) -->
    <el-dialog 
      v-model="showIssueSalaryDialog" 
      title="💰 全员月度薪酬核算与工资条发放中心" 
      width="680px"
      destroy-on-close
    >
      <div class="salary-dist-intro">
        <el-alert 
          type="info" 
          show-icon 
          :closable="false"
          title="支持为人事主管、门诊医生及商城商户核发月度工资条。点击「AI 智能测算绩效与工资」系统将联动数据库真实看诊/处方/电商订单/出库实绩进行深度智能核算与评分！"
        />
      </div>

      <el-form label-width="120px" class="mt-4">
        <el-form-item label="发薪员工 *" required>
          <el-select 
            v-model="selectedCandidateStaffId" 
            placeholder="请选择要核发薪酬的员工" 
            style="width: 100%"
            @change="handleSelectSalaryCandidate"
          >
            <el-option 
              v-for="c in staffCandidates" 
              :key="c.staffId" 
              :label="`${c.staffId} - ${c.realName} (${getCandidateRoleLabel(c.role)})`" 
              :value="c.staffId" 
            />
          </el-select>
        </el-form-item>

        <div style="display: flex; gap: 16px; align-items: center;">
          <el-form-item label="发薪月份 *" required style="flex: 1; margin-bottom: 18px;">
            <el-input v-model="salaryDistForm.month" placeholder="如 2026-09" />
          </el-form-item>
          
          <div style="margin-bottom: 18px;">
            <el-button 
              type="primary" 
              class="ai-calc-btn"
              :loading="aiCalcLoading"
              @click="handleAiCalculateSalary"
            >
              🤖 AI 智能测算绩效与工资
            </el-button>
          </div>
        </div>

        <!-- 💡 AI 测算分析依据与数据追溯卡片 -->
        <div v-if="aiMetricsBasis" class="ai-reasoning-card mb-4">
          <div class="ai-reasoning-title">
            <span>💡 AI 智能测算依据与工作实绩分析</span>
            <el-tag size="small" type="success" effect="dark">{{ aiMetricsBasis.rating || '卓越 A+' }}</el-tag>
          </div>
          <div class="ai-reasoning-item">
            <span class="ai-k">📊 真实工作量支撑：</span>
            <span class="ai-v">{{ aiMetricsBasis.workloadDesc }}</span>
          </div>
          <div class="ai-reasoning-item">
            <span class="ai-k">🧮 薪酬测算明细公式：</span>
            <span class="ai-v" style="font-family: monospace; color: #0284c7; font-weight: bold;">{{ aiMetricsBasis.formula }}</span>
          </div>
          <div class="ai-reasoning-tags">
            <span class="badge">履约满意度: {{ aiMetricsBasis.satisfaction || '98.8%' }}</span>
            <span class="badge">制度合规率: {{ aiMetricsBasis.complianceRate || '100%' }}</span>
            <span class="badge">员工角色: {{ getCandidateRoleLabel(selectedCandidateRole) }}</span>
          </div>
        </div>

        <div class="salary-calc-box">
          <div class="calc-row">
            <el-form-item label="基本岗位底薪">
              <el-input-number v-model="salaryDistForm.baseSalary" :min="0" :step="100" @change="recalcNetSalary" />
            </el-form-item>
            <el-form-item :label="commLabel1">
              <el-input-number v-model="salaryDistForm.clinicCommission" :min="0" :step="100" @change="recalcNetSalary" />
            </el-form-item>
          </div>

          <div class="calc-row">
            <el-form-item :label="commLabel2">
              <el-input-number v-model="salaryDistForm.plasterCommission" :min="0" :step="100" @change="recalcNetSalary" />
            </el-form-item>
            <el-form-item label="五险一金代扣">
              <el-input-number v-model="salaryDistForm.deductionSocial" :min="0" :step="50" @change="recalcNetSalary" />
            </el-form-item>
          </div>

          <div class="calc-row">
            <el-form-item label="个人所得税">
              <el-input-number v-model="salaryDistForm.tax" :min="0" :step="10" @change="recalcNetSalary" />
            </el-form-item>
            <el-form-item label="实发到手薪酬">
              <span class="net-salary-highlight">¥{{ computedNetSalary.toFixed(2) }}</span>
            </el-form-item>
          </div>

          <el-form-item label="AI 智能考评评语">
            <el-input 
              v-model="salaryDistForm.aiComment" 
              type="textarea" 
              :rows="3" 
              placeholder="AI 智能考评评语将在此展示，支持人工编辑调整..." 
            />
          </el-form-item>
        </div>
      </el-form>

      <template #footer>
        <el-button @click="showIssueSalaryDialog = false">取消</el-button>
        <el-button type="success" :loading="distributeLoading" @click="submitDistributeSalary">
          确认正式发放工资条
        </el-button>
      </template>
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
import { ref, onMounted, computed, nextTick , watch } from 'vue'
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
    isLoggedIn.value = false
    showProfileDialog.value = false
    ElMessage.success('已安全注销退出')
  }).catch(() => {})
}

// ==============================================
// 业务数据状态
// ==============================================
const currentTab = ref('analytics')
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
const showRegisterDoctorDialog = ref(false)
const registerLoading = ref(false)
const newDoctorForm = ref({
  username: '',
  doctorName: '',
  password: '123456',
  doctorId: '',
  department: '全科慢病门诊',
  title: '主治医师',
  phone: ''
})

const activeDoctorCount = computed(() => doctorAccounts.value.filter(d => d.status === 'ENABLE').length)
const departmentCount = computed(() => new Set(doctorAccounts.value.map(d => d.department).filter(Boolean)).size || 4)

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
  category: '感冒发热',
  specification: '0.35g*24粒/盒',
  manufacturer: '北京同仁堂科技发展股份有限公司',
  retailGuidePrice: 28.00,
  wholesalePrice: 14.50,
  stock: 200,
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
const showRegisterHrDialog = ref(false)
const registerHrLoading = ref(false)
const newHrForm = ref({
  username: '',
  realName: '',
  password: '123456',
  phone: '',
  department: '人事行政科',
  title: '人事主管'
})

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
    } else {
      createNewAdminSession()
    }
    saveAdminSessions()
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
  if (currentUserRole.value === 'ADMIN' || currentUserRole.value === 'MERCHANT') {
    loadMallOrders()
    loadMallAdminProducts()
    loadMallUsers()
  }
  if (currentUserRole.value === 'ADMIN') {
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

// 医生管理
const loadDoctorAccounts = async () => {
  doctorLoading.value = true
  try {
    const res = await axios.get('/api/doctor/list')
    if (res.data?.success) doctorAccounts.value = res.data.data || []
  } catch (e) {} finally { doctorLoading.value = false }
}

const submitRegisterDoctor = async () => {
  if (!newDoctorForm.value.username.trim() || !newDoctorForm.value.doctorName.trim()) {
    ElMessage.warning('请输入登录工号与医生真实姓名')
    return
  }
  registerLoading.value = true
  try {
    const res = await axios.post('/api/doctor/register', newDoctorForm.value)
    if (res.data?.success) {
      ElNotification({
        title: '医生账号注册成功！',
        message: `工号 [${newDoctorForm.value.username}] 已创建，可立即在医生工作台 (http://localhost:5173/login) 登录！`,
        type: 'success'
      })
      showRegisterDoctorDialog.value = false
      newDoctorForm.value = { username: '', doctorName: '', password: '123456', doctorId: '', department: '全科慢病门诊', title: '主治医师', phone: '' }
      loadDoctorAccounts()
    } else {
      ElMessage.error(res.data?.message || '注册失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message)
  } finally { registerLoading.value = false }
}

const handleToggleDoctorStatus = async (row) => {
  const newStatus = row.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
  try {
    await axios.post('/api/doctor/update-status', { id: row.id, status: newStatus })
    row.status = newStatus
    ElMessage.success(newStatus === 'ENABLE' ? '账号已启用' : '账号已停用')
  } catch (e) { ElMessage.error('更新失败') }
}

const handleResetDoctorPassword = async (row) => {
  ElMessageBox.confirm(`确定将医生 [${row.doctorName}] 的工作台密码重置为 123456 吗？`, '重置确认', { type: 'warning' })
    .then(async () => {
      const res = await axios.post('/api/doctor/reset-password', { id: row.id, newPassword: '123456' })
      ElMessage.success(res.data?.message || '密码已重置为 123456')
    }).catch(() => {})
}

const handleDeleteDoctor = async (row) => {
  ElMessageBox.confirm(`确定要注销删除医生账号 [${row.doctorName}] 吗？`, '删除确认', { type: 'danger' })
    .then(async () => {
      await axios.delete(`/api/doctor/${row.id}`)
      ElMessage.success('已删除')
      loadDoctorAccounts()
    }).catch(() => {})
}

// 商城商品进销存
const loadMallAdminProducts = async () => {
  mallLoading.value = true
  try {
    const res = await axios.get('/api/admin/mall/products')
    if (res.data?.success) mallProducts.value = res.data.data || []
  } catch (e) {} finally { mallLoading.value = false }
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
      category: '感冒发热',
      specification: '0.35g*24粒/盒',
      manufacturer: '北京同仁堂科技发展股份有限公司',
      retailGuidePrice: 28.00,
      wholesalePrice: 14.50,
      stock: 200,
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
    const res = await axios.get('/api/admin/mall/user/orders?buyerName=' + encodeURIComponent(row.nickname || row.username))
    userOrders.value = res.data?.data || []
    showUserOrderDrawer.value = true
  } catch (e) { ElMessage.error('获取订单失败') }
}

// 人事管理 (Admin 专属)
const loadStaffList = async () => {
  staffLoading.value = true
  try {
    const res = await axios.get('/api/admin/staff/list')
    if (res.data?.success) allStaffList.value = res.data.data || []
  } catch (e) {} finally { staffLoading.value = false }
}

const submitRegisterHr = async () => {
  if (!newHrForm.value.username.trim() || !newHrForm.value.realName.trim()) {
    ElMessage.warning('请填写人事工号与真实姓名')
    return
  }
  registerHrLoading.value = true
  try {
    const res = await axios.post('/api/admin/hr/register', newHrForm.value)
    if (res.data?.success) {
      ElNotification({
        title: '人事账号注册成功！',
        message: `人事主管 [${newHrForm.value.realName} (${newHrForm.value.username})] 可立即登录中台进行审批与发薪！`,
        type: 'success'
      })
      showRegisterHrDialog.value = false
      newHrForm.value = { username: '', realName: '', password: '123456', phone: '', department: '人事行政科', title: '人事主管' }
      loadStaffList()
    } else {
      ElMessage.error(res.data?.message || '注册失败')
    }
  } catch (e) { ElMessage.error(e.response?.data?.message || e.message) }
  finally { registerHrLoading.value = false }
}

const handleToggleStaffStatus = async (row) => {
  const nextStatus = row.status === 'ENABLE' ? 'DISABLE' : 'ENABLE'
  try {
    await axios.post('/api/admin/staff/status', { id: row.id, status: nextStatus })
    row.status = nextStatus
    ElMessage.success('员工状态已更新')
  } catch (e) { ElMessage.error('操作失败') }
}

const handleResetStaffPassword = async (row) => {
  ElMessageBox.confirm(`确定将 [${row.realName}] 的密码重置为 123456 吗？`, '重置确认')
    .then(async () => {
      const res = await axios.post('/api/admin/staff/reset-password', { id: row.id, newPassword: '123456' })
      ElMessage.success(res.data?.message || '密码已重置为 123456')
    }).catch(() => {})
}

const handleDeleteStaff = async (row) => {
  ElMessageBox.confirm(`确定删除员工账号 [${row.realName}] 吗？`, '删除确认', { type: 'danger' })
    .then(async () => {
      await axios.delete('/api/admin/staff/' + row.id)
      ElMessage.success('已删除')
      loadStaffList()
    }).catch(() => {})
}

// OA 与 AI 助手逻辑
const askAssistant = (query) => {
  inputQuery.value = query
  sendAssistantQuery()
}

const sendAssistantQuery = async () => {
  const text = inputQuery.value.trim()
  if (!text || chatLoading.value) return

  // 压入用户消息
  chatMessages.value.push({ role: 'user', content: text })
  // 首个用户问题自动生成会话标题
  const curSess = adminSessionList.value.find(s => s.id === currentAdminSessionId.value)
  if (curSess) {
    const hadUserMsg = (curSess.messages || []).some(m => m.role === 'user')
    if (!hadUserMsg || curSess.title === '新建调度会话' || curSess.title === '全中台运营调度主会话') {
      curSess.title = text.length > 14 ? text.slice(0, 14) + '…' : text
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
  const sseUrl = `/api/assistant/chat/stream?message=${encodeURIComponent(text)}&userId=${encodeURIComponent(currentUserStaffId.value)}&userRole=${encodeURIComponent(currentUserRole.value)}&userName=${encodeURIComponent(currentUserName.value)}`
  try {
    const resp = await fetch(sseUrl, {
      headers: { 'Authorization': 'Bearer ' + localStorage.getItem('chunbo_admin_token') },
      signal: abort.signal
    })
    if (!resp.ok || !resp.body) throw new Error('HTTP ' + resp.status)
    const reader = resp.body.getReader()
    const dec = new TextDecoder('utf-8')
    let buf = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buf += dec.decode(value, { stream: true })
      const parts = buf.split('\n\n')
      buf = parts.pop()
      for (const ev of parts) {
        const dl = ev.split('\n').find(l => l.startsWith('data:'))
        if (!dl) continue
        let piece = dl.slice(5)
        if (piece === '[DONE]') continue
        chatMessages.value[assistantMsgIndex].content += piece
        nextTick(() => {
          if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
        })
      }
    }
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
    nextTick(() => {
      if (chatBoxRef.value) chatBoxRef.value.scrollTop = chatBoxRef.value.scrollHeight
    })
  }
}

// ── 停止 AI 调度生成 ──
let assistantAbort = null
const stopAssistantGeneration = () => {
  if (assistantAbort) {
    assistantAbort.abort()
    assistantAbort = null
  }
  chatLoading.value = false
  const last = [...chatMessages.value].reverse().find(m => m.role === 'assistant')
  if (last && !last.content) last.content = '（已停止生成）'
  saveUserChatHistory()
}

const submitLeave = async () => {
  try {
    const payload = {
      applicantName: currentUserName.value,
      applicantId: currentUserStaffId.value,
      approvalType: leaveForm.value.type,
      reason: leaveForm.value.reason,
      durationDays: leaveForm.value.days,
      status: '待审批',
      approverName: '人事主管 / 院办',
      comment: '已提交人事待办审批'
    }
    await axios.post('/api/assistant/approval/create', payload)
    ElNotification({
      title: '请假申请提交成功',
      message: `已提交至人事/院办审批待办`,
      type: 'success'
    })
    loadApprovals()
  } catch (e) { ElMessage.error('提交失败') }
}

const handleDeanApprove = async (row, action) => {
  try {
    await axios.post('/api/assistant/approval/process', {
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
  } catch (e) {
    console.error("加载商城订单失败", e)
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
  role: 'DOCTOR',
  month: '2026-09',
  baseSalary: 6500,
  clinicCommission: 3200,
  plasterCommission: 4200,
  deductionSocial: 1150,
  tax: 280,
  netSalary: 12470,
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

.admin-content { flex: 1; padding: 20px 24px; overflow-y: auto; background: #f1f5f9; }
.tab-pane { display: flex; flex-direction: column; }

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

.user-order-card {
  background: #f8fafc; border: 1px solid #e2e8f0; border-radius: 8px; padding: 12px; margin-bottom: 12px; font-size: 12.5px;
}
.order-sub-info { display: flex; justify-content: space-between; color: #64748b; font-size: 11.5px; margin: 6px 0; }
.order-json-box { background: #ffffff; border: 1px dashed #cbd5e1; border-radius: 6px; padding: 8px; font-family: monospace; font-size: 11px; color: #334155; word-break: break-all; }

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
