package com.cryptoplatform.api.service;

import com.cryptoplatform.api.model.Ticker;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class MarketService {

    // Hardcoded allowed symbols as per requirements
    private static final List<String> SUPPORTED_SYMBOLS = List.of("BTC-USD", "ETH-USD");
    
    private final StringRedisTemplate redisTemplate;

    public MarketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public List<String> getSupportedMarkets() {
        return SUPPORTED_SYMBOLS;
    }

    public Optional<Ticker> getLatestPrice(String symbol) {
        if (!SUPPORTED_SYMBOLS.contains(symbol)) {
            return Optional.empty();
        }

        String key = "latest:" + symbol;
        Map<Object, Object> rawHash = redisTemplate.opsForHash().entries(key);

        if (rawHash.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(mapToTicker(rawHash));
    }

    public Map<String, Ticker> getAllLatestPrices() {
        return SUPPORTED_SYMBOLS.stream()
            .map(this::getLatestPrice)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toMap(Ticker::symbol, ticker -> ticker));
    }

    private Ticker mapToTicker(Map<Object, Object> hash) {
        return new Ticker(
            (String) hash.get("symbol"),
            Long.parseLong((String) hash.getOrDefault("ts", "0")),
            new BigDecimal((String) hash.getOrDefault("bid", "0")),
            new BigDecimal((String) hash.getOrDefault("ask", "0")),
            new BigDecimal((String) hash.getOrDefault("last", "0")),
            new BigDecimal((String) hash.getOrDefault("volume24h", "0")),
            new BigDecimal((String) hash.getOrDefault("change24h", "0"))
        );
    }
}
