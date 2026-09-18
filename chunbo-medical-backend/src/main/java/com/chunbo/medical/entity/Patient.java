package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("patient")
public class Patient {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String gender;
    private Integer age;
    private String phone;
    private String allergies;
    private String medicalHistory;
    private String remarks;
    private String idCard;
    private String address;

    // === 会员系统字段 ===
    /** 会员等级: 普通居民 / 慢病签约会员 / VIP会员 */
    private String memberLevel;
    /** 会员折扣率(0.0~1.0), 例如0.85表示85折 */
    private Double discountRate;
    /** 会员积分 */
    private Integer points;
    /** 会员储值余额(元) */
    private Double balance;
    /** 会员到期日期 (yyyy-MM-dd) */
    private String memberExpiry;
    /** 附属卡主患者ID: 该患者是哪位主会员的附属卡 */
    private Long auxiliaryOf;
    /** 随访日期 */
    private String followupDate;
    private String followupNotes;
    private LocalDateTime createTime;

    // Getters & Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getGender() { return gender; }
    public void setGender(String gender) { this.gender = gender; }
    public Integer getAge() { return age; }
    public void setAge(Integer age) { this.age = age; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getAllergies() { return allergies; }
    public void setAllergies(String allergies) { this.allergies = allergies; }
    public String getMedicalHistory() { return medicalHistory; }
    public void setMedicalHistory(String medicalHistory) { this.medicalHistory = medicalHistory; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public String getIdCard() { return idCard; }
    public void setIdCard(String idCard) { this.idCard = idCard; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getMemberLevel() { return memberLevel; }
    public void setMemberLevel(String memberLevel) { this.memberLevel = memberLevel; }
    public Double getDiscountRate() { return discountRate; }
    public void setDiscountRate(Double discountRate) { this.discountRate = discountRate; }
    public Integer getPoints() { return points; }
    public void setPoints(Integer points) { this.points = points; }
    public Double getBalance() { return balance; }
    public void setBalance(Double balance) { this.balance = balance; }
    public String getMemberExpiry() { return memberExpiry; }
    public void setMemberExpiry(String memberExpiry) { this.memberExpiry = memberExpiry; }
    public Long getAuxiliaryOf() { return auxiliaryOf; }
    public void setAuxiliaryOf(Long auxiliaryOf) { this.auxiliaryOf = auxiliaryOf; }
    public String getFollowupDate() { return followupDate; }
    public void setFollowupDate(String followupDate) { this.followupDate = followupDate; }
    public String getFollowupNotes() { return followupNotes; }
    public void setFollowupNotes(String followupNotes) { this.followupNotes = followupNotes; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}