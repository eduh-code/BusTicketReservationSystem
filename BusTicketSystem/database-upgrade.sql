USE bus_db;

-- Add this field before creating routes so existing buses can retain their paths.
CREATE TABLE IF NOT EXISTS routes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    source VARCHAR(255) NOT NULL,
    destination VARCHAR(255) NOT NULL,
    transportation_fee DOUBLE NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_routes_source_destination (source, destination)
) ENGINE=InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

INSERT INTO routes (source, destination, transportation_fee)
SELECT source, destination, MIN(price)
FROM buses
GROUP BY source, destination
ON DUPLICATE KEY UPDATE source = VALUES(source);

ALTER TABLE buses
    ADD COLUMN route_id BIGINT NULL,
    ADD INDEX idx_buses_route_id (route_id),
    ADD CONSTRAINT fk_buses_route FOREIGN KEY (route_id) REFERENCES routes (id);

UPDATE buses b
JOIN routes r
  ON LOWER(r.source) = LOWER(b.source)
 AND LOWER(r.destination) = LOWER(b.destination)
SET b.route_id = r.id,
    b.price = r.transportation_fee
WHERE b.route_id IS NULL OR b.price <> r.transportation_fee;

-- Existing local simulated payments are not proof of a real charge; invalidate them.
ALTER TABLE payments
    MODIFY transaction_id VARCHAR(255) NULL,
    MODIFY ticket_number VARCHAR(255) NULL;

UPDATE payments
SET status = 'SIMULATED_NOT_PAID',
    transaction_id = NULL,
    ticket_number = NULL
WHERE status = 'SUCCESS';

ALTER TABLE payments
    ADD COLUMN merchant_reference VARCHAR(255) NULL,
    ADD COLUMN provider_tracking_id VARCHAR(255) NULL,
    ADD COLUMN checkout_url VARCHAR(2048) NULL;

UPDATE payments
SET merchant_reference = CONCAT('LEGACY-', id),
    provider_tracking_id = CONCAT('LEGACY-', id),
    checkout_url = 'https://pesapal.com/'
WHERE merchant_reference IS NULL;

ALTER TABLE payments
    MODIFY merchant_reference VARCHAR(255) NOT NULL,
    MODIFY provider_tracking_id VARCHAR(255) NOT NULL,
    MODIFY checkout_url VARCHAR(2048) NOT NULL,
    ADD UNIQUE KEY uk_payments_merchant_reference (merchant_reference),
    ADD UNIQUE KEY uk_payments_provider_tracking_id (provider_tracking_id);
