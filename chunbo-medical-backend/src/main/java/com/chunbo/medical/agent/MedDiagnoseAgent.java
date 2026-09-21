package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 问诊·辨证开方智能体（MED_DIAGNOSE）
 * 处理「辨证开方/拟定治疗方案/推荐处方」类意图
 */
@Component
public class MedDiagnoseAgent extends MedBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MED_DIAGNOSE;
    }

    @Override
    protected String skillHint() {
        return "MED_DIAGNOSE";
    }
}
