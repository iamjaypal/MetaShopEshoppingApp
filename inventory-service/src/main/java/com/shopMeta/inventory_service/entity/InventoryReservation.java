package com.shopMeta.inventory_service.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

/**
 * One line of an order holding stock (spec 9.2 / 9.4).
 *
 * <p>The unique key on {@code (order_id, product_id)} is what makes reserving idempotent per order:
 * a retried reserve cannot create a second hold for the same product.
 */
@Entity
@Table(
        name = "inventory_reservations",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_reservations_order_product",
                columnNames = { "order_id", "product_id" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryReservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservationStatus status;

    /** When a still-RESERVED hold becomes stale and the sweeper releases it. */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

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

}
