package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("sys_ai_config")
public class SysAiConfig {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String providerName;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private BigDecimal temperature;
    private Integer maxTokens;
    private Integer isActive;
    private LocalDateTime updateTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getProviderName() { return providerName; }
    public void setProviderName(String providerName) { this.providerName = providerName; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }
    public BigDecimal getTemperature() { return temperature; }
    public void setTemperature(BigDecimal temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public Integer getIsActive() { return isActive; }
    public void setIsActive(Integer isActive) { this.isActive = isActive; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
