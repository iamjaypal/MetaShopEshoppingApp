package com.shopMeta.payment_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

import com.shopMeta.payment_service.client.dto.OrderView;
import com.shopMeta.payment_service.config.PaymentProperties;
import com.shopMeta.payment_service.dto.CreatePaymentRequest;
import com.shopMeta.payment_service.dto.PaymentResponse;
import com.shopMeta.payment_service.entity.Payment;
import com.shopMeta.payment_service.entity.PaymentMethod;
import com.shopMeta.payment_service.entity.PaymentStatus;
import com.shopMeta.payment_service.exception.BusinessRuleException;
import com.shopMeta.payment_service.exception.DownstreamServiceException;
import com.shopMeta.payment_service.exception.ResourceNotFoundException;

/**
 * Unit tests of the payment flow: the authoritative order read, the ledger guards, the charge and
 * its reporting, and the honesty rules (a captured-but-unconfirmable charge never lies; a declined
 * charge is always reported).
 */
@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    private static final Long USER_ID = 1L;
    private static final Long ORDER_ID = 100L;

    @Mock
    private DownstreamClients downstreamClients;

    @Mock
    private PaymentStore paymentStore;

    @Mock
    private MockPaymentGateway mockGateway;

    private PaymentService paymentService;
    private PaymentProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PaymentProperties();
        paymentService = new PaymentService(downstreamClients, paymentStore, mockGateway);
    }

    // ------------------------------------------------------------------ fixtures

    private OrderView createdOrder() {
        OrderView order = new OrderView();
        order.setId(ORDER_ID);
        order.setUserId(USER_ID);
        order.setStatus("CREATED");
        order.setTotal(new BigDecimal("449.00"));
        return order;
    }

    private OrderView order(String status) {
        OrderView order = createdOrder();
        order.setStatus(status);
        return order;
    }

    /** The store assigns the id on save, like the identity column would. */
    private void storeAssignsId() {
        given(paymentStore.create(any(Payment.class))).willAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(500L);
            return payment;
        });
        given(paymentStore.save(any(Payment.class)))
                .willAnswer(inv -> inv.<Payment>getArgument(0));
    }

    private void givenChargeCaptures() {
        given(mockGateway.charge(ORDER_ID))
                .willReturn(MockPaymentGateway.ChargeResult.captured("MOCK-100-abcd1234"));
    }

    private void givenChargeDeclines() {
        given(mockGateway.charge(ORDER_ID))
                .willReturn(MockPaymentGateway.ChargeResult.declined("Declined by the mock gateway"));
    }

    private CreatePaymentRequest payByCard() {
        return new CreatePaymentRequest(ORDER_ID, PaymentMethod.CARD);
    }

    // ------------------------------------------------------------------ happy path

    @Test
    @DisplayName("pay: a captured charge is SUCCEEDED with the gateway reference and reported")
    void pay_capturedAndReported() {

        given(downstreamClients.readOrder(ORDER_ID)).willReturn(createdOrder());
        storeAssignsId();
        givenChargeCaptures();

        PaymentResponse response = paymentService.pay(USER_ID, payByCard());

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(response.getGatewayReference()).isEqualTo("MOCK-100-abcd1234");
        assertThat(response.getAmount()).isEqualByComparingTo("449.00");
        then(downstreamClients).should().reportPaymentConfirmed(ORDER_ID);
        then(downstreamClients).should(never()).reportPaymentFailed(anyLong());
    }

    @Test
    @DisplayName("pay: the amount is the order's total, never anything the client sent")
    void pay_amountComesFromTheOrder() {

        given(downstreamClients.readOrder(ORDER_ID)).willReturn(createdOrder());
        storeAssignsId();
        givenChargeCaptures();

        paymentService.pay(USER_ID, payByCard());

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        then(paymentStore).should().create(captor.capture());
        assertThat(captor.getValue().getAmount()).isEqualByComparingTo("449.00");
        // The row is created PENDING; the status field itself is mutated in place when the charge
        // completes, so the captured reference no longer shows the creation state here.
        assertThat(captor.getValue().getMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    }

    // ------------------------------------------------------------------ validation

    @Test
    @DisplayName("pay: an unknown order is a 404, before anything is recorded")
    void pay_unknownOrder() {

        given(downstreamClients.readOrder(ORDER_ID))
                .willThrow(new ResourceNotFoundException("Order 100 was not found"));

        assertThatThrownBy(() -> paymentService.pay(USER_ID, payByCard()))
                .isInstanceOf(ResourceNotFoundException.class);

        then(paymentStore).should(never()).create(any());
    }

    @Test
    @DisplayName("pay: someone else's order is the same 404 as a missing one")
    void pay_foreignOrderIsNotFound() {

        OrderView foreign = order("CREATED");
        foreign.setUserId(7L);
        given(downstreamClients.readOrder(ORDER_ID)).willReturn(foreign);

        assertThatThrownBy(() -> paymentService.pay(USER_ID, payByCard()))
                .isInstanceOf(ResourceNotFoundException.class);

        then(mockGateway).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("pay: an order that already moved on is a 422 — no charge is taken")
    void pay_orderNotWaitingForPayment() {

        given(downstreamClients.readOrder(ORDER_ID)).willReturn(order("CANCELLED"));

        assertThatThrownBy(() -> paymentService.pay(USER_ID, payByCard()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("CANCELLED");

        then(mockGateway).shouldHaveNoInteractions();
        then(paymentStore).should(never()).create(any());
    }

    @Test
    @DisplayName("pay: an already-paid order is refused")
    void pay_alreadyPaid() {

        given(downstreamClients.readOrder(ORDER_ID)).willReturn(createdOrder());
        given(paymentStore.hasSucceededForOrder(ORDER_ID)).willReturn(true);

        assertThatThrownBy(() -> paymentService.pay(USER_ID, payByCard()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already paid");

        then(mockGateway).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("pay: a second click while a charge is in flight is refused")
    void pay_attemptAlreadyInFlight() {

        given(downstreamClients.readOrder(ORDER_ID)).willReturn(createdOrder());
        given(paymentStore.hasPendingForOrder(ORDER_ID)).willReturn(true);

        assertThatThrownBy(() -> paymentService.pay(USER_ID, payByCard()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("already in progress");

        then(mockGateway).shouldHaveNoInteractions();
    }

    // ------------------------------------------------------------------ declined

    @Test
    @DisplayName("pay: a declined charge is FAILED, reported, and the decline is returned as data")
    void pay_declinedAndReported() {

        given(downstreamClients.readOrder(ORDER_ID)).willReturn(createdOrder());
        storeAssignsId();
        givenChargeDeclines();

        PaymentResponse response = paymentService.pay(USER_ID, payByCard());

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(response.getFailureReason()).contains("Declined");
        assertThat(response.getGatewayReference()).isNull();
        then(downstreamClients).should().reportPaymentFailed(ORDER_ID);
        then(downstreamClients).should(never()).reportPaymentConfirmed(anyLong());
    }

    @Test
    @DisplayName("pay: a declined charge is reported best-effort — an outage never hides the decline")
    void pay_declineReportFailureIsSwallowed() {

        given(downstreamClients.readOrder(ORDER_ID)).willReturn(createdOrder());
        storeAssignsId();
        givenChargeDeclines();
        org.mockito.BDDMockito.willThrow(new DownstreamServiceException(
                "Could not reach order-service. Please try again shortly.", null))
                .given(downstreamClients).reportPaymentFailed(ORDER_ID);

        PaymentResponse response = paymentService.pay(USER_ID, payByCard());

        // The ledger is truthful even though the order side was not told.
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.FAILED);
        then(paymentStore).should().save(any(Payment.class));
    }

    // ------------------------------------------------------------------ captured but refused

    @Test
    @DisplayName("pay: a captured charge that order-service refuses (422) becomes FAILED with the reason")
    void pay_capturedButRefused() {

        given(downstreamClients.readOrder(ORDER_ID)).willReturn(createdOrder());
        storeAssignsId();
        givenChargeCaptures();
        org.mockito.BDDMockito.willThrow(new BusinessRuleException(
                "The reserved stock for order 100 expired and the order cannot be confirmed"))
                .given(downstreamClients).reportPaymentConfirmed(ORDER_ID);

        assertThatThrownBy(() -> paymentService.pay(USER_ID, payByCard()))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("expired");

        // The ledger does not claim success over a refused charge: written SUCCEEDED for the
        // captured charge, then written again as FAILED when order-service refused it.
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        then(paymentStore).should(org.mockito.Mockito.times(2)).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);
        assertThat(saved.getValue().getFailureReason()).contains("expired");
    }

    @Test
    @DisplayName("pay: a captured charge whose confirmation cannot be delivered stays SUCCEEDED — the money is real")
    void pay_capturedButUnreportable_staysSucceeded() {

        given(downstreamClients.readOrder(ORDER_ID)).willReturn(createdOrder());
        storeAssignsId();
        givenChargeCaptures();
        org.mockito.BDDMockito.willThrow(new DownstreamServiceException(
                "Could not reach order-service. Please try again shortly.", null))
                .given(downstreamClients).reportPaymentConfirmed(ORDER_ID);

        assertThatThrownBy(() -> paymentService.pay(USER_ID, payByCard()))
                .isInstanceOf(DownstreamServiceException.class);

        // The caller got the 503 — but the ledger keeps the captured charge, never a fake failure.
        ArgumentCaptor<Payment> saved = ArgumentCaptor.forClass(Payment.class);
        then(paymentStore).should().save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(PaymentStatus.SUCCEEDED);
        assertThat(saved.getValue().getGatewayReference()).isNotNull();
    }

    // ------------------------------------------------------------------ reads

    @Test
    @DisplayName("getPayment: an owner reads their own attempt")
    void getPayment_owner() {

        Payment payment = Payment.builder().id(500L).orderId(ORDER_ID).userId(USER_ID)
                .amount(new BigDecimal("449.00")).method(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCEEDED).build();
        given(paymentStore.find(500L)).willReturn(Optional.of(payment));

        PaymentResponse response = paymentService.getPayment(USER_ID, 500L, false);

        assertThat(response.getId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("getPayment: someone else's attempt is the same 404 as a missing one")
    void getPayment_notYoursIsNotFound() {

        Payment payment = Payment.builder().id(500L).orderId(ORDER_ID).userId(7L)
                .amount(new BigDecimal("449.00")).method(PaymentMethod.CARD)
                .status(PaymentStatus.SUCCEEDED).build();
        given(paymentStore.find(500L)).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentService.getPayment(USER_ID, 500L, false))
                .isInstanceOf(ResourceNotFoundException.class);

        // ...but an admin sees everything.
        assertThat(paymentService.getPayment(USER_ID, 500L, true).getId()).isEqualTo(500L);
    }

    @Test
    @DisplayName("getPayment: a missing attempt is a 404")
    void getPayment_missing() {

        given(paymentStore.find(404L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getPayment(USER_ID, 404L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("listMyPayments: only the caller's attempts are returned (paging delegated)")
    void listMyPayments() {

        given(paymentStore.pageByUser(eq(USER_ID), any()))
                .willReturn(org.mockito.Mockito.mock(Page.class));

        assertThat(paymentService.listMyPayments(USER_ID, 0, 20)).isNotNull();

        then(paymentStore).should().pageByUser(eq(USER_ID), any());
    }

    @Test
    @DisplayName("listAll: the orderId filter wins over the status filter")
    void listAll_orderFilterWins() {

        given(paymentStore.pageByOrder(eq(ORDER_ID), any()))
                .willReturn(org.mockito.Mockito.mock(Page.class));

        paymentService.listAll(PaymentStatus.FAILED, ORDER_ID, 0, 20);

        then(paymentStore).should().pageByOrder(eq(ORDER_ID), any());
        then(paymentStore).should(never()).pageByStatus(any(), any());
    }
}
