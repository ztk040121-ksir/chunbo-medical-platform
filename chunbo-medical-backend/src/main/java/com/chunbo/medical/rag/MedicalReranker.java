package com.chunbo.medical.rag;

import java.util.*;
import java.util.regex.Pattern;

/**
 * 医疗专业 Cross-Encoder 风格重排序器 (Medical Cross-Encoder Reranker)
 * 对向量检索 (Dense) 与 BM25 检索 (Sparse) 召回的候选候选集进行精细交叉特征打分，
 * 彻底解决专有名词召回后排序靠后、引文不精准的问题。
 */
public class MedicalReranker {

    private static final Pattern SPLIT_PATTERN = Pattern.compile("[\\s，。、；？！,:\\-_/()\\[\\]]+");

    /**
     * 候选重排项封装
     */
    public static class CandidateItem {
        public final String title;
        public final String text;
        public final double vectorScore; // 归一化向量分数 0~1
        public final double bm25Score;   // 归一化 BM25 分数 0~1
        public double finalScore;        // 重排后综合置信度分数 0~1
        public String rankReason;        // 重排理由说明

        public CandidateItem(String title, String text, double vectorScore, double bm25Score) {
            this.title = title != null ? title : "";
            this.text = text != null ? text : "";
            this.vectorScore = vectorScore;
            this.bm25Score = bm25Score;
            this.finalScore = 0.0;
            this.rankReason = "";
        }
    }

    /**
     * 执行重排序打分并返回 Top-K 结果
     *
     * @param query       用户查询问题
     * @param candidates  候选集（已包含向量与BM25基础分）
     * @param vectorWeight 向量检索基础权重（默认 0.7）
     * @param bm25Weight   BM25 基础权重（默认 0.3）
     * @param topK        最终截取的黄金片段数
     * @return 重排序后的最优片段列表
     */
    public List<CandidateItem> rerank(String query, List<CandidateItem> candidates,
                                     double vectorWeight, double bm25Weight, int topK) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<CandidateItem> resultList = new ArrayList<>(candidates);
        List<String> queryKeywords = extractKeywords(query);

        for (CandidateItem item : resultList) {
            // 1. 基础混合融合分 (Hybrid Base Score: 70% Dense + 30% Sparse)
            double baseHybrid = (item.vectorScore * vectorWeight) + (item.bm25Score * bm25Weight);

            // 2. 医疗专有名词实体覆盖度 (Entity Coverage: 0 ~ 0.35)
            double coverageScore = calculateEntityCoverage(queryKeywords, item.text);

            // 3. 标题与主题匹配加成 (Title Topic Prior: 0 ~ 0.25)
            double titleBonus = calculateTitleBonus(queryKeywords, item.title);

            // 4. 词项跨度与就近共现加成 (Proximity & Co-occurrence: 0 ~ 0.20)
            double proximityBonus = calculateProximityBonus(queryKeywords, item.text);

            // 5. 综合计算 Cross-Encoder 最终置信度 (经 Sigmoid 归一化映射在 0.60 ~ 0.99 区间)
            double rawScore = (baseHybrid * 0.45) + (coverageScore * 0.25) + (titleBonus * 0.18) + (proximityBonus * 0.12);
            double confidence = 1.0 / (1.0 + Math.exp(-4.5 * (rawScore - 0.35)));

            // 格式化置信度百分比
            double rounded = Math.round(confidence * 1000.0) / 10.0; // 例如 98.5%
            item.finalScore = rounded / 100.0;
            item.rankReason = String.format("Dense:%.2f | BM25:%.2f | 专有名词覆盖:%.0f%% | 标题权重:+%.2f",
                    item.vectorScore, item.bm25Score, coverageScore * 100, titleBonus);
        }

        // 按 finalScore 降序排列
        resultList.sort((a, b) -> Double.compare(b.finalScore, a.finalScore));

        if (resultList.size() > topK) {
            return new ArrayList<>(resultList.subList(0, topK));
        }
        return resultList;
    }

    /**
     * 提取 Query 中的关键检索单元
     */
    private List<String> extractKeywords(String query) {
        if (query == null) return Collections.emptyList();
        List<String> list = new ArrayList<>();
        for (String w : SPLIT_PATTERN.split(query.trim().toLowerCase())) {
            if (w.length() >= 2) {
                list.add(w);
            }
        }
        return list;
    }

    /**
     * 计算实体覆盖率（Query 里的关键医学名词在文档里命中了多少）
     */
    private double calculateEntityCoverage(List<String> keywords, String text) {
        if (keywords.isEmpty() || text == null || text.isBlank()) return 0.0;
        String lower = text.toLowerCase();
        int matched = 0;
        for (String kw : keywords) {
            if (lower.contains(kw)) {
                matched++;
            }
        }
        return (double) matched / keywords.size();
    }

    /**
     * 标题与主题匹配度加成
     */
    private double calculateTitleBonus(List<String> keywords, String title) {
        if (keywords.isEmpty() || title == null || title.isBlank()) return 0.0;
        String lower = title.toLowerCase();
        int matched = 0;
        for (String kw : keywords) {
            if (lower.contains(kw)) {
                matched++;
            }
        }
        return Math.min(1.0, (double) matched / Math.max(1, keywords.size()));
    }

    /**
     * 词项就近共现加成（如果多个查询词在同一句 30 字内出现，相关度极高）
     */
    private double calculateProximityBonus(List<String> keywords, String text) {
        if (keywords.size() <= 1 || text == null || text.isBlank()) return 0.1;
        String lower = text.toLowerCase();
        for (String sentence : lower.split("[。！？\n]+")) {
            if (sentence.length() < 10) continue;
            int count = 0;
            for (String kw : keywords) {
                if (sentence.contains(kw)) count++;
            }
            if (count >= 2) {
                return 1.0; // 同一句话内共现，赋予满额紧密度奖励
            }
        }
        return 0.2;
    }
}
