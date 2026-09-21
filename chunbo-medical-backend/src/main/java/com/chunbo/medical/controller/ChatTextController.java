package com.chunbo.medical.controller;

import com.chunbo.medical.service.ChatTextService;
import com.chunbo.medical.vo.TextTemplateVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 通用文本处理 Controller（参照《SpringAI》笔记通用文本模型）
 * 提供模板下发与文本处理（帮写/续写/润色/精简/联想词）
 */
@RestController
@RequestMapping("/api/text")
public class ChatTextController {

    @Autowired
    private ChatTextService chatTextService;

    /** 获取全部文本处理模板（$input 为用户输入占位符） */
    @GetMapping("/templates")
    public TextTemplateVO getTemplates() {
        return chatTextService.getTemplates();
    }

    /**
     * 文本处理
     * POST /api/text/process  body: {"type":"polish", "input":"待处理文本"}
     */
    @PostMapping("/process")
    public Map<String, Object> process(@RequestBody Map<String, String> body) {
        String type = body.getOrDefault("type", "polish");
        String input = body.getOrDefault("input", "");
        String result = chatTextService.process(type, input);
        return Map.of("success", true, "type", type, "result", result == null ? "" : result);
    }
}
