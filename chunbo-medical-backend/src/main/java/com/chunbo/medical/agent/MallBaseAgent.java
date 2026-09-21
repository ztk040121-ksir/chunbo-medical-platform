package com.chunbo.medical.agent;

import com.chunbo.medical.config.ToolResultHolder;
import com.chunbo.medical.enums.ChatEventTypeEnum;
import com.chunbo.medical.service.B2bMultiAgentService;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商城小药师业务智能体基类（参照《SpringAI》笔记 tjxt 多智能体标准实现）
 * 每个细分商城意图（推荐/咨询/订单/禁忌/配送/通用）一个独立 Agent 类，
 * 路由判出的意图由 AgentRouter 直接命中对应子类，真正实现多智能体分流（不再靠单个大类内部 if-else）。
 * 业务逻辑复用 B2bMultiAgentService 多智能体工作流，本基类负责把结果转成标准事件流。
 */
public abstract class MallBaseAgent extends AbstractAgent {

    @Autowired
    protected B2bMultiAgentService b2bMultiAgentService;

    @Override
    public String bizType() {
        return "mall";
    }

    /** 子类返回本智能体对应的固定路由意图（如 MALL_RECOMMEND / MALL_SHIPPING） */
    protected abstract String skillHint();

    @Override
    protected Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId) {
        return buildContentFlux(question, sessionId, userId, skillHint());
    }

    @Override
    protected Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId, String routeHint) {
        // userId 即商城手机号 phone；用本智能体的固定意图驱动技能分流
        Map<String, Object> result;
        try {
            result = b2bMultiAgentService.runMultiAgentWorkflow(question, "consumer", sessionId, userId, "", skillHint());
        } catch (Exception e) {
            result = Map.of("reply", "您好！遇到身体不适，建议多喝温开水清淡饮食。如需用药可参考右侧分类选品货架或随时再向我咨询。");
        }
        String reply = String.valueOf(result.getOrDefault("reply", ""));
        Object recs = result.get("recommendations");

        // 回复文本切片 → DATA 事件（打字机效果）
        List<ChatEventVO> events = new ArrayList<>();
        int chunkSize = 6;
        for (int i = 0; i < reply.length(); i += chunkSize) {
            events.add(ChatEventVO.builder()
                    .eventType(ChatEventTypeEnum.DATA.getValue())
                    .eventData(reply.substring(i, Math.min(i + chunkSize, reply.length())))
                    .build());
        }
        // 推荐商品卡片 → 写入 ToolResultHolder，流末尾由 AbstractAgent 统一提取转 PARAM 下发
        if (recs != null) {
            ToolResultHolder.put(AbstractAgent.currentRequestId(), "recommendations", recs);
        }
        return Flux.fromIterable(events).delayElements(Duration.ofMillis(25));
    }
}
