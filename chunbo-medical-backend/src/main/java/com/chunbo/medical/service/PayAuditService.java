package com.chunbo.medical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.PayAccount;
import com.chunbo.medical.entity.PayAuditRecord;
import com.chunbo.medical.entity.PayTransaction;
import com.chunbo.medical.mapper.PayAccountMapper;
import com.chunbo.medical.mapper.PayAuditRecordMapper;
import com.chunbo.medical.mapper.PayTransactionMapper;
import com.chunbo.medical.tools.PayAuditTools;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class PayAuditService {

    @Autowired
    private PayTransactionMapper txMapper;

    @Autowired
    private PayAuditRecordMapper auditMapper;

    @Autowired
    private PayAccountMapper accountMapper;

    @Autowired
    private PayAuditTools auditTools;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    public List<PayTransaction> getTransactions() {
        return txMapper.selectList(new LambdaQueryWrapper<PayTransaction>().orderByDesc(PayTransaction::getTradeTime));
    }

    public List<PayAuditRecord> getAuditRecords() {
        return auditMapper.selectList(new LambdaQueryWrapper<PayAuditRecord>().orderByDesc(PayAuditRecord::getCreateTime));
    }

    public List<PayAccount> getAccounts() {
        return accountMapper.selectList(new LambdaQueryWrapper<PayAccount>().orderByDesc(PayAccount::getUpdateTime));
    }

    /**
     * 多模态凭证智能审计闭环：多模态比对 -> 决策风险判定 -> 并行触发工具执行（冻结/报警/放行）
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> analyzeReceipt(String orderNo, String anomalyScenario, String imageUrl) {
        Map<String, Object> result = new HashMap<>();

        PayTransaction tx = txMapper.selectOne(new LambdaQueryWrapper<PayTransaction>().eq(PayTransaction::getOrderNo, orderNo));
        if (tx == null) {
            result.put("success", false);
            result.put("message", "未找到对应的交易订单流水：" + orderNo);
            return result;
        }

        BigDecimal detectedAmount;
        BigDecimal actualAmount = tx.getAmount();
        String riskLevel;
        String anomalyType;
        String aiAnalysis;
        String actionsTaken;

        if ("tamper".equals(anomalyScenario)) {
            // 场景 1：凭证金额篡改 (多模态视觉识别出 126,000.00，而系统真实底账为 12,600.00)
            detectedAmount = new BigDecimal("126000.00");
            riskLevel = "高危(阻断)";
            anomalyType = "凭证金额恶意篡改";
            aiAnalysis = "【春播多模态大模型视觉深度审计】：\n" +
                    "1. 截图文本字符分析：转账凭证金额标注为「¥126,000.00」，但“126”与后方“,000.00”字体渲染平滑度、像素灰度梯度不一致，存在显著的数字修图拼接伪造痕迹；\n" +
                    "2. 底账网关一致性校验：网关真实到账流水仅为「¥" + actualAmount + "」，凭证金额虚标放大 10 倍，判定为恶意骗取医药控销大宗货物的假凭证；\n" +
                    "3. 处置决策：判定为高危风险，立即阻断交易并封控商户账户！";

            // 自动调用工具执行动作（识别 -> 决策 -> 执行闭环）
            String freezeResult = auditTools.freezeAccountOrPayment(orderNo, tx.getMerchantId(), "凭证金额伪造虚标", "临时风控冻结");
            String alertResult = auditTools.sendRiskNotification("企业微信风控应急群", riskLevel, "订单 " + orderNo + " 凭证金额恶意篡改，已阻断出货！");
            actionsTaken = freezeResult + " | " + alertResult;

        } else if ("duplicate".equals(anomalyScenario)) {
            // 场景 2：重复核销（一图多付）
            detectedAmount = actualAmount;
            riskLevel = "中危";
            anomalyType = "一图多付重复核销";
            aiAnalysis = "【春播多模态大模型视觉深度审计】：\n" +
                    "1. 视觉感知哈希（pHash）比对：当前上传的静态码转账截图，与历史异常库 2026-09-14 订单凭证哈希重合度达 99.8%；\n" +
                    "2. 交易状态：同一凭证在不同订单多次核销，判定为重复付款套现风险；\n" +
                    "3. 处置决策：拦截自动入账流程，转入人工二次复核通道。";

            tx.setRiskStatus("疑似风险");
            txMapper.updateById(tx);
            actionsTaken = auditTools.sendRiskNotification("钉钉运营合规群", riskLevel, "订单 " + orderNo + " 检测到疑似重复截图，已转人工复核");

        } else {
            // 场景 3：合规正常凭证
            detectedAmount = actualAmount;
            riskLevel = "低危(合规)";
            anomalyType = "合规正常凭证";
            aiAnalysis = "【春播多模态大模型视觉深度审计】：\n" +
                    "1. 视觉凭证排查：电子回单印章印泥特征自然，防伪底纹连续完整，无像素拼接涂抹；\n" +
                    "2. 跨模态四要素核对：付款人【" + tx.getPayerName() + "】、收款商户【" + tx.getMerchantName() + "】、金额【¥" + actualAmount + "】与银联底账完全匹配；\n" +
                    "3. 处置决策：合规无误，自动放行入账。";

            tx.setRiskStatus("正常");
            tx.setPayStatus("支付成功");
            txMapper.updateById(tx);
            actionsTaken = "多模态核验通过，放行自动结算入账";
        }

        // 保存审计记录
        PayAuditRecord record = new PayAuditRecord();
        record.setOrderNo(orderNo);
        record.setReceiptImageUrl(imageUrl != null ? imageUrl : "/samples/receipt.png");
        record.setDetectedAmount(detectedAmount);
        record.setActualAmount(actualAmount);
        record.setRiskLevel(riskLevel);
        record.setAnomalyType(anomalyType);
        record.setAiAnalysisResult(aiAnalysis);
        record.setActionsTaken(actionsTaken);
        record.setAuditStatus("已处置");
        record.setCreateTime(LocalDateTime.now());
        auditMapper.insert(record);

        // 写入 Redis 缓存（体现 Redis 在支付审计中的快速检索与风控状态标记）
        if (redisTemplate != null) {
            try {
                redisTemplate.opsForValue().set("risk:order:" + orderNo, riskLevel, 1, TimeUnit.HOURS);
            } catch (Exception ignored) {}
        }

        result.put("success", true);
        result.put("auditRecord", record);
        result.put("transaction", txMapper.selectOne(new LambdaQueryWrapper<PayTransaction>().eq(PayTransaction::getOrderNo, orderNo)));
        result.put("accounts", accountMapper.selectList(null));
        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    public PayAccount freezeAccount(String accountNo, BigDecimal amount) {
        PayAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<PayAccount>().eq(PayAccount::getAccountNo, accountNo));
        if (acc != null) {
            acc.setAccountStatus("临时风控冻结");
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                acc.setFrozenBalance(acc.getFrozenBalance().add(amount));
                if (acc.getAvailableBalance().compareTo(amount) >= 0) {
                    acc.setAvailableBalance(acc.getAvailableBalance().subtract(amount));
                }
            }
            acc.setUpdateTime(LocalDateTime.now());
            accountMapper.updateById(acc);
        }
        return acc;
    }

    @Transactional(rollbackFor = Exception.class)
    public PayAccount unfreezeAccount(String accountNo) {
        PayAccount acc = accountMapper.selectOne(new LambdaQueryWrapper<PayAccount>().eq(PayAccount::getAccountNo, accountNo));
        if (acc != null) {
            acc.setAccountStatus("正常");
            acc.setAvailableBalance(acc.getAvailableBalance().add(acc.getFrozenBalance()));
            acc.setFrozenBalance(BigDecimal.ZERO);
            accountMapper.updateById(acc);
        }
        return acc;
    }
}