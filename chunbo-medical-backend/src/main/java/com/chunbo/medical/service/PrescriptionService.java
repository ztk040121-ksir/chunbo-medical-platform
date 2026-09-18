package com.chunbo.medical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.dto.PrescriptionCreateRequest;
import com.chunbo.medical.entity.Prescription;
import com.chunbo.medical.entity.PrescriptionItem;
import com.chunbo.medical.mapper.PrescriptionItemMapper;
import com.chunbo.medical.mapper.PrescriptionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PrescriptionService {

    @Autowired
    private PrescriptionMapper prescriptionMapper;
    @Autowired
    private PrescriptionItemMapper prescriptionItemMapper;

    public List<Prescription> getByPatientId(Long patientId) {
        return prescriptionMapper.selectList(
                new LambdaQueryWrapper<Prescription>()
                        .eq(Prescription::getPatientId, patientId)
                        .orderByDesc(Prescription::getCreateTime)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public Prescription createPrescription(PrescriptionCreateRequest request) {
        Prescription p = new Prescription();
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        p.setPrescriptionNo("RX" + dateStr + ThreadLocalRandom.current().nextInt(100, 999));
        p.setPatientId(request.getPatientId());
        p.setPatientName(request.getPatientName());
        p.setDoctorName(request.getDoctorName() != null ? request.getDoctorName() : "主诊医师");
        p.setDiagnosis(request.getDiagnosis());
        p.setAiAdvice(request.getAiAdvice());
        p.setStatus("待缴费"); // 医生已签发

        BigDecimal total = BigDecimal.ZERO;
        if (request.getItems() != null) {
            for (PrescriptionCreateRequest.ItemDto item : request.getItems()) {
                if (item.getPrice() != null && item.getQuantity() != null) {
                    total = total.add(item.getPrice().multiply(new BigDecimal(item.getQuantity())));
                }
            }
        }
        p.setTotalAmount(total);
        prescriptionMapper.insert(p);

        if (request.getItems() != null) {
            for (PrescriptionCreateRequest.ItemDto item : request.getItems()) {
                PrescriptionItem pi = new PrescriptionItem();
                pi.setPrescriptionId(p.getId());
                pi.setMedicineId(item.getMedicineId());
                pi.setMedicineName(item.getMedicineName());
                pi.setSpecification(item.getSpecification());
                pi.setQuantity(item.getQuantity());
                pi.setPrice(item.getPrice());
                pi.setDosage(item.getDosage());
                pi.setFrequency(item.getFrequency());
                pi.setRoute(item.getRoute() != null ? item.getRoute() : "口服");
                prescriptionItemMapper.insert(pi);
            }
        }
        return p;
    }
}
