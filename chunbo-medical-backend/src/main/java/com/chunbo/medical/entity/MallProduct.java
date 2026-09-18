package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("mall_product")
public class MallProduct {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String productName;
    private String genericName;
    private String specification;
    private String manufacturer;
    private BigDecimal wholesalePrice;
    private BigDecimal retailGuidePrice;
    private BigDecimal profitRate;
    private Integer stock;
    private Integer stockQty;
    private String status; // ON_SALE, OFF_SALE
    private String category;
    private String directorPitch;
    private String buyerPitch;
    private String csPitch;
    private String imageUrl;
    private LocalDateTime createTime;

    public String getStatus() {
        return status != null && !status.isEmpty() ? status : "ON_SALE";
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getStock() {
        if (stock != null) return stock;
        if (stockQty != null) return stockQty;
        return 500;
    }

    public void setStock(Integer stock) {
        this.stock = stock;
        this.stockQty = stock;
    }

    public String getCategory() {
        return category != null ? category : "家庭常备药";
    }
}
