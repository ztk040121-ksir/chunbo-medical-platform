package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 中台·药房预警智能体（OA_INVENTORY）
 * 处理「药房库存/预警/补货」类指令
 */
@Component
public class OaInventoryAgent extends OaBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.OA_INVENTORY;
    }

    @Override
    protected String skillHint() {
        return "OA_INVENTORY";
    }
}
