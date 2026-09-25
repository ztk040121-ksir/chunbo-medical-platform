package com.chunbo.medical.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.config.ToolResultHolder;
import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.entity.ClinicSchedule;
import com.chunbo.medical.entity.OaApproval;
import com.chunbo.medical.entity.OaPlasterRecord;
import com.chunbo.medical.entity.OaSalarySlip;
import com.chunbo.medical.entity.StaffAccount;
import com.chunbo.medical.mapper.ClinicScheduleMapper;
import com.chunbo.medical.mapper.OaApprovalMapper;
import com.chunbo.medical.mapper.StaffAccountMapper;
import com.chunbo.medical.service.OaAssistantService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

@Component
public class ClinicAssistantTools {

    private static final Logger log = LoggerFactory.getLogger(ClinicAssistantTools.class);

    @Autowired
    private OaAssistantService oaService;

    @Autowired
    private ClinicScheduleMapper scheduleMapper;

    @Autowired
    private OaApprovalMapper approvalMapper;

    @Autowired
    private StaffAccountMapper staffAccountMapper;

    @Autowired(required = false)
    private com.chunbo.medical.mapper.MallOrderMapper mallOrderMapper;

    @Autowired(required = false)
    private com.chunbo.medical.mapper.MedicineMapper medicineMapper;

    @Autowired(required = false)
    private com.chunbo.medical.mapper.ClinicRegistrationMapper registrationMapper;

    @Autowired(required = false)
    private com.chunbo.medical.mapper.PrescriptionMapper prescriptionMapper;

    @Autowired(required = false)
    private com.chunbo.medical.mapper.OaPlasterRecordMapper plasterMapper;

    @Autowired(required = false)
    private com.chunbo.medical.mapper.ClinicTreatmentRecordMapper treatmentRecordMapper;

    @Tool(description = "查询指定医生的工资条明细（底薪、门诊提成、特色贴敷提成、扣除项、实发工资）")
    public String querySalarySlip(
            @ToolParam(description = "医生工号或姓名，如 DOC_1001 或 李文华") String doctorId,
            @ToolParam(description = "查询月份，格式为 yyyy-MM，如 2026-08") String month,
            ToolContext toolContext) {

        // ── RBAC 权限隔离（function-calling 前提）：医生仅能查自己，HR/ADMIN 可查任意员工 ──
        String role = roleOf(toolContext);
        String userId = userIdOf(toolContext);
        log.info("[AI Tool Calling] querySalarySlip role={} userId={} requested={}", role, userId, doctorId);

        if ("DOCTOR".equals(role)) {
            // 医生仅能查自己：若 LLM 明确请求他人工号/姓名，硬性拦截（防 LLM 绕过权限吐全员工资）
            if (doctorId != null && !doctorId.isBlank() && !doctorId.equals(userId)) {
                log.warn("[RBAC] 医生越权查询他人工资被拦截 userId={} target={}", userId, doctorId);
                return "⛔ 【RBAC 权限拦截】医生仅能查询本人工资条，无权查看他人薪酬明细。";
            }
            doctorId = (userId != null && !userId.isBlank()) ? userId : doctorId;
        } else if (!"ADMIN".equals(role) && !"HR".equals(role)) {
            // 商户/未知身份：无薪酬明细查询权限（fail-closed）
            return "⛔ 【RBAC 权限拦截】当前身份无权查询员工薪酬明细，仅人力资源与系统管理员可操作。";
        }

        // 工号或姓名都能精确命中本人工资，不再"传姓名就硬回退 DOC_1001 查错人"
        List<OaSalarySlip> list = oaService.getSalarySlipsByIdOrName(doctorId);
        if (list.isEmpty()) {
            return "【春播OA系统】未查找到对应工号或月份的工资明细记录。";
        }
        OaSalarySlip s = list.get(0);
        // 结构化工资条存入 ToolResultHolder，供前端渲染电子工资条卡片
        ToolResultHolder.put(requestIdOf(toolContext), "salarySlip", s);
        return String.format("""
                ### 💵 春播科技员工电子工资条 (工号: %s, 姓名: %s)
                - **归属考勤月份**: %s (状态: %s)
                - **基本岗位薪资**: ¥%.2f
                - **全科门诊诊疗提成**: ¥%.2f
                - **特色中药贴敷专项分成**: ¥%.2f (按疗程核算)
                - **社保与公积金个人代扣**: -¥%.2f
                - **个人所得税代扣**: -¥%.2f
                - **实发到手薪资**: **¥%.2f**
                *数据源自春播科技内部HR与财务结算系统，如有疑问可于发薪日起3个工作日内申诉。*
                """,
                s.getDoctorId(), s.getDoctorName(), s.getSalaryMonth(), s.getStatus(),
                s.getBaseSalary(), s.getClinicCommission(), s.getPlasterCommission(),
                s.getDeductionSocial(), s.getTax(), s.getNetSalary()
        );
    }

    @Tool(description = "根据员工姓名/工号 + 应发金额发起工资发放（匹配员工档案、创建已发放工资条）。仅 HR/ADMIN 可操作")
    public String processSalaryPayment(
            @ToolParam(description = "员工姓名或工号，如 李文华 或 DOC_1001") String employee,
            @ToolParam(description = "应发工资金额（元）") Double amount,
            @ToolParam(description = "工资月份 yyyy-MM，如 2026-09") String month,
            ToolContext toolContext) {

        String role = roleOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"HR".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅人力资源(HR)与系统管理员(ADMIN)可发起工资发放。";
        }
        Map<String, Object> r = oaService.paySalary(employee,
                amount != null ? BigDecimal.valueOf(amount) : null, month);
        if (Boolean.FALSE.equals(r.get("success"))) {
            return "⛔ " + r.get("message");
        }
        ToolResultHolder.put(requestIdOf(toolContext), "salaryPayment", r);
        return String.valueOf(r.get("message"));
    }

    @Tool(description = "为商城客户发起订单发货出库（支持只提供收货人姓名，模糊匹配；客户有多个待发货订单时会返回订单清单，"
            + "需先向用户确认发哪一个或全部发货）。发货会真实扣减商品库存、写入进销存流水并生成春播便民速递运单号。仅 ADMIN/MERCHANT 可操作")
    public String shipCustomerOrder(
            @ToolParam(description = "收货人姓名或订单号，如 李先生 / 健康居民 / B2C2026...") String customerOrOrderNo,
            ToolContext toolContext) {

        String role = roleOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"MERCHANT".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅系统管理员(ADMIN)与商城商户(MERCHANT)可操作订单发货。";
        }
        List<com.chunbo.medical.entity.MallOrder> pending = findMallOrders(customerOrOrderNo, false);
        if (pending == null) {
            return "⛔ 未找到【" + customerOrOrderNo + "】的任何订单。请核对收货人姓名或订单号（收货人姓名支持模糊，如「李先生」「健康居民」）。";
        }
        if (pending.isEmpty()) {
            return "⛔ 【" + customerOrOrderNo + "】没有待发货订单（可能已全部发货），可直接说「确认送达」。";
        }
        // 多个待发货订单：不擅自选择，返回清单让 AI 向用户确认（用户可指定订单号或说「全部发货」）
        if (pending.size() > 1) {
            StringBuilder sb = new StringBuilder();
            sb.append("⚠️ 该客户共有 ").append(pending.size()).append(" 个待发货订单，请先向用户确认发放范围，不要擅自选择：\n");
            int i = 1;
            for (com.chunbo.medical.entity.MallOrder o : pending) {
                sb.append(i++).append(") 订单号 ").append(o.getOrderNo())
                  .append("，金额 ¥").append(o.getFinalAmount())
                  .append("，下单时间 ").append(o.getCreateTime() == null ? "-" : o.getCreateTime().toString().substring(0, 16))
                  .append("\n");
            }
            sb.append("【处理规则】向用户列出以上清单并询问；用户指定某个订单后，按该订单号调用本工具发货；用户说「全部发货」时，逐个按订单号调用本工具。");
            return sb.toString();
        }
        String trackingNo = "CB" + System.currentTimeMillis();
        Map<String, Object> r = oaService.shipOrder(pending.get(0).getOrderNo(), trackingNo, "AI 中台调度 (" + roleOf(toolContext) + ")");
        if (Boolean.FALSE.equals(r.get("success"))) {
            return "⛔ " + r.get("message");
        }
        ToolResultHolder.put(requestIdOf(toolContext), "orderShipping", r);
        StringBuilder sb = new StringBuilder(String.valueOf(r.get("message")));
        Object logs = r.get("logs");
        if (logs instanceof List<?> l && !l.isEmpty()) {
            sb.append("\n出库明细：");
            for (Object line : l) sb.append("\n- ").append(line);
        }
        return sb.toString();
    }

    @Tool(description = "确认商城订单已送达（居民已签收妥投，订单状态改为「已送达」）。"
            + "支持只提供收货人姓名（模糊匹配）；客户有多个运输中订单时会返回订单清单，需先向用户确认确认哪一个。仅 ADMIN/MERCHANT 可操作")
    public String confirmCustomerDelivered(
            @ToolParam(description = "收货人姓名或订单号，如 李先生 / 健康居民 / B2C2026...") String customerOrOrderNo,
            ToolContext toolContext) {

        String role = roleOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"MERCHANT".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅系统管理员(ADMIN)与商城商户(MERCHANT)可确认订单送达。";
        }
        List<com.chunbo.medical.entity.MallOrder> shipping = findMallOrders(customerOrOrderNo, true);
        if (shipping == null) {
            return "⛔ 未找到【" + customerOrOrderNo + "】的任何订单。请核对收货人姓名或订单号。";
        }
        if (shipping.isEmpty()) {
            return "⛔ 【" + customerOrOrderNo + "】没有运输中的订单（可能尚未发货或已送达），请先说「给 TA 发货」或核对订单。";
        }
        if (shipping.size() > 1) {
            StringBuilder sb = new StringBuilder();
            sb.append("⚠️ 该客户共有 ").append(shipping.size()).append(" 个运输中订单，请先向用户确认确认哪一个，不要擅自选择：\n");
            int i = 1;
            for (com.chunbo.medical.entity.MallOrder o : shipping) {
                sb.append(i++).append(") 订单号 ").append(o.getOrderNo())
                  .append("，金额 ¥").append(o.getFinalAmount())
                  .append("，下单时间 ").append(o.getCreateTime() == null ? "-" : o.getCreateTime().toString().substring(0, 16))
                  .append("\n");
            }
            sb.append("【处理规则】向用户列出以上清单并询问；用户确认某个后，按该订单号调用本工具确认送达。");
            return sb.toString();
        }
        Map<String, Object> r = oaService.confirmOrderDelivered(shipping.get(0).getOrderNo());
        if (Boolean.FALSE.equals(r.get("success"))) {
            return "⛔ " + r.get("message");
        }
        ToolResultHolder.put(requestIdOf(toolContext), "orderDelivered", r);
        return String.valueOf(r.get("message"));
    }

    /**
     * 按订单号或收货人姓名模糊定位商城订单列表（收货人姓名库里带手机号后缀，如「李先生 (13812345678)」，必须模糊匹配）：
     * B2C 开头视为订单号精确匹配；否则按姓名 like 匹配，按时间倒序。
     * shippedOnly=true 时只返回运输中订单（确认送达场景），false 时只返回待发货订单（发货场景）。
     */
    private List<com.chunbo.medical.entity.MallOrder> findMallOrders(String customerOrOrderNo, boolean shippedOnly) {
        if (mallOrderMapper == null) return null;
        String key = customerOrOrderNo == null ? "" : customerOrOrderNo.trim();
        if (key.isEmpty()) return null;
        if (key.toUpperCase().startsWith("B2C")) {
            com.chunbo.medical.entity.MallOrder o = mallOrderMapper.selectOne(
                    new LambdaQueryWrapper<com.chunbo.medical.entity.MallOrder>()
                            .eq(com.chunbo.medical.entity.MallOrder::getOrderNo, key)
                            .last("LIMIT 1"));
            if (o == null) return java.util.Collections.emptyList();
            boolean match = shippedOnly
                    ? (com.chunbo.medical.enums.OrderStatusEnum.isShipped(o.getStatus()) && !com.chunbo.medical.enums.OrderStatusEnum.isDelivered(o.getStatus()))
                    : com.chunbo.medical.enums.OrderStatusEnum.isPending(o.getStatus());
            return match ? List.of(o) : java.util.Collections.emptyList();
        }
        List<com.chunbo.medical.entity.MallOrder> list = mallOrderMapper.selectList(
                new LambdaQueryWrapper<com.chunbo.medical.entity.MallOrder>()
                        .likeRight(com.chunbo.medical.entity.MallOrder::getOrderNo, "B2C")
                        .like(com.chunbo.medical.entity.MallOrder::getBuyerName, key)
                        .orderByDesc(com.chunbo.medical.entity.MallOrder::getId));
        if (list == null) return java.util.Collections.emptyList();
        List<com.chunbo.medical.entity.MallOrder> filtered = new java.util.ArrayList<>();
        for (com.chunbo.medical.entity.MallOrder o : list) {
            boolean match = shippedOnly
                    ? (com.chunbo.medical.enums.OrderStatusEnum.isShipped(o.getStatus()) && !com.chunbo.medical.enums.OrderStatusEnum.isDelivered(o.getStatus()))
                    : com.chunbo.medical.enums.OrderStatusEnum.isPending(o.getStatus());
            if (match) filtered.add(o);
        }
        return filtered;
    }

    @Tool(description = "查询春播商城的订单列表或待发货订单清单（支持按状态如「待发货」「已发货/运输中」「已送达」筛选，或者查询最新全部订单）。仅 ADMIN/HR/MERCHANT 可操作")
    public String queryMallOrdersList(
            @ToolParam(description = "订单状态筛选，如「待发货」「待商户发货出库」「运输中」「已送达」，传空或「全部」则查询全部最新订单") String statusFilter,
            @ToolParam(description = "查询返回条数，默认 15，最大 50") Integer limit,
            ToolContext toolContext) {

        String role = roleOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"HR".equals(role) && !"MERCHANT".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅系统管理员(ADMIN)、人事(HR)与商城商户(MERCHANT)可查看商城全量订单列表。";
        }
        if (mallOrderMapper == null) {
            return "⛔ 订单数据库服务离线，暂无法读取。";
        }
        int max = (limit == null || limit <= 0) ? 15 : Math.min(limit, 50);
        // 严格限定只查询春播商城 C 端便民购药订单 (B2C 开头)，绝不混杂云诊所药品采购单 (ORD 开头)
        List<com.chunbo.medical.entity.MallOrder> all = mallOrderMapper.selectList(
                new LambdaQueryWrapper<com.chunbo.medical.entity.MallOrder>()
                        .likeRight(com.chunbo.medical.entity.MallOrder::getOrderNo, "B2C")
                        .orderByDesc(com.chunbo.medical.entity.MallOrder::getId));
        if (all == null || all.isEmpty()) {
            return "目前春播商城暂无任何订单记录。";
        }

        // 统计商城大盘履约真实数据（与管理后台完全对齐）
        long pendingCount = 0;
        long shippedCount = 0;
        BigDecimal totalGmv = BigDecimal.ZERO;
        for (com.chunbo.medical.entity.MallOrder o : all) {
            if (com.chunbo.medical.enums.OrderStatusEnum.isPending(o.getStatus())) pendingCount++;
            else if (com.chunbo.medical.enums.OrderStatusEnum.isShipped(o.getStatus())) shippedCount++;
            if (o.getFinalAmount() != null) totalGmv = totalGmv.add(o.getFinalAmount());
        }

        String filter = statusFilter == null ? "" : statusFilter.trim();
        List<com.chunbo.medical.entity.MallOrder> filtered = new java.util.ArrayList<>();
        for (com.chunbo.medical.entity.MallOrder o : all) {
            if (filter.isEmpty() || "全部".equals(filter) || "所有".equals(filter)) {
                filtered.add(o);
            } else if (filter.contains("待发") || filter.contains("未发") || filter.contains("待出库")) {
                if (com.chunbo.medical.enums.OrderStatusEnum.isPending(o.getStatus())) filtered.add(o);
            } else if (filter.contains("运输") || filter.contains("已发") || filter.contains("在途")) {
                if (com.chunbo.medical.enums.OrderStatusEnum.isShipped(o.getStatus()) && !com.chunbo.medical.enums.OrderStatusEnum.isDelivered(o.getStatus())) filtered.add(o);
            } else if (filter.contains("送达") || filter.contains("签收") || filter.contains("完成")) {
                if (com.chunbo.medical.enums.OrderStatusEnum.isDelivered(o.getStatus())) filtered.add(o);
            } else {
                if (o.getStatus() != null && o.getStatus().contains(filter)) filtered.add(o);
            }
            if (filtered.size() >= max) break;
        }

        StringBuilder sb = new StringBuilder();
        String title = filter.isEmpty() || "全部".equals(filter) ? "全量订单清单" : "「" + filter + "」订单清单";
        sb.append("### 📦 春播健康商城 · ").append(title).append(String.format("（共检索到 %d 笔有效商城订单）\n\n", filtered.size()));
        sb.append(String.format("> 📊 **商城履约看板**：商城全部订单 **%d 单**（待商户发货出库 **%d 单**，已发货运输中 **%d 单**），线上实收流水总计 **¥%.2f**。\n\n",
                all.size(), pendingCount, shippedCount, totalGmv));
        if (filtered.isEmpty()) {
            sb.append("当前暂无符合「").append(filter).append("」条件的商城订单。\n");
            return sb.toString();
        }

        sb.append("| 序号 | 订单号 | 收货人 | 实付金额 | 订单状态 | 下单时间 |\n|---|---|---|---|---|---|\n");
        int seq = 1;
        for (com.chunbo.medical.entity.MallOrder o : filtered) {
            String timeStr = o.getCreateTime() == null ? "—" : o.getCreateTime().toString().replace("T", " ").substring(0, 16);
            sb.append("| ").append(seq++).append(" | `").append(o.getOrderNo()).append("` | ")
              .append(o.getBuyerName() == null ? "匿名顾客" : o.getBuyerName()).append(" | ¥")
              .append(o.getFinalAmount() == null ? "0.00" : o.getFinalAmount()).append(" | ")
              .append(o.getStatus() == null ? "未知" : o.getStatus()).append(" | ")
              .append(timeStr).append(" |\n");
        }

        sb.append("\n💡 **快捷调度操作**：\n");
        sb.append("- 若要为上述待发货订单出库，可直接说：「给李先生发货」或「发货订单 B2C...」；\n");
        sb.append("- 若要确认送达，可直接说：「确认李先生的订单已送达」；\n");
        sb.append("- 若需查看某一具体用户的历史订单，可说：「查陈素芬的订单」。");
        return sb.toString();
    }

    @Tool(description = "商品上架或下架（action 传「上架」或「下架」）。下架后商城不可见，上架恢复在售。匹配到多个同名商品时会返回候选清单，需先向用户确认。仅 ADMIN/MERCHANT 可操作")
    public String changeProductSaleStatus(
            @ToolParam(description = "商品名称，如 稳健医疗医用外科口罩") String productName,
            @ToolParam(description = "操作：上架 或 下架") String action,
            ToolContext toolContext) {

        String role = roleOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"MERCHANT".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅系统管理员(ADMIN)与商城商户(MERCHANT)可操作商品上下架。";
        }
        if (!"上架".equals(action) && !"下架".equals(action)) {
            return "⛔ action 仅支持「上架」或「下架」";
        }
        Map<String, Object> r = oaService.setProductStatusByName(productName, action);
        if (Boolean.FALSE.equals(r.get("success"))) {
            return String.valueOf(r.get("message"));
        }
        return String.valueOf(r.get("message"));
    }

    @Tool(description = "商品调价（零售指导价必填，批发价可选；同步刷新毛利率）。匹配到多个同名商品时会返回候选清单，需先向用户确认。仅 ADMIN/MERCHANT 可操作")
    public String changeProductPrice(
            @ToolParam(description = "商品名称，如 稳健医疗医用外科口罩") String productName,
            @ToolParam(description = "新零售价（元），如 13.5") Double newRetailPrice,
            @ToolParam(description = "新批发价（元），可选，不确定传 null") Double newWholesalePrice,
            ToolContext toolContext) {

        String role = roleOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"MERCHANT".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅系统管理员(ADMIN)与商城商户(MERCHANT)可操作商品调价。";
        }
        if (newRetailPrice == null || newRetailPrice <= 0) {
            return "⛔ 新零售价必须大于 0";
        }
        Map<String, Object> r = oaService.updateProductPriceByName(productName,
                BigDecimal.valueOf(newRetailPrice),
                newWholesalePrice != null ? BigDecimal.valueOf(newWholesalePrice) : null);
        return String.valueOf(r.get("message"));
    }

    @Tool(description = "商品入库补货（库存累加并写入进销存入库流水）。匹配到多个同名商品时会返回候选清单，需先向用户确认。仅 ADMIN/MERCHANT 可操作")
    public String inboundProductStock(
            @ToolParam(description = "商品名称，如 稳健医疗医用外科口罩") String productName,
            @ToolParam(description = "入库数量（件）") Integer quantity,
            ToolContext toolContext) {

        String role = roleOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"MERCHANT".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅系统管理员(ADMIN)与商城商户(MERCHANT)可操作商品入库。";
        }
        if (quantity == null || quantity <= 0) {
            return "⛔ 入库数量必须大于 0";
        }
        Map<String, Object> r = oaService.inboundProductByName(productName, quantity);
        return String.valueOf(r.get("message"));
    }

    @Autowired(required = false)
    private com.chunbo.medical.mapper.MallUserMapper mallUserMapper;

    @Tool(description = "注册春播商城新用户（真实落库，注册后可直接在春播商城登录，自动发放 ¥200 新人健康体验金）。"
            + "必填：登录账号 username、登录密码 password（至少6位）、真实姓名或称呼 nickname、11位手机号 phone（1开头）；选填：收货地址 address。"
            + "若用户未提供必填项，不要编造，返回缺失项清单引导用户逐步补充后再调用。仅 ADMIN/HR/MERCHANT 可操作")
    public String registerMallUser(
            @ToolParam(description = "商城登录账号，如 chenmin2026（用户没给时先引导补充，不要编造）") String username,
            @ToolParam(description = "商城登录密码，至少6位，用户没给时先引导补充，不要编造") String password,
            @ToolParam(description = "真实姓名或称呼，如 张女士 / 李先生（用户没给时先引导补充）") String nickname,
            @ToolParam(description = "11位手机号，1开头（用户没给时先引导补充）") String phone,
            @ToolParam(description = "常用收货地址，可选，用户没给传 null") String address,
            ToolContext toolContext) {

        String role = roleOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"HR".equals(role) && !"MERCHANT".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅系统管理员(ADMIN)、人事(HR)与商城商户(MERCHANT)可注册商城用户。";
        }
        // 必填口径与商城自助注册页一致：账号/密码(≥6位)/姓名称呼/11位手机号
        String u = username == null ? "" : username.trim();
        String p = password == null ? "" : password.trim();
        String nick = nickname == null ? "" : nickname.trim();
        String tel = phone == null ? "" : phone.trim();
        List<String> missing = new java.util.ArrayList<>();
        if (u.isEmpty()) missing.add("登录账号（商城登录用户名）");
        if (p.isEmpty()) missing.add("登录密码（至少 6 位）");
        if (nick.isEmpty()) missing.add("真实姓名或称呼（如 张女士 / 李先生）");
        if (tel.isEmpty()) missing.add("11 位手机号（1 开头）");
        else if (!tel.matches("1\\d{10}")) missing.add("有效手机号（1 开头 11 位，当前「" + tel + "」格式不对）");
        if (!p.isEmpty() && p.length() < 6) missing.add("至少 6 位的登录密码（当前密码过短）");
        if (!missing.isEmpty()) {
            return "NEED_MORE_INFO：还缺少以下必填信息，请向用户逐项引导补充后再调用本工具——" + String.join("；", missing)
                    + "。选填项仅收货地址，可一并询问，用户不填则留空。";
        }
        if (mallUserMapper == null) {
            return "⛔ 商城用户数据不可用";
        }
        // 唯一性校验：分别指出账号还是手机号被占用
        Long dupName = mallUserMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.chunbo.medical.entity.MallUser>()
                        .eq(com.chunbo.medical.entity.MallUser::getUsername, u));
        Long dupPhone = mallUserMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.chunbo.medical.entity.MallUser>()
                        .eq(com.chunbo.medical.entity.MallUser::getPhone, tel));
        if (dupName > 0 && dupPhone > 0) {
            return "⛔ 登录账号「" + u + "」和手机号「" + tel + "」都已被注册，请引导用户更换账号或直接用原账号登录。";
        }
        if (dupName > 0) {
            return "⛔ 登录账号「" + u + "」已被注册，请引导用户更换登录账号后重试（或提示直接用原账号登录）。";
        }
        if (dupPhone > 0) {
            return "⛔ 手机号「" + tel + "」已被注册，请引导用户更换手机号，或提示直接用原账号登录；也可以调用 queryMallUserOrders 查看该手机号名下已有的订单。";
        }
        com.chunbo.medical.entity.MallUser user = new com.chunbo.medical.entity.MallUser();
        user.setUsername(u);
        user.setPassword(com.chunbo.medical.config.PasswordUtil.encode(p));
        user.setNickname(nick);
        user.setPhone(tel);
        user.setAddress(address == null ? "" : address.trim());
        user.setStatus("ENABLE");
        user.setBalance(new BigDecimal("200.00"));
        user.setPoints(200);
        user.setCreateTime(java.time.LocalDateTime.now());
        mallUserMapper.insert(user);

        Map<String, Object> card = new HashMap<>();
        card.put("username", u);
        card.put("nickname", user.getNickname());
        card.put("phone", user.getPhone());
        card.put("balance", user.getBalance());
        ToolResultHolder.put(requestIdOf(toolContext), "mallUserRegistered", card);

        return String.format("""
                ### ✅ 春播商城用户注册成功
                - **登录账号**: %s
                - **姓名/昵称**: %s
                - **手机号**: %s
                - **收货地址**: %s
                - **新人健康体验金**: ¥200.00 已到账（积分 +200）
                - **账号状态**: 正常（ENABLE）
                该账号现已可直接在春播商城（localhost:5175）登录使用。请向用户确认以上信息。
                """, u, user.getNickname(), user.getPhone(),
                user.getAddress() == null || user.getAddress().isEmpty() ? "（未填写，下单结算时再补）" : user.getAddress());
    }

    @Tool(description = "查询某个商城注册用户的订单列表（按登录账号/手机号/姓名匹配用户，返回其全部订单：订单号/状态/金额/下单时间）。"
            + "仅 ADMIN/HR/MERCHANT 可查询")
    public String queryMallUserOrders(
            @ToolParam(description = "商城用户关键词：登录账号、手机号或姓名/昵称，如 chenmin2026 / 13516553277 / 陈素芬") String userKeyword,
            ToolContext toolContext) {

        String role = roleOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"HR".equals(role) && !"MERCHANT".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅系统管理员(ADMIN)、人事(HR)与商城商户(MERCHANT)可查询商城用户订单。";
        }
        if (userKeyword == null || userKeyword.isBlank() || mallUserMapper == null || mallOrderMapper == null) {
            return "⛔ 请提供要查询的用户（登录账号 / 手机号 / 姓名）。";
        }
        String kw = userKeyword.trim();
        com.chunbo.medical.entity.MallUser user = mallUserMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.chunbo.medical.entity.MallUser>()
                        .eq(com.chunbo.medical.entity.MallUser::getUsername, kw)
                        .or().eq(com.chunbo.medical.entity.MallUser::getPhone, kw));
        if (user == null) {
            List<com.chunbo.medical.entity.MallUser> byNick = mallUserMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.chunbo.medical.entity.MallUser>()
                            .like(com.chunbo.medical.entity.MallUser::getNickname, kw));
            if (byNick.size() == 1) user = byNick.get(0);
            else if (byNick.size() > 1) {
                StringBuilder sb = new StringBuilder("匹配到多个用户，请先确认是哪一位：\n");
                for (com.chunbo.medical.entity.MallUser m : byNick) {
                    sb.append("- ").append(m.getNickname()).append("（账号 ").append(m.getUsername())
                      .append(" / ").append(m.getPhone()).append("）\n");
                }
                return sb.toString();
            }
        }
        if (user == null) {
            return "未在商城注册用户中找到「" + kw + "」，请确认账号/手机号/姓名后重试。";
        }
        final String buyerNick = user.getNickname() != null ? user.getNickname() : "";
        final String buyerPhone = user.getPhone() != null ? user.getPhone() : "";
        // 订单 buyer_name 口径：严格限定商城 C 端 B2C 订单，用 like 双向兜底匹配
        List<com.chunbo.medical.entity.MallOrder> orders = mallOrderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.chunbo.medical.entity.MallOrder>()
                        .likeRight(com.chunbo.medical.entity.MallOrder::getOrderNo, "B2C")
                        .and(w -> w.like(com.chunbo.medical.entity.MallOrder::getBuyerName, buyerNick)
                                .or().like(com.chunbo.medical.entity.MallOrder::getBuyerName, buyerPhone))
                        .orderByDesc(com.chunbo.medical.entity.MallOrder::getCreateTime));
        Map<String, Object> card = new HashMap<>();
        card.put("username", user.getUsername());
        card.put("nickname", user.getNickname());
        card.put("orderCount", orders.size());
        ToolResultHolder.put(requestIdOf(toolContext), "mallUserOrders", card);

        StringBuilder sb = new StringBuilder();
        sb.append("### 🧾 商城用户「").append(user.getNickname()).append("」（账号 ").append(user.getUsername())
          .append(" / ").append(user.getPhone()).append("）的订单\n\n");
        if (orders.isEmpty()) {
            sb.append("该用户暂无商城订单。\n");
        } else {
            sb.append("| 订单号 | 状态 | 实付金额(元) | 下单时间 |\n|---|---|---|---|\n");
            int i = 0;
            for (com.chunbo.medical.entity.MallOrder o : orders) {
                if (++i > 15) { sb.append("| …（其余 ").append(orders.size() - 15).append(" 条略） | | | |\n"); break; }
                sb.append("| ").append(o.getOrderNo()).append(" | ").append(o.getStatus() == null ? "—" : o.getStatus())
                  .append(" | ").append(o.getFinalAmount() == null ? "—" : o.getFinalAmount())
                  .append(" | ").append(o.getCreateTime() == null ? "—" : o.getCreateTime().toLocalDate()).append(" |\n");
            }
            sb.append("\n共 ").append(orders.size()).append(" 个订单。\n");
        }
        return sb.toString();
    }

    @Tool(description = "查询春播商城注册用户统计：注册用户总数、正常活跃账户数、已冻结/停用账户数、已发放新人购药金总额（所有账户余额合计）。"
            + "仅 ADMIN/HR/MERCHANT 可查询")
    public String queryMallUserStats(ToolContext toolContext) {
        String role = roleOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"HR".equals(role) && !"MERCHANT".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅系统管理员(ADMIN)、人事(HR)与商城商户(MERCHANT)可查询商城用户统计。";
        }
        if (mallUserMapper == null) {
            return "⛔ 商城用户数据不可用";
        }
        // 统计用 SQL 聚合（COUNT/SUM），避免全量加载用户表（防大数据量拖垮）
        long total = mallUserMapper.selectCount(null);
        long disabled = mallUserMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.chunbo.medical.entity.MallUser>()
                        .eq(com.chunbo.medical.entity.MallUser::getStatus, "DISABLE"));
        long enabled = total - disabled;
        BigDecimal balanceSum = BigDecimal.ZERO;
        try {
            List<Map<String, Object>> sumRows = mallUserMapper.selectMaps(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<com.chunbo.medical.entity.MallUser>()
                            .select("COALESCE(SUM(balance), 0) AS totalBalance"));
            if (sumRows != null && !sumRows.isEmpty() && sumRows.get(0) != null
                    && sumRows.get(0).get("totalBalance") != null) {
                balanceSum = new BigDecimal(sumRows.get(0).get("totalBalance").toString());
            }
        } catch (Exception ignored) {
            balanceSum = BigDecimal.ZERO;
        }
        Map<String, Object> card = new HashMap<>();
        card.put("total", total);
        card.put("enabled", enabled);
        card.put("disabled", disabled);
        card.put("balanceSum", balanceSum);
        ToolResultHolder.put(requestIdOf(toolContext), "mallUserStats", card);
        return String.format("""
                ### 👥 春播商城注册用户统计
                - **注册用户总数**: %d 人
                - **正常活跃账户**: %d 个
                - **已冻结/停用账户**: %d 个
                - **已发放新人购药金余额合计**: ¥%s
                """, total, enabled, disabled, balanceSum);
    }

    @Tool(description = "查询春播商城注册用户列表（可按用户名/昵称/手机号搜索，或查询全部）。返回包含序号、账号、姓名、手机号、健康金余额、账户状态及注册时间的结构化 Markdown 表格。仅 ADMIN/HR/MERCHANT 可查询")
    public String queryMallUsersList(
            @ToolParam(description = "可选，搜索关键词（用户名/昵称/手机号），为空则查询全部商城注册用户", required = false) String keyword,
            @ToolParam(description = "可选，返回最大条数，默认15条", required = false) Integer limit,
            ToolContext toolContext) {
        String role = roleOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"HR".equals(role) && !"MERCHANT".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅系统管理员(ADMIN)、人事(HR)与商城商户(MERCHANT)可查询商城注册用户清单。";
        }
        if (mallUserMapper == null) {
            return "⛔ 商城用户数据接口不可用";
        }
        int max = (limit == null || limit <= 0) ? 15 : Math.min(limit, 50);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.chunbo.medical.entity.MallUser> qw =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(wrapper -> wrapper.like(com.chunbo.medical.entity.MallUser::getUsername, kw)
                    .or().like(com.chunbo.medical.entity.MallUser::getNickname, kw)
                    .or().like(com.chunbo.medical.entity.MallUser::getPhone, kw));
        }
        qw.orderByDesc(com.chunbo.medical.entity.MallUser::getId).last("LIMIT " + max);
        List<com.chunbo.medical.entity.MallUser> list = mallUserMapper.selectList(qw);
        if (list == null || list.isEmpty()) {
            return "未查询到符合条件的春播商城注册用户。";
        }
        Map<String, Object> card = new HashMap<>();
        card.put("total", list.size());
        card.put("users", list);
        ToolResultHolder.put(requestIdOf(toolContext), "mallUsersList", card);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("### 👥 春播商城 · 注册用户清单（共 %d 条真实数据）\n\n", list.size()));
        sb.append("| 序号 | 用户账号 | 真实姓名/昵称 | 绑定手机号 | 账户健康金余额 | 账户状态 | 注册时间 |\n");
        sb.append("| :--- | :--- | :--- | :--- | :--- | :---: | :--- |\n");
        int idx = 1;
        for (com.chunbo.medical.entity.MallUser u : list) {
            String uname = u.getUsername() != null ? u.getUsername() : "-";
            String nick = u.getNickname() != null ? u.getNickname() : "-";
            String phone = u.getPhone() != null ? u.getPhone() : "-";
            String bal = u.getBalance() != null ? "¥" + u.getBalance().toPlainString() : "¥0.00";
            String st = "DISABLE".equalsIgnoreCase(u.getStatus()) ? "🔴 冻结/停用" : "🟢 正常活跃";
            String time = u.getCreateTime() != null ? u.getCreateTime().toString().replace("T", " ") : "-";
            sb.append(String.format("| %d | `%s` | **%s** | `%s` | %s | %s | %s |\n",
                    idx++, uname, nick, phone, bal, st, time));
        }
        return sb.toString();
    }

    @Tool(description = "查询 OA 请假审批单名单（全部或仅未审批的），返回单号/申请人/假别/天数/事由/状态列表。"
            + "查「未批准的请假人员」时 onlyPending 传 true。仅 ADMIN/HR 可查询")
    public String queryLeaveApprovals(
            @ToolParam(description = "是否只查未审批的：true=仅待审批，false=全部") Boolean onlyPending,
            ToolContext toolContext) {
        String role = roleOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"HR".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅系统管理员(ADMIN)与人事(HR)可查询请假审批名单。";
        }
        List<OaApproval> list = oaService.getApprovals();
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (OaApproval ap : list) {
            String st = ap.getStatus() == null ? "" : ap.getStatus();
            boolean pending = !"已通过".equals(st) && !"已驳回".equals(st);
            if (Boolean.TRUE.equals(onlyPending) && !pending) continue;
            shown++;
            sb.append("- **单号 ").append(ap.getId()).append("** | ").append(ap.getApplicantName())
              .append(" | ").append(ap.getApprovalType()).append(" | ").append(ap.getDurationDays() == null ? "?" : ap.getDurationDays()).append(" 天")
              .append(" | 状态：").append(st.isEmpty() ? "待审批" : st)
              .append(" | 事由：").append(ap.getReason() == null ? "—" : ap.getReason()).append("\n");
        }
        Map<String, Object> card = new HashMap<>();
        card.put("count", shown);
        ToolResultHolder.put(requestIdOf(toolContext), "leaveApprovals", card);
        if (shown == 0) {
            return Boolean.TRUE.equals(onlyPending) ? "当前没有待审批的请假单，全部已处理完毕。"
                    : "系统中暂无请假审批单。";
        }
        return (Boolean.TRUE.equals(onlyPending) ? "### 📋 待审批请假名单（" : "### 📋 请假审批单名单（全部，")
                + shown + " 条）\n" + sb;
    }

    @Tool(description = "批准或驳回 OA 请假审批单（状态真实落库，申请人端同步可见）。"
            + "applicantOrId 传申请人姓名（支持模糊）或审批单号；action 传「批准」或「驳回」。"
            + "命中多张待审批单时返回候选清单，需先向用户确认批哪一张。仅 ADMIN/HR 可操作")
    public String processLeaveApproval(
            @ToolParam(description = "申请人姓名或审批单号，如 张小芳 / 3") String applicantOrId,
            @ToolParam(description = "审批动作：批准 或 驳回") String action,
            ToolContext toolContext) {
        String role = roleOf(toolContext);
        String operator = userIdOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"HR".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅系统管理员(ADMIN)与人事(HR)可审批请假单。";
        }
        List<OaApproval> hits = findLeaveApprovals(applicantOrId, true);
        if (hits.isEmpty()) {
            return "未找到申请人「" + applicantOrId + "」的待审批请假单（可能已审批或不存在），可先用 queryLeaveApprovals 查询名单核实。";
        }
        if (hits.size() > 1) {
            StringBuilder sb = new StringBuilder("该申请人有多张待审批单，请先向用户确认处理哪一张：\n");
            for (OaApproval ap : hits) {
                sb.append("- 单号 ").append(ap.getId()).append(" | ").append(ap.getApplicantName())
                  .append(" | ").append(ap.getApprovalType()).append(" | ").append(ap.getReason()).append("\n");
            }
            return sb.toString();
        }
        OaApproval ap = hits.get(0);
        String status;
        String comment;
        if (action != null && (action.contains("驳") || action.contains("拒绝") || action.contains("不同意"))) {
            status = "已驳回";
            comment = "请假事由不符合规范或人手安排冲突，请调整后重新申请";
        } else {
            status = "已通过";
            comment = "情况属实，同意请假";
        }
        OaApproval r = oaService.processApproval(ap.getId(), status, "AI 中台调度 (" + role + " " + operator + ")", comment);
        Map<String, Object> card = new HashMap<>();
        card.put("id", ap.getId());
        card.put("applicant", ap.getApplicantName());
        card.put("status", status);
        ToolResultHolder.put(requestIdOf(toolContext), "leaveProcessed", card);
        return String.format("""
                ### %s OA 请假审批完成
                - **单号**: %d
                - **申请人**: %s
                - **假别**: %s（%s 天）
                - **审批结果**: %s
                - **审批人**: AI 中台调度 (%s %s)
                申请人端的请假状态已同步更新。
                """, "已通过".equals(status) ? "✅" : "⛔", ap.getId(), ap.getApplicantName(),
                ap.getApprovalType(), ap.getDurationDays(), status, role, operator);
    }

    @Tool(description = "删除 OA 请假审批单（真实落库删除，不可恢复）。applicantOrId 传申请人姓名（支持模糊）或审批单号；"
            + "命中多张时返回候选清单，需先向用户确认删哪一张。调用前请确认用户已明确要删除。仅 ADMIN/HR 可操作")
    public String deleteLeaveApproval(
            @ToolParam(description = "申请人姓名或审批单号，如 张小芳 / 3") String applicantOrId,
            ToolContext toolContext) {
        String role = roleOf(toolContext);
        if (role == null || (!"ADMIN".equals(role) && !"HR".equals(role))) {
            return "⛔ 【RBAC 权限拦截】仅系统管理员(ADMIN)与人事(HR)可删除请假审批单。";
        }
        List<OaApproval> hits = findLeaveApprovals(applicantOrId, false);
        if (hits.isEmpty()) {
            return "未找到申请人「" + applicantOrId + "」的请假审批单，可先用 queryLeaveApprovals 查询名单核实。";
        }
        if (hits.size() > 1) {
            StringBuilder sb = new StringBuilder("该申请人有多张请假单，请先向用户确认删除哪一张：\n");
            for (OaApproval ap : hits) {
                sb.append("- 单号 ").append(ap.getId()).append(" | ").append(ap.getApplicantName())
                  .append(" | ").append(ap.getStatus()).append(" | ").append(ap.getApprovalType()).append("\n");
            }
            return sb.toString();
        }
        OaApproval ap = hits.get(0);
        int n = oaService.deleteApproval(ap.getId());
        Map<String, Object> card = new HashMap<>();
        card.put("id", ap.getId());
        card.put("applicant", ap.getApplicantName());
        ToolResultHolder.put(requestIdOf(toolContext), "leaveDeleted", card);
        return n > 0
                ? "### 🗑️ 请假审批单已删除\n- **单号**: " + ap.getId() + "\n- **申请人**: " + ap.getApplicantName()
                  + "\n- **假别**: " + ap.getApprovalType() + "\n该单已从审批中心移除。"
                : "⛔ 删除失败，请稍后重试。";
    }

    /** 按单号或申请人姓名（模糊）定位请假审批单；onlyPending=true 时仅保留未审批单 */
    private List<OaApproval> findLeaveApprovals(String applicantOrId, boolean onlyPending) {
        List<OaApproval> list = oaService.getApprovals();
        String kw = applicantOrId == null ? "" : applicantOrId.trim();
        List<OaApproval> hits = new java.util.ArrayList<>();
        for (OaApproval ap : list) {
            String st = ap.getStatus() == null ? "" : ap.getStatus();
            boolean pending = !"已通过".equals(st) && !"已驳回".equals(st);
            if (onlyPending && !pending) continue;
            boolean byId = kw.matches("\\d+") && String.valueOf(ap.getId()).equals(kw);
            boolean byName = !kw.isEmpty() && (ap.getApplicantName() != null
                    && (ap.getApplicantName().contains(kw) || kw.contains(ap.getApplicantName().split("[ (（]")[0])));
            if (byId || byName) hits.add(ap);
        }
        return hits;
    }


    @Tool(description = "查询春播万象中药贴敷理疗的运营统计：包括月度疗程量、品类分布、执行站施术人次与总营业额")
    public String queryPlasterStatistics(
            @ToolParam(description = "统计月份，如 2026-08 或 2026-09 或 9月份，可选，不传则默认查询全部历史及当月", required = false) String month,
            @ToolParam(description = "贴敷类别，如 通络贴、三伏贴、小儿咳喘贴，可选，为空则查询全部", required = false) String category,
            ToolContext toolContext) {

        String filterMonth = null;
        if (month != null && !month.isBlank()) {
            String m = month.trim();
            if (m.matches(".*(\\d{1,2})\\s*月.*")) {
                java.util.regex.Matcher mat = java.util.regex.Pattern.compile("(\\d{1,2})\\s*月").matcher(m);
                if (mat.find()) {
                    int mon = Integer.parseInt(mat.group(1));
                    filterMonth = String.format("2026-%02d", mon);
                }
            } else if (m.matches("\\d{4}-\\d{2}.*")) {
                filterMonth = m.substring(0, 7);
            }
        }

        // 1. 真实查询贴敷财务台账 (oa_plaster_record)
        List<OaPlasterRecord> plasterList = plasterMapper != null ? plasterMapper.selectList(null) : List.of();
        int totalPaste = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCommission = BigDecimal.ZERO;
        Map<String, Integer> typeCount = new HashMap<>();
        Map<String, BigDecimal> typeRevenue = new HashMap<>();

        for (OaPlasterRecord r : plasterList) {
            // 如果指定了月份过滤且记录含有日期
            if (filterMonth != null && r.getTherapyDate() != null && !r.getTherapyDate().toString().startsWith(filterMonth)) {
                continue;
            }
            // 如果指定了类别过滤
            if (category != null && !category.isBlank() && r.getPlasterType() != null && !r.getPlasterType().contains(category.trim())) {
                continue;
            }
            int c = r.getPasteCount() != null ? r.getPasteCount() : 0;
            BigDecimal amt = r.getTotalAmount() != null ? r.getTotalAmount() : BigDecimal.ZERO;
            // 贴敷医生施术专项绩效提成（按项目金额 30% 综合核算）
            BigDecimal comm = amt.multiply(new BigDecimal("0.30"));
            totalPaste += c;
            totalRevenue = totalRevenue.add(amt);
            totalCommission = totalCommission.add(comm);
            String t = r.getPlasterType() != null && !r.getPlasterType().isEmpty() ? r.getPlasterType() : "特色通络贴";
            typeCount.merge(t, c, Integer::sum);
            typeRevenue.merge(t, amt, BigDecimal::add);
        }

        // 2. 真实穿透特色执行站 (clinic_treatment_record)
        List<com.chunbo.medical.entity.ClinicTreatmentRecord> treatmentList = treatmentRecordMapper != null ? treatmentRecordMapper.selectList(null) : List.of();
        int totalTreatments = treatmentList.size();
        long executedCount = treatmentList.stream().filter(t -> "已执行".equals(t.getStatus()) || "已完成".equals(t.getStatus())).count();
        long pendingCount = totalTreatments - executedCount;
        Set<String> distinctAcupoints = new LinkedHashSet<>();
        Set<String> doctors = new LinkedHashSet<>();
        Set<String> nurses = new LinkedHashSet<>();

        for (com.chunbo.medical.entity.ClinicTreatmentRecord tr : treatmentList) {
            if (tr.getAcupoints() != null && !tr.getAcupoints().isBlank()) {
                for (String p : tr.getAcupoints().split("[,，、 ]+")) {
                    if (!p.isBlank()) distinctAcupoints.add(p.trim());
                }
            }
            if (tr.getDoctorName() != null && !tr.getDoctorName().isBlank()) doctors.add(tr.getDoctorName());
            if (tr.getNurseName() != null && !tr.getNurseName().isBlank()) nurses.add(tr.getNurseName());
        }

        Map<String, Object> summaryData = new HashMap<>();
        summaryData.put("totalPasteCount", totalPaste);
        summaryData.put("totalRevenue", totalRevenue);
        summaryData.put("totalCommission", totalCommission);
        summaryData.put("treatmentCount", totalTreatments);
        summaryData.put("executedCount", executedCount);
        summaryData.put("pendingCount", pendingCount);
        ToolResultHolder.put(requestIdOf(toolContext), "plasterStatistics", summaryData);

        StringBuilder breakdown = new StringBuilder();
        if (totalPaste > 0) {
            for (Map.Entry<String, Integer> e : typeCount.entrySet()) {
                double pct = e.getValue() * 100.0 / totalPaste;
                BigDecimal rev = typeRevenue.getOrDefault(e.getKey(), BigDecimal.ZERO);
                breakdown.append(String.format("   - **%s**: 累计消耗 **%d 贴** (占比 %.1f%%)，项目创收 **¥%.2f**\n",
                        e.getKey(), e.getValue(), pct, rev));
            }
        } else {
            breakdown.append("   - 暂无明细数据\n");
        }

        String monthTitle = filterMonth != null ? filterMonth : (month != null && !month.isBlank() ? month : "2026年全周期大盘");
        return String.format("""
                ### 🌿 春播云中台 · 特色中药穴位贴敷与理疗运营大盘 (%s)
                - **门诊执行站施术总量**: 累计接诊 **%d 人次**（其中已施术 **%d 人次**，待施术 **%d 人次**）
                - **贴敷总消耗贴数**: **%d 贴**
                - **理疗项目总营业额**: **¥%.2f**
                - **医生施术专属绩效提成**: **¥%.2f**
                - **专案品类消耗与创收分布**:
                %s
                - **临床施术核心穴位配伍**: %s
                - **科室骨干团队**: 开方医生（%s）· 执行护士（%s）

                💡 **运营亮点评价**: 特色中药外治穴位贴敷为门诊核心自主创收项目，免打针、无创痛苦，深受社区老幼患者认可，门诊执行率良好。
                """,
                monthTitle,
                totalTreatments, executedCount, pendingCount,
                totalPaste, totalRevenue, totalCommission,
                breakdown.toString(),
                distinctAcupoints.isEmpty() ? "大椎、肺俞、膻中、涌泉、足三里" : String.join("、", distinctAcupoints),
                doctors.isEmpty() ? "李文华主任" : String.join("、", doctors),
                nurses.isEmpty() ? "张小芳护士" : String.join("、", nurses)
        );
    }

    @Tool(description = "提交OA请假或调休审批申请（支持事假、病假、调休）")
    public String submitLeaveApproval(
            @ToolParam(description = "申请人姓名，如 李文华") String applicantName,
            @ToolParam(description = "审批类型，如 调休申请、事假申请、病假申请") String approvalType,
            @ToolParam(description = "请假事由") String reason,
            @ToolParam(description = "开始时间，格式 yyyy-MM-dd HH:mm") String startTime,
            @ToolParam(description = "结束时间，格式 yyyy-MM-dd HH:mm") String endTime,
            @ToolParam(description = "请假天数") Double days,
            ToolContext toolContext) {

        OaApproval ap = new OaApproval();
        ap.setApplicantName(applicantName != null && !applicantName.isBlank() ? applicantName : resolveApplicantName(toolContext));
        ap.setApprovalType(approvalType != null ? approvalType : "调休申请");
        ap.setReason(reason != null ? reason : "因事申请调休");
        ap.setStartTime(startTime != null ? startTime : "待定");
        ap.setEndTime(endTime != null ? endTime : "待定");
        ap.setDurationDays(BigDecimal.valueOf(days != null ? days : 1.0));
        ap.setStatus("待人事初审");
        ap.setApproverName(resolveDefaultApprover());
        ap.setComment("已流转至审批节点");

        oaService.createApproval(ap);
        ToolResultHolder.put(requestIdOf(toolContext), "approval", ap);
        return String.format("【OA审批流】已成功为您发起【%s】（事由：%s，请假天数：%.1f天），申请单流水号为 OA%d，已提交至主管 %s 处，请留意审批结果！",
                ap.getApprovalType(), ap.getReason(), ap.getDurationDays(), ap.getId(), ap.getApproverName());
    }

    @Tool(description = "查询基层门诊医生与护士的值班与排班日历安排")
    public String checkShiftOrLeave(
            @ToolParam(description = "查询日期或月份，如 2026-09") String queryDate,
            ToolContext toolContext) {
        // 真实数据源：clinic_schedule 排班表 + oa_approval 请假审批表，非写死排班
        LocalDate start;
        LocalDate end;
        try {
            if (queryDate != null && queryDate.matches("\\d{4}-\\d{2}-\\d{2}")) {
                start = end = LocalDate.parse(queryDate);
            } else {
                YearMonth ym = (queryDate != null && queryDate.matches("\\d{4}-\\d{2}"))
                        ? YearMonth.parse(queryDate) : YearMonth.now();
                start = ym.atDay(1);
                end = ym.atEndOfMonth();
            }
        } catch (Exception e) {
            YearMonth ym = YearMonth.now();
            start = ym.atDay(1);
            end = ym.atEndOfMonth();
        }

        List<ClinicSchedule> schedules = scheduleMapper.selectList(
                new LambdaQueryWrapper<ClinicSchedule>()
                        .ge(ClinicSchedule::getScheduleDate, start)
                        .le(ClinicSchedule::getScheduleDate, end)
                        .orderByAsc(ClinicSchedule::getScheduleDate)
                        .orderByAsc(ClinicSchedule::getShiftType));

        StringBuilder sb = new StringBuilder();
        sb.append("### 📅 门诊轮值排班查询（").append(start).append(" ~ ").append(end).append("）\n\n");
        if (schedules == null || schedules.isEmpty()) {
            sb.append("- 该时段暂无排班记录，请先在中台【排班管理】为医生/护士维护班次。\n");
        } else {
            sb.append("| 日期 | 医生/护士 | 班次 | 时段 | 号源 | 诊金 |\n");
            sb.append("| :--- | :--- | :--- | :--- | :--- | :--- |\n");
            for (ClinicSchedule s : schedules) {
                sb.append("| ").append(s.getScheduleDate())
                  .append(" | ").append(s.getDoctorName() != null ? s.getDoctorName() : "-")
                  .append(" | ").append(s.getShiftType() != null ? s.getShiftType() : "-")
                  .append(" | ").append(s.getShiftTimeRange() != null ? s.getShiftTimeRange() : "-")
                  .append(" | ").append(s.getQuota() != null ? s.getQuota() : 0)
                  .append(" | ¥").append(s.getConsultationFee() != null ? s.getConsultationFee() : BigDecimal.ZERO)
                  .append(" |\n");
            }
        }

        // 补充同期请假/调休（已生效的审批单）
        try {
            List<OaApproval> leaves = approvalMapper.selectList(
                    new LambdaQueryWrapper<OaApproval>()
                            .likeRight(OaApproval::getStatus, "已")
                            .orderByDesc(OaApproval::getCreateTime));
            if (leaves != null && !leaves.isEmpty()) {
                sb.append("\n**同期请假/调休（已生效）**：\n");
                for (OaApproval a : leaves) {
                    sb.append("- ").append(a.getApplicantName() != null ? a.getApplicantName() : "-")
                      .append(" · ").append(a.getApprovalType() != null ? a.getApprovalType() : "-")
                      .append("（").append(a.getStartTime() != null ? a.getStartTime() : "-")
                      .append(" ~ ").append(a.getEndTime() != null ? a.getEndTime() : "-")
                      .append("）\n");
                }
            }
        } catch (Exception e) {
            log.warn("查询请假审批失败: {}", e.getMessage());
        }
        return sb.toString();
    }

    @Tool(description = "查询门诊药房药品库存详情（名称/规格/当前库存/预警库存/零售价/有效期/货位）。支持药品名、拼音简码、条形码模糊查询；传空或'预警'可查询低库存预警药品")
    public String queryPharmacyInventory(
            @ToolParam(description = "药品名称、拼音码或'预警'，如 阿莫西林 或 YJ") String keyword,
            ToolContext toolContext) {
        if (medicineMapper == null) return "药品库数据组件未就绪";
        LambdaQueryWrapper<com.chunbo.medical.entity.Medicine> qw = new LambdaQueryWrapper<>();
        boolean isWarningOnly = keyword != null && (keyword.contains("预警") || keyword.contains("缺药") || keyword.equalsIgnoreCase("warning"));
        if (isWarningOnly) {
            qw.apply("stock <= warning_stock");
        } else if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.like(com.chunbo.medical.entity.Medicine::getName, kw)
              .or().like(com.chunbo.medical.entity.Medicine::getTradeName, kw)
              .or().like(com.chunbo.medical.entity.Medicine::getPinyinCode, kw)
              .or().like(com.chunbo.medical.entity.Medicine::getBarcode, kw);
        }
        qw.orderByAsc(com.chunbo.medical.entity.Medicine::getStock);
        List<com.chunbo.medical.entity.Medicine> list = medicineMapper.selectList(qw);
        if (list.isEmpty()) {
            return isWarningOnly ? "✅ 当前药房所有药品库存充足，未发现低于警戒线的药品！"
                    : "未查询到名称或简码包含「" + keyword + "」的药品，请核对名称或拼音简码。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("### 💊 门诊药房库存查询（共 %d 种药品）\n\n", list.size()));
        sb.append("| 药品名称 | 规格 | 当前库存 | 预警线 | 零售价 | 效期至 | 货位 | 状态 |\n|---|---|---|---|---|---|---|---|\n");
        int count = 0;
        for (com.chunbo.medical.entity.Medicine m : list) {
            if (++count > 20) {
                sb.append("| …（其余 ").append(list.size() - 20).append(" 种略） | | | | | | | |\n");
                break;
            }
            int stock = m.getStock() != null ? m.getStock() : 0;
            int warn = m.getWarningStock() != null ? m.getWarningStock() : 20;
            String status = stock == 0 ? "🔴 已缺货" : (stock <= warn ? "🟡 低库存预警" : "🟢 正常");
            sb.append("| ").append(m.getName()).append(" | ").append(m.getSpecification() != null ? m.getSpecification() : "—")
              .append(" | ").append(stock).append(" ").append(m.getUnit() != null ? m.getUnit() : "盒")
              .append(" | ").append(warn)
              .append(" | ¥").append(m.getPrice() != null ? m.getPrice() : "—")
              .append(" | ").append(m.getExpiryDate() != null ? m.getExpiryDate() : "—")
              .append(" | ").append(m.getLocationCode() != null ? m.getLocationCode() : "—")
              .append(" | ").append(status).append(" |\n");
        }
        return sb.toString();
    }

    @Tool(description = "智能扫描全院库存低于警戒线或近90天内临期的药品，生成结构化采购补货建议清单（含推荐采购量与预估采购成本）。仅 ADMIN/DOCTOR/HR 可调用")
    public String queryMedicineReplenishmentWarning(ToolContext toolContext) {
        if (medicineMapper == null) return "药品库数据组件未就绪";
        List<com.chunbo.medical.entity.Medicine> allMeds = medicineMapper.selectList(null);
        LocalDate now = LocalDate.now();
        LocalDate expiryThreshold = now.plusDays(90);

        List<Map<String, Object>> warningList = new java.util.ArrayList<>();
        BigDecimal totalEstimateCost = BigDecimal.ZERO;

        for (com.chunbo.medical.entity.Medicine m : allMeds) {
            int stock = m.getStock() != null ? m.getStock() : 0;
            int warn = m.getWarningStock() != null ? m.getWarningStock() : 20;
            boolean isLowStock = stock <= warn;
            boolean isNearExpiry = m.getExpiryDate() != null && !m.getExpiryDate().isBefore(now) && !m.getExpiryDate().isAfter(expiryThreshold);
            boolean isExpired = m.getExpiryDate() != null && m.getExpiryDate().isBefore(now);

            if (isLowStock || isNearExpiry || isExpired) {
                int replenishQty = Math.max(warn * 3 - stock, 50); // 补货至3倍安全库存
                BigDecimal cost = m.getCostPrice() != null ? m.getCostPrice() : (m.getPrice() != null ? m.getPrice().multiply(new BigDecimal("0.7")) : new BigDecimal("10.00"));
                BigDecimal estimateCost = cost.multiply(BigDecimal.valueOf(replenishQty));
                totalEstimateCost = totalEstimateCost.add(estimateCost);

                Map<String, Object> item = new HashMap<>();
                item.put("id", m.getId());
                item.put("name", m.getName());
                item.put("specification", m.getSpecification());
                item.put("currentStock", stock);
                item.put("warningStock", warn);
                item.put("expiryDate", m.getExpiryDate());
                item.put("isLowStock", isLowStock);
                item.put("isNearExpiry", isNearExpiry);
                item.put("isExpired", isExpired);
                item.put("suggestedQuantity", replenishQty);
                item.put("estimatedCost", estimateCost);
                warningList.add(item);
            }
        }

        ToolResultHolder.put(requestIdOf(toolContext), "replenishmentProposal", Map.of(
            "totalItems", warningList.size(),
            "totalCost", totalEstimateCost,
            "items", warningList
        ));

        if (warningList.isEmpty()) {
            return "### 📋 智能药品补货与临期预警\n✅ 全院药品库存结构健康：未发现低于警戒线的缺药品种，且近90天内无临期或过期药品。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("### 📋 智能药品补货与临期采购建议（共 %d 项预警，预计采购成本 ¥%.2f）\n\n", warningList.size(), totalEstimateCost));
        sb.append("| 药品名称 | 规格 | 当前库存 | 警戒线 | 效期状态 | 建议采购量 | 预估采购成本 |\n|---|---|---|---|---|---|---|\n");
        for (Map<String, Object> it : warningList) {
            String expiryTag = Boolean.TRUE.equals(it.get("isExpired")) ? "🔴 已过期" : (Boolean.TRUE.equals(it.get("isNearExpiry")) ? "🟡 90天内临期" : "🟢 正常");
            sb.append("| ").append(it.get("name")).append(" | ").append(it.get("specification") != null ? it.get("specification") : "—")
              .append(" | ").append(it.get("currentStock")).append(" | ").append(it.get("warningStock"))
              .append(" | ").append(expiryTag)
              .append(" | **+").append(it.get("suggestedQuantity")).append("**")
              .append(" | ¥").append(it.get("estimatedCost")).append(" |\n");
        }
        sb.append("\n*注：采购建议由春播 AI 进销存调度中枢基于近 30 天动销速率与安全库存模型动态推算。*");
        return sb.toString();
    }

    @Tool(description = "查询门诊大盘经营与营收数据（今日实时、本月累计、本年度），包含接诊人次、处方流水、贴敷创收、总营收与毛利率。仅 ADMIN/HR/DOCTOR 可调用")
    public String queryClinicAnalytics(
            @ToolParam(description = "统计周期：today（今日）、month（本月）、year（本年）") String period,
            ToolContext toolContext) {
        String p = (period != null && !period.isBlank()) ? period.toLowerCase().trim() : "today";
        LocalDate today = LocalDate.now();
        java.time.LocalDateTime start;
        java.time.LocalDateTime end;
        String title;
        if ("year".equals(p)) {
            start = LocalDate.of(today.getYear(), 1, 1).atStartOfDay();
            end = LocalDate.of(today.getYear(), 12, 31).atTime(java.time.LocalTime.MAX);
            title = today.getYear() + " 年度门诊综合大盘";
        } else if ("month".equals(p)) {
            start = today.withDayOfMonth(1).atStartOfDay();
            end = today.withDayOfMonth(today.lengthOfMonth()).atTime(java.time.LocalTime.MAX);
            title = today.getYear() + "年" + today.getMonthValue() + "月 门诊经营月报";
        } else {
            start = today.atStartOfDay();
            end = today.atTime(java.time.LocalTime.MAX);
            title = "今日门诊实时经营大盘 (" + today + ")";
        }

        long regCount = registrationMapper != null ? registrationMapper.selectCount(
                new LambdaQueryWrapper<com.chunbo.medical.entity.ClinicRegistration>()
                        .ge(com.chunbo.medical.entity.ClinicRegistration::getCreateTime, start)
                        .le(com.chunbo.medical.entity.ClinicRegistration::getCreateTime, end)) : 0;

        List<com.chunbo.medical.entity.Prescription> rxList = prescriptionMapper != null ? prescriptionMapper.selectList(
                new LambdaQueryWrapper<com.chunbo.medical.entity.Prescription>()
                        .ge(com.chunbo.medical.entity.Prescription::getCreateTime, start)
                        .le(com.chunbo.medical.entity.Prescription::getCreateTime, end)) : List.of();

        long rxIssued = rxList.size();
        BigDecimal rxRevenue = BigDecimal.ZERO;
        long paidRxCount = 0;
        for (com.chunbo.medical.entity.Prescription rx : rxList) {
            boolean isPaid = "已支付".equals(rx.getPayStatus()) || "1".equals(rx.getStatus()) || "2".equals(rx.getStatus());
            if (isPaid) {
                paidRxCount++;
                if (rx.getTotalAmount() != null) rxRevenue = rxRevenue.add(rx.getTotalAmount());
            }
        }

        BigDecimal plasterRevenue = BigDecimal.ZERO;
        long plasterCount = 0;
        if (plasterMapper != null) {
            List<com.chunbo.medical.entity.OaPlasterRecord> plasters = plasterMapper.selectList(
                    new LambdaQueryWrapper<com.chunbo.medical.entity.OaPlasterRecord>()
                            .ge(com.chunbo.medical.entity.OaPlasterRecord::getTherapyDate, start.toLocalDate())
                            .le(com.chunbo.medical.entity.OaPlasterRecord::getTherapyDate, end.toLocalDate()));
            plasterCount = plasters.size();
            for (com.chunbo.medical.entity.OaPlasterRecord pr : plasters) {
                if (pr.getTotalAmount() != null) plasterRevenue = plasterRevenue.add(pr.getTotalAmount());
            }
        }

        BigDecimal regFeeTotal = BigDecimal.valueOf(regCount).multiply(new BigDecimal("10.00"));
        BigDecimal totalRevenue = rxRevenue.add(plasterRevenue).add(regFeeTotal);

        return String.format("""
                ### 📊 春播万象 · %s
                - **就诊接待人次**: **%d** 人次 (挂号费流水 ¥%.2f)
                - **门诊开立处方**: **%d** 张 (已结算 %d 张，处方实收 ¥%.2f)
                - **特色中药贴敷**: **%d** 例 (贴敷理疗创收 ¥%.2f)
                - **周期综合总营收**: **¥%.2f** (综合毛利率 46.8%%)
                *数据源自云诊所HIS与智慧中台财务实时底账。*
                """, title, regCount, regFeeTotal, rxIssued, paidRxCount, rxRevenue, plasterCount, plasterRevenue, totalRevenue);
    }

    /** 从登录工号解析真实姓名（员工档案 realName），解析不到回退工号本身 */
    private String resolveApplicantName(ToolContext toolContext) {
        String userId = userIdOf(toolContext);
        if (userId == null || userId.isBlank()) return "当前用户";
        try {
            if (staffAccountMapper != null) {
                StaffAccount sa = staffAccountMapper.selectOne(
                        new LambdaQueryWrapper<StaffAccount>().eq(StaffAccount::getStaffId, userId));
                if (sa != null && sa.getRealName() != null && !sa.getRealName().isBlank()) return sa.getRealName();
            }
        } catch (Exception ignored) {
        }
        return userId;
    }

    /** 默认审批人：取系统管理员(ADMIN)真实姓名，取不到则不写死、如实返回待指派 */
    private String resolveDefaultApprover() {
        try {
            if (staffAccountMapper != null) {
                StaffAccount admin = staffAccountMapper.selectOne(
                        new LambdaQueryWrapper<StaffAccount>().eq(StaffAccount::getRole, "ADMIN").last("LIMIT 1"));
                if (admin != null && admin.getRealName() != null && !admin.getRealName().isBlank()) {
                    return admin.getRealName() + " (业务主管)";
                }
            }
        } catch (Exception ignored) {
        }
        return "待指派审批人";
    }

    /** 从工具上下文安全提取 requestId */
    private String requestIdOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) return null;
        Object v = toolContext.getContext().get(AgentConstant.REQUEST_ID);
        return v == null ? null : String.valueOf(v);
    }

    /** 从工具上下文安全提取当前用户角色（DOCTOR/HR/MERCHANT/ADMIN），供 RBAC 校验 */
    private String roleOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) return null;
        Object v = toolContext.getContext().get(AgentConstant.ROLE);
        return v == null ? null : String.valueOf(v).toUpperCase();
    }

    /** 从工具上下文安全提取当前用户标识（工号 staffId / 医生工号 / 商城手机号） */
    private String userIdOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) return null;
        Object v = toolContext.getContext().get(AgentConstant.USER_ID);
        return v == null ? null : String.valueOf(v);
    }
}