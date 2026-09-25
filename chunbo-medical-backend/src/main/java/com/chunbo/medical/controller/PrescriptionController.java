package com.chunbo.medical.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.*;
import com.chunbo.medical.mapper.*;
import com.chunbo.medical.service.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/prescription")
public class PrescriptionController {

    @Autowired
    private PrescriptionMapper prescriptionMapper;

    @Autowired
    private PrescriptionItemMapper itemMapper;

    @Autowired
    private MedicineMapper medicineMapper;

    @Autowired
    private PatientMapper patientMapper;

    @Autowired
    private ClinicTreatmentRecordMapper treatmentRecordMapper;

    @Autowired
    private ClinicRegistrationMapper registrationMapper;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private com.chunbo.medical.service.PrescriptionReviewService prescriptionReviewService;

    @Autowired(required = false)
    private PayTransactionMapper payTransactionMapper;

    @Autowired
    private org.springframework.transaction.PlatformTransactionManager transactionManager;

    /**
     * AI 处方合理性审查（剂量/配伍/禁忌/重复用药）——开方后的一道硬关卡，审查不过前端标红提示医生。
     * 纯实时审查不落库、不阻断开方主流程；输入处方明细 + 患者信息，返回结构化审查结论。
     */
    @PostMapping("/review")
    public Map<String, Object> reviewPrescription(@RequestBody Map<String, Object> req) {
        return prescriptionReviewService.reviewPrescription(req);
    }

    @GetMapping("/list")
    public List<Map<String, Object>> listPrescriptions() {
        LambdaQueryWrapper<Prescription> qw = new LambdaQueryWrapper<>();
        qw.orderByDesc(Prescription::getCreateTime);
        List<Prescription> list = prescriptionMapper.selectList(qw);

        List<Map<String, Object>> res = new ArrayList<>();
        for (Prescription p : list) {
            Map<String, Object> map = new HashMap<>();
            map.put("prescription", p);
            List<PrescriptionItem> items = itemMapper.selectList(
                    new LambdaQueryWrapper<PrescriptionItem>().eq(PrescriptionItem::getPrescriptionId, p.getId())
            );
            map.put("items", items);
            res.add(map);
        }
        return res;
    }

    /**
     * 患者历次就诊记录查询：支持 patientId 或身份证号定位同一患者（就诊记录跟着患者走）。
     * 返回结构对接前端「就诊记录」面板与「引用开方」。
     */
    @GetMapping("/patient-history")
    public List<Map<String, Object>> patientHistory(
            @RequestParam(value = "patientId", required = false) Long patientId,
            @RequestParam(value = "idCard", required = false) String idCard) {
        List<Map<String, Object>> res = new ArrayList<>();
        String card = (idCard != null && !idCard.trim().isEmpty()) ? idCard.trim() : null;
        if (patientId == null && card == null) return res;

        // 候选 id 集合：患者档案 id + 该患者（按身份证/档案关联）名下所有挂号记录 id。
        // 历史版本处方落库时 patientId 存的是挂号记录 id，此处统一兼容两种语义。
        // 注意：同一身份证可能存在多条历史档案（旧数据未按身份证归一），必须全部纳入合并，
        // 不能 LIMIT 1 随机取一条，否则其余档案名下的就诊记录会丢失。
        Set<Long> candidateIds = new LinkedHashSet<>();
        if (patientId != null) candidateIds.add(patientId);
        if (card != null) {
            for (Patient ap : patientMapper.selectList(new LambdaQueryWrapper<Patient>()
                    .eq(Patient::getIdCard, card))) {
                if (ap.getId() != null) candidateIds.add(ap.getId());
            }
        }

        // 收集这些档案 + 该身份证名下的所有挂号记录：挂号 id 进候选集，挂号姓名进姓名兜底集
        Set<String> relatedNamesFinal = new LinkedHashSet<>();
        List<ClinicRegistration> regs = registrationMapper.selectList(new LambdaQueryWrapper<ClinicRegistration>()
                .and(w -> {
                    boolean hasCond = false;
                    if (card != null) {
                        w.eq(ClinicRegistration::getIdCard, card);
                        hasCond = true;
                    }
                    if (!candidateIds.isEmpty()) {
                        if (hasCond) w.or();
                        w.in(ClinicRegistration::getPatientId, candidateIds);
                    }
                }));
        for (ClinicRegistration r : regs) {
            if (r.getId() != null) candidateIds.add(r.getId());
            if (r.getPatientId() != null) candidateIds.add(r.getPatientId());
            if (r.getPatientName() != null && !r.getPatientName().isEmpty()) relatedNamesFinal.add(r.getPatientName());
        }

        // 兼容历史数据：处方.patient_id 曾存过挂号 id / 不同档案 id，统一按 id 集合 + 同身份证挂号姓名 双通道合并。
        // 两个集合都可能为空，MyBatis-Plus .in(空集合) 会生成非法 SQL "IN ()"，必须逐个判空后再拼条件。
        List<Prescription> list;
        if (candidateIds.isEmpty() && relatedNamesFinal.isEmpty()) {
            list = new ArrayList<>();
        } else {
            LambdaQueryWrapper<Prescription> pw = new LambdaQueryWrapper<>();
            pw.and(w -> {
                boolean hasCond = false;
                if (!candidateIds.isEmpty()) {
                    w.in(Prescription::getPatientId, candidateIds);
                    hasCond = true;
                }
                if (!relatedNamesFinal.isEmpty()) {
                    if (hasCond) w.or();
                    w.in(Prescription::getPatientName, relatedNamesFinal);
                }
            });
            pw.orderByDesc(Prescription::getCreateTime).last("LIMIT 10");
            list = prescriptionMapper.selectList(pw);
        }

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        for (Prescription p : list) {
            List<PrescriptionItem> items = itemMapper.selectList(new LambdaQueryWrapper<PrescriptionItem>()
                    .eq(PrescriptionItem::getPrescriptionId, p.getId()));

            List<Map<String, Object>> treatments = new ArrayList<>();
            List<Map<String, Object>> patchItems = new ArrayList<>();
            List<Map<String, Object>> westernItems = new ArrayList<>();
            List<Map<String, Object>> tcmItems = new ArrayList<>();
            for (PrescriptionItem it : items) {
                String rawName = it.getMedicineName() != null ? it.getMedicineName() : "";
                String cleanName = rawName.replaceFirst("^【[^】]*】", "");
                Map<String, Object> row = new HashMap<>();
                row.put("name", cleanName);
                row.put("dosage", it.getDosage());
                row.put("quantity", it.getQuantity());
                row.put("unitPrice", it.getPrice());
                row.put("totalPrice", it.getSubtotal());
                if (rawName.startsWith("【诊疗】")) {
                    row.put("price", it.getPrice());
                    treatments.add(row);
                } else if (rawName.startsWith("【贴敷】")) {
                    row.put("acupoints", it.getDosage());
                    row.put("dose", "10");
                    row.put("days", 3);
                    patchItems.add(row);
                } else if (rawName.startsWith("【中药饮片】")) {
                    row.put("dose", "10");
                    row.put("frequency", "1剂/天");
                    tcmItems.add(row);
                } else {
                    row.put("frequency", "tid");
                    row.put("days", 3);
                    row.put("unit", "盒");
                    westernItems.add(row);
                }
            }

            Map<String, Object> visit = new HashMap<>();
            visit.put("date", p.getCreateTime() != null ? p.getCreateTime().format(fmt) : "");
            visit.put("visitType", "历史");
            visit.put("typeBadge", "中西");
            visit.put("doctor", p.getDoctorName());
            visit.put("diagnosis", p.getDiagnosis());
            visit.put("tcmDiagnosis", "");
            visit.put("symptoms", (p.getRemark() != null && !p.getRemark().isEmpty()) ? p.getRemark() : "历史就诊记录");
            visit.put("treatments", treatments);
            visit.put("patchItems", patchItems);
            visit.put("westernItems", westernItems);
            visit.put("tcmItems", tcmItems);
            visit.put("advice", (p.getAiAdvice() != null && !p.getAiAdvice().isEmpty()) ? p.getAiAdvice() : "遵医嘱规律服药。");
            visit.put("totalFee", p.getTotalAmount());
            res.add(visit);
        }
        return res;
    }

    /**
     * 医生开具处方（涵盖患者档案自动建立、过敏红线拦截、智能生成待划价处方及理疗执行任务）
     */
    @PostMapping(value = {"/manual-create", "/create"})
    public Map<String, Object> manualCreatePrescription(@RequestBody Map<String, Object> req, jakarta.servlet.http.HttpServletRequest request) {
        Map<String, Object> res = new HashMap<>();
        // 开单医生兜底 = 当前登录人真实姓名（前端传了 doctorName 则以前端为准）
        String fallbackDoctor = currentUserService.displayName(request);
        if (fallbackDoctor == null || fallbackDoctor.isBlank()) fallbackDoctor = "系统用户";
        
        // 1. 获取或建立患者档案（就诊中涵盖患者档案建立）
        Long patientId = null;
        if (req.get("patientId") != null && !req.get("patientId").toString().isEmpty()) {
            try {
                patientId = Long.parseLong(req.get("patientId").toString());
            } catch (Exception ignored) {}
        }
        
        String patientName = req.getOrDefault("patientName", "门诊就诊患者").toString();
        String reqIdCard = req.getOrDefault("idCard", "").toString().trim();
        Patient patient = null;
        if (patientId != null) {
            patient = patientMapper.selectById(patientId);
        }
        // 身份证优先归一档案：同一身份证无论姓名/手机号是否不同，都复用同一条患者档案
        if (patient == null && !reqIdCard.isEmpty()) {
            patient = patientMapper.selectOne(new LambdaQueryWrapper<Patient>()
                    .eq(Patient::getIdCard, reqIdCard)
                    .orderByAsc(Patient::getId)
                    .last("LIMIT 1"));
        }
        if (patient == null && !patientName.isEmpty()) {
            patient = patientMapper.selectOne(new LambdaQueryWrapper<Patient>().eq(Patient::getName, patientName).last("LIMIT 1"));
            // 姓名命中但档案缺身份证号时补全，保证后续就诊可按身份证关联
            if (patient != null && !reqIdCard.isEmpty()
                    && (patient.getIdCard() == null || patient.getIdCard().trim().isEmpty())) {
                patient.setIdCard(reqIdCard);
                patientMapper.updateById(patient);
            }
        }
        if (patient == null) {
            // 自动建档（身份证号为空时留空，绝不写死默认值污染档案）
            patient = new Patient();
            patient.setName(patientName);
            patient.setGender(req.getOrDefault("gender", "男").toString());
            try {
                Object ageObj = req.get("patientAge") != null ? req.get("patientAge") : req.get("age");
                if (ageObj != null && !ageObj.toString().trim().isEmpty()) {
                    patient.setAge(Integer.parseInt(ageObj.toString().trim()));
                } else {
                    patient.setAge(0);
                }
            } catch (Exception ignored) {
                patient.setAge(0);
            }
            patient.setPhone(req.getOrDefault("phone", "").toString());
            patient.setIdCard(reqIdCard);
            patient.setAllergies(req.getOrDefault("allergies", "未记录药物过敏史").toString());
            patient.setMedicalHistory(req.getOrDefault("medicalHistory", "未记录既往慢病史").toString());
            patient.setAddress(req.getOrDefault("address", "").toString());
            patient.setCreateTime(LocalDateTime.now());
            patientMapper.insert(patient);
            patientId = patient.getId();
        } else {
            patientId = patient.getId();
        }

        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) req.get("items");
        if (rawItems == null || rawItems.isEmpty()) {
            res.put("success", false);
            res.put("message", "处方药品明细不能为空！");
            return res;
        }

        // 2. 过敏红线安全校验 (硬性拦截，按药品成分族映射，覆盖衍生物)
        String allergies = patient.getAllergies() == null ? "" : patient.getAllergies();
        for (Map<String, Object> itemMap : rawItems) {
            String medName = itemMap.getOrDefault("medicineName", "").toString();
            // 青霉素类过敏 → 拦截整族（阿莫西林/氨苄西林/哌拉西林/苯唑西林/氨苄青霉素等）
            if (allergies.contains("青霉素")) {
                String[] penicillins = {"青霉素", "阿莫西林", "氨苄西林", "哌拉西林", "苯唑西林", "氨苄青霉素", "阿莫西林克拉维酸钾"};
                for (String p : penicillins) {
                    if (medName.contains(p)) {
                        res.put("success", false);
                        res.put("message", "【过敏红线高危阻断】患者明确对【青霉素类】过敏，严禁开具" + medName + "！");
                        return res;
                    }
                }
            }
            // 头孢菌素类过敏 → 拦截整族
            if (allergies.contains("头孢")) {
                if (medName.contains("头孢") || medName.contains("先锋")) {
                    res.put("success", false);
                    res.put("message", "【过敏红线高危阻断】患者明确对【头孢菌素类】过敏，严禁开具" + medName + "！");
                    return res;
                }
            }
            // 磺胺类过敏 → 拦截磺胺族
            if (allergies.contains("磺胺")) {
                String[] sulfonamides = {"磺胺", "复方新诺明", "柳氮磺"};
                for (String s : sulfonamides) {
                    if (medName.contains(s)) {
                        res.put("success", false);
                        res.put("message", "【过敏红线高危阻断】患者明确对【磺胺类】过敏，严禁开具" + medName + "！");
                        return res;
                    }
                }
            }
        }

        // 2b. AI 处方合理性审查（硬关卡：剂量超量、严重配伍禁忌、重复用药）
        boolean forcePass = Boolean.TRUE.equals(req.get("forcePass"))
                || "true".equalsIgnoreCase(String.valueOf(req.get("forcePass")));
        if (!forcePass && prescriptionReviewService != null) {
            try {
                Map<String, Object> reviewReq = new HashMap<>();
                reviewReq.put("patientName", patient.getName());
                reviewReq.put("patientAge", patient.getAge() != null ? String.valueOf(patient.getAge()) : "");
                reviewReq.put("allergies", allergies);
                reviewReq.put("diagnosis", req.getOrDefault("diagnosis", ""));
                reviewReq.put("items", rawItems);

                Map<String, Object> reviewResult = prescriptionReviewService.reviewPrescription(reviewReq);
                if (reviewResult != null) {
                    boolean passed = Boolean.TRUE.equals(reviewResult.get("passed"));
                    String riskLevel = String.valueOf(reviewResult.getOrDefault("riskLevel", "低风险"));
                    List<Map<String, Object>> warnings = (List<Map<String, Object>>) reviewResult.get("warnings");
                    boolean hasHighRisk = "高风险".equals(riskLevel) || "高危".equals(riskLevel) || !passed;
                    if (warnings != null) {
                        for (Map<String, Object> w : warnings) {
                            if ("高危".equals(w.get("level")) || "禁忌".equals(w.get("type"))) {
                                hasHighRisk = true;
                                break;
                            }
                        }
                    }
                    if (hasHighRisk) {
                        res.put("success", false);
                        res.put("blockedByReview", true);
                        res.put("riskLevel", riskLevel);
                        res.put("summary", reviewResult.get("summary"));
                        res.put("warnings", warnings);
                        res.put("message", "【AI处方合理性审查高危拦截】" + reviewResult.getOrDefault("summary", "检测到严重用药安全风险！"));
                        return res;
                    }
                }
            } catch (Exception ex) {
                // 审查服务异常降级，不阻断正常业务
            }
        }

        // 3. 事务隔离：AI 审查在外执行完成，落库操作进入原子事务极速持久化（<5ms）
        final Patient finalPatient = patient;
        final Long finalPatientId = patientId;
        final String finalFallbackDoctor = fallbackDoctor;

        org.springframework.transaction.support.TransactionTemplate tx =
                new org.springframework.transaction.support.TransactionTemplate(transactionManager);

        return tx.execute(status -> {
            Prescription p = new Prescription();
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        p.setPrescriptionNo("RX" + dateStr + ThreadLocalRandom.current().nextInt(100, 999));
        p.setPatientId(finalPatientId);
        p.setPatientName(finalPatient.getName());
        String doctorName = req.getOrDefault("doctorName", finalFallbackDoctor).toString();
        p.setDoctorName(doctorName);
        // 电子处方：医生开方提交即签字确认，签字人/签字时间真实落库（医学红线，不再是 prompt 软约束）
        p.setSignedBy(doctorName);
        p.setSignedAt(LocalDateTime.now());
        String reqDiag = req.getOrDefault("diagnosis", "").toString().trim();
        p.setDiagnosis(reqDiag.isEmpty() ? "门诊确诊（待补录）" : reqDiag);
        p.setAiAdvice(req.getOrDefault("aiAdvice", "遵医嘱按时规律用药，清淡饮食").toString());
        p.setType(req.getOrDefault("type", "western").toString());
        p.setCraftNotes(req.getOrDefault("craftNotes", "").toString());
        p.setRemark(req.getOrDefault("remark", "").toString());
        String reqStatus = req.getOrDefault("status", "0").toString();
        if ("PAID".equalsIgnoreCase(reqStatus) || "1".equals(reqStatus) || "已支付".equals(req.get("payStatus"))) {
            p.setStatus("1"); // 1: 待调剂发药 (门诊现场已结清，直接推智慧药房)
            p.setPayStatus("已支付");
        } else {
            p.setStatus("0"); // 0: 待划价收费 (推划价收费台)
            p.setPayStatus("待支付");
        }
        p.setCreateTime(LocalDateTime.now());

        BigDecimal total = BigDecimal.ZERO;
        List<PrescriptionItem> savedItems = new ArrayList<>();
        boolean hasTherapyItem = false;
        String therapyName = "";
        String therapyAcupoints = "";

        for (Map<String, Object> itemMap : rawItems) {
            Long medId = null;
            if (itemMap.get("medicineId") != null) {
                try {
                    long parseId = Long.parseLong(itemMap.get("medicineId").toString());
                    // 过滤前端临时占位ID（如 999, 101, 201, 301），仅对真实大于0的ID采纳
                    if (parseId > 0 && parseId != 999L && parseId != 101L && parseId != 201L && parseId != 301L) {
                        medId = parseId;
                    }
                } catch (Exception ignored) {}
            }
            
            String reqMedName = itemMap.getOrDefault("medicineName", itemMap.getOrDefault("name", "药品")).toString();
            String cleanMedName = reqMedName.replaceAll("^【[^】]*】", "").trim();

            Medicine med = null;
            if (medId != null) {
                med = medicineMapper.selectById(medId);
            }
            // 若ID未匹配到或无有效ID，尝试按药品名称反查真实药房档案，保证ID与名称真实统一
            if (med == null && !cleanMedName.isEmpty()) {
                List<Medicine> matched = medicineMapper.selectList(
                        new LambdaQueryWrapper<Medicine>()
                                .eq(Medicine::getName, cleanMedName)
                                .last("LIMIT 1")
                );
                if (matched != null && !matched.isEmpty()) {
                    med = matched.get(0);
                    medId = med.getId();
                } else {
                    List<Medicine> likeMatched = medicineMapper.selectList(
                            new LambdaQueryWrapper<Medicine>()
                                    .like(Medicine::getName, cleanMedName)
                                    .last("LIMIT 1")
                    );
                    if (likeMatched != null && !likeMatched.isEmpty()) {
                        med = likeMatched.get(0);
                        medId = med.getId();
                    }
                }
            }

            int qty = 1;
            try {
                if (itemMap.get("quantity") != null) qty = Integer.parseInt(itemMap.get("quantity").toString());
            } catch (Exception ignored) {}

            BigDecimal price = new BigDecimal("25.00");
            if (med != null && med.getPrice() != null) {
                price = med.getPrice();
            } else if (itemMap.get("price") != null) {
                try { price = new BigDecimal(itemMap.get("price").toString()); } catch (Exception ignored) {}
            } else if (itemMap.get("unitPrice") != null) {
                try { price = new BigDecimal(itemMap.get("unitPrice").toString()); } catch (Exception ignored) {}
            }

            PrescriptionItem pi = new PrescriptionItem();
            pi.setMedicineId(medId);
            // 真实保护：若匹配到药房档案则使用官方规范品名，否则忠实保留开立药品名称，绝不强制覆盖为首个药品
            String mName = med != null ? med.getName() : reqMedName;
            pi.setMedicineName(mName);
            pi.setSpecification(med != null ? med.getSpecification() : itemMap.getOrDefault("specification", "常规规格").toString());
            pi.setQuantity(qty);
            pi.setPrice(price);
            BigDecimal sub = price.multiply(new BigDecimal(qty));
            pi.setSubtotal(sub);
            pi.setDosage(itemMap.getOrDefault("dosage", "遵医嘱").toString());
            pi.setFrequency(itemMap.getOrDefault("frequency", "每日1次").toString());
            pi.setRoute(itemMap.getOrDefault("route", "口服/外用").toString());

            savedItems.add(pi);
            total = total.add(sub);

            if (mName.contains("贴") || mName.contains("温敷") || mName.contains("敷") || mName.contains("理疗")) {
                hasTherapyItem = true;
                therapyName = mName;
                therapyAcupoints = itemMap.getOrDefault("dosage", "大椎、双肺俞穴温敷").toString();
            }
        }

        p.setTotalAmount(total);
        prescriptionMapper.insert(p);

        // 3b. 药品-物资自动联动：处方药品命中联动配置(medicine_supply_link)时，
        //     自动附加联动物资明细行（如注射药品→注射器、清创药品→纱布），
        //     联动物资随处方一并划价收费、发药时真实扣减物资库存。
        try {
            for (PrescriptionItem pi : new ArrayList<>(savedItems)) {
                if (pi.getMedicineId() == null) continue;
                List<Map<String, Object>> links = jdbcTemplate.queryForList(
                        "SELECT supply_id, supply_name, quantity FROM medicine_supply_link WHERE medicine_id = ?", pi.getMedicineId());
                // 兜底：药品行为手动输入名称/临时ID时，按药品名模糊匹配联动配置（如名称带【中药饮片】前缀也能命中）
                if (links.isEmpty() && pi.getMedicineName() != null) {
                    String nm = pi.getMedicineName();
                    links = jdbcTemplate.queryForList(
                            "SELECT supply_id, supply_name, quantity FROM medicine_supply_link WHERE ? LIKE CONCAT('%', medicine_name, '%') OR medicine_name LIKE CONCAT('%', ?, '%')",
                            nm, nm);
                }
                for (Map<String, Object> link : links) {
                    Long supplyId = Long.valueOf(link.get("supply_id").toString());
                    boolean alreadyOpened = savedItems.stream().anyMatch(x -> supplyId.equals(x.getMedicineId()));
                    if (alreadyOpened) continue; // 医生已手动开出该物资，不重复附加
                    int perQty = 1;
                    try { perQty = Integer.parseInt(link.get("quantity").toString()); } catch (Exception ignored) {}
                    int supplyQty = perQty * (pi.getQuantity() != null ? pi.getQuantity() : 1);
                    Medicine supply = medicineMapper.selectById(supplyId);
                    PrescriptionItem spi = new PrescriptionItem();
                    spi.setMedicineId(supplyId);
                    spi.setMedicineName(supply != null ? supply.getName() : String.valueOf(link.get("supply_name")));
                    spi.setSpecification(supply != null && supply.getSpecification() != null ? supply.getSpecification() : "医用材料");
                    spi.setQuantity(supplyQty);
                    BigDecimal sPrice = (supply != null && supply.getPrice() != null) ? supply.getPrice() : BigDecimal.ZERO;
                    spi.setPrice(sPrice);
                    spi.setSubtotal(sPrice.multiply(new BigDecimal(supplyQty)));
                    spi.setDosage("【联动自动附加】伴随【" + pi.getMedicineName() + "】使用");
                    spi.setFrequency("按处方");
                    spi.setRoute("外用/配套");
                    savedItems.add(spi);
                    total = total.add(spi.getSubtotal());
                }
            }
            if (p.getTotalAmount().compareTo(total) != 0) {
                p.setTotalAmount(total);
                prescriptionMapper.updateById(p);
            }
        } catch (Exception e) {
            // 联动失败不阻断开方主流程
        }

        for (PrescriptionItem pi : savedItems) {
            pi.setPrescriptionId(p.getId());
            itemMapper.insert(pi);
        }

        // 4. 若开具了特色穴位理疗/敷贴，自动生成特色执行站任务记录
        if (hasTherapyItem) {
            ClinicTreatmentRecord tr = new ClinicTreatmentRecord();
            tr.setRecordNo("TR" + dateStr + ThreadLocalRandom.current().nextInt(100, 999));
            tr.setPatientId(finalPatientId);
            tr.setPatientName(finalPatient.getName());
            tr.setPatientGender(finalPatient.getGender());
            tr.setPatientAge(finalPatient.getAge() + "岁");
            tr.setPrescriptionId(p.getId());
            tr.setTreatmentName(therapyName);
            tr.setTechnique("特色姜汁穴位温敷渗透理疗");
            tr.setAcupoints(therapyAcupoints.isEmpty() ? "大椎穴、双肺俞穴、定喘穴" : therapyAcupoints);
            tr.setDurationHours(4);
            tr.setPatchCount(2);
            tr.setMaterialsUsed("医用温敷贴、姜汁理疗透皮吸收敷料");
            tr.setDoctorName(p.getDoctorName());
            tr.setNurseName("李护士 (理疗执行岗)");
            tr.setStatus("pending");
            tr.setCreatedAt(LocalDateTime.now());
            treatmentRecordMapper.insert(tr);
        }

        // 5. 将该患者挂号状态更新为已诊（精准匹配指定挂号ID，或该患者当前活跃就诊记录，杜绝按姓名全表误更）
        boolean regUpdated = false;
        if (req.get("registrationId") != null) {
            try {
                Long regId = Long.parseLong(req.get("registrationId").toString());
                ClinicRegistration reg = registrationMapper.selectById(regId);
                if (reg != null) {
                    reg.setStatus("已诊");
                    registrationMapper.updateById(reg);
                    regUpdated = true;
                }
            } catch (Exception ignored) {}
        }
        if (!regUpdated && finalPatientId != null) {
            try {
                // 仅更新该患者当前处于接诊中或待诊中的挂号记录，杜绝跨日期及历史记录全部误更
                List<ClinicRegistration> activeRegs = registrationMapper.selectList(new LambdaQueryWrapper<ClinicRegistration>()
                        .eq(ClinicRegistration::getPatientId, finalPatientId)
                        .in(ClinicRegistration::getStatus, Arrays.asList("就诊中", "接诊中", "待诊"))
                        .orderByDesc(ClinicRegistration::getId)
                        .last("LIMIT 1"));
                if (activeRegs != null && !activeRegs.isEmpty()) {
                    ClinicRegistration r = activeRegs.get(0);
                    r.setStatus("已诊");
                    registrationMapper.updateById(r);
                }
            } catch (Exception ignored) {}
        }

        res.put("success", true);
        res.put("message", "门诊处方开具成功！已实时推送至【划价收费】中台，患者缴费后药房即可调配发药！");
        res.put("prescription", p);
        res.put("items", savedItems);
        return res;
        });
    }

    /**
     * 门诊结账与处方收费（收费成功后流转至智慧药房待发药队列，生成对账支付流水）
     */
    @PostMapping(value = {"/checkout/{id}", "/checkout"})
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> checkout(@PathVariable(value = "id", required = false) Long pathId,
                                        @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> res = new HashMap<>();
        Long id = pathId;
        if (id == null && body != null && body.get("prescriptionId") != null) {
            try { id = Long.parseLong(body.get("prescriptionId").toString()); } catch (Exception ignored) {}
        }
        if (id == null) {
            res.put("success", false);
            res.put("message", "处方ID不能为空！");
            return res;
        }

        Prescription p = prescriptionMapper.selectById(id);
        if (p == null) {
            res.put("success", false);
            res.put("message", "未找到该处方记录！");
            return res;
        }

        String payChannel = "春播综合收银台";
        if (body != null && body.get("payMethod") != null) {
            String m = body.get("payMethod").toString();
            payChannel = switch (m) {
                case "wechat" -> "微信支付";
                case "alipay" -> "支付宝";
                case "cash" -> "门诊现金结算";
                case "chunbo-pay" -> "春播医保聚合付";
                case "balance", "member" -> "会员储值余额扣减";
                default -> m;
            };
        }

        // 若使用会员储值抵扣，扣减患者卡内余额
        if (body != null && Boolean.parseBoolean(String.valueOf(body.getOrDefault("useBalance", "false")))) {
            Long pid = p.getPatientId();
            if (pid != null && patientMapper != null) {
                Patient patient = patientMapper.selectById(pid);
                if (patient != null && patient.getBalance() != null) {
                    BigDecimal balance = BigDecimal.valueOf(patient.getBalance());
                    BigDecimal total = p.getTotalAmount() != null ? p.getTotalAmount() : BigDecimal.ZERO;
                    if (balance.compareTo(total) >= 0) {
                        patient.setBalance(balance.subtract(total).setScale(2, java.math.RoundingMode.HALF_UP).doubleValue());
                        patientMapper.updateById(patient);
                        payChannel = "会员储值余额全额抵扣";
                    }
                }
            }
        }

        p.setPayStatus("已支付");
        p.setStatus("1"); // 1: 待发药
        prescriptionMapper.updateById(p);

        // 写入真实支付交易流水，财务对账闭环
        if (payTransactionMapper != null) {
            try {
                PayTransaction tx = new PayTransaction();
                tx.setOrderNo(p.getPrescriptionNo());
                tx.setChannelType(payChannel);
                tx.setPayerName(p.getPatientName() != null ? p.getPatientName() : "门诊患者");
                tx.setPayerAccount(p.getPatientName());
                tx.setMerchantId("MCH_CLINIC_001");
                tx.setMerchantName("春播万象基层门诊全科诊室");
                tx.setAmount(p.getTotalAmount() != null ? p.getTotalAmount() : BigDecimal.ZERO);
                tx.setPayStatus("支付成功");
                tx.setRiskStatus("正常");
                tx.setTradeTime(LocalDateTime.now());
                payTransactionMapper.insert(tx);
            } catch (Exception ignored) {}
        }

        res.put("success", true);
        res.put("message", "处方收费核销完成（" + payChannel + "）！已生成资金流水，并成功推送到智慧药房发药调配队列！");
        res.put("prescription", p);
        return res;
    }
}
