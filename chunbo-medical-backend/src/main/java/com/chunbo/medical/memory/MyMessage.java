package com.chunbo.medical.memory;

/**
 * 可序列化消息体（用于 Redis 会话记忆持久化）
 * Spring AI 的 Message 实现类 textContent 无 getter，直接序列化会丢内容，
 * 故自定义一个扁平对象，把需要的字段拷贝过来后再做 JSON 序列化（参照《SpringAI》笔记 MessageUtil 设计）。
 */
public class MyMessage {

    /** 消息类型：SYSTEM / USER / ASSISTANT / TOOL */
    private String messageType;

    /** 消息文本内容 */
    private String textContent;

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }

    public String getTextContent() {
        return textContent;
    }

    public void setTextContent(String textContent) {
        this.textContent = textContent;
    }
}
