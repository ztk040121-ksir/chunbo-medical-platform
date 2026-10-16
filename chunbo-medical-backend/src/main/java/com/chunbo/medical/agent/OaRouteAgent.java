package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import com.chunbo.medical.prompt.PromptConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 中台路由智能体（参照《SpringAI》多智能体协作标准实现）
 * 由独立的非流式 LLM 调用精准分析中台指令意图，返回业务智能体类型名称
 */
@Component
public class OaRouteAgent extends RouteAgent {

    @Autowired(required = false)
    private com.chunbo.medical.agent.react.ReActEngine reActEngine;

    @Autowired
    private PromptConfig promptConfig;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.OA_ROUTE;
    }

    @Override
    public String systemMessage() {
        return promptConfig.getOaRoutePrompt();
    }

    @Override
    public String process(String question, String sessionId, String userId) {
        String q = question == null ? "" : question.trim();
        // 复合多步链式任务（含条件分支、多个动作组合），交由 ReAct 引擎全局统筹
        if (reActEngine != null && reActEngine.isCompositeTask(q)) {
            return "OA_GENERAL";
        }
        return super.process(question, sessionId, userId);
    }
}
