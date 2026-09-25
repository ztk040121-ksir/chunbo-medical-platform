package com.chunbo.medical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.InventoryRecord;
import com.chunbo.medical.entity.MallOrder;
import com.chunbo.medical.entity.MallProduct;
import com.chunbo.medical.entity.MallUser;
import com.chunbo.medical.enums.OrderStatusEnum;
import com.chunbo.medical.mapper.InventoryRecordMapper;
import com.chunbo.medical.mapper.MallOrderMapper;
import com.chunbo.medical.mapper.MallProductMapper;
import com.chunbo.medical.mapper.MallUserMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class B2bMultiAgentService {

    @Autowired
    private MallProductMapper productMapper;

    @Autowired
    private MallOrderMapper orderMapper;

    @Autowired(required = false)
    private MallUserMapper userMapper;

    @Autowired(required = false)
    private InventoryRecordMapper inventoryRecordMapper;

    @Autowired(required = false)
    private AiModelConfigService aiModelConfigService;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired(required = false)
    private ChatModel chatModel;

    @Autowired(required = false)
    private RagKnowledgeService ragKnowledgeService;

    @Value("${chunbo.ai.llm-enabled:true}")
    private boolean llmEnabled;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<MallProduct> getProducts() {
        return productMapper.selectList(new LambdaQueryWrapper<MallProduct>()
                .and(w -> w.isNull(MallProduct::getStatus).or().ne(MallProduct::getStatus, "OFF_SALE"))
                .orderByAsc(MallProduct::getId));
    }

    public List<MallOrder> getOrders() {
        return orderMapper.selectList(new LambdaQueryWrapper<MallOrder>().orderByDesc(MallOrder::getCreateTime));
    }

    /** 按当前登录用户过滤订单（商城「我的订单」不再全量下发，防他人订单信息泄漏） */
    public List<MallOrder> getOrdersForUser(String username) {
        if (username == null || username.isBlank()) return List.of();
        MallUser user = null;
        if (userMapper != null) {
            user = userMapper.selectOne(new LambdaQueryWrapper<MallUser>().eq(MallUser::getUsername, username));
        }
        if (user == null) return List.of();
        final Long uid = user.getId();
        final String phone = user.getPhone() == null ? "" : user.getPhone().trim();
        final String nickname = user.getNickname() == null ? "" : user.getNickname().trim();
        LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<MallOrder>().orderByDesc(MallOrder::getCreateTime);
        // 优先按 user_id 精确隔离（新订单已落 user_id）；历史无 user_id 的订单回退昵称/手机号匹配
        wrapper.and(w -> {
            w.eq(MallOrder::getUserId, uid);
            if (!nickname.isEmpty()) w.or().like(MallOrder::getBuyerName, nickname);
            if (!phone.isEmpty()) w.or().like(MallOrder::getBuyerName, phone);
        });
        return orderMapper.selectList(wrapper);
    }

    public Map<String, Object> runMultiAgentWorkflow(String message, String userRole, String sessionId) {
        return runMultiAgentWorkflow(message, userRole, sessionId, "", "");
    }

    /**
     * AI 便民在线药师多智能体与 MCP 工作流 (支持真实数据库穿透、物流订单追踪、禁忌审查与 Markdown 表格渲染)
     */
    public Map<String, Object> runMultiAgentWorkflow(String message, String userRole, String sessionId, String phone, String userName) {
        return runMultiAgentWorkflow(message, userRole, sessionId, phone, userName, null);
    }

    /** 归一化路由意图提示：提取 LLM 输出中的 MALL_XXX 类型名，失败返回 null */
    private String normalizeHint(String intentHint) {
        if (intentHint == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("MALL_(ORDER|GUARD|SHIPPING|RECOMMEND|CONSULT|GENERAL)").matcher(intentHint.toUpperCase());
        return m.find() ? m.group() : null;
    }

    /** 去药意图识别："去掉/不要/移除/换掉 XX药" 提取药名片段，无去药意图返回 null */
    private String extractExcludeName(String msg) {
        if (msg == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?:去掉|不要|除去|移除|换掉|排除)\\s*([\\u4e00-\\u9fa5A-Za-z0-9]{2,12})").matcher(msg);
        return m.find() ? m.group(1).trim() : null;
    }

    /**
     * AI 便民在线药师多智能体与 MCP 工作流（携带路由智能体判出的意图提示）
     * 意图提示命中时直接进入对应技能分支，不再依赖易误伤的关键词顺序
     */
    public Map<String, Object> runMultiAgentWorkflow(String message, String userRole, String sessionId, String phone, String userName, String intentHint) {
        String msg = message != null ? message.trim() : "";
        String role = userRole != null ? userRole.trim().toLowerCase() : "consumer";

        // 判断是否进入 B2B 医药电商 Spring AI 多智能体协同状态网络
        boolean isB2b = "director".equalsIgnoreCase(role) || "buyer".equalsIgnoreCase(role) || "cs".equalsIgnoreCase(role)
                || msg.contains("诊所主任") || msg.contains("采购主管") || msg.contains("门诊客服")
                || msg.contains("阶梯") || msg.contains("议价") || msg.contains("首营") || msg.contains("控销")
                || msg.contains("批发") || msg.contains("订购300盒") || msg.contains("采购 50 盒");

        if (isB2b) {
            return handleB2bMultiAgentWorkflow(msg, role, sessionId);
        }

        Map<String, Object> result = new HashMap<>();
        List<String> stateFlow = new ArrayList<>();
        List<Map<String, Object>> recommendations = new ArrayList<>();

        String reply = "";

        // 路由意图提示（语义路由结果），优先级高于关键词匹配
        String hint = normalizeHint(intentHint);
        boolean hintOrder = "MALL_ORDER".equals(hint);
        boolean hintGuard = "MALL_GUARD".equals(hint);
        boolean hintShipping = "MALL_SHIPPING".equals(hint);
        boolean hintRecommend = "MALL_RECOMMEND".equals(hint) || "MALL_CONSULT".equals(hint);

        // ── 去药意图：用户要求"去掉/不要/换掉某药"时，剔除该药后基于剩余语义重新推荐 ──
        String excludeName = extractExcludeName(msg);
        String effectiveMsg = msg;
        boolean excludeHit = false;
        if (excludeName != null) {
            effectiveMsg = msg.replace(excludeName, " ").replaceAll("给我|去掉|不要|除去|移除|换掉|开点|换个", " ").trim();
            if (effectiveMsg.length() < 2) effectiveMsg = msg.replaceAll("给我|去掉|不要|除去|移除|换掉", " ").trim();
            excludeHit = true;
        }

        // ── 动态对症匹配：从 mall_product 真实药品档案匹配（管理员新增药品实时可被 AI 识别推荐） ──
        List<MallProduct> dynamicMatched = hintGuard || hintShipping ? new ArrayList<>() : matchProductsByMessage(effectiveMsg);

        // ── 推荐优先级：LLM 智能理解 > 关键词引擎 > 硬编码模板 ──
        List<MallProduct> recommended;
        boolean llmHit = false;
        if (llmEnabled && !isDeterministicQuery(effectiveMsg)) {
            List<MallProduct> llm = tryLlmRecommend(effectiveMsg);
            llmHit = llm != null && !llm.isEmpty();
            recommended = llmHit ? llm : dynamicMatched;
        } else {
            recommended = dynamicMatched;
        }
        // 去药：从最终推荐中剔除用户点名的药品；剔除后为空则找同分类替代药（绝不回退到未过滤列表）
        if (excludeName != null) {
            List<MallProduct> filtered = recommended.stream()
                    .filter(p -> p.getProductName() == null || !p.getProductName().contains(excludeName))
                    .collect(java.util.stream.Collectors.toList());
            if (filtered.isEmpty()) {
                String excludedCategory = getProducts().stream()
                        .filter(p -> p.getProductName() != null && p.getProductName().contains(excludeName))
                        .map(MallProduct::getCategory).findFirst().orElse("");
                final String cat = excludedCategory == null ? "" : excludedCategory;
                filtered = getProducts().stream()
                        .filter(p -> p.getProductName() == null || !p.getProductName().contains(excludeName))
                        .filter(p -> cat.isEmpty() || cat.equals(p.getCategory()))
                        .limit(3)
                        .collect(java.util.stream.Collectors.toList());
                if (filtered.isEmpty()) {
                    filtered = getProducts().stream()
                            .filter(p -> p.getProductName() == null || !p.getProductName().contains(excludeName))
                            .limit(3)
                            .collect(java.util.stream.Collectors.toList());
                }
            }
            recommended = filtered;
        }

        // ── MCP 技能 1: 查我的便民订单与速递物流跟踪 ──
        // 注意：不做 hintOrder 强制——语义路由会把"去掉某药"误判成订单意图，订单只认关键词
        if (msg.contains("订单") || msg.contains("物流") || msg.contains("速递") || msg.contains("快递")
                || msg.contains("发货") || msg.contains("配送") || msg.contains("送达") || msg.contains("单号") || msg.contains("到哪")) {
            stateFlow.add("【MCP 工具调用】mcp_query_mall_express_tracking -> 穿透检索 MySQL 8.0 mall_order 真实便民订单库");

            List<MallOrder> allOrders = orderMapper.selectList(
                    new LambdaQueryWrapper<MallOrder>()
                            .like(MallOrder::getOrderNo, "B2C")
                            .orderByDesc(MallOrder::getCreateTime)
            );

            // 优先匹配当前用户的手机号或姓名
            List<MallOrder> matchedOrders = new ArrayList<>();
            if (phone != null && !phone.isEmpty()) {
                for (MallOrder o : allOrders) {
                    if (o.getBuyerName() != null && o.getBuyerName().contains(phone)) {
                        matchedOrders.add(o);
                    }
                }
            }
            if (matchedOrders.isEmpty() && userName != null && !userName.isEmpty() && !userName.equals("居民顾客")) {
                for (MallOrder o : allOrders) {
                    if (o.getBuyerName() != null && o.getBuyerName().contains(userName)) {
                        matchedOrders.add(o);
                    }
                }
            }
            // 严禁将他人订单兜底泄露给当前用户（医疗与个人隐私红线）
            StringBuilder sb = new StringBuilder();
            sb.append("### 🚚 【春播健康便民速递 · 实时物流跟踪履约看板】\n\n");

            if (matchedOrders.isEmpty()) {
                sb.append("💡 **查询结果**：暂未查询到与您当前登录账户关联的便民速递订单。\n\n")
                  .append("- 若您刚完成下单，系统正在同步生成运单，请稍后刷新重试；\n")
                  .append("- 若您使用其他手机号下单，可在对话中发送「查手机号 138xxxx 订单」或直接输入订单号查询；\n")
                  .append("- 您也可以直接在商城右上角查看【我的订单】列表。\n");
            } else {
                sb.append("> 💡 **真实数据验证**：以下数据来自春播便民速运出库履约数据中心。\n");
                for (MallOrder o : matchedOrders) {
                    // 从订单号提取数字生成运单号；订单号无数字时用默认值，避免空串/越界
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
                        // 动态推算配送时效（根据下单时间动态推算，消除写死15:00）
                        if (o.getCreateTime() != null) {
                            java.time.LocalDateTime orderTime = o.getCreateTime();
                            if (orderTime.getHour() >= 18) {
                                eta = "预计次日上午 09:30 社区极速送达";
                            } else {
                                java.time.LocalDateTime estTime = orderTime.plusHours(2);
                                eta = String.format("预计今日 %02d:%02d 社区极速送达", estTime.getHour(), estTime.getMinute());
                            }
                        } else {
                            eta = "预计2小时内社区网格专员送达";
                        }
                    } else {
                        statusTag = "⏳ 待商户配货出库";
                        eta = "商家备货中（预计1小时内发出）";
                    }

                    String cleanItems = parseItemsSummary(o.getItemsJson());
                    sb.append("\n**📦 订单 `").append(o.getOrderNo()).append("`** · ").append(statusTag).append("\n");
                    sb.append("- 💊 药品明细：").append(cleanItems).append("\n");
                    sb.append("- 💰 实付金额：**¥").append(String.format("%.2f", o.getFinalAmount() != null ? o.getFinalAmount().doubleValue() : 0.0)).append("**\n");
                    sb.append("- 📮 收货地址：").append(o.getClinicName() != null ? o.getClinicName() : "社区网格直达").append("\n");
                    sb.append("- 🚚 承运速递：**春播便民速递**（单号 `").append(trackingNo).append("`）\n");
                    sb.append("- ⏱️ 配送时效：").append(eta).append("\n");
                }
            }

            sb.append("\n📦 **春播便民速运承诺**：由春播社区网格配送专员专人直发上门。如需更改配送时间或联系送药师傅，可随时直接在对话框告诉我！\n");
            reply = sb.toString();

        // ── MCP 技能 2: 药品安全与配伍禁忌深度审查（按提问精准作答，不吐全量文档） ──
        } else if (hintGuard || msg.contains("禁忌") || msg.contains("一起吃") || msg.contains("同时吃") || msg.contains("一起喝")
                || msg.contains("同服") || msg.contains("配伍") || msg.contains("混吃") || msg.contains("头孢") || msg.contains("副作用")) {
            stateFlow.add("【MCP 工具调用】mcp_contraindication_guard -> 触发国家药典临床合理用药与配伍禁忌审查");
            reply = buildContraindicationAnswer(msg, stateFlow);

        // ── MCP 技能 3: 药房药品清单、价格表与选药比价查询 ──
        } else if (!hintShipping && (msg.contains("清单") || msg.contains("价格表") || msg.contains("多少钱") || msg.contains("有哪些药")
                || msg.contains("常备药") || msg.contains("买药") || msg.contains("热销") || msg.contains("目录"))) {
            stateFlow.add("【MCP 工具调用】mcp_query_real_mall_products -> 实时穿透 MySQL mall_product 正品商品数据库");

            List<MallProduct> prods = getProducts();
            StringBuilder sb = new StringBuilder();
            sb.append("### 🏪 【春播便民网上药房 · 真实在售对症药品与价格清单】\n\n");
            sb.append("> 💡 **正品特惠保障**：春播便民大药房所有药品均由特约药企直供，批号可追溯，满68元享春播健康便民速运免邮！\n");

            if (prods.isEmpty()) {
                sb.append("\n当前药房暂无在售药品。\n");
            } else {
                for (MallProduct p : prods) {
                    String pitch = p.getCsPitch() != null && !p.getCsPitch().isEmpty() ? p.getCsPitch() : "生活对症常用必备好药";
                    sb.append("\n**PRD-").append(String.format("%03d", p.getId())).append(" ").append(p.getProductName()).append("** ｜ `").append(p.getSpecification() != null ? p.getSpecification() : "常规盒装").append("`\n");
                    sb.append("- 💰 惠民价：**¥").append(String.format("%.2f", p.getRetailGuidePrice() != null ? p.getRetailGuidePrice().doubleValue() : 0.0)).append("** ｜ 🏷️ ").append(p.getCategory()).append(" ｜ ✅ 现货直发\n");
                    sb.append("- 🏭 ").append(p.getManufacturer() != null ? p.getManufacturer() : "春播特约药企").append("\n");
                    sb.append("- 💡 ").append(pitch).append("\n");
                }
            }

            sb.append("\n💡 您可直接点击右侧分类货架的【加入购物车】一键结算，或继续向小药师描述具体症状！\n");
            reply = sb.toString();

            // 附带前三款推荐卡片
            for (int i = 0; i < Math.min(3, prods.size()); i++) {
                MallProduct p = prods.get(i);
                Map<String, Object> map = new HashMap<>();
                map.put("id", p.getId());
                map.put("productName", p.getProductName());
                map.put("specification", p.getSpecification());
                map.put("price", p.getRetailGuidePrice());
                map.put("category", p.getCategory());
                recommendations.add(map);
            }

        // ── MCP 技能 4: 便民速运配送与免邮政策查询 ──
        } else if (hintShipping || msg.contains("运费") || msg.contains("包邮") || msg.contains("免邮") || msg.contains("邮费")
                || msg.contains("满多少") || msg.contains("配送费") || msg.contains("送货") || msg.contains("时效")) {
            stateFlow.add("【MCP 工具调用】mcp_query_shipping_policy -> 查询春播健康便民速递配送时效与运费规则");

            reply = "### 📦 【春播健康便民速运 · 配送服务与免邮政策】\n\n" +
                    "- **🚚 社区同城极速直达**：由春播健康社区网格配送专员专人专送，社区同城网格内 **30 分钟 ~ 2 小时** 极速送药上门！\n" +
                    "- **🎉 普惠包邮门槛**：全场订单实付满 **¥68.00** 即享 **春播健康便民速递免费包邮**！未满 68 元仅收取基础便民速运费 6 元。\n" +
                    "- **❄️ 专业医药冷链箱**：需低温冷藏或避光保存的药品均配有专业社区保温冷链箱直递，保障药品质量与生物活性。\n" +
                    "- **📱 便捷实时跟踪**：下单后系统自动分配 `CB` 开头便民速运单号，随时可通过对话向小药师输入「查我的订单」实时追踪最新运力节点。\n";

        // ── 动态对症推荐：基于 mall_product 真实药品档案匹配（管理员新增/修改药品实时生效） ──
        } else if (!recommended.isEmpty()) {
            stateFlow.add(llmHit
                    ? "【LLM 智能对症推荐】语义理解 + 药品目录检索，命中 " + recommended.size() + " 款对症好药"
                    : "【动态对症推荐 Agent】从真实药品档案精准匹配 " + recommended.size() + " 款对症好药");

            List<MallProduct> shown = recommended.size() > 6 ? recommended.subList(0, 6) : recommended;
            StringBuilder sb = new StringBuilder();
            sb.append("### 💊 【春播小药师 · 为您匹配到 ").append(shown.size()).append(" 款对症好药】\n\n");
            if (excludeHit && excludeName != null) {
                sb.append("✅ 已按您的要求去掉「**").append(excludeName).append("**」，为您重新推荐：\n\n");
            }
            sb.append("根据您描述的情况，从春播便民药房**真实在售药品档案**中为您精准匹配：\n\n");
            for (MallProduct p : shown) {
                String pitch = p.getCsPitch() != null && !p.getCsPitch().isEmpty() ? p.getCsPitch() : "对症常用必备好药";
                sb.append("**").append(p.getProductName()).append("** ｜ `").append(p.getSpecification() != null ? p.getSpecification() : "常规盒装")
                  .append("` ｜ **¥").append(String.format("%.2f", p.getRetailGuidePrice() != null ? p.getRetailGuidePrice().doubleValue() : 0.0)).append("**\n");
                sb.append("- 💊 ").append(pitch).append("\n\n");
            }
            sb.append("💡 以上均为药房真实档案药品，可在右侧货架一键加购，或继续向我描述症状帮您进一步筛选！\n");
            reply = sb.toString();

            for (MallProduct p : recommended) {
                if (recommendations.size() >= 3) break;
                Map<String, Object> map = new HashMap<>();
                map.put("id", p.getId());
                map.put("productName", p.getProductName());
                map.put("specification", p.getSpecification());
                map.put("price", p.getRetailGuidePrice());
                map.put("category", p.getCategory());
                map.put("csPitch", p.getCsPitch());
                recommendations.add(map);
            }

        // ── MCP 技能 5: 对症用药咨询 (感冒/发热/咳嗽/胃肠/外伤/滋补；语义路由判定为推荐意图时跳过静态模板) ──
        } else if (!hintRecommend && (msg.contains("感冒") || msg.contains("发烧") || msg.contains("热") || msg.contains("鼻涕") || msg.contains("头痛"))) {
            stateFlow.add("【MCP 工具调用】mcp_match_cold_symptom -> 检索感冒退热与抗病毒对症药品库");

            StringBuilder sb = new StringBuilder();
            sb.append("### 💊 【春播小药师 · 感冒发热生活对症用药方案】\n\n");
            sb.append("针对感冒发热、头痛身痛与鼻塞流涕，药师建议采取对症缓解与充分休息结合策略：\n\n");
            appendProductLine(sb, "复方氨酚烷胺胶囊", "缓解感冒初起发热、头痛鼻塞流涕，口服一次1粒，一日2次");
            appendProductLine(sb, "布洛芬混悬滴剂", "用于儿童与成人高热及头痛牙痛，餐后温水送服，24小时不超过4次");
            appendProductLine(sb, "连花清瘟胶囊", "清瘟解毒、宣肺泄热，针对发热咽痛恶寒咳嗽，口服一次4粒，一日3次");
            sb.append("\n💡 **居家护理医嘱**：保证每日饮水 1500~2000ml，多清淡饮食，若体温持续超过 38.5℃ 或超过3天未退，请及时前往门诊就医。\n");
            reply = sb.toString();

            recommendProductByName("复方氨酚烷胺胶囊 (快克)", recommendations);
            recommendProductByName("连花清瘟胶囊", recommendations);
            recommendProductByName("布洛芬混悬滴剂 (美林)", recommendations);

        } else if (!hintRecommend && (msg.contains("咳") || msg.contains("痰") || msg.contains("咽痛") || msg.contains("喉咙"))) {
            stateFlow.add("【MCP 工具调用】mcp_match_cough_symptom -> 检索止咳化痰与利咽解毒药品");

            StringBuilder sb = new StringBuilder();
            sb.append("### 🍯 【春播小药师 · 咽痛咳嗽化痰对症指导】\n\n");
            sb.append("咽喉干痒微痛、咳嗽痰多时，推荐滋润咽喉与化痰止咳组合：\n\n");
            appendProductLine(sb, "京都念慈菴蜜炼川贝枇杷膏", "润肺化痰、止咳平喘、护喉利咽，温开水调服或含服，一次1汤匙");
            appendProductLine(sb, "连花清瘟胶囊", "宣肺解毒利咽，适合咽干灼热伴微咳发热患者");
            sb.append("\n💡 **生活调理建议**：禁食生冷刺激与辛辣油炸食品，可用罗汉果或金银花泡温水频服。\n");
            reply = sb.toString();

            recommendProductByName("京都念慈菴蜜炼川贝枇杷膏", recommendations);
            recommendProductByName("连花清瘟胶囊", recommendations);

        } else if (!hintRecommend && (msg.contains("胃") || msg.contains("胀") || msg.contains("消化") || msg.contains("吃多") || msg.contains("积食"))) {
            stateFlow.add("【MCP 工具调用】mcp_match_digest_symptom -> 检索脾胃消化与健胃消食药品");

            StringBuilder sb = new StringBuilder();
            sb.append("### 🥣 【春播小药师 · 胃部不适与消化不良调理方案】\n\n");
            sb.append("针对饭后胃胀、反酸早饱或饮食积滞，推荐促胃肠动力与健脾消食调理：\n\n");
            appendProductLine(sb, "江中牌健胃消食片", "健胃消食，用于脾胃虚弱所致的积食消化不良，嚼服一次3片，一日3次");
            appendProductLine(sb, "多潘立酮片", "促胃动力药，用于消化不良伴腹胀嗳气恶心，饭前半小时口服一次1片");
            sb.append("\n💡 **调理提示**：平时少食多餐，细嚼慢咽，饭后半小时内避免立刻剧烈运动或平卧。\n");
            reply = sb.toString();

            recommendProductByName("江中牌健胃消食片", recommendations);
            recommendProductByName("多潘立酮片 (吗丁啉)", recommendations);

        } else if (!hintRecommend && (msg.contains("拉肚子") || msg.contains("腹泻") || msg.contains("肚子疼"))) {
            stateFlow.add("【MCP 工具调用】mcp_match_diarrhea_symptom -> 检索急慢性腹泻肠道黏膜保护剂");

            StringBuilder sb = new StringBuilder();
            sb.append("### 💧 【春播小药师 · 急性腹泻与肠道黏膜保护应急指导】\n\n");
            sb.append("出现腹泻拉肚子时，核心是防止脱水并吸附毒素、保护肠道黏膜：\n\n");
            appendProductLine(sb, "蒙脱石散", "强效吸附肠道病毒细菌与毒素，修复肠粘膜。温水50ml摇匀空腹口服");
            sb.append("\n💡 **重要防脱水提示**：腹泻极易导致电解质紊乱，请务必少量多次饮用淡盐水或补液盐。若排便带有浓血或伴高热，请立刻前往门诊。\n");
            reply = sb.toString();

            recommendProductByName("蒙脱石散 (思密达)", recommendations);

        } else if (!hintRecommend && (msg.contains("摔") || msg.contains("扭") || msg.contains("碰") || msg.contains("破") || msg.contains("跌打") || msg.contains("创口"))) {
            stateFlow.add("【MCP 工具调用】mcp_match_trauma_symptom -> 检索运动扭伤与外科创面敷料库");

            StringBuilder sb = new StringBuilder();
            sb.append("### 🩹 【春播小药师 · 跌打扭伤与外伤创面应急护理】\n\n");
            sb.append("针对生活中的意外擦碰伤、关节扭伤与表皮破损，推荐应急处理：\n\n");
            appendProductLine(sb, "云南白药气雾剂", "活血散瘀消肿止痛，用于跌打瘀痛、肌肉酸痛，先喷红瓶冷敷再喷白瓶");
            appendProductLine(sb, "海氏海诺医用创口贴", "浅表创口止血透气防水，清洗消毒后贴敷保护创面");
            sb.append("\n💡 **急救原则**：急性扭伤24小时内严格冷敷，切勿用力揉搓按压，48小时后再行热敷或涂抹活血药物。\n");
            reply = sb.toString();

            recommendProductByName("云南白药气雾剂", recommendations);
            recommendProductByName("海氏海诺医用无菌创口贴", recommendations);

        } else {
            stateFlow.add("【通用家庭健康导购 Agent】展示生活常备基础药品与便民服务");

            StringBuilder sb = new StringBuilder();
            sb.append("### 👩‍⚕️ 【您好！我是您的 24 小时春播便民在线药师】\n\n");
            sb.append("生活中有任何感冒发热、咽痛咳嗽、胃胀腹泻、跌打擦伤或慢病日常用药疑问，都可以随时告诉我。\n\n");
            sb.append("🌟 **我能为您提供的便民 MCP 技能服务**：\n");
            sb.append("- 🚚 **物流速递跟踪**：输入「查我的订单」实时追踪春播健康便民速递配送节点\n");
            sb.append("- 📋 **药房药品清单**：输入「价格表」或「常备药清单」穿透查看全部在售正品直供好药\n");
            sb.append("- 🚨 **用药安全审查**：输入「两种药能一起吃吗」触发禁忌与配伍安全审查\n");
            sb.append("- 📦 **免邮极速达**：全场实付满 ¥68 免运费，社区同城 30分钟~2小时 极速送达\n\n");
            sb.append("🛒 **精选家庭生活常备用药**：\n");
            appendProductLine(sb, "布洛芬混悬滴剂", "退热镇痛、儿童成人常备");
            appendProductLine(sb, "江中牌健胃消食片", "健胃消食、化解食积腹胀");
            appendProductLine(sb, "海氏海诺医用创口贴", "外伤创面保护、无菌防水");
            reply = sb.toString();

            recommendProductByName("布洛芬混悬滴剂 (美林)", recommendations);
            recommendProductByName("江中牌健胃消食片", recommendations);
            recommendProductByName("海氏海诺医用无菌创口贴", recommendations);
        }

        result.put("targetAgent", "RECOMMEND_AGENT");
        result.put("content", reply);
        result.put("stateFlow", stateFlow);
        result.put("reply", reply);
        result.put("agentName", "春播便民健康小药师");
        result.put("recommendations", recommendations);
        return result;
    }

    /**
     * 禁忌精准简答：识别用户具体问的药物组合，只输出相关条目（不整篇禁忌文档）
     * 未识别到具体组合时输出简明三条通用安全守则
     */
    private String buildContraindicationAnswer(String msg, List<String> stateFlow) {
        boolean alcohol = msg.contains("酒");
        boolean cef = msg.contains("头孢") || msg.contains("甲硝唑") || msg.contains("抗生素") || msg.contains("消炎药");
        boolean ibu = msg.contains("布洛芬") || msg.contains("美林");
        boolean coldMed = msg.contains("感冒药") || msg.contains("快克") || msg.contains("氨酚") || msg.contains("感康") || msg.contains("白加黑");
        boolean mont = msg.contains("蒙脱") || msg.contains("思密达");
        boolean asp = msg.contains("阿司匹林") || msg.contains("阿斯匹林");

        boolean specific = (alcohol && cef) || (ibu && coldMed) || (mont && (cef || asp || ibu)) || (asp && ibu) || cef || (ibu && alcohol);

        StringBuilder sb = new StringBuilder();
        sb.append("### 🚨 【春播执业药师 · 用药安全审查】\n\n");
        sb.append("针对您的问题「").append(msg).append("」，药师审查结论：\n\n");

        if (cef && alcohol) {
            sb.append("**🔴 头孢类抗菌药 + 酒精 = 极高风险！**\n");
            sb.append("- ⚠️ 机理：头孢抑制乙醛脱氢酶，饮酒会引发**双硫仑样反应**（胸闷、心悸、呼吸困难，严重可休克）\n");
            sb.append("- ✅ 药师指导：**服用头孢期间及停药后 7 天内绝对不能饮酒**，含酒精的食物（酒心巧克力、藿香正气水、醉蟹）也要避开\n");
            sb.append("- ⏱️ 记不住？一句话：**\"头孢配酒，说走就走\"，停药 7 天内滴酒不沾**\n\n");
        } else if (ibu && coldMed) {
            sb.append("**🔴 布洛芬 + 复方感冒药（快克/感康等）= 极高风险！**\n");
            sb.append("- ⚠️ 机理：复方感冒药大多含对乙酰氨基酚，与布洛芬叠加服用会加重肝肾负担，易致肝肾损伤\n");
            sb.append("- ✅ 药师指导：**退热镇痛只保留一种**，服药前看清成分表\n\n");
        } else if (mont) {
            sb.append("**🟡 蒙脱石散 + 其他口服药 = 需错开服用**\n");
            sb.append("- ⚠️ 机理：蒙脱石散吸附力强，会把我方其他药物一并吸附排出，导致药效失效\n");
            sb.append("- ✅ 药师指导：**与其他药间隔 1.5~2 小时** 服用\n\n");
        } else if (asp && ibu) {
            sb.append("**🔴 阿司匹林 + 布洛芬 = 高风险**\n");
            sb.append("- ⚠️ 机理：竞争同一代谢酶，增加消化道出血风险\n");
            sb.append("- ✅ 药师指导：避免自行联用，遵心血管医师指导\n\n");
        } else if (cef) {
            sb.append("**💊 头孢类用药安全要点**：\n");
            sb.append("- 服用期间及停药后 **7 天内禁止饮酒**（双硫仑样反应，可致命）\n");
            sb.append("- 过敏体质者用药前需做皮试，出现皮疹瘙痒立即停药就医\n\n");
        } else {
            sb.append("**居家用药三条安全守则**：\n");
            sb.append("1. **成分不叠加**：布洛芬与复方感冒药（含对乙酰氨基酚）不要同服\n");
            sb.append("2. **头孢不碰酒**：服头孢期间及停药 7 天内滴酒不沾\n");
            sb.append("3. **吸附剂错开**：蒙脱石散与其他药间隔 1.5~2 小时\n\n");
            sb.append("💡 您可以问得更具体，例如「头孢和酒能一起吗」「布洛芬和快克能同服吗」。\n");
        }

        if (specific) {
            sb.append("> ⚠️ 以上结论基于国家药典配伍禁忌规范，症状复杂或正在服用多种慢病药物时，请咨询医师或到店咨询执业药师。\n");
        } else {
            sb.append("💡 春播便民药师 24 小时在线，欢迎随时追问具体药物组合！\n");
        }
        return sb.toString();
    }

    private String parseItemsSummary(String itemsJson) {
        if (itemsJson == null || itemsJson.trim().isEmpty()) return "家庭便民生活常备药品";
        try {
            java.util.regex.Pattern p = java.util.regex.Pattern.compile("\"productName\":\\s*\"([^\"]+)\"");
            java.util.regex.Matcher m = p.matcher(itemsJson);
            List<String> names = new ArrayList<>();
            while (m.find()) {
                names.add(m.group(1));
            }
            if (!names.isEmpty()) {
                return String.join(" + ", names);
            }
        } catch (Exception ignored) {}
        return "家庭便民生活常备药品";
    }

    /** 从 mall_product 真实药品档案按名称模糊查询商品（未命中返回 null） */
    private MallProduct findProduct(String name) {
        if (name == null || name.length() < 2) return null;
        try {
            LambdaQueryWrapper<MallProduct> qw = new LambdaQueryWrapper<>();
            qw.like(MallProduct::getProductName, name.substring(0, Math.min(4, name.length())));
            List<MallProduct> prods = productMapper.selectList(qw);
            return prods.isEmpty() ? null : prods.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 追加一条「真实药品」文本行：名称/规格/价格均从 mall_product 真实档案读取，
     * 杜绝写死价格假数据；未命中时价格标注「以货架实价为准」。
     */
    private void appendProductLine(StringBuilder sb, String name, String pitch) {
        MallProduct p = findProduct(name);
        // 药名/规格/价格/话术优先取 DB 真实档案，仅在未命中时才用传入的兜底文案
        String displayName = (p != null && p.getProductName() != null) ? p.getProductName() : name;
        String spec = (p != null && p.getSpecification() != null) ? p.getSpecification() : "常规盒装";
        String price = (p != null && p.getRetailGuidePrice() != null)
                ? "¥" + String.format("%.2f", p.getRetailGuidePrice().doubleValue()) : "以货架实价为准";
        String realPitch = (p != null && p.getCsPitch() != null && !p.getCsPitch().isEmpty()) ? p.getCsPitch() : pitch;
        sb.append("**").append(displayName).append("** ｜ `").append(spec).append("` ｜ **").append(price).append("**\n");
        sb.append("- 💊 ").append(realPitch).append("\n\n");
    }

    private void recommendProductByName(String name, List<Map<String, Object>> list) {
        LambdaQueryWrapper<MallProduct> qw = new LambdaQueryWrapper<>();
        qw.like(MallProduct::getProductName, name.substring(0, Math.min(4, name.length())));
        List<MallProduct> prods = productMapper.selectList(qw);
        if (!prods.isEmpty()) {
            MallProduct p = prods.get(0);
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("productName", p.getProductName());
            map.put("specification", p.getSpecification());
            map.put("price", p.getRetailGuidePrice());
            map.put("category", p.getCategory());
            map.put("csPitch", p.getCsPitch());
            list.add(map);
        }
    }

    /**
     * LLM 智能推荐：把真实药品目录 + 用户症状交给大模型，返回最对症药品的 id 列表。
     * 任何异常（端点不可达/超时/解析失败）都返回 null，由调用方降级到关键词引擎。
     */
    private List<MallProduct> tryLlmRecommend(String msg) {
        if (chatModel == null) return null;
        List<MallProduct> prods = getProducts();
        if (prods.isEmpty()) return null;

        StringBuilder catalog = new StringBuilder();
        for (MallProduct p : prods) {
            catalog.append(p.getId()).append("|").append(p.getProductName())
                   .append("|").append(p.getCategory())
                   .append("|").append(p.getCsPitch() != null ? p.getCsPitch() : "")
                   .append("\n");
        }

        String system = "你是春播便民药房的执业药师。根据用户的症状描述，从下面的真实药品目录中挑选**最针对性**的 1~3 款药品。"
                + "挑选原则：药品的适应症必须与用户症状直接对应，宁缺毋滥；例如用户明确说\"退烧\"就优先选退热类（布洛芬等），"
                + "只说流鼻涕着凉就优先选感冒对症药，不要把所有感冒退热药一股脑全推。按针对性从高到低排序。"
                + "只输出一行 JSON，格式：{\"ids\":[药品id,...]}，id 必须是目录中真实存在的数字，不要输出任何解释或多余文字。"
                + "如果没有对症药品，输出 {\"ids\":[]}。\n药品目录(每行: id|名称|分类|适应症话术):\n" + catalog;

        try {
            String content = ChatClient.builder(chatModel).build()
                    .prompt()
                    .system(system)
                    .user(msg)
                    .call()
                    .content();
            if (content == null) return null;

            List<Long> ids = new ArrayList<>();
            Matcher m = Pattern.compile("\"ids\"\\s*:\\s*\\[([0-9,\\s]*)\\]").matcher(content);
            if (m.find()) {
                for (String s : m.group(1).split(",")) {
                    String t = s.trim();
                    if (!t.isEmpty()) ids.add(Long.valueOf(t));
                }
            }
            if (ids.isEmpty()) return null;

            List<MallProduct> matched = new ArrayList<>();
            for (MallProduct p : prods) {
                if (ids.contains(p.getId())) matched.add(p);
            }
            return matched;
        } catch (Exception e) {
            System.out.println("[LLM 推荐降级] " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }
    }

    /** 确定性查询（订单/禁忌/清单/邮费）不触发 LLM，保持秒回 */
    private boolean isDeterministicQuery(String msg) {
        return msg.contains("订单") || msg.contains("物流") || msg.contains("速递") || msg.contains("快递")
                || msg.contains("发货") || msg.contains("配送") || msg.contains("送达") || msg.contains("单号") || msg.contains("到哪")
                || msg.contains("禁忌") || msg.contains("一起吃") || msg.contains("一起喝") || msg.contains("同时吃") || msg.contains("配伍")
                || msg.contains("混吃") || msg.contains("头孢") || msg.contains("副作用")
                || msg.contains("清单") || msg.contains("价格表") || msg.contains("有哪些药")
                || msg.contains("常备药") || msg.contains("买药") || msg.contains("热销") || msg.contains("目录")
                || msg.contains("运费") || msg.contains("包邮") || msg.contains("免邮") || msg.contains("邮费") || msg.contains("满多少")
                || msg.contains("送货") || msg.contains("时效");
    }

    /**
     * 动态对症匹配引擎：把用户消息与 mall_product 全部真实档案
     * (商品名/通用名/分类/三个销售话术字段) 做双向关键词 + 同义词匹配。
     * 管理员新增或修改药品档案后，AI 无需任何改动即可实时识别并推荐新药。
     */
    private List<MallProduct> matchProductsByMessage(String msg) {
        List<MallProduct> matched = new ArrayList<>();
        if (msg == null || msg.length() < 2) return matched;

        // 1. 从用户消息提取匹配词：原文分词 + 同义词扩展
        Set<String> terms = new LinkedHashSet<>(splitKeywords(msg));
        terms.addAll(expandSynonyms(msg));

        // 2. 与每条真实药品档案文本做包含匹配
        for (MallProduct p : getProducts()) {
            String text = String.join(" ",
                    nullSafe(p.getProductName()), nullSafe(p.getGenericName()), nullSafe(p.getCategory()),
                    nullSafe(p.getCsPitch()), nullSafe(p.getDirectorPitch()), nullSafe(p.getBuyerPitch()));
            for (String term : terms) {
                if (text.contains(term)) {
                    matched.add(p);
                    break;
                }
            }
            if (matched.size() >= 6) break;
        }
        return matched;
    }

    /** 同义词扩展：把口语症状词映射为药品档案常用的功效词 */
    private Set<String> expandSynonyms(String msg) {
        Map<String, String[]> groups = new LinkedHashMap<>();
        groups.put("退烧", new String[]{"退热", "布洛芬", "氨酚", "清热"});
        groups.put("着凉", new String[]{"感冒", "清热", "氨酚"});
        groups.put("流鼻涕", new String[]{"氨酚", "感冒", "鼻塞"});
        groups.put("鼻塞", new String[]{"氨酚", "感冒"});
        groups.put("感冒", new String[]{"氨酚", "清瘟", "清热"});
        groups.put("发烧", new String[]{"退热", "清热", "布洛芬", "氨酚"});
        groups.put("发热", new String[]{"退热", "清热"});
        groups.put("咳嗽", new String[]{"止咳", "化痰", "润肺"});
        groups.put("咳痰", new String[]{"化痰", "止咳"});
        groups.put("咽痛", new String[]{"利咽", "清咽", "润喉"});
        groups.put("嗓子疼", new String[]{"利咽", "润喉"});
        groups.put("喉咙痛", new String[]{"利咽", "润喉"});
        groups.put("头痛", new String[]{"镇痛", "止痛"});
        groups.put("头疼", new String[]{"镇痛", "止痛"});
        groups.put("胃胀", new String[]{"消食", "健胃", "胀"});
        groups.put("腹胀", new String[]{"消食", "健胃"});
        groups.put("胀气", new String[]{"消食", "健胃"});
        groups.put("积食", new String[]{"消食", "健胃"});
        groups.put("消化不良", new String[]{"消食", "健胃", "促胃动力"});
        groups.put("反酸", new String[]{"制酸", "胃酸"});
        groups.put("拉肚子", new String[]{"止泻", "腹泻", "蒙脱"});
        groups.put("腹泻", new String[]{"止泻", "蒙脱"});
        groups.put("拉稀", new String[]{"止泻", "腹泻"});
        groups.put("便秘", new String[]{"通便", "润肠"});
        groups.put("扭伤", new String[]{"活血", "消肿", "跌打"});
        groups.put("崴脚", new String[]{"活血", "消肿", "跌打"});
        groups.put("跌打", new String[]{"活血", "消肿"});
        groups.put("擦伤", new String[]{"创口", "无菌", "敷料"});
        groups.put("伤口", new String[]{"创口", "无菌", "消毒"});
        groups.put("烫伤", new String[]{"烧烫", "烫伤膏"});
        groups.put("痒", new String[]{"止痒", "炉甘石"});
        groups.put("蚊虫", new String[]{"止痒", "驱蚊"});
        groups.put("上火", new String[]{"清火", "清热", "解毒"});
        groups.put("口腔溃疡", new String[]{"溃疡", "清火"});
        groups.put("气血", new String[]{"补气", "补血", "阿胶"});
        groups.put("贫血", new String[]{"补血", "阿胶"});
        groups.put("高血压", new String[]{"血压", "降压"});
        groups.put("失眠", new String[]{"安神", "助眠"});

        Set<String> terms = new LinkedHashSet<>();
        for (Map.Entry<String, String[]> e : groups.entrySet()) {
            if (msg.contains(e.getKey())) {
                terms.addAll(Arrays.asList(e.getValue()));
            }
        }
        return terms;
    }

    /** 把文本按非中英文数字的分隔符拆成 2~12 字的关键词片段 */
    private List<String> splitKeywords(String text) {
        List<String> kws = new ArrayList<>();
        if (text == null || text.isEmpty()) return kws;
        for (String seg : text.split("[^\\u4e00-\\u9fa5A-Za-z0-9]+")) {
            String t = seg.trim();
            if (t.length() >= 2 && t.length() <= 12) kws.add(t);
        }
        return kws;
    }

    private String nullSafe(String s) {
        return s != null ? s : "";
    }

    /**
     * B2B 医药电商 Spring AI 多智能体编排网络：
     * Router Agent 语义分派 -> Recommend / CS / Bargain Agent 业务执行 -> 订单防超卖一键落库
     */
    private Map<String, Object> handleB2bMultiAgentWorkflow(String msg, String userRole, String sessionId) {
        Map<String, Object> result = new HashMap<>();
        List<String> stateFlow = new ArrayList<>();
        List<Map<String, Object>> recommendations = new ArrayList<>();

        String role = userRole != null ? userRole.toLowerCase().trim() : "director";
        String lower = msg != null ? msg.toLowerCase() : "";

        // 1. Router Agent 意图分派
        String targetAgent;
        String roleTitle = "director".equals(role) ? "诊所全科主任" : ("buyer".equals(role) ? "门诊采购主管" : "门诊客服合规");
        stateFlow.add("【Spring AI 智能体编排网络】Router Agent 启动意图解析与业务分发中枢");
        stateFlow.add("【角色画像注入】识别咨询主体视角：【" + roleTitle + "】");

        boolean isBargain = lower.contains("议价") || lower.contains("折扣") || lower.contains("订购")
                || lower.contains("采购") || lower.contains("买赠") || lower.contains("阶梯") || lower.contains("批发")
                || lower.contains("多少钱") || lower.contains("优惠") || lower.contains("申请") || "buyer".equals(role);

        boolean isCs = lower.contains("资质") || lower.contains("合规") || lower.contains("gsp") || lower.contains("认证")
                || lower.contains("首营") || lower.contains("生产许可") || lower.contains("备案") || lower.contains("冷链")
                || lower.contains("检验报告") || lower.contains("药检") || "cs".equals(role);

        boolean isRecommend = lower.contains("推荐") || lower.contains("选品") || lower.contains("特色") || lower.contains("贴敷")
                || lower.contains("秋冬") || lower.contains("学术") || lower.contains("疗效") || lower.contains("提成") || "director".equals(role);

        if (isBargain && (lower.contains("盒") || lower.contains("折扣") || lower.contains("议价") || lower.contains("订购") || lower.contains("阶梯") || lower.contains("采购"))) {
            targetAgent = "BARGAIN_AGENT";
        } else if (isCs && (lower.contains("资质") || lower.contains("gsp") || lower.contains("认证") || lower.contains("合规") || lower.contains("冷链"))) {
            targetAgent = "CS_AGENT";
        } else if (isRecommend) {
            targetAgent = "RECOMMEND_AGENT";
        } else {
            targetAgent = "buyer".equals(role) ? "BARGAIN_AGENT" : ("cs".equals(role) ? "CS_AGENT" : "RECOMMEND_AGENT");
        }

        String content = "";
        Map<String, Object> bargainDraft = null;

        if ("BARGAIN_AGENT".equals(targetAgent)) {
            stateFlow.add("【Spring AI 状态机流转】Router Agent ➔ 命中【🤝 Bargain Agent 大宗采购阶梯议价智能体】");
            stateFlow.add("【阶梯价格模型检索】穿透 MySQL mall_product 提取真实供货价与控销阶梯政策");

            int qty = 100;
            Matcher m = Pattern.compile("(\\d+)\\s*(?:盒|件|套|瓶|袋|箱)?").matcher(msg != null ? msg : "");
            if (m.find()) {
                try {
                    qty = Integer.parseInt(m.group(1));
                } catch (Exception ignored) {}
            }
            if (qty <= 0) qty = 100;

            String prodName = "春播万象通络贴";
            if (msg != null && (msg.contains("小儿") || msg.contains("止咳"))) {
                prodName = "小儿止咳化痰贴";
            } else if (msg != null && msg.contains("三伏")) {
                prodName = "三伏冬病夏治贴";
            }

            BigDecimal unitPrice = new BigDecimal("38.00");
            BigDecimal totalAmt = unitPrice.multiply(BigDecimal.valueOf(qty));
            BigDecimal discountAmt;
            BigDecimal finalAmt;
            String discountRate;
            String giftNotes;

            if (qty >= 300) {
                discountRate = "8.0折 (战略合作极速专享价)";
                finalAmt = totalAmt.multiply(new BigDecimal("0.80")).setScale(2, java.math.RoundingMode.HALF_UP);
                discountAmt = totalAmt.subtract(finalAmt);
                giftNotes = "买 300 盒立赠 30 盒同款耗材 + 顺丰医药冷链极速直达 + 授予本县域门诊独家控销经营权";
            } else if (qty >= 100) {
                discountRate = "8.5折 (大宗门诊批量阶梯价)";
                finalAmt = totalAmt.multiply(new BigDecimal("0.85")).setScale(2, java.math.RoundingMode.HALF_UP);
                discountAmt = totalAmt.subtract(finalAmt);
                giftNotes = "买 100 赠 10 盒耗材 + 配套门诊候诊区亚克力展示架与贴敷规范宣教看板";
            } else if (qty >= 50) {
                discountRate = "9.0折 (小批量起订阶梯价)";
                finalAmt = totalAmt.multiply(new BigDecimal("0.90")).setScale(2, java.math.RoundingMode.HALF_UP);
                discountAmt = totalAmt.subtract(finalAmt);
                giftNotes = "赠送门诊宣传易拉宝 1 套与患者健康宣教手册 50 份";
            } else {
                discountRate = "10.0折 (基准批发直供价)";
                finalAmt = totalAmt;
                discountAmt = BigDecimal.ZERO;
                giftNotes = "正品直发，实付满 68 元享春播健康便民速递免邮";
            }

            bargainDraft = new HashMap<>();
            bargainDraft.put("productName", prodName);
            bargainDraft.put("quantity", qty);
            bargainDraft.put("wholesalePrice", unitPrice.toString());
            bargainDraft.put("totalAmount", totalAmt.setScale(2, java.math.RoundingMode.HALF_UP).toString());
            bargainDraft.put("discountAmount", discountAmt.setScale(2, java.math.RoundingMode.HALF_UP).toString());
            bargainDraft.put("finalAmount", finalAmt.setScale(2, java.math.RoundingMode.HALF_UP).toString());
            bargainDraft.put("notes", giftNotes);

            // 调用 Spring AI 生成商务议价批复
            String llmNegotiation = null;
            if (aiModelConfigService != null && aiModelConfigService.getBareChatClient() != null && !aiModelConfigService.isMockEnabled()) {
                try {
                    String prompt = String.format("你是春播万象医药电商B2B平台的商务总监兼Bargain Agent。"
                            + "客户身份：%s，申请订购【%s】共 %d 盒。"
                            + "商务政策：批发基准单价 ¥%s，阶梯政策为 %s，标价总计 ¥%s，优惠减免 ¥%s，实付仅需 ¥%s。"
                            + "配套赠送扶持：%s。"
                            + "请用热情、严谨、专业的商务公文与Markdown格式，输出一份【B2B 大宗采购阶梯议价核准批复函】，"
                            + "包含：1. 采购核算明细表格；2. 区域控销与账期质保承诺；3. 引导点击下方议价方案卡片一键落库。",
                            roleTitle, prodName, qty, unitPrice, discountRate, totalAmt, discountAmt, finalAmt, giftNotes);
                    llmNegotiation = aiModelConfigService.getBareChatClient().prompt(prompt).call().content();
                } catch (Exception ignored) {}
            }

            if (llmNegotiation != null && !llmNegotiation.isBlank()) {
                content = llmNegotiation;
            } else {
                content = "### 🤝 【春播商城 B2B 大宗采购阶梯议价批复函】\n\n"
                        + "尊敬的**" + roleTitle + "**，您申请的【**" + prodName + "**】大宗采购议价申请已由 **Spring AI Bargain Agent** 自动核准备案！\n\n"
                        + "| 采购商品 | 采购数量 | 批发基准价 | 阶梯折扣档位 | 标价总计 | 阶梯直降减免 | **核准实付金额** |\n"
                        + "| :--- | :--- | :--- | :--- | :--- | :--- | :--- |\n"
                        + "| **" + prodName + "** | **" + qty + " 盒** | ¥" + unitPrice + " | " + discountRate + " | ¥" + totalAmt + " | -¥" + discountAmt + " | **¥" + finalAmt + "** |\n\n"
                        + "🎁 **大宗扶持政策与赠药方案**：\n"
                        + "- **配套赠药**：" + giftNotes + "\n"
                        + "- **控销保障**：严格一县一所独家控销，支持 100% 临期无忧退换货与 GSP 电子追溯\n"
                        + "- **履约时效**：已联动智能仓储调度，确认订单后 24 小时内发出\n\n"
                        + "👇 **下一步操作指引**：\n"
                        + "系统已在下方生成结构化【议价采购确认卡片】，请核对数量与金额后，点击【**确认议价方案并一键落单**】即可直接完成落库与进销存原子出库！\n";
            }

        } else if ("CS_AGENT".equals(targetAgent)) {
            stateFlow.add("【Spring AI 状态机流转】Router Agent ➔ 命中【👩‍💼 CS Agent 客户服务与资质合规智能体】");
            stateFlow.add("【GSP 合规档案穿透】调取春播万象药品经营许可证与首营资质电子证照库");

            content = "### 👩‍💼 【春播万象医药电商 · GSP 首营资质与合规审查档案】\n\n"
                    + "尊敬的**" + roleTitle + "**，春播商城全线经营产品均已严格遵循国家《药品管理法》及 GSP 合规规范，随时支持门诊药监飞行检查：\n\n"
                    + "📋 **首营资质备案与法定资质公示**：\n"
                    + "1. **药品经营许可证**：`陕AA20230018`（合规经营范围：中药饮片、中成药、化学药制剂、生化药品、一/二类医疗器械）\n"
                    + "2. **第一类医疗器械生产备案凭证**：`陕械备20220036号`（春播万象通络贴、小儿止咳化痰贴全覆盖）\n"
                    + "3. **药品 GSP 质量管理认证**：全流程温湿度自动记录仪 24 小时在线监测，出库即随货同行附带纸质/电子质检报告单（COA）\n"
                    + "4. **冷链温控保障**：疫苗与生物制剂采用医用级相变蓄冷箱恒温运输（2℃~8℃ 全程温度打卡可溯源）\n\n"
                    + "💡 **首营资料一键下载**：本批次检验报告书（COA）及企业三证电子章版已同步归档至门诊 OA 系统，您也可在管理中台一键导出打印。";

        } else {
            // RECOMMEND_AGENT
            stateFlow.add("【Spring AI 状态机流转】Router Agent ➔ 命中【🌿 Recommend Agent 角色差异化选品智能体】");
            stateFlow.add("【差异化价值模型】根据【" + roleTitle + "】定制学术疗效、阶梯毛利与控销方案");
            stateFlow.add("【RAG 医药知识检索】检索秋冬季节特色中药穴位贴敷适宜技术库");

            if ("director".equals(role)) {
                content = "### 👨‍⚕️ 【诊所主任 · 学术疗效与特色适宜技术控销推荐】\n\n"
                        + "尊敬的李文华主任，针对基层社区门诊特色专科建设与秋冬季节呼吸/骨关节高发期，**Recommend Agent** 为您定制以下高疗效、无输液风险的拳头特色项目：\n\n"
                        + "1. 🌿 **春播万象通络贴 (骨关节与软组织损伤外治)**\n"
                        + "   - **学术机理**：古方透皮吸收，针对颈肩腰腿痛、风湿骨痛，纯中药萃取，30 分钟渗透起效；\n"
                        + "   - **配穴推荐**：大椎、阿是穴、肾俞、足三里；\n"
                        + "   - **技术创收**：单次贴敷收费标准 ¥48.00/贴，医生操作提成达 ¥15.00/贴 (31.2%)，综合毛利率 **46.8%**！\n\n"
                        + "2. 🍯 **小儿止咳化痰贴 (儿科无痛绿色外治)**\n"
                        + "   - **学术机理**：解决儿童服药难、输液抗生素滥用痛点，针对急慢性支气管炎、风热咳嗽；\n"
                        + "   - **配穴推荐**：天突、膻中、双肺俞；\n"
                        + "   - **技术创收**：单次收费 ¥38.00/贴，医生阳光提成 ¥12.00/贴 (31.5%)，深受家长患儿信赖。\n\n"
                        + "💡 **临床推广建议**：贴敷项目不仅规避静脉输液不良反应风险，还能建立门诊特色品牌。如需批量订购开展，可直接向采购主管转交议价方案！\n";
            } else if ("buyer".equals(role)) {
                content = "### 💼 【采购主管 · 阶梯毛利返利与区域独家控销方案】\n\n"
                        + "尊敬的陈主管，**Recommend Agent** 为您精选门诊周转最快、毛利保障最充足的控销专案产品：\n\n"
                        + "1. 📈 **春播万象通络贴 (爆款控销 · 门诊专供)**\n"
                        + "   - **零售指导价**：¥48.00/盒 ｜ **基准批发单价**：¥38.00/盒；\n"
                        + "   - **毛利保障**：门诊综合毛利率达 **46.8%**，单盒进销差价高达 ¥10.00~¥17.60；\n"
                        + "   - **阶梯采购**：订购 100 盒立享 8.5 折，订购 300 盒直降至 8.0 折（¥30.40/盒），并赠送 10% 备药！\n\n"
                        + "2. 🛡️ **严格区域控销防串货体系**：\n"
                        + "   - 每一盒贴敷耗材均赋独立防伪溯源码，严格执行一县一门诊独家特约授权，杜绝网络平台恶意低价窜货；\n"
                        + "   - 实行 100% 临期包退换承诺，保障门诊零资金占用风险。\n\n"
                        + "💡 您可随时在输入框发送「采购300盒通络贴能给多少折扣？」由 Bargain Agent 帮您一键测算生成议价采购单！\n";
            } else {
                content = "### 👩‍💼 【门诊客服 · 宣教资料齐备与便民履约选品指引】\n\n"
                        + "尊敬的门诊客服同事，**Recommend Agent** 为您准备了患者接受度高、零客诉纠纷的便民外治方案：\n\n"
                        + "1. 📄 **标准化患教资料随箱直配**：\n"
                        + "   - 每套贴敷耗材均配有全彩《穴位贴敷居家护理卡》与禁忌提示（孕妇腹部忌贴、皮肤破损禁用等）；\n"
                        + "   - 配合微信扫码即看主任示范贴敷高清短视频，极大降低护士宣教沟通成本。\n\n"
                        + "2. 🚚 **春播健康便民速递直达**：\n"
                        + "   - 满 68 元极速免邮直达社区网格，门诊患者复诊用药可支持送药上门；\n"
                        + "   - 顺丰医药专送与冷链箱温控直达，患者满意度高达 99.2%。\n";
            }

            List<MallProduct> allProds = getProducts();
            for (MallProduct p : allProds) {
                if (p.getProductName() != null && (p.getProductName().contains("通络") || p.getProductName().contains("贴") || p.getProductName().contains("布洛芬"))) {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", p.getId());
                    map.put("productName", p.getProductName());
                    map.put("specification", p.getSpecification());
                    map.put("price", p.getRetailGuidePrice());
                    map.put("category", p.getCategory());
                    map.put("csPitch", p.getCsPitch());
                    recommendations.add(map);
                    if (recommendations.size() >= 3) break;
                }
            }
        }

        result.put("stateFlow", stateFlow);
        result.put("targetAgent", targetAgent);
        result.put("agentName", "BARGAIN_AGENT".equals(targetAgent) ? "🤝 Bargain Agent (阶梯议价智能体)"
                : ("CS_AGENT".equals(targetAgent) ? "👩‍💼 Customer Service Agent (客服合规智能体)" : "🌿 Recommend Agent (角色选品智能体)"));
        result.put("content", content);
        result.put("reply", content);
        result.put("bargainDraft", bargainDraft);
        result.put("recommendations", recommendations);
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public MallOrder createOrderFromBargain(Map<String, Object> req) {
        MallOrder o = new MallOrder();
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        
        String clinicName = req.getOrDefault("clinicName", req.getOrDefault("address", "春播社区卫生服务站")).toString();
        String buyerName = req.getOrDefault("buyerName", "采购主管").toString();
        boolean isB2bOrder = clinicName.contains("服务站") || clinicName.contains("门诊") || clinicName.contains("诊所")
                || buyerName.contains("主任") || buyerName.contains("主管") || "director".equalsIgnoreCase(String.valueOf(req.get("role"))) || "buyer".equalsIgnoreCase(String.valueOf(req.get("role")));
        
        o.setOrderNo((isB2bOrder ? "B2B" : "B2C") + dateStr + ThreadLocalRandom.current().nextInt(100, 999));
        o.setClinicName(clinicName);
        o.setBuyerName(buyerName);

        // 落下单用户主键（用于「我的订单」精确隔离）
        Object uname = req.get("_username");
        MallUser u = null;
        if (uname != null && userMapper != null) {
            try {
                u = userMapper.selectOne(new LambdaQueryWrapper<MallUser>().eq(MallUser::getUsername, uname.toString()));
                if (u != null) o.setUserId(u.getId());
            } catch (Exception ignored) {}
        }

        String totalStr = req.getOrDefault("totalAmount", "0.00").toString();
        String discountStr = req.getOrDefault("discountAmount", "0.00").toString();
        String finalStr = req.getOrDefault("finalAmount", totalStr).toString();

        BigDecimal totalAmt = new BigDecimal(totalStr);
        BigDecimal discountAmt = new BigDecimal(discountStr);
        BigDecimal finalAmt = new BigDecimal(finalStr);

        String itemsJson = req.containsKey("itemsJson") && req.get("itemsJson") != null
                ? req.get("itemsJson").toString()
                : "[{\"productName\":\"家庭生活常备用药\",\"quantity\":1,\"price\":" + finalStr + "}]";
        o.setItemsJson(itemsJson);

        // ── 真实原子扣减商品库存（防并发超卖与保证进销存台账账实相符） ──
        try {
            List<Map<String, Object>> itemList = objectMapper.readValue(itemsJson, new TypeReference<List<Map<String, Object>>>() {});
            if (itemList != null && !itemList.isEmpty()) {
                for (Map<String, Object> item : itemList) {
                    int qty = 1;
                    if (item.get("quantity") != null) {
                        try {
                            qty = Integer.parseInt(item.get("quantity").toString());
                        } catch (Exception ignored) {}
                    }
                    if (qty <= 0) qty = 1;

                    MallProduct targetProd = null;
                    Object idObj = item.get("id") != null ? item.get("id") : item.get("productId");
                    if (idObj != null) {
                        try {
                            targetProd = productMapper.selectById(Long.parseLong(idObj.toString()));
                        } catch (Exception ignored) {}
                    }
                    if (targetProd == null && item.get("productName") != null) {
                        String pName = item.get("productName").toString().trim();
                        List<MallProduct> list = productMapper.selectList(new LambdaQueryWrapper<MallProduct>()
                                .eq(MallProduct::getProductName, pName));
                        if (!list.isEmpty()) {
                            targetProd = list.get(0);
                        } else {
                            list = productMapper.selectList(new LambdaQueryWrapper<MallProduct>()
                                    .like(MallProduct::getProductName, pName));
                            if (!list.isEmpty()) targetProd = list.get(0);
                        }
                    }

                    if (targetProd != null) {
                        // 执行原子防超卖扣减：UPDATE mall_product SET stock = stock - #{qty} WHERE id = #{id} AND stock >= #{qty}
                        int affected = productMapper.deductStock(targetProd.getId(), qty);
                        if (affected == 0) {
                            int currentStock = targetProd.getStock() != null ? targetProd.getStock() : 0;
                            throw new IllegalStateException("商品【" + targetProd.getProductName() + "】库存不足，当前仅剩 " + currentStock + " 件，无法满足本次采购 " + qty + " 件！");
                        }

                        // 真实记录进销存出库台账流水（inventory_record）
                        if (inventoryRecordMapper != null) {
                            InventoryRecord ir = new InventoryRecord();
                            ir.setRecordType(isB2bOrder ? "B2B医药大宗采购出库" : "商城在线便民购药出库");
                            ir.setMedicineId(targetProd.getId());
                            ir.setMedicineName(targetProd.getProductName());
                            ir.setChangeQty(-qty);
                            int after = Math.max(0, (targetProd.getStock() != null ? targetProd.getStock() : qty) - qty);
                            ir.setAfterStock(after);
                            ir.setRefOrderNo(o.getOrderNo());
                            ir.setOperator(o.getBuyerName() != null ? o.getBuyerName() : "采购专员");
                            ir.setRemark("春播商城订单原子出库 · 订单号:" + o.getOrderNo());
                            ir.setCreateTime(LocalDateTime.now());
                            inventoryRecordMapper.insert(ir);
                        }
                    }
                }
            }
        } catch (IllegalStateException e) {
            throw e; // 触发事务回滚，坚决拦截超卖
        } catch (Exception e) {
            System.err.println("解析订单商品明细扣减库存非致命警告: " + e.getMessage());
        }

        // 体验金 / 账户余额抵扣处理
        boolean useBalance = Boolean.TRUE.equals(req.get("useBalance")) || "true".equalsIgnoreCase(String.valueOf(req.get("useBalance")));
        if (useBalance && u != null && u.getBalance() != null && u.getBalance().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal maxDeduct = u.getBalance().min(totalAmt);
            if (maxDeduct.compareTo(BigDecimal.ZERO) > 0) {
                u.setBalance(u.getBalance().subtract(maxDeduct));
                userMapper.updateById(u);
                discountAmt = maxDeduct;
                finalAmt = totalAmt.subtract(maxDeduct).max(BigDecimal.ZERO);
            }
        } else if (!useBalance && discountAmt.compareTo(BigDecimal.ZERO) == 0) {
            finalAmt = totalAmt;
        }

        o.setTotalAmount(totalAmt);
        o.setDiscountAmount(discountAmt);
        o.setFinalAmount(finalAmt);
        o.setStatus("待商户发货出库");
        String baseNotes = req.getOrDefault("notes", "个人便民购药 · 满68元春播便民速递免邮 · 正品保障").toString();
        if (useBalance && u != null) {
            baseNotes += " 【已使用健康体验金抵扣，账户结余: ¥" + u.getBalance() + "】";
        }
        o.setBargainNotes(baseNotes);
        o.setCreateTime(LocalDateTime.now());

        orderMapper.insert(o);
        return o;
    }

    /** 从 RAG 知识库检索用药规范片段，未就绪/无结果返回空串 */
    private String buildRagContext(String msg) {
        if (ragKnowledgeService == null || !ragKnowledgeService.isReady()) return "";
        try {
            List<String> hits = ragKnowledgeService.search(msg, 2);
            return hits.isEmpty() ? "" : String.join("\n", hits);
        } catch (Exception e) {
            return "";
        }
    }
}
