# 春播万象 Android 手机端 · 第五轮复检报告

> 时间：2026-09-27 17:01
> 基线：第四轮整改（购物车±2、地址同步、登录头像、订单状态、会话删除、购物车持久化、清理缓存）+ 本轮新增「叫号轮询提醒」之后。

---

## 一、本轮新增：叫号轮询提醒（纯手机端，不碰后端）

- 新建 `util/QueueReminderManager`：每 30 秒轮询 `/api/registration/list?userPhone=`，检测挂号状态从「待诊/候诊中」变为「就诊中」时弹本地通知（通知渠道 + 高优先级）。
- `AndroidManifest` 加 `POST_NOTIFICATIONS`；`MainActivity` 启动时请求通知权限 + 创建通知渠道。
- `PatientFragment.loadPatientRecords` 在有生效待诊挂号时启动轮询，无生效挂号/待签到时停止。
- **局限（如实说明）**：这是纯手机端轮询方案，App 退到后台但进程活着时有效；**App 被杀死后不提醒**，且轮询有最多 30 秒延迟。要做到"进程被杀也能提醒"需前台服务 + WorkManager，或后端推送。

---

## 二、第四轮整改确认（已做，6 项）

购物车±2、设置中心地址同步云端、登录头像保留、订单状态文案精简、会话历史删除、购物车本地持久化 + 清理缓存真实化——均已落地，无新引入问题。

---

## 三、第五轮剩余 Bug / 不合理

1. **死代码**：`ServerConfigDialog` 整个类已无引用（`MainActivity` 只剩一行 import，实际用的是 `AppSettingsDialog`），可删除该类 + import。
2. **SSE 接口角色错位（待后端确认）**：`CopilotFragment` 走 `MedicalChatStreamClient` 调 `/api/medical/chat/stream`（医生端临床问诊接口，后端 `role=DOCTOR`，`doctorId` 传的是手机号/`mobile_guest`）。患者端"药师导医"定位更贴合 `/api/mall/chat/stream`。若后端问诊工具按医生工号做 RBAC，手机号会拿不到结果——需要后端确认或改用商城药师流式接口。
3. **提醒开关仍是假功能**：`AppSettingsDialog` 的「处方通知 / 服药提醒 / 叫号弹窗」三个开关仍只弹 Toast，没有真实持久化状态、也没有对应提醒逻辑（叫号提醒已另有 `QueueReminderManager` 实现，但两者没打通）。

---

## 四、剩余假数据 / 硬编码

| 位置 | 内容 |
|---|---|
| `ApiClient.DEFAULT_BASE_URL` / `AppSettingsDialog` | 默认 IP `192.168.0.238` |
| `MallAuthHelper.btnFillTestAccount` | 测试账号 `kzt/123456` |
| `MallFragment`（两处下单） | `clinicName` 写死"春播网上健康大药房直发" |
| `RegionPickerHelper.REGION_DATA` | 地区数据硬编码十几个省、覆盖不全；兜底"湖南长沙岳麓区" |
| `RegionPickerHelper` / `AppSettingsDialog` | 硬编码颜色 `#059669/#64748B/#1E293B/#DC2626` 等 |
| `MarkdownFormatter` | `COLOR_HEADING/COLOR_BULLET/COLOR_NUM` 硬编码（深色下部分看不清） |
| `ProfileFragment.convertPrescriptionToOrder` | 地址兜底"春播万象便民医疗服务站自提" |

> 以上"演示环境约定"（IP、测试账号、写死商户名）是否清理由你定；建议至少把颜色收口到 `colors.xml` 以适配深色。

---

## 五、UI / 布局剩余

1. 所有列表仍 `notifyDataSetChanged`（商品/订单/处方/购物车/历史会话），未用 DiffUtil，长列表无增量动画。
2. 深色模式：`MarkdownFormatter` 硬编码颜色 + 部分布局硬编码浅色卡片（`#F0FDF4`/`#DCFCE7` 等）未收口。
3. 处方卡片 gender/age 恒空（后端 `Prescription` 实体无字段，手机端只能显示姓名）。

---

## 六、未闭环（需后端配合）

1. 订单物流/发货跟踪（你已明确暂不做）。
2. 处方/服药提醒的真实推送（需后端定时任务/推送，或纯本地定时通知）。
3. 叫号提醒的后台持续（App 被杀后不提醒，需前台服务 + WorkManager 或后端推送）。

---

## 七、可添加的功能

1. **前台服务 / WorkManager** 让叫号提醒在 App 后台/被杀后仍生效（不依赖后端）。
2. **处方到期 / 服药打卡提醒**（纯本地 `AlarmManager`/`WorkManager` 定时通知）。
3. **语音问诊**（后端已有 `/api/audio` TTS/ASR，手机端可接）。
4. 商品搜索历史、收藏；就医满意度评价。

---

## 八、项目健康度小结（五轮累计）

- **P0 已清零**：admin 硬编码登录、余额假扣、字段错位、购物车±2、再次购买 productId 恒 1 等已全部修复。
- **P1 基本清零**：注册手机号、登录头像、订单状态、地址同步、会话删除等已修。
- **剩余 = P2 工程项 + 后端依赖项**：DiffUtil、深色收口、死代码清理（纯手机端可做）；SSE 角色、物流、处方/服药提醒推送（需后端）。
