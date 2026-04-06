-- Reservation table for time-based booking (MySQL)
CREATE TABLE IF NOT EXISTS `table_reservations` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `table_id` INT NOT NULL,
  `customer_name` VARCHAR(100) NOT NULL,
  `customer_phone` VARCHAR(30) NOT NULL,
  `party_size` INT NOT NULL,
  `start_time` DATETIME NOT NULL,
  `end_time` DATETIME NOT NULL,
  `status` VARCHAR(30) NOT NULL DEFAULT 'pending',
  `is_buffet` TINYINT(1) NOT NULL DEFAULT 0,
  `buffet_package_id` INT DEFAULT NULL,
  `buffet_package_name` VARCHAR(255) DEFAULT NULL,
  `notes` VARCHAR(500) DEFAULT NULL,
  `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_resv_table_time` (`table_id`, `start_time`, `end_time`),
  KEY `idx_resv_status` (`status`)
);

-- Optional link from orders to reservations
ALTER TABLE `orders`
  ADD COLUMN `reservation_id` INT NULL AFTER `table_key`,
  ADD KEY `idx_orders_reservation` (`reservation_id`);

-- Foreign key can be added if desired:
-- ALTER TABLE `orders`
--   ADD CONSTRAINT `fk_orders_reservation`
--   FOREIGN KEY (`reservation_id`) REFERENCES `table_reservations`(`id`);
