package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("oa_salary_slip")
public class OaSalarySlip {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String doctorId;
    private String doctorName;
    private String salaryMonth;
    private BigDecimal baseSalary;
    private BigDecimal clinicCommission;
    private BigDecimal plasterCommission;
    private BigDecimal deductionSocial;
    private BigDecimal tax;
    private BigDecimal netSalary;
    private String status;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDoctorId() { return doctorId; }
    public void setDoctorId(String doctorId) { this.doctorId = doctorId; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getSalaryMonth() { return salaryMonth; }
    public void setSalaryMonth(String salaryMonth) { this.salaryMonth = salaryMonth; }

    public BigDecimal getBaseSalary() { return baseSalary; }
    public void setBaseSalary(BigDecimal baseSalary) { this.baseSalary = baseSalary; }

    public BigDecimal getClinicCommission() { return clinicCommission; }
    public void setClinicCommission(BigDecimal clinicCommission) { this.clinicCommission = clinicCommission; }

    public BigDecimal getPlasterCommission() { return plasterCommission; }
    public void setPlasterCommission(BigDecimal plasterCommission) { this.plasterCommission = plasterCommission; }

    public BigDecimal getDeductionSocial() { return deductionSocial; }
    public void setDeductionSocial(BigDecimal deductionSocial) { this.deductionSocial = deductionSocial; }

    public BigDecimal getTax() { return tax; }
    public void setTax(BigDecimal tax) { this.tax = tax; }

    public BigDecimal getNetSalary() { return netSalary; }
    public void setNetSalary(BigDecimal netSalary) { this.netSalary = netSalary; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
