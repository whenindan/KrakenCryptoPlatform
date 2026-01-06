package com.cryptoplatform.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class KrakenApiService {
    
    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Value("${kraken.api.key:}")
    private String apiKey;
    
    @Value("${kraken.api.secret:}")
    private String apiSecret;
    
    private static final String KRAKEN_API_URL = "https://api.kraken.com";
    
    public KrakenApiService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder()
                .baseUrl(KRAKEN_API_URL)
                .build();
    }
    
    /**
     * Generate HMAC-SHA512 signature for Kraken API authentication
     */
    private String generateSignature(String path, String nonce, String postData) {
        try {
            // Step 1: SHA256(nonce + postdata)
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String noncePostData = nonce + postData;
            byte[] sha256Hash = md.digest(noncePostData.getBytes(StandardCharsets.UTF_8));
            
            // Step 2: path + sha256Hash
            byte[] pathBytes = path.getBytes(StandardCharsets.UTF_8);
            byte[] message = new byte[pathBytes.length + sha256Hash.length];
            System.arraycopy(pathBytes, 0, message, 0, pathBytes.length);
            System.arraycopy(sha256Hash, 0, message, pathBytes.length, sha256Hash.length);
            
            // Step 3: HMAC-SHA512(message, base64decode(API-Secret))
            byte[] secretDecoded = Base64.getDecoder().decode(apiSecret);
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKey = new SecretKeySpec(secretDecoded, "HmacSHA512");
            mac.init(secretKey);
            byte[] signature = mac.doFinal(message);
            
            return Base64.getEncoder().encodeToString(signature);
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate Kraken API signature", e);
        }
    }
    
    /**
     * Place an order on Kraken
     */
    public JsonNode placeOrder(String pair, String type, String orderType, BigDecimal volume, BigDecimal price) {
        try {
            validateApiCredentials();
            
            String path = "/0/private/AddOrder";
            String nonce = String.valueOf(System.currentTimeMillis() * 1000);
            
            Map<String, String> params = new HashMap<>();
            params.put("nonce", nonce);
            params.put("pair", pair);
            params.put("type", type); // buy or sell
            params.put("ordertype", orderType); // market or limit
            params.put("volume", volume.toPlainString());
            
            if ("limit".equals(orderType) && price != null) {
                params.put("price", price.toPlainString());
            }
            
            String postData = buildPostData(params);
            String signature = generateSignature(path, nonce, postData);
            
            String response = webClient.post()
                    .uri(path)
                    .header("API-Key", apiKey)
                    .header("API-Sign", signature)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue(postData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode jsonResponse = objectMapper.readTree(response);
            checkKrakenErrors(jsonResponse);
            
            return jsonResponse;
        } catch (Exception e) {
            throw new RuntimeException("Failed to place Kraken order: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get account balance from Kraken
     */
    public JsonNode getBalance() {
        try {
            validateApiCredentials();
            
            String path = "/0/private/Balance";
            String nonce = String.valueOf(System.currentTimeMillis() * 1000);
            
            Map<String, String> params = new HashMap<>();
            params.put("nonce", nonce);
            
            String postData = buildPostData(params);
            String signature = generateSignature(path, nonce, postData);
            
            String response = webClient.post()
                    .uri(path)
                    .header("API-Key", apiKey)
                    .header("API-Sign", signature)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue(postData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode jsonResponse = objectMapper.readTree(response);
            checkKrakenErrors(jsonResponse);
            
            return jsonResponse.path("result");
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Kraken balance: " + e.getMessage(), e);
        }
    }
    
    /**
     * Get open orders from Kraken
     */
    public JsonNode getOpenOrders() {
        try {
            validateApiCredentials();
            
            String path = "/0/private/OpenOrders";
            String nonce = String.valueOf(System.currentTimeMillis() * 1000);
            
            Map<String, String> params = new HashMap<>();
            params.put("nonce", nonce);
            
            String postData = buildPostData(params);
            String signature = generateSignature(path, nonce, postData);
            
            String response = webClient.post()
                    .uri(path)
                    .header("API-Key", apiKey)
                    .header("API-Sign", signature)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue(postData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode jsonResponse = objectMapper.readTree(response);
            checkKrakenErrors(jsonResponse);
            
            return jsonResponse.path("result");
        } catch (Exception e) {
            throw new RuntimeException("Failed to get Kraken open orders: " + e.getMessage(), e);
        }
    }
    
    /**
     * Cancel an order on Kraken
     */
    public JsonNode cancelOrder(String txid) {
        try {
            validateApiCredentials();
            
            String path = "/0/private/CancelOrder";
            String nonce = String.valueOf(System.currentTimeMillis() * 1000);
            
            Map<String, String> params = new HashMap<>();
            params.put("nonce", nonce);
            params.put("txid", txid);
            
            String postData = buildPostData(params);
            String signature = generateSignature(path, nonce, postData);
            
            String response = webClient.post()
                    .uri(path)
                    .header("API-Key", apiKey)
                    .header("API-Sign", signature)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .bodyValue(postData)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            JsonNode jsonResponse = objectMapper.readTree(response);
            checkKrakenErrors(jsonResponse);
            
            return jsonResponse;
        } catch (Exception e) {
            throw new RuntimeException("Failed to cancel Kraken order: " + e.getMessage(), e);
        }
    }
    
    /**
     * Test API connection and credentials
     */
    public boolean testConnection() {
        try {
            validateApiCredentials();
            getBalance();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    private void validateApiCredentials() {
        if (apiKey == null || apiKey.isEmpty() || apiSecret == null || apiSecret.isEmpty()) {
            throw new IllegalStateException("Kraken API credentials not configured. Please set KRAKEN_API_KEY and KRAKEN_API_SECRET in environment variables.");
        }
    }
    
    private void checkKrakenErrors(JsonNode response) {
        JsonNode errors = response.path("error");
        if (errors.isArray() && errors.size() > 0) {
            throw new RuntimeException("Kraken API error: " + errors.toString());
        }
    }
    
    private String buildPostData(Map<String, String> params) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (sb.length() > 0) {
                sb.append("&");
            }
            sb.append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }
}
