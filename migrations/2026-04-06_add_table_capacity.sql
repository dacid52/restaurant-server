-- Thêm cột capacity (sức chứa) vào bảng tables
USE tabledb;

ALTER TABLE tables
  ADD COLUMN IF NOT EXISTS capacity INT DEFAULT NULL COMMENT 'Sức chứa tối đa của bàn';
