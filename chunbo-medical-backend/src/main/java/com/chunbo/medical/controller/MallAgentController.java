package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.chunbo.medical.agent.AgentRouter;
import com.chunbo.medical.agent.MallGeneralAgent;
import com.chunbo.medical.agent.MallRouteAgent;
import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.dto.PageParam;
import com.chunbo.medical.entity.MallOrder;
import com.chunbo.medical.entity.MallProduct;
import com.chunbo.medical.service.B2bMultiAgentService;
import com.chunbo.medical.vo.ChatEventVO;
import com.chunbo.medical.vo.PageResult;
import jakarta.servlet.http.HttpServletRequest;
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

    @Autowired
    private com.chunbo.medical.mapper.MallUserMapper mallUserMapper;

    @Autowired(required = false)
    private com.chunbo.medical.mapper.MallProductMapper productMapper;

    @Autowired(required = false)
    private com.chunbo.medical.mapper.MallOrderMapper orderMapper;

    @Autowired
    private com.chunbo.medical.service.OaAssistantService oaAssistantService;

    @GetMapping("/products")
    public Object getProducts(
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            PageParam pageParam) {
        if ((pageNo != null || pageSize != null) && productMapper != null) {
            LambdaQueryWrapper<MallProduct> qw = new LambdaQueryWrapper<MallProduct>()
                    .and(w -> w.isNull(MallProduct::getStatus).or().ne(MallProduct::getStatus, "OFF_SALE"))
                    .orderByAsc(MallProduct::getId);
            Page<MallProduct> pPage = productMapper.selectPage(pageParam.toPage(), qw);
            return PageResult.of(pPage);
        }
        return agentService.getProducts();
    }

    @GetMapping("/orders")
    public Object getOrders(
            @RequestParam(value = "username", required = false) String paramUsername,
            @RequestParam(value = "phone", required = false) String paramPhone,
            @RequestParam(value = "pageNo", required = false) Integer pageNo,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            PageParam pageParam,
            HttpServletRequest request) {
        String username = (String) request.getAttribute("username");
        if (username == null || username.isBlank()) {
            username = paramUsername;
        }
        if ((username == null || username.isBlank()) && paramPhone != null && !paramPhone.isBlank() && mallUserMapper != null) {
            com.chunbo.medical.entity.MallUser mu = mallUserMapper.selectOne(
                    new LambdaQueryWrapper<com.chunbo.medical.entity.MallUser>()
                            .eq(com.chunbo.medical.entity.MallUser::getPhone, paramPhone.trim())
            );
            if (mu != null) username = mu.getUsername();
        }
        if (pageNo != null || pageSize != null) {
            if (username == null || username.isBlank() || orderMapper == null) {
                return PageResult.of(new java.util.ArrayList<>(), 0, pageParam.getPageNo(), pageParam.getPageSize());
            }
            com.chunbo.medical.entity.MallUser user = mallUserMapper.selectOne(
                    new LambdaQueryWrapper<com.chunbo.medical.entity.MallUser>()
                            .eq(com.chunbo.medical.entity.MallUser::getUsername, username));
            if (user == null) {
                return PageResult.of(new java.util.ArrayList<>(), 0, pageParam.getPageNo(), pageParam.getPageSize());
            }
            final Long uid = user.getId();
            final String ph = user.getPhone() == null ? "" : user.getPhone().trim();
            final String nickname = user.getNickname() == null ? "" : user.getNickname().trim();
            LambdaQueryWrapper<MallOrder> wrapper = new LambdaQueryWrapper<MallOrder>().orderByDesc(MallOrder::getCreateTime);
            wrapper.and(w -> {
                w.eq(MallOrder::getUserId, uid);
                if (!nickname.isEmpty()) w.or().like(MallOrder::getBuyerName, nickname);
                if (!ph.isEmpty()) {
                    w.or().like(MallOrder::getBuyerName, ph)
                            .or().like(MallOrder::getBargainNotes, ph);
                }
            });
            Page<MallOrder> pPage = orderMapper.selectPage(pageParam.toPage(), wrapper);
            return PageResult.of(pPage);
        }
        return agentService.getOrdersForUser(username);
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
            @RequestParam(value = "attachmentId", required = false) String attachmentId,
            jakarta.servlet.http.HttpServletResponse response) {
        if (response != null) {
            response.setHeader("Cache-Control", "no-cache, no-transform");
            response.setHeader("X-Accel-Buffering", "no");
            response.setHeader("Connection", "keep-alive");
        }

        String effectiveUserId = (phone != null && !phone.isEmpty()) ? phone
                : (userName != null && !userName.isEmpty() ? userName : sessionId);
        // 多智能体路由：MallRouteAgent 判意图 → 业务智能体 processStream（携带附件标识，供图片识别）
        Map<String, Object> context = new HashMap<>();
        if (attachmentId != null && !attachmentId.isEmpty()) context.put(AgentConstant.ATTACHMENT_ID, attachmentId);
        if (phone != null && !phone.isEmpty()) context.put("phone", phone);
        if (userName != null && !userName.isEmpty()) context.put("userName", userName);
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
    public MallOrder createOrder(@RequestBody Map<String, Object> req, HttpServletRequest request) {
        // 从 token 解析当前登录用户名，用于订单落 user_id 精确隔离
        String username = (String) request.getAttribute("username");
        if ((username == null || username.isBlank()) && req.containsKey("username") && req.get("username") != null) {
            username = String.valueOf(req.get("username")).trim();
        }
        if ((username == null || username.isBlank()) && req.containsKey("buyerPhone") && req.get("buyerPhone") != null) {
            String bPhone = String.valueOf(req.get("buyerPhone")).trim();
            if (!bPhone.isBlank() && mallUserMapper != null) {
                com.chunbo.medical.entity.MallUser mu = mallUserMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.chunbo.medical.entity.MallUser>()
                                .eq(com.chunbo.medical.entity.MallUser::getPhone, bPhone)
                );
                if (mu != null) username = mu.getUsername();
            }
        }
        if (username != null && !username.isBlank()) {
            req.put("_username", username);
        }
        return agentService.createOrderFromBargain(req);
    }

    /**
     * 患者端确认送达（居民签收）——C 端可访问接口（/api/mall 白名单放行）。
     * 状态流转：已发货 → 已送达，与 PC 端 /api/admin/mall/order/deliver 共用同一份确定性逻辑。
     * POST /api/mall/order/deliver  body: {"orderNo": "B2C..."}
     */
    @PostMapping("/order/deliver")
    public Map<String, Object> confirmDelivered(@RequestBody Map<String, Object> body) {
        String orderNo = body.getOrDefault("orderNo", "").toString().trim();
        Map<String, Object> res = new HashMap<>();
        if (orderNo.isEmpty()) {
            res.put("success", false);
            res.put("message", "订单号不能为空");
            return res;
        }
        Map<String, Object> r = oaAssistantService.confirmOrderDelivered(orderNo);
        res.putAll(r);
        return res;
    }
}
