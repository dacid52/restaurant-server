-- phpMyAdmin SQL Dump
-- version 5.2.2
-- https://www.phpmyadmin.net/
--
-- Host: localhost:3306
-- Generation Time: Apr 08, 2026 at 04:11 PM
-- Server version: 8.4.3
-- PHP Version: 8.3.30

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `inventorydb`
--
CREATE DATABASE IF NOT EXISTS `inventorydb` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `inventorydb`;

-- --------------------------------------------------------

--
-- Table structure for table `ingredients`
--

CREATE TABLE `ingredients` (
  `id` int NOT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `unit` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `quantity` decimal(10,2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `ingredients`
--
ALTER TABLE `ingredients`
  ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `ingredients`
--
ALTER TABLE `ingredients`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;
--
-- Database: `kitchendb`
--
CREATE DATABASE IF NOT EXISTS `kitchendb` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `kitchendb`;

-- --------------------------------------------------------

--
-- Table structure for table `kitchen_queue`
--

CREATE TABLE `kitchen_queue` (
  `id` int NOT NULL,
  `order_detail_id` int DEFAULT NULL,
  `status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `kitchen_queue`
--
ALTER TABLE `kitchen_queue`
  ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `kitchen_queue`
--
ALTER TABLE `kitchen_queue`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;
--
-- Database: `menudb`
--
CREATE DATABASE IF NOT EXISTS `menudb` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `menudb`;

-- --------------------------------------------------------

--
-- Table structure for table `buffet_packages`
--

CREATE TABLE `buffet_packages` (
  `id` int NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `price` decimal(18,2) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `categories`
--

CREATE TABLE `categories` (
  `id` int NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_drink` tinyint(1) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `foods`
--

CREATE TABLE `foods` (
  `id` int NOT NULL,
  `name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `image_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `category_id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `food_ingredients`
--

CREATE TABLE `food_ingredients` (
  `id` int NOT NULL,
  `food_id` int DEFAULT NULL,
  `ingredient_id` int DEFAULT NULL,
  `amount` decimal(10,2) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `buffet_packages`
--
ALTER TABLE `buffet_packages`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `categories`
--
ALTER TABLE `categories`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `foods`
--
ALTER TABLE `foods`
  ADD PRIMARY KEY (`id`),
  ADD KEY `category_id` (`category_id`);

--
-- Indexes for table `food_ingredients`
--
ALTER TABLE `food_ingredients`
  ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `buffet_packages`
--
ALTER TABLE `buffet_packages`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `categories`
--
ALTER TABLE `categories`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `foods`
--
ALTER TABLE `foods`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `food_ingredients`
--
ALTER TABLE `food_ingredients`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `foods`
--
ALTER TABLE `foods`
  ADD CONSTRAINT `foods_ibfk_1` FOREIGN KEY (`category_id`) REFERENCES `categories` (`id`);
--
-- Database: `orderdb`
--
CREATE DATABASE IF NOT EXISTS `orderdb` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `orderdb`;

-- --------------------------------------------------------

--
-- Table structure for table `orders`
--

CREATE TABLE `orders` (
  `id` int NOT NULL,
  `table_id` int DEFAULT NULL,
  `user_id` int DEFAULT NULL,
  `order_time` datetime DEFAULT NULL,
  `status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `total` decimal(18,2) DEFAULT NULL,
  `table_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_buffet` tinyint(1) DEFAULT NULL,
  `buffet_session_id` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `buffet_package_id` int DEFAULT NULL,
  `buffet_package_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `payment_status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `order_details`
--

CREATE TABLE `order_details` (
  `id` int NOT NULL,
  `order_id` int DEFAULT NULL,
  `food_id` int DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `price` decimal(10,2) DEFAULT NULL,
  `custom_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_free_drink` tinyint(1) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `orders`
--
ALTER TABLE `orders`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `order_details`
--
ALTER TABLE `order_details`
  ADD PRIMARY KEY (`id`),
  ADD KEY `order_id` (`order_id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `orders`
--
ALTER TABLE `orders`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `order_details`
--
ALTER TABLE `order_details`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `order_details`
--
ALTER TABLE `order_details`
  ADD CONSTRAINT `order_details_ibfk_1` FOREIGN KEY (`order_id`) REFERENCES `orders` (`id`);
--
-- Database: `paymentdb`
--
CREATE DATABASE IF NOT EXISTS `paymentdb` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `paymentdb`;

-- --------------------------------------------------------

--
-- Table structure for table `payments`
--

CREATE TABLE `payments` (
  `id` int NOT NULL,
  `order_id` int NOT NULL,
  `amount` decimal(18,2) NOT NULL,
  `method` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `cash_received` decimal(18,2) DEFAULT NULL,
  `cash_change` decimal(18,2) DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `paid_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `payment_requests`
--

CREATE TABLE `payment_requests` (
  `id` int NOT NULL,
  `order_id` int NOT NULL,
  `table_id` int NOT NULL,
  `total` decimal(18,2) NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_time` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `payments`
--
ALTER TABLE `payments`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `payment_requests`
--
ALTER TABLE `payment_requests`
  ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `payments`
--
ALTER TABLE `payments`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `payment_requests`
--
ALTER TABLE `payment_requests`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;
--
-- Database: `tabledb`
--
CREATE DATABASE IF NOT EXISTS `tabledb` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `tabledb`;

-- --------------------------------------------------------

--
-- Table structure for table `restaurant_layouts`
--

CREATE TABLE `restaurant_layouts` (
  `id` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `layout_data` longtext COLLATE utf8mb4_unicode_ci
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `tables`
--

CREATE TABLE `tables` (
  `id` int NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `is_buffet` tinyint(1) DEFAULT NULL,
  `capacity` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `table_keys`
--

CREATE TABLE `table_keys` (
  `id` int NOT NULL,
  `table_id` int DEFAULT NULL,
  `key_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `expires_at` datetime DEFAULT NULL,
  `is_valid` tinyint(1) DEFAULT NULL,
  `device_session` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `table_reservations`
--

CREATE TABLE `table_reservations` (
  `id` int NOT NULL,
  `buffet_package_id` int DEFAULT NULL,
  `buffet_package_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `customer_id` int DEFAULT NULL,
  `customer_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `customer_phone` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `end_time` datetime(6) NOT NULL,
  `is_buffet` bit(1) NOT NULL,
  `notes` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `party_size` int NOT NULL,
  `start_time` datetime(6) NOT NULL,
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL,
  `table_id` int NOT NULL,
  `updated_at` datetime(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `restaurant_layouts`
--
ALTER TABLE `restaurant_layouts`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `tables`
--
ALTER TABLE `tables`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `table_keys`
--
ALTER TABLE `table_keys`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `table_reservations`
--
ALTER TABLE `table_reservations`
  ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `tables`
--
ALTER TABLE `tables`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `table_keys`
--
ALTER TABLE `table_keys`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `table_reservations`
--
ALTER TABLE `table_reservations`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;
--
-- Database: `userdb`
--
CREATE DATABASE IF NOT EXISTS `userdb` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `userdb`;

-- --------------------------------------------------------

--
-- Table structure for table `email_verification_otp_logs`
--

CREATE TABLE `email_verification_otp_logs` (
  `id` bigint NOT NULL,
  `attempt_count` int NOT NULL,
  `created_at` datetime(6) DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `last_attempt_at` datetime(6) DEFAULT NULL,
  `otp_code` varchar(6) COLLATE utf8mb4_unicode_ci NOT NULL,
  `sent_at` datetime(6) NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `is_used` bit(1) NOT NULL,
  `used_at` datetime(6) DEFAULT NULL,
  `is_valid` bit(1) NOT NULL,
  `user_id` int NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `logs`
--

CREATE TABLE `logs` (
  `id` int NOT NULL,
  `user_id` int DEFAULT NULL,
  `action` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `revoked_tokens`
--

CREATE TABLE `revoked_tokens` (
  `id` bigint NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `token` text COLLATE utf8mb4_unicode_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `roles`
--

CREATE TABLE `roles` (
  `id` int NOT NULL,
  `name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- --------------------------------------------------------

--
-- Table structure for table `users`
--

CREATE TABLE `users` (
  `id` int NOT NULL,
  `username` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `password` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `role_id` int DEFAULT NULL,
  `full_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `phone_number` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `age` int DEFAULT NULL,
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `address` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `created_at` datetime DEFAULT NULL,
  `updated_at` datetime DEFAULT NULL,
  `email_verification_expires_at` datetime(6) DEFAULT NULL,
  `email_verification_token` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email_verified` bit(1) NOT NULL,
  `email_verification_otp` varchar(6) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email_verification_otp_expires_at` datetime(6) DEFAULT NULL,
  `email_verification_otp_attempt_count` int NOT NULL,
  `email_verification_otp_email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email_verification_otp_last_attempt_at` datetime(6) DEFAULT NULL,
  `email_verification_otp_sent_at` datetime(6) DEFAULT NULL,
  `email_verification_otp_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `email_verification_otp_used` bit(1) NOT NULL,
  `email_verification_otp_used_at` datetime(6) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `email_verification_otp_logs`
--
ALTER TABLE `email_verification_otp_logs`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_otp_logs_email` (`email`),
  ADD KEY `idx_otp_logs_status` (`status`),
  ADD KEY `idx_otp_logs_expires_at` (`expires_at`),
  ADD KEY `FKcif4rpv9onary0437ib849va0` (`user_id`);

--
-- Indexes for table `logs`
--
ALTER TABLE `logs`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `revoked_tokens`
--
ALTER TABLE `revoked_tokens`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `roles`
--
ALTER TABLE `roles`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `users`
--
ALTER TABLE `users`
  ADD PRIMARY KEY (`id`),
  ADD KEY `role_id` (`role_id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `email_verification_otp_logs`
--
ALTER TABLE `email_verification_otp_logs`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `logs`
--
ALTER TABLE `logs`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `revoked_tokens`
--
ALTER TABLE `revoked_tokens`
  MODIFY `id` bigint NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `roles`
--
ALTER TABLE `roles`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `users`
--
ALTER TABLE `users`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- Constraints for dumped tables
--

--
-- Constraints for table `email_verification_otp_logs`
--
ALTER TABLE `email_verification_otp_logs`
  ADD CONSTRAINT `FKcif4rpv9onary0437ib849va0` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

--
-- Constraints for table `users`
--
ALTER TABLE `users`
  ADD CONSTRAINT `users_ibfk_1` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`);
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
