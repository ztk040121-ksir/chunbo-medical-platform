package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.*;
import com.chunbo.medical.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/stocktake")
public class StocktakeController {

    @Autowired
    private ClinicStocktakeMapper stocktakeMapper;

    @Autowired
    private ClinicStocktakeItemMapper stocktakeItemMapper;

    @Autowired
    private MedicineMapper medicineMapper;

    @GetMapping("/list")
    public List<ClinicStocktake> getList() {
        LambdaQueryWrapper<ClinicStocktake> qw = new LambdaQueryWrapper<>();
        qw.orderByDesc(ClinicStocktake::getCreatedAt);
        return stocktakeMapper.selectList(qw);
    }

    @GetMapping("/{id}")
    public Map<String, Object> getDetail(@PathVariable("id") Long id) {
        Map<String, Object> res = new HashMap<>();
        ClinicStocktake st = stocktakeMapper.selectById(id);
        res.put("stocktake", st);
        if (st != null) {
            List<ClinicStocktakeItem> items = stocktakeItemMapper.selectList(
                new LambdaQueryWrapper<ClinicStocktakeItem>().eq(ClinicStocktakeItem::getStocktakeId, id)
            );
            res.put("items", items);
        }
        return res;
    }

    @PostMapping("/create")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createStocktake(@RequestBody Map<String, Object> payload) {
        Map<String, Object> res = new HashMap<>();
        String scope = (String) payload.getOrDefault("categoryScope", "全品类");
        String stocktakeNo = "PD" + System.currentTimeMillis();

        LambdaQueryWrapper<Medicine> qw = new LambdaQueryWrapper<>();
        if (!"全品类".equals(scope)) {
            qw.eq(Medicine::getPrimaryCategory, scope);
        }
        List<Medicine> meds = medicineMapper.selectList(qw);

        ClinicStocktake st = new ClinicStocktake();
        st.setStocktakeNo(stocktakeNo);
        st.setCategoryScope(scope);
        st.setOperatorName("张医生");
        st.setStatus("completed");
        st.setRemark("快速盘点生成与损益核算");

        int totalBook = 0;
        int totalActual = 0;
        BigDecimal totalProfitLoss = BigDecimal.ZERO;

        stocktakeMapper.insert(st);

        @SuppressWarnings("unchecked")
        Map<String, Integer> actualInput = (Map<String, Integer>) payload.getOrDefault("actualCounts", Collections.emptyMap());

        for (Medicine m : meds) {
            int bookQty = m.getStock();
            // If user inputted actual count, use it, else default to bookQty
            int actualQty = actualInput.containsKey(String.valueOf(m.getId())) 
                ? actualInput.get(String.valueOf(m.getId())) 
                : bookQty;
            int diffQty = actualQty - bookQty;
            BigDecimal cost = m.getCostPrice() != null ? m.getCostPrice() : BigDecimal.ZERO;
            BigDecimal diffAmt = cost.multiply(BigDecimal.valueOf(diffQty));

            totalBook += bookQty;
            totalActual += actualQty;
            totalProfitLoss = totalProfitLoss.add(diffAmt);

            ClinicStocktakeItem item = new ClinicStocktakeItem();
            item.setStocktakeId(st.getId());
            item.setMedicineId(m.getId());
            item.setMedicineName(m.getName());
            item.setSpecification(m.getSpecification());
            item.setBookQuantity(bookQty);
            item.setActualQuantity(actualQty);
            item.setDiffQuantity(diffQty);
            item.setCostPrice(cost);
            item.setDiffAmount(diffAmt);
            item.setBatchNumber("BATCH-" + m.getId());
            item.setLocationCode(m.getLocationCode());
            stocktakeItemMapper.insert(item);

            // Update real medicine stock to actual count
            if (diffQty != 0) {
                m.setStock(actualQty);
                medicineMapper.updateById(m);
            }
        }

        st.setTotalBookQty(totalBook);
        st.setTotalActualQty(totalActual);
        st.setProfitLossQty(totalActual - totalBook);
        st.setProfitLossAmount(totalProfitLoss);
        stocktakeMapper.updateById(st);

        res.put("success", true);
        res.put("stocktakeNo", stocktakeNo);
        res.put("totalBookQty", totalBook);
        res.put("totalActualQty", totalActual);
        res.put("profitLossQty", totalActual - totalBook);
        res.put("profitLossAmount", totalProfitLoss);
        res.put("message", "盘点单 " + stocktakeNo + " 已成功完成核算与库存校准！");
        return res;
    }
}
