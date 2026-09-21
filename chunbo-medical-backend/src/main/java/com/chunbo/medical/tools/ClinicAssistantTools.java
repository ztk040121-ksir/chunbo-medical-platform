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

    @Tool(description = "查询春播万象中药贴敷治疗的月度疗程量、品类分布与总营收统计")
    public String queryPlasterStatistics(
            @ToolParam(description = "统计月份，如 2026-08") String month,
            @ToolParam(description = "贴敷类别，如 通络贴、三伏贴、小儿咳喘贴，为空则查询全部") String category,
            ToolContext toolContext) {

        Map<String, Object> summary = oaService.getPlasterSummary();
        ToolResultHolder.put(requestIdOf(toolContext), "plasterStatistics", summary);
        // 按贴敷类型真实聚合品类占比，杜绝写死占比数字
        StringBuilder typeLine = new StringBuilder();
        Object recordsObj = summary.get("records");
        if (recordsObj instanceof List<?> list && !list.isEmpty()) {
            Map<String, Integer> typeCount = new HashMap<>();
            int totalPaste = 0;
            for (Object o : list) {
                if (o instanceof OaPlasterRecord r) {
                    String t = r.getPlasterType() != null && !r.getPlasterType().isEmpty() ? r.getPlasterType() : "其他";
                    int c = r.getPasteCount() != null ? r.getPasteCount() : 0;
                    typeCount.merge(t, c, Integer::sum);
                    totalPaste += c;
                }
            }
            if (totalPaste > 0) {
                for (Map.Entry<String, Integer> e : typeCount.entrySet()) {
                    double pct = e.getValue() * 100.0 / totalPaste;
                    typeLine.append("   - ").append(e.getKey()).append(": ").append(e.getValue())
                            .append(" 贴 (").append(String.format("%.1f", pct)).append("%)\n");
                }
            } else {
                typeLine.append("   - 暂无贴敷记录\n");
            }
        } else {
            typeLine.append("   - 暂无贴敷记录\n");
        }
        return String.format("""
                ### 🌿 春播万象 · 特色中药贴敷专项运营统计 (%s)
                - **贴敷治疗总疗程量**: %s 贴
                - **贴敷总项目营业额**: **¥%s**
                - **按贴敷类型真实分布**:
                %s
                """,
                (month != null ? month : "全部"),
                summary.get("totalPasteCount"),
                summary.get("totalRevenue"),
                typeLine.toString()
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
        ap.setStatus("待审批");
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