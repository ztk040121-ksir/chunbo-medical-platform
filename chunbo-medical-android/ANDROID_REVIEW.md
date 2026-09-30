# 春播万象 Android 手机端体检报告

> 审查范围：`chunbo-medical-android` 全部源码 + 手机端所调用的后端接口契约（仅聚焦手机端本身的问题）。
> 审查日期：2026-09-26

---

## 一、结论速览

这个 App 实际是**「患者端 + 挂号 + 商城导购」**，不是 README 宣称的"四大模块"。真实实现里**没有医生端、没有临床 CDSS**。最严重的问题集中在三块：**① 硬编码 admin 账号自动登录（安全 + 数据串号）② 商城"体验金"是本地假扣、后端不落账 ③ 商城订单/商品字段与后端大面积对不上**。

---

## 二、高危 Bug（会造成数据错误 / 安全问题）

### B1【严重·安全】硬编码管理员 `admin/123456` 自动登录
- 位置：`data/api/ApiClient.kt:127-153` 的 `fetchTokenSync()` 写死 `{"username":"admin","password":"123456"}`；`authInterceptor`（第 40-42 行）对任何无 token 的请求自动用管理员身份登录并附带 `Bearer` token。
- 后果：
  1. 反编译 APK 即可拿到后台管理员口令；
  2. **游客/未登录**浏览也会以 admin 身份请求 `/api/registration/list`、`/api/prescription/list`、`/api/mall/orders`，导致 `getOrdersForUser("admin")` 用管理员身份查数据；
  3. 一旦商城用户 token 失效（401），拦截器**静默换成 admin token 重试**，用户数据瞬间变成管理员视图。
- 附带问题：`ApiClient` 的 `KEY_AUTH_TOKEN` 与 `UserManager` 的 `KEY_TOKEN` 是**两套互不同步**的 token 存储，游客态与登录态切换极易串号。

### B2【严重·业务】商城下单不真正扣款，体验金是假数据
- 后端 `createOrderFromBargain` 只有 `useBalance=true` 且能解析到登录用户时才扣余额、才算抵扣。
- Android 下单（`MallFragment.kt:286-299`）**根本没传 `useBalance`**，也不带任何登录态字段 → 后端永远不扣钱、无抵扣。
- Android 侧只靠 `UserManager.deductBalance()` 扣**本地 SharedPreferences** 余额（`UserManager.kt:82-88`），服务端余额纹丝不动。
- 结果："200 元健康体验金"只是本地展示数字；`getUserInfo` 接口定义了却从未调用（`MedicalApiService.kt:25-26`），余额永远不回流同步。**支付-余额闭环断裂。**

### B3【业务】下单状态语义错位
- Android 提交 `"status":"PAID"`（`MallFragment.kt:298`），后端却**强制覆盖**为 `"待商户发货出库"`。前端以为"已支付"，实际是"待发货"。手机端没有对接任何发货/物流查询接口，履约状态无法闭环跟踪。

### B4【数据】MallOrder 字段大面积对不上（订单列表残缺）
- 后端 `MallOrder` 实体只有：`orderNo/clinicName/buyerName/userId/totalAmount/discountAmount/finalAmount/itemsJson/status/bargainNotes/createTime`。
- Android `MallOrder` 期望的 `productName/productId/quantity/unitPrice/buyerPhone/shippingAddress` **全部不存在**。
- 后果（`OrderAdapter.kt`）：`quantity` 恒 null → 永远显示"共计: 1 件"；`buyerPhone`/`shippingAddress` 恒 null → 收件人电话、地址显示不出来；商品名只能靠 `itemsJson` 兜底解析。
- 且 Android 下单写入的 `itemsJson` 用的是 `productId`（`MallFragment.kt:297`），后端扣库存时优先取 `item.get("id")`，拿不到 id 只能靠 `productName` 模糊匹配，商品可能匹配错。

### B5【数据】MallProduct 字段对不上（搜索/筛选失效）
- 后端 `MallProduct` **没有** `brand/price/description/suitableSymptoms`（实际是 `directorPitch/buyerPitch/csPitch`）。
- Android 依赖 `suitableSymptoms` 做分类筛选（感冒/咳嗽/儿童/疼痛，`MallFragment.kt:174-197`）和搜索，恒为空 → **分类筛选与症状搜索基本失效**；`brand`、`description` 也恒空。
- `displayStock` 兜底 500、`displayCategory` 兜底"家庭常备"等都是本地造数。

### B6【业务】注册手机号硬编码 + 缺手机号输入框
- 注册弹窗（`dialog_mall_login.xml`）**没有手机号输入框**；代码 `MallFragment.kt:405` 与 `ProfileFragment.kt:388` 用 `if(账号是11位手机号) 账号 else "13800000000"`。
- 后端强制 `phone` 必须 `1\d{10}`。非手机号用户名注册 → 全都落成 `13800000000`，**第二个非手机号用户注册必失败**（"账号或手机号已存在"）。

### B7【数据】处方缺字段
- 后端 `Prescription` 实体**没有** `gender/age/totalPrice`，Android `PrescriptionDetail` 有 → 处方卡片性别/年龄恒空（`PrescriptionAdapter.kt:46-49`）。

---

## 三、业务流程未闭环

1. **医生端完全缺失**：README 宣称的"医生移动工作台 DoctorFragment"在代码里**根本不存在**。底部导航只有 4 个 tab（挂号/AI导医/商城/我的）。`callPatient/finishPatient/cancelPatient` 接口和 `QueueAdapter` 的 `onCallClick/onFinishClick` 全是**死代码**（默认 null、无人调用）。叫号→接诊→开方→发药在手机端 = 0。
2. **"实时叫号广播"是假的**：手机端无推送/SSE/轮询，只有下拉刷新。患者看"就诊中/被叫号"完全靠手动刷新。
3. **扫码签到闭环松**：`CameraScanActivity.performSignOperation` 只识别 `CHUNBO_SIGN:`/`REG_ID:` 两种前缀（第 265 行）；扫到其它任何内容都会落到 `specificId==null` 分支，直接把用户自己那条"待签到"记录签掉——**"扫任意二维码都能自签"**，二维码内容实际没参与校验。
4. **支付/余额/发货/物流**：无支付回调、无余额同步、无发货/物流接口。商城"下单→支付→发货→收货"四步里**只有"下单"一步是真的**。
5. **AI 导医定位错位**：README 说 Copilot 是"临床 CDSS 处方审查/十八反十九畏"，实际 `CopilotFragment` 调的是 `/api/mall/chat`（`role="consumer"`，商城消费者药师）。真正的临床接口 `/api/assistant/chat`（`chatAssistant`）在 `MedicalApiService` 里定义了却**从未调用**，临床决策能力在手机端 = 0。
6. **"一键按方送药到家"闭环坏**：`ProfileFragment.kt:234` 把处方药的 `medicineId` 直接当商城 `productId` 下单，但 `medicineId` 与 `mall_product.id` 是**两张表主键**，后端按 productId 查不到商品 → 扣库存失败/订单商品错乱。
7. **游客订单孤儿化**：游客下单时拦截器自动带 admin token → `_username="admin"` → 后端查不到 MallUser"admin" → `userId` 不落库；`getOrdersForUser("admin")` 又返回空 → 游客下的单永远查不到。
8. **预问诊历史关联存疑**：`PreConsultDialogHelper.kt:129` 用 `user.phone.ifBlank{username}.ifBlank{"guest"}` 当 userId 去查 session，与后端按 sessionId 存储的关联口径需核对，可能永远查不到历史。

---

## 四、假数据 / 硬编码清单

| 位置 | 内容 |
|---|---|
| `ApiClient.kt:24` | 默认服务器 `http://192.168.0.238:8080/` |
| `ApiClient.kt:134` | 自动登录 `admin/123456` |
| `ServerConfigDialog.kt` | 预设 `192.168.0.238` / `127.0.0.1` / `10.0.2.2` |
| `MallFragment.kt:345` / `ProfileFragment.kt:327` | 测试账号快捷填充 `kzt/123456` |
| `MallFragment.kt:405` / `ProfileFragment.kt:388` | 注册兜底手机 `13800000000` |
| `UserManager.kt` / 后端 `MallAuthController` | 余额/积分默认 200（前后端双写死） |
| `ProfileFragment.kt:241-242` | 处方兜底手机 `13800138000`、地址"春播万象便民医疗服务站自提" |
| `PatientFragment.kt:286,302,356,481` | 科室兜底列表、挂号费兜底 `15.0`、医生等级兜底 |
| `MallProduct.kt` | 库存兜底 500、分类兜底、价格兜底 |
| `OrderAdapter.kt:43` | 订单金额兜底 38.0 |
| `MallFragment.kt:296` | `clinicName` 写死"春播网上便民药房直发" |
| 各处 Kotlin/XML | 大量中文文案硬编码，未走 `strings.xml` |

---

## 五、UI / 布局 / 体验问题

1. `activity_main.xml` 的 `fragment_container` 用 `marginBottom=64dp` + 底部导航硬编码 64dp，未适配状态栏/手势条，刘海屏底部易重叠或被遮挡。
2. 四个 Fragment 用 `hide/show` 切换，**无状态保存**；`MainActivity.onCreate` 每次都强制 `selectedItemId = nav_patient`，进程重建/重进 App 永远回到挂号页。
3. 顶部刷新按钮 `triggerRefresh()`（`MainActivity.kt:108-113`）只处理 Mall/Patient，**Copilot 和 Profile 页点刷新无反应**。
4. `ImageLoader` 无磁盘缓存、无降采样（`BitmapFactory.decodeStream` 直接解全尺寸），大图易 OOM；用 `CoroutineScope(Dispatchers.IO)` 每次新建 scope，无生命周期绑定，有内存泄漏风险。
5. 所有 RecyclerView 都用 `notifyDataSetChanged()`，未用 DiffUtil，长列表卡顿、无动画。
6. 登录逻辑在 `MallFragment` 和 `ProfileFragment` 里**完整复制粘贴两份**（数百行重复），改一处漏一处。
7. 注册/登录弹窗：无手机号输入、无确认密码、无密码强度提示。
8. 无全局网络状态提示、无 401 统一跳登录、无 token 过期提示（反而是静默换 admin token）。
9. 身份证只校验长度 18/15，未校验校验位；且多处**明文回显**身份证号。
10. 加载态/空态/错误态粗糙，普遍用 `Toast` 兜底。

---

## 六、代码质量 / 架构问题

- 无 MVVM / Repository 分层，Fragment 直接 `ApiClient.service.xxx`，业务逻辑堆在 Activity/Fragment 里（`PatientFragment` 543 行、`MallFragment` 451 行、`ProfileFragment` 435 行）。
- 死代码：`chatAssistant / getQueue / callPatient / finishPatient / cancelPatient / preConsultWelcome / preConsultQuickReplies / getUserInfo` 定义了但从未调用。
- `util/qrcode/` 下手工内嵌了 Nayuki 二维码库（`QrCode.java` 815 行 + `QrSegment.java` 317 行），可改用 zxing 的 `QRCodeWriter` 精简（zxing 已在依赖里）。
- **README 与实际代码严重不符**：宣称的"四大模块 / DoctorFragment / 医生叫号接诊 / 临床 CDSS"多数未实现或实现对象错位。

---

## 七、改进建议（按优先级）

**P0（必须立即修）**
1. 去掉 `admin/123456` 自动登录，改为真实登录态；明确区分"管理员 / 医生 / 患者"三套 token，杜绝串号。
2. 下单请求带 `useBalance=true` 并携带登录态，让后端真正扣余额、落抵扣，去掉本地假扣。

**P1（重要）**
3. 对齐商城 `MallProduct`/`MallOrder` 字段：`itemsJson` 统一用 `id`（而非 `productId`）；订单列表回显商品名/数量/电话/地址。
4. 注册弹窗补手机号输入，去掉 `13800000000` 兜底。
5. `CameraScanActivity` 补全现场签到码的识别，并把签到与具体挂号绑定，不要"扫啥都自签"。
6. "按方送药"做 medicine→product 的真实映射，或改为"按药名搜商城"而不是拿 medicineId 当 productId。

**P2（体验与工程）**
7. 改用 DiffUtil + ViewPager2 替代 hide/show；统一 `viewLifecycleOwner.lifecycleScope`；`ImageLoader` 加磁盘缓存和 `inSampleSize` 降采样。
8. 抽取公共登录组件，消除 MallFragment / ProfileFragment 重复代码。
9. 补状态保存、全局 401 处理、网络提示；修正 README、清理死代码。
10. 身份证加校验位校验与脱敏显示。
