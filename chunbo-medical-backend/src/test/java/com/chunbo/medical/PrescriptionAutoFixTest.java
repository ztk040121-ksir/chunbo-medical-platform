package com.chunbo.medical;

import com.chunbo.medical.service.PrescriptionReviewService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PrescriptionAutoFixTest {

    @InjectMocks
    private PrescriptionReviewService reviewService;

    @Mock
    private com.chunbo.medical.service.AiModelConfigService aiConfigService;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void testTcm18FanAutoFix_WutouBanxia() {
        Map<String, Object> req = new HashMap<>();
        req.put("patientName", "张三");
        req.put("patientAge", "45");
        req.put("allergies", "无");
        req.put("diagnosis", "寒湿痹痛");

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("medicineName", "川乌", "dosage", "6g", "quantity", 1));
        items.add(Map.of("medicineName", "半夏", "dosage", "10g", "quantity", 1));
        req.put("items", items);

        Map<String, Object> res = reviewService.reviewPrescription(req);
        Assertions.assertFalse((Boolean) res.get("passed"));
        Assertions.assertEquals("高风险", res.get("riskLevel"));

        Map<String, Object> autoFix = (Map<String, Object>) res.get("autoFix");
        Assertions.assertNotNull(autoFix);
        Assertions.assertTrue((Boolean) autoFix.get("canAutoFix"));

        List<Map<String, Object>> actions = (List<Map<String, Object>>) autoFix.get("actions");
        Assertions.assertFalse(actions.isEmpty());
        Map<String, Object> firstAction = actions.get(0);
        Assertions.assertEquals("REPLACE_MEDICINE", firstAction.get("type"));
        Assertions.assertEquals("半夏", firstAction.get("targetName"));
        Assertions.assertEquals("胆南星", firstAction.get("replaceName"));
    }

    @Test
    public void testTcm18FanAutoFix_GancaoHaizao() {
        Map<String, Object> req = new HashMap<>();
        req.put("patientName", "李四");
        req.put("patientAge", "50");
        req.put("allergies", "无");
        req.put("diagnosis", "瘰疬痰核");

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("medicineName", "炙甘草", "dosage", "6g", "quantity", 1));
        items.add(Map.of("medicineName", "海藻", "dosage", "10g", "quantity", 1));
        req.put("items", items);

        Map<String, Object> res = reviewService.reviewPrescription(req);
        Assertions.assertFalse((Boolean) res.get("passed"));

        Map<String, Object> autoFix = (Map<String, Object>) res.get("autoFix");
        Assertions.assertTrue((Boolean) autoFix.get("canAutoFix"));
        List<Map<String, Object>> actions = (List<Map<String, Object>>) autoFix.get("actions");
        Map<String, Object> act = actions.get(0);
        Assertions.assertEquals("海藻", act.get("targetName"));
        Assertions.assertEquals("昆布", act.get("replaceName"));
    }

    @Test
    public void testChildQuinoloneAutoFix() {
        Map<String, Object> req = new HashMap<>();
        req.put("patientName", "王小宝");
        req.put("patientAge", "8岁");
        req.put("allergies", "无");
        req.put("diagnosis", "急性扁桃体炎");

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("medicineName", "左氧氟沙星片", "dosage", "0.5g", "quantity", 1));
        req.put("items", items);

        Map<String, Object> res = reviewService.reviewPrescription(req);
        Assertions.assertFalse((Boolean) res.get("passed"));

        Map<String, Object> autoFix = (Map<String, Object>) res.get("autoFix");
        Assertions.assertTrue((Boolean) autoFix.get("canAutoFix"));
        List<Map<String, Object>> actions = (List<Map<String, Object>>) autoFix.get("actions");
        Map<String, Object> act = actions.get(0);
        Assertions.assertEquals("REPLACE_MEDICINE", act.get("type"));
        Assertions.assertTrue(act.get("targetName").toString().contains("左氧氟沙星"));
        Assertions.assertEquals("头孢克洛干混悬剂", act.get("replaceName"));
    }

    @Test
    public void testNsaidDuplicateAutoFix() {
        Map<String, Object> req = new HashMap<>();
        req.put("patientName", "赵六");
        req.put("patientAge", "32");
        req.put("allergies", "无");
        req.put("diagnosis", "急性上呼吸道感染高热");

        List<Map<String, Object>> items = new ArrayList<>();
        items.add(Map.of("medicineName", "感冒灵颗粒", "dosage", "1袋", "quantity", 1));
        items.add(Map.of("medicineName", "布洛芬缓释胶囊", "dosage", "0.3g", "quantity", 1));
        req.put("items", items);

        Map<String, Object> res = reviewService.reviewPrescription(req);
        Assertions.assertFalse((Boolean) res.get("passed"));

        Map<String, Object> autoFix = (Map<String, Object>) res.get("autoFix");
        Assertions.assertTrue((Boolean) autoFix.get("canAutoFix"));
        List<Map<String, Object>> actions = (List<Map<String, Object>>) autoFix.get("actions");
        Map<String, Object> act = actions.get(0);
        Assertions.assertEquals("REMOVE_MEDICINE", act.get("type"));
        Assertions.assertTrue(act.get("targetName").toString().contains("布洛芬"));
    }
}
