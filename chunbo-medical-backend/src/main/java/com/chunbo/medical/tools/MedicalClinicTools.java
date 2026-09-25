package com.chunbo.medical.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.config.ToolResultHolder;
import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.entity.ClinicalCase;
import com.chunbo.medical.entity.Medicine;
import com.chunbo.medical.entity.Patient;
import com.chunbo.medical.entity.Prescription;
import com.chunbo.medical.mapper.ClinicalCaseMapper;
import com.chunbo.medical.mapper.MedicineMapper;
import com.chunbo.medical.mapper.PatientMapper;
import com.chunbo.medical.mapper.PrescriptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基层云诊所 Function Calling 工具集
 * 对应简历业绩：把“查询患者档案 / 历史处方 / 药品库存”封装为 Tool，由 LLM 自主规划调用次序
 */
@Component
public class MedicalClinicTools {

    private static final Logger log = LoggerFactory.getLogger(MedicalClinicTools.class);

    @Autowired
    private PatientMapper patientMapper;
    @Autowired
    private MedicineMapper medicineMapper;
    @Autowired
    private PrescriptionMapper prescriptionMapper;
    @Autowired
    private ClinicalCaseMapper clinicalCaseMapper;
    @Autowired(required = false)
    private com.chunbo.medical.mapper.ClinicRegistrationMapper clinicRegistrationMapper;

    @Tool(description = "根据患者18位身份证号(idCard)检索就诊患者在全科门诊的健康档案、既往慢病史、药物过敏史、历史就诊与处方记录。判断是否为同一个患者并获取既往病历时调用。")
    public java.util.Map<String, Object> queryPatientMedicalHistoryByIdCard(
            @org.springframework.ai.tool.annotation.ToolParam(description = "患者18位身份证号码") String idCard,
            ToolContext toolContext) {
        log.info("[AI Tool Calling] 根据身份证号检索患者历史病历档案 idCard={}", idCard);
        java.util.Map<String, Object> res = new java.util.HashMap<>();
        if (idCard == null || idCard.trim().isEmpty()) {
            return res;
        }
        String cleanId = idCard.trim().toUpperCase();

        // 1. 优先查 patient 表已绑定身份证记录
        Patient patient = patientMapper.selectOne(
                new LambdaQueryWrapper<Patient>().eq(Patient::getIdCard, cleanId).last("LIMIT 1"));

        // 2. 若 patient 未直接命中，尝试查往期挂号单 clinic_registration 中的真实身份证关联
        if (patient == null && clinicRegistrationMapper != null) {
            com.chunbo.medical.entity.ClinicRegistration reg = clinicRegistrationMapper.selectOne(
                    new LambdaQueryWrapper<com.chunbo.medical.entity.ClinicRegistration>()
                            .eq(com.chunbo.medical.entity.ClinicRegistration::getIdCard, cleanId)
                            .orderByDesc(com.chunbo.medical.entity.ClinicRegistration::getCreateTime)
                            .last("LIMIT 1"));
            if (reg != null && reg.getPatientId() != null) {
                patient = patientMapper.selectById(reg.getPatientId());
            }
        }

        if (patient == null) {
            res.put("isSamePatient", false);
            res.put("hasHistory", false);
            res.put("message", "未检索到该身份证号的既往历史档案，属于初次就诊患者");
            return res;
        }

        res.put("isSamePatient", true);
        res.put("hasHistory", true);
        res.put("patientId", patient.getId());
        res.put("name", patient.getName());
        res.put("idCard", cleanId);
        res.put("gender", patient.getGender());
        res.put("age", patient.getAge());
        res.put("allergies", patient.getAllergies() != null ? patient.getAllergies() : "未记录药物过敏史");
        res.put("medicalHistory", patient.getMedicalHistory() != null ? patient.getMedicalHistory() : "无明确既往慢病记录");
        res.put("remarks", patient.getRemarks() != null ? patient.getRemarks() : "");

        // 4. 查询患者历史开方与诊断记录
        List<Prescription> pastPrescriptions = prescriptionMapper.selectList(
                new LambdaQueryWrapper<Prescription>()
                        .eq(Prescription::getPatientId, patient.getId())
                        .orderByDesc(Prescription::getCreateTime)
                        .last("LIMIT 3")
        );
        java.util.List<String> diagList = new java.util.ArrayList<>();
        if (pastPrescriptions != null && !pastPrescriptions.isEmpty()) {
            for (Prescription p : pastPrescriptions) {
                if (p.getDiagnosis() != null && !p.getDiagnosis().isBlank()) {
                    diagList.add(p.getDiagnosis() + (p.getDoctorName() != null ? "(" + p.getDoctorName() + ")" : ""));
                }
            }
        }
        res.put("pastDiagnoses", diagList);

        // 5. 往期挂号就诊记录次数
        int visitCount = 0;
        if (clinicRegistrationMapper != null) {
            visitCount = Math.toIntExact(clinicRegistrationMapper.selectCount(
                    new LambdaQueryWrapper<com.chunbo.medical.entity.ClinicRegistration>()
                            .eq(com.chunbo.medical.entity.ClinicRegistration::getIdCard, cleanId)
                            .or()
                            .eq(com.chunbo.medical.entity.ClinicRegistration::getPatientId, patient.getId())
            ));
        }
        res.put("pastVisitsCount", Math.max(visitCount, pastPrescriptions != null ? pastPrescriptions.size() : 0));

        // 6. 构造供大模型阅读的既往病历精炼总结
        String summary = String.format("【姓名】%s，【性别/年龄】%s/%s岁，【药物过敏史】%s，【既往慢病史】%s，【往期就诊】%d次%s",
                patient.getName(),
                patient.getGender(),
                patient.getAge(),
                res.get("allergies"),
                res.get("medicalHistory"),
                res.get("pastVisitsCount"),
                diagList.isEmpty() ? "" : "，往期诊断：" + String.join("、", diagList)
        );
        res.put("summaryText", summary);

        ToolResultHolder.put(requestIdOf(toolContext), "patientMedicalHistory", res);
        log.info("[AI Tool Calling] 身份证号【{}】成功关联到就诊患者【{}】: {}", cleanId, patient.getName(), summary);
        return res;
    }
    @Tool(description = "根据患者ID查询就诊患者健康档案，包括姓名、年龄、过敏史、既往慢病史。开方前必须优先调用此工具！")
    public Patient queryPatientProfile(Long patientId, ToolContext toolContext) {
        log.info("[AI Tool Calling] 检索患者档案 patientId={}", patientId);
        Patient patient = patientMapper.selectById(patientId);
        if (patient == null) {
            patient = new Patient();
            patient.setName("未知患者");
            patient.setAllergies("未记录过敏史");
        }
        // 结构化结果存入 ToolResultHolder，智能体流结束后由 AbstractAgent 提取下发卡片
        ToolResultHolder.put(requestIdOf(toolContext), "patientProfile", patient);
        return patient;
    }

    @Tool(description = "根据患者ID查询历史开立处方及诊断记录，辅助研判既往用药依从性与疗效")
    public List<Prescription> queryPrescriptionHistory(Long patientId, ToolContext toolContext) {
        log.info("[AI Tool Calling] 检索患者历史处方 patientId={}", patientId);
        List<Prescription> list = prescriptionMapper.selectList(
                new LambdaQueryWrapper<Prescription>()
                        .eq(Prescription::getPatientId, patientId)
                        .orderByDesc(Prescription::getCreateTime)
                        .last("LIMIT 5")
        );
        ToolResultHolder.put(requestIdOf(toolContext), "prescriptionHistory", list);
        return list;
    }

    @Tool(description = "根据药品通用名模糊查询诊所药房实时库存、单价及规格包装。推荐处方前必须确保库存充足！")
    public List<Medicine> queryMedicineStock(String medicineKeyword, ToolContext toolContext) {
        log.info("[AI Tool Calling] 核查药房库存 keyword={}", medicineKeyword);
        List<Medicine> list = medicineMapper.selectList(
                new LambdaQueryWrapper<Medicine>()
                        .like(Medicine::getName, medicineKeyword)
                        .gt(Medicine::getStock, 0)
        );
        ToolResultHolder.put(requestIdOf(toolContext), "medicineStock", list);
        return list;
    }

    @Tool(description = "根据疾病关键词检索基层临床指南、标准用药规范及警示禁忌")
    public List<ClinicalCase> queryClinicalGuideline(String diseaseKeyword, ToolContext toolContext) {
        log.info("[AI Tool Calling] 检索基层临床指南 keyword={}", diseaseKeyword);
        List<ClinicalCase> list = clinicalCaseMapper.selectList(
                new LambdaQueryWrapper<ClinicalCase>()
                        .like(ClinicalCase::getDiseaseName, diseaseKeyword)
                        .or()
                        .like(ClinicalCase::getTypicalSymptoms, diseaseKeyword)
        );
        ToolResultHolder.put(requestIdOf(toolContext), "clinicalGuideline", list);
        return list;
    }

    /** 从工具上下文安全提取本次请求 requestId（无上下文时返回 null，不污染 ToolResultHolder） */
    private String requestIdOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) return null;
        Object v = toolContext.getContext().get(AgentConstant.REQUEST_ID);
        return v == null ? null : String.valueOf(v);
    }
}
