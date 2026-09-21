package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 商城·配送政策智能体（MALL_SHIPPING）
 * 处理「运费/包邮/配送时效」类意图
 */
@Component
public class MallShippingAgent extends MallBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MALL_SHIPPING;
    }

    @Override
    protected String skillHint() {
        return "MALL_SHIPPING";
    }
}
