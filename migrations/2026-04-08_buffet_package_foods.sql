-- ============================================================
-- Migration: Buffet package foods + is_active flag
-- Date: 2026-04-08
-- ============================================================

-- 1. Thêm cột is_active vào bảng buffet_packages (nếu chưa có)
ALTER TABLE buffet_packages
    ADD COLUMN IF NOT EXISTS is_active TINYINT(1) NOT NULL DEFAULT 1;

-- 2. Bảng liên kết gói buffet ↔ danh sách món
CREATE TABLE IF NOT EXISTS buffet_package_foods (
    id                 INT         NOT NULL AUTO_INCREMENT,
    buffet_package_id  INT         NOT NULL,
    food_id            INT         NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_pkg_food (buffet_package_id, food_id),
    INDEX idx_pkg  (buffet_package_id),
    INDEX idx_food (food_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Bảng ghi nhận lịch sử sử dụng buffet (để thống kê, không ảnh hưởng đến luồng đặt món)
--    Mỗi lần khách gọi món trong phiên buffet → ghi 1 dòng vào đây
CREATE TABLE IF NOT EXISTS buffet_usage_log (
    id                 INT         NOT NULL AUTO_INCREMENT,
    buffet_session_id  VARCHAR(255) NOT NULL,
    buffet_package_id  INT         NOT NULL,
    table_id           INT         NOT NULL,
    food_id            INT         NOT NULL,
    food_name          VARCHAR(200),
    quantity           INT         NOT NULL DEFAULT 1,
    logged_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_session (buffet_session_id),
    INDEX idx_table   (table_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
