package com.cryptoplatform.api.controller;

import com.cryptoplatform.api.dto.AiCommandRequest;
import com.cryptoplatform.api.dto.AiCommandResponse;
import com.cryptoplatform.api.model.AgentRule;
import com.cryptoplatform.api.model.Order;
import com.cryptoplatform.api.model.User;
import com.cryptoplatform.api.repository.AgentRuleRepository;
import com.cryptoplatform.api.repository.UserRepository;
import com.cryptoplatform.api.service.AiAgentService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ai")
public class AiAgentController {

    private final AiAgentService aiAgentService;
    private final AgentRuleRepository ruleRepository;
    private final UserRepository userRepository;

    public AiAgentController(AiAgentService aiAgentService,
                            AgentRuleRepository ruleRepository,
                            UserRepository userRepository) {
        this.aiAgentService = aiAgentService;
        this.ruleRepository = ruleRepository;
        this.userRepository = userRepository;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (String) auth.getPrincipal();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }

    @PostMapping("/command")
    public ResponseEntity<AiCommandResponse> processCommand(@RequestBody AiCommandRequest request) {
        try {
            Long userId = getCurrentUserId();
            Object result = aiAgentService.processCommand(userId, request.getCommand());
            
            // Handle AiCommandResponse type (with confirmation)
            if (result instanceof AiCommandResponse) {
                return ResponseEntity.ok((AiCommandResponse) result);
            }
            
            // Legacy handling for direct execution results
            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<Order> orders = (List<Order>) result;
                
                // Build detailed message for multiple orders
                StringBuilder message = new StringBuilder();
                if (orders.size() == 2 && orders.get(0).getSide() == Order.Side.SELL && orders.get(1).getSide() == Order.Side.BUY) {
                    // Coin conversion
                    Order sellOrder = orders.get(0);
                    Order buyOrder = orders.get(1);
                    message.append(String.format("Conversion executed: Sold %s %s at $%s, bought %s %s at $%s", 
                        sellOrder.getQuantity(), sellOrder.getSymbol(),
                        sellOrder.getFilledPrice(),
                        buyOrder.getQuantity(), buyOrder.getSymbol(),
                        buyOrder.getFilledPrice()));
                } else {
                    // Portfolio diversification
                    message.append(String.format("Portfolio diversified across %d assets: ", orders.size()));
                    for (int i = 0; i < orders.size(); i++) {
                        Order order = orders.get(i);
                        message.append(String.format("%s %s", order.getQuantity(), order.getSymbol()));
                        if (i < orders.size() - 1) {
                            message.append(", ");
                        }
                    }
                }
                
                return ResponseEntity.ok(AiCommandResponse.success(message.toString()));
            } else if (result instanceof Order) {
                Order order = (Order) result;
                return ResponseEntity.ok(AiCommandResponse.withOrder(order, 
                    String.format("Order executed: %s %s of %s", 
                        order.getSide(), order.getQuantity(), order.getSymbol())));
            } else if (result instanceof AgentRule) {
                AgentRule rule = (AgentRule) result;
                return ResponseEntity.ok(AiCommandResponse.withRule(rule, 
                    String.format("Rule created: %s when %s %s %s", 
                        rule.getRuleText(), rule.getSymbol(), 
                        rule.getCondition(), rule.getTargetPrice())));
            } else {
                return ResponseEntity.ok(AiCommandResponse.success("Command processed successfully"));
            }
            
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(AiCommandResponse.error("Failed to process command: " + e.getMessage()));
        }
    }

    @GetMapping("/rules")
    public ResponseEntity<List<AgentRule>> getRules() {
        Long userId = getCurrentUserId();
        List<AgentRule> rules = ruleRepository.findByUserIdOrderByCreatedAtDesc(userId);
        return ResponseEntity.ok(rules);
    }

    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Void> deactivateRule(@PathVariable Long ruleId) {
        Long userId = getCurrentUserId();
        AgentRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new RuntimeException("Rule not found"));
        
        // Verify ownership
        if (!rule.getUser().getId().equals(userId)) {
            return ResponseEntity.status(403).build();
        }
        
        rule.setIsActive(false);
        ruleRepository.save(rule);
        
        return ResponseEntity.ok().build();
    }
    
    @PostMapping("/confirm/{confirmationId}")
    public ResponseEntity<AiCommandResponse> confirmCommand(@PathVariable String confirmationId) {
        try {
            Long userId = getCurrentUserId();
            Object result = aiAgentService.executeConfirmation(userId, confirmationId);
            
            // Handle execution results
            if (result instanceof List) {
                @SuppressWarnings("unchecked")
                List<Order> orders = (List<Order>) result;
                StringBuilder message = new StringBuilder();
                if (orders.size() == 2 && orders.get(0).getSide() == Order.Side.SELL && orders.get(1).getSide() == Order.Side.BUY) {
                    Order sellOrder = orders.get(0);
                    Order buyOrder = orders.get(1);
                    message.append(String.format("✅ Conversion executed: Sold %s %s at $%s, bought %s %s at $%s", 
                        sellOrder.getQuantity(), sellOrder.getSymbol(),
                        sellOrder.getFilledPrice(),
                        buyOrder.getQuantity(), buyOrder.getSymbol(),
                        buyOrder.getFilledPrice()));
                } else {
                    message.append(String.format("✅ Portfolio diversified across %d assets: ", orders.size()));
                    for (int i = 0; i < orders.size(); i++) {
                        Order order = orders.get(i);
                        message.append(String.format("%s %s", order.getQuantity(), order.getSymbol()));
                        if (i < orders.size() - 1) {
                            message.append(", ");
                        }
                    }
                }
                return ResponseEntity.ok(AiCommandResponse.success(message.toString()));
            } else if (result instanceof Order) {
                Order order = (Order) result;
                return ResponseEntity.ok(AiCommandResponse.withOrder(order, 
                    String.format("✅ Order executed: %s %s of %s", 
                        order.getSide(), order.getQuantity(), order.getSymbol())));
            } else if (result instanceof AgentRule) {
                AgentRule rule = (AgentRule) result;
                return ResponseEntity.ok(AiCommandResponse.withRule(rule, 
                    String.format("✅ Rule created: Will %s %s when price %s $%s", 
                        rule.getAction(), rule.getSymbol().replace("-USD", ""),
                        rule.getCondition().equals(AgentRule.Condition.PRICE_ABOVE) ? "goes above" : "goes below",
                        rule.getTargetPrice())));
            }
            
            return ResponseEntity.ok(AiCommandResponse.success("✅ Command executed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(AiCommandResponse.error("Failed to execute: " + e.getMessage()));
        }
    }
    
    @DeleteMapping("/confirm/{confirmationId}")
    public ResponseEntity<AiCommandResponse> cancelCommand(@PathVariable String confirmationId) {
        try {
            aiAgentService.cancelConfirmation(confirmationId);
            return ResponseEntity.ok(AiCommandResponse.success("Command canceled"));
        } catch (Exception e) {
            return ResponseEntity.badRequest()
                    .body(AiCommandResponse.error("Failed to cancel: " + e.getMessage()));
        }
    }
}
