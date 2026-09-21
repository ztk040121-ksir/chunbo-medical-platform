package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 商城·家庭健康导购智能体（MALL_GENERAL，兜底）
 * 处理其它未细分的问题
 */
@Component
public class MallGeneralAgent extends MallBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MALL_GENERAL;
    }

    @Override
    protected String skillHint() {
        return "MALL_GENERAL";
    }
}
