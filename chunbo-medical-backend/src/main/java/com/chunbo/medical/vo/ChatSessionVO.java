package com.chunbo.medical.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话历史 VO（参照《SpringAI》笔记 ChatSessionVO）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSessionVO {

    /** 会话 id */
    private String sessionId;

    /** 会话标题 */
    private String title;

    /** 更新时间 */
    private LocalDateTime updateTime;
}
