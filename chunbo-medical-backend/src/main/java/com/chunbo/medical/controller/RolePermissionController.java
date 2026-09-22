package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.RolePermission;
import com.chunbo.medical.mapper.RolePermissionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色权限配置接口：
 * GET  /api/role-permissions          —— 登录用户可读（云诊所端按角色渲染菜单 / admin 权限编辑页）
 * POST /api/admin/role-permissions/save —— ADMIN/HR 可写（路径级权限保护），body: {role, roleLabel, modules, description}
 */
@RestController
public class RolePermissionController {

    @Autowired
    private RolePermissionMapper rolePermissionMapper;

    @GetMapping("/api/role-permissions")
    public List<RolePermission> list() {
        return rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().orderByAsc(RolePermission::getId));
    }

    @PostMapping("/api/admin/role-permissions/save")
    public ResponseEntity<Map<String, Object>> save(@RequestBody Map<String, Object> body) {
        Map<String, Object> res = new HashMap<>();
        String role = body.getOrDefault("role", "").toString().trim().toUpperCase();
        String scope = body.getOrDefault("scope", "CLINIC").toString().trim().toUpperCase();
        if (!"CLINIC".equals(scope) && !"ADMIN".equals(scope)) scope = "CLINIC";
        if (role.isEmpty()) {
            res.put("success", false);
            res.put("message", "缺少角色标识 role");
            return ResponseEntity.badRequest().body(res);
        }
        Object modules = body.get("modules");
        String modulesJson;
        try {
            com.fasterxml.jackson.databind.ObjectMapper om = new com.fasterxml.jackson.databind.ObjectMapper();
            modulesJson = om.writeValueAsString(modules == null ? List.of() : modules);
        } catch (Exception e) {
            modulesJson = "[]";
        }
        // 同角色同系统唯一（role + scope）
        RolePermission rp = rolePermissionMapper.selectOne(
                new LambdaQueryWrapper<RolePermission>()
                        .eq(RolePermission::getRole, role)
                        .eq(RolePermission::getScope, scope));
        if (rp == null) {
            rp = new RolePermission();
            rp.setRole(role);
            rp.setScope(scope);
        }
        rp.setRoleLabel(body.getOrDefault("roleLabel", role).toString());
        rp.setModulesJson(modulesJson);
        rp.setDescription(body.getOrDefault("description", "").toString());
        rp.setUpdateTime(LocalDateTime.now());
        if (rp.getId() == null) rolePermissionMapper.insert(rp);
        else rolePermissionMapper.updateById(rp);
        res.put("success", true);
        res.put("message", "角色 [" + role + "] 的" + ("ADMIN".equals(scope) ? "管理中台" : "云诊所") + "权限范围已更新，相关账号重新登录后生效");
        return ResponseEntity.ok(res);
    }
}
