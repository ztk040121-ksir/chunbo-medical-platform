package com.chunbo.medical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.ClinicRegistration;
import com.chunbo.medical.entity.Prescription;
import com.chunbo.medical.entity.PrescriptionItem;
import com.chunbo.medical.mapper.ClinicRegistrationMapper;
import com.chunbo.medical.mapper.PrescriptionItemMapper;
import com.chunbo.medical.mapper.PrescriptionMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 运营周报服务：每周自动汇总营收/患者/药品动销，由 LLM 生成自然语言周报推给管理员。
 * 遵循「能用 LLM 就交给 LLM」：数据只负责真实汇总（MySQL 穿透），解读与成文全部交给大模型。
 */
@Service
public class WeeklyReportService {

    @Autowired
    private AiModelConfigService aiConfigService;

    @Autowired
    private ClinicRegistrationMapper registrationMapper;

    @Autowired
    private PrescriptionMapper prescriptionMapper;

    @Autowired
    private PrescriptionItemMapper itemMapper;

    /** 生成最近 7 天运营周报，返回 { weekRange, data, report, aiAvailable } */
    public Map<String, Object> generateWeeklyReport() {
        Map<String, Object> res = new HashMap<>();

        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(6); // 近 7 天（含今天）
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = today.atTime(LocalTime.MAX);

        // 1. 挂号人数
        long regCount = registrationMapper.selectCount(new LambdaQueryWrapper<ClinicRegistration>()
                .ge(ClinicRegistration::getCreateTime, start)
                .le(ClinicRegistration::getCreateTime, end));

        // 2. 处方数 + 营收
        List<Prescription> rxs = prescriptionMapper.selectList(new LambdaQueryWrapper<Prescription>()
                .ge(Prescription::getCreateTime, start)
                .le(Prescription::getCreateTime, end));
        long rxCount = rxs.size();
        BigDecimal revenue = BigDecimal.ZERO;
        for (Prescription rx : rxs) {
            if (rx.getTotalAmount() != null) revenue = revenue.add(rx.getTotalAmount());
        }

        // 3. 药品动销 top 5（按处方明细数量聚合）
        List<Long> rxIds = rxs.stream().map(Prescription::getId).collect(Collectors.toList());
        Map<String, Integer> medCount = new LinkedHashMap<>();
        if (!rxIds.isEmpty()) {
            List<PrescriptionItem> items = itemMapper.selectList(new LambdaQueryWrapper<PrescriptionItem>()
                    .in(PrescriptionItem::getPrescriptionId, rxIds));
            for (PrescriptionItem it : items) {
                String name = it.getMedicineName() == null ? "" : it.getMedicineName().replaceFirst("^【[^】]*】", "");
                if (name.isBlank()) continue;
                int qty = it.getQuantity() == null ? 1 : it.getQuantity();
                medCount.merge(name, qty, Integer::sum);
            }
        }
        List<Map<String, Object>> topMeds = medCount.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", e.getKey());
                    m.put("quantity", e.getValue());
                    return m;
                })
                .collect(Collectors.toList());

        // 汇总数据
        Map<String, Object> data = new HashMap<>();
        data.put("weekStart", startDate.toString());
        data.put("weekEnd", today.toString());
        data.put("registrations", regCount);
        data.put("prescriptions", rxCount);
        data.put("revenue", revenue);
        data.put("topMedicines", topMeds);
        res.put("data", data);

        // 4. LLM 生成周报
        ChatClient client = aiConfigService.getBareChatClient();
        if (client == null) {
            res.put("aiAvailable", false);
            res.put("report", "");
            return res;
        }

        try {
            String userPrompt = "请根据以下春播万象基层诊所最近 7 天的真实运营数据，撰写一份简洁专业的运营周报（自然语言，Markdown 排版）。\n"
                    + "时间范围：" + startDate + " 至 " + today + "\n"
                    + "门诊接诊人次：" + regCount + "\n"
                    + "开出处方数：" + rxCount + "\n"
                    + "门诊营收合计：¥" + revenue + "\n"
                    + "药品动销 TOP：" + (topMeds.isEmpty() ? "无" : topMeds.stream()
                        .map(m -> m.get("name") + "(" + m.get("quantity") + ")" ).collect(Collectors.joining("、"))) + "\n\n"
                    + "要求：① 简要概括本周经营概况；② 指出 1-3 个值得关注的数据亮点或异常；③ 给出下周经营建议。"
                    + "直接输出周报正文，不要输出 JSON、不要重复题目。";

            String report = client.prompt()
                    .system("你是基层医疗机构的运营分析助手，善于从经营数据中提炼洞察，语气专业、客观、务实。")
                    .user(userPrompt)
                    .call()
                    .content();

            res.put("aiAvailable", true);
            res.put("report", report == null ? "" : report.trim());
        } catch (Exception e) {
            System.err.println("AI 周报生成异常: " + e.getMessage());
            res.put("aiAvailable", false);
            res.put("report", "");
        }
        return res;
    }
}
