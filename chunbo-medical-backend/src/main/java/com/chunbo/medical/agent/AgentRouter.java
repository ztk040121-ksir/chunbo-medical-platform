package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import com.chunbo.medical.vo.ChatEventVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 智能体路由框架（参照《SpringAI》多智能体协作标准实现）
 * 100% 由路由智能体（RouteAgent）通过大模型（LLM + ChatMemory会话记忆上下文）精准判别用户自然语言意图，
 * 再通过 findAgentByType 找到对应业务智能体，委派其 processStream 进行流式推理与工具闭环。
 * 彻底废除硬编码正则匹配与假会话延续。
 */
@Component
public class AgentRouter {

    private static final Logger log = LoggerFactory.getLogger(AgentRouter.class);

    @Autowired
    private List<Agent> agents;

    /**
     * 路由：RouteAgent 判意图 → 业务智能体 processStream；意图未命中或路由失败时回退到默认业务智能体
     *
     * @param routeAgent   路由智能体
     * @param defaultAgent 默认兜底业务智能体
     */
    public Flux<ChatEventVO> route(Agent routeAgent, Agent defaultAgent,
                                   String question, String sessionId, String userId) {
        return route(routeAgent, defaultAgent, question, sessionId, userId, null);
    }

    /**
     * 路由（携带业务上下文）：100% 由 RouteAgent 进行 LLM 意图识别，并将 context 透传给业务智能体
     */
    public Flux<ChatEventVO> route(Agent routeAgent, Agent defaultAgent,
                                   String question, String sessionId, String userId, Map<String, Object> context) {
        String intent = null;
        Agent target = null;

        // 1. 100% 由路由智能体（基于 LLM + 会话历史记忆上下文）进行意图深度理解与分流
        if (routeAgent != null) {
            try {
                intent = routeAgent.process(question, sessionId, userId);
                AgentTypeEnum type = AgentTypeEnum.agentNameOf(intent);
                if (type != null && !type.getAgentName().endsWith("_ROUTE")) {
                    target = findAgentByType(type);
                }
            } catch (Exception e) {
                log.warn("[AgentRouter] 路由智能体处理异常: {}", e.getMessage());
            }
        }

        // 2. 兜底回退
        if (target == null) {
            target = defaultAgent;
            log.info("[AgentRouter] 意图未命中或路由回退，采用默认智能体: {}", defaultAgent != null ? defaultAgent.getAgentType() : "null");
        }

        if (target == null) {
            return Flux.empty();
        }

        return target.processStream(question, sessionId, userId, intent, context);
    }

    /** 从 Spring 容器按类型查找业务智能体 */
    private Agent findAgentByType(AgentTypeEnum type) {
        if (type == null) return null;
        for (Agent agent : agents) {
            if (agent.getAgentType() == type) {
                return agent;
            }
        }
        return null;
    }
}
