package com.chunbo.medical.agent;

import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.controller.AssistantController;
import com.chunbo.medical.entity.DoctorAccount;
import com.chunbo.medical.entity.StaffAccount;
import com.chunbo.medical.enums.ChatEventTypeEnum;
import com.chunbo.medical.mapper.DoctorAccountMapper;
import com.chunbo.medical.mapper.StaffAccountMapper;
import com.chunbo.medical.service.FileUploadService;
import com.chunbo.medical.tools.ClinicAssistantTools;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.ai.content.Media;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 中台业务智能体基类（参照《SpringAI》笔记 tjxt 多智能体标准实现）
 * 每个中台意图（薪资/订单/库存/大盘/审批/贴敷/通用）一个独立 Agent 类，
 * 路由判出的意图由 AgentRouter 直接命中对应子类，真正分流到不同中台技能。
 * 注：用 @Lazy 注入 Controller 以打破 Controller↔Agent 循环依赖。
 */
public abstract class OaBaseAgent extends AbstractAgent {

    @Autowired
    @Lazy
    protected AssistantController assistantController;

    @Autowired(required = false)
    protected StaffAccountMapper staffAccountMapper;

    @Autowired(required = false)
    protected DoctorAccountMapper doctorAccountMapper;

    @Autowired
    protected ClinicAssistantTools assistantTools;

    @Autowired(required = false)
    protected FileUploadService fileUploadService;

    @Autowired
    protected com.chunbo.medical.service.OaAssistantService oaAssistantService;

    @Autowired(required = false)
    protected com.chunbo.medical.agent.react.ReActEngine reActEngine;

    @Override
    public String bizType() {
        return "oa";
    }

    /** 子类返回本智能体对应的固定路由意图（如 OA_SALARY / OA_APPROVAL） */
    protected abstract String skillHint();

    @Override
    protected Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId) {
        return buildContentFlux(question, sessionId, userId, skillHint(), null);
    }

    @Override
    protected Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId,
                                                 String routeHint, Map<String, Object> context) {
        // 依据 userId（staffId/工号）还原真实角色与姓名，保证薪资等技能的 RBAC 权限隔离生效
        String role = resolveRole(userId);
        String name = resolveName(userId, role);

        // 🧠 核心架构升级：ReAct 多步自主链式任务引擎
        if (reActEngine != null && reActEngine.isCompositeTask(question)) {
            return reActEngine.executeReActStream(question, sessionId, userId, role, name);
        }

        // 附件（工资表图片/Excel）→ 提取表格 + function-calling 发工资
        String attachmentId = context != null ? String.valueOf(context.getOrDefault(AgentConstant.ATTACHMENT_ID, "")) : "";
        String attachmentFileName = context != null ? String.valueOf(context.getOrDefault(AgentConstant.ATTACHMENT_FILE_NAME, "")) : "";
        if (attachmentId != null && !attachmentId.isBlank() && fileUploadService != null) {
            return salaryPaymentFlux(question, sessionId, userId, role, attachmentId, attachmentFileName);
        }

        org.springframework.ai.chat.model.ToolContext toolContext = createToolContext(sessionId, userId, role);

        // 1. 薪资绩效：预取真实工资条数据，纯流式输出（杜绝带 tools 导致网关聚合缓冲）
        if ("OA_SALARY".equals(skillHint())) {
            String salaryData = "";
            try {
                boolean isSummaryQuery = question.contains("全院") || question.contains("汇总") || question.contains("全部") || question.contains("所有人") || question.contains("清单") || question.contains("列表");
                String extracted = extractEmployee(question);
                if (isSummaryQuery || (extracted == null && ("ADMIN".equals(role) || "HR".equals(role)))) {
                    salaryData = assistantTools.queryAllSalarySlips(toolContext);
                } else {
                    String targetId = (extracted != null && !extracted.isBlank()) ? extracted : userId;
                    salaryData = assistantTools.querySalarySlip(targetId, null, toolContext);
                }
            } catch (Exception e) {
                salaryData = "查询工资条异常：" + e.getMessage();
            }
            String sys = buildSalarySystemPrompt(name, role, userId)
                    + "\n\n【春播OA系统实时核验工资数据】\n" + salaryData
                    + "\n\n【本轮回复无需调用任何工具】系统已在上方预取真实薪酬数据。请直接基于上方真实数据向用户解答，条理清晰，使用标准 Markdown 表格排版。";
            return functionCallingFlux(question, sessionId, userId, role, sys);
        }

        // 2. 商城履约：精准区分「列表查询」与「发货动作」，预取真实订单数据后纯流式下发
        if ("OA_ORDER".equals(skillHint())) {
            StringBuilder sysBuilder = new StringBuilder();
            sysBuilder.append("你是「春播云管理系统中台 · 商城履约调度智能体」，当前登录用户：").append(name)
                    .append("（角色：").append(role).append("，工号：").append(userId).append("）。\n");
            try {
                boolean isListQuery = question.contains("清单") || question.contains("列表") || question.contains("有哪些")
                        || question.contains("查看") || question.contains("查询") || question.contains("待发货订单")
                        || question.contains("所有订单") || question.contains("全部订单") || question.contains("看看") || question.contains("全部");

                if (!isListQuery && (question.contains("发货") || question.contains("出库")) && (question.contains("给") || question.contains("为") || question.contains("B2C") || question.matches(".*[\\u4e00-\\u9fa5]{2,4}.*发货.*"))) {
                    String customer = extractTargetName(question, "发货", "出库", "给", "为");
                    String res = assistantTools.shipCustomerOrder(customer, toolContext);
                    sysBuilder.append("\n【系统执行订单发货结果】\n").append(res).append("\n");
                } else if (!isListQuery && (question.contains("送达") || question.contains("签收")) && (question.contains("给") || question.contains("为") || question.contains("B2C") || question.contains("确认") || question.matches(".*[\\u4e00-\\u9fa5]{2,4}.*(送达|签收).*"))) {
                    String customer = extractTargetName(question, "送达", "签收", "确认");
                    String res = assistantTools.confirmCustomerDelivered(customer, toolContext);
                    sysBuilder.append("\n【系统确认送达结果】\n").append(res).append("\n");
                } else {
                    String filter = "全部";
                    if (question.contains("待发") || question.contains("未发") || question.contains("待出库")) filter = "待发货";
                    else if (question.contains("运输") || question.contains("在途") || question.contains("已发")) filter = "运输中";
                    else if (question.contains("送达") || question.contains("已完成") || question.contains("已签收")) filter = "已送达";
                    String res = assistantTools.queryMallOrdersList(filter, 15, toolContext);
                    sysBuilder.append("\n【系统查询商城订单列表结果】\n").append(res).append("\n");
                }
            } catch (Exception e) {
                sysBuilder.append("\n【操作结果】").append(e.getMessage()).append("\n");
            }
            sysBuilder.append("\n【本回复无需调用任何工具】系统已自动核查真实数据，请直接将上述真实订单履约看板和清单表格忠实清晰地转述输出，严禁擅自宣称无订单。");
            return functionCallingFlux(question, sessionId, userId, role, sysBuilder.toString());
        }

        // 3. 商品与进销存：预取商品库存或执行上下架/调价/入库后纯流式下发
        if ("OA_PRODUCT".equals(skillHint())) {
            StringBuilder sysBuilder = new StringBuilder();
            sysBuilder.append("你是「春播云管理系统中台 · 商品与进销存调度智能体」，当前登录用户：").append(name)
                    .append("（角色：").append(role).append("，工号：").append(userId).append("）。\n");
            try {
                boolean isAnalysisOrOverview = question.contains("研判") || question.contains("预警") || question.contains("分析")
                        || question.contains("概况") || question.contains("报告") || question.contains("全部") || question.contains("清单") || question.contains("列表") || question.contains("所有");

                if (!isAnalysisOrOverview && question.contains("下架") && (question.contains("把") || question.contains("给") || question.matches(".*[\\u4e00-\\u9fa5]{2,6}.*下架.*"))) {
                    String pName = extractProductName(question, "下架", "把", "给");
                    String res = assistantTools.changeProductSaleStatus(pName, "下架", toolContext);
                    sysBuilder.append("\n【系统执行商品下架结果】\n").append(res).append("\n");
                } else if (!isAnalysisOrOverview && question.contains("上架") && (question.contains("把") || question.contains("给") || question.matches(".*[\\u4e00-\\u9fa5]{2,6}.*上架.*"))) {
                    String pName = extractProductName(question, "上架", "把", "给");
                    String res = assistantTools.changeProductSaleStatus(pName, "上架", toolContext);
                    sysBuilder.append("\n【系统执行商品上架结果】\n").append(res).append("\n");
                } else if (!isAnalysisOrOverview && (question.contains("调价") || question.contains("改价")) && extractPrice(question) != null) {
                    Double newPrice = extractPrice(question);
                    String pName = extractProductName(question, "调价", "改价", "价格", "到", "为");
                    String res = assistantTools.changeProductPrice(pName, newPrice, null, toolContext);
                    sysBuilder.append("\n【系统执行商品调价结果】\n").append(res).append("\n");
                } else if (!isAnalysisOrOverview && (question.contains("补货") || question.contains("入库")) && extractQuantity(question) != null) {
                    Integer qty = extractQuantity(question);
                    String pName = extractProductName(question, "补货", "入库", "件", "盒");
                    String res = assistantTools.inboundProductStock(pName, qty, toolContext);
                    sysBuilder.append("\n【系统执行商品入库结果】\n").append(res).append("\n");
                } else {
                    String res = assistantTools.queryMallInventoryAndPriceAnalysis(toolContext);
                    sysBuilder.append("\n【系统全盘商品进销存与补货调价研判大盘数据】\n").append(res).append("\n");
                }
            } catch (Exception e) {
                sysBuilder.append("\n【操作结果】").append(e.getMessage()).append("\n");
            }
            sysBuilder.append("\n【本回复无需调用任何工具】系统已自动核查真实商品进销存数据，请直接将上述【系统全盘商品进销存与补货调价研判大盘数据】忠实清晰地呈现给用户，做专业的补货优先级与调价策略解读，严禁声称未找到商品！");
            return functionCallingFlux(question, sessionId, userId, role, sysBuilder.toString());
        }

        // 4. 商城用户管理：预取用户清单或统计后纯流式下发
        if ("OA_MALL_USER".equals(skillHint())) {
            StringBuilder sysBuilder = new StringBuilder();
            sysBuilder.append("你是「春播云管理系统中台 · 商城用户管理智能体」，当前登录用户：").append(name)
                    .append("（角色：").append(role).append("，工号：").append(userId).append("）。\n");
            try {
                if (question.contains("统计") || question.contains("总数") || question.contains("概况")) {
                    String res = assistantTools.queryMallUserStats(toolContext);
                    sysBuilder.append("\n【商城用户大盘统计结果】\n").append(res).append("\n");
                } else {
                    String kw = extractCleanKeyword(question, "商城", "用户", "名单", "列表", "有哪些", "查看", "查询", "注册");
                    String res = assistantTools.queryMallUsersList(kw, 15, toolContext);
                    sysBuilder.append("\n【商城注册用户名单结果】\n").append(res).append("\n");
                }
            } catch (Exception e) {
                sysBuilder.append("\n【查询结果】").append(e.getMessage()).append("\n");
            }
            sysBuilder.append("\n【本回复无需调用任何工具】系统已自动检索真实商城用户数据，请基于上述结果向用户清晰转述与排版。");
            return functionCallingFlux(question, sessionId, userId, role, sysBuilder.toString());
        }

        // 5. OA 请假审批：预取请假名单或执行审批动作后纯流式下发
        if ("OA_APPROVAL".equals(skillHint())) {
            StringBuilder sysBuilder = new StringBuilder();
            sysBuilder.append("你是「春播云管理系统中台 · OA 请假审批调度智能体」，当前登录用户：").append(name)
                    .append("（角色：").append(role).append("，工号：").append(userId).append("）。\n");
            try {
                if (question.contains("批准") || question.contains("通过") || question.contains("同意")) {
                    String target = extractTargetName(question, "批准", "通过", "同意", "请假", "单", "的");
                    String res = assistantTools.processLeaveApproval(target, "批准", toolContext);
                    sysBuilder.append("\n【系统执行审批批准结果】\n").append(res).append("\n");
                } else if (question.contains("驳回") || question.contains("拒绝")) {
                    String target = extractTargetName(question, "驳回", "拒绝", "请假", "单", "的");
                    String res = assistantTools.processLeaveApproval(target, "驳回", toolContext);
                    sysBuilder.append("\n【系统执行审批驳回结果】\n").append(res).append("\n");
                } else if (question.contains("删")) {
                    String target = extractTargetName(question, "删", "删除", "请假", "单", "的");
                    String res = assistantTools.deleteLeaveApproval(target, toolContext);
                    sysBuilder.append("\n【系统执行删除请假单结果】\n").append(res).append("\n");
                } else {
                    boolean onlyPending = question.contains("未") || question.contains("待");
                    String res = assistantTools.queryLeaveApprovals(onlyPending, toolContext);
                    sysBuilder.append("\n【系统查询请假单名单结果】\n").append(res).append("\n");
                }
            } catch (Exception e) {
                sysBuilder.append("\n【操作结果】").append(e.getMessage()).append("\n");
            }
            sysBuilder.append("\n【本回复无需调用任何工具】系统已自动核查/执行上述指令，请基于上述真实审批结果向用户汇报。");
            return functionCallingFlux(question, sessionId, userId, role, sysBuilder.toString());
        }

        // 6. 药房库存与智能补货调度：预取药房库存或临期预警后纯流式下发
        if ("OA_INVENTORY".equals(skillHint())) {
            StringBuilder sysBuilder = new StringBuilder();
            sysBuilder.append("你是「春播云管理系统中台 · 智慧药房与库存调度智能体」，当前登录用户：").append(name)
                    .append("（角色：").append(role).append("，工号：").append(userId).append("）。\n");
            try {
                if (question.contains("补货") || question.contains("预警") || question.contains("缺药") || question.contains("临期") || question.contains("采购") || question.contains("快没")) {
                    String res = assistantTools.queryMedicineReplenishmentWarning(toolContext);
                    sysBuilder.append("\n【药房补货与临期预警实时分析】\n").append(res).append("\n");
                } else {
                    String kw = extractMedicineName(question);
                    String res = assistantTools.queryPharmacyInventory(kw, toolContext);
                    sysBuilder.append("\n【门诊药房真实库存查询结果】\n").append(res).append("\n");
                }
            } catch (Exception e) {
                sysBuilder.append("\n【查询结果】").append(e.getMessage()).append("\n");
            }
            sysBuilder.append("\n【本回复无需调用任何工具】系统已检索真实药房进销存数据，请基于上述数据以专业医药管理口吻向用户汇报。");
            return functionCallingFlux(question, sessionId, userId, role, sysBuilder.toString());
        }

        // 7. 大盘经营诊断：预取门诊统计后纯流式下发
        if ("OA_ANALYTICS".equals(skillHint())) {
            String period = "today";
            if (question.contains("年")) period = "year";
            else if (question.contains("月")) period = "month";
            String res = "";
            try {
                res = assistantTools.queryClinicAnalytics(period, toolContext);
            } catch (Exception e) {
                res = "大盘数据获取异常：" + e.getMessage();
            }
            String sys = "你是「春播云管理系统中台 · 门诊大盘经营诊断智能体」，当前登录用户：" + name + "（角色：" + role + "，工号：" + userId + "）。\n"
                    + "\n【门诊大盘与综合营收真实统计】\n" + res
                    + "\n\n【本回复无需调用任何工具】请基于上述真实门诊运营大盘数据，结构化解读现状，提炼亮点与改善建议。";
            return functionCallingFlux(question, sessionId, userId, role, sys);
        }

        // 8. 特色中药贴敷理疗：预取贴敷理疗台账后纯流式下发
        if ("OA_PLASTER".equals(skillHint())) {
            String month = null;
            if (question.contains("9月") || question.contains("09")) month = "2026-09";
            else if (question.contains("8月") || question.contains("08")) month = "2026-08";
            String res = "";
            try {
                res = assistantTools.queryPlasterStatistics(month, null, toolContext);
            } catch (Exception e) {
                res = "贴敷数据获取异常：" + e.getMessage();
            }
            String sys = "你是「春播云管理系统中台 · 中药贴敷特色理疗智能体」，当前登录用户：" + name + "（角色：" + role + "，工号：" + userId + "）。\n"
                    + "\n【中药贴敷特色理疗运营真实统计】\n" + res
                    + "\n\n【本回复无需调用任何工具】请基于上述真实贴敷统计数据，详细转述疗程量、各贴敷类型占比、施术人次与创收数据，给出专业运营分析。";
            return functionCallingFlux(question, sessionId, userId, role, sys);
        }

        // 9. 门诊值班排班：预取排班表后纯流式下发
        if ("OA_SCHEDULE".equals(skillHint())) {
            String res = "";
            try {
                res = assistantTools.checkShiftOrLeave(null, toolContext);
            } catch (Exception e) {
                res = "排班数据获取异常：" + e.getMessage();
            }
            String sys = "你是「春播云管理系统中台 · 医生护士排班值班智能体」，当前登录用户：" + name + "（角色：" + role + "，工号：" + userId + "）。\n"
                    + "\n【门诊轮值排班与请假真实日历】\n" + res
                    + "\n\n【本回复无需调用任何工具】请基于上述真实排班表，清晰呈现医生护士的值班安排。";
            return functionCallingFlux(question, sessionId, userId, role, sys);
        }

        return assistantController.buildOaContentFlux(question, userId, role, name, skillHint());
    }

    /** 工资表附件处理：Excel 走确定性解析发放（不依赖 LLM），图片走视觉识别 + function-calling */
    private Flux<ChatEventVO> salaryPaymentFlux(String question, String sessionId, String userId, String role, String attachmentId, String fileName) {
        if (fileUploadService.isExcel(attachmentId)) {
            // 先尝试工资表解析（表头含工资/金额列）；解析不出再按商品表处理（商品/零售价列）
            List<Map<String, Object>> salaryRows = fileUploadService.excelToRows(attachmentId);
            if (salaryRows != null) {
                // 确定性发放：解析表格 → 逐行 paySalary（含账号校验 + 幂等覆盖）→ 汇总，结果真实落库
                // 从文件名自动识别发放月份（如"工资表-2026年10月.xlsx"→2026-10），识别不到才用当前月
                String month = extractMonthFromFileName(fileName);
                Map<String, Object> summary = oaAssistantService.paySalaryFromRows(salaryRows, month);
                String md = buildPaySummaryMarkdown(summary, month);
                return Flux.just(ChatEventVO.builder()
                        .eventType(ChatEventTypeEnum.DATA.getValue())
                        .eventData(md)
                        .build());
            }
            List<Map<String, Object>> productRows = fileUploadService.excelToProductRows(attachmentId);
            if (productRows != null) {
                // 确定性商品导入：新商品建档上架 / 同价库存累加 / 异价拒绝
                Map<String, Object> summary = oaAssistantService.importProductsFromRows(productRows);
                String md = buildProductImportMarkdown(summary);
                return Flux.just(ChatEventVO.builder()
                        .eventType(ChatEventTypeEnum.DATA.getValue())
                        .eventData(md)
                        .build());
            }
            return Flux.just(ChatEventVO.builder()
                    .eventType(ChatEventTypeEnum.DATA.getValue())
                    .eventData("⚠️ 表格解析失败：未识别到工资表（需含「工资/金额」列）或商品表（需含「商品名称/零售价」列）表头，请检查文件。")
                    .build());
        }
        if (fileUploadService.isImage(attachmentId)) {
            Media media = fileUploadService.toImageMedia(attachmentId);
            String sys = "你是春播云管理系统中台·薪酬绩效智能体。用户上传了一张工资表图片，请先识别图片中的表格内容"
                    + "（员工姓名/工号、应发金额），然后对每位员工调用 processSalaryPayment 工具发起工资发放，最后汇总结果。"
                    + "金额以图片表格为准，不要编造。";
            return functionCallingFlux(question, sessionId, userId, role, sys, List.of(media), assistantTools);
        }
        return Flux.just(ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.DATA.getValue())
                .eventData("⚠️ 暂不支持该附件类型，请上传图片或 Excel 工资表。")
                .build());
    }

    /** 商品表格导入汇总 → Markdown（确定性写库结果） */
    private String buildProductImportMarkdown(Map<String, Object> summary) {
        StringBuilder sb = new StringBuilder();
        sb.append("已按商品表完成**确定性解析导入**（不依赖模型推测，结果真实写入系统）：\n\n");
        sb.append("| 商品 | 零售价(元) | 处理结果 |\n");
        sb.append("|---|---|---|\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) summary.getOrDefault("details", List.of());
        for (Map<String, Object> d : details) {
            boolean ok = Boolean.TRUE.equals(d.get("success"));
            sb.append("| ").append(d.getOrDefault("productName", ""))
              .append(" | ").append(d.getOrDefault("retailPrice", ""))
              .append(" | ").append(ok ? "✅ " : "⛔ ").append(String.valueOf(d.getOrDefault("message", ""))).append(" |\n");
        }
        sb.append("\n**导入统计**：新增 ").append(summary.getOrDefault("addedCount", 0))
          .append(" 个，同价库存累加 ").append(summary.getOrDefault("mergedCount", 0))
          .append(" 个，失败 ").append(summary.getOrDefault("failCount", 0)).append(" 个。\n\n");
        sb.append("> 已存在商品价格一致时自动库存累加；价格不匹配一律拒绝并保留原价。结果可在「商城商品与进销存」页面查看。");
        return sb.toString();
    }

    /** 从工资表文件名提取发放月份（yyyy-MM），支持「2026年10月」「2026-10」等写法；识别不到返回 null */
    private String extractMonthFromFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(20\\d{2})\\s*[-年/.]\\s*(1[0-2]|0?[1-9])\\s*月?")
                .matcher(fileName);
        if (m.find()) {
            int mm = Integer.parseInt(m.group(2));
            return m.group(1) + "-" + (mm < 10 ? "0" + mm : String.valueOf(mm));
        }
        return null;
    }

    /** 确定性发放汇总 → Markdown（与页面工资发放中心同口径） */
    private String buildPaySummaryMarkdown(Map<String, Object> summary, String month) {
        StringBuilder sb = new StringBuilder();
        sb.append("已按工资表完成**确定性解析发放**（不依赖模型推测，结果真实写入系统）：\n\n");
        sb.append("> 发放月份已从文件名自动识别为 **").append(month != null ? month : "当前月").append("**；")
          .append("具体发放日期默认按 **").append(month != null ? month + "-01" : "今日").append("** 记账。")
          .append("如需指定其他具体日期，请回复告知（如「按 ").append(month != null ? month : "本月").append("-15 发放」），我将重新发起覆盖。\n\n");
        sb.append("| 工号/姓名 | 部门 | 应发金额(元) | 发放结果 |\n");
        sb.append("|---|---|---|---|\n");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> details = (List<Map<String, Object>>) summary.getOrDefault("details", List.of());
        for (Map<String, Object> d : details) {
            String employee = String.valueOf(d.getOrDefault("employee", ""));
            String dept = String.valueOf(d.getOrDefault("department", ""));
            String amount = String.valueOf(d.getOrDefault("amount", ""));
            boolean ok = Boolean.TRUE.equals(d.get("success"));
            sb.append("| ").append(employee).append(" | ").append(dept)
              .append(" | ").append(amount)
              .append(" | ").append(ok ? "✅ 已发放" : "⛔ " + String.valueOf(d.getOrDefault("message", "失败"))).append(" |\n");
        }
        sb.append("\n**发放统计**：成功 ").append(summary.getOrDefault("successCount", 0))
          .append(" 人，失败 ").append(summary.getOrDefault("failCount", 0))
          .append(" 人，合计 ¥").append(summary.getOrDefault("totalAmount", 0)).append("。\n\n");
        sb.append("> 以上为系统直接写库的真实发放结果，可在「工资条发放与核算 → 发放记录」中查询到对应工资条。");
        return sb.toString();
    }

    /** 薪资智能体 system prompt：引导 LLM 先调工具核对工资条，同时向 LLM 声明当前身份供透明化 */
    private String buildSalarySystemPrompt(String name, String role, String userId) {
        return "你是「春播云管理系统中台 · 薪酬绩效智能体」，当前登录用户：" + name + "（角色：" + role + "，工号：" + userId + "）。\n"
                + "【查询工资必须自主调用工具】\n"
                + "1. 用户查询自己或他人工资时，必须调用 querySalarySlip(doctorId, month) 获取真实工资条数据；\n"
                + "2. 工具的权限由系统后台强制校验（医生只能查自己，管理员/人事可查任意员工），你无需自行判断权限；\n"
                + "3. 拿到工具返回的工资数据后，用清晰的 Markdown 表格呈现。\n"
                + "【注意】不要编造任何工资数字，一切以工具返回的真实数据为准。";
    }

    /** 按工号前缀还原角色（DOC_ 医生 / HR_ 人事 / MERCH_ 商户 / 其它 ADMIN），并以 DB 为准修正 */
    private String resolveRole(String userId) {
        String id = userId == null ? "" : userId.toUpperCase();
        String role;
        if (id.startsWith("DOC_")) role = "DOCTOR";
        else if (id.startsWith("HR_")) role = "HR";
        else if (id.startsWith("MERCH_")) role = "MERCHANT";
        else role = "ADMIN";
        try {
            if (staffAccountMapper != null) {
                StaffAccount sa = staffAccountMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StaffAccount>()
                                .eq(StaffAccount::getStaffId, userId));
                if (sa != null && sa.getRole() != null && !sa.getRole().isEmpty()) role = sa.getRole().toUpperCase();
            }
        } catch (Exception ignored) {
        }
        return role;
    }

    /** 还原真实姓名：员工档案 realName > 医生档案 doctorName > userId 本身 */
    private String resolveName(String userId, String role) {
        try {
            if ("DOCTOR".equals(role) && doctorAccountMapper != null) {
                DoctorAccount da = doctorAccountMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DoctorAccount>()
                                .eq(DoctorAccount::getDoctorId, userId));
                if (da != null && da.getDoctorName() != null && !da.getDoctorName().isEmpty()) return da.getDoctorName();
            }
            if (staffAccountMapper != null) {
                StaffAccount sa = staffAccountMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StaffAccount>()
                                .eq(StaffAccount::getStaffId, userId));
                if (sa != null && sa.getRealName() != null && !sa.getRealName().isEmpty()) return sa.getRealName();
            }
        } catch (Exception ignored) {
        }
        return userId == null || userId.isEmpty() ? "系统用户" : userId;
    }

    private org.springframework.ai.chat.model.ToolContext createToolContext(String sessionId, String userId, String role) {
        return new org.springframework.ai.chat.model.ToolContext(buildToolContext(sessionId, currentRequestId(), userId, role));
    }

    private String extractEmployee(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(DOC_\\w+|HR_\\w+|ADM_\\w+|[\\u4e00-\\u9fa5]{2,4}(?:医生|大夫|护士|主任)?)").matcher(text);
        while (m.find()) {
            String hit = m.group(1).replaceAll("(医生|大夫|护士|主任)", "");
            if (!hit.matches("工资|薪酬|明细|考勤|查询|看看|多少|本月|上月|这个月")) {
                return hit;
            }
        }
        return null;
    }

    private String extractTargetName(String text, String... verbs) {
        if (text == null) return "";
        java.util.regex.Matcher b2cMatcher = java.util.regex.Pattern.compile("(B2C\\w+)").matcher(text);
        if (b2cMatcher.find()) {
            return b2cMatcher.group(1);
        }
        java.util.regex.Matcher numMatcher = java.util.regex.Pattern.compile("\\b(\\d+)\\b").matcher(text);
        if (numMatcher.find()) {
            return numMatcher.group(1);
        }
        String clean = text;
        for (String v : verbs) {
            clean = clean.replace(v, "");
        }
        clean = clean.replaceAll("[，。？！、,?!\\s]", "").trim();
        return clean.isEmpty() ? text : clean;
    }

    private String extractProductName(String text, String... stopwords) {
        if (text == null) return "";
        String clean = text;
        for (String sw : stopwords) {
            clean = clean.replace(sw, "");
        }
        clean = clean.replaceAll("[，。？！、,?!\\s]", "").trim();
        return clean;
    }

    private Double extractPrice(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+(?:\\.\\d{1,2})?)").matcher(text);
        if (m.find()) {
            try { return Double.parseDouble(m.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }

    private Integer extractQuantity(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(text);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }

    private String extractCleanKeyword(String text, String... stopwords) {
        if (text == null) return null;
        String clean = text;
        for (String sw : stopwords) {
            clean = clean.replace(sw, "");
        }
        clean = clean.replaceAll("[，。？！、,?!\\s]", "").trim();
        return clean.isEmpty() ? null : clean;
    }

    private String extractMedicineName(String text) {
        if (text == null) return null;
        String clean = text.replaceAll("(查询|查一下|库存|还有多少|有没有|看看|门诊药房|药房|药品)", "")
                .replaceAll("[，。？！、,?!\\s]", "").trim();
        return clean.isEmpty() ? null : clean;
    }
}
