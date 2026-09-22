package com.chunbo.medical.constant;

/**
 * 多智能体工具上下文常量（参照《SpringAI》笔记 tjxt 标准实现的 Constant）
 * 工具通过 ToolContext 拿到本次请求的 requestId，把结构化结果存入 ToolResultHolder，
 * 智能体流式输出结束后用 requestId 取回，转成 PARAM 事件下发给前端渲染卡片。
 */
public final class AgentConstant {

    private AgentConstant() {
    }

    /** 本次请求唯一标识（放入 ToolContext，工具据此关联 ToolResultHolder） */
    public static final String REQUEST_ID = "requestId";

    /** 当前用户标识（工号 staffId / 医生工号 doctorId / 商城手机号 phone） */
    public static final String USER_ID = "userId";

    /** 当前用户角色（DOCTOR / HR / MERCHANT / ADMIN），用于工具内部 RBAC 权限隔离 */
    public static final String ROLE = "role";

    /** 当前接诊患者档案 ID（问诊域） */
    public static final String PATIENT_ID = "patientId";

    /** 当前门诊病历摘要（问诊域，AI 辨证依据） */
    public static final String EMR_CONTEXT = "emrContext";

    /** 会话标识 */
    public static final String SESSION_ID = "sessionId";

    /** 附件标识（图片/Excel 等文件上传后的 fileId，供多模态智能体读取识别） */
    public static final String ATTACHMENT_ID = "attachmentId";

    /** 附件原始文件名（用于从文件名自动识别发放月份，如"工资表-2026年10月.xlsx"→2026-10） */
    public static final String ATTACHMENT_FILE_NAME = "attachmentFileName";

    /** 路由智能体判出的业务意图提示（如 MALL_SHIPPING / OA_SALARY / MED_DIAGNOSE） */
    public static final String ROUTE_HINT = "routeHint";

    /**
     * 工具内部字段描述常量（供 @ToolParam(description=...) 复用，避免散落魔法字符串）
     */
    public static final class ToolParams {
        /** 医生工号或姓名 */
        public static final String DOCTOR_ID = "医生工号或姓名，如 DOC_1001 或 李文华";
        /** 查询月份 yyyy-MM */
        public static final String MONTH = "查询月份，格式 yyyy-MM，如 2026-08";
        /** 患者档案 ID */
        public static final String PATIENT_ID = "就诊患者档案ID";
        /** 药品关键词 */
        public static final String MEDICINE_KEYWORD = "药品通用名关键词，如 布洛芬、硝苯地平";
        /** 订单流水号 */
        public static final String ORDER_NO = "订单/交易流水号";
    }
}
