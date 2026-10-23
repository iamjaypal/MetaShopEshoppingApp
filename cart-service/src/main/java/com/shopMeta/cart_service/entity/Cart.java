package com.shopMeta.cart_service.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One shopping cart per user (unique {@code user_id}). Created lazily by the first mutation; a GET
 * for a user without a cart returns an empty cart and creates nothing.
 *
 * <p>The cart carries no lines: {@code CartItem} rows point at it via {@code cart_id}, and the
 * store reads them with its own queries. The unique key on {@code (cart_id, product_id)} is what
 * makes a concurrent double-add safe.
 *
 * <p>{@code version} is a JPA {@code @Version} column: two concurrent mutations of the same cart row
 * make the loser fail with an optimistic-lock exception (mapped to a retryable 409) instead of
 * silently losing a change.
 */
@Entity
@Table(name = "carts")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Cart {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
