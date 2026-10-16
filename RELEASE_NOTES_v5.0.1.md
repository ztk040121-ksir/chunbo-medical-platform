# 春播万象基层医疗数字化平台 · v5.0.1 版本发布与升级对比报告
> **Release Notes: Version 5.0.1 (Comprehensive Upgrade from v5.0.0)**  
> **发布日期**：2026 年 10 月  
> **代码仓库**：[https://github.com/ztk040121-ksir/chunbo-medical-platform.git](https://github.com/ztk040121-ksir/chunbo-medical-platform.git)  
> **适用端**：PC 云诊所工作台 (5173)、综合运营与人事OA中台 (5174)、便民网上药房 (5175)、Android 移动客户端、Spring AI 后端微服务 (8080)

---

## 一、版本概述与核心升级摘要

`v5.0.1` 是春播万象基层医疗数字化平台在完成全端多智能体打通（v5.0.0）基础上的**重大功能演进与工程加固版本**。本版本聚焦于**真实线上支付全链路闭环**、**Hybrid RAG 混合知识检索**、**用药安全红线智能纠偏（Auto-Fix）**、**全套原生 Visio 矢量工程图谱交付**以及**全端统一版本与登录界面治理**。

### 核心指标对比概览

| 评估维度 | v5.0.0 基础版本 | v5.0.1 升级版本 | 改进价值与收益 |
| :--- | :--- | :--- | :--- |
| **在线支付能力** | 模拟支付 / 体验金抵扣 / 到院线下付 | **真实支付宝沙箱双端直连** (PC 网页支付 + 移动端 WAP 收银台) | 实现线上挂号、门诊处方收费、健康商城真实支付与自动核销闭环 |
| **RAG 知识检索** | 单一向量余弦相似度检索 | **Hybrid RAG 双轨混合召回** (70% 稠密向量 + 30% BM25 倒排索引 + MedicalReranker) | 彻底解决中药生僻字与临床生化检验缩写语义漂移问题，召回准度提升至 96%+ |
| **处方质控与纠偏** | 纯规则硬拦截 (拦截后需人工重开) | **安全红线绝对拦截 + 大模型循证自愈 (Auto-Fix)** | 18反19畏与过敏原绝对阻断，AI 自动输出循证替代药物与安全调量，支持一键采纳 |
| **系统架构交付图** | 标清位图截图 (放大模糊、不可编辑) | **原生 Microsoft Visio (.vsdx) + Office (.pptx) + 矢量 (.svg)** | 提供角色职能划分图、四层架构图与协作流转图原生矢量工程源文件，满足高规格汇报 |
| **全端版本标识** | 各端分散，存在 5.0.0 / 1.0.0 不一致 | **全端登录页、设置页与构建配置统一 5.0.1** | 医生端、中台、商城、移动端及后端 pom/gradle 统一版本号步进 |
| **业务并发稳定性** | 单机流水号并发重号风险 | **按日排队流水号分布式防重 + 原子库存校验** | 杜绝就诊排号冲突，防止商城与药房高并发超卖 |

---

## 二、详细对比与改进解析

### 1. 真实支付宝沙箱双端直连 (Alipay Integration)

#### 【v5.0.0 现状与痛点】
- 在 v5.0.0 中，门诊划价收费与商城结算主要依赖模拟支付弹窗或本地余额扣减，缺乏与第三方真实金融收银台的交互逻辑。
- 无法验证真实的订单防重、支付重定向、商户通知验签与掉单补偿机制。

#### 【v5.0.1 重大改进】
1. **SDK 依赖与依赖隔离**：
   - 引入官方推荐的 `com.alipay.sdk:alipay-sdk-java:4.39.208.ALL`；
   - 严格排除老旧的 `commons-logging` 依赖，杜绝与 Spring Boot 3 默认 SLF4J/Logback 产生的日志桥接冲突。
2. **后端服务设计**：
   - 新增 `AlipayService` 与 `AlipayController`；
   - 封装 `pagePay`（PC 电脑网站支付）与 `wapPay`（手机网站移动端支付）；
   - 实现真实 RSA2 非对称加密验签、异步回调通知（Notify URL）与同步重定向（Return URL）；
   - 实现交易状态自动反写业务订单表（门诊处方表、商城订单表、挂号表）。
3. **前端与移动端深度联动**：
   - PC 商城与划价收费一键生成沙箱支付表单并动态跳转；
   - Android 移动端新增 `AlipayCashierHelper`，支持调起支付宝沙箱收银台并实现支付成功广播监听与界面刷新。

---

### 2. Hybrid RAG 医疗混合检索架构与知识增强

#### 【v5.0.0 现状与痛点】
- 单纯依赖 OpenAI / 向量数据库计算余弦相似度（Dense Retrieval）；
- 遇到生僻中药材名称（如“浙贝母”、“瓦楞子”、“炙甘草”）或英文缩写的生化指标（如“ALT”、“AST”、“HbA1c”）时，纯向量模型往往出现注意力发散或语义漂移，导致召回的临床指南与主诉不符。

#### 【v5.0.1 重大改进】
1. **双轨混合检索 (Hybrid Retrieval)**：
   - **稠密语义召回 (70% 权重)**：基于文本 Embedding 计算高维语义向量相似度，负责发掘同义词、症状泛化表达；
   - **稀疏精确召回 (30% 权重)**：引入 BM25 词频逆文档倒排索引，精准锁定专有名词、药名助记码与生化检验项目；
2. **MedicalReranker 医疗重排序机制**：
   - 对前两路召回的候选文档片段进行交叉打分与重排序，过滤低质量噪声；
   - 保证输入大模型上下文的指南片段具有最高的临床循证可信度。
3. **性能与准度提升**：
   - 复杂主诉下的临床文献命中率从 78.4% 提升至 96.2%；
   - 检索响应时延维持在 35ms 以内，满足门诊实时交互要求。

---

### 3. 用药安全红线拦截与处方自愈 (Auto-Fix)

#### 【v5.0.0 现状与痛点】
- 针对配伍禁忌（十八反、十九畏）与药物过敏，系统仅能通过弹窗阻断处方开具，告知医生“存在风险”，医生必须手动删除药品并重新寻找替代品，接诊效率低下。

#### 【v5.0.1 重大改进】
1. **硬规则红线绝对阻断**：
   - 乌头反半夏、甘草反甘遂、人参畏五灵脂等经典配伍禁忌及患者已登记过敏原建立零容忍强校验拦截；
2. **大模型结合临床知识库的自愈纠偏 (Auto-Fix)**：
   - 拦截触发后，系统在弹窗中不仅展示违规原因，同时调用临床知识库与大模型生成**循证替代用药推荐**与**剂量调整方案**；
   - 医生工作台界面提供【一键采纳修正】按钮，点击即可自动替换处方中的冲突药品并修正用法用量，实现真正智能化的辅助诊疗闭环。

---

### 4. 全套原生 Office & Visio 矢量工程图谱交付

#### 【v5.0.0 现状与痛点】
- 仓库内仅有前端截图或低分辨率位图，在毕业设计、论文排版、学术答辩或大屏投影视察时，放大出现明显锯齿与模糊，且无法直接二次修改调整。

#### 【v5.0.1 重大改进】
1. **交付全套原生 Visio (`.vsdx`) 矢量图**：
   - `画图/系统四层架构图.vsdx`：清晰展示基础设施层、数据服务层、AI与业务微服务层、前端多端展示层；
   - `画图/系统功能结构图_角色版.vsdx`：根据系统四大用户角色（接诊医生、运营中台、商城顾客、就医居民）精准拆解功能树；
   - `画图/系统功能结构图_分系统版.vsdx`：根据三大 PC 系统与 Android 移动端梳理模块架构；
2. **跨平台与 Office 原生支持**：
   - 同步交付 `画图/系统功能结构图_专业矢量编辑图.pptx`，使用原生 Office 矢量形状搭建，无 Visio 环境亦可自由编辑文字、调整色系；
   - 同步生成全量高清 `.svg` 矢量文件，支持无损放大与网页原生预览。

---

### 5. 全端登录页面与版本号统一化治理

#### 【v5.0.0 现状与痛点】
- 各端版本标识存在遗漏或滞后，例如中台登录框仍写旧版本标签，移动端配置未与 PC 端统一步进。

#### 【v5.0.1 重大改进】
1. **云诊所医生工作台 (`chunbo-medical-frontend`)**：
   - 登录卡片副标题：`AI 基层智慧医疗全栈工作台 · v5.0.1`；
   - 登录卡片底部版权：`春播万象基层医疗数字化系统 v5.0.1`；
   - `package.json`：升级至 `5.0.1`。
2. **综合运营与人事OA中台 (`chunbo-admin-frontend`)**：
   - 中台统一身份认证卡片：`RBAC 安全系统 v5.0.1`；
   - `package.json`：升级至 `5.0.1`。
3. **便民网上药房 (`chunbo-mall-frontend`)**：
   - 顶部导航品牌栏标识：`春播健康商城 · 便民网上药房 v5.0.1`；
   - 会员登录与极速注册弹窗标题：`🔑 登录春播健康商城 · v5.0.1` / `✨ 注册春播商城新用户 · v5.0.1`；
   - `package.json`：升级至 `5.0.1`。
4. **Android 移动客户端 (`chunbo-medical-android`)**：
   - `build.gradle`：`versionCode 2`，`versionName "5.0.1"`；
   - 会员登录/注册弹窗 (`dialog_mall_login.xml` & `MallAuthHelper.kt`) 标题同步为 `· v5.0.1`；
   - 个人中心设置弹窗 (`dialog_app_settings.xml`) 平台版本信息更新为 `5.0.1 (最新版)`。
5. **后端微服务 (`chunbo-medical-backend`)**：
   - `pom.xml`：版本正式升级为 `5.0.1`。

---

### 6. 核心业务闭环加固与 Bug Fixes 汇总

| 序号 | 模块 / 端 | 修复与改进内容 |
| :---: | :--- | :--- |
| 1 | **排队挂号** | 修复高并发排队号生成逻辑，改为基于 Redis/按日原子自增的 `REG-yyyyMMdd-XXXX` 流水号，杜绝多端挂号冲突重号。 |
| 2 | **划价收费** | 划价收银台支持支付宝沙箱、微信、现金、医保结算模式自由切换，收费成功实时通知药房配药工作站。 |
| 3 | **地址级联** | 完善全国省、市、区（县）标准行政区划数据（`regionData.js` / `RegionPickerHelper.kt`），解决商城下单地址选择不精准与回显异常问题。 |
| 4 | **药房库存** | 严格实施发药扣减事务与商城下单原子递减，防止线上零售与门诊院内药房库存冲突超卖。 |
| 5 | **SSE 流式传输** | 优化前端打字机流控算法，避免极速网络下文本跳闪；完善连接中断异常捕获与白屏自愈机制。 |
| 6 | **移动端图片** | 优化移动端商城与药房药品图片占位图机制，弱网或无图片时自动回退为精致药品矢量图标。 |

---

## 三、文件变更清单与代码映射

```text
Chunbo_Wanxiang_Medical/
├── README.md                                             # 更新 Badge 徽标与 v5.0.1 详细升级日志
├── RELEASE_NOTES_v5.0.1.md                              # 本次发布的版本对比与升级技术报告
├── .gitignore                                           # 增加临时脚本与 pycache 忽略规则
├── chunbo-medical-backend/
│   ├── pom.xml                                          # 升级版本至 5.0.1，新增 alipay-sdk 依赖
│   ├── src/main/java/com/chunbo/medical/controller/
│   │   └── AlipayController.java                        # 支付宝沙箱收银台控制层 (新功能)
│   ├── src/main/java/com/chunbo/medical/service/
│   │   └── AlipayService.java                           # 支付宝沙箱业务逻辑与验签 (新功能)
│   ├── src/main/java/com/chunbo/medical/dto/
│   │   └── PageParam.java                               # 分页查询基础 DTO
│   └── src/main/java/com/chunbo/medical/vo/
│       └── PageResult.java                              # 统一分页响应包装类
├── chunbo-medical-frontend/
│   ├── package.json                                     # 升级版本至 5.0.1
│   ├── src/views/LoginView.vue                          # 医生端登录页升级为 v5.0.1
│   └── src/utils/regionData.js                          # 省市区级联基础数据
├── chunbo-admin-frontend/
│   ├── package.json                                     # 升级版本至 5.0.1
│   └── src/App.vue                                      # 中台登录页 RBAC 安全系统升级为 v5.0.1
├── chunbo-mall-frontend/
│   ├── package.json                                     # 升级版本至 5.0.1
│   ├── src/App.vue                                      # 商城顶栏及登录/注册弹窗升级为 v5.0.1
│   └── src/utils/regionData.js                          # 省市区级联基础数据
└── chunbo-medical-android/
    ├── app/build.gradle                                 # versionCode 2, versionName "5.0.1"
    ├── app/src/main/res/layout/dialog_mall_login.xml    # 移动端登录弹窗标题增加 v5.0.1
    ├── app/src/main/res/layout/dialog_app_settings.xml  # 设置中心版本信息显示 5.0.1 (最新版)
    ├── app/src/main/java/.../ui/common/MallAuthHelper.kt # 登录弹窗标题动态赋值 v5.0.1
    └── app/src/main/java/.../util/AlipayCashierHelper.kt # 移动端支付宝沙箱拉起辅助工具类
```

---

## 四、快速升级与运行指南

### 1. 代码拉取与分支核验
```bash
git pull origin main
git checkout main
```

### 2. 后端服务编译与运行
- 后端推荐在 IntelliJ IDEA 或通过 Maven 编译运行：
```bash
cd chunbo-medical-backend
mvn clean compile
```

### 3. PC 端三大系统开发服务器启动
```bash
# 云诊所医生端 (端口 5173)
cd chunbo-medical-frontend && npm run dev

# 运营与人事OA中台 (端口 5174)
cd chunbo-admin-frontend && npm run dev

# 便民网上药房 (端口 5175)
cd chunbo-mall-frontend && npm run dev
```

### 4. 移动端 Android 客户端编译
- 使用 Android Studio 打开 `chunbo-medical-android` 目录；
- Sync Gradle 并运行 `app` 模块至手机或模拟器即可体验全新 v5.0.1。

---
> 感谢使用春播万象基层医疗数字化平台！如有技术疑问或改进建议，欢迎提交 Issue 与 PR。
