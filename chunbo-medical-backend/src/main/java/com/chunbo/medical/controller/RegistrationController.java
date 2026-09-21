package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.ClinicRegistration;
import com.chunbo.medical.entity.Patient;
import com.chunbo.medical.mapper.ClinicRegistrationMapper;
import com.chunbo.medical.mapper.PatientMapper;
import com.chunbo.medical.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/registration")
public class RegistrationController {

    @Autowired
    private ClinicRegistrationMapper registrationMapper;

    @Autowired(required = false)
    private PatientMapper patientMapper;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @GetMapping(value = {"/list", "/queue"})
    public List<ClinicRegistration> getList(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "doctorName", required = false) String doctorName,
            @RequestParam(value = "date", required = false) String date) {
        
        LambdaQueryWrapper<ClinicRegistration> qw = new LambdaQueryWrapper<>();
        if (date != null && !date.trim().isEmpty()) {
            qw.apply("DATE(create_time) = {0}", date);
        }
        if (status != null && !status.trim().isEmpty() && !"all".equalsIgnoreCase(status)) {
            if ("待就诊".equals(status) || "待诊".equals(status)) {
                qw.in(ClinicRegistration::getStatus, "待诊", "待就诊", "候诊中");
            } else {
                qw.eq(ClinicRegistration::getStatus, status);
            }
        }
        if (department != null && !department.trim().isEmpty()) {
            qw.eq(ClinicRegistration::getDepartment, department);
        }
        if (doctorName != null && !doctorName.trim().isEmpty()) {
            qw.eq(ClinicRegistration::getDoctorName, doctorName);
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            qw.and(wrapper -> wrapper
                    .like(ClinicRegistration::getPatientName, keyword)
                    .or().like(ClinicRegistration::getPhone, keyword)
                    .or().like(ClinicRegistration::getRegNo, keyword)
                    .or().like(ClinicRegistration::getQueueNumber, keyword));
        }

        qw.orderByDesc(ClinicRegistration::getId);
        List<ClinicRegistration> list = registrationMapper.selectList(qw);

        // Normalize any empty queueNumber or legacy status
        for (ClinicRegistration reg : list) {
            if (reg.getQueueNumber() == null || reg.getQueueNumber().isEmpty()) {
                reg.setQueueNumber(String.format("%02d", reg.getQueueNo() != null ? reg.getQueueNo() : reg.getId()));
            }
            if ("候诊中".equals(reg.getStatus())) {
                reg.setStatus("待诊");
            }
            if (reg.getFee() == null && reg.getRegFee() != null) {
                reg.setFee(reg.getRegFee());
            }
        }

        return list;
    }

    @PostMapping("/create")
    public ClinicRegistration createRegistration(@RequestBody Map<String, Object> req, jakarta.servlet.http.HttpServletRequest request) {
        ClinicRegistration reg = new ClinicRegistration();
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        reg.setRegNo("REG" + dateStr + ThreadLocalRandom.current().nextInt(100, 999));

        String patientName = req.getOrDefault("patientName", req.getOrDefault("name", "张建国")).toString();
        reg.setPatientName(patientName);
        // 挂号医生兜底 = 当前登录人真实姓名（前端挂号页选了医生则以前端为准）
        String fallbackDoctor = currentUserService.displayName(request);
        if (fallbackDoctor == null || fallbackDoctor.isBlank()) fallbackDoctor = "系统用户";
        reg.setDoctorName(req.getOrDefault("doctorName", fallbackDoctor).toString());
        reg.setDepartment(req.getOrDefault("department", "全科门诊").toString());
        reg.setRegType(req.getOrDefault("regType", "现场挂号").toString());

        String feeStr = req.getOrDefault("fee", req.getOrDefault("regFee", "10.00")).toString();
        BigDecimal fee = new BigDecimal(feeStr);
        reg.setRegFee(fee);
        reg.setFee(fee);

        String st = req.getOrDefault("status", "待诊").toString();
        if ("候诊中".equals(st)) st = "待诊";
        if ("就诊中".equals(st) || "接诊中".equals(st)) {
            String doc = reg.getDoctorName();
            if (doc != null && !doc.trim().isEmpty()) {
                LambdaQueryWrapper<ClinicRegistration> qw = new LambdaQueryWrapper<>();
                qw.eq(ClinicRegistration::getDoctorName, doc)
                  .in(ClinicRegistration::getStatus, Arrays.asList("就诊中", "接诊中"));
                List<ClinicRegistration> others = registrationMapper.selectList(qw);
                for (ClinicRegistration other : others) {
                    other.setStatus("待诊");
                    registrationMapper.updateById(other);
                }
            }
        }
        reg.setStatus(st);

        reg.setGender(req.getOrDefault("gender", "男").toString());

        try {
            reg.setAge(Integer.parseInt(req.getOrDefault("age", req.getOrDefault("ageYears", "30")).toString()));
        } catch (Exception e) {
            reg.setAge(30);
        }

        reg.setAgeText(req.getOrDefault("ageText", reg.getAge() + "岁").toString());
        reg.setPhone(req.getOrDefault("phone", "13800138000").toString());
        reg.setIdCard(req.getOrDefault("idCard", "").toString());
        reg.setAddress(req.getOrDefault("address", "").toString());
        reg.setSymptoms(req.getOrDefault("symptoms", "").toString());

        // 挂号时采集的患者详细资料 (选填)
        reg.setMarriage(req.getOrDefault("marriage", "").toString());
        reg.setHeight(req.getOrDefault("height", "").toString());
        reg.setWeight(req.getOrDefault("weight", "").toString());
        reg.setJob(req.getOrDefault("job", "").toString());
        reg.setCompany(req.getOrDefault("company", "").toString());
        reg.setWechat(req.getOrDefault("wechat", "").toString());
        reg.setInsuranceNo(req.getOrDefault("insuranceNo", "").toString());
        reg.setAccompany(req.getOrDefault("accompany", "").toString());
        reg.setAccompanyPhone(req.getOrDefault("accompanyPhone", "").toString());

        // 排队号：优先用 Redis 原子自增（并发安全），Redis 不可用时退化为 count+1
        int queueNo;
        try {
            if (redisTemplate != null) {
                String seqKey = "reg:queue:seq:" + dateStr;
                // 首次初始化：以当日已有挂号数为起点，避免与历史排队号冲突
                Long todayCount = registrationMapper.selectCount(new LambdaQueryWrapper<ClinicRegistration>()
                        .apply("DATE(create_time) = {0}", java.time.LocalDate.now().toString()));
                redisTemplate.opsForValue().setIfAbsent(seqKey, String.valueOf(todayCount == null ? 0 : todayCount));
                Long seq = redisTemplate.opsForValue().increment(seqKey);
                queueNo = seq != null ? seq.intValue() : 0;
            } else {
                queueNo = 0;
            }
        } catch (Exception e) {
            queueNo = 0;
        }
        if (queueNo <= 0) {
            queueNo = registrationMapper.selectCount(null).intValue() + 1;
        }
        reg.setQueueNo(queueNo);
        reg.setQueueNumber(String.format("%02d", queueNo));
        reg.setCreateTime(LocalDateTime.now());

        // patientId link or sync (身份证号优先匹配同一患者，姓名仅作无身份证时的兜底)
        try {
            if (patientMapper != null) {
                String idCardVal = reg.getIdCard() != null ? reg.getIdCard().trim() : "";
                LambdaQueryWrapper<Patient> pqw = new LambdaQueryWrapper<>();
                if (!idCardVal.isEmpty()) {
                    pqw.eq(Patient::getIdCard, idCardVal).orderByAsc(Patient::getId);
                } else {
                    pqw.eq(Patient::getName, patientName);
                }
                pqw.last("LIMIT 1");
                Patient existing = patientMapper.selectOne(pqw);
                if (existing != null) {
                    reg.setPatientId(existing.getId());
                    // 补全历史档案中缺失的身份证号
                    if (!idCardVal.isEmpty() && (existing.getIdCard() == null || existing.getIdCard().trim().isEmpty())) {
                        existing.setIdCard(idCardVal);
                        patientMapper.updateById(existing);
                    }
                } else {
                    Patient newP = new Patient();
                    newP.setName(patientName);
                    newP.setGender(reg.getGender());
                    newP.setAge(reg.getAge());
                    newP.setPhone(reg.getPhone());
                    newP.setIdCard(reg.getIdCard());
                    newP.setAddress(reg.getAddress());
                    newP.setAllergies("无");
                    newP.setMedicalHistory("既往体健，无特殊慢性病史");
                    newP.setCreateTime(LocalDateTime.now());
                    patientMapper.insert(newP);
                    reg.setPatientId(newP.getId());
                }
            } else {
                reg.setPatientId(1L);
            }
        } catch (Exception e) {
            reg.setPatientId(1L);
        }

        registrationMapper.insert(reg);
        return reg;
    }

    /**
     * 删除本次就诊记录（仅删除挂号/队列记录，患者档案完整保留）
     */
    @DeleteMapping("/{id}")
    public Map<String, Object> deleteRegistration(@PathVariable("id") Long id) {
        Map<String, Object> res = new HashMap<>();
        ClinicRegistration reg = registrationMapper.selectById(id);
        if (reg == null) {
            res.put("success", false);
            res.put("message", "就诊记录不存在或已删除");
            return res;
        }
        registrationMapper.deleteById(id);
        res.put("success", true);
        res.put("message", "本次就诊记录已删除，患者档案保留");
        return res;
    }

    @PostMapping("/sign/{id}")
    public ClinicRegistration signPatient(@PathVariable("id") Long id) {
        ClinicRegistration reg = registrationMapper.selectById(id);
        if (reg != null) {
            reg.setStatus("待诊");
            registrationMapper.updateById(reg);
        }
        return reg;
    }

    @PostMapping("/call/{id}")
    public ClinicRegistration callPatient(@PathVariable("id") Long id) {
        ClinicRegistration reg = registrationMapper.selectById(id);
        if (reg != null) {
            // 语音叫号提醒仅广播通知患者前往诊室候诊，绝不直接将状态更改为就诊中！
            // 只有接诊医生在工作台点击【开始接诊】时才真正转入就诊中！
            registrationMapper.updateById(reg);
        }
        return reg;
    }

    @PostMapping("/start-consult/{id}")
    public ClinicRegistration startConsultation(@PathVariable("id") Long id) {
        ClinicRegistration reg = registrationMapper.selectById(id);
        if (reg != null) {
            String doc = reg.getDoctorName();
            if (doc != null && !doc.trim().isEmpty()) {
                LambdaQueryWrapper<ClinicRegistration> qw = new LambdaQueryWrapper<>();
                qw.eq(ClinicRegistration::getDoctorName, doc)
                  .in(ClinicRegistration::getStatus, Arrays.asList("就诊中", "接诊中"))
                  .ne(ClinicRegistration::getId, id);
                List<ClinicRegistration> others = registrationMapper.selectList(qw);
                for (ClinicRegistration other : others) {
                    other.setStatus("待诊");
                    registrationMapper.updateById(other);
                }
            }
            reg.setStatus("就诊中");
            registrationMapper.updateById(reg);
        }
        return reg;
    }

    @PostMapping("/finish/{id}")
    public ClinicRegistration finishConsultation(@PathVariable("id") Long id) {
        ClinicRegistration reg = registrationMapper.selectById(id);
        if (reg != null) {
            reg.setStatus("已诊");
            registrationMapper.updateById(reg);
        }
        return reg;
    }

    @PostMapping("/cancel/{id}")
    public ClinicRegistration cancelRegistration(@PathVariable("id") Long id) {
        ClinicRegistration reg = registrationMapper.selectById(id);
        if (reg != null) {
            reg.setStatus("已退");
            registrationMapper.updateById(reg);
        }
        return reg;
    }

    @PostMapping("/pass/{id}")
    public ClinicRegistration passPatient(@PathVariable("id") Long id) {
        ClinicRegistration reg = registrationMapper.selectById(id);
        if (reg != null) {
            reg.setStatus("过号");
            registrationMapper.updateById(reg);
        }
        return reg;
    }

    @PostMapping("/restore/{id}")
    public ClinicRegistration restorePatient(@PathVariable("id") Long id) {
        ClinicRegistration reg = registrationMapper.selectById(id);
        if (reg != null) {
            reg.setStatus("待诊");
            registrationMapper.updateById(reg);
        }
        return reg;
    }
    @PostMapping("/update-name/{id}")
    public ClinicRegistration updatePatientInfo(@PathVariable("id") Long id, @RequestBody Map<String, Object> req) {
        ClinicRegistration reg = registrationMapper.selectById(id);
        if (reg != null) {
            if (req.containsKey("patientName")) {
                reg.setPatientName(req.get("patientName").toString());
            }
            if (req.containsKey("phone")) {
                reg.setPhone(req.get("phone").toString());
            }
            if (req.containsKey("gender")) {
                reg.setGender(req.get("gender").toString());
            }
            if (req.containsKey("age")) {
                try {
                    int a = Integer.parseInt(req.get("age").toString());
                    reg.setAge(a);
                    reg.setAgeText(a + "岁");
                } catch (Exception e) {}
            }
            registrationMapper.updateById(reg);
        }
        return reg;
    }
}
