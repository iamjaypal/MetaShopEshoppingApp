package com.shopMeta.payment_service.service;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shopMeta.payment_service.entity.Payment;
import com.shopMeta.payment_service.entity.PaymentStatus;
import com.shopMeta.payment_service.repository.PaymentRepository;

import lombok.RequiredArgsConstructor;

/**
 * Every database write of payment-service lives here (the CartStore/OrderStore pattern): short,
 * focused transactions, while {@link PaymentService} orchestrates the gateway and the
 * order-service calls <em>outside</em> any transaction (no Feign inside a TX, D-062/D-068).
 */
@Service
@RequiredArgsConstructor
public class PaymentStore {

    private final PaymentRepository paymentRepository;

    /** Persists a new attempt (status PENDING). */
    @Transactional
    public Payment create(Payment payment) {
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public Optional<Payment> find(Long id) {
        return paymentRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Page<Payment> pageByUser(Long userId, Pageable pageable) {
        return paymentRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Payment> pageByStatus(PaymentStatus status, Pageable pageable) {

        return (status == null)
                ? paymentRepository.findAllByOrderByCreatedAtDesc(pageable)
                : paymentRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Payment> pageByOrder(Long orderId, Pageable pageable) {
        return paymentRepository.findByOrderIdOrderByCreatedAtDesc(orderId, pageable);
    }

    @Transactional(readOnly = true)
    public boolean hasPendingForOrder(Long orderId) {
        return paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(
                orderId, com.shopMeta.payment_service.entity.PaymentStatus.PENDING).isPresent();
    }

    @Transactional(readOnly = true)
    public boolean hasSucceededForOrder(Long orderId) {
        return paymentRepository.findFirstByOrderIdAndStatusOrderByCreatedAtDesc(
                orderId, com.shopMeta.payment_service.entity.PaymentStatus.SUCCEEDED).isPresent();
    }

    /** Persists a status transition; the {@code @Version} column guards racing writers. */
    @Transactional
    public Payment save(Payment payment) {
        return paymentRepository.save(payment);
    }
}
