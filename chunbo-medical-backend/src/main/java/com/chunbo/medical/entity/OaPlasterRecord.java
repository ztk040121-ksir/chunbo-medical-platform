package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("oa_plaster_record")
public class OaPlasterRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String patientName;
    private String plasterType;
    private Integer pasteCount;
    private BigDecimal unitPrice;
    private BigDecimal totalAmount;
    private String doctorName;
    private String clinicName;
    private LocalDate therapyDate;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }
    public String getPlasterType() { return plasterType; }
    public void setPlasterType(String plasterType) { this.plasterType = plasterType; }
    public Integer getPasteCount() { return pasteCount; }
    public void setPasteCount(Integer pasteCount) { this.pasteCount = pasteCount; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }
    public String getClinicName() { return clinicName; }
    public void setClinicName(String clinicName) { this.clinicName = clinicName; }
    public LocalDate getTherapyDate() { return therapyDate; }
    public void setTherapyDate(LocalDate therapyDate) { this.therapyDate = therapyDate; }

}
