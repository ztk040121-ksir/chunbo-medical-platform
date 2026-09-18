package com.chunbo.medical.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chunbo.medical.entity.Patient;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PatientMapper extends BaseMapper<Patient> {
}
