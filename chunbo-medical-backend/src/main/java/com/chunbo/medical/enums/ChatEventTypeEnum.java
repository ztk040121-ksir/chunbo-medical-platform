package com.chunbo.medical.enums;

/**
 * 聊天消息事件类型（参照《SpringAI》笔记课程卡片标准实现）
 */
public enum ChatEventTypeEnum {
    DATA(1001, "数据事件"),
    STOP(1002, "停止事件"),
    PARAM(1003, "参数事件"),
    PROCESS(1004, "过程事件（MCP工具调用/数据核验等中间过程，前端生成完隐藏）");

    private final int value;
    private final String desc;

    ChatEventTypeEnum(int value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    public int getValue() {
        return value;
    }

    public String getDesc() {
        return desc;
    }
}
