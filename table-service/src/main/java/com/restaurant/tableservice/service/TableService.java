package com.restaurant.tableservice.service;

import com.restaurant.tableservice.entity.RestaurantTable;
import com.restaurant.tableservice.entity.TableKey;
import com.restaurant.tableservice.repository.TableKeyRepository;
import com.restaurant.tableservice.repository.TableRepository;
import com.restaurant.tableservice.repository.TableReservationRepository;
import com.restaurant.tableservice.util.QrCodeUtil;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.NonNull;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TableService {

    private static final Logger log = LoggerFactory.getLogger(TableService.class);
    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter VN_FMT = DateTimeFormatter.ofPattern("HH:mm, dd/MM/yyyy");
    private static final int AUTO_CANCEL_GRACE_MINUTES = 20;

    private static final Set<String> VALID_TABLE_STATUSES = Set.of("Trống", "Đang sử dụng", "Đã đặt", "Chờ xác nhận");

    @Value("${app.customer-base-url:http://restaurant-server.site:3011}")
    private String customerBaseUrl;

    @Value("${app.restaurant.name:Nhà Hàng Restaurant}")
    private String restaurantName;

    @Value("${app.restaurant.address:123 Đường Nguyễn Huệ, Quận 1, TP. Hồ Chí Minh}")
    private String restaurantAddress;

    @Value("${app.restaurant.phone:028 1234 5678}")
    private String restaurantPhone;

    private final TableRepository tableRepository;
    private final TableKeyRepository tableKeyRepository;
    private final TableReservationRepository tableReservationRepository;
    private final JavaMailSender mailSender;

    public List<RestaurantTable> getAllTables() {
        return tableRepository.findAll();
    }

    public RestaurantTable getTableById(@NonNull Integer id) {
        return tableRepository.findById(id).orElseThrow(() -> new RuntimeException("Không tìm thấy bàn"));
    }

    public List<com.restaurant.tableservice.entity.TableReservation> getReservations(Integer tableId, String from, String to) {
        if (tableId == null) throw new RuntimeException("Thiếu table_id");
        if (from != null && to != null) {
            LocalDateTime fromTime = parseDateTime(from, "from");
            LocalDateTime toTime = parseDateTime(to, "to");
            if (!fromTime.isBefore(toTime)) {
                throw new RuntimeException("Khoảng thời gian không hợp lệ");
            }
            return tableReservationRepository.findByTableIdAndStartTimeBetween(tableId, fromTime, toTime);
        }
        return tableReservationRepository.findByTableIdOrderByStartTimeAsc(tableId);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public com.restaurant.tableservice.entity.TableReservation createReservation(Integer tableId, Map<String, Object> payload) {
        if (tableId == null) throw new RuntimeException("Thiếu table_id");
        getTableById(tableId);

        String customerName = payload.get("customer_name") != null ? payload.get("customer_name").toString() : null;
        String customerPhone = payload.get("customer_phone") != null ? payload.get("customer_phone").toString() : null;
        Integer partySize = payload.get("party_size") != null ? ((Number) payload.get("party_size")).intValue() : null;
        String startStr = payload.get("start_time") != null ? payload.get("start_time").toString() : null;
        String endStr = payload.get("end_time") != null ? payload.get("end_time").toString() : null;
        boolean hasCustomerId = payload.get("customer_id") != null;

        // customer_id có nghĩa là khách đã đăng ký — name/phone có thể lấy từ tài khoản
        if (!hasCustomerId) {
            if (customerName == null || customerName.isBlank()) throw new RuntimeException("Thiếu tên khách");
            if (customerPhone == null || customerPhone.isBlank()) throw new RuntimeException("Thiếu số điện thoại");
        } else {
            if (customerName == null || customerName.isBlank()) customerName = "Khách hàng #" + payload.get("customer_id");
            if (customerPhone == null || customerPhone.isBlank()) customerPhone = "";
        }
        if (partySize == null || partySize <= 0) throw new RuntimeException("Số lượng khách không hợp lệ");
        if (startStr == null || endStr == null) throw new RuntimeException("Thiếu thời gian đặt bàn");

        LocalDateTime startTime = parseDateTime(startStr, "start_time");
        LocalDateTime endTime = parseDateTime(endStr, "end_time");
        if (!startTime.isBefore(endTime)) {
            throw new RuntimeException("Thời gian kết thúc phải lớn hơn thời gian bắt đầu");
        }

        Integer overlapCount = tableReservationRepository.countOverlappingReservations(tableId, startTime, endTime);
        if (overlapCount != null && overlapCount > 0) {
            throw new RuntimeException("Bàn đã có đặt chỗ trong khung giờ này");
        }

        com.restaurant.tableservice.entity.TableReservation reservation = new com.restaurant.tableservice.entity.TableReservation();
        reservation.setTableId(tableId);
        reservation.setCustomerName(customerName);
        reservation.setCustomerPhone(customerPhone);
        reservation.setPartySize(partySize);
        reservation.setStartTime(startTime);
        reservation.setEndTime(endTime);

        if (payload.containsKey("status") && payload.get("status") != null) {
            reservation.setStatus(payload.get("status").toString());
        }

        if (payload.containsKey("is_buffet")) {
            reservation.setIsBuffet(Boolean.TRUE.equals(payload.get("is_buffet")));
        }

        if (payload.get("buffet_package_id") != null) {
            reservation.setBuffetPackageId(((Number) payload.get("buffet_package_id")).intValue());
        }
        if (payload.get("buffet_package_name") != null) {
            reservation.setBuffetPackageName(payload.get("buffet_package_name").toString());
        }
        if (payload.get("notes") != null) {
            reservation.setNotes(payload.get("notes").toString());
        }
        if (payload.get("customer_id") != null) {
            reservation.setCustomerId(((Number) payload.get("customer_id")).intValue());
        }
        if (payload.get("customer_email") != null) {
            reservation.setCustomerEmail(payload.get("customer_email").toString());
        }

        try {
            return tableReservationRepository.save(reservation);
        } catch (DataIntegrityViolationException e) {
            throw new RuntimeException("Bàn đã được đặt trong khung giờ này (xung đột đồng thời)");
        }
    }

    public boolean isReservationAvailable(Integer tableId, String startStr, String endStr) {        if (tableId == null) throw new RuntimeException("Thiếu table_id");
        if (startStr == null || endStr == null) throw new RuntimeException("Thiếu thời gian kiểm tra");
        LocalDateTime startTime = parseDateTime(startStr, "start_time");
        LocalDateTime endTime = parseDateTime(endStr, "end_time");
        if (!startTime.isBefore(endTime)) {
            throw new RuntimeException("Thời gian kết thúc phải lớn hơn thời gian bắt đầu");
        }
        Integer overlapCount = tableReservationRepository.countOverlappingReservations(tableId, startTime, endTime);
        return overlapCount == null || overlapCount == 0;
    }

    @Transactional
    @SuppressWarnings("null")
    public com.restaurant.tableservice.entity.TableReservation updateReservationStatus(Integer id, String status) {
        if (id == null) throw new RuntimeException("Thiếu reservation_id");
        if (status == null || status.isBlank()) throw new RuntimeException("Thiếu trạng thái");
        com.restaurant.tableservice.entity.TableReservation reservation = tableReservationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy đặt bàn"));
        reservation.setStatus(status);
        tableReservationRepository.save(reservation);

        // Khi hoàn thành hoặc khách không đến → đóng phiên bàn
        if ("completed".equals(status) || "no_show".equals(status)) {
            invalidateTableKey(reservation.getTableId());
        }

        // Khi xác nhận → gửi email thông báo cho khách
        if ("confirmed".equals(status) && reservation.getCustomerEmail() != null
                && !reservation.getCustomerEmail().isBlank()) {
            try {
                sendConfirmationEmail(reservation);
            } catch (Exception ex) {
                log.warn("Không thể gửi email xác nhận cho đơn #{}: {}", id, ex.getMessage());
            }
        }

        return reservation;
    }

    /** Khách tự hủy đơn của mình (chỉ được hủy pending hoặc confirmed). */
    @Transactional
    @SuppressWarnings("null")
    public com.restaurant.tableservice.entity.TableReservation cancelMyReservation(
            @NonNull Integer reservationId, @NonNull Integer customerId) {
        com.restaurant.tableservice.entity.TableReservation reservation =
                tableReservationRepository.findById(reservationId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn đặt bàn"));
        if (!customerId.equals(reservation.getCustomerId())) {
            throw new RuntimeException("Bạn không có quyền hủy đơn này");
        }
        if ("confirmed".equals(reservation.getStatus())) {
            throw new RuntimeException(
                "Đơn đặt bàn của bạn đã được nhà hàng xác nhận nên không thể tự hủy. " +
                "Nếu bạn cần hỗ trợ, vui lòng liên hệ hotline: 0792967979 để được phục vụ.");
        }
        if ("serving".equals(reservation.getStatus()) || "completed".equals(reservation.getStatus())) {
            throw new RuntimeException("Không thể hủy đơn khi đang phục vụ hoặc đã hoàn thành");
        }
        if ("cancelled".equals(reservation.getStatus())) {
            throw new RuntimeException("Đơn đặt bàn đã được hủy");
        }
        reservation.setStatus("cancelled");
        return tableReservationRepository.save(reservation);
    }

    /** Auto-cancel: mỗi phút kiểm tra các đơn confirmed quá 20 phút chưa nhận bàn. */
    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void autoCancelExpiredReservations() {
        LocalDateTime deadline = ZonedDateTime.now(VN_ZONE)
                .minusMinutes(AUTO_CANCEL_GRACE_MINUTES)
                .toLocalDateTime();
        List<com.restaurant.tableservice.entity.TableReservation> expired =
                tableReservationRepository.findConfirmedPastDeadline(deadline);
        for (com.restaurant.tableservice.entity.TableReservation r : expired) {
            r.setStatus("no_show");
            tableReservationRepository.save(r);
            log.info("Auto-cancel reservation #{} (bàn {}, khách {}): quá 20 phút chưa nhận bàn",
                    r.getId(), r.getTableId(), r.getCustomerName());
        }
    }

    public List<com.restaurant.tableservice.entity.TableReservation> getMyReservations(@NonNull Integer customerId) {
        return tableReservationRepository.findByCustomerIdOrderByStartTimeDesc(customerId);
    }

    public List<com.restaurant.tableservice.entity.TableReservation> getAllReservations(String status) {
        if (status == null || status.isBlank() || "all".equalsIgnoreCase(status)) {
            return tableReservationRepository.findAllByOrderByStartTimeDesc();
        }
        return tableReservationRepository.findByStatusOrderByStartTimeDesc(status);
    }

    /**
     * Trả về reservation pending/confirmed tiếp theo của bàn (nếu có).
     * Dùng để admin FE biết bàn sắp có khách đặt.
     */
    public Optional<com.restaurant.tableservice.entity.TableReservation> getUpcomingReservation(@NonNull Integer tableId) {
        return tableReservationRepository.findNextUpcomingReservation(tableId, LocalDateTime.now());
    }

    /**
     * Trả về active key của bàn (nếu còn hiệu lực).
     * Dùng để FE admin hiển thị thời gian còn lại cho phiên hiện tại.
     */
    public java.util.Optional<TableKey> getActiveKey(@NonNull Integer tableId) {
        return tableKeyRepository.findActiveKey(tableId);
    }

    @Transactional
    public RestaurantTable createTable(@NonNull RestaurantTable table) {
        if (table.getName() == null) throw new RuntimeException("Tên bàn là bắt buộc");
        if (table.getStatus() == null) table.setStatus("Trống");
        return tableRepository.save(table);
    }

    @SuppressWarnings("null")
    @Transactional
    public RestaurantTable updateTable(@NonNull Integer id, String name, String status, Boolean isBuffet, Integer capacity) {
        RestaurantTable existing = getTableById(id);
        if (name != null) existing.setName(name);

        if (status != null) {
            // BUG-016: Validate status transition
            if (!VALID_TABLE_STATUSES.contains(status)) {
                throw new IllegalArgumentException("Trạng thái bàn không hợp lệ: " + status);
            }
            existing.setStatus(status);
            if ("Trống".equals(status)) {
                tableKeyRepository.invalidateKeysByTableId(id);
            }
        }

        if (isBuffet != null) existing.setIsBuffet(isBuffet);
        if (capacity != null && capacity > 0) existing.setCapacity(capacity);
        return tableRepository.save(existing);
    }

    @Transactional
    public void deleteTable(@NonNull Integer id) {
        // BUG-015: Xóa cascade reservation và key trước khi xóa bàn
        tableKeyRepository.deleteByTableId(id);
        tableReservationRepository.deleteByTableId(id);
        tableRepository.deleteById(id);
    }

    /**
     * Checkin nghiệp vụ: khách đến nhận bàn theo đơn đã confirmed.
     *
     * Các bước:
     *  1. Tìm reservation theo id, kiểm tra status = confirmed.
     *  2. Đơn không được quá giờ kết thúc (grace +30 phút).
     *  3. Tạo dynamic QR key cho bàn (expiry thông minh nếu có reservation khác sau đó).
     *  4. Cập nhật reservation → status = "serving".
     *
     * Response trả thêm: reservation_id, customer_name, table_name (ngoài QR fields chuẩn).
     */
    @Transactional
    @SuppressWarnings("null")
    public Map<String, Object> checkinReservation(@NonNull Integer reservationId) {
        com.restaurant.tableservice.entity.TableReservation reservation =
                tableReservationRepository.findById(reservationId)
                        .orElseThrow(() -> new RuntimeException("Không tìm thấy đơn đặt bàn"));

        if (!"confirmed".equals(reservation.getStatus())) {
            throw new RuntimeException("Chỉ có thể nhận bàn cho đơn đã xác nhận (confirmed). "
                    + "Trạng thái hiện tại: " + reservation.getStatus());
        }

        // Grace period: cho phép check-in tối đa 30 phút sau giờ kết thúc dự kiến
        LocalDateTime deadline = reservation.getEndTime().plusMinutes(30);
        if (LocalDateTime.now().isAfter(deadline)) {
            String endStr = reservation.getEndTime().format(DateTimeFormatter.ofPattern("HH:mm dd/MM"));
            throw new RuntimeException("Đơn đặt bàn đã quá giờ kết thúc (" + endStr + "). Vui lòng tạo đặt bàn mới.");
        }

        // Tạo QR động — tự xử lý expiry thông minh dựa vào reservation sắp tới kế tiếp
        Map<String, Object> qrResult = generateDynamicQRCode(reservation.getTableId());

        // Cập nhật trạng thái reservation → đang phục vụ
        reservation.setStatus("serving");
        tableReservationRepository.save(reservation);

        RestaurantTable table = getTableById(reservation.getTableId());

        Map<String, Object> result = new HashMap<>(qrResult);
        result.put("reservation_id", reservationId);
        result.put("customer_name", reservation.getCustomerName());
        result.put("customer_phone", reservation.getCustomerPhone());
        result.put("party_size", reservation.getPartySize());
        result.put("table_name", table.getName());
        return result;
    }

    /**
     * Xác thực table key của khách gọi món tại bàn.
     *
     * Response fields:
     *   valid           – true nếu phiên hợp lệ
     *   reason          – "ok" | "not_found" | "expired" | "taken"
     *   seconds_remaining – giây còn lại của phiên (0 nếu invalid)
     *   expires_at      – LocalDateTime hết hạn (chỉ có khi valid = true)
     */
    @Transactional
    @SuppressWarnings("null")
    public Map<String, Object> validateTableKey(@NonNull Integer tableId,
                                                @NonNull String tableKey,
                                                String deviceSession) {
        List<TableKey> keys = tableKeyRepository.findValidKey(tableId, tableKey);
        if (keys.isEmpty()) {
            // Phân biệt "hết hạn / bị invalidate" vs "key chưa bao giờ tồn tại"
            String reason = tableKeyRepository.findByTableIdAndKeyValue(tableId, tableKey).isPresent()
                    ? "expired"
                    : "not_found";
            return Map.of("valid", false, "reason", reason, "seconds_remaining", 0);
        }

        TableKey key = keys.get(0);

        // --- Device-session binding (chống 1 QR dùng trên nhiều thiết bị) ---
        if (deviceSession != null) {
            if (key.getDeviceSession() == null) {
                // Atomic claim — tránh race condition 2 thiết bị cùng claim
                int claimed = tableKeyRepository.claimDeviceSession(key.getId(), deviceSession);
                if (claimed == 0) {
                    key = tableKeyRepository.findById(key.getId()).orElse(key);
                    if (!deviceSession.equals(key.getDeviceSession())) {
                        return Map.of("valid", false, "reason", "taken", "seconds_remaining", 0);
                    }
                }
            } else if (!key.getDeviceSession().equals(deviceSession)) {
                return Map.of("valid", false, "reason", "taken", "seconds_remaining", 0);
            }
        }

        // --- Cập nhật trạng thái bàn sang "Đang sử dụng" nếu chưa ---
        RestaurantTable table = getTableById(tableId);
        if (!"Đang sử dụng".equals(table.getStatus())) {
            table.setStatus("Đang sử dụng");
            tableRepository.save(table);
        }

        long secondsRemaining = Duration.between(LocalDateTime.now(), key.getExpiresAt()).toSeconds();
        Map<String, Object> result = new HashMap<>();
        result.put("valid", true);
        result.put("reason", "ok");
        result.put("seconds_remaining", Math.max(0, secondsRemaining));
        result.put("expires_at", key.getExpiresAt());
        return result;
    }

    @Transactional
    public void invalidateTableKey(@NonNull Integer tableId) {
        tableKeyRepository.invalidateKeysByTableId(tableId);
        RestaurantTable table = getTableById(tableId);
        table.setStatus("Trống");
        // BUG-017: Không reset is_buffet — đây là thuộc tính vĩnh viễn của bàn
        tableRepository.save(table);
    }

    @Transactional
    public Map<String, Object> generateStaticQRCode(@NonNull Integer id) {
        RestaurantTable table = getTableById(id);
        // Static QR trỏ thẳng đến endpoint generate-access trên table-service (:3011).
        // Khi khách quét, /generate-access sẽ tạo key mới rồi redirect sang index.html.
        String qrUrl = customerBaseUrl + "/api/tables/" + id + "/generate-access";

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
        tableKeyRepository.invalidateKeysByTableId(id);

        LocalDateTime now = LocalDateTime.now();

        // Kiểm tra reservation gần nhất của bàn này
        Optional<com.restaurant.tableservice.entity.TableReservation> upcoming =
                tableReservationRepository.findNextUpcomingReservation(id, now);

        LocalDateTime expiresAt;
        String warning = null;

        if (upcoming.isPresent()) {
            LocalDateTime reservationStart = upcoming.get().getStartTime();
            long minutesUntil = Duration.between(now, reservationStart).toMinutes();

            // Đặt buffer 15 phút trước giờ reservation để bàn kịp dọn dẹp
            final long BUFFER_MINUTES = 15;

            if (minutesUntil <= BUFFER_MINUTES) {
                // Quá gần giờ đặt bàn → không cho phép cấp key
                String timeStr = reservationStart.format(DateTimeFormatter.ofPattern("HH:mm dd/MM"));
                throw new RuntimeException(
                        "Bàn đã được đặt trước lúc " + timeStr +
                        ". Không thể cấp key mới (còn " + minutesUntil + " phút). " +
                        "Vui lòng giữ bàn cho khách có đặt chỗ."
                );
            }

            // Key hết hạn trước giờ reservation (buffer 15 phút để dọn bàn)
            LocalDateTime cappedExpiry = reservationStart.minusMinutes(BUFFER_MINUTES);
            if (cappedExpiry.isBefore(now.plusHours(2))) {
                // Có reservation trong vòng 2 giờ → cắt ngắn key
                expiresAt = cappedExpiry;
                String timeStr = reservationStart.format(DateTimeFormatter.ofPattern("HH:mm"));
                warning = "Bàn đã được đặt lúc " + timeStr +
                          ". Key sẽ tự hết hạn lúc " +
                          cappedExpiry.format(DateTimeFormatter.ofPattern("HH:mm")) +
                          " (trước " + BUFFER_MINUTES + " phút) để kịp dọn bàn.";
            } else {
                expiresAt = now.plusHours(2);
            }
        } else {
            expiresAt = now.plusHours(2);
        }

        String keyValue = UUID.randomUUID().toString();

        TableKey key = new TableKey();
        key.setTableId(id);
        key.setKeyValue(keyValue);
        key.setCreatedAt(now);
        key.setExpiresAt(expiresAt);
        key.setIsValid(true);
        tableKeyRepository.save(key);

        String qrUrl = customerBaseUrl + "/index.html?tableId=" + id + "&tableKey=" + keyValue;

        Map<String, Object> res = new HashMap<>();
        res.put("table_id", id);
        res.put("key", keyValue);
        res.put("expires_at", expiresAt);
        res.put("qr_code", QrCodeUtil.generateQrCodeBase64(qrUrl));
        res.put("url", qrUrl);
        res.put("type", "dynamic");
        if (warning != null) {
            res.put("warning", warning);
            res.put("upcoming_reservation_start", upcoming.get().getStartTime());
        }
        return res;
    }

    /**
     * Public access flow for static QR.
     * Security hardening: do not rotate/invalidate key if table already has an active session.
     * This prevents unauthenticated users from force-killing in-use sessions by repeatedly hitting generate-access.
     */
    @Transactional
    public Map<String, Object> generateDynamicQRCodeForPublicAccess(@NonNull Integer id) {
        getTableById(id);
        if (tableKeyRepository.findActiveKey(id).isPresent()) {
            throw new RuntimeException("Ban dang duoc su dung. Vui long lien he nhan vien de ho tro.");
        }
        return generateDynamicQRCode(id);
    }

    private LocalDateTime parseDateTime(String value, String fieldName) {
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_DATE_TIME);
        } catch (Exception e) {
            throw new RuntimeException("Định dạng thời gian không hợp lệ: " + fieldName);
        }
    }

    /** Gửi email HTML xác nhận đặt bàn thành công cho khách. */
    private void sendConfirmationEmail(com.restaurant.tableservice.entity.TableReservation r) throws Exception {
        String to = r.getCustomerEmail();
        if (to == null || to.isBlank()) return;

        RestaurantTable table;
        String tableName;
        try {
            table = getTableById(r.getTableId());
            tableName = table.getName();
        } catch (Exception e) {
            tableName = "Bàn #" + r.getTableId();
        }

        String startVn = r.getStartTime().atZone(VN_ZONE).format(VN_FMT);
        String endVn   = r.getEndTime().atZone(VN_ZONE).format(VN_FMT);

        String html = buildConfirmationEmailHtml(r, tableName, startVn, endVn);

        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
        helper.setTo(to);
        helper.setSubject("✅ Xác nhận đặt bàn – " + restaurantName);
        helper.setText(html, true);
        mailSender.send(msg);
        log.info("Đã gửi email xác nhận đặt bàn đến {} cho đơn #{}", to, r.getId());
    }

    private String buildConfirmationEmailHtml(
            com.restaurant.tableservice.entity.TableReservation r,
            String tableName, String startVn, String endVn) {
        return "<!DOCTYPE html><html lang='vi'><head><meta charset='UTF-8'/></head><body style='margin:0;padding:0;background:#f4f4f4;font-family:Arial,sans-serif;'>" +
               "<table width='100%' cellpadding='0' cellspacing='0' style='background:#f4f4f4;padding:30px 0;'><tr><td align='center'>" +
               "<table width='600' cellpadding='0' cellspacing='0' style='background:#ffffff;border-radius:12px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.1);'>" +
               // Header
               "<tr><td style='background:#c0392b;padding:30px 40px;text-align:center;'>" +
               "<h1 style='color:#ffffff;margin:0;font-size:26px;letter-spacing:1px;'>" + escHtml(restaurantName) + "</h1>" +
               "<p style='color:#f5b7b1;margin:6px 0 0;font-size:14px;'>Xác nhận đặt bàn</p>" +
               "</td></tr>" +
               // Greeting
               "<tr><td style='padding:30px 40px 10px;'>" +
               "<p style='font-size:16px;color:#333;margin:0;'>Kính gửi <strong>" + escHtml(r.getCustomerName()) + "</strong>,</p>" +
               "<p style='font-size:14px;color:#555;margin:12px 0 0;line-height:1.7;'>Chúng tôi rất vui được thông báo rằng đơn đặt bàn của bạn đã được <strong style='color:#27ae60;'>xác nhận thành công</strong>. Dưới đây là thông tin chi tiết:</p>" +
               "</td></tr>" +
               // Info box
               "<tr><td style='padding:10px 40px 20px;'>" +
               "<table width='100%' cellpadding='12' cellspacing='0' style='background:#fdf9f9;border:1px solid #e8d5d5;border-radius:8px;'>" +
               "<tr><td style='font-size:13px;color:#555;border-bottom:1px solid #f0e0e0;'><strong style='color:#c0392b;'>📍 Địa điểm</strong></td>" +
               "<td style='font-size:13px;color:#333;border-bottom:1px solid #f0e0e0;'>" + escHtml(restaurantAddress) + "</td></tr>" +
               "<tr><td style='font-size:13px;color:#555;border-bottom:1px solid #f0e0e0;'><strong style='color:#c0392b;'>🪑 Bàn</strong></td>" +
               "<td style='font-size:13px;color:#333;border-bottom:1px solid #f0e0e0;'>" + escHtml(tableName) + "</td></tr>" +
               "<tr><td style='font-size:13px;color:#555;border-bottom:1px solid #f0e0e0;'><strong style='color:#c0392b;'>🕐 Giờ đến</strong></td>" +
               "<td style='font-size:13px;color:#333;font-weight:bold;border-bottom:1px solid #f0e0e0;'>" + escHtml(startVn) + "</td></tr>" +
               "<tr><td style='font-size:13px;color:#555;border-bottom:1px solid #f0e0e0;'><strong style='color:#c0392b;'>🕐 Dự kiến kết thúc</strong></td>" +
               "<td style='font-size:13px;color:#333;border-bottom:1px solid #f0e0e0;'>" + escHtml(endVn) + "</td></tr>" +
               "<tr><td style='font-size:13px;color:#555;border-bottom:1px solid #f0e0e0;'><strong style='color:#c0392b;'>👥 Số khách</strong></td>" +
               "<td style='font-size:13px;color:#333;border-bottom:1px solid #f0e0e0;'>" + r.getPartySize() + " người</td></tr>" +
               (r.getIsBuffet() != null && r.getIsBuffet() ?
                   "<tr><td style='font-size:13px;color:#555;'><strong style='color:#c0392b;'>🍽️ Gói buffet</strong></td>" +
                   "<td style='font-size:13px;color:#333;'>" + escHtml(r.getBuffetPackageName() != null ? r.getBuffetPackageName() : "Có") + "</td></tr>" : "") +
               (r.getNotes() != null && !r.getNotes().isBlank() ?
                   "<tr><td style='font-size:13px;color:#555;'><strong style='color:#c0392b;'>📝 Ghi chú</strong></td>" +
                   "<td style='font-size:13px;color:#555;font-style:italic;'>" + escHtml(r.getNotes()) + "</td></tr>" : "") +
               "</table></td></tr>" +
               // Note
               "<tr><td style='padding:10px 40px 20px;'>" +
               "<div style='background:#fff8e1;border-left:4px solid #f39c12;padding:12px 16px;border-radius:4px;font-size:13px;color:#555;line-height:1.6;'>" +
               "⚠️ <strong>Lưu ý:</strong> Vui lòng đến đúng giờ. Đơn đặt bàn sẽ tự động bị hủy nếu quá <strong>20 phút</strong> kể từ giờ đặt mà chưa có mặt." +
               "</div></td></tr>" +
               // Footer
               "<tr><td style='background:#f8f8f8;padding:20px 40px;text-align:center;border-top:1px solid #eee;'>" +
               "<p style='font-size:12px;color:#999;margin:0;'>📞 Hotline: " + escHtml(restaurantPhone) + "</p>" +
               "<p style='font-size:12px;color:#bbb;margin:8px 0 0;'>Email này được gửi tự động, vui lòng không trả lời trực tiếp.</p>" +
               "</td></tr>" +
               "</table></td></tr></table>" +
               "</body></html>";
    }

    private static String escHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
