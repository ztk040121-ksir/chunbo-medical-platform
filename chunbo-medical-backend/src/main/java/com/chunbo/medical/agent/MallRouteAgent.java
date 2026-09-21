package com.chunbo.medical.agent;

import com.chunbo.medical.enums.AgentTypeEnum;
import org.springframework.stereotype.Component;

/**
 * 商城路由智能体（参照《SpringAI》笔记多智能体协作标准实现）
 * 用一次 LLM 调用分析用户意图，返回业务智能体类型名称
 */
@Component
public class MallRouteAgent extends RouteAgent {

    @Override
    public AgentTypeEnum getAgentType() {
        return AgentTypeEnum.MALL_ROUTE;
    }

    @Override
    public String systemMessage() {
        return "你是春播便民药房的智能路由助手，负责分析用户意图。\n"
                + "请只输出以下类型之一（不要任何解释或多余文字）：\n"
                + "MALL_RECOMMEND（想找/推荐/挑选/去掉或更换某药品、描述症状求药）\n"
                + "MALL_CONSULT（咨询感冒/咳嗽/胃肠/外伤等用药）\n"
                + "MALL_ORDER（明确查询自己已下订单的进度/物流/快递单号/发货状态）\n"
                + "MALL_GUARD（问用药禁忌/配伍/能否一起吃）\n"
                + "MALL_SHIPPING（问运费/包邮/配送时效）\n"
                + "MALL_GENERAL（其它问题）\n"
                + "注意：「给我去掉XX药」「不要XX」「换个药」都是对推荐清单的修改，属于 MALL_RECOMMEND，绝不是订单查询。";
    }
}
