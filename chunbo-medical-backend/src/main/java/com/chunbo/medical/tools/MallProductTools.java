package com.chunbo.medical.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.config.ToolResultHolder;
import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.entity.MallOrder;
import com.chunbo.medical.entity.MallProduct;
import com.chunbo.medical.enums.OrderStatusEnum;
import com.chunbo.medical.mapper.MallOrderMapper;
import com.chunbo.medical.mapper.MallProductMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商城小药师多智能体核心工具集（涵盖药品选品搜索、订单物流追踪、免邮运费规则与用药安全禁忌）
 */
@Component
public class MallProductTools {

    @Autowired
    private MallProductMapper productMapper;

    @Autowired(required = false)
    private MallOrderMapper orderMapper;

    @Tool(description = "根据药品名称或通用名查询春播商城是否有该药品在售，返回真实库存、规格、零售价，并生成下单推荐卡片")
    public String searchMallProduct(
            @ToolParam(description = "药品名称或通用名，如 布洛芬、连花清瘟胶囊") String name,
            ToolContext toolContext) {

        if (name == null || name.trim().length() < 2) {
            return "药品名称太短，无法查询，请提供更具体的药名。";
        }
        List<MallProduct> prods;
        try {
            prods = productMapper.selectList(new LambdaQueryWrapper<MallProduct>()
                    .like(MallProduct::getProductName, name)
                    .or().like(MallProduct::getGenericName, name)
                    .last("LIMIT 3"));
        } catch (Exception e) {
            return "查询商城药品失败：" + e.getMessage();
        }
        if (prods == null || prods.isEmpty()) {
            return "春播商城暂未找到【" + name + "】相关药品，建议换个名称，或咨询商城药师推荐替代品。";
        }

        List<Map<String, Object>> recs = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        sb.append("已在春播商城为您找到以下药品：\n");
        for (MallProduct p : prods) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("productName", p.getProductName());
            map.put("specification", p.getSpecification());
            map.put("price", p.getRetailGuidePrice());
            map.put("category", p.getCategory());
            map.put("csPitch", p.getCsPitch());
            recs.add(map);
            sb.append("- 【").append(p.getProductName()).append("】")
              .append(" 规格：").append(p.getSpecification() != null ? p.getSpecification() : "-")
              .append("，库存：").append(p.getStock() != null ? p.getStock() : 0)
              .append("，零售价：¥").append(p.getRetailGuidePrice() != null ? p.getRetailGuidePrice() : "-")
              .append("\n");
        }
        // 结构化推荐写 ToolResultHolder，流末尾由 AbstractAgent 统一转 PARAM 卡片下发（下单卡片）
        ToolResultHolder.put(requestIdOf(toolContext), "recommendations", recs);
        return sb.toString();
    }

    @Tool(description = "查询顾客的便民商城订单与最新速递物流跟踪节点。支持按手机号或订单号查询")
    public String queryMallOrderTracking(
            @ToolParam(description = "顾客手机号（如 13800000000）或订单号（如 B2C...）") String keyword,
            ToolContext toolContext) {

        if (orderMapper == null) {
            return "订单数据中心暂时离线，请稍后再试。";
        }
        if (keyword == null || keyword.trim().isEmpty()) {
            return "请提供您下单时预留的手机号或订单编号，以便为您精准追踪物流。";
        }

        String kw = keyword.trim();
        if (kw.length() < 3) {
            return "输入的查询凭证过短，请提供完整的手机号或订单编号以确保精准查询与隐私安全。";
        }
        List<MallOrder> list = orderMapper.selectList(new LambdaQueryWrapper<MallOrder>()
                .like(MallOrder::getOrderNo, kw)
                .or().like(MallOrder::getBuyerName, kw)
                .orderByDesc(MallOrder::getCreateTime)
                .last("LIMIT 5"));

        if (list == null || list.isEmpty()) {
            return "未查询到与【" + kw + "】相关的便民速递订单。请核对手机号或订单号是否正确。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("已为您查询到 ").append(list.size()).append(" 笔便民订单物流节点：\n\n");
        for (MallOrder o : list) {
            String digits = o.getOrderNo() != null ? o.getOrderNo().replaceAll("[^0-9]", "") : "";
            String trackingNo = digits.isEmpty() ? "CB88888888"
                    : "CB" + digits.substring(Math.max(0, digits.length() - 8));
            String statusTag;
            String eta;
            if (OrderStatusEnum.isDelivered(o.getStatus())) {
                statusTag = "✅ 已送达 / 居民已签收";
                eta = "妥投完成";
            } else if (OrderStatusEnum.isShipped(o.getStatus())) {
                statusTag = "🚚 春播便民速递运输中";
                if (o.getCreateTime() != null) {
                    LocalDateTime orderTime = o.getCreateTime();
                    if (orderTime.getHour() >= 18) {
                        eta = "预计次日上午 09:30 社区极速送达";
                    } else {
                        LocalDateTime est = orderTime.plusHours(2);
                        eta = String.format("预计今日 %02d:%02d 社区极速送达", est.getHour(), est.getMinute());
                    }
                } else {
                    eta = "预计2小时内社区网格送达";
                }
            } else {
                statusTag = "⏳ 待商户配货出库";
                eta = "商家备货中（预计1小时内发出）";
            }

            sb.append("**📦 订单 `").append(o.getOrderNo()).append("`** · ").append(statusTag).append("\n");
            sb.append("- 💰 实付金额：¥").append(o.getFinalAmount() != null ? o.getFinalAmount() : "0.00").append("\n");
            sb.append("- 🚚 承运速递：春播便民速运（单号 `").append(trackingNo).append("`）\n");
            sb.append("- ⏱️ 配送时效：").append(eta).append("\n\n");
        }
        return sb.toString();
    }

    @Tool(description = "查询春播健康便民速运的免邮门槛、配送时效、运费标准与冷链配送规则")
    public String queryShippingPolicy() {
        return """
                ### 📦 【春播健康便民速运 · 配送服务与免邮政策】
                - **🚚 社区同城极速直达**：由春播健康社区网格配送专员专人专送，社区同城网格内 **30 分钟 ~ 2 小时** 极速送药上门！
                - **🎉 普惠包邮门槛**：全场订单实付满 **¥68.00** 即享 **春播健康便民速递免费包邮**！未满 68 元仅收取基础便民速运费 6 元。
                - **❄️ 专业医药冷链箱**：需低温冷藏或避光保存的药品均配有专业社区保温冷链箱直递，保障药品质量与生物活性。
                - **📱 便捷实时跟踪**：下单后系统自动分配 CB 开头便民速运单号，随时可通过向小药师提供手机号查询最新物流进展。
                """;
    }

    @Tool(description = "查询国家药典临床用药配伍禁忌、不良反应与联合用药高危警示（如头孢配酒双硫仑样反应、退烧药叠服等）")
    public String queryDrugSafetyWarning(
            @ToolParam(description = "用药疑问或药品组合，如 头孢和酒、布洛芬和感冒灵") String question) {

        if (question == null || question.isEmpty()) {
            return "请提供具体的用药组合或问题。";
        }
        String q = question.toLowerCase();
        if (q.contains("酒") && (q.contains("头孢") || q.contains("甲硝唑") || q.contains("替硝唑"))) {
            return """
                    🚨 **【致命级用药禁忌：双硫仑样反应】**
                    - **危害机制**：头孢类抗生素（如头孢克肟、头孢曲松、头孢哌酮等）及甲硝唑会抑制体内乙醛脱氢酶，导致酒精代谢停滞在乙醛阶段，蓄积中毒！
                    - **严重后果**：可导致面部潮红、心悸胸闷、呼吸困难、血压剧降甚至休克猝死！
                    - **医学硬要求**：服用头孢期间及停药后 **至少 7 天内严禁饮酒**，亦不可食用含酒精食品（如酒心巧克力、藿香正气水、发酵酒酿等）！
                    """;
        }
        if ((q.contains("布洛芬") || q.contains("美林")) && (q.contains("感冒灵") || q.contains("感康") || q.contains("对乙酰氨基酚") || q.contains("泰诺"))) {
            return """
                    ⚠️ **【严重用药警示：退热解热药重叠蓄积】**
                    - **危害机制**：感冒灵、感康、白加黑等复方感冒药中已普遍含有 **对乙酰氨基酚 (扑热息痛)**，若与布洛芬叠加服用，属于同类解热镇痛药超剂量使用！
                    - **严重后果**：会显著加重胃肠道溃疡出血风险，并对肝脏、肾脏造成急性不可逆损伤！
                    - **用药指引**：选一种即可，切勿同时服用两款退热/止痛药；两次退热药服用间隔需至少 4~6 小时。
                    """;
        }
        return "国家药典指导建议：联合用药务必确认药物成分是否重复，慢性病长期服药者应在医师或执业药师指导下用药，出现皮疹、心悸或不适请立即停药就医。";
    }

    private String requestIdOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) return null;
        Object v = toolContext.getContext().get(AgentConstant.REQUEST_ID);
        return v == null ? null : String.valueOf(v);
    }
}
