-- ---------------------------------------------------------------------------
-- MetaShop payment-service — initial schema
-- ---------------------------------------------------------------------------

-- One row per payment ATTEMPT. The amount is copied from the order (the
-- authoritative total lives in order-service) and never taken from the client.
-- A customer may retry after a decline, so an order can have several FAILED
-- rows but at most one that ends up SUCCEEDED (guarded in the service; the
-- order-service callback is idempotent, D-071).
-- `version` is a @Version column: only one writer touches a payment row, but
-- the column keeps a racing admin view from reading a half-applied state and
-- matches the other services' conventions.
CREATE TABLE payments (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    order_id          BIGINT        NOT NULL,
    user_id           BIGINT        NOT NULL,
    amount            DECIMAL(10,2) NOT NULL,
    method            VARCHAR(20)   NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    gateway_reference VARCHAR(64)   NULL,
    failure_reason    VARCHAR(255)  NULL,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        DATETIME      NOT NULL,
    updated_at        DATETIME      NOT NULL,
    PRIMARY KEY (id),
    KEY idx_payments_user (user_id, created_at),
    KEY idx_payments_order (order_id),
    CONSTRAINT chk_payments_method
        CHECK (method IN ('CARD', 'UPI', 'WALLET')),
    CONSTRAINT chk_payments_status
        CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_payments_amount CHECK (amount >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
