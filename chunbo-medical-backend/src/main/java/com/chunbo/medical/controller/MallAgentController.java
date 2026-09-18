package com.chunbo.medical.controller;

import com.chunbo.medical.entity.MallOrder;
import com.chunbo.medical.entity.MallProduct;
import com.chunbo.medical.service.B2bMultiAgentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mall")
public class MallAgentController {

    @Autowired
    private B2bMultiAgentService agentService;

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

    @PostMapping("/order/create")
    public MallOrder createOrder(@RequestBody Map<String, Object> req) {
        return agentService.createOrderFromBargain(req);
    }
}
