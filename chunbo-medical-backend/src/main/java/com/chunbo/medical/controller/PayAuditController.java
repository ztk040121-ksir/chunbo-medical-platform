package com.chunbo.medical.controller;

import com.chunbo.medical.entity.PayAccount;
import com.chunbo.medical.entity.PayAuditRecord;
import com.chunbo.medical.entity.PayTransaction;
import com.chunbo.medical.service.PayAuditService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = {"/api/pay-audit", "/api/pay"})
public class PayAuditController {

    @Autowired
    private PayAuditService auditService;

    @GetMapping(value = {"/transactions", "/audit/transactions"})
    public List<PayTransaction> getTransactions() {
        return auditService.getTransactions();
    }

    @GetMapping(value = {"/records", "/audit/records"})
    public List<PayAuditRecord> getAuditRecords() {
        return auditService.getAuditRecords();
    }

    @GetMapping(value = {"/accounts", "/audit/accounts"})
    public List<PayAccount> getAccounts() {
        return auditService.getAccounts();
    }

    @PostMapping(value = {"/analyze", "/audit/analyze"})
    public Map<String, Object> analyzeReceipt(@RequestBody Map<String, String> request) {
        String orderNo = request.getOrDefault("orderNo", "PAY202609151004");
        String scenario = request.getOrDefault("scenario", "tamper");
        String imageUrl = request.getOrDefault("imageUrl", "/samples/receipt.png");
        return auditService.analyzeReceipt(orderNo, scenario, imageUrl);
    }

        @PostMapping(value = {"/unfreeze", "/audit/unfreeze"})
    public PayAccount unfreezeAccount(@RequestBody Map<String, String> request) {
        String accountNo = request.getOrDefault("accountNo", "ACC_MCH_001");
        return auditService.unfreezeAccount(accountNo);
    }

    @PostMapping(value = {"/freeze", "/audit/freeze"})
    public PayAccount freezeAccount(@RequestBody Map<String, Object> request) {
        String accountNo = request.getOrDefault("accountNo", "ACC_MCH_001").toString();
        java.math.BigDecimal amount = request.containsKey("amount") && request.get("amount") != null
                ? new java.math.BigDecimal(request.get("amount").toString())
                : new java.math.BigDecimal("880.00");
        return auditService.freezeAccount(accountNo, amount);
    }
}
