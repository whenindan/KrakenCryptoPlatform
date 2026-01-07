package com.cryptoplatform.api.repository;

import com.cryptoplatform.api.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserIdOrderByTimestampDesc(Long userId);
    List<Order> findBySymbolAndStatus(String symbol, Order.Status status);
    List<Order> findByUserIdAndStatus(Long userId, Order.Status status);
}
