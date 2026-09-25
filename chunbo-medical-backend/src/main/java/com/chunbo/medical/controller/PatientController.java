package com.chunbo.medical.controller;

import com.chunbo.medical.entity.Patient;
import com.chunbo.medical.mapper.PatientMapper;
import com.chunbo.medical.service.PatientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(value = {"/api/patients", "/api/patient"})
public class PatientController {

    @Autowired
    private PatientService patientService;

    @Autowired
    private PatientMapper patientMapper;

    @GetMapping(value = {"", "/list"})
    public List<Patient> listPatients() {
        return patientService.listAll();
    }

    @GetMapping("/{id}")
    public Patient getPatient(@PathVariable("id") Long id) {
        return patientService.getById(id);
    }

    @PostMapping(value = {"", "/create"})
    public Patient addPatient(@RequestBody Patient patient) {
        if (patient.getAllergies() == null || patient.getAllergies().isBlank()) {
            patient.setAllergies("无");
        }
        if (patient.getMedicalHistory() == null || patient.getMedicalHistory().isBlank()) {
            patient.setMedicalHistory("体质平稳，无慢病史");
        }
        if (patient.getMemberLevel() == null || patient.getMemberLevel().isBlank()) {
            patient.setMemberLevel("普通居民");
        }
        if (patient.getDiscountRate() == null) {
            patient.setDiscountRate(1.0);
        }
        if (patient.getPoints() == null) patient.setPoints(0);
        if (patient.getBalance() == null) patient.setBalance(0.0);
        patientMapper.insert(patient);
        return patient;
    }

    @PutMapping("/{id}")
    public Patient updatePatient(@PathVariable("id") Long id, @RequestBody Patient patient) {
        patient.setId(id);
        patientMapper.updateById(patient);
        return patientMapper.selectById(id);
    }

    // ── 会员升级 ──
    @PostMapping("/{id}/upgrade-member")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> upgradeMember(
            @PathVariable("id") Long id,
            @RequestParam("level") String level,
            @RequestParam(value = "expiry", defaultValue = "") String expiry) {
        Patient p = patientMapper.selectById(id);
        if (p == null) return Map.of("success", false, "msg", "患者不存在");
        p.setMemberLevel(level);
        if (!expiry.isBlank()) p.setMemberExpiry(expiry);
        // 设置折扣率
        double rate = switch (level) {
            case "慢病签约会员" -> 0.9;
            case "VIP会员" -> 0.85;
            default -> 1.0;
        };
        p.setDiscountRate(rate);
        patientMapper.updateById(p);
        Map<String, Object> res = new HashMap<>();
        res.put("success", true);
        res.put("patient", p);
        return res;
    }

    // ── 储值充值 ──
    @PostMapping("/{id}/recharge")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> recharge(
            @PathVariable("id") Long id,
            @RequestParam("amount") Double amount) {
        Patient p = patientMapper.selectById(id);
        if (p == null) return Map.of("success", false, "msg", "患者不存在");
        java.math.BigDecimal cur = java.math.BigDecimal.valueOf(p.getBalance() == null ? 0.0 : p.getBalance());
        java.math.BigDecimal add = java.math.BigDecimal.valueOf(amount != null ? amount : 0.0);
        double newBalance = cur.add(add).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue();
        p.setBalance(newBalance);
        // 充值同时增加积分 (1元=1积分)
        int newPoints = (p.getPoints() == null ? 0 : p.getPoints()) + (int) Math.floor(amount != null ? amount : 0.0);
        p.setPoints(newPoints);
        patientMapper.updateById(p);
        return Map.of("success", true, "balance", newBalance, "points", newPoints);
    }

    // ── 绑定附属卡 (主患者 -> 附属患者) ──
    @PostMapping("/{mainId}/bind-auxiliary/{auxId}")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> bindAuxiliary(
            @PathVariable("mainId") Long mainId,
            @PathVariable("auxId") Long auxId) {
        // 检查主会员
        Patient main = patientMapper.selectById(mainId);
        if (main == null) return Map.of("success", false, "msg", "主患者不存在");
        if (!"慢病签约会员".equals(main.getMemberLevel()) && !"VIP会员".equals(main.getMemberLevel())) {
            return Map.of("success", false, "msg", "仅会员可绑定附属卡");
        }
        // 检查附属卡数量上限(3个)
        List<Patient> allPatients = patientService.listAll();
        long auxCount = allPatients.stream()
                .filter(p -> mainId.equals(p.getAuxiliaryOf()))
                .count();
        if (auxCount >= 3) return Map.of("success", false, "msg", "每位会员最多绑定3张附属卡");
        // 检查被绑定患者是否已是某人的附属卡
        Patient aux = patientMapper.selectById(auxId);
        if (aux == null) return Map.of("success", false, "msg", "附属患者不存在");
        if (aux.getAuxiliaryOf() != null) return Map.of("success", false, "msg", "该患者已是其他会员的附属卡");
        // 绑定：同步会员等级和折扣率
        aux.setAuxiliaryOf(mainId);
        aux.setMemberLevel(main.getMemberLevel());
        aux.setDiscountRate(main.getDiscountRate());
        patientMapper.updateById(aux);
        return Map.of("success", true, "msg", "附属卡绑定成功", "auxiliary", aux);
    }

    // ── 解绑附属卡 ──
    @PostMapping("/{auxId}/unbind-auxiliary")
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> unbindAuxiliary(@PathVariable("auxId") Long auxId) {
        Patient aux = patientMapper.selectById(auxId);
        if (aux == null) return Map.of("success", false, "msg", "患者不存在");
        aux.setAuxiliaryOf(null);
        aux.setMemberLevel("普通居民");
        aux.setDiscountRate(1.0);
        patientMapper.updateById(aux);
        return Map.of("success", true, "msg", "附属卡已解绑");
    }

    // ── 获取某主会员的所有附属卡列表 ──
    @GetMapping("/{mainId}/auxiliaries")
    public List<Patient> getAuxiliaries(@PathVariable("mainId") Long mainId) {
        return patientService.listAll().stream()
                .filter(p -> mainId.equals(p.getAuxiliaryOf()))
                .collect(Collectors.toList());
    }
}