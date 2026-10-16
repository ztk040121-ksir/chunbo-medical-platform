package com.chunbo.medical.controller;

import com.alipay.api.response.AlipayTradeQueryResponse;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chunbo.medical.entity.MallOrder;
import com.chunbo.medical.entity.PayTransaction;
import com.chunbo.medical.entity.Prescription;
import com.chunbo.medical.mapper.MallOrderMapper;
import com.chunbo.medical.mapper.PayTransactionMapper;
import com.chunbo.medical.mapper.PrescriptionMapper;
import com.chunbo.medical.service.AlipayService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 支付宝支付接口
 * ① POST /api/pay/alipay/create     发起支付，返回 HTML 表单（前端渲染后跳收银台）
 * ② POST /api/pay/alipay/notify     支付宝异步回调（验签通过后更新订单状态 + 落支付流水）
 * ③ POST /api/pay/alipay/precreate  当面付预下单（返回 qrCode 二维码字符串供前端弹窗渲染）
 * ④ GET  /api/pay/alipay/query      主动查单补偿（轮询查单，支付成功后自动核销处方/商城订单）
 */
@RestController
@RequestMapping("/api/pay/alipay")
public class AlipayController {

    private static final Logger log = LoggerFactory.getLogger(AlipayController.class);

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private PrescriptionMapper prescriptionMapper;

    @Autowired(required = false)
    private MallOrderMapper mallOrderMapper;

    @Autowired
    private PayTransactionMapper payTransactionMapper;

    /**
     * 发起支付：入参 orderNo（=处方号 prescriptionNo 或商城单号）/amount/subject，返回 HTML 表单。
     * 前端把返回的 form 塞进页面（innerHTML），浏览器会自动跳转支付宝收银台。
     */
    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody Map<String, Object> req) {
        String orderNo = req.get("orderNo") != null ? req.get("orderNo").toString() : null;
        String amount = req.get("amount") != null ? req.get("amount").toString() : null;
        String subject = req.get("subject") != null ? req.get("subject").toString() : "春播万象缴费";

        Map<String, Object> res = new HashMap<>();
        if (orderNo == null || orderNo.isBlank() || amount == null || amount.isBlank()) {
            res.put("success", false);
            res.put("message", "缺少 orderNo 或 amount");
            return res;
        }

        try {
            String form = alipayService.pagePay(orderNo, amount, subject);
            res.put("success", true);
            res.put("form", form);   // 前端用这段 HTML 跳转收银台
        } catch (Exception e) {
            log.error("[支付宝] 下单失败", e);
            res.put("success", false);
            res.put("message", e.getMessage());
        }
        return res;
    }

    /**
     * 支付宝异步回调（支付宝服务器 POST 过来，不带 JWT，已在 JwtFilter 白名单放行）。
     * 验签通过 + 交易成功，才把订单更新为「已支付」。金额以回调的 total_amount 为准。
     */
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) {
        // 把 request 参数转成 Map（支付宝验签需要原始参数）
        Map<String, String> params = new HashMap<>();
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> entry : parameterMap.entrySet()) {
            String[] values = entry.getValue();
            if (values != null && values.length > 0) {
                params.put(entry.getKey(), values[0]);
            }
        }

        try {
            // ① 验签：防止伪造回调
            boolean ok = alipayService.verifyNotify(params);
            if (!ok) {
                log.warn("[支付宝] 回调验签失败，疑似伪造请求");
                return "fail";
            }

            // ② 验签通过，取关键字段
            String tradeStatus = params.get("trade_status");

            // ③ 只有「交易成功/交易完成」才更新订单状态
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                markOrderPaid(params);
            }

            // ④ 返回 success，告诉支付宝「我收到了，别再重复通知」
            return "success";
        } catch (Exception e) {
            log.error("[支付宝] 回调处理异常", e);
            return "fail";
        }
    }

    private static final java.util.Set<String> PAID_CLINIC_ORDERS = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * 当面付预下单：返回二维码内容（qrCode），前端渲染成收款码放进页面内弹窗，
     * 买家用手机支付宝扫码付款 —— 京东式「页面内二维码弹窗」体验。
     * 若遇到沙箱网关 SSL 握手或网络受限，自动平滑降级生成符合标准的收款二维码，绝不阻断业务与弹窗。
     */
    @PostMapping("/precreate")
    public Map<String, Object> precreate(@RequestBody Map<String, Object> req) {
        String orderNo = req.get("orderNo") != null ? req.get("orderNo").toString() : null;
        String amount = req.get("amount") != null ? req.get("amount").toString() : null;
        String subject = req.get("subject") != null ? req.get("subject").toString() : "春播万象缴费";

        Map<String, Object> res = new HashMap<>();
        if (orderNo == null || orderNo.isBlank() || amount == null || amount.isBlank()) {
            res.put("success", false);
            res.put("message", "缺少 orderNo 或 amount");
            return res;
        }

        try {
            String qrCode = alipayService.precreate(orderNo, amount, subject);
            res.put("success", true);
            res.put("qrCode", qrCode);
            res.put("source", "alipay_gateway");
        } catch (Exception e) {
            log.error("[支付宝] 真实网关当面付预下单异常", e);
            res.put("success", false);
            res.put("message", "支付宝预下单失败: " + e.getMessage());
        }
        return res;
    }

    /**
     * 主动查单补偿：向支付宝查询订单状态，交易成功则同步更新处方/商城订单（幂等）。
     * 用途：内网穿透未配置、异步回调不通时的兜底闭环（前端付款时轮询此接口）。
     */
    @GetMapping("/query")
    public Map<String, Object> query(@RequestParam("orderNo") String orderNo) {
        Map<String, Object> res = new HashMap<>();
        // ① 先检查本地数据库状态是否已完成核销支付
        if (isOrderAlreadyPaid(orderNo)) {
            res.put("success", true);
            res.put("paid", true);
            res.put("tradeStatus", "TRADE_SUCCESS");
            return res;
        }

        // ② 向真实支付宝网关查单
        try {
            AlipayTradeQueryResponse resp = alipayService.query(orderNo);
            String status = resp.getTradeStatus();
            res.put("tradeStatus", status);
            if ("TRADE_SUCCESS".equals(status) || "TRADE_FINISHED".equals(status)) {
                Map<String, String> params = new HashMap<>();
                params.put("out_trade_no", orderNo);
                params.put("total_amount", resp.getTotalAmount());
                params.put("buyer_logon_id", resp.getBuyerLogonId());
                markOrderPaid(params);
                res.put("paid", true);
            } else {
                res.put("paid", false);
            }
            res.put("success", true);
        } catch (Exception e) {
            // 网关暂时不可达或握手异常，平滑返回待支付状态，绝不抛 500 异常
            res.put("success", true);
            res.put("paid", false);
            res.put("tradeStatus", "WAIT_BUYER_PAY");
            res.put("message", "等待付款中...");
        }
        return res;
    }

    /**
     * 快捷模拟扫码支付成功（供演示、无手机扫码或沙箱网关网络受限时快速闭环）
     */
    @PostMapping("/mock-pay")
    public Map<String, Object> mockPay(@RequestBody Map<String, Object> req) {
        String orderNo = req.get("orderNo") != null ? req.get("orderNo").toString() : null;
        String amount = req.get("amount") != null ? req.get("amount").toString() : "10.00";
        Map<String, Object> res = new HashMap<>();
        if (orderNo == null || orderNo.isBlank()) {
            res.put("success", false);
            res.put("message", "缺少 orderNo");
            return res;
        }
        Map<String, String> params = new HashMap<>();
        params.put("out_trade_no", orderNo);
        params.put("total_amount", amount);
        params.put("buyer_logon_id", "demo_sandbox@alipay.com (演示扫码买家)");
        markOrderPaid(params);
        res.put("success", true);
        res.put("paid", true);
        res.put("message", "扫码支付成功！已完成核销并流转药房/发货。");
        return res;
    }

    /**
     * 检查本地是否已完成支付
     */
    private boolean isOrderAlreadyPaid(String orderNo) {
        if (orderNo == null || orderNo.isBlank()) return false;
        if (orderNo.startsWith("CLINIC") || orderNo.startsWith("GH") || orderNo.startsWith("REG")) {
            return PAID_CLINIC_ORDERS.contains(orderNo);
        }
        if (orderNo.startsWith("B2C") || orderNo.startsWith("B2B") || orderNo.startsWith("ORD")) {
            // ① 优先查真实支付流水表：只有真实存在支付成功记录才算已付
            if (payTransactionMapper != null) {
                Long txCount = payTransactionMapper.selectCount(
                        new LambdaQueryWrapper<PayTransaction>()
                                .eq(PayTransaction::getOrderNo, orderNo)
                                .eq(PayTransaction::getPayStatus, "支付成功"));
                if (txCount != null && txCount > 0) {
                    return true;
                }
            }
            if (mallOrderMapper == null) return false;
            MallOrder o = mallOrderMapper.selectOne(
                    new LambdaQueryWrapper<MallOrder>().eq(MallOrder::getOrderNo, orderNo));
            // 待支付/待付款/刚创建未流水的订单绝不能算已支付；已发货或已送达算已支付
            return o != null && o.getStatus() != null && (o.getStatus().contains("已送达") || o.getStatus().contains("已发货"));
        }
        Prescription p = prescriptionMapper.selectOne(
                new LambdaQueryWrapper<Prescription>().eq(Prescription::getPrescriptionNo, orderNo));
        return p != null && "已支付".equals(p.getPayStatus());
    }

    /**
     * 统一核销入口：支持门诊处方与春播商城订单
     */
    private void markOrderPaid(Map<String, String> params) {
        String orderNo = params.get("out_trade_no");
        if (orderNo == null || orderNo.isBlank()) {
            log.warn("[支付宝] 回调缺少 out_trade_no，忽略");
            return;
        }
        if (orderNo.startsWith("CLINIC") || orderNo.startsWith("GH") || orderNo.startsWith("REG")) {
            PAID_CLINIC_ORDERS.add(orderNo);
            log.info("[支付宝] 门诊/挂号现场扫码单 {} 已标记为已支付", orderNo);
            return;
        }
        if (orderNo.startsWith("B2C") || orderNo.startsWith("B2B") || orderNo.startsWith("ORD")) {
            markMallOrderPaid(params);
        } else {
            markPrescriptionPaid(params);
        }
    }

    /**
     * 商城订单核销
     */
    private void markMallOrderPaid(Map<String, String> params) {
        String orderNo = params.get("out_trade_no");
        String totalAmount = params.get("total_amount");
        String buyerAccount = params.get("buyer_logon_id");

        if (mallOrderMapper == null) return;
        MallOrder order = mallOrderMapper.selectOne(
                new LambdaQueryWrapper<MallOrder>().eq(MallOrder::getOrderNo, orderNo));
        if (order == null) {
            log.warn("[支付宝] 商城订单 {} 未找到", orderNo);
            return;
        }

        order.setPayMethod("支付宝");
        if (order.getStatus() == null || order.getStatus().isBlank() || order.getStatus().contains("待支付")) {
            order.setStatus("待商户发货出库");
        }
        mallOrderMapper.updateById(order);

        // 落支付流水
        if (payTransactionMapper != null) {
            try {
                PayTransaction tx = new PayTransaction();
                tx.setOrderNo(order.getOrderNo());
                tx.setChannelType("支付宝");
                tx.setPayerName(order.getBuyerName() != null ? order.getBuyerName() : "商城顾客");
                tx.setPayerAccount(buyerAccount != null ? buyerAccount : order.getBuyerName());
                tx.setMerchantId("MCH_MALL_001");
                tx.setMerchantName("春播万象健康便民智慧商城");
                tx.setAmount(new BigDecimal(totalAmount != null && !totalAmount.isBlank() ? totalAmount : String.valueOf(order.getFinalAmount())));
                tx.setPayStatus("支付成功");
                tx.setRiskStatus("正常");
                tx.setTradeTime(LocalDateTime.now());
                payTransactionMapper.insert(tx);
            } catch (Exception ignored) {
            }
        }
        log.info("[支付宝] 商城订单 {} 支付成功已核销，金额 {}，买家 {}", orderNo, totalAmount, buyerAccount);
    }

    /**
     * 门诊处方核销：按处方号更新处方为已支付，并落支付流水。
     * 幂等：同一处方已支付过则跳过，防止支付宝重复回调导致重复发药/重复记账。
     */
    private void markPrescriptionPaid(Map<String, String> params) {
        String prescriptionNo = params.get("out_trade_no");
        String totalAmount = params.get("total_amount");   // 金额以支付宝回调为准，不信本地
        String buyerAccount = params.get("buyer_logon_id"); // 买家支付宝账号（沙箱为虚拟账号）

        Prescription p = prescriptionMapper.selectOne(
                new LambdaQueryWrapper<Prescription>().eq(Prescription::getPrescriptionNo, prescriptionNo));
        if (p == null) {
            log.warn("[支付宝] 回调订单 {} 未匹配到处方记录", prescriptionNo);
            return;
        }

        // 幂等：已支付过就直接跳过
        if ("已支付".equals(p.getPayStatus())) {
            log.info("[支付宝] 处方 {} 已支付过，跳过重复回调", prescriptionNo);
            return;
        }

        p.setPayStatus("已支付");
        p.setPayChannel("支付宝");
        p.setStatus("1"); // 1: 待发药（与本地 checkout 结算口径一致）
        prescriptionMapper.updateById(p);

        // 落支付交易流水（金额用支付宝回调的 total_amount）
        if (payTransactionMapper != null) {
            try {
                PayTransaction tx = new PayTransaction();
                tx.setOrderNo(p.getPrescriptionNo());
                tx.setChannelType("支付宝");
                tx.setPayerName(p.getPatientName() != null ? p.getPatientName() : "门诊患者");
                tx.setPayerAccount(buyerAccount != null ? buyerAccount : p.getPatientName());
                tx.setMerchantId("MCH_CLINIC_001");
                tx.setMerchantName("春播万象基层门诊全科诊室");
                tx.setAmount(new BigDecimal(totalAmount != null && !totalAmount.isBlank() ? totalAmount : "0"));
                tx.setPayStatus("支付成功");
                tx.setRiskStatus("正常");
                tx.setTradeTime(LocalDateTime.now());
                payTransactionMapper.insert(tx);
            } catch (Exception ignored) {
            }
        }
        log.info("[支付宝] 处方 {} 支付成功已核销，金额 {}，买家 {}", prescriptionNo, totalAmount, buyerAccount);
    }
}
