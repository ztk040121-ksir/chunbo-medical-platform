package com.chunbo.medical.rag;

import com.chunbo.medical.agent.react.ReActEngine;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

public class HybridRagAndReActTest {

    @Test
    @DisplayName("测试 BM25 稀疏精确检索器对生僻字、方剂名与生化指标缩写(HbA1c/eGFR)的高精度召回")
    void testBm25Retriever() {
        Bm25Retriever retriever = new Bm25Retriever(1.5, 0.75);

        List<Map.Entry<String, String>> docs = List.of(
                Map.entry("2型糖尿病用药指南", "2型糖尿病一线首选二甲双胍，糖化血红蛋白 HbA1c > 7.0% 时联合用药，肾功能 eGFR < 45 ml/min 慎用或减量。"),
                Map.entry("高血压基层用药指南", "高血压一线使用钙通道阻滞剂苯磺酸氨氯地平，监测踝部水肿与心率，不可突然停药。"),
                Map.entry("中药配伍禁忌十八反", "十八反歌诀：半蒌贝蔹芨攻乌，乌头反半夏、瓜蒌、贝母、白蔹、白芨；藻戟遂芫俱战草。")
        );

        retriever.buildIndex(docs);

        // 1. 验证生化指标缩写精确匹配
        List<Bm25Retriever.Bm25Hit> hits1 = retriever.search("eGFR 45 二甲双胍", 2);
        Assertions.assertFalse(hits1.isEmpty());
        Assertions.assertEquals("2型糖尿病用药指南", hits1.get(0).docId);

        // 2. 验证中药配伍生僻词精确匹配
        List<Bm25Retriever.Bm25Hit> hits2 = retriever.search("乌头 半夏 十八反", 2);
        Assertions.assertFalse(hits2.isEmpty());
        Assertions.assertEquals("中药配伍禁忌十八反", hits2.get(0).docId);
    }

    @Test
    @DisplayName("测试 MedicalReranker 混合重排打分加成与排序合理性")
    void testMedicalReranker() {
        MedicalReranker reranker = new MedicalReranker();

        MedicalReranker.CandidateItem item1 = new MedicalReranker.CandidateItem(
                "2型糖尿病一线指南",
                "二甲双胍一线首选，HbA1c达标率高",
                0.80, // 向量分
                0.90  // BM25分
        );

        MedicalReranker.CandidateItem item2 = new MedicalReranker.CandidateItem(
                "健康饮食宣教",
                "多吃蔬菜粗粮有助健康",
                0.85, // 向量语义分稍高但缺乏核心临床关键词
                0.10  // BM25分很低
        );

        List<MedicalReranker.CandidateItem> ranked = reranker.rerank(
                "2型糖尿病 二甲双胍 HbA1c",
                List.of(item1, item2),
                0.70,
                0.30,
                2
        );

        Assertions.assertEquals(2, ranked.size());
        // 经过 Cross-Encoder 医疗专有名词与词项覆盖度加成后，item1 应当强势重排为第一名
        Assertions.assertEquals("2型糖尿病一线指南", ranked.get(0).title);
        Assertions.assertTrue(ranked.get(0).finalScore > ranked.get(1).finalScore);
    }

    @Test
    @DisplayName("测试 ReActEngine 复合链式多步任务意图识别判定")
    void testReActEngineCompositeDetection() {
        ReActEngine engine = new ReActEngine();

        // 典型场景：条件分支 + 跨系统动作链条
        String complexPrompt1 = "查看感冒灵当前的库存，如果低于100盒，就把它在商城的售价调到18元，并给李文华医生发一条补货提醒";
        Assertions.assertTrue(engine.isCompositeTask(complexPrompt1));

        String complexPrompt2 = "查询布洛芬库存，若小于50件调价到25元然后通知值班护士";
        Assertions.assertTrue(engine.isCompositeTask(complexPrompt2));

        // 单一操作不应误触发 ReAct 链式引擎
        String singleAction1 = "查一下感冒灵的库存";
        Assertions.assertFalse(engine.isCompositeTask(singleAction1));

        String singleAction2 = "给李文华发一条通知";
        Assertions.assertFalse(engine.isCompositeTask(singleAction2));
    }
}
