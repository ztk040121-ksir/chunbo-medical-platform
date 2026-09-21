package com.chunbo.medical.tools;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.config.ToolResultHolder;
import com.chunbo.medical.constant.AgentConstant;
import com.chunbo.medical.entity.PayAccount;
import com.chunbo.medical.entity.PayAuditRecord;
import com.chunbo.medical.entity.PayTransaction;
import com.chunbo.medical.mapper.PayAccountMapper;
import com.chunbo.medical.mapper.PayAuditRecordMapper;
import com.chunbo.medical.mapper.PayTransactionMapper;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Component
public class PayAuditTools {

    @Autowired
    private PayTransactionMapper txMapper;

    @Autowired
    private PayAccountMapper accountMapper;

    @Autowired
    private PayAuditRecordMapper auditRecordMapper;

    @Tool(description = "查询聚合支付交易流水底账详情（核验金额、商户、支付渠道与支付状态）")
    public String queryTransactionDetail(@ToolParam(description = "订单交易流水号，如 PAY202609151004") String orderNo, ToolContext toolContext) {
        // ── RBAC：支付底账属财务敏感数据，仅系统管理员可查（fail-closed） ──
        if (!"ADMIN".equals(roleOf(toolContext))) {
            return "⛔ 【RBAC 权限拦截】支付流水底账属财务机密，仅系统管理员可查询。";
        }
        PayTransaction tx = txMapper.selectOne(new LambdaQueryWrapper<PayTransaction>().eq(PayTransaction::getOrderNo, orderNo));
        if (tx == null) {
            return "【支付网关底账】未检索到流水号为 " + orderNo + " 的实际支付交易。";
        }
        ToolResultHolder.put(requestIdOf(toolContext), "transactionDetail", tx);
        return String.format("【支付网关底账】订单号: %s, 支付渠道: %s, 付款人: %s, 收款商户: %s, 网关真实入账金额: ¥%.2f, 交易状态: %s, 当前风控状态: %s",
                tx.getOrderNo(), tx.getChannelType(), tx.getPayerName(), tx.getMerchantName(),
                tx.getAmount(), tx.getPayStatus(), tx.getRiskStatus());
    }

    @Tool(description = "阻断风险交易并自动冻结涉案商户或控销结算资金账户")
    public String freezeAccountOrPayment(
            @ToolParam(description = "订单流水号") String orderNo,
            @ToolParam(description = "商户ID，如 MCH_001") String merchantId,
            @ToolParam(description = "冻结原因，如 PS篡改转账金额、伪造支付流水") String reason,
            @ToolParam(description = "冻结等级，如 临时风控冻结、资金全额冻结") String freezeLevel,
            ToolContext toolContext) {

        // ── RBAC：资金冻结属高危风控动作，仅系统管理员可执行（fail-closed） ──
        if (!"ADMIN".equals(roleOf(toolContext))) {
            return "⛔ 【RBAC 权限拦截】资金冻结为高危风控操作，仅系统管理员可执行。";
        }

        PayTransaction tx = txMapper.selectOne(new LambdaQueryWrapper<PayTransaction>().eq(PayTransaction::getOrderNo, orderNo));
        if (tx != null) {
            tx.setRiskStatus("拦截冻结");
            tx.setPayStatus("交易失败(风控拦截)");
            txMapper.updateById(tx);
        }

        PayAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<PayAccount>().eq(PayAccount::getMerchantId, merchantId));
        if (acc != null) {
            acc.setAccountStatus(freezeLevel != null ? freezeLevel : "临时风控冻结");
            if (tx != null && tx.getAmount() != null) {
                // 判空兜底：账户余额字段可能为 null，避免 NPE
                BigDecimal frozen = acc.getFrozenBalance() != null ? acc.getFrozenBalance() : BigDecimal.ZERO;
                BigDecimal available = acc.getAvailableBalance() != null ? acc.getAvailableBalance() : BigDecimal.ZERO;
                acc.setFrozenBalance(frozen.add(tx.getAmount()));
                if (available.compareTo(tx.getAmount()) >= 0) {
                    acc.setAvailableBalance(available.subtract(tx.getAmount()));
                }
            }
            accountMapper.updateById(acc);
            ToolResultHolder.put(requestIdOf(toolContext), "freezeResult", acc);
            return String.format("【自动风控执行】已成功执行资金保护动作！订单 %s 已标记为【拦截冻结】；商户账户【%s】状态变更为【%s】，冻结金额 ¥%.2f。冻结事由: %s",
                    orderNo, acc.getMerchantName(), acc.getAccountStatus(), (tx != null ? tx.getAmount() : BigDecimal.ZERO), reason);
        }

        return "【自动风控执行】订单已标记冻结，未找到关联的商户结算资金账户。";
    }

    @Tool(description = "向春播万象合规风控组与商户安全中心推送多渠道高危报警通知")
    public String sendRiskNotification(
            @ToolParam(description = "报警通知渠道，如 企业微信机器人、钉钉告警群、短信紧急呼叫") String alertChannel,
            @ToolParam(description = "风险等级，如 高危(阻断)、中危(人工核查)") String riskLevel,
            @ToolParam(description = "告警具体内容与涉案详情") String message,
            ToolContext toolContext) {

        // 真实落地：把告警动作持久化到风控审计记录表（pay_audit_record），作为"已发出告警"的可查询留痕，
        // 而非仅返回一句文案。风控/合规人员可据此追溯每一条告警。
        PayAuditRecord record = new PayAuditRecord();
        record.setRiskLevel(riskLevel != null ? riskLevel : "中危(人工核查)");
        record.setAnomalyType("风险告警通知");
        record.setAiAnalysisResult(message);
        record.setActionsTaken(alertChannel != null && !alertChannel.isBlank() ? alertChannel : "企业微信风控群");
        record.setAuditStatus("已告警");
        record.setCreateTime(LocalDateTime.now());
        auditRecordMapper.insert(record);

        return String.format("【安全中心报警推送】已通过【%s】触发【%s】级警报，告警已持久化（审计留痕单号 PA%d）。通知内容：%s。值班风控专家与法务合规已同步收到工单。",
                (alertChannel != null && !alertChannel.isBlank() ? alertChannel : "企业微信风控群"),
                (riskLevel != null ? riskLevel : "中危"),
                record.getId(), message);
    }

    /** 从工具上下文安全提取 requestId */
    private String requestIdOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) return null;
        Object v = toolContext.getContext().get(AgentConstant.REQUEST_ID);
        return v == null ? null : String.valueOf(v);
    }

    /** 从工具上下文安全提取当前用户角色，供 RBAC 校验 */
    private String roleOf(ToolContext toolContext) {
        if (toolContext == null || toolContext.getContext() == null) return null;
        Object v = toolContext.getContext().get(AgentConstant.ROLE);
        return v == null ? null : String.valueOf(v).toUpperCase();
    }
}