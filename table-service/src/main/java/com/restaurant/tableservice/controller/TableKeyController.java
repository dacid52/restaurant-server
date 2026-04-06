package com.restaurant.tableservice.controller;

import com.restaurant.tableservice.service.TableService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
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

    /**
     * Trả về thông tin key đang active của bàn (nếu có).
     * FE admin dùng để hiển thị thời gian còn lại của phiên hiện tại.
     */
    @GetMapping("/{id}/active-key")
    public ResponseEntity<?> getActiveKey(@PathVariable @NonNull Integer id) {
        return tableService.getActiveKey(id)
                .<ResponseEntity<?>>map(key -> {
                    long secondsLeft = Duration.between(LocalDateTime.now(), key.getExpiresAt()).toSeconds();
                    return ResponseEntity.ok(Map.of(
                            "expires_at", key.getExpiresAt(),
                            "seconds_remaining", Math.max(0, secondsLeft)
                    ));
                })
                .orElse(ResponseEntity.noContent().build());
    }
    
    // Other access logic (generateAccessKey, accessTable) would redirect user to actual frontend,
    // Typically backend returns redirect to the order page in React/Vue.

    /**
     * Endpoint cho Static QR: mỗi lần quét → tự động tạo key mới → redirect sang trang gọi món.
     *
     * Luồng:
     *   Khách quét QR tĩnh → POST /api/tables/{id}/generate-access
     *     → invalidate key cũ nếu có
     *     → tạo key mới (2h)
     *     → redirect: /index.html?tableId={id}&tableKey={newKey}
     *
     * Lưu ý nghiệp vụ:
     *   - Staff cần "arm" bàn trước khi khách ngồi (chuyển status sang "Trống" để lock)
     *   - Nếu bàn đang có khách (status "Đang sử dụng"), trước khi cho quét QR mới
     *     phải invalidate key cũ từ admin — nếu không session cũ bị kill ngay lập tức.
     *   - Mỗi lần quét tạo key mới, thiết bị đầu tiên validate sẽ được claim session.
     */
    @GetMapping("/{id}/generate-access")
    public void generateAccess(@PathVariable @NonNull Integer id,
                                HttpServletResponse response) throws IOException {
        try {
            Map<String, Object> result = tableService.generateDynamicQRCode(id);
            String key = (String) result.get("key");
            response.sendRedirect("/index.html?tableId=" + id + "&tableKey=" + key);
        } catch (RuntimeException e) {
            // Lỗi nghiệp vụ (VD: bàn đang có reservation sắp tới ≤ 15 phút)
            String msg = URLEncoder.encode(e.getMessage(), StandardCharsets.UTF_8);
            // Redirect về loading screen với thông báo lỗi — initApp sẽ hiển thị overlay lỗi
            response.sendRedirect("/index.html?qr_access_error=" + msg);
        }
    }
}
