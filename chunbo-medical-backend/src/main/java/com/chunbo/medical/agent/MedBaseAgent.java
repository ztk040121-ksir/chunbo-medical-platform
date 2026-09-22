package com.chunbo.medical.agent;

import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.dto.ChatRequest;
import com.chunbo.medical.enums.ChatEventTypeEnum;
import com.chunbo.medical.service.FileUploadService;
import com.chunbo.medical.service.MedicalChatService;
import com.chunbo.medical.service.RagKnowledgeService;
import com.chunbo.medical.tools.MedicalClinicTools;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 问诊业务智能体基类（参照《SpringAI》笔记 tjxt 多智能体标准实现）
 * 每个问诊意图（辨证开方/药房库存/用药知识）一个独立 Agent 类，
 * 路由判出的意图由 AgentRouter 直接命中对应子类，真正分流到不同问诊技能。
 *
 * 辨证开方（MED_DIAGNOSE）已升级为真正的 function-calling：LLM 自主规划调用
 * 患者档案/库存/指南工具，工具结果进 ToolResultHolder → PARAM 卡片；
 * 药房库存 / 用药知识仍保留确定性引擎（数据精度要求高，不宜交给 LLM 拼表）。
 *
 * 患者约束：未接诊且消息未点名患者时，开方智能体会提示先接诊，
 * 绝不默认套用库里 1 号患者的档案开方（杜绝"给 kzt 开方却按张建国开"的错位）。
 */
public abstract class MedBaseAgent extends AbstractAgent {

    @Autowired
    protected MedicalChatService medicalChatService;

    @Autowired
    protected MedicalClinicTools clinicTools;

    @Autowired(required = false)
    protected FileUploadService fileUploadService;

    @Autowired(required = false)
    protected RagKnowledgeService ragKnowledgeService;

    @Override
    public String bizType() {
        return "medical";
    }

    /** 子类返回本智能体对应的固定路由意图（如 MED_DIAGNOSE / MED_STOCK / MED_KNOWLEDGE） */
    protected abstract String skillHint();

    @Override
    protected Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId) {
        return buildContentFlux(question, sessionId, userId, skillHint(), null);
    }

    @Override
    protected Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId,
                                                 String routeHint, java.util.Map<String, Object> context) {
        String emr = context != null ? String.valueOf(context.getOrDefault(AgentConstant.EMR_CONTEXT, "")) : "";

        // 图片附件 → 视觉诊断闭环：识别发病部位图片 → 检索 RAG 知识库比对 → 给出诊断
        // → （已接诊患者时）核对过敏史 + 查真实库存开方 → 生成 rxItems 开方卡片
        String attachmentId = context != null ? String.valueOf(context.getOrDefault(AgentConstant.ATTACHMENT_ID, "")) : "";
        Media imageMedia = null;
        if (attachmentId != null && !attachmentId.isBlank() && fileUploadService != null) {
            imageMedia = fileUploadService.toImageMedia(attachmentId);
        }
        if (imageMedia != null) {
            return visualDiagnosisFlux(question, sessionId, userId, imageMedia, emr, context);
        }

        // 辨证开方路径在分支内部解析患者（消息点名优先于接诊上下文）
        Long pid = resolvePatientId(context);

        // 辨证开方：走真正的 function-calling，LLM 自主调工具
        if ("MED_DIAGNOSE".equals(skillHint())) {
            // 消息点名患者（"给XX开方"句式或包含患者姓名）时，点名优先于当前接诊上下文
            Long namedPid = medicalChatService.resolveNamedPatientForRx(question);
            if (namedPid != null && namedPid == -1L) {
                // 点名了但查无此人：明确提示，绝不静默套用其他患者档案
                String askedName = medicalChatService.extractRxNamedPatient(question);
                return Flux.just(ChatEventVO.builder()
                        .eventType(ChatEventTypeEnum.DATA.getValue())
                        .eventData("⛔ 系统中未找到患者【" + (askedName != null ? askedName : "") + "】的档案，无法为其开方。"
                                + "请确认姓名是否正确，或先在【门诊接诊】中为其建档接诊。")
                        .build());
            }
            if (namedPid != null) {
                pid = namedPid;
            }
            if (pid == null) {
                return Flux.just(ChatEventVO.builder()
                        .eventType(ChatEventTypeEnum.DATA.getValue())
                        .eventData("⛔ 当前没有接诊患者，AI 无法辨证开方。请先在【门诊接诊】中接诊或选择患者后再试；"
                                + "也可以直接在消息中写明患者姓名（如「给张三开个方」），我会调取 TA 的真实档案。")
                        .build());
            }
            String role = context != null && context.get(AgentConstant.ROLE) != null
                    ? String.valueOf(context.get(AgentConstant.ROLE)) : "DOCTOR";
            String sys = medicalChatService.buildFunctionCallingPrompt(question, pid, emr);
            // 流末尾由独立 LLM 提取结构化处方卡片写入 ToolResultHolder（与用药知识路径一致），
            // 由 AbstractAgent.wrapWithToolResult 统一下发 PARAM，前端才能渲染"开方卡片"
            String requestId = AbstractAgent.currentRequestId();
            StringBuffer ans = new StringBuffer();
            return functionCallingFlux(question, sessionId, userId, role, sys, clinicTools)
                    .doOnNext(ev -> {
                        if (ev != null && ev.getEventType() == ChatEventTypeEnum.DATA.getValue()
                                && ev.getEventData() instanceof String s) {
                            ans.append(s);
                        }
                    })
                    .concatWith(Flux.defer(() -> {
                        medicalChatService.extractAndStoreRxItems(requestId, ans.toString());
                        return Flux.empty();
                    }));
        }

        // 药房库存 / 用药知识：保留确定性引擎（真实 SQL 穿透，避免 LLM 编造库存数字）
        ChatRequest req = new ChatRequest();
        req.setSessionId(sessionId);
        req.setPatientId(pid);
        req.setMessage(question);
        req.setDoctorId(userId);
        req.setEmrContext(emr == null || emr.isBlank() ? null : emr);
        req.setRouteHint(skillHint());
        // 透传本次请求 id，供服务内部把结构化卡片写入 ToolResultHolder，由 AbstractAgent 统一提取下发
        req.setRequestId(AbstractAgent.currentRequestId());
        return medicalChatService.doStreamChat(req);
    }

    /**
     * 视觉诊断闭环：患者发病部位图片 → 多模态识别病灶特征 → LLM 自主调用 searchKnowledge（RAG）
     * 与 queryClinicalGuideline 比对临床规范 → 给出最可能诊断 →（已接诊患者时）核对过敏史 +
     * 核查真实药房库存 → 按规范开方 → 流末提取 rxItems 结构化开方卡片（PARAM 下发）。
     */
    private Flux<ChatEventVO> visualDiagnosisFlux(String question, String sessionId, String userId,
                                                  Media imageMedia, String emr, java.util.Map<String, Object> context) {
        String role = "DOCTOR";
        Long pid = resolvePatientId(context);
        // 兜底：前端接诊状态可能只带出患者姓名（patientId 回查失败时），
        // 从「消息 + 病历上下文」中按姓名解析接诊/点名患者，保证图片开方必用接诊患者档案
        if (pid == null) {
            try {
                Long named = medicalChatService.resolveNamedPatientForRx(
                        question + (emr == null || emr.isBlank() ? "" : ("；" + emr)));
                if (named != null && named > 0) pid = named;
            } catch (Exception ignored) {
            }
        }
        boolean canPrescribe = pid != null;

        StringBuilder sys = new StringBuilder();
        sys.append("你是春播云诊所AI辅助诊断智能体。医生上传了患者发病部位/病灶的图片，请严格按以下五步处理并输出：\n")
           .append("【第一步·图像识别】用3-6条要点客观描述图片病灶特征（部位/形态/颜色/分布/皮损类型），不要大段重复。\n")
           .append("【第二步·知识库比对】【必须调用工具】调用 searchKnowledge 检索基层诊疗知识库（RAG），query 用图片病症特征描述；")
           .append("命中方向后再调用 queryClinicalGuideline(疾病名) 获取该病的标准诊疗与用药规范。引用知识库条目标题作为比对依据。\n")
           .append("【第三步·给出诊断】结合图像特征与知识库规范，明确给出最可能的诊断（按可能性排序1-2个），各写一句支持点与关键鉴别点。\n");
        if (canPrescribe) {
            if (emr != null && !emr.isBlank()) {
                sys.append("【病历依据】本次门诊病历记录（真实有效，严禁凭空编造）：").append(emr).append("。\n");
            }
            sys.append("【第四步·按规范开方】当前接诊患者ID：").append(pid)
               .append("。先调用 queryPatientProfile(").append(pid)
               .append(") 核对过敏史（青霉素过敏严禁阿莫西林，头孢过敏严禁头孢类），再调用 queryMedicineStock(药品名) 核实真实库存与价格；")
               .append("然后按知识库规范输出【推荐处方药品】：只开规范推荐且药房有库存的药，每行格式「药品名 | 规格 | 用法用量 | ¥单价」。\n");
        } else {
            sys.append("【第四步·按规范开方】当前未接诊患者：仍必须开具处方！直接按知识库规范给出该病的标准治疗方案，")
               .append("调用 queryMedicineStock(药品名) 核实真实库存与价格，只开规范推荐且药房有库存的药，每行格式「药品名 | 规格 | 用法用量 | ¥单价」；")
               .append("并在处方前加一行提示：「⚠️ 未接诊患者，尚未核对过敏史，采纳处方前请医生当面核对患者过敏信息」。\n");
        }
        sys.append("【第五步·就诊建议】面诊/检查建议与转诊指征，附免责声明（最终诊断须执业医师面诊确认）。\n")
           .append("【格式要求】回答末尾必须单独一节，标题为「推荐处方药品」，其下每行一条：「- 药品名 | 规格 | 用法用量 | ¥单价」，系统将据此生成结构化处方卡片。\n")
           .append("【红线】图片仅供辅助参考；接诊患者时处方前必须核对过敏史；严禁编造药房没有的药品或价格。");

        String requestId = AbstractAgent.currentRequestId();
        StringBuffer ans = new StringBuffer();
        // 工具集：可开方时挂患者档案/库存/指南工具；仅分析时只挂 RAG 检索与指南
        java.util.List<Object> toolList = new java.util.ArrayList<>();
        if (ragKnowledgeService != null) toolList.add(ragKnowledgeService);
        toolList.add(clinicTools);
        java.util.List<Media> media = java.util.List.of(imageMedia);

        return functionCallingFlux(question, sessionId, userId, role, sys.toString(), media, toolList.toArray())
                .doOnNext(ev -> {
                    if (ev != null && ev.getEventType() == ChatEventTypeEnum.DATA.getValue()
                            && ev.getEventData() instanceof String s) {
                        ans.append(s);
                    }
                })
                .concatWith(Flux.defer(() -> {
                    // 流末用独立 LLM 从回答文本提取 rxItems 写入 ToolResultHolder，
                    // 由 AbstractAgent.wrapWithToolResult 统一转 PARAM 卡片下发（与 MED_DIAGNOSE 路径一致）。
                    // 未接诊患者同样按规范开方（卡片即标准治疗方案），医生采纳前自行核对过敏史。
                    medicalChatService.extractAndStoreRxItems(requestId, ans.toString());
                    return Flux.empty();
                }));
    }

    /** 从路由上下文还原真实接诊患者ID（未接诊且未点名时返回 null，由业务层提示先接诊） */
    private Long resolvePatientId(java.util.Map<String, Object> context) {
        Object pidObj = context != null ? context.get(AgentConstant.PATIENT_ID) : null;
        try {
            if (pidObj instanceof Number n) return n.longValue();
            if (pidObj != null && !String.valueOf(pidObj).isBlank()) return Long.valueOf(String.valueOf(pidObj));
        } catch (Exception ignored) {
        }
        return null;
    }
}
