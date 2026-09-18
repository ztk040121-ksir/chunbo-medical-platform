package com.chunbo.medical.controller;

import com.chunbo.medical.dto.ChatRequest;
import com.chunbo.medical.service.MedicalChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * 医疗智能问诊 Controller
 * 提供 SSE (Server-Sent Events) 流式对话打字机接口
 */
@RestController
@RequestMapping("/api/medical/chat")
public class MedicalChatController {

    @Autowired
    private MedicalChatService medicalChatService;

    /**
     * SSE 流式问诊接口
     * GET /api/medical/chat/stream?sessionId=1001&patientId=1&message=头晕血压高
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChatGet(@RequestParam("sessionId") String sessionId,
                                      @RequestParam("patientId") Long patientId,
                                      @RequestParam("message") String message) {
        ChatRequest req = new ChatRequest();
        req.setSessionId(sessionId);
        req.setPatientId(patientId);
        req.setMessage(message);
        return medicalChatService.streamChat(req);
    }

    /**
     * POST 方式流式问诊
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> streamChatPost(@RequestBody ChatRequest request) {
        return medicalChatService.streamChat(request);
    }
}
