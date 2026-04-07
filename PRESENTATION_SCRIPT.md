# 🎤 KỊCH BẢN THUYẾT TRÌNH
## Hệ thống Quản lý Nhà hàng — Các Chức năng Nâng cao

> **Đối tượng:** Hội đồng / Giảng viên hướng dẫn  
> **Thời lượng gợi ý:** 25–35 phút  
> **Người thuyết trình:** [Tên nhóm]

---

## 🗂️ MỤC LỤC TRÌNH BÀY

1. Tổng quan kiến trúc hệ thống
2. Quản lý phiên gọi món theo bàn (Session-based Ordering)
3. Luồng đặt Buffet
4. Real-time với WebSocket / STOMP
5. Quản lý hàng đợi bếp & đồng bộ trạng thái tự động
6. QR Code thông minh & đặt bàn chống xung đột
7. Bảo mật nâng cao (JWT, RBAC, Token Blacklist)
8. Tự động trừ kho nguyên liệu
9. Trang Thu ngân — Thanh toán & In hóa đơn nhiệt
10. Demo trực tiếp

---

## 📌 PHẦN 1 — TỔNG QUAN KIẾN TRÚC

### 🎙️ Lời dẫn

> *"Thưa hội đồng, hệ thống của chúng em được xây dựng theo mô hình **Microservices** với 9 service độc lập, giao tiếp qua **REST API** và **WebSocket STOMP** để đảm bảo real-time. Toàn bộ frontend là **Next.js 16** với role-based UI."*

### Sơ đồ kiến trúc

```
[Khách hàng - Web]     [Admin Panel - Fe-Admin]
        │                        │
        └──────────┬─────────────┘
                   ▼
          [API Gateway :3000]
                   │
    ┌──────────────┼──────────────┐
    ▼              ▼              ▼
[User :3005] [Order :3003] [Table :3006]
                   │
         ┌─────────┼──────────┐
         ▼         ▼          ▼
  [Kitchen:3004] [Payment:3008] [Menu:3002]
                               │
                      [Inventory:3007]
                      [Image:3009]
```

### Điểm nhấn kỹ thuật
- **9 microservices** — mỗi service có database riêng
- **API Gateway** (Spring Cloud Gateway) — routing, CORS, header stripping
- **WebSocket trực tiếp** tới service (không qua Gateway) — giải quyết giới hạn proxy WebSocket
- **Tất cả khởi động bằng 1 lệnh** `all.bat`

---

## 📌 PHẦN 2 — PHIÊN GỌI MÓN THEO BÀN

### 🎙️ Lời dẫn

> *"Thách thức đầu tiên: làm sao để nhiều lần gọi món từ cùng 1 bàn được gộp lại thành 1 hóa đơn duy nhất, trong khi khách có thể đặt thêm bất kỳ lúc nào?"*

### Vấn đề & Giải pháp

**Vấn đề:** Một bàn có thể tạo 3–5 đơn hàng riêng lẻ trong suốt bữa ăn.

**Giải pháp — Session-based Ordering:**
- Khi bàn được phục vụ, server cấp `table_key` (UUID) có hiệu lực **2 tiếng**
- Mọi đơn hàng từ bàn đó đều mang `(table_id, table_key)`
- Hệ thống nhận diện chúng là **cùng 1 phiên** và gộp lại

```java
// OrderService.java — getSessionDetail()
// Truy vấn tất cả đơn cùng phiên
findByTableIdAndTableKeyAndPaymentStatusNot("unpaid")
```

### Giao diện minh họa

- **Orders Page (Admin):** Hiển thị dạng "Session Summary" — 1 hàng = 1 bàn, không phải 1 đơn
- Cột: *Đại diện đơn #, Bàn, Số đơn, Tổng món, Tổng tiền, Trạng thái*
- Click vào → xem chi tiết từng đơn trong phiên

> 💡 **Nhấn mạnh:** Đây là thiết kế phù hợp với nghiệp vụ thực tế của nhà hàng — khách không phải gọi tất cả món ngay từ đầu.

---

## 📌 PHẦN 3 — ĐẶT BUFFET

### 🎙️ Lời dẫn

> *"Hệ thống hỗ trợ đặt buffet với 2 luồng khác nhau và bảo vệ tránh giả mạo từ phía client."*

### Luồng 1 — Kích hoạt gói Buffet

```
Khách chọn gói buffet → POST /orders
  body: { is_buffet: true, buffet_package_id: X }
  
Server:
  1. Validate gói tồn tại
  2. Sinh buffet_session_id = UUID (server-side, không tin client)
  3. Tạo đơn "kích hoạt" — đơn này không có món ăn, chỉ để khai báo gói
```

### Luồng 2 — Gọi thêm món trong Buffet

```
Khách chọn thêm món → POST /orders
  body: { is_buffet: true, details: [món ăn...] }

Server:
  1. Tìm buffet_session_id hiện tại của bàn (query ORDER BY desc LIMIT 1)
  2. Gắn buffet_session_id vào đơn mới
  3. Các đơn cùng buffet_session_id = cùng 1 phiên buffet
```

> 💡 **Bảo mật:** `buffet_session_id` luôn được **sinh server-side** — client không thể tự tạo/giả mạo session buffet.

---

## 📌 PHẦN 4 — REAL-TIME VỚI WEBSOCKET / STOMP

### 🎙️ Lời dẫn

> *"Đây là một trong những tính năng phức tạp nhất trong hệ thống. Chúng em phải giải quyết bài toán: làm sao để 3 vai trò — khách hàng, bếp, thu ngân — tự động nhận thông tin khi có thay đổi mà không cần F5?"*

### Thách thức kỹ thuật

**Vấn đề:** Spring Cloud Gateway không proxy WebSocket Upgrade.  
**Giải pháp:** Frontend kết nối **trực tiếp** tới từng service URL.

```typescript
// lib/socket.ts
// HTTP  → qua API Gateway :3000
// WS    → trực tiếp tới service
const ORDER_SERVICE_URL   = 'http://localhost:3003';
const KITCHEN_SERVICE_URL = 'http://localhost:3004';
const PAYMENT_SERVICE_URL = 'http://localhost:3008';
```

### Bản đồ Events

```
Khách đặt món
  → order-service emit: /topic/order.created
      ← Orders Page nhận → refresh danh sách
      ← Cashier Page nhận → toast "Đơn hàng mới - Bàn X"

Admin confirm đơn
  → order-service emit: /topic/order.status.updated
      ← Kitchen nhận đơn vào queue
      ← Orders Page cập nhật status
      ← Cashier Page cập nhật phiên bàn

Bếp hoàn thành món
  → kitchen-service emit: /topic/kitchen.queue-updated
                           /topic/order.item-status.{tableId}
      ← Kitchen Page highlight món hoàn thành
      ← Orders Page cập nhật chi tiết item

Khách yêu cầu thanh toán
  → payment-service emit: /topic/payment.request
      ← Cashier nhận toast + danh sách chờ thanh toán cập nhật

Thu ngân xử lý xong
  → payment-service emit: /topic/payment.completed
      ← Tất cả refresh → bàn về trạng thái "Trống"
```

### Singleton Pattern

```typescript
// Tránh multiple connections — dùng singleton
export const paymentSocket = new SocketClient(PAYMENT_SERVICE_URL, '/ws/payment');
export const kitchenSocket = new SocketClient(KITCHEN_SERVICE_URL, '/ws/kitchen');
export const orderSocket   = new SocketClient(ORDER_SERVICE_URL,   '/ws/order');
```

- Reconnect delay: **5 giây** tự động
- Heartbeat: **4 giây** in/out
- Cleanup khi unmount component (`useEffect` return disconnect)

---

## 📌 PHẦN 5 — HÀNG ĐỢI BẾP & ĐỒNG BỘ TRẠNG THÁI TỰ ĐỘNG

### 🎙️ Lời dẫn

> *"Sau khi admin xác nhận đơn, bếp phải biết cần nấu gì. Và khi bếp hoàn thành, hệ thống phải tự động cập nhật trạng thái đơn hàng — không cần ai thao tác thêm."*

### Luồng Xác nhận → Bếp

```
Admin bấm "Xác nhận" đơn #123
  → OrderService.confirmOrder(123)
      1. Gọi kitchenClient.notifyNewOrder([detail_ids])  ← Feign HTTP
         ↳ Nếu kitchen DOWN → throw exception → rollback transaction
      2. order.status = "Đang nấu"
      3. Socket emit: order.status.updated → FE cập nhật

Kitchen-service nhận /api/kitchen/notify-new-order
  → Tạo KitchenQueue entry cho từng detail_id
  → Socket emit: kitchen.new-order → Kitchen Page refresh
```

### Đồng bộ tự động khi bếp hoàn thành

```java
// KitchenService.updateQueueItemStatus()
// Sau khi đổi 1 item → kiểm tra TOÀN BỘ order này:
List<String> allStatuses = repo.findAllStatusesForOrder(orderId);

if (allStatuses.allMatch("Hoàn thành")) {
    orderClient.updateOrderStatus(orderId, "Hoàn thành"); // Feign call
    socketService.emitOrderStatusUpdated(tableId, payload);
} else if (anyMatch("Đang chế biến") || anyMatch("Chờ chế biến")) {
    orderClient.updateOrderStatus(orderId, "Đang nấu");
}
```

> 💡 **Nhấn mạnh:** Không có cron job, không có polling — đây là **event-driven**: mỗi lần bếp cập nhật 1 món → hệ thống NGAY LẬP TỨC tính lại trạng thái tổng.

### Giao diện Kitchen Page

- **Tab All / Chờ chế biến / Đang chế biến / Hoàn thành**
- Auto-refresh **5 giây** qua SWR + STOMP real-time override
- Nút "Bắt đầu chế biến" → "Hoàn thành" cho từng món
- Nút "Xóa hoàn thành" — batch DELETE 1 lần

---

## 📌 PHẦN 6 — QR CODE THÔNG MINH & ĐẶT BÀN CHỐNG XUNG ĐỘT

### 🎙️ Lời dẫn

> *"Hai chức năng này xử lý bài toán đồng thời — race condition — với mức độ nghiêm trọng khác nhau."*

### 6A. QR Code Thông minh

**Vấn đề:** QR tĩnh không bảo mật — ai scan cũng vào được, bất kể giờ nào.

**Giải pháp:**

```
Static QR (in lên bàn) → scan → /generate-access
                                      ↓
                           Server tạo table_key (UUID, 2h)
                                      ↓
                           Smart expiry:
                           • Nếu có reservation tiếp theo trong 2.25h
                             → key hết hạn 15 phút TRƯỚC reservation đó
                           • Nếu reservation quá gần (≤ 15 phút)
                             → TỪ CHỐI cấp key mới
                                      ↓
                           Redirect: /index.html?tableId=X&tableKey=UUID
```

**Device Session Binding — chống share QR:**

```sql
-- 1 câu SQL atomic — tránh 2 thiết bị cùng "claim" 1 lúc
UPDATE table_keys
SET    device_session = :newSession
WHERE  id = :keyId
AND    device_session IS NULL   -- chỉ thành công nếu chưa ai bind
```

**Response validate key:**
```json
{
  "valid": true,
  "reason": "ok",              // ok | expired | not_found | taken
  "seconds_remaining": 5400,
  "expires_at": "2026-04-07T20:00:00"
}
```

### 6B. Đặt bàn Chống Xung đột (Race Condition)

**Vấn đề:** 2 người cùng đặt cùng bàn, cùng giờ → chỉ 1 người thắng.

**Giải pháp 3 lớp:**

```
Lớp 1: Transaction SERIALIZABLE
        @Transactional(isolation = SERIALIZABLE)
        createReservation()

Lớp 2: Query kiểm tra overlap trước khi insert
        SELECT COUNT(*) FROM reservations
        WHERE table_id = ? 
        AND start_time < :end AND end_time > :start

Lớp 3: UNIQUE constraint database (last resort)
        UNIQUE INDEX uq_table_slot(table_id, start_time, end_time)
        → DataIntegrityViolationException → 409 Conflict
```

> 💡 **Nhấn mạnh:** 3 lớp bảo vệ — application logic, transaction isolation, và database constraint. Không thể double-booking dù có nhiều request đồng thời.

---

## 📌 PHẦN 7 — BẢO MẬT NÂNG CAO

### 🎙️ Lời dẫn

> *"Hệ thống xử lý dữ liệu khách hàng và giao dịch tài chính, nên bảo mật là ưu tiên hàng đầu. Chúng em triển khai 7 lớp bảo mật."*

### 7.1 — JWT + Role-based Access Control

```
Token chứa: { userId, username, roleId, roleName }

5 Roles và quyền truy cập:
  ADMIN    → toàn bộ trang
  MANAGER  → trừ trang Staff
  CASHIER  → Dashboard, Orders, Tables, Cashier, Reservations
  KITCHEN  → Kitchen, Inventory, Portioning
  STAFF    → (legacy, hạn chế)
  CUSTOMER → Web khách (riêng biệt)
```

**Per-endpoint guard:**
```java
// OrderController.java
String roleName = (String) request.getAttribute("roleName");
boolean isAllowed = "ADMIN".equals(roleName) 
                 || "CASHIER".equals(roleName)
                 || "MANAGER".equals(roleName);
if (!isAllowed) return 403;
```

### 7.2 — JWT Blacklist (Logout thực sự)

**Vấn đề:** JWT stateless — sau logout, token vẫn valid đến khi hết hạn.

**Giải pháp:** Persistent blacklist trong MySQL:

```sql
CREATE TABLE revoked_tokens (
    token      TEXT NOT NULL,
    expires_at DATETIME NOT NULL,
    INDEX idx_revoked_token(token(255))
);
```

```java
// Khi logout:
tokenBlacklistService.revokeToken(token, expiry);

// Mỗi request check:
if (tokenBlacklistService.isRevoked(token)) → 401

// Scheduler dọn dẹp mỗi giờ:
@Scheduled(fixedRate = 3600000)
deleteByExpiresAtBefore(LocalDateTime.now())
```

### 7.3 — Email OTP Verification

```
Đăng ký → gửi OTP 6 chữ số qua email (10 phút)
         → Lưu EmailVerificationOtpLog {
               otp_code, status (PENDING/VERIFIED/INVALID),
               attempt_count (≤ 5), expires_at
           }
         → Chưa verify → KHÔNG cho login
         → Gửi lại OTP: OTP cũ → SUPERSEDED
```

### 7.4 — Các lớp bảo mật khác

| Lớp | Kỹ thuật |
|-----|---------|
| Password | BCrypt 10 rounds — server-side |
| Token Storage | **sessionStorage** (không localStorage) — clear khi đóng tab |
| CORS | allowedOriginPatterns whitelist (không `*`) |
| Inter-service | `X-Service-Token` header cho internal calls |
| Input | Email regex RFC 5322, phone 9–11 chữ số |
| Header inject | Gateway strip `X-Internal-Call`, `X-Service-Token` từ client |

---

## 📌 PHẦN 8 — TỰ ĐỘNG TRỪ KHO NGUYÊN LIỆU

### 🎙️ Lời dẫn

> *"Hệ thống kết nối trực tiếp từ đơn hàng xuống kho nguyên liệu, không cần nhân viên nhập tay."*

### Luồng tự động

```
Admin confirm đơn hàng
  → OrderService.deductInventoryForOrder(order)
  
    1. Lấy tất cả food_id trong đơn
    2. Gọi menuClient.getFoodIngredients([food_ids])
       ← Trả về: { food_id → [{ingredient_id, amount_per_portion}] }
    
    3. Tính tổng: amount × quantity cho từng nguyên liệu
       Ví dụ: Phở bò (2 tô) × 300g xương = 600g
    
    4. Gọi inventoryClient.batchDeduct([{ingredient_id, amount}])
       ← Transaction: tất cả thành công hoặc rollback

    ⚡ Lỗi inventory KHÔNG làm hỏng confirm order
       → chỉ log warning (fault tolerance)
```

### Trừ kho tại Bếp (Portioning)

```
Bếp đổi status → "Hoàn thành"
  → KitchenService.deductIngredientsForDish(item)
  
  Lấy công thức từ menu-service → trừ nguyên liệu thực tế đã nấu
  (Tách biệt với trừ kho lúc confirm — 2 mục đích khác nhau)
```

### Trang Portioning (Fe-Admin)

- Xem/sửa công thức từng món (ingredient name + amount)
- Multi-row editor: thêm/xóa nguyên liệu động
- Hiển thị Low Stock alert khi nguyên liệu < 10 đơn vị

---

## 📌 PHẦN 9 — THU NGÂN & IN HÓA ĐƠN NHIỆT

### 🎙️ Lời dẫn

> *"Trang thu ngân là giao diện trung tâm — nhận thông báo real-time từ 2 socket khác nhau, xử lý thanh toán, và in hóa đơn nhiệt trực tiếp từ browser."*

### Dual-socket Architecture

```typescript
// cashier/page.tsx — 2 useEffect, 2 socket:

// Socket 1: paymentSocket (port 3008)
paymentSocket.subscribe('/topic/payment.request', (data) => {
    toast.info(`Bàn ${data.table_id} yêu cầu thanh toán!`);
    mutate("/payments/waiting");
});

// Socket 2: orderSocket (port 3003)
orderSocket.subscribe('/topic/order.created', (data) => {
    toast.info(`Đơn hàng mới - Bàn ${data.table_id}`);
    mutate("/orders/sessions");
});
orderSocket.subscribe('/topic/order.status.updated', () => {
    mutate("/orders/sessions");
});
```

### Luồng Thanh toán

```
1. Bảng "Chờ thanh toán" hiển thị các yêu cầu từ khách
2. Thu ngân click "Thanh toán" → Dialog xác nhận
3. POST /payments/process/cash {order_id, table_id, table_key}

   Server:
   a. Validate order tồn tại + chưa paid
   b. Tìm TẤT CẢ payment_request chưa paid trong phiên
   c. Mark "paid" toàn bộ
   d. Tạo Payment record
   e. Gọi order-service.completePayment() → mark orders "paid"
   f. Socket emit: payment.completed

4. FE tự động in hóa đơn (window.print())
5. Bàn reset → "Trống"
```

### In hóa đơn nhiệt (không cần phần mềm)

```javascript
// Tự động khi thanh toán thành công:
const printWindow = window.open('', '_blank');
printWindow.document.write(`
  <html> CSS in nhiệt (width 300px, font Courier New) </html>
`);
window.onload = () => { window.print(); window.close(); }
```

Hóa đơn bao gồm: Logo nhà hàng, số bàn, ngày giờ, danh sách món, **TỔNG CỘNG**, QR Wifi.

---

## 📌 PHẦN 10 — DEMO TRỰC TIẾP

### 🗺️ Kịch bản Demo (10 phút)

#### Bước 1 — Khách quét QR và gọi món (2 phút)
- Mở web khách → quét QR bàn 3
- Gọi 2 món → confirm đặt hàng
- **Quan sát:** Orders Page (Admin) tự động cập nhật ← *WebSocket order.created*

#### Bước 2 — Admin xác nhận đơn (1 phút)
- Orders Page → click "Xác nhận phiên bàn 3"
- **Quan sát:** Kitchen Page xuất hiện 2 món mới ← *Feign + kitchen.new-order*
- **Quan sát:** Orders Page: status "Chờ xác nhận" → "Đang nấu"

#### Bước 3 — Bếp chế biến (2 phút)
- Kitchen Page → Bắt đầu chế biến Món 1 → Hoàn thành
- Bắt đầu chế biến Món 2 → Hoàn thành
- **Quan sát:** Sau món cuối: Orders Page status → "Hoàn thành" ← *Auto sync*

#### Bước 4 — Khách thêm đồ uống (1 phút)
- Web khách → gọi thêm 1 ly nước
- **Quan sát:** Orders Page: Phiên bàn 3 tăng tổng món ← *real-time*
- Cashier Page: toast "Đơn hàng mới - Bàn 3" ← *order.created*

#### Bước 5 — Thanh toán (2 phút)
- Web khách → "Yêu cầu thanh toán"
- **Quan sát:** Cashier Page: toast "Bàn 3 yêu cầu thanh toán!" ← *payment.request*
- Thu ngân → click "Thanh toán" → xác nhận
- **Quan sát:** Hóa đơn nhiệt in ra
- **Quan sát:** Tất cả trang refresh, bàn 3 → "Trống"

#### Bước 6 — Đặt bàn trước (tùy chọn, 2 phút)
- Reservations Page → Đặt bàn 3, giờ gần nhất
- Tạo bàn 3 lần 2 cùng giờ → **Quan sát:** lỗi 409 Conflict ← *Race condition safe*
- Check-in reservation → nhận QR mới

---

## 💡 CÂU HỎI THƯỜNG GẶP — GỢI Ý TRẢ LỜI

**Q: Tại sao dùng microservices thay vì monolith?**
> *"Mỗi service có thể scale độc lập — bếp busy thì scale kitchen-service, không ảnh hưởng payment. Ngoài ra dễ maintain và triển khai riêng lẻ từng phần."*

**Q: WebSocket không qua Gateway có an toàn không?**
> *"WebSocket endpoint vẫn yêu cầu JWT token trong Authorization header khi connect — chỉ routing là trực tiếp, không phải bỏ qua authentication."*

**Q: Giải thích race condition trong đặt bàn?**
> *"Nếu 2 request gần như đồng thời, transaction SERIALIZABLE khiến cái sau phải đợi. Sau khi cái trước commit, cái sau query thấy overlap → trả lỗi 409. Unique constraint là lưới an toàn cuối cùng."*

**Q: Buffet session ID tại sao phải server-side?**
> *"Nếu client gửi buffet_session_id, họ có thể fake session của bàn khác để gộp hóa đơn. Server tự sinh UUID từ phiên hiện tại của bàn — client không thể can thiệp."*

**Q: Token blacklist có làm chậm hệ thống không?**
> *"Có indexed column trên token (255 ký tự) — lookup O(log n). Scheduler xóa token hết hạn mỗi giờ nên table luôn nhỏ. Trong thực tế với vài trăm user đồng thời, latency không đáng kể."*

---

## 📊 TỔNG KẾT SLIDE CUỐI

| Hạng mục | Con số |
|---|---|
| Microservices | 9 services |
| Endpoints API | ~60+ endpoints |
| WebSocket Topics | 11 topics |
| Database | 6 schemas riêng biệt |
| Bug fixes documented | 32 bugs |
| Migration files | 10 files |
| Frontend pages (Admin) | 11 trang |
| Advanced features | 25+ tính năng |

> *"Hệ thống được thiết kế để phục vụ thực tế — từ QR code khách quét đến bếp nhận món, thu ngân in hóa đơn — tất cả kết nối real-time, tự động, và bảo mật đa lớp."*

---

*Chuẩn bị bởi nhóm phát triển — April 2026*
