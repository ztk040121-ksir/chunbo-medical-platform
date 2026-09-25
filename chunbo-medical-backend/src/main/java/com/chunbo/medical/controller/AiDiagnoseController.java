package com.chunbo.medical.controller;

import com.chunbo.medical.service.AiModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 医生工作站 AI 临床诊断与智能医嘱生成控制器
 * 提供真实基于 Spring AI (DeepSeek / 通用大模型) 的临床决策支持
 */
@RestController
public class AiDiagnoseController {

    private static final Logger log = LoggerFactory.getLogger(AiDiagnoseController.class);

    @Autowired(required = false)
    private AiModelConfigService aiConfigService;

    /**
     * 医生工作站：根据患者主诉、西医诊断、中医辨证生成个性化临床医嘱与居家生活调护建议
     * POST /api/ai/diagnose
     */
    @PostMapping("/api/ai/diagnose")
    public Map<String, Object> generateDiagnoseAdvice(@RequestBody Map<String, Object> req) {
        String symptoms = req.getOrDefault("symptoms", "一般门诊不适").toString().trim();
        String history = req.getOrDefault("history", "").toString().trim();
        String diagnosis = req.getOrDefault("diagnosis", "").toString().trim();

        log.info("【医生工作站 AI 医嘱生成】主诉: {}, 诊断提示: {}", symptoms, history);

        Map<String, Object> result = new HashMap<>();
        String advice = null;

        // 优先使用 Spring AI (DeepSeek / 配置的大模型) 真实生成临床医嘱
        if (aiConfigService != null && aiConfigService.getBareChatClient() != null && !aiConfigService.isMockEnabled()) {
            try {
                String systemPrompt = """
                        你是一名精通中西医结合的资深主任医师兼全科临床专家。
                        请根据患者的主诉症状、病史与诊断证型，为医生开具的处方快速拟定3条临床医嘱与生活注意事项。
                        要求：
                        1. 严格针对该病种（如呼吸道、骨关节、脾胃消化、儿科外治等）给出个性化指导，严禁千篇一律的套话；
                        2. 语言精炼、严谨、富有人文关怀，便于直接粘贴至门诊病历医嘱栏；
                        3. 格式按编号 1. 2. 3. 输出，严格控制在3条以内（150字以内）。
                        """;

                String userPrompt = String.format("患者主诉：%s\n诊断与证型需求：%s\n%s\n请生成3条针对性临床医嘱及生活调护事项：",
                        symptoms, history, diagnosis.isEmpty() ? "" : "临床确诊：" + diagnosis);

                advice = aiConfigService.getBareChatClient()
                        .prompt()
                        .system(systemPrompt)
                        .user(userPrompt)
                        .call()
                        .content();

                if (advice != null) {
                    advice = advice.trim();
                }
            } catch (Exception e) {
                log.warn("Spring AI 医嘱大模型调用异常，转入对症医学规则库: {}", e.getMessage());
            }
        }

        // 离线/规则对症兜底（根据病种智能差异化匹配，杜绝僵死文本）
        if (advice == null || advice.isBlank()) {
            String combined = (symptoms + " " + history + " " + diagnosis).toLowerCase();
            if (combined.contains("痛") || combined.contains("痹") || combined.contains("腰") || combined.contains("关节") || combined.contains("骨")) {
                advice = "1. 穴位贴敷处避风防寒，每日贴敷时间控制在4~6小时，若局部红痒灼热请及时揭除；\n"
                       + "2. 避免提重物、过度弯腰与久坐久站，晨起可进行关节局部温和伸展活动；\n"
                       + "3. 忌食生冷油腻及辛辣发物，保证患处局部保暖，症状持续不减请随诊。";
            } else if (combined.contains("咳") || combined.contains("喘") || combined.contains("感冒") || combined.contains("呼吸") || combined.contains("咽")) {
                advice = "1. 遵医嘱规范服用止咳化痰及解表药物，温开水频服（每日不低于1500ml）；\n"
                       + "2. 严格忌食辛辣刺激、冷饮海鲜及甜腻厚味，避免冷空气直接刺激呼吸道；\n"
                       + "3. 保持室内空气流通湿润，早晚防寒保暖，若出现持续高热或痰中带血立即复诊。";
            } else if (combined.contains("胃") || combined.contains("腹") || combined.contains("泄") || combined.contains("吐") || combined.contains("消化")) {
                advice = "1. 规律三餐，细嚼慢咽，严格遵循清淡易消化温软饮食，忌暴饮暴食；\n"
                       + "2. 腹泻呕吐期间注意少量多次补水防脱水，忌食生冷油炸、浓茶及烈酒；\n"
                       + "3. 饭后避免立即平卧，顺时针轻揉腹部助运化，若腹痛剧烈或大便带血随诊。";
            } else if (combined.contains("儿") || combined.contains("小儿") || combined.contains("积食")) {
                advice = "1. 小儿皮肤娇嫩，穴位贴敷时间不超过2~4小时，家长需严密观察贴敷局部皮损情况；\n"
                       + "2. 饮食以米粥等易消化辅食为主，严格控制零食甜品与生冷瓜果；\n"
                       + "3. 遵医嘱按时足量服药，保证充足睡眠，体温异常或精神萎靡随时就医。";
            } else {
                advice = "1. 遵医嘱按时规范服药与外治，出现任何皮疹、恶心等不适及时停药复诊；\n"
                       + "2. 规律作息，避免劳累熬夜，保持情志舒畅与适度活动；\n"
                       + "3. 科学饮食调理，清淡少盐，禁烟限酒，3日内病情发生变化随时随诊。";
            }
        }

        result.put("lifestyleAdvice", advice);
        result.put("medicalAdvice", advice);
        result.put("status", "success");
        return result;
    }
}
