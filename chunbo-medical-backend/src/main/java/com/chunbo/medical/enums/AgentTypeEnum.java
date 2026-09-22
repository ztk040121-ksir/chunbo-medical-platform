package com.chunbo.medical.enums;

/**
 * 智能体类型（参照《SpringAI》笔记多智能体协作标准实现）
 * 覆盖三个业务域：商城 / 问诊 / OA，各域一个 ROUTE 路由 + 若干业务智能体
 */
public enum AgentTypeEnum {
    // ============ 商城 ============
    MALL_ROUTE("MALL_ROUTE", "商城路由智能体"),
    MALL_RECOMMEND("MALL_RECOMMEND", "商品推荐智能体"),
    MALL_CONSULT("MALL_CONSULT", "用药咨询智能体"),
    MALL_ORDER("MALL_ORDER", "订单物流智能体"),
    MALL_GUARD("MALL_GUARD", "用药禁忌审查智能体"),
    MALL_SHIPPING("MALL_SHIPPING", "配送政策智能体"),
    MALL_GENERAL("MALL_GENERAL", "家庭健康导购智能体"),

    // ============ 问诊 ============
    MED_ROUTE("MED_ROUTE", "问诊路由智能体"),
    MED_DIAGNOSE("MED_DIAGNOSE", "辨证开方智能体"),
    MED_STOCK("MED_STOCK", "药房库存智能体"),
    MED_KNOWLEDGE("MED_KNOWLEDGE", "用药知识智能体"),

    // ============ OA ============
    OA_ROUTE("OA_ROUTE", "中台路由智能体"),
    OA_SALARY("OA_SALARY", "薪资绩效智能体"),
    OA_ORDER("OA_ORDER", "商城履约智能体"),
    OA_PRODUCT("OA_PRODUCT", "商品与进销存智能体"),
    OA_MALL_USER("OA_MALL_USER", "商城用户管理智能体"),
    OA_INVENTORY("OA_INVENTORY", "药房预警智能体"),
    OA_ANALYTICS("OA_ANALYTICS", "运营大盘智能体"),
    OA_APPROVAL("OA_APPROVAL", "OA审批智能体"),
    OA_PLASTER("OA_PLASTER", "贴敷理疗智能体"),
    OA_GENERAL("OA_GENERAL", "中台通用智能体");

    private final String agentName;
    private final String desc;

    AgentTypeEnum(String agentName, String desc) {
        this.agentName = agentName;
        this.desc = desc;
    }

    public String getAgentName() {
        return agentName;
    }

    public String getDesc() {
        return desc;
    }

    /** 通过智能体名称查找枚举（找不到返回 null） */
    public static AgentTypeEnum agentNameOf(String agentName) {
        if (agentName == null) return null;
        for (AgentTypeEnum e : values()) {
            if (e.agentName.equalsIgnoreCase(agentName.trim())) {
                return e;
            }
        }
        return null;
    }
}
