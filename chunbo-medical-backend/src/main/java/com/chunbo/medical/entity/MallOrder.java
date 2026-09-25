package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mall_order")
public class MallOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String orderNo;
    private String clinicName;
    private String buyerName;
    private Long userId;
    private BigDecimal totalAmount;
    private BigDecimal discountAmount;
    private BigDecimal finalAmount;
    private String itemsJson;
    private String status;
    private String bargainNotes;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }

    public String getClinicName() { return clinicName; }
    public void setClinicName(String clinicName) { this.clinicName = clinicName; }

    public String getBuyerName() { return buyerName; }
    public void setBuyerName(String buyerName) { this.buyerName = buyerName; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getFinalAmount() { return finalAmount; }
    public void setFinalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; }

    public String getItemsJson() { return itemsJson; }
    public void setItemsJson(String itemsJson) { this.itemsJson = itemsJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getBargainNotes() { return bargainNotes; }
    public void setBargainNotes(String bargainNotes) { this.bargainNotes = bargainNotes; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
