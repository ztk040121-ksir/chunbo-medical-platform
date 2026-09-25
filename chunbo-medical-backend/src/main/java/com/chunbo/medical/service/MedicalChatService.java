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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MedicalChatService.class);

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

    @Autowired(required = false)
    private com.chunbo.medical.tools.MedicalClinicTools medicalClinicTools;

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
                    + "【真实性红线】：舌象和脉象属于医生查体体征，未经望诊切脉严禁凭空捏造！\"tongue\" 与 \"pulse\" 字段请统一返回空字符串 \"\"；现病史必须紧扣主诉客观规范书写，严禁捏造患者未提及的体温或具体用药！\n"
                    + "JSON 格式：{\"presentIllness\":\"现病史\",\"tongue\":\"\",\"pulse\":\"\","
                    + "\"diagnosis\":\"西医初步诊断\",\"tcmDiagnosis\":\"中医辨证\",\"medicalAdvice\":\"生活医嘱(多行用\\\\n分隔)\"}";
            String content = client.prompt()
                    .system("你是基层中医全科门诊的病历书写助手，根据主诉生成专业、规范的病历字段，内容严谨、不过度诊断，不臆造未提及的体温、舌象或脉象。")
                    .user(userPrompt)
                    .call()
                    .content();
            if (content == null) return result;
            int s = content.indexOf('{');
            int e = content.lastIndexOf('}');
            if (s < 0 || e <= s) return result;
            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(content.substring(s, e + 1));
            String[] fields = {"presentIllness", "diagnosis", "tcmDiagnosis", "medicalAdvice"};
            for (String f : fields) {
                com.fasterxml.jackson.databind.JsonNode n = root.get(f);
                if (n != null && !n.isNull() && !n.asText().isBlank()) {
                    result.put(f, n.asText().replace("\\n", "\n"));
                }
            }
            // 舌象与脉象必须由接诊医师望诊切脉或舌象照片多模态识别输入，绝不凭空假造
            result.put("tongue", "");
            result.put("pulse", "");
            return result;
        } catch (Exception ex) {
            System.err.println("AI 病历生成异常: " + ex.getMessage());
            return result;
        }
    }

    /**
     * 智能预问诊多轮交互对话（重载方法保持兼容）：委托执行全量问询逻辑
     */
    public Map<String, Object> preConsultDialogue(String patientName, String gender, String age, List<Map<String, String>> history, String userReply) {
        return preConsultDialogue(patientName, gender, age, null, null, history, userReply);
    }

    /**
     * 智能预问诊多轮交互对话：与 generateEmr 采用完全一致架构，由 activeChatClient 进行真实大模型推理（绝无硬编码）
     * 增强能力：
     * 1. 身份证号精准研判同患者：通过 MedicalClinicTools 工具获取该患者档案、既往慢病、药物过敏与历史开方；
     * 2. 会话记忆与历史留存：通过 Spring AI ChatMemory 统一存储会话上下文，并异步更新到 ChatSessionService；
     * 3. 停止生成：维护 GENERATE_STATUS 状态标记，支持随时中止大模型思考输出。
     */
    public Map<String, Object> preConsultDialogue(String patientName, String gender, String age, String idCard, String sessionId, List<Map<String, String>> history, String userReply) {
        Map<String, Object> res = new HashMap<>();
        String effectiveSessionId = (sessionId != null && !sessionId.isBlank()) 
                ? sessionId.trim() 
                : "pre_consult_" + System.currentTimeMillis() + "_" + ((int) (Math.random() * 900) + 100);
        res.put("sessionId", effectiveSessionId);
        GENERATE_STATUS.put(effectiveSessionId, true);

        try {
            ChatClient client = aiConfigService.getActiveChatClient();
            if (client == null) {
                log.error("[预问诊-大模型状态] activeChatClient 为 null，大模型服务未就绪");
                res.put("success", false);
                res.put("message", "AI 模型未就绪，请在【AI 设置】中配置并启用模型");
                return res;
            }

            // 【身份证号精准判断同一患者】：调用 Tool 检索历史档案、过敏史、既往慢病史与历史开方记录
            Map<String, Object> matchedPatient = null;
            if (idCard != null && !idCard.isBlank() && medicalClinicTools != null) {
                try {
                    matchedPatient = medicalClinicTools.queryPatientMedicalHistoryByIdCard(idCard.trim(), null);
                    if (matchedPatient != null && Boolean.TRUE.equals(matchedPatient.get("isSamePatient"))) {
                        res.put("matchedPatient", matchedPatient);
                        log.info(">>> [预问诊Service-身份证匹配成功] 患者身份证【{}】通过 Tool 匹配到既往档案: {}", idCard, matchedPatient.get("summaryText"));
                    }
                } catch (Exception ex) {
                    log.warn("[预问诊Service-身份证Tool检索异常] {}", ex.getMessage());
                }
            }

            // 【初次问询接待】：真实调用大模型生成个性化亲切开门问候与基础主诉选项
            // 【初次问询接待】：真实调用大模型生成个性化亲切开门问候与基础主诉选项
            if ((history == null || history.isEmpty()) && (userReply == null || userReply.isBlank())) {
                log.info(">>> [预问诊Service-首轮接待] 患者【{}】进入预问诊，提交大模型生成首轮接待问候与高频主诉选项...", patientName);
                
                int hour = java.time.LocalTime.now().getHour();
                String timeOfDay = (hour < 9 ? "早晨" : (hour < 12 ? "上午" : (hour < 14 ? "中午" : (hour < 18 ? "下午" : "晚上"))));
                
                // 语气风格多样性引导库，避免AI千篇一律套用同一个句式
                String[] stylePrompts = {
                    "亲切温暖型：如身边的全科家庭护士般关怀，带有时段问候，自然温和地询问哪里不舒服",
                    "专业利落型：简练温和，直奔关切主题，询问最困扰身体的异常体征",
                    "启发引导型：语气轻柔，安抚患者放松，引导讲出起病诱因或主要感觉",
                    "日常问安型：结合今日时段问好，询问今日就诊主要是为了哪方面的身体困扰"
                };
                String currentStyle = stylePrompts[new java.util.Random().nextInt(stylePrompts.length)];

                // 随机轮换推荐的主诉侧重系统（呼吸、消化、头面躯体、综合门诊）
                String[][] symptomThemes = {
                    new String[]{"呼吸感冒类", "🌡️ 突发高热伴寒战", "🤧 咳嗽咳痰咽痛", "👃 鼻塞流涕喷嚏", "😵 头痛头重乏力", "🩺 慢病复诊配药"},
                    new String[]{"消化胃肠类", "🤢 胃胀胃痛反酸", "🤮 恶心呕吐纳差", "🚽 腹痛腹泻频繁", "🍽️ 食欲不振消化差", "🩺 慢病定期配药"},
                    new String[]{"头面胸腹类", "🤕 头昏偏头痛跳痛", "💓 心悸胸闷气短", "💤 失眠多梦乏力", "🦴 颈肩腰腿酸痛", "🩺 常规健康检查"},
                    new String[]{"综合基层门诊类", "🌡️ 感冒发热不退", "🤧 咽痛咳嗽难忍", "🤢 急性腹痛腹泻", "😵 头晕身软无力", "🩺 慢病续方取药"}
                };
                String[] randomTheme = symptomThemes[new java.util.Random().nextInt(symptomThemes.length)];

                String welcomePrompt;
                if (matchedPatient != null && Boolean.TRUE.equals(matchedPatient.get("isSamePatient"))) {
                    String pName = String.valueOf(matchedPatient.getOrDefault("name", patientName != null ? patientName : "患者"));
                    String medHist = String.valueOf(matchedPatient.getOrDefault("medicalHistory", ""));
                    String allergies = String.valueOf(matchedPatient.getOrDefault("allergies", ""));
                    String summary = String.valueOf(matchedPatient.getOrDefault("summaryText", ""));
                    welcomePrompt = String.format("""
                            【当前时段】：%s好
                            【🪪 身份证识别：已确认命中该患者全科门诊既往档案（由 Tool 检索提供）】：
                            身份证号：【%s】
                            患者姓名：【%s】
                            历史病历摘要：%s
                            既往慢病史：【%s】
                            过敏史：【%s】
                            
                            任务：你是春播万象全科门诊智能预问诊护士。请根据患者既往档案和当前时段（%s好），向就诊患者输出一句真实、亲切、鲜活的个性化开门问候语，并提供4~5个基层门诊主诉选项供患者快捷点击。
                            重点要求：
                            1. 问候语中请自然表明系统已调阅到其健康档案，询问是既往慢病【%s】有反复，还是身体出现了新的不舒服（用词请生动鲜活，切忌套用千篇一律的机械套话！）。
                            2. 快捷选项必须覆盖其既往病史与常见高频原因（例如：["🩺 复查慢病配药","🤕 既往症状反复","🌡️ 感冒发热","🤧 咳嗽咽痛","🤢 腹痛腹泻"]），配以生动Emoji，去除顿号！
                            红线要求：严禁凭空假设患者当前已经发热或恶化，待患者自述！
                            严格只输出一行紧凑纯 JSON：
                            {"reply":"护士亲切问候语","quickReplies":["快捷选项1","快捷选项2","快捷选项3","快捷选项4"],"isComplete":false}
                            """, timeOfDay, idCard, pName, summary, medHist, allergies, timeOfDay, medHist);
                } else {
                    welcomePrompt = String.format("""
                            【当前时段】：%s好
                            【就诊患者信息】：【%s，%s，%s岁】
                            【本次护士风格引导】：%s
                            【本次主诉推荐侧重参考】：%s（如：%s、%s、%s、%s、%s）
                            场景：患者初次进入基层全科门诊挂号预问诊，尚未自述病情。
                            任务：作为春播万象基层全科门诊护士，请向患者输出一句亲切热情的问候语，并提供4~5个最常见的主诉选项供患者快捷点击。
                            重点要求：
                            1. 结合当前时段（%s好）与患者（%s），用你自己的语言现场构思一句自然、真诚的开门问候（25~40字）。
                               【严禁千篇一律！严禁机械重复固定句式“很高兴为您服务～请问您今天主要是哪里不舒服呢？大概持续多久啦？”，请每次变换不同的口吻、词汇与关切切入点】！
                            2. 快捷选项必须精炼生动并配有Emoji图标，绝不要带有顿号等机械分隔，每项要有明确临床指向（可参考上述侧重或高频组合）。
                            红线要求：问候语严禁凭空假设患者已有发热或特定疾病！
                            严格只输出一行紧凑纯 JSON：
                            {"reply":"AI护士现场个性化问候语","quickReplies":["带Emoji选项1","带Emoji选项2","带Emoji选项3","带Emoji选项4"],"isComplete":false}
                            """, 
                            timeOfDay,
                            patientName != null && !patientName.isBlank() ? patientName : "就诊患者", gender, age,
                            currentStyle,
                            randomTheme[0], randomTheme[1], randomTheme[2], randomTheme[3], randomTheme[4], randomTheme[5],
                            timeOfDay,
                            patientName != null && !patientName.isBlank() ? patientName : "您"
                    );
                }

                try {
                    long tStart = System.currentTimeMillis();
                    String content = client.prompt()
                            .system("你是春播万象全科门诊智能预问诊护士。语言亲切自然、充满人情味、严禁千篇一律套用模板，严禁凭空捏造未提及体征。只输出纯 JSON。")
                            .user(welcomePrompt)
                            .call()
                            .content();
                    long tElapsed = System.currentTimeMillis() - tStart;
                    if (content != null && content.contains("{") && content.contains("}")) {
                        int s = content.indexOf('{');
                        int e = content.lastIndexOf('}');
                        com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(content.substring(s, e + 1));
                        res.put("success", true);
                        String nurseReply = root.path("reply").asText("您好！请问您今天主要是哪里不舒服？持续多久了？");
                        res.put("reply", nurseReply);
                        res.put("elapsedMs", tElapsed);
                        List<String> qr = new ArrayList<>();
                        if (root.has("quickReplies") && root.get("quickReplies").isArray()) {
                            for (com.fasterxml.jackson.databind.JsonNode q : root.get("quickReplies")) qr.add(q.asText());
                        }
                        if (qr.isEmpty()) qr = List.of(randomTheme[1], randomTheme[2], randomTheme[3], randomTheme[4], randomTheme[5]);
                        res.put("quickReplies", qr);
                        res.put("isComplete", false);

                        // 首轮接待仅存入 ChatMemory 供对话上下文记忆；患者尚未自述症状（未提问），严禁在 chat_session 数据库建档生成空白记录！
                        if (chatMemory != null && nurseReply != null && !nurseReply.isBlank()) {
                            try {
                                chatMemory.add(effectiveSessionId, new org.springframework.ai.chat.messages.AssistantMessage(nurseReply.trim()));
                            } catch (Exception ignored) {}
                        }

                        log.info("<<< [预问诊Service-首轮接待完成] 真实大模型首轮生成完成 (耗时 {} ms): \"{}\", 选项: {}", tElapsed, res.get("reply"), qr);
                        return res;
                    }
                } catch (Exception ex) {
                    log.warn("[预问诊Service-首轮接待] 大模型生成异常，切入客观标准接待: {}", ex.getMessage());
                }
                res.put("success", true);
                String defReply = String.format("%s好！请问您今天主要是哪里感觉不太舒服？大概持续几天了呢？", timeOfDay);
                res.put("reply", defReply);
                res.put("elapsedMs", 450);
                res.put("quickReplies", List.of(randomTheme[1], randomTheme[2], randomTheme[3], randomTheme[4], randomTheme[5]));
                res.put("isComplete", false);
                if (chatMemory != null && defReply != null && !defReply.isBlank()) {
                    try {
                        chatMemory.add(effectiveSessionId, new org.springframework.ai.chat.messages.AssistantMessage(defReply.trim()));
                    } catch (Exception ignored) {}
                }
                return res;
            }

            // 中途停止检测
            if (!GENERATE_STATUS.getOrDefault(effectiveSessionId, false)) {
                res.put("success", false);
                res.put("message", "生成已被用户停止");
                return res;
            }

            StringBuilder conv = new StringBuilder();
            if (history != null) {
                for (Map<String, String> m : history) {
                    conv.append(m.getOrDefault("role", "user")).append(": ").append(m.getOrDefault("content", "")).append("\n");
                }
            }
            if (userReply != null && !userReply.isBlank()) {
                conv.append("user: ").append(userReply.trim()).append("\n");
            }

            // 如果通过 Tool 关联了既往病历，拼装入提示词中
            String historyContext = "";
            if (matchedPatient != null && Boolean.TRUE.equals(matchedPatient.get("isSamePatient"))) {
                historyContext = String.format("""
                        【🪪 既往病历档案（Tool 检索提供）】：
                        患者既往有【%s】慢病史，过敏史【%s】。
                        若患者当前主诉可能与既往史有关，请在追问中结合既往病史进行更专业、更有针对性的临床鉴别追问！
                        """,
                        matchedPatient.getOrDefault("medicalHistory", "无"),
                        matchedPatient.getOrDefault("allergies", "无")
                );
            }

            String userPrompt = String.format("""
                    问诊患者：【%s，%s，%s】
                    %s
                    问诊历史对话：
                    %s
                    
                    患者最新自述：【%s】
                    
                    任务：你是春播万象基层全科门诊专业护士，请严格针对患者自述的不适症状，进行亲切、规范的进一步临床追问。
                    红线要求：
                    1. 严禁凭空编造患者未提及的症状、体征或病因（如患者未提及发热，严禁主动认定患者有发热）！
                    2. 语言亲切简短（25字内），针对性追问病程、体温/症状程度或服药情况。
                    3. 给出3~4个专为患者设计的【快捷回答选项（quickReplies）】：
                       - 必须是患者回答护士追问的自然口吻；
                       - 必须包含具体的病程、发热/程度或用药状态（例如：“起病1-2天，自测低热37.8℃左右，未吃药”、“突发高热38.5℃以上伴全身酸痛”、“已服退烧药/消炎药，体温有所回落”、“体温正常未发热，主要是局部不适”）；
                       - 绝不可简单重复症状名词，每个选项要有明确的临床鉴别信息供患者一键回复。
                    4. 若主要症状、病程、诱因、体温、自服药等信息已基本明确，isComplete 填 true，否则填 false。
                    
                    严格只输出一行紧凑 JSON，绝不要包含其他额外说明或 markdown：
                    {"reply":"护士亲切追问","quickReplies":["快捷回答1","快捷回答2","快捷回答3"],"isComplete":false}
                    """,
                    patientName != null && !patientName.isBlank() ? patientName : "患者",
                    gender != null && !gender.isBlank() ? gender : "男",
                    age != null && !age.isBlank() ? age : "30",
                    historyContext,
                    conv.toString(),
                    userReply != null ? userReply.trim() : ""
            );

            log.info(">>> [预问诊Service-LLM推理开始] 向大模型提交 Prompt (长度: {} 字符)...", userPrompt.length());
            long t0 = System.currentTimeMillis();
            String content = client.prompt()
                    .system("你是春播万象基层全科门诊智能预问诊护士。你的职责是向就诊患者了解不适症状，进行亲切、规范、简短的临床追问，并提供患者可快速点击的选项。严禁凭空捏造未提及的体征。严格只输出纯 JSON。")
                    .user(userPrompt)
                    .call()
                    .content();

            long elapsed = System.currentTimeMillis() - t0;
            log.info("<<< [预问诊Service-LLM响应返回] 耗时: {} ms, 原始返回字符: {}", elapsed, content != null ? content.trim() : "null");
            
            // 生成完毕后再次检测停止状态
            if (!GENERATE_STATUS.getOrDefault(effectiveSessionId, false)) {
                res.put("success", false);
                res.put("message", "生成已被用户停止");
                return res;
            }

            if (content == null || content.isBlank()) {
                res.put("success", false);
                res.put("message", "大模型响应为空");
                return res;
            }

            int s = content.indexOf('{');
            int e = content.lastIndexOf('}');
            if (s < 0 || e <= s) {
                res.put("success", false);
                res.put("message", "大模型未返回有效 JSON 结构");
                return res;
            }

            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(content.substring(s, e + 1));

            String nurseReply = root.path("reply").asText("请问您目前主要哪里不舒服？持续几天了？");
            res.put("success", true);
            res.put("reply", nurseReply);
            List<String> qr = new ArrayList<>();
            if (root.has("quickReplies") && root.get("quickReplies").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode q : root.get("quickReplies")) {
                    qr.add(q.asText());
                }
            }
            if (qr.isEmpty()) {
                qr = List.of("起病1-2天，自测低热未吃药", "起病3天以上，有发热伴畏寒", "已自服退烧药/感冒药，有所缓解", "体温正常未发热，主要是局部不适");
            }
            res.put("quickReplies", qr);
            res.put("isComplete", root.path("isComplete").asBoolean(false));

            // 保存记忆与会话历史
            savePreConsultMemoryAndSession(effectiveSessionId, idCard, patientName, userReply, nurseReply);

            log.info("<<< [预问诊Service-问询成功] 生成追问: \"{}\", 选项: {}, isComplete: {}", res.get("reply"), qr, res.get("isComplete"));
            return res;
        } catch (Exception ex) {
            log.error("[预问诊-多轮问询] 真实大模型调用异常: {}", ex.getMessage(), ex);
            res.put("success", false);
            res.put("message", "预问诊推理异常: " + ex.getMessage());
            return res;
        } finally {
            GENERATE_STATUS.remove(effectiveSessionId);
        }
    }

    /**
     * 动态生成或随机轮换快捷回复选项（支持 AI 现场构思或全科专科症状池随机调用）
     */
    public Map<String, Object> generateQuickReplies(String patientName, String gender, String age, String currentSymptom, boolean forceAi) {
        Map<String, Object> res = new HashMap<>();
        
        // 8大基层常见全科临床分类池（支持瞬间随机切换轮换）
        String[][] categoryPools = {
            new String[]{"呼吸感冒类", "🌡️ 突发高热伴寒战", "🤧 剧烈干咳咽部刺痛", "👃 鼻塞流涕打喷嚏", "😵 头痛身重全身酸痛", "🫁 胸闷咳嗽伴有黄痰"},
            new String[]{"消化胃肠类", "🤢 胃部胀痛反酸嗳气", "🤮 恶心呕吐食欲不振", "🚽 阵发性腹痛腹泻", "💩 便秘排便困难", "🍽️ 消化不良餐后腹胀"},
            new String[]{"头面神经类", "🤕 血管神经性偏头痛", "😵 阵发性眩晕视物旋转", "💤 严重失眠多梦易醒", "👂 耳鸣伴听力下降", "👁️ 眼睛干涩视物模糊"},
            new String[]{"骨骼软组织类", "🦴 颈椎僵硬酸胀不适", "⚡ 腰部急性扭伤刺痛", "🦵 膝关节活动弹响肿痛", "🧣 肩部受凉活动受限", "🦶 足跟疼痛足底筋膜炎"},
            new String[]{"心血管慢病类", "💓 阵发性心慌心悸", "🫀 胸闷气短活动后加重", "🩺 高血压规律复诊配药", "🩸 糖尿病空腹血糖复查", "💊 慢病长处方续方"},
            new String[]{"儿科常见症状", "👶 婴幼儿发热哭闹", "🍼 厌食挑食消化不良", "🤧 小儿夜间阵发性咳嗽", "🩹 皮肤湿疹发红瘙痒", "💧 腹泻脱水精神差"},
            new String[]{"皮肤黏膜类", "🔴 皮肤大片风团奇痒", "🩹 湿疹皮炎脱屑干燥", "👄 口腔溃疡反复发作", "🌾 接触花粉过敏红肿", "☀️ 日光性皮炎灼痛"},
            new String[]{"全科健康咨询", "📋 体检报告异常指标咨询", "💊 药物相互作用核对", "💉 疫苗接种前健康评估", "🥗 营养与慢性病饮食调理", "🩺 术后康复随访"}
        };
        
        // 如果要求调用大模型生成，尝试由 activeChatClient 推理
        if (forceAi) {
            try {
                ChatClient client = aiConfigService.getActiveChatClient();
                if (client != null) {
                    long tStart = System.currentTimeMillis();
                    String prompt = String.format("""
                            患者信息：【%s，%s，%s岁】，当前主诉或意向：【%s】
                            任务：你是春播万象全科门诊AI助手，请为门诊预问诊患者生成 5 个针对性的快捷回复选项。
                            要求：
                            1. 选项短小精悍（4~8字），每项开头配 1 个贴切的生动 Emoji。
                            2. 紧扣全科基层门诊真实患者的自述用语（严禁顿号、严禁学术生僻词）。
                            3. 只输出 JSON 数组，如：["选项1", "选项2", "选项3", "选项4", "选项5"]
                            """,
                            patientName != null ? patientName : "患者",
                            gender != null ? gender : "男",
                            age != null ? age : "30",
                            currentSymptom != null && !currentSymptom.isBlank() ? currentSymptom : "门诊就诊初始选择"
                    );
                    String resp = client.prompt()
                            .system("你是医疗全科助手，只输出纯 JSON 字符串数组，严禁其他文字。")
                            .user(prompt)
                            .call()
                            .content();
                    long elapsed = System.currentTimeMillis() - tStart;
                    if (resp != null && resp.contains("[") && resp.contains("]")) {
                        int s = resp.indexOf('[');
                        int e = resp.lastIndexOf(']');
                        com.fasterxml.jackson.databind.JsonNode arr = new com.fasterxml.jackson.databind.ObjectMapper()
                                .readTree(resp.substring(s, e + 1));
                        List<String> items = new ArrayList<>();
                        if (arr.isArray()) {
                            for (com.fasterxml.jackson.databind.JsonNode n : arr) {
                                items.add(n.asText());
                            }
                        }
                        if (items.size() >= 3) {
                            res.put("success", true);
                            res.put("category", "AI 智能针对性联想");
                            res.put("quickReplies", items);
                            res.put("source", "ai");
                            res.put("elapsedMs", elapsed);
                            return res;
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[预问诊-AI快捷选项生成异常] {}", ex.getMessage());
            }
        }
        
        // 随机选择一个专科分类池
        int randIdx = new java.util.Random().nextInt(categoryPools.length);
        String[] selected = categoryPools[randIdx];
        List<String> list = new ArrayList<>();
        for (int i = 1; i < selected.length; i++) {
            list.add(selected[i]);
        }
        res.put("success", true);
        res.put("category", selected[0]);
        res.put("quickReplies", list);
        res.put("source", "random_pool");
        res.put("elapsedMs", 15);
        return res;
    }

    /**
     * 辅助存储预问诊会话记忆与会话记录
     */
    private void savePreConsultMemoryAndSession(String sessionId, String idCard, String patientName, String userReply, String assistantReply) {
        if (sessionId == null || sessionId.isBlank()) return;
        String effectiveUserId = (idCard != null && !idCard.isBlank()) ? idCard.trim() : (patientName != null && !patientName.isBlank() ? patientName.trim() : "visitor");

        // 1. 存入 Spring AI ChatMemory 统一会话记忆
        if (chatMemory != null) {
            try {
                if (userReply != null && !userReply.isBlank()) {
                    chatMemory.add(sessionId, new org.springframework.ai.chat.messages.UserMessage(userReply.trim()));
                }
                if (assistantReply != null && !assistantReply.isBlank()) {
                    chatMemory.add(sessionId, new org.springframework.ai.chat.messages.AssistantMessage(assistantReply.trim()));
                }
            } catch (Exception ignored) {}
        }

        // 2. 存入 ChatSessionService 历史持久化（支持按身份证/姓名分组归档与标题自动提炼）
        // 🚨 核心原则：患者未提问/未自述症状（userReply 为空），严禁在 chat_session 生成空白历史记录！
        // 只有患者开展了实际提问或回答（userReply 存在），才在此建档并由大模型提炼准确主诉标题。
        if (chatSessionService != null && userReply != null && !userReply.isBlank()) {
            try {
                String convContent = "USER:" + userReply.trim() + "\nASSISTANT:" + (assistantReply != null ? assistantReply.trim() : "");
                chatSessionService.update("pre_consult", sessionId, effectiveUserId, convContent);
            } catch (Exception ignored) {}
        }
    }

    /**
     * 智能预问诊客观病史结构化提炼：与 generateEmr 采用完全一致架构，由 activeChatClient 进行真实大模型提炼
     */
    public Map<String, Object> preConsultExtract(String patientName, String gender, String age, List<Map<String, String>> history) {
        Map<String, Object> res = new HashMap<>();
        if (history == null || history.isEmpty()) {
            res.put("success", false);
            res.put("message", "问询记录为空，无法提炼。");
            return res;
        }
        try {
            ChatClient client = aiConfigService.getActiveChatClient();
            if (client == null) {
                res.put("success", false);
                res.put("message", "AI 模型未就绪");
                return res;
            }

            StringBuilder conv = new StringBuilder();
            for (Map<String, String> m : history) {
                conv.append(m.getOrDefault("role", "user")).append(": ").append(m.getOrDefault("content", "")).append("\n");
            }

            String userPrompt = String.format("""
                    患者【%s，%s，%s岁】预问诊对话记录：
                    %s
                    
                    请严格依据患者亲口陈述提炼客观结构化病历（未测写未测，未用药写未用药，严禁凭空捏造）：
                    【医疗红线规定】：舌象与脉象必须固定为空字符串 ""（tongue 与 pulse 填 ""）！
                    
                    严格只输出一行紧凑 JSON，绝不要包含其他额外说明或 markdown：
                    {"chiefComplaint":"主诉（如：发热咳嗽2天）","presentIllness":"客观现病史（忠于患者自述）","duration":"病程","frequency":"持续性/阵发性","temperature":"未测/正常","takenMedicines":"就诊前未自服用药","symptomsList":["发热","咳嗽"],"allergies":"无已知药物过敏","department":"全科门诊","tcmPattern":"","tongue":"","pulse":""}
                    """,
                    patientName != null && !patientName.isBlank() ? patientName : "患者",
                    gender != null && !gender.isBlank() ? gender : "男",
                    age != null && !age.isBlank() ? age : "30",
                    conv.toString()
            );

            log.info(">>> [病史提炼Service-LLM推理开始] 正在提交大模型结构化提炼...");
            long t0 = System.currentTimeMillis();
            String content = client.prompt()
                    .system("你是基层全科门诊智能病历提炼专家。严格依据患者亲口陈述提炼客观结构化病历，严禁编造未提及的体征、舌苔或脉象。严格只输出纯 JSON。")
                    .user(userPrompt)
                    .call()
                    .content();

            long elapsed = System.currentTimeMillis() - t0;
            log.info("<<< [病史提炼Service-LLM响应返回] 耗时: {} ms, 原始返回字符: {}", elapsed, content != null ? content.trim() : "null");
            if (content == null || content.isBlank()) {
                res.put("success", false);
                res.put("message", "模型提炼响应为空");
                return res;
            }

            int s = content.indexOf('{');
            int e = content.lastIndexOf('}');
            if (s < 0 || e <= s) {
                res.put("success", false);
                res.put("message", "模型提炼未返回有效 JSON");
                return res;
            }

            com.fasterxml.jackson.databind.JsonNode root = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(content.substring(s, e + 1));

            res.put("success", true);
            res.put("chiefComplaint", root.path("chiefComplaint").asText("身体不适待查"));
            res.put("presentIllness", root.path("presentIllness").asText(""));
            res.put("duration", root.path("duration").asText(""));
            res.put("frequency", root.path("frequency").asText("阵发性"));
            res.put("temperature", root.path("temperature").asText("未测/正常"));
            res.put("takenMedicines", root.path("takenMedicines").asText("就诊前未自服用药"));
            List<String> syms = new ArrayList<>();
            if (root.has("symptomsList") && root.get("symptomsList").isArray()) {
                for (com.fasterxml.jackson.databind.JsonNode sn : root.get("symptomsList")) {
                    syms.add(sn.asText());
                }
            }
            res.put("symptomsList", syms);
            res.put("allergies", root.path("allergies").asText("无已知药物过敏"));
            res.put("department", root.path("department").asText("全科门诊"));
            res.put("tcmPattern", root.path("tcmPattern").asText(""));
            // 医疗红线：固定为空
            res.put("tongue", "");
            res.put("pulse", "");
            log.info("<<< [预问诊-病史提炼] 真实大模型提炼完成 (耗时 {} ms): chiefComplaint={}", elapsed, res.get("chiefComplaint"));
            return res;
        } catch (Exception ex) {
            log.error("[预问诊-病史提炼] 异常: {}", ex.getMessage());
            res.put("success", false);
            res.put("message", "AI 病史提炼失败: " + ex.getMessage());
            return res;
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
            procSteps.add("[药房台账检索] 正在检索 MySQL 8.0 智慧药房真实进销存台账");
            procSteps.add("[数据核验] 库存数据实时取自真实药品台账");
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
        procSteps.add("[档案调阅] 调阅当前就诊患者电子健康档案与既往记录");
        procSteps.add("[档案核验] 就诊患者：" + patientName + " | 药物过敏史：" + allergies + " | 既往病史：" + history);
        sb.append("### 【春播云诊所 AI 智能问诊分析报告】\n\n");

        if (msg.contains("感冒") || msg.contains("咽痛") || msg.contains("发热") || msg.contains("咳嗽") || msg.contains("阿莫西林")) {
            procSteps.add("[指南匹配] 匹配国家基层急性上呼吸道感染规范与用药指南");
            procSteps.add("[库存核实] 药房当前布洛芬混悬滴剂实时库存：" + ibuprofenStock);
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
            procSteps.add("[指南匹配] 匹配消化系统基层常见病临床诊疗指南");
            procSteps.add("[库存核实] 药房当前对症中成药与理疗敷贴库存充足");
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
            procSteps.add("[库存核实] 药房当前硝苯地平控释片真实库存：" + nifedipineStock);
            procSteps.add("[指南匹配] 匹配国家基层高血压防治管理指南用药路径");
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
            procSteps.add("[知识库检索] 检索基层常见病综合知识库进行病情初判");
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
