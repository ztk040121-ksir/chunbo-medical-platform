package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.*;
import com.chunbo.medical.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/inbound")
public class InboundController {

    @Autowired
    private ClinicInboundOrderMapper inboundOrderMapper;

    @Autowired
    private ClinicInboundItemMapper inboundItemMapper;

    @Autowired
    private MedicineMapper medicineMapper;

    @Autowired
    private InventoryRecordMapper inventoryMapper;

    @GetMapping("/orders")
    public List<ClinicInboundOrder> getOrders(@RequestParam(value = "orderType", required = false) String orderType) {
        LambdaQueryWrapper<ClinicInboundOrder> qw = new LambdaQueryWrapper<>();
        if (orderType != null && !orderType.isEmpty()) {
            qw.eq(ClinicInboundOrder::getOrderType, orderType);
        }
        qw.orderByDesc(ClinicInboundOrder::getCreatedAt);
        return inboundOrderMapper.selectList(qw);
    }

    @GetMapping("/orders/{id}")
    public Map<String, Object> getOrderDetail(@PathVariable("id") Long id) {
        Map<String, Object> res = new HashMap<>();
        ClinicInboundOrder order = inboundOrderMapper.selectById(id);
        res.put("order", order);
        if (order != null) {
            List<ClinicInboundItem> items = inboundItemMapper.selectList(
                new LambdaQueryWrapper<ClinicInboundItem>().eq(ClinicInboundItem::getInboundId, id)
            );
            res.put("items", items);
        }
        return res;
    }

    @PostMapping("/create")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createInboundOrder(@RequestBody Map<String, Object> payload) {
        Map<String, Object> res = new HashMap<>();
        String orderType = (String) payload.getOrDefault("orderType", "采购入库");
        String supplierName = (String) payload.getOrDefault("supplierName", "国药控股湖南有限公司");
        String remark = (String) payload.getOrDefault("remark", "");
        String inboundNo = "RK" + System.currentTimeMillis();

        ClinicInboundOrder order = new ClinicInboundOrder();
        order.setInboundNo(inboundNo);
        order.setOrderType(orderType);
        order.setSupplierName(supplierName);
        order.setCreatorName("张医生");
        order.setStatus("completed");
        order.setAuditorName("张医生");
        order.setAuditTime(LocalDateTime.now());
        order.setRemark(remark);
        
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> itemsData = (List<Map<String, Object>>) payload.get("items");
        BigDecimal totalAmount = BigDecimal.ZERO;
        int count = 0;

        inboundOrderMapper.insert(order);

        if (itemsData != null) {
            for (Map<String, Object> itemMap : itemsData) {
                Long medId = Long.valueOf(itemMap.get("medicineId").toString());
                int qty = Integer.parseInt(itemMap.get("quantity").toString());
                BigDecimal costPrice = new BigDecimal(itemMap.get("costPrice").toString());
                BigDecimal retailPrice = new BigDecimal(itemMap.getOrDefault("retailPrice", "0.00").toString());
                String batchNo = (String) itemMap.getOrDefault("batchNumber", "PH" + System.currentTimeMillis() % 10000);
                
                Medicine med = medicineMapper.selectById(medId);
                String medName = med != null ? med.getName() : "药品";
                String spec = med != null ? med.getSpecification() : "";
                
                BigDecimal itemTotal = costPrice.multiply(BigDecimal.valueOf(qty));
                totalAmount = totalAmount.add(itemTotal);
                count++;

                ClinicInboundItem item = new ClinicInboundItem();
                item.setInboundId(order.getId());
                item.setMedicineId(medId);
                item.setMedicineName(medName);
                item.setSpecification(spec);
                item.setBatchNumber(batchNo);
                item.setQuantity(qty);
                item.setCostPrice(costPrice);
                item.setRetailPrice(retailPrice);
                item.setTotalCost(itemTotal);
                inboundItemMapper.insert(item);

                // Update medicine stock & write inventory record
                if (med != null) {
                    med.setStock(med.getStock() + qty);
                    medicineMapper.updateById(med);

                    InventoryRecord ir = new InventoryRecord();
                    ir.setMedicineId(med.getId());
                    ir.setMedicineName(med.getName());
                    ir.setRecordType(orderType);
                    ir.setChangeQty(qty);
                    ir.setBalanceQty(med.getStock());
                    ir.setRefOrderNo(inboundNo);
                    ir.setOperator("张医生");
                    ir.setRemark("入库单入库过账，增加库存");
                    ir.setCreateTime(LocalDateTime.now());
                    inventoryMapper.insert(ir);
                }
            }
        }

        order.setTotalAmount(totalAmount);
        order.setItemCount(count);
        inboundOrderMapper.updateById(order);

        res.put("success", true);
        res.put("inboundNo", inboundNo);
        res.put("totalAmount", totalAmount);
        res.put("message", "入库单已生成并成功审核过账，药品库存已实时累加！");
        return res;
    }
}
