package com.chunbo.medical.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聊天事件封装（参照《SpringAI》笔记课程卡片标准实现）
 * 把原本纯文本的 SSE 流升级为「事件流」：文字走 DATA 事件，结构化卡片走 PARAM 事件，结束走 STOP 事件
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEventVO {

    /**
     * 事件携带的数据：DATA 事件为文本字符串，PARAM 事件为结构化 Map
     */
    private Object eventData;

    /**
     * 事件类型：1001-数据事件(文字) / 1002-停止事件 / 1003-参数事件(结构化卡片)
     */
    private int eventType;
}
