package com.shopMeta.cart_service.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One line of a cart: a product and how many units of it the user wants.
 *
 * <p>Deliberately minimal. The cart stores only the identity of the line ({@code productId},
 * {@code quantity}) plus the price at the moment the line was added ({@code addedPrice}), so the UI
 * can flag "price changed since you added this". Everything shown to the user — name, image, live
 * price, stock — is fetched from product-service / inventory-service when the cart is rendered
 * (spec 10.2), so a line can never go stale in the database.
 *
 * <p>Uniqueness of {@code (cart_id, product_id)} is what makes a concurrent double-add safe: the
 * second insert fails and the caller retries onto the update path (see {@code CartStore.addItem}).
 */
@Entity
@Table(name = "cart_items")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    @Setter(AccessLevel.NONE)
    private Cart cart;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private int quantity;

    /** Catalogue price when the line was added — kept only to detect a later price change. */
    @Column(name = "added_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal addedPrice;

    /** When the line was created; stays fixed for the life of the line. */
    @Column(name = "added_at", nullable = false)
    private LocalDateTime addedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.addedAt == null) {
            this.addedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
