package com.chunbo.medical.config;

import com.chunbo.medical.tools.MedicalClinicTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
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

    @Value("${chunbo.ai.memory.max:100}")
    private int maxMessages;

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
     * WebClient Builder（流式调用与音频 TTS/ASR 走 Reactor Netty），
     * 同样需要显式配置 VPN HTTP 代理，否则直连 ohmygpt 会连接超时；
     * 音频合成/上传较慢，响应超时放宽到 60s。
     */
    @Bean
    public org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder() {
        reactor.netty.http.client.HttpClient httpClient = reactor.netty.http.client.HttpClient.create()
                .responseTimeout(java.time.Duration.ofSeconds(60));
        if (proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0) {
            httpClient = httpClient.proxy(spec -> spec
                    .type(reactor.netty.transport.ProxyProvider.Proxy.HTTP)
                    .host(proxyHost)
                    .port(proxyPort));
        }
        return org.springframework.web.reactive.function.client.WebClient.builder()
                .clientConnector(new org.springframework.http.client.reactive.ReactorClientHttpConnector(httpClient));
    }

    /**
     * 会话记忆管理器：MessageWindowChatMemory（滑动窗口限流） + RedisChatMemoryRepository（Redis 持久化）
     * 参照《SpringAI》笔记标准实现，最多保存 maxMessages 条，超出自动淘汰最旧消息
     */
    @Bean
    public ChatMemory chatMemory(ChatMemoryRepository chatMemoryRepository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(this.maxMessages)
                .build();
    }

    /**
     * 向量库：SimpleVectorStore（内存实现，教学/演示用，参照《SpringAI》笔记 RAG 标准实现）
     * 由 RagKnowledgeService 把 rag_docs 诊疗规范切块向量化后写入，检索用 similaritySearch(SearchRequest)。
     * 知识库源头是静态 md 文件（每次启动重新向量化），故无需 JSON 持久化。
     */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        return SimpleVectorStore.builder(embeddingModel).build();
    }

    /**
     * 构建具备医学人设、工具调用能力及记忆拦截器的 ChatClient
     * （会话记忆使用 RedisChatMemory 持久化实现，Spring 自动注入 ChatMemory 接口的唯一实现）
     */
    @Bean
    public ChatClient medicalChatClient(ChatModel chatModel,
                                         ChatMemory chatMemory,
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
                        MessageChatMemoryAdvisor.builder(chatMemory).build()
                )
                // 挂载基层医疗工具集
                .defaultTools(clinicTools)
                .build();
    }

    /**
     * 独立的标题提炼 ChatClient（参照《SpringAI》笔记 AI 提炼标题）
     * 注意：不能与业务 medicalChatClient 共享同一个 Client 对象，需创建新的裸 Client
     * （不挂载工具、记忆 advisor，也不挂医疗人设，专门用于把"用户提问+AI回答"提炼成会话标题）
     */
    @Bean
    public ChatClient titleChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }

    /**
     * 独立的通用文本处理 ChatClient（参照《SpringAI》笔记通用文本模型）
     * 专门用于帮写/续写/润色/精简等文本处理，同样不与业务 Client 共享
     */
    @Bean
    public ChatClient textChatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
