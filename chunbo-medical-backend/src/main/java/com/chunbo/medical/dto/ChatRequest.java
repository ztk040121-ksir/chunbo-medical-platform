package com.chunbo.medical.dto;

import lombok.Data;

@Data
public class ChatRequest {
    private String sessionId;   // 会话标识
    private Long patientId;     // 当前问诊患者ID
    private String message;      // 医生输入的主诉或追问

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

}
