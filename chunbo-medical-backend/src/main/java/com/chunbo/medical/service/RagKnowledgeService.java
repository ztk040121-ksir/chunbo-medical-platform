package com.chunbo.medical.service;

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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * 药品诊疗知识库 RAG 服务：加载 rag_docs 下的临床用药规范文档，切块后写入官方向量库 VectorStore（SimpleVectorStore），
 * 检索走标准 similaritySearch(SearchRequest)（底层由 EmbeddingModel 向量化 + 余弦相似度）。
 * 检索结果通过 MCP / Function Calling 工具暴露给大模型，支撑用药问答的"有据可依"。
 *
 * 知识库源头是静态 md 文件（每次启动重新向量化），故无需 JSON 持久化；
 * 向量库不可用 / 检索为空时，降级为关键词匹配，保证业务稳健。
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

    /** 文本块源（title + text），用于向量库不可用时的关键词降级 */
    private volatile List<Chunk> chunks = new ArrayList<>();
    private volatile boolean ready = false;
    /** 最近一次检索命中的文档标题（供前端展示"知识库引用"） */
    private volatile List<String> lastTitles = new ArrayList<>();

    static class Chunk {
        String title;
        String text;
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
        List<Document> documents = new ArrayList<>();
        for (Path p : files) {
            try {
                String content = Files.readString(p);
                for (String section : content.split("(?=## )")) {
                    String clean = section.trim();
                    if (clean.isEmpty()) continue;
                    String title = clean.split("\\n")[0].replaceAll("#+", "").trim();
                    Chunk c = new Chunk();
                    c.title = title;
                    c.text = clean;
                    loaded.add(c);
                    // 构造 Document：text 为片段正文，metadata 带标题（检索后据此回填 title）
                    Map<String, Object> meta = new HashMap<>();
                    meta.put(META_TITLE, title);
                    documents.add(new Document(clean, meta));
                }
            } catch (Exception e) {
                log.warn("读取文档失败: {}", p, e);
            }
        }
        chunks = loaded;
        ready = !loaded.isEmpty();

        // 写入官方向量库（SimpleVectorStore 内部用 EmbeddingModel 向量化）
        if (vectorStore != null && !documents.isEmpty()) {
            try {
                vectorStore.add(documents);
                log.info("RAG 知识库已写入 VectorStore：文档块 {} 个", documents.size());
            } catch (Exception e) {
                log.warn("写入 VectorStore 失败，将降级为关键词检索: {}", e.getMessage());
            }
        } else {
            log.info("RAG 知识库加载完成：文档块 {} 个（VectorStore 未就绪，走关键词降级）", loaded.size());
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

    /** 检索 topK 个最相关片段（优先官方 similaritySearch，失败/为空降级关键词），并记录命中标题 */
    public List<String> search(String query, int topK) {
        if (chunks.isEmpty()) { lastTitles = new ArrayList<>(); return List.of(); }
        // 1. 官方向量检索（VectorStore + SearchRequest，标准 RAG）
        if (vectorStore != null) {
            try {
                SearchRequest request = SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThresholdAll() // 阈值 0：不过滤正相似度，按相似度排序取 topK（与原行为一致）
                        .build();
                List<Document> docs = vectorStore.similaritySearch(request);
                if (docs != null && !docs.isEmpty()) {
                    List<String> titles = new ArrayList<>();
                    List<String> hits = new ArrayList<>();
                    for (Document d : docs) {
                        String title = d.getMetadata() != null
                                ? String.valueOf(d.getMetadata().getOrDefault(META_TITLE, "")) : "";
                        titles.add(title);
                        hits.add("【" + title + "】\n" + d.getText());
                    }
                    lastTitles = titles;
                    return hits;
                }
            } catch (Exception e) {
                log.warn("向量检索失败，降级关键词: {}", e.getMessage());
            }
        }
        // 2. 关键词降级兜底
        return keywordSearch(query, topK);
    }

    /** 关键词降级：向量库不可用/检索为空时的兜底检索 */
    private List<String> keywordSearch(String query, int topK) {
        final String q = query;
        List<String> titles = new ArrayList<>();
        List<String> hits = new ArrayList<>();
        chunks.stream()
                .filter(c -> containsAny(c.text, q))
                .limit(topK)
                .forEach(c -> {
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
