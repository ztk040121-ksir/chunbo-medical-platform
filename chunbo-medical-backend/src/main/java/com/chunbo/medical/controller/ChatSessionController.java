package com.chunbo.medical.controller;

import com.chunbo.medical.service.ChatSessionService;
import com.chunbo.medical.vo.ChatSessionVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 会话历史 Controller（参照《SpringAI》笔记 SessionController）
 * 提供历史会话查询（按时间段分组）、删除、手动更新标题
 */
@RestController
@RequestMapping("/api/session")
public class ChatSessionController {

    @Autowired
    private ChatSessionService chatSessionService;

    /**
     * 查询历史会话列表（最多 30 条，按当天/30天/1年/1年以上分组）
     * GET /api/session/history?bizType=medical&userId=xxx
     */
    @GetMapping("/history")
    public Map<String, List<ChatSessionVO>> queryHistory(
            @RequestParam("bizType") String bizType,
            @RequestParam("userId") String userId) {
        return chatSessionService.queryHistory(bizType, userId);
    }

    /**
     * 删除历史会话（DB + Redis 记忆）
     * DELETE /api/session/history?bizType=medical&sessionId=xxx&userId=xxx
     */
    @DeleteMapping("/history")
    public void deleteSession(
            @RequestParam("bizType") String bizType,
            @RequestParam("sessionId") String sessionId,
            @RequestParam("userId") String userId) {
        chatSessionService.deleteSession(bizType, sessionId, userId);
    }

    /**
     * 手动更新历史会话标题
     * PUT /api/session/history?bizType=medical&sessionId=xxx&userId=xxx&title=xxx
     */
    @PutMapping("/history")
    public void updateTitle(
            @RequestParam("bizType") String bizType,
            @RequestParam("sessionId") String sessionId,
            @RequestParam("userId") String userId,
            @RequestParam("title") String title) {
        chatSessionService.updateTitle(bizType, sessionId, userId, title);
    }

    /**
     * 查询指定会话的完整历史消息（从 ChatMemory 取回）
     * GET /api/session/messages?sessionId=xxx
     */
    @GetMapping("/messages")
    public List<Map<String, String>> getSessionMessages(@RequestParam("sessionId") String sessionId) {
        return chatSessionService.getSessionMessages(sessionId);
    }
}
