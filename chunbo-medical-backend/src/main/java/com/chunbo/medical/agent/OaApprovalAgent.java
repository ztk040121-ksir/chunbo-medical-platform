package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 中台·OA审批智能体（OA_APPROVAL）
 * 处理「请假/审批/考勤/代班」类指令（交互式收集信息后创建审批单）
 */
@Component
public class OaApprovalAgent extends OaBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.OA_APPROVAL;
    }

    @Override
    protected String skillHint() {
        return "OA_APPROVAL";
    }
}
