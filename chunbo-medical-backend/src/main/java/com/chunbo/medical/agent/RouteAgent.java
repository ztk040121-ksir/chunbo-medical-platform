package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import com.chunbo.medical.vo.ChatEventVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 路由智能体基类（参照《SpringAI》多智能体协作标准实现）
 * 由独立的非流式 LLM 调用深入分析用户意图，返回业务智能体类型名称（如 OA_SALARY、MED_DIAGNOSE、MALL_RECOMMEND）。
 * 各业务域继承本类，仅需 override systemMessage()（提供严谨的意图分类枚举与规则定义）与 getAgentType()。
 */
public abstract class RouteAgent extends AbstractAgent {

    private static final Logger log = LoggerFactory.getLogger(RouteAgent.class);

    @Autowired(required = false)
    protected ChatModel chatModel;

    private static final Pattern ENUM_PATTERN = Pattern.compile("[A-Z0-9_]{3,}");

    /**
     * 意图分析：100% 由路由智能体基于 LLM + 会话历史分析用户自然语言，返回目标智能体类型名称
     */
    @Override
    public String process(String question, String sessionId, String userId) {
        if (question == null || question.trim().isEmpty()) {
            return null;
        }
        try {
            ChatClient client = null;
            if (aiModelConfigService != null) {
                client = aiModelConfigService.getPreConsultChatClient();
            }
            if (client == null && chatModel != null) {
                client = ChatClient.builder(chatModel).build();
            }
            if (client == null) {
                log.warn("[RouteAgent] 无可用的 ChatClient/ChatModel，无法执行意图路由");
                return null;
            }

            // 结合 ChatMemory 注入近期会话历史，赋予路由大模型多轮指代与消歧理解能力
            String userPrompt = buildPromptWithHistory(question.trim(), sessionId);

            long start = System.currentTimeMillis();
            String rawContent = client.prompt()
                    .system(systemMessage())
                    .user(userPrompt)
                    .options(OpenAiChatOptions.builder().temperature(0.0).build())
                    .call()
                    .content();

            long elapsed = System.currentTimeMillis() - start;
            String intent = extractIntent(rawContent);
            log.info("[RouteAgent] 智能体意图识别完成 -> 输入: \"{}\", 模型原始输出: \"{}\", 解析目标: {}, 耗时: {}ms",
                    question, rawContent != null ? rawContent.trim() : "null", intent, elapsed);
            return intent;
        } catch (Exception e) {
            log.warn("[RouteAgent] 意图分析调用异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 提取 ChatMemory 最近几轮对话作为提示词上下文，供路由模型准确判断「发第二个」「9月份」「确定」等指代消息
     */
    private String buildPromptWithHistory(String question, String sessionId) {
        if (chatMemory == null || sessionId == null || sessionId.isBlank()) {
            return question;
        }
        try {
            List<org.springframework.ai.chat.messages.Message> history = chatMemory.get(sessionId);
            if (history == null || history.isEmpty()) {
                return question;
            }
            StringBuilder histSb = new StringBuilder();
            histSb.append("【此前会话历史上下文（供多轮指代与上下文消歧参考）】\n");
            int startIdx = Math.max(0, history.size() - 6);
            int count = 0;
            for (int i = startIdx; i < history.size(); i++) {
                org.springframework.ai.chat.messages.Message msg = history.get(i);
                if (msg == null) continue;
                String role = msg.getMessageType() == org.springframework.ai.chat.messages.MessageType.USER ? "用户" : "助手";
                String text = msg.getText();
                if (text != null && !text.isBlank()) {
                    if (text.length() > 150) text = text.substring(0, 150) + "...";
                    histSb.append(role).append("：").append(text.trim()).append("\n");
                    count++;
                }
            }
            if (count == 0) return question;
            histSb.append("\n【当前用户最新输入】\n").append(question);
            return histSb.toString();
        } catch (Exception e) {
            log.warn("[RouteAgent] 读取会话记忆异常: {}", e.getMessage());
            return question;
        }
    }

    /**
     * 严谨解析并映射为系统内合法的 AgentTypeEnum 枚举名称
     * 容错 markdown 代码块、标点、前缀/后缀解释说明等
     */
    protected String extractIntent(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) return null;
        String clean = rawContent.replaceAll("`", "").trim();

        // 1. 精确匹配
        AgentTypeEnum direct = AgentTypeEnum.agentNameOf(clean);
        if (direct != null && !direct.getAgentName().endsWith("_ROUTE")) {
            return direct.getAgentName();
        }

        // 2. 正则提取大写候选词匹配
        Matcher matcher = ENUM_PATTERN.matcher(clean);
        while (matcher.find()) {
            String candidate = matcher.group();
            AgentTypeEnum candidateEnum = AgentTypeEnum.agentNameOf(candidate);
            if (candidateEnum != null && !candidateEnum.getAgentName().endsWith("_ROUTE")) {
                return candidateEnum.getAgentName();
            }
        }

        // 3. 包含匹配兜底
        for (AgentTypeEnum e : AgentTypeEnum.values()) {
            if (!e.getAgentName().endsWith("_ROUTE") && clean.contains(e.getAgentName())) {
                return e.getAgentName();
            }
        }

        return null;
    }

    /**
     * 路由智能体不参与流式内容输出，返回空流
     */
    @Override
    protected Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId) {
        return Flux.empty();
    }
}
