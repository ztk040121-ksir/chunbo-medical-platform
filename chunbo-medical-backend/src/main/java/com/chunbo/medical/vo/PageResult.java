package com.chunbo.medical.vo;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 通用分页响应视图对象
 * 标准字段：list、total、pageNo、pageSize、pages
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 当前页数据列表 */
    private List<T> list = new ArrayList<>();

    /** 数据库真实总条数 */
    private long total = 0;

    /** 当前页码（从 1 开始） */
    private long pageNo = 1;

    /** 每页条数 */
    private long pageSize = 10;

    /** 总页数 */
    private long pages = 0;

    /** 快速从 MyBatis-Plus IPage 转换 */
    public static <T> PageResult<T> of(IPage<T> page) {
        if (page == null) {
            return new PageResult<>(new ArrayList<>(), 0, 1, 10, 0);
        }
        return new PageResult<>(
                page.getRecords() != null ? page.getRecords() : new ArrayList<>(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize(),
                page.getPages()
        );
    }

    /** 快速手工组装 */
    public static <T> PageResult<T> of(List<T> list, long total, long pageNo, long pageSize) {
        long pages = (pageSize > 0) ? (total + pageSize - 1) / pageSize : 0;
        return new PageResult<>(list != null ? list : new ArrayList<>(), total, pageNo, pageSize, pages);
    }
}
