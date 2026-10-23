package com.shopMeta.order_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.shopMeta.order_service.client.dto.AddressSnapshot;
import com.shopMeta.order_service.client.dto.CartIssueView;
import com.shopMeta.order_service.client.dto.CartLine;
import com.shopMeta.order_service.client.dto.CartValidationView;
import com.shopMeta.order_service.client.dto.CartView;
import com.shopMeta.order_service.client.dto.OrderReserveRequest;
import com.shopMeta.order_service.client.dto.ProductSnapshot;
import com.shopMeta.order_service.config.OrderProperties;
import com.shopMeta.order_service.dto.CreateOrderRequest;
import com.shopMeta.order_service.dto.OrderResponse;
import com.shopMeta.order_service.entity.Order;
import com.shopMeta.order_service.entity.OrderStatus;
import com.shopMeta.order_service.entity.PaymentStatus;
import com.shopMeta.order_service.exception.BusinessRuleException;
import com.shopMeta.order_service.exception.CheckoutBlockedException;
import com.shopMeta.order_service.exception.DownstreamServiceException;
import com.shopMeta.order_service.exception.InsufficientStockException;
import com.shopMeta.order_service.exception.ResourceNotFoundException;

/**
 * Unit tests of the order business rules: the checkout saga (cart read → validation → snapshots →
 * persist → reserve, with compensation), ownership, the lifecycle transitions and the payment
 * callbacks with their idempotent replays.
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ADDRESS_ID = 9L;
    private static final Long PRODUCT_A = 50L;
    private static final Long PRODUCT_B = 51L;

    @Mock
    private DownstreamClients downstreamClients;

    @Mock
    private OrderStore orderStore;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(downstreamClients, orderStore, new OrderProperties());
    }

    // ------------------------------------------------------------------ fixtures

    private CartLine line(Long productId, int quantity) {
        CartLine line = new CartLine();
        line.setItemId(productId * 10);
        line.setProductId(productId);
        line.setQuantity(quantity);
        return line;
    }

    private CartView cart(CartLine... lines) {
        CartView view = new CartView();
        view.setCartId(3L);
        view.setUserId(USER_ID);
        view.setItems(List.of(lines));
        return view;
    }

    private ProductSnapshot product(Long id, String price, boolean active) {
        return ProductSnapshot.builder()
                .id(id)
                .sku("SKU-" + id)
                .name("Product " + id)
                .price(price == null ? null : new BigDecimal(price))
                .imageUrl("/uploads/products/" + id + ".png")
                .active(active)
                .build();
    }

    private AddressSnapshot address() {
        AddressSnapshot snapshot = new AddressSnapshot();
        snapshot.setId(ADDRESS_ID);
        snapshot.setFullName("Jay Pal");
        snapshot.setPhone("9876543210");
        snapshot.setLine1("12 Palm Road");
        snapshot.setLine2("Near Park");
        snapshot.setCity("Surat");
        snapshot.setState("Gujarat");
        snapshot.setPostalCode("395007");
        snapshot.setCountry("IN");
        return snapshot;
    }

    private CartValidationView validCart() {
        CartValidationView view = new CartValidationView();
        view.setValid(true);
        view.setItemCount(2);
        view.setIssues(List.of());
        return view;
    }

    private CreateOrderRequest createRequest() {
        return new CreateOrderRequest(ADDRESS_ID);
    }

    /** The store assigns the id on save, like the identity column would. */
    private void storeAssignsId() {
        given(orderStore.create(any(Order.class))).willAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId(100L);
            return order;
        });
    }

    private void givenHappyCatalogue() {
        given(downstreamClients.loadCart()).willReturn(cart(
                line(PRODUCT_A, 2), line(PRODUCT_B, 1)));
        given(downstreamClients.validateCart()).willReturn(validCart());
        given(downstreamClients.fetchProducts(any()))
                .willReturn(Map.of(
                        PRODUCT_A, product(PRODUCT_A, "100.00", true),
                        PRODUCT_B, product(PRODUCT_B, "200.00", true)));
        given(downstreamClients.fetchAddress(USER_ID, ADDRESS_ID)).willReturn(address());
        storeAssignsId();
    }

    // ------------------------------------------------------------------ checkout

    @Test
    @DisplayName("createOrder: the happy path freezes prices, address and money, then reserves")
    void createOrder_happyPath() {

        givenHappyCatalogue();

        OrderResponse response = orderService.createOrder(USER_ID, createRequest());

        // The persisted order carried the frozen money snapshot (2×100 + 1×200 = 400 < 499).
        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        then(orderStore).should().create(orderCaptor.capture());
        Order created = orderCaptor.getValue();

        assertThat(created.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(created.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(created.getSubtotal()).isEqualByComparingTo("400.00");
        assertThat(created.getShippingFee()).isEqualByComparingTo("49.00");
        assertThat(created.getTotal()).isEqualByComparingTo("449.00");
        assertThat(created.getFullName()).isEqualTo("Jay Pal");
        assertThat(created.getCity()).isEqualTo("Surat");
        assertThat(created.getItems()).hasSize(2);
        assertThat(created.getItems().get(0).getProductId()).isEqualTo(PRODUCT_A);
        assertThat(created.getItems().get(0).getSku()).isEqualTo("SKU-50");
        assertThat(created.getItems().get(0).lineTotal()).isEqualByComparingTo("200.00");

        // The reservation was made for the new order id with the cart's lines.
        ArgumentCaptor<OrderReserveRequest> reserveCaptor =
                ArgumentCaptor.forClass(OrderReserveRequest.class);
        then(downstreamClients).should().reserveStock(eq(100L), reserveCaptor.capture());
        assertThat(reserveCaptor.getValue().getOrderId()).isEqualTo(100L);
        assertThat(reserveCaptor.getValue().getItems()).hasSize(2);

        // The response is the CREATED order.
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(response.getItems()).hasSize(2);
        assertThat(response.getShippingAddress().getPostalCode()).isEqualTo("395007");
    }

    @Test
    @DisplayName("createOrder: a subtotal at or above the threshold ships free")
    void createOrder_freeShipping() {

        given(downstreamClients.loadCart()).willReturn(cart(line(PRODUCT_A, 6)));
        given(downstreamClients.validateCart()).willReturn(validCart());
        given(downstreamClients.fetchProducts(any()))
                .willReturn(Map.of(PRODUCT_A, product(PRODUCT_A, "100.00", true)));
        given(downstreamClients.fetchAddress(USER_ID, ADDRESS_ID)).willReturn(address());
        storeAssignsId();

        orderService.createOrder(USER_ID, createRequest());

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        then(orderStore).should().create(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getSubtotal()).isEqualByComparingTo("600.00");
        assertThat(orderCaptor.getValue().getShippingFee()).isEqualByComparingTo("0.00");
        assertThat(orderCaptor.getValue().getTotal()).isEqualByComparingTo("600.00");
    }

    @Test
    @DisplayName("createOrder: an empty cart is a 422 and touches nothing else")
    void createOrder_emptyCart() {

        given(downstreamClients.loadCart()).willReturn(cart());

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, createRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("empty");

        then(downstreamClients).should(never()).fetchProducts(any());
        then(orderStore).should(never()).create(any());
        then(downstreamClients).should(never()).reserveStock(anyLong(), any());
    }

    @Test
    @DisplayName("createOrder: a cart that fails validation is blocked with the issues")
    void createOrder_invalidCart() {

        given(downstreamClients.loadCart()).willReturn(cart(line(PRODUCT_A, 2)));

        CartIssueView issue = new CartIssueView();
        issue.setItemId(50L * 10);
        issue.setProductId(PRODUCT_A);
        issue.setType("INSUFFICIENT_STOCK");
        issue.setMessage("Only 1 unit(s) of product 50 are available");
        CartValidationView invalid = new CartValidationView();
        invalid.setValid(false);
        invalid.setIssues(List.of(issue));
        given(downstreamClients.validateCart()).willReturn(invalid);

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, createRequest()))
                .isInstanceOf(CheckoutBlockedException.class)
                .hasMessageContaining("cannot be checked out");

        then(orderStore).should(never()).create(any());
        then(downstreamClients).should(never()).reserveStock(anyLong(), any());
    }

    @Test
    @DisplayName("createOrder: a product that vanished from the catalogue (drift) blocks checkout")
    void createOrder_missingProduct() {

        given(downstreamClients.loadCart()).willReturn(cart(
                line(PRODUCT_A, 2), line(PRODUCT_B, 1)));
        given(downstreamClients.validateCart()).willReturn(validCart());
        // The batch answer only knows about product A — product B was deleted in between.
        given(downstreamClients.fetchProducts(any()))
                .willReturn(Map.of(PRODUCT_A, product(PRODUCT_A, "100.00", true)));

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, createRequest()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("no longer available");

        then(orderStore).should(never()).create(any());
    }

    @Test
    @DisplayName("createOrder: a deactivated product blocks checkout")
    void createOrder_inactiveProduct() {

        given(downstreamClients.loadCart()).willReturn(cart(line(PRODUCT_A, 2)));
        given(downstreamClients.validateCart()).willReturn(validCart());
        given(downstreamClients.fetchProducts(any()))
                .willReturn(Map.of(PRODUCT_A, product(PRODUCT_A, "100.00", false)));

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, createRequest()))
                .isInstanceOf(BusinessRuleException.class);

        then(orderStore).should(never()).create(any());
    }

    @Test
    @DisplayName("createOrder: an address that is not the caller's is a 404, before any write")
    void createOrder_unknownAddress() {

        given(downstreamClients.loadCart()).willReturn(cart(line(PRODUCT_A, 2)));
        given(downstreamClients.validateCart()).willReturn(validCart());
        given(downstreamClients.fetchProducts(any()))
                .willReturn(Map.of(PRODUCT_A, product(PRODUCT_A, "100.00", true)));
        given(downstreamClients.fetchAddress(USER_ID, ADDRESS_ID))
                .willThrow(new ResourceNotFoundException("Shipping address 9 was not found"));

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, createRequest()))
                .isInstanceOf(ResourceNotFoundException.class);

        then(orderStore).should(never()).create(any());
    }

    @Test
    @DisplayName("createOrder: out of stock compensates the order to CANCELLED and rethrows the 409")
    void createOrder_insufficientStock() {

        givenHappyCatalogue();
        willThrow(new InsufficientStockException("Only 1 unit(s) of product 50 are available",
                List.of()))
                .given(downstreamClients).reserveStock(eq(100L), any());
        // The real store sets the status; mimic it so the assertion documents the intent.
        given(orderStore.cancel(any(Order.class))).willAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setStatus(OrderStatus.CANCELLED);
            return saved;
        });

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, createRequest()))
                .isInstanceOf(InsufficientStockException.class);

        // The compensation: the CREATED row does not linger without a reservation.
        ArgumentCaptor<Order> cancelled = ArgumentCaptor.forClass(Order.class);
        then(orderStore).should().cancel(cancelled.capture());
        assertThat(cancelled.getValue().getId()).isEqualTo(100L);
        assertThat(cancelled.getValue().getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("createOrder: a downstream outage at reserve time also compensates and rethrows 503")
    void createOrder_downstreamFailure() {

        givenHappyCatalogue();
        willThrow(new DownstreamServiceException(
                "Could not reach inventory-service. Please try again shortly.", null))
                .given(downstreamClients).reserveStock(eq(100L), any());
        given(orderStore.cancel(any(Order.class))).willAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setStatus(OrderStatus.CANCELLED);
            return saved;
        });

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, createRequest()))
                .isInstanceOf(DownstreamServiceException.class);

        then(orderStore).should().cancel(any(Order.class));
    }

    // ------------------------------------------------------------------ reads

    @Test
    @DisplayName("getOrder: an owner reads their own order")
    void getOrder_owner() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING).build();
        given(orderStore.findWithItemsForUser(100L, USER_ID)).willReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(USER_ID, 100L, false);

        assertThat(response.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("getOrder: someone else's order is the same 404 as a missing one")
    void getOrder_notYoursIsNotFound() {

        given(orderStore.findWithItemsForUser(100L, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(USER_ID, 100L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("getOrder: an admin can read any order")
    void getOrder_admin() {

        Order order = Order.builder().id(100L).userId(7L).status(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));

        OrderResponse response = orderService.getOrder(USER_ID, 100L, true);

        assertThat(response.getUserId()).isEqualTo(7L);
    }

    // ------------------------------------------------------------------ cancellation

    @Test
    @DisplayName("cancelByOwner: an unpaid order is cancelled and its stock released")
    void cancelByOwner_releasesStock() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING).build();
        given(orderStore.findWithItemsForUser(100L, USER_ID)).willReturn(Optional.of(order));
        given(orderStore.cancel(any(Order.class))).willAnswer(invocation -> {
            Order saved = invocation.getArgument(0);
            saved.setStatus(OrderStatus.CANCELLED);
            return saved;
        });

        OrderResponse response = orderService.cancelByOwner(USER_ID, 100L);

        then(downstreamClients).should().releaseStock(100L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PENDING);
    }

    @Test
    @DisplayName("cancelByOwner: a paid (CONFIRMED) order is refused — refunds go through payment-service")
    void cancelByOwner_paidOrderIsRefused() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID).build();
        given(orderStore.findWithItemsForUser(100L, USER_ID)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelByOwner(USER_ID, 100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("paid orders are refunded");

        then(downstreamClients).should(never()).releaseStock(anyLong());
        then(orderStore).should(never()).cancel(any());
    }

    @Test
    @DisplayName("cancelByOwner: an order that is not yours is the same 404 as a missing one")
    void cancelByOwner_notYoursIsNotFound() {

        given(orderStore.findWithItemsForUser(100L, USER_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelByOwner(USER_ID, 100L))
                .isInstanceOf(ResourceNotFoundException.class);

        then(downstreamClients).should(never()).releaseStock(anyLong());
    }

    // ------------------------------------------------------------------ admin status

    @Test
    @DisplayName("updateStatus: CONFIRMED → SHIPPED")
    void updateStatus_ship() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));
        given(orderStore.save(any(Order.class))).willAnswer(inv -> inv.<Order>getArgument(0));

        OrderResponse response = orderService.updateStatus(100L, OrderStatus.SHIPPED);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    @DisplayName("updateStatus: SHIPPED → DELIVERED")
    void updateStatus_deliver() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.SHIPPED)
                .paymentStatus(PaymentStatus.PAID).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));
        given(orderStore.save(any(Order.class))).willAnswer(inv -> inv.<Order>getArgument(0));

        assertThat(orderService.updateStatus(100L, OrderStatus.DELIVERED).getStatus())
                .isEqualTo(OrderStatus.DELIVERED);
    }

    @Test
    @DisplayName("updateStatus: a CREATED order cannot be shipped")
    void updateStatus_shipCreatedIsRefused() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(100L, OrderStatus.SHIPPED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("confirmed orders");
    }

    @Test
    @DisplayName("updateStatus: a CONFIRMED order cannot be cancelled here (refund flow, later)")
    void updateStatus_cancelConfirmedIsRefused() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(100L, OrderStatus.CANCELLED))
                .isInstanceOf(BusinessRuleException.class);

        then(downstreamClients).should(never()).releaseStock(anyLong());
    }

    @Test
    @DisplayName("updateStatus: an admin can cancel a CREATED order, which releases the stock")
    void updateStatus_cancelCreated() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));
        given(orderStore.save(any(Order.class))).willAnswer(inv -> inv.<Order>getArgument(0));

        OrderResponse response = orderService.updateStatus(100L, OrderStatus.CANCELLED);

        then(downstreamClients).should().releaseStock(100L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("updateStatus: CONFIRMED is payment's job, not an admin's")
    void updateStatus_confirmIsRefused() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateStatus(100L, OrderStatus.CONFIRMED))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("confirmed by their payment");
    }

    // ------------------------------------------------------------------ payment callbacks

    @Test
    @DisplayName("paymentConfirmed: CREATED → CONFIRMED, stock confirmed, cart cleared")
    void paymentConfirmed_happyPath() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));
        given(orderStore.save(any(Order.class))).willAnswer(inv -> inv.<Order>getArgument(0));

        OrderResponse response = orderService.paymentConfirmed(100L);

        then(downstreamClients).should().confirmStock(100L);
        then(downstreamClients).should().clearCart(USER_ID);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("paymentConfirmed: a replay is idempotent and makes no downstream calls")
    void paymentConfirmed_replay() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));

        OrderResponse response = orderService.paymentConfirmed(100L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        then(downstreamClients).should(never()).confirmStock(anyLong());
        then(downstreamClients).should(never()).clearCart(anyLong());
        then(orderStore).should(never()).save(any());
    }

    @Test
    @DisplayName("paymentConfirmed: an order that already moved on is a 422")
    void paymentConfirmed_wrongStatus() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.SHIPPED)
                .paymentStatus(PaymentStatus.PAID).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.paymentConfirmed(100L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("waiting for payment");
    }

    @Test
    @DisplayName("paymentConfirmed: a cart hiccup after the payment succeeded must not fail the order")
    void paymentConfirmed_cartClearFailureIsSwallowed() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));
        given(orderStore.save(any(Order.class))).willAnswer(inv -> inv.<Order>getArgument(0));
        doThrow(new DownstreamServiceException("Could not reach cart-service.", null))
                .when(downstreamClients).clearCart(USER_ID);

        OrderResponse response = orderService.paymentConfirmed(100L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
    }

    @Test
    @DisplayName("paymentFailed: CREATED → CANCELLED, stock released, payment FAILED")
    void paymentFailed_happyPath() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CREATED)
                .paymentStatus(PaymentStatus.PENDING).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));
        given(orderStore.save(any(Order.class))).willAnswer(inv -> inv.<Order>getArgument(0));

        OrderResponse response = orderService.paymentFailed(100L);

        then(downstreamClients).should().releaseStock(100L);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.FAILED);
    }

    @Test
    @DisplayName("paymentFailed: a replay is idempotent and makes no downstream calls")
    void paymentFailed_replay() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CANCELLED)
                .paymentStatus(PaymentStatus.FAILED).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));

        OrderResponse response = orderService.paymentFailed(100L);

        assertThat(response.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        then(downstreamClients).should(never()).releaseStock(anyLong());
        then(orderStore).should(never()).save(any());
    }

    @Test
    @DisplayName("paymentFailed: a confirmed order cannot be failed afterwards")
    void paymentFailed_wrongStatus() {

        Order order = Order.builder().id(100L).userId(USER_ID).status(OrderStatus.CONFIRMED)
                .paymentStatus(PaymentStatus.PAID).build();
        given(orderStore.findWithItems(100L)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.paymentFailed(100L))
                .isInstanceOf(BusinessRuleException.class);

        then(downstreamClients).should(never()).releaseStock(anyLong());
    }
}
