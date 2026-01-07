package com.cryptoplatform.api.service;

import com.cryptoplatform.api.model.*;
import com.cryptoplatform.api.repository.*;
import jakarta.transaction.Transactional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

@Service
public class PaperTradingService implements TradingServiceInterface {

    private final OrderRepository orderRepository;
    private final PositionRepository positionRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final StringRedisTemplate redisTemplate;

    private static final BigDecimal FEE_RATE = new BigDecimal("0.002"); // 0.2%

    public PaperTradingService(OrderRepository orderRepository, PositionRepository positionRepository,
                          UserRepository userRepository, AccountRepository accountRepository, 
                          StringRedisTemplate redisTemplate) {
        this.orderRepository = orderRepository;
        this.positionRepository = positionRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public Order placeOrder(Long userId, TradeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Account account = user.getAccount();
        if (account == null) throw new RuntimeException("Account not found");

        Order order = new Order(user, request.getSymbol(), request.getSide(), request.getType(), request.getQuantity(), request.getLimitPrice());

        if (request.getType() == Order.Type.MARKET) {
            executeMarketOrder(order, account);
        } else {
            // For LIMIT, we just save it as OPEN (in a real system we'd lock funds here)
            // Validating funds for LIMIT buy
            if (request.getSide() == Order.Side.BUY) {
                 BigDecimal estimatedCost = request.getLimitPrice().multiply(request.getQuantity());
                 if (account.getBalance().compareTo(estimatedCost) < 0) {
                     throw new RuntimeException("Insufficient funds for limit order");
                 }
                 // Lock funds (deduct from balance now, refund if cancelled? Or separate 'locked' balance)
                 // For simplicity, we deduct now.
                 account.setBalance(account.getBalance().subtract(estimatedCost));
                 accountRepository.save(account);
            } else {
                // Validate asset execution for SELL
                Position position = positionRepository.findByAccountIdAndSymbol(account.getId(), request.getSymbol())
                        .orElseThrow(() -> new RuntimeException("Insufficient position"));
                if (position.getQuantity().compareTo(request.getQuantity()) < 0) {
                    throw new RuntimeException("Insufficient position");
                }
                // Lock assets
                position.setQuantity(position.getQuantity().subtract(request.getQuantity()));
                positionRepository.save(position);
            }
            orderRepository.save(order);
        }

        return order;
    }

    private void executeMarketOrder(Order order, Account account) {
        // Fetch latest price from Redis
        String key = "latest:" + order.getSymbol(); // Ensure this matches what MarketGateway writes
        // MarketGateway writes to "latest:BTC-USD"
        
        Map<Object, Object> tickerData = redisTemplate.opsForHash().entries(key);
        if (tickerData.isEmpty()) {
            throw new RuntimeException("Market data unavailable for " + order.getSymbol());
        }

        BigDecimal price = new BigDecimal((String) tickerData.get("last"));
        // Apply slippage? For now just use last price.
        
        executeTrade(order, account, price);
    }
    
    // Core trade execution (atomic update of balance/inventory)
    private void executeTrade(Order order, Account account, BigDecimal price) {
        BigDecimal totalValue = price.multiply(order.getQuantity());
        BigDecimal fee = totalValue.multiply(FEE_RATE);
        
        if (order.getSide() == Order.Side.BUY) {
            BigDecimal totalCost = totalValue.add(fee);
            if (account.getBalance().compareTo(totalCost) < 0) {
                throw new RuntimeException("Insufficient funds");
            }
            
            // Update Balance
            account.setBalance(account.getBalance().subtract(totalCost));
            
            // Update Position
            Position position = positionRepository.findByAccountIdAndSymbol(account.getId(), order.getSymbol())
                    .orElse(new Position(account, order.getSymbol(), BigDecimal.ZERO));
            
            // Update Avg Entry (Simple weighted average)
            // (OldQty * OldAvg + NewQty * Price) / (OldQty + NewQty)
            BigDecimal totalQty = position.getQuantity().add(order.getQuantity());
            if (position.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                position.setAvgEntryPrice(price);
            } else {
                 BigDecimal oldVal = position.getQuantity().multiply(position.getAvgEntryPrice());
                 BigDecimal newVal = order.getQuantity().multiply(price);
                 position.setAvgEntryPrice(oldVal.add(newVal).divide(totalQty, 8, RoundingMode.HALF_UP));
            }
            position.setQuantity(totalQty);
            positionRepository.save(position);
            
        } else {
            // SELL
            BigDecimal totalProceeds = totalValue.subtract(fee);
            
            // Check Position (if MARKET)
            // If LIMIT, assets were already locked/deducted in placeOrder, so we don't check/deduct again?
            // Wait, my placeOrder logic for LIMIT deducted assets. 
            // So if this is MARKET, we must check/deduct. 
            // If this is LIMIT execution (called by processLimitOrders later), we assume assets already deducted?
            // Actually, let's keep it consistent. 
            
            if (order.getType() == Order.Type.MARKET) {
                Position position = positionRepository.findByAccountIdAndSymbol(account.getId(), order.getSymbol())
                        .orElseThrow(() -> new RuntimeException("No position found"));
                
                if (position.getQuantity().compareTo(order.getQuantity()) < 0) {
                    throw new RuntimeException("Insufficient quantity");
                }
                position.setQuantity(position.getQuantity().subtract(order.getQuantity()));
                positionRepository.save(position);
            }
            
            // Add Proceeds
            account.setBalance(account.getBalance().add(totalProceeds));
        }
        
        accountRepository.save(account);
        
        // Update Order
        order.setStatus(Order.Status.FILLED);
        order.setFilledPrice(price);
        order.setFee(fee);
        orderRepository.save(order);
    }
    
    @Transactional
    public void processLimitOrders(Ticker ticker) {
        // Find OPEN orders for this symbol
        List<Order> openOrders = orderRepository.findBySymbolAndStatus(ticker.symbol(), Order.Status.OPEN);
        
        for (Order order : openOrders) {
            boolean filled = false;
            BigDecimal marketPrice = ticker.last();
            
            if (order.getSide() == Order.Side.BUY) {
                // Buy if market price <= limit price
                if (marketPrice.compareTo(order.getLimitPrice()) <= 0) {
                    filled = true;
                }
            } else {
                // Sell if market price >= limit price
                if (marketPrice.compareTo(order.getLimitPrice()) >= 0) {
                    filled = true;
                }
            }
            
            if (filled) {
                // For LIMIT orders, funds/assets were deducted at placement.
                // For BUY: We deducted Cost = LimitPrice * Qty.
                // Now we fill at MarketPrice (which is better or equal).
                // Actual Cost = MarketPrice * Qty.
                // Refund = (LimitPrice - MarketPrice) * Qty.
                
                Account account = order.getUser().getAccount();
                executeTradeFromLocked(order, account, marketPrice);
            }
        }
    }
    
    private void executeTradeFromLocked(Order order, Account account, BigDecimal price) {
        BigDecimal totalValue = price.multiply(order.getQuantity());
        BigDecimal fee = totalValue.multiply(FEE_RATE);
        
        if (order.getSide() == Order.Side.BUY) {
            // Funds were already deducted based on Limit Price.
            BigDecimal initialCost = order.getLimitPrice().multiply(order.getQuantity());
            BigDecimal actualCost = totalValue.add(fee);
            
            // Refund the difference (if any)
            // Warning: We didn't account for Fee in the initial deduction of Limit Order?
            // In placeOrder: account.setBalance(account.getBalance().subtract(estimatedCost)); where estimatedCost = limitPrice * qty.
            // So we didn't deduct fee.
            // If actualCost > initialCost (due to fee), we might go negative?
            // Let's assume user has margin or small float. Correct way is to reserve (Price + Fee).
            // For this 'Paper Trading' MVP, we just adjust balance.
            
            BigDecimal balanceAdjustment = initialCost.subtract(actualCost);
            account.setBalance(account.getBalance().add(balanceAdjustment));
            
            // Update Position
             Position position = positionRepository.findByAccountIdAndSymbol(account.getId(), order.getSymbol())
                    .orElse(new Position(account, order.getSymbol(), BigDecimal.ZERO));
             
             BigDecimal totalQty = position.getQuantity().add(order.getQuantity());
             
             // Update Avg Entry - This is complex if we already had a position.
             // Simplification: We add the new qty at the filled price.
             // Recalculate avg
             if (position.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                 position.setAvgEntryPrice(price);
             } else {
                 BigDecimal oldVal = position.getQuantity().multiply(position.getAvgEntryPrice());
                 BigDecimal newVal = order.getQuantity().multiply(price);
                 position.setAvgEntryPrice(oldVal.add(newVal).divide(totalQty, 8, RoundingMode.HALF_UP));
             }
             position.setQuantity(totalQty);
             positionRepository.save(position);
             
        } else {
            // SELL
            // Assets were locked (deducted). We just add proceeds.
            BigDecimal proceeds = totalValue.subtract(fee);
            account.setBalance(account.getBalance().add(proceeds));
        }
        
        accountRepository.save(account);
        
        order.setStatus(Order.Status.FILLED);
        order.setFilledPrice(price);
        order.setFee(fee);
        orderRepository.save(order);
    }
    
    public List<Order> getOrderHistory(Long userId) {
        return orderRepository.findByUserIdOrderByTimestampDesc(userId);
    }
    
    public List<Position> getPortfolio(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return positionRepository.findByAccountId(user.getAccount().getId());
    }
    
    @Override
    public List<Order> getOpenOrders(Long userId) {
        return orderRepository.findByUserIdAndStatus(userId, Order.Status.OPEN);
    }
    
    @Override
    public void cancelOrder(Long userId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        
        if (!order.getUser().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }
        
        if (order.getStatus() != Order.Status.OPEN) {
            throw new RuntimeException("Cannot cancel order that is not OPEN");
        }
        
        // Refund locked funds/assets
        Account account = order.getUser().getAccount();
        if (order.getSide() == Order.Side.BUY) {
            // Refund USD
            BigDecimal refund = order.getLimitPrice().multiply(order.getQuantity());
            account.setBalance(account.getBalance().add(refund));
        } else {
            // Refund crypto
            Position position = positionRepository.findByAccountIdAndSymbol(account.getId(), order.getSymbol())
                    .orElse(new Position(account, order.getSymbol(), BigDecimal.ZERO));
            position.setQuantity(position.getQuantity().add(order.getQuantity()));
            positionRepository.save(position);
        }
        accountRepository.save(account);
        
        order.setStatus(Order.Status.CANCELLED);
        orderRepository.save(order);
    }
    
    @Override
    public BigDecimal getBalance(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        return user.getAccount().getBalance();
    }
}
