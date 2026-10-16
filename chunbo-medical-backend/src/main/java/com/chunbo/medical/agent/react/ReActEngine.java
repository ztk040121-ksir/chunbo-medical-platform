package com.chunbo.medical.agent.react;

import com.chunbo.medical.config.ToolResultHolder;
import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.enums.ChatEventTypeEnum;
import com.chunbo.medical.service.AiModelConfigService;
import com.chunbo.medical.tools.ClinicAssistantTools;
import com.chunbo.medical.vo.ChatEventVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 医疗中台 ReAct 多步自主链式任务引擎 (Reasoning + Action Autonomous Engine)
 * 支持大模型自主进行【Thought ➔ Action ➔ Observation ➔ Next Action ... ➔ Final Answer】循环，
 * 一次性闭环多动作、多条件判定的复合调度任务（如：查库存 ➔ 判定条件 ➔ 动态调价 ➔ 联动通知医护）。
 */
@Component
public class ReActEngine {

    private static final Logger log = LoggerFactory.getLogger(ReActEngine.class);
    private static final int MAX_REACT_STEPS = 6;
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private AiModelConfigService aiModelConfigService;

    @Autowired
    private ClinicAssistantTools assistantTools;

    /**
     * 判断用户输入是否为复合链式任务（含条件分支、多个动作组合、先做A再做B等）
     */
    public boolean isCompositeTask(String question) {
        if (question == null || question.isBlank()) return false;
        String q = question.trim();
        // 匹配常见的链式动作与条件语法
        boolean hasCondition = q.contains("如果") || q.contains("若") || q.contains("低于")
                || q.contains("高于") || q.contains("小于") || q.contains("大于") || q.contains("且");
        boolean hasMultipleActions = (q.contains("查看") || q.contains("查一下") || q.contains("查询"))
                && (q.contains("调到") || q.contains("调价") || q.contains("改价") || q.contains("发货")
                    || q.contains("通知") || q.contains("发消息") || q.contains("补货") || q.contains("上架") || q.contains("下架"));
        boolean hasStepWords = q.contains("然后") || q.contains("接着") || q.contains("并且") || q.contains("并给");

        return (hasCondition && hasMultipleActions) || (hasMultipleActions && hasStepWords);
    }

    /**
     * 执行 ReAct 多步自主推理与工具调用链
     *
     * @param question  复合调度指令
     * @param sessionId 会话ID
     * @param userId    当前用户ID
     * @param role      当前用户角色
     * @param userName  当前用户姓名
     * @return SSE 流式事件
     */
    public Flux<ChatEventVO> executeReActStream(String question, String sessionId, String userId, String role, String userName) {
        return Flux.create(sink -> {
            Thread executionThread = new Thread(() -> {
                try {
                    doReActLoop(question, sessionId, userId, role, userName, sink);
                    sink.complete();
                } catch (Exception e) {
                    log.error("[ReAct Engine] 链式自主调度异常", e);
                    sink.next(ChatEventVO.builder()
                            .eventType(ChatEventTypeEnum.DATA.getValue())
                            .eventData("\n\n⚠️ 【ReAct 调度异常】" + e.getMessage())
                            .build());
                    sink.complete();
                }
            }, "react-engine-" + System.currentTimeMillis());
            executionThread.setDaemon(true);
            executionThread.start();
        });
    }

    private void doReActLoop(String userQuery, String sessionId, String userId, String role, String userName, FluxSink<ChatEventVO> sink) {
        ChatClient chatClient = aiModelConfigService.getPreConsultChatClient();
        if (chatClient == null) {
            emitText(sink, "⚠️ AI 模型客户端未就绪，无法启动 ReAct 智能体。");
            return;
        }

        String requestId = UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> ctxMap = new HashMap<>();
        ctxMap.put(AgentConstant.REQUEST_ID, requestId);
        ctxMap.put(AgentConstant.USER_ID, userId);
        ctxMap.put(AgentConstant.ROLE, role);
        ctxMap.put("userName", userName);
        ToolContext toolContext = new ToolContext(ctxMap);

        emitText(sink, "🧠 **春播中枢 · ReAct 多步链式自主调度引擎启动**\n\n");
        emitText(sink, String.format("> 🎯 **目标任务**: `%s`\n> 👤 **操作人员**: %s (%s)\n\n", userQuery, userName, role));

        StringBuilder historyPrompt = new StringBuilder();
        historyPrompt.append("用户复合指令: ").append(userQuery).append("\n\n");

        List<String> reasoningSteps = new ArrayList<>();
        int step = 0;
        boolean finished = false;

        emitProcess(sink, "🧠 春播中枢 · ReAct 多步链式自主调度引擎启动，开始分析复合指令...");

        while (step < MAX_REACT_STEPS && !finished) {
            step++;
            String reactSystemPrompt = buildReActSystemPrompt(userName, role, userId);
            String currentPrompt = historyPrompt.toString() + "\n现在请给出第 " + step + " 步的 Thought 和 Action（若无需继续调用工具，请给出 Final Answer）：\n";

            String llmResponse = "";
            try {
                llmResponse = chatClient.prompt()
                        .system(reactSystemPrompt)
                        .user(currentPrompt)
                        .call()
                        .content();
            } catch (Exception e) {
                log.error("[ReAct] Step {} LLM 调用失败: {}", step, e.getMessage());
                emitText(sink, "\n⚠️ 大模型推理中断：" + e.getMessage() + "\n");
                break;
            }

            if (llmResponse == null || llmResponse.isBlank()) {
                break;
            }

            llmResponse = llmResponse.trim();
            historyPrompt.append(llmResponse).append("\n");

            // 判断是否给出最终结论 Final Answer
            if (llmResponse.contains("Final Answer:") || llmResponse.contains("Final Answer：")) {
                finished = true;
                String finalAns = extractFinalAnswer(llmResponse);
                emitProcess(sink, "✅ 全流程链式任务已达成，正在整理最终报告...");
                // 最终只输出干净的正文内容给用户，不把思考过程强塞进正文
                emitText(sink, finalAns);
                break;
            }

            // 解析 Thought 与 Action
            String thought = extractField(llmResponse, "Thought:", "Thought：");
            String action = extractField(llmResponse, "Action:", "Action：");
            String actionInput = extractField(llmResponse, "Action Input:", "Action Input：");

            if (thought != null && !thought.isBlank()) {
                reasoningSteps.add("💡 [步骤 " + step + "/推理] " + thought);
                emitProcess(sink, String.format("💡 步骤 %d 推理中: %s", step, thought));
            }

            if (action == null || action.isBlank() || "None".equalsIgnoreCase(action.trim())) {
                // 没有后续动作
                finished = true;
                emitText(sink, llmResponse);
                break;
            }

            action = action.trim();
            emitProcess(sink, String.format("⚡ 步骤 %d 执行工具: %s", step, action));

            // 执行真正的工具动作
            String observation = executeToolAction(action, actionInput, toolContext);
            reasoningSteps.add("⚡ [步骤 " + step + "/执行] " + action + " 入参: " + actionInput);
            reasoningSteps.add("📋 [步骤 " + step + "/观测] " + observation);
            emitProcess(sink, String.format("📋 步骤 %d 观测到数据: %s", step, truncateObs(observation)));

            historyPrompt.append("Observation: ").append(observation).append("\n\n");
        }

        if (!finished) {
            emitText(sink, "⚠️ 已达最大自主链式迭代步数限制，调度完成。");
        }
    }

    private String truncateObs(String obs) {
        if (obs == null) return "（无数据）";
        String s = obs.trim();
        return s.length() > 80 ? s.substring(0, 80) + "…" : s;
    }

    private void emitText(FluxSink<ChatEventVO> sink, String text) {
        sink.next(ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.DATA.getValue())
                .eventData(text)
                .build());
    }

    private void emitProcess(FluxSink<ChatEventVO> sink, String processDesc) {
        sink.next(ChatEventVO.builder()
                .eventType(ChatEventTypeEnum.PROCESS.getValue())
                .eventData(processDesc)
                .build());
    }

    private String buildReActSystemPrompt(String userName, String role, String userId) {
        String template = com.chunbo.medical.prompt.PromptConfig.get("oa_react");
        if (template.isBlank()) {
            return "你是「春播云管理系统 · 协同运营中台 ReAct 多步链式自主调度智能体」，当前登录操作员：" + userName + "（角色：" + role + "，工号：" + userId + "）。";
        }
        return String.format(template, userName, role, userId);
    }

    /**
     * 真正反射调用 Java 工具
     */
    private String executeToolAction(String actionName, String actionInputJson, ToolContext toolContext) {
        try {
            JsonNode inputNode = null;
            if (actionInputJson != null && !actionInputJson.isBlank()) {
                try {
                    inputNode = objectMapper.readTree(actionInputJson);
                } catch (Exception e) {
                    log.warn("[ReAct] 解析 Action Input JSON 失败: {}", actionInputJson);
                }
            }

            switch (actionName) {
                case "queryMallProductStock":
                case "queryPharmacyInventory": { // 容错重定向至商城库存查询，彻底杜绝混入云诊所药房
                    String name = inputNode != null && inputNode.has("productName")
                            ? inputNode.get("productName").asText()
                            : (inputNode != null && inputNode.has("keyword") ? inputNode.get("keyword").asText() : "");
                    return assistantTools.queryMallProductStock(name, toolContext);
                }
                case "changeProductPrice": {
                    String name = inputNode != null && inputNode.has("productName") ? inputNode.get("productName").asText() : "商品";
                    Double retailPrice = inputNode != null && inputNode.has("newRetailPrice") ? inputNode.get("newRetailPrice").asDouble() : 0.0;
                    Double wholesale = (inputNode != null && inputNode.has("newWholesalePrice") && !inputNode.get("newWholesalePrice").isNull())
                            ? inputNode.get("newWholesalePrice").asDouble() : null;
                    return assistantTools.changeProductPrice(name, retailPrice, wholesale, toolContext);
                }
                case "sendDoctorNotice": {
                    String doctor = inputNode != null && inputNode.has("doctor") ? inputNode.get("doctor").asText() : "李文华";
                    String type = inputNode != null && inputNode.has("noticeType") ? inputNode.get("noticeType").asText() : "系统提醒";
                    String content = inputNode != null && inputNode.has("content") ? inputNode.get("content").asText() : "";
                    return assistantTools.sendDoctorNotice(doctor, type, content, toolContext);
                }
                case "inboundProductStock": {
                    String name = inputNode != null && inputNode.has("productName") ? inputNode.get("productName").asText() : "";
                    Integer qty = inputNode != null && inputNode.has("quantity") ? inputNode.get("quantity").asInt() : 0;
                    return assistantTools.inboundProductStock(name, qty, toolContext);
                }
                case "changeProductSaleStatus": {
                    String name = inputNode != null && inputNode.has("productName") ? inputNode.get("productName").asText() : "";
                    String action = inputNode != null && inputNode.has("action") ? inputNode.get("action").asText() : "上架";
                    return assistantTools.changeProductSaleStatus(name, action, toolContext);
                }
                case "queryPlasterStatistics": {
                    String month = inputNode != null && inputNode.has("month") ? inputNode.get("month").asText() : null;
                    String cat = inputNode != null && inputNode.has("category") ? inputNode.get("category").asText() : null;
                    return assistantTools.queryPlasterStatistics(month, cat, toolContext);
                }
                default:
                    return "⚠️ 未知工具: " + actionName;
            }
        } catch (Exception e) {
            log.error("[ReAct] 执行工具 {} 异常: {}", actionName, e.getMessage(), e);
            return "执行失败: " + e.getMessage();
        }
    }

    private String extractField(String text, String... prefixes) {
        for (String line : text.split("\n")) {
            for (String prefix : prefixes) {
                if (line.trim().startsWith(prefix)) {
                    return line.trim().substring(prefix.length()).trim();
                }
            }
        }
        return "";
    }

    private String extractFinalAnswer(String text) {
        int idx = text.indexOf("Final Answer:");
        if (idx == -1) idx = text.indexOf("Final Answer：");
        if (idx != -1) {
            return text.substring(idx + 13).trim();
        }
        return text;
    }
}
