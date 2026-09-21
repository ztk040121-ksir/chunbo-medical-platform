# 春播万象 · 基层医疗多智能体平台

> 基于 **Spring AI** 的基层医疗数字化平台，集 **云诊所智能问诊**、**运营管理中台**、**便民网上药房** 三大子系统于一体，深度集成 **MCP 协议工具调用**、**RAG 用药知识库检索** 与 **多智能体协同**。

![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen) ![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0.0-blue) ![Vue](https://img.shields.io/badge/Vue-3-green) ![JDK](https://img.shields.io/badge/JDK-17-orange) ![Version](https://img.shields.io/badge/Version-2.0.0-red) ![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

## 目录

- [一、项目简介](#一项目简介)
- [二、三大子系统](#二三大子系统)
- [三、技术栈](#三技术栈)
- [四、核心亮点](#四核心亮点)
- [五、整体架构](#五整体架构)
- [六、目录结构](#六目录结构)
- [七、快速启动](#七快速启动)
- [八、配置说明](#八配置说明)
- [九、多智能体协作机制](#九多智能体协作机制)
- [十、版本历史](#十版本历史)

---

## 一、项目简介

春播万象是一个面向基层医疗机构的数字化平台，覆盖「挂号 → 接诊 → AI 问诊 → 处方质控 → 划价收费 → 药房发药」的全业务闭环，同时提供运营管理中台与便民网上药房。平台以 **AI 智能体** 为核心竞争力，通过 Spring AI 实现了多智能体协同、MCP 协议工具调用与 RAG 检索增强生成。

## 二、三大子系统

| 子系统 | 前端端口 | 说明 |
| --- | --- | --- |
| 🩺 云诊所（智能问诊工作站） | 5173 | 挂号、接诊、AI 辨证问诊、处方开立、划价收费、发药闭环 |
| 🏢 运营管理中台（春播助手） | 5174 | 薪酬核算、OA 审批、贴敷运营统计、商城履约、运营大盘 |
| 🛒 便民网上药房（春播商城） | 5175 | 症状选药、用药禁忌审查、B2C 下单、物流履约 |

三个子系统共享同一个后端中台（`chunbo-medical-backend`）。

## 三、技术栈

- **后端**：Spring Boot 3.2.5、Spring AI 1.0.0（GA）、MyBatis-Plus 3.5.5、MySQL 8.0、Redis、JWT、WebFlux（SSE）
- **前端**：Vue 3、Element Plus、Vite、Axios
- **AI**：OpenAI 兼容大模型网关（Chat 流式 + Embedding 向量化）、Function Calling、MCP Server、语音识别（Whisper）/ 语音合成（TTS）
- **工程**：Maven、全局异常处理、统一鉴权、原子化库存扣减

## 四、核心亮点

- **SSE 流式问诊**：基于 WebFlux `Flux<String>` 实现逐字流式输出，支持中途停止生成
- **MCP 协议工具集**：将患者档案、历史处方、药品库存、薪酬核算、OA 审批、支付审计、数据库操作、网页抓取等 15 个能力封装为 MCP 工具
- **RAG 用药知识库**：`rag_docs/` 临床诊疗规范 → 切块 → Embedding 向量化 → 官方向量库相似度检索，问答有据可依，回答标注知识库引用来源
- **多智能体协同**：云诊所问诊智能体、OA 运营智能体、商城药师智能体，统一「路由工作流」框架协同
- **意图区分**：知识咨询只答知识、开方场景才输出处方，避免"问什么都给处方"
- **用药安全红线**：青霉素/头孢过敏史强拦截，配伍禁忌审查
- **并发安全**：发药库存原子扣减防超卖、Redis 排队号自增
- **鉴权体系**：JWT + 角色（ADMIN/HR/DOCTOR/USER）+ 接口级权限校验 + 全局异常处理
- **语音输入**：自动选麦 + 自适应增益（AGC）+ 静音拒发 + 后端幻听过滤，多设备环境下稳定识别

## 五、整体架构

```
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ 云诊所 5173  │  │ 管理中台 5174 │  │ 便民商城 5175 │
└──────┬──────┘  └──────┬──────┘  └──────┬──────┘
       └────────────────┴────────────────┘
                        │ /api/**
                 ┌──────▼──────┐
                 │ 后端中台 8080 │  Spring AI + MCP Server + RAG
                 └──────┬──────┘
            ┌───────────┼────────────┐
       ┌────▼───┐  ┌────▼────┐  ┌────▼────┐
       │ MySQL 8 │  │  Redis  │  │ LLM 网关 │
       └────────┘  └─────────┘  └─────────┘
```

## 六、目录结构

```
chunbo-medical-platform/
├── chunbo-medical-backend/     # 后端中台（Spring Boot）
│   ├── src/main/java/com/chunbo/medical/
│   │   ├── controller/         # REST 控制器（问诊/会话/音频/商城/OA…）
│   │   ├── service/            # 业务服务（问诊/RAG/支付审计/多智能体…）
│   │   ├── agent/              # 多智能体框架（Agent/AbstractAgent/RouteAgent/AgentRouter）
│   │   ├── tools/              # MCP 工具（医疗/OA/支付/DB/网页抓取）
│   │   ├── memory/             # 会话记忆（Redis 持久化 + 序列化）
│   │   └── config/             # 鉴权/MCP/异常/模型/向量库配置
│   └── src/main/resources/     # application.yml、schema/data-h2.sql 等
├── chunbo-medical-frontend/    # 云诊所前端（Vue3）
├── chunbo-admin-frontend/      # 管理中台前端（Vue3）
├── chunbo-mall-frontend/       # 便民商城前端（Vue3）
├── rag_docs/                   # RAG 用药知识库文档（糖尿病/高血压/上呼吸道感染诊疗规范）
└── sql/                        # 数据库建表与初始化脚本
```

## 七、快速启动

### 前置环境

- JDK 17、Maven 3.8+
- MySQL 8.0（初始化 `sql/` 下的建表脚本，默认库名 `chunbo_medical`）
- Redis（可选，缺省时排队号自动降级为 count+1）
- Node.js 18+（前端）

### 1. 后端

```bash
cd chunbo-medical-backend
# 首次运行：复制本地配置模板并填入密钥
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
# 启动（加载 local 配置）
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### 2. 前端（三个分别启动）

```bash
cd chunbo-medical-frontend && npm install && npm run dev   # 云诊所 5173
cd chunbo-admin-frontend && npm install && npm run dev     # 管理中台 5174
cd chunbo-mall-frontend && npm install && npm run dev      # 便民商城 5175
```

### 3. 访问

| 端 | 地址 | 默认账号 |
| --- | --- | --- |
| 云诊所 | http://localhost:5173 | kzt / 123456 |
| 管理中台 | http://localhost:5174 | admin / 123456 |
| 便民商城 | http://localhost:5175 | 游客可逛，注册后下单 |

## 八、配置说明

| 配置项 | 说明 |
| --- | --- |
| `OPENAI_API_KEY` | 环境变量，大模型网关密钥（**不要提交到仓库**） |
| `DB_PASSWORD` | 环境变量，数据库密码（本地可默认 `12345`） |
| `spring.ai.openai.api-key` | 大模型密钥，源码中已用 `sk-YOUR_API_KEY_HERE` 占位，请在 `application-local.yml` 填入真实值 |
| `spring.ai.mcp.server` | MCP 服务端配置（默认 `http://localhost:8080/sse`） |

> ⚠️ 密钥管理：真实密钥请放在 `application-local.yml`（已被 `.gitignore` 忽略），勿写入 `application.yml` 或代码。生产环境请同步替换 JWT 签名密钥。

---

## 九、多智能体协作机制

### 9.1 协作架构总览

三个业务域共用**同一套「路由工作流」多智能体框架**。

```
Controller（SSE 入口）
   │  调用 agentRouter.route(routeAgent, defaultAgent, question, sessionId, userId, context)
   ▼
AgentRouter.route()
   1. routeAgent.process(question)  —— 一次 LLM 调用，判断用户意图，返回类型名（如 MALL_RECOMMEND / MED_DIAGNOSE / OA_SALARY）
   2. AgentTypeEnum.agentNameOf(intent) —— 意图名 → 枚举
   3. findAgentByType(type) —— 从 Spring 容器按枚举找到对应业务 Agent
   4. target.processStream(...) —— 委派给业务 Agent 流式处理（意图名作为 routeHint 透传）
   │
   ▼
AbstractAgent.processStream()  —— 统一套上：停止生成 + 会话记忆 + 卡片提取 + AI 提炼标题 + STOP 事件
   │
   ▼
业务 Agent.buildContentFlux()  —— 各自实现业务内容流（function-calling 或确定性引擎）
```

### 9.2 核心类清单

| 类 | 文件 | 职责 |
|---|---|---|
| `Agent` 接口 | `agent/Agent.java` | 定义所有智能体的统一契约（processStream / process / getAgentType / stop / tools / toolContext / bizType） |
| `AbstractAgent` 抽象类 | `agent/AbstractAgent.java` | 封装通用逻辑：停止生成、流式、会话记忆、ToolResultHolder 卡片提取、AI 提炼标题、function-calling 发起 |
| `RouteAgent` 抽象类 | `agent/RouteAgent.java` | 路由智能体基类：`process()` 用一次非流式 LLM 判意图 |
| `AgentRouter` | `agent/AgentRouter.java` | 路由框架：判意图 → 找 Agent → 委派 |
| `ToolResultHolder` | `config/ToolResultHolder.java` | 卡片结果容器（requestId → 结构化数据，并发安全） |
| `AgentTypeEnum` | `enums/AgentTypeEnum.java` | 全部智能体类型枚举（MALL_*/MED_*/OA_*） |

### 9.3 统一契约与框架

**① Agent 接口 —— 统一契约**

```java
public interface Agent {
    Object[] EMPTY_OBJECTS = new Object[0];

    Flux<ChatEventVO> processStream(String question, String sessionId, String userId);
    default Flux<ChatEventVO> processStream(String question, String sessionId, String userId, String routeHint) {
        return processStream(question, sessionId, userId);
    }
    default Flux<ChatEventVO> processStream(String question, String sessionId, String userId, String routeHint, Map<String, Object> context) {
        return processStream(question, sessionId, userId, routeHint);
    }
    String process(String question, String sessionId, String userId); // 非流式（路由智能体用）
    AgentTypeEnum getAgentType();      // 每个业务智能体的类型标识
    void stop(String sessionId);       // 停止生成
    default String systemMessage() { return ""; }
    default Object[] tools() { return EMPTY_OBJECTS; }
    default Map<String, Object> toolContext(String sessionId, String requestId) { return Map.of(); }
    default String bizType() { return "general"; } // 会话历史分组用
}
```

**② AbstractAgent —— 通用逻辑封装（模板方法）**

```java
public abstract class AbstractAgent implements Agent {
    @Autowired protected ChatMemory chatMemory;
    @Autowired protected ChatSessionService chatSessionService;
    @Autowired protected AiModelConfigService aiModelConfigService;

    public static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>(); // 停止生成标记
    private static final ThreadLocal<String> CURRENT_REQUEST_ID = new ThreadLocal<>();

    // 子类只需实现业务内容流（DATA 事件）
    protected abstract Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId);

    @Override
    public Flux<ChatEventVO> processStream(...) {
        String requestId = UUID.randomUUID().toString().replace("-", "");
        CURRENT_REQUEST_ID.set(requestId);
        Flux<ChatEventVO> content = buildContentFlux(...);
        StringBuffer outputBuilder = new StringBuffer(); // 线程安全收集已输出文字
        return wrapWithToolResult(requestId, content
                .doFirst(() -> GENERATE_STATUS.put(sessionId, true))   // ①开始标记
                .doOnError(e -> GENERATE_STATUS.remove(sessionId))     // ②出错清理
                .doOnComplete(() -> GENERATE_STATUS.remove(sessionId)) // ③完成清理
                .doOnNext(ev -> { if (DATA 文本) outputBuilder.append(text); })
                .doOnCancel(() -> saveStopHistoryRecord(sessionId, outputBuilder.toString())) // ④取消保存半截
                .takeWhile(ev -> GENERATE_STATUS.getOrDefault(sessionId, false)) // ⑤停止开关
                .doFinally(s -> updateSession(bizType(), sessionId, userId, question, outputBuilder.toString()))); // ⑥更新会话历史
    }

    // 统一包装：流末尾把 ToolResultHolder 里的结构化数据转成 PARAM 卡片下发，再接 STOP 事件
    private Flux<ChatEventVO> wrapWithToolResult(String requestId, Flux<ChatEventVO> content) {
        return content
                .concatWith(Flux.defer(() -> {
                    Map<String, Object> toolResult = ToolResultHolder.get(requestId);
                    if (toolResult != null && !toolResult.isEmpty()) {
                        ToolResultHolder.remove(requestId);
                        return Flux.just(PARAM事件(toolResult), STOP事件);
                    }
                    return Flux.just(STOP事件);
                }))
                .doFinally(s -> { CURRENT_REQUEST_ID.remove(); ToolResultHolder.remove(requestId); });
    }
}
```

**③ RouteAgent —— 路由基类（一次 LLM 判意图）**

```java
public abstract class RouteAgent extends AbstractAgent {
    @Autowired protected ChatModel chatModel;

    @Override
    public String process(String question, String sessionId, String userId) {
        String content = ChatClient.builder(chatModel).build()
                .prompt().system(systemMessage()).user(question).call().content();
        return content == null ? null : content.trim(); // 返回意图名，如 "MED_DIAGNOSE"
    }
}
```

每个域继承并只写一个 `systemMessage()` 定义本域意图集合：

```java
public class MedRouteAgent extends RouteAgent {
    @Override public AgentTypeEnum getAgentType() { return AgentTypeEnum.MED_ROUTE; }
    @Override public String systemMessage() {
        return "你是春播云诊所的问诊路由助手...只输出以下类型之一：\n"
             + "MED_STOCK（查药房库存/药品台账/进销存/缺药）\n"
             + "MED_DIAGNOSE（辨证开方/拟定治疗方案/推荐处方）\n"
             + "MED_KNOWLEDGE（用药知识/诊疗规范/医学常识咨询）";
    }
}
```

**④ AgentRouter —— 路由委派**

```java
@Component
public class AgentRouter {
    @Autowired private List<Agent> agents; // Spring 注入所有 Agent 实现

    public Flux<ChatEventVO> route(Agent routeAgent, Agent defaultAgent,
                                   String question, String sessionId, String userId, Map<String, Object> context) {
        Agent target = null; String intent = null;
        try {
            intent = routeAgent.process(question, sessionId, userId); // 一次 LLM 判意图
            AgentTypeEnum type = AgentTypeEnum.agentNameOf(intent);
            if (type != null && !type.getAgentName().endsWith("_ROUTE")) target = findAgentByType(type);
        } catch (Exception ignored) { }
        if (target == null) target = defaultAgent; // 路由失败/未命中 → 兜底业务 Agent
        return target.processStream(question, sessionId, userId, intent, context); // 意图作为 routeHint 透传
    }

    private Agent findAgentByType(AgentTypeEnum type) {
        for (Agent agent : agents) if (agent.getAgentType() == type) return agent;
        return null;
    }
}
```

### 9.4 三个智能体的实现逻辑

**业务 Agent 映射表**

| 域 | 路由 Agent | 业务 Agent | 业务实现 |
|---|---|---|---|
| 云诊所 | `MedRouteAgent` | `MedDiagnoseAgent`(MED_DIAGNOSE) / `MedStockAgent`(MED_STOCK) / `MedKnowledgeAgent`(MED_KNOWLEDGE，兜底) | `MedBaseAgent` → `MedicalChatService` |
| OA 中台 | `OaRouteAgent` | `OaSalaryAgent` / `OaApprovalAgent` / `OaAnalyticsAgent` / `OaInventoryAgent` / `OaOrderAgent` / `OaPlasterAgent` / `OaGeneralAgent`(兜底) | `OaBaseAgent` → `AssistantController` |
| 商城 | `MallRouteAgent` | `MallRecommendAgent` / `MallConsultAgent` / `MallOrderAgent` / `MallGuardAgent` / `MallShippingAgent` / `MallGeneralAgent`(兜底) | `MallBaseAgent` → `B2bMultiAgentService` |

每个业务 Agent 只 override 差异点：

```java
@Component
public class MedDiagnoseAgent extends MedBaseAgent {
    @Override public AgentTypeEnum getAgentType() { return AgentTypeEnum.MED_DIAGNOSE; }
    @Override protected String skillHint() { return "MED_DIAGNOSE"; }
}
```

**① 云诊所问诊**（`/api/medical/chat/stream`）

- `MED_DIAGNOSE`（辨证开方）：**真正的 function-calling** —— LLM 自主规划调用患者档案/库存/指南工具；流末尾独立提取 `rxItems` 处方卡片。开方前患者解析优先级：**消息点名 > 已接诊患者 > 提示先接诊**。
- `MED_STOCK`（药房库存）/ `MED_KNOWLEDGE`（用药知识）：库存用真实 SQL 穿透引擎（避免 LLM 编库存数字），知识咨询走 LLM + RAG。

**② OA 中台助手**（`/api/assistant/chat/stream`）

- `OA_SALARY`（薪资）：function-calling，`querySalarySlip` 工具内部做 RBAC（医生只能查自己，ADMIN/HR 查任意）。
- 其余技能（订单/库存/大盘/审批/贴敷/通用）：确定性技能引擎（真实 DB 穿透）+ LLM 兜底。

**③ 商城小药师**（`/api/mall/chat/stream`）

- 把意图传给 `B2bMultiAgentService.runMultiAgentWorkflow`，按意图 + 关键词分流到 6 类技能（订单物流 / 用药禁忌 / 商品清单 / 配送政策 / 对症推荐 / 用药咨询）。
- 推荐优先级：**LLM 智能推荐 > 关键词动态匹配 > 模板兜底**，推荐结果 → `recommendations` 卡片。

### 9.5 四大技术机制

#### ① RAG（知识库检索）

**向量库 Bean**（`config/SpringAiConfig.java`）：

```java
@Bean
public VectorStore vectorStore(EmbeddingModel embeddingModel) {
    return SimpleVectorStore.builder(embeddingModel).build(); // 官方 SimpleVectorStore
}
```

**知识库服务**（`service/RagKnowledgeService.java`）：

```java
@Service
public class RagKnowledgeService {
    @Autowired(required = false) private VectorStore vectorStore;

    // 启动时加载 rag_docs/*.md → 按 "## " 切块 → 构造 Document → 写入 VectorStore
    public synchronized void load() { ... vectorStore.add(documents); }

    // 检索：官方 similaritySearch(SearchRequest)，失败/为空降级关键词
    public List<String> search(String query, int topK) {
        if (vectorStore != null) {
            SearchRequest request = SearchRequest.builder()
                    .query(query).topK(topK).similarityThresholdAll().build();
            List<Document> docs = vectorStore.similaritySearch(request);
            // 从 Document 取 text + metadata.title 拼成片段
        }
        return keywordSearch(query, topK); // 关键词降级
    }

    // 以 @Tool 暴露给大模型
    @Tool(description = "检索基层诊疗与用药规范知识库（RAG）...")
    public String searchKnowledge(@ToolParam String query) { ... }
}
```

> 依赖：`spring-ai-vector-store`（提供 SimpleVectorStore / VectorStore / SearchRequest）。

#### ② 会话记忆（Redis 持久化）

```java
@Bean
public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
    return MessageWindowChatMemory.builder()          // 滑动窗口限流
            .chatMemoryRepository(chatMemoryRepository) // Redis 持久化
            .maxMessages(100)                          // 最多保留 100 条
            .build();
}
```

`memory/RedisChatMemoryRepository.java`：key = `CHAT:` + conversationId，`StringRedisTemplate` 读写 List；内存 `ConcurrentHashMap` 兜底。`memory/MessageUtil.java` + `memory/MyMessage.java` 负责序列化（Spring AI `Message.getText()` 无稳定序列化，用扁平 `MyMessage` 存 `messageType + textContent`）。

接入点：`MessageChatMemoryAdvisor.builder(chatMemory).build()` 挂到 ChatClient，请求时通过 `advisors(a -> a.param("chat_memory_conversation_id", sessionId))` 指定会话 id。

#### ③ 会话历史（MySQL + AI 提炼标题）

表 `chat_session(id, session_id, biz_type, user_id, title, create_time, update_time)`。

```java
@Service
public class ChatSessionService {
    @Autowired @Qualifier("titleChatClient") private ChatClient titleChatClient; // 独立提炼标题 Client

    // 异步更新：不存在则建会话；标题为空则用独立 ChatClient + 独立 prompt 提炼
    @Async("chatSessionExecutor")
    public void update(String bizType, String sessionId, String userId, String content) { ... }

    // 历史查询（最多30条，按 当天/最近30天/最近1年/1年以上 分组）
    public Map<String, List<ChatSessionVO>> queryHistory(String bizType, String userId) { ... }

    // 删除：物理删 DB + 清 Redis 记忆
    public void deleteSession(String bizType, String sessionId, String userId) { ...; chatMemory.clear(sessionId); }
}
```

标题提炼用独立 `titleChatClient`（不挂工具/记忆/医疗人设），在 `AbstractAgent.processStream` 的 `doFinally` 里异步调用。

#### ④ 停止生成（后端 Flux 流控）

核心：`GENERATE_STATUS`（ConcurrentHashMap<String, Boolean>，key=sessionId）+ Flux 操作符链。原理是"打断输出流"。

```java
content
  .doFirst(() -> GENERATE_STATUS.put(sessionId, true))      // ①开始标记
  .doOnError(e -> GENERATE_STATUS.remove(sessionId))        // ②出错清理
  .doOnComplete(() -> GENERATE_STATUS.remove(sessionId))    // ③完成清理
  .doOnCancel(() -> saveStopHistoryRecord(sessionId, outputBuilder.toString())) // ④取消保存半截（必须放 takeWhile 之上）
  .takeWhile(ev -> GENERATE_STATUS.getOrDefault(sessionId, false)) // ⑤关键开关
  .doFinally(s -> updateSession(...))                       // ⑥更新会话历史
```

前端停止按钮 = "先调后端 `/stop` 接口（移除标记 → takeWhile 终止 Flux）+ 再 AbortController 断开 SSE 连接"。

### 9.6 事件流协议（前端消费约定）

`ChatEventVO { eventData, eventType }`，`ChatEventTypeEnum`：

| eventType | 值 | 含义 |
|---|---|---|
| DATA | 1001 | 文字流 |
| STOP | 1002 | 结束 |
| PARAM | 1003 | 结构化卡片 |
| PROCESS | 1004 | 过程事件（生成中可见、完成后隐藏） |

前端按 `eventData` 字段名消费：问诊 `rxItems`（处方卡片）/`kbTitles`（知识库引用），商城 `recommendations`（推荐卡片）。

---

## 十、版本历史

- **v1.0.0** — 首个完整版本：三子系统闭环、MCP + RAG + 多智能体、鉴权与并发安全加固
- **v2.0.0** — 本轮升级：
  - RAG 底层官方化（`SimpleVectorStore` 向量库），纯走官方向量检索
  - 卡片机制统一（`ToolResultHolder` 统一下发 `rxItems`/`kbTitles`/`recommendations`）
  - 假数据/空架子全面真实化（工资、诊疗、商品、病历、购物车、药房导出均接真实 DB/LLM）
  - 语音输入四层修复（自动选麦 + AGC 增益 + 静音拒发 + 后端幻听过滤 + 繁转简）
  - 患者解析优先级（消息点名 > 接诊患者 > 提示接诊）、开方卡片补齐、会话标题/子标签持久化
  - 并发与线程安全加固（并发容器、异步线程池、NPE/越界修复）
