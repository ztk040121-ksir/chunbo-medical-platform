# 春播万象 · 基层医疗多智能体平台

> 基于 Spring AI 的基层医疗数字化平台，集 **云诊所智能问诊**、**运营管理中台**、**便民网上药房** 三大子系统于一体，深度集成 MCP 协议工具调用、RAG 用药知识库检索与多智能体协同。

![Tech](https://img.shields.io/badge/Spring%20Boot-3.2.5-brightgreen) ![AI](https://img.shields.io/badge/Spring%20AI-1.0.0--M7-blue) ![Vue](https://img.shields.io/badge/Vue-3-green) ![JDK](https://img.shields.io/badge/JDK-17-orange) ![License](https://img.shields.io/badge/License-MIT-lightgrey)

---

## 目录

- [项目简介](#项目简介)
- [三大子系统](#三大子系统)
- [技术栈](#技术栈)
- [核心亮点](#核心亮点)
- [整体架构](#整体架构)
- [目录结构](#目录结构)
- [快速启动](#快速启动)
- [配置说明](#配置说明)
- [版本](#版本)

---

## 项目简介

春播万象是一个面向基层医疗机构的数字化平台，覆盖「挂号 → 接诊 → AI 问诊 → 处方质控 → 划价收费 → 药房发药」的全业务闭环，同时提供运营管理中台与便民网上药房。平台以 **AI 智能体** 为核心竞争力，通过 Spring AI 实现了多智能体协同、MCP 协议工具调用与 RAG 检索增强生成。

## 三大子系统

| 子系统 | 前端端口 | 说明 |
| --- | --- | --- |
| 🩺 云诊所（智能问诊工作站） | 5173 | 挂号、接诊、AI 辨证问诊、处方开立、划价收费、发药闭环 |
| 🏢 运营管理中台（春播助手） | 5174 | 薪酬核算、OA 审批、贴敷运营统计、商城履约、运营大盘 |
| 🛒 便民网上药房（春播商城） | 5175 | 症状选药、用药禁忌审查、B2C 下单、物流履约 |

三个子系统共享同一个后端中台（`chunbo-medical-backend`）。

## 技术栈

- **后端**：Spring Boot 3.2.5、Spring AI 1.0.0-M7、MyBatis-Plus 3.5.5、MySQL 8.0、Redis、JWT、WebFlux（SSE）
- **前端**：Vue 3、Element Plus、Vite、Axios
- **AI**：OpenAI 兼容大模型网关（Chat 流式 + Embedding 向量化）、Function Calling、MCP Server
- **工程**：Maven、全局异常处理、统一鉴权、原子化库存扣减

## 核心亮点

- **SSE 流式问诊**：基于 WebFlux `Flux<String>` 实现逐字流式输出，支持中途停止生成
- **MCP 协议工具集**：将患者档案、历史处方、药品库存、薪酬核算、OA 审批、支付审计、数据库操作、网页抓取等 15 个能力封装为 MCP 工具（`http://localhost:8080/sse`）
- **RAG 用药知识库**：`rag_docs/` 临床诊疗规范 → 切块 → Embedding 向量化 → 余弦相似度检索，问答有据可依，回答标注知识库引用来源
- **多智能体协同**：云诊所问诊智能体、OA 运营智能体、商城药师智能体，各自带角色与意图区分
- **意图区分**：知识咨询只答知识、开方场景才输出处方，避免"问什么都给处方"
- **用药安全红线**：青霉素/头孢过敏史强拦截，配伍禁忌审查
- **并发安全**：发药库存原子扣减防超卖、Redis 排队号自增
- **鉴权体系**：JWT + 角色（ADMIN/HR/DOCTOR/USER）+ 接口级权限校验 + 全局异常处理

## 整体架构

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

## 目录结构

```
chunbo-medical-platform/
├── chunbo-medical-backend/     # 后端中台（Spring Boot）
│   ├── src/main/java/com/chunbo/medical/
│   │   ├── controller/         # 28 个 REST 控制器
│   │   ├── service/            # 业务服务（问诊/RAG/支付审计/多智能体…）
│   │   ├── tools/              # MCP 工具（医疗/OA/支付/DB/网页抓取）
│   │   └── config/             # 鉴权/MCP/异常/模型配置
│   └── src/main/resources/     # application.yml 等
├── chunbo-medical-frontend/    # 云诊所前端（Vue3）
├── chunbo-admin-frontend/      # 管理中台前端（Vue3）
├── chunbo-mall-frontend/       # 便民商城前端（Vue3）
├── rag_docs/                   # RAG 用药知识库文档
├── sql/                        # 数据库建表脚本
├── chunbo-mall-agent/          # 商城智能体 SOP/压测/契约文档
└── chunbo-oa-assistant/        # OA 智能体 SOP/压测/契约文档
```

## 快速启动

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

> 后端 AI 对话需走 OpenAI 兼容网关；本地若需 VPN 代理访问模型，请保持 `chunbo.ai.proxy` 配置正确。

## 配置说明

| 配置项 | 说明 |
| --- | --- |
| `OPENAI_API_KEY` | 环境变量，大模型网关密钥（**不要提交到仓库**） |
| `DB_PASSWORD` | 环境变量，数据库密码（本地可默认 `12345`） |
| `chunbo.ai.proxy` | 访问模型的本地代理（host/port） |
| `spring.ai.mcp.server` | MCP 服务端配置（默认 `http://localhost:8080/sse`） |
| `spring.ai.mcp.client` | MCP 客户端（魔搭等外部 MCP，需 Spring AI 稳定版再启用） |

> ⚠️ 密钥管理：真实密钥请放在 `application-local.yml`（已被 `.gitignore` 忽略），勿写入 `application.yml` 或代码。生产环境请同步替换 JWT 签名密钥。

## 版本

- **v1.0.0** — 首个完整版本：三子系统闭环、MCP + RAG + 多智能体、鉴权与并发安全加固
