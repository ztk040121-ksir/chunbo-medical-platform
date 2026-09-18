package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("clinic_patient_followup")
public class ClinicPatientFollowup {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String followupNo;
    private Long patientId;
    private String patientName;
    private String patientPhone;
    private String diagnosis;
    private LocalDate planDate;
    private LocalDate actualDate;
    private String followupContent;
    private String followupResult;
    private String operatorName;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getFollowupNo() { return followupNo; }
    public void setFollowupNo(String followupNo) { this.followupNo = followupNo; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getPatientPhone() { return patientPhone; }
    public void setPatientPhone(String patientPhone) { this.patientPhone = patientPhone; }
    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }
    public LocalDate getPlanDate() { return planDate; }
    public void setPlanDate(LocalDate planDate) { this.planDate = planDate; }
    public LocalDate getActualDate() { return actualDate; }
    public void setActualDate(LocalDate actualDate) { this.actualDate = actualDate; }
    public String getFollowupContent() { return followupContent; }
    public void setFollowupContent(String followupContent) { this.followupContent = followupContent; }
    public String getFollowupResult() { return followupResult; }
    public void setFollowupResult(String followupResult) { this.followupResult = followupResult; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
