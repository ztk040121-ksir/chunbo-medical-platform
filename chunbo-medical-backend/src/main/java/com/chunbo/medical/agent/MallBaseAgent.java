package com.chunbo.medical.agent;

import com.chunbo.medical.config.ToolResultHolder;
import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.enums.ChatEventTypeEnum;
import com.chunbo.medical.prompt.PromptConfig;
import com.chunbo.medical.service.B2bMultiAgentService;
import com.chunbo.medical.service.FileUploadService;
import com.chunbo.medical.tools.MallProductTools;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 商城小药师业务智能体基类（参照《SpringAI》标准多智能体协作架构）
 * 各细分业务智能体（推荐选品、用药咨询、订单物流、禁忌审查、配送政策、综合导购）继承本基类。
 * 由 AgentRouter 经 LLM 路由命中具体智能体，通过 PromptConfig.formatPrompt 一行读取对应 txt 模板并注入顾客状态，
 * 100% 由大模型自主驱动推理与 Function Calling 工具调用，彻底消除 Java 端字符串拼接与硬编码。
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

    /** 子类返回本智能体对应的固定路由意图（如 MALL_RECOMMEND / MALL_SHIPPING 等） */
    protected abstract String skillHint();

    /** 子类提示词键名（对应 src/main/resources/prompts/ 下的 .txt 文件名），默认与 skillHint 相同小写 */
    protected String promptKey() {
        String hint = skillHint();
        return hint != null ? hint.toLowerCase() : "mall_recommend";
    }

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
        // 1. 图片附件 → 视觉识别药品名 → 自主调用 searchMallProduct 查询商城库存 → 生成下单卡片
        String attachmentId = context != null ? String.valueOf(context.getOrDefault(AgentConstant.ATTACHMENT_ID, "")) : "";
        if (attachmentId != null && !attachmentId.isBlank() && fileUploadService != null) {
            Media media = fileUploadService.toImageMedia(attachmentId);
            if (media != null && mallProductTools != null) {
                return productImageFlux(question, sessionId, userId, media);
            }
        }

        // 2. 大模型可用时，发起真正的 Spring AI Function Calling 流式生成
        if (aiModelConfigService != null && aiModelConfigService.getBareChatClient() != null && !aiModelConfigService.isMockEnabled()) {
            // 纯配置化：一行读取 txt 提示词模板并注入顾客动态上下文参数
            String customerInfo = buildCustomerInfo(context);
            String sysPrompt = PromptConfig.formatPrompt(promptKey(), customerInfo);

            if (mallProductTools != null) {
                return functionCallingFlux(question, sessionId, userId, "CONSUMER", sysPrompt, mallProductTools);
            } else {
                return functionCallingFlux(question, sessionId, userId, "CONSUMER", sysPrompt);
            }
        }

        // 3. 离线/降级模式：大模型客户端不可用（未配置/网络受限/mocked）
        Map<String, Object> result;
        try {
            result = b2bMultiAgentService.runMultiAgentWorkflow(question, "consumer", sessionId, userId, "", skillHint());
        } catch (Exception e) {
            result = Map.of("reply", "您好！遇到身体不适，建议多喝温开水清淡饮食。如需用药可参考右侧分类选品货架或随时向我咨询。");
        }
        String reply = String.valueOf(result.getOrDefault("reply", ""));
        Object recs = result.get("recommendations");

        String notice = "【本地规则兜底 · 非 AI 生成】当前大模型服务未连接（AI 客户端不可用或网络受限），"
                + "以下内容为本地规则匹配结果，不代表 AI 智能体的分析与建议：\n\n";
        List<ChatEventVO> events = new ArrayList<>();
        events.add(ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.DATA.getValue())
                .eventData(notice)
                .build());
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

    /** 格式化当前顾客状态文本供 txt 提示词模板填充 */
    private String buildCustomerInfo(Map<String, Object> context) {
        String phone = context != null ? String.valueOf(context.getOrDefault("phone", "")) : "";
        String userName = context != null ? String.valueOf(context.getOrDefault("userName", "")) : "";
        if (phone != null && !phone.isBlank() && !"guest".equalsIgnoreCase(phone)) {
            return "已登录顾客（姓名/称呼：" + (userName.isBlank() ? "居民" : userName) + "，绑定手机号：" + phone + "）。当顾客询问个人订单进度时，直接以该手机号调用 queryMallOrderTracking 查询。";
        } else {
            return "游客或未登录。当顾客询问个人订单时，礼貌引导其提供下单时预留的手机号或订单号。";
        }
    }

    /** 药品图片识别：图片送入多模态大模型识别药名 → 自主调用 searchMallProduct 查询商城库存 → 生成下单卡片 */
    private Flux<ChatEventVO> productImageFlux(String question, String sessionId, String userId, Media media) {
        String role = "CONSUMER";
        String sys = PromptConfig.get("mall_product_image");
        if (sys == null || sys.isBlank()) {
            sys = "你是春播商城的小药师智能体。用户上传了一张药品图片，请按步骤识别并核实商城库存。";
        }
        return functionCallingFlux(question, sessionId, userId, role, sys, List.of(media), mallProductTools);
    }
}
