-- Migration: thêm cột ban cho bảng users
-- Cho phép admin cấm customer đăng nhập

ALTER TABLE users
    ADD COLUMN is_banned TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN ban_reason VARCHAR(500) DEFAULT NULL,
    ADD COLUMN banned_at DATETIME DEFAULT NULL;
