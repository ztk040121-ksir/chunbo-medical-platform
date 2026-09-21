package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 问诊·用药知识智能体（MED_KNOWLEDGE，兜底）
 * 处理「用药知识/诊疗规范/医学常识咨询」及其它未细分问题
 */
@Component
public class MedKnowledgeAgent extends MedBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MED_KNOWLEDGE;
    }

    @Override
    protected String skillHint() {
        return "MED_KNOWLEDGE";
    }
}
