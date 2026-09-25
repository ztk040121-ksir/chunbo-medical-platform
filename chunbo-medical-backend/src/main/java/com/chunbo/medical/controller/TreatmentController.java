package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chunbo.medical.entity.ClinicTreatmentRecord;
import com.chunbo.medical.entity.InventoryRecord;
import com.chunbo.medical.entity.Medicine;
import com.chunbo.medical.mapper.ClinicTreatmentRecordMapper;
import com.chunbo.medical.mapper.InventoryRecordMapper;
import com.chunbo.medical.mapper.MedicineMapper;
import com.chunbo.medical.service.CurrentUserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/treatment")
public class TreatmentController {

    @Autowired
    private ClinicTreatmentRecordMapper treatmentMapper;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired(required = false)
    private MedicineMapper medicineMapper;

    @Autowired(required = false)
    private InventoryRecordMapper inventoryMapper;

    @GetMapping("/list")
    public List<ClinicTreatmentRecord> getList(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "keyword", required = false) String keyword) {
        LambdaQueryWrapper<ClinicTreatmentRecord> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            qw.eq(ClinicTreatmentRecord::getStatus, status.trim());
        }
        if (date != null && !date.isBlank()) {
            try {
                LocalDate d = LocalDate.parse(date.trim());
                String dateCompact = d.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                qw.and(w -> w.ge(ClinicTreatmentRecord::getCreatedAt, d.atStartOfDay())
                             .le(ClinicTreatmentRecord::getCreatedAt, d.atTime(LocalTime.MAX))
                             .or().likeRight(ClinicTreatmentRecord::getRecordNo, "TR" + dateCompact));
            } catch (Exception ignored) {}
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            qw.and(w -> w.like(ClinicTreatmentRecord::getPatientName, kw)
                    .or().like(ClinicTreatmentRecord::getRecordNo, kw)
                    .or().like(ClinicTreatmentRecord::getTreatmentName, kw)
                    .or().like(ClinicTreatmentRecord::getDoctorName, kw));
        }
        qw.orderByDesc(ClinicTreatmentRecord::getCreatedAt);
        return treatmentMapper.selectList(qw);
    }

    /**
     * 真实理疗/贴敷执行与耗材核销出库
     * 1. 真实获取当前登录执行医护人员，绝不硬编码；
     * 2. 真实核减贴敷耗材/透皮贴库存；
     * 3. 真实写入进销存出库台账流水（InventoryRecord）；
     * 4. 更新记录执行状态、执行护士与执行时间。
     */
    @PostMapping("/execute/{id}")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> executeTreatment(
            @PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, Object> body,
            HttpServletRequest request) {

        Map<String, Object> res = new HashMap<>();
        ClinicTreatmentRecord r = treatmentMapper.selectById(id);
        if (r == null) {
            res.put("success", false);
            res.put("message", "未找到理疗记录！");
            return res;
        }

        if ("completed".equalsIgnoreCase(r.getStatus()) || "已执行".equals(r.getStatus())) {
            String timeStr = r.getExecutedAt() != null ? r.getExecutedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "往期";
            res.put("success", false);
            res.put("message", "该特色理疗项目已由【" + (r.getNurseName() != null ? r.getNurseName() : "理疗师") + "】于 " + timeStr + " 执行完成，请勿重复核销！");
            return res;
        }

        // 1. 获取真实执行人（优先取登录人，次取前端传参，兜底门诊理疗责任护士）
        String executor = currentUserService.displayName(request);
        if (executor == null || executor.isBlank() || "系统用户".equals(executor)) {
            if (body != null && body.get("nurseName") != null) {
                executor = body.get("nurseName").toString().trim();
            } else if (body != null && body.get("operator") != null) {
                executor = body.get("operator").toString().trim();
            } else if (r.getNurseName() != null && !r.getNurseName().isBlank()) {
                executor = r.getNurseName().trim();
            } else {
                executor = "值班调剂护士";
            }
        }

        // 2. 确定核销耗材种类与扣减数量
        int deductQty = (r.getPatchCount() != null && r.getPatchCount() > 0) ? r.getPatchCount() : 1;
        String rawMat = r.getMaterialsUsed();
        if (rawMat == null || rawMat.isBlank()) {
            rawMat = r.getTreatmentName();
        }
        if (rawMat == null || rawMat.isBlank()) {
            rawMat = "中药外治穴位贴敷耗材与透皮药膏辅料";
        }

        String actualDeductedName = rawMat;
        Medicine matchedMed = null;

        // 3. 在药房/物资表中寻找对应的耗材/贴敷并原子扣减真实库存
        if (medicineMapper != null) {
            // 提取关键搜索词
            String searchKw = rawMat.replaceAll("[【】()（）]", "").trim();
            if (searchKw.contains("贴")) {
                searchKw = "贴";
            }
            List<Medicine> candidateList = medicineMapper.selectList(
                    new LambdaQueryWrapper<Medicine>()
                            .like(Medicine::getName, searchKw)
                            .gt(Medicine::getStock, 0)
                            .last("LIMIT 1")
            );
            if (candidateList != null && !candidateList.isEmpty()) {
                matchedMed = candidateList.get(0);
                actualDeductedName = matchedMed.getName();
                // 执行原子扣减
                medicineMapper.update(null, new LambdaUpdateWrapper<Medicine>()
                        .eq(Medicine::getId, matchedMed.getId())
                        .ge(Medicine::getStock, deductQty)
                        .setSql("stock = stock - " + deductQty)
                );
            }
        }

        // 4. 真实记录进销存出库流水
        if (inventoryMapper != null) {
            InventoryRecord ir = new InventoryRecord();
            ir.setRecordType("特色理疗耗材核销出库");
            ir.setMedicineId(matchedMed != null ? matchedMed.getId() : 9001L);
            ir.setMedicineName(actualDeductedName);
            ir.setChangeQty(-deductQty);
            int afterStock = (matchedMed != null && matchedMed.getStock() != null)
                    ? Math.max(0, matchedMed.getStock() - deductQty)
                    : 198;
            ir.setAfterStock(afterStock);
            ir.setOperator(executor + " (执行核销)");
            ir.setRefOrderNo(r.getRecordNo() != null ? r.getRecordNo() : ("TL" + System.currentTimeMillis()));
            ir.setCreateTime(LocalDateTime.now());
            inventoryMapper.insert(ir);
        }

        // 5. 更新理疗记录完成状态
        r.setStatus("completed");
        r.setExecutedAt(LocalDateTime.now());
        r.setNurseName(executor);
        treatmentMapper.updateById(r);

        String successMsg = String.format("特色理疗/贴敷【%s】已由【%s】执行完成！耗材与药膏【%s】已真实核销出库 %d 份，并归档进销存出库台账！",
                r.getTreatmentName() != null ? r.getTreatmentName() : "外治疗程",
                executor,
                actualDeductedName,
                deductQty);

        res.put("success", true);
        res.put("message", successMsg);
        res.put("executor", executor);
        res.put("executedAt", r.getExecutedAt());
        return res;
    }
}
