package com.chunbo.medical.rag;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 医疗专业 BM25 稀疏检索器
 * 核心针对基层医疗中药生僻字、方剂名、检验指标缩写（ALT/AST/eGFR/HbA1c）及西药通用名，
 * 实现工业级 BM25 (Best Matching 25) 稀疏打分算法。
 */
public class Bm25Retriever {

    private final double k1;
    private final double b;

    private int totalDocs = 0;
    private double avgDocLength = 0.0;

    /** 词典：term -> 文档频率 DF (包含该词的文档数) */
    private final Map<String, Integer> docFrequencies = new HashMap<>();

    /** 每个文档的长度 (分词数量) */
    private final List<Integer> docLengths = new ArrayList<>();

    /** 每个文档的词频统计：docIndex -> (term -> tf) */
    private final List<Map<String, Integer>> docTermFrequencies = new ArrayList<>();

    /** 文档标识列表，与索引顺序对应 */
    private final List<String> docIds = new ArrayList<>();

    /** 文档原始文本 */
    private final List<String> docTexts = new ArrayList<>();

    /** 匹配医学英文缩写、数字指标、以及中文连续字 */
    private static final Pattern MEDICAL_TOKEN_PATTERN = Pattern.compile(
            "[a-zA-Z0-9_+\\-<>.]+|[\u4e00-\u9fa5]{1,2}"
    );

    public Bm25Retriever() {
        this(1.5, 0.75);
    }

    public Bm25Retriever(double k1, double b) {
        this.k1 = k1;
        this.b = b;
    }

    /**
     * 构建或重建 BM25 索引
     *
     * @param documents 文档列表（key 为文档唯一标识如标题或id，value 为文档全文）
     */
    public synchronized void buildIndex(List<Map.Entry<String, String>> documents) {
        docFrequencies.clear();
        docLengths.clear();
        docTermFrequencies.clear();
        docIds.clear();
        docTexts.clear();

        totalDocs = documents.size();
        if (totalDocs == 0) {
            avgDocLength = 0;
            return;
        }

        long totalLength = 0;
        for (int i = 0; i < totalDocs; i++) {
            Map.Entry<String, String> entry = documents.get(i);
            String id = entry.getKey();
            String text = entry.getValue() != null ? entry.getValue() : "";

            docIds.add(id);
            docTexts.add(text);

            List<String> tokens = tokenize(text);
            int len = tokens.size();
            docLengths.add(len);
            totalLength += len;

            Map<String, Integer> tfMap = new HashMap<>();
            Set<String> uniqueTermsInDoc = new HashSet<>();
            for (String t : tokens) {
                tfMap.merge(t, 1, Integer::sum);
                uniqueTermsInDoc.add(t);
            }
            docTermFrequencies.add(tfMap);

            for (String term : uniqueTermsInDoc) {
                docFrequencies.merge(term, 1, Integer::sum);
            }
        }

        avgDocLength = totalLength > 0 ? (double) totalLength / totalDocs : 1.0;
    }

    /**
     * 医疗专有分词器：提取中英文缩写、指标、以及中文 1-2 元分词
     */
    public List<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Collections.emptyList();
        List<String> tokens = new ArrayList<>();
        Matcher matcher = MEDICAL_TOKEN_PATTERN.matcher(text.toLowerCase());
        while (matcher.find()) {
            String token = matcher.group().trim();
            if (!token.isEmpty() && !isStopWord(token)) {
                tokens.add(token);
            }
        }
        // 对于中文连续词段，额外补充 2-gram 提升生僻中药及方剂名命中率（如：半夏、黄芩、通络、贴敷）
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length - 1; i++) {
            if (isChinese(chars[i]) && isChinese(chars[i + 1])) {
                String bi = "" + chars[i] + chars[i + 1];
                if (!isStopWord(bi)) {
                    tokens.add(bi.toLowerCase());
                }
            }
        }
        return tokens;
    }

    private boolean isChinese(char c) {
        return c >= 0x4e00 && c <= 0x9fa5;
    }

    private boolean isStopWord(String s) {
        return "的".equals(s) || "了".equals(s) || "和".equals(s) || "是".equals(s)
                || "在".equals(s) || "有".equals(s) || "与".equals(s) || "等".equals(s)
                || "及".equals(s) || "可".equals(s) || "于".equals(s) || "为".equals(s);
    }

    /**
     * 计算逆文档频率 IDF（含平滑）
     */
    private double idf(String term) {
        int df = docFrequencies.getOrDefault(term, 0);
        // Robertson-Spärck Jones IDF 公式加平滑，避免负数
        return Math.log(1.0 + (totalDocs - df + 0.5) / (df + 0.5));
    }

    /**
     * 计算单篇文档关于 Query 的 BM25 分数
     */
    public double scoreDoc(int docIdx, List<String> queryTokens) {
        if (docIdx < 0 || docIdx >= totalDocs) return 0.0;
        int docLen = docLengths.get(docIdx);
        Map<String, Integer> tfMap = docTermFrequencies.get(docIdx);

        double score = 0.0;
        for (String qTerm : queryTokens) {
            if (!tfMap.containsKey(qTerm)) continue;
            int tf = tfMap.get(qTerm);
            double idfVal = idf(qTerm);
            double denom = tf + k1 * (1.0 - b + b * (docLen / avgDocLength));
            double termScore = idfVal * (tf * (k1 + 1.0)) / denom;
            score += termScore;
        }
        return score;
    }

    /**
     * 执行 BM25 检索，返回全部文档的得分列表（降序）
     */
    public List<Bm25Hit> search(String query, int topK) {
        if (totalDocs == 0 || query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) return Collections.emptyList();

        List<Bm25Hit> hits = new ArrayList<>(totalDocs);
        for (int i = 0; i < totalDocs; i++) {
            double score = scoreDoc(i, queryTokens);
            if (score > 0.0) {
                hits.add(new Bm25Hit(i, docIds.get(i), docTexts.get(i), score));
            }
        }

        hits.sort((a, b) -> Double.compare(b.score, a.score));
        if (hits.size() > topK) {
            return hits.subList(0, topK);
        }
        return hits;
    }

    public static class Bm25Hit {
        public final int docIndex;
        public final String docId;
        public final String text;
        public final double score;

        public Bm25Hit(int docIndex, String docId, String text, double score) {
            this.docIndex = docIndex;
            this.docId = docId;
            this.text = text;
            this.score = score;
        }
    }
}
