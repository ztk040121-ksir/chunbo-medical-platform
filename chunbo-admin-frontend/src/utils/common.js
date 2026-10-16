// 通用工具函数（从 App.vue 抽取，供各业务子组件复用）
import { reactive, computed, watch } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

// 通用列表搜索/分页工厂（支持客户端切片及后端标准分页对象适配）
export const makeListPager = (sourceRef, fields) => {
  const state = reactive({ search: '', page: 1, size: 10 })
  const searched = computed(() => {
    const key = state.search.trim().toLowerCase()
    const list = Array.isArray(sourceRef.value) ? sourceRef.value : (sourceRef.value?.list || sourceRef.value?.data || sourceRef.value?.records || [])
    if (!key) return list
    return list.filter(item => fields.some(f => String(item[f] ?? '').toLowerCase().includes(key)))
  })
  const paged = computed(() => {
    if (sourceRef.value && !Array.isArray(sourceRef.value) && (sourceRef.value.records || sourceRef.value.list)) {
      return sourceRef.value.records || sourceRef.value.list || []
    }
    return searched.value.slice((state.page - 1) * state.size, state.page * state.size)
  })
  const total = computed(() => {
    if (sourceRef.value && !Array.isArray(sourceRef.value) && sourceRef.value.total != null) {
      return Number(sourceRef.value.total)
    }
    return searched.value.length
  })
  watch(() => state.search, () => { state.page = 1 })
  return reactive({ state, searched, paged, total })
}

// 解析订单商品明细 JSON，解析失败返回兜底单条
export const parseOrderItems = (itemsJson) => {
  if (!itemsJson) return []
  try {
    return JSON.parse(itemsJson)
  } catch (e) {
    return [{ productName: '生活药品组合', quantity: 1 }]
  }
}

// 通用批量删除（多选删除，singleMode=true 时逐条单删）
export const batchDeleteRows = async (url, ids, label, reload, singleMode = false) => {
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
