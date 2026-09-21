package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 智能体路由框架（参照《SpringAI》笔记多智能体协作标准实现）
 * 先由路由智能体（RouteAgent）用一次 LLM 调用判断用户意图，
 * 再通过 findAgentByType 找到对应业务智能体，委派其 processStream 流式处理。
 * 补全了参考源码 AgentServiceImpl 中「路由出意图后 return null」的半成品缺陷。
 */
@Component
public class AgentRouter {

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
        Agent target = null;
        String intent = null;
        try {
            intent = routeAgent.process(question, sessionId, userId);
            AgentTypeEnum type = AgentTypeEnum.agentNameOf(intent);
            if (type != null && !type.getAgentName().endsWith("_ROUTE")) {
                target = findAgentByType(type);
            }
        } catch (Exception ignored) {
            // 路由判断异常，走默认智能体
        }
        if (target == null) {
            target = defaultAgent;
        }
        // 把路由判出的意图作为提示传给业务智能体（如 MALL_SHIPPING 直接走配送政策技能）
        return target.processStream(question, sessionId, userId, intent);
    }

    /**
     * 路由（携带业务上下文）：同上，额外把 patientId/emrContext 等上下文透传给业务智能体
     */
    public Flux<ChatEventVO> route(Agent routeAgent, Agent defaultAgent,
                                   String question, String sessionId, String userId, java.util.Map<String, Object> context) {
        Agent target = null;
        String intent = null;
        try {
            intent = routeAgent.process(question, sessionId, userId);
            AgentTypeEnum type = AgentTypeEnum.agentNameOf(intent);
            if (type != null && !type.getAgentName().endsWith("_ROUTE")) {
                target = findAgentByType(type);
            }
        } catch (Exception ignored) {
        }
        if (target == null) {
            target = defaultAgent;
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
