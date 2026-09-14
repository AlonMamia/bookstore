package com.alon.bookstore.order;

import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderItemRepository {
    List<OrderItem> findOrderItemsByUserId(Long userId);
}
