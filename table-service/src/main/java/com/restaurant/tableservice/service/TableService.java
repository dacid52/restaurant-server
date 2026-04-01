package com.restaurant.tableservice.service;

import com.restaurant.tableservice.entity.RestaurantTable;
import com.restaurant.tableservice.entity.TableKey;
import com.restaurant.tableservice.repository.TableKeyRepository;
import com.restaurant.tableservice.repository.TableRepository;
import com.restaurant.tableservice.util.QrCodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.lang.NonNull;

@Service
@RequiredArgsConstructor
public class TableService {

    private final TableRepository tableRepository;
    private final TableKeyRepository tableKeyRepository;

    public List<RestaurantTable> getAllTables() {
        return tableRepository.findAll();
    }

    public RestaurantTable getTableById(@NonNull Integer id) {
        return tableRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy bàn"));
    }

    @Transactional
    public RestaurantTable createTable(@NonNull RestaurantTable table) {
        if (table.getName() == null) throw new RuntimeException("Tên bàn là bắt buộc");
        if (table.getStatus() == null) table.setStatus("Trống");
        return tableRepository.save(table);
    }

    @Transactional
    @SuppressWarnings("null")
    public RestaurantTable updateTable(@NonNull Integer id, String name, String status, Boolean isBuffet) {
        RestaurantTable existing = getTableById(id);
        if (name != null) existing.setName(name);
        
        if (status != null) {
            existing.setStatus(status);
            if ("Trống".equals(status)) {
                tableKeyRepository.invalidateKeysByTableId(id);
            }
        }
        
        if (isBuffet != null) existing.setIsBuffet(isBuffet);
        return tableRepository.save(existing);
    }

    @Transactional
    public void deleteTable(@NonNull Integer id) {
        tableRepository.deleteById(id);
    }

    @Transactional
    public Boolean validateTableKey(@NonNull Integer tableId, @NonNull String tableKey, String deviceSession) {
        List<TableKey> keys = tableKeyRepository.findValidKey(tableId, tableKey);
        if (keys.isEmpty()) return false;
        
        TableKey key = keys.get(0);
        
        // Cập nhật trạng thái bàn sang "Đang sử dụng"
        RestaurantTable table = getTableById(tableId);
        if (!"Đang sử dụng".equals(table.getStatus())) {
            table.setStatus("Đang sử dụng");
            tableRepository.save(table);
        }
        
        // Khóa theo thiết bị đầu tiên truy cập
        if (deviceSession != null) {
            if (key.getDeviceSession() == null) {
                key.setDeviceSession(deviceSession);
                tableKeyRepository.save(key);
                return true;
            } else {
                return key.getDeviceSession().equals(deviceSession);
            }
        }
        
        return true;
    }

    @Transactional
    public void invalidateTableKey(@NonNull Integer tableId) {
        tableKeyRepository.invalidateKeysByTableId(tableId);
        RestaurantTable table = getTableById(tableId);
        table.setStatus("Trống");
        table.setIsBuffet(false);
        tableRepository.save(table);
    }
    
    @Transactional
    public Map<String, Object> generateStaticQRCode(@NonNull Integer id) {
        RestaurantTable table = getTableById(id);
        String localIP = getLocalIpAddress();
        String qrUrl = "http://" + localIP + ":4000/api/tables/" + id + "/generate-access"; // Gateway port
        
        Map<String, Object> res = new HashMap<>();
        res.put("table_id", id);
        res.put("table_name", table.getName());
        res.put("qr_code", QrCodeUtil.generateQrCodeBase64(qrUrl));
        res.put("url", qrUrl);
        res.put("type", "static");
        return res;
    }
    
    @Transactional
    public Map<String, Object> generateDynamicQRCode(@NonNull Integer id) {
        getTableById(id);
        
        String keyValue = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(2);
        
        TableKey key = new TableKey();
        key.setTableId(id);
        key.setKeyValue(keyValue);
        key.setCreatedAt(LocalDateTime.now());
        key.setExpiresAt(expiresAt);
        key.setIsValid(true);
        tableKeyRepository.save(key);
        
        String localIP = getLocalIpAddress();
        String qrUrl = "http://" + localIP + ":3011/index.html?tableId=" + id + "&tableKey=" + keyValue;
        
        Map<String, Object> res = new HashMap<>();
        res.put("table_id", id);
        res.put("key", keyValue);
        res.put("expires_at", expiresAt);
        res.put("qr_code", QrCodeUtil.generateQrCodeBase64(qrUrl));
        res.put("url", qrUrl);
        res.put("type", "dynamic");
        return res;
    }

    private String getLocalIpAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "localhost";
        }
    }
}
