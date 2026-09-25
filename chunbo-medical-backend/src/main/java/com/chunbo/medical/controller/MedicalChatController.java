package com.chunbo.medical.controller;

import com.chunbo.medical.agent.AgentRouter;
import com.chunbo.medical.agent.MedKnowledgeAgent;
import com.chunbo.medical.agent.MedRouteAgent;
import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.dto.ChatRequest;
import com.chunbo.medical.service.MedicalChatService;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 医疗智能问诊 Controller（多智能体路由：RouteAgent 判意图 → 业务智能体 processStream）
 * 提供 SSE (Server-Sent Events) 流式对话接口
 */
@RestController
@RequestMapping("/api/medical/chat")
public class MedicalChatController {

    @Autowired
    private AgentRouter agentRouter;

    @Autowired
    private MedRouteAgent medRouteAgent;

    @Autowired
    private MedKnowledgeAgent medKnowledgeAgent;

    @Autowired
    private MedicalChatService medicalChatService;

    @Autowired
    private com.chunbo.medical.service.AiModelConfigService aiConfigService;

    @Autowired(required = false)
    private com.chunbo.medical.service.ChatSessionService chatSessionService;

    @Autowired(required = false)
    private org.springframework.ai.chat.memory.ChatMemory chatMemory;

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MedicalChatController.class);

    /**
     * AI 智能门诊预问诊与智能分诊 Agent：
     * 针对患者自述不适症状，提炼规范主诉、现病史、就诊科室与辨证方向，供医生工作台一键导入
     */
    @PostMapping("/triage")
    public java.util.Map<String, Object> triageIntake(@RequestBody java.util.Map<String, Object> body) {
        String chiefComplaint = body.getOrDefault("chiefComplaint", "").toString().trim();
        String patientName = body.getOrDefault("patientName", "").toString().trim();
        String age = body.getOrDefault("age", "").toString().trim();
        String gender = body.getOrDefault("gender", "").toString().trim();
        String allergies = body.getOrDefault("allergies", "").toString().trim();
        String history = body.getOrDefault("history", "").toString().trim();

        java.util.Map<String, Object> res = new java.util.HashMap<>();

        // 若患者未登记任何不适自述或预问诊信息，严禁凭空捏造假病历，如实向工作台反馈
        if (chiefComplaint.isEmpty() || "患者感觉身体不适前来就诊".equals(chiefComplaint)) {
            res.put("success", false);
            res.put("message", "当前患者尚未登记挂号主诉或提交预问诊问卷，请由接诊医师当面问询后录入。");
            return res;
        }

        org.springframework.ai.chat.client.ChatClient client = aiConfigService.getPreConsultChatClient();
        if (client == null) {
            client = aiConfigService.getBareChatClient();
        }
        if (client != null && !aiConfigService.isMockEnabled()) {
            try {
                final org.springframework.ai.chat.client.ChatClient finalClient = client;
                String prompt = String.format("""
                        你是春播基层全科门诊智能预问诊梳理专家。
                        患者基础信息：姓名【%s】、性别【%s】、年龄【%s】、药物过敏史【%s】、既往病史【%s】。
                        患者自述主诉/不适症状：【%s】。

                        请对患者的真实自述进行专业、客观的结构化梳理，为接诊医生提供辅助：
                        1. 规范化临床主诉（提炼核心症状与患者自述的时长，不加戏）；
                        2. 规范化现病史（严格以患者自述事实为准，未提及的伴随症状注明“待接诊医师面询”，绝不臆造未提及的体温、用药或并发症）；
                        3. 推荐就诊科室与初步辨证方向（仅供医生参考）；
                        4. 接诊重点查体提示（医生面诊时应重点关注的部位或体征）。

                        【医疗合规与真实性红线】：
                        - 舌象与脉象必须由医生望闻切诊或专业舌象多模态模型得出，严禁凭空编造！
                        - 字段 "tongue" 与 "pulse" 必须输出空字符串 ""！

                        严格以 JSON 格式输出：
                        ```json
                        {
                          "chiefComplaint": "根据自述提炼的主诉",
                          "presentIllness": "客观提炼的现病史",
                          "recommendedDepartment": "中医全科 / 呼吸内科等",
                          "tcmPattern": "初步拟定辨证方向",
                          "tongue": "",
                          "pulse": "",
                          "examPointers": "重点查体提示"
                        }
                        ```
                        """,
                        patientName.isEmpty() ? "未提供" : patientName,
                        gender.isEmpty() ? "未知" : gender,
                        age.isEmpty() ? "未知" : age,
                        allergies.isEmpty() ? "无" : allergies,
                        history.isEmpty() ? "无" : history,
                        chiefComplaint
                );

                String content = finalClient.prompt().user(prompt).call().content();
                if (content != null && !content.isBlank()) {
                    String clean = content.trim();
                    if (clean.contains("```json")) {
                        clean = clean.substring(clean.indexOf("```json") + 7);
                        if (clean.contains("```")) clean = clean.substring(0, clean.indexOf("```"));
                    } else if (clean.startsWith("```")) {
                        clean = clean.replaceAll("^```[a-zA-Z]*\\s*", "").replaceAll("\\s*```$", "");
                    }
                    com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                    com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(clean.trim());
                    res.put("success", true);
                    res.put("chiefComplaint", node.path("chiefComplaint").asText(chiefComplaint));
                    res.put("presentIllness", node.path("presentIllness").asText("患者自述：" + chiefComplaint + "。具体病程、伴随症状与体征待接诊医生面询。"));
                    res.put("recommendedDepartment", node.path("recommendedDepartment").asText("全科门诊"));
                    res.put("tcmPattern", node.path("tcmPattern").asText(""));
                    res.put("tongue", "");
                    res.put("pulse", "");
                    res.put("examPointers", node.path("examPointers").asText("建议结合患者自述进行规范查体"));
                    return res;
                }
            } catch (Exception ex) {
                log.warn("[分诊梳理] AI处理超时或异常，切入客观兜底: {}", ex.getMessage());
            }
        }

        // 客观兜底：严格基于真实自述，杜绝捏造体温、受凉病因、舌苔或脉象！
        res.put("success", true);
        res.put("chiefComplaint", chiefComplaint);
        res.put("presentIllness", "患者自述：" + chiefComplaint + "。具体发病诱因、伴随症状、病程演变及二便睡眠情况待接诊医师面询详查。");
        res.put("recommendedDepartment", "全科门诊");
        res.put("tcmPattern", "");
        res.put("tongue", "");
        res.put("pulse", "");
        res.put("examPointers", "建议结合患者自述主诉【" + chiefComplaint + "】进行体征测量与针对性查体。");
        return res;
    }

    /**
     * 智能预问诊多轮交互对话：直接交由 MedicalChatService 真实大模型推理（架构与 generateEmr 完全一致）
     * POST /api/medical/chat/pre-consult/dialogue
     */
    @PostMapping("/pre-consult/dialogue")
    public java.util.Map<String, Object> preConsultDialogue(@RequestBody java.util.Map<String, Object> body) {
        long startTime = System.currentTimeMillis();
        String patientName = body.getOrDefault("patientName", "就诊患者").toString();
        String age = body.getOrDefault("age", "30").toString();
        String gender = body.getOrDefault("gender", "男").toString();
        String idCard = body.getOrDefault("idCard", "").toString().trim();
        String sessionId = body.getOrDefault("sessionId", "").toString().trim();
        String userReply = body.getOrDefault("userReply", "").toString().trim();
        java.util.List<java.util.Map<String, String>> history = 
                (java.util.List<java.util.Map<String, String>>) body.getOrDefault("history", new java.util.ArrayList<>());

        log.info("==================== [预问诊Controller-收到请求] ====================");
        log.info(">>> 患者档案: 【{}】(性别: {}, 年龄: {}, 身份证: {})", patientName, gender, age, idCard.isEmpty() ? "未填写" : idCard);
        log.info(">>> 会话ID: {}, 用户输入: \"{}\", 已有历史轮数: {}", sessionId, userReply, history.size());
        if (!history.isEmpty()) {
            log.info(">>> 上轮会话: {}", history.get(history.size() - 1));
        }

        java.util.Map<String, Object> result = medicalChatService.preConsultDialogue(patientName, gender, age, idCard, sessionId, history, userReply);
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("<<< [预问诊Controller-响应完毕] 耗时: {} ms, 成功: {}, AI回复: \"{}\"", 
                 elapsed, result.get("success"), result.get("reply"));
        log.info("<<< [预问诊Controller-快捷选项] {}", result.get("quickReplies"));
        if (result.containsKey("matchedPatient")) {
            log.info("<<< [预问诊Controller-身份证Tool匹配既往档案] {}", result.get("matchedPatient"));
        }
        log.info("========================================================================");
        return result;
    }

    /**
     * 智能预问诊快捷回复获取与刷新（支持随机专科池轮换或 AI 实时现场构思）
     * POST /api/medical/chat/pre-consult/quick-replies
     */
    @PostMapping("/pre-consult/quick-replies")
    public java.util.Map<String, Object> getQuickReplies(@RequestBody java.util.Map<String, Object> body) {
        String patientName = body.getOrDefault("patientName", "就诊患者").toString();
        String age = body.getOrDefault("age", "30").toString();
        String gender = body.getOrDefault("gender", "男").toString();
        String currentSymptom = body.getOrDefault("currentSymptom", "").toString();
        boolean forceAi = Boolean.parseBoolean(String.valueOf(body.getOrDefault("forceAi", "false")));

        log.info(">>> [预问诊Controller-获取快捷选项] 患者: {}, forceAi: {}, 提示: {}", patientName, forceAi, currentSymptom);
        return medicalChatService.generateQuickReplies(patientName, gender, age, currentSymptom, forceAi);
    }

    /**
     * 智能预问诊停止生成控制接口
     * POST /api/medical/chat/pre-consult/stop
     */
    @PostMapping("/pre-consult/stop")
    public java.util.Map<String, Object> stopPreConsult(
            @RequestParam(value = "sessionId", required = false) String sessionId,
            @RequestBody(required = false) java.util.Map<String, Object> body) {
        String sid = (sessionId != null && !sessionId.isBlank()) ? sessionId.trim() : null;
        if (sid == null && body != null && body.containsKey("sessionId")) {
            sid = String.valueOf(body.get("sessionId")).trim();
        }
        if (sid != null && !sid.isEmpty()) {
            medicalChatService.stop(sid);
            log.info("<<< [预问诊Controller] 成功中止会话 sessionId={} 的大模型输出", sid);
        }
        return java.util.Map.of("success", true, "message", "已停止生成");
    }

    /**
     * 查询患者往期预问诊会话列表
     * GET /api/medical/chat/pre-consult/sessions?userId=xxx
     */
    @GetMapping("/pre-consult/sessions")
    public java.util.Map<String, java.util.List<com.chunbo.medical.vo.ChatSessionVO>> getPreConsultSessions(
            @RequestParam("userId") String userId) {
        if (chatSessionService == null) return java.util.Collections.emptyMap();
        java.util.Map<String, java.util.List<com.chunbo.medical.vo.ChatSessionVO>> rawMap = chatSessionService.queryHistory("pre_consult", userId);
        if (rawMap == null || rawMap.isEmpty()) return java.util.Collections.emptyMap();

        // 仅保留真实建档的往期会话，排除旧版残留的空接待标题
        java.util.Map<String, java.util.List<com.chunbo.medical.vo.ChatSessionVO>> filtered = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, java.util.List<com.chunbo.medical.vo.ChatSessionVO>> entry : rawMap.entrySet()) {
            java.util.List<com.chunbo.medical.vo.ChatSessionVO> validList = new java.util.ArrayList<>();
            for (com.chunbo.medical.vo.ChatSessionVO vo : entry.getValue()) {
                if (vo.getSessionId() == null) continue;
                String title = vo.getTitle() != null ? vo.getTitle() : "";
                if (title.contains("何处不适") && title.startsWith("初次问询接待")) continue;
                validList.add(vo);
            }
            if (!validList.isEmpty()) {
                filtered.put(entry.getKey(), validList);
            }
        }
        return filtered;
    }

    /**
     * 查询往期预问诊会话对话详情记录（从 ChatMemory 取回）
     * GET /api/medical/chat/pre-consult/session-messages?sessionId=xxx
     */
    @GetMapping("/pre-consult/session-messages")
    public java.util.List<java.util.Map<String, String>> getPreConsultSessionMessages(
            @RequestParam("sessionId") String sessionId) {
        java.util.List<java.util.Map<String, String>> res = new java.util.ArrayList<>();
        if (chatMemory != null && sessionId != null && !sessionId.isBlank()) {
            try {
                java.util.List<org.springframework.ai.chat.messages.Message> msgs = chatMemory.get(sessionId);
                if (msgs != null) {
                    for (org.springframework.ai.chat.messages.Message m : msgs) {
                        String role = "user";
                        if (m.getMessageType() == org.springframework.ai.chat.messages.MessageType.ASSISTANT) role = "assistant";
                        String text = m.getText() != null ? m.getText() : "";
                        res.add(java.util.Map.of("role", role, "content", text));
                    }
                }
            } catch (Exception ignored) {}
        }
        return res;
    }

    /**
     * 删除往期预问诊会话记录
     * DELETE /api/medical/chat/pre-consult/session?sessionId=xxx&userId=xxx
     */
    @DeleteMapping("/pre-consult/session")
    public java.util.Map<String, Object> deletePreConsultSession(
            @RequestParam("sessionId") String sessionId,
            @RequestParam("userId") String userId) {
        if (chatSessionService != null) {
            chatSessionService.deleteSession("pre_consult", sessionId, userId);
        }
        return java.util.Map.of("success", true, "message", "已删除往期预问诊会话记录");
    }

    /**
     * 智能预问诊结构化提炼：直接交由 MedicalChatService 真实大模型提炼（架构与 generateEmr 完全一致）
     * POST /api/medical/chat/pre-consult/extract
     */
    @PostMapping("/pre-consult/extract")
    public java.util.Map<String, Object> preConsultExtract(@RequestBody java.util.Map<String, Object> body) {
        long startTime = System.currentTimeMillis();
        String patientName = body.getOrDefault("patientName", "就诊患者").toString();
        String age = body.getOrDefault("age", "30").toString();
        String gender = body.getOrDefault("gender", "男").toString();
        java.util.List<java.util.Map<String, String>> history = 
                (java.util.List<java.util.Map<String, String>>) body.getOrDefault("history", new java.util.ArrayList<>());

        log.info("-------------------- [病史提炼Controller-收到请求] --------------------");
        log.info(">>> 提交大模型客观结构化提炼，患者: 【{}】, 对话轮数: {}", patientName, history.size());
        java.util.Map<String, Object> result = medicalChatService.preConsultExtract(patientName, gender, age, history);
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("<<< [病史提炼Controller-提炼完毕] 耗时: {} ms, 主诉: \"{}\", 现病史: \"{}\", 舌象: \"{}\", 脉象: \"{}\"", 
                 elapsed, result.get("chiefComplaint"), result.get("presentIllness"), result.get("tongue"), result.get("pulse"));
        log.info("------------------------------------------------------------------------");
        return result;
    }

    /**
     * AI 智能生成门诊病历字段（真实 LLM 生成，非前端写死话术）
     * POST /api/medical/chat/generate-emr  body: {"chiefComplaint":"主诉","duration":"病程","frequency":"频率"}
     */
    @PostMapping("/generate-emr")
    public java.util.Map<String, Object> generateEmr(@RequestBody java.util.Map<String, String> body) {
        java.util.Map<String, String> emr = medicalChatService.generateEmr(
                body.getOrDefault("chiefComplaint", ""),
                body.getOrDefault("duration", ""),
                body.getOrDefault("frequency", ""));
        java.util.Map<String, Object> res = new java.util.HashMap<>(emr);
        res.put("success", !emr.isEmpty());
        if (emr.isEmpty()) {
            res.put("message", "AI 病历生成服务暂不可用，请稍后重试");
        }
        return res;
    }

    /**
     * SSE 流式问诊接口
     * GET /api/medical/chat/stream?sessionId=1001&patientId=1&message=头晕血压高&doctorId=kzt
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventVO> streamChatGet(@RequestParam("sessionId") String sessionId,
                                      @RequestParam(value = "patientId", required = false) Long patientId,
                                      @RequestParam("message") String message,
                                      @RequestParam(value = "doctorId", required = false) String doctorId,
                                      @RequestParam(value = "emr", required = false) String emrContext,
                                      @RequestParam(value = "attachmentId", required = false) String attachmentId) {
        // 多智能体路由：MedRouteAgent 判意图 → 业务智能体 processStream（携带 patientId + 病历摘要 + 身份上下文）
        java.util.Map<String, Object> context = new java.util.HashMap<>();
        if (patientId != null) context.put(AgentConstant.PATIENT_ID, patientId);
        if (emrContext != null && !emrContext.isEmpty()) context.put(AgentConstant.EMR_CONTEXT, emrContext);
        if (attachmentId != null && !attachmentId.isEmpty()) context.put(AgentConstant.ATTACHMENT_ID, attachmentId);
        // 身份上下文：userId=医生工号，role=DOCTOR（问诊域仅医生/管理员可用），供工具内部 RBAC 校验
        context.put(AgentConstant.USER_ID, doctorId);
        context.put(AgentConstant.ROLE, "DOCTOR");
        return agentRouter.route(medRouteAgent, medKnowledgeAgent, message, sessionId, doctorId, context);
    }

    /**
     * POST 方式流式问诊
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventVO> streamChatPost(@RequestBody ChatRequest request) {
        java.util.Map<String, Object> context = new java.util.HashMap<>();
        if (request.getPatientId() != null) context.put(AgentConstant.PATIENT_ID, request.getPatientId());
        if (request.getEmrContext() != null && !request.getEmrContext().isEmpty()) context.put(AgentConstant.EMR_CONTEXT, request.getEmrContext());
        context.put(AgentConstant.USER_ID, request.getDoctorId());
        context.put(AgentConstant.ROLE, "DOCTOR");
        return agentRouter.route(medRouteAgent, medKnowledgeAgent, request.getMessage(), request.getSessionId(), request.getDoctorId(), context);
    }

    /**
     * 停止生成（后端终止 Flux 输出）
     * POST /api/medical/chat/stop?sessionId=xxx
     */
    @PostMapping("/stop")
    public void stop(@RequestParam String sessionId) {
        medKnowledgeAgent.stop(sessionId);
    }
}
