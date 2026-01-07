package com.cryptoplatform.api.service;

import com.cryptoplatform.api.model.User;
import com.cryptoplatform.api.repository.UserRepository;
import org.springframework.stereotype.Service;

/**
 * Routes trading requests to either PaperTradingService or LiveTradingService
 * based on the user's trading mode setting.
 */
@Service
public class TradingServiceRouter implements TradingServiceInterface {
    
    private final PaperTradingService paperTradingService;
    private final LiveTradingService liveTradingService;
    private final UserRepository userRepository;
    
    public TradingServiceRouter(
            PaperTradingService paperTradingService,
            LiveTradingService liveTradingService,
            UserRepository userRepository) {
        this.paperTradingService = paperTradingService;
        this.liveTradingService = liveTradingService;
        this.userRepository = userRepository;
    }
    
    /**
     * Get the appropriate trading service based on user's mode
     */
    private TradingServiceInterface getServiceForUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (user.getTradingMode() == User.TradingMode.LIVE) {
            return liveTradingService;
        }
        
        return paperTradingService;
    }
    
    @Override
    public com.cryptoplatform.api.model.Order placeOrder(Long userId, TradeRequest request) {
        return getServiceForUser(userId).placeOrder(userId, request);
    }
    
    @Override
    public java.util.List<com.cryptoplatform.api.model.Position> getPortfolio(Long userId) {
        return getServiceForUser(userId).getPortfolio(userId);
    }
    
    @Override
    public java.util.List<com.cryptoplatform.api.model.Order> getOpenOrders(Long userId) {
        return getServiceForUser(userId).getOpenOrders(userId);
    }
    
    @Override
    public void cancelOrder(Long userId, Long orderId) {
        getServiceForUser(userId).cancelOrder(userId, orderId);
    }
    
    @Override
    public java.math.BigDecimal getBalance(Long userId) {
        return getServiceForUser(userId).getBalance(userId);
    }
    
    /**
     * Get order history (delegates to paper trading service, might need both)
     */
    public java.util.List<com.cryptoplatform.api.model.Order> getOrderHistory(Long userId) {
        // For now, always use paper trading for order history
        // In production, you'd need to merge both paper and live order histories
        return paperTradingService.getOrderHistory(userId);
    }
}
