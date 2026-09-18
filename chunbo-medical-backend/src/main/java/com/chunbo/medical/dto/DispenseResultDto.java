package com.chunbo.medical.dto;

import lombok.Data;
import java.util.List;

@Data
public class DispenseResultDto {
    private boolean success;
    private String message;
    private Long prescriptionId;
    private String prescriptionNo;
    private String patientName;
    private List<StockDeductionInfo> deductions;
    private List<String> details;
    private List<String> deductionLogs;
    private String voiceBroadcastText;

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Long getPrescriptionId() { return prescriptionId; }
    public void setPrescriptionId(Long prescriptionId) { this.prescriptionId = prescriptionId; }

    public String getPrescriptionNo() { return prescriptionNo; }
    public void setPrescriptionNo(String prescriptionNo) { this.prescriptionNo = prescriptionNo; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public List<StockDeductionInfo> getDeductions() { return deductions; }
    public void setDeductions(List<StockDeductionInfo> deductions) { this.deductions = deductions; }

    public List<String> getDetails() { return details; }
    public void setDetails(List<String> details) { this.details = details; this.deductionLogs = details; }

    public List<String> getDeductionLogs() { return deductionLogs; }
    public void setDeductionLogs(List<String> deductionLogs) { this.deductionLogs = deductionLogs; this.details = deductionLogs; }

    public String getVoiceBroadcastText() { return voiceBroadcastText; }
    public void setVoiceBroadcastText(String voiceBroadcastText) { this.voiceBroadcastText = voiceBroadcastText; }

    @Data
    public static class StockDeductionInfo {
        private String medicineName;
        private Integer quantityDeducted;
        private Integer deductQuantity;
        private Integer remainingStock;

        public StockDeductionInfo() {}

        public StockDeductionInfo(String medicineName, Integer quantityDeducted, Integer remainingStock) {
            this.medicineName = medicineName;
            this.quantityDeducted = quantityDeducted;
            this.deductQuantity = quantityDeducted;
            this.remainingStock = remainingStock;
        }

        public String getMedicineName() { return medicineName; }
        public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

        public Integer getQuantityDeducted() { return quantityDeducted; }
        public void setQuantityDeducted(Integer quantityDeducted) { 
            this.quantityDeducted = quantityDeducted; 
            this.deductQuantity = quantityDeducted;
        }

        public Integer getDeductQuantity() { return deductQuantity; }
        public void setDeductQuantity(Integer deductQuantity) { 
            this.deductQuantity = deductQuantity; 
            this.quantityDeducted = deductQuantity;
        }

        public Integer getRemainingStock() { return remainingStock; }
        public void setRemainingStock(Integer remainingStock) { this.remainingStock = remainingStock; }
    }
}
