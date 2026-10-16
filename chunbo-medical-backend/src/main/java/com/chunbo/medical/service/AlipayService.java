package com.chunbo.medical.service;

import com.alipay.api.AlipayApiException;
import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.internal.util.AlipaySignature;
import com.alipay.api.request.AlipayTradePagePayRequest;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.request.AlipayTradeQueryRequest;
import com.alipay.api.request.AlipayTradeRefundRequest;
import com.alipay.api.response.AlipayTradePagePayResponse;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.alipay.api.response.AlipayTradeQueryResponse;
import com.alipay.api.response.AlipayTradeRefundResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 支付宝支付服务（电脑网站支付）
 * 封装：发起下单（pagePay）、异步回调验签（verifyNotify）、主动查询（query）、退款（refund）。
 * 沙箱与生产仅通过 application.yml 的 gateway / 密钥切换，代码无需改动。
 */
@Service
public class AlipayService {

    @Value("${alipay.app-id}")
    private String appId;

    @Value("${alipay.app-private-key}")
    private String appPrivateKey;

    @Value("${alipay.alipay-public-key}")
    private String alipayPublicKey;

    @Value("${alipay.gateway}")
    private String gateway;

    @Value("${alipay.notify-url}")
    private String notifyUrl;

    @Value("${alipay.return-url}")
    private String returnUrl;

    /** 签名算法：RSA2（支付宝推荐） */
    private static final String SIGN_TYPE = "RSA2";
    /** 字符集 */
    private static final String CHARSET = "UTF-8";
    /** 返回格式 */
    private static final String FORMAT = "json";

    /**
     * 构建支付宝客户端（每次调用现建，避免持有长连接）
     * 内置 DirectBypassHttpClient：自动绑定物理网卡 IP 直连国内真实节点（110.75.132.25），
     * 彻底解决 Clash/VPN TUN 代理环境下阿里云 WAF 握手阻断导致的 SSLHandshakeException。
     */
    private AlipayClient alipayClient() {
        try {
            com.alipay.api.AlipayConfig config = new com.alipay.api.AlipayConfig();
            config.setServerUrl(gateway);
            config.setAppId(appId);
            config.setFormat(FORMAT);
            config.setCharset(CHARSET);
            config.setSignType(SIGN_TYPE);
            config.setPrivateKey(appPrivateKey);
            config.setAlipayPublicKey(alipayPublicKey);
            config.setCustomizedHttpClient(new DirectBypassHttpClient());
            return new DefaultAlipayClient(config);
        } catch (Exception e) {
            return new DefaultAlipayClient(
                    gateway, appId, appPrivateKey, FORMAT, CHARSET, alipayPublicKey, SIGN_TYPE);
        }
    }

    public static class DirectBypassHttpClient extends com.alipay.api.internal.util.AbstractHttpClient {
        private final okhttp3.OkHttpClient okClient;

        public DirectBypassHttpClient() {
            okhttp3.OkHttpClient.Builder builder = new okhttp3.OkHttpClient.Builder();
            // 1. DNS 直连国内真实网关节点 110.75.132.25，避免代理劫持
            builder.dns(hostname -> {
                if ("openapi-sandbox.dl.alipaydev.com".equalsIgnoreCase(hostname)) {
                    return java.util.Collections.singletonList(
                            java.net.InetAddress.getByAddress(hostname, new byte[]{110, 75, (byte) 132, 25}));
                }
                return okhttp3.Dns.SYSTEM.lookup(hostname);
            });

            // 2. 绑定物理网卡 IP，绕开 Meta Tunnel 路由拦截
            final java.net.InetAddress localIp = findPhysicalLocalIp();
            if (localIp != null) {
                builder.socketFactory(new javax.net.SocketFactory() {
                    @Override
                    public java.net.Socket createSocket() throws java.io.IOException {
                        java.net.Socket s = new java.net.Socket();
                        s.bind(new java.net.InetSocketAddress(localIp, 0));
                        return s;
                    }
                    @Override
                    public java.net.Socket createSocket(String host, int port) throws java.io.IOException {
                        java.net.Socket s = createSocket();
                        s.connect(new java.net.InetSocketAddress(host, port));
                        return s;
                    }
                    @Override
                    public java.net.Socket createSocket(String host, int port, java.net.InetAddress localHost, int localPort) throws java.io.IOException {
                        java.net.Socket s = new java.net.Socket();
                        s.bind(new java.net.InetSocketAddress(localHost, localPort));
                        s.connect(new java.net.InetSocketAddress(host, port));
                        return s;
                    }
                    @Override
                    public java.net.Socket createSocket(java.net.InetAddress host, int port) throws java.io.IOException {
                        java.net.Socket s = createSocket();
                        s.connect(new java.net.InetSocketAddress(host, port));
                        return s;
                    }
                    @Override
                    public java.net.Socket createSocket(java.net.InetAddress address, int port, java.net.InetAddress localAddress, int localPort) throws java.io.IOException {
                        java.net.Socket s = new java.net.Socket();
                        s.bind(new java.net.InetSocketAddress(localAddress, localPort));
                        s.connect(new java.net.InetSocketAddress(address, port));
                        return s;
                    }
                });
            }

            // 3. 信任证书防止代理环境中间人拦截证书报错
            try {
                javax.net.ssl.TrustManager[] trustAll = new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public void checkClientTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] chain, String authType) {}
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() { return new java.security.cert.X509Certificate[0]; }
                    }
                };
                javax.net.ssl.SSLContext sslContext = javax.net.ssl.SSLContext.getInstance("TLS");
                sslContext.init(null, trustAll, new java.security.SecureRandom());
                builder.sslSocketFactory(sslContext.getSocketFactory(), (javax.net.ssl.X509TrustManager) trustAll[0]);
                builder.hostnameVerifier((hostname, session) -> true);
            } catch (Exception ignored) {}

            this.okClient = builder.build();
        }

        @Override
        public String doPost(String url, java.util.Map<String, String> params, String charset, java.util.Map<String, String> headers) throws java.io.IOException {
            okhttp3.FormBody.Builder form = new okhttp3.FormBody.Builder();
            if (params != null) {
                for (java.util.Map.Entry<String, String> e : params.entrySet()) {
                    if (e.getValue() != null) {
                        form.add(e.getKey(), e.getValue());
                    }
                }
            }
            okhttp3.Request.Builder req = new okhttp3.Request.Builder().url(url).post(form.build());
            if (headers != null) {
                for (java.util.Map.Entry<String, String> h : headers.entrySet()) {
                    req.header(h.getKey(), h.getValue());
                }
            }
            try (okhttp3.Response resp = okClient.newCall(req.build()).execute()) {
                if (!resp.isSuccessful()) {
                    throw new java.io.IOException("HTTP " + resp.code() + ": " + resp.message());
                }
                return resp.body() != null ? resp.body().string() : "";
            }
        }

        @Override
        public String doPost(String url, java.util.Map<String, String> params, java.util.Map<String, com.alipay.api.FileItem> fileParams, String charset, java.util.Map<String, String> headers) throws java.io.IOException {
            return doPost(url, params, charset, headers);
        }

        private static java.net.InetAddress findPhysicalLocalIp() {
            try {
                java.util.Enumeration<java.net.NetworkInterface> ifaces = java.net.NetworkInterface.getNetworkInterfaces();
                while (ifaces.hasMoreElements()) {
                    java.net.NetworkInterface ni = ifaces.nextElement();
                    String name = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();
                    if (name.contains("wlan") || name.contains("wi-fi") || name.contains("ethernet") || name.contains("以太网")) {
                        if (name.contains("virtual") || name.contains("direct") || name.contains("vmware")) continue;
                        java.util.Enumeration<java.net.InetAddress> addrs = ni.getInetAddresses();
                        while (addrs.hasMoreElements()) {
                            java.net.InetAddress a = addrs.nextElement();
                            if (a instanceof java.net.Inet4Address && !a.isLoopbackAddress() && !a.isLinkLocalAddress()) {
                                return a;
                            }
                        }
                    }
                }
                return java.net.InetAddress.getByName("192.168.0.238");
            } catch (Exception ignored) {}
            return null;
        }
    }

    /**
     * 电脑网站支付下单：返回一段 HTML 表单字符串，前端渲染后浏览器自动跳转支付宝收银台。
     *
     * @param orderNo 商户订单号（业务唯一，如处方号/挂号单号）
     * @param amount  金额（元，两位小数，如 "99.90"）
     * @param subject 商品标题（如 "挂号缴费" / "处方划价"）
     * @return 支付宝返回的 HTML 表单
     */
    public String pagePay(String orderNo, String amount, String subject) throws AlipayApiException {
        AlipayClient client = alipayClient();

        AlipayTradePagePayRequest request = new AlipayTradePagePayRequest();
        // 异步通知地址：支付宝付款成功后，服务器主动回调此地址（必须公网可访问）
        request.setNotifyUrl(notifyUrl);
        // 同步跳转地址：用户付完款后，浏览器跳回此地址
        request.setReturnUrl(returnUrl);

        // 业务参数：订单号、金额、标题、产品码（FAST_INSTANT_TRADE_PAY = 电脑网站支付）
        request.setBizContent("{" +
                "\"out_trade_no\":\"" + orderNo + "\"," +
                "\"total_amount\":\"" + amount + "\"," +
                "\"subject\":\"" + subject + "\"," +
                "\"product_code\":\"FAST_INSTANT_TRADE_PAY\"" +
                "}");

        AlipayTradePagePayResponse response = client.pageExecute(request);
        if (!response.isSuccess()) {
            throw new IllegalStateException("支付宝下单失败：" + response.getMsg() + " " + response.getSubMsg());
        }
        // getBody() 就是那段 <form> HTML，直接返回给前端
        return response.getBody();
    }

    /**
     * 异步回调验签：用支付宝公钥验证回调参数是否真的是支付宝发来的（防伪造）。
     *
     * @param params 回调的原始参数（request.getParameterMap 转成的 Map）
     * @return true = 验签通过，可信；false = 伪造/被篡改
     */
    public boolean verifyNotify(Map<String, String> params) throws AlipayApiException {
        return AlipaySignature.rsaCheckV1(params, alipayPublicKey, CHARSET, SIGN_TYPE);
    }

    /**
     * 当面付预下单（扫码支付）：返回二维码内容字符串，前端渲染成收款码，
     * 买家用支付宝 App 扫码付款 —— 这是「页面内二维码弹窗」体验的标准实现方式。
     *
     * @param orderNo 商户订单号
     * @param amount  金额（元）
     * @param subject 商品标题
     * @return qr_code 二维码内容
     */
    public String precreate(String orderNo, String amount, String subject) throws AlipayApiException {
        AlipayClient client = alipayClient();
        AlipayTradePrecreateRequest request = new AlipayTradePrecreateRequest();
        request.setNotifyUrl(notifyUrl);
        request.setBizContent("{" +
                "\"out_trade_no\":\"" + orderNo + "\"," +
                "\"total_amount\":\"" + amount + "\"," +
                "\"subject\":\"" + subject + "\"" +
                "}");
        AlipayTradePrecreateResponse response = client.execute(request);
        if (!response.isSuccess()) {
            throw new IllegalStateException("支付宝预下单失败：" + response.getMsg() + " " + response.getSubMsg());
        }
        return response.getQrCode();
    }

    /**
     * 主动查询订单支付状态（用于对账、回调不通时的查单补偿）
     *
     * @param orderNo 商户订单号
     * @return 支付宝查询响应（tradeStatus / totalAmount / buyerLogonId 等）
     */
    public AlipayTradeQueryResponse query(String orderNo) throws AlipayApiException {
        AlipayClient client = alipayClient();
        AlipayTradeQueryRequest request = new AlipayTradeQueryRequest();
        request.setBizContent("{\"out_trade_no\":\"" + orderNo + "\"}");
        AlipayTradeQueryResponse response = client.execute(request);
        if (!response.isSuccess()) {
            throw new IllegalStateException("支付宝查询失败：" + response.getMsg() + " " + response.getSubMsg());
        }
        return response;
    }

    /**
     * 退款
     *
     * @param orderNo 商户订单号
     * @param amount  退款金额（元）
     */
    public void refund(String orderNo, String amount) throws AlipayApiException {
        AlipayClient client = alipayClient();
        AlipayTradeRefundRequest request = new AlipayTradeRefundRequest();
        request.setBizContent("{" +
                "\"out_trade_no\":\"" + orderNo + "\"," +
                "\"refund_amount\":\"" + amount + "\"" +
                "}");
        AlipayTradeRefundResponse response = client.execute(request);
        if (!response.isSuccess()) {
            throw new IllegalStateException("支付宝退款失败：" + response.getMsg() + " " + response.getSubMsg());
        }
    }
}
