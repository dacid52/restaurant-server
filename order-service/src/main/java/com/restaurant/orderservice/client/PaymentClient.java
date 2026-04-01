package com.restaurant.orderservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.Map;

@FeignClient(name = "payment-service", url = "${services.payment}")
public interface PaymentClient {

    @PostMapping("/api/payments/request")
    void requestPayment(@RequestBody Map<String, Object> paymentData);
}
