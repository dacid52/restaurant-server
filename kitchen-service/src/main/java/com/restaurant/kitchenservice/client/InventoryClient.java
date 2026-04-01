package com.restaurant.kitchenservice.client;

import com.restaurant.kitchenservice.dto.InventoryDeductRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import java.util.List;

@FeignClient(name = "inventory-service", url = "${services.inventory:http://localhost:3006}")
public interface InventoryClient {
    @PostMapping("/api/inventory/ingredients/batch-deduct")
    void deductStock(@RequestBody List<InventoryDeductRequest> requests);
}
