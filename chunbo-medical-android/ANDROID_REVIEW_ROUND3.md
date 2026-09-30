# 春播万象 Android 手机端 · 第三轮复检报告（最新代码）

> 时间：2026-09-27 16:14
> 背景：项目在 14:00–16:00 期间被大幅扩展（新增 SSE 流式对话、购物车、会话历史底栏、Markdown 渲染、地区选择、头像上传、订单详情/再次购买等），后端同步新增了用户资料/地址/头像接口、修了 JwtFilter 白名单"吃掉登录态"的问题。本轮基于**最新代码**重新检查。

---

## 一、已确认的真实 Bug（数据错误 / 逻辑错位）

1. **「再次购买」商品 id 恒为 1（严重）**
   - 后端 `MallOrder` 实体没有 `productId` 字段，Android `MallOrder.productId` 恒为 null。
   - `ProfileFragment.handleReorder` 里 `id = order.productId ?: 1L` → 每次"再次购买"加购物车的商品 id 都是 1。
   - `parseOrderItemsList` 从 `itemsJson` 解析时**没解析 `id` 字段**（下单时 itemsJson 里明明写了 `id`）。
   - 后果：再次下单会按 id=1 去扣库存/匹配商品，可能扣错商品或订单商品错乱。

2. **购物车「体验金抵扣」文案参数错位**（`MallFragment.updateCartBar`）
   - `"体验金抵扣 ¥%.2f (余额: ¥%.2f)", user.balance, user.balance` —— 两个占位符都填了 balance，第一个本应显示可抵扣金额 `min(balance, totalPrice)`。

3. **MarkdownFormatter 行内 `` `code` `` 未处理**
   - `INLINE_CODE_PATTERN` 定义了但 `appendWithInlineFormatting` 只处理 `**加粗**`，反引号会原样显示。

4. **MarkdownFormatter 硬编码深色**（深色模式看不清）
   - `COLOR_BOLD = 0xFF0F172A`、`COLOR_HEADING` 等写死深色，深色主题下加粗/标题文字近乎隐形。

5. **余额抵扣"前端算 + 本地扣"与后端重复**
   - 下单时前端算 `discount/finalAmount`，本地 `updateBalanceAndPoints(balanceAfter)`，再 `refreshBalanceFromServer()` 拉服务端覆盖。后端现在能真扣（白名单已修、登录态带 token 即可），但前端本地算的抵扣额与后端重算的可能不一致（余额不同步时），且有短暂余额闪烁。建议：下单成功后**只** `refreshBalanceFromServer()`，去掉本地 `updateBalanceAndPoints(balanceAfter)`。

---

## 二、未闭环 / 缺失功能

6. **会话历史没有删除入口**：`ChatHistoryBottomSheetHelper` 只展示 + 点击回放，无删除单条会话的按钮（`deleteSession` / `deletePreConsultSession` 接口已定义但没人调用）。
7. **购物车纯内存**：`CartManager` 是 `mutableMapOf`，无持久化，App 重启/进程被杀后购物车清空。
8. **SSE 接口角色错位（待确认）**：`CopilotFragment` 走 `MedicalChatStreamClient` 调 `/api/medical/chat/stream`（这是**医生端临床问诊**接口，后端 context `role=DOCTOR`，`doctorId` 传的是手机号/`mobile_guest`）。手机端定位是"药师导医"，更像应走商城药师 `/api/mall/chat/stream`。若后端问诊工具按医生工号做 RBAC，手机号会拿不到结果。
9. **订单无物流/发货状态 + 收货确认**：商城"下单→发货→收货"仍只有下单一步，订单详情里没有发货/物流/收货按钮。

---

## 三、假数据 / 硬编码

| 位置 | 内容 |
|---|---|
| `ApiClient.DEFAULT_BASE_URL` / AppSettingsDialog | 默认 IP `192.168.0.238` |
| `MallAuthHelper.btnFillTestAccount` | 测试账号 `kzt/123456` |
| `MallFragment`（两处下单） | `clinicName` 写死"春播网上健康大药房直发" |
| `ProfileFragment.handleReorder` | 假 `stock=500`、`category="春播正品"`、`id=1L` |
| `ProfileFragment.convertPrescriptionToOrder` | 地址兜底"春播万象便民医疗服务站自提" |
| `MarkdownFormatter` | 硬编码颜色（含深色） |

---

## 四、UI / 布局建议

10. 所有列表仍 `notifyDataSetChanged`（含新增的 `ChatAdapter`/`CartAdapter`/`HistorySessionAdapter`），未用 DiffUtil。
11. 深色模式：`MarkdownFormatter` 硬编码色 + 部分布局硬编码浅色卡片（`#F0FDF4`/`#DCFCE7` 等）未收口到 `colors.xml`。
12. `MainActivity` 仍 import `ServerConfigDialog`，但已被 `AppSettingsDialog` 取代（死 import，无害）。

---

## 五、可添加的功能（按价值）

1. 会话历史删除（接口已就绪，纯 UI 补个删除按钮）。
2. 购物车本地持久化（SharedPreferences/JSON，重启不丢）。
3. 订单物流/发货状态跟踪 + 收货确认（补齐商城履约闭环）。
4. 叫号实时推送 / 后台轮询提醒（便民就医核心场景）。
5. 商城药师 SSE 流式（把 Copilot 的 `/api/medical/chat/stream` 换成 `/api/mall/chat/stream`，角色更贴合患者）。
6. 商品搜索历史、收藏。

---

## 六、建议的修复优先级（纯手机端）

- **P0**：修「再次购买」productId 恒 1L（`parseOrderItemsList` 解析 itemsJson 的 `id`，`handleReorder` 用真实 id）。
- **P1**：`updateCartBar` 文案参数错位；MarkdownFormatter 行内 code + 深色适配；下单去掉本地假扣只刷新服务端余额。
- **P2**：会话历史删除按钮；购物车持久化；DiffUtil；深色模式硬编码收口。
- **需后端/需确认**：SSE 接口角色、叫号推送、订单物流。
