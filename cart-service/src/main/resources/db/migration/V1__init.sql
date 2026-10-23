-- ---------------------------------------------------------------------------
-- MetaShop cart-service — initial schema
-- ---------------------------------------------------------------------------

-- One cart per user. Created lazily by the first mutation; a GET for a user
-- without a cart returns an empty cart and creates nothing.
-- `version` is a @Version column: two concurrent mutations of the same cart row
-- make the loser fail its optimistic-lock check instead of silently dropping a
-- line. Line-level races are handled by the unique key on cart_items instead.
CREATE TABLE carts (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    user_id    BIGINT   NOT NULL,
    version    BIGINT   NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_carts_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- One line per product in a cart. Deliberately minimal: the cart stores only the
-- product identity + quantity + the price at add time (added_price, kept only to
-- flag "price changed since you added this"). Name / image / live price / stock
-- are fetched from product-service and inventory-service on every render, so a
-- line can never go stale in the database.
-- The unique key on (cart_id, product_id) is what makes a concurrent double-add
-- safe: the losing insert fails and the caller retries onto the update path.
CREATE TABLE cart_items (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    cart_id     BIGINT        NOT NULL,
    product_id  BIGINT        NOT NULL,
    quantity    INT           NOT NULL,
    added_price DECIMAL(10,2) NOT NULL,
    added_at    DATETIME      NOT NULL,
    created_at  DATETIME      NOT NULL,
    updated_at  DATETIME      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_cart_items_cart_product (cart_id, product_id),
    KEY idx_cart_items_product (product_id),
    CONSTRAINT chk_cart_items_quantity CHECK (quantity BETWEEN 1 AND 20)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
