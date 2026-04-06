-- Migration: Thêm role CUSTOMER vào userdb
-- Chạy trên schema: userdb

USE userdb;

INSERT INTO roles (name)
SELECT 'CUSTOMER'
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'CUSTOMER'
);
