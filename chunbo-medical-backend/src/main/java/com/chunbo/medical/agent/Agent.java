package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import com.chunbo.medical.vo.ChatEventVO;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI 代理接口（参照《SpringAI》笔记多智能体协作标准实现）
 * 定义处理聊天事件和会话的核心能力，业务智能体只需实现差异点，通用逻辑由 AbstractAgent 封装
 */
public interface Agent {

    /** 空参数预定义数组 */
    Object[] EMPTY_OBJECTS = new Object[0];

    /**
     * 处理流式请求（业务智能体）：返回标准事件流（文字 DATA / 卡片 PARAM / 结束 STOP）
     */
    Flux<ChatEventVO> processStream(String question, String sessionId, String userId);

    /**
     * 处理流式请求（携带路由智能体判出的意图提示）
     * 默认忽略 hint 走三参版本，业务智能体可覆盖以利用语义路由结果
     */
    default Flux<ChatEventVO> processStream(String question, String sessionId, String userId, String routeHint) {
        return processStream(question, sessionId, userId);
    }

    /**
     * 处理流式请求（携带意图提示 + 业务上下文，如 patientId/emrContext）
     * 默认忽略 context，需要的业务智能体覆盖
     */
    default Flux<ChatEventVO> processStream(String question, String sessionId, String userId, String routeHint, Map<String, Object> context) {
        return processStream(question, sessionId, userId, routeHint);
    }

    /**
     * 处理标准请求（非流式，路由智能体用）：返回意图类型名称字符串
     */
    String process(String question, String sessionId, String userId);

    /**
     * 获取智能体类型标识
     */
    AgentTypeEnum getAgentType();

    /**
     * 停止指定会话的处理
     */
    void stop(String sessionId);

    /**
     * 系统提示信息模板，默认为空字符串，子类覆盖返回自定义人设
     */
    default String systemMessage() {
        return "";
    }

    /**
     * 工具列表，默认空数组，子类按需覆盖
     */
    default Object[] tools() {
        return EMPTY_OBJECTS;
    }

    /**
     * 工具上下文（用于把 requestId 传给工具，卡片结果通过 ToolResultHolder 关联），默认空
     */
    default Map<String, Object> toolContext(String sessionId, String requestId) {
        return Map.of();
    }

    /**
     * 业务类型（medical 云诊所 / oa 中台 / mall 商城），用于会话历史分组
     */
    default String bizType() {
        return "general";
    }
}
