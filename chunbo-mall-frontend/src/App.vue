<template>
  <div id="mall-app">
    <!-- 顶部便民网上药房导航 -->
    <header class="mall-header">
      <div class="header-container">
        <div class="brand-zone">
          <div class="logo-box">🌿</div>
          <div>
            <div class="brand-title">春播健康商城 · 便民网上药房</div>
            <div class="brand-sub">正品好药 · 顺丰即日达 · 24小时家庭药箱 · 专业药师指导</div>
          </div>
        </div>

        <!-- 中部搜索框 -->
        <div class="search-bar-wrap">
          <el-input 
            v-model="searchKeyword" 
            placeholder="搜索感冒发烧、咳嗽胃胀、创口贴、小儿用药、滋补调理等生活药品..." 
            prefix-icon="Search"
            clearable
            size="large"
            class="consumer-search-input"
          />
        </div>

        <!-- 用户便民服务区 -->
        <div class="header-actions">
          <div class="address-capsule" @click="showAddressModal = true" title="点击修改配送地址">
            <el-icon color="#059669"><Location /></el-icon>
            <span class="address-text">送至: {{ userAddress.city }}{{ userAddress.district }}{{ userAddress.detail }}</span>
            <span class="user-receiver">({{ userAddress.name }})</span>
          </div>

          <el-badge :value="cartCount" :hidden="cartCount === 0" class="cart-badge">
            <el-button type="primary" class="cart-btn" @click="showCartDrawer = true">
              <el-icon><ShoppingCart /></el-icon> 购物车
              <span v-if="cartTotal > 0" class="cart-price-sum">¥{{ cartTotal.toFixed(2) }}</span>
            </el-button>
          </el-badge>

          <el-button type="info" plain class="order-history-btn" @click="showOrderModal = true">
            <el-icon><Tickets /></el-icon> 我的购药订单
          </el-button>

          <!-- 用户身份区域 -->
          <div v-if="currentUser" class="mall-user-capsule">
            <el-avatar :size="30" class="user-avatar-badge">
              {{ (currentUser.realName || currentUser.username || '客').charAt(0) }}
            </el-avatar>
            <div class="user-info-text">
              <span class="user-name-title">{{ currentUser.realName || currentUser.username }}</span>
              <span class="user-tag">商城会员</span>
            </div>
            <el-button type="danger" link size="small" class="logout-link-btn" @click="handleLogoutMall" title="安全退出">
              <el-icon><SwitchButton /></el-icon> 退出
            </el-button>
          </div>
          <div v-else class="mall-guest-actions">
            <el-button type="success" round class="mall-login-trigger-btn" @click="openAuthDialog('login')">
              <el-icon><User /></el-icon> 登录 / 注册
            </el-button>
          </div>
        </div>
      </div>
    </header>

    <!-- 分类与家庭药箱快捷标签栏 -->
    <nav class="category-nav-bar">
      <div class="category-container">
        <div 
          v-for="cat in categories" 
          :key="cat.key" 
          class="cat-item"
          :class="{ active: currentCategory === cat.key }"
          @click="currentCategory = cat.key"
        >
          <span class="cat-icon">{{ cat.icon }}</span>
          <span class="cat-label">{{ cat.label }}</span>
        </div>
      </div>
    </nav>

    <!-- 主体区域：左侧 AI 问药药师 + 右侧生活药品货架 -->
    <main class="mall-main-content">
      <div class="mall-layout">
        <!-- 左侧：春播便民小药师 (AI 在线问药助手) -->
        <section class="ai-pharmacist-section">
          <div class="pharmacist-card">
            <div class="pharmacist-header">
              <div class="avatar-ring">
                <span class="avatar-emoji">👩‍⚕️</span>
                <span class="online-pulse"></span>
              </div>
              <div class="pharmacist-meta">
                <div class="doc-title">春播便民健康小药师 <el-tag size="small" type="success" effect="dark">AI在线指导</el-tag></div>
                <div class="doc-desc">24小时对症用药指导 · 穿透 MySQL 真实数据</div>
                <div class="doc-actions">
                  <el-button size="small" type="primary" plain round class="history-trigger-btn" @click="showSessionDrawer = true">
                    <el-icon><Clock /></el-icon> 历史会话 ({{ chatSessions.length }})
                  </el-button>
                </div>
              </div>
            </div>

            <!-- 常见生活问药快捷 Pills -->
            <div class="quick-question-zone">
              <div class="qq-title"><el-icon><ChatDotRound /></el-icon> 常见生活健康小疑问:</div>
              <div class="qq-tags">
                <el-tag 
                  v-for="(q, qIndex) in quickQuestions" 
                  :key="qIndex" 
                  class="qq-tag"
                  @click="askPharmacist(q)"
                >
                  {{ q }}
                </el-tag>
              </div>
            </div>

            <!-- 问答消息历史窗口 -->
            <div class="chat-messages-container" ref="chatScrollRef">
              <div v-for="(msg, mIndex) in chatMessages" :key="mIndex" class="chat-bubble-row" :class="msg.sender">
                <div class="msg-avatar">{{ msg.sender === 'user' ? '👤' : '👩‍⚕️' }}</div>
                <div class="msg-content-box">
                  <div class="msg-sender-name">{{ msg.sender === 'user' ? '我' : '春播健康小药师' }}</div>
                  <div class="msg-text markdown-body" v-html="renderMarkdown(msg.text)"></div>

                  <!-- 药师推荐药品快捷加购卡片 -->
                  <div v-if="msg.recommendations && msg.recommendations.length > 0" class="recommend-cards-wrap">
                    <div class="rec-card-title">💡 药师对症精选家庭常备好药:</div>
                    <div v-for="rec in msg.recommendations" :key="rec.id" class="rec-mini-card">
                      <div class="rec-info">
                        <span class="rec-name font-bold">{{ rec.productName }}</span>
                        <span class="rec-spec">{{ rec.specification }}</span>
                        <span class="rec-price">¥{{ rec.price }}</span>
                      </div>
                      <el-button type="success" size="small" @click="addRecToCart(rec)">
                        + 加入购物车
                      </el-button>
                    </div>
                  </div>
                </div>
              </div>

              <!-- 小药师思考中：MCP 工具调用过程展示 -->
              <div v-if="chatLoading" class="chat-bubble-row pharmacist">
                <div class="msg-avatar">👩‍⚕️</div>
                <div class="msg-content-box">
                  <div class="msg-sender-name">春播健康小药师</div>
                  <div class="msg-text thinking-box">
                    <div class="thinking-title">
                      <span class="dot-pulse"></span> 小药师思考中 · 正在调用 MCP 工具穿透真实数据：
                    </div>
                    <ul class="thinking-steps">
                      <li><code>mcp_query_real_mall_products()</code> 穿透 MySQL 商品库</li>
                      <li><code>mcp_query_mall_express_tracking()</code> 检索便民订单台账</li>
                      <li><code>mcp_contraindication_guard()</code> 用药配伍安全审查</li>
                    </ul>
                  </div>
                </div>
              </div>
            </div>

            <!-- 提问输入框 -->
            <div class="chat-input-zone">
              <el-input 
                v-model="userQueryText" 
                placeholder="描述身体不适或想买的药(如: 胃胀反酸吃什么好)..." 
                @keyup.enter="handleSendQuestion"
              >
                <template #append>
                  <el-button v-if="!chatLoading" type="primary" @click="handleSendQuestion">
                    咨询药师
                  </el-button>
                  <el-button v-else type="danger" @click="stopPharmacistGeneration">
                    ⏹ 停止
                  </el-button>
                </template>
              </el-input>
            </div>
          </div>
        </section>

        <!-- 右侧：生活常备药专区与药品卡片网格 -->
        <section class="products-grid-section">
          <!-- 专区公告横幅 -->
          <div class="mall-banner-strip">
            <div class="banner-badge">🚚 全民惠民</div>
            <div class="banner-text">全场生活药品满 <strong>¥68.00</strong> 即享顺丰冷链特快包邮 · 执业药师100%正品保障</div>
          </div>

          <!-- 药品卡片列表 -->
          <div class="products-grid">
            <div 
              v-for="p in filteredProducts" 
              :key="p.id" 
              class="product-card"
            >
              <div class="prod-badge-strip">
                <el-tag size="small" type="success" effect="plain">{{ p.category || '生活常备' }}</el-tag>
                <span class="prod-stock-tip">现货充足</span>
              </div>

              <!-- 商品实拍图 -->
              <div class="prod-img-box">
                <img
                  v-if="p.imageUrl"
                  :src="p.imageUrl"
                  :alt="p.productName"
                  class="prod-img"
                  loading="lazy"
                />
                <div v-else class="prod-img-placeholder">
                  <span class="ph-emoji">{{ categoryIcon(p.category) }}</span>
                  <span class="ph-text">商品图片上传后展示</span>
                </div>
              </div>

              <div class="prod-main-info">
                <h3 class="prod-name">{{ p.productName }}</h3>
                <div class="prod-generic">{{ p.genericName }} · {{ p.specification }}</div>
                <div class="prod-mfg"><el-icon><OfficeBuilding /></el-icon> {{ p.manufacturer }}</div>
                <p class="prod-pitch">{{ p.csPitch || p.directorPitch }}</p>
              </div>

              <div class="prod-action-bar">
                <div class="price-box">
                  <span class="currency">¥</span>
                  <span class="amount">{{ p.retailGuidePrice || p.wholesalePrice }}</span>
                  <span class="unit-text">/盒</span>
                </div>
                <div class="btn-group">
                  <el-button type="warning" plain size="small" @click="addToCart(p)">
                    <el-icon><ShoppingCart /></el-icon> 加购物车
                  </el-button>
                  <el-button type="primary" size="small" class="buy-now-btn" @click="quickBuy(p)">
                    立即购买
                  </el-button>
                </div>
              </div>
            </div>

            <div v-if="filteredProducts.length === 0" class="empty-products-box">
              <el-empty description="未找到符合条件的生活药品，您可以换个关键词或向左侧药师咨询推荐哦" />
            </div>
          </div>
        </section>
      </div>
    </main>

    <!-- 侧边栏：便民购药购物车与快速结算抽屉 -->
    <el-drawer 
      v-model="showCartDrawer" 
      title="🛒 我的购药便民清单" 
      size="440px"
      class="cart-drawer-custom"
    >
      <div class="cart-drawer-content">
        <!-- 配送地址概览 -->
        <div class="cart-address-box">
          <div class="box-top">
            <span class="font-bold"><el-icon><LocationInformation /></el-icon> 配送收货地址</span>
            <el-button type="primary" link size="small" @click="showAddressModal = true">更换</el-button>
          </div>
          <div class="box-receiver">{{ userAddress.name }} · {{ userAddress.phone }}</div>
          <div class="box-detail">{{ userAddress.province }}{{ userAddress.city }}{{ userAddress.district }}{{ userAddress.detail }}</div>
        </div>

        <!-- 购物车商品列表 -->
        <div class="cart-items-list">
          <div v-for="(item, idx) in cartItems" :key="item.id" class="cart-item-row">
            <div class="item-desc">
              <div class="item-name font-bold">{{ item.productName }}</div>
              <div class="item-spec">{{ item.specification }} · ¥{{ item.price }}/盒</div>
            </div>
            <div class="item-ctrls">
              <el-input-number 
                v-model="item.quantity" 
                :min="1" 
                :max="99" 
                size="small"
                @change="updateCartSum"
              />
              <el-button type="danger" link size="small" @click="removeCartItem(idx)">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
          </div>

          <div v-if="cartItems.length === 0" class="empty-cart-hint">
            <el-empty description="购物车空空如也，快去挑选中意的常备药吧" />
          </div>
        </div>

        <!-- 底部结算栏 -->
        <div class="cart-footer-checkout">
          <div class="fee-row">
            <span>药品小计:</span>
            <span class="font-bold">¥{{ cartTotal.toFixed(2) }}</span>
          </div>
          <div class="fee-row">
            <span>顺丰特快快递费:</span>
            <span :class="shippingFee === 0 ? 'text-green font-bold' : ''">
              {{ shippingFee === 0 ? '免运费 (满¥68包邮)' : '¥' + shippingFee.toFixed(2) }}
            </span>
          </div>
          <div class="fee-row total-row">
            <span>应付总金额:</span>
            <span class="final-price">¥{{ finalPayAmount.toFixed(2) }}</span>
          </div>

          <div class="pay-methods-row">
            <span class="label">支付方式:</span>
            <el-radio-group v-model="selectedPayType" size="small">
              <el-radio label="wechat">微信支付</el-radio>
              <el-radio label="alipay">支付宝</el-radio>
              <el-radio label="card">银联/医保在线</el-radio>
            </el-radio-group>
          </div>

          <el-button 
            type="primary" 
            size="large" 
            class="submit-order-btn" 
            :disabled="cartItems.length === 0"
            :loading="orderSubmitting"
            @click="handleSubmitConsumerOrder"
          >
            立即支付 ¥{{ finalPayAmount.toFixed(2) }} 并顺丰极速配送
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 弹窗：收货地址修改 -->
    <el-dialog v-model="showAddressModal" title="设置送药上门收货地址" width="500px">
      <el-form :model="userAddress" label-width="90px">
        <el-form-item label="收货人">
          <el-input v-model="userAddress.name" placeholder="请输入收货人姓名" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="userAddress.phone" placeholder="请输入手机号码" />
        </el-form-item>
        <el-form-item label="省市地区">
          <el-row :gutter="10">
            <el-col :span="8">
              <el-input v-model="userAddress.province" placeholder="省份" />
            </el-col>
            <el-col :span="8">
              <el-input v-model="userAddress.city" placeholder="城市" />
            </el-col>
            <el-col :span="8">
              <el-input v-model="userAddress.district" placeholder="区县" />
            </el-col>
          </el-row>
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="userAddress.detail" placeholder="街道、小区、楼栋门牌号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showAddressModal = false">取消</el-button>
        <el-button type="primary" @click="saveAddress">保存配送地址</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗：我的购药订单历史与顺丰追踪 -->
    <el-dialog v-model="showOrderModal" title="📦 我的生活购药订单与物流追踪" width="720px">
      <el-table :data="myOrders" stripe class="order-table-custom">
        <el-table-column prop="orderNo" label="订单号" width="160">
          <template #default="scope">
            <el-tag type="info" size="small">{{ scope.row.orderNo }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="clinicName" label="送达地址" min-width="180">
          <template #default="scope">
            <span>{{ scope.row.clinicName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="finalAmount" label="实付金额" width="100">
          <template #default="scope">
            <span class="font-bold text-red">¥{{ scope.row.finalAmount }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="配送状态" width="120">
          <template #default="scope">
            <el-tag type="success">{{ scope.row.status || '顺丰运输中' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="下单时间" width="160">
          <template #default="scope">
            <span>{{ formatTime(scope.row.createTime) }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="showOrderModal = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 弹窗：商城用户登录与注册 -->
    <el-dialog 
      v-model="showAuthModal" 
      :title="authMode === 'login' ? '🔑 登录春播健康商城' : '✨ 注册春播商城新用户'" 
      width="440px"
      destroy-on-close
      append-to-body
      class="mall-auth-dialog"
    >
      <el-tabs v-model="authMode" class="auth-tabs" stretch>
        <!-- 登录 TAB -->
        <el-tab-pane label="会员登录" name="login">
          <el-form label-position="top" class="auth-form">
            <el-form-item label="登录账号 / 手机号">
              <el-input 
                v-model="authForm.username" 
                placeholder="请输入用户名或11位手机号" 
                prefix-icon="User"
                size="large"
                clearable
              />
            </el-form-item>
            <el-form-item label="登录密码">
              <el-input 
                v-model="authForm.password" 
                type="password" 
                placeholder="请输入登录密码" 
                show-password 
                prefix-icon="Lock"
                size="large"
                @keyup.enter="handleLoginMall"
              />
            </el-form-item>
            <div class="auth-submit-bar">
              <el-button 
                type="success" 
                size="large" 
                class="auth-main-btn" 
                :loading="authLoading" 
                @click="handleLoginMall"
              >
                立即登录
              </el-button>
            </div>
            <div class="auth-switch-tip">
              还没有商城账号？
              <el-button link type="primary" @click="authMode = 'register'">免费注册新账号</el-button>
            </div>
          </el-form>
        </el-tab-pane>

        <!-- 注册 TAB -->
        <el-tab-pane label="新用户注册" name="register">
          <el-form label-position="top" class="auth-form">
            <el-form-item label="注册用户名 *" required>
              <el-input 
                v-model="regForm.username" 
                placeholder="用于登录的用户名 (如 user123)" 
                prefix-icon="User"
                size="default"
                clearable
              />
            </el-form-item>
            <el-form-item label="真实姓名 / 称呼 *" required>
              <el-input 
                v-model="regForm.realName" 
                placeholder="如: 张女士 / 李先生" 
                prefix-icon="Postcard"
                size="default"
              />
            </el-form-item>
            <el-form-item label="联系手机号 *" required>
              <el-input 
                v-model="regForm.phone" 
                placeholder="11位手机号 (用于收货物流短信)" 
                prefix-icon="Phone"
                size="default"
                maxlength="11"
              />
            </el-form-item>
            <el-form-item label="设置登录密码 *" required>
              <el-input 
                v-model="regForm.password" 
                type="password" 
                placeholder="请输入密码 (至少6位)" 
                show-password 
                prefix-icon="Lock"
                size="default"
              />
            </el-form-item>
            <el-form-item label="确认密码 *" required>
              <el-input 
                v-model="regForm.confirmPassword" 
                type="password" 
                placeholder="再次输入密码确认" 
                show-password 
                prefix-icon="Lock"
                size="default"
              />
            </el-form-item>
            <el-form-item label="常用收货地址 (可选)">
              <el-input 
                v-model="regForm.address" 
                type="textarea" 
                :rows="2"
                placeholder="如: 湖南省长沙市岳麓区中海国际社区3栋201" 
              />
            </el-form-item>
            <div class="auth-submit-bar">
              <el-button 
                type="success" 
                size="large" 
                class="auth-main-btn" 
                :loading="authLoading" 
                @click="handleRegisterMall"
              >
                注册并登录
              </el-button>
            </div>
            <div class="auth-switch-tip">
              已有账号？
              <el-button link type="primary" @click="authMode = 'login'">直接登录</el-button>
            </div>
          </el-form>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>

    <!-- 抽屉：小药师咨询历史会话 (分组：今天/本周/本月/今年/更早) -->
    <el-drawer
      v-model="showSessionDrawer"
      title="🕘 咨询历史会话"
      size="360px"
      direction="rtl"
      class="mall-history-drawer"
    >
      <div class="mhd-body">
        <div class="mhd-top">
          <span class="mhd-count">共 {{ chatSessions.length }} 场咨询会话</span>
          <div style="display: flex; gap: 6px;">
            <el-button size="small" type="danger" plain text @click="clearCurrentSession">🧹 清空当前</el-button>
            <el-button size="small" type="primary" @click="createNewSession">
              <el-icon><Plus /></el-icon> 新建会话
            </el-button>
          </div>
        </div>

        <template v-for="g in SESSION_GROUPS" :key="g.key">
          <div v-if="groupedSessions[g.key].length" class="mhd-group">
            <div class="mhd-group-label">{{ g.label }} · {{ groupedSessions[g.key].length }}</div>
            <div
              v-for="s in groupedSessions[g.key]"
              :key="s.id"
              class="mhd-card"
              :class="{ active: s.id === activeSessionId }"
              @click="switchSession(s.id)"
            >
              <div class="mhd-card-top">
                <span class="mhd-title" :title="s.title">{{ s.title }}</span>
                <span class="mhd-del" title="删除该会话" @click.stop="deleteSession(s.id)">×</span>
              </div>
              <div class="mhd-card-bottom">
                <span class="mhd-time">🕒 {{ formatSessionTime(s.updatedAt) }}</span>
                <el-tag size="small" :type="s.id === activeSessionId ? 'success' : 'info'">
                  {{ s.id === activeSessionId ? '当前会话' : ((s.messages ? s.messages.length : 0) + '条') }}
                </el-tag>
              </div>
            </div>
          </div>
        </template>

        <div v-if="!chatSessions.length" class="mhd-empty">暂无历史会话记录</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { marked } from 'marked'
import { ref, computed, onMounted, nextTick , watch } from 'vue'
import { ElMessage, ElNotification } from 'element-plus'
import axios from 'axios'

// 当前商城登录用户 (提升至顶部，彻底解决 TDZ 引用错误)
const currentUser = ref(null)

// 搜索与品类
const searchKeyword = ref('')
const currentCategory = ref('all')

const categories = [
  { key: 'all', label: '全部家庭好药', icon: '🌟' },
  { key: '感冒发热', label: '感冒发烧 / 止咳退热', icon: '🤧' },
  { key: '胃肠消化', label: '胃肠消化 / 腹泻止吐', icon: '💊' },
  { key: '跌打损伤', label: '跌打损伤 / 创可贴', icon: '🩹' },
  { key: '皮肤外用', label: '皮肤外用 / 蚊虫止痒', icon: '🌿' },
  { key: '滋补调理', label: '滋补调理 / 气血养生', icon: '🧘' },
  { key: '儿科健康', label: '小儿健康 / 家庭常备', icon: '👶' },
  { key: '家用器械', label: '家用器械 / 血压防护', icon: '🩺' }
]

// 用户收货地址
const showAddressModal = ref(false)
const userAddress = ref({
  name: '李先生',
  phone: '13812345678',
  province: '湖南省',
  city: '长沙市',
  district: '岳麓区',
  detail: '中海国际社区 3栋201室'
})

const saveAddress = () => {
  ElMessage.success('送药收货地址已更新保存！')
  showAddressModal.value = false
}

// 药品商品列表
const products = ref([])
const loadProducts = async () => {
  try {
    const res = await axios.get('/api/mall/products')
    products.value = res.data || []
  } catch (e) {
    console.error("加载商城商品失败:", e)
  }
}

// 分类占位图标（无图商品兜底展示）
const categoryIcon = (cat) => {
  const map = {
    '感冒发热': '🤧',
    '胃肠消化': '💊',
    '跌打损伤': '🩹',
    '外伤跌打': '🩹',
    '皮肤外用': '🌿',
    '皮肤用药': '🌿',
    '滋补调理': '🧘',
    '儿科健康': '👶',
    '家用器械': '🩺',
    '咳嗽咽痛': '🍯'
  }
  return map[cat] || '💊'
}

const filteredProducts = computed(() => {
  return products.value.filter(p => {
    // 过滤已下架商品 (由云中台管理的上下架状态)
    if (p.status === 'OFF_SALE') {
      return false
    }
    if (currentCategory.value !== 'all' && p.category !== currentCategory.value) {
      return false
    }
    if (searchKeyword.value) {
      const kw = searchKeyword.value.toLowerCase()
      const matchName = p.productName && p.productName.toLowerCase().includes(kw)
      const matchGen = p.genericName && p.genericName.toLowerCase().includes(kw)
      const matchPitch = p.csPitch && p.csPitch.toLowerCase().includes(kw)
      if (!matchName && !matchGen && !matchPitch) return false
    }
    return true
  })
})

// 购物车状态
const showCartDrawer = ref(false)
const cartItems = ref([])
const selectedPayType = ref('wechat')
const orderSubmitting = ref(false)

const cartCount = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + item.quantity, 0)
})

const cartTotal = computed(() => {
  return cartItems.value.reduce((sum, item) => sum + (item.price * item.quantity), 0)
})

const shippingFee = computed(() => {
  if (cartTotal.value === 0) return 0
  return cartTotal.value >= 68 ? 0 : 8.00
})

const finalPayAmount = computed(() => {
  if (cartTotal.value === 0) return 0
  return cartTotal.value + shippingFee.value
})

const addToCart = (product) => {
  const price = Number(product.retailGuidePrice || product.wholesalePrice || 20)
  const existing = cartItems.value.find(item => item.id === product.id)
  if (existing) {
    existing.quantity += 1
  } else {
    cartItems.value.push({
      id: product.id,
      productName: product.productName,
      specification: product.specification,
      price: price,
      quantity: 1
    })
  }
  ElMessage.success(`已将【${product.productName}】加入购物车！`)
}

const addRecToCart = (rec) => {
  addToCart({
    id: rec.id,
    productName: rec.productName,
    specification: rec.specification,
    retailGuidePrice: rec.price
  })
}

const quickBuy = (product) => {
  addToCart(product)
  showCartDrawer.value = true
}

const removeCartItem = (index) => {
  cartItems.value.splice(index, 1)
}

const updateCartSum = () => {
  // trigger reactivity
}

// 提交订单
const showOrderModal = ref(false)
const myOrders = ref([])

const loadOrders = async () => {
  try {
    const res = await axios.get('/api/mall/orders')
    myOrders.value = res.data || []
  } catch (e) {}
}

const handleSubmitConsumerOrder = async () => {
  if (cartItems.value.length === 0) return
  if (!currentUser.value) {
    ElMessage.warning('购买生活药品请先登录或注册春播商城账号！')
    openAuthDialog('login')
    return
  }
  orderSubmitting.value = true
  try {
    const payload = {
      buyerName: `${userAddress.value.name} (${userAddress.value.phone})`,
      address: `${userAddress.value.province}${userAddress.value.city}${userAddress.value.district}${userAddress.value.detail}`,
      totalAmount: cartTotal.value.toFixed(2),
      discountAmount: '0.00',
      finalAmount: finalPayAmount.value.toFixed(2),
      notes: `生活购药订单 · 支付方式: ${selectedPayType.value} · 满68顺丰包邮`,
      itemsJson: JSON.stringify(cartItems.value)
    }
    const res = await axios.post('/api/mall/order/create', payload)
    const newOrder = res.data

    ElNotification({
      title: '🎉 购药下单成功！顺丰特快揽收中',
      message: `订单编号：${newOrder.orderNo}，药品将极速送达至【${userAddress.value.detail}】！`,
      type: 'success',
      duration: 6000
    })

    cartItems.value = []
    showCartDrawer.value = false
    await loadOrders()
  } catch (e) {
    ElMessage.error('下单遇到错误，请重试')
  } finally {
    orderSubmitting.value = false
  }
}

// AI 在线便民小药师聊天
const chatScrollRef = ref(null)
const userQueryText = ref('')
const chatLoading = ref(false)

const quickQuestions = [
  '📦 查我的便民速递订单与物流进度',
  '📋 便民药房在售药品与价格清单',
  '🚨 布洛芬和复方感冒胶囊能一起吃吗？',
  '🚚 春播便民速运怎么配送？满多少包邮？',
  '🤧 有点受凉流清涕、低烧咳嗽，该买什么药？',
  '🍲 晚餐暴饮暴食胃胀反酸，推荐什么消食药？',
  '🩹 运动扭伤擦伤，用什么药喷雾冷敷？'
]


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

// 会话身份隔离标识（按登录手机号/用户名，未登录则 guest）
const getIdentity = () => currentUser.value?.phone || currentUser.value?.username || 'guest'
const getSessionsKey = () => 'chunbo_mall_sessions_' + getIdentity()
const getActiveKey = () => 'chunbo_mall_active_session_' + getIdentity()
const getLegacyKey = () => 'chunbo_mall_chat_' + getIdentity()

const getDefaultPharmacistWelcome = () => [
  {
    sender: 'pharmacist',
    text: `您好${currentUser.value?.realName ? ' **' + currentUser.value.realName + '**' : ''}！我是您的 24 小时 **春播便民在线药师**。\n\n系统已为您载入**隔离的专属会话记忆空间**。生活中遇到任何感冒发热、咽痛咳嗽、胃肠不适、跌打外伤或家庭常备药问题，都可以随时向我咨询！\n\n- 🚚 输入 **「查我的订单」**：实时查询便民速递配送节点\n- 📋 输入 **「价格表」**：查看在售正品直供药品清单与惠民单价\n- 🚨 输入 **「两种药能一起吃吗」**：触发药师用药配伍与安全禁忌审查\n- 📦 输入 **「满多少包邮」**：查询春播便民速运配送与免邮政策`
  }
]

// 多会话历史：chatSessions 数组 + 当前激活会话 id
const chatSessions = ref([])
const activeSessionId = ref('')
const showSessionDrawer = ref(false)

const activeSession = computed(() => chatSessions.value.find(s => s.id === activeSessionId.value) || null)

// 会话按时间分组：今天 / 本周 / 本月 / 今年 / 更早
const SESSION_GROUPS = [
  { key: 'today', label: '今天' },
  { key: 'week', label: '本周' },
  { key: 'month', label: '本月' },
  { key: 'year', label: '今年' },
  { key: 'earlier', label: '更早' }
]

const groupedSessions = computed(() => {
  const groups = { today: [], week: [], month: [], year: [], earlier: [] }
  const now = new Date()
  const dayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const weekStart = dayStart - ((now.getDay() + 6) % 7) * 86400000 // 周一为一周起点
  const monthStart = new Date(now.getFullYear(), now.getMonth(), 1).getTime()
  const yearStart = new Date(now.getFullYear(), 0, 1).getTime()
  for (const s of chatSessions.value) {
    const t = s.updatedAt || s.createdAt || 0
    if (t >= dayStart) groups.today.push(s)
    else if (t >= weekStart) groups.week.push(s)
    else if (t >= monthStart) groups.month.push(s)
    else if (t >= yearStart) groups.year.push(s)
    else groups.earlier.push(s)
  }
  return groups
})

const chatMessages = computed({
  get: () => activeSession.value ? activeSession.value.messages : [],
  set: (v) => { if (activeSession.value) activeSession.value.messages = v }
})

const persistSessions = () => {
  try {
    localStorage.setItem(getSessionsKey(), JSON.stringify(chatSessions.value))
    localStorage.setItem(getActiveKey(), activeSessionId.value || '')
  } catch (e) {}
}

const makeSession = (title, messages) => ({
  id: 'sess_' + Date.now() + '_' + Math.random().toString(36).slice(2, 6),
  title,
  createdAt: Date.now(),
  updatedAt: Date.now(),
  messages: messages || getDefaultPharmacistWelcome()
})

const loadSessions = () => {
  let sessions = []
  try {
    const raw = localStorage.getItem(getSessionsKey())
    if (raw) {
      const parsed = JSON.parse(raw)
      if (Array.isArray(parsed)) sessions = parsed.filter(s => s && s.id && Array.isArray(s.messages))
    }
  } catch (e) {}

  // 兼容旧版：单 key 历史迁移为第一个会话
  if (sessions.length === 0) {
    try {
      const legacy = localStorage.getItem(getLegacyKey())
      if (legacy) {
        const parsed = JSON.parse(legacy)
        if (Array.isArray(parsed) && parsed.length > 0) {
          sessions = [makeSession('历史咨询记录', parsed)]
          localStorage.removeItem(getLegacyKey())
        }
      }
    } catch (e) {}
  }

  if (sessions.length === 0) {
    sessions = [makeSession('默认咨询', getDefaultPharmacistWelcome())]
  }

  chatSessions.value = sessions

  const savedActive = localStorage.getItem(getActiveKey())
  if (savedActive && sessions.some(s => s.id === savedActive)) {
    activeSessionId.value = savedActive
  } else {
    activeSessionId.value = sessions[0].id
  }
  persistSessions()
}

const createNewSession = () => {
  const s = makeSession('新会话')
  chatSessions.value.unshift(s)
  activeSessionId.value = s.id
  persistSessions()
  showSessionDrawer.value = false
  scrollToBottom()
  ElMessage.success('已新建一个咨询会话')
}

const switchSession = (id) => {
  if (activeSessionId.value === id) return
  activeSessionId.value = id
  persistSessions()
  showSessionDrawer.value = false
  scrollToBottom()
}

const deleteSession = (id) => {
  const idx = chatSessions.value.findIndex(s => s.id === id)
  if (idx === -1) return
  chatSessions.value.splice(idx, 1)
  if (chatSessions.value.length === 0) {
    const s = makeSession('默认咨询', getDefaultPharmacistWelcome())
    chatSessions.value.push(s)
  }
  if (activeSessionId.value === id) {
    activeSessionId.value = chatSessions.value[0].id
  }
  persistSessions()
  ElMessage.success('已删除该会话')
}

const clearCurrentSession = () => {
  if (activeSession.value) {
    activeSession.value.messages = getDefaultPharmacistWelcome()
    activeSession.value.title = '新会话'
    activeSession.value.updatedAt = Date.now()
  }
  persistSessions()
  ElMessage.success('已清空当前会话内容')
}

const formatSessionTime = (ts) => {
  if (!ts) return ''
  const d = new Date(ts)
  const now = new Date()
  const pad = (n) => (n < 10 ? '0' + n : n)
  const hm = pad(d.getHours()) + ':' + pad(d.getMinutes())
  if (d.toDateString() === now.toDateString()) return hm
  return (d.getMonth() + 1) + '/' + d.getDate() + ' ' + hm
}

watch(currentUser, () => {
  loadSessions()
}, { deep: true })

watch(chatSessions, () => {
  persistSessions()
}, { deep: true })


const scrollToBottom = () => {
  nextTick(() => {
    if (chatScrollRef.value) {
      chatScrollRef.value.scrollTop = chatScrollRef.value.scrollHeight
    }
  })
}

const askPharmacist = (question) => {
  userQueryText.value = question.replace(/^[^\w一-龥]+/, '')
  handleSendQuestion()
}

const handleSendQuestion = async () => {
  const q = userQueryText.value.trim()
  if (!q) return

  chatMessages.value.push({
    sender: 'user',
    text: q
  })
  // 首个用户问题自动生成会话标题
  if (activeSession.value) {
    const hasUserMsg = activeSession.value.messages.some(m => m.sender === 'user')
    if (!hasUserMsg || activeSession.value.title === '新会话' || activeSession.value.title === '默认咨询') {
      activeSession.value.title = q.length > 14 ? q.slice(0, 14) + '…' : q
    }
    activeSession.value.updatedAt = Date.now()
  }
  userQueryText.value = ''
  scrollToBottom()

  chatLoading.value = true
  const abort = new AbortController()
  pharmacistAbort = abort
  try {
    const res = await axios.post('/api/mall/chat', {
      message: q,
      role: 'consumer',
      phone: currentUser.value?.phone || '',
      userName: currentUser.value?.realName || currentUser.value?.username || '居民顾客'
    }, { signal: abort.signal })
    const data = res.data || {}
    chatMessages.value.push({
      sender: 'pharmacist',
      text: data.reply || '已为您分析生活用药方案。',
      recommendations: data.recommendations || []
    })
  } catch (e) {
    if (e.name !== 'CanceledError' && e.code !== 'ERR_CANCELED') {
      chatMessages.value.push({
        sender: 'pharmacist',
        text: '您好！遇到身体不适，建议多喝温开水清淡饮食。如需用药可参考右侧分类选品货架或随时再向我咨询。'
      })
    }
  } finally {
    if (pharmacistAbort === abort) pharmacistAbort = null
    if (activeSession.value) activeSession.value.updatedAt = Date.now()
    chatLoading.value = false
    scrollToBottom()
  }
}

// ── 停止药师生成（取消当前请求） ──
let pharmacistAbort = null
const stopPharmacistGeneration = () => {
  if (pharmacistAbort) {
    pharmacistAbort.abort()
    pharmacistAbort = null
  }
  chatLoading.value = false
  scrollToBottom()
}

const formatTime = (timeStr) => {
  if (!timeStr) return ''
  return timeStr.replace('T', ' ').substring(0, 16)
}


// 商城用户认证与登录状态
const showAuthModal = ref(false)
const authMode = ref('login') // 'login' | 'register'
const authLoading = ref(false)
// currentUser moved to top

const authForm = ref({
  username: '',
  password: ''
})

const regForm = ref({
  username: '',
  realName: '',
  phone: '',
  password: '',
  confirmPassword: '',
  address: ''
})

const initMallUser = () => {
  try {
    const saved = localStorage.getItem('mall_user')
    if (saved) {
      const user = JSON.parse(saved)
      currentUser.value = user
      if (user.realName) userAddress.value.name = user.realName
      if (user.phone) userAddress.value.phone = user.phone
      if (user.address) userAddress.value.detail = user.address
    }
  } catch (e) {}
}

const openAuthDialog = (mode = 'login') => {
  authMode.value = mode
  showAuthModal.value = true
}

const handleLoginMall = async () => {
  if (!authForm.value.username || !authForm.value.password) {
    ElMessage.warning('请输入用户名/手机号和密码！')
    return
  }
  authLoading.value = true
  try {
    const res = await axios.post('/api/mall/user/login', {
      username: authForm.value.username.trim(),
      password: authForm.value.password
    })
    const data = res.data
    if (data && (data.success || data.code === 200) && data.user) {
      currentUser.value = data.user
      localStorage.setItem('mall_user', JSON.stringify(data.user))
      localStorage.setItem('mall_token', data.token)
      if (data.user.realName) userAddress.value.name = data.user.realName
      if (data.user.phone) userAddress.value.phone = data.user.phone
      if (data.user.address) userAddress.value.detail = data.user.address

      ElMessage.success(`欢迎您，${data.user.realName || data.user.username}！已登录春播商城`)
      showAuthModal.value = false
      authForm.value.password = ''
      loadOrders()
    } else {
      ElMessage.error(data.message || '登录失败，请检查账号或密码')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '登录失败，账号或密码错误')
  } finally {
    authLoading.value = false
  }
}

const handleRegisterMall = async () => {
  const f = regForm.value
  if (!f.username || !f.realName || !f.phone || !f.password) {
    ElMessage.warning('请填写必填项（用户名、姓名、手机号、密码）！')
    return
  }
  if (f.password.length < 6) {
    ElMessage.warning('密码长度至少6位！')
    return
  }
  if (f.password !== f.confirmPassword) {
    ElMessage.warning('两次输入的密码不一致！')
    return
  }

  authLoading.value = true
  try {
    const res = await axios.post('/api/mall/user/register', {
      username: f.username.trim(),
      realName: f.realName.trim(),
      phone: f.phone.trim(),
      password: f.password,
      address: f.address || ''
    })
    const data = res.data
    if (data && (data.success || data.code === 200) && data.user) {
      currentUser.value = data.user
      localStorage.setItem('mall_user', JSON.stringify(data.user))
      localStorage.setItem('mall_token', data.token)
      if (data.user.realName) userAddress.value.name = data.user.realName
      if (data.user.phone) userAddress.value.phone = data.user.phone
      if (data.user.address) userAddress.value.detail = data.user.address

      ElMessage.success('🎉 注册成功，欢迎使用春播便民网上药房！')
      showAuthModal.value = false
      // 清空表单
      regForm.value = { username: '', realName: '', phone: '', password: '', confirmPassword: '', address: '' }
      loadOrders()
    } else {
      ElMessage.error(data.message || '注册失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '注册遇到错误，该用户名或手机号可能已被占用')
  } finally {
    authLoading.value = false
  }
}

const handleLogoutMall = () => {
  currentUser.value = null
  localStorage.removeItem('mall_user')
  localStorage.removeItem('mall_token')
  ElMessage.info('已安全退出商城登录')
}

onMounted(() => {
  initMallUser()
  loadProducts()
  loadOrders()
  loadSessions()
})
</script>

<style scoped>
#mall-app {
  height: 100vh;
  overflow: hidden;
  background: #f8fafc;
  color: #1e293b;
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
  display: flex;
  flex-direction: column;
}

/* 顶部便民药房导航 */
.mall-header {
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.03);
  position: sticky;
  top: 0;
  z-index: 100;
}

.header-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 14px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}

.brand-zone {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo-box {
  width: 44px;
  height: 44px;
  background: linear-gradient(135deg, #10b981, #059669);
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24px;
  box-shadow: 0 4px 10px rgba(16, 185, 129, 0.3);
}

.brand-title {
  font-size: 18px;
  font-weight: 800;
  color: #065f46;
  letter-spacing: -0.5px;
}

.brand-sub {
  font-size: 12px;
  color: #64748b;
  margin-top: 2px;
}

.search-bar-wrap {
  flex: 1;
  max-width: 480px;
}

.consumer-search-input :deep(.el-input__wrapper) {
  border-radius: 24px;
  padding: 4px 16px;
  box-shadow: 0 0 0 1px #cbd5e1 inset;
}

.consumer-search-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 2px #10b981 inset !important;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.address-capsule {
  display: flex;
  align-items: center;
  gap: 6px;
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 12px;
  color: #166534;
  cursor: pointer;
  transition: all 0.2s;
}

.address-capsule:hover {
  background: #dcfce7;
}

.address-text {
  max-width: 180px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-receiver {
  font-weight: 700;
}

.cart-btn {
  background: linear-gradient(135deg, #10b981, #059669);
  border: none;
  font-weight: 700;
  border-radius: 20px;
  padding: 8px 18px;
}

.cart-price-sum {
  margin-left: 6px;
  background: rgba(255, 255, 255, 0.25);
  padding: 1px 6px;
  border-radius: 10px;
  font-size: 12px;
}

/* 分类导航条：固定在头部下方，不随内容滚动 */
.category-nav-bar {
  background: #ffffff;
  border-bottom: 1px solid #edf2f7;
  flex-shrink: 0;
  box-shadow: 0 2px 6px rgba(0, 0, 0, 0.03);
}

.category-container {
  max-width: 1400px;
  margin: 0 auto;
  padding: 0 24px;
  display: flex;
  align-items: center;
  gap: 8px;
  overflow-x: auto;
}

.cat-item {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 12px 16px;
  font-size: 14px;
  font-weight: 600;
  color: #475569;
  cursor: pointer;
  border-bottom: 3px solid transparent;
  transition: all 0.2s;
  white-space: nowrap;
}

.cat-item:hover {
  color: #059669;
}

.cat-item.active {
  color: #059669;
  border-bottom-color: #059669;
}

/* 主体布局：App 壳式 —— 顶部固定，左列智能体固定，右列药品独立滚动 */
.mall-main-content {
  flex: 1;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
  padding: 16px 24px 12px;
  overflow: hidden;
  min-height: 0;
}

.mall-layout {
  height: 100%;
  display: grid;
  grid-template-columns: 420px 1fr;
  gap: 24px;
  align-items: stretch;
  min-height: 0;
}

.ai-pharmacist-section {
  min-height: 0;
  height: 100%;
}

/* 左侧：药师专区（固定占满，内部自滚） */
.pharmacist-card {
  background: #ffffff;
  border-radius: 16px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.03);
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
}

.pharmacist-header {
  padding: 16px 20px;
  background: linear-gradient(135deg, #ecfdf5, #d1fae5);
  border-radius: 16px 16px 0 0;
  border-bottom: 1px solid #a7f3d0;
  display: flex;
  align-items: center;
  gap: 14px;
}

.avatar-ring {
  width: 46px;
  height: 46px;
  background: #ffffff;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26px;
  box-shadow: 0 2px 8px rgba(5, 150, 105, 0.2);
  position: relative;
}

.online-pulse {
  position: absolute;
  bottom: 0;
  right: 0;
  width: 12px;
  height: 12px;
  background: #10b981;
  border: 2px solid #ffffff;
  border-radius: 50%;
}

.doc-title {
  font-size: 15px;
  font-weight: 800;
  color: #065f46;
  display: flex;
  align-items: center;
  gap: 8px;
  white-space: nowrap;
}

.doc-desc {
  font-size: 12px;
  color: #047857;
  margin-top: 3px;
  line-height: 1.5;
}

.doc-actions {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}

.pharmacist-meta {
  flex: 1;
  min-width: 0;
}

/* 历史会话按钮 */
.history-trigger-btn {
  font-weight: 700;
  flex-shrink: 0;
}

/* 历史会话抽屉 */
.mhd-body {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mhd-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-bottom: 10px;
  border-bottom: 1px solid #f1f5f9;
}

.mhd-count {
  font-size: 12px;
  color: #64748b;
  font-weight: 600;
}

.mhd-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.mhd-group-label {
  font-size: 12px;
  font-weight: 700;
  color: #94a3b8;
  padding-bottom: 3px;
  border-bottom: 1px dashed #e2e8f0;
}

.mhd-card {
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px 12px;
  cursor: pointer;
  transition: all 0.15s;
  background: #ffffff;
}

.mhd-card:hover {
  border-color: #34d399;
  background: #f0fdf4;
}

.mhd-card.active {
  border-color: #059669;
  background: #d1fae5;
}

.mhd-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.mhd-title {
  font-size: 13px;
  font-weight: 700;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
}

.mhd-card.active .mhd-title {
  color: #065f46;
}

.mhd-del {
  flex-shrink: 0;
  width: 18px;
  height: 18px;
  line-height: 16px;
  text-align: center;
  border-radius: 50%;
  color: #94a3b8;
  font-weight: 700;
  font-size: 13px;
}

.mhd-del:hover {
  background: #ef4444;
  color: #ffffff;
}

.mhd-card-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 6px;
}

.mhd-time {
  font-size: 11px;
  color: #94a3b8;
}

.mhd-empty {
  text-align: center;
  color: #94a3b8;
  padding: 40px 0;
  font-size: 13px;
}

.quick-question-zone {
  padding: 12px 16px;
  background: #f8fafc;
  border-bottom: 1px dashed #e2e8f0;
}

.qq-title {
  font-size: 12px;
  font-weight: 700;
  color: #64748b;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.qq-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.qq-tag {
  cursor: pointer;
  background: #ffffff;
  border: 1px solid #cbd5e1;
  color: #334155;
  font-size: 12px;
  border-radius: 12px;
  transition: all 0.2s;
}

.qq-tag:hover {
  background: #ecfdf5;
  border-color: #34d399;
  color: #059669;
}

.chat-messages-container {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.chat-bubble-row {
  display: flex;
  gap: 10px;
}

.chat-bubble-row.user {
  flex-direction: row-reverse;
}

.msg-avatar {
  font-size: 24px;
}

.msg-content-box {
  max-width: 82%;
}

.chat-bubble-row.pharmacist .msg-content-box {
  max-width: 96%;
}

.msg-sender-name {
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 4px;
}

.chat-bubble-row.user .msg-sender-name {
  text-align: right;
}

.msg-text {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.6;
  overflow-wrap: break-word;
}

.chat-bubble-row.pharmacist .msg-text {
  background: #f1f5f9;
  color: #1e293b;
  border-top-left-radius: 2px;
}

.chat-bubble-row.user .msg-text {
  background: linear-gradient(135deg, #10b981, #059669);
  color: #ffffff;
  border-top-right-radius: 2px;
}

/* ── Markdown 富文本排版（标题/列表/引用/代码/表格） ── */
.msg-text.markdown-body h1,
.msg-text.markdown-body h2,
.msg-text.markdown-body h3,
.msg-text.markdown-body h4 {
  margin: 8px 0 6px;
  font-weight: 800;
  line-height: 1.4;
}
.msg-text.markdown-body h3 { font-size: 14px; color: #065f46; }
.msg-text.markdown-body h4 { font-size: 13px; color: #065f46; }

.msg-text.markdown-body p { margin: 6px 0; }
.msg-text.markdown-body ul,
.msg-text.markdown-body ol { margin: 6px 0; padding-left: 20px; }
.msg-text.markdown-body li { margin: 3px 0; }

.msg-text.markdown-body blockquote {
  margin: 8px 0;
  padding: 6px 12px;
  border-left: 3px solid #34d399;
  background: #f0fdf4;
  color: #166534;
  border-radius: 0 6px 6px 0;
}

.msg-text.markdown-body code {
  background: #e2e8f0;
  color: #0f172a;
  padding: 1px 5px;
  border-radius: 4px;
  font-size: 12px;
  font-family: "SFMono-Regular", Consolas, "Liberation Mono", Menlo, monospace;
}

.msg-text.markdown-body strong { font-weight: 800; color: #0f172a; }

/* 表格横向滚动容器 + 单元格样式，避免窄气泡里被压成竖排单字 */
.md-table-scroll {
  overflow-x: auto;
  margin: 8px 0;
  border-radius: 6px;
  border: 1px solid #e2e8f0;
}

.msg-text.markdown-body table {
  border-collapse: collapse;
  width: 100%;
  font-size: 12px;
  line-height: 1.5;
}

.msg-text.markdown-body th,
.msg-text.markdown-body td {
  border: 1px solid #e2e8f0;
  padding: 6px 8px;
  text-align: left;
  white-space: nowrap;
  vertical-align: top;
}

.msg-text.markdown-body th {
  background: #f1f5f9;
  font-weight: 700;
  color: #334155;
}

.msg-text.markdown-body tbody tr:nth-child(even) {
  background: #fafafa;
}

.recommend-cards-wrap {
  margin-top: 10px;
  background: #ffffff;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 10px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

/* 思考中：MCP 工具调用展示 */
.thinking-box {
  background: #f8fafc;
  border: 1px dashed #cbd5e1;
}

.thinking-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  font-weight: 700;
  color: #475569;
  margin-bottom: 6px;
}

.dot-pulse {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #10b981;
  animation: dotPulse 1s ease-in-out infinite;
  flex-shrink: 0;
}

@keyframes dotPulse {
  0%, 100% { opacity: 0.3; transform: scale(0.8); }
  50% { opacity: 1; transform: scale(1.2); }
}

.thinking-steps {
  margin: 0;
  padding-left: 18px;
  font-size: 11.5px;
  color: #64748b;
}

.thinking-steps li {
  margin: 3px 0;
}

.thinking-steps code {
  background: #e2e8f0;
  color: #0f172a;
  padding: 1px 4px;
  border-radius: 4px;
  font-size: 10.5px;
}

.rec-card-title {
  font-size: 11px;
  font-weight: 700;
  color: #059669;
}

.rec-mini-card {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8fafc;
  padding: 8px 10px;
  border-radius: 8px;
}

.rec-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.rec-name {
  font-size: 12px;
  color: #0f172a;
}

.rec-spec {
  font-size: 11px;
  color: #64748b;
}

.rec-price {
  font-size: 12px;
  font-weight: 700;
  color: #e11d48;
}

.chat-input-zone {
  padding: 12px 16px;
  border-top: 1px solid #e2e8f0;
  background: #ffffff;
  border-radius: 0 0 16px 16px;
}

/* 右侧货架：独立滚动区域 */
.products-grid-section {
  min-height: 0;
  height: 100%;
  overflow-y: auto;
  padding-right: 6px;
  padding-bottom: 12px;
}

.products-grid-section::-webkit-scrollbar {
  width: 8px;
}

.products-grid-section::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 4px;
}

.products-grid-section::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

/* 右侧货架 */
.mall-banner-strip {
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  border: 1px solid #fcd34d;
  border-radius: 12px;
  padding: 12px 18px;
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
}

.banner-badge {
  background: #d97706;
  color: #ffffff;
  font-size: 11px;
  font-weight: 800;
  padding: 3px 8px;
  border-radius: 6px;
}

.banner-text {
  font-size: 13px;
  color: #92400e;
}

.products-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 18px;
}

.product-card {
  background: #ffffff;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  padding: 16px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  gap: 14px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.02);
  transition: all 0.25s ease;
}

.product-card:hover {
  transform: translateY(-3px);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.06);
  border-color: #cbd5e1;
}

.prod-badge-strip {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

/* 商品实拍图 */
.prod-img-box {
  height: 170px;
  border-radius: 10px;
  overflow: hidden;
  background: #f8fafc;
  border: 1px solid #f1f5f9;
}

.prod-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.3s ease;
}

.product-card:hover .prod-img {
  transform: scale(1.04);
}

.prod-img-placeholder {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  background: linear-gradient(135deg, #f8fafc, #f1f5f9);
}

.ph-emoji {
  font-size: 40px;
}

.ph-text {
  font-size: 11px;
  color: #94a3b8;
}

.prod-stock-tip {
  font-size: 11px;
  color: #10b981;
  font-weight: 600;
}

.prod-name {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
  margin: 0 0 6px 0;
  line-height: 1.4;
}

.prod-generic {
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}

.prod-mfg {
  font-size: 11px;
  color: #94a3b8;
  margin-bottom: 8px;
  display: flex;
  align-items: center;
  gap: 4px;
}

.prod-pitch {
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
  background: #f8fafc;
  padding: 8px 10px;
  border-radius: 8px;
  margin: 0;
}

.prod-action-bar {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  border-top: 1px dashed #f1f5f9;
  padding-top: 12px;
}

.price-box {
  color: #e11d48;
}

.price-box .currency {
  font-size: 14px;
  font-weight: 700;
}

.price-box .amount {
  font-size: 22px;
  font-weight: 800;
}

.price-box .unit-text {
  font-size: 11px;
  color: #94a3b8;
}

.btn-group {
  display: flex;
  gap: 6px;
}

.buy-now-btn {
  background: linear-gradient(135deg, #e11d48, #be123c);
  border: none;
  font-weight: 700;
}

/* 购物车抽屉 */
.cart-drawer-content {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.cart-address-box {
  background: #f0fdf4;
  border: 1px solid #bbf7d0;
  border-radius: 10px;
  padding: 12px;
  margin-bottom: 16px;
}

.cart-address-box .box-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #166534;
  margin-bottom: 4px;
}

.cart-address-box .box-receiver {
  font-size: 13px;
  font-weight: 700;
  color: #0f172a;
}

.cart-address-box .box-detail {
  font-size: 12px;
  color: #475569;
  margin-top: 2px;
}

.cart-items-list {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cart-item-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8fafc;
  padding: 10px 12px;
  border-radius: 8px;
  border: 1px solid #e2e8f0;
}

.item-name {
  font-size: 13px;
  color: #0f172a;
}

.item-spec {
  font-size: 11px;
  color: #64748b;
  margin-top: 2px;
}

.item-ctrls {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cart-footer-checkout {
  border-top: 1px solid #e2e8f0;
  padding-top: 16px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.fee-row {
  display: flex;
  justify-content: space-between;
  font-size: 13px;
  color: #64748b;
}

.fee-row.total-row {
  font-size: 16px;
  font-weight: 800;
  color: #0f172a;
  border-top: 1px dashed #e2e8f0;
  padding-top: 8px;
}

.final-price {
  color: #e11d48;
  font-size: 22px;
}

.pay-methods-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: #475569;
  margin-top: 4px;
}

.submit-order-btn {
  background: linear-gradient(135deg, #10b981, #059669);
  border: none;
  font-weight: 800;
  font-size: 15px;
  margin-top: 8px;
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

.text-green {
  color: #10b981;
}

.text-red {
  color: #e11d48;
}

.empty-products-box {
  grid-column: 1 / -1;
  background: #ffffff;
  border-radius: 12px;
  padding: 40px;
}

/* 用户登录会员状态胶囊 */
.mall-user-capsule {
  display: flex;
  align-items: center;
  gap: 8px;
  background: #f0fdf4;
  border: 1px solid #a7f3d0;
  padding: 4px 12px;
  border-radius: 20px;
}

.user-avatar-badge {
  background: linear-gradient(135deg, #10b981, #059669);
  color: #ffffff;
  font-weight: 700;
  font-size: 13px;
}

.user-info-text {
  display: flex;
  flex-direction: column;
}

.user-name-title {
  font-size: 13px;
  font-weight: 700;
  color: #065f46;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-tag {
  font-size: 10px;
  color: #059669;
  background: #d1fae5;
  padding: 0 4px;
  border-radius: 4px;
  width: fit-content;
}

.logout-link-btn {
  font-size: 12px;
  margin-left: 4px;
  padding: 0 4px;
}

.mall-login-trigger-btn {
  font-weight: 700;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.2);
}

.auth-tabs :deep(.el-tabs__item.is-active) {
  color: #059669 !important;
  font-weight: bold;
}

.auth-tabs :deep(.el-tabs__active-bar) {
  background-color: #059669 !important;
}

.auth-form {
  padding: 10px 8px 0;
}

.auth-main-btn {
  width: 100%;
  font-weight: 700;
  letter-spacing: 1px;
  background: linear-gradient(135deg, #10b981, #059669);
  border: none;
}

.auth-switch-tip {
  margin-top: 14px;
  text-align: center;
  font-size: 13px;
  color: #64748b;
}

.stock-tag-custom {
  font-size: 11px;
  margin-left: 6px;
  color: #059669;
}
</style>
