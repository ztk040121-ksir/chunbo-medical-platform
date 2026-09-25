package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.config.JwtUtil;
import com.chunbo.medical.config.PasswordUtil;
import com.chunbo.medical.entity.MallUser;
import com.chunbo.medical.mapper.MallUserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/mall/user")
public class MallAuthController {

    @Autowired
    private MallUserMapper mallUserMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> register(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();
        String realName = body.getOrDefault("realName", "").trim();
        String nickname = body.getOrDefault("nickname", "").trim();
        String phone = body.getOrDefault("phone", "").trim();
        String address = body.getOrDefault("address", "").trim();

        Map<String, Object> res = new HashMap<>();
        if (username.isEmpty() || password.isEmpty()) {
            res.put("success", false);
            res.put("code", 400);
            res.put("message", "请输入注册账号和密码");
            return ResponseEntity.badRequest().body(res);
        }
        // 口径与商城注册页一致：真实姓名/称呼、联系手机号必填，密码至少 6 位，手机号 1 开头 11 位
        if (realName.isEmpty() && nickname.isEmpty()) {
            res.put("success", false);
            res.put("code", 400);
            res.put("message", "请输入真实姓名或称呼");
            return ResponseEntity.badRequest().body(res);
        }
        if (phone.isEmpty() || !phone.matches("1\\d{10}")) {
            res.put("success", false);
            res.put("code", 400);
            res.put("message", "请输入 1 开头的 11 位手机号");
            return ResponseEntity.badRequest().body(res);
        }
        if (password.length() < 6) {
            res.put("success", false);
            res.put("code", 400);
            res.put("message", "密码长度至少 6 位");
            return ResponseEntity.badRequest().body(res);
        }

        // 检查用户名或手机号是否已被占用
        LambdaQueryWrapper<MallUser> checkQw = new LambdaQueryWrapper<MallUser>()
                .eq(MallUser::getUsername, username);
        if (!phone.isEmpty()) {
            checkQw.or().eq(MallUser::getPhone, phone);
        }
        Long count = mallUserMapper.selectCount(checkQw);
        if (count > 0) {
            res.put("success", false);
            res.put("code", 400);
            res.put("message", "账号或手机号已存在，请直接登录");
            return ResponseEntity.badRequest().body(res);
        }

        String displayName = !realName.isEmpty() ? realName : (!nickname.isEmpty() ? nickname : "健康居民");
        String finalPhone = !phone.isEmpty() ? phone : username;

        MallUser user = new MallUser();
        user.setUsername(username);
        user.setPassword(PasswordUtil.encode(password)); // BCrypt 强哈希加密
        user.setNickname(displayName);
        user.setPhone(finalPhone);
        user.setAddress(address); // 收货地址由用户自行填写，注册时未填则留空（下单结算时再补），不写死默认地址
        user.setStatus("ENABLE");
        user.setBalance(new BigDecimal("200.00")); // 赠送新人健康体验金
        user.setPoints(200);
        user.setCreateTime(LocalDateTime.now());

        mallUserMapper.insert(user);

        String token = jwtUtil.generateToken(username, "USER");
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("nickname", user.getNickname());
        userData.put("realName", user.getNickname());
        userData.put("phone", user.getPhone());
        userData.put("address", user.getAddress());
        userData.put("balance", user.getBalance());
        userData.put("points", user.getPoints());

        res.put("success", true);
        res.put("code", 200);
        res.put("token", token);
        res.put("user", userData);
        res.put("message", "注册成功，欢迎使用春播便民网上药房！");
        return ResponseEntity.ok(res);
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String account = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();

        Map<String, Object> res = new HashMap<>();
        if (account.isEmpty() || password.isEmpty()) {
            res.put("success", false);
            res.put("code", 400);
            res.put("message", "请输入用户名或手机号以及密码");
            return ResponseEntity.badRequest().body(res);
        }

        // 支持输入用户名或手机号进行登录
        MallUser user = mallUserMapper.selectOne(
                new LambdaQueryWrapper<MallUser>()
                        .eq(MallUser::getUsername, account)
                        .or()
                        .eq(MallUser::getPhone, account)
        );
        if (user == null) {
            res.put("success", false);
            res.put("code", 401);
            res.put("message", "账号或手机号不存在，请先注册");
            return ResponseEntity.status(401).body(res);
        }

        if (!PasswordUtil.matches(password, user.getPassword())) {
            res.put("success", false);
            res.put("code", 401);
            res.put("message", "密码错误，请重新输入");
            return ResponseEntity.status(401).body(res);
        }

        if ("DISABLE".equalsIgnoreCase(user.getStatus())) {
            res.put("success", false);
            res.put("code", 403);
            res.put("message", "该用户账号已被停用，请联系商城客服");
            return ResponseEntity.status(403).body(res);
        }

        String token = jwtUtil.generateToken(user.getUsername(), "USER");
        Map<String, Object> userData = new HashMap<>();
        userData.put("id", user.getId());
        userData.put("username", user.getUsername());
        userData.put("nickname", user.getNickname());
        userData.put("realName", user.getNickname());
        userData.put("phone", user.getPhone());
        userData.put("address", user.getAddress());
        userData.put("balance", user.getBalance());
        userData.put("points", user.getPoints());

        res.put("success", true);
        res.put("code", 200);
        res.put("token", token);
        res.put("user", userData);
        res.put("message", "登录成功，欢迎回来！");
        return ResponseEntity.ok(res);
    }

    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getInfo(@RequestHeader(value = "Authorization", required = false) String auth) {
        Map<String, Object> res = new HashMap<>();
        if (auth != null && auth.startsWith("Bearer ")) {
            String username = jwtUtil.validateToken(auth.substring(7));
            if (username != null) {
                MallUser user = mallUserMapper.selectOne(
                        new LambdaQueryWrapper<MallUser>().eq(MallUser::getUsername, username)
                );
                if (user != null) {
                    res.put("success", true);
                    res.put("code", 200);
                    Map<String, Object> userData = new HashMap<>();
                    userData.put("id", user.getId());
                    userData.put("username", user.getUsername());
                    userData.put("nickname", user.getNickname());
                    userData.put("realName", user.getNickname());
                    userData.put("phone", user.getPhone());
                    userData.put("address", user.getAddress());
                    userData.put("balance", user.getBalance());
                    userData.put("points", user.getPoints());
                    res.put("user", userData);
                    return ResponseEntity.ok(res);
                }
            }
        }
        res.put("success", false);
        res.put("code", 401);
        res.put("message", "登录已过期");
        return ResponseEntity.status(401).body(res);
    }
}
