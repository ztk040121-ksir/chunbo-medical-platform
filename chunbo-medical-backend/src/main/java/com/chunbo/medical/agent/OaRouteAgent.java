package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 中台路由智能体（参照《SpringAI》笔记多智能体协作标准实现）
 * 用一次 LLM 调用分析用户指令意图，返回业务智能体类型名称
 */
@Component
public class OaRouteAgent extends RouteAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.OA_ROUTE;
    }

    @Override
    public String systemMessage() {
        return "你是春播云管理系统的中台路由助手，负责分析用户指令意图。\n"
                + "请只输出以下类型之一（不要任何解释或多余文字）：\n"
                + "OA_SALARY（工资/薪酬/提成/工资表）\n"
                + "OA_ORDER（商城订单/发货/出库/物流速递）\n"
                + "OA_INVENTORY（药房库存/预警/补货）\n"
                + "OA_ANALYTICS（营业/营收/门诊大盘/统计）\n"
                + "OA_APPROVAL（请假/审批/考勤/代班）\n"
                + "OA_PLASTER（贴敷/理疗/穴位/外治）\n"
                + "OA_GENERAL（其它问题）";
    }
}
