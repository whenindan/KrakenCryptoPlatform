package com.cryptoplatform.api.service;

import com.cryptoplatform.api.model.Order;
import com.cryptoplatform.api.model.Position;
import com.cryptoplatform.api.model.User;
import com.cryptoplatform.api.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Live trading service that executes real orders on Kraken
 */
@Service
public class LiveTradingService implements TradingServiceInterface {
    
    private final KrakenApiService krakenApiService;
    private final UserRepository userRepository;
    private final com.cryptoplatform.api.repository.OrderRepository orderRepository;
    
    public LiveTradingService(KrakenApiService krakenApiService, 
                             UserRepository userRepository,
                             com.cryptoplatform.api.repository.OrderRepository orderRepository) {
        this.krakenApiService = krakenApiService;
        this.userRepository = userRepository;
        this.orderRepository =orderRepository;
    }
    
    @Override
    public Order placeOrder(Long userId, TradeRequest request) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Map symbol (BTC-USD → XBTUSDT for Kraken)
            String krakenPair = mapSymbolToKraken(request.getSymbol());
            
            // Map side (BUY/SELL)
            String type = request.getSide() == Order.Side.BUY ? "buy" : "sell";
            
            // Map order type (MARKET/LIMIT)
            String orderType = request.getType() == Order.Type.MARKET ? "market" : "limit";
            
            // Place order on Kraken
            JsonNode response = krakenApiService.placeOrder(
                krakenPair,
                type,
                orderType,
                request.getQuantity(),
                request.getLimitPrice()
            );
            
            // Create Order object
            Order order = new Order(
                user,
                request.getSymbol(),
                request.getSide(),
                request.getType(),
                request.getQuantity(),
                request.getLimitPrice()
            );
            
            // Parse Kraken response and extract transaction ID
            JsonNode result = response.path("result");
            if (result.has("txid")) {
                JsonNode txids = result.get("txid");
                if (txids.isArray() && txids.size() > 0) {
                    String krakenTxid = txids.get(0).asText();
                    order.setKrakenTxid(krakenTxid);
                    
                    // For market orders, mark as filled immediately
                    // For limit orders, mark as open
                    if (request.getType() == Order.Type.MARKET) {
                        order.setStatus(Order.Status.FILLED);
                        // Note: Kraken doesn't immediately return fill price for market orders
                        // In production, you'd query the order status to get the actual fill price
                    } else {
                        order.setStatus(Order.Status.OPEN);
                    }
                }
            }
            
            // Save order to database
            return orderRepository.save(order);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to place live order on Kraken: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Position> getPortfolio(Long userId) {
        try {
            JsonNode balances = krakenApiService.getBalance();
            List<Position> positions = new ArrayList<>();
            
            // Parse Kraken balance response
            Iterator<String> fieldNames = balances.fieldNames();
            while (fieldNames.hasNext()) {
                String asset = fieldNames.next();
                String balanceStr = balances.get(asset).asText();
                BigDecimal balance = new BigDecimal(balanceStr);
                
                if (balance.compareTo(BigDecimal.ZERO) > 0) {
                    // Map Kraken asset names back to our symbols
                    String symbol = mapKrakenAssetToSymbol(asset);
                    if (symbol != null) {
                        Position position = new Position();
                        position.setSymbol(symbol);
                        position.setQuantity(balance);
                        // Note: Kraken doesn't provide avg entry price directly
                        // We'd need to calculate from trade history
                        position.setAvgEntryPrice(BigDecimal.ZERO);
                        positions.add(position);
                    }
                }
            }
            
            return positions;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Kraken portfolio: " + e.getMessage(), e);
        }
    }
    
    @Override
    public List<Order> getOpenOrders(Long userId) {
        try {
            JsonNode openOrders = krakenApiService.getOpenOrders();
            List<Order> orders = new ArrayList<>();
            
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found"));
            
            // Parse open orders from Kraken
            JsonNode open = openOrders.path("open");
            Iterator<String> orderIds = open.fieldNames();
            
            while (orderIds.hasNext()) {
                String orderId = orderIds.next();
                JsonNode orderData = open.get(orderId);
                
                // Parse order details
                JsonNode descr = orderData.path("descr");
                String pair = descr.path("pair").asText();
                String type = descr.path("type").asText();
                String ordertype = descr.path("ordertype").asText();
                BigDecimal volume = new BigDecimal(orderData.path("vol").asText());
                
                Order order = new Order();
                order.setUser(user);
                order.setSymbol(mapKrakenPairToSymbol(pair));
                order.setSide("buy".equals(type) ? Order.Side.BUY : Order.Side.SELL);
                order.setType("market".equals(ordertype) ? Order.Type.MARKET : Order.Type.LIMIT);
                order.setQuantity(volume);
                order.setStatus(Order.Status.OPEN);
                
                if (descr.has("price")) {
                    order.setLimitPrice(new BigDecimal(descr.path("price").asText()));
                }
                
                orders.add(order);
            }
            
            return orders;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Kraken open orders: " + e.getMessage(), e);
        }
    }
    
    @Override
    public void cancelOrder(Long userId, Long orderId) {
        try {
            // Get order from database
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not found"));
            
            // Verify user owns this order
            if (!order.getUser().getId().equals(userId)) {
                throw new RuntimeException("Unauthorized: Order does not belong to user");
            }
            
            // Check if order can be cancelled
            if (order.getStatus() != Order.Status.OPEN) {
                throw new RuntimeException("Cannot cancel order: Order is already " + order.getStatus());
            }
            
            // Get Kraken transaction ID
            if (order.getKrakenTxid() == null || order.getKrakenTxid().isEmpty()) {
                throw new RuntimeException("Cannot cancel: No Kraken transaction ID found");
            }
            
            // Cancel on Kraken
            krakenApiService.cancelOrder(order.getKrakenTxid());
            
            // Update order status in database
            order.setStatus(Order.Status.CANCELLED);
            orderRepository.save(order);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to cancel Kraken order: " + e.getMessage(), e);
        }
    }
    
    @Override
    public BigDecimal getBalance(Long userId) {
        try {
            JsonNode balances = krakenApiService.getBalance();
            
            // Get USD balance (ZUSD in Kraken)
            if (balances.has("ZUSD")) {
                return new BigDecimal(balances.get("ZUSD").asText());
            }
            
            return BigDecimal.ZERO;
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Kraken balance: " + e.getMessage(), e);
        }
    }
    
    // Symbol mapping helpers
    private String mapSymbolToKraken(String symbol) {
        // Map our symbols to Kraken pairs
        switch (symbol) {
            case "BTC-USD": return "XXBTZUSD";
            case "ETH-USD": return "XETHZUSD";
            case "SOL-USD": return "SOLUSD";
            case "XRP-USD": return "XXRPZUSD";
            default:
                throw new IllegalArgumentException("Unsupported symbol for Kraken: " + symbol);
        }
    }
    
    private String mapKrakenAssetToSymbol(String krakenAsset) {
        // Map Kraken asset names to our symbols
        switch (krakenAsset) {
            case "XXBT":
            case "XBT": return "BTC-USD";
            case "XETH":
            case "ETH": return "ETH-USD";
            case "SOL": return "SOL-USD";
            case "XXRP":
            case "XRP": return "XRP-USD";
            case "ZUSD":
            case "USD": return null; // USD is not a position, it's balance
            default: return null;
        }
    }
    
    private String mapKrakenPairToSymbol(String krakenPair) {
        // Map Kraken pairs back to our symbols
        if (krakenPair.contains("XBT")) return "BTC-USD";
        if (krakenPair.contains("ETH")) return "ETH-USD";
        if (krakenPair.contains("SOL")) return "SOL-USD";
        if (krakenPair.contains("XRP")) return "XRP-USD";
        return krakenPair; // fallback
    }
}
