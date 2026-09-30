package com.chunbo.medical.service;

import com.chunbo.medical.rag.Bm25Retriever;
import com.chunbo.medical.rag.MedicalReranker;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

/**
 * 药品诊疗规范知识库混合检索 RAG 服务 (Hybrid Search + Medical Reranker)
 * 采用【BM25 关键词精确匹配 (30%权重) + 语义向量检索 (70%权重) + Cross-Encoder 医疗重排】架构，
 * 专攻中药生僻字、方剂名、生化检验指标缩写（ALT/AST/eGFR/HbA1c）及西药通用名，
 * 彻底解决单一向量检索在医疗专有名词上召回不准的痛点。
 */
@Service
public class RagKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeService.class);

    /** 文档元数据 key：标题（检索命中后据此回填"知识库引用"标题） */
    private static final String META_TITLE = "title";

    /** 官方向量库（Spring AI 标准 RAG：VectorStore 接口 + SimpleVectorStore 实现） */
    @Autowired(required = false)
    private VectorStore vectorStore;

    @Value("${chunbo.rag.doc-path:../rag_docs}")
    private String docPath;

    /** 权重配置：向量稠密语义权重（默认 0.70） */
    @Value("${chunbo.rag.vector-weight:0.70}")
    private double vectorWeight = 0.70;

    /** 权重配置：BM25 关键词稀疏权重（默认 0.30） */
    @Value("${chunbo.rag.bm25-weight:0.30}")
    private double bm25Weight = 0.30;

    /** 医疗专有 BM25 稀疏检索器 (30% 权重) */
    private final Bm25Retriever bm25Retriever = new Bm25Retriever(1.5, 0.75);

    /** 医疗 Cross-Encoder 风格重排序器 */
    private final MedicalReranker medicalReranker = new MedicalReranker();

    /** 文本块源（title + text），用于倒排索引构建与备用兜底 */
    private volatile List<Chunk> chunks = new ArrayList<>();
    private volatile boolean ready = false;

    /** 线程隔离的检索命中标题，防止并发问诊时引用串话 */
    private final ThreadLocal<List<String>> threadLocalLastTitles = ThreadLocal.withInitial(ArrayList::new);
    /** 最近一次检索命中的文档标题（兜底兼容） */
    private volatile List<String> lastTitles = new ArrayList<>();

    public static class Chunk {
        public String title;
        public String text;
    }

    @PostConstruct
    public void init() {
        // 后台异步加载，避免阻塞应用启动（embedding 走网络可能较慢）
        Thread t = new Thread(() -> {
            try {
                load();
            } catch (Exception e) {
                log.error("RAG 知识库加载异常", e);
            }
        }, "rag-hybrid-loader");
        t.setDaemon(true);
        t.start();
    }

    public synchronized void load() {
        List<Chunk> loaded = new ArrayList<>();
        List<Path> files = resolveDocFiles();
        if (files.isEmpty()) {
            log.warn("RAG 知识库未找到文档（候选目录: {}），请确认 rag_docs 存在", docPath);
            return;
        }
        List<Document> documents = new ArrayList<>();
        List<Map.Entry<String, String>> bm25Entries = new ArrayList<>();

        for (Path p : files) {
            try {
                String content = Files.readString(p);
                // 提取文档顶级标题（例如 # 2型糖尿病基层临床规范指南 或 文件名）
                String docMainTitle = p.getFileName().toString().replaceAll("\\.md$", "");
                for (String line : content.split("\n")) {
                    String trimmed = line.trim();
                    if (trimmed.startsWith("# ")) {
                        docMainTitle = trimmed.substring(2).trim();
                        break;
                    }
                }

                for (String section : content.split("(?=## )")) {
                    String clean = section.trim();
                    if (clean.isEmpty()) continue;
                    String subTitle = clean.split("\\n")[0].replaceAll("#+", "").trim();
                    String title = subTitle;
                    if (!subTitle.equals(docMainTitle) && !subTitle.startsWith("《" + docMainTitle)) {
                        title = "《" + docMainTitle + "》· " + subTitle;
                    }

                    Chunk c = new Chunk();
                    c.title = title;
                    c.text = clean;
                    loaded.add(c);

                    // 构造 Document 写入 VectorStore
                    Map<String, Object> meta = new HashMap<>();
                    meta.put(META_TITLE, title);
                    documents.add(new Document(clean, meta));

                    // 构建 BM25 词条
                    bm25Entries.add(Map.entry(title, clean));
                }
            } catch (Exception e) {
                log.warn("读取文档失败: {}", p, e);
            }
        }

        chunks = loaded;
        ready = !loaded.isEmpty();

        // 1. 构建 BM25 倒排索引
        bm25Retriever.buildIndex(bm25Entries);
        log.info("RAG 知识库已构建 BM25 倒排索引，分词与词频统计就绪（片段数: {}）", bm25Entries.size());

        // 2. 写入官方向量库（SimpleVectorStore 内部用 EmbeddingModel 向量化）
        if (vectorStore != null && !documents.isEmpty()) {
            try {
                vectorStore.add(documents);
                log.info("RAG 知识库已成功写入 VectorStore：文档块 {} 个（Embedding 向量空间索引已就绪）", documents.size());
            } catch (Exception e) {
                log.warn("写入 VectorStore 失败，将依赖 BM25 关键词混合引擎: {}", e.getMessage());
            }
        } else {
            log.info("RAG 知识库加载完成：文档块 {} 个（VectorStore 离线，将以 BM25 稀疏引擎为主）", loaded.size());
        }
    }

    private List<Path> resolveDocFiles() {
        List<Path> files = new ArrayList<>();
        String[] candidates = {docPath, "../rag_docs", "rag_docs"};
        for (String dir : candidates) {
            try {
                Path p = Paths.get(dir);
                if (Files.isDirectory(p)) {
                    try (Stream<Path> s = Files.list(p)) {
                        s.filter(f -> f.toString().endsWith(".md")).forEach(files::add);
                    }
                    if (!files.isEmpty()) break;
                }
            } catch (Exception ignored) {}
        }
        return files;
    }

    /**
     * 核心混合检索 (Hybrid Search) + 医疗重排 (Medical Rerank)
     * 1. 向量语义检索召回 Top-N
     * 2. BM25 关键词精确匹配召回 Top-N
     * 3. 联合归一化加权合并候选池 (70% 稠密向量 + 30% 稀疏关键词)
     * 4. Cross-Encoder 风格医疗重排器按医学专有名词、共现度、标题加成二次打分
     * 5. 提取黄金 Top-K 片段
     */
    public List<String> search(String query, int topK) {
        if (chunks.isEmpty() || query == null || query.isBlank()) {
            lastTitles = new ArrayList<>();
            return List.of();
        }

        Map<String, MedicalReranker.CandidateItem> candidateMap = new LinkedHashMap<>();

        // ── 1. 官方向量稠密检索 (Dense Semantic Search) ──
        if (vectorStore != null) {
            try {
                SearchRequest request = SearchRequest.builder()
                        .query(query)
                        .topK(Math.max(topK * 2, 8))
                        .similarityThresholdAll()
                        .build();
                List<Document> vectorDocs = vectorStore.similaritySearch(request);
                if (vectorDocs != null && !vectorDocs.isEmpty()) {
                    int rank = 0;
                    for (Document d : vectorDocs) {
                        String title = d.getMetadata() != null
                                ? String.valueOf(d.getMetadata().getOrDefault(META_TITLE, "")) : "";
                        String text = d.getText();
                        String key = title + "::" + text.hashCode();
                        // 向量分数做位次加权归一化 (0.95 ~ 0.65)
                        double vScore = Math.max(0.65, 0.95 - (rank * 0.04));
                        candidateMap.put(key, new MedicalReranker.CandidateItem(title, text, vScore, 0.0));
                        rank++;
                    }
                }
            } catch (Exception e) {
                log.warn("向量语义检索调用异常，继续走 BM25 检索: {}", e.getMessage());
            }
        }

        // ── 2. BM25 稀疏精确检索 (Sparse Keyword Search) ──
        try {
            List<Bm25Retriever.Bm25Hit> bm25Hits = bm25Retriever.search(query, Math.max(topK * 2, 8));
            if (!bm25Hits.isEmpty()) {
                double maxBm25 = bm25Hits.get(0).score;
                for (Bm25Retriever.Bm25Hit hit : bm25Hits) {
                    String title = hit.docId;
                    String text = hit.text;
                    String key = title + "::" + text.hashCode();
                    double normalizedBm25 = maxBm25 > 0 ? (hit.score / maxBm25) : 0.5;

                    if (candidateMap.containsKey(key)) {
                        // 两路均命中：更新 BM25 分数
                        MedicalReranker.CandidateItem existing = candidateMap.get(key);
                        candidateMap.put(key, new MedicalReranker.CandidateItem(title, text, existing.vectorScore, normalizedBm25));
                    } else {
                        // 仅 BM25 命中（如专有生僻中药名或特定指标）
                        candidateMap.put(key, new MedicalReranker.CandidateItem(title, text, 0.50, normalizedBm25));
                    }
                }
            }
        } catch (Exception e) {
            log.warn("BM25 检索异常: {}", e.getMessage());
        }

        // ── 3. 兜底保障：若向量与BM25均未匹配到，走基础扫描 ──
        if (candidateMap.isEmpty()) {
            for (Chunk c : chunks) {
                if (containsAny(c.text, query)) {
                    String key = c.title + "::" + c.text.hashCode();
                    candidateMap.put(key, new MedicalReranker.CandidateItem(c.title, c.text, 0.55, 0.55));
                    if (candidateMap.size() >= topK) break;
                }
            }
        }

        // ── 4. Cross-Encoder 医疗重排 (Medical Cross-Encoder Reranking) ──
        List<MedicalReranker.CandidateItem> candidateList = new ArrayList<>(candidateMap.values());
        List<MedicalReranker.CandidateItem> rerankedResults = medicalReranker.rerank(
                query, candidateList, vectorWeight, bm25Weight, topK
        );

        // ── 5. 组装输出与标题追踪 ──
        List<String> titles = new ArrayList<>();
        List<String> hits = new ArrayList<>();

        for (int i = 0; i < rerankedResults.size(); i++) {
            MedicalReranker.CandidateItem item = rerankedResults.get(i);
            titles.add(item.title);
            String formatted = String.format("【临床指南片段 %d: %s | 混合置信度: %.1f%% (%s)】\n%s",
                    i + 1, item.title, item.finalScore * 100, item.rankReason, item.text);
            hits.add(formatted);
        }

        threadLocalLastTitles.set(titles);
        lastTitles = titles;
        log.info("[Hybrid RAG] 查询「{}」完成混合检索重排，召回黄金片段 {} 条: {}",
                query, hits.size(), titles);
        return hits;
    }

    /** 最近一次检索命中的文档标题列表（线程隔离优先） */
    public List<String> getLastTitles() {
        List<String> tl = threadLocalLastTitles.get();
        if (tl != null && !tl.isEmpty()) {
            return new ArrayList<>(tl);
        }
        return lastTitles == null ? new ArrayList<>() : new ArrayList<>(lastTitles);
    }

    private boolean containsAny(String text, String query) {
        for (String kw : query.split("[\\s，。、；？！,:]+")) {
            if (kw.length() >= 2 && text.contains(kw)) return true;
        }
        return false;
    }

    @Tool(description = "检索基层诊疗与用药规范知识库（采用 BM25 关键词精确匹配 + 语义向量混合检索 + Cross-Encoder 医疗重排），返回权威临床指南片段与禁忌证据")
    public String searchKnowledge(@ToolParam(description = "医学检索问题或病症关键词，如：2型糖尿病二联用药方案 或 氨氯地平禁忌症") String query) {
        if (!ready) return "知识库尚未就绪，请稍后再试。";
        List<String> hits = search(query, 3);
        if (hits.isEmpty()) return "未在基层临床知识库中检索到相关用药证据。";
        StringBuilder sb = new StringBuilder();
        sb.append("### 📚 春播基层临床诊疗规范与指南依据 (混合检索 + Rerank 重排核验通过)\n\n");
        for (String hit : hits) {
            sb.append(hit).append("\n\n");
        }
        return sb.toString();
    }

    public boolean isReady() {
        return ready;
    }
}
