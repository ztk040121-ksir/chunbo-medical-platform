package com.chunbo.medical.agent;

import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.dto.ChatRequest;
import com.chunbo.medical.enums.ChatEventTypeEnum;
import com.chunbo.medical.service.MedicalChatService;
import com.chunbo.medical.tools.MedicalClinicTools;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

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
