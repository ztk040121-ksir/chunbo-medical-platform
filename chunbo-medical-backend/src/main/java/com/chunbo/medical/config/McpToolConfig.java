package com.chunbo.medical.config;

import com.chunbo.medical.service.RagKnowledgeService;
import com.chunbo.medical.tools.ClinicAssistantTools;
import com.chunbo.medical.tools.DatabaseMcpTools;
import com.chunbo.medical.tools.MedicalClinicTools;
import com.chunbo.medical.tools.PayAuditTools;
import com.chunbo.medical.tools.WebFetchTools;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MCP 工具注册中心：把所有 @Tool 方法注册为 MCP 工具，
 * 由 Spring AI MCP Server 统一暴露（默认端点 http://localhost:8080/sse）。
 * 覆盖：医疗问诊、OA 运营、支付审计、数据库操作、RAG 知识检索、网页抓取。
 */
@Configuration
public class McpToolConfig {

    @Bean
    public ToolCallbackProvider mcpToolProvider(
            MedicalClinicTools clinicTools,
            ClinicAssistantTools assistantTools,
            PayAuditTools payAuditTools,
            DatabaseMcpTools databaseTools,
            RagKnowledgeService ragKnowledgeService,
            WebFetchTools webFetchTools) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(clinicTools, assistantTools, payAuditTools, databaseTools, ragKnowledgeService, webFetchTools)
                .build();
    }
}
