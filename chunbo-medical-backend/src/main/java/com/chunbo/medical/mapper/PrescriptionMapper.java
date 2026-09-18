package com.chunbo.medical.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chunbo.medical.entity.Prescription;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PrescriptionMapper extends BaseMapper<Prescription> {
}
