package com.chunbo.medical.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.OaApproval;
import com.chunbo.medical.entity.OaPlasterRecord;
import com.chunbo.medical.entity.OaSalarySlip;
import com.chunbo.medical.entity.SysTokenLog;
import com.chunbo.medical.mapper.OaApprovalMapper;
import com.chunbo.medical.mapper.OaPlasterRecordMapper;
import com.chunbo.medical.mapper.OaSalarySlipMapper;
import com.chunbo.medical.mapper.SysTokenLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OaAssistantService {

    @Autowired
    private OaSalarySlipMapper salarySlipMapper;

    @Autowired
    private OaPlasterRecordMapper plasterRecordMapper;

    @Autowired
    private OaApprovalMapper approvalMapper;

    @Autowired
    private SysTokenLogMapper tokenLogMapper;

    public List<OaSalarySlip> getSalarySlips(String doctorId) {
        LambdaQueryWrapper<OaSalarySlip> qw = new LambdaQueryWrapper<>();
        if (doctorId != null && !doctorId.isEmpty()) {
            qw.eq(OaSalarySlip::getDoctorId, doctorId);
        }
        qw.orderByDesc(OaSalarySlip::getSalaryMonth);
        return salarySlipMapper.selectList(qw);
    }

    /** 按工号或姓名查询工资条（工号优先，未命中再按姓名匹配），避免"传姓名却硬回退 DOC_1001 查错人" */
    public List<OaSalarySlip> getSalarySlipsByIdOrName(String key) {
        LambdaQueryWrapper<OaSalarySlip> qw = new LambdaQueryWrapper<>();
        if (key != null && !key.isEmpty()) {
            qw.and(w -> w.eq(OaSalarySlip::getDoctorId, key).or().eq(OaSalarySlip::getDoctorName, key));
        }
        qw.orderByDesc(OaSalarySlip::getSalaryMonth);
        return salarySlipMapper.selectList(qw);
    }

    public List<OaPlasterRecord> getPlasterRecords() {
        return plasterRecordMapper.selectList(new LambdaQueryWrapper<OaPlasterRecord>().orderByDesc(OaPlasterRecord::getTherapyDate));
    }

    public Map<String, Object> getPlasterSummary() {
        List<OaPlasterRecord> list = plasterRecordMapper.selectList(null);
        int totalCount = 0;
        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (OaPlasterRecord r : list) {
            totalCount += r.getPasteCount();
            totalRevenue = totalRevenue.add(r.getTotalAmount());
        }
        Map<String, Object> map = new HashMap<>();
        map.put("totalPasteCount", totalCount);
        map.put("totalRevenue", totalRevenue);
        map.put("recordCount", list.size());
        map.put("records", list);
        return map;
    }

    public List<OaApproval> getApprovals() {
        return approvalMapper.selectList(new LambdaQueryWrapper<OaApproval>().orderByDesc(OaApproval::getCreateTime));
    }

    public OaApproval createApproval(OaApproval approval) {
        if (approval.getStatus() == null) {
            approval.setStatus("待审批");
        }
        approval.setCreateTime(LocalDateTime.now());
        approvalMapper.insert(approval);
        return approval;
    }

    public OaApproval processApproval(Long id, String status, String approver, String comment) {
        OaApproval ap = approvalMapper.selectById(id);
        if (ap != null) {
            ap.setStatus(status);
            ap.setApproverName(approver);
            ap.setComment(comment);
            approvalMapper.updateById(ap);
        }
        return ap;
    }

    public void logTokenUsage(String sessionId, String modelName, int promptTokens, int completionTokens, long latencyMs) {
        SysTokenLog log = new SysTokenLog();
        log.setSessionId(sessionId);
        log.setModelName(modelName);
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setTotalTokens(promptTokens + completionTokens);
        log.setLatencyMs(latencyMs);

        // gpt-4o-mini 定价估算: 输入约 0.001元/k token, 输出约 0.004元/k token
        BigDecimal cost = new BigDecimal(promptTokens).multiply(new BigDecimal("0.000001"))
                .add(new BigDecimal(completionTokens).multiply(new BigDecimal("0.000004")));
        log.setCostCny(cost);
        log.setCreateTime(LocalDateTime.now());
        tokenLogMapper.insert(log);
    }

    public Map<String, Object> getTokenStats() {
        List<SysTokenLog> logs = tokenLogMapper.selectList(
                new LambdaQueryWrapper<SysTokenLog>().orderByDesc(SysTokenLog::getCreateTime)
        );
        int totalTokens = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        long totalLatency = 0;
        for (SysTokenLog l : logs) {
            totalTokens += (l.getTotalTokens() != null ? l.getTotalTokens() : 0);
            totalCost = totalCost.add(l.getCostCny() != null ? l.getCostCny() : BigDecimal.ZERO);
            totalLatency += (l.getLatencyMs() != null ? l.getLatencyMs() : 0);
        }
        long avgLatency = logs.isEmpty() ? 0 : totalLatency / logs.size();

        Map<String, Object> res = new HashMap<>();
        res.put("totalCalls", logs.size());
        res.put("totalTokens", totalTokens);
        res.put("totalCostCny", totalCost);
        res.put("avgLatencyMs", avgLatency);
        res.put("recentLogs", logs.stream().limit(10).toList());
        return res;
    }
}