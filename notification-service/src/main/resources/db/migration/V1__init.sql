-- ---------------------------------------------------------------------------
-- MetaShop notification-service — initial schema (spec section 13)
-- ---------------------------------------------------------------------------

-- The audit log of every email this service has tried to send. A row is written before the
-- SMTP call, so a delivery that dies half way is still visible and retryable from the admin
-- API rather than silently lost.
CREATE TABLE notifications (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    -- The event that produced this row. Added to the spec's column list (see D-080): it is
    -- what makes a redelivery update the existing row instead of sending the same email
    -- twice, and what the admin retry endpoint can correlate back to the source event.
    event_id    VARCHAR(64)  NULL,
    -- The recipient's user id when the event carries one. NULL for admin alerts such as
    -- InventoryLowEvent, which have no owning user.
    user_id     BIGINT       NULL,
    email       VARCHAR(150) NOT NULL,
    -- NotificationType name, e.g. ORDER_CONFIRMED. Deliberately not the Kafka event name:
    -- one event type can map to several notifications later without a schema change.
    type        VARCHAR(50)  NOT NULL,
    subject     VARCHAR(255) NOT NULL,
    -- The rendered HTML body. Rendered once at send time and stored, so a retry replays
    -- exactly what the user would have received.
    body        TEXT         NOT NULL,
    status      VARCHAR(20)  NOT NULL,
    -- Failed delivery attempts. Spec 13 retries three times before the event is dead-lettered.
    retry_count INT          NOT NULL DEFAULT 0,
    -- Why the last attempt failed, truncated to the column width.
    error       VARCHAR(255) NULL,
    created_at  DATETIME     NOT NULL,
    sent_at     DATETIME     NULL,
    PRIMARY KEY (id),
    -- NULL is allowed and MySQL permits many NULLs in a unique index, so notifications that
    -- do not come from an event (none today) would not collide.
    UNIQUE KEY uk_notifications_event (event_id),
    -- The admin list is "newest first, optionally filtered by status" (spec 13).
    KEY idx_notifications_status_created (status, created_at),
    KEY idx_notifications_user_created (user_id, created_at),
    KEY idx_notifications_type (type),
    -- Spec 13 lists an ENUM. A VARCHAR + CHECK is used instead for the same reason as
    -- inventory-service (D-049): adding a status later is a migration, not a table rebuild.
    CONSTRAINT chk_notifications_status CHECK (status IN ('PENDING', 'SENT', 'FAILED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- Consumer de-duplication (spec 14.4). Kafka delivers at least once, so the same event can
-- arrive twice; an event id present here has already been handled and is skipped. The table
-- shape is identical to the one already created in inventory-service / order-service /
-- payment-service so the Kafka phase sees one consistent contract.
CREATE TABLE processed_events (
    event_id     VARCHAR(64) NOT NULL,
    event_type   VARCHAR(80) NULL,
    processed_at DATETIME    NOT NULL,
    PRIMARY KEY (event_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
