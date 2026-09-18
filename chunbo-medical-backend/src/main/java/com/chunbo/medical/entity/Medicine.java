package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@TableName("medicine")
public class Medicine {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String tradeName;
    private String pinyinCode;
    private String barcode;
    private String approvalNumber;
    private String manufacturer;
    private String primaryCategory;
    private String secondaryCategory;
    private String specification;
    private String category;
    private Integer stock;
    private Integer warningStock;
    private BigDecimal costPrice;
    private BigDecimal price;
    private String unit;
    private String locationCode;
    private LocalDate expiryDate;
    private Integer isSplit;
    private String splitUnit;
    private String isPrescription;
    private Integer isActive;
    private String defaultDosage;
    private String contraindications;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTradeName() { return tradeName; }
    public void setTradeName(String tradeName) { this.tradeName = tradeName; }

    public String getPinyinCode() { return pinyinCode; }
    public void setPinyinCode(String pinyinCode) { this.pinyinCode = pinyinCode; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public String getApprovalNumber() { return approvalNumber; }
    public void setApprovalNumber(String approvalNumber) { this.approvalNumber = approvalNumber; }

    public String getManufacturer() { return manufacturer; }
    public void setManufacturer(String manufacturer) { this.manufacturer = manufacturer; }

    public String getPrimaryCategory() { return primaryCategory; }
    public void setPrimaryCategory(String primaryCategory) { this.primaryCategory = primaryCategory; }

    public String getSecondaryCategory() { return secondaryCategory; }
    public void setSecondaryCategory(String secondaryCategory) { this.secondaryCategory = secondaryCategory; }

    public String getSpecification() { return specification; }
    public void setSpecification(String specification) { this.specification = specification; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }

    public Integer getWarningStock() { return warningStock; }
    public void setWarningStock(Integer warningStock) { this.warningStock = warningStock; }

    public BigDecimal getCostPrice() { return costPrice; }
    public void setCostPrice(BigDecimal costPrice) { this.costPrice = costPrice; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }

    public String getLocationCode() { return locationCode; }
    public void setLocationCode(String locationCode) { this.locationCode = locationCode; }

    public LocalDate getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDate expiryDate) { this.expiryDate = expiryDate; }

    public Integer getIsSplit() { return isSplit; }
    public void setIsSplit(Integer isSplit) { this.isSplit = isSplit; }

    public String getSplitUnit() { return splitUnit; }
    public void setSplitUnit(String splitUnit) { this.splitUnit = splitUnit; }

    public String getIsPrescription() { return isPrescription; }
    public void setIsPrescription(String isPrescription) { this.isPrescription = isPrescription; }

    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }

    public String getDefaultDosage() { return defaultDosage; }
    public void setDefaultDosage(String defaultDosage) { this.defaultDosage = defaultDosage; }

    public String getContraindications() { return contraindications; }
    public void setContraindications(String contraindications) { this.contraindications = contraindications; }
}
