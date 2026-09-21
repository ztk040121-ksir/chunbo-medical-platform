package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 商城·订单物流智能体（MALL_ORDER）
 * 处理「查自己已下订单的进度/物流/快递单号/发货状态」类意图
 */
@Component
public class MallOrderAgent extends MallBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MALL_ORDER;
    }

    @Override
    protected String skillHint() {
        return "MALL_ORDER";
    }
}
