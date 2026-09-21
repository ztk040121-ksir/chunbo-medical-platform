package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 商城·用药咨询智能体（MALL_CONSULT）
 * 处理「感冒/咳嗽/胃肠/外伤等用药咨询」类意图
 */
@Component
public class MallConsultAgent extends MallBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MALL_CONSULT;
    }

    @Override
    protected String skillHint() {
        return "MALL_CONSULT";
    }
}
