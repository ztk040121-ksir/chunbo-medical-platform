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

        // 若大模型可用，发起真正的流式生成：
        // 推荐/咨询类 → 在售商品清单同步预取进 prompt，不挂工具，纯流式逐字输出；
        // 订单/物流/运费类 → 需要动态参数（手机号/单号），保留 function-calling 挂工具。
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

            boolean needsTools = question.matches(".*(订单|物流|快递|到哪了|到哪|运费|包邮|配送|发货|退换).*");

            if (needsTools) {
                if (question.contains("运费") || question.contains("包邮") || question.contains("配送") || question.contains("发货") || question.contains("几天到")) {
                    try {
                        sysBuilder.append("\n【春播商城配送与免邮政策（系统已预取）】\n")
                                .append(mallProductTools.queryShippingPolicy()).append("\n");
                    } catch (Exception ignored) {}
                }
                if (question.contains("订单") || question.contains("物流") || question.contains("快递") || question.contains("到哪")) {
                    try {
                        String queryKey = (userPhone != null && !userPhone.isBlank()) ? userPhone : question;
                        sysBuilder.append("\n【顾客订单物流追踪信息（系统已实时核查）】\n")
                                .append(mallProductTools.queryMallOrderTracking(queryKey, null)).append("\n");
                    } catch (Exception ignored) {}
                }
                if (question.contains("禁忌") || question.contains("配伍") || question.contains("一起吃") || question.contains("退换")) {
                    try {
                        sysBuilder.append("\n【用药安全与配伍禁忌知识（系统已预取）】\n")
                                .append(mallProductTools.queryDrugSafetyWarning(question)).append("\n");
                    } catch (Exception ignored) {}
                }
                String catalog = buildProductCatalogText();
                sysBuilder.append("\n【春播商城在售商品清单（真实库存，系统已预取）】\n").append(catalog).append("\n");
                sysBuilder.append("""
                        【本回复无需调用任何工具】
                        系统已把所需政策、订单状态与在售库存预取在上方上下文中。
                        请基于上述真实信息，条理清晰、亲切温和地解答顾客的问题，使用标准 Markdown 格式排版输出。
                        """);
                return functionCallingFlux(question, sessionId, userId, "CONSUMER", sysBuilder.toString());
            }

            // 推荐/咨询类：在售商品清单同步预取进上下文，不挂工具 → 纯流式逐字输出（消除 stream+tools 聚合等待）
            String catalog = buildProductCatalogText();
            sysBuilder.append("\n【春播商城在售商品清单（真实库存，系统已预取）】\n").append(catalog).append("\n");
            sysBuilder.append("""
                    【本回复无需调用任何工具】
                    1. 推荐/选品直接从上方清单中挑选，逐项列明「药名（规格）· ¥单价」，并给出对症用法用量建议；
                    2. 清单中确实没有的对症药品，如实说明「商城暂缺，建议线下药店选购」，绝不编造清单外的商品与价格；
                    3. 涉及配伍禁忌（如头孢配酒、布洛芬与感冒灵重叠）直接给出严谨医学警告；
                    4. 保持条理清晰、亲切温和，推荐清单用编号列表逐项输出，严禁使用 Markdown 表格。
                    """);
            return functionCallingFlux(question, sessionId, userId, "CONSUMER", sysBuilder.toString());
        }

        // 离线/降级模式：大模型客户端不可用（未配置/网络受限/mocked）。规则兜底必须明示「非 AI 生成」，绝不伪装成 AI 流式回复
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

    /** 预取商城在售商品清单文本（真实库存），供推荐类请求直接选品、免工具纯流式生成 */
    private String buildProductCatalogText() {
        try {
            List<com.chunbo.medical.entity.MallProduct> products = b2bMultiAgentService.getProducts();
            if (products == null || products.isEmpty()) return "（暂无在售商品数据）";
            StringBuilder sb = new StringBuilder();
            int count = 0;
            for (com.chunbo.medical.entity.MallProduct p : products) {
                Integer stock = p.getStock();
                if (stock != null && stock <= 0) continue;
                sb.append("- ").append(p.getProductName() == null ? "未命名商品" : p.getProductName())
                  .append("（").append(p.getSpecification() == null || p.getSpecification().isBlank() ? "标准规格" : p.getSpecification())
                  .append("）¥").append(p.getRetailGuidePrice() == null ? "0.00" : p.getRetailGuidePrice().toPlainString())
                  .append("，库存 ").append(stock == null ? 0 : stock)
                  .append("\n");
                if (++count >= 40) break;
            }
            return sb.isEmpty() ? "（暂无在售商品数据）" : sb.toString();
        } catch (Exception e) {
            return "（商品清单加载失败，请如实告知顾客商品信息暂不可用）";
        }
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
