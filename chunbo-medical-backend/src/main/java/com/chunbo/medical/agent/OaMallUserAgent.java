package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 中台·商城用户管理智能体（OA_MALL_USER）
 * 处理「商城用户注册引导 / 查询某用户订单 / 用户统计（总数/活跃/冻结/新人购药金）」类指令
 */
@Component
public class OaMallUserAgent extends OaBaseAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.OA_MALL_USER;
    }

    @Override
    protected String skillHint() {
        return "OA_MALL_USER";
    }
}
