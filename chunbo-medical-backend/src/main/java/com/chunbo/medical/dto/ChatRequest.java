package com.chunbo.medical.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String sessionId;   // 会话标识
    private Long patientId;     // 当前问诊患者ID
    private String message;      // 医生输入的主诉或追问
    private String doctorId;     // 医生工号（会话历史 userId，方案A：前端从登录态传入）
    private String emrContext;   // 本次门诊病历摘要（前端病历结构化注入，AI 辨证基于真实病历而非模板）
    private String routeHint;    // 路由智能体判出的意图（MED_DIAGNOSE/MED_STOCK/MED_KNOWLEDGE），驱动业务技能分流
    private String requestId;    // 本次请求 id（ToolResultHolder 卡片关联，由 AbstractAgent 生成并透传）

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getDoctorId() { return doctorId; }
    public void setDoctorId(String doctorId) { this.doctorId = doctorId; }

}
