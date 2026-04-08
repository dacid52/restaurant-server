-- Thêm cột phương thức thanh toán và mã giao dịch MoMo vào payment_requests
ALTER TABLE payment_requests
    ADD COLUMN payment_method VARCHAR(20) NOT NULL DEFAULT 'cash' AFTER status,
    ADD COLUMN momo_trans_id  VARCHAR(100) DEFAULT NULL          AFTER payment_method;
