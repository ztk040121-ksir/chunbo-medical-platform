package com.chunbo.medical.service;

import com.chunbo.medical.entity.Medicine;
import com.chunbo.medical.mapper.MedicineMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MedicineService {
    @Autowired
    private MedicineMapper medicineMapper;

    public List<Medicine> listAll() {
        return medicineMapper.selectList(null);
    }
}
