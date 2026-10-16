package com.chunbo.medical.prompt;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 系统提示词统一配置与加载管理器
 * 
 * 核心机制：
 * 1. 启动时自动从 classpath:prompts/*.txt 读取所有独立文本提示词；
 * 2. 内存线程安全缓存（ConcurrentHashMap），支持毫秒级检索；
 * 3. 支持 reload() 动态热更新提示词；
 * 4. 提供静态与依赖注入双重访问方式，方便各业务智能体随时引用。
 */
@Component
public class PromptConfig {

    private static final Logger log = LoggerFactory.getLogger(PromptConfig.class);

    private static PromptConfig INSTANCE;

    private final Map<String, String> promptMap = new ConcurrentHashMap<>();

    public PromptConfig() {
        INSTANCE = this;
    }

    @PostConstruct
    public void init() {
        reload();
    }

    /**
     * 加载/热重载 prompts 目录下的所有 .txt 提示词文件
     */
    public synchronized void reload() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:prompts/*.txt");
            int loaded = 0;
            for (Resource r : resources) {
                String filename = r.getFilename();
                if (filename == null || !filename.endsWith(".txt")) {
                    continue;
                }
                String key = filename.substring(0, filename.length() - 4); // 去除 .txt 后缀
                try (InputStream in = r.getInputStream()) {
                    String content = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
                    promptMap.put(key, content);
                    loaded++;
                } catch (Exception ex) {
                    log.error("[PromptConfig] 加载提示词文件 {} 失败", filename, ex);
                }
            }
            log.info("[PromptConfig] 提示词加载完成，共载入 {} 个 .txt 提示词文件", loaded);
        } catch (Exception e) {
            log.error("[PromptConfig] 扫描 prompts 目录异常", e);
        }
    }

    /**
     * 获取指定 key 的提示词内容（key 为去除 .txt 后的文件名，如 "oa_route"）
     */
    public String getPrompt(String key) {
        if (key == null) return "";
        return promptMap.getOrDefault(key.trim(), "");
    }

    /**
     * 获取指定 key 的提示词并使用 String.format 填充参数
     */
    public String format(String key, Object... args) {
        String template = getPrompt(key);
        if (template.isEmpty()) return "";
        try {
            return String.format(template, args);
        } catch (Exception e) {
            log.warn("[PromptConfig] 格式化提示词 {} 失败: {}", key, e.getMessage());
            return template;
        }
    }

    // ==================== 静态便捷访问 ====================

    public static String get(String key) {
        if (INSTANCE != null) {
            return INSTANCE.getPrompt(key);
        }
        return "";
    }

    public static String formatPrompt(String key, Object... args) {
        if (INSTANCE != null) {
            return INSTANCE.format(key, args);
        }
        return "";
    }

    // ==================== 常用提示词便捷 Getter ====================

    public String getOaRoutePrompt() {
        return getPrompt("oa_route");
    }

    public String getMedRoutePrompt() {
        return getPrompt("med_route");
    }

    public String getMallRoutePrompt() {
        return getPrompt("mall_route");
    }

    public String getMedDiagnosePrompt() {
        return getPrompt("med_diagnose");
    }

    public String getMedVisualDiagnosisPrompt() {
        return getPrompt("med_visual_diagnosis");
    }

    public String getPreConsultTriagePrompt() {
        return getPrompt("pre_consult_triage");
    }

    public String getPreConsultExtractPrompt() {
        return getPrompt("pre_consult_extract");
    }

    public String getOaSalaryPrompt() {
        return getPrompt("oa_salary");
    }

    public String getOaOrderPrompt() {
        return getPrompt("oa_order");
    }

    public String getOaProductPrompt() {
        return getPrompt("oa_product");
    }

    public String getOaMallUserPrompt() {
        return getPrompt("oa_mall_user");
    }

    public String getOaInventoryPrompt() {
        return getPrompt("oa_inventory");
    }

    public String getOaAnalyticsPrompt() {
        return getPrompt("oa_analytics");
    }

    public String getOaApprovalPrompt() {
        return getPrompt("oa_approval");
    }

    public String getOaPlasterPrompt() {
        return getPrompt("oa_plaster");
    }

    public String getOaSchedulePrompt() {
        return getPrompt("oa_schedule");
    }

    public String getOaReActPrompt() {
        return getPrompt("oa_react");
    }

    public String getMallRecommendPrompt() {
        return getPrompt("mall_recommend");
    }

    public String getMallOrderPrompt() {
        return getPrompt("mall_order");
    }

    public String getMallShippingPrompt() {
        return getPrompt("mall_shipping");
    }

    public String getMallGuardPrompt() {
        return getPrompt("mall_guard");
    }

    public String getMallConsultPrompt() {
        return getPrompt("mall_consult");
    }

    public String getMallGeneralPrompt() {
        return getPrompt("mall_general");
    }

    public String getMallPolicyPrompt() {
        return getPrompt("mall_policy");
    }

    public String getMallProductImagePrompt() {
        return getPrompt("mall_product_image");
    }

    public String getChatSessionTitlePrompt() {
        return getPrompt("chat_session_title");
    }

    public String getChatTextPrompt() {
        return getPrompt("chat_text");
    }

    public String getPayAuditPrompt() {
        return getPrompt("pay_audit");
    }

    public String getPrescriptionReviewPrompt() {
        return getPrompt("prescription_review");
    }
}
