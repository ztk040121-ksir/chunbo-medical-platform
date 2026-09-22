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

        // 附件（工资表图片/Excel）→ 提取表格 + function-calling 发工资
        String attachmentId = context != null ? String.valueOf(context.getOrDefault(AgentConstant.ATTACHMENT_ID, "")) : "";
        String attachmentFileName = context != null ? String.valueOf(context.getOrDefault(AgentConstant.ATTACHMENT_FILE_NAME, "")) : "";
        if (attachmentId != null && !attachmentId.isBlank() && fileUploadService != null) {
            return salaryPaymentFlux(question, sessionId, userId, role, attachmentId, attachmentFileName);
        }

        // 薪资绩效：升级为真正的 function-calling，LLM 自主调用 querySalarySlip（工具内部做 RBAC 校验）
        if ("OA_SALARY".equals(skillHint())) {
            String sys = buildSalarySystemPrompt(name, role, userId);
            return functionCallingFlux(question, sessionId, userId, role, sys, assistantTools);
        }

        // 商城履约：AI 自主调用发货/确认送达工具（真实扣库存 + 出库台账 + 状态流转），支持按收货人姓名或订单号
        if ("OA_ORDER".equals(skillHint())) {
            String sys = "你是「春播云管理系统中台 · 商城履约调度智能体」，当前登录用户：" + name + "（角色：" + role + "，工号：" + userId + "）。\n"
                    + "【订单发货与送达必须自主调用工具，严禁凭空描述结果】\n"
                    + "1. 用户要求发货/出库（如「给李先生发货」）时，调用 shipCustomerOrder(customerOrOrderNo) 工具——支持收货人姓名或订单号，"
                    + "系统会匹配其最新待发货订单，真实扣减库存、写入进销存流水并生成春播便民速递运单号；\n"
                    + "2. 用户要求确认送达/签收（如「李先生的订单已送到了」）时，调用 confirmCustomerDelivered(customerOrOrderNo) 工具——"
                    + "系统自动匹配其最新运输中订单并把状态改为「已送达 / 居民已签收」；\n"
                    + "3. 一条回复只处理用户当前要求的动作，不要既发货又确认送达；\n"
                    + "4. 工具返回成功后，向用户清晰转述运单号/状态变化；工具返回失败（如查无订单、重复发货）时如实告知原因，不要编造。";
            return functionCallingFlux(question, sessionId, userId, role, sys, assistantTools);
        }

        // 商品与进销存：AI 自主调用上下架/调价/入库工具（真实改库），支持商品名模糊匹配
        if ("OA_PRODUCT".equals(skillHint())) {
            String sys = "你是「春播云管理系统中台 · 商品与进销存调度智能体」，当前登录用户：" + name + "（角色：" + role + "，工号：" + userId + "）。\n"
                    + "【商品操作必须自主调用工具，严禁凭空描述结果】\n"
                    + "1. 用户要求下架/上架商品（如「给稳健医疗医用外科口罩下架」）时，调用 changeProductSaleStatus(productName, action)；\n"
                    + "2. 用户要求调价（如「口罩调价到 13.5」）时，调用 changeProductPrice(productName, newRetailPrice, newWholesalePrice)；\n"
                    + "3. 用户要求入库补货（如「口罩补货 200 件」）时，调用 inboundProductStock(productName, quantity)；\n"
                    + "4. 工具返回 needConfirm/候选清单时，向用户列出候选并询问操作哪一个，不要擅自选择；\n"
                    + "5. 工具返回成功后清晰转述变更（价格从 X 到 Y、库存变化等）；失败时如实告知原因，不要编造。";
            return functionCallingFlux(question, sessionId, userId, role, sys, assistantTools);
        }
        // 商城用户管理：注册引导 / 查用户订单 / 用户统计，AI 自主调用工具真实落库与查询
        if ("OA_MALL_USER".equals(skillHint())) {
            String sys = "你是「春播云管理系统中台 · 商城用户管理智能体」，当前登录用户：" + name + "（角色：" + role + "，工号：" + userId + "）。\n"
                    + "【用户注册必须走工具，严禁凭空声称已注册】\n"
                    + "1. 用户要注册/新增春播商城账户时，调用 registerMallUser(username, password, nickname, phone, address)；\n"
                    + "   - 必填项与商城注册页完全一致：登录账号、登录密码（至少6位）、真实姓名或称呼、11位手机号（1开头）；只有收货地址可选；\n"
                    + "   - 用户没提供时**一步一步引导**，一次问一项，别一口气抛一堆问题；信息齐了才调用工具一次完成注册；\n"
                    + "   - 用户中途只补充部分信息（如只发一个手机号）时，继续引导剩余必填项，不要重新开始也不要胡乱套用；\n"
                    + "   - 注册成功后如实转述账号信息与 ¥200 新人体验金；工具返回 NEED_MORE_INFO 时按清单继续提问；\n"
                    + "   - 返回账号/手机号已占用时，如实转述是哪一个被占用，并给出换号或用原账号登录的建议。\n"
                    + "2. 用户要查某个用户的订单（如「看看陈素芬的订单」）时，调用 queryMallUserOrders(userKeyword)——支持账号/手机号/姓名；\n"
                    + "3. 用户问用户总数/活跃账户/冻结账户/新人购药金时，调用 queryMallUserStats()；\n"
                    + "4. 一切以工具返回的真实数据为准，查询不到就如实说明，不要编造。";
            return functionCallingFlux(question, sessionId, userId, role, sys, assistantTools);
        }
        // OA 请假审批：AI 自主调用名单查询/批准驳回/删除工具（真实落库，申请人端状态同步）
        if ("OA_APPROVAL".equals(skillHint())) {
            String sys = "你是「春播云管理系统中台 · OA 请假审批调度智能体」，当前登录用户：" + name + "（角色：" + role + "，工号：" + userId + "）。\n"
                    + "【审批操作必须调用工具，严禁凭空描述结果】\n"
                    + "1. 用户要请假名单（如「把请假人员名单列出来」「未批准的请假人员」）时，调用 queryLeaveApprovals(onlyPending)——查未批准的传 true，查全部传 false；\n"
                    + "2. 用户要批准/驳回请假（如「批准张小芳的请假」）时，调用 processLeaveApproval(applicantOrId, action)；\n"
                    + "   - 工具返回候选清单（同一申请人多张单）时，先向用户确认处理哪一张，不要擅自选择；\n"
                    + "3. 用户要删除请假单（如「把张小芳那个请假删了」）时，调用 deleteLeaveApproval(applicantOrId)；删除不可恢复，用户意图不明确时先确认；\n"
                    + "4. 用户要提交请假申请（如「我明天想请一天病假」）时，调用 submitLeaveApproval(applicantName, approvalType, reason, startTime, endTime, days)——缺必填信息（假别/时间/事由）先一次性引导补齐再提交；\n"
                    + "5. 工具返回成功后如实转述单号/申请人/审批结果；查不到或失败时如实说明，不要编造。";
            return functionCallingFlux(question, sessionId, userId, role, sys, assistantTools);
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
}
