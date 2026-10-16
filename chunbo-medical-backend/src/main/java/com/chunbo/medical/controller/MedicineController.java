package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.dto.PageParam;
import com.chunbo.medical.entity.Medicine;
import com.chunbo.medical.mapper.MedicineMapper;
import com.chunbo.medical.service.MedicineService;
import com.chunbo.medical.vo.PageResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/medicines")
public class MedicineController {

    @Autowired
    private MedicineService medicineService;

    @Autowired
    private MedicineMapper medicineMapper;

    @GetMapping
    public Object listMedicines(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            PageParam pageParam) {
        if (pageNo != null || pageSize != null) {
            return PageResult.of(medicineMapper.selectPage(pageParam.toPage(), new LambdaQueryWrapper<Medicine>().orderByAsc(Medicine::getId)));
        }
        return medicineService.listAll();
    }
}
