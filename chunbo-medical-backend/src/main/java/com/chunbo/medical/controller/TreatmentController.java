package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.ClinicTreatmentRecord;
import com.chunbo.medical.mapper.ClinicTreatmentRecordMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/treatment")
public class TreatmentController {

    @Autowired
    private ClinicTreatmentRecordMapper treatmentMapper;

    @GetMapping("/list")
    public List<ClinicTreatmentRecord> getList(@RequestParam(value = "status", required = false) String status) {
        LambdaQueryWrapper<ClinicTreatmentRecord> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty()) {
            qw.eq(ClinicTreatmentRecord::getStatus, status);
        }
        qw.orderByDesc(ClinicTreatmentRecord::getCreatedAt);
        return treatmentMapper.selectList(qw);
    }

    @PostMapping("/execute/{id}")
    public Map<String, Object> executeTreatment(@PathVariable("id") Long id) {
        Map<String, Object> res = new HashMap<>();
        ClinicTreatmentRecord r = treatmentMapper.selectById(id);
        if (r == null) {
            res.put("success", false);
            res.put("message", "未找到理疗记录！");
            return res;
        }
        r.setStatus("completed");
        r.setExecutedAt(LocalDateTime.now());
        treatmentMapper.updateById(r);

        res.put("success", true);
        res.put("message", "特色贴敷/理疗已由李护士执行完成！耗材与药膏辅料已核销出库！");
        return res;
    }
}
