package com.shopMeta.inventory_service.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.shopMeta.inventory_service.entity.Inventory;

/**
 * Stock rows (spec 9.2) plus the three atomic counter transitions (spec 9.4).
 *
 * <p>The transitions are single conditional {@code UPDATE} statements executed by the database. That
 * is the whole overselling defence: two concurrent transactions cannot both succeed, because the
 * second one finds {@code available_quantity < :quantity} and updates zero rows. No application-level
 * lock, no read-then-write race.
 *
 * <p>Each statement also bumps {@code version} by hand. The native statements bypass Hibernate's
 * {@code @Version} handling, so bumping it here keeps the column truthful and makes any concurrent
 * JPA-managed write (the admin update) fail its optimistic-lock check instead of silently winning.
 */
@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    /**
     * Holds {@code quantity} units for an order: moves them from available to reserved.
     *
     * @return 1 when the hold was taken, 0 when there was not enough stock
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE inventory
               SET available_quantity = available_quantity - :quantity,
                   reserved_quantity  = reserved_quantity + :quantity,
                   version            = version + 1,
                   updated_at         = :now
             WHERE product_id = :productId
               AND available_quantity >= :quantity
            """, nativeQuery = true)
    int reserve(@Param("productId") Long productId,
                @Param("quantity") int quantity,
                @Param("now") LocalDateTime now);

    /**
     * Returns a hold to available stock (payment failed, order cancelled, reservation expired).
     *
     * @return 1 when the hold was returned, 0 when there was no matching reserved stock
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE inventory
               SET available_quantity = available_quantity + :quantity,
                   reserved_quantity  = reserved_quantity - :quantity,
                   version            = version + 1,
                   updated_at         = :now
             WHERE product_id = :productId
               AND reserved_quantity >= :quantity
            """, nativeQuery = true)
    int release(@Param("productId") Long productId,
                @Param("quantity") int quantity,
                @Param("now") LocalDateTime now);

    /**
     * Turns a hold into a sale (payment succeeded): the units leave reserved stock for good.
     *
     * @return 1 when the hold was confirmed, 0 when there was no matching reserved stock
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            UPDATE inventory
               SET reserved_quantity = reserved_quantity - :quantity,
                   version           = version + 1,
                   updated_at        = :now
             WHERE product_id = :productId
               AND reserved_quantity >= :quantity
            """, nativeQuery = true)
    int confirm(@Param("productId") Long productId,
                @Param("quantity") int quantity,
                @Param("now") LocalDateTime now);

    /** Products at or below their own configured threshold (spec 9.3). */
    @Query("""
            SELECT i FROM Inventory i
             WHERE i.availableQuantity <= i.lowStockThreshold
             ORDER BY i.availableQuantity ASC, i.productId ASC
            """)
    Page<Inventory> findLowStock(Pageable pageable);

    /** Products at or below an explicit threshold, overriding the per-row one (spec 9.3). */
    @Query("""
            SELECT i FROM Inventory i
             WHERE i.availableQuantity <= :threshold
             ORDER BY i.availableQuantity ASC, i.productId ASC
            """)
    Page<Inventory> findLowStockBelow(@Param("threshold") int threshold, Pageable pageable);
}
