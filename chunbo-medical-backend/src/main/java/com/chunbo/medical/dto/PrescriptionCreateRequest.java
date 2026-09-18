package com.chunbo.medical.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PrescriptionCreateRequest {
    private Long patientId;
    private String patientName;
    private String doctorName;
    private String diagnosis;
    private String aiAdvice;
    private List<ItemDto> items;

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public String getPatientName() { return patientName; }
    public void setPatientName(String patientName) { this.patientName = patientName; }

    public String getDoctorName() { return doctorName; }
    public void setDoctorName(String doctorName) { this.doctorName = doctorName; }

    public String getDiagnosis() { return diagnosis; }
    public void setDiagnosis(String diagnosis) { this.diagnosis = diagnosis; }

    public String getAiAdvice() { return aiAdvice; }
    public void setAiAdvice(String aiAdvice) { this.aiAdvice = aiAdvice; }

    public List<ItemDto> getItems() { return items; }
    public void setItems(List<ItemDto> items) { this.items = items; }

    @Data
    public static class ItemDto {
        private Long medicineId;
        private String medicineName;
        private String specification;
        private Integer quantity;
        private BigDecimal price;
        private String dosage;
        private String frequency;
        private String route;

        public Long getMedicineId() { return medicineId; }
        public void setMedicineId(Long medicineId) { this.medicineId = medicineId; }

        public String getMedicineName() { return medicineName; }
        public void setMedicineName(String medicineName) { this.medicineName = medicineName; }

        public String getSpecification() { return specification; }
        public void setSpecification(String specification) { this.specification = specification; }

        public Integer getQuantity() { return quantity; }
        public void setQuantity(Integer quantity) { this.quantity = quantity; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }

        public String getDosage() { return dosage; }
        public void setDosage(String dosage) { this.dosage = dosage; }

        public String getFrequency() { return frequency; }
        public void setFrequency(String frequency) { this.frequency = frequency; }

        public String getRoute() { return route; }
        public void setRoute(String route) { this.route = route; }
    }
}
