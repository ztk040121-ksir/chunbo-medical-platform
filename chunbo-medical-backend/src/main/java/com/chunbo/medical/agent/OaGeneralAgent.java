package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 中台·通用智能体（OA_GENERAL，兜底）
 * 处理其它未细分的中台问题，走 LLM 兜底
 */
@Component
public class OaGeneralAgent extends OaBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.OA_GENERAL;
    }

    @Override
    protected String skillHint() {
        return "OA_GENERAL";
    }
}
