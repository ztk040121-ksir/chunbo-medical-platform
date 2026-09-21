package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 中台·运营大盘智能体（OA_ANALYTICS）
 * 处理「营业/营收/门诊大盘/统计」类指令
 */
@Component
public class OaAnalyticsAgent extends OaBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.OA_ANALYTICS;
    }

    @Override
    protected String skillHint() {
        return "OA_ANALYTICS";
    }
}
