-- =====================================================================
-- 0. ShedLock — distributed scheduler lock
-- =====================================================================
CREATE TABLE IF NOT EXISTS `shedlock` (
    `name`       VARCHAR(64)  NOT NULL,
    `lock_until` TIMESTAMP(3) NOT NULL,
    `locked_at`  TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `locked_by`  VARCHAR(255) NOT NULL,
    PRIMARY KEY (`name`)
) ENGINE=InnoDB;

-- =====================================================================
-- 1. Users
-- =====================================================================
CREATE TABLE IF NOT EXISTS `users` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `email`        VARCHAR(255) NULL UNIQUE,
    `phone`        VARCHAR(20)  NULL UNIQUE,
    `password`     VARCHAR(255) NOT NULL,
    `nickname`     VARCHAR(100) NOT NULL,
    `verified`     TINYINT(1)   NOT NULL DEFAULT 0,
    `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    INDEX `idx_email` (`email`),
    INDEX `idx_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- 2. Products — master catalog, independent of flash sales
-- =====================================================================
CREATE TABLE IF NOT EXISTS `products` (
    `id`             BIGINT        NOT NULL AUTO_INCREMENT,
    `name`           VARCHAR(255)  NOT NULL,
    `original_price` DECIMAL(12,2) NOT NULL,
    `category`       VARCHAR(100)  NULL,
    `image_url`      VARCHAR(500)  NULL,
    `created_at`     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`     DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    INDEX `idx_category` (`category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- 3. Inventory — global stock per product
-- =====================================================================
CREATE TABLE IF NOT EXISTS `inventory` (
    `id`              BIGINT  NOT NULL AUTO_INCREMENT,
    `product_id`      BIGINT  NOT NULL UNIQUE,
    `total_stock`     INT     NOT NULL DEFAULT 0,
    `available_stock` INT     NOT NULL DEFAULT 0,
    `version`         INT     NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- 4. Flash Sale — campaign / event
-- =====================================================================
CREATE TABLE IF NOT EXISTS `flash_sale` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `name`         VARCHAR(255) NOT NULL,
    `start_time`   DATETIME(3)  NOT NULL,
    `end_time`     DATETIME(3)  NOT NULL,
    `status`       TINYINT      NOT NULL DEFAULT 1 COMMENT '0=INACTIVE, 1=ACTIVE',
    `created_at`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    INDEX `idx_time_range` (`start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- 5. Flash Sale Product — junction: products allocated to a flash sale
--    Stock is managed HERE (per-sale allocation), not in global inventory
-- =====================================================================
CREATE TABLE IF NOT EXISTS `flash_sale_product` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `flash_sale_id`   BIGINT        NOT NULL,
    `product_id`      BIGINT        NOT NULL,
    `sale_price`      DECIMAL(12,2) NOT NULL,
    `sale_stock`      INT           NOT NULL DEFAULT 0 COMMENT 'Allocated stock for this sale',
    `sale_available`  INT           NOT NULL DEFAULT 0 COMMENT 'Remaining stock for this sale',
    `per_user_limit`  INT           NOT NULL DEFAULT 1,
    `enabled`         TINYINT(1)    NOT NULL DEFAULT 1,
    `version`         INT           NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_sale_product` (`flash_sale_id`, `product_id`),
    INDEX `idx_flash_sale_id` (`flash_sale_id`),
    INDEX `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- 6. Orders — linked to flash_sale_product for full traceability
-- =====================================================================
CREATE TABLE IF NOT EXISTS `orders` (
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT,
    `order_no`              VARCHAR(64)   NOT NULL UNIQUE,
    `user_id`               BIGINT        NOT NULL,
    `flash_sale_product_id` BIGINT        NOT NULL,
    `sale_price`            DECIMAL(12,2) NOT NULL,
    `status`                TINYINT       NOT NULL DEFAULT 0 COMMENT '0=CREATED, 1=PAID, 2=CANCELLED',
    `created_at`            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `updated_at`            DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE INDEX `uk_order_no` (`order_no`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_fsp_id` (`flash_sale_product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =====================================================================
-- Seed data: 100 products, 5 flash sale campaigns, allocations
-- Uses CURDATE() so data is always "today" on every restart
-- =====================================================================

-- Products (100 items)
INSERT IGNORE INTO `products` (`id`, `name`, `original_price`, `category`) VALUES
(1, 'Samsung Galaxy S24 Ultra', 1499.99, 'Smartphones'),
(2, 'Apple iPhone 15 Pro Max', 1399.99, 'Smartphones'),
(3, 'Google Pixel 8 Pro', 1099.99, 'Smartphones'),
(4, 'OnePlus 12', 899.99, 'Smartphones'),
(5, 'Xiaomi 14 Ultra', 999.99, 'Smartphones'),
(6, 'Sony Xperia 1 VI', 1199.99, 'Smartphones'),
(7, 'Samsung Galaxy Z Fold5', 1999.99, 'Smartphones'),
(8, 'Apple iPhone 15', 899.99, 'Smartphones'),
(9, 'Motorola Edge 50 Pro', 599.99, 'Smartphones'),
(10, 'Nothing Phone 2', 699.99, 'Smartphones'),
(11, 'MacBook Pro 16 M3', 2799.99, 'Laptops'),
(12, 'Dell XPS 15 9530', 1999.99, 'Laptops'),
(13, 'Lenovo ThinkPad X1 Carbon Gen 11', 1849.99, 'Laptops'),
(14, 'ASUS ROG Zephyrus G14', 1799.99, 'Laptops'),
(15, 'HP Spectre x360 16', 1699.99, 'Laptops'),
(16, 'Razer Blade 16', 2999.99, 'Laptops'),
(17, 'Microsoft Surface Laptop 6', 1499.99, 'Laptops'),
(18, 'Acer Swift Go 14', 949.99, 'Laptops'),
(19, 'LG Gram 17', 1699.99, 'Laptops'),
(20, 'Framework Laptop 16', 1599.99, 'Laptops'),
(21, 'iPad Air 6', 699.99, 'Tablets'),
(22, 'iPad Pro 13 M4', 1499.99, 'Tablets'),
(23, 'Samsung Galaxy Tab S9 Ultra', 1399.99, 'Tablets'),
(24, 'Microsoft Surface Pro 10', 1199.99, 'Tablets'),
(25, 'Lenovo Tab P12 Pro', 749.99, 'Tablets'),
(26, 'OnePlus Pad 2', 579.99, 'Tablets'),
(27, 'Amazon Fire Max 11', 329.99, 'Tablets'),
(28, 'Google Pixel Tablet', 599.99, 'Tablets'),
(29, 'Xiaomi Pad 6S Pro', 549.99, 'Tablets'),
(30, 'Huawei MatePad Pro 13.2', 899.99, 'Tablets'),
(31, 'Sony WH-1000XM5', 399.99, 'Audio'),
(32, 'Apple AirPods Pro 2', 299.99, 'Audio'),
(33, 'Bose QuietComfort Ultra', 499.99, 'Audio'),
(34, 'Sennheiser Momentum 4', 399.99, 'Audio'),
(35, 'JBL Charge 5', 229.99, 'Audio'),
(36, 'Sony WF-1000XM5', 349.99, 'Audio'),
(37, 'Bose SoundLink Max', 449.99, 'Audio'),
(38, 'Marshall Stanmore III', 429.99, 'Audio'),
(39, 'Bang & Olufsen Beoplay H95', 999.99, 'Audio'),
(40, 'Sonos Era 300', 549.99, 'Audio'),
(41, 'Apple Watch Ultra 2', 899.99, 'Wearables'),
(42, 'Samsung Galaxy Watch 6 Classic', 449.99, 'Wearables'),
(43, 'Garmin Fenix 8', 999.99, 'Wearables'),
(44, 'Google Pixel Watch 2', 399.99, 'Wearables'),
(45, 'Fitbit Charge 6', 199.99, 'Wearables'),
(46, 'Apple Watch Series 9', 449.99, 'Wearables'),
(47, 'Garmin Venu 3S', 549.99, 'Wearables'),
(48, 'Withings ScanWatch 2', 399.99, 'Wearables'),
(49, 'Samsung Galaxy Ring', 449.99, 'Wearables'),
(50, 'Oura Ring Gen 3', 349.99, 'Wearables'),
(51, 'Canon EOS R6 II', 2799.99, 'Cameras'),
(52, 'Sony A7 IV', 2799.99, 'Cameras'),
(53, 'Nikon Z8', 3499.99, 'Cameras'),
(54, 'Fujifilm X-T5', 1899.99, 'Cameras'),
(55, 'Canon EOS R8', 1699.99, 'Cameras'),
(56, 'Sony ZV-E1', 2399.99, 'Cameras'),
(57, 'GoPro Hero 12 Black', 499.99, 'Cameras'),
(58, 'DJI Osmo Pocket 3', 619.99, 'Cameras'),
(59, 'Panasonic Lumix S5 IIX', 2299.99, 'Cameras'),
(60, 'Leica Q3', 3499.99, 'Cameras'),
(61, 'LG OLED C3 65-inch', 1999.99, 'Monitors'),
(62, 'Dell UltraSharp U2723QE', 719.99, 'Monitors'),
(63, 'Samsung Odyssey G9 49-inch', 1499.99, 'Monitors'),
(64, 'Apple Studio Display', 1799.99, 'Monitors'),
(65, 'ASUS ProArt PA32UCG-K', 3499.99, 'Monitors'),
(66, 'BenQ PD3220U', 1199.99, 'Monitors'),
(67, 'Sony Bravia XR A95L 55-inch', 2799.99, 'Monitors'),
(68, 'LG UltraGear 27GP950-B', 899.99, 'Monitors'),
(69, 'Samsung S95C OLED 55-inch', 2199.99, 'Monitors'),
(70, 'ViewSonic VP2786-4K', 849.99, 'Monitors'),
(71, 'Logitech MX Keys S', 129.99, 'Peripherals'),
(72, 'Logitech MX Master 3S', 119.99, 'Peripherals'),
(73, 'Razer DeathAdder V3 Pro', 109.99, 'Peripherals'),
(74, 'Corsair K100 RGB', 279.99, 'Peripherals'),
(75, 'SteelSeries Arctis Nova Pro', 399.99, 'Peripherals'),
(76, 'Elgato Stream Deck Plus', 249.99, 'Peripherals'),
(77, 'CalDigit TS4 Thunderbolt Dock', 449.99, 'Peripherals'),
(78, 'Anker 737 Power Bank', 129.99, 'Peripherals'),
(79, 'Samsung T7 Shield 2TB SSD', 199.99, 'Peripherals'),
(80, 'WD Black SN850X 2TB NVMe', 159.99, 'Peripherals'),
(81, 'Apple AirTag 4-Pack', 119.99, 'Smart Home'),
(82, 'Amazon Echo Show 15', 299.99, 'Smart Home'),
(83, 'Google Nest Hub Max', 279.99, 'Smart Home'),
(84, 'Ring Video Doorbell Pro 2', 299.99, 'Smart Home'),
(85, 'Dyson V15 Detect', 849.99, 'Smart Home'),
(86, 'iRobot Roomba j9+', 999.99, 'Smart Home'),
(87, 'Nanoleaf Shapes Hexagons 9-Pack', 249.99, 'Smart Home'),
(88, 'Philips Hue Starter Kit E26', 229.99, 'Smart Home'),
(89, 'Sonos Beam Gen 2', 549.99, 'Smart Home'),
(90, 'Apple HomePod 2', 349.99, 'Smart Home'),
(91, 'Sony PlayStation 5 Slim', 499.99, 'Gaming'),
(92, 'Xbox Series X', 549.99, 'Gaming'),
(93, 'Nintendo Switch OLED', 399.99, 'Gaming'),
(94, 'Steam Deck OLED 1TB', 749.99, 'Gaming'),
(95, 'Meta Quest 3', 599.99, 'Gaming'),
(96, 'Backbone One Controller', 119.99, 'Gaming'),
(97, 'Razer Kishi Ultra', 179.99, 'Gaming'),
(98, 'ASUS ROG Ally X', 899.99, 'Gaming'),
(99, 'Analogue Pocket', 269.99, 'Gaming'),
(100, '8BitDo Ultimate Controller', 89.99, 'Gaming');

-- Global inventory for all 100 products
INSERT IGNORE INTO `inventory` (`id`, `product_id`, `total_stock`, `available_stock`, `version`) VALUES
(1,1,500,500,0),(2,2,400,400,0),(3,3,350,350,0),(4,4,600,600,0),(5,5,450,450,0),
(6,6,200,200,0),(7,7,150,150,0),(8,8,550,550,0),(9,9,700,700,0),(10,10,400,400,0),
(11,11,250,250,0),(12,12,300,300,0),(13,13,180,180,0),(14,14,280,280,0),(15,15,350,350,0),
(16,16,120,120,0),(17,17,400,400,0),(18,18,600,600,0),(19,19,250,250,0),(20,20,220,220,0),
(21,21,550,550,0),(22,22,200,200,0),(23,23,250,250,0),(24,24,350,350,0),(25,25,500,500,0),
(26,26,650,650,0),(27,27,800,800,0),(28,28,450,450,0),(29,29,380,380,0),(30,30,150,150,0),
(31,31,600,600,0),(32,32,800,800,0),(33,33,350,350,0),(34,34,300,300,0),(35,35,700,700,0),
(36,36,500,500,0),(37,37,250,250,0),(38,38,180,180,0),(39,39,100,100,0),(40,40,350,350,0),
(41,41,200,200,0),(42,42,400,400,0),(43,43,140,140,0),(44,44,500,500,0),(45,45,750,750,0),
(46,46,550,550,0),(47,47,300,300,0),(48,48,350,350,0),(49,49,180,180,0),(50,50,250,250,0),
(51,51,120,120,0),(52,52,160,160,0),(53,53,110,110,0),(54,54,240,240,0),(55,55,200,200,0),
(56,56,140,140,0),(57,57,600,600,0),(58,58,550,550,0),(59,59,130,130,0),(60,60,100,100,0),
(61,61,300,300,0),(62,62,500,500,0),(63,63,350,350,0),(64,64,200,200,0),(65,65,150,150,0),
(66,66,380,380,0),(67,67,400,400,0),(68,68,250,250,0),(69,69,180,180,0),(70,70,320,320,0),
(71,71,650,650,0),(72,72,800,800,0),(73,73,700,700,0),(74,74,450,450,0),(75,75,350,350,0),
(76,76,300,300,0),(77,77,550,550,0),(78,78,600,600,0),(79,79,500,500,0),(80,80,400,400,0),
(81,81,750,750,0),(82,82,550,550,0),(83,83,650,650,0),(84,84,800,800,0),(85,85,500,500,0),
(86,86,400,400,0),(87,87,300,300,0),(88,88,380,380,0),(89,89,200,200,0),(90,90,350,350,0),
(91,91,350,350,0),(92,92,300,300,0),(93,93,550,550,0),(94,94,250,250,0),(95,95,350,350,0),
(96,96,700,700,0),(97,97,450,450,0),(98,98,180,180,0),(99,99,280,280,0),(100,100,800,800,0);

-- 5 Flash Sale campaigns (all on CURDATE())
INSERT IGNORE INTO `flash_sale` (`id`, `name`, `start_time`, `end_time`, `status`) VALUES
(1, 'Morning Rush: Smartphones & Laptops',   TIMESTAMP(CURDATE(), '07:00:00'), TIMESTAMP(CURDATE(), '09:00:00'), 1),
(2, 'Midday Deals: Tablets & Audio',         TIMESTAMP(CURDATE(), '10:00:00'), TIMESTAMP(CURDATE(), '12:00:00'), 1),
(3, 'Afternoon Blitz: Wearables & Cameras',  TIMESTAMP(CURDATE(), '13:00:00'), TIMESTAMP(CURDATE(), '15:00:00'), 1),
(4, 'Evening Sale: Monitors & Peripherals',  TIMESTAMP(CURDATE(), '16:00:00'), TIMESTAMP(CURDATE(), '18:00:00'), 1),
(5, 'Night Frenzy: Smart Home & Gaming',     TIMESTAMP(CURDATE(), '19:00:00'), TIMESTAMP(CURDATE(), '21:00:00'), 1);

-- Flash Sale Product allocations (100 rows — each product in 1 campaign)
-- sale_stock is a subset of global inventory allocated for this flash sale
INSERT IGNORE INTO `flash_sale_product` (`id`, `flash_sale_id`, `product_id`, `sale_price`, `sale_stock`, `sale_available`, `per_user_limit`, `enabled`, `version`) VALUES
-- Campaign 1: Smartphones & Laptops (07:00-09:00) — products 1-20
(1,  1, 1,  1299.99, 300, 300, 1, 1, 0),
(2,  1, 2,  1199.99, 150, 150, 1, 1, 0),
(3,  1, 3,  999.99,  200, 200, 1, 1, 0),
(4,  1, 4,  799.99,  400, 400, 1, 1, 0),
(5,  1, 5,  899.99,  250, 250, 1, 1, 0),
(6,  1, 6,  1099.99, 100, 100, 1, 1, 0),
(7,  1, 7,  1799.99, 80,  80,  1, 1, 0),
(8,  1, 8,  799.99,  350, 350, 1, 1, 0),
(9,  1, 9,  499.99,  500, 500, 1, 1, 0),
(10, 1, 10, 599.99,  220, 220, 1, 1, 0),
(11, 1, 11, 2499.99, 120, 120, 1, 1, 0),
(12, 1, 12, 1799.99, 180, 180, 1, 1, 0),
(13, 1, 13, 1649.99, 90,  90,  1, 1, 0),
(14, 1, 14, 1599.99, 160, 160, 1, 1, 0),
(15, 1, 15, 1499.99, 200, 200, 1, 1, 0),
(16, 1, 16, 2699.99, 60,  60,  1, 1, 0),
(17, 1, 17, 1299.99, 250, 250, 1, 1, 0),
(18, 1, 18, 849.99,  400, 400, 1, 1, 0),
(19, 1, 19, 1499.99, 140, 140, 1, 1, 0),
(20, 1, 20, 1399.99, 110, 110, 1, 1, 0),
-- Campaign 2: Tablets & Audio (10:00-12:00) — products 21-40
(21, 2, 21, 599.99,  350, 350, 1, 1, 0),
(22, 2, 22, 1299.99, 100, 100, 1, 1, 0),
(23, 2, 23, 1199.99, 130, 130, 1, 1, 0),
(24, 2, 24, 999.99,  200, 200, 1, 1, 0),
(25, 2, 25, 649.99,  300, 300, 1, 1, 0),
(26, 2, 26, 479.99,  450, 450, 1, 1, 0),
(27, 2, 27, 229.99,  500, 500, 1, 1, 0),
(28, 2, 28, 499.99,  280, 280, 1, 1, 0),
(29, 2, 29, 449.99,  190, 190, 1, 1, 0),
(30, 2, 30, 799.99,  75,  75,  1, 1, 0),
(31, 2, 31, 349.99,  400, 400, 1, 1, 0),
(32, 2, 32, 249.99,  500, 500, 1, 1, 0),
(33, 2, 33, 429.99,  200, 200, 1, 1, 0),
(34, 2, 34, 349.99,  150, 150, 1, 1, 0),
(35, 2, 35, 179.99,  450, 450, 1, 1, 0),
(36, 2, 36, 299.99,  300, 300, 1, 1, 0),
(37, 2, 37, 399.99,  120, 120, 1, 1, 0),
(38, 2, 38, 379.99,  80,  80,  1, 1, 0),
(39, 2, 39, 899.99,  50,  50,  1, 1, 0),
(40, 2, 40, 449.99,  180, 180, 1, 1, 0),
-- Campaign 3: Wearables & Cameras (13:00-15:00) — products 41-60
(41, 3, 41, 799.99,  100, 100, 1, 1, 0),
(42, 3, 42, 399.99,  250, 250, 1, 1, 0),
(43, 3, 43, 899.99,  70,  70,  1, 1, 0),
(44, 3, 44, 349.99,  300, 300, 1, 1, 0),
(45, 3, 45, 159.99,  480, 480, 1, 1, 0),
(46, 3, 46, 399.99,  350, 350, 1, 1, 0),
(47, 3, 47, 449.99,  150, 150, 1, 1, 0),
(48, 3, 48, 349.99,  200, 200, 1, 1, 0),
(49, 3, 49, 399.99,  90,  90,  1, 1, 0),
(50, 3, 50, 299.99,  130, 130, 1, 1, 0),
(51, 3, 51, 2499.99, 60,  60,  1, 1, 0),
(52, 3, 52, 2499.99, 80,  80,  1, 1, 0),
(53, 3, 53, 2999.99, 55,  55,  1, 1, 0),
(54, 3, 54, 1699.99, 120, 120, 1, 1, 0),
(55, 3, 55, 1499.99, 100, 100, 1, 1, 0),
(56, 3, 56, 2199.99, 70,  70,  1, 1, 0),
(57, 3, 57, 399.99,  400, 400, 1, 1, 0),
(58, 3, 58, 519.99,  350, 350, 1, 1, 0),
(59, 3, 59, 1999.99, 65,  65,  1, 1, 0),
(60, 3, 60, 2999.99, 50,  50,  1, 1, 0),
-- Campaign 4: Monitors & Peripherals (16:00-18:00) — products 61-80
(61, 4, 61, 1799.99, 150, 150, 1, 1, 0),
(62, 4, 62, 619.99,  300, 300, 1, 1, 0),
(63, 4, 63, 1299.99, 180, 180, 1, 1, 0),
(64, 4, 64, 1599.99, 100, 100, 1, 1, 0),
(65, 4, 65, 2999.99, 75,  75,  1, 1, 0),
(66, 4, 66, 999.99,  200, 200, 1, 1, 0),
(67, 4, 67, 2499.99, 250, 250, 1, 1, 0),
(68, 4, 68, 799.99,  120, 120, 1, 1, 0),
(69, 4, 69, 1899.99, 90,  90,  1, 1, 0),
(70, 4, 70, 749.99,  160, 160, 1, 1, 0),
(71, 4, 71, 109.99,  420, 420, 1, 1, 0),
(72, 4, 72, 99.99,   500, 500, 1, 1, 0),
(73, 4, 73, 89.99,   450, 450, 1, 1, 0),
(74, 4, 74, 229.99,  280, 280, 1, 1, 0),
(75, 4, 75, 349.99,  200, 200, 1, 1, 0),
(76, 4, 76, 199.99,  150, 150, 1, 1, 0),
(77, 4, 77, 399.99,  350, 350, 1, 1, 0),
(78, 4, 78, 109.99,  400, 400, 1, 1, 0),
(79, 4, 79, 159.99,  300, 300, 1, 1, 0),
(80, 4, 80, 129.99,  250, 250, 1, 1, 0),
-- Campaign 5: Smart Home & Gaming (19:00-21:00) — products 81-100
(81, 5, 81, 99.99,   480, 480, 1, 1, 0),
(82, 5, 82, 249.99,  350, 350, 1, 1, 0),
(83, 5, 83, 229.99,  420, 420, 1, 1, 0),
(84, 5, 84, 249.99,  500, 500, 1, 1, 0),
(85, 5, 85, 749.99,  300, 300, 1, 1, 0),
(86, 5, 86, 899.99,  250, 250, 1, 1, 0),
(87, 5, 87, 199.99,  150, 150, 1, 1, 0),
(88, 5, 88, 179.99,  200, 200, 1, 1, 0),
(89, 5, 89, 449.99,  100, 100, 1, 1, 0),
(90, 5, 90, 299.99,  180, 180, 1, 1, 0),
(91, 5, 91, 449.99,  200, 200, 1, 1, 0),
(92, 5, 92, 499.99,  160, 160, 1, 1, 0),
(93, 5, 93, 349.99,  350, 350, 1, 1, 0),
(94, 5, 94, 649.99,  120, 120, 1, 1, 0),
(95, 5, 95, 499.99,  180, 180, 1, 1, 0),
(96, 5, 96, 99.99,   450, 450, 1, 1, 0),
(97, 5, 97, 149.99,  280, 280, 1, 1, 0),
(98, 5, 98, 799.99,  90,  90,  1, 1, 0),
(99, 5, 99, 219.99,  140, 140, 1, 1, 0),
(100,5,100, 69.99,   500, 500, 1, 1, 0);
