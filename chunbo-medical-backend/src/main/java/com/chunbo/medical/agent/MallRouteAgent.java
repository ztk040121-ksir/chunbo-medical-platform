package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import com.chunbo.medical.prompt.PromptConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 商城路由智能体（参照《SpringAI》多智能体协作标准实现）
 * 由独立的非流式 LLM 调用精准分析商城用户意图，返回业务智能体类型名称
 */
@Component
public class MallRouteAgent extends RouteAgent {

    @Autowired
    private PromptConfig promptConfig;

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MALL_ROUTE;
    }

    @Override
    public String systemMessage() {
        return promptConfig.getMallRoutePrompt();
    }
}
