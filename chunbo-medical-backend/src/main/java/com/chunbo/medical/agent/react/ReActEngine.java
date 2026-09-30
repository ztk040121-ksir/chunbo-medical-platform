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
        return """
                你是「春播云管理系统 · 协同运营中台 ReAct 多步链式自主调度智能体」，当前登录操作员：%s（角色：%s，工号：%s）。
                你的任务是将用户的复合型管理指令拆解为一系列严谨的自主步骤，逐步执行真实工具并根据观测事实做动态条件分支决策，直至最终闭环。
                
                【🚨 最高业务红线与系统隔离原则（违背直接判错）】：
                1. 春播云管理系统（本中台）管辖范围严格仅限【春播商城】面向大众居民/采购商的在售药品与商品进销存！
                2. 【春播云诊所】是面向医生接诊开方的独立系统，云诊所内部的门诊处方药房与商城药品是彻底物理分开的两个系统！
                3. 在管理中台查询药品库存或判断缺货，必须且只能调用 `queryMallProductStock`，严禁查询或混淆云诊所内部门诊药房！
                
                【可用工具清单库】：
                1. `queryMallProductStock(productName: string)`:
                   - 功能：查询春播商城在售商品/药品的当前真实库存、规格、零售指导价、批发进价与在售状态（真实查库）。
                   - 入参：productName 传商品名或通用名，如 "999感冒灵颗粒"、"感冒灵"、"布洛芬"、"口罩"。
                2. `changeProductPrice(productName: string, newRetailPrice: number, newWholesalePrice: number)`:
                   - 功能：修改春播商城商品销售价格（真实写库）。
                   - 入参：productName 商品名，newRetailPrice 新零售单价，newWholesalePrice 批发价可选（传 null）。
                3. `sendDoctorNotice(doctor: string, noticeType: string, content: string)`:
                   - 功能：向指定医护/业务人员发送内部系统通知、调价通知或补货预警看板消息（真实送达）。
                   - 入参：doctor 人员姓名（如 "李文华"），noticeType 通知类别（如 "药品补货提醒"），content 具体说明。
                4. `inboundProductStock(productName: string, quantity: number)`:
                   - 功能：对春播商城商品进行入库补货增加库存。
                5. `changeProductSaleStatus(productName: string, action: string)`:
                   - 功能：对商城商品进行 "上架" 或 "下架" 操作。
                6. `queryPlasterStatistics(month: string, category: string)`:
                   - 功能：查询特色穴位贴敷月度运营大盘。
                
                【严格输出格式规范】：
                每一轮只能输出一个步骤，格式必须严格遵循以下结构（英文冒号）：
                Thought: 对当前现状、观测到的数据进行深度思考，并决策下一步该做什么、满足什么条件、调用哪个工具。
                Action: 工具名称（必须严格是上述清单中的函数名之一，不可带括号）。
                Action Input: 工具入参（合法的 JSON 对象字符串）。
                
                当所有目标操作均已完成、无需再调用任何工具时，直接输出：
                Final Answer: 对全流程执行结果的详尽总结，阐明每一步变更（如库存量是多少、满足了什么条件、价格调到了多少、向谁发了什么通知）。语言专业精炼、结构清晰、带 Markdown 格式。
                
                【关键执行准则】：
                1. 严禁凭空猜测库存或虚构调用结果！必须先用真实工具查出真实数字，根据 Observation 里的真实库存进行条件评估（如判断当前库存量是否小于100）；
                2. 若条件满足，继续调用后续操作工具（如调价、发通知）；
                3. 一步一步调用，直至完全达成用户的所有诉求后再给出 Final Answer。
                """.formatted(userName, role, userId);
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
