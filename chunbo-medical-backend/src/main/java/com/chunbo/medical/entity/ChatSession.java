package com.chunbo.medical.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 会话历史实体（参照《SpringAI》笔记 ChatSession）
 * 持久化到 MySQL，记录每个智能体会话的标题、更新时间，支持历史查询/删除/更新标题
 */
@Data
@TableName("chat_session")
public class ChatSession {

    /** 数据 id */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 会话 id（与 ChatMemory 的 conversationId 对应） */
    private String sessionId;

    /** 业务类型：medical 云诊所问诊 / oa 中台助手 / mall 商城药师 */
    private String bizType;

    /** 用户标识（云诊所/OA 为 staffId，商城为 phone） */
    private String userId;

    /** 会话标题 */
    private String title;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
