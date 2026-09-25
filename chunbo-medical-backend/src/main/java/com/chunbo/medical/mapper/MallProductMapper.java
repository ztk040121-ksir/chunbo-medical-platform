package com.chunbo.medical.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.chunbo.medical.entity.MallProduct;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface MallProductMapper extends BaseMapper<MallProduct> {

    /** 原子扣减库存：仅当剩余 stock >= qty 时才成功（影响行数 0 = 库存不足），防并发超卖 */
    @Update("UPDATE mall_product SET stock = stock - #{qty} WHERE id = #{id} AND stock >= #{qty}")
    int deductStock(@Param("id") Long id, @Param("qty") int qty);

    /** 原子累加库存（入库 / 补货用） */
    @Update("UPDATE mall_product SET stock = stock + #{qty} WHERE id = #{id}")
    int addStock(@Param("id") Long id, @Param("qty") int qty);
}