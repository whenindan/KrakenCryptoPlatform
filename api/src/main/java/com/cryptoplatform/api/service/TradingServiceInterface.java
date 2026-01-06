package com.cryptoplatform.api.service;

import com.cryptoplatform.api.model.Order;
import com.cryptoplatform.api.model.Position;
import com.cryptoplatform.api.service.TradeRequest;

import java.math.BigDecimal;
import java.util.List;

/**
 * Interface for trading operations.
 * Allows switching between paper trading and live Kraken trading.
 */
public interface TradingServiceInterface {
    
    /**
     * Place an order (buy or sell)
     */
    Order placeOrder(Long userId, TradeRequest request);
    
    /**
     * Get user's portfolio/positions
     */
    List<Position> getPortfolio(Long userId);
    
    /**
     * Get user's open orders
     */
    List<Order> getOpenOrders(Long userId);
    
    /**
     * Cancel an order
     */
    void cancelOrder(Long userId, Long orderId);
    
    /**
     * Get account balance
     */
    BigDecimal getBalance(Long userId);
}
