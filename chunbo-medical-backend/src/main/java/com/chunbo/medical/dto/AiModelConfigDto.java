package com.chunbo.medical.dto;

import lombok.Data;

/**
 * AI 引擎动态配置实体
 */
@Data
public class AiModelConfigDto {
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String modelName;
    private Double temperature;
    private Boolean mockEnabled;

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModelName() { return modelName; }
    public void setModelName(String modelName) { this.modelName = modelName; }

    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }

    public Boolean getMockEnabled() { return mockEnabled; }
    public void setMockEnabled(Boolean mockEnabled) { this.mockEnabled = mockEnabled; }
}
