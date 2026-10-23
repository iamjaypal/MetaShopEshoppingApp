-- =====================================================================
-- MetaShop - product-service
-- V1 : initial catalog schema (spec section 8.2)
-- MySQL 8, InnoDB, utf8mb4
-- =====================================================================

-- ---------------------------------------------------------------------
-- categories (hierarchical)
-- ---------------------------------------------------------------------
CREATE TABLE categories (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(120) NOT NULL,
    description VARCHAR(500) NULL,
    parent_id   BIGINT       NULL,
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  DATETIME(6)  NOT NULL,
    updated_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_categories_slug (slug),
    KEY idx_categories_parent (parent_id),
    CONSTRAINT fk_categories_parent
        FOREIGN KEY (parent_id) REFERENCES categories (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- brands
-- ---------------------------------------------------------------------
CREATE TABLE brands (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    name       VARCHAR(100) NOT NULL,
    slug       VARCHAR(120) NOT NULL,
    logo_url   VARCHAR(500) NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_brands_name (name),
    UNIQUE KEY uk_brands_slug (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- products
-- ---------------------------------------------------------------------
CREATE TABLE products (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    sku               VARCHAR(50)   NOT NULL,
    name              VARCHAR(200)  NOT NULL,
    slug              VARCHAR(220)  NULL,
    short_description VARCHAR(500)  NULL,
    description       TEXT          NULL,
    price             DECIMAL(12,2) NOT NULL,
    mrp               DECIMAL(12,2) NULL,
    currency          CHAR(3)       NOT NULL DEFAULT 'INR',
    category_id       BIGINT        NULL,
    brand_id          BIGINT        NULL,
    active            BOOLEAN       NOT NULL DEFAULT TRUE,
    average_rating    DECIMAL(2,1)  NOT NULL DEFAULT 0.0,
    rating_count      INT           NOT NULL DEFAULT 0,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_products_sku (sku),
    UNIQUE KEY uk_products_slug (slug),
    KEY idx_products_category_active (category_id, active),
    KEY idx_products_brand (brand_id),
    KEY idx_products_price (price),
    FULLTEXT KEY ft_products_name_short (name, short_description),
    CONSTRAINT fk_products_category
        FOREIGN KEY (category_id) REFERENCES categories (id),
    CONSTRAINT fk_products_brand
        FOREIGN KEY (brand_id) REFERENCES brands (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- product_images
-- ---------------------------------------------------------------------
CREATE TABLE product_images (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    product_id BIGINT       NOT NULL,
    url        VARCHAR(500) NOT NULL,
    alt_text   VARCHAR(255) NULL,
    sort_order INT          NOT NULL DEFAULT 0,
    is_primary BOOLEAN      NOT NULL DEFAULT FALSE,
    PRIMARY KEY (id),
    KEY idx_product_images_product (product_id),
    CONSTRAINT fk_product_images_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- product_attributes  (e.g. RAM = 8GB)
-- ---------------------------------------------------------------------
CREATE TABLE product_attributes (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    product_id BIGINT       NOT NULL,
    attr_key   VARCHAR(80)  NOT NULL,
    attr_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_product_attributes_product (product_id),
    CONSTRAINT fk_product_attributes_product
        FOREIGN KEY (product_id) REFERENCES products (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;
