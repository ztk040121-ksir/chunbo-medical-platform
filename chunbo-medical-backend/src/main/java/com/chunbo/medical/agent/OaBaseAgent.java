package com.chunbo.medical.agent;

import com.chunbo.medical.controller.AssistantController;
import com.chunbo.medical.entity.DoctorAccount;
import com.chunbo.medical.entity.StaffAccount;
import com.chunbo.medical.mapper.DoctorAccountMapper;
import com.chunbo.medical.mapper.StaffAccountMapper;
import com.chunbo.medical.tools.ClinicAssistantTools;
import com.chunbo.medical.vo.ChatEventVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import reactor.core.publisher.Flux;

/**
 * 中台业务智能体基类（参照《SpringAI》笔记 tjxt 多智能体标准实现）
 * 每个中台意图（薪资/订单/库存/大盘/审批/贴敷/通用）一个独立 Agent 类，
 * 路由判出的意图由 AgentRouter 直接命中对应子类，真正分流到不同中台技能。
 * 注：用 @Lazy 注入 Controller 以打破 Controller↔Agent 循环依赖。
 */
public abstract class OaBaseAgent extends AbstractAgent {

    @Autowired
    @Lazy
    protected AssistantController assistantController;

    @Autowired(required = false)
    protected StaffAccountMapper staffAccountMapper;

    @Autowired(required = false)
    protected DoctorAccountMapper doctorAccountMapper;

    @Autowired
    protected ClinicAssistantTools assistantTools;

    @Override
    public String bizType() {
        return "oa";
    }

    /** 子类返回本智能体对应的固定路由意图（如 OA_SALARY / OA_APPROVAL） */
    protected abstract String skillHint();

    @Override
    protected Flux<ChatEventVO> buildContentFlux(String question, String sessionId, String userId) {
        // 依据 userId（staffId/工号）还原真实角色与姓名，保证薪资等技能的 RBAC 权限隔离生效
        String role = resolveRole(userId);
        String name = resolveName(userId, role);

        // 薪资绩效：升级为真正的 function-calling，LLM 自主调用 querySalarySlip（工具内部做 RBAC 校验）
        if ("OA_SALARY".equals(skillHint())) {
            String sys = buildSalarySystemPrompt(name, role, userId);
            return functionCallingFlux(question, sessionId, userId, role, sys, assistantTools);
        }
        return assistantController.buildOaContentFlux(question, userId, role, name, skillHint());
    }

    /** 薪资智能体 system prompt：引导 LLM 先调工具核对工资条，同时向 LLM 声明当前身份供透明化 */
    private String buildSalarySystemPrompt(String name, String role, String userId) {
        return "你是「春播云管理系统中台 · 薪酬绩效智能体」，当前登录用户：" + name + "（角色：" + role + "，工号：" + userId + "）。\n"
                + "【查询工资必须自主调用工具】\n"
                + "1. 用户查询自己或他人工资时，必须调用 querySalarySlip(doctorId, month) 获取真实工资条数据；\n"
                + "2. 工具的权限由系统后台强制校验（医生只能查自己，管理员/人事可查任意员工），你无需自行判断权限；\n"
                + "3. 拿到工具返回的工资数据后，用清晰的 Markdown 表格呈现。\n"
                + "【注意】不要编造任何工资数字，一切以工具返回的真实数据为准。";
    }

    /** 按工号前缀还原角色（DOC_ 医生 / HR_ 人事 / MERCH_ 商户 / 其它 ADMIN），并以 DB 为准修正 */
    private String resolveRole(String userId) {
        String id = userId == null ? "" : userId.toUpperCase();
        String role;
        if (id.startsWith("DOC_")) role = "DOCTOR";
        else if (id.startsWith("HR_")) role = "HR";
        else if (id.startsWith("MERCH_")) role = "MERCHANT";
        else role = "ADMIN";
        try {
            if (staffAccountMapper != null) {
                StaffAccount sa = staffAccountMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StaffAccount>()
                                .eq(StaffAccount::getStaffId, userId));
                if (sa != null && sa.getRole() != null && !sa.getRole().isEmpty()) role = sa.getRole().toUpperCase();
            }
        } catch (Exception ignored) {
        }
        return role;
    }

    /** 还原真实姓名：员工档案 realName > 医生档案 doctorName > userId 本身 */
    private String resolveName(String userId, String role) {
        try {
            if ("DOCTOR".equals(role) && doctorAccountMapper != null) {
                DoctorAccount da = doctorAccountMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DoctorAccount>()
                                .eq(DoctorAccount::getDoctorId, userId));
                if (da != null && da.getDoctorName() != null && !da.getDoctorName().isEmpty()) return da.getDoctorName();
            }
            if (staffAccountMapper != null) {
                StaffAccount sa = staffAccountMapper.selectOne(
                        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<StaffAccount>()
                                .eq(StaffAccount::getStaffId, userId));
                if (sa != null && sa.getRealName() != null && !sa.getRealName().isEmpty()) return sa.getRealName();
            }
        } catch (Exception ignored) {
        }
        return userId == null || userId.isEmpty() ? "系统用户" : userId;
    }
}
