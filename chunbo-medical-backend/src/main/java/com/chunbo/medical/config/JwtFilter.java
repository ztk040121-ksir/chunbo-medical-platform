package com.chunbo.medical.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    private static final List<String> WHITE_LIST = List.of(
            "/api/auth/",
            "/api/medical/chat/pre-consult", // 智能预问诊接口开放（支持患者端/自助端免密问答）
            "/api/session",                  // 会话历史接口开放（支持预问诊与各端调阅）
            "/api/mall/user/",
            "/api/mall/products",
            "/api/mall/chat",
            "/api/audio/",   // 语音接口开放给未登录的商城游客（TTS 朗读/ASR 录音）
            "/api/upload/",  // 多模态附件上传开放给商城游客（图片/Excel 识别）
            "/mcp",
            "/sse",
            "/uploads/",
            "/error",
            "/favicon.ico",
            "/v3/api-docs",
            "/swagger-ui",
            "/doc.html"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        if (WHITE_LIST.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        String username = null;
        String role = null;
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            username = jwtUtil.validateToken(token);
            if (username != null) {
                role = jwtUtil.extractRole(token);
                request.setAttribute("username", username);
                request.setAttribute("role", role);
            }
        }

        // 未登录统一 401
        if (username == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"success\":false,\"error\":\"\u672A\u6388\u6743\uFF0C\u8BF7\u5148\u767B\u5F55\",\"code\":401}");
            return;
        }

        // 接口级角色校验：管理接口仅 ADMIN/HR，医生接口仅 ADMIN
        // 例外：医生/员工名单是挂号前台的基础数据，任何已登录角色都可读
        if ("/api/doctor/list".equals(path) && "GET".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }
        // 商城普通用户（USER，可自助注册）仅能访问商城域，隔离医疗/管理接口，堵住自注册越权
        if ("USER".equals(role) && !path.startsWith("/api/mall/")) {
            writeForbidden(response, "\u65E0\u6743\u9650\u8BBF\u95EE\u8BE5\u63A5\u53E3");
            return;
        }
        if (path.startsWith("/api/admin/") && !"ADMIN".equals(role) && !"HR".equals(role)) {
            writeForbidden(response, "\u65E0\u6743\u9650\u8BBF\u95EE\u7BA1\u7406\u63A5\u53E3");
            return;
        }
        if (path.startsWith("/api/doctor/") && !"ADMIN".equals(role)) {
            writeForbidden(response, "\u65E0\u6743\u9650\u8BBF\u95EE\u533B\u751F\u7BA1\u7406\u63A5\u53E3");
            return;
        }
        // 数据导出中心涉及全院工资/订单等敏感数据，仅 ADMIN/HR 可导出
        if (path.startsWith("/api/export/") && !"ADMIN".equals(role) && !"HR".equals(role)) {
            writeForbidden(response, "\u65E0\u6743\u9650\u8BBF\u95EE\u6570\u636E\u5BFC\u51FA\u63A5\u53E3");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeForbidden(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"error\":\"" + msg + "\",\"code\":403}");
    }
}
