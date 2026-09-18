package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("clinic_registration")
public class ClinicRegistration {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String regNo;
    private Long patientId;
    private String patientName;
    private String doctorName;
    private String department;
    private String regType;
    private BigDecimal regFee;
    private String status;
    private Integer queueNo;
    private String queueNumber;
    private String gender;
    private Integer age;
    private String ageText;
    private String phone;
    private String idCard;
    private String address;
    private String symptoms;

    // 挂号时采集的患者详细资料 (选填)
    private String marriage;
    private String height;
    private String weight;
    private String job;
    private String company;
    private String wechat;
    private String insuranceNo;
    private String accompany;
    private String accompanyPhone;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;

    @TableField(exist = false)
    private BigDecimal fee;

    public BigDecimal getFee() {
        return fee != null ? fee : regFee;
    }

    public void setFee(BigDecimal fee) {
        this.regFee = fee;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRegNo() { return regNo; }
    public void setRegNo(String regNo) { this.regNo = regNo; }
    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }
    public String getRegType() { return regType; }
    public void setRegType(String regType) { this.regType = regType; }
    public BigDecimal getRegFee() { return regFee; }
    public void setRegFee(BigDecimal regFee) { this.regFee = regFee; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getQueueNo() { return queueNo; }
    public void setQueueNo(Integer queueNo) { this.queueNo = queueNo; }
    public String getQueueNumber() { return queueNumber; }
    public void setQueueNumber(String queueNumber) { this.queueNumber = queueNumber; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getAgeText() { return ageText; }
    public void setAgeText(String ageText) { this.ageText = ageText; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getSymptoms() { return symptoms; }
    public void setSymptoms(String symptoms) { this.symptoms = symptoms; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

}
