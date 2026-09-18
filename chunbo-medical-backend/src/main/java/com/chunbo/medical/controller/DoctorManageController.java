package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.DoctorAccount;
import com.chunbo.medical.mapper.DoctorAccountMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/doctor")
public class DoctorManageController {

    @Autowired
    private DoctorAccountMapper doctorAccountMapper;

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listDoctors() {
        List<DoctorAccount> list = doctorAccountMapper.selectList(
                new LambdaQueryWrapper<DoctorAccount>().orderByDesc(DoctorAccount::getId)
        );
        list.forEach(item -> item.setPassword("******"));
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("data", list);
        res.put("total", list.size());
        return ResponseEntity.ok(res);
    }

    @PostMapping("/register")
    public ResponseEntity<Map<String, Object>> registerDoctor(@RequestBody Map<String, String> body) {
        String username = body.getOrDefault("username", "").trim();
        String password = body.getOrDefault("password", "").trim();
        String doctorName = body.getOrDefault("doctorName", "").trim();
        String doctorId = body.getOrDefault("doctorId", "").trim();
        String department = body.getOrDefault("department", "\u5168\u79D1\u95E8\u8BCA").trim();
        String title = body.getOrDefault("title", "\u4E3B\u6CBB\u533B\u5E08").trim();
        String phone = body.getOrDefault("phone", "").trim();
        String qualificationNo = body.getOrDefault("qualificationNo", "110430105002819").trim();

        Map<String, Object> res = new HashMap<>();
        if (username.isEmpty()) {
            res.put("success", false);
            res.put("message", "\u8BF7\u8F93\u5165\u767B\u5F55\u8D26\u53F7\uFF08\u5DE5\u53F7\uFF09");
            return ResponseEntity.badRequest().body(res);
        }
        if (password.isEmpty()) {
            password = "123456";
        }
        if (doctorName.isEmpty()) {
            res.put("success", false);
            res.put("message", "\u8BF7\u8F93\u5165\u533B\u751F\u771F\u5B9E\u59D3\u540D");
            return ResponseEntity.badRequest().body(res);
        }

        Long count = doctorAccountMapper.selectCount(
                new LambdaQueryWrapper<DoctorAccount>().eq(DoctorAccount::getUsername, username)
        );
        if (count > 0) {
            res.put("success", false);
            res.put("message", "\u8D26\u53F7 [" + username + "] \u5DF2\u7ECF\u5B58\u5728\uFF0C\u8BF7\u66F4\u6362\u5176\u4ED6\u5DE5\u53F7\u6216\u767B\u5F55\u540D");
            return ResponseEntity.badRequest().body(res);
        }

        if (doctorId.isEmpty()) {
            doctorId = "DOC_" + (1000 + System.currentTimeMillis() % 9000);
        }

        DoctorAccount account = new DoctorAccount();
        account.setUsername(username);
        account.setPassword(password);
        account.setDoctorName(doctorName);
        account.setDoctorId(doctorId);
        account.setDepartment(department.isEmpty() ? "\u5168\u79D1\u95E8\u8BCA" : department);
        account.setTitle(title.isEmpty() ? "\u4E3B\u6CBB\u533B\u5E08" : title);
        account.setPhone(phone);
        account.setQualificationNo(qualificationNo);
        account.setStatus("ENABLE");
        account.setCreateTime(LocalDateTime.now());

        doctorAccountMapper.insert(account);

        res.put("success", true);
        res.put("message", "\u533B\u751F\u8D26\u53F7\u6CE8\u518C\u6210\u529F\uFF0C\u53EF\u7ACB\u5373\u5728\u533B\u751F\u5DE5\u4F5C\u53F0\u767B\u5F55");
        account.setPassword("******");
        res.put("data", account);
        return ResponseEntity.ok(res);
    }

    @PostMapping("/update-status")
    public ResponseEntity<Map<String, Object>> updateStatus(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String status = body.getOrDefault("status", "ENABLE").toString();
        DoctorAccount account = doctorAccountMapper.selectById(id);
        Map<String, Object> res = new HashMap<>();
        if (account != null) {
            account.setStatus(status);
            doctorAccountMapper.updateById(account);
            res.put("success", true);
            res.put("message", "\u72B6\u6001\u66F4\u65B0\u6210\u529F");
            return ResponseEntity.ok(res);
        }
        res.put("success", false);
        res.put("message", "\u8D26\u53F7\u4E0D\u5B58\u5728");
        return ResponseEntity.badRequest().body(res);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(@RequestBody Map<String, Object> body) {
        Long id = Long.valueOf(body.get("id").toString());
        String newPassword = body.getOrDefault("newPassword", "123456").toString();
        DoctorAccount account = doctorAccountMapper.selectById(id);
        Map<String, Object> res = new HashMap<>();
        if (account != null) {
            account.setPassword(newPassword);
            doctorAccountMapper.updateById(account);
            res.put("success", true);
            res.put("message", "\u5BC6\u7801\u91CD\u7F6E\u6210\u529F\uFF0C\u65B0\u5BC6\u7801\u4E3A: " + newPassword);
            return ResponseEntity.ok(res);
        }
        res.put("success", false);
        res.put("message", "\u8D26\u53F7\u4E0D\u5B58\u5728");
        return ResponseEntity.badRequest().body(res);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteDoctor(@PathVariable("id") Long id) {
        doctorAccountMapper.deleteById(id);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("message", "\u5220\u9664\u6210\u529F");
        return ResponseEntity.ok(res);
    }
}