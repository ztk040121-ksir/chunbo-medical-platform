package com.chunbo.medical.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 药品诊疗知识库 RAG 服务：加载 rag_docs 下的临床用药规范文档，切块后向量化，
 * 提供语义检索（embedding 可用时走余弦相似度，否则降级为关键词匹配）。
 * 检索结果通过 MCP / Function Calling 工具暴露给大模型，支撑用药问答的"有据可依"。
 */
@Service
public class RagKnowledgeService {

    private static final Logger log = LoggerFactory.getLogger(RagKnowledgeService.class);

    @Autowired(required = false)
    private EmbeddingModel embeddingModel;

    @Value("${chunbo.rag.doc-path:../rag_docs}")
    private String docPath;

    private volatile List<Chunk> chunks = new ArrayList<>();
    private volatile boolean ready = false;
    /** 最近一次检索命中的文档标题（供前端展示"知识库引用"） */
    private volatile List<String> lastTitles = new ArrayList<>();

    static class Chunk {
        String title;
        String text;
        float[] vector;
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
        }, "rag-loader");
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
        for (Path p : files) {
            try {
                String content = Files.readString(p);
                for (String section : content.split("(?=## )")) {
                    String clean = section.trim();
                    if (clean.isEmpty()) continue;
                    Chunk c = new Chunk();
                    c.title = clean.split("\\n")[0].replaceAll("#+", "").trim();
                    c.text = clean;
                    if (embeddingModel != null) {
                        try {
                            c.vector = embeddingModel.embed(clean);
                        } catch (Exception e) {
                            // 单块向量化失败不影响整体，降级为关键词
                        }
                    }
                    loaded.add(c);
                }
            } catch (Exception e) {
                log.warn("读取文档失败: {}", p, e);
            }
        }
        chunks = loaded;
        ready = !loaded.isEmpty();
        log.info("RAG 知识库加载完成：文档块 {} 个，向量化 {}", chunks.size(), embeddingModel != null);
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

    /** 检索 topK 个最相关片段（embedding 可用走余弦，否则关键词降级），并记录命中标题 */
    public List<String> search(String query, int topK) {
        if (chunks.isEmpty()) { lastTitles = new ArrayList<>(); return List.of(); }
        List<String> titles = new ArrayList<>();
        if (embeddingModel != null) {
            try {
                float[] qv = embeddingModel.embed(query);
                List<Chunk> sorted = new ArrayList<>(chunks);
                sorted.sort((a, b) -> Double.compare(cosine(qv, b.vector), cosine(qv, a.vector)));
                List<String> hits = new ArrayList<>();
                sorted.stream().limit(topK).forEach(c -> {
                    titles.add(c.title);
                    hits.add("【" + c.title + "】\n" + c.text);
                });
                lastTitles = titles;
                return hits;
            } catch (Exception e) {
                log.warn("向量检索失败，降级关键词: {}", e.getMessage());
            }
        }
        final String q = query;
        List<Chunk> kwHits = chunks.stream()
                .filter(c -> containsAny(c.text, q))
                .limit(topK)
                .toList();
        List<String> hits = new ArrayList<>();
        kwHits.forEach(c -> {
            titles.add(c.title);
            hits.add("【" + c.title + "】\n" + c.text);
        });
        lastTitles = titles;
        return hits;
    }

    /** 最近一次检索命中的文档标题列表 */
    public List<String> getLastTitles() {
        return lastTitles == null ? new ArrayList<>() : lastTitles;
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length) return 0;
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private boolean containsAny(String text, String query) {
        for (String kw : query.split("[\\s，。、；？！,:]+")) {
            if (kw.length() >= 2 && text.contains(kw)) return true;
        }
        return false;
    }

    @Tool(description = "检索基层诊疗与用药规范知识库（RAG），返回与问题最相关的临床指南片段，供用药问答引用")
    public String searchKnowledge(@ToolParam(description = "检索问题，如：高血压的一线降压药有哪些") String query) {
        if (!ready) return "知识库尚未就绪，请稍后再试。";
        List<String> hits = search(query, 3);
        if (hits.isEmpty()) return "未在知识库中检索到相关内容。";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < hits.size(); i++) {
            sb.append("【片段 ").append(i + 1).append("】").append(hits.get(i)).append("\n");
        }
        return sb.toString();
    }

    public boolean isReady() {
        return ready;
    }
}
