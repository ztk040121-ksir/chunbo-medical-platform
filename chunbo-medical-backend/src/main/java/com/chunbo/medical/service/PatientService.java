package com.chunbo.medical.service;

import com.chunbo.medical.entity.Patient;
import com.chunbo.medical.mapper.PatientMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PatientService {
    @Autowired
    private PatientMapper patientMapper;

    public List<Patient> listAll() {
        return patientMapper.selectList(null);
    }

    public Patient getById(Long id) {
        return patientMapper.selectById(id);
    }
}
