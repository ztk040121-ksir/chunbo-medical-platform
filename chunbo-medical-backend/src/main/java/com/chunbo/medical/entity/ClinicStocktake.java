package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("clinic_stocktake")
public class ClinicStocktake {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String stocktakeNo;
    private String categoryScope;
    private Integer totalBookQty;
    private Integer totalActualQty;
    private Integer profitLossQty;
    private BigDecimal profitLossAmount;
    private String operatorName;
    private String status;
    private String remark;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getStocktakeNo() { return stocktakeNo; }
    public void setStocktakeNo(String stocktakeNo) { this.stocktakeNo = stocktakeNo; }
    public String getCategoryScope() { return categoryScope; }
    public void setCategoryScope(String categoryScope) { this.categoryScope = categoryScope; }
    public Integer getTotalBookQty() { return totalBookQty; }
    public void setTotalBookQty(Integer totalBookQty) { this.totalBookQty = totalBookQty; }
    public Integer getTotalActualQty() { return totalActualQty; }
    public void setTotalActualQty(Integer totalActualQty) { this.totalActualQty = totalActualQty; }
    public Integer getProfitLossQty() { return profitLossQty; }
    public void setProfitLossQty(Integer profitLossQty) { this.profitLossQty = profitLossQty; }
    public BigDecimal getProfitLossAmount() { return profitLossAmount; }
    public void setProfitLossAmount(BigDecimal profitLossAmount) { this.profitLossAmount = profitLossAmount; }
    public String getOperatorName() { return operatorName; }
    public void setOperatorName(String operatorName) { this.operatorName = operatorName; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

}
