package com.chunbo.medical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.DoctorAccount;
import com.chunbo.medical.entity.OaApproval;
import com.chunbo.medical.entity.OaPlasterRecord;
import com.chunbo.medical.entity.OaSalarySlip;
import com.chunbo.medical.entity.StaffAccount;
import com.chunbo.medical.entity.SysTokenLog;
import com.chunbo.medical.mapper.DoctorAccountMapper;
import com.chunbo.medical.mapper.OaApprovalMapper;
import com.chunbo.medical.mapper.OaPlasterRecordMapper;
import com.chunbo.medical.mapper.OaSalarySlipMapper;
import com.chunbo.medical.mapper.StaffAccountMapper;
import com.chunbo.medical.mapper.SysTokenLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OaAssistantService {

    @Autowired
    private OaSalarySlipMapper salarySlipMapper;

    @Autowired
    private OaPlasterRecordMapper plasterRecordMapper;

    @Autowired
    private OaApprovalMapper approvalMapper;

    @Autowired
    private SysTokenLogMapper tokenLogMapper;

    @Autowired(required = false)
    private StaffAccountMapper staffAccountMapper;

    @Autowired(required = false)
    private DoctorAccountMapper doctorAccountMapper;

    @Autowired(required = false)
    private com.chunbo.medical.mapper.MallOrderMapper mallOrderMapper;

    @Autowired(required = false)
    private com.chunbo.medical.mapper.MallProductMapper mallProductMapper;

    @Autowired(required = false)
    private com.chunbo.medical.mapper.InventoryRecordMapper inventoryRecordMapper;

    public List<OaSalarySlip> getSalarySlips(String doctorId) {
        LambdaQueryWrapper<OaSalarySlip> qw = new LambdaQueryWrapper<>();
        if (doctorId != null && !doctorId.isEmpty()) {
            qw.eq(OaSalarySlip::getDoctorId, doctorId);
        }
        qw.orderByDesc(OaSalarySlip::getSalaryMonth);
        return salarySlipMapper.selectList(qw);
    }

    /** 按工号或姓名查询工资条（工号优先，未命中再按姓名匹配），避免"传姓名却硬回退 DOC_1001 查错人" */
    public List<OaSalarySlip> getSalarySlipsByIdOrName(String key) {
        LambdaQueryWrapper<OaSalarySlip> qw = new LambdaQueryWrapper<>();
        if (key != null && !key.isEmpty()) {
            qw.and(w -> w.eq(OaSalarySlip::getDoctorId, key).or().eq(OaSalarySlip::getDoctorName, key));
        }
        qw.orderByDesc(OaSalarySlip::getSalaryMonth);
        return salarySlipMapper.selectList(qw);
    }

    /**
     * 发工资：按姓名或工号匹配员工档案（staff_account / doctor_account），
     * 创建一条"已发放"工资条（金额来自表格提取结果）。匹配不到返回 success=false。
     */
    public Map<String, Object> paySalary(String employee, BigDecimal amount, String month) {
        Map<String, Object> result = new HashMap<>();
        String doctorId = null;
        String doctorName = null;
        if (employee != null && !employee.isBlank()) {
            try {
                if (staffAccountMapper != null) {
                    StaffAccount sa = staffAccountMapper.selectOne(new LambdaQueryWrapper<StaffAccount>()
                            .and(w -> w.eq(StaffAccount::getStaffId, employee).or().eq(StaffAccount::getRealName, employee))
                            .last("LIMIT 1"));
                    if (sa != null) {
                        doctorId = sa.getStaffId();
                        doctorName = sa.getRealName();
                    }
                }
            } catch (Exception ignored) {
            }
            if (doctorId == null) {
                try {
                    if (doctorAccountMapper != null) {
                        DoctorAccount da = doctorAccountMapper.selectOne(new LambdaQueryWrapper<DoctorAccount>()
                                .and(w -> w.eq(DoctorAccount::getDoctorId, employee).or().eq(DoctorAccount::getDoctorName, employee))
                                .last("LIMIT 1"));
                        if (da != null) {
                            doctorId = da.getDoctorId();
                            doctorName = da.getDoctorName();
                        }
                    }
                } catch (Exception ignored) {
                }
            }
        }
        if (doctorId == null) {
            result.put("success", false);
            result.put("message", "未在员工档案中匹配到【" + (employee != null ? employee : "") + "】，请核对姓名或工号后重试");
            return result;
        }
        // 发薪月份口径：缺省取当前完整日期；传了 7 位"YYYY-MM"（文件名识别/月份口径）时补为"YYYY-MM-01"
        String monthStr;
        if (month != null && !month.isBlank()) {
            String t = month.trim();
            if (t.length() == 7 && t.charAt(4) == '-') {
                monthStr = t + "-01";
            } else {
                monthStr = t;
            }
        } else {
            monthStr = LocalDateTime.now().toLocalDate().toString();
        }
        BigDecimal net = amount != null ? amount : BigDecimal.ZERO;
        // 幂等防重发：同一员工同一发放日已有工资条时覆盖更新，避免台账出现重复发放记录
        OaSalarySlip existed = salarySlipMapper.selectOne(new LambdaQueryWrapper<OaSalarySlip>()
                .eq(OaSalarySlip::getDoctorId, doctorId)
                .eq(OaSalarySlip::getSalaryMonth, monthStr)
                .last("LIMIT 1"));
        boolean overwrite = existed != null;
        OaSalarySlip slip = overwrite ? existed : new OaSalarySlip();
        slip.setDoctorId(doctorId);
        slip.setDoctorName(doctorName != null ? doctorName : employee);
        slip.setSalaryMonth(monthStr);
        slip.setBaseSalary(net);
        slip.setClinicCommission(BigDecimal.ZERO);
        slip.setPlasterCommission(BigDecimal.ZERO);
        slip.setDeductionSocial(BigDecimal.ZERO);
        slip.setTax(BigDecimal.ZERO);
        slip.setNetSalary(net);
        slip.setStatus("已发放");
        if (overwrite) {
            // 覆盖更新时刷新发放时间，台账/记录里看到的应是最近一次发放时间
            slip.setCreateTime(LocalDateTime.now());
            salarySlipMapper.updateById(slip);
        } else {
            salarySlipMapper.insert(slip);
        }

        result.put("success", true);
        result.put("doctorId", doctorId);
        result.put("doctorName", slip.getDoctorName());
        result.put("month", monthStr);
        result.put("amount", net);
        result.put("message", "已为员工【" + slip.getDoctorName() + "】发放 " + monthStr + " 工资 ¥" + net
                + (overwrite ? "（当日已有工资条，已覆盖更新原记录）" : "（工资条已入账）"));
        return result;
    }

    /**
     * 确定性批量发放工资（工资表附件/工资发放中心共用，不依赖 LLM）
     * 逐行调用 paySalary（含系统账号校验 + 同发放日幂等覆盖），返回汇总。
     * rows: [{employeeId, employeeName, department, amount}]，month 可空（缺省取当前完整日期）
     */
    public Map<String, Object> paySalaryFromRows(List<Map<String, Object>> rows, String month) {
        Map<String, Object> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            result.put("success", false);
            result.put("message", "工资表为空，未识别到员工记录");
            return result;
        }
        List<Map<String, Object>> details = new ArrayList<>();
        int ok = 0, fail = 0;
        BigDecimal total = BigDecimal.ZERO;
        for (Map<String, Object> row : rows) {
            String idVal = String.valueOf(row.getOrDefault("employeeId", ""));
            String nameVal = String.valueOf(row.getOrDefault("employeeName", ""));
            String amtStr = String.valueOf(row.getOrDefault("amount", ""));
            String emp = !idVal.isBlank() ? idVal : nameVal;
            Map<String, Object> d = new HashMap<>();
            d.put("employee", !nameVal.isBlank() ? nameVal : emp);
            d.put("department", row.getOrDefault("department", ""));
            d.put("amount", amtStr);
            BigDecimal amt = null;
            try { amt = new BigDecimal(amtStr.trim()); } catch (Exception ignore) { }
            if (emp.isBlank() || amt == null) {
                d.put("success", false);
                d.put("message", "行数据不完整（姓名/工号或金额缺失）");
                fail++;
                details.add(d);
                continue;
            }
            Map<String, Object> pr = paySalary(emp, amt, month);
            boolean s = Boolean.TRUE.equals(pr.get("success"));
            d.put("success", s);
            d.put("message", pr.get("message"));
            if (s) total = total.add(amt);
            if (s) ok++; else fail++;
            details.add(d);
        }
        result.put("success", ok > 0);
        result.put("successCount", ok);
        result.put("failCount", fail);
        result.put("totalAmount", total);
        result.put("details", details);
        result.put("message", "工资表发放完成：成功 " + ok + " 人，失败 " + fail + " 人，合计 ¥" + total);
        return result;
    }

    /**
     * 商城订单发货出库（确定性，AI 工具与页面接口共用）：
     * 扣减商品真实库存 + 写入进销存流水 + 订单状态改为「已发货运输中」+ 生成便民速递单号
     */
    public Map<String, Object> shipOrder(String orderNo, String trackingNo, String operator) {
        Map<String, Object> res = new HashMap<>();
        if (mallOrderMapper == null) {
            res.put("success", false);
            res.put("message", "商城订单服务不可用");
            return res;
        }
        com.chunbo.medical.entity.MallOrder order = mallOrderMapper.selectOne(
                new LambdaQueryWrapper<com.chunbo.medical.entity.MallOrder>()
                        .eq(com.chunbo.medical.entity.MallOrder::getOrderNo, orderNo));
        if (order == null) {
            res.put("success", false);
            res.put("message", "未找到该商城订单: " + orderNo);
            return res;
        }
        if (com.chunbo.medical.enums.OrderStatusEnum.isShipped(order.getStatus())) {
            res.put("success", false);
            res.put("message", "该订单已完成发货出库，请勿重复发货！当前状态: " + order.getStatus());
            return res;
        }
        List<String> logs = new ArrayList<>();
        String itemsJson = order.getItemsJson();
        if (itemsJson != null && !itemsJson.isEmpty()) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode rootNode = mapper.readTree(itemsJson);
                if (rootNode.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode item : rootNode) {
                        Long prodId = item.has("id") ? item.get("id").asLong() : null;
                        int qty = item.has("quantity") ? item.get("quantity").asInt() : 1;
                        if (prodId == null) continue;
                        com.chunbo.medical.entity.MallProduct prod = mallProductMapper.selectById(prodId);
                        if (prod == null) continue;
                        int currentStock = prod.getStock() != null ? prod.getStock() : 0;
                        int newStock = Math.max(0, currentStock - qty);
                        prod.setStock(newStock);
                        mallProductMapper.updateById(prod);
                        com.chunbo.medical.entity.InventoryRecord ir = new com.chunbo.medical.entity.InventoryRecord();
                        ir.setMedicineId(prod.getId());
                        ir.setMedicineName(prod.getProductName());
                        ir.setRecordType("商城订单发货出库");
                        ir.setChangeQty(-qty);
                        ir.setAfterStock(newStock);
                        ir.setRefOrderNo(order.getOrderNo());
                        ir.setOperator(operator);
                        ir.setRemark("春播健康便民速递揽收 (单号: " + trackingNo + ", 送至: " + order.getClinicName() + ")");
                        ir.setCreateTime(LocalDateTime.now());
                        inventoryRecordMapper.insert(ir);
                        logs.add("商品【" + prod.getProductName() + "】出库扣减 " + qty + " 件，结余库存: " + newStock);
                    }
                }
            } catch (Exception e) {
                com.chunbo.medical.entity.InventoryRecord ir = new com.chunbo.medical.entity.InventoryRecord();
                ir.setMedicineName("春播商城综合购药订单");
                ir.setRecordType("商城订单发货出库");
                ir.setChangeQty(-1);
                ir.setAfterStock(0);
                ir.setRefOrderNo(order.getOrderNo());
                ir.setOperator(operator);
                ir.setRemark("春播健康便民速递: " + trackingNo);
                ir.setCreateTime(LocalDateTime.now());
                inventoryRecordMapper.insert(ir);
            }
        }
        order.setStatus(com.chunbo.medical.enums.OrderStatusEnum.SHIPPED.getCode());
        String existingNotes = order.getBargainNotes() != null ? order.getBargainNotes() : "";
        order.setBargainNotes(existingNotes + " 【春播健康便民速递单号: " + trackingNo + "，发货人: " + operator + "】");
        mallOrderMapper.updateById(order);
        res.put("success", true);
        res.put("message", "订单履约发货出库成功！春播健康便民速递运单号【" + trackingNo + "】，库存已实时扣减并生成进销存台账！");
        res.put("trackingNo", trackingNo);
        res.put("logs", logs);
        return res;
    }

    /** 商城订单确认送达（确定性，AI 工具与页面接口共用）：状态改为「已送达 / 居民已签收」 */
    public Map<String, Object> confirmOrderDelivered(String orderNo) {
        Map<String, Object> res = new HashMap<>();
        if (mallOrderMapper == null) {
            res.put("success", false);
            res.put("message", "商城订单服务不可用");
            return res;
        }
        com.chunbo.medical.entity.MallOrder order = mallOrderMapper.selectOne(
                new LambdaQueryWrapper<com.chunbo.medical.entity.MallOrder>()
                        .eq(com.chunbo.medical.entity.MallOrder::getOrderNo, orderNo));
        if (order == null) {
            res.put("success", false);
            res.put("message", "未找到该商城订单: " + orderNo);
            return res;
        }
        if (com.chunbo.medical.enums.OrderStatusEnum.isDelivered(order.getStatus())) {
            res.put("success", false);
            res.put("message", "该订单已处于「已送达」状态，无需重复确认");
            return res;
        }
        order.setStatus(com.chunbo.medical.enums.OrderStatusEnum.DELIVERED.getCode());
        String existingNotes = order.getBargainNotes() != null ? order.getBargainNotes() : "";
        order.setBargainNotes(existingNotes + " 【春播便民速递妥投完成，居民已顺利签收】");
        mallOrderMapper.updateById(order);
        res.put("success", true);
        res.put("message", "订单【" + orderNo + "】已确认送达并由居民成功签收！");
        return res;
    }

    // ==========================================
    // 商品与进销存（AI 工具与页面接口共用）
    // ==========================================

    /** 按商品名模糊查商品列表（时间倒序） */
    public List<com.chunbo.medical.entity.MallProduct> findProductsByName(String productName) {
        return mallProductMapper.selectList(
                new LambdaQueryWrapper<com.chunbo.medical.entity.MallProduct>()
                        .like(com.chunbo.medical.entity.MallProduct::getProductName, productName)
                        .orderByDesc(com.chunbo.medical.entity.MallProduct::getId));
    }

    /** 单商品操作歧义时的候选清单文本（让 AI 向用户确认是哪一个） */
    public String productCandidatesText(List<com.chunbo.medical.entity.MallProduct> products) {
        StringBuilder sb = new StringBuilder("⚠️ 匹配到 ").append(products.size())
                .append(" 个同名/相似商品，请先向用户确认操作哪一个，不要擅自选择：\n");
        int i = 1;
        for (com.chunbo.medical.entity.MallProduct p : products) {
            sb.append(i++).append(") ID ").append(p.getId())
              .append("，").append(p.getProductName())
              .append("（").append(p.getSpecification() == null ? "" : p.getSpecification())
              .append("，零售价 ¥").append(p.getRetailGuidePrice())
              .append("，库存 ").append(p.getStock()).append("）\n");
        }
        sb.append("【处理规则】用户确认某个商品后，携带该商品 ID 对应的完整商品名重新调用工具。");
        return sb.toString();
    }

    /** 商品上架/下架（action: 上架/下架），成功返回 {success, message} */
    public Map<String, Object> setProductStatusByName(String productName, String action) {
        Map<String, Object> res = new HashMap<>();
        List<com.chunbo.medical.entity.MallProduct> list = findProductsByName(productName);
        if (list.isEmpty()) {
            res.put("success", false);
            res.put("message", "未找到商品【" + productName + "】");
            return res;
        }
        if (list.size() > 1) {
            res.put("success", false);
            res.put("needConfirm", true);
            res.put("message", productCandidatesText(list));
            return res;
        }
        com.chunbo.medical.entity.MallProduct p = list.get(0);
        boolean onSale = "上架".equals(action);
        p.setStatus(onSale ? "ON_SALE" : "OFF_SALE");
        mallProductMapper.updateById(p);
        res.put("success", true);
        res.put("message", "商品【" + p.getProductName() + "】已" + action + (onSale ? "，恢复商城在售" : "，商城已不可见"));
        return res;
    }

    /** 商品调价（零售价必填，批发价可选） */
    public Map<String, Object> updateProductPriceByName(String productName, BigDecimal retail, BigDecimal wholesale) {
        Map<String, Object> res = new HashMap<>();
        List<com.chunbo.medical.entity.MallProduct> list = findProductsByName(productName);
        if (list.isEmpty()) {
            res.put("success", false);
            res.put("message", "未找到商品【" + productName + "】");
            return res;
        }
        if (list.size() > 1) {
            res.put("success", false);
            res.put("needConfirm", true);
            res.put("message", productCandidatesText(list));
            return res;
        }
        com.chunbo.medical.entity.MallProduct p = list.get(0);
        BigDecimal oldRetail = p.getRetailGuidePrice();
        p.setRetailGuidePrice(retail);
        if (wholesale != null) {
            p.setWholesalePrice(wholesale);
            double ws = wholesale.doubleValue();
            if (ws > 0) {
                p.setProfitRate(BigDecimal.valueOf((retail.doubleValue() - ws) / ws * 100)
                        .setScale(2, java.math.RoundingMode.HALF_UP));
            }
        }
        mallProductMapper.updateById(p);
        res.put("success", true);
        res.put("message", "商品【" + p.getProductName() + "】零售价已由 ¥" + oldRetail + " 调整为 ¥" + retail
                + (wholesale != null ? "，批发价同步调整为 ¥" + wholesale : ""));
        return res;
    }

    /** 商品入库补货（库存累加 + 进销存入库流水） */
    public Map<String, Object> inboundProductByName(String productName, int qty) {
        Map<String, Object> res = new HashMap<>();
        List<com.chunbo.medical.entity.MallProduct> list = findProductsByName(productName);
        if (list.isEmpty()) {
            res.put("success", false);
            res.put("message", "未找到商品【" + productName + "】");
            return res;
        }
        if (list.size() > 1) {
            res.put("success", false);
            res.put("needConfirm", true);
            res.put("message", productCandidatesText(list));
            return res;
        }
        com.chunbo.medical.entity.MallProduct p = list.get(0);
        int oldStock = p.getStock() != null ? p.getStock() : (p.getStockQty() != null ? p.getStockQty() : 0);
        int newStock = oldStock + qty;
        p.setStock(newStock);
        p.setStockQty(newStock);
        mallProductMapper.updateById(p);
        try {
            com.chunbo.medical.entity.InventoryRecord record = new com.chunbo.medical.entity.InventoryRecord();
            record.setMedicineId(p.getId());
            record.setMedicineName(p.getProductName());
            record.setRecordType("入库");
            record.setChangeQty(qty);
            record.setAfterStock(newStock);
            record.setRefOrderNo("INB_" + System.currentTimeMillis());
            record.setOperator("AI 中台调度");
            record.setRemark("AI 中台补货入库");
            record.setCreateTime(LocalDateTime.now());
            inventoryRecordMapper.insert(record);
        } catch (Exception ignored) {
        }
        res.put("success", true);
        res.put("message", "商品【" + p.getProductName() + "】入库补货 " + qty + " 件成功，库存由 " + oldStock + " 变更为 " + newStock);
        return res;
    }

    /** 安全转 BigDecimal（空/非法值返回 0） */
    private BigDecimal toBigDecimal(Object v) {
        if (v == null) return BigDecimal.ZERO;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(s);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    /**
     * 商品表格批量导入（确定性，AI 附件与页面接口共用）：
     * - 新商品：直接新增档案（含图片链接，缺省 ON_SALE）；
     * - 已存在且价格一致：库存累加 + 入库流水；
     * - 已存在但价格不匹配：添加失败并提醒（绝不擅改价格）。
     * rows: [{productName, category, specification, manufacturer, wholesalePrice, retailGuidePrice, quantity, imageUrl}]
     */
    public Map<String, Object> importProductsFromRows(List<Map<String, Object>> rows) {
        Map<String, Object> result = new HashMap<>();
        if (rows == null || rows.isEmpty()) {
            result.put("success", false);
            result.put("message", "商品表为空，未识别到商品记录");
            return result;
        }
        List<Map<String, Object>> details = new ArrayList<>();
        int added = 0, merged = 0, failed = 0;
        for (Map<String, Object> row : rows) {
            String name = String.valueOf(row.getOrDefault("productName", "")).trim();
            Map<String, Object> d = new HashMap<>();
            d.put("productName", name);
            if (name.isEmpty() || "null".equals(name)) continue;
            BigDecimal wholesale = toBigDecimal(row.get("wholesalePrice"));
            BigDecimal retail = toBigDecimal(row.get("retailPrice"));
            int qty;
            try {
                qty = Integer.parseInt(String.valueOf(row.getOrDefault("quantity", "0")).trim());
            } catch (Exception e) {
                qty = 0;
            }
            d.put("retailPrice", retail);
            List<com.chunbo.medical.entity.MallProduct> existed = mallProductMapper.selectList(
                    new LambdaQueryWrapper<com.chunbo.medical.entity.MallProduct>()
                            .eq(com.chunbo.medical.entity.MallProduct::getProductName, name));
            if (existed != null && !existed.isEmpty()) {
                com.chunbo.medical.entity.MallProduct p = existed.get(0);
                boolean priceMatch = p.getRetailGuidePrice() != null
                        && p.getRetailGuidePrice().compareTo(retail) == 0;
                if (!priceMatch) {
                    d.put("success", false);
                    d.put("message", "该商品已存在且价格不匹配（现售 ¥" + p.getRetailGuidePrice()
                            + "，表格 ¥" + retail + "），添加失败，未做任何修改");
                    failed++;
                    details.add(d);
                    continue;
                }
                // 同价：库存累加 + 入库流水
                if (qty > 0) {
                    int oldStock = p.getStock() != null ? p.getStock() : 0;
                    int newStock = oldStock + qty;
                    p.setStock(newStock);
                    p.setStockQty(newStock);
                    mallProductMapper.updateById(p);
                    try {
                        com.chunbo.medical.entity.InventoryRecord record = new com.chunbo.medical.entity.InventoryRecord();
                        record.setMedicineId(p.getId());
                        record.setMedicineName(p.getProductName());
                        record.setRecordType("入库");
                        record.setChangeQty(qty);
                        record.setAfterStock(newStock);
                        record.setRefOrderNo("IMP_" + System.currentTimeMillis());
                        record.setOperator("商品表格导入");
                        record.setRemark("商品表格导入：已有商品库存累加");
                        record.setCreateTime(LocalDateTime.now());
                        inventoryRecordMapper.insert(record);
                    } catch (Exception ignored) {
                    }
                    d.put("success", true);
                    d.put("message", "商品已存在且价格一致，库存累加 " + qty + " 件（" + oldStock + " → " + newStock + "）");
                } else {
                    d.put("success", true);
                    d.put("message", "商品已存在且价格一致，无入库数量，未做修改");
                }
                merged++;
                details.add(d);
                continue;
            }
            // 新商品：新增档案
            try {
                com.chunbo.medical.entity.MallProduct p = new com.chunbo.medical.entity.MallProduct();
                p.setProductName(name);
                p.setGenericName(name);
                p.setCategory(String.valueOf(row.getOrDefault("category", "综合分类")));
                p.setSpecification(String.valueOf(row.getOrDefault("specification", "")));
                p.setManufacturer(String.valueOf(row.getOrDefault("manufacturer", "")));
                p.setWholesalePrice(wholesale);
                p.setRetailGuidePrice(retail);
                double ws = wholesale.doubleValue();
                double rt = retail.doubleValue();
                p.setProfitRate(ws > 0
                        ? BigDecimal.valueOf((rt - ws) / ws * 100).setScale(2, java.math.RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);
                p.setStock(qty > 0 ? qty : 0);
                p.setStockQty(qty > 0 ? qty : 0);
                String img = String.valueOf(row.getOrDefault("imageUrl", ""));
                p.setImageUrl(img != null && !img.isBlank() && !"null".equals(img) ? img.trim() : "");
                p.setStatus("ON_SALE");
                p.setCreateTime(LocalDateTime.now());
                mallProductMapper.insert(p);
                d.put("success", true);
                d.put("message", "新商品建档成功并上架，初始库存 " + (qty > 0 ? qty : 0) + " 件");
                added++;
            } catch (Exception e) {
                d.put("success", false);
                d.put("message", "新增失败：" + e.getMessage());
                failed++;
            }
            details.add(d);
        }
        result.put("success", failed == 0 || added > 0 || merged > 0);
        result.put("addedCount", added);
        result.put("mergedCount", merged);
        result.put("failCount", failed);
        result.put("details", details);
        result.put("message", "商品表导入完成：新增 " + added + " 个，同价库存累加 " + merged + " 个，失败 " + failed + " 个");
        return result;
    }

    public List<OaPlasterRecord> getPlasterRecords() {
        return plasterRecordMapper.selectList(new LambdaQueryWrapper<OaPlasterRecord>().orderByDesc(OaPlasterRecord::getTherapyDate));
    }

    public Map<String, Object> getPlasterSummary() {
        List<OaPlasterRecord> list = plasterRecordMapper.selectList(null);
        int totalCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (OaPlasterRecord r : list) {
            totalCount += r.getPasteCount();
            totalRevenue = totalRevenue.add(r.getTotalAmount());
        }
        Map<String, Object> map = new HashMap<>();
        map.put("totalPasteCount", totalCount);
        map.put("totalRevenue", totalRevenue);
        map.put("recordCount", list.size());
        map.put("records", list);
        return map;
    }

    public List<OaApproval> getApprovals() {
        return approvalMapper.selectList(new LambdaQueryWrapper<OaApproval>().orderByDesc(OaApproval::getCreateTime));
    }

    public OaApproval createApproval(OaApproval approval) {
        if (approval.getStatus() == null) {
            approval.setStatus("待审批");
        }
        approval.setCreateTime(LocalDateTime.now());
        approvalMapper.insert(approval);
        return approval;
    }

    public OaApproval processApproval(Long id, String status, String approver, String comment) {
        OaApproval ap = approvalMapper.selectById(id);
        if (ap != null) {
            ap.setStatus(status);
            ap.setApproverName(approver);
            ap.setComment(comment);
            approvalMapper.updateById(ap);
        }
        return ap;
    }

    /** 删除单条审批单 */
    public int deleteApproval(Long id) {
        return approvalMapper.deleteById(id);
    }

    public void logTokenUsage(String sessionId, String modelName, int promptTokens, int completionTokens, long latencyMs) {
        SysTokenLog log = new SysTokenLog();
        log.setSessionId(sessionId);
        log.setModelName(modelName);
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setTotalTokens(promptTokens + completionTokens);
        log.setLatencyMs(latencyMs);

        // gpt-4o-mini 定价估算: 输入约 0.001元/k token, 输出约 0.004元/k token
        BigDecimal cost = new BigDecimal(promptTokens).multiply(new BigDecimal("0.000001"))
                .add(new BigDecimal(completionTokens).multiply(new BigDecimal("0.000004")));
        log.setCostCny(cost);
        log.setCreateTime(LocalDateTime.now());
        tokenLogMapper.insert(log);
    }

    public Map<String, Object> getTokenStats() {
        List<SysTokenLog> logs = tokenLogMapper.selectList(
                new LambdaQueryWrapper<SysTokenLog>().orderByDesc(SysTokenLog::getCreateTime)
        );
        int totalTokens = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        long totalLatency = 0;
        for (SysTokenLog l : logs) {
            totalTokens += (l.getTotalTokens() != null ? l.getTotalTokens() : 0);
            totalCost = totalCost.add(l.getCostCny() != null ? l.getCostCny() : BigDecimal.ZERO);
            totalLatency += (l.getLatencyMs() != null ? l.getLatencyMs() : 0);
        }
        long avgLatency = logs.isEmpty() ? 0 : totalLatency / logs.size();

        Map<String, Object> res = new HashMap<>();
        res.put("totalCalls", logs.size());
        res.put("totalTokens", totalTokens);
        res.put("totalCostCny", totalCost);
        res.put("avgLatencyMs", avgLatency);
        res.put("recentLogs", logs.stream().limit(10).toList());
        return res;
    }
}