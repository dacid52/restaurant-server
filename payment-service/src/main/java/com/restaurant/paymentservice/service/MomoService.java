package com.restaurant.paymentservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
public class MomoService {

    @Value("${momo.partner-code}")
    private String partnerCode;

    @Value("${momo.access-key}")
    private String accessKey;

    @Value("${momo.secret-key}")
    private String secretKey;

    @Value("${momo.api-url}")
    private String apiUrl;

    @Value("${momo.redirect-url-base}")
    private String redirectUrlBase;

    @Value("${momo.ipn-url}")
    private String ipnUrl;

    private final RestTemplate restTemplate;

    public MomoService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Tạo yêu cầu thanh toán MoMo, trả về payUrl và deeplink (nếu có).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> createPayment(Integer orderId, Integer tableId, String tableKey, BigDecimal amount) throws Exception {
        String requestId = UUID.randomUUID().toString();
        // orderId trong MoMo phải unique – gắn timestamp để tránh trùng khi test
        String momoOrderId = partnerCode + "_" + orderId + "_" + System.currentTimeMillis();
        String requestType = "captureWallet";
        String orderInfo = "Thanh toan don hang #" + orderId + " - Ban " + tableId;

        // Encode tableId/tableKey/orderId vào extraData để dùng sau khi redirect
        String extraDataJson = String.format(
                "{\"orderId\":%d,\"tableId\":%d,\"tableKey\":\"%s\"}",
                orderId, tableId, tableKey.replace("\"", "\\\""));
        String extraData = Base64.getEncoder()
                .encodeToString(extraDataJson.getBytes(StandardCharsets.UTF_8));

        long amountLong = amount.setScale(0, java.math.RoundingMode.DOWN).longValue();

        // redirectUrl có chứa tableId, tableKey, orderId để payment-return.html đọc
        String redirectUrl = redirectUrlBase
                + "?tableId=" + tableId
                + "&tableKey=" + URLEncoder.encode(tableKey, StandardCharsets.UTF_8)
                + "&orderId=" + orderId;

        // Chuỗi ký theo đúng thứ tự alphabet của MoMo v2
        String rawHash = "accessKey=" + accessKey
                + "&amount=" + amountLong
                + "&extraData=" + extraData
                + "&ipnUrl=" + ipnUrl
                + "&orderId=" + momoOrderId
                + "&orderInfo=" + orderInfo
                + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl
                + "&requestId=" + requestId
                + "&requestType=" + requestType;

        String signature = hmacSha256(rawHash, secretKey);

        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("partnerCode", partnerCode);
        requestBody.put("accessKey", accessKey);
        requestBody.put("requestId", requestId);
        requestBody.put("amount", amountLong);
        requestBody.put("orderId", momoOrderId);
        requestBody.put("orderInfo", orderInfo);
        requestBody.put("redirectUrl", redirectUrl);
        requestBody.put("ipnUrl", ipnUrl);
        requestBody.put("extraData", extraData);
        requestBody.put("requestType", requestType);
        requestBody.put("signature", signature);
        requestBody.put("lang", "vi");

        log.info("📤 Calling MoMo API: orderId={}, amount={}", momoOrderId, amountLong);
        Map<String, Object> response = restTemplate.postForObject(apiUrl, requestBody, Map.class);
        log.info("📥 MoMo API response: {}", response);
        return response;
    }

    // ---------------------------------------------------------------
    private String hmacSha256(String data, String key) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
