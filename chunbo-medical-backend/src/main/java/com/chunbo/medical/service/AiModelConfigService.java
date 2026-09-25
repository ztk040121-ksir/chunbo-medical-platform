package com.chunbo.medical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chunbo.medical.dto.AiModelConfigDto;
import com.chunbo.medical.entity.SysAiConfig;
import com.chunbo.medical.mapper.SysAiConfigMapper;
import com.chunbo.medical.tools.MedicalClinicTools;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.io.File;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

/**
 * AI 模型网关与动态配置中枢服务
 * 具备数据库持久化（sys_ai_config表）与本地文件双重保障，支持重启后无缝加载，支持热切换模型渠道
 */
@Service
public class AiModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(AiModelConfigService.class);

    private static final String BACKUP_CONFIG_PATH = "config/ai_model_config.json";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private SysAiConfigMapper sysAiConfigMapper;

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

    @Value("${spring.ai.openai.api-key:sk-1FEAUBAdC6ee71Eaf9a3T3BLbkFJ6756Bd2A1B6B40B8aa77}")
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
                    .responseTimeout(Duration.ofSeconds(90))
                    .proxy(p -> p.type(ProxyProvider.Proxy.HTTP).host(proxyHost).port(proxyPort));
            builder.clientConnector(new ReactorClientHttpConnector(httpClient));
        }
        return builder;
    }

    @PostConstruct
    public void init() {
        // 1. 尝试从数据库 sys_ai_config 加载持久化配置
        boolean loadedFromDb = false;
        if (sysAiConfigMapper != null) {
            try {
                SysAiConfig activeEntity = sysAiConfigMapper.selectOne(
                        new LambdaQueryWrapper<SysAiConfig>().eq(SysAiConfig::getIsActive, 1).last("LIMIT 1")
                );
                if (activeEntity != null) {
                    currentConfig = entityToDto(activeEntity);
                    loadedFromDb = true;
                    log.info("[AI Gateway] 成功从数据库 sys_ai_config 加载已激活模型配置: provider={} model={}",
                            currentConfig.getProvider(), currentConfig.getModelName());
                }
            } catch (Exception e) {
                log.warn("[AI Gateway] 从数据库加载 AI 配置异常，准备读取备份文件: {}", e.getMessage());
            }
        }

        // 2. 若未从数据库加载，尝试从本地文件载入
        if (!loadedFromDb) {
            AiModelConfigDto backup = loadFromFile();
            if (backup != null) {
                currentConfig = backup;
                log.info("[AI Gateway] 成功从本地配置文件 {} 加载模型配置: provider={} model={}",
                        BACKUP_CONFIG_PATH, currentConfig.getProvider(), currentConfig.getModelName());
            } else {
                // 3. 兜底默认值
                currentConfig = new AiModelConfigDto();
                currentConfig.setProvider("ohmygpt");
                currentConfig.setBaseUrl(normalizeBaseUrl(defaultBaseUrl));
                currentConfig.setApiKey(defaultApiKey);
                currentConfig.setModelName(defaultModel);
                currentConfig.setTemperature(0.3);
                currentConfig.setMockEnabled(defaultMock);
                log.info("[AI Gateway] 使用系统默认配置初始化: provider={} model={}",
                        currentConfig.getProvider(), currentConfig.getModelName());
            }
        }

        // 4. 重建大模型客户端
        rebuildChatClient();
    }

    public synchronized AiModelConfigDto getCurrentConfig() {
        return currentConfig;
    }

    /**
     * 真实热更新：更新内存单例 + 同步持久化落库（sys_ai_config）与落盘文件 + 实时重建 ChatClient
     */
    public synchronized void updateConfig(AiModelConfigDto newConfig) {
        if (newConfig.getBaseUrl() != null) {
            newConfig.setBaseUrl(normalizeBaseUrl(newConfig.getBaseUrl()));
        }
        this.currentConfig = newConfig;

        // 1. 持久化到数据库 sys_ai_config
        persistToDatabase(newConfig);

        // 2. 写入备份文件
        persistToFile(newConfig);

        // 3. 实时重建大模型客户端
        rebuildChatClient();
        log.info("[AI Gateway] AI 模型配置已完成真实持久化落库并热重载: Provider={} Model={} Mock={}",
                newConfig.getProvider(), newConfig.getModelName(), newConfig.getMockEnabled());
    }

    /**
     * 查询所有保存的模型通道列表
     */
    public List<SysAiConfig> getAllConfigs() {
        if (sysAiConfigMapper == null) return List.of();
        try {
            return sysAiConfigMapper.selectList(new LambdaQueryWrapper<SysAiConfig>().orderByDesc(SysAiConfig::getIsActive));
        } catch (Exception e) {
            log.error("获取模型配置列表失败", e);
            return List.of();
        }
    }

    /**
     * 一键切换激活指定模型
     */
    public synchronized AiModelConfigDto switchConfig(Long id) {
        if (sysAiConfigMapper == null) return currentConfig;
        SysAiConfig target = sysAiConfigMapper.selectById(id);
        if (target == null) throw new IllegalArgumentException("未找到ID为 " + id + " 的AI配置！");

        sysAiConfigMapper.update(null, new LambdaUpdateWrapper<SysAiConfig>().set(SysAiConfig::getIsActive, 0));
        target.setIsActive(1);
        target.setUpdateTime(LocalDateTime.now());
        sysAiConfigMapper.updateById(target);

        AiModelConfigDto dto = entityToDto(target);
        this.currentConfig = dto;
        persistToFile(dto);
        rebuildChatClient();
        log.info("[AI Gateway] 已成功切换并激活模型配置: ID={} Provider={} Model={}", id, dto.getProvider(), dto.getModelName());
        return dto;
    }

    private void persistToDatabase(AiModelConfigDto dto) {
        if (sysAiConfigMapper == null) return;
        try {
            // 先将其他所有记录设为非激活
            sysAiConfigMapper.update(null, new LambdaUpdateWrapper<SysAiConfig>().set(SysAiConfig::getIsActive, 0));

            String provider = dto.getProvider() != null ? dto.getProvider() : "custom";
            String model = dto.getModelName() != null ? dto.getModelName() : "deepseek-v4-flash";

            // 查找是否存在相同提供商与模型名称的记录
            SysAiConfig exist = sysAiConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysAiConfig>()
                            .eq(SysAiConfig::getProviderName, provider)
                            .eq(SysAiConfig::getModelName, model)
                            .last("LIMIT 1")
            );

            if (exist != null) {
                exist.setBaseUrl(dto.getBaseUrl());
                exist.setApiKey(dto.getApiKey());
                exist.setTemperature(BigDecimal.valueOf(dto.getTemperature() != null ? dto.getTemperature() : 0.3));
                exist.setIsActive(1);
                exist.setUpdateTime(LocalDateTime.now());
                sysAiConfigMapper.updateById(exist);
            } else {
                SysAiConfig entity = new SysAiConfig();
                entity.setProviderName(provider);
                entity.setBaseUrl(dto.getBaseUrl());
                entity.setApiKey(dto.getApiKey());
                entity.setModelName(model);
                entity.setTemperature(BigDecimal.valueOf(dto.getTemperature() != null ? dto.getTemperature() : 0.3));
                entity.setMaxTokens(2048);
                entity.setIsActive(1);
                entity.setUpdateTime(LocalDateTime.now());
                sysAiConfigMapper.insert(entity);
            }
        } catch (Exception e) {
            log.warn("[AI Gateway] 持久化落库异常: {}", e.getMessage());
        }
    }

    private void persistToFile(AiModelConfigDto dto) {
        try {
            File f = new File(BACKUP_CONFIG_PATH);
            File parent = f.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(f, dto);
        } catch (Exception e) {
            log.warn("[AI Gateway] 备份配置文件失败: {}", e.getMessage());
        }
    }

    private AiModelConfigDto loadFromFile() {
        try {
            File f = new File(BACKUP_CONFIG_PATH);
            if (f.exists()) {
                return objectMapper.readValue(f, AiModelConfigDto.class);
            }
        } catch (Exception e) {
            log.warn("[AI Gateway] 读取本地备份配置异常: {}", e.getMessage());
        }
        return null;
    }

    private AiModelConfigDto entityToDto(SysAiConfig entity) {
        AiModelConfigDto dto = new AiModelConfigDto();
        dto.setProvider(entity.getProviderName());
        dto.setBaseUrl(normalizeBaseUrl(entity.getBaseUrl()));
        dto.setApiKey(entity.getApiKey());
        dto.setModelName(entity.getModelName());
        dto.setTemperature(entity.getTemperature() != null ? entity.getTemperature().doubleValue() : 0.3);
        dto.setMockEnabled(entity.getProviderName() != null && entity.getProviderName().contains("Mock"));
        return dto;
    }

    public synchronized ChatClient getActiveChatClient() {
        return activeChatClient;
    }

    /** 视觉模型名（图片识别用，中转站 DeepSeek V4 Flash 视觉实验版） */
    public String getVisionModel() {
        return visionModel;
    }

    public synchronized ChatClient getBareChatClient() {
        if (activeChatModel == null) return null;
        return ChatClient.builder(activeChatModel)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                .build();
    }

    public synchronized ChatClient getPreConsultChatClient() {
        if (activeChatModel == null) return null;
        return ChatClient.builder(activeChatModel).build();
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
            this.activeChatModel = null;
        }
    }

    public Map<String, Object> testConnectivity(AiModelConfigDto testConfig) {
        Map<String, Object> result = new HashMap<>();
        long start = System.currentTimeMillis();
        try {
            if (Boolean.TRUE.equals(testConfig.getMockEnabled())) {
                result.put("success", true);
                result.put("latency", 15);
                result.put("message", "本地规则引擎响应正常（离线 Mock 模式）");
                return result;
            }

            String apiKey = testConfig.getApiKey() != null ? testConfig.getApiKey().trim() : "";
            if (apiKey.isEmpty()) apiKey = "none";
            String baseUrl = normalizeBaseUrl(testConfig.getBaseUrl());
            String model = testConfig.getModelName() != null ? testConfig.getModelName().trim() : "deepseek-v4-flash";

            OpenAiApi testApi = OpenAiApi.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .restClientBuilder(proxyRestClientBuilder)
                    .webClientBuilder(buildProxyWebClient())
                    .build();

            OpenAiChatModel testModel = OpenAiChatModel.builder()
                    .openAiApi(testApi)
                    .defaultOptions(OpenAiChatOptions.builder().model(model).temperature(0.1).build())
                    .build();

            ChatClient testClient = ChatClient.builder(testModel).build();
            String reply = testClient.prompt()
                    .user("你好，请只回复「PING_OK」四个字测试网络连通性。")
                    .call()
                    .content();

            long cost = System.currentTimeMillis() - start;
            result.put("success", true);
            result.put("latency", cost);
            result.put("message", "连通成功！大模型响应: " + (reply != null ? reply.trim() : "OK") + " (耗时 " + cost + "ms)");
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            result.put("success", false);
            result.put("latency", cost);
            result.put("message", "测试连通失败: " + e.getMessage());
        }
        return result;
    }
}
