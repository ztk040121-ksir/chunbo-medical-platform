package com.chunbo.medical.vo;

import lombok.Data;

/**
 * 通用文本处理模板 VO（参照《SpringAI》笔记通用文本模型 TemplateVO）
 * 模板中的 $input 是用户输入内容的占位符
 */
@Data
public class TextTemplateVO {

    /** 联想词：根据关键词生成提问联想 */
    private String associationalWord = """
            用户输入关键词：$input|生成规则：生成3个，每个问题含【$input】不超过20字|输出要求：纯文本，问题间用|分隔
            """;

    /** 帮写：根据主题/关键词智能生成完整文案 */
    private String helpedWrite = """
            基于用户提供的主题/关键词，智能生成完整的文案内容（如文章、邮件、报告等），帮助用户快速搭建内容框架
            用户输入：
            $input
            """;

    /** 续写：在已有文本基础上延续写作思路 */
    private String continuedWrite = """
            在用户已有文本基础上，自动延续写作思路生成后续内容，保持上下文逻辑连贯性
            用户输入：
            $input
            """;

    /** 润色：语言优化（句式/词汇/风格） */
    private String polish = """
            对现有文本进行语言优化，包括调整句式结构、替换精准词汇、统一行文风格等
            用户输入：
            $input
            """;

    /** 精简：提炼核心信息压缩长文本 */
    private String streamline = """
            通过语义分析智能提炼核心信息，删除冗余表达，将长文本压缩为简洁版本
            用户输入：
            $input
            """;
}
