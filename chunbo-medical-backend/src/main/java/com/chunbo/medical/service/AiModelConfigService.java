package com.chunbo.medical.service;

import com.chunbo.medical.dto.AiModelConfigDto;
import com.chunbo.medical.tools.MedicalClinicTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * AI 模型网关与动态配置中枢服务
 */
@Service
public class AiModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiModelConfigService.class);

    @Autowired
    private ChatMemory chatMemory;

    @Autowired
    private MedicalClinicTools clinicTools;

    @Autowired
    private com.chunbo.medical.tools.WebFetchTools webFetchTools;

    @Autowired
    private RestClient.Builder proxyRestClientBuilder;

    @Value("${chunbo.ai.proxy.host:}")
    private String proxyHost;

    @Value("${chunbo.ai.proxy.port:0}")
    private int proxyPort;

    @Value("${spring.ai.openai.api-key:sk-YOUR_API_KEY_HERE}")
    private String defaultApiKey;

    @Value("${spring.ai.openai.base-url:https://api.ohmygpt.com}")
    private String defaultBaseUrl;

    @Value("${spring.ai.openai.chat.options.model:deepseek-v4-flash}")
    private String defaultModel;

    @Value("${chunbo.ai.vision-model:deepseek:deepseek/deepseek-v4-flash-vision-exp}")
    private String visionModel;

    @Value("${chunbo.ai.mock-enabled:false}")
    private boolean defaultMock;

    private AiModelConfigDto currentConfig;
    private ChatClient activeChatClient;
    private org.springframework.ai.chat.model.ChatModel activeChatModel;

    private String normalizeBaseUrl(String url) {
        if (url == null) return "https://api.ohmygpt.com";
        String u = url.trim();
        while (u.endsWith("/")) {
            u = u.substring(0, u.length() - 1);
        }
        if (u.endsWith("/v1")) {
            u = u.substring(0, u.length() - 3);
        }
        return u;
    }

    /** 构建带本地代理的 WebClient（流式 LLM 调用走 VPN 代理；魔搭等国内 MCP 直连不使用它） */
    private WebClient.Builder buildProxyWebClient() {
        WebClient.Builder builder = WebClient.builder();
        if (proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0) {
            HttpClient httpClient = HttpClient.create()
                    .proxy(p -> p.type(ProxyProvider.Proxy.HTTP).host(proxyHost).port(proxyPort));
            builder.clientConnector(new ReactorClientHttpConnector(httpClient));
        }
        return builder;
    }

    @PostConstruct
    public void init() {
        currentConfig = new AiModelConfigDto();
        currentConfig.setProvider("ohmygpt");
        currentConfig.setBaseUrl(normalizeBaseUrl(defaultBaseUrl));
        currentConfig.setApiKey(defaultApiKey);
        currentConfig.setModelName(defaultModel);
        currentConfig.setTemperature(0.3);
        currentConfig.setMockEnabled(defaultMock);

        rebuildChatClient();
    }

    public synchronized AiModelConfigDto getCurrentConfig() {
        return currentConfig;
    }

    public synchronized void updateConfig(AiModelConfigDto newConfig) {
        if (newConfig.getBaseUrl() != null) {
            newConfig.setBaseUrl(normalizeBaseUrl(newConfig.getBaseUrl()));
        }
        this.currentConfig = newConfig;
        rebuildChatClient();
        log.info("[AI Gateway] AI 模型配置已热更新 Provider={} Model={} Mock={}", newConfig.getProvider(), newConfig.getModelName(), newConfig.getMockEnabled());
    }

    public synchronized ChatClient getActiveChatClient() {
        return activeChatClient;
    }

    /** 视觉模型名（图片识别用，中转站 DeepSeek V4 Flash 视觉实验版） */
    public String getVisionModel() {
        return visionModel;
    }

    /**
     * 返回不带默认工具集的裸 ChatClient（保留记忆 advisor），供 function-calling 智能体按域显式挂载工具。
     * 避免与 getActiveChatClient 的 defaultTools(clinicTools, webFetchTools) 重复挂载同一批工具报错。
     */
    public synchronized ChatClient getBareChatClient() {
        if (activeChatModel == null) return null;
        return ChatClient.builder(activeChatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    public synchronized boolean isMockEnabled() {
        return Boolean.TRUE.equals(currentConfig.getMockEnabled());
    }

    /**
     * 重建 Spring AI ChatClient
     */
    private void rebuildChatClient() {
        if (Boolean.TRUE.equals(currentConfig.getMockEnabled())) {
            this.activeChatClient = null;
            this.activeChatModel = null;
            return;
        }

        try {
            String apiKey = currentConfig.getApiKey() != null ? currentConfig.getApiKey().trim() : "";
            // 本地模型（Ollama 等）无需真实密钥：空 key 兜底为占位符，避免构建失败
            if (apiKey.isEmpty()) apiKey = "none";
            String baseUrl = normalizeBaseUrl(currentConfig.getBaseUrl());
            String model = currentConfig.getModelName() != null ? currentConfig.getModelName().trim() : "deepseek-v4-flash";
            Double temp = currentConfig.getTemperature() != null ? currentConfig.getTemperature() : 0.3;

            OpenAiApi openAiApi = OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .restClientBuilder(proxyRestClientBuilder)
                    .webClientBuilder(buildProxyWebClient())
                    .build();

            OpenAiChatOptions options = OpenAiChatOptions.builder()
                    .model(model)
                    .temperature(temp)
                    .build();

            OpenAiChatModel chatModel = OpenAiChatModel.builder()
                    .openAiApi(openAiApi)
                    .defaultOptions(options)
                    .build();

            this.activeChatModel = chatModel;

            String systemPrompt = """
                    你是春播科技研发的基层全科医疗AI专家「春播万象」。
                    你的核心职责：
                    1. 严格结合患者主诉、症状推荐权威临床诊疗与用药方案。
                    2. 在开具处方前，务必调用工具查询患者药物过敏史（如青霉素过敏、头孢过敏）与药品库存。
                    3. 若患者过敏史与处方冲突，严禁开具该药物，并重点给出警示！
                    4. 每次输出遵循规范结构：
                       - 临床初步诊断
                       - 规范诊疗依据
                       - 推荐处方建议清单
                       - 用药安全与过敏禁忌预警
                       - 基层转诊与随访指征
                    所有用药建议仅供执业医师临床决策参考，必须经医师核准签字后生效。
                    """;

            this.activeChatClient = ChatClient.builder(chatModel)
                    .defaultSystem(systemPrompt)
                    .defaultAdvisors(
                            new SimpleLoggerAdvisor(),
                            MessageChatMemoryAdvisor.builder(chatMemory).build()
                    )
                    .defaultTools(clinicTools, webFetchTools)
                    .build();
        } catch (Exception e) {
            log.error("动态构建 ChatClient 失败", e);
            this.activeChatClient = null;
        }
    }

    /**
     * 一键连通性测试
     */
    public Map<String, Object> testConnectivity(AiModelConfigDto testConfig) {
        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();
        try {
            if (Boolean.TRUE.equals(testConfig.getMockEnabled())) {
                result.put("success", true);
                result.put("message", "本地临床规则引擎已就绪 (内网离线模式，模拟响应延迟 1ms)");
                result.put("latencyMs", 1);
                return result;
            }

            String baseUrl = normalizeBaseUrl(testConfig.getBaseUrl());
            String apiKey = testConfig.getApiKey() != null ? testConfig.getApiKey().trim() : "";
            String modelName = testConfig.getModelName() != null ? testConfig.getModelName().trim() : "deepseek-v4-flash";

            OpenAiApi api = OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .restClientBuilder(proxyRestClientBuilder)
                    .webClientBuilder(buildProxyWebClient())
                    .build();

            OpenAiChatOptions opts = OpenAiChatOptions.builder()
                    .model(modelName)
                    .build();

            OpenAiChatModel model = OpenAiChatModel.builder()
                    .openAiApi(api)
                    .defaultOptions(opts)
                    .build();

            String response = model.call("回复'连接成功'四个字。");

            long latency = System.currentTimeMillis() - start;
            result.put("success", true);
            result.put("message", "大模型连接正常！返回: " + response.trim());
            result.put("latencyMs", latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
            result.put("latencyMs", latency);
        }
        return result;
    }
}
