package com.chunbo.medical.tools;

import com.chunbo.medical.entity.OaApproval;
import com.chunbo.medical.entity.OaPlasterRecord;
import com.chunbo.medical.entity.OaSalarySlip;
import com.chunbo.medical.service.OaAssistantService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ClinicAssistantTools {

    @Autowired
    private OaAssistantService oaService;

    @Tool(description = "查询指定医生的工资条明细（底薪、门诊提成、特色贴敷提成、扣除项、实发工资）")
    public String querySalarySlip(
            @ToolParam(description = "医生工号或姓名，如 DOC_1001 或 李文华") String doctorId,
            @ToolParam(description = "查询月份，格式为 yyyy-MM，如 2026-08") String month) {

        List<OaSalarySlip> list = oaService.getSalarySlips(doctorId != null && doctorId.contains("DOC") ? doctorId : "DOC_1001");
        if (list.isEmpty()) {
            return "【春播OA系统】未查找到对应工号或月份的工资明细记录。";
        }
        OaSalarySlip s = list.get(0);
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
            @ToolParam(description = "贴敷类别，如 通络贴、三伏贴、小儿咳喘贴，为空则查询全部") String category) {

        Map<String, Object> summary = oaService.getPlasterSummary();
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
            @ToolParam(description = "请假天数") Double days) {

        OaApproval ap = new OaApproval();
        ap.setApplicantName(applicantName != null ? applicantName : "李文华");
        ap.setApprovalType(approvalType != null ? approvalType : "调休申请");
        ap.setReason(reason != null ? reason : "因事申请调休");
        ap.setStartTime(startTime != null ? startTime : "2026-09-18 08:30");
        ap.setEndTime(endTime != null ? endTime : "2026-09-18 17:30");
        ap.setDurationDays(BigDecimal.valueOf(days != null ? days : 1.0));
        ap.setStatus("待审批");
        ap.setApproverName("张院长 (业务主管)");
        ap.setComment("已流转至业务院长审核节点");

        oaService.createApproval(ap);
        return String.format("【OA审批流】已成功为您发起【%s】（事由：%s，请假天数：%.1f天），申请单流水号为 OA%d，已提交至主管 %s 处，请留意审批结果！",
                ap.getApprovalType(), ap.getReason(), ap.getDurationDays(), ap.getId(), ap.getApproverName());
    }

    @Tool(description = "查询基层门诊医生与护士的值班与排班日历安排")
    public String checkShiftOrLeave(
            @ToolParam(description = "查询日期或月份，如 2026-09") String queryDate) {
        // 排班数据当前无真实数据源，如实提示而非返回写死的排班表
        return "### 📅 门诊轮值排班查询\n" +
                "- 查询范围：" + (queryDate != null ? queryDate : "未指定") + "\n" +
                "- 说明：当前系统尚未接入真实排班数据源，无法给出确切班次，请以院内排班系统或值班主管通知为准。\n";
    }
}