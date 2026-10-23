package com.shopMeta.order_service.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopMeta.order_service.entity.Order;
import com.shopMeta.order_service.entity.OrderItem;
import com.shopMeta.order_service.entity.OrderStatus;
import com.shopMeta.order_service.repository.OrderItemRepository;
import com.shopMeta.order_service.repository.OrderRepository;

import lombok.RequiredArgsConstructor;

/**
 * Every database write of order-service lives here (the cart-service {@code CartStore} pattern):
 * short, focused transactions, while {@link OrderService} does the orchestration and the
 * downstream calls <em>outside</em> any transaction (no Feign inside a TX, D-064).
 *
 * <p>List results hydrate the lazy {@code items} collection with one batched query, so building
 * responses outside the transaction cannot hit a lazy-init exception.
 */
@Service
@RequiredArgsConstructor
public class OrderStore {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    /** Persists the order with its lines (cascade). */
    @Transactional
    public Order create(Order order) {
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findWithItems(Long id) {
        return orderRepository.findWithItemsById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Order> findWithItemsForUser(Long id, Long userId) {
        return orderRepository.findWithItemsByIdAndUserId(id, userId);
    }

    @Transactional(readOnly = true)
    public Page<Order> pageByUser(Long userId, Pageable pageable) {

        Page<Order> page = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return hydrate(page);
    }

    @Transactional(readOnly = true)
    public Page<Order> pageByStatus(OrderStatus status, Pageable pageable) {

        Page<Order> page = (status == null)
                ? orderRepository.findAllByOrderByCreatedAtDesc(pageable)
                : orderRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        return hydrate(page);
    }

    /**
     * Persists a status transition. The {@code @Version} column makes two racing writers (a
     * payment callback and an admin update) fail the loser's merge with an optimistic-lock
     * exception instead of double-transitioning.
     */
    @Transactional
    public Order save(Order order) {
        return orderRepository.save(order);
    }

    /** Marks an order CANCELLED — the compensation for a failed checkout or a cancellation. */
    @Transactional
    public Order cancel(Order order) {

        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    // ------------------------------------------------------------------ hydration

    private Page<Order> hydrate(Page<Order> page) {

        List<Order> orders = page.getContent();
        if (orders.isEmpty()) {
            return page;
        }

        List<Long> orderIds = orders.stream().map(Order::getId).toList();

        Map<Long, List<OrderItem>> itemsByOrderId = orderItemRepository
                .findByOrder_IdIn(orderIds).stream()
                .collect(Collectors.groupingBy(item -> item.getOrder().getId()));

        orders.forEach(order -> {
            order.getItems().clear();
            itemsByOrderId.getOrDefault(order.getId(), List.of())
                    .forEach(order::addItem);
        });

        return page;
    }
}
