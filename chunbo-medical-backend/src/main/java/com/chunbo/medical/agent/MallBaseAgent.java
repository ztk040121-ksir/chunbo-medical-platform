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

        // 若大模型可用，发起真正的流式 function-calling（自主检索商城药品并触发推荐卡片）
        if (aiModelConfigService != null && aiModelConfigService.getBareChatClient() != null && !aiModelConfigService.isMockEnabled() && mallProductTools != null) {
            String userPhone = context != null ? String.valueOf(context.getOrDefault("phone", "")) : "";
            String userName = context != null ? String.valueOf(context.getOrDefault("userName", "")) : "";

            StringBuilder sysBuilder = new StringBuilder();
            sysBuilder.append("""
                    你是春播万象网上智慧便民药房的24小时专业在线执业药师「春播小药师」。
                    你的核心职责是为社区居民与患者提供权威、安全、温暖的用药咨询、对症选品与订单履约服务。
                    """);
            if (userPhone != null && !userPhone.isBlank() && !"guest".equalsIgnoreCase(userPhone)) {
                sysBuilder.append("\n【当前登录顾客信息】姓名/称呼：").append(userName).append("，绑定手机号：").append(userPhone).append("。\n")
                        .append("当顾客说「查我的订单」「我的快递到哪了」或询问个人订单进度时，必须直接使用该手机号 ").append(userPhone)
                        .append(" 作为 keyword 调用 queryMallOrderTracking 工具主动查询，绝对无需让顾客重复提供手机号！\n")
                        .append("若顾客明确询问其它手机号或具体单号，则按其指定的号码查询。\n");
            } else {
                sysBuilder.append("\n【当前顾客状态】游客或未登录。当顾客询问个人订单时，礼貌引导其提供下单时预留的手机号或订单号，然后调用 queryMallOrderTracking 精准核验。\n");
            }
            sysBuilder.append("""
                    【核心准则】
                    1. 当用户咨询症状或选购药品时，务必调用 searchMallProduct 工具查询春播商城真实在售商品与库存；
                    2. 当用户查询个人订单或物流进度时，调用 queryMallOrderTracking 工具精准核验；
                    3. 当用户询问运费、满多少包邮、送货时效时，调用 queryShippingPolicy 工具获取官方政策；
                    4. 当用户询问配伍禁忌（如头孢配酒、布洛芬与感冒灵重叠）时，调用 queryDrugSafetyWarning 工具给出严谨医学警告；
                    5. 保持条理清晰、亲切温和，使用标准 Markdown 格式排版输出。
                    """);
            return functionCallingFlux(question, sessionId, userId, "CONSUMER", sysBuilder.toString(), mallProductTools);
        }

        // 离线/降级模式：复用 B2bMultiAgentService 多智能体规则分流
        Map<String, Object> result;
        try {
            result = b2bMultiAgentService.runMultiAgentWorkflow(question, "consumer", sessionId, userId, "", skillHint());
        } catch (Exception e) {
            result = Map.of("reply", "您好！遇到身体不适，建议多喝温开水清淡饮食。如需用药可参考右侧分类选品货架或随时向我咨询。");
        }
        String reply = String.valueOf(result.getOrDefault("reply", ""));
        Object recs = result.get("recommendations");

        List<ChatEventVO> events = new ArrayList<>();
        int chunkSize = 6;
        for (int i = 0; i < reply.length(); i += chunkSize) {
            events.add(ChatEventVO.builder()
                    .eventType(ChatEventTypeEnum.DATA.getValue())
                    .eventData(reply.substring(i, Math.min(i + chunkSize, reply.length())))
                    .build());
        }
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
