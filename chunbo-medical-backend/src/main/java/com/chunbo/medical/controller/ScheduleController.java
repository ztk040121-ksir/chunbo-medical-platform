package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.ClinicSchedule;
import com.chunbo.medical.mapper.ClinicScheduleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    @Autowired
    private ClinicScheduleMapper scheduleMapper;

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

    @PostMapping("/save")
    public Map<String, Object> saveSchedule(@RequestBody ClinicSchedule schedule) {
        Map<String, Object> res = new HashMap<>();
        if (schedule.getScheduleDate() != null) {
            schedule.setDayOfWeek(schedule.getScheduleDate().getDayOfWeek().getValue());
        }
        if (schedule.getId() == null) {
            scheduleMapper.insert(schedule);
        } else {
            scheduleMapper.updateById(schedule);
        }
        res.put("success", true);
        res.put("schedule", schedule);
        return res;
    }

    @PostMapping("/copy-last-week")
    public Map<String, Object> copyLastWeek() {
        Map<String, Object> res = new HashMap<>();
        LocalDate thisWeekMonday = LocalDate.now().minusDays(LocalDate.now().getDayOfWeek().getValue() - 1);
        LocalDate lastWeekMonday = thisWeekMonday.minusDays(7);
        LocalDate lastWeekSunday = lastWeekMonday.plusDays(6);

        LambdaQueryWrapper<ClinicSchedule> qw = new LambdaQueryWrapper<>();
        qw.ge(ClinicSchedule::getScheduleDate, lastWeekMonday)
          .le(ClinicSchedule::getScheduleDate, lastWeekSunday);
        List<ClinicSchedule> lastWeekList = scheduleMapper.selectList(qw);

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
            scheduleMapper.insert(newSch);
            count++;
        }
        res.put("success", true);
        res.put("copiedCount", count);
        res.put("message", "成功从上一周复制 " + count + " 条医生排班记录！");
        return res;
    }
}
