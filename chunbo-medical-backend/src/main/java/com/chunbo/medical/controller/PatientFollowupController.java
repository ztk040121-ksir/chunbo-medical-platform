package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.ClinicPatientFollowup;
import com.chunbo.medical.mapper.ClinicPatientFollowupMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/followup")
public class PatientFollowupController {

    @Autowired
    private ClinicPatientFollowupMapper followupMapper;

    @GetMapping("/list")
    public List<ClinicPatientFollowup> getList(@RequestParam(value = "patientId", required = false) Long patientId) {
        LambdaQueryWrapper<ClinicPatientFollowup> qw = new LambdaQueryWrapper<>();
        if (patientId != null) {
            qw.eq(ClinicPatientFollowup::getPatientId, patientId);
        }
        qw.orderByDesc(ClinicPatientFollowup::getCreatedAt);
        return followupMapper.selectList(qw);
    }

    @PostMapping("/create")
    public Map<String, Object> createFollowup(@RequestBody ClinicPatientFollowup followup) {
        Map<String, Object> res = new HashMap<>();
        followup.setFollowupNo("SF" + System.currentTimeMillis());
        if (followup.getPlanDate() == null) {
            followup.setPlanDate(LocalDate.now().plusDays(7));
        }
        followupMapper.insert(followup);
        res.put("success", true);
        res.put("followup", followup);
        return res;
    }

    @PostMapping("/update")
    public Map<String, Object> updateFollowup(@RequestBody ClinicPatientFollowup followup) {
        Map<String, Object> res = new HashMap<>();
        if (followup.getActualDate() == null) {
            followup.setActualDate(LocalDate.now());
        }
        followupMapper.updateById(followup);
        res.put("success", true);
        res.put("message", "随访结果已录入归档！");
        return res;
    }
}
