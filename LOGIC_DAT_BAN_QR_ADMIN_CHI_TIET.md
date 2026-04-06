# Logic Dat Ban, Tuong Tac QR Ban, va Trang Don Dat Ban (Admin) - Ban Chi Tiet

## 1. Pham vi tai lieu
Tai lieu nay mo ta implementation hien tai (as-is) dua tren code dang chay cua:
- table-service (backend reservation + QR table key)
- Fe-Admin (trang quan ly don dat ban va trang quan ly ban lien quan den QR)
- api-gateway (duong di request tu FE toi table-service)

Muc tieu:
- Ghi ro logic dat ban hien tai
- Ghi ro logic reservation anh huong the nao den viec cap QR dong cho ban
- Ghi ro logic trang admin /reservations dang hoat dong nhu the nao

Khong mo ta y tuong redesign; chi mo ta dung behavior hien tai trong code.

---

## 2. Kien truc va duong di request

### 2.1 Duong di tong quan
1. Fe-Admin goi API theo base URL gateway (mac dinh `http://localhost:3000/api`).
2. Gateway route `Path=/api/tables/**` sang table-service (`http://localhost:3011`).
3. Table-service xu ly reservation, table status, table key, QR generation.

### 2.2 Serialization response
Table-service set `spring.jackson.property-naming-strategy: SNAKE_CASE`, nen response backend chu yeu tra ve dang snake_case (`start_time`, `customer_name`, `expires_at`...).

Fe-Admin co normalize o mot so noi de dung ca snake_case va camelCase (dac biet o du lieu upcoming reservation).

---

## 3. Du lieu va rang buoc dat ban

## 3.1 Bang `table_reservations`
Cac cot chinh:
- `id`
- `table_id`
- `customer_name`
- `customer_phone`
- `party_size`
- `start_time`, `end_time`
- `status` (default `pending`)
- `is_buffet`, `buffet_package_id`, `buffet_package_name`
- `notes`
- `customer_id` (nullable, them sau migration customer)
- `created_at`, `updated_at`

### 3.2 Rang buoc overlap
Logic backend check overlap theo status dang duoc xem la "chiem cho":
- `pending`
- `confirmed`

Dieu kien overlap:
- Cung `table_id`
- Va KHONG thuoc truong hop tach roi nhau: `NOT (end_time <= start_new OR start_time >= end_new)`

### 3.3 Chong race-condition
Ngoai check overlap bang query, DB co unique index:
- `uq_table_slot (table_id, start_time, end_time)`

Y nghia:
- Neu 2 request dong thoi chen cung slot y chang, DB chan va backend bat `DataIntegrityViolationException` de tra loi loi nghiep vu.

Luu y:
- Unique index tren `(table_id, start_time, end_time)` chi chan case trung exact slot.
- Case overlap khac khung gio (khong exact) van phu thuoc query overlap o service.

---

## 4. Logic dat ban hien tai (backend)

## 4.1 Endpoint lien quan
- `POST /api/tables/{id}/reservations` - tao don dat ban
- `GET /api/tables/{id}/reservations` - danh sach reservation cua 1 ban (co optional `from`, `to`)
- `GET /api/tables/{id}/reservations/availability?start=&end=` - check trong/ban
- `GET /api/tables/reservations/my` - reservation cua user dang login
- `GET /api/tables/reservations` - danh sach reservation toan he thong (co loc status)
- `GET /api/tables/admin/reservations` - alias cho admin page
- `PUT /api/tables/reservations/{reservationId}/status` - cap nhat status
- `GET /api/tables/{id}/upcoming-reservation` - reservation sap toi cua ban (dung cho admin tables + QR warning)

## 4.2 Auth/phan quyen trong filter
Filter custom `JwtAuthenticationFilter` ap dung tren nhieu pattern `/api/tables/**`.

Quy tac chinh:
- `validate-key`, `qr/static`, `qr/dynamic`: bo qua token check.
- `GET` khong co Authorization: cho phep anonymous (ngoai tru `/reservations/my`).
- `/reservations/my`: bat buoc co token.
- Neu co token role CUSTOMER (`role_id == 5`):
  - Duoc phep:
    - `POST /tables/{id}/reservations`
    - `.../reservations/my`
  - Khong duoc phep endpoint tables khac.
- Role 1-4 (admin/manager/cashier/waiter): full access trong module table.

## 4.3 Tao reservation: chi tiet tung buoc
Khi goi `POST /api/tables/{id}/reservations`:

### Buoc A - Controller xu ly payload + customer_id
- Tao mutable copy cua payload.
- Doc `userId`, `roleId` do filter dat vao request attribute.
- Neu request da authenticate:
  - Neu payload khong co `customer_id`: tu gan `customer_id = userId`.
  - Neu role la CUSTOMER va payload co `customer_id`: override ve `userId` (chan mao danh).
- Neu request anonymous:
  - Remove `customer_id` khoi payload (chan inject customer_id gia).

### Buoc B - Service validate business
1. Validate co `tableId`, va ban ton tai.
2. Parse cac field:
   - `customer_name`, `customer_phone`, `party_size`, `start_time`, `end_time`, optional fields.
3. Rule customer info:
   - Neu KHONG co `customer_id`: bat buoc co `customer_name` + `customer_phone`.
   - Neu CO `customer_id`: cho phep thieu name/phone; service tu dien:
     - name mac dinh: `Khach hang #<customer_id>`
     - phone mac dinh: chuoi rong.
4. Validate `party_size > 0`.
5. Parse datetime theo ISO_DATE_TIME.
6. Validate `start_time < end_time`.
7. Check overlap `pending/confirmed`.
8. Tao entity reservation + map optional fields:
   - `status` (neu payload co)
   - `is_buffet`
   - `buffet_package_id`, `buffet_package_name`
   - `notes`
   - `customer_id`
9. Save DB.
10. Neu trung unique index do race-condition: tra loi loi nghiep vu "xung dot dong thoi".

### Buoc C - Response
- Thanh cong: HTTP 201 + reservation object.
- Loi validate/business: GlobalExceptionHandler bat RuntimeException -> HTTP 400 + `{ "message": "..." }`.

## 4.4 Check availability
`GET /api/tables/{id}/reservations/availability?start=&end=`:
- Parse start/end
- Validate start < end
- Chay overlap query
- Tra `{ "available": true/false }`

## 4.5 Lay danh sach reservation

### Theo ban
`GET /api/tables/{id}/reservations`:
- Neu co `from` + `to`: lay reservations theo start_time trong khoang
- Neu khong co: lay tat ca theo `start_time ASC`

### Theo customer dang login
`GET /api/tables/reservations/my`:
- Bat buoc userId tu JWT
- Query theo `customer_id`, sap xep `start_time DESC`

### Cho admin
`GET /api/tables/admin/reservations?status=...`:
- `status` rong hoac `all`: lay toan bo `ORDER BY start_time DESC`
- Co status cu the: loc theo status, `ORDER BY start_time DESC`

## 4.6 Cap nhat status reservation
`PUT /api/tables/reservations/{id}/status`:
- Bat buoc co reservation id
- Bat buoc co status khong rong
- Set truc tiep status moi va save

Luu y quan trong:
- Hien tai KHONG co state machine transition (vi du pending -> confirmed/cancelled thi hop ly; nhung status khac van co the duoc set neu client gui len).
- UI admin dang gioi han thao tac thanh `confirmed` hoac `cancelled`, nen hanh vi thuc te tren UI an toan hon backend.

---

## 5. Tuong tac logic dat ban voi QR code ban

## 5.1 Cau truc key/QR
Backend dung bang `table_keys` de quan ly key truy cap ban:
- `key_value` (UUID)
- `created_at`
- `expires_at`
- `is_valid`
- `device_session` (claim cho 1 thiet bi)

QR dong la URL chua `tableId` + `tableKey`:
- `http://<local-ip>:3011/index.html?tableId=<id>&tableKey=<uuid>`

## 5.2 Endpoint lien quan QR/key
- `GET /api/tables/{id}/qr/static`
- `POST /api/tables/{id}/qr/dynamic`
- `POST /api/tables/{id}/keys/invalidate`
- `GET /api/tables/{id}/active-key`
- `GET /api/tables/{id}/validate-key?tableKey=...&deviceSession=...`

## 5.3 Core interaction: reservation anh huong expiry QR dong
Khi staff tao QR dong (`POST /qr/dynamic`), backend chay logic sau:

### Buoc 1 - Vo hieu hoa key cu
- `invalidateKeysByTableId(id)`
- Dam bao moi ban chi con 1 key active moi nhat.

### Buoc 2 - Tim reservation sap toi
- Query `findNextUpcomingReservation(tableId, now)`
- Dieu kien query hien tai:
  - `status = 'confirmed'`
  - `start_time > now`
  - lay ban ghi som nhat (`ORDER BY start_time ASC LIMIT 1`)

Diem can luu y:
- Comment trong service mo ta "pending/confirmed", nhung query thuc te CHI lay `confirmed`.
- Nghia la don `pending` khong cat ngan key QR dong.

### Buoc 3 - Tinh expiry voi buffer 15 phut
Dat `BUFFER_MINUTES = 15`.

Case A - Khong co upcoming confirmed reservation:
- `expires_at = now + 2h`

Case B - Co upcoming confirmed reservation:
- `minutesUntil = reservationStart - now`
- Neu `minutesUntil <= 15`:
  - Tu choi cap key moi (throw RuntimeException)
  - Message giai thich ban sap toi gio dat, can giu ban cho khach.
- Neu `minutesUntil > 15`:
  - `cappedExpiry = reservationStart - 15m`
  - Neu `cappedExpiry < now + 2h`:
    - Cat ngan key: `expires_at = cappedExpiry`
    - Tra them `warning` + `upcoming_reservation_start`
  - Nguoc lai:
    - Van cap key 2h nhu binh thuong.

Tom lai theo cong thuc:
- Mac dinh: `expiry = now + 2h`
- Neu co confirmed reservation sap toi: `expiry = min(now + 2h, reservationStart - 15m)`
- Neu `reservationStart - now <= 15m`: khong cap key

### Buoc 4 - Luu key + tra QR
- Tao UUID moi
- Save `table_keys`
- Tao QR base64
- Response chua:
  - `table_id`, `key`, `expires_at`, `qr_code`, `url`, `type=dynamic`
  - Neu bi cat ngan do reservation: co them `warning`, `upcoming_reservation_start`

## 5.4 validate-key va rang buoc 1 thiet bi
`GET /validate-key`:
- Tim key hop le (`is_valid = true`, `expires_at > now`, dung tableId/keyValue)
- Neu co `deviceSession`:
  - Neu key chua co deviceSession: claim atomically (`claimDeviceSession`)
  - Neu claim that bai va session khac da claim truoc: tra false
  - Neu key da claim boi session khac: false
- Neu pass validate:
  - Neu status ban khac `Dang su dung`, set thanh `Dang su dung`
  - Tra true

Y nghia nghiep vu:
- 1 key QR dong duoc "khoa" cho 1 session thiet bi sau lan dung dau.
- Han che truong hop nhieu thiet bi cung dung 1 QR.

## 5.5 active-key cho admin countdown
`GET /active-key`:
- Lay key active moi nhat cua ban (`is_valid=true`, `expires_at>now`, order created desc)
- Tra ve:
  - `expires_at`
  - `seconds_remaining`

Fe-Admin dung du lieu nay de hien countdown con lai tren card ban dang su dung.

## 5.6 Invalidate key
`POST /keys/invalidate`:
- Set `is_valid=false` cac key active cua ban
- Dat status ban ve `Trong`

---

## 6. Logic trang Don Dat Ban (Admin) - Fe-Admin/app/reservations/page.tsx

## 6.1 Muc tieu trang
Trang `/reservations` cho thu ngan/nhan vien:
- Theo doi don dat ban
- Loc theo status
- Xac nhan hoac huy don

## 6.2 State va gia tri mac dinh
- `statusFilter`: mac dinh `'pending'` (vao trang la uu tien don cho xu ly)
- `reservations`: danh sach hien thi
- `tables`: danh sach ban de map id -> ten ban
- `loading`
- `updatingId`: id don dang update

## 6.3 Cac API trang goi
Khi fetchData:
1. `GET /tables/admin/reservations` voi params:
   - neu filter `all`: khong gui status
   - nguoc lai: `status=<statusFilter>`
2. `GET /tables` de map `tableId -> tableName`

Su dung `Promise.allSettled`:
- Neu reservation call fail -> throw, toast loi
- Neu tables call fail -> khong chan trang, fallback hien `Ban {id}`

## 6.4 Polling va refresh
- Fetch ngay khi mount
- Auto refresh moi 20 giay
- Refetch khi doi `statusFilter`
- Co nut `Lam moi` goi lai fetchData thu cong

## 6.5 Hien thi dashboard mini
So lieu card tren trang duoc tinh tu mang `reservations` dang hien thi:
- Tong don = `reservations.length`
- Cho xac nhan = count status `pending`
- Da xac nhan = count status `confirmed`

Luu y:
- Vi du lieu da bi loc boi `statusFilter`, cac count nay phan anh tap du lieu hien tai, KHONG phai toan bo he thong neu dang loc theo status cu the.

## 6.6 Render tung don
Moi item hien:
- Ten ban (tu map, fallback `Ban {tableId}`)
- Khach, SDT, so khach
- Khoang thoi gian start-end (format locale vi-VN)
- Ghi chu (neu co)
- Badge status (pending/confirmed/cancelled/completed/no_show)

## 6.7 Logic thao tac status tren UI

### Neu status = pending
Hien 2 nut:
- Xac nhan -> `PUT /tables/reservations/{id}/status` body `{ status: 'confirmed' }`
- Huy -> body `{ status: 'cancelled' }`

### Neu status = confirmed
Hien 1 nut:
- Huy don -> update thanh `cancelled`

### Neu status khac (cancelled/completed/no_show)
- Hien text "Khong co thao tac them"

Sau khi update thanh cong:
1. Toast thanh cong
2. Force `setStatusFilter('all')`
3. Goi lai fetchData

Y nghia:
- Don vua doi status se khong bi "bien mat" do filter cu.

## 6.8 Error handling
- Fetch fail: toast "Khong the tai danh sach don dat ban"
- Update fail: toast "Khong the cap nhat trang thai don dat ban"
- 401 tu axios interceptor: xoa token + redirect `/login`

---

## 7. Logic lien ket giua trang Reservations (admin) va QR ban

Trang `/reservations` khong tao QR truc tiep, nhung tac dong gian tiep den QR thong qua status reservation:

1. Don moi tao tu customer thuong la `pending`.
2. Query upcoming reservation de canh bao/cat expiry QR CHI lay `confirmed`.
3. Vi vay:
   - Don pending: chua anh huong QR dynamic expiry.
   - Sau khi admin xac nhan (confirmed): reservation bat dau anh huong logic cap QR.
4. Neu admin huy don confirmed:
   - Lan cap QR dong sau do co the quay lai expiry 2h (neu khong con confirmed reservation gan).

Noi cach khac, trang admin reservations la diem "bat cong tac" de reservation tham gia vao logic QR thong minh.

---

## 8. Logic trang Tables (admin) lien quan reservation + QR
(Bo sung de thay ro interaction day du)

Trang `/tables` co 2 nhom logic lien quan:

### 8.1 Hien thong tin reservation sap toi tren card ban
- Moi 20s, FE goi cho tung ban:
  - `GET /tables/{id}/upcoming-reservation`
  - Neu ban dang su dung: goi them `GET /tables/{id}/active-key`
- Card ban chi hien block "reservation sap toi" neu su kien trong vong 30 phut toi.
- Trong detail dialog cua ban, van co the xem upcoming reservation du khong nam trong 30 phut.

### 8.2 Tao QR dong va canh bao
Khi staff bam "QR code" trong dialog ban:
- Goi `POST /tables/{id}/qr/dynamic`
- FE hien:
  - Anh QR
  - Gio het han key (`expires_at`)
  - Warning mau vang neu backend tra `warning`

Neu backend tu choi cap key (vi qua sat gio reservation <= 15 phut):
- FE hien toast loi tu message backend.

---

## 9. Cac luong nghiep vu tieu bieu (end-to-end)

## 9.1 Luong A - Customer dat ban moi
1. Customer goi `POST /tables/{id}/reservations` (token customer).
2. Controller ep `customer_id = userId` JWT.
3. Service tao reservation status mac dinh `pending`.
4. Don xuat hien tren trang admin `/reservations` (filter pending).
5. Don CHUA tac dong den QR expiry vi chua confirmed.

## 9.2 Luong B - Admin xac nhan don
1. Admin bam "Xac nhan" tren `/reservations`.
2. FE goi `PUT /tables/reservations/{id}/status` -> `confirmed`.
3. Tu thoi diem nay:
   - `/tables/{id}/upcoming-reservation` co the tra ve don nay (neu la don confirmed som nhat > now).
   - `POST /qr/dynamic` se dung don nay de cat expiry/canh bao/chan cap key neu can.

## 9.3 Luong C - Staff tao QR cho ban sap co reservation
1. Staff vao `/tables`, mo detail ban, bam tao QR.
2. Backend tim upcoming confirmed reservation.
3. Neu con >15p den gio dat:
   - Cap QR, nhung co the cat ngan expiry truoc gio dat 15p.
4. Neu con <=15p:
   - Tu choi cap key, tra loi thong bao giu ban cho khach dat truoc.

## 9.4 Luong D - Admin huy don da confirmed
1. Admin bam huy tren don confirmed.
2. status -> cancelled.
3. Lan tao QR sau do khong con bi reservation nay gioi han (tru khi con don confirmed khac som hon).

---

## 10. Hanh vi can ghi nho (implementation detail quan trong)

1. Upcoming reservation hien tai phu thuoc CHI status `confirmed`.
2. Reservation pending van block overlap khi tao don moi (query overlap tinh pending + confirmed).
3. `/reservations/my` la endpoint GET duy nhat bat buoc JWT ngay ca khi GET khac cho anonymous.
4. `POST /qr/dynamic` dang de path allow khong bat buoc token trong filter; viec bao ve endpoint chu yeu dua vao tang truoc do (gateway/client context).
5. Trang admin reservations default filter `pending`, va sau update status se tu dong chuyen filter `all`.
6. Trang admin tables:
   - Polling 20s de cap nhat upcoming + active key
   - Countdown key cap nhat moi giay tren UI.

---

## 11. Bang doi chieu nhanh endpoint va tac dung

| Endpoint | Muc dich | Anh huong reservation/QR |
|---|---|---|
| `POST /api/tables/{id}/reservations` | Tao don dat ban | Tao pending/confirmed, co check overlap |
| `PUT /api/tables/reservations/{id}/status` | Doi trang thai don | Xac dinh don co tham gia upcoming/QR hay khong |
| `GET /api/tables/{id}/upcoming-reservation` | Lay don sap toi cua ban | Du lieu cho canh bao tren tables page + logic QR |
| `POST /api/tables/{id}/qr/dynamic` | Cap QR dong | Tinh expiry theo upcoming confirmed reservation |
| `GET /api/tables/{id}/active-key` | Lay key active | Hien countdown phien dang dung ban |
| `GET /api/tables/{id}/validate-key` | Xac thuc key + claim session | Neu hop le, chuyen ban sang "Dang su dung" |
| `POST /api/tables/{id}/keys/invalidate` | Thu hoi key | Reset status ban ve "Trong" |

---

## 12. Ket luan
Logic hien tai dang phan tach ro 3 lop:
- Lop reservation tao/duyet/huy don
- Lop QR table key cap phien truy cap theo thoi gian
- Lop admin UI dieu khien status reservation va van hanh ban

Diem giao nhau quan trong nhat la:
- Reservation chi anh huong QR khi da duoc `confirmed`.
- Trang admin `/reservations` la noi quyet dinh de reservation di vao luong gioi han QR.
- Trang admin `/tables` la noi staff nhin canh bao reservation va cap QR theo expiry thong minh.