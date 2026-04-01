package com.restaurant.tableservice.controller;

import com.restaurant.tableservice.service.TableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.springframework.lang.NonNull;

@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class TableKeyController {

    private final TableService tableService;

    @GetMapping("/{id}/qr/static")
    public ResponseEntity<Map<String, Object>> generateStaticQRCode(@PathVariable @NonNull Integer id) {
        return ResponseEntity.ok(tableService.generateStaticQRCode(id));
    }

    @PostMapping("/{id}/qr/dynamic")
    public ResponseEntity<Map<String, Object>> generateDynamicQRCode(@PathVariable @NonNull Integer id) {
        return ResponseEntity.ok(tableService.generateDynamicQRCode(id));
    }

    @PostMapping("/{id}/keys/invalidate")
    public ResponseEntity<Map<String, Object>> invalidateKeys(@PathVariable @NonNull Integer id) {
        tableService.invalidateTableKey(id);
        return ResponseEntity.ok(Map.of("message", "Đã vô hiệu hóa key và reset trạng thái bàn"));
    }
    
    // Other access logic (generateAccessKey, accessTable) would redirect user to actual frontend,
    // Typically backend returns redirect to the order page in React/Vue.
}
