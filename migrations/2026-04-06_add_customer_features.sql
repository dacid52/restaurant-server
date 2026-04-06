-- Migration: Thêm các trường xác thực email cho customer và liên kết reservation
-- Chạy THEO THỨ TỰ: migration này sau 2026-04-06_add_customer_role.sql

-- ============================================================
-- 1. userdb: Thêm các trường email verification vào bảng users
-- ============================================================
USE userdb;

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS email_verified        TINYINT(1)   NOT NULL DEFAULT 0        COMMENT '1 = đã xác thực email',
    ADD COLUMN IF NOT EXISTS email_verification_token  VARCHAR(255) NULL DEFAULT NULL     COMMENT 'UUID token gửi qua email',
    ADD COLUMN IF NOT EXISTS email_verification_expires_at DATETIME NULL DEFAULT NULL     COMMENT 'Thời hạn token (24 giờ)';

-- Index để lookup token nhanh
ALTER TABLE users
    ADD INDEX IF NOT EXISTS idx_email_verification_token (email_verification_token);

-- Đảm bảo cột email là UNIQUE (customer đăng ký bằng email phải unique)
-- Nếu đã có duplicate email trong DB cần dọn trước khi chạy lệnh này
-- ALTER TABLE users ADD UNIQUE INDEX IF NOT EXISTS uq_users_email (email);

-- Đảm bảo cột phone_number là UNIQUE cho customer
-- ALTER TABLE users ADD UNIQUE INDEX IF NOT EXISTS uq_users_phone (phone_number);

-- ============================================================
-- 2. tabledb: Thêm customer_id vào table_reservations
-- ============================================================
USE tabledb;

ALTER TABLE table_reservations
    ADD COLUMN IF NOT EXISTS customer_id INT NULL DEFAULT NULL
        COMMENT 'ID từ userdb.users. NULL = staff tạo cho khách vãng lai (walk-in qua điện thoại)';

ALTER TABLE table_reservations
    ADD INDEX IF NOT EXISTS idx_resv_customer (customer_id);
