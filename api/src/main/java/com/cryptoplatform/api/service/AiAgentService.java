package com.cryptoplatform.api.service;

import com.cryptoplatform.api.dto.AiCommandResponse;
import com.cryptoplatform.api.dto.PendingCommand;
import com.cryptoplatform.api.model.AgentRule;
import com.cryptoplatform.api.model.Order;
import com.cryptoplatform.api.model.Position;
import com.cryptoplatform.api.model.User;
import com.cryptoplatform.api.repository.AgentRuleRepository;
import com.cryptoplatform.api.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class AiAgentService {

    private final OpenAiClient openAiClient;
    private final TradingService tradingService;
    private final MarketService marketService;
    private final AgentRuleRepository ruleRepository;
    private final UserRepository userRepository;
    
    // In-memory cache for pending confirmations (in production, use Redis)
    private final java.util.Map<String, PendingCommand> pendingCommands = new java.util.concurrent.ConcurrentHashMap<>();

    public AiAgentService(OpenAiClient openAiClient, TradingService tradingService,
                          MarketService marketService, AgentRuleRepository ruleRepository,
                          UserRepository userRepository) {
        this.openAiClient = openAiClient;
        this.tradingService = tradingService;
        this.marketService = marketService;
        this.ruleRepository = ruleRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Object processCommand(Long userId, String naturalLanguageCommand) {
        try {
            // Call OpenAI to parse the command
            JsonNode response = openAiClient.parseCommand(naturalLanguageCommand);
            
            // Extract function name and arguments
            String functionName = openAiClient.extractFunctionName(response);
            JsonNode args = openAiClient.extractFunctionArguments(response);
            
            if (functionName == null || args == null) {
                // AI returned a plain text response (error or clarification)
                String aiMessage = openAiClient.extractTextResponse(response);
                if (aiMessage != null && !aiMessage.isEmpty()) {
                    return AiCommandResponse.error(aiMessage);
                }
                return AiCommandResponse.error("I'm not sure what you want me to do. Could you please rephrase your request? I can help you buy, sell, convert crypto, or create automated trading rules.");
            }
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Generate confirmation ID
            String confirmationId = java.util.UUID.randomUUID().toString();
            
            // Store pending command
            PendingCommand pending = new PendingCommand(
                userId.toString(),
                functionName,
                args,
                naturalLanguageCommand
            );
            pendingCommands.put(confirmationId, pending);
            
            // Generate confirmation message
            String confirmationMessage = generateConfirmationMessage(functionName, args, user);
            
            // Return confirmation response
            return AiCommandResponse.requireConfirmation(confirmationId, confirmationMessage, pending);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to process command: " + e.getMessage(), e);
        }
    }

    private Order executeImmediateOrder(User user, JsonNode args) {
        String symbol = args.path("symbol").asText();
        String action = args.path("action").asText();
        String amountType = args.path("amount_type").asText();
        BigDecimal amount = new BigDecimal(args.path("amount").asText());
        
        BigDecimal quantity;
        
        if ("USD".equalsIgnoreCase(amountType)) {
            // User specified dollar amount (e.g., "buy $100 of BTC")
            // Get current price and calculate quantity
            BigDecimal currentPrice = marketService.getCurrentPrice(symbol);
            quantity = amount.divide(currentPrice, 8, RoundingMode.HALF_UP);
        } else if ("CRYPTO".equalsIgnoreCase(amountType)) {
            // User specified crypto quantity (e.g., "buy 1 BTC")
            // Use amount directly as quantity
            quantity = amount;
        } else if ("ALL".equalsIgnoreCase(amountType)) {
            // User wants to sell entire position (e.g., "sell all my BTC")
            if (!"SELL".equals(action)) {
                throw new IllegalArgumentException("ALL amount type can only be used with SELL action");
            }
            // Query user's position
            List<Position> positions = tradingService.getPortfolio(user.getId());
            Position position = positions.stream()
                    .filter(p -> p.getSymbol().equals(symbol))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No position found for " + symbol));
            
            if (position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Insufficient holdings for " + symbol);
            }
            quantity = position.getQuantity();
        } else {
            throw new IllegalArgumentException("Invalid amount_type: " + amountType + ". Must be USD, CRYPTO, or ALL.");
        }
        
        // Create trade request
        TradeRequest request = new TradeRequest();
        request.setSymbol(symbol);
        request.setSide("BUY".equals(action) ? Order.Side.BUY : Order.Side.SELL);
        request.setType(Order.Type.MARKET);
        request.setQuantity(quantity);
        
        // Execute via trading service
        return tradingService.placeOrder(user.getId(), request);
    }

    private AgentRule createRule(User user, String ruleText, JsonNode args) {
        String symbol = args.path("symbol").asText();
        String conditionStr = args.path("condition").asText();
        BigDecimal targetPrice = new BigDecimal(args.path("targetPrice").asText());
        String action = args.path("action").asText();
        String amountTypeStr = args.path("amount_type").asText();
        BigDecimal amount = new BigDecimal(args.path("amount").asText("0")); // Default 0 for ALL
        
        AgentRule.Condition condition = AgentRule.Condition.valueOf(conditionStr);
        AgentRule.Action ruleAction = AgentRule.Action.valueOf(action);
        AgentRule.AmountType amountType = AgentRule.AmountType.valueOf(amountTypeStr);
        
        AgentRule rule = new AgentRule(user, ruleText, symbol, condition, targetPrice, ruleAction, amountType, amount);
        return ruleRepository.save(rule);
    }

    @Transactional
    private List<Order> executeCoinConversion(User user, JsonNode args) {
        String fromSymbol = args.path("from_symbol").asText();
        String toSymbol = args.path("to_symbol").asText();
        String amountType = args.path("amount_type").asText();
        
        BigDecimal sellQuantity;
        
        if ("ALL".equalsIgnoreCase(amountType)) {
            // Sell entire position
            List<Position> positions = tradingService.getPortfolio(user.getId());
            Position position = positions.stream()
                    .filter(p -> p.getSymbol().equals(fromSymbol))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No position found for " + fromSymbol));
            
            if (position.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Insufficient holdings for " + fromSymbol);
            }
            sellQuantity = position.getQuantity();
        } else if ("CRYPTO".equalsIgnoreCase(amountType)) {
            BigDecimal amount = new BigDecimal(args.path("amount").asText());
            sellQuantity = amount;
        } else if ("USD".equalsIgnoreCase(amountType)) {
            BigDecimal dollarAmount = new BigDecimal(args.path("amount").asText());
            BigDecimal currentPrice = marketService.getCurrentPrice(fromSymbol);
            sellQuantity = dollarAmount.divide(currentPrice, 8, RoundingMode.HALF_UP);
        } else {
            throw new IllegalArgumentException("Invalid amount_type: " + amountType);
        }
        
        // Execute SELL order
        TradeRequest sellRequest = new TradeRequest();
        sellRequest.setSymbol(fromSymbol);
        sellRequest.setSide(Order.Side.SELL);
        sellRequest.setType(Order.Type.MARKET);
        sellRequest.setQuantity(sellQuantity);
        
        Order sellOrder = tradingService.placeOrder(user.getId(), sellRequest);
        
        // Calculate proceeds from sell (price * quantity - fee)
        BigDecimal sellPrice = sellOrder.getFilledPrice();
        BigDecimal sellValue = sellPrice.multiply(sellQuantity);
        BigDecimal sellFee = sellOrder.getFee();
        BigDecimal proceeds = sellValue.subtract(sellFee);
        
        // Execute BUY order with proceeds
        BigDecimal buyPrice = marketService.getCurrentPrice(toSymbol);
        // Account for buy fee: proceeds = buyPrice * qty + fee
        // fee = buyPrice * qty * feeRate
        // proceeds = buyPrice * qty * (1 + feeRate)
        // qty = proceeds / (buyPrice * (1 + feeRate))
        BigDecimal feeRate = new BigDecimal("0.002"); // 0.2% fee
        BigDecimal buyQuantity = proceeds.divide(buyPrice.multiply(BigDecimal.ONE.add(feeRate)), 8, RoundingMode.HALF_UP);
        
        TradeRequest buyRequest = new TradeRequest();
        buyRequest.setSymbol(toSymbol);
        buyRequest.setSide(Order.Side.BUY);
        buyRequest.setType(Order.Type.MARKET);
        buyRequest.setQuantity(buyQuantity);
        
        Order buyOrder = tradingService.placeOrder(user.getId(), buyRequest);
        
        // Return both orders
        List<Order> orders = new ArrayList<>();
        orders.add(sellOrder);
        orders.add(buyOrder);
        return orders;
    }

    @Transactional
    private List<Order> executeDiversification(User user, JsonNode args) {
        BigDecimal totalAmount = new BigDecimal(args.path("total_amount").asText());
        JsonNode allocationsNode = args.path("allocations");
        
        if (!allocationsNode.isArray()) {
            throw new IllegalArgumentException("Allocations must be an array");
        }
        
        // Validate percentages sum to 100
        BigDecimal totalPercentage = BigDecimal.ZERO;
        for (JsonNode allocation : allocationsNode) {
            BigDecimal percentage = new BigDecimal(allocation.path("percentage").asText());
            totalPercentage = totalPercentage.add(percentage);
        }
        
        if (totalPercentage.compareTo(new BigDecimal("100")) != 0) {
            throw new IllegalArgumentException("Percentages must sum to 100. Current sum: " + totalPercentage);
        }
        
        // Execute buy orders for each allocation
        List<Order> orders = new ArrayList<>();
        
        for (JsonNode allocation : allocationsNode) {
            String symbol = allocation.path("symbol").asText();
            BigDecimal percentage = new BigDecimal(allocation.path("percentage").asText());
            
            // Calculate dollar amount for this allocation
            BigDecimal allocationAmount = totalAmount.multiply(percentage).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            
            // Get current price
            BigDecimal currentPrice = marketService.getCurrentPrice(symbol);
            
            // Calculate quantity (accounting for fees)
            BigDecimal feeRate = new BigDecimal("0.002"); // 0.2% fee
            BigDecimal quantity = allocationAmount.divide(currentPrice.multiply(BigDecimal.ONE.add(feeRate)), 8, RoundingMode.HALF_UP);
            
            // Execute buy order
            TradeRequest request = new TradeRequest();
            request.setSymbol(symbol);
            request.setSide(Order.Side.BUY);
            request.setType(Order.Type.MARKET);
            request.setQuantity(quantity);
            
            Order order = tradingService.placeOrder(user.getId(), request);
            orders.add(order);
        }
        
        return orders;
    }
    
    // Generate human-readable confirmation message
    private String generateConfirmationMessage(String functionName, JsonNode args, User user) {
        try {
            switch (functionName) {
                case "execute_trade":
                    String action = args.path("action").asText();
                    String symbol = args.path("symbol").asText();
                    String amountType = args.path("amount_type").asText();
                    String amount = args.path("amount").asText();
                    
                    if ("ALL".equals(amountType)) {
                        return String.format("Do you want to %s all your %s?", action.toLowerCase(), symbol.replace("-USD", ""));
                    } else if ("USD".equals(amountType)) {
                        return String.format("Do you want to %s $%s of %s?", action.toLowerCase(), amount, symbol.replace("-USD", ""));
                    } else {
                        return String.format("Do you want to %s %s %s?", action.toLowerCase(), amount, symbol.replace("-USD", ""));
                    }
                    
                case "convert_crypto":
                    String from = args.path("from_symbol").asText().replace("-USD", "");
                    String to = args.path("to_symbol").asText().replace("-USD", "");
                    String convAmountType = args.path("amount_type").asText();
                    
                    if ("ALL".equals(convAmountType)) {
                        return String.format("Do you want to convert all your %s to %s?", from, to);
                    } else {
                        String convAmount = args.path("amount").asText();
                        return String.format("Do you want to convert %s %s to %s?", convAmount, from, to);
                    }
                    
                case "diversify_portfolio":
                    String totalAmount = args.path("total_amount").asText();
                    JsonNode allocationsNode = args.path("allocations");
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("Do you want to diversify $%s across:", totalAmount));
                    for (JsonNode allocation : allocationsNode) {
                        String sym = allocation.path("symbol").asText().replace("-USD", "");
                        String pct = allocation.path("percentage").asText();
                        sb.append(String.format(" %s%% %s,", pct, sym));
                    }
                    return sb.substring(0, sb.length() - 1) + "?";
                    
                case "create_rule":
                    String ruleSymbol = args.path("symbol").asText().replace("-USD", "");
                    String condition = args.path("condition").asText();
                    String targetPrice = args.path("targetPrice").asText();
                    String ruleAction = args.path("action").asText();
                    
                    String conditionText = condition.equals("PRICE_ABOVE") ? "goes above" : "goes below";
                    return String.format("Do you want to create a rule to %s %s when price %s $%s?", 
                        ruleAction.toLowerCase(), ruleSymbol, conditionText, targetPrice);
                    
                default:
                    return "Do you want to execute this command?";
            }
        } catch (Exception e) {
            return "Do you want to execute this command?";
        }
    }
    
    // Execute confirmed command
    @Transactional
    public Object executeConfirmation(Long userId, String confirmationId) {
        PendingCommand pending = pendingCommands.get(confirmationId);
        
        if (pending == null) {
            throw new RuntimeException("Confirmation not found or expired");
        }
        
        // Verify user owns this command
        if (!pending.getUserId().equals(userId.toString())) {
            throw new RuntimeException("Unauthorized");
        }
        
        // Remove from pending
        pendingCommands.remove(confirmationId);
        
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Execute based on function type
        String functionName = pending.getFunctionName();
        JsonNode args = pending.getArguments();
        
        switch (functionName) {
            case "execute_trade":
                return executeImmediateOrder(user, args);
            case "create_rule":
                return createRule(user, pending.getOriginalMessage(), args);
            case "convert_crypto":
                return executeCoinConversion(user, args);
            case "diversify_portfolio":
                return executeDiversification(user, args);
            default:
                throw new RuntimeException("Unknown function: " + functionName);
        }
    }
    
    // Cancel confirmation
    public void cancelConfirmation(String confirmationId) {
        pendingCommands.remove(confirmationId);
    }
}
