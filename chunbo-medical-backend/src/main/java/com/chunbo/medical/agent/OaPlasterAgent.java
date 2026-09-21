package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 中台·贴敷理疗智能体（OA_PLASTER）
 * 处理「贴敷/理疗/穴位/外治」类指令
 */
@Component
public class OaPlasterAgent extends OaBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.OA_PLASTER;
    }

    @Override
    protected String skillHint() {
        return "OA_PLASTER";
    }
}
