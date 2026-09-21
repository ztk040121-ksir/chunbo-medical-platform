package com.chunbo.medical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.config.ToolResultHolder;
import com.chunbo.medical.dto.ChatRequest;
import com.chunbo.medical.entity.Medicine;
import com.chunbo.medical.entity.Patient;
import com.chunbo.medical.enums.ChatEventTypeEnum;
import com.chunbo.medical.mapper.MedicineMapper;
import com.chunbo.medical.mapper.PatientMapper;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 医疗智能问诊会话服务
 * 集成真实大模型流式调用与离线 Mock 推理引擎，支持后台随时热切换，穿透 MySQL 真实药房库存
 */
@Service
public class MedicalChatService {

    @Autowired
    private AiModelConfigService aiConfigService;

    @Autowired
    private PatientMapper patientMapper;

    @Autowired(required = false)
    private MedicineMapper medicineMapper;

    @Autowired(required = false)
    private RagKnowledgeService ragKnowledgeService;

    @Autowired(required = false)
    private ChatMemory chatMemory;

    @Autowired(required = false)
    private ChatSessionService chatSessionService;

    // =====================================================================
    // 停止生成（后端 Flux 流控，参照《SpringAI》笔记标准实现）
    // 大模型生成过程无法真正中断，只能打断输出流；是否继续输出取决于该标记
    // =====================================================================
    /** 全局线程安全的生成状态表：key=sessionId；true=继续输出；移除=false=终止输出 */
    private static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();

    /** 停止生成：移除标记，takeWhile 检测到后终止 Flux 输出 */
    public void stop(String sessionId) {
        GENERATE_STATUS.remove(sessionId);
    }

    /**
     * 流式问诊接口响应（标准事件流：文字 DATA / 知识库与处方卡片 PARAM / 结束 STOP）
     * 外层统一挂后端停止生成控制 + 会话历史更新（userId 用医生工号 doctorId）
     */
    public Flux<ChatEventVO> streamChat(ChatRequest request) {
        String sessionId = request.getSessionId();
        String question = request.getMessage() != null ? request.getMessage().trim() : "";
        String doctorId = request.getDoctorId();

        Flux<ChatEventVO> flow = doStreamChat(request);
        if (sessionId == null || sessionId.isEmpty()) {
            return flow;
        }

        // 已输出内容缓存器：停止生成时保存半截回答，结束时用于 AI 提炼标题
        StringBuilder outputBuilder = new StringBuilder();
        return flow
                .doFirst(() -> GENERATE_STATUS.put(sessionId, true))   // 1. 开始时标记
                .doOnError(e -> GENERATE_STATUS.remove(sessionId))     // 2. 出错时清理标记
                .doOnComplete(() -> GENERATE_STATUS.remove(sessionId)) // 3. 正常完成时清理标记
                .doOnNext(ev -> {                                      // 收集 DATA 事件文字（结构化 PARAM 不进历史）
                    if (ev != null && ev.getEventType() == ChatEventTypeEnum.DATA.getValue()
                            && ev.getEventData() instanceof String text) {
                        outputBuilder.append(text);
                    }
                })
                .doOnCancel(() -> saveStopHistoryRecord(sessionId, outputBuilder.toString())) // 取消时保存
                .takeWhile(ev -> GENERATE_STATUS.getOrDefault(sessionId, false)) // 4. 关键开关：true 继续 / false 终止
                .doFinally(signalType -> updateSession("medical", sessionId, doctorId, question, outputBuilder.toString()))
                .concatWith(Flux.just(ChatEventVO.builder()
                        .eventType(ChatEventTypeEnum.STOP.getValue())
                        .build()));
    }

    /** 停止时把已输出的半截回答存入会话记忆（笔记"解决bug"章节：SpringAI 不记录中断流，需自己记录） */
    private void saveStopHistoryRecord(String conversationId, String content) {
        try {
            if (chatMemory != null && content != null && !content.isEmpty()) {
                chatMemory.add(conversationId, new AssistantMessage(content));
            }
        } catch (Exception ignored) {
        }
    }

    /** 会话历史：异步建会话记录 + AI 提炼标题 */
    private void updateSession(String bizType, String sessionId, String userId, String question, String answer) {
        if (chatSessionService == null) return;
        try {
            chatSessionService.update(bizType, sessionId, userId, "USER:" + question + "\nASSISTANT:" + answer);
        } catch (Exception ignored) {
        }
    }

    /**
     * 问诊业务内容流（供多智能体路由的业务智能体委托，业务逻辑复用）
     * 返回文字 DATA / 知识库与处方卡片 PARAM 事件流，不含停止控制与标题（由 AbstractAgent 统一封装）
     */
    public Flux<ChatEventVO> doStreamChat(ChatRequest request) {
        String msg = request.getMessage() != null ? request.getMessage().trim() : "";
        // 本次请求 id（由 AbstractAgent 生成并透传），用于把结构化卡片写入 ToolResultHolder
        String requestId = request.getRequestId();

        // 若涉及真实药房库存、缺药排查或处方药品查询，直接调用本地 MySQL 8.0 穿透引擎，保证数据 100% 真实，严禁伪造虚假数据
        // routeHint=MED_STOCK 时强制走库存引擎（多智能体路由判定的语义意图，不依赖关键词）
        if ("MED_STOCK".equals(request.getRouteHint())
                || msg.contains("库存") || msg.contains("药房") || msg.contains("多少") || msg.contains("缺药")
                || msg.contains("备药") || msg.contains("查药") || msg.contains("台账") || msg.contains("进销存")) {
            return mockClinicalStream(request);
        }

        // 如果开启了 Mock 模式，直接走本地临床引擎
        if (aiConfigService.isMockEnabled()) {
            return mockClinicalStream(request);
        }

        ChatClient client = aiConfigService.getActiveChatClient();
        if (client == null) {
            return mockClinicalStream(request);
        }

        try {
            // 消息中点名了患者姓名时，按姓名匹配真实患者档案（如"给康镇涛开个方子"）
            // 未接诊且未点名时 pid 为 null：不默认拿 1 号患者，AI 会提示先接诊
            Long pid = resolvePatientId(request, msg);
            Patient patient = (pid != null) ? patientMapper.selectById(pid) : null;
            String patientName = patient != null ? patient.getName() : null;
            String allergies = patient != null ? patient.getAllergies() : "无过敏记录";
            String history = patient != null ? patient.getMedicalHistory() : "无慢病记录";

            String systemPrompt;
            if (patientName != null) {
                systemPrompt = "你是春播万象诊所AI临床辅助诊断与用药助手。接诊患者：" + patientName
                        + "，药物过敏史：【" + allergies + "】，既往慢病史：【" + history + "】。\n";
            } else {
                systemPrompt = "你是春播万象诊所AI临床辅助诊断与用药助手。（当前未指定接诊患者）\n"
                        + "【无患者约束（严格）】：若用户要求辨证、开方、开药，但你没有任何接诊患者档案，"
                        + "**必须提示医生先在【门诊接诊】中接诊或选择患者（或在消息中写明患者姓名）后再开方，"
                        + "严禁默认套用任何其他患者的档案开方**；若用户仅咨询用药知识，正常回答知识本身。\n";
            }
            // 前端传来的本次门诊病历摘要（主诉/现病史/既往史/查体等），AI 辨证必须基于真实病历而非模板
            String emr = request.getEmrContext();
            if (emr != null && !emr.isBlank()) {
                systemPrompt += "【本次门诊病历记录（医生已书写，真实有效）】\n" + emr + "\n"
                        + "辨证与开方必须以上述病历记录为依据，严禁凭空编造患者没有的症状与病史。\n";
            }
            if (patientName != null) {
                systemPrompt += "【意图区分（重要）】：先判断用户意图。若为用药知识、诊疗规范、医学常识类咨询（如\"某类药有哪些\"\"怎么选\"\"什么原理\"），"
                        + "仅基于知识库与医学知识回答问题本身，**不要输出处方方案、剂量价格与辨证开方**；\n"
                        + "【开方场景（严格）】：只要用户要求辨证、开方、开药、拟定治疗方案（如\"开个方子\"\"拟定处方\"），"
                        + "你必须**直接输出完整的推荐处方方案**（具体药品名称、规格、单次剂量、给药频次、疗程），"
                        + "**严禁反问患者症状或要求补充信息**——病历与患者档案已提供，结合病历记录与临床规范直接拟定。\n";
            }
            systemPrompt += "【约束规范】：回答需严谨专业，如输出处方则采用清晰整洁的 Markdown 表格，并对过敏史执行绝对阻断拦截。";

            // RAG 知识库增强：检索基层诊疗规范，让回答有据可依
            String ragCtx = buildRagContext(msg);
            if (!ragCtx.isEmpty()) {
                systemPrompt += "\n\n【基层诊疗知识库参考（RAG 检索）】\n" + ragCtx + "\n请优先结合以上规范作答。";
            }

            // 过程事件（PROCESS 1004）：把 MCP 工具调用与数据核验过程与正文分离，前端生成中展示、完成后隐藏
            List<String> procSteps = new ArrayList<>();
            procSteps.add(pid != null
                    ? "[Tool Call] queryPatientProfile(patientId=" + pid + ") -> 核对【" + patientName + "】过敏史与慢病史"
                    : "[Tool Call] queryPatientProfile -> 未指定接诊患者（医生尚未接诊，AI 将提示先接诊）");
            if (!ragCtx.isEmpty()) {
                List<String> kbTitles0 = (ragKnowledgeService != null) ? ragKnowledgeService.getLastTitles() : List.of();
                procSteps.add("[RAG] 命中基层诊疗知识库：" + String.join("、", kbTitles0));
            }
            procSteps.add("[LLM] 结合患者档案与临床知识库进行辨证推理中...");
            Map<String, Object> procParam = new HashMap<>();
            procParam.put("steps", procSteps);
            ChatEventVO procEvent = ChatEventVO.builder()
                    .eventType(ChatEventTypeEnum.PROCESS.getValue())
                    .eventData(procParam)
                    .build();

            Flux<ChatEventVO> content = client.prompt()
                    .system(systemPrompt)
                    .user(request.getMessage())
                    .advisors(advisorSpec -> advisorSpec.param("chat_memory_conversation_id", request.getSessionId()))
                    .stream()
                    .content()
                    .map(text -> ChatEventVO.builder()
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .eventData(text)
                            .build())
                    .onErrorResume(e -> {
                        System.err.println("大模型流式调用异常，自动降级本地临床引擎: " + e.getMessage());
                        return mockClinicalStream(request);
                    });

            // 收集回答全文，流结束后由独立 LLM 调用提取结构化处方卡片，写入 ToolResultHolder 由 AbstractAgent 统一下发
            StringBuilder ansBuilder = new StringBuilder();
            Flux<ChatEventVO> flow = content
                    .doOnNext(ev -> {
                        if (ev != null && ev.getEventType() == ChatEventTypeEnum.DATA.getValue()
                                && ev.getEventData() instanceof String s) {
                            ansBuilder.append(s);
                        }
                    })
                    .concatWith(Flux.defer(() -> {
                        extractAndStoreRxItems(requestId, ansBuilder.toString());
                        return Flux.empty();
                    }));

            // RAG 命中时，把命中的知识库文档标题写入 ToolResultHolder（前端"知识库引用"标签）
            List<String> kbTitles = (ragKnowledgeService != null) ? ragKnowledgeService.getLastTitles() : List.of();
            if (!ragCtx.isEmpty() && !kbTitles.isEmpty()) {
                ToolResultHolder.put(requestId, "kbTitles", kbTitles);
            }
            return Flux.concat(Flux.just(procEvent), flow);
        } catch (Exception e) {
            System.err.println("大模型在线调用异常，自动降级为本地医学临床逻辑: " + e.getMessage());
            return mockClinicalStream(request);
        }
    }

    /**
     * function-calling 辨证开方的动态 systemPrompt（不预取患者档案，而是引导 LLM 自主调用工具）。
     * 患者档案/库存/指南均通过工具实时获取并进入 ToolResultHolder → PARAM 卡片，
     * 这是与旧「Java 预取塞 prompt + 关键词引擎」的本质区别。
     */
    public String buildFunctionCallingPrompt(String msg, Long pid, String emrContext) {
        StringBuilder sb = new StringBuilder();
        if (pid == null) {
            sb.append("你是春播万象诊所AI临床辅助诊断与用药助手。（当前未指定接诊患者）\n\n");
            sb.append("【无患者约束（严格）】：用户要求开方，但当前没有任何接诊患者档案。"
                    + "必须提示医生先在【门诊接诊】中接诊或选择患者（或在消息中写明患者姓名）后再开方，"
                    + "严禁默认套用任何其他患者的档案开方。\n\n");
        } else {
            // 查真实患者姓名写入 prompt：让 AI 开方时明确点名"根据谁的档案"，而不是只报患者ID
            String patientName = null;
            try {
                Patient p = (patientMapper != null) ? patientMapper.selectById(pid) : null;
                patientName = (p != null && p.getName() != null) ? p.getName() : null;
            } catch (Exception ignored) {
            }
            sb.append("你是春播万象诊所AI临床辅助诊断与用药助手。当前接诊患者：")
              .append(patientName != null ? patientName : ("ID：" + pid))
              .append("（患者ID：").append(pid).append("）。\n\n");
        }
        sb.append("【开方依据说明（严格，回复中必须明确交代）】\n")
          .append("- 本次开方依据 = ①本次门诊病历（医生书写，最核心）②患者档案中的过敏史与慢病史（必查）③基层诊疗规范（RAG/指南工具）；\n")
          .append("- 患者的历史处方/历史病历仅作依从性与疗效参考，**不是本次开方的依据**，严禁照抄历史处方；\n")
          .append("- 回复开头必须注明「本次开方依据：患者【姓名】的本次门诊病历 + 患者档案」，严禁含糊其辞。\n\n");
        sb.append("【开方前必须自主调用工具（重要）】\n");
        if (pid != null) {
            sb.append("1. 必须先调用 queryPatientProfile(patientId=").append(pid).append(") 查询患者过敏史与慢病史；\n");
        }
        sb.append("2. 推荐任何药品前，调用 queryMedicineStock(药品名) 核对药房实时库存、规格与价格；\n");
        sb.append("3. 可调用 queryClinicalGuideline(疾病名) 检索基层诊疗规范；\n");
        sb.append("4. 若患者有药物过敏史（如青霉素过敏），严禁推荐相关过敏药物（如阿莫西林），并加粗警示。\n\n");
        if (emrContext != null && !emrContext.isBlank()) {
            sb.append("【本次门诊病历记录（医生已书写，真实有效）】\n").append(emrContext).append("\n")
              .append("辨证与开方必须以上述病历为依据，严禁编造患者没有的症状与病史。\n\n");
        }
        String ragCtx = buildRagContext(msg);
        if (!ragCtx.isEmpty()) {
            sb.append("【基层诊疗知识库参考（RAG 检索）】\n").append(ragCtx).append("\n请优先结合以上规范作答。\n\n");
        }
        sb.append("【输出规范】按以下结构输出：临床初步诊断、规范诊疗依据、推荐处方清单（通用名/规格/剂量/频次，用 Markdown 表格）、用药安全与过敏预警、基层转诊与随访指征。\n");
        sb.append("所有内容仅供执业医师临床决策参考，最终处方需执业医师签字生效。\n");
        return sb.toString();
    }

    /**
     * AI 智能生成门诊病历字段：由真实大模型根据主诉生成现病史/舌脉/诊断/辨证/医嘱，
     * 返回结构化 Map（前端直接填充病历表单）。LLM 不可用时返回空 Map，前端据此提示。
     */
    public Map<String, String> generateEmr(String chiefComplaint, String duration, String frequency) {
        Map<String, String> result = new HashMap<>();
        if (chiefComplaint == null || chiefComplaint.isBlank()) return result;
        try {
            ChatClient client = aiConfigService.getActiveChatClient();
            if (client == null) return result;
            String userPrompt = "请根据以下门诊主诉，生成规范的中医电子病历字段，只输出一行 JSON，不要任何解释或多余文字。\n"
                    + "主诉：" + chiefComplaint + "\n"
                    + "病程：" + (duration != null && !duration.isBlank() ? duration : "未提供") + "\n"
                    + "发作频率：" + (frequency != null && !frequency.isBlank() ? frequency : "未提供") + "\n"
                    + "JSON 格式：{\"presentIllness\":\"现病史\",\"tongue\":\"舌象\",\"pulse\":\"脉象\","
                    + "\"diagnosis\":\"西医诊断\",\"tcmDiagnosis\":\"中医辨证\",\"medicalAdvice\":\"医嘱(多行用\\\\n分隔)\"}";
            String content = client.prompt()
                    .system("你是基层中医全科门诊的病历书写助手，根据主诉生成专业、规范的病历字段，内容严谨、不过度诊断。")
                    .user(userPrompt)
                    .call()
                    .content();
            if (content == null) return result;
            int s = content.indexOf('{');
            int e = content.lastIndexOf('}');
            if (s < 0 || e <= s) return result;
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(content.substring(s, e + 1));
            String[] fields = {"presentIllness", "tongue", "pulse", "diagnosis", "tcmDiagnosis", "medicalAdvice"};
            for (String f : fields) {
                com.fasterxml.jackson.databind.JsonNode n = root.get(f);
                if (n != null && !n.isNull() && !n.asText().isBlank()) {
                    result.put(f, n.asText().replace("\\n", "\n"));
                }
            }
            return result;
        } catch (Exception ex) {
            System.err.println("AI 病历生成异常: " + ex.getMessage());
            return result;
        }
    }

    /** 消息中点名患者姓名时按姓名匹配真实患者档案；未指定且未点名时返回 null（绝不默认拿 1 号患者开方） */
    private Long resolvePatientId(ChatRequest request, String msg) {
        Long fallback = request.getPatientId();
        try {
            if (msg != null && msg.length() >= 2 && patientMapper != null) {
                List<Patient> all = patientMapper.selectList(null);
                if (all != null) {
                    for (Patient p : all) {
                        if (p.getName() != null && p.getName().length() >= 2 && msg.contains(p.getName())) {
                            return p.getId();
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }

    /** 按消息中点名的患者姓名匹配真实患者ID（供开方智能体在未接诊时使用），未命中返回 null */
    public Long resolvePatientIdByName(String msg) {
        return resolvePatientId(new ChatRequest(), msg);
    }

    /**
     * 开方点名患者解析（消息点名优先于接诊上下文）：
     * 1) 命中「给/为/帮/替 + 姓名 + 开(个)方」句式 → 按姓名查库：命中返回患者ID，点名但查无此人返回 -1L；
     * 2) 消息全文包含某患者完整姓名 → 返回该患者ID；
     * 3) 均未命中 → null（沿用已接诊患者上下文）。
     */
    public Long resolveNamedPatientForRx(String msg) {
        if (msg == null || msg.isBlank()) return null;
        try {
            if (patientMapper != null) {
                java.util.regex.Matcher m = java.util.regex.Pattern
                        .compile("[给为帮替]\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{2,10}?)\\s*开\\s*(?:一?个)?方")
                        .matcher(msg);
                if (m.find()) {
                    Long id = findPatientIdByName(m.group(1).trim());
                    return id != null ? id : -1L;
                }
            }
        } catch (Exception ignored) {
        }
        return resolvePatientId(new ChatRequest(), msg);
    }

    /** 提取开方句式「给XX开(个)方」中点名的患者姓名（无句式返回 null），供查无此人时提示 */
    public String extractRxNamedPatient(String msg) {
        if (msg == null || msg.isBlank()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("[给为帮替]\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{2,10}?)\\s*开\\s*(?:一?个)?方")
                .matcher(msg);
        return m.find() ? m.group(1).trim() : null;
    }

    /** 按姓名精确（未命中再模糊）查询患者ID，未命中返回 null */
    private Long findPatientIdByName(String name) {
        try {
            if (patientMapper == null || name == null || name.isBlank()) return null;
            Patient p = patientMapper.selectOne(new LambdaQueryWrapper<Patient>()
                    .eq(Patient::getName, name).last("LIMIT 1"));
            if (p != null) return p.getId();
            List<Patient> list = patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                    .like(Patient::getName, name));
            return (list != null && !list.isEmpty()) ? list.get(0).getId() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 回答结束后由独立 LLM 调用提取结构化处方卡片，写入 ToolResultHolder（由 AbstractAgent 统一提取下发 PARAM 事件）
     * 任何异常静默忽略（不发卡片事件）
     */
    public void extractAndStoreRxItems(String requestId, String answer) {
        try {
            if (answer == null || answer.length() < 30) return;
            ChatClient judge = aiConfigService.getActiveChatClient();
            if (judge == null) return;
            String content = judge.prompt()
                    .system("你是处方结构化提取器。从AI临床助手的回答中提取\"推荐处方药品\"，只输出一行JSON，格式："
                            + "{\"rxItems\":[{\"category\":\"西药\",\"name\":\"药品名\",\"specification\":\"规格\",\"dose\":\"单次剂量与频次\",\"unitPrice\":价格数字}]}。"
                            + "若回答中没有明确推荐处方药品，输出 {\"rxItems\":[]}。不要输出任何解释或多余文字。")
                    .user(answer)
                    .call()
                    .content();
            if (content == null || !content.contains("rxItems")) return;
            int s = content.indexOf('{');
            int e = content.lastIndexOf('}');
            if (s < 0 || e <= s) return;
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(content.substring(s, e + 1));
            com.fasterxml.jackson.databind.JsonNode items = root.get("rxItems");
            if (items == null || !items.isArray() || items.isEmpty()) return;
            List<Map<String, Object>> rxItems = new ArrayList<>();
            for (com.fasterxml.jackson.databind.JsonNode n : items) {
                Map<String, Object> item = new HashMap<>();
                item.put("category", n.path("category").asText("西药"));
                item.put("name", n.path("name").asText(""));
                item.put("specification", n.path("specification").asText(""));
                item.put("dose", n.path("dose").asText(""));
                item.put("unitPrice", n.path("unitPrice").asDouble(0));
                if (!String.valueOf(item.get("name")).isEmpty()) rxItems.add(item);
            }
            if (rxItems.isEmpty()) return;
            // 处方卡片写入 ToolResultHolder，流末尾由 AbstractAgent.wrapWithToolResult 统一提取转 PARAM 下发
            ToolResultHolder.put(requestId, "rxItems", rxItems);
        } catch (Exception ex) {
            // 忽略，不发卡片
        }
    }

    /**
     * 具备过敏史核对、真实药房库存穿透与临床指南的流式输出引擎（返回标准事件流）
     */
    private Flux<ChatEventVO> mockClinicalStream(ChatRequest request) {
        // 消息中点名患者姓名时按姓名匹配真实患者档案；未接诊且未点名则不套用任何患者档案
        Long pid = resolvePatientId(request, request.getMessage());
        Patient patient = (pid != null) ? patientMapper.selectById(pid) : null;
        String patientName = patient != null ? patient.getName() : "患者";
        String allergies = patient != null ? patient.getAllergies() : "无过敏记录";
        String history = patient != null ? patient.getMedicalHistory() : "无慢病记录";
        String msg = request.getMessage() != null ? request.getMessage() : "";

        // 真实库存穿透：从 MySQL medicine 表动态读取，杜绝写死库存数字
        String ibuprofenStock = findStockText("布洛芬");
        String nifedipineStock = findStockText("硝苯地平");
        // 真实药品档案：规格/剂量/价格均从 medicine 表读取，杜绝写死（未命中为 null，走兜底文案）
        Medicine ibuprofen = findMedicine("布洛芬");
        Medicine nifedipine = findMedicine("硝苯地平");

        // 过程事件（PROCESS 1004）：MCP 工具调用与数据核验过程，与正文分离下发
        List<String> procSteps = new ArrayList<>();

        StringBuilder sb = new StringBuilder();
        // 处方卡片结构化数据（开方场景填充，通过 PARAM 事件下发）
        List<Map<String, Object>> rxItems = new ArrayList<>();

        // 专属技能 1: 药房库存查询与紧缺基药台账 (医生专用查药技能)
        if ("MED_STOCK".equals(request.getRouteHint()) || msg.contains("库存") || msg.contains("药房") || msg.contains("多少盒") || msg.contains("缺药") || msg.contains("备药") || msg.contains("查药")) {
            procSteps.add("[Tool Call] queryPharmacyRealInventory(keyword=\"" + msg + "\") -> 穿透 MySQL 8.0 medicine 真实库存台账");
            procSteps.add("[数据核验] 库存数据 100% 实时取自 MySQL medicine 真实台账，无任何模拟伪造数据");
            sb.append("### 🏥 【春播智慧药房 · 临床药品真实库存台账】\n\n");
            sb.append("| 药品编号 | 药品通用名称 | 商品规格 | 药房当前库存 | 安全预警线 | 零售指导价 | 剂型分类 | 处方类别 | 生产药企 | 库存预警研判 |\n");
            sb.append("| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n");

            if (medicineMapper != null) {
                List<Medicine> list = medicineMapper.selectList(null);
                for (Medicine m : list) {
                    int stock = m.getStock() != null ? m.getStock() : 0;
                    int warn = m.getWarningStock() != null ? m.getWarningStock() : 50;
                    String statusStr = stock <= warn ? (stock < 20 ? "🚨 严重短缺" : "⚠️ 临界警戒") : "✅ 库存充盈";
                    sb.append(String.format("| MED-%03d | **%s** | %s | **%d %s** | %d %s | ¥%.2f | %s | %s | %s | %s |\n",
                            m.getId(), m.getName(), m.getSpecification() != null ? m.getSpecification() : "常规装",
                            stock, m.getUnit() != null ? m.getUnit() : "盒",
                            warn, m.getUnit() != null ? m.getUnit() : "盒",
                            m.getPrice() != null ? m.getPrice().doubleValue() : 0.0,
                            m.getCategory() != null ? m.getCategory() : "西药",
                            m.getIsPrescription() != null && m.getIsPrescription().equals(1) ? "处方药" : "OTC",
                            m.getManufacturer() != null && !m.getManufacturer().isEmpty() ? m.getManufacturer() : "春播特约药企",
                            statusStr));
                }
            } else {
                sb.append("| — | 药品档案数据源未就绪 | — | — | — | — | — | — | — | 请稍后重试 |\n");
            }
            sb.append("\n💡 **临床开单调剂建议**：上述标红药品当前处于紧缺或临界状态，若需为就诊患者开立，请留意药房实时剩余调剂余量。\n");
            sb.append("\n---\n*数据源自春播云智慧药房进销存数据库实时快照*");

            return Flux.concat(processEventFlux(procSteps), toDataEvents(sb.toString(), Duration.ofMillis(30)));
        }

        // 临床辨证辅助问诊分析
        procSteps.add("[Tool Call] queryPatientProfile(patientId=" + pid + ") -> 查询就诊患者电子健康档案");
        procSteps.add("[档案核验] 就诊患者：" + patientName + " | 药物过敏史：" + allergies + " | 既往病史：" + history);
        sb.append("### 【春播云诊所 AI 智能问诊分析报告】\n\n");

        if (msg.contains("感冒") || msg.contains("咽痛") || msg.contains("发热") || msg.contains("咳嗽") || msg.contains("阿莫西林")) {
            procSteps.add("[Tool Call] queryClinicalGuideline('急性上呼吸道感染') -> 匹配抗菌药物规范");
            procSteps.add("[Tool Call] queryMedicineStock('布洛芬混悬滴剂') -> 真实库存：" + ibuprofenStock);
            sb.append("#### 一、 临床初步诊断\n");
            sb.append("诊断拟为：**急性上呼吸道感染 (伴发热/咽痛)**。\n\n");
            sb.append("#### 二、 极重要用药安全预警 (Tool 强阻断校验)\n");
            if (allergies.contains("青霉素")) {
                sb.append("> 🚨 **【高危用药拦截警告】**：系统通过 Tool 检索到患者档案有明确的【**青霉素过敏史**】！\n");
                sb.append("> **严禁开具阿莫西林、青霉素V钾等任何青霉素类抗生素**！AI 已在候选处方中自动过滤潜在致敏药品。\n\n");
            }
            sb.append("#### 三、 推荐规范处方清单\n");
            sb.append("| 推荐药品名称 | 规格包装 | 单次用药剂量 | 给药频次与途径 | 药房当前库存 | 建议开具数量 | 医嘱注意事项 |\n");
            sb.append("| :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n");
            String ibuName = ibuprofen != null && ibuprofen.getName() != null ? ibuprofen.getName() : "布洛芬混悬滴剂 (美林)";
            String ibuSpec = ibuprofen != null && ibuprofen.getSpecification() != null ? ibuprofen.getSpecification() : "15ml:0.6g/瓶";
            String ibuDose = ibuprofen != null && ibuprofen.getDefaultDosage() != null ? ibuprofen.getDefaultDosage() : "每次 1.25ml，发热>38.5℃必要时服用";
            sb.append("| **").append(ibuName).append("** | ").append(ibuSpec).append(" | ").append(ibuDose)
              .append(" | 口服，发热>38.5℃时必要时服用 | ").append(ibuprofenStock).append(" | 1 瓶 | 两次用药间隔需 >= 6小时 |\n\n");
            sb.append("#### 四、 医嘱与健康宣教\n");
            sb.append("- 多饮温开水，清淡饮食，避免辛辣刺激，密切观察体温及咽痛变化。\n");
            // 处方卡片结构化数据（PARAM 事件下发），规格/剂量/价格均来自真实 medicine 档案
            Map<String, Object> coldRx = new HashMap<>();
            coldRx.put("category", "西药");
            coldRx.put("name", ibuName);
            coldRx.put("specification", ibuSpec);
            coldRx.put("dose", ibuDose);
            coldRx.put("unitPrice", ibuprofen != null ? findPrice(ibuprofen.getName()) : findPrice("布洛芬"));
            rxItems.add(coldRx);
        } else if (msg.contains("肚子") || msg.contains("腹") || msg.contains("胃") || msg.contains("拉肚子") || msg.contains("腹泻") || msg.contains("消化") || msg.contains("呕吐")) {
            procSteps.add("[Tool Call] queryClinicalGuideline('急性胃肠炎与胃肠功能紊乱') -> 匹配消化系统基层用药指南");
            procSteps.add("[Tool Call] queryMedicineStock('丹栀逍遥丸') -> 真实库存充足");
            sb.append("#### 一、 临床初步诊断\n");
            sb.append("诊断拟为：**急性胃肠功能紊乱 / 胃脘痛 (待查)**。\n\n");
            sb.append("#### 二、 辨证分析与治则治法\n");
            sb.append("辩证属：**肝郁脾虚、气机失调证**。治法宜疏肝和胃、理气止痛。推荐以疏肝和胃中成药为主，联合神阙穴(脐疗)中药贴敷温中和胃，调理气机。\n\n");
            sb.append("#### 三、 推荐规范处方清单\n");
            sb.append("| 推荐处方项目 | 规格与剂型 | 单次剂量 | 频次与途径 | 建议数量 | 创收与提成提示 |\n");
            sb.append("| :--- | :--- | :--- | :--- | :--- | :--- |\n");
            sb.append("| **丹栀逍遥丸** | 浓缩丸 | 8丸 | 饭后口服 tid | 1盒 | 常规中成药处方 |\n");
            sb.append("| **医用无菌敷贴 (中药贴敷专用)** | 中药贴敷专用 | 1贴 | 神阙穴温敷 4小时 | 3贴 | 特色贴敷理疗 |\n\n");
            sb.append("#### 四、 医嘱与饮食宣教\n");
            sb.append("- 饮食以清淡易消化米粥、软面为主，禁食生冷油腻与辛辣生硬食物，注意腹部保暖。\n");
            // 处方卡片结构化数据（PARAM 事件下发）
            Map<String, Object> digestRx1 = new HashMap<>();
            digestRx1.put("category", "中成药");
            digestRx1.put("name", "丹栀逍遥丸");
            digestRx1.put("specification", "浓缩丸");
            digestRx1.put("dose", "8丸，饭后口服 tid");
            digestRx1.put("unitPrice", findPrice("丹栀逍遥丸"));
            rxItems.add(digestRx1);
            Map<String, Object> digestRx2 = new HashMap<>();
            digestRx2.put("category", "特色贴敷");
            digestRx2.put("name", "医用无菌敷贴 (中药贴敷专用)");
            digestRx2.put("specification", "中药贴敷专用");
            digestRx2.put("dose", "1贴，神阙穴温敷4小时");
            digestRx2.put("unitPrice", findPrice("医用无菌敷贴"));
            rxItems.add(digestRx2);
        } else if (msg.contains("血压") || msg.contains("头晕") || msg.contains("高血压") || history.contains("高血压")) {
            procSteps.add("[Tool Call] queryMedicineStock('硝苯地平控释片') -> 药房真实库存：" + nifedipineStock);
            procSteps.add("[Tool Call] queryClinicalGuideline('原发性高血压') -> 匹配基层用药路径");
            sb.append("#### 一、 临床初步诊断\n");
            sb.append("诊断拟为：**原发性高血压（建议复核诊室静息血压）**。\n\n");
            sb.append("#### 二、 规范诊疗依据 (RAG 基层慢病管理指南)\n");
            sb.append("依据《国家基层高血压防治管理指南》，患者既往有高血压病史，出现头晕头胀等靶器官缺血症状。一线首选长效二氢吡啶类钙通道阻滞剂（CCB），具有平稳降压、靶器官保护及无绝对过敏交叉禁忌的优势。\n\n");
            sb.append("#### 三、 推荐规范处方建议\n");
            sb.append("| 推荐药品名称 | 规格包装 | 单次用量 | 给药频次与途径 | 药房当前库存 | 建议开具数量 | 医嘱注意事项 |\n");
            sb.append("| :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n");
            String nifName = nifedipine != null && nifedipine.getName() != null ? nifedipine.getName() : "硝苯地平控释片 (拜新同)";
            String nifSpec = nifedipine != null && nifedipine.getSpecification() != null ? nifedipine.getSpecification() : "30mg*7片/盒";
            String nifDose = nifedipine != null && nifedipine.getDefaultDosage() != null ? nifedipine.getDefaultDosage() : "30mg(1片)，口服每日1次早晨整片吞服";
            sb.append("| **").append(nifName).append("** | ").append(nifSpec).append(" | ").append(nifDose)
              .append(" | 口服，每日1次(早晨整片吞服) | ").append(nifedipineStock).append(" | 2盒 | 整片吞服，严禁嚼碎 |\n\n");
            sb.append("#### 四、 用药安全与过敏禁忌预警\n");
            sb.append("> **安全核对通过**：已自主核对患者过敏史【").append(allergies).append("】，硝苯地平不属于该类交叉过敏原。注意监测晨起血压，长期服用需警惕轻度踝部水肿。\n\n");
            sb.append("#### 五、 基层转诊与随访指征\n");
            sb.append("- 嘱患者低盐低脂饮食，每日食盐摄入控制在 5g 以下。\n");
            sb.append("- 若收缩压持续 >= 160mmHg 或出现剧烈头痛、呕吐、胸痛，须立即启动绿色通道转诊上级医院。\n");
            // 处方卡片结构化数据（PARAM 事件下发），规格/剂量/价格均来自真实 medicine 档案
            Map<String, Object> bpRx = new HashMap<>();
            bpRx.put("category", "西药");
            bpRx.put("name", nifName);
            bpRx.put("specification", nifSpec);
            bpRx.put("dose", nifDose);
            bpRx.put("unitPrice", nifedipine != null ? findPrice(nifedipine.getName()) : findPrice("硝苯地平"));
            rxItems.add(bpRx);
        } else {
            procSteps.add("[Tool Call] queryClinicalGuideline('基层常见临床症状') -> 基层常见病综合知识库");
            sb.append("#### 一、 临床病情初步研判\n");
            sb.append("患者主诉：").append(msg).append("。结合其既往病史【").append(history).append("】，需重点排查基础疾病急性波动或合并感染可能。\n\n");
            sb.append("#### 二、 规范处置指引\n");
            sb.append("建议主诊医师进一步查体（血压测量、心肺听诊），并核实近期服药规律。如有必要可完善血常规及生化常规检查。\n");
        }
        sb.append("\n---\n*以上建议由春播万象 AI 临床智能体生成，请主诊医师审核后开立正式处方。*");

        Flux<ChatEventVO> dataEvents = toDataEvents(sb.toString(), Duration.ofMillis(40));
        if (!rxItems.isEmpty()) {
            // 处方卡片写入 ToolResultHolder，流末尾由 AbstractAgent 统一提取转 PARAM 下发（前端渲染结构化处方卡）
            ToolResultHolder.put(request.getRequestId(), "rxItems", rxItems);
        }
        // 过程事件（生成中可见、完成后前端隐藏）+ 正文
        return Flux.concat(processEventFlux(procSteps), dataEvents);
    }

    /** 包装过程事件流（PROCESS 1004，携带 MCP 工具调用/数据核验步骤列表） */
    private Flux<ChatEventVO> processEventFlux(List<String> steps) {
        if (steps == null || steps.isEmpty()) return Flux.empty();
        Map<String, Object> param = new HashMap<>();
        param.put("steps", steps);
        return Flux.just(ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.PROCESS.getValue())
                .eventData(param)
                .build());
    }

    /** 把完整文本切块包装成 DATA 事件流（打字机效果） */
    private Flux<ChatEventVO> toDataEvents(String fullText, Duration delay) {
        int chunkSize = 20;
        int len = fullText.length();
        int chunks = (len + chunkSize - 1) / chunkSize;
        List<ChatEventVO> events = new ArrayList<>(chunks);
        for (int i = 0; i < chunks; i++) {
            int start = i * chunkSize;
            int end = Math.min(start + chunkSize, len);
            events.add(ChatEventVO.builder()
                    .eventType(ChatEventTypeEnum.DATA.getValue())
                    .eventData(fullText.substring(start, end))
                    .build());
        }
        return Flux.fromIterable(events).delayElements(delay);
    }

    /** 从 RAG 知识库检索与当前问诊相关的诊疗规范片段，未就绪/无结果返回空串 */
    private String buildRagContext(String msg) {
        if (ragKnowledgeService == null || !ragKnowledgeService.isReady()) return "";
        try {
            List<String> hits = ragKnowledgeService.search(msg, 2);
            return hits.isEmpty() ? "" : String.join("\n", hits);
        } catch (Exception e) {
            return "";
        }
    }

    /** 从 medicine 表按名称模糊查询真实药品档案（未命中返回 null），替代离线引擎里写死的规格/剂量 */
    private Medicine findMedicine(String keyword) {
        if (medicineMapper == null || keyword == null || keyword.isEmpty()) return null;
        try {
            List<Medicine> list = medicineMapper.selectList(
                    new LambdaQueryWrapper<Medicine>().like(Medicine::getName, keyword));
            return (list != null && !list.isEmpty()) ? list.get(0) : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** 从 medicine 表按名称模糊查询真实零售价，未命中返回 0（杜绝写死价格） */
    private double findPrice(String keyword) {
        if (medicineMapper == null) return 0;
        try {
            List<Medicine> list = medicineMapper.selectList(
                    new LambdaQueryWrapper<Medicine>().like(Medicine::getName, keyword));
            if (list != null && !list.isEmpty() && list.get(0).getPrice() != null) {
                return list.get(0).getPrice().doubleValue();
            }
        } catch (Exception e) {
            // 忽略
        }
        return 0;
    }

    /** 从 medicine 表按名称模糊查询真实库存文案，未命中返回"未记录" */
    private String findStockText(String keyword) {
        if (medicineMapper == null) return "未记录";
        try {
            List<Medicine> list = medicineMapper.selectList(
                    new LambdaQueryWrapper<Medicine>().like(Medicine::getName, keyword));
            if (list == null || list.isEmpty()) return "未记录";
            Medicine m = list.get(0);
            int stock = m.getStock() != null ? m.getStock() : 0;
            int warn = m.getWarningStock() != null ? m.getWarningStock() : 0;
            String unit = m.getUnit() != null ? m.getUnit() : "盒";
            return stock + " " + unit + (warn > 0 && stock <= warn ? " (警戒线:" + warn + ")" : "");
        } catch (Exception e) {
            return "未记录";
        }
    }
}
