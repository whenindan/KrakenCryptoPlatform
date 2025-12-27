package com.cryptoplatform.api.redis;

import com.cryptoplatform.api.model.Ticker;
import com.cryptoplatform.api.service.TradingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class TradeEngineListener implements StreamListener<String, MapRecord<String, String, String>> {

    private static final Logger logger = LoggerFactory.getLogger(TradeEngineListener.class);
    private final TradingService tradingService;

    public TradeEngineListener(TradingService tradingService) {
        this.tradingService = tradingService;
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
                new BigDecimal(map.getOrDefault("last", "0")), // Market Price
                new BigDecimal(map.getOrDefault("volume24h", "0")),
                new BigDecimal(map.getOrDefault("change24h", "0"))
            );
            
            tradingService.processLimitOrders(ticker);
            
        } catch (Exception e) {
            logger.error("Failed to process stream message for Trade Engine", e);
        }
    }
}
