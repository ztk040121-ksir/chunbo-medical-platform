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

    private static volatile boolean redisAvailable = true;
    private static volatile long lastCheckTime = 0;
    private static final long CHECK_INTERVAL_MS = 30000;

    private boolean isRedisHealthy() {
        if (!redisAvailable) {
            long now = System.currentTimeMillis();
            if (now - lastCheckTime < CHECK_INTERVAL_MS) {
                return false;
            }
        }
        return true;
    }

    private void markRedisDown() {
        redisAvailable = false;
        lastCheckTime = System.currentTimeMillis();
    }

    private void markRedisUp() {
        redisAvailable = true;
    }

    /** 查询所有对话 id（自动剥离前缀，确保对外提供干净的 conversationId） */
    @Override
    public List<String> findConversationIds() {
        if (!isRedisHealthy()) {
            return new ArrayList<>(FALLBACK.keySet());
        }
        try {
            Set<String> keys = stringRedisTemplate.keys(DEFAULT_PREFIX + "*");
            markRedisUp();
            if (keys == null || keys.isEmpty()) {
                return List.of();
            }
            return keys.stream()
                    .map(k -> k.startsWith(DEFAULT_PREFIX) ? k.substring(DEFAULT_PREFIX.length()) : k)
                    .toList();
        } catch (Exception e) {
            markRedisDown();
            return new ArrayList<>(FALLBACK.keySet());
        }
    }

    /** 根据对话 id 查询 Message 列表 */
    @Override
    public List<Message> findByConversationId(String conversationId) {
        if (!isRedisHealthy()) {
            return FALLBACK.getOrDefault(conversationId, List.of());
        }
        try {
            List<String> jsonList = stringRedisTemplate.opsForList().range(getKey(conversationId), 0, -1);
            if (jsonList == null || jsonList.isEmpty()) {
                return List.of();
            }
            markRedisUp();
            return jsonList.stream().map(MessageUtil::toMessage).toList();
        } catch (Exception e) {
            markRedisDown();
            return FALLBACK.getOrDefault(conversationId, List.of());
        }
    }

    /** 全量覆盖保存 Message 列表（先删旧数据再追加，并设置 14 天 TTL 防止内存泄漏） */
    @Override
    public void saveAll(String conversationId, List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        if (!isRedisHealthy()) {
            FALLBACK.put(conversationId, new ArrayList<>(messages));
            return;
        }
        try {
            String key = getKey(conversationId);
            stringRedisTemplate.delete(key);
            for (Message message : messages) {
                stringRedisTemplate.opsForList().rightPush(key, MessageUtil.toJson(message));
            }
            // 自动配置 14 天滑动过期时间，避免废弃会话长期滞留撑满 Redis
            stringRedisTemplate.expire(key, 14, java.util.concurrent.TimeUnit.DAYS);
            markRedisUp();
        } catch (Exception e) {
            markRedisDown();
            FALLBACK.put(conversationId, new ArrayList<>(messages));
        }
    }

    /** 根据对话 id 删除历史消息 */
    @Override
    public void deleteByConversationId(String conversationId) {
        if (isRedisHealthy()) {
            try {
                stringRedisTemplate.delete(getKey(conversationId));
                markRedisUp();
            } catch (Exception ignored) {
                markRedisDown();
            }
        }
        FALLBACK.remove(conversationId);
    }

    /** 根据前缀和对话 id 生成完整 Redis Key（幂等处理，防止重复拼接前缀） */
    private String getKey(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return DEFAULT_PREFIX + "default";
        }
        if (conversationId.startsWith(DEFAULT_PREFIX)) {
            return conversationId;
        }
        return DEFAULT_PREFIX + conversationId;
    }
}
