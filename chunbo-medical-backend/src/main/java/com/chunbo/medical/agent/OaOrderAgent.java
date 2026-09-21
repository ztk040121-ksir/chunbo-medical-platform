package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 中台·商城履约智能体（OA_ORDER）
 * 处理「商城订单/发货/出库/物流速递」类指令
 */
@Component
public class OaOrderAgent extends OaBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.OA_ORDER;
    }

    @Override
    protected String skillHint() {
        return "OA_ORDER";
    }
}
