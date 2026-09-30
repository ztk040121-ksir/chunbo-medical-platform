package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.ClinicPatientFollowup;
import com.chunbo.medical.mapper.ClinicPatientFollowupMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 国家基本公共卫生服务慢病随访与健康管理中枢
 * 涵盖：高血压、2型糖尿病等基层慢病自动分阶随访计划生成、随访结果录入与控制率指标
 */
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
        if (followup.getCreatedAt() == null) {
            followup.setCreatedAt(LocalDateTime.now());
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

    /**
     * AI 慢病随访计划自动生成引擎（依据《国家基层高血压/糖尿病防治指南》生成 3 阶段分阶随访任务）
     */
    @PostMapping("/ai-generate-plan")
    public Map<String, Object> generateChronicFollowupPlan(@RequestBody Map<String, Object> req) {
        Map<String, Object> res = new HashMap<>();
        Long patientId = req.get("patientId") != null ? Long.parseLong(req.get("patientId").toString()) : null;
        String patientName = req.getOrDefault("patientName", "慢病患者").toString();
        String patientPhone = req.getOrDefault("patientPhone", "").toString();
        String diagnosis = req.getOrDefault("diagnosis", "原发性高血压").toString();
        String doctorName = req.getOrDefault("doctorName", "责任全科医生").toString();

        List<ClinicPatientFollowup> generatedTasks = new ArrayList<>();
        LocalDate today = LocalDate.now();

        boolean isHypertension = diagnosis.contains("高血压");
        boolean isDiabetes = diagnosis.contains("糖") || diagnosis.contains("消渴");

        // 阶段 1：Day 3 - 用药初期不良反应追踪
        ClinicPatientFollowup f1 = new ClinicPatientFollowup();
        f1.setFollowupNo("SF" + System.currentTimeMillis() + "01");
        f1.setPatientId(patientId);
        f1.setPatientName(patientName);
        f1.setPatientPhone(patientPhone);
        f1.setDiagnosis(diagnosis);
        f1.setPlanDate(today.plusDays(3));
        f1.setCreatedAt(LocalDateTime.now());
        f1.setOperatorName(doctorName);
        if (isHypertension) {
            f1.setFollowupContent("【阶段一 · 用药耐受性排查】追踪钙拮抗剂(CCB)面部潮红、踝部水肿，或ARB干咳、直立性低血压等不良反应，核查每日晨起规律服药依从性。");
        } else if (isDiabetes) {
            f1.setFollowupContent("【阶段一 · 低血糖早期防范】询问餐前有无心慌、手抖、出冷汗等低血糖轻微前驱症状，二甲双胍随餐服用胃肠耐受度确认。");
        } else {
            f1.setFollowupContent("【阶段一 · 疗效初期评估】回访发热、咳嗽缓解进度，有无皮疹、胃肠道不适等服药反应。");
        }
        followupMapper.insert(f1);
        generatedTasks.add(f1);

        // 阶段 2：Day 14 - 靶标控制率与生活方式复核
        ClinicPatientFollowup f2 = new ClinicPatientFollowup();
        f2.setFollowupNo("SF" + System.currentTimeMillis() + "02");
        f2.setPatientId(patientId);
        f2.setPatientName(patientName);
        f2.setPatientPhone(patientPhone);
        f2.setDiagnosis(diagnosis);
        f2.setPlanDate(today.plusDays(14));
        f2.setCreatedAt(LocalDateTime.now());
        f2.setOperatorName(doctorName);
        if (isHypertension) {
            f2.setFollowupContent("【阶段二 · 降压达标率评估】复测清晨静息血压（目标值一般<140/90 mmHg，耐受良好者<130/80 mmHg），督促限盐<5g/日。");
        } else if (isDiabetes) {
            f2.setFollowupContent("【阶段二 · 空腹与餐后血糖评估】复核空腹血糖（建议 4.4~7.0 mmol/L）及餐后2小时血糖，评估主食控量与餐后散步依从性。");
        } else {
            f2.setFollowupContent("【阶段二 · 巩固疗效复测】评估病症是否彻底痊愈，指导停药或按疗程转为巩固调理。");
        }
        followupMapper.insert(f2);
        generatedTasks.add(f2);

        // 阶段 3：Day 30 - 慢病复诊与靶器官生化评估
        ClinicPatientFollowup f3 = new ClinicPatientFollowup();
        f3.setFollowupNo("SF" + System.currentTimeMillis() + "03");
        f3.setPatientId(patientId);
        f3.setPatientName(patientName);
        f3.setPatientPhone(patientPhone);
        f3.setDiagnosis(diagnosis);
        f3.setPlanDate(today.plusDays(30));
        f3.setCreatedAt(LocalDateTime.now());
        f3.setOperatorName(doctorName);
        f3.setFollowupContent("【阶段三 · 满月门诊复诊】门诊面诊复诊，评估近1月血压/血糖达标平稳率，建议复查肝肾功能、电解质及尿微量白蛋白，必要时微调处方。");
        followupMapper.insert(f3);
        generatedTasks.add(f3);

        res.put("success", true);
        res.put("message", "已依据国家基本公卫规范成功为患者【" + patientName + "】生成 3 阶段慢病随访管理计划！");
        res.put("tasks", generatedTasks);
        return res;
    }
}
