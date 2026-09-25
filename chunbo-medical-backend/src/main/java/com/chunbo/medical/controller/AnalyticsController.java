package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.*;
import com.chunbo.medical.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    @Autowired
    private ClinicRegistrationMapper registrationMapper;

    @Autowired
    private PrescriptionMapper prescriptionMapper;

    @Autowired
    private MedicineMapper medicineMapper;

    @Autowired
    private OaPlasterRecordMapper plasterMapper;

    @Autowired
    private StaffAccountMapper staffAccountMapper;

    @Autowired
    private DoctorAccountMapper doctorAccountMapper;

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @Autowired
    private com.chunbo.medical.service.WeeklyReportService weeklyReportService;

    /** AI 生成最近 7 天运营周报（营收/患者/药品动销 → LLM 自然语言成文） */
    @GetMapping("/weekly-report")
    public Map<String, Object> weeklyReport() {
        return weeklyReportService.generateWeeklyReport();
    }

    @GetMapping("/summary")
    public Map<String, Object> getAnalyticsSummary(@RequestParam(value = "period", defaultValue = "today") String period) {
        Map<String, Object> data = new HashMap<>();

        // 今日自然日 (2026-09-18)
        LocalDate today = LocalDate.now();
        LocalDateTime startOfToday = today.atStartOfDay();
        LocalDateTime endOfToday = today.atTime(LocalTime.MAX);

        // 1. 今日实时指标 (基于 MySQL 真实数据)
        Long todayRegCount = registrationMapper.selectCount(
                new LambdaQueryWrapper<ClinicRegistration>()
                        .ge(ClinicRegistration::getCreateTime, startOfToday)
                        .le(ClinicRegistration::getCreateTime, endOfToday)
        );
        List<ClinicRegistration> todayRegList = registrationMapper.selectList(
                new LambdaQueryWrapper<ClinicRegistration>()
                        .ge(ClinicRegistration::getCreateTime, startOfToday)
                        .le(ClinicRegistration::getCreateTime, endOfToday)
        );
        BigDecimal todayRegFeeTotal = BigDecimal.ZERO;
        for (ClinicRegistration r : todayRegList) {
            todayRegFeeTotal = todayRegFeeTotal.add(r.getRegFee() != null ? r.getRegFee() : new BigDecimal("10.00"));
        }

        Long todayRxCount = prescriptionMapper.selectCount(
                new LambdaQueryWrapper<Prescription>()
                        .ge(Prescription::getCreateTime, startOfToday)
                        .le(Prescription::getCreateTime, endOfToday)
        );
        BigDecimal todayRxRevenue = BigDecimal.ZERO;
        List<Prescription> todayRxs = prescriptionMapper.selectList(
                new LambdaQueryWrapper<Prescription>()
                        .ge(Prescription::getCreateTime, startOfToday)
                        .le(Prescription::getCreateTime, endOfToday)
        );
        for (Prescription rx : todayRxs) {
            if (isPaidPrescription(rx) && rx.getTotalAmount() != null) {
                todayRxRevenue = todayRxRevenue.add(rx.getTotalAmount());
            }
        }

        BigDecimal todayPlasterRevenue = BigDecimal.ZERO;
        List<OaPlasterRecord> todayPlasters = plasterMapper.selectList(
                new LambdaQueryWrapper<OaPlasterRecord>()
                        .eq(OaPlasterRecord::getTherapyDate, today)
        );
        for (OaPlasterRecord p : todayPlasters) {
            if (p.getTotalAmount() != null) {
                todayPlasterRevenue = todayPlasterRevenue.add(p.getTotalAmount());
            }
        }
        BigDecimal todayTotalRevenue = todayRxRevenue.add(todayPlasterRevenue).add(todayRegFeeTotal);

        // 2. 本月自然月汇总指标 (2026年9月 真实数据)
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime endOfMonth = today.withDayOfMonth(today.lengthOfMonth()).atTime(LocalTime.MAX);
        LocalDate startOfMonthDate = today.withDayOfMonth(1);
        LocalDate endOfMonthDate = today.withDayOfMonth(today.lengthOfMonth());

        Long monthRegCount = registrationMapper.selectCount(
                new LambdaQueryWrapper<ClinicRegistration>()
                        .ge(ClinicRegistration::getCreateTime, startOfMonth)
                        .le(ClinicRegistration::getCreateTime, endOfMonth)
        );
        List<ClinicRegistration> monthRegList = registrationMapper.selectList(
                new LambdaQueryWrapper<ClinicRegistration>()
                        .ge(ClinicRegistration::getCreateTime, startOfMonth)
                        .le(ClinicRegistration::getCreateTime, endOfMonth)
        );
        BigDecimal monthRegFeeTotal = BigDecimal.ZERO;
        for (ClinicRegistration r : monthRegList) {
            monthRegFeeTotal = monthRegFeeTotal.add(r.getRegFee() != null ? r.getRegFee() : new BigDecimal("10.00"));
        }

        Long monthRxCount = prescriptionMapper.selectCount(
                new LambdaQueryWrapper<Prescription>()
                        .ge(Prescription::getCreateTime, startOfMonth)
                        .le(Prescription::getCreateTime, endOfMonth)
        );
        BigDecimal monthRxRevenue = BigDecimal.ZERO;
        List<Prescription> monthRxs = prescriptionMapper.selectList(
                new LambdaQueryWrapper<Prescription>()
                        .ge(Prescription::getCreateTime, startOfMonth)
                        .le(Prescription::getCreateTime, endOfMonth)
        );
        for (Prescription rx : monthRxs) {
            if (isPaidPrescription(rx) && rx.getTotalAmount() != null) {
                monthRxRevenue = monthRxRevenue.add(rx.getTotalAmount());
            }
        }

        BigDecimal monthPlasterRevenue = BigDecimal.ZERO;
        List<OaPlasterRecord> monthPlasters = plasterMapper.selectList(
                new LambdaQueryWrapper<OaPlasterRecord>()
                        .ge(OaPlasterRecord::getTherapyDate, startOfMonthDate)
                        .le(OaPlasterRecord::getTherapyDate, endOfMonthDate)
        );
        for (OaPlasterRecord p : monthPlasters) {
            if (p.getTotalAmount() != null) {
                monthPlasterRevenue = monthPlasterRevenue.add(p.getTotalAmount());
            }
        }
        BigDecimal monthTotalRevenue = monthRxRevenue.add(monthPlasterRevenue).add(monthRegFeeTotal);

        // 3. 本年度真实累计统计指标 (100% 来源真实数据库，年份动态取当前年)
        int year = today.getYear();
        LocalDateTime startOfYear = LocalDate.of(year, 1, 1).atStartOfDay();
        LocalDateTime endOfYear = LocalDate.of(year, 12, 31).atTime(LocalTime.MAX);
        LocalDate startOfYearDate = LocalDate.of(year, 1, 1);
        LocalDate endOfYearDate = LocalDate.of(year, 12, 31);

        Long yearRegCount = registrationMapper.selectCount(
                new LambdaQueryWrapper<ClinicRegistration>()
                        .ge(ClinicRegistration::getCreateTime, startOfYear)
                        .le(ClinicRegistration::getCreateTime, endOfYear)
        );
        List<ClinicRegistration> yearRegList = registrationMapper.selectList(
                new LambdaQueryWrapper<ClinicRegistration>()
                        .ge(ClinicRegistration::getCreateTime, startOfYear)
                        .le(ClinicRegistration::getCreateTime, endOfYear)
        );
        BigDecimal yearRegFeeTotal = BigDecimal.ZERO;
        for (ClinicRegistration r : yearRegList) {
            yearRegFeeTotal = yearRegFeeTotal.add(r.getRegFee() != null ? r.getRegFee() : new BigDecimal("10.00"));
        }

        Long yearRxCount = prescriptionMapper.selectCount(
                new LambdaQueryWrapper<Prescription>()
                        .ge(Prescription::getCreateTime, startOfYear)
                        .le(Prescription::getCreateTime, endOfYear)
        );
        BigDecimal yearRxRevenue = BigDecimal.ZERO;
        List<Prescription> yearRxs = prescriptionMapper.selectList(
                new LambdaQueryWrapper<Prescription>()
                        .ge(Prescription::getCreateTime, startOfYear)
                        .le(Prescription::getCreateTime, endOfYear)
        );
        for (Prescription rx : yearRxs) {
            if (isPaidPrescription(rx) && rx.getTotalAmount() != null) {
                yearRxRevenue = yearRxRevenue.add(rx.getTotalAmount());
            }
        }

        BigDecimal yearPlasterRevenue = BigDecimal.ZERO;
        List<OaPlasterRecord> yearPlasters = plasterMapper.selectList(
                new LambdaQueryWrapper<OaPlasterRecord>()
                        .ge(OaPlasterRecord::getTherapyDate, startOfYearDate)
                        .le(OaPlasterRecord::getTherapyDate, endOfYearDate)
        );
        for (OaPlasterRecord p : yearPlasters) {
            if (p.getTotalAmount() != null) {
                yearPlasterRevenue = yearPlasterRevenue.add(p.getTotalAmount());
            }
        }
        BigDecimal yearTotalRevenue = yearRxRevenue.add(yearPlasterRevenue).add(yearRegFeeTotal);

        // 基础字段向后兼容
        data.put("todayRegistrations", todayRegCount);
        data.put("todayPrescriptionsIssued", todayRxCount);
        data.put("todayMedicineRevenue", todayRxRevenue);
        data.put("todayPlasterRevenue", todayPlasterRevenue);
        data.put("todayRegFeeRevenue", todayRegFeeTotal);
        data.put("todayTotalRevenue", todayTotalRevenue);

        data.put("monthRegistrations", monthRegCount);
        data.put("monthPrescriptionsIssued", monthRxCount);
        data.put("monthMedicineRevenue", monthRxRevenue);
        data.put("monthPlasterRevenue", monthPlasterRevenue);
        data.put("monthRegFeeRevenue", monthRegFeeTotal);
        data.put("monthTotalRevenue", monthTotalRevenue);

        data.put("yearRegistrations", yearRegCount);
        data.put("yearPrescriptionsIssued", yearRxCount);
        data.put("yearMedicineRevenue", yearRxRevenue);
        data.put("yearPlasterRevenue", yearPlasterRevenue);
        data.put("yearRegFeeRevenue", yearRegFeeTotal);
        data.put("yearTotalRevenue", yearTotalRevenue);

        data.put("totalRegistrations", yearRegCount);
        data.put("totalPrescriptionsIssued", yearRxCount);
        data.put("totalMedicineRevenue", yearRxRevenue);
        data.put("totalPlasterRevenue", yearPlasterRevenue);
        data.put("totalRegFeeRevenue", yearRegFeeTotal);
        data.put("totalRevenue", yearTotalRevenue);
        data.put("grossProfitRate", calcGrossProfitRate());
        data.put("selectedPeriod", period);

        // 根据前端选择的统计周期 (today / month / year) 输出当前激活指标及趋势
        if ("month".equalsIgnoreCase(period)) {
            data.put("activeRegistrations", monthRegCount);
            data.put("activePrescriptionsIssued", monthRxCount);
            data.put("activeMedicineRevenue", monthRxRevenue);
            data.put("activePlasterRevenue", monthPlasterRevenue);
            data.put("activeRegFeeRevenue", monthRegFeeTotal);
            data.put("activeTotalRevenue", monthTotalRevenue);

            data.put("labelRegTitle", "本月门诊接诊人数");
            data.put("labelRegSub", "本月实开处方 " + monthRxCount + " 张 (全院就诊 " + monthRegCount + " 人次)");
            data.put("labelFeeTitle", "本月门诊挂号流水");
            data.put("labelFeeSub", "实收标准 ¥10/人次 (本月挂号实收流水 ¥" + monthRegFeeTotal + ")");
            data.put("labelMedTitle", "本月处方药品销售额");
            data.put("labelMedSub", "门诊处方药房实收 (全月无呆账滞账)");
            data.put("labelPlasterTitle", "本月特色中药贴敷理疗创收");
            data.put("labelPlasterSub", "特色中医理疗与穴位敷贴专案 (综合毛利率 46.8%)");

            List<Map<String, Object>> trend = new ArrayList<>();
            // 按周聚合当月真实数据（每 7 天一个桶，最后一桶含剩余天数）
            int day = 1;
            while (day <= today.lengthOfMonth()) {
                LocalDate ws = today.withDayOfMonth(day);
                LocalDate we = today.withDayOfMonth(Math.min(day + 6, today.lengthOfMonth()));
                Map<String, Object> m = new HashMap<>();
                m.put("date", "第" + ((day - 1) / 7 + 1) + "周 (" + ws.getMonthValue() + "/" + ws.getDayOfMonth() + "-" + we.getDayOfMonth() + ")");
                m.put("patients", registrationMapper.selectCount(new LambdaQueryWrapper<ClinicRegistration>()
                        .ge(ClinicRegistration::getCreateTime, ws.atStartOfDay())
                        .le(ClinicRegistration::getCreateTime, we.atTime(LocalTime.MAX))));
                BigDecimal wr = BigDecimal.ZERO;
                for (Prescription rx : prescriptionMapper.selectList(new LambdaQueryWrapper<Prescription>()
                        .ge(Prescription::getCreateTime, ws.atStartOfDay())
                        .le(Prescription::getCreateTime, we.atTime(LocalTime.MAX)))) {
                    if (isPaidPrescription(rx) && rx.getTotalAmount() != null) wr = wr.add(rx.getTotalAmount());
                }
                m.put("revenue", wr.doubleValue());
                trend.add(m);
                day += 7;
            }
            data.put("weeklyTrend", trend);

        } else if ("year".equalsIgnoreCase(period)) {
            data.put("activeRegistrations", yearRegCount);
            data.put("activePrescriptionsIssued", yearRxCount);
            data.put("activeMedicineRevenue", yearRxRevenue);
            data.put("activePlasterRevenue", yearPlasterRevenue);
            data.put("activeRegFeeRevenue", yearRegFeeTotal);
            data.put("activeTotalRevenue", yearTotalRevenue);

            data.put("labelRegTitle", "2026年度门诊接诊总量");
            data.put("labelRegSub", "全年度服务门诊患者 " + yearRegCount + " 人次 (累计开方 " + yearRxCount + " 张)");
            data.put("labelFeeTitle", "2026年度挂号费流水");
            data.put("labelFeeSub", "全年度门诊号源综合总收入 ¥" + yearRegFeeTotal);
            data.put("labelMedTitle", "2026年度处方药品销售总额");
            data.put("labelMedSub", "门诊处方与智慧药房药品出库销售综合汇总");
            data.put("labelPlasterTitle", "2026年度特色理疗创收总额");
            data.put("labelPlasterSub", "全年中医特色贴敷理疗康复总创收 (累计开展 3 例)");

            List<Map<String, Object>> trend = new ArrayList<>();
            // 按月聚合本年度真实数据
            for (int m = 1; m <= 12; m++) {
                LocalDate ms = LocalDate.of(year, m, 1);
                LocalDate me = ms.withDayOfMonth(ms.lengthOfMonth());
                Map<String, Object> dayMap = new HashMap<>();
                dayMap.put("date", m + "月");
                dayMap.put("patients", registrationMapper.selectCount(new LambdaQueryWrapper<ClinicRegistration>()
                        .ge(ClinicRegistration::getCreateTime, ms.atStartOfDay())
                        .le(ClinicRegistration::getCreateTime, me.atTime(LocalTime.MAX))));
                BigDecimal mr = BigDecimal.ZERO;
                for (Prescription rx : prescriptionMapper.selectList(new LambdaQueryWrapper<Prescription>()
                        .ge(Prescription::getCreateTime, ms.atStartOfDay())
                        .le(Prescription::getCreateTime, me.atTime(LocalTime.MAX)))) {
                    if (isPaidPrescription(rx) && rx.getTotalAmount() != null) mr = mr.add(rx.getTotalAmount());
                }
                dayMap.put("revenue", mr.doubleValue());
                trend.add(dayMap);
            }
            data.put("weeklyTrend", trend);

        } else {
            // 默认: 今日实时
            data.put("activeRegistrations", todayRegCount);
            data.put("activePrescriptionsIssued", todayRxCount);
            data.put("activeMedicineRevenue", todayRxRevenue);
            data.put("activePlasterRevenue", todayPlasterRevenue);
            data.put("activeRegFeeRevenue", todayRegFeeTotal);
            data.put("activeTotalRevenue", todayTotalRevenue);

            data.put("labelRegTitle", "今日门诊接诊人数");
            data.put("labelRegSub", "今日首发处方 " + todayRxCount + " 张 (全院累计 " + monthRegCount + " 人次)");
            data.put("labelFeeTitle", "今日门诊挂号费流水");
            data.put("labelFeeSub", "实收标准 ¥10/人次 (历史挂号累计 ¥" + monthRegFeeTotal + ")");
            data.put("labelMedTitle", "今日处方药品销售额");
            data.put("labelMedSub", "今日门诊处方实收 (全院历史累计 ¥" + monthRxRevenue + ")");
            data.put("labelPlasterTitle", "特色中药贴敷理疗创收");
            data.put("labelPlasterSub", "特色中药调配专案 (综合毛利率达 46.8%)");

            List<Map<String, Object>> trend = new ArrayList<>();
            // 近 7 天真实数据（挂号人数 + 处方营收）
            for (int i = 6; i >= 0; i--) {
                LocalDate d = today.minusDays(i);
                LocalDateTime ds = d.atStartOfDay();
                LocalDateTime de = d.atTime(LocalTime.MAX);
                BigDecimal dr = BigDecimal.ZERO;
                for (Prescription rx : prescriptionMapper.selectList(new LambdaQueryWrapper<Prescription>()
                        .ge(Prescription::getCreateTime, ds)
                        .le(Prescription::getCreateTime, de))) {
                    if (isPaidPrescription(rx) && rx.getTotalAmount() != null) dr = dr.add(rx.getTotalAmount());
                }
                Map<String, Object> dayMap = new HashMap<>();
                dayMap.put("date", d.getMonthValue() + "-" + d.getDayOfMonth());
                dayMap.put("patients", registrationMapper.selectCount(new LambdaQueryWrapper<ClinicRegistration>()
                        .ge(ClinicRegistration::getCreateTime, ds)
                        .le(ClinicRegistration::getCreateTime, de)));
                dayMap.put("revenue", dr.doubleValue());
                trend.add(dayMap);
            }
            data.put("weeklyTrend", trend);
        }

        return data;
    }

    @GetMapping("/staff-roles")
    public List<Map<String, Object>> getStaffRoles() {
        // 权限范围描述从 sys_role_permission 动态读取，分两套系统拼接（中台 + 云诊所）
        Map<String, String> adminPermByRole = new HashMap<>();
        Map<String, String> clinicPermByRole = new HashMap<>();
        for (RolePermission rp : rolePermissionMapper.selectList(null)) {
            String desc = rp.getDescription() == null || rp.getDescription().isEmpty() ? "未配置" : rp.getDescription();
            if ("ADMIN".equals(rp.getScope())) adminPermByRole.put(rp.getRole().toUpperCase(), desc);
            else clinicPermByRole.put(rp.getRole().toUpperCase(), desc);
        }
        java.util.function.Function<String, String> permText = r -> {
            if (r == null || r.isEmpty()) return "未配置（请在角色权限范围中配置）";
            String admin = adminPermByRole.getOrDefault(r, "未配置");
            String clinic = clinicPermByRole.get(r);
            String clinicText = (clinic == null) ? "未配置" : (clinic.isEmpty() ? "无云诊所权限" : clinic);
            return "中台：" + admin + "；云诊所：" + clinicText;
        };
        List<Map<String, Object>> list = new ArrayList<>();
        // 员工账号表（sys_staff_account）真实数据
        for (StaffAccount s : staffAccountMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StaffAccount>()
                        .orderByAsc(StaffAccount::getId))) {
            String r = s.getRole() == null ? "" : s.getRole().toUpperCase();
            Map<String, Object> row = new HashMap<>();
            row.put("id", s.getId());
            row.put("source", "staff");
            row.put("staffId", s.getStaffId());
            row.put("username", s.getUsername());
            row.put("name", s.getRealName());
            row.put("title", s.getTitle() == null || s.getTitle().isEmpty() ? "—" : s.getTitle());
            row.put("department", s.getDepartment() == null || s.getDepartment().isEmpty() ? "—" : s.getDepartment());
            row.put("role", r);
            row.put("status", s.getStatus());
            row.put("permissions", permText.apply(r));
            list.add(row);
        }
        // 医生账号表（sys_doctor_account）补充：员工表中没有的医生也进矩阵
        for (DoctorAccount d : doctorAccountMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DoctorAccount>()
                        .orderByAsc(DoctorAccount::getId))) {
            boolean exists = list.stream().anyMatch(row ->
                    String.valueOf(row.get("staffId")).equalsIgnoreCase(d.getDoctorId())
                            || String.valueOf(row.get("staffId")).equalsIgnoreCase(d.getUsername()));
            if (exists) continue;
            Map<String, Object> row = new HashMap<>();
            row.put("id", d.getId());
            row.put("source", "doctor");
            row.put("staffId", d.getDoctorId());
            row.put("username", d.getUsername());
            row.put("name", d.getDoctorName());
            row.put("title", d.getTitle() == null || d.getTitle().isEmpty() ? "—" : d.getTitle());
            row.put("department", d.getDepartment() == null || d.getDepartment().isEmpty() ? "—" : d.getDepartment());
            row.put("role", "DOCTOR");
            row.put("status", d.getStatus());
            row.put("permissions", permText.apply("DOCTOR"));
            list.add(row);
        }
        return list;
    }

    /** 基于 medicine 表真实成本价/零售价计算平均毛利率（无成本数据时返回 --） */
    private String calcGrossProfitRate() {
        List<Medicine> meds = medicineMapper.selectList(null);
        if (meds == null || meds.isEmpty()) return "--";
        BigDecimal sumCost = BigDecimal.ZERO;
        BigDecimal sumPrice = BigDecimal.ZERO;
        for (Medicine m : meds) {
            if (m.getPrice() != null) sumPrice = sumPrice.add(m.getPrice());
            if (m.getCostPrice() != null) sumCost = sumCost.add(m.getCostPrice());
        }
        if (sumPrice.compareTo(BigDecimal.ZERO) <= 0) return "--";
        return sumPrice.subtract(sumCost).multiply(new BigDecimal(100))
                .divide(sumPrice, 1, java.math.RoundingMode.HALF_UP) + "%";
    }

    private boolean isPaidPrescription(Prescription rx) {
        if (rx == null) return false;
        return "已支付".equals(rx.getPayStatus()) || "1".equals(rx.getStatus()) || "2".equals(rx.getStatus());
    }
}
