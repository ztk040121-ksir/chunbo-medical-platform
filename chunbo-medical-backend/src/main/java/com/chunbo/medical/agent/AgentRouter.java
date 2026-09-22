package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体路由框架（参照《SpringAI》笔记多智能体协作标准实现）
 * 先由路由智能体（RouteAgent）用一次 LLM 调用判断用户意图，
 * 再通过 findAgentByType 找到对应业务智能体，委派其 processStream 流式处理。
 * 补全了参考源码 AgentServiceImpl 中「路由出意图后 return null」的半成品缺陷。
 *
 * 会话意图延续：用户发出「发第二个」「全部发货」这类指代性短消息时，
 * 单轮路由 LLM 无法从消息本身判断业务域（会掉进规则技能兜底），
 * 此时沿用该会话上一轮的路由意图，保证多轮操作（如多订单确认）不中断。
 */
@Component
public class AgentRouter {

    @Autowired
    private List<Agent> agents;

    /** 会话最近一次路由意图（指代性短消息沿用），key=sessionId */
    private final Map<String, String> lastIntentBySession = new ConcurrentHashMap<>();

    /** 指代性/延续性短消息识别：指代序号、确认类，以及「补充信息类」（如按提示补账号/密码/手机号），且不含明确业务关键词 */
    private boolean isFollowUp(String question) {
        if (question == null) return false;
        String q = question.trim();
        if (q.isEmpty() || q.length() > 40) return false;
        // 明确含业务关键词时正常路由，不沿用
        if (q.contains("工资") || q.contains("工资表") || q.contains("订单号") || q.contains("库存")
                || q.contains("审批") || q.contains("贴敷") || q.contains("营收")) {
            return false;
        }
        // 补充信息类短消息（多轮引导填单场景）：含手机号/账号/密码/姓名/地址等字段词或较长数字串
        // 如「14539326819，用这个手机号」「登录账号：77，密码：123456」——单轮路由判不出意图，应沿用上一轮
        if (q.matches(".*\\d{4,}.*") || q.contains("手机号") || q.contains("电话") || q.contains("账号")
                || q.contains("密码") || q.contains("姓名") || q.contains("昵称") || q.contains("地址")) {
            return true;
        }
        return q.matches(".*(第[一二三四五六七八九十百\\d]+\\s*[个条单笔号]?|全部(发货|送达|发放|确认|出库)?|都发|依次|按顺序|上一个|刚(才|刚)?那个|这个|就绪|确定|好|可以|是的?).*");
    }

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
        // 指代性短消息沿用上一轮意图（如多订单确认的「发第二个」「全部发货」）
        String cached = sessionId != null ? lastIntentBySession.get(sessionId) : null;
        if (cached != null && isFollowUp(question)) {
            intent = cached;
            target = findAgentByType(AgentTypeEnum.agentNameOf(intent));
        }
        if (target == null) {
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
        }
        if (sessionId != null && intent != null && !intent.endsWith("_ROUTE")) {
            lastIntentBySession.put(sessionId, intent);
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
        // 指代性短消息沿用上一轮意图（如多订单确认的「发第二个」「全部发货」）
        String cached = sessionId != null ? lastIntentBySession.get(sessionId) : null;
        if (cached != null && isFollowUp(question)) {
            intent = cached;
            target = findAgentByType(AgentTypeEnum.agentNameOf(intent));
        }
        if (target == null) {
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
        }
        if (sessionId != null && intent != null && !intent.endsWith("_ROUTE")) {
            lastIntentBySession.put(sessionId, intent);
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
