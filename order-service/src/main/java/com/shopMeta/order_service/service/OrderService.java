package com.shopMeta.order_service.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.shopMeta.order_service.client.dto.AddressSnapshot;
import com.shopMeta.order_service.client.dto.CartLine;
import com.shopMeta.order_service.client.dto.CartValidationView;
import com.shopMeta.order_service.client.dto.CartView;
import com.shopMeta.order_service.client.dto.OrderReserveRequest;
import com.shopMeta.order_service.client.dto.ProductSnapshot;
import com.shopMeta.order_service.config.OrderProperties;
import com.shopMeta.order_service.dto.CreateOrderRequest;
import com.shopMeta.order_service.dto.OrderAddressResponse;
import com.shopMeta.order_service.dto.OrderItemResponse;
import com.shopMeta.order_service.dto.OrderPageResponse;
import com.shopMeta.order_service.dto.OrderResponse;
import com.shopMeta.order_service.entity.Order;
import com.shopMeta.order_service.entity.OrderItem;
import com.shopMeta.order_service.entity.OrderStatus;
import com.shopMeta.order_service.entity.PaymentStatus;
import com.shopMeta.order_service.exception.BusinessRuleException;
import com.shopMeta.order_service.exception.CheckoutBlockedException;
import com.shopMeta.order_service.exception.DownstreamServiceException;
import com.shopMeta.order_service.exception.InsufficientStockException;
import com.shopMeta.order_service.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * The order lifecycle (spec 11).
 *
 * <p><strong>Checkout</strong> is a saga with downstream reads first, one write, then the stock
 * hold — no Feign call inside a transaction (D-064):
 *
 * <ol>
 *   <li>read the buyer's cart and validate it, under the buyer's own JWT (D-061);</li>
 *   <li>freeze the product snapshots (price/sku/name/image) for every line;</li>
 *   <li>snapshot the shipping address from auth-service;</li>
 *   <li>persist the order as {@code CREATED} with the money snapshot;</li>
 *   <li>reserve stock for the new {@code orderId} (idempotent, D-053). If the reserve fails, the
 *       order row is compensated to {@code CANCELLED} and the business answer (409) or the outage
 *       (503) is rethrown.</li>
 * </ol>
 *
 * <p><strong>Payment results</strong> arrive from payment-service on the internal endpoints: success
 * confirms the stock (holds become sales), empties the cart and marks the order {@code CONFIRMED};
 * failure releases the stock and cancels the order. Transitions are guarded by the
 * {@code @Version} column, so a replayed callback cannot double-confirm.
 *
 * <p>Cancellation releases stock <em>before</em> the status write: if the release succeeds and the
 * status write fails, a retry finds the order still cancellable — the reverse order would strand
 * the reservation behind a CANCELLED status nobody may touch again.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final DownstreamClients downstreamClients;
    private final OrderStore orderStore;
    private final OrderProperties orderProperties;

    // ------------------------------------------------------------------ checkout

    public OrderResponse createOrder(Long userId, CreateOrderRequest request) {

        // 1+2. The buyer's cart, read and validated as the buyer (their JWT is forwarded).
        CartView cart = downstreamClients.loadCart();
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new BusinessRuleException("Your cart is empty");
        }

        CartValidationView validation = downstreamClients.validateCart();
        if (validation != null && !validation.isValid()) {
            throw new CheckoutBlockedException(
                    "Your cart cannot be checked out right now", validation.getIssues());
        }

        // 3. Freeze the product snapshot for every line.
        List<CartLine> lines = cart.getItems();
        List<Long> productIds = lines.stream().map(CartLine::getProductId).distinct().toList();

        Map<Long, ProductSnapshot> products = downstreamClients.fetchProducts(productIds);

        List<Long> missing = productIds.stream()
                .filter(productId -> {
                    ProductSnapshot product = products.get(productId);
                    return product == null || !Boolean.TRUE.equals(product.getActive());
                })
                .toList();
        if (!missing.isEmpty()) {
            // The cart validated moments ago — this is catalogue drift between the two calls.
            throw new BusinessRuleException(
                    "Some items in your cart are no longer available and were removed "
                            + "from the catalogue");
        }

        // 4. Snapshot the shipping address.
        AddressSnapshot address = downstreamClients.fetchAddress(
                userId, request.getShippingAddressId());

        // 5. Persist the CREATED order with the money snapshot.
        Order order = orderStore.create(buildOrder(userId, lines, products, address));

        // 6. Hold the stock for the new orderId.
        try {
            downstreamClients.reserveStock(order.getId(), buildReserveRequest(order, lines));
        } catch (InsufficientStockException | DownstreamServiceException ex) {
            // Compensate: the order must not linger as CREATED without a reservation.
            log.warn("Checkout of order {} failed ({}, compensating to CANCELLED)",
                    order.getId(), ex.getMessage());
            orderStore.cancel(order);
            throw ex;
        }

        log.info("Order {} created for user {} ({} line(s), total {})",
                order.getId(), userId, lines.size(), order.getTotal());

        return toResponse(order);
    }

    // ------------------------------------------------------------------ reads

    public OrderResponse getOrder(Long callerId, Long orderId, boolean isAdmin) {

        // A customer asking for someone else's order gets the same 404 as a missing one —
        // the ids cannot be probed (spec 18).
        Order order = (isAdmin
                ? orderStore.findWithItems(orderId)
                : orderStore.findWithItemsForUser(orderId, callerId))
                .orElseThrow(() ->
                        new ResourceNotFoundException("Order " + orderId + " was not found"));

        return toResponse(order);
    }

    public OrderPageResponse listMyOrders(Long userId, Integer page, Integer size) {

        Page<Order> orders = orderStore.pageByUser(userId,
                pageable(page, size));
        return toPageResponse(orders);
    }

    public OrderPageResponse listAllOrders(OrderStatus status, Integer page, Integer size) {

        Page<Order> orders = orderStore.pageByStatus(status, pageable(page, size));
        return toPageResponse(orders);
    }

    // ------------------------------------------------------------------ customer cancel

    public OrderResponse cancelByOwner(Long userId, Long orderId) {

        Order order = orderStore.findWithItemsForUser(orderId, userId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Order " + orderId + " was not found"));

        // Only an unpaid order can be cancelled by its owner: stock is still RESERVED (releasable)
        // and no refund question exists yet. Paid orders go through support/payment-service.
        requireCancellable(order);

        // Release first (idempotent), then write the status — see the class comment.
        downstreamClients.releaseStock(orderId);

        order = orderStore.cancel(order);
        log.info("Order {} cancelled by its owner {}", orderId, userId);

        return toResponse(order);
    }

    // ------------------------------------------------------------------ admin

    public OrderResponse updateStatus(Long orderId, OrderStatus target) {

        Order order = orderStore.findWithItems(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Order " + orderId + " was not found"));

        switch (target) {
            case SHIPPED -> requireStatus(order, OrderStatus.CONFIRMED,
                    "Only confirmed orders can be shipped");
            case DELIVERED -> requireStatus(order, OrderStatus.SHIPPED,
                    "Only shipped orders can be delivered");
            case CANCELLED -> {
                requireCancellable(order);
                downstreamClients.releaseStock(orderId);
            }
            // CONFIRMED is payment's job; CREATED is where every order starts.
            default -> throw new BusinessRuleException(
                    "Status " + target + " cannot be set directly: orders are confirmed by "
                            + "their payment, not by an admin");
        }

        order.setStatus(target);
        order = orderStore.save(order);
        log.info("Order {} moved to {} by an admin", orderId, target);

        return toResponse(order);
    }

    // ------------------------------------------------------------------ payment callbacks (internal)

    public OrderResponse paymentConfirmed(Long orderId) {

        Order order = orderStore.findWithItems(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Order " + orderId + " was not found"));

        // Idempotent replay of the success callback.
        if (order.getStatus() == OrderStatus.CONFIRMED
                && order.getPaymentStatus() == PaymentStatus.PAID) {
            return toResponse(order);
        }

        requireStatus(order, OrderStatus.CREATED,
                "Only orders waiting for payment can be confirmed");

        // Holds become sales. If the reservation expired while the payment was in flight this
        // throws BusinessRuleException (422) — a real conflict payment-service must report back
        // to the customer, not swallow.
        downstreamClients.confirmStock(orderId);

        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentStatus(PaymentStatus.PAID);
        order = orderStore.save(order);

        // Best effort: the customer's cart is only emptied now that payment truly succeeded
        // (spec 10.3). A cart-service hiccup must not fail an already-confirmed payment — the
        // buyer can clear the cart themselves, the order is safe.
        try {
            downstreamClients.clearCart(order.getUserId());
        } catch (DownstreamServiceException ex) {
            log.error("Order {} confirmed but its cart could not be cleared for user {}: {}",
                    orderId, order.getUserId(), ex.getMessage());
        }

        log.info("Order {} confirmed (payment succeeded)", orderId);
        return toResponse(order);
    }

    public OrderResponse paymentFailed(Long orderId) {

        Order order = orderStore.findWithItems(orderId)
                .orElseThrow(() ->
                        new ResourceNotFoundException("Order " + orderId + " was not found"));

        // Idempotent replay of the failure callback.
        if (order.getStatus() == OrderStatus.CANCELLED
                && order.getPaymentStatus() == PaymentStatus.FAILED) {
            return toResponse(order);
        }

        requireStatus(order, OrderStatus.CREATED,
                "Only orders waiting for payment can be failed");

        // Release the holds first — see the class comment.
        downstreamClients.releaseStock(orderId);

        order.setStatus(OrderStatus.CANCELLED);
        order.setPaymentStatus(PaymentStatus.FAILED);
        order = orderStore.save(order);

        log.info("Order {} cancelled (payment failed)", orderId);
        return toResponse(order);
    }

    // ------------------------------------------------------------------ building

    private Order buildOrder(Long userId, List<CartLine> lines,
            Map<Long, ProductSnapshot> products, AddressSnapshot address) {

        BigDecimal subtotal = BigDecimal.ZERO;

        Order order = Order.builder()
                .userId(userId)
                .status(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING)
                .shippingFee(BigDecimal.ZERO)
                .total(BigDecimal.ZERO)
                .fullName(address.getFullName())
                .phone(address.getPhone())
                .line1(address.getLine1())
                .line2(address.getLine2())
                .city(address.getCity())
                .state(address.getState())
                .postalCode(address.getPostalCode())
                .country(address.getCountry())
                .build();

        for (CartLine line : lines) {
            ProductSnapshot product = products.get(line.getProductId());

            OrderItem item = OrderItem.builder()
                    .productId(product.getId())
                    .sku(product.getSku())
                    .name(product.getName())
                    .imageUrl(product.getImageUrl())
                    .unitPrice(product.getPrice())
                    .quantity(line.getQuantity())
                    .build();
            order.addItem(item);

            subtotal = subtotal.add(item.lineTotal());
        }

        BigDecimal shippingFee = subtotal.compareTo(orderProperties.getFreeShippingThreshold()) >= 0
                ? BigDecimal.ZERO
                : orderProperties.getShippingFee();

        order.setSubtotal(subtotal);
        order.setShippingFee(shippingFee);
        order.setTotal(subtotal.add(shippingFee));

        return order;
    }

    private static OrderReserveRequest buildReserveRequest(Order order, List<CartLine> lines) {

        List<OrderReserveRequest.ReservationLine> reservationLines = new ArrayList<>();
        lines.forEach(line -> reservationLines.add(new OrderReserveRequest.ReservationLine(
                line.getProductId(), line.getQuantity())));

        return new OrderReserveRequest(order.getId(), reservationLines);
    }

    private OrderResponse toResponse(Order order) {

        List<OrderItemResponse> items = order.getItems().stream()
                .map(item -> OrderItemResponse.builder()
                        .id(item.getId())
                        .productId(item.getProductId())
                        .sku(item.getSku())
                        .name(item.getName())
                        .imageUrl(item.getImageUrl())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .lineTotal(item.lineTotal())
                        .build())
                .toList();

        return OrderResponse.builder()
                .id(order.getId())
                .userId(order.getUserId())
                .status(order.getStatus())
                .paymentStatus(order.getPaymentStatus())
                .items(items)
                .subtotal(order.getSubtotal())
                .shippingFee(order.getShippingFee())
                .total(order.getTotal())
                .shippingAddress(OrderAddressResponse.builder()
                        .fullName(order.getFullName())
                        .phone(order.getPhone())
                        .line1(order.getLine1())
                        .line2(order.getLine2())
                        .city(order.getCity())
                        .state(order.getState())
                        .postalCode(order.getPostalCode())
                        .country(order.getCountry())
                        .build())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }

    private OrderPageResponse toPageResponse(Page<Order> page) {

        List<OrderResponse> content = page.getContent().stream()
                .map(this::toResponse)
                .toList();

        return OrderPageResponse.builder()
                .content(content)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }

    // ------------------------------------------------------------------ rules

    /** An order is cancellable while CREATED: stock still held, payment never reported. */
    private static void requireCancellable(Order order) {

        requireStatus(order, OrderStatus.CREATED,
                "Order " + order.getId() + " is " + order.getStatus() + " and can no longer "
                        + "be cancelled here"
                        + (order.getStatus() == OrderStatus.CONFIRMED
                                ? " — paid orders are refunded through payment-service"
                                : ""));
    }

    private static void requireStatus(Order order, OrderStatus expected, String message) {

        if (order.getStatus() != expected) {
            throw new BusinessRuleException(message);
        }
    }

    private static Pageable pageable(Integer page, Integer size) {

        int pageNumber = page == null ? 0 : Math.max(0, page);
        int pageSize = size == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(MAX_PAGE_SIZE, Math.max(1, size));

        return PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }
}
