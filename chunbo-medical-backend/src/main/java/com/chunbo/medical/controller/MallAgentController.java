package com.chunbo.medical.controller;

import com.chunbo.medical.agent.AgentRouter;
import com.chunbo.medical.agent.MallGeneralAgent;
import com.chunbo.medical.agent.MallRouteAgent;
import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.entity.MallOrder;
import com.chunbo.medical.entity.MallProduct;
import com.chunbo.medical.service.B2bMultiAgentService;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mall")
public class MallAgentController {

    @Autowired
    private B2bMultiAgentService agentService;

    @Autowired
    private AgentRouter agentRouter;

    @Autowired
    private MallRouteAgent mallRouteAgent;

    @Autowired
    private MallGeneralAgent mallGeneralAgent;

    @GetMapping("/products")
    public List<MallProduct> getProducts() {
        return agentService.getProducts();
    }

    @GetMapping("/orders")
    public List<MallOrder> getOrders() {
        return agentService.getOrders();
    }

    @PostMapping("/chat")
    public Map<String, Object> chatWithAgents(@RequestBody Map<String, Object> req) {
        String message = req.getOrDefault("message", "推荐一些适合我们社区门诊的特色贴敷产品").toString();
        String role = req.getOrDefault("role", "consumer").toString();
        String sessionId = req.getOrDefault("sessionId", "SESSION_MALL_001").toString();
        String phone = req.getOrDefault("phone", "").toString();
        String userName = req.getOrDefault("userName", "").toString();
        return agentService.runMultiAgentWorkflow(message, role, sessionId, phone, userName);
    }

    /**
     * SSE 流式药师对话（多智能体路由：RouteAgent 判意图 → 业务智能体 processStream）
     * GET /api/mall/chat/stream?message=xx&sessionId=xx
     */
    @GetMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ChatEventVO> chatWithAgentsStream(
            @RequestParam("message") String message,
            @RequestParam(value = "role", required = false, defaultValue = "consumer") String role,
            @RequestParam(value = "sessionId", required = false, defaultValue = "SESSION_MALL_001") String sessionId,
            @RequestParam(value = "phone", required = false, defaultValue = "") String phone,
            @RequestParam(value = "userName", required = false, defaultValue = "") String userName,
            @RequestParam(value = "attachmentId", required = false) String attachmentId) {

        String effectiveUserId = (phone != null && !phone.isEmpty()) ? phone
                : (userName != null && !userName.isEmpty() ? userName : sessionId);
        // 多智能体路由：MallRouteAgent 判意图 → 业务智能体 processStream（携带附件标识，供图片识别）
        Map<String, Object> context = new HashMap<>();
        if (attachmentId != null && !attachmentId.isEmpty()) context.put(AgentConstant.ATTACHMENT_ID, attachmentId);
        return agentRouter.route(mallRouteAgent, mallGeneralAgent, message, sessionId, effectiveUserId, context);
    }

    /**
     * 停止生成（后端终止 Flux 输出）
     * POST /api/mall/chat/stop?sessionId=xxx
     */
    @PostMapping("/chat/stop")
    public void stopChat(@RequestParam String sessionId) {
        mallGeneralAgent.stop(sessionId);
    }

    @PostMapping("/order/create")
    public MallOrder createOrder(@RequestBody Map<String, Object> req) {
        return agentService.createOrderFromBargain(req);
    }
}
