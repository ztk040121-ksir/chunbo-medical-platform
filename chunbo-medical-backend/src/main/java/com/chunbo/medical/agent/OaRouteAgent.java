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
                + "OA_ORDER（商城订单/发货/出库/物流速递/确认送达）\n"
                + "OA_PRODUCT（商城商品的下架/上架/调价/改价/入库补货/商品表格新增/进销存）\n"
                + "OA_MALL_USER（商城注册用户管理：注册/新增商城用户或账户、查看某用户的订单、用户总数/活跃/冻结统计、新人购药金/体验金）\n"
                + "OA_INVENTORY（医院内部药房的药品库存/预警台账/门诊用药补货）\n"
                + "OA_ANALYTICS（营业/营收/门诊大盘/统计）\n"
                + "OA_APPROVAL（请假/审批/考勤/代班/请假名单/批准请假/驳回请假/删除请假单）\n"
                + "OA_PLASTER（贴敷/理疗/穴位/外治）\n"
                + "OA_GENERAL（其它问题）\n"
                + "【消歧规则】商城商品（如口罩/布洛芬商品档/下架/调价到X元/补货N件）→ OA_PRODUCT；"
                + "医院药房门诊用药的库存与预警台账 → OA_INVENTORY。";
    }

    /**
     * 确定性意图前置规则：商品操作类强模式指令直接命中 OA_PRODUCT，不再交给 LLM 猜
     * （「补货」「入库」在 OA_PRODUCT 与 OA_INVENTORY 两个意图间有歧义，LLM 单轮判断易串）
     */
    @Override
    public String process(String question, String sessionId, String userId) {
        String q = question == null ? "" : question.trim();
        // 排除明确指向医院药房/门诊药品的语句
        boolean pharmacy = q.contains("药房") || q.contains("门诊药品") || q.contains("药品库存");
        boolean productOp = (q.contains("下架") || q.contains("上架"))
                || (q.contains("调价") || q.contains("改价") || q.contains("价格调"))
                || ((q.contains("补货") || q.contains("入库")) && !pharmacy && q.length() <= 30);
        if (productOp && !pharmacy) {
            return "OA_PRODUCT";
        }
        // 商城注册用户管理类强模式：注册/新增商城账户、按用户查订单、用户统计
        boolean mallUserOp = ((q.contains("注册") || q.contains("新增用户") || q.contains("新建用户"))
                && (q.contains("商城") || q.contains("账户") || q.contains("账号") || q.contains("用户")))
                || ((q.contains("用户") || q.contains("账户")) && (q.contains("订单") || q.contains("总数")
                        || q.contains("多少") || q.contains("统计") || q.contains("冻结") || q.contains("活跃")
                        || q.contains("体验金") || q.contains("购药金")));
        if (mallUserOp) {
            return "OA_MALL_USER";
        }
        // 请假审批操作类强模式：名单/批准/驳回/删除 + 请假 → OA_APPROVAL
        if (q.contains("请假") && (q.contains("名单") || q.contains("批准") || q.contains("审批")
                || q.contains("驳回") || q.contains("删除"))) {
            return "OA_APPROVAL";
        }
        return super.process(question, sessionId, userId);
    }
}
