package com.chunbo.medical.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.config.ToolResultHolder;
import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.entity.MallProduct;
import com.chunbo.medical.mapper.MallProductMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 商城药师 function-calling 工具：图片识别出药品名后，据此查询春播商城数据库是否有该药品，
 * 命中则返回真实库存/规格/价格，并把结构化推荐写入 ToolResultHolder → 前端渲染「下单卡片」。
 */
@Component
public class MallProductTools {

    @Autowired
    private MallProductMapper productMapper;

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

    private String requestIdOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) return null;
        Object v = toolContext.getContext().get(AgentConstant.REQUEST_ID);
        return v == null ? null : String.valueOf(v);
    }
}
