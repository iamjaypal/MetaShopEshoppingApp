package com.shopMeta.inventory_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * Stock row for one product (spec 9.2).
 *
 * <p>{@code availableQuantity} is sellable stock, {@code reservedQuantity} is stock held by a
 * {@code RESERVED} reservation. {@code version} is the JPA optimistic-lock counter; the atomic
 * reserve statement bumps it too (spec 9.4).
 */
@Entity
@Table(name = "inventory")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(length = 50)
    private String sku;

    @Column(name = "available_quantity", nullable = false)
    @Builder.Default
    private Integer availableQuantity = 0;

    @Column(name = "reserved_quantity", nullable = false)
    @Builder.Default
    private Integer reservedQuantity = 0;

    @Column(name = "low_stock_threshold", nullable = false)
    @Builder.Default
    private Integer lowStockThreshold = 5;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /** Sellable right now. */
    public boolean isInStock() {
        return availableQuantity != null && availableQuantity > 0;
    }

    public boolean isBelowThreshold() {
        return availableQuantity != null && availableQuantity <= lowStockThreshold;
    }

}
