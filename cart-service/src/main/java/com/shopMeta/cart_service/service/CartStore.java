package com.shopMeta.cart_service.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopMeta.cart_service.entity.Cart;
import com.shopMeta.cart_service.entity.CartItem;
import com.shopMeta.cart_service.exception.ResourceNotFoundException;
import com.shopMeta.cart_service.repository.CartItemRepository;
import com.shopMeta.cart_service.repository.CartRepository;

import lombok.RequiredArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The persistence layer of the cart, and the only place that opens a transaction.
 *
 * <p>Kept apart from {@link CartService} so that business rules can consult the catalogue
 * <em>before</em> a transaction is opened: a Feign call must never run while a database connection
 * is held (spec 5.10).
 */
@Service
@RequiredArgsConstructor
public class CartStore {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;

    /** The user's cart as stored, or {@code null} when they have never added anything. */
    @Transactional(readOnly = true)
    public CartData load(Long userId) {

        return cartRepository.findByUserId(userId)
                .map(cart -> new CartData(
                        cart.getId(),
                        cart.getUserId(),
                        cart.getUpdatedAt(),
                        cartItemRepository.findByCartIdOrderByIdAsc(cart.getId()).stream()
                                .map(CartStore::toLine)
                                .toList()))
                .orElse(null);
    }

    /**
     * Adds quantity to a line, creating the cart and/or the line on first use.
     *
     * @throws org.springframework.dao.DataIntegrityViolationException when a concurrent request
     *         created the cart or the line first — the unique keys are what make that impossible to
     *         get wrong, and the caller simply retries once and lands on the update branch
     */
    @Transactional
    public void addItem(Long userId, Long productId, int quantity, BigDecimal addedPrice) {

        Cart cart = cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.saveAndFlush(Cart.builder().userId(userId).build()));

        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElse(null);

        if (item == null) {
            cartItemRepository.saveAndFlush(CartItem.builder()
                    .cart(cart)
                    .productId(productId)
                    .quantity(quantity)
                    .addedPrice(addedPrice)
                    .build());
        } else {
            item.setQuantity(item.getQuantity() + quantity);
            cartItemRepository.save(item);
        }

        touch(cart);
    }

    /** Sets a line to an absolute quantity. The write is scoped to the cart, so it cannot miss. */
    @Transactional
    public void updateQuantity(Long cartId, Long itemId, int quantity) {

        int updated = cartItemRepository.updateQuantity(itemId, cartId, quantity, LocalDateTime.now());

        if (updated == 0) {
            throw new ResourceNotFoundException(
                    "Cart item " + itemId + " was not found in your cart");
        }

        touch(cartId);
    }

    @Transactional
    public void removeItem(Long cartId, Long itemId) {

        int deleted = cartItemRepository.deleteByIdAndCartId(itemId, cartId);

        if (deleted == 0) {
            throw new ResourceNotFoundException(
                    "Cart item " + itemId + " was not found in your cart");
        }

        touch(cartId);
    }

    /**
     * Empties a cart. Idempotent: clearing an already empty cart deletes nothing and succeeds, which
     * is what order-service needs when it replays a confirmation (spec 10.3).
     *
     * @return how many lines were removed
     */
    @Transactional
    public int clearItems(Long cartId) {

        int deleted = cartItemRepository.deleteAllByCartId(cartId);

        if (deleted > 0) {
            touch(cartId);
        }

        return deleted;
    }

    private void touch(Cart cart) {
        cart.setUpdatedAt(LocalDateTime.now());
        cartRepository.save(cart);
    }

    private void touch(Long cartId) {
        cartRepository.findById(cartId).ifPresent(this::touch);
    }

    private static CartData.Line toLine(CartItem item) {
        return new CartData.Line(
                item.getId(),
                item.getProductId(),
                item.getQuantity(),
                item.getAddedPrice(),
                item.getAddedAt());
    }
}
