package com.chunbo.medical.agent;

import com.chunbo.medical.config.ToolResultHolder;
import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.enums.ChatEventTypeEnum;
import com.chunbo.medical.service.B2bMultiAgentService;
import com.chunbo.medical.service.FileUploadService;
import com.chunbo.medical.tools.MallProductTools;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.ai.content.Media;
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

    @Autowired(required = false)
    protected FileUploadService fileUploadService;

    @Autowired(required = false)
    protected MallProductTools mallProductTools;

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
        return buildContentFlux(question, sessionId, userId, routeHint, null);
    }

    @Override
    protected Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId, String routeHint, Map<String, Object> context) {
        // 图片附件 → 视觉识别药品名 → function-calling 查询商城库存 → 生成下单卡片
        String attachmentId = context != null ? String.valueOf(context.getOrDefault(AgentConstant.ATTACHMENT_ID, "")) : "";
        if (attachmentId != null && !attachmentId.isBlank() && fileUploadService != null) {
            Media media = fileUploadService.toImageMedia(attachmentId);
            if (media != null && mallProductTools != null) {
                return productImageFlux(question, sessionId, userId, media);
            }
        }

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

    /** 药品图片识别：图片送入多模态大模型识别药名 → 自主调用 searchMallProduct 查询商城库存 → 生成下单卡片 */
    private Flux<ChatEventVO> productImageFlux(String question, String sessionId, String userId, Media media) {
        String role = "CONSUMER";
        String sys = "你是春播商城的小药师智能体。用户上传了一张药品图片，请按以下步骤处理：\n"
                + "1. 仔细观察图片，识别药品名称、品牌、规格信息；\n"
                + "2. 【必须调用工具】调用 searchMallProduct 查询春播商城是否有该药品在售（工具会返回真实库存/规格/价格并生成下单卡片）；\n"
                + "3. 根据工具返回结果，告诉用户该药品在商城是否有货、价格多少，并可引导下单。\n"
                + "若商城无该药品，如实说明并建议替代品。不要编造库存或价格。";
        return functionCallingFlux(question, sessionId, userId, role, sys, List.of(media), mallProductTools);
    }
}
