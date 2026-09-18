package com.chunbo.medical.config;

import com.chunbo.medical.tools.MedicalClinicTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Spring AI 核心配置类
 * 对应简历亮点：基于 ChatClient 搭建 Session 服务，配置 SimpleLoggerAdvisor 与 MessageChatMemoryAdvisor
 */
@Configuration
public class SpringAiConfig {

    @Value("${chunbo.ai.clinic-name:春播云诊所 AI 辅助问诊中心}")
    private String clinicName;

    @Value("${chunbo.ai.proxy.host:}")
    private String proxyHost;

    @Value("${chunbo.ai.proxy.port:0}")
    private int proxyPort;

    /**
     * 为 Spring AI 的 OpenAI 客户端提供短超时 RestClient，
     * 避免大模型端点不可达时因默认长连接超时(约20s) + 重试导致接口长时间阻塞。
     * 支持可选的本地 HTTP 代理（VPN 开系统代理模式时 Java 进程不会自动走代理，需显式配置）。
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(15000);
        if (proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0) {
            factory.setProxy(new java.net.Proxy(java.net.Proxy.Type.HTTP,
                    new java.net.InetSocketAddress(proxyHost, proxyPort)));
        }
        return RestClient.builder().requestFactory(factory);
    }

    /**
     * 会话记忆管理器 (内存持久化，可无缝平替为 RedisChatMemory)
     */
    @Bean
    public InMemoryChatMemory inMemoryChatMemory() {
        return new InMemoryChatMemory();
    }

    /**
     * 构建具备医学人设、工具调用能力及记忆拦截器的 ChatClient
     */
    @Bean
    public ChatClient medicalChatClient(ChatModel chatModel,
                                         InMemoryChatMemory chatMemory,
                                         MedicalClinicTools clinicTools) {
        String systemPrompt = """
                你是由春播万象科技自主研发的专业医疗AI问诊智能体「春播云诊所处方助手」。
                【服务机构】：""" + clinicName + """
                【核心职责】：
                1. 辅助基层医生采集患者病情、梳理症状，推荐权威诊疗方案与用药指南。
                2. 在给出用药建议前，【必须自主调用工具】核对患者过敏史（如青霉素、磺胺等过敏史）以及药房实时库存规格。
                3. 若患者有药物过敏史（如青霉素过敏），严禁推荐相关过敏药物（如阿莫西林），并加粗显著警示！
                4. 每次建议需遵循规范结构：
                   - 【临床初步诊断与病情分析】
                   - 【基层规范诊疗方案依据】
                   - 【推荐规范处方清单】（包含通用名、规格、单次剂量、给药频次）
                   - 【用药安全与过敏禁忌预警】
                   - 【基层转诊指征与随访建议】
                【严正声明】：所有内容仅供执业医师临床决策参考，最终处方需由执业医师签字生效。
                """;

        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                // 挂载日志审计 Advisor (记录 Token 与会话耗时)
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        new MessageChatMemoryAdvisor(chatMemory)
                )
                // 挂载基层医疗工具集
                .defaultTools(clinicTools)
                .build();
    }
}
