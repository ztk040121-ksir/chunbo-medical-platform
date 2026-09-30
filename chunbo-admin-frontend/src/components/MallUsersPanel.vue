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
        <el-table-column label="头像" width="70" align="center">
          <template #default="scope">
            <el-avatar :size="34" :src="resolveAvatarUrl(scope.row.avatar)" style="background: #10B981; color: #fff; font-weight: bold;">
              {{ (scope.row.nickname || scope.row.username || '用').substring(0, 1) }}
            </el-avatar>
          </template>
        </el-table-column>
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
        <el-table-column label="用户管控" width="220" fixed="right">
          <template #default="scope">
            <el-button size="small" type="warning" link @click="openEditUserDialog(scope.row)">编辑</el-button>
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
    <el-dialog v-model="showAddMallUserDialog" title="➕ 新增春播商城用户" width="500px">
      <el-form :model="newMallUserForm" label-width="100px">
        <el-form-item label="用户头像">
          <div style="display: flex; align-items: center; gap: 12px;">
            <el-avatar :size="56" :src="resolveAvatarUrl(newMallUserForm.avatar)" style="background: #10B981; color: #fff; font-weight: bold;">
              {{ (newMallUserForm.nickname || newMallUserForm.username || '用').substring(0, 1) }}
            </el-avatar>
            <div>
              <el-button size="small" @click="triggerAvatarUpload('register')">上传头像</el-button>
              <div style="font-size: 11px; color: #94a3b8; margin-top: 4px;">选填 · jpg/png，未上传用默认形象</div>
            </div>
          </div>
        </el-form-item>
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
        <el-form-item label="所在地区">
          <el-cascader
            v-model="newMallUserForm.regionPath"
            :options="regionCascaderOptions"
            placeholder="选填：省 / 市 / 区县"
            style="width: 100%;"
            clearable
            filterable
          />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="newMallUserForm.addressDetail" placeholder="选填：街道、社区、小区、楼栋门牌号" />
        </el-form-item>
      </el-form>
      <div style="font-size: 12px; color: #64748b;">注册成功自动发放 ¥200 新人健康体验金 + 200 健康积分，账号立即可在春播商城登录。</div>
      <template #footer>
        <el-button @click="showAddMallUserDialog = false">取消</el-button>
        <el-button type="primary" :loading="addMallUserLoading" @click="submitAddMallUser">注册用户</el-button>
      </template>
    </el-dialog>

    <!-- 编辑商城用户档案弹窗 -->
    <el-dialog v-model="showEditUserDialog" title="✏️ 编辑商城用户档案" width="500px">
      <el-form :model="editUserForm" label-width="100px">
        <el-form-item label="用户头像">
          <div style="display: flex; align-items: center; gap: 12px;">
            <el-avatar :size="56" :src="resolveAvatarUrl(editUserForm.avatar)" style="background: #10B981; color: #fff; font-weight: bold;">
              {{ (editUserForm.nickname || '用').substring(0, 1) }}
            </el-avatar>
            <el-button size="small" @click="triggerAvatarUpload('edit')">上传新头像</el-button>
          </div>
        </el-form-item>
        <el-form-item label="登录账号">
          <el-input :model-value="editUserForm.username" disabled />
        </el-form-item>
        <el-form-item label="姓名/称呼" required>
          <el-input v-model="editUserForm.nickname" />
        </el-form-item>
        <el-form-item label="手机号" required>
          <el-input v-model="editUserForm.phone" maxlength="11" />
        </el-form-item>
        <el-form-item label="所在地区">
          <el-cascader
            v-model="editUserForm.regionPath"
            :options="regionCascaderOptions"
            placeholder="选填：省 / 市 / 区县"
            style="width: 100%;"
            clearable
            filterable
          />
        </el-form-item>
        <el-form-item label="详细地址">
          <el-input v-model="editUserForm.addressDetail" placeholder="选填：街道、社区、小区、楼栋门牌号" />
        </el-form-item>
        <el-form-item label="重置密码">
          <el-input v-model="editUserForm.password" placeholder="留空表示不修改登录密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showEditUserDialog = false">取消</el-button>
        <el-button type="primary" :loading="editUserLoading" @click="submitEditUser">保存修改</el-button>
      </template>
    </el-dialog>

    <!-- 头像上传隐藏文件选择器 -->
    <input ref="avatarFileInput" type="file" accept="image/*" style="display: none" @change="handleAvatarFileChange" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElNotification } from 'element-plus'
import { makeListPager, parseOrderItems, batchDeleteRows } from '../utils/common.js'
import { regionCascaderOptions, splitRegionPrefix } from '../utils/regionData.js'

const mallUsers = ref([])
const mallUsersLoading = ref(false)
const showUserOrderDrawer = ref(false)
const userOrders = ref([])
const userSelection = ref([])
const showAddMallUserDialog = ref(false)
const addMallUserLoading = ref(false)
const newMallUserForm = ref({ username: '', password: '123456', nickname: '', phone: '', regionPath: [], addressDetail: '', avatar: '' })
const usersPager = makeListPager(mallUsers, ['username', 'phone', 'status'])

// 编辑用户档案
const showEditUserDialog = ref(false)
const editUserLoading = ref(false)
const editUserForm = ref({ id: null, username: '', nickname: '', phone: '', regionPath: [], addressDetail: '', avatar: '', password: '' })
const avatarFileInput = ref(null)
const avatarUploadMode = ref('edit')

const resolveAvatarUrl = (avatar) => {
  if (!avatar) return ''
  if (avatar.startsWith('http://') || avatar.startsWith('https://') || avatar.startsWith('data:image')) {
    return avatar
  }
  // 服务端落盘的真实头像（/uploads/attachments/xxx.jpg），dev 下 /uploads 已代理到后端 8080
  if (avatar.startsWith('/uploads/')) return avatar
  // avatar_resident_N 预设头像是手机端矢量资源，PC 端无对应文件，降级为首字母占位
  return ''
}

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
  newMallUserForm.value = { username: '', password: '123456', nickname: '', phone: '', regionPath: [], addressDetail: '', avatar: '' }
  showAddMallUserDialog.value = true
}

/** 省市区级联 + 详细地址 → 落库地址串（与手机端「省 市 区 详细」空格口径一致） */
const composeAddress = (regionPath, detail) => {
  return [...(regionPath || []), (detail || '').trim()].filter(Boolean).join(' ')
}

const triggerAvatarUpload = (mode) => {
  avatarUploadMode.value = mode
  avatarFileInput.value?.click()
}

const handleAvatarFileChange = async (e) => {
  const file = e.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('请选择图片文件（jpg/png）')
    e.target.value = ''
    return
  }
  const username = (avatarUploadMode.value === 'edit' ? editUserForm.value.username : newMallUserForm.value.username || '').trim()
  if (!username) {
    ElMessage.warning('请先填写登录账号，再上传头像')
    e.target.value = ''
    return
  }
  const fd = new FormData()
  fd.append('file', file)
  try {
    const res = await axios.post('/api/mall/user/upload-avatar?username=' + encodeURIComponent(username), fd)
    const url = res.data?.url || res.data?.avatar
    if (res.data?.success && url) {
      if (avatarUploadMode.value === 'edit') {
        editUserForm.value.avatar = url
        ElMessage.success('头像已上传并保存')
        loadMallUsers()
      } else {
        newMallUserForm.value.avatar = url
        ElMessage.success('头像已上传，注册时将一并保存')
      }
    } else {
      ElMessage.error(res.data?.message || '头像上传失败')
    }
  } catch (err) {
    ElMessage.error('头像上传失败')
  } finally {
    e.target.value = ''
  }
}

const openEditUserDialog = (row) => {
  const addr = (row.address || '').trim()
  const regionPath = splitRegionPrefix(addr)
  const prefixLen = regionPath.join(' ').length
  const detail = regionPath.length ? addr.slice(prefixLen).trim() : addr
  editUserForm.value = {
    id: row.id,
    username: row.username,
    nickname: row.nickname,
    phone: row.phone,
    regionPath,
    addressDetail: detail,
    avatar: row.avatar || '',
    password: ''
  }
  showEditUserDialog.value = true
}

const submitEditUser = async () => {
  const f = editUserForm.value
  if (!f.nickname.trim() || !f.phone.trim()) {
    ElMessage.warning('姓名/称呼与手机号为必填项')
    return
  }
  if (!/^1\d{10}$/.test(f.phone.trim())) {
    ElMessage.warning('请输入 1 开头的 11 位手机号')
    return
  }
  if (f.password.trim() && f.password.trim().length < 6) {
    ElMessage.warning('重置密码长度至少 6 位')
    return
  }
  editUserLoading.value = true
  try {
    const body = {
      id: f.id,
      nickname: f.nickname.trim(),
      phone: f.phone.trim(),
      address: composeAddress(f.regionPath, f.addressDetail),
      avatar: f.avatar || ''
    }
    if (f.password.trim()) body.password = f.password.trim()
    const res = await axios.post('/api/admin/mall/user/update', body)
    if (res.data?.success) {
      ElMessage.success('用户档案已更新')
      showEditUserDialog.value = false
      loadMallUsers()
    } else {
      ElMessage.error(res.data?.message || '更新失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '更新失败')
  } finally { editUserLoading.value = false }
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
      address: composeAddress(f.regionPath, f.addressDetail),
      avatar: f.avatar || ''
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
