# Kiến Trúc Hệ Thống — Restaurant Management System
> **Cập nhật lần cuối:** 06/04/2026

## Mục lục
1. [Tổng quan kiến trúc](#1-tổng-quan-kiến-trúc)
2. [Port & Service Map](#2-port--service-map)
3. [Database Design](#3-database-design)
4. [API Endpoints](#4-api-endpoints)
5. [Frontend — Customer Web (static HTML)](#5-frontend--customer-web-static-html)
6. [Frontend — Fe-Admin (Next.js)](#6-frontend--fe-admin-nextjs)
7. [Phân quyền (Role & JWT)](#7-phân-quyền-role--jwt)
8. [Logic nghiệp vụ quan trọng](#8-logic-nghiệp-vụ-quan-trọng)
9. [Known Issues & Bugs](#9-known-issues--bugs)
10. [Migrations](#10-migrations)

---

## 1. Tổng quan kiến trúc

```
         Browser (Staff / Admin)
                │
        Fe-Admin (Next.js :3010)
                │
                ▼
  ┌──── API Gateway :3000 ─────────────────────────────┐
  │  /api/users/**    → user-service :3005              │
  │  /api/menu/**     → menu-service :3002              │
  │  /api/orders/**   → order-service :3003             │
  │  /api/tables/**   → table-service :3011             │
  │  /api/kitchen/**  → kitchen-service :3004           │
  │  /api/inventory/**→ inventory-service :3006         │
  │  /api/payments/** → payment-service :3008           │
  │  /api/images/**   → image-service :3007             │
  └────────────────────────────────────────────────────┘

  Browser (Customer)             Browser (Dine-in, scan QR)
        │                                    │
        └──── table-service :3011 ───────────┘
              (API + serve static HTML)
```

### Quyết định kiến trúc

| Quyết định | Lựa chọn | Lý do |
|---|---|---|
| Customer auth | Mở rộng `user-service` (không tạo service mới) | Same bounded context — Identity domain, tránh duplicate JWT logic |
| Customer frontend | Plain HTML/JS nhúng trong `table-service/src/main/resources/static/` | Không cần build step, serve trực tiếp qua Spring Boot, không tạo thêm service |
| Port customer web | `:3011` (cùng table-service) | `customer-web` service (port 3001) đã bị xóa — tiết kiệm 1 service |
| WebSocket | Không dùng cho customer web | SWR polling 30s đủ cho reservation status |

---

## 2. Port & Service Map

| Service | Port | Ghi chú |
|---------|------|---------|
| api-gateway | **3000** | Entry point cho tất cả API |
| menu-service | **3002** | |
| order-service | **3003** | |
| kitchen-service | **3004** | |
| user-service | **3005** | Auth + user management |
| inventory-service | **3006** | |
| image-service | **3007** | Upload/serve ảnh |
| payment-service | **3008** | WebSocket `/ws/payment/**` |
| Fe-Admin | **3010** | Next.js, chạy riêng (`npm run dev`) |
| table-service | **3011** | API + serve customer web (HTML tĩnh) |

> **Không còn port 3001** — `customer-web` service đã bị xóa. Customer web được serve tại `:3011`.

---

## 3. Database Design

### Schema `userdb`

**Bảng `users`**

| Column | Type | Ghi chú |
|--------|------|---------|
| id | INT PK AUTO | |
| username | VARCHAR(50) UNIQUE | Auto-generate cho customer: `customer_<phone>` hoặc `customer_<email>` |
| password | VARCHAR(100) | BCrypt hashed |
| role_id | INT FK→roles.id | |
| full_name | VARCHAR(100) | |
| phone_number | VARCHAR(20) | Unique khi đăng ký bằng SĐT |
| email | VARCHAR(100) | Unique khi đăng ký bằng email |
| email_verified | TINYINT(1) | 0 = chưa xác thực, 1 = đã xác thực |
| email_verification_token | VARCHAR(255) | UUID token gửi trong email |
| email_verification_expires_at | DATETIME | Token hết hạn sau 24 giờ |
| created_at, updated_at | DATETIME | |

**Bảng `roles`**

| ID | Name |
|----|------|
| 1 | ADMIN |
| 2 | MANAGER |
| 3 | CASHIER |
| 4 | WAITER |
| 5 | CUSTOMER |

### Schema `tabledb`

**Bảng `tables`**

| Column | Type | Ghi chú |
|--------|------|---------|
| id | INT PK AUTO | |
| name | VARCHAR(50) | Tên bàn |
| status | VARCHAR(50) | `Trống` / `Đang sử dụng` / `Đã đặt` |
| is_buffet | TINYINT(1) | Có phục vụ buffet không |

**Bảng `table_reservations`**

| Column | Type | Ghi chú |
|--------|------|---------|
| id | INT PK AUTO | |
| table_id | INT NOT NULL | Bàn được đặt |
| customer_name | VARCHAR(100) NOT NULL | |
| customer_phone | VARCHAR(30) NOT NULL | |
| party_size | INT NOT NULL | Số người |
| start_time | DATETIME NOT NULL | |
| end_time | DATETIME NOT NULL | |
| status | VARCHAR(30) | `pending` / `confirmed` / `cancelled` / `completed` / `no_show` |
| is_buffet | TINYINT(1) DEFAULT 0 | |
| buffet_package_id | INT NULL | |
| buffet_package_name | VARCHAR(255) NULL | |
| notes | VARCHAR(500) NULL | |
| customer_id | INT NULL | Soft-link → `userdb.users.id`. NULL nếu staff đặt cho khách vãng lai |
| created_at, updated_at | DATETIME | |

> Index: `idx_resv_table_time(table_id, start_time, end_time)`, `idx_resv_customer(customer_id)`

**Bảng `table_keys`**

| Column | Type | Ghi chú |
|--------|------|---------|
| id | INT PK AUTO | |
| table_id | INT NOT NULL | |
| key_value | VARCHAR(100) NOT NULL | UUID random |
| created_at | DATETIME NOT NULL | |
| expires_at | DATETIME NOT NULL | Thời gian hết hạn tự động |
| is_valid | TINYINT NOT NULL DEFAULT 1 | Bị set 0 khi invalidate |
| device_session | VARCHAR(100) NULL | Device tracking (first device to validate claims the key) |

---

## 4. API Endpoints

### user-service (:3005)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| POST | `/api/users/login` | ❌ Public | Đăng nhập (email / SĐT / username) |
| POST | `/api/users/register` | ❌ Public | Đăng ký tài khoản customer |
| GET | `/api/users/verify-email?token=` | ❌ Public | Xác thực email từ link trong mail |
| GET | `/api/users/me` | ✅ JWT | Lấy thông tin user hiện tại |
| GET | `/api/users` | ✅ ADMIN | Danh sách tất cả user |
| POST | `/api/users` | ✅ ADMIN | Tạo user mới (staff) |
| PUT | `/api/users/{id}` | ✅ JWT | Cập nhật user |
| DELETE | `/api/users/{id}` | ✅ ADMIN | Xóa user |
| GET | `/api/roles` | ✅ JWT | Danh sách roles |

**Register flow:**
- Identifier dạng email → gửi email xác thực, tài khoản chỉ login được sau khi xác thực
- Identifier dạng SĐT → email_verified = 1 ngay, tài khoản active luôn

### table-service (:3011)

| Method | Path | Auth | Mô tả |
|--------|------|------|-------|
| GET | `/api/tables` | ❌ Public | Danh sách bàn |
| GET | `/api/tables/{id}` | ❌ Public | Chi tiết bàn |
| POST | `/api/tables` | ✅ ADMIN/MANAGER | Tạo bàn |
| PUT | `/api/tables/{id}` | ✅ ADMIN/MANAGER | Cập nhật bàn |
| DELETE | `/api/tables/{id}` | ✅ ADMIN/MANAGER | Xóa bàn |
| POST | `/api/tables/{id}/reservations` | ✅ JWT | Đặt bàn (customer_id tự fill từ JWT nếu role=CUSTOMER) |
| GET | `/api/tables/reservations/my` | ✅ JWT (CUSTOMER) | Lịch đặt bàn của tôi |
| GET | `/api/tables/{id}/reservations` | ✅ STAFF | Tất cả đặt bàn của 1 bàn |
| PUT | `/api/tables/reservations/{id}/status` | ✅ STAFF | Đổi trạng thái đặt bàn |
| GET | `/api/tables/{id}/upcoming-reservation` | ✅ STAFF | Đặt bàn tiếp theo (dùng cảnh báo QR) |
| GET | `/api/tables/{id}/active-key` | ✅ STAFF | Key đang hoạt động + seconds remaining |
| POST | `/api/tables/{id}/qr/dynamic` | ✅ STAFF | Tạo QR key động (smart expiry) |
| POST | `/api/tables/{id}/qr/static` | ✅ STAFF | Tạo QR key tĩnh |
| POST | `/api/tables/{id}/keys/invalidate` | ✅ STAFF | Vô hiệu hóa tất cả key, reset status bàn |
| GET | `/api/tables/{id}/validate-key` | ❌ Public | Validate key (từ trang đặt món của khách) |

---

## 5. Frontend — Customer Web (static HTML)

**Vị trí:** `table-service/src/main/resources/static/`
**Truy cập:** `http://localhost:3011/<page>/`
**Serve bởi:** Spring Boot `SpaResourceConfig` — route `/**` → tìm file → `{path}/index.html` → 404

### Cấu trúc file

```
static/
├── index.html                  ← Trang đặt món tại bàn (scan QR — có sẵn trước)
├── payment-return.html         ← Trang sau thanh toán
├── js/
│   ├── config.js               ← API_BASE = 'http://localhost:3000'
│   ├── auth.js                 ← saveAuth, getToken, apiFetch, requireAuth, logout
│   └── navbar.js               ← renderNavbar, escHtml
├── css/
├── images/
├── login/index.html            ← Đăng nhập
├── register/index.html         ← Đăng ký tài khoản
├── verify-email/index.html     ← Xác thực email từ link
├── booking/index.html          ← Đặt bàn (requireAuth)
├── my-reservations/index.html  ← Lịch sử đặt bàn (requireAuth)
└── menu/index.html             ← Xem menu (public)
```

### Trang và API call

| Trang | Auth | API gọi | Ghi chú |
|-------|------|---------|---------|
| login | ❌ | POST `/api/users/login` | Redirect về `?redirect=` nếu có |
| register | ❌ | POST `/api/users/register` | Email → check email; SĐT → link login |
| verify-email | ❌ | GET `/api/users/verify-email?token=` | Đọc `?token=` từ URL |
| booking | ✅ | GET `/api/tables`, POST `/api/tables/{id}/reservations` | requireAuth() redirect login |
| my-reservations | ✅ | GET `/api/tables/reservations/my` | Read-only, sắp xếp DESC |
| menu | ❌ | GET `/api/menu/items` | Public, lọc available != false |

### Auth state (`localStorage`)

```js
customer_token  // JWT string
customer_user   // JSON: { id, fullName, email, phoneNumber, role }
```

---

## 6. Frontend — Fe-Admin (Next.js)

**Vị trí:** `Fe-Admin/`
**Port dev:** `3010`
**Base URL:** `http://localhost:3000/api`

### Trang bàn (`/tables`) — tính năng mới

#### Countdown phiên đang dùng
- State `tick` tăng 1 mỗi giây (setInterval 1000ms)
- Với bàn "Đang sử dụng" → gọi `GET /api/tables/{id}/active-key` khi load và mỗi 20s
- Hiển thị: `⏱️ {h}g {mm}p {ss}s còn lại trong phiên` (nền đỏ nhạt)

#### Lịch đặt bàn sắp tới
- Gọi `GET /api/tables/{id}/upcoming-reservation` cho mỗi bàn khi load và mỗi 20s
- Hiển thị: `📅 Hôm nay/Ngày mai HH:mm · Tên khách · N khách` (nền xanh nhạt)

#### QR Dialog — cảnh báo key sắp hết
- Nếu server trả về `response.data.warning`:
  - Hộp cảnh báo màu vàng `⚠️ {warning}`
  - Giờ hết hạn chính xác `Key hết hạn lúc HH:MM`
- Đóng dialog → tự reset warning + expiresAt

---

## 7. Phân quyền (Role & JWT)

### JWT Claims

```json
{
  "id": 42,
  "username": "customer_0912345678",
  "role_id": 5,
  "iat": 1744137600,
  "exp": 1744224000
}
```

**Secret:** `restaurant_secret_key_12345678900` (dùng chung tất cả service)
**Expiration:** 24 giờ (86400000ms)

### Bảng phân quyền

| Endpoint | Public | CUSTOMER(5) | CASHIER(3)/WAITER(4) | MANAGER(2)/ADMIN(1) |
|----------|--------|-------------|----------------------|---------------------|
| GET `/api/tables` | ✅ | ✅ | ✅ | ✅ |
| POST `/api/tables/{id}/reservations` | ❌ | ✅ | ✅ | ✅ |
| GET `/api/tables/reservations/my` | ❌ | ✅ | ❌ | ❌ |
| POST `/api/tables/{id}/qr/dynamic` | ❌ | ❌ | ✅ | ✅ |
| GET `/api/users` | ❌ | ❌ | ❌ | ✅ ADMIN only |
| GET `/api/menu/items` | ✅ | ✅ | ✅ | ✅ |

> CUSTOMER (role_id=5) bị chặn tại table-service với tất cả endpoint admin. Chỉ được phép: đặt bàn + xem lịch của mình.

---

## 8. Logic nghiệp vụ quan trọng

### Smart QR Key Expiry (tích hợp reservation)

Khi staff gọi `POST /api/tables/{id}/qr/dynamic`:

```
1. Tìm đặt bàn tiếp theo của bàn đó (status: pending/confirmed)

2. Không có đặt bàn nào trong 2 giờ tới:
   → expiresAt = now + 2 giờ (bình thường)

3. Có đặt bàn, còn ≤ 15 phút:
   → HTTP 400 "Bàn đã được đặt lúc HH:MM, không thể tạo key"

4. Có đặt bàn, còn 15 phút → 2 giờ:
   → expiresAt = reservationStart - 15 phút
   → response.warning = chuỗi cảnh báo (FE hiển thị)
   → response.upcoming_reservation_start = thời gian đặt bàn
```

**Buffer:** 15 phút (hardcoded trong `TableService.java`)

### Kiểm tra trùng lịch đặt bàn

```sql
SELECT COUNT(*) FROM table_reservations
WHERE table_id = ?
  AND status IN ('pending', 'confirmed')
  AND NOT (end_time <= :start OR start_time >= :end)
```

Nếu count > 0 → lỗi "Bàn đã được đặt trong khung giờ này".

### Customer Registration Flow

```
identifier chứa "@"  → loại email
  → Tạo user với email_verified = 0
  → Gửi email xác thực (token UUID, hết hạn 24h qua SMTP)
  → Nếu SMTP chưa config → in link ra console (DEV mode)
  → Tài khoản chỉ login được sau khi email_verified = 1

identifier chỉ có số → loại SĐT
  → Tạo user với email_verified = 1 ngay
  → Tài khoản active ngay, không cần xác thực

Link xác thực email:
  http://localhost:3000/api/users/verify-email?token={UUID}
  Sau xác thực redirect tới: http://localhost:3011/login?verified=true
```

### Invalidate Session

`POST /api/tables/{id}/keys/invalidate`:
- Set `is_valid = 0` cho tất cả key của bàn đó
- Reset `table.status = "Trống"`

> ⚠️ **Bug hiện tại:** Cũng reset `is_buffet = false` — sai logic. `is_buffet` là thuộc tính vĩnh viễn của bàn, không phải trạng thái session.

---

## 9. Known Issues & Bugs

### 🔴 Cần sửa

| # | Vấn đề | Vị trí | Mức độ |
|---|--------|--------|--------|
| 1 | Invalidate key reset cả `is_buffet = false` — `is_buffet` là thuộc tính bàn không phải session | `TableService.java` | HIGH |
| 2 | Email xác thực redirect về `http://localhost:3001/login` — port sai, customer web ở `:3011` | `EmailService.java` / config `app.frontend-url` | HIGH |
| 3 | Booking page không gọi availability check trước khi POST reservation — chỉ xử lý lỗi từ server | `booking/index.html` | MEDIUM |
| 4 | `customer_id` trong reservation không validate user có tồn tại trong userdb (cross-service soft link) | `TableController.java` | MEDIUM |

### 🟡 Cần chú ý

| # | Vấn đề | Vị trí |
|---|--------|--------|
| 5 | Key expiry mặc định 2 giờ hardcode, không config được | `TableService.java` |
| 6 | `device_session` tracking có thể chặn thiết bị thứ 2 dùng cùng key | `TableKey.java` |
| 7 | Không validate format SĐT thực tế (9-11 chữ số bất kỳ đều qua) | `AuthService.java` |
| 8 | Không có rate limiting trên register / email verification | `AuthController.java` |
| 9 | Logout chỉ xóa localStorage, không có server-side token invalidation | Thiết kế hệ thống |

---

## 10. Migrations

Chạy theo thứ tự trong MySQL:

| File | Schema | Nội dung |
|------|--------|---------|
| `migrations/2026-04-02_add_table_reservations.sql` | tabledb | Tạo bảng `table_reservations`, indexes |
| `migrations/2026-04-06_add_customer_role.sql` | userdb | INSERT role CUSTOMER (ID=5) |
| `migrations/2026-04-06_add_customer_features.sql` | userdb + tabledb | Thêm `email_verified`, `email_verification_token`, `email_verification_expires_at` vào `users`; thêm `customer_id` vào `table_reservations` |
