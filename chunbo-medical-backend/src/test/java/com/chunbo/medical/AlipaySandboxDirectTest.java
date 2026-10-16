package com.chunbo.medical;

import com.alipay.api.AlipayClient;
import com.alipay.api.DefaultAlipayClient;
import com.alipay.api.request.AlipayTradePrecreateRequest;
import com.alipay.api.response.AlipayTradePrecreateResponse;
import com.chunbo.medical.service.AlipayService;
import org.junit.jupiter.api.Test;

public class AlipaySandboxDirectTest {

    @Test
    public void testDirectPrecreate() throws Exception {
        String appId = "9021000169614435";
        String appPrivateKey = "MIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDHTiLnOsJZDS1/RlrokVbflMgVavhW+Br6N7gjvV2mH7/+tAiieYa7yJ/YkDtUzGubWXvs6bQ0n1jNkRbGVI7KKJRW+NnEOQam/RvrZWWGrxZqaLyKmVpXYyuwgEvkE3Xfq/4wZn7qI31WUEXUJtxNUwXdk1SyjlCB8Ub37gjg5ioa1uX5G+3i5qmGm8jqGZcwscb53kKf5f0xiXbIVHnXeATSa+gekCUVTHhqFGoEfKvvaiQM+GqMteAB4BMBkPmtQaFAl/izoixIXHp8yo6I13M1k1bHJVDK+jjIeYfIzw+HReeo1J5G8mpU+uC2HCEgcJBg/Y1xiZzlyUVclVx7AgMBAAECggEAATj3edegbY9pIgi6cgbCM6jj+U2orE/lSnehB4cX1fcH5HYSP3zjWK+IW3m3A3QljNRlsDRWyKPZlZk4PIs0PQU2s7nfDBcVdVzEGROMJeFZD9oIngkGxtZy5ChFTubXqEGT05QtXx5MnITtiLmcTFUgChXnRRkeYU2rk8XABDEidMw9rCPVQuyGySwmpAbplQw+AJYGH1kvDMEB+dBe0ntnIBX66jEsjl3KvkpnOfaNKbdPv24lNpOaW4NiKbadIzVhxx4MM/lKsMybJmrWxNAkYc0MLmUHYcFcWPVNsKa6k4THYso0koFK0czW2VVqV+Z+lgmWtbuj1xBMeinkMQKBgQD2xtU76F8smr/DQQgk/0p+I4JLgBRJqO7QivPnvk/g0Sd3VC4Lwzv2DbDGZBk7oBQRDNQyJx7R0FnJTn0SpHu3EmRp5YiC9pVG+xN71A/pI82wCSbRpiBJlVVfWGJt00w8Ife41ScZGN6GxNbjRxAWSVoRp0GGGd4vZY7iyedWbwKBgQDOwRhKTOm2nFjBhzWCsIGnuTzS1DM7l+KK8GsQbSQIj7k9lHaJdgS9+BpaFxT2HoAWQc5TfsD85Jig0NhhvBxcMNJmKX/46KzzQP8oCYg4FX4s69T4HG2buaD2hzzlxNimqTf0g7MFXUnPw02bQQcnS7p71uqtWLhvmZy/QO7AtQKBgQDOCr+5MFBezqoz3ZVuRpUs8nr0jPDje6BCEPKK2LSa8ibwlCGKAq2KyG2Dh8olpYd7oOl/IniyGDMPSHBXY2xEM6ARZutu5XFH/ZSL/Eh4kUNIqqt1ui4VIhFQ9VvhdN2fC4FpGtd1NfV+MtV0mD3PTdldCfhEfk6r5jDCHHACEwKBgQCQ8ixOQpeCjx9mJZ0tDwMGi8WJZHy6ZaPLlfrpHCqr2d6avk9P1S5D5ADNcesfFIjlkaLNREqVwMkDzQY1loQnWY7XUnbGEJDXRJTdA7m838yCP58hhgSNrNicnHjj+FmOlYmF4SKLOzh48Emi8zbJpLyK/0Os3UsH6iBKsIHxFQKBgB0Kn/PLJY3npqB8HTgQQPrDkD94UpJWdT07S1GGh9RKXdWEGsnTK8PZz0FpM4ly1IzBMVzunhKra4Xzg8u/wRpNn9NRZ4iOSntALnXebK+ir91nZ/s0Yb3Dn4Rv93917uCpwrKnuT99W81bEpTw+o3GHvl27GA4Wpovu7RNOYij";
        String alipayPublicKey = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAvv82oJtu1ihVfjEMAFs6k7cI8TvUhzI6MqFDPQ5hWgUAo+9w+Ck3zmyjXR0sQaNnhreXEYhg9rMTG4vspzAzrVX4+bAfTfTC2AdTEloZwAsA+e9XuDz14ASIHD4fUYd1xM1RYbM4tfG2WfI6ZPdvkXL25eA+n0xHwflNK/TCVnVdMJBOz3tvEZh2jVq7pkUOq9ncT3fFr0H9BbqlkPAd0OgPcuRrlfQkSek0mqBm0AWq3gsZZC+q+HDdbF8sgEmH+WKo+AMokCr5pHNbxC7irODBRMPFpCVERwgiSE1/C2TzTF5g4la+ND2AWbiY4AS1C8IZVVLyOd56B2lJDaVtgwIDAQAB";
        String gateway = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";

        com.alipay.api.AlipayConfig config = new com.alipay.api.AlipayConfig();
        config.setServerUrl(gateway);
        config.setAppId(appId);
        config.setFormat("json");
        config.setCharset("UTF-8");
        config.setSignType("RSA2");
        config.setPrivateKey(appPrivateKey);
        config.setAlipayPublicKey(alipayPublicKey);
        config.setCustomizedHttpClient(new AlipayService.DirectBypassHttpClient());

        AlipayClient client = new DefaultAlipayClient(config);

        AlipayTradePrecreateRequest req = new AlipayTradePrecreateRequest();
        String outTradeNo = "TEST_" + System.currentTimeMillis();
        req.setBizContent("{\"out_trade_no\":\"" + outTradeNo + "\",\"total_amount\":\"10.00\",\"subject\":\"测试预下单\"}");

        AlipayTradePrecreateResponse resp = client.execute(req);
        System.out.println("TEST RESULT: code=" + resp.getCode() + ", msg=" + resp.getMsg() + ", qr=" + resp.getQrCode() + ", subCode=" + resp.getSubCode() + ", subMsg=" + resp.getSubMsg());
    }
}
