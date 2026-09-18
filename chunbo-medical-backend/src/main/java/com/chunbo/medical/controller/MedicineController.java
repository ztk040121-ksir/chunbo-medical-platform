package com.chunbo.medical.controller;

import com.chunbo.medical.entity.Medicine;
import com.chunbo.medical.service.MedicineService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/medicines")
public class MedicineController {

    @Autowired
    private MedicineService medicineService;

    @GetMapping
    public List<Medicine> listMedicines() {
        return medicineService.listAll();
    }
}
