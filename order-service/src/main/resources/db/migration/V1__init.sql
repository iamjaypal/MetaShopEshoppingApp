-- ---------------------------------------------------------------------------
-- MetaShop order-service — initial schema
-- ---------------------------------------------------------------------------

-- One row per order. The money columns are a SNAPSHOT frozen at checkout time
-- (spec 10.2): later price changes in product-service never move an order total.
-- The shipping address is copied onto the order for the same reason — the user
-- may edit or delete their address book entry tomorrow; the order must not move.
-- `version` is a @Version column: a racing payment callback and an admin cancel
-- make the loser fail its optimistic-lock check instead of double-transitioning.
CREATE TABLE orders (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    user_id       BIGINT        NOT NULL,
    status        VARCHAR(20)   NOT NULL,
    payment_status VARCHAR(20)  NOT NULL,
    subtotal      DECIMAL(10,2) NOT NULL,
    shipping_fee  DECIMAL(10,2) NOT NULL,
    total         DECIMAL(10,2) NOT NULL,
    -- address snapshot (auth-service /api/v1/internal/users/{id}/addresses/{aid})
    full_name     VARCHAR(100)  NOT NULL,
    phone         VARCHAR(20)   NOT NULL,
    line1         VARCHAR(200)  NOT NULL,
    line2         VARCHAR(200)  NULL,
    city          VARCHAR(100)  NOT NULL,
    state         VARCHAR(100)  NOT NULL,
    postal_code   VARCHAR(20)   NOT NULL,
    country       VARCHAR(2)    NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    created_at    DATETIME      NOT NULL,
    updated_at    DATETIME      NOT NULL,
    PRIMARY KEY (id),
    KEY idx_orders_user (user_id, created_at),
    -- status is a VARCHAR + CHECK rather than a MySQL ENUM so that the
    -- @Enumerated(STRING) mapping passes Hibernate's ddl-auto=validate and the
    -- allowed values stay visible in the schema (same deviation as D-049).
    CONSTRAINT chk_orders_status
        CHECK (status IN ('CREATED', 'CONFIRMED', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT chk_orders_payment_status
        CHECK (payment_status IN ('PENDING', 'PAID', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- One line per product of an order. sku / name / image / unit_price are frozen
-- copies from the product-service snapshot taken at checkout — the order is the
-- receipt and must survive a product being edited or deleted later.
CREATE TABLE order_items (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    order_id   BIGINT        NOT NULL,
    product_id BIGINT        NOT NULL,
    sku        VARCHAR(50)   NULL,
    name       VARCHAR(255)  NOT NULL,
    image_url  VARCHAR(500)  NULL,
    unit_price DECIMAL(10,2) NOT NULL,
    quantity   INT           NOT NULL,
    created_at DATETIME      NOT NULL,
    updated_at DATETIME      NOT NULL,
    PRIMARY KEY (id),
    KEY idx_order_items_order (order_id),
    KEY idx_order_items_product (product_id),
    CONSTRAINT chk_order_items_quantity CHECK (quantity > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
