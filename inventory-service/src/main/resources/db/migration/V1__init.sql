-- ---------------------------------------------------------------------------
-- MetaShop inventory-service — initial schema (spec section 9.2)
-- ---------------------------------------------------------------------------

-- One row per product. availableQuantity + reservedQuantity are the source of
-- truth for "can this be sold right now":
--   availableQuantity  -> physically sellable stock
--   reservedQuantity   -> held by a RESERVED reservation, not yet sold
-- The CHECK constraints are the last line of defence against a negative count:
-- even a buggy UPDATE cannot drive either counter below zero.
CREATE TABLE inventory (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    product_id          BIGINT      NOT NULL,
    sku                 VARCHAR(50) NULL,
    available_quantity  INT         NOT NULL DEFAULT 0,
    reserved_quantity   INT         NOT NULL DEFAULT 0,
    low_stock_threshold INT         NOT NULL DEFAULT 5,
    -- @Version: bumped by every write, including the atomic reserve UPDATE, so a
    -- concurrent JPA-managed write on the same row fails instead of silently
    -- overwriting (spec 9.4).
    version             BIGINT      NOT NULL DEFAULT 0,
    created_at          DATETIME    NOT NULL,
    updated_at          DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_product (product_id),
    CONSTRAINT chk_inventory_available CHECK (available_quantity >= 0),
    CONSTRAINT chk_inventory_reserved CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_inventory_threshold CHECK (low_stock_threshold >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- A reservation is one line of an order holding stock. One row per (order, product)
-- makes the reservation idempotent per order: a duplicate reserve hits the unique key.
-- NOTE: `status` is a VARCHAR + CHECK rather than a MySQL ENUM so that the
-- @Enumerated(STRING) mapping passes Hibernate's ddl-auto=validate and the allowed
-- values stay visible in the schema.
CREATE TABLE inventory_reservations (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    order_id   BIGINT      NOT NULL,
    product_id BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    status     VARCHAR(20) NOT NULL,
    expires_at DATETIME    NULL,
    created_at DATETIME    NOT NULL,
    updated_at DATETIME    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reservations_order_product (order_id, product_id),
    KEY idx_reservations_product (product_id),
    -- Used by the expiry sweeper: "RESERVED and expires_at < now" (spec 9.4).
    KEY idx_reservations_status_expires (status, expires_at),
    CONSTRAINT chk_reservations_quantity CHECK (quantity > 0),
    CONSTRAINT chk_reservations_status
        CHECK (status IN ('RESERVED', 'CONFIRMED', 'RELEASED', 'EXPIRED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Append-only audit trail of every stock change (spec 9.2). `reason` is a
-- StockMovementReason constant; `reference` carries the order id or the actor.
CREATE TABLE stock_movements (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    product_id BIGINT       NOT NULL,
    change_qty INT          NOT NULL,
    reason     VARCHAR(50)  NOT NULL,
    reference  VARCHAR(100) NULL,
    created_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_stock_movements_product (product_id, created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Kafka consumer de-duplication (spec 9.2 / 14.4). Created now so the schema is
-- complete; the consumer that writes to it arrives with the Kafka phase.
CREATE TABLE processed_events (
    event_id     VARCHAR(64) NOT NULL,
    event_type   VARCHAR(80) NULL,
    processed_at DATETIME    NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
