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
        return "你是春播万象全科医疗与便民药房的智能问诊路由助手，负责分析用户输入的真实意图。\n"
                + "请只输出以下类型之一（不要任何解释或多余文字）：\n"
                + "MED_MALL（商城购药/下单买药/常备药直购/药品选购/药品对症购买咨询）\n"
                + "MED_DIAGNOSE（辨证开方/拟定治疗方案/推荐处方/症状诊断/就医科室分诊导医）\n"
                + "MED_KNOWLEDGE（日常疾病常识/合理用药规范/药物禁忌与医学科普）\n"
                + "MED_STOCK（药房管理员/医生专用的药房库存进销存台账查询）";
    }

    @Override
    public String process(String question, String sessionId, String userId) {
        String q = question != null ? question.trim() : "";
        // 规则先行：用户表达明确下单、购药、选药或提到常见商城药品意图时，直接路由到 MED_MALL
        if (q.contains("下单") || q.contains("买药") || q.contains("想买") || q.contains("帮我买")
                || q.contains("购买") || q.contains("去买") || q.contains("商城") || q.contains("连花清瘟")
                || (q.contains("买") && (q.contains("药") || q.contains("盒") || q.contains("瓶") || q.contains("胶囊") || q.contains("贴")))) {
            return "MED_MALL";
        }
        String res = super.process(question, sessionId, userId);
        if (res != null && (res.contains("MED_MALL") || res.contains("MED_DIAGNOSE") || res.contains("MED_KNOWLEDGE") || res.contains("MED_STOCK"))) {
            return res.trim();
        }
        return "MED_MALL";
    }
}
