package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 中台·薪资绩效智能体（OA_SALARY）
 * 处理「工资/薪酬/提成/工资表」类指令（含 RBAC 权限隔离）
 */
@Component
public class OaSalaryAgent extends OaBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.OA_SALARY;
    }

    @Override
    protected String skillHint() {
        return "OA_SALARY";
    }
}
