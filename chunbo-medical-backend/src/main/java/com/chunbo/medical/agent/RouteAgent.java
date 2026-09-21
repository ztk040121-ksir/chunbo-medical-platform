package com.chunbo.medical.agent;

import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import reactor.core.publisher.Flux;

/**
 * 路由智能体基类（参照《SpringAI》笔记多智能体协作标准实现）
 * 用一次非流式 LLM 调用分析用户意图，返回业务智能体类型名称（如 MALL_RECOMMEND / MED_DIAGNOSE / OA_SALARY）。
 * 各业务域继承本类，仅需 override systemMessage()（路由 prompt）与 getAgentType()。
 */
public abstract class RouteAgent extends AbstractAgent {

    @Autowired
    protected ChatModel chatModel;

    /**
     * 意图分析：用独立 LLM 调用判断用户意图，返回智能体类型名称
     */
    @Override
    public String process(String question, String sessionId, String userId) {
        try {
            String content = ChatClient.builder(chatModel).build()
                    .prompt()
                    .system(systemMessage())
                    .user(question)
                    .call()
                    .content();
            return content == null ? null : content.trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 路由智能体不参与流式内容输出，返回空流
     */
    @Override
    protected Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId) {
        return Flux.empty();
    }
}
