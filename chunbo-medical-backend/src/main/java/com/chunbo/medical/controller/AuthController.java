package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.config.JwtUtil;
import com.chunbo.medical.config.PasswordUtil;
import com.chunbo.medical.entity.DoctorAccount;
import com.chunbo.medical.entity.StaffAccount;
import com.chunbo.medical.mapper.DoctorAccountMapper;
import com.chunbo.medical.mapper.StaffAccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private StaffAccountMapper staffAccountMapper;

    @Autowired
    private DoctorAccountMapper doctorAccountMapper;

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();

        if (username.isEmpty() || password.isEmpty()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("message", "\u8BF7\u8F93\u5165\u8D26\u53F7\u548C\u5BC6\u7801");
            return ResponseEntity.status(401).body(error);
        }

        StaffAccount account = null;
        try {
            account = staffAccountMapper.selectOne(
                    new LambdaQueryWrapper<StaffAccount>().eq(StaffAccount::getUsername, username)
            );
        } catch (Exception e) {
            // DB fallback
        }

        if (account != null) {
            // BCrypt 密码校验
            if (!PasswordUtil.matches(password, account.getPassword())) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "\u5BC6\u7801\u9519\u8BEF\uFF0C\u8BF7\u91CD\u65B0\u8F93\u5165");
                return ResponseEntity.status(401).body(error);
            }

            if ("DISABLE".equalsIgnoreCase(account.getStatus())) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "\u8BE5\u8D26\u53F7\u5DF2\u88AB\u505C\u7528\uFF0C\u8BF7\u8054\u7CFB\u7CFB\u7EDF\u7BA1\u7406\u5458");
                return ResponseEntity.status(401).body(error);
            }

            // 如果密码是旧的明文，自动迁移为 BCrypt 加密存储
            if (!account.getPassword().startsWith("$2a$") && !account.getPassword().startsWith("$2b$")) {
                account.setPassword(PasswordUtil.encode(password));
                staffAccountMapper.updateById(account);
            }

            String token = jwtUtil.generateToken(username, account.getRole());
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("token", token);
            result.put("role", account.getRole()); // ADMIN, HR, DOCTOR
            result.put("username", username);
            result.put("displayName", account.getRealName());
            result.put("doctorId", account.getStaffId());
            result.put("staffId", account.getStaffId());
            result.put("department", account.getDepartment());
            result.put("title", account.getTitle());
            return ResponseEntity.ok(result);
        }

        // 医生账号表（sys_doctor_account）：医生账号注册与授权页注册的账号在这里
        DoctorAccount doctorAccount = null;
        try {
            doctorAccount = doctorAccountMapper.selectOne(
                    new LambdaQueryWrapper<DoctorAccount>().eq(DoctorAccount::getUsername, username)
            );
        } catch (Exception e) {
            // DB fallback
        }
        if (doctorAccount != null) {
            // BCrypt 校验；历史明文密码兼容比对并自动迁移加密
            boolean ok;
            if (doctorAccount.getPassword().startsWith("$2a$") || doctorAccount.getPassword().startsWith("$2b$")) {
                ok = PasswordUtil.matches(password, doctorAccount.getPassword());
            } else {
                ok = password.equals(doctorAccount.getPassword());
                if (ok) {
                    doctorAccount.setPassword(PasswordUtil.encode(password));
                    doctorAccountMapper.updateById(doctorAccount);
                }
            }
            if (!ok) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "密码错误，请重新输入");
                return ResponseEntity.status(401).body(error);
            }
            if ("DISABLE".equalsIgnoreCase(doctorAccount.getStatus())) {
                Map<String, Object> error = new HashMap<>();
                error.put("success", false);
                error.put("message", "该医生账号已被停用，请联系系统管理员");
                return ResponseEntity.status(401).body(error);
            }
            String token = jwtUtil.generateToken(username, "DOCTOR");
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("token", token);
            result.put("role", "DOCTOR");
            result.put("username", username);
            result.put("displayName", doctorAccount.getDoctorName());
            result.put("doctorId", doctorAccount.getDoctorId());
            result.put("staffId", doctorAccount.getDoctorId());
            result.put("department", doctorAccount.getDepartment());
            result.put("title", doctorAccount.getTitle());
            return ResponseEntity.ok(result);
        }

        // 兼容默认兜底管理员与医生
        if ("admin".equals(username) && "123456".equals(password)) {
            String token = jwtUtil.generateToken(username, "ADMIN");
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("token", token);
            result.put("role", "ADMIN");
            result.put("username", username);
            result.put("displayName", "\u7CFB\u7EDF\u6700\u9AD8\u7BA1\u7406\u5458");
            result.put("staffId", "ADM_0001");
            result.put("department", "\u533B\u9662\u9662\u529E");
            result.put("title", "\u7BA1\u7406\u5458");
            return ResponseEntity.ok(result);
        }

        if ("kzt".equals(username) && "123456".equals(password)) {
            String token = jwtUtil.generateToken(username, "DOCTOR");
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("token", token);
            result.put("role", "DOCTOR");
            result.put("username", username);
            result.put("displayName", "\u5EB7\u4E3B\u4EFB");
            result.put("doctorId", "DOC_1001");
            result.put("staffId", "DOC_1001");
            result.put("department", "\u5168\u79D1\u95E8\u8BCA");
            result.put("title", "\u4E3B\u4EFB\u533B\u5E08");
            return ResponseEntity.ok(result);
        }

        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", "\u8D26\u53F7\u6216\u5BC6\u7801\u9519\u8BEF\uFF0C\u8BF7\u6838\u5BF9\u540E\u91CD\u8BD5");
        return ResponseEntity.status(401).body(error);
    }

    @GetMapping("/verify")
    public ResponseEntity<Map<String, Object>> verify(@RequestHeader(value = "Authorization", required = false) String auth) {
        Map<String, Object> res = new HashMap<>();
        if (auth != null && auth.startsWith("Bearer ")) {
            String username = jwtUtil.validateToken(auth.substring(7));
            if (username != null) {
                res.put("valid", true);
                res.put("username", username);
                try {
                    StaffAccount acc = staffAccountMapper.selectOne(
                            new LambdaQueryWrapper<StaffAccount>().eq(StaffAccount::getUsername, username)
                    );
                    if (acc != null) {
                        res.put("role", acc.getRole());
                        res.put("displayName", acc.getRealName());
                        res.put("staffId", acc.getStaffId());
                        res.put("doctorId", acc.getStaffId());
                        res.put("department", acc.getDepartment());
                        res.put("title", acc.getTitle());
                    }
                } catch (Exception e) {}
                return ResponseEntity.ok(res);
            }
        }
        res.put("valid", false);
        res.put("message", "Token \u65E0\u6548\u6216\u5DF2\u8FC7\u671F");
        return ResponseEntity.status(401).body(res);
    }
}
