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
