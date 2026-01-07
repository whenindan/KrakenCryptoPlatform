package com.cryptoplatform.api.service;

import com.cryptoplatform.api.model.Order;
import com.cryptoplatform.api.model.Position;
import com.cryptoplatform.api.model.Ticker;
import com.cryptoplatform.api.service.TradeRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class AiService {

    private static final Logger logger = LoggerFactory.getLogger(AiService.class);
    
    // We use the same model as requested
    private static final String OPENAI_MODEL = "gpt-4o-mini";
    private static final String OPENAI_URL = "https://api.openai.com/v1/chat/completions";

    private final String openAiApiKey;
    private final TradingServiceRouter tradingService;
    private final MarketService marketService;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public AiService(@Value("${ai.api.key}") String openAiApiKey,
                     TradingServiceRouter tradingService,
                     MarketService marketService,
                     ObjectMapper objectMapper) {
        this.openAiApiKey = openAiApiKey;
        this.tradingService = tradingService;
        this.marketService = marketService;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().build();
    }

    public String processCommand(Long userId, String userMessage) {
        // 1. Gather Context
        String context = buildContext(userId);

        // 2. Build Prompt
        String systemPrompt = """
            You are a Crypto Trading AI Agent. Your goal is to help the user execute trades on their Paper Trading account.
            
            Current Market Data and User Portfolio is provided in the Context.
            
            You can execute the following actions by returning a JSON object.
            Do NOT return markdown. Return ONLY the raw JSON.
            
            If the user asks to buy or sell, calculate the quantity if they say "50%" or "all" based on their balance/portfolio.
            If they ask to buy a specific amount (e.g. "Buy 0.1 BTC"), use that.
            
            Output Schema:
            {
                "response": "Text response to user",
                "orders": [
                    {
                        "symbol": "BTC-USD",
                        "side": "BUY" or "SELL",
                        "type": "MARKET" or "LIMIT",
                        "quantity": 1.23,
                        "limitPrice": 50000.0 (optional, only for LIMIT)
                    }
                ]
            }
            
            Rules:
            - If the user wants to buy but has insufficient funds, tell them.
            - If the user wants to sell but has no position, tell them.
            - For "Sell all BTC when price > 90000", create a LIMIT SELL order at 90000.
            - For "Sell 50% ETH and buy SOL", generate TWO orders. Calculate the estimated proceeds from ETH sell to determine SOL buy quantity (use current SOL price).
            - Supported Symbols: BTC-USD, ETH-USD, XRP-USD, SOL-USD, USDT-USD, BNB-USD, USDC-USD.
            - If the intent is unclear, asking for clarification in 'response' and empty 'orders'.
            """;

        String userPrompt = "Context:\n" + context + "\n\nUser Request: " + userMessage;

        // 3. Call OpenAI
        try {
            String jsonResponse = callOpenAi(systemPrompt, userPrompt);
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            String content = root.path("choices").get(0).path("message").path("content").asText();
            // Clean markdown code blocks if any
            if (content.startsWith("```json")) {
                content = content.substring(7);
                if (content.endsWith("```")) {
                    content = content.substring(0, content.length() - 3);
                }
            }
            
            JsonNode result = objectMapper.readTree(content);
            String textResponse = result.path("response").asText();
            
            // 4. Execute Orders
            if (result.has("orders")) {
                for (JsonNode orderNode : result.get("orders")) {
                    executeOrder(userId, orderNode);
                }
            }
            
            return textResponse;

        } catch (Exception e) {
            logger.error("AI processing failed", e);
            return "Sorry, I encountered an error processing your request.";
        }
    }

    private void executeOrder(Long userId, JsonNode orderNode) {
        try {
            String symbol = orderNode.get("symbol").asText();
            Order.Side side = Order.Side.valueOf(orderNode.get("side").asText().toUpperCase());
            Order.Type type = Order.Type.valueOf(orderNode.get("type").asText().toUpperCase());
            BigDecimal qty = new BigDecimal(orderNode.get("quantity").asText());
            BigDecimal limitPrice = orderNode.has("limitPrice") ? new BigDecimal(orderNode.get("limitPrice").asText()) : null;

            TradeRequest request = new TradeRequest();
            request.setSymbol(symbol);
            request.setSide(side);
            request.setType(type);
            request.setQuantity(qty);
            request.setLimitPrice(limitPrice);

            tradingService.placeOrder(userId, request);
            logger.info("Executed AI Order: {} {} {}", side, qty, symbol);
        } catch (Exception e) {
            logger.error("Failed to execute AI order", e);
        }
    }

    private String buildContext(Long userId) {
        StringBuilder sb = new StringBuilder();
        
        // Market Data
        Map<String, Ticker> prices = marketService.getAllLatestPrices();
        sb.append("Current Prices:\n");
        prices.forEach((k, v) -> sb.append(k).append(": ").append(v.last()).append("\n"));
        
        // Portfolio
        List<Position> portfolio = tradingService.getPortfolio(userId);
        sb.append("\nUser Portfolio:\n");
        for (Position p : portfolio) {
            sb.append(p.getSymbol()).append(": ").append(p.getQuantity()).append("\n");
        }
        
        return sb.toString();
    }

    private String callOpenAi(String system, String user) throws JsonProcessingException {
        Map<String, Object> request = Map.of(
            "model", OPENAI_MODEL,
            "messages", List.of(
                Map.of("role", "system", "content", system),
                Map.of("role", "user", "content", user)
            ),
            "temperature", 0.0
        );
        
        String requestJson = objectMapper.writeValueAsString(request);
        
        return restClient.post()
            .uri(OPENAI_URL)
            .header("Authorization", "Bearer " + openAiApiKey)
            .header("Content-Type", "application/json")
            .body(requestJson)
            .retrieve()
            .body(String.class);
    }
}