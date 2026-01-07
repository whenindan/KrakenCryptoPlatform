package com.cryptoplatform.api.service;

import com.cryptoplatform.api.model.AgentRule;
import com.cryptoplatform.api.model.Order;
import com.cryptoplatform.api.model.Position;
import com.cryptoplatform.api.repository.AgentRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class RuleMonitorService {

    private static final Logger log = LoggerFactory.getLogger(RuleMonitorService.class);

    private final AgentRuleRepository ruleRepository;
    private final MarketService marketService;
    private final TradingServiceRouter tradingService;

    public RuleMonitorService(AgentRuleRepository ruleRepository, 
                             MarketService marketService,
                             TradingServiceRouter tradingService) {
        this.ruleRepository = ruleRepository;
        this.marketService = marketService;
        this.tradingService = tradingService;
    }

    @Scheduled(fixedDelay = 10000) // Run every 10 seconds
    @Transactional
    public void checkAndExecuteRules() {
        try {
            List<AgentRule> activeRules = ruleRepository.findByIsActive(true);
            
            log.info("Checking {} active trading rules", activeRules.size());
            
            for (AgentRule rule : activeRules) {
                try {
                    checkRule(rule);
                } catch (Exception e) {
                    log.error("Error checking rule {}: {}", rule.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error in rule monitoring: {}", e.getMessage());
        }
    }

    private void checkRule(AgentRule rule) {
        // Get current price
        BigDecimal currentPrice;
        try {
            currentPrice = marketService.getCurrentPrice(rule.getSymbol());
        } catch (Exception e) {
            log.warn("Could not get price for {}, skipping rule {}", rule.getSymbol(), rule.getId());
            return;
        }
        
        boolean shouldExecute = false;
        
        // Check if condition is met
        switch (rule.getCondition()) {
            case PRICE_ABOVE:
                if (currentPrice.compareTo(rule.getTargetPrice()) >= 0) {
                    shouldExecute = true;
                }
                break;
            case PRICE_BELOW:
                if (currentPrice.compareTo(rule.getTargetPrice()) <= 0) {
                    shouldExecute = true;
                }
                break;
            case PRICE_EQUALS:
                // Use a small tolerance for equality (e.g., within 0.1%)
                BigDecimal tolerance = rule.getTargetPrice().multiply(new BigDecimal("0.001"));
                if (currentPrice.subtract(rule.getTargetPrice()).abs().compareTo(tolerance) <= 0) {
                    shouldExecute = true;
                }
                break;
        }
        
        if (shouldExecute) {
            log.info("Rule {} triggered! Current price: {}, Target: {}", 
                    rule.getId(), currentPrice, rule.getTargetPrice());
            executeRule(rule, currentPrice);
        }
    }

    private void executeRule(AgentRule rule, BigDecimal currentPrice) {
        try {
            BigDecimal quantity;
            
            // Handle different amount types
            if (rule.getAmountType() == null || rule.getAmountType() == AgentRule.AmountType.USD) {
                // Dollar amount (backward compatibility for existing rules)
                quantity = rule.getAmount().divide(currentPrice, 8, RoundingMode.HALF_UP);
            } else if (rule.getAmountType() == AgentRule.AmountType.CRYPTO) {
                // Crypto quantity
                quantity = rule.getAmount();
            } else if (rule.getAmountType() == AgentRule.AmountType.ALL) {
                // Sell/buy entire position
                if (rule.getAction() == AgentRule.Action.SELL) {
                    // Query user's position
                    List<Position> positions = tradingService.getPortfolio(rule.getUser().getId());
                    Position position = positions.stream()
                            .filter(p -> p.getSymbol().equals(rule.getSymbol()))
                            .findFirst()
                            .orElseThrow(() -> new RuntimeException("No position found for " + rule.getSymbol()));
                    
                    if (position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                        throw new RuntimeException("Insufficient holdings for " + rule.getSymbol());
                    }
                    quantity = position.getQuantity();
                } else {
                    // ALL is only valid for SELL
                    throw new IllegalStateException("ALL amount type is only supported for SELL rules");
                }
            } else {
                throw new IllegalArgumentException("Invalid amount type: " + rule.getAmountType());
            }
            
            // Create trade request
            TradeRequest request = new TradeRequest();
            request.setSymbol(rule.getSymbol());
            request.setSide(rule.getAction() == AgentRule.Action.BUY ? Order.Side.BUY : Order.Side.SELL);
            request.setType(Order.Type.MARKET);
            request.setQuantity(quantity);
            
            // Execute trade
            Order order = tradingService.placeOrder(rule.getUser().getId(), request);
            
            // Mark rule as executed
            rule.setIsActive(false);
            rule.setExecutedAt(LocalDateTime.now());
            ruleRepository.save(rule);
            
            log.info("Rule {} executed successfully. Order ID: {}", rule.getId(), order.getId());
            
        } catch (Exception e) {
            log.error("Failed to execute rule {}: {}", rule.getId(), e.getMessage());
            // Rule remains active to retry later
        }
    }
}
