package com.chunbo.medical.agent;

import com.chunbo.medical.config.ToolResultHolder;
import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.enums.ChatEventTypeEnum;
import com.chunbo.medical.service.AiModelConfigService;
import com.chunbo.medical.service.ChatSessionService;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽象智能体（参照《SpringAI》笔记多智能体协作标准实现）
 * 封装了停止生成 / 流式输出 / 会话记忆 / 结构化卡片 / 更新标题等通用逻辑，
 * 业务智能体只需 override {@link #buildContentFlux} 提供业务内容流，以及 getAgentType / bizType 等差异点。
 */
public abstract class AbstractAgent implements Agent {

    @Autowired
    protected ChatMemory chatMemory;

    @Autowired
    protected ChatSessionService chatSessionService;

    @Autowired
    protected AiModelConfigService aiModelConfigService;

    /** 输出结束标记事件 */
    public static final ChatEventVO STOP_EVENT = ChatEventVO.builder()
            .eventType(ChatEventTypeEnum.STOP.getValue())
            .build();

    /** 大模型生成状态表：key=sessionId；true=继续输出；移除=false=终止输出（线程安全） */
    public static final Map<String, Boolean> GENERATE_STATUS = new ConcurrentHashMap<>();

    /**
     * 当前请求的 requestId（ThreadLocal）。工具/service 技能通过
     * {@link #currentRequestId()} 拿到本次请求 id，把结构化结果存入 {@link ToolResultHolder}，
     * 由 {@link #wrapWithToolResult} 在流结束时统一提取转 PARAM 事件下发。
     */
    private static final ThreadLocal<String> CURRENT_REQUEST_ID = new ThreadLocal<>();

    /** 获取当前线程的 requestId（供工具与确定性技能把结构化数据存入 ToolResultHolder） */
    public static String currentRequestId() {
        return CURRENT_REQUEST_ID.get();
    }

    /**
     * 业务内容流模板方法：子类提供「业务内容事件流（DATA 事件，不含停止控制/标题/STOP）」
     */
    protected abstract Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId);

    /**
     * 业务内容流模板方法（携带路由意图提示）：默认忽略 hint，需要语义意图的子类（如商城）覆盖
     */
    protected Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId, String routeHint) {
        return buildContentFlux(question, sessionId, userId);
    }

    /**
     * 业务内容流模板方法（携带业务上下文 patientId/emrContext 等）：默认忽略，需要的子类（如问诊）覆盖
     */
    protected Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId, String routeHint, java.util.Map<String, Object> context) {
        return buildContentFlux(question, sessionId, userId, routeHint);
    }

    /**
     * 处理流式请求：给业务内容流统一套上停止生成控制 + 会话历史（AI 提炼标题）+ 结束 STOP 事件
     */
    @Override
    public Flux<ChatEventVO> processStream(String question, String sessionId, String userId) {
        return processStream(question, sessionId, userId, null);
    }

    /**
     * 处理流式请求（携带路由意图提示）：给业务内容流统一套上停止生成控制 + 会话历史 + 结束 STOP 事件
     */
    @Override
    public Flux<ChatEventVO> processStream(String question, String sessionId, String userId, String routeHint) {
        return processStream(question, sessionId, userId, routeHint, null);
    }

    /**
     * 处理流式请求（携带意图提示 + 业务上下文）
     */
    @Override
    public Flux<ChatEventVO> processStream(String question, String sessionId, String userId, String routeHint, java.util.Map<String, Object> context) {
        // 每次请求生成独立 requestId，作为 ToolResultHolder 的 key，避免同 session 并发串数据
        String requestId = UUID.randomUUID().toString().replace("-", "");

        // 提前把 requestId 挂到 ThreadLocal：供 buildContentFlux 在「同步阶段」发起 function-calling 请求、
        // 组装 toolContext 时读取；真正订阅后由 wrapWithToolResult 的 doFirst 重新设置同一 requestId。
        CURRENT_REQUEST_ID.set(requestId);
        Flux<ChatEventVO> content;
        try {
            content = buildContentFlux(question, sessionId, userId, routeHint, context);
        } catch (Exception e) {
            CURRENT_REQUEST_ID.remove();
            throw e;
        }
        if (sessionId == null || sessionId.isEmpty()) {
            return wrapWithToolResult(requestId, content);
        }

        // 已输出内容缓存器：停止生成时保存半截回答，结束时用于 AI 提炼标题（StringBuffer 线程安全）
        StringBuffer outputBuilder = new StringBuffer();
        return wrapWithToolResult(requestId, content
                .doFirst(() -> GENERATE_STATUS.put(sessionId, true))   // 1. 开始时标记
                .doOnError(e -> GENERATE_STATUS.remove(sessionId))     // 2. 出错时清理标记
                .doOnComplete(() -> GENERATE_STATUS.remove(sessionId)) // 3. 正常完成时清理标记
                .doOnNext(ev -> {                                      // 收集 DATA 事件文字（结构化 PARAM 不进历史）
                    if (ev != null && ev.getEventType() == ChatEventTypeEnum.DATA.getValue()
                            && ev.getEventData() instanceof String text) {
                        outputBuilder.append(text);
                    }
                })
                .doOnCancel(() -> saveStopHistoryRecord(sessionId, outputBuilder.toString())) // 取消时保存
                .takeWhile(ev -> GENERATE_STATUS.getOrDefault(sessionId, false)) // 4. 关键开关
                .doFinally(signalType -> updateSession(bizType(), sessionId, userId, question, outputBuilder.toString())));
    }

    /**
     * 统一包装（参照《SpringAI》笔记 tjxt 的 ToolResultHolder 提取机制）：
     * 1. 订阅时把 requestId 挂到 ThreadLocal，供工具/技能把结构化结果存入 ToolResultHolder；
     * 2. 流结束后用 requestId 提取结构化结果，转 PARAM 事件下发，随后接 STOP 结束事件；
     * 3. finally 清理 ThreadLocal 与 ToolResultHolder，防止内存泄漏。
     */
    private Flux<ChatEventVO> wrapWithToolResult(String requestId, Flux<ChatEventVO> content) {
        return content
                .doFirst(() -> CURRENT_REQUEST_ID.set(requestId))
                .concatWith(Flux.defer(() -> {
                    Map<String, Object> toolResult = ToolResultHolder.get(requestId);
                    if (toolResult != null && !toolResult.isEmpty()) {
                        ToolResultHolder.remove(requestId); // 取出即清除，防止泄漏
                        ChatEventVO paramEvent = ChatEventVO.builder()
                                .eventType(ChatEventTypeEnum.PARAM.getValue())
                                .eventData(toolResult)
                                .build();
                        return Flux.just(paramEvent, STOP_EVENT);
                    }
                    return Flux.just(STOP_EVENT);
                }))
                .doFinally(signalType -> {
                    CURRENT_REQUEST_ID.remove();
                    ToolResultHolder.remove(requestId);
                });
    }

    /**
     * 发起真正的 function-calling 流：挂工具 + 传 toolContext(requestId/userId/role)，由 LLM 自主规划调用次序。
     * 工具方法把结构化结果写入 {@link ToolResultHolder}，流结束后由 {@link #wrapWithToolResult} 提取转 PARAM 卡片。
     *
     * @param systemMessage 动态系统人设（患者档案/病历/RAG 等），子类构建
     * @param tools         本智能体挂载的工具 bean（可变参数），为空则仅用 client 默认工具
     */
    protected Flux<ChatEventVO> functionCallingFlux(String question, String sessionId, String userId,
                                                    String role, String systemMessage, Object... tools) {
        return functionCallingFlux(question, sessionId, userId, role, systemMessage, null, tools);
    }

    /**
     * 发起真正的 function-calling 流（支持多模态图片附件）：
     * media 非空时，用户消息 = 文本 + 图片（视觉识别），否则纯文本。
     */
    protected Flux<ChatEventVO> functionCallingFlux(String question, String sessionId, String userId,
                                                    String role, String systemMessage, List<Media> media, Object... tools) {
        if (aiModelConfigService == null) {
            return Flux.error(new IllegalStateException("AI 模型服务未就绪"));
        }
        ChatClient client = aiModelConfigService.getBareChatClient();
        if (client == null) {
            return Flux.error(new IllegalStateException("AI 模型服务未就绪（可能未配置或离线）"));
        }
        String requestId = currentRequestId();
        Map<String, Object> toolCtx = buildToolContext(sessionId, requestId, userId, role);

        ChatClient.ChatClientRequestSpec spec = client.prompt()
                .system(systemMessage != null ? systemMessage : "");
        // 多模态：图片随用户消息一起送入大模型（OpenAI 兼容 image_url content），并切换视觉模型
        if (media != null && !media.isEmpty()) {
            spec = spec.user(u -> u.text(question).media(media.toArray(new Media[0])));
            try {
                String visionModel = aiModelConfigService.getVisionModel();
                if (visionModel != null && !visionModel.isBlank()) {
                    spec = spec.options(OpenAiChatOptions.builder().model(visionModel).build());
                }
            } catch (Exception ignored) {
            }
        } else {
            spec = spec.user(question);
        }
        spec = spec.toolContext(toolCtx);
        if (tools != null && tools.length > 0) {
            spec = spec.tools(tools);
        }
        if (sessionId != null && !sessionId.isEmpty()) {
            spec = spec.advisors(a -> a.param("chat_memory_conversation_id", sessionId));
        }
        return spec.stream()
                .content()
                .map(text -> ChatEventVO.builder()
                        .eventType(ChatEventTypeEnum.DATA.getValue())
                        .eventData(text)
                        .build())
                .onErrorResume(e -> {
                    System.err.println("[function-calling] LLM 流式调用异常: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    return Flux.just(ChatEventVO.builder()
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .eventData("\n\n⚠️ AI 服务调用异常，请稍后重试。")
                            .build());
                });
    }

    /**
     * 组装工具上下文：requestId 用于关联 ToolResultHolder 卡片，userId/role 供工具内部 RBAC 权限校验
     */
    protected Map<String, Object> buildToolContext(String sessionId, String requestId, String userId, String role) {
        Map<String, Object> ctx = new HashMap<>();
        if (requestId != null) ctx.put(AgentConstant.REQUEST_ID, requestId);
        if (sessionId != null) ctx.put(AgentConstant.SESSION_ID, sessionId);
        if (userId != null) ctx.put(AgentConstant.USER_ID, userId);
        if (role != null) ctx.put(AgentConstant.ROLE, role);
        return ctx;
    }

    /**
     * 非流式处理：默认返回空，路由智能体（RouteAgent）覆盖此方法做意图判断
     */
    @Override
    public String process(String question, String sessionId, String userId) {
        return null;
    }

    /**
     * 停止指定会话的处理
     */
    @Override
    public void stop(String sessionId) {
        GENERATE_STATUS.remove(sessionId);
    }

    /** 停止时把已输出的半截回答存入会话记忆（笔记"解决bug"章节：SpringAI 不记录中断流，需自己记录） */
    private void saveStopHistoryRecord(String conversationId, String content) {
        try {
            if (chatMemory != null && content != null && !content.isEmpty()) {
                chatMemory.add(conversationId, new AssistantMessage(content));
            }
        } catch (Exception ignored) {
        }
    }

    /** 会话历史：异步建会话记录 + AI 提炼标题 */
    private void updateSession(String bizType, String sessionId, String userId, String question, String answer) {
        if (chatSessionService == null) return;
        try {
            chatSessionService.update(bizType, sessionId, userId, "USER:" + question + "\nASSISTANT:" + answer);
        } catch (Exception ignored) {
        }
    }
}
