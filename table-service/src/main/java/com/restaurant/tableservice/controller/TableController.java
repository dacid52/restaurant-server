package com.restaurant.tableservice.controller;

import com.restaurant.tableservice.entity.RestaurantTable;
import com.restaurant.tableservice.service.TableService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import org.springframework.lang.NonNull;

@RestController
@RequestMapping("/api/tables")
@RequiredArgsConstructor
public class TableController {

    private final TableService tableService;

    @GetMapping
    public ResponseEntity<List<RestaurantTable>> getTables() {
        return ResponseEntity.ok(tableService.getAllTables());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RestaurantTable> getTableById(@PathVariable @NonNull Integer id) {
        return ResponseEntity.ok(tableService.getTableById(id));
    }

    @PostMapping
    public ResponseEntity<RestaurantTable> createTable(@RequestBody @NonNull RestaurantTable table) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tableService.createTable(table));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RestaurantTable> updateTable(@PathVariable @NonNull Integer id, @RequestBody Map<String, Object> payload) {
        String name = (String) payload.get("name");
        String status = (String) payload.get("status");
        Boolean isBuffet = payload.containsKey("is_buffet") ? (Boolean) payload.get("is_buffet") : null;
        return ResponseEntity.ok(tableService.updateTable(id, name, status, isBuffet));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTable(@PathVariable @NonNull Integer id) {
        tableService.deleteTable(id);
        return ResponseEntity.ok().build();
    }

    // Key validation endpoints accessed by other services or directly internally
    @GetMapping("/{id}/validate-key")
    public ResponseEntity<Boolean> validateTableKey(@PathVariable @NonNull Integer id, @RequestParam @NonNull String tableKey, @RequestParam(required = false) String deviceSession) {
        return ResponseEntity.ok(tableService.validateTableKey(id, tableKey, deviceSession));
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<Void> updateTableStatus(@PathVariable @NonNull Integer id, @RequestParam String status, @RequestParam Boolean isBuffet) {
        tableService.updateTable(id, null, status, isBuffet);
        return ResponseEntity.ok().build();
    }
    
    @PutMapping("/{id}/keys/invalidate")
    public ResponseEntity<Void> invalidateKeysExternal(@PathVariable @NonNull Integer id) {
        tableService.invalidateTableKey(id);
        return ResponseEntity.ok().build();
    }
}
