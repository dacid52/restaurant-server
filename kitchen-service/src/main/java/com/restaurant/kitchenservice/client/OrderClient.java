package com.restaurant.kitchenservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "order-service", url = "${services.order}")
public interface OrderClient {

    @PutMapping("/api/orders/{id}/status")
    void updateOrderStatus(@PathVariable("id") Integer orderId, @RequestBody java.util.Map<String, String> payload);
    // or handled within the OrderService logic.
}
