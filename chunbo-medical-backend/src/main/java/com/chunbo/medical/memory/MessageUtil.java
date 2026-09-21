package com.chunbo.medical.memory;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * 消息转换工具类，提供 Message 与 JSON 字符串的互转，用于 Redis 会话记忆存储
 * （参照《SpringAI》笔记 MessageUtil，改用项目已有的 Jackson 替代 hutool，不新增依赖）
 */
public final class MessageUtil {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MessageUtil() {
    }

    /** 将 Message 转为可存入 Redis 的 JSON 字符串 */
    public static String toJson(Message message) {
        MyMessage mm = new MyMessage();
        mm.setMessageType(message.getMessageType().name());
        mm.setTextContent(message.getText());
        try {
            return MAPPER.writeValueAsString(mm);
        } catch (Exception e) {
            throw new RuntimeException("消息序列化失败", e);
        }
    }

    /** 将 Redis 中的 JSON 字符串还原为对应类型的 Message */
    public static Message toMessage(String json) {
        try {
            MyMessage mm = MAPPER.readValue(json, MyMessage.class);
            MessageType type = MessageType.valueOf(mm.getMessageType());
            String text = mm.getTextContent() == null ? "" : mm.getTextContent();
            switch (type) {
                case SYSTEM -> {
                    return new SystemMessage(text);
                }
                case USER -> {
                    return new UserMessage(text);
                }
                case ASSISTANT -> {
                    return new AssistantMessage(text);
                }
                case TOOL -> {
                    // 项目会话历史仅记录 USER/ASSISTANT 文本，TOOL 消息不参与持久化
                    return new AssistantMessage(text);
                }
            }
            throw new RuntimeException("无法识别的消息类型: " + mm.getMessageType());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("消息反序列化失败", e);
        }
    }
}
