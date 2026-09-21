package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.ClinicSchedule;
import com.chunbo.medical.mapper.ClinicScheduleMapper;
import com.chunbo.medical.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    @Autowired
    private ClinicScheduleMapper scheduleMapper;

    @Autowired
    private CurrentUserService currentUserService;

    @GetMapping("/weekly")
    public Map<String, Object> getWeeklySchedule(
            @RequestParam(value = "startDate", required = false) String startDateStr,
            @RequestParam(value = "doctorId", required = false) Long doctorId) {

        LocalDate start = (startDateStr != null && !startDateStr.isEmpty())
                ? LocalDate.parse(startDateStr)
                : LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
        LocalDate end = start.plusDays(6);

        LambdaQueryWrapper<ClinicSchedule> qw = new LambdaQueryWrapper<>();
        qw.ge(ClinicSchedule::getScheduleDate, start)
          .le(ClinicSchedule::getScheduleDate, end);
        if (doctorId != null) {
            qw.eq(ClinicSchedule::getDoctorId, doctorId);
        }
        qw.orderByAsc(ClinicSchedule::getScheduleDate).orderByAsc(ClinicSchedule::getDoctorId);
        List<ClinicSchedule> list = scheduleMapper.selectList(qw);

        Map<String, Object> res = new HashMap<>();
        res.put("startDate", start.toString());
        res.put("endDate", end.toString());
        res.put("schedules", list);
        return res;
    }

    /** 解析医生身份：优先前端传值，缺省用当前登录人；doctorId 缺失时按已有同名排班回填，仍无则用时间戳 */
    private void fillDoctorIdentity(ClinicSchedule s, jakarta.servlet.http.HttpServletRequest request) {
        String loginName = currentUserService.displayName(request);
        if (s.getDoctorName() == null || s.getDoctorName().isBlank()) {
            s.setDoctorName(loginName != null && !loginName.isBlank() ? loginName : "系统用户");
        }
        if (s.getDoctorId() == null) {
            // 先按同名历史排班回填 doctorId，避免 NOT NULL 约束 500 与同医生多 ID
            ClinicSchedule exist = scheduleMapper.selectOne(new LambdaQueryWrapper<ClinicSchedule>()
                    .eq(ClinicSchedule::getDoctorName, s.getDoctorName())
                    .last("LIMIT 1"));
            s.setDoctorId(exist != null && exist.getDoctorId() != null ? exist.getDoctorId()
                    : System.currentTimeMillis() % 1000000L);
        }
        if (s.getDepartment() == null || s.getDepartment().isBlank()) s.setDepartment("全科门诊");
        if (s.getShiftType() == null || s.getShiftType().isBlank()) s.setShiftType("全天班");
        if (s.getQuota() == null) s.setQuota(50);
        if (s.getConsultationFee() == null) s.setConsultationFee(new java.math.BigDecimal("10.00"));
        if (s.getStatus() == null || s.getStatus().isBlank()) s.setStatus("active");
    }

    /** 同医生+同日期+同班次 upsert：重复保存不再插重复行 */
    private ClinicSchedule upsertSchedule(ClinicSchedule schedule) {
        ClinicSchedule exist = scheduleMapper.selectOne(new LambdaQueryWrapper<ClinicSchedule>()
                .eq(ClinicSchedule::getDoctorName, schedule.getDoctorName())
                .eq(ClinicSchedule::getScheduleDate, schedule.getScheduleDate())
                .eq(ClinicSchedule::getShiftType, schedule.getShiftType())
                .last("LIMIT 1"));
        if (exist != null) {
            schedule.setId(exist.getId());
            schedule.setBookedCount(exist.getBookedCount());
            scheduleMapper.updateById(schedule);
            return schedule;
        }
        scheduleMapper.insert(schedule);
        return schedule;
    }

    @PostMapping("/save")
    public Map<String, Object> saveSchedule(@RequestBody ClinicSchedule schedule, jakarta.servlet.http.HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        if (schedule.getScheduleDate() == null) {
            res.put("success", false);
            res.put("message", "排班日期不能为空！");
            return res;
        }
        fillDoctorIdentity(schedule, request);
        schedule.setDayOfWeek(schedule.getScheduleDate().getDayOfWeek().getValue());
        ClinicSchedule saved = upsertSchedule(schedule);
        res.put("success", true);
        res.put("schedule", saved);
        return res;
    }

    /** 复制上周排班：支持传入目标周周一（查看哪周就复制到哪周），默认复制到本周 */
    @PostMapping("/copy-last-week")
    public Map<String, Object> copyLastWeek(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> res = new HashMap<>();
        LocalDate targetMonday = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
        if (payload != null && payload.get("startDate") != null && !payload.get("startDate").toString().isEmpty()) {
            targetMonday = LocalDate.parse(payload.get("startDate").toString());
        }
        LocalDate sourceMonday = targetMonday.minusDays(7);
        LocalDate sourceSunday = sourceMonday.plusDays(6);

        LambdaQueryWrapper<ClinicSchedule> qw = new LambdaQueryWrapper<>();
        qw.ge(ClinicSchedule::getScheduleDate, sourceMonday)
          .le(ClinicSchedule::getScheduleDate, sourceSunday);
        List<ClinicSchedule> lastWeekList = scheduleMapper.selectList(qw);

        if (lastWeekList.isEmpty()) {
            res.put("success", false);
            res.put("copiedCount", 0);
            res.put("message", "上周（" + sourceMonday + " 至 " + sourceSunday.plusDays(0) + "）没有任何排班记录，请先设置上周班次或使用排班模板！");
            return res;
        }

        int count = 0;
        for (ClinicSchedule old : lastWeekList) {
            ClinicSchedule newSch = new ClinicSchedule();
            newSch.setDoctorId(old.getDoctorId());
            newSch.setDoctorName(old.getDoctorName());
            newSch.setDepartment(old.getDepartment());
            newSch.setScheduleDate(old.getScheduleDate().plusDays(7));
            newSch.setDayOfWeek(old.getDayOfWeek());
            newSch.setShiftType(old.getShiftType());
            newSch.setShiftTimeRange(old.getShiftTimeRange());
            newSch.setQuota(old.getQuota());
            newSch.setBookedCount(0);
            newSch.setConsultationFee(old.getConsultationFee());
            newSch.setStatus("active");
            upsertSchedule(newSch); // 已存在同班次则更新，不重复插行
            count++;
        }
        res.put("success", true);
        res.put("copiedCount", count);
        res.put("message", "成功从 " + sourceMonday + " 那一周复制 " + count + " 条医生排班记录！");
        return res;
    }

    /**
     * 排班模板一键应用：按模板的 7 天配置铺满目标周（传 startDate 为目标周周一）。
     * payload: { doctorName?, department?, startDate?, days: [{ dayOfWeek:1-7, enabled, shiftType, quota, consultationFee }] }
     */
    @PostMapping("/apply-template")
    public Map<String, Object> applyTemplate(@RequestBody Map<String, Object> payload, jakarta.servlet.http.HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        Object daysObj = payload.get("days");
        if (!(daysObj instanceof List)) {
            res.put("success", false);
            res.put("message", "模板数据不合法（缺少 days）！");
            return res;
        }
        String doctorName = payload.get("doctorName") != null && !payload.get("doctorName").toString().isEmpty()
                ? payload.get("doctorName").toString() : null;
        String department = payload.getOrDefault("department", "全科门诊").toString();
        LocalDate weekMonday = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
        if (payload.get("startDate") != null && !payload.get("startDate").toString().isEmpty()) {
            weekMonday = LocalDate.parse(payload.get("startDate").toString());
        }

        List<?> days = (List<?>) daysObj;
        int count = 0;
        for (Object o : days) {
            @SuppressWarnings("unchecked")
            Map<String, Object> d = (Map<String, Object>) o;
            boolean enabled = Boolean.TRUE.equals(d.get("enabled")) || "true".equals(String.valueOf(d.get("enabled")));
            if (!enabled) continue;
            int dayOfWeek = Integer.parseInt(d.getOrDefault("dayOfWeek", "1").toString());
            ClinicSchedule s = new ClinicSchedule();
            s.setDoctorName(doctorName);
            s.setDepartment(department);
            s.setScheduleDate(weekMonday.plusDays(dayOfWeek - 1L));
            s.setDayOfWeek(dayOfWeek);
            s.setShiftType(d.get("shiftType") != null ? d.get("shiftType").toString() : "全天班");
            s.setQuota(d.get("quota") != null ? Integer.parseInt(d.get("quota").toString()) : 50);
            s.setConsultationFee(d.get("consultationFee") != null
                    ? new java.math.BigDecimal(d.get("consultationFee").toString())
                    : new java.math.BigDecimal("10.00"));
            s.setStatus("active");
            fillDoctorIdentity(s, request);
            upsertSchedule(s);
            count++;
        }
        res.put("success", true);
        res.put("appliedCount", count);
        res.put("message", "模板已一键应用到 " + weekMonday + " 那一周，共生成 " + count + " 条班次！");
        return res;
    }
}
