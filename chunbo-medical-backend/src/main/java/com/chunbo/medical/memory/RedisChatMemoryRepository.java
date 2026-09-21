package com.chunbo.medical.memory;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 基于 Redis 实现 ChatMemoryRepository（参照《SpringAI》笔记 RedisChatMemoryRepository）
 * 用 Redis List 结构存储聊天记录，key = "CHAT:" + conversationId
 * 配合 MessageWindowChatMemory 实现滑动窗口限流
 *
 * 容错：Redis 不可用时降级内存 Map，保证业务不中断；Redis 恢复后自动切回持久化。
 */
@Component
public class RedisChatMemoryRepository implements ChatMemoryRepository {

    /** Redis 中 key 的前缀 */
    public static final String DEFAULT_PREFIX = "CHAT:";

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /** 内存兜底存储：Redis 不可用时临时保存会话记忆 */
    private static final Map<String, List<Message>> FALLBACK = new ConcurrentHashMap<>();

    /** 查询所有对话 id */
    @Override
    public List<String> findConversationIds() {
        try {
            Set<String> keys = stringRedisTemplate.keys(DEFAULT_PREFIX + "*");
            return keys == null ? List.of() : new ArrayList<>(keys);
        } catch (Exception e) {
            return new ArrayList<>(FALLBACK.keySet());
        }
    }

    /** 根据对话 id 查询 Message 列表 */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        try {
            List<String> jsonList = stringRedisTemplate.opsForList().range(getKey(conversationId), 0, -1);
            if (jsonList == null || jsonList.isEmpty()) {
                return List.of();
            }
            return jsonList.stream().map(MessageUtil::toMessage).toList();
        } catch (Exception e) {
            return FALLBACK.getOrDefault(conversationId, List.of());
        }
    }

    /** 全量覆盖保存 Message 列表（先删旧数据再追加） */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        try {
            String key = getKey(conversationId);
            stringRedisTemplate.delete(key);
            for (Message message : messages) {
                stringRedisTemplate.opsForList().rightPush(key, MessageUtil.toJson(message));
            }
        } catch (Exception e) {
            FALLBACK.put(conversationId, new ArrayList<>(messages));
        }
    }

    /** 根据对话 id 删除历史消息 */
    @Override
    public void deleteByConversationId(String conversationId) {
        try {
            stringRedisTemplate.delete(getKey(conversationId));
        } catch (Exception ignored) {
        }
        FALLBACK.remove(conversationId);
    }

    /** 根据前缀和对话 id 生成完整 Redis Key */
    private String getKey(String conversationId) {
        return DEFAULT_PREFIX + conversationId;
    }
}
