package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("clinical_case")
public class ClinicalCase {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String diseaseName;
    private String typicalSymptoms;
    private String diagnosisStandard;
    private String recommendedTreatment;
    private String cautionWarnings;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDiseaseName() { return diseaseName; }
    public void setDiseaseName(String diseaseName) { this.diseaseName = diseaseName; }
    public String getTypicalSymptoms() { return typicalSymptoms; }
    public void setTypicalSymptoms(String typicalSymptoms) { this.typicalSymptoms = typicalSymptoms; }
    public String getDiagnosisStandard() { return diagnosisStandard; }
    public void setDiagnosisStandard(String diagnosisStandard) { this.diagnosisStandard = diagnosisStandard; }
    public String getRecommendedTreatment() { return recommendedTreatment; }
    public void setRecommendedTreatment(String recommendedTreatment) { this.recommendedTreatment = recommendedTreatment; }
    public String getCautionWarnings() { return cautionWarnings; }
    public void setCautionWarnings(String cautionWarnings) { this.cautionWarnings = cautionWarnings; }

}
