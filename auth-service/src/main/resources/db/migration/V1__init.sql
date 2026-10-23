-- ---------------------------------------------------------------------------
-- MetaShop auth-service — initial schema (spec section 7.2)
-- ---------------------------------------------------------------------------

CREATE TABLE roles (
    id   BIGINT      NOT NULL AUTO_INCREMENT,
    name VARCHAR(30) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_roles_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

-- NOTE: the utf8mb4_unicode_ci collation makes the unique index on `email`
-- case-insensitive, which is required by the "email unique (case-insensitive)"
-- business rule (spec 7.4).
CREATE TABLE users (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    first_name            VARCHAR(80)  NOT NULL,
    last_name             VARCHAR(80)  NOT NULL,
    email                 VARCHAR(150) NOT NULL,
    phone                 VARCHAR(20)  NULL,
    password_hash         VARCHAR(100) NOT NULL,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified        BOOLEAN      NOT NULL DEFAULT FALSE,
    -- Account lockout after N failed logins (spec 7.4)
    failed_login_attempts INT          NOT NULL DEFAULT 0,
    locked_until          DATETIME     NULL,
    created_at            DATETIME     NOT NULL,
    updated_at            DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    KEY idx_user_roles_role (role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE addresses (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    full_name   VARCHAR(120) NOT NULL,
    phone       VARCHAR(20)  NOT NULL,
    line1       VARCHAR(200) NOT NULL,
    line2       VARCHAR(200) NULL,
    city        VARCHAR(80)  NOT NULL,
    state       VARCHAR(80)  NOT NULL,
    postal_code VARCHAR(12)  NOT NULL,
    country     VARCHAR(2)   NOT NULL DEFAULT 'IN',
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  DATETIME     NOT NULL,
    updated_at  DATETIME     NOT NULL,
    PRIMARY KEY (id),
    KEY idx_addresses_user (user_id),
    CONSTRAINT fk_addresses_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE refresh_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(100) NOT NULL,
    expires_at DATETIME     NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_tokens_hash (token_hash),
    KEY idx_refresh_tokens_user (user_id),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;

CREATE TABLE password_reset_tokens (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(100) NOT NULL,
    expires_at DATETIME     NOT NULL,
    used       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at DATETIME     NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_password_reset_tokens_hash (token_hash),
    KEY idx_password_reset_tokens_user (user_id),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
