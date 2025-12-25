package com.cryptoplatform.api.websocket;

import com.cryptoplatform.api.model.Ticker;
import com.cryptoplatform.api.service.MarketService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class PriceWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(PriceWebSocketHandler.class);
    private final ObjectMapper objectMapper;
    
    // Session -> Set of Symbols
    private final Map<WebSocketSession, Set<String>> sessionSubscriptions = new ConcurrentHashMap<>();
    
    // Session -> Symbol -> Last Ticker (to detect changes)
    private final Map<WebSocketSession, Map<String, Ticker>> lastSentTickers = new ConcurrentHashMap<>();

    // Rate Limiting (Token Bucket - simplify to counter reset every second)
    private final Map<WebSocketSession, AtomicInteger> messageCounter = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    public PriceWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        
        // Reset rate limits every second
        scheduler.scheduleAtFixedRate(this::resetRateLimits, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessionSubscriptions.put(session, Collections.synchronizedSet(new HashSet<>()));
        lastSentTickers.put(session, new ConcurrentHashMap<>());
        messageCounter.put(session, new AtomicInteger(0));
        logger.info("New WebSocket connection: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionSubscriptions.remove(session);
        lastSentTickers.remove(session);
        messageCounter.remove(session);
        logger.info("WebSocket connection closed: {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            if (node.has("type") && "subscribe".equals(node.get("type").asText())) {
                if (node.has("symbols")) {
                    Set<String> newSymbols = new HashSet<>();
                    for (JsonNode s : node.get("symbols")) {
                        newSymbols.add(s.asText());
                    }
                    
                    sessionSubscriptions.get(session).addAll(newSymbols);
                    
                    // Send confirmation
                    Map<String, Object> response = Map.of(
                        "type", "subscribed",
                        "symbols", newSymbols
                    );
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
                }
            }
        } catch (Exception e) {
            logger.error("Error handling message from session {}", session.getId(), e);
        }
    }

    public void onTick(Ticker currentTicker) {
        sessionSubscriptions.forEach((session, symbols) -> {
            if (!session.isOpen()) return;
            if (!symbols.contains(currentTicker.symbol())) return;

            AtomicInteger counter = messageCounter.get(session);
            // Check rate limit (max 5 per second)
            if (counter.get() >= 5) return;

            // Check if changed
            Ticker lastSent = lastSentTickers.get(session).get(currentTicker.symbol());
            if (isDifferent(lastSent, currentTicker)) {
                try {
                    // Send update
                    Map<String, Object> msg = Map.of(
                        "type", "tick",
                        "symbol", currentTicker.symbol(),
                        "last", currentTicker.last(),
                        "bid", currentTicker.bid(),
                        "ask", currentTicker.ask(),
                        "ts", currentTicker.tsEpochMs()
                    );
                    
                    session.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
                    
                    // Update state
                    lastSentTickers.get(session).put(currentTicker.symbol(), currentTicker);
                    counter.incrementAndGet();
                    
                } catch (IOException e) {
                    logger.error("Failed to send message to session {}", session.getId(), e);
                }
            }
        });
    }

    private boolean isDifferent(Ticker last, Ticker current) {
        if (last == null) return true;
        // Compare request timestamps or specific fields. 
        return last.tsEpochMs() != current.tsEpochMs();
    }

    private void resetRateLimits() {
        messageCounter.values().forEach(c -> c.set(0));
    }
}
