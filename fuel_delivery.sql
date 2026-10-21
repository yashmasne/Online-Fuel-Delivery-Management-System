-- =====================================================================
-- FuelExpress - Online Fuel Delivery Management System
-- Fixed schema - matches dao/*.java and model/*.java exactly.
-- Compatible with: MySQL 8.x
--
-- Amounts are in INR (rupees). Sample data uses Pune, Maharashtra.
-- =====================================================================
DROP DATABASE IF EXISTS fuel_delivery_db;
CREATE DATABASE fuel_delivery_db;
USE fuel_delivery_db;

-- =====================================================================
-- 1. USERS  (maps to model/User.java, dao/UserDAO.java)
--    Holds BOTH customers and admins - the "role" column tells them apart.
--    There is deliberately no separate "admins" table: AdminLoginServlet
--    looks up admins via UserDAO.login() and checks role = 'ADMIN'.
-- =====================================================================
CREATE TABLE users (
    user_id       INT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100)  NOT NULL,
    email         VARCHAR(100)  NOT NULL UNIQUE,
    phone         VARCHAR(15)   NOT NULL,
    password      VARCHAR(255)  NOT NULL,
    address       VARCHAR(255),
    city          VARCHAR(50),
    pincode       VARCHAR(10),
    role          ENUM('ADMIN','USER') NOT NULL DEFAULT 'USER',
    status        ENUM('ACTIVE','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- =====================================================================
-- 2. DELIVERY_AGENTS  (maps to model/DeliveryAgent.java, DeliveryAgentDAO.java)
-- =====================================================================
CREATE TABLE delivery_agents (
    agent_id            INT AUTO_INCREMENT PRIMARY KEY,
    full_name           VARCHAR(100)  NOT NULL,
    email               VARCHAR(100)  NOT NULL UNIQUE,
    phone               VARCHAR(15)   NOT NULL,
    password            VARCHAR(255)  NOT NULL,
    vehicle_number      VARCHAR(20),
    vehicle_type        VARCHAR(30),
    availability_status ENUM('AVAILABLE','BUSY') NOT NULL DEFAULT 'AVAILABLE',
    latitude            DECIMAL(10,7) DEFAULT NULL,
    longitude           DECIMAL(10,7) DEFAULT NULL,
    location_updated_at TIMESTAMP NULL DEFAULT NULL,
    created_at          TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- =====================================================================
-- 3. FUEL_TYPES  (maps to model/FuelType.java, dao/FuelTypeDAO.java)
--    Price is per litre, in INR.
-- =====================================================================
CREATE TABLE fuel_types (
    fuel_id          INT AUTO_INCREMENT PRIMARY KEY,
    fuel_name        VARCHAR(50)   NOT NULL UNIQUE,
    price_per_litre  DECIMAL(10,2) NOT NULL,
    description      VARCHAR(255),
    price_updated_at TIMESTAMP NULL DEFAULT NULL
) ENGINE=InnoDB;

-- =====================================================================
-- 4. FUEL_ORDERS  (maps to model/FuelOrder.java, dao/FuelOrderDAO.java)
--    price_per_litre is snapshotted per order so a later price change
--    never alters the total of an already-placed order.
-- =====================================================================
CREATE TABLE fuel_orders (
    order_id          INT AUTO_INCREMENT PRIMARY KEY,
    order_number      VARCHAR(30)   NOT NULL UNIQUE,
    user_id           INT           NOT NULL,
    agent_id          INT           DEFAULT NULL,
    fuel_id           INT           NOT NULL,
    vehicle_number    VARCHAR(20),
    vehicle_type      VARCHAR(30),
    quantity          DECIMAL(10,2) NOT NULL,
    price_per_litre   DECIMAL(10,2) NOT NULL,
    total_amount      DECIMAL(10,2) NOT NULL,
    delivery_address  VARCHAR(255)  NOT NULL,
    latitude          DECIMAL(10,6) DEFAULT 0,
    longitude         DECIMAL(10,6) DEFAULT 0,
    payment_method    ENUM('CASH','CARD','UPI','WALLET') NOT NULL DEFAULT 'UPI',
    payment_status    ENUM('PENDING','PAID','FAILED','REFUNDED') NOT NULL DEFAULT 'PENDING',
    order_status      ENUM('PENDING','CONFIRMED','ASSIGNED','OUT_FOR_DELIVERY','DELIVERED','CANCELLED')
                        NOT NULL DEFAULT 'PENDING',
    order_date        TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    delivery_date     TIMESTAMP NULL DEFAULT NULL,

    CONSTRAINT fk_order_user   FOREIGN KEY (user_id)  REFERENCES users(user_id)            ON DELETE CASCADE,
    CONSTRAINT fk_order_agent  FOREIGN KEY (agent_id) REFERENCES delivery_agents(agent_id) ON DELETE SET NULL,
    CONSTRAINT fk_order_fuel   FOREIGN KEY (fuel_id)  REFERENCES fuel_types(fuel_id)       ON DELETE RESTRICT
) ENGINE=InnoDB;

-- =====================================================================
-- 5. PAYMENTS  (maps to model/Payment.java, dao/PaymentDAO.java)
--    One payment row per order (UNIQUE order_id).
-- =====================================================================
CREATE TABLE payments (
    payment_id      INT AUTO_INCREMENT PRIMARY KEY,
    order_id        INT           NOT NULL UNIQUE,
    transaction_id  VARCHAR(50)   NOT NULL UNIQUE,
    payment_method  ENUM('CASH','CARD','UPI','WALLET') NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    payment_status  ENUM('PENDING','PAID','FAILED','REFUNDED') NOT NULL DEFAULT 'PENDING',
    payment_date    TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_payment_order FOREIGN KEY (order_id) REFERENCES fuel_orders(order_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- =====================================================================
-- 6. FEEDBACK  (maps to model/Feedback.java, dao/FeedbackDAO.java)
--    One feedback row per order (UNIQUE order_id).
-- =====================================================================
CREATE TABLE feedback (
    feedback_id     INT AUTO_INCREMENT PRIMARY KEY,
    order_id        INT     NOT NULL UNIQUE,
    user_id         INT     NOT NULL,
    rating          TINYINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    comments        VARCHAR(500),
    feedback_date   TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_feedback_order FOREIGN KEY (order_id) REFERENCES fuel_orders(order_id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_user  FOREIGN KEY (user_id)  REFERENCES users(user_id)         ON DELETE CASCADE
) ENGINE=InnoDB;

-- =====================================================================
-- 7. CONTACT_MESSAGES  (maps to model/ContactMessage.java, dao/ContactMessageDAO.java)
-- =====================================================================
CREATE TABLE contact_messages (
    message_id    INT AUTO_INCREMENT PRIMARY KEY,
    full_name     VARCHAR(100)  NOT NULL,
    email         VARCHAR(100)  NOT NULL,
    message       VARCHAR(1000) NOT NULL,
    status        ENUM('NEW','READ','RESPONDED') NOT NULL DEFAULT 'NEW',
    submitted_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- =====================================================================
-- 8. NOTIFICATIONS  (maps to model/Notification.java, dao/NotificationDAO.java)
-- =====================================================================
CREATE TABLE notifications (
    notification_id INT AUTO_INCREMENT PRIMARY KEY,
    recipient_type  VARCHAR(10)   NOT NULL,
    recipient_id    INT           NOT NULL,
    order_id        INT           DEFAULT NULL,
    title           VARCHAR(150)  NOT NULL,
    message         VARCHAR(500)  NOT NULL,
    is_read         TINYINT(1)    NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_notifications_order FOREIGN KEY (order_id) REFERENCES fuel_orders(order_id) ON DELETE CASCADE,
    INDEX idx_notifications_recipient (recipient_type, recipient_id, is_read),
    INDEX idx_notifications_order (order_id)
) ENGINE=InnoDB;

-- =====================================================================
-- Helpful indexes for dashboard / lookup queries
-- =====================================================================
CREATE INDEX idx_orders_status   ON fuel_orders(order_status);
CREATE INDEX idx_orders_agent    ON fuel_orders(agent_id);
CREATE INDEX idx_orders_user     ON fuel_orders(user_id);
CREATE INDEX idx_agents_status   ON delivery_agents(availability_status);
CREATE INDEX idx_payments_status ON payments(payment_status);
CREATE INDEX idx_users_role      ON users(role);

-- =====================================================================
-- Sample data
-- =====================================================================

-- Admin login: admin@fuelexpress.in / admin123  (CHANGE THIS before going live)
INSERT INTO users (full_name, email, phone, password, address, city, pincode, role, status) VALUES
('System Administrator', 'admin@fuelexpress.in', '9800000000', 'admin123',
 'FuelExpress HQ, Hinjawadi Phase 1', 'Pune', '411057', 'ADMIN', 'ACTIVE');

-- Sample customers (Pune / Mumbai)
INSERT INTO users (full_name, email, phone, password, address, city, pincode, role, status) VALUES
('Rahul Sharma', 'rahul@example.com', '9876543210', 'pass123', '123 MG Road', 'Pune', '411001', 'USER', 'ACTIVE'),
('Priya Verma',  'priya@example.com', '9876543211', 'pass123', '45 Park Street', 'Mumbai', '400001', 'USER', 'ACTIVE');

-- Sample delivery agents
INSERT INTO delivery_agents (full_name, email, phone, password, vehicle_number, vehicle_type, availability_status) VALUES
('Suresh Kumar', 'suresh.agent@example.com', '9123456780', 'agent123', 'MH12AB1234', 'Tanker Bike', 'AVAILABLE'),
('Anita Rao',    'anita.agent@example.com',  '9123456781', 'agent123', 'MH14CD5678', 'Mini Van',    'AVAILABLE');

-- Fuel types with 2026-era Pune/Maharashtra pump prices (₹ per litre)
INSERT INTO fuel_types (fuel_name, price_per_litre, description) VALUES
('Petrol (Premium 95)', 104.50, 'Premium quality petrol, octane 95'),
('Diesel (Ultra-Low)',   92.75, 'High speed, ultra-low sulphur diesel'),
('CNG',                  85.00, 'Compressed natural gas, per kg'),
('LPG (Cylinder)',      850.00, 'Domestic LPG cylinder, per unit');

-- A couple of sample orders + payments
INSERT INTO fuel_orders
    (order_number, user_id, agent_id, fuel_id, vehicle_number, vehicle_type, quantity, price_per_litre,
     total_amount, delivery_address, latitude, longitude, payment_method, payment_status, order_status)
VALUES
    ('FE-ORD-A1B2C3D4', 1, 1, 1, 'MH12XY9999', 'Sedan / Saloon', 10.00, 104.50,
     1045.00, '123 MG Road, Pune', 18.516726, 73.856255, 'UPI', 'PAID', 'DELIVERED'),
    ('FE-ORD-E5F6G7H8', 2, NULL, 2, 'MH04ZZ1234', 'Heavy Cargo Truck', 5.00, 92.75,
     463.75, '45 Park Street, Mumbai', 19.075983, 72.877655, 'CASH', 'PENDING', 'PENDING');

INSERT INTO payments (order_id, transaction_id, payment_method, amount, payment_status) VALUES
(1, 'TXN0000000001', 'UPI',  1045.00, 'PAID'),
(2, 'TXN0000000002', 'CASH',  463.75, 'PENDING');

INSERT INTO feedback (order_id, user_id, rating, comments) VALUES
(1, 1, 5, 'Fast delivery, great service!');

INSERT INTO contact_messages (full_name, email, message, status) VALUES
('Guest User', 'guest@example.com', 'My order is delayed, please check.', 'NEW');

-- =====================================================================
-- END OF SCHEMA
-- =====================================================================
