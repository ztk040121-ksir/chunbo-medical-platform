package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 商城·用药禁忌审查智能体（MALL_GUARD）
 * 处理「用药禁忌/配伍/能否一起吃」类意图
 */
@Component
public class MallGuardAgent extends MallBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MALL_GUARD;
    }

    @Override
    protected String skillHint() {
        return "MALL_GUARD";
    }
}
