# 春播万象 Android 手机端 · 二次复检报告（整改后）

> 范围：仅 `chunbo-medical-android`（手机端），不动 PC 端、不动后端。
> 时间：2026-09-27 00:08
> 上一轮已修 10 处（admin 硬编码登录、注册手机号、商城字段对齐、下单假扣、AI 会话本地化、tab 状态、图片降采样、扫码现场码、按方送药等），本报告聚焦**剩余问题 + 新增可加功能**。

---

## 一、剩余 Bug / 不合理

1. **登录/注册逻辑重复两份**：`MallFragment` 与 `ProfileFragment` 里的登录/注册弹窗逻辑几乎逐行复制（各约 200 行）。本轮改"注册加手机号"就不得不改两遍，长期是 bug 温床。应抽成公共组件/函数。
2. **身份证校验弱 + 明文回显**：挂号弹窗只校验长度 18/15（`PatientFragment`），不校验校验位；且 `dialog_registration.xml` 的 `edit_id_card`、排队记录多处明文显示身份证号，存在隐私泄露风险。应加校验位 + 脱敏（如 `110***********1234`）。
3. **性别用文本框手输**：`dialog_registration.xml` 的 `edit_patient_gender` 是 `TextInputEditText`（默认"男"），用户可乱填"男/女/其它/空"。应改 Spinner 或分段选择。
4. **余额用 Float 存储，精度漂移**：`UserManager` 全程 `putFloat/getFloat` 存余额，`200.00 - 19.99` 可能显示成 `180.00999...`。应用 Long（分）或 String（两位小数）存金额。
5. **死代码**：`UserManager.deductBalance` 上轮去掉调用后已无人使用，可删。
6. **处方卡片 gender/age 恒空**：后端 `Prescription` 实体无 gender/age，`PrescriptionAdapter` 仍拼接空值，显示成"就诊人 ( )"。可改为只显示姓名 + 引导看详情。
7. **AI 历史回放丢快捷胶囊**：`CopilotFragment.openSession` 回放历史消息时未带 `quickReplies`，回放后欢迎语/回答下方的快捷按钮消失。需在 `LocalChatMessage` 增加 `quickReplies` 字段并序列化。（上轮引入，待修）

---

## 二、剩余假数据 / 硬编码

| 位置 | 内容 |
|---|---|
| `ApiClient` / `ServerConfigDialog` | 默认服务器 `http://192.168.0.238:8080/`、预设 127.0.0.1 / 10.0.2.2 |
| 登录弹窗 `btnFillTestAccount` | 测试账号快捷填充 `kzt/123456` |
| `UserManager` | 余额/积分兜底 `?: 200`（saveUser / getUser 多处） |
| `PatientFragment` | 挂号费兜底 `15.0`、科室兜底列表、医生等级兜底"专家/普通" |
| `MallFragment` / `ProfileFragment` | `clinicName` 写死"春播网上便民药房直发"、地址兜底"春播万象便民医疗服务站自提" |
| `MallProduct` | `displayCategory` 兜底"家庭常备" |
| 各处 Kotlin/XML | 大量中文文案硬编码，未走 `strings.xml`（无法国际化） |

> 说明：`192.168.0.238`、`kzt/123456`、余额 200 属于"演示环境约定"，是否保留由你定；但建议至少把余额/积分兜底和文案抽到常量/资源。

---

## 三、业务流程仍未闭环

1. **叫号无实时推送**：患者看"待诊→就诊中→被叫号"只能手动下拉刷新，无 SSE/轮询/推送。要真正闭环需后端提供轮询或 SSE 接口。
2. **余额扣款 / 订单 user 隔离被后端白名单"吃掉"**（上轮已列）：`/api/mall/orders`、`/api/mall/order/` 在白名单，`request.getAttribute("username")` 恒 null → 订单列表永远空、下单不落 userId、`useBalance` 不生效。手机端修不了，需后端最小改动。
3. **预问诊历史关联存疑**：`PreConsultDialogHelper` 用 `user.phone.ifBlank{username}.ifBlank{"guest"}` 当 userId 查 `getPreConsultSessions`，与后端 session 存储的 userId 口径未核对，可能永远查不到往期记录。
4. **商城"支付→发货→收货"只有下单一步**：无发货/物流查询、无收货确认，订单状态只能看到"待商户发货出库"。
5. **挂号后无提醒**：无到店提醒、无叫号通知（可做本地通知 + 后台轮询）。
6. **游客订单孤儿化**：游客下单 `_username` 为空 → 后端 userId 不落库，前端也无从查询自己的订单。

---

## 四、UI / 布局改进建议

1. **底部导航手势条适配**：`activity_main.xml` 的 `marginBottom=64dp` 硬编码，未用 WindowInsets；锁竖屏后多数设备正常，但全面屏手势条下仍有轻微遮挡。
2. **全 RecyclerView 用 `notifyDataSetChanged`**：商品/订单/处方/队列/聊天列表均无 DiffUtil，长列表卡顿、无增删动画。
3. **无深色模式**：`themes.xml` 固定浅色，可加 `values-night`。
4. **加载/空态/错误态粗糙**：普遍 Toast 兜底，无骨架屏、无重试按钮。
5. **无全局 401 / 网络异常统一处理**：断网、token 过期等各页各自 catch，体验不一致。
6. 挂号弹窗：性别改下拉、身份证脱敏、金额展示统一两位小数。

---

## 五、可添加的功能（按价值排序）

1. **叫号实时推送 / 后台轮询**（对接后端 SSE 或定时刷新，配本地通知）——最贴合"便民就医"场景。
2. **就诊提醒**：挂号后本地定时提醒到店、叫号通知。
3. **订单物流/发货状态跟踪 + 收货确认**：补齐商城履约闭环。
4. **语音问诊**：后端已有 `/api/audio`（TTS/ASR），手机端可接入语音输入/播报。
5. **商品搜索历史 + 收藏**：轻量本地实现。
6. **深色模式**：加 `values-night` 配色。
7. **健康档案查看**：历次就诊 / 处方 / 检验结果聚合页。
8. **复诊 / 处方到期提醒**。
9. **就医满意度评价 / 反馈**。
10. **分享邀请拉新**（生成邀请码/海报）。

---

## 六、需要后端配合的最小改动（上轮已列，重申，交你拍板）

1. **JwtFilter 白名单放行导致登录态丢失**：`/api/mall/orders`、`/api/mall/order/` 需"可选解析 token"（带 token 就设 username/role，不强制登录），否则订单隔离与余额抵扣永远失效。
2. **`/api/mall/chat` 无状态**：若要服务端真多轮记忆 + 跨设备历史，需在 `runMultiAgentWorkflow` 落会话 + 写 ChatMemory。

---

## 七、建议的下一批改动（纯手机端、可直接做）

- 抽公共登录组件（消重复）→ 身份证校验位 + 脱敏 + 性别下拉 → 余额改 Long/String 存 → 删 dead code → DiffUtil → 手势条适配 → 深色模式。
- AI：`LocalChatMessage` 加 `quickReplies` 字段修复历史回放丢胶囊。
