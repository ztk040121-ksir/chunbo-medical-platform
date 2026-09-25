<template>
  <div class="tab-pane">
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
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElNotification } from 'element-plus'
import { makeListPager, parseOrderItems, batchDeleteRows } from '../utils/common.js'

const mallUsers = ref([])
const mallUsersLoading = ref(false)
const showUserOrderDrawer = ref(false)
const userOrders = ref([])
const userSelection = ref([])
const showAddMallUserDialog = ref(false)
const addMallUserLoading = ref(false)
const newMallUserForm = ref({ username: '', password: '123456', nickname: '', phone: '', address: '' })
const usersPager = makeListPager(mallUsers, ['username', 'phone', 'status'])

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

onMounted(() => { loadMallUsers() })
</script>
