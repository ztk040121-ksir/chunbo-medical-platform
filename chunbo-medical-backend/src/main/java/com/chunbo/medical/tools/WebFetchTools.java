package com.chunbo.medical.tools;

import com.chunbo.medical.config.ToolResultHolder;
import com.chunbo.medical.constant.AgentConstant;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 网页抓取工具：让三个智能体都能抓取公开网页内容并转为纯文本，
 * 供 AI 查询文献、新闻、公开资料。作为 Function Calling 工具 + MCP 工具双重暴露。
 */
@Component
public class WebFetchTools {

    @Tool(description = "抓取指定网页内容并转为纯文本，用于查询公开网络资料、文献、新闻（需传入完整 http/https 链接）")
    public String fetchWebpage(@ToolParam(description = "要抓取的网页完整 URL，如 https://example.com/page") String url, ToolContext toolContext) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            return "请提供合法的 http/https 网页链接。";
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(Duration.ofSeconds(15))
                    .GET().build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() >= 400) {
                return "网页请求失败：HTTP " + resp.statusCode();
            }
            String text = htmlToText(resp.body());
            if (text.length() > 3000) {
                text = text.substring(0, 3000) + "\n…（内容过长已截断）";
            }
            ToolResultHolder.put(requestIdOf(toolContext), "webpageContent", text);
            return text;
        } catch (Exception e) {
            return "网页抓取失败：" + (e.getMessage() == null ? e.toString() : e.getMessage());
        }
    }

    /** 从工具上下文安全提取 requestId */
    private String requestIdOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) return null;
        Object v = toolContext.getContext().get(AgentConstant.REQUEST_ID);
        return v == null ? null : String.valueOf(v);
    }

    private String htmlToText(String html) {
        if (html == null) return "";
        String s = html.replaceAll("(?is)<script.*?</script>", " ")
                .replaceAll("(?is)<style.*?</style>", " ")
                .replaceAll("(?is)<[^>]+>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        return s.replaceAll("\\s+", " ").trim();
    }
}
