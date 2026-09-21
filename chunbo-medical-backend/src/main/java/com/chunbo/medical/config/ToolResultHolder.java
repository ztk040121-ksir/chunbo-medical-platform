package com.chunbo.medical.config;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具结果保持器（参照《SpringAI》笔记课程卡片标准实现）
 * 用来存储 tools 中得到的结果，请求 id 作为 key，value 为键值对数据。
 * 工具执行完把结果放入容器，流输出的最后判断容器是否有数据，有则追加 PARAM 事件给前端渲染卡片。
 * 为什么用 requestId 而不是 sessionId：同一个 sessionId 也可能并发，必须用每次请求独立的 requestId 关联。
 */
public class ToolResultHolder {

    private static final Map<String, Map<String, Object>> HANDLER_MAP = new ConcurrentHashMap<>();

    /** 工具类，禁止实例化 */
    private ToolResultHolder() {
    }

    public static void put(String key, String field, Object result) {
        if (key == null || field == null) return;
        // 内层同样用并发容器：同一 requestId 下多个工具并发写不同 field 时才不会数据竞争
        HANDLER_MAP.computeIfAbsent(key, k -> new ConcurrentHashMap<>()).put(field, result);
    }

    public static Map<String, Object> get(String key) {
        return key == null ? null : HANDLER_MAP.get(key);
    }

    public static Object get(String key, String field) {
        if (key == null || field == null) return null;
        return Optional.ofNullable(HANDLER_MAP.get(key))
                .map(map -> map.get(field))
                .orElse(null);
    }

    public static void remove(String key) {
        if (key == null) return;
        HANDLER_MAP.remove(key);
    }
}
