# 春播万象 Android 手机端 · 第四轮复检报告

> 时间：2026-09-27 16:46
> 基线：上一轮已完成「发货→确认收货闭环 + reorder/体验金文案/Markdown/下单假扣」修复之后的最新代码。
> 本轮重点：前几轮未细看的购物车、订单列表、地区选择、设置中心、头像等文件。

---

## 一、确认的新 Bug

### B1【严重】购物车加/减数量「双重计数」——点一次 ±2
- 位置：`CartAdapter.kt` 的 `btnCartItemMinus` / `btnCartItemPlus`。
- 根因：`CartAdapter.submitList(CartManager.getItems())` 传入的 `CartItem` 与 `CartManager.cartItems` 里的对象是**同一个引用**。点击时先 `CartManager.updateQuantity(id, item.quantity - 1)`（内部已把 `item.quantity` 改成 -1），随后又执行 `item.quantity -= 1`，导致**点一次减号数量减 2、点一次加号数量加 2**。
- 后果：用户点一次"+"，购物车数量从 1 直接跳到 3，下单数量与金额全错。

### B2【业务】设置中心改地址不同步云端
- `AppSettingsDialog` 的「常用地址」点击后只调 `UserManager.updateAddress`（本地 prefs），**没调后端 `/api/mall/user/update-address`**。
- 对比 `ProfileFragment.showEditAddressDialog` 是调了后端接口的。两处行为不一致：在设置中心改地址，PC 端/云端看不到，且下次登录会被服务端旧地址覆盖。

### B3【业务】登录后头像被重置为默认
- `MallAuthHelper` 登录成功调 `saveUser(...)` 时**没传 avatar**（后端 login 返回的 userData 里也没有 avatar 字段），`saveUser` 内部 `avatar=null → "avatar_resident_1"`。
- 用户上传/选过头像后，只要重新登录，头像就被重置为默认头像。

---

## 二、假数据 / 硬编码

| 位置 | 内容 |
|---|---|
| `AppSettingsDialog.btnClearCache` | "已清空，释放 **14.6MB** 存储空间"——**写死假数据**，实际没有清理任何东西 |
| `AppSettingsDialog` 三个提醒开关 | 处方/服药/叫号开关只弹 Toast，**无真实推送逻辑**（假功能） |
| `RegionPickerHelper.REGION_DATA` | 地区数据**硬编码**十几个省的部分城市，覆盖不全，非全国数据 |
| `RegionPickerHelper.splitAddress` | 兜底地址写死"湖南省 长沙市 岳麓区" |
| `RegionPickerHelper` | 硬编码颜色 `#059669 / #64748B / #1E293B`（深色下看不清） |
| `AppSettingsDialog` | 硬编码颜色 `#DC2626 / #FEE2E2 / #059669`；预设 IP `192.168.0.238` |
| 遗留 | 默认 IP、`kzt/123456`、`clinicName` 写死、按方送药地址兜底（前几轮已列，未清） |

---

## 三、UI / 布局

1. **订单状态文案过长**：`OrderAdapter` 直接显示后端中文串"已发货 / 春播便民速递运输中"、"已送达 / 居民已签收"，在列表 badge 里塞不下，应精简为"已发货"/"已送达"（订单详情页里可显示完整文案）。
2. 全部列表仍 `notifyDataSetChanged`（含 CartAdapter / HistorySessionAdapter），未用 DiffUtil。
3. 深色模式硬编码浅色卡片 + 上述硬编码颜色未收口到 `colors.xml`。

---

## 四、未闭环 / 缺失

4. **消息/提醒是真假功能**：设置中心的处方/服药/叫号开关没有任何实际推送、定时提醒、叫号监听实现（纯 Toast）。
5. 购物车纯内存，重启丢失（前几轮已列）。
6. 会话历史仍无删除入口（前几轮已列，`deleteSession` 接口已定义未用）。

---

## 五、建议的修复优先级（纯手机端）

- **P0**：修购物车 ±2 双重计数（`CartAdapter` 去掉重复的 `item.quantity ±=`，只保留 `CartManager.updateQuantity` 一次）。
- **P1**：设置中心改地址补调后端 `update-address`；登录后保留头像（登录接口返回/回传 avatar）；订单状态文案精简。
- **P2**：会话历史删除按钮；购物车本地持久化；地区数据改后端下发或完整数据源；清理缓存做真实清理。
- **需后端**：叫号/处方/服药提醒的真实推送（SSE/轮询 + 通知）。

---

## 六、可添加的功能

1. 叫号实时推送 + 本地通知（便民就医核心，需后端轮询/SSE 配合）。
2. 处方/服药提醒（定时本地通知，可先纯本地实现）。
3. 会话历史删除 + 购物车持久化。
4. 订单状态机更细化（待付款/待发货/已发货/已送达的进度条展示）。
5. 商品搜索历史、收藏。
