package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 问诊路由智能体（参照《SpringAI》笔记多智能体协作标准实现）
 * 用一次 LLM 调用分析医生输入意图，返回业务智能体类型名称
 */
@Component
public class MedRouteAgent extends RouteAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MED_ROUTE;
    }

    @Override
    public String systemMessage() {
        return "你是春播云诊所的问诊路由助手，负责分析医生输入的意图。\n"
                + "请只输出以下类型之一（不要任何解释或多余文字）：\n"
                + "MED_STOCK（查药房库存/药品台账/进销存/缺药）\n"
                + "MED_DIAGNOSE（辨证开方/拟定治疗方案/推荐处方）\n"
                + "MED_KNOWLEDGE（用药知识/诊疗规范/医学常识咨询）";
    }
}
