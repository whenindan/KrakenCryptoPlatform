package com.cryptoplatform.marketgateway.service;

import com.cryptoplatform.marketgateway.config.KrakenProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

@Service
public class KrakenGatewayService {

    private static final Logger logger = LoggerFactory.getLogger(KrakenGatewayService.class);
    private final ReactorNettyWebSocketClient client;
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final KrakenProperties krakenProperties;

    public KrakenGatewayService(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper,
            KrakenProperties krakenProperties) {
        this.client = new ReactorNettyWebSocketClient();
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.krakenProperties = krakenProperties;
    }

    @PostConstruct
    public void start() {
        connect();
    }

    private void connect() {
        logger.info("Connecting to Kraken WS v2: {}", krakenProperties.getUrl());

        client.execute(URI.create(krakenProperties.getUrl()), session -> {
            logger.info("Connected to Kraken WS");

            // Send subscription message
            Map<String, Object> subscribeMsg = Map.of(
                    "method", "subscribe",
                    "params", Map.of(
                            "channel", "ticker",
                            "symbol", krakenProperties.getSymbols()));

            try {
                String jsonMsg = objectMapper.writeValueAsString(subscribeMsg);
                session.send(Mono.just(session.textMessage(jsonMsg))).subscribe();
                logger.info("Sent subscription: {}", jsonMsg);
            } catch (JsonProcessingException e) {
                logger.error("Failed to serialize subscription message", e);
                return Mono.error(e);
            }

            return session.receive()
                    .timeout(Duration.ofSeconds(15))
                    .map(msg -> msg.getPayloadAsText())
                    .doOnNext(this::handleMessage)
                    .doOnError(e -> logger.error("WebSocket error", e))
                    .then();
        })
                .retryWhen(Retry.backoff(Long.MAX_VALUE, Duration.ofSeconds(2))
                        .maxBackoff(Duration.ofSeconds(30))
                        .doBeforeRetry(s -> logger.warn("Reconnecting to Kraken WS in {}", s.totalRetries())))
                .subscribe();
    }

    private void handleMessage(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);

            // Basic check for channel data
            if (root.has("channel") && "ticker".equals(root.get("channel").asText())) {
                if (root.has("data")) {
                    for (JsonNode item : root.get("data")) {
                        processTickerItem(item);
                    }
                }
            } else if (root.has("method") && "subscribe".equals(root.get("method").asText())) {
                logger.info("Subscription confirmation: {}", payload);
            } else {
                logger.debug("Received event: {}", payload);
            }
        } catch (Exception e) {
            logger.error("Failed to parse message: {}", payload, e);
        }
    }

    private void processTickerItem(JsonNode data) {
        String symbol = data.get("symbol").asText().replace("/", "-"); // Normalize BTC/USD -> BTC-USD
        long now = Instant.now().toEpochMilli();

        // Extract fields safely
        double bid = data.has("bid") ? data.get("bid").asDouble() : 0.0;
        double ask = data.has("ask") ? data.get("ask").asDouble() : 0.0;
        double last = data.has("last") ? data.get("last").asDouble() : 0.0;
        double volume = data.has("volume") ? data.get("volume").asDouble() : 0.0;
        double change = data.has("change") ? data.get("change").asDouble() : 0.0;

        String key = "latest:" + symbol;

        Map<String, String> hash = Map.of(
                "symbol", symbol,
                "ts", String.valueOf(now),
                "bid", String.valueOf(bid),
                "ask", String.valueOf(ask),
                "last", String.valueOf(last),
                "volume24h", String.valueOf(volume),
                "change24h", String.valueOf(change));

        String streamKey = "stream:market_ticks";

        redisTemplate.opsForHash().putAll(key, hash)
            .then(redisTemplate.opsForStream().add(streamKey, hash))
            .doOnSuccess(v -> logger.debug("Updated ticker and stream for {}", symbol))
            .subscribe();
    }
}
