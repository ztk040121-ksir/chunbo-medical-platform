package com.chunbo.medical.service;

import com.chunbo.medical.vo.TextTemplateVO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 通用文本处理服务（参照《SpringAI》笔记通用文本模型）
 * 帮写/续写/润色/精简/联想词等文本处理，统一由独立的 textChatClient 完成
 * （不与业务智能体共享 ChatClient 对象，system prompt 亦独立）
 */
@Service
public class ChatTextService {

    /** 通用文本助手独立 system prompt（笔记标准） */
    private static final String TEXT_SYSTEM_PROMPT = """
            你是春播万象平台的专业文本处理助手，根据用户给定的指令与文本，完成帮写、续写、润色、精简、联想等文本处理任务。
            要求：直接输出处理结果本身，不要任何解释、前缀或多余格式。
            """;

    @Autowired
    @Qualifier("textChatClient")
    private ChatClient textChatClient;

    /** 下发全部文本处理模板（$input 为用户输入占位符） */
    public TextTemplateVO getTemplates() {
        return new TextTemplateVO();
    }

    /**
     * 文本处理：按模板类型渲染 $input 后交给独立文本 ChatClient 处理
     *
     * @param type  模板类型 associationalWord/helpedWrite/continuedWrite/polish/streamline
     * @param input 用户输入内容
     */
    public String process(String type, String input) {
        TextTemplateVO vo = new TextTemplateVO();
        String template = switch (type == null ? "" : type) {
            case "associationalWord" -> vo.getAssociationalWord();
            case "helpedWrite" -> vo.getHelpedWrite();
            case "continuedWrite" -> vo.getContinuedWrite();
            case "polish" -> vo.getPolish();
            case "streamline" -> vo.getStreamline();
            default -> null;
        };
        if (template == null) {
            throw new IllegalArgumentException("不支持的文本处理类型: " + type);
        }
        String rendered = template.replace("$input", input == null ? "" : input);
        return textChatClient.prompt()
                .system(TEXT_SYSTEM_PROMPT)
                .user(rendered)
                .call()
                .content();
    }
}
