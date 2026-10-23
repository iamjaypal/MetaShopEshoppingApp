package com.shopMeta.cart_service.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.shopMeta.cart_service.entity.CartItem;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    List<CartItem> findByCartIdOrderByIdAsc(Long cartId);

    Optional<CartItem> findByCartIdAndProductId(Long cartId, Long productId);

    /**
     * Quantity change scoped to a cart. Scoping the write by {@code cart_id} in SQL means a caller
     * can never touch another user's line: a foreign itemId simply matches no row.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update CartItem ci
               set ci.quantity = :quantity,
                   ci.updatedAt = :now
             where ci.id = :itemId
               and ci.cart.id = :cartId
            """)
    int updateQuantity(
            @Param("itemId") Long itemId,
            @Param("cartId") Long cartId,
            @Param("quantity") int quantity,
            @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CartItem ci where ci.id = :itemId and ci.cart.id = :cartId")
    int deleteByIdAndCartId(@Param("itemId") Long itemId, @Param("cartId") Long cartId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from CartItem ci where ci.cart.id = :cartId")
    int deleteAllByCartId(@Param("cartId") Long cartId);
}
