package com.chunbo.medical.dto;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;

/**
 * 通用分页请求参数封装
 * 兼容接收 pageNo / pageNum / page 与 pageSize / size 参数
 */
@Data
public class PageParam {

    private Integer pageNo = 1;
    private Integer pageSize = 10;

    public Integer getPageNo() {
        return (pageNo != null && pageNo > 0) ? pageNo : 1;
    }

    public Integer getPageSize() {
        return (pageSize != null && pageSize > 0) ? Math.min(pageSize, 200) : 10;
    }

    public void setPageNum(Integer pageNum) {
        if (pageNum != null && pageNum > 0) {
            this.pageNo = pageNum;
        }
    }

    public void setPage(Integer page) {
        if (page != null && page > 0) {
            this.pageNo = page;
        }
    }

    public void setSize(Integer size) {
        if (size != null && size > 0) {
            this.pageSize = size;
        }
    }

    /** 快速转换为 MyBatis-Plus Page 对象 */
    public <T> Page<T> toPage() {
        return new Page<>(getPageNo(), getPageSize());
    }
}
