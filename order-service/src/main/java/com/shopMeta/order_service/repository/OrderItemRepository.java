package com.shopMeta.order_service.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.shopMeta.order_service.entity.OrderItem;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    /** Batch hydration of the lines of several orders (for list responses). */
    List<OrderItem> findByOrder_IdIn(Collection<Long> orderIds);
}
