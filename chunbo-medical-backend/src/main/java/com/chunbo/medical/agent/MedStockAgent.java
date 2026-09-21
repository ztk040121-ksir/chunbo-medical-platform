package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 问诊·药房库存智能体（MED_STOCK）
 * 处理「查药房库存/药品台账/进销存/缺药」类意图
 */
@Component
public class MedStockAgent extends MedBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MED_STOCK;
    }

    @Override
    protected String skillHint() {
        return "MED_STOCK";
    }
}
