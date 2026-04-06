# Bug Report — Restaurant Management System
> Audit ngày: 06/04/2026 | Tổng: 30 bugs | CRITICAL: 6 | HIGH: 8 | MEDIUM: 11 | LOW: 5

---

## Mục lục

1. [Tóm tắt nhanh](#1-tóm-tắt-nhanh)
2. [CRITICAL — Cần sửa ngay](#2-critical--cần-sửa-ngay)
3. [HIGH — Sửa tuần này](#3-high--sửa-tuần-này)
4. [MEDIUM — Sửa sprint này](#4-medium--sửa-sprint-này)
5. [LOW — Cải thiện dần](#5-low--cải-thiện-dần)
6. [Bảng tổng hợp](#6-bảng-tổng-hợp)

---

## 1. Tóm tắt nhanh

| Mức độ | Số lượng | Ảnh hưởng chính |
|--------|----------|----------------|
| 🔴 CRITICAL | 6 | Bảo mật hệ thống, thanh toán trùng, XSS |
| 🟠 HIGH | 8 | Token rò rỉ, validate thiếu, race condition |
| 🟡 MEDIUM | 11 | Logic sai, dữ liệu mồ côi, không có transaction timeout |
| 🟢 LOW | 5 | Cấu hình cứng, performance |

### Cần làm ngay trước khi deploy production
1. JWT secret đang hardcode trong code
2. Đặt bàn có thể bị đặt trùng (race condition)
3. Thanh toán có thể bị xử lý 2 lần
4. XSS qua `innerHTML` trên trang booking
5. Validate table key không atomic
6. NullPointerException tiềm ẩn tại `/api/users` endpoint

---

## 2. CRITICAL — Cần sửa ngay

---

### BUG-001 — JWT Secret hardcode trong source code

**File:** `user-service/src/main/resources/application.yml`  
**Mức độ:** 🔴 CRITICAL — Security

**Hiện tại:**
```yaml
jwt:
  secret: restaurant_secret_key_12345678900
  expiration: 86400000
```

**Vấn đề:** Secret này được commit vào git. Bất kỳ ai đọc source code đều có thể tự ký JWT hợp lệ với bất kỳ `role_id` nào, giả mạo làm ADMIN mà không cần đăng nhập.

**Sửa:**
```yaml
jwt:
  secret: ${JWT_SECRET}   # bắt buộc từ environment, không có default
  expiration: ${JWT_EXPIRATION:86400000}
```
Đặt `JWT_SECRET` trong `.env` hoặc biến môi trường OS, **không commit file `.env`**.

---

### BUG-002 — Race condition đặt bàn trùng giờ (Reservation Overlap)

**File:** `table-service/.../service/TableService.java` — hàm `createReservation()`  
**Mức độ:** 🔴 CRITICAL — Concurrency

**Luồng lỗi:**
```
Request A: Kiểm tra overlap → 0 record → OK
Request B: Kiểm tra overlap → 0 record → OK   ← chạy song song với A
Request A: INSERT reservation
Request B: INSERT reservation → Hai đặt bàn cùng giờ, cùng bàn!
```

**Code hiện tại (không atomic):**
```java
Integer count = tableReservationRepository.countOverlappingReservations(tableId, start, end);
if (count > 0) throw new RuntimeException("Bàn đã có đặt chỗ...");
// ← race condition window ở đây
tableReservationRepository.save(reservation);
```

**Sửa — thêm unique constraint + xử lý exception:**
```sql
-- Migration
ALTER TABLE table_reservations
ADD UNIQUE INDEX uq_table_slot (table_id, start_time, end_time);
```
```java
try {
    return tableReservationRepository.save(reservation);
} catch (DataIntegrityViolationException e) {
    throw new RuntimeException("Bàn đã được đặt trong khung giờ này");
}
```
Hoặc dùng `@Lock(LockModeType.PESSIMISTIC_WRITE)` trên query kiểm tra.

---

### BUG-003 — Thanh toán có thể xử lý 2 lần (Double Payment)

**File:** `order-service/.../service/OrderService.java` — hàm `completePayment()`  
**Mức độ:** 🔴 CRITICAL — Business Logic

**Vấn đề:** Không kiểm tra order đã thanh toán chưa trước khi xử lý:
```java
for (Order order : orders) {
    // KHÔNG kiểm tra order.getPaymentStatus()
    order.setPaymentStatus("paid");
    orderRepository.save(order);
}
```

**Kịch bản lỗi:**
1. Thu ngân A click "Thanh toán" → gọi API
2. Thu ngân B (do lag mạng) click lại → gọi API cùng lúc
3. Cả 2 request đều pass → 2 bản ghi Payment tạo ra, order được đánh dấu paid 2 lần

**Sửa:**
```java
for (Order order : orders) {
    if ("paid".equals(order.getPaymentStatus())) {
        throw new RuntimeException("Order #" + order.getId() + " đã được thanh toán");
    }
    order.setPaymentStatus("paid");
    orderRepository.save(order);
}
```
Cộng thêm: thêm `UNIQUE(order_id)` trên bảng `payments`.

---

### BUG-004 — XSS qua innerHTML trên trang đặt bàn

**File:** `table-service/src/main/resources/static/booking/index.html`  
**Mức độ:** 🔴 CRITICAL — Security (XSS)

**Code lỗi:**
```javascript
tables.forEach(t => {
  sel.innerHTML += `<option value="${t.id}">${t.name} (sức chứa: ${t.capacity})</option>`;
});
```

**Tấn công:** Nếu tên bàn trong DB chứa `<script>alert(1)</script>` (do admin bị tấn công hoặc SQL injection), đoạn JS độc hại sẽ chạy trên trình duyệt khách hàng.

**Sửa — dùng DOM API thay vì innerHTML:**
```javascript
tables.forEach(t => {
  const opt = document.createElement('option');
  opt.value = t.id;
  opt.textContent = `${t.name} (sức chứa: ${t.capacity || 'N/A'})`;
  sel.appendChild(opt);
});
```
Áp dụng tương tự cho tất cả chỗ render dữ liệu từ API vào DOM (`my-reservations`, `menu`, `register`).

---

### BUG-005 — Table key validation không atomic (Race condition)

**File:** `table-service/.../service/TableService.java` — hàm `validateTableKey()`  
**Mức độ:** 🔴 CRITICAL — Concurrency

**Code lỗi (3 DB round-trips riêng biệt):**
```java
List<TableKey> keys = tableKeyRepository.findValidKey(tableId, tableKey);
if (keys.isEmpty()) return false;
TableKey key = keys.get(0);
if (key.getDeviceSession() == null) {
    key.setDeviceSession(deviceSession);  // ← ghi lần 2
    tableKeyRepository.save(key);
}
return key.getDeviceSession().equals(deviceSession);
```

**Vấn đề:** 2 thiết bị gọi đồng thời → cả 2 đều thấy `deviceSession == null` → cả 2 đều claim key → 2 thiết bị dùng cùng bàn.

**Sửa — atomic update:**
```java
@Modifying
@Query("UPDATE TableKey k SET k.deviceSession = :device " +
       "WHERE k.id = :id AND k.deviceSession IS NULL AND k.isValid = true")
int claimDeviceSession(@Param("id") Integer id, @Param("device") String device);
```
```java
// Nếu update trả về 0 → đã bị claim bởi thiết bị khác
int updated = tableKeyRepository.claimDeviceSession(key.getId(), deviceSession);
if (updated == 0 && !deviceSession.equals(key.getDeviceSession())) return false;
```

---

### BUG-006 — NullPointerException tiềm ẩn tại GET /api/users

**File:** `user-service/.../controller/UserController.java`  
**Mức độ:** 🔴 CRITICAL — Security + Stability

**Code lỗi:**
```java
@GetMapping("/users")
public ResponseEntity<?> getUsers(HttpServletRequest request) {
    Integer roleId = (Integer) request.getAttribute("roleId");
    if (roleId != 1) {  // ← NullPointerException nếu roleId == null
        return ResponseEntity.status(403).build();
    }
    ...
}
```

**Xảy ra khi:** Request từ endpoint public hoặc JWT filter bỏ qua attribute. `roleId` là `null`, unboxing `null != 1` ném NPE → server trả 500 thay vì 403.

**Sửa:**
```java
Integer roleId = (Integer) request.getAttribute("roleId");
if (roleId == null || roleId != 1) {
    return ResponseEntity.status(403).build();
}
```
Áp dụng pattern này cho **tất cả** endpoint có kiểm tra `roleId`.

---

## 3. HIGH — Sửa tuần này

---

### BUG-007 — JWT không bị vô hiệu hóa khi logout

**File:** `Fe-Admin/lib/auth.ts`, `table-service/src/main/resources/static/js/auth.js`  
**Mức độ:** 🟠 HIGH — Security

**Vấn đề:** Logout chỉ xóa token khỏi localStorage. Token vẫn hợp lệ trên server trong 24 giờ. Nếu token bị lộ (XSS, log files, network sniff), kẻ tấn công có thể dùng tới khi hết hạn.

**Sửa tạm thời (không cần Redis):**
```sql
CREATE TABLE revoked_tokens (
  token_hash VARCHAR(64) PRIMARY KEY,  -- SHA256 của token
  revoked_at DATETIME,
  expires_at DATETIME
);
```
```java
// Trong JwtAuthenticationFilter: kiểm tra token_hash khi validate
```
**Sửa đúng:** Dùng HTTPOnly cookie + refresh token pattern.

---

### BUG-008 — Không validate định dạng email khi đăng ký

**File:** `user-service/.../dto/RegisterRequest.java` và `AuthService.java`  
**Mức độ:** 🟠 HIGH — Input Validation

**Vấn đề:** Check chỉ dựa vào "có chứa @" không phải regex:
```java
private boolean isEmail(String identifier) {
    return identifier.contains("@");
}
```

**Các input pass validation nhưng sai:**
- `a@` → pass (không có domain)
- `@gmail.com` → pass (không có local part)
- `test@test` → pass (domain không có TLD)

**Sửa:**
```java
private static final Pattern EMAIL_REGEX = 
    Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

private boolean isEmail(String identifier) {
    return EMAIL_REGEX.matcher(identifier).matches();
}
```

---

### BUG-009 — Password policy quá yếu (tối thiểu 6 ký tự)

**File:** `user-service/.../dto/RegisterRequest.java`  
**Mức độ:** 🟠 HIGH — Security

**Vấn đề:** `@Size(min = 6)` cho phép `123456` làm mật khẩu.

**Sửa:**
```java
@Pattern(
    regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$",
    message = "Mật khẩu tối thiểu 8 ký tự, gồm chữ thường, chữ hoa và số"
)
private String password;
```

---

### BUG-010 — Request payment race condition (payment_status duplicate)

**File:** `order-service/.../service/OrderService.java` — hàm `requestPayment()`  
**Mức độ:** 🟠 HIGH — Concurrency

**Vấn đề:** Không kiểm tra `payment_status` trước khi set `"waiting"`. Khách có thể bấm "Yêu cầu thanh toán" nhiều lần → nhiều `PaymentRequest` record với cùng `order_id`.

**Sửa:**
```java
// Trước khi tạo PaymentRequest
Order order = orderRepository.findById(orderId).orElseThrow();
if (!"unpaid".equals(order.getPaymentStatus())) {
    throw new RuntimeException("Order đã trong trạng thái " + order.getPaymentStatus());
}
```

---

### BUG-011 — escHtml() không escape dấu nháy đơn — XSS trong onclick

**File:** `table-service/src/main/resources/static/js/navbar.js`  
**Mức độ:** 🟠 HIGH — Security (XSS)

**Code lỗi:**
```javascript
function escHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
        // ← THIẾU: .replace(/'/g, '&#39;')
}
```

**Tấn công:** Nếu `fullName = "O'Malley"` được đặt trong `onclick='action("O'Malley")'` → phá vỡ chuỗi JS.

**Sửa:**
```javascript
function escHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#39;');
}
```

---

### BUG-012 — JWT lưu trong localStorage — dễ bị đánh cắp qua XSS

**File:** `Fe-Admin/lib/auth.ts`, `static/js/auth.js`  
**Mức độ:** 🟠 HIGH — Security

**Vấn đề:** `localStorage` không có bảo vệ XSS. Bất kỳ script nào trên trang (kể cả từ XSS) có thể đọc `localStorage.token`.

**Sửa đúng (long-term):** Migrate sang HTTPOnly Secure Cookie (backend set-cookie, FE không đọc được trực tiếp).

**Sửa tạm (giảm thiểu rủi ro):**
- Kết hợp với Trusted Types / CSP header để hạn chế XSS
- Áp dụng Content Security Policy nghiêm ngặt tại gateway
- Giảm JWT expiry xuống còn 1-2 giờ, implement refresh token

---

### BUG-013 — Payment service không validate order còn tồn tại

**File:** `payment-service/.../service/PaymentService.java`  
**Mức độ:** 🟠 HIGH — Data Integrity

**Vấn đề:** Xử lý thanh toán mà không gọi order-service kiểm tra order có tồn tại không. Nếu order bị xóa hoặc `order_id` giả mạo → vẫn tạo Payment record.

**Sửa:**
```java
// Gọi order-service qua Feign/RestTemplate trước khi xử lý
OrderDto order = orderClient.getOrder(orderId);
if (order == null) {
    throw new RuntimeException("Order không tồn tại");
}
if (!"waiting".equals(order.getPaymentStatus())) {
    throw new RuntimeException("Order chưa yêu cầu thanh toán");
}
```

---

### BUG-014 — payment-service không check payment_status của order trước khi process

**File:** `payment-service/.../controller/PaymentController.java`  
**Mức độ:** 🟠 HIGH — Business Logic

**Vấn đề:** Endpoint `POST /api/payments/process/cash` không kiểm tra `payment_status == "waiting"` của order. Có thể xử lý payment của order `"unpaid"` hoặc `"paid"` → sai trạng thái, mất tiền.

**Sửa:** Verify `paymentRequest.getStatus().equals("waiting")` trước khi tiến hành process.

---

## 4. MEDIUM — Sửa sprint này

---

### BUG-015 — Xóa bàn không xóa reservation liên quan (Orphaned Records)

**File:** `table-service/.../service/TableService.java` — hàm `deleteTable()`  
**Mức độ:** 🟡 MEDIUM — Data Integrity

**Vấn đề:**
```java
public void deleteTable(Integer id) {
    tableRepository.deleteById(id);
    // ← KHÔNG xóa table_reservations, table_keys
}
```
Kết quả: các `table_reservations` và `table_keys` vô chủ, query vẫn trả về chúng.

**Sửa:**
```java
@Transactional
public void deleteTable(Integer id) {
    tableKeyRepository.deleteByTableId(id);
    tableReservationRepository.deleteByTableId(id);
    tableRepository.deleteById(id);
}
```

---

### BUG-016 — Không validate status transition hợp lệ khi update bàn

**File:** `table-service/.../service/TableService.java` — hàm `updateTable()`  
**Mức độ:** 🟡 MEDIUM — Logic

**Vấn đề:** Cho phép set status thành bất kỳ chuỗi nào:
```java
if (status != null) existing.setStatus(status);
```
Status không hợp lệ như `"Đang sửa chữa"`, `"null"`, `""` đều được lưu vào DB.

**Sửa:**
```java
private static final Set<String> VALID_STATUSES = Set.of("Trống", "Đang sử dụng", "Đã đặt");

if (status != null) {
    if (!VALID_STATUSES.contains(status)) {
        throw new IllegalArgumentException("Trạng thái không hợp lệ: " + status);
    }
    existing.setStatus(status);
}
```

---

### BUG-017 — invalidate key reset is_buffet = false (sai logic)

**File:** `table-service/.../service/TableService.java` — hàm `invalidateAllKeys()`  
**Mức độ:** 🟡 MEDIUM — Logic

**Vấn đề:**
```java
table.setStatus("Trống");
table.setIsBuffet(false);  // ← SAI! is_buffet là thuộc tính vĩnh viễn của bàn
tableRepository.save(table);
```
Sau mỗi lần kết thúc phiên, bàn buffet bị chuyển thành bàn thường. Staff phải mở Fe-Admin để bật lại.

**Sửa:** Chỉ reset status, không đụng đến `is_buffet`:
```java
table.setStatus("Trống");
// Bỏ dòng: table.setIsBuffet(false);
tableRepository.save(table);
```

---

### BUG-018 — Email verification redirect sai port (3001 thay vì 3011)

**File:** `user-service/src/main/resources/application.yml`  
**Mức độ:** 🟡 MEDIUM — Configuration / Logic

**Vấn đề:**
```yaml
app:
  frontend-url: http://localhost:3001  # ← SAI: customer-web service đã bị xóa
```
Sau khi xác thực email, người dùng bị redirect tới `http://localhost:3001/login?verified=true` — cổng không có gì đang chạy → trang trắng.

**Sửa:**
```yaml
app:
  frontend-url: http://localhost:3011
```

---

### BUG-019 — Booking page không kiểm tra availability trước khi submit

**File:** `table-service/src/main/resources/static/booking/index.html`  
**Mức độ:** 🟡 MEDIUM — UX + Logic

**Vấn đề:** Form đặt bàn POST thẳng vào `/reservations`. Chỉ khi server trả lỗi thì customer mới biết bàn đã bị đặt. API `GET /api/tables/{id}/reservations/availability` có sẵn nhưng không được gọi.

**Sửa — gọi availability check trước:**
```javascript
async function submitForm() {
    const tableId = document.getElementById('tableId').value;
    const start = buildDateTime();
    const end = buildEndDateTime();

    // Kiểm tra trước
    const check = await apiFetch(
        `/api/tables/${tableId}/reservations/availability?start=${start}&end=${end}`
    );
    const data = await check.json();
    if (!data.available) {
        showError('Bàn này đã có đặt chỗ trong khung giờ đã chọn');
        return;
    }

    // Mới thực sự POST
    const res = await apiFetch(`/api/tables/${tableId}/reservations`, { method: 'POST', ... });
}
```

---

### BUG-020 — Customer_id không được validate tồn tại trong userdb

**File:** `table-service/.../controller/TableController.java`  
**Mức độ:** 🟡 MEDIUM — Data Integrity

**Vấn đề:** Khi tạo reservation, `customer_id` được lấy từ JWT claim `id` và ghi thẳng vào DB mà không gọi user-service kiểm tra user có thực sự tồn tại không. Nếu user bị xóa khỏi userdb, reservation vẫn có `customer_id` trỏ tới record không tồn tại.

**Sửa (short-term):** Thêm soft-delete flag trên users thay vì xóa hẳn.  
**Sửa (long-term):** Gọi user-service để verify trước khi ghi.

---

### BUG-021 — Không có transaction timeout — nguy cơ deadlock

**File:** `order-service/.../service/OrderService.java`  
**Mức độ:** 🟡 MEDIUM — Stability

**Vấn đề:** `@Transactional` không có timeout → 1 transaction chậm (gọi service ngoài bên trong transaction) có thể giữ lock DB vô thời hạn.

**Sửa:**
```java
@Transactional(timeout = 30)   // 30 giây
public Order createOrder(OrderRequest request) { ... }
```

---

### BUG-022 — buffet_session_id do FE tự sinh, không validate server-side

**File:** `table-service/src/main/resources/static/index.html` (dine-in app)  
**Mức độ:** 🟡 MEDIUM — Data Integrity / Logic

**Vấn đề:**
```javascript
const buffetSessionId = `buffet_${tableId}_${Date.now()}`;
```
FE tự tạo session ID. Backend không kiểm tra format hay tính hợp lệ của session ID này. Khách có thể:
- Điền `buffet_session_id` của bàn khác → gộp order vào bàn khác
- Tạo session ID ngẫu nhiên

**Sửa:** Backend generate `buffet_session_id` khi nhận order buffet đầu tiên, trả về cho FE:
```java
if (isBuffet && buffetSessionId == null) {
    buffetSessionId = UUID.randomUUID().toString();
}
```

---

### BUG-023 — Không rate limiting trên auth endpoints

**File:** `user-service/.../controller/AuthController.java`  
**Mức độ:** 🟡 MEDIUM — Security

**Vấn đề:** `/api/users/login` và `/api/users/register` không có rate limiting. Brute-force 1000 lần/giây không bị chặn.

**Sửa tại API Gateway:**
```yaml
# api-gateway/src/main/resources/application.yml
spring:
  cloud:
    gateway:
      routes:
        - id: user-service
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 5
                redis-rate-limiter.burstCapacity: 10
```
Hoặc dùng `Bucket4j` nếu không có Redis.

---

### BUG-024 — Kitchen notification không có retry nếu thất bại

**File:** `order-service/.../service/OrderService.java` — hàm `confirmOrder()`  
**Mức độ:** 🟡 MEDIUM — Reliability

**Vấn đề:** Order bị đánh dấu `"Đang nấu"` rồi mới gọi kitchen-service. Nếu kitchen-service chết:
```java
order.setStatus("Đang nấu");
orderRepository.save(order);
// ← Nếu dòng dưới throw exception, order đã "Đang nấu" nhưng bếp không biết!
kitchenClient.notifyKitchen(payload);
```

**Sửa:** Gọi kitchen trước, update status sau:
```java
kitchenClient.notifyKitchen(payload);   // Nếu lỗi → throw, rollback
order.setStatus("Đang nấu");
orderRepository.save(order);
```
Hoặc đặt trong cùng `@Transactional` với `@Retryable` trên `kitchenClient`.

---

### BUG-025 — Sensitive data trong log (food_id, order detail, tồn kho)

**File:** `kitchen-service/.../service/KitchenService.java`  
**Mức độ:** 🟡 MEDIUM — Security

**Vấn đề:**
```java
log.info("[TRU KHO] foodId={}, quantity={}", foodId, quantity);
```
Log level INFO được ghi trong production → lộ data nghiệp vụ, dễ leak qua log aggregator.

**Sửa:**
- Chuyển sang `log.debug()` (chỉ hiện khi `logging.level=DEBUG`)
- Bật log DEBUG chỉ trong profile `local`, không phải `production`

---

## 5. LOW — Cải thiện dần

---

### BUG-026 — API_BASE hardcode trong config.js

**File:** `table-service/src/main/resources/static/js/config.js`  
**Mức độ:** 🟢 LOW — Portability

**Vấn đề:** `const API_BASE = 'http://localhost:3000'` — không thể deploy lên server khác mà không sửa tay.

**Sửa:** Dùng relative URL hoặc inject qua template:
```javascript
// Tự detect theo domain hiện tại
const API_BASE = window.location.protocol + '//' + window.location.hostname + ':3000';
```
Hoặc nếu deploy cùng domain:
```javascript
const API_BASE = '';  // Dùng relative path, proxy handled by nginx
```

---

### BUG-027 — Thiếu database index cho cột query nhiều

**File:** Migration SQL  
**Mức độ:** 🟢 LOW — Performance

**Vấn đề:** Các cột `orders.table_key`, `orders.payment_status`, `table_keys.key_value` được query thường xuyên nhưng có thể không có index.

**Sửa:**
```sql
ALTER TABLE orders ADD INDEX idx_orders_table_key (table_key);
ALTER TABLE orders ADD INDEX idx_orders_payment_status (payment_status);
ALTER TABLE table_keys ADD INDEX idx_keys_value_valid (key_value, is_valid);
ALTER TABLE kitchen_queue ADD INDEX idx_kitchen_status (status);
```

---

### BUG-028 — Code upgrade password plaintext còn trong production

**File:** `user-service/.../service/AuthService.java`  
**Mức độ:** 🟢 LOW — Code Quality

**Vấn đề:** Logic hỗ trợ mật khẩu plaintext (legacy) vẫn còn trong code:
```java
// Legacy password upgrade
if (!user.getPassword().startsWith("$2a$")) {
    if (user.getPassword().equals(rawPassword)) {
        // upgrade to BCrypt...
    }
}
```
Rủi ro: Nếu ai đó có plaintext password trong DB → đăng nhập không qua BCrypt check.

**Sửa:** Chạy migration để upgrade tất cả password, rồi xóa code legacy:
```sql
-- Sau khi migrate: các user vẫn dùng plaintext phải đổi mật khẩu
UPDATE users SET password = NULL WHERE password NOT LIKE '$2a$%';
```

---

### BUG-029 — Không có CORS configuration tường minh tại từng service

**File:** Các service Spring Boot  
**Mức độ:** 🟢 LOW — Security

**Vấn đề:** CORS cấu hình `Allow: *` tại API Gateway. Nếu có service nào bị expose trực tiếp (bypass gateway), không có CORS protection.

**Sửa:** Thêm `@CrossOrigin` giới hạn hoặc `WebMvcConfigurer` tại từng service:
```java
@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:3010", "http://localhost:3011")
            .allowedMethods("GET","POST","PUT","DELETE");
    }
}
```

---

### BUG-030 — Inventory không tự trừ khi có order

**File:** `order-service` / `menu-service`  
**Mức độ:** 🟢 LOW — Feature Gap (không phải bug logic nhưng gây hiểu lầm)

**Vấn đề:** `food_ingredients` trong menudb tồn tại (link food → ingredient + amount) nhưng khi tạo order, inventory-service không bao giờ được gọi để trừ kho. Module inventory tồn kho chỉ là CRUD thủ công.

**Sửa (nếu muốn feature hoạt động):**
Trong `OrderService.createOrder()`, sau khi save order_details:
```java
for (OrderDetail detail : savedDetails) {
    inventoryClient.deductStock(detail.getFoodId(), detail.getQuantity());
}
```

---

## 6. Bảng tổng hợp

| # | Title | File | Mức độ | Loại | Trạng thái |
|---|-------|------|--------|------|------------|
| 001 | JWT secret hardcode | `application.yml` | 🔴 CRITICAL | Security | Chưa sửa |
| 002 | Reservation race condition | `TableService.java` | 🔴 CRITICAL | Concurrency | Chưa sửa |
| 003 | Double payment | `OrderService.java` | 🔴 CRITICAL | Logic | Chưa sửa |
| 004 | XSS innerHTML booking | `booking/index.html` | 🔴 CRITICAL | Security | Chưa sửa |
| 005 | Table key race condition | `TableService.java` | 🔴 CRITICAL | Concurrency | Chưa sửa |
| 006 | NPE tại /api/users | `UserController.java` | 🔴 CRITICAL | Stability | Chưa sửa |
| 007 | JWT không invalidate khi logout | `auth.ts` / `auth.js` | 🟠 HIGH | Security | Chưa sửa |
| 008 | Validate email yếu | `AuthService.java` | 🟠 HIGH | Validation | Chưa sửa |
| 009 | Password policy yếu | `RegisterRequest.java` | 🟠 HIGH | Security | Chưa sửa |
| 010 | Payment request race | `OrderService.java` | 🟠 HIGH | Concurrency | Chưa sửa |
| 011 | escHtml thiếu nháy đơn | `navbar.js` | 🟠 HIGH | Security | Chưa sửa |
| 012 | JWT trong localStorage | `auth.ts` / `auth.js` | 🟠 HIGH | Security | Chưa sửa |
| 013 | Payment không validate order | `PaymentService.java` | 🟠 HIGH | Data Integrity | Chưa sửa |
| 014 | Payment không check status | `PaymentController.java` | 🟠 HIGH | Logic | Chưa sửa |
| 015 | Xóa bàn → orphaned records | `TableService.java` | 🟡 MEDIUM | Data Integrity | Chưa sửa |
| 016 | Status transition không validate | `TableService.java` | 🟡 MEDIUM | Logic | Chưa sửa |
| 017 | invalidate key reset is_buffet | `TableService.java` | 🟡 MEDIUM | Logic | Chưa sửa |
| 018 | Email redirect sai port 3001 | `application.yml` | 🟡 MEDIUM | Config | Chưa sửa |
| 019 | Booking không check availability | `booking/index.html` | 🟡 MEDIUM | UX / Logic | Chưa sửa |
| 020 | customer_id không validate | `TableController.java` | 🟡 MEDIUM | Data Integrity | Chưa sửa |
| 021 | Không có transaction timeout | `OrderService.java` | 🟡 MEDIUM | Stability | Chưa sửa |
| 022 | buffet_session_id client-generated | `index.html` | 🟡 MEDIUM | Logic | Chưa sửa |
| 023 | Không rate limiting auth | `AuthController.java` | 🟡 MEDIUM | Security | Chưa sửa |
| 024 | Kitchen notification không retry | `OrderService.java` | 🟡 MEDIUM | Reliability | Chưa sửa |
| 025 | Sensitive data in logs | `KitchenService.java` | 🟡 MEDIUM | Security | Chưa sửa |
| 026 | API_BASE hardcode | `config.js` | 🟢 LOW | Portability | Chưa sửa |
| 027 | Thiếu database index | Migration | 🟢 LOW | Performance | Chưa sửa |
| 028 | Legacy plaintext password code | `AuthService.java` | 🟢 LOW | Code Quality | Chưa sửa |
| 029 | CORS không tường minh | Các service | 🟢 LOW | Security | Chưa sửa |
| 030 | Inventory không auto-deduct | `OrderService.java` | 🟢 LOW | Feature Gap | Chưa sửa |

---

## Ưu tiên xử lý đề xuất

### Ngay hôm nay (trước khi demo/deploy)
- BUG-001: Chuyển JWT secret sang environment variable
- BUG-006: Sửa null check tại UserController
- BUG-017: Bỏ `is_buffet = false` trong invalidate key
- BUG-018: Sửa `frontend-url` từ `:3001` thành `:3011`

### Tuần này
- BUG-003: Thêm kiểm tra đã thanh toán chưa trong completePayment
- BUG-004 + BUG-011: Sửa tất cả chỗ dùng innerHTML thành DOM API + thêm escape nháy đơn
- BUG-014: payment-service check payment_status trước khi process
- BUG-019: Gọi availability check trước khi submit đặt bàn

### Sprint tiếp
- BUG-002 + BUG-005: Giải quyết race condition qua locking/unique constraint
- BUG-007: Implement token revocation
- BUG-015: Cascade delete khi xóa bàn
- BUG-022: Chuyển buffet_session_id generation về backend
- BUG-023: Thêm rate limiting

### Backlog
- BUG-008, BUG-009: Cải thiện validation
- BUG-012: Migrate sang HTTPOnly cookie
- BUG-024: Retry mechanism cho kitchen notification
- BUG-027: Thêm database indexes
- BUG-030: Implement inventory auto-deduction
