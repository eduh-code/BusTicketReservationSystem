CREATE TABLE IF NOT EXISTS routes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(255) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    transportation_fee DOUBLE NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_routes_source_destination (source, destination)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS buses (
    id BIGINT NOT NULL AUTO_INCREMENT,
    bus_name VARCHAR(255) NOT NULL,
    bus_number VARCHAR(255) NOT NULL,
    source VARCHAR(255) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    departure_time VARCHAR(255),
    arrival_time VARCHAR(255),
    price DOUBLE NOT NULL,
    total_seats INT NOT NULL,
    route_id BIGINT,
    PRIMARY KEY (id),
    INDEX idx_buses_route (source, destination),
    CONSTRAINT fk_buses_route FOREIGN KEY (route_id) REFERENCES routes (id)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS bookings (
    id BIGINT NOT NULL AUTO_INCREMENT,
    passenger_name VARCHAR(255) NOT NULL,
    passenger_email VARCHAR(255) NOT NULL,
    selected_seats VARCHAR(255) NOT NULL,
    total_price DOUBLE NOT NULL,
    booking_date DATETIME(6) NOT NULL,
    bus_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_bookings_passenger_email (passenger_email),
    INDEX idx_bookings_bus_id (bus_id),
    CONSTRAINT fk_bookings_bus FOREIGN KEY (bus_id) REFERENCES buses (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS payments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    booking_id BIGINT NOT NULL,
    payment_method VARCHAR(255) NOT NULL,
    phone_number VARCHAR(255) NOT NULL,
    transaction_id VARCHAR(255),
    ticket_number VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    payment_date DATETIME(6) NOT NULL,
    merchant_reference VARCHAR(255) NOT NULL,
    provider_tracking_id VARCHAR(255) NOT NULL,
    checkout_url VARCHAR(2048) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_payments_booking (booking_id),
    UNIQUE KEY uk_payments_transaction (transaction_id),
    UNIQUE KEY uk_payments_ticket (ticket_number),
    UNIQUE KEY uk_payments_merchant_reference (merchant_reference),
    UNIQUE KEY uk_payments_provider_tracking_id (provider_tracking_id),
    CONSTRAINT fk_payments_booking FOREIGN KEY (booking_id) REFERENCES bookings (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
