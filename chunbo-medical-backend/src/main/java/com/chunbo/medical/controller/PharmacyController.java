package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chunbo.medical.dto.DispenseResultDto;
import com.chunbo.medical.entity.*;
import com.chunbo.medical.mapper.*;
import com.chunbo.medical.service.CurrentUserService;
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

    @Autowired
    private CurrentUserService currentUserService;

    /**
     * 1. 获取处方清单 (待发药 / 已发药 / 待收费)，支持按开方日期筛选（如 2026-09-20 只看当天）
     */
    @GetMapping("/prescriptions")
    public List<Map<String, Object>> getPrescriptions(@RequestParam(value = "status", required = false) String status,
                                                      @RequestParam(value = "date", required = false) String date) {
        LambdaQueryWrapper<Prescription> qw = new LambdaQueryWrapper<>();
        if (status != null && !status.isEmpty() && !status.equalsIgnoreCase("all")) {
            qw.eq(Prescription::getStatus, status);
        }
        if (date != null && !date.isEmpty()) {
            // 按开方日期筛选：选中哪天就只显示那一天开具的处方
            java.time.LocalDate d = java.time.LocalDate.parse(date);
            qw.ge(Prescription::getCreateTime, d.atStartOfDay())
              .le(Prescription::getCreateTime, d.atTime(java.time.LocalTime.MAX));
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
    public DispenseResultDto dispense(@PathVariable("prescriptionId") Long prescriptionId, jakarta.servlet.http.HttpServletRequest request) {
        DispenseResultDto res = new DispenseResultDto();
        // 发药药师 = 当前登录人真实姓名
        String dispenser = currentUserService.displayName(request);
        if (dispenser == null || dispenser.isBlank()) dispenser = "系统用户";
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
                ir.setOperator(dispenser + " (调剂药师)");
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
        // stock/price 为 NOT NULL 无默认值字段，新增建档时前端未填则兜底，避免 INSERT 报错
        if (med.getStock() == null) med.setStock(0);
        if (med.getPrice() == null) med.setPrice(java.math.BigDecimal.ZERO);
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
     * 5b. 删除药品/物资档案（物资管理与商品档案的删除入口）
     */
    @PostMapping("/medicines/delete/{id}")
    public Map<String, Object> deleteMedicine(@PathVariable("id") Long id) {
        Map<String, Object> res = new HashMap<>();
        Medicine m = medicineMapper.selectById(id);
        if (m == null) {
            res.put("success", false);
            res.put("message", "未找到该药品档案！");
            return res;
        }
        medicineMapper.deleteById(id);
        res.put("success", true);
        res.put("message", "档案【" + m.getName() + "】已删除！");
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

    // ==================== 药品-物资开方联动配置 ====================

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    /** 联动配置列表（可选按物资 supplyId 过滤；或按触发药品 medicineId / medicineName 匹配，供开方实时联动查询） */
    @GetMapping("/supply-links")
    public List<Map<String, Object>> listSupplyLinks(
            @RequestParam(value = "supplyId", required = false) Long supplyId,
            @RequestParam(value = "medicineId", required = false) Long medicineId,
            @RequestParam(value = "medicineName", required = false) String medicineName) {
        String sql = "SELECT l.id, l.medicine_id, l.medicine_name, l.supply_id, l.supply_name, l.quantity, " +
                "m.price AS supply_price, m.specification AS supply_specification " +
                "FROM medicine_supply_link l LEFT JOIN medicine m ON m.id = l.supply_id";
        List<Object> args = new ArrayList<>();
        List<String> conds = new ArrayList<>();
        if (supplyId != null) {
            conds.add("l.supply_id = ?");
            args.add(supplyId);
        }
        if (medicineId != null) {
            conds.add("l.medicine_id = ?");
            args.add(medicineId);
        }
        if (medicineName != null && !medicineName.isEmpty()) {
            // 名称模糊匹配兜底（手打药名带前缀也能命中）
            conds.add("(? LIKE CONCAT('%', l.medicine_name, '%') OR l.medicine_name LIKE CONCAT('%', ?, '%'))");
            args.add(medicineName);
            args.add(medicineName);
        }
        if (!conds.isEmpty()) {
            sql += " WHERE " + String.join(" AND ", conds);
        }
        sql += " ORDER BY l.id DESC";
        return jdbcTemplate.queryForList(sql, args.toArray());
    }

    /** 保存联动配置（新增/更新）：哪些药品开方时自动附加本物资、附加数量 */
    @PostMapping("/supply-links/save")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveSupplyLink(@RequestBody Map<String, Object> payload) {
        Map<String, Object> res = new HashMap<>();
        Long medicineId = Long.valueOf(payload.get("medicineId").toString());
        Long supplyId = Long.valueOf(payload.get("supplyId").toString());
        int quantity = 1;
        try { quantity = Integer.parseInt(payload.getOrDefault("quantity", 1).toString()); } catch (Exception ignored) {}
        if (quantity < 1) quantity = 1;
        if (medicineId.equals(supplyId)) {
            res.put("success", false);
            res.put("message", "药品与联动物资不能是同一条档案！");
            return res;
        }
        Medicine med = medicineMapper.selectById(medicineId);
        Medicine supply = medicineMapper.selectById(supplyId);
        if (med == null || supply == null) {
            res.put("success", false);
            res.put("message", "药品或物资档案不存在！");
            return res;
        }

        Object idObj = payload.get("id");
        if (idObj != null && !idObj.toString().isEmpty()) {
            jdbcTemplate.update("UPDATE medicine_supply_link SET medicine_id=?, medicine_name=?, supply_id=?, supply_name=?, quantity=? WHERE id=?",
                    medicineId, med.getName(), supplyId, supply.getName(), quantity, Long.valueOf(idObj.toString()));
            res.put("message", "联动配置已更新！");
        } else {
            // 同一药品+物资组合去重
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM medicine_supply_link WHERE medicine_id=? AND supply_id=?",
                    Integer.class, medicineId, supplyId);
            if (exists != null && exists > 0) {
                jdbcTemplate.update("UPDATE medicine_supply_link SET quantity=?, medicine_name=?, supply_name=? WHERE medicine_id=? AND supply_id=?",
                        quantity, med.getName(), supply.getName(), medicineId, supplyId);
                res.put("message", "该药品已存在联动配置，数量已更新为 " + quantity + "！");
            } else {
                jdbcTemplate.update("INSERT INTO medicine_supply_link (medicine_id, medicine_name, supply_id, supply_name, quantity) VALUES (?,?,?,?,?)",
                        medicineId, med.getName(), supplyId, supply.getName(), quantity);
                res.put("message", "联动配置已保存！开方【" + med.getName() + "】将自动附加【" + supply.getName() + " × " + quantity + "】");
            }
        }
        res.put("success", true);
        return res;
    }

    /** 删除联动配置 */
    @PostMapping("/supply-links/delete/{id}")
    public Map<String, Object> deleteSupplyLink(@PathVariable("id") Long id) {
        Map<String, Object> res = new HashMap<>();
        jdbcTemplate.update("DELETE FROM medicine_supply_link WHERE id=?", id);
        res.put("success", true);
        res.put("message", "联动配置已删除！");
        return res;
    }

    // ==================== 基础数据字典（表单下拉可选项管理） ====================

    /** 字典列表（按类型查询，如 规格单位/二级剂型/批准文号/生产厂家） */
    @GetMapping("/dict")
    public List<Map<String, Object>> dictList(@RequestParam("type") String type) {
        return jdbcTemplate.queryForList(
                "SELECT id, dict_type, dict_value FROM biz_dict WHERE dict_type = ? ORDER BY id", type);
    }

    /** 新增/修改字典项（同类型同值去重更新） */
    @PostMapping("/dict/save")
    public Map<String, Object> dictSave(@RequestBody Map<String, Object> payload) {
        Map<String, Object> res = new HashMap<>();
        String type = payload.getOrDefault("dictType", "").toString().trim();
        String value = payload.getOrDefault("dictValue", "").toString().trim();
        if (type.isEmpty() || value.isEmpty()) {
            res.put("success", false);
            res.put("message", "字典类型与字典值均不能为空！");
            return res;
        }
        Object idObj = payload.get("id");
        if (idObj != null && !idObj.toString().isEmpty()) {
            jdbcTemplate.update("UPDATE biz_dict SET dict_type=?, dict_value=? WHERE id=?",
                    type, value, Long.valueOf(idObj.toString()));
            res.put("message", "字典项已更新！");
        } else {
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM biz_dict WHERE dict_type=? AND dict_value=?", Integer.class, type, value);
            if (exists != null && exists > 0) {
                res.put("success", false);
                res.put("message", "该选项已存在，无需重复添加！");
                return res;
            }
            jdbcTemplate.update("INSERT INTO biz_dict (dict_type, dict_value) VALUES (?,?)", type, value);
            res.put("message", "选项已添加！");
        }
        res.put("success", true);
        return res;
    }

    /** 删除字典项 */
    @PostMapping("/dict/delete/{id}")
    public Map<String, Object> dictDelete(@PathVariable("id") Long id) {
        Map<String, Object> res = new HashMap<>();
        jdbcTemplate.update("DELETE FROM biz_dict WHERE id=?", id);
        res.put("success", true);
        res.put("message", "选项已删除！");
        return res;
    }
}
