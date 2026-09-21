package com.chunbo.medical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.chunbo.medical.entity.ChatSession;
import com.chunbo.medical.mapper.ChatSessionMapper;
import com.chunbo.medical.vo.ChatSessionVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 会话历史服务（参照《SpringAI》笔记 ChatSessionService）
 * 负责会话标题的新建、异步更新、历史查询（按时间段分组）、删除、手动改标题
 */
@Service
public class ChatSessionService {

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @Autowired
    private ChatMemory chatMemory;

    /** 独立的标题提炼 Client（不共享业务 Client，参照《SpringAI》笔记 AI 提炼标题） */
    @Autowired
    @Qualifier("titleChatClient")
    private ChatClient titleChatClient;

    /** AI 提炼标题专用 system prompt（笔记 10205 行标准模板） */
    private static final String TITLE_PROMPT = """
            请根据对话内容生成中文标题，严格遵循：
            1. 长度≤18字符（含标点/空格，1个汉字=1字符）
            2. 直接输出标题，无任何解释或格式
            3. 禁用非核心词汇："问题""帮助""方法"等（除非对话核心）
            4. 关键词前置突出对话主旨
            5. 语言绝对精简，删除冗余修饰词
            """;

    /**
     * 保存或更新会话：会话不存在则新建（title 为首次提问），存在则更新 updateTime
     * 若已有标题则不再覆盖（保持首次标题或用户手动改的标题）
     */
    public void saveOrUpdate(String bizType, String sessionId, String userId, String title) {
        ChatSession exist = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, sessionId)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (exist == null) {
            ChatSession cs = new ChatSession();
            cs.setSessionId(sessionId);
            cs.setBizType(bizType == null ? "medical" : bizType);
            cs.setUserId(userId);
            cs.setTitle(title != null ? (title.length() > 100 ? title.substring(0, 100) : title) : null);
            cs.setCreateTime(now);
            cs.setUpdateTime(now);
            chatSessionMapper.insert(cs);
        } else {
            // 若标题为空且传入新标题，则补上标题
            if ((exist.getTitle() == null || exist.getTitle().isEmpty()) && title != null && !title.isEmpty()) {
                exist.setTitle(title.length() > 100 ? title.substring(0, 100) : title);
            }
            exist.setUpdateTime(now);
            chatSessionMapper.updateById(exist);
        }
    }

    /**
     * 异步更新会话（参照《SpringAI》笔记 ChatSessionServiceImpl.update）
     * 在 Flux 的 doFinally 里调用：会话不存在则新建；标题为空则用独立 ChatClient + 独立 system prompt 提炼标题；
     * 提炼失败时兜底为首次提问截断；最后刷新 updateTime。
     *
     * @param bizType  业务类型 medical/oa/mall
     * @param sessionId 会话 id（即 conversationId）
     * @param userId    用户标识（云诊所 doctorId / OA staffId / 商城 phone）
     * @param content   完整对话 "USER:提问 ASSISTANT:回答"（用于 AI 提炼标题）
     */
    @Async("chatSessionExecutor")
    public void update(String bizType, String sessionId, String userId, String content) {
        if (sessionId == null || sessionId.isEmpty()) return;
        String effectiveBiz = bizType == null ? "medical" : bizType;
        LocalDateTime now = LocalDateTime.now();

        ChatSession exist = chatSessionMapper.selectOne(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getBizType, effectiveBiz)
                .eq(ChatSession::getUserId, userId)
                .last("LIMIT 1"));

        if (exist == null) {
            ChatSession cs = new ChatSession();
            cs.setSessionId(sessionId);
            cs.setBizType(effectiveBiz);
            cs.setUserId(userId);
            cs.setTitle(null); // 标题先留空，交给下方 AI 提炼
            cs.setCreateTime(now);
            cs.setUpdateTime(now);
            chatSessionMapper.insert(cs);
            exist = cs;
        }

        // 标题为空时才提炼（保留首次标题或用户手动改的标题）
        if (exist.getTitle() == null || exist.getTitle().isEmpty()) {
            String title = aiExtractTitle(content);
            if (title == null || title.isEmpty()) {
                title = fallbackTitle(content);
            }
            if (title != null && !title.isEmpty()) {
                exist.setTitle(title.length() > 100 ? title.substring(0, 100) : title);
            }
        }

        exist.setUpdateTime(now);
        chatSessionMapper.updateById(exist);
    }

    /** 用独立标题 Client 把对话内容提炼为标题，异常/失败返回 null */
    private String aiExtractTitle(String content) {
        if (titleChatClient == null || content == null || content.isEmpty()) return null;
        try {
            String t = titleChatClient.prompt()
                    .system(TITLE_PROMPT)
                    .user("对话内容：\n" + content)
                    .call()
                    .content();
            if (t != null) t = t.trim();
            return (t == null || t.isEmpty()) ? null : t;
        } catch (Exception e) {
            return null;
        }
    }

    /** 提炼失败时兜底：从 content 里取首次提问，截断为标题 */
    private String fallbackTitle(String content) {
        if (content == null) return null;
        String q = content;
        int idx = q.indexOf("ASSISTANT:");
        if (idx >= 0) q = q.substring(0, idx);
        if (q.startsWith("USER:")) q = q.substring("USER:".length());
        q = q.trim();
        if (q.isEmpty()) return null;
        return q.length() > 20 ? q.substring(0, 20) : q;
    }

    /**
     * 查询历史会话（最多 30 条），按「当天 / 最近30天 / 最近1年 / 1年以上」分组
     */
    public Map<String, List<ChatSessionVO>> queryHistory(String bizType, String userId) {
        List<ChatSession> list = chatSessionMapper.selectList(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getBizType, bizType)
                .eq(ChatSession::getUserId, userId)
                .isNotNull(ChatSession::getTitle)
                .orderByDesc(ChatSession::getUpdateTime)
                .last("LIMIT 30"));

        final String TODAY = "当天";
        final String LAST_30_DAYS = "最近30天";
        final String LAST_YEAR = "最近1年";
        final String MORE_THAN_YEAR = "1年以上";

        LocalDateTime now = LocalDateTime.now();
        return list.stream()
                .map(cs -> ChatSessionVO.builder()
                        .sessionId(cs.getSessionId())
                        .title(cs.getTitle())
                        .updateTime(cs.getUpdateTime())
                        .build())
                .collect(Collectors.groupingBy(vo -> {
                    long between = Math.abs(ChronoUnit.DAYS.between(
                            vo.getUpdateTime().toLocalDate(), now.toLocalDate()));
                    if (between == 0) return TODAY;
                    else if (between <= 30) return LAST_30_DAYS;
                    else if (between <= 365) return LAST_YEAR;
                    else return MORE_THAN_YEAR;
                }, Collectors.toList()));
    }

    /**
     * 删除历史会话：物理删除 DB 数据 + 清除 Redis 会话记忆
     */
    public void deleteSession(String bizType, String sessionId, String userId) {
        chatSessionMapper.delete(new LambdaQueryWrapper<ChatSession>()
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getBizType, bizType)
                .eq(ChatSession::getUserId, userId));
        try {
            chatMemory.clear(sessionId);
        } catch (Exception ignored) {
        }
    }

    /**
     * 手动更新历史会话标题
     */
    public void updateTitle(String bizType, String sessionId, String userId, String title) {
        chatSessionMapper.update(null, new LambdaUpdateWrapper<ChatSession>()
                .set(ChatSession::getTitle, title != null && title.length() > 100 ? title.substring(0, 100) : title)
                .eq(ChatSession::getSessionId, sessionId)
                .eq(ChatSession::getBizType, bizType)
                .eq(ChatSession::getUserId, userId));
    }
}
