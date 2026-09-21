package com.chunbo.medical.enums;

/**
 * 商城订单状态枚举（统一散落各处的订单状态魔法字符串）
 * 存储值为中文描述串，与 mall_order.status 现有数据一致（避免数据迁移）；
 * 前端筛选 key 对应 PENDING / SHIPPED / DELIVERED。
 */
public enum OrderStatusEnum {

    PENDING("待发货", "待发货出库"),
    SHIPPED("已发货 / 春播便民速递运输中", "已发货运输中"),
    DELIVERED("已送达 / 居民已签收", "已送达");

    /** 数据库存储值 */
    private final String code;
    /** 展示文案 */
    private final String label;

    OrderStatusEnum(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 是否已发货（含运输中 / 已送达） */
    public static boolean isShipped(String status) {
        return status != null && (status.contains("已发货") || status.contains("已送达"));
    }

    /** 是否已送达（终态） */
    public static boolean isDelivered(String status) {
        return status != null && status.contains("已送达");
    }

    /** 是否待发货（未发货，含空状态） */
    public static boolean isPending(String status) {
        return status == null || status.isEmpty() || !isShipped(status);
    }
}
