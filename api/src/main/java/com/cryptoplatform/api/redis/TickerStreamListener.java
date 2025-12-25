package com.cryptoplatform.api.redis;

import com.cryptoplatform.api.model.Ticker;
import com.cryptoplatform.api.websocket.PriceWebSocketHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class TickerStreamListener implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger logger = LoggerFactory.getLogger(TickerStreamListener.class);
    private final PriceWebSocketHandler webSocketHandler;

    public TickerStreamListener(PriceWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            Map<String, String> map = message.getValue();
            
            Ticker ticker = new Ticker(
                map.get("symbol"),
                Long.parseLong(map.getOrDefault("ts", "0")),
                new BigDecimal(map.getOrDefault("bid", "0")),
                new BigDecimal(map.getOrDefault("ask", "0")),
                new BigDecimal(map.getOrDefault("last", "0")),
                new BigDecimal(map.getOrDefault("volume24h", "0")),
                new BigDecimal(map.getOrDefault("change24h", "0"))
            );
            
            webSocketHandler.onTick(ticker);
            
        } catch (Exception e) {
            logger.error("Failed to process stream message", e);
        }
    }
}
