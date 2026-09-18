package com.chunbo.medical.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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

    @Tool(description = "根据患者ID查询就诊患者健康档案，包括姓名、年龄、过敏史、既往慢病史。开方前必须优先调用此工具！")
    public Patient queryPatientProfile(Long patientId) {
        log.info("[AI Tool Calling] 检索患者档案 patientId={}", patientId);
        Patient patient = patientMapper.selectById(patientId);
        if (patient == null) {
            patient = new Patient();
            patient.setName("未知患者");
            patient.setAllergies("未记录过敏史");
        }
        return patient;
    }

    @Tool(description = "根据患者ID查询历史开立处方及诊断记录，辅助研判既往用药依从性与疗效")
    public List<Prescription> queryPrescriptionHistory(Long patientId) {
        log.info("[AI Tool Calling] 检索患者历史处方 patientId={}", patientId);
        return prescriptionMapper.selectList(
                new LambdaQueryWrapper<Prescription>()
                        .eq(Prescription::getPatientId, patientId)
                        .orderByDesc(Prescription::getCreateTime)
                        .last("LIMIT 5")
        );
    }

    @Tool(description = "根据药品通用名模糊查询诊所药房实时库存、单价及规格包装。推荐处方前必须确保库存充足！")
    public List<Medicine> queryMedicineStock(String medicineKeyword) {
        log.info("[AI Tool Calling] 核查药房库存 keyword={}", medicineKeyword);
        return medicineMapper.selectList(
                new LambdaQueryWrapper<Medicine>()
                        .like(Medicine::getName, medicineKeyword)
                        .gt(Medicine::getStock, 0)
        );
    }

    @Tool(description = "根据疾病关键词检索基层临床指南、标准用药规范及警示禁忌")
    public List<ClinicalCase> queryClinicalGuideline(String diseaseKeyword) {
        log.info("[AI Tool Calling] 检索基层临床指南 keyword={}", diseaseKeyword);
        return clinicalCaseMapper.selectList(
                new LambdaQueryWrapper<ClinicalCase>()
                        .like(ClinicalCase::getDiseaseName, diseaseKeyword)
                        .or()
                        .like(ClinicalCase::getTypicalSymptoms, diseaseKeyword)
        );
    }
}
