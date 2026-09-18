package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chunbo.medical.dto.DispenseResultDto;
import com.chunbo.medical.entity.*;
import com.chunbo.medical.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/pharmacy")
public class PharmacyController {

    @Autowired
    private PrescriptionMapper prescriptionMapper;

    @Autowired
    private PrescriptionItemMapper itemMapper;

    @Autowired
    private MedicineMapper medicineMapper;

    @Autowired
    private InventoryRecordMapper inventoryMapper;

    @Autowired
    private MallOrderMapper mallOrderMapper;

    @Autowired
    private ClinicSupplierMapper supplierMapper;

    /**
     * 1. 获取处方清单 (待发药 / 已发药 / 待收费)
     */
    @GetMapping("/prescriptions")
    public List<Map<String, Object>> getPrescriptions(@RequestParam(value = "status", required = false) String status) {
        LambdaQueryWrapper<Prescription> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("all")) {
            qw.eq(Prescription::getStatus, status);
        }
        qw.orderByDesc(Prescription::getCreateTime);
        List<Prescription> list = prescriptionMapper.selectList(qw);

        List<Map<String, Object>> result = new ArrayList<>();
        for (Prescription p : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("prescription", p);
            List<PrescriptionItem> items = itemMapper.selectList(
                    new LambdaQueryWrapper<PrescriptionItem>().eq(PrescriptionItem::getPrescriptionId, p.getId())
            );
            map.put("items", items);
            result.add(map);
        }
        return result;
    }

    /**
     * 2. 一键发药出库 + 真实扣减药品库存 + 自动记录进销存流水
     */
    @PostMapping("/dispense/{prescriptionId}")
    @Transactional(rollbackFor = Exception.class)
    public DispenseResultDto dispense(@PathVariable("prescriptionId") Long prescriptionId) {
        DispenseResultDto res = new DispenseResultDto();
        Prescription p = prescriptionMapper.selectById(prescriptionId);
        if (p == null) {
            res.setSuccess(false);
            res.setMessage("未找到处方记录！");
            return res;
        }
        if ("2".equals(p.getStatus()) || "已发药".equals(p.getStatus())) {
            res.setSuccess(false);
            res.setMessage("该处方已完成发药出库，请勿重复发药！");
            return res;
        }

        List<PrescriptionItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<PrescriptionItem>().eq(PrescriptionItem::getPrescriptionId, prescriptionId)
        );

        List<String> logs = new ArrayList<>();
        for (PrescriptionItem item : items) {
            Medicine med = medicineMapper.selectById(item.getMedicineId());
            if (med != null) {
                int deduct = item.getQuantity() != null ? item.getQuantity() : 1;
                // 原子扣减库存：仅当实时库存充足时扣减，杜绝并发超卖；
                // 扣减失败返回 0，立即回滚整个发药事务并提示库存不足。
                int updated = medicineMapper.update(null, new LambdaUpdateWrapper<Medicine>()
                        .eq(Medicine::getId, med.getId())
                        .ge(Medicine::getStock, deduct)
                        .setSql("stock = stock - " + deduct));
                if (updated == 0) {
                    res.setSuccess(false);
                    res.setMessage("药品【" + med.getName() + "】实时库存不足（需 " + deduct + " " + (med.getUnit() != null ? med.getUnit() : "盒") + "），发药失败，请先补货！");
                    // 手动标记事务回滚，撤销本单已扣减的其它药品库存
                    org.springframework.transaction.interceptor.TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    return res;
                }
                int newStock = med.getStock() - deduct;

                // 记录出库流水
                InventoryRecord ir = new InventoryRecord();
                ir.setMedicineId(med.getId());
                ir.setMedicineName(med.getName());
                ir.setRecordType("处方发药出库");
                ir.setChangeQty(-deduct);
                ir.setAfterStock(newStock);
                ir.setOperator("张医生/调剂药师");
                ir.setRefOrderNo(p.getPrescriptionNo());
                ir.setCreateTime(LocalDateTime.now());
                inventoryMapper.insert(ir);

                logs.add(String.format("药品【%s】扣减 %d %s，当前实时库存: %d", med.getName(), deduct, med.getUnit() != null ? med.getUnit() : "盒", newStock));
            }
        }

        p.setStatus("2"); // 2: 已发药出库
        prescriptionMapper.updateById(p);

        res.setSuccess(true);
        res.setMessage("处方调配核对通过，药品出库发药完成！");
        res.setDeductionLogs(logs);
        res.setVoiceBroadcastText(String.format("请 %s 到药房窗口取药", p.getPatientName()));
        return res;
    }

    /**
     * 3. 获取药房药品字典与实时库存
     */
    @GetMapping("/medicines")
    public List<Medicine> getMedicines() {
        return medicineMapper.selectList(new LambdaQueryWrapper<Medicine>().orderByAsc(Medicine::getId));
    }

    /**
     * 4. 保存/编辑药品
     */
    @PostMapping("/medicines/save")
    public Map<String, Object> saveMedicine(@RequestBody Medicine med) {
        Map<String, Object> res = new HashMap<>();
        if (med.getId() == null) {
            medicineMapper.insert(med);
        } else {
            medicineMapper.updateById(med);
        }
        res.put("success", true);
        res.put("message", "药品信息已同步更新！");
        return res;
    }

    /**
     * 5. 切换药品启用/停售状态
     */
    @PostMapping("/medicines/toggle-active/{id}")
    public Map<String, Object> toggleActive(@PathVariable("id") Long id) {
        Map<String, Object> res = new HashMap<>();
        Medicine m = medicineMapper.selectById(id);
        if (m == null) {
            res.put("success", false);
            res.put("message", "未找到该药品！");
            return res;
        }
        Integer current = m.getIsActive();
        m.setIsActive(current != null && current == 1 ? 0 : 1);
        medicineMapper.updateById(m);
        res.put("success", true);
        res.put("isActive", m.getIsActive());
        res.put("message", "药品状态已切换为: " + (m.getIsActive() == 1 ? "正常在售" : "已停用/下架"));
        return res;
    }

    /**
     * 6. 药房运营关键统计
     */
    @GetMapping("/stats")
    public Map<String, Object> getStats() {
        Map<String, Object> s = new HashMap<>();
        s.put("pendingDispense", prescriptionMapper.selectCount(new LambdaQueryWrapper<Prescription>().eq(Prescription::getStatus, "1")));
        s.put("todayDispensed", prescriptionMapper.selectCount(new LambdaQueryWrapper<Prescription>().eq(Prescription::getStatus, "2")));
        s.put("stockWarnings", medicineMapper.selectCount(new LambdaQueryWrapper<Medicine>().apply("stock <= warning_stock")));
        s.put("totalSku", medicineMapper.selectCount(null));
        return s;
    }

    /**
     * 7. 进销存流水记录
     */
    @GetMapping("/inventory-records")
    public List<InventoryRecord> getInventoryRecords() {
        return inventoryMapper.selectList(new LambdaQueryWrapper<InventoryRecord>().orderByDesc(InventoryRecord::getCreateTime).last("LIMIT 50"));
    }

    /**
     * 8. 库存预警药品
     */
    @GetMapping("/warnings")
    public List<Medicine> getWarnings() {
        return medicineMapper.selectList(new LambdaQueryWrapper<Medicine>().apply("stock <= warning_stock"));
    }

    /**
     * 9. 供应商列表
     */
    @GetMapping("/suppliers")
    public List<ClinicSupplier> getSuppliers() {
        return supplierMapper.selectList(null);
    }
}
