package com.cryptoplatform.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MarketControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private HashOperations<String, Object, Object> hashOperations;

    @Test
    void shouldReturnLatestPriceForSymbol() throws Exception {
        // Given
        when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        when(hashOperations.entries("latest:BTC-USD")).thenReturn(Map.of(
            "symbol", "BTC-USD",
            "ts", "1700000000000",
            "bid", "50000.00",
            "ask", "50001.00",
            "last", "50000.50",
            "volume24h", "100.5",
            "change24h", "1.2"
        ));

        // When & Then
        mockMvc.perform(get("/prices/latest?symbol=BTC-USD"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.symbol").value("BTC-USD"))
            .andExpect(jsonPath("$.last").value(50000.50));
    }

    @Test
    void shouldReturn404ForUnknownSymbol() throws Exception {
        mockMvc.perform(get("/prices/latest?symbol=UNKNOWN"))
            .andExpect(status().isNotFound());
    }
    
    @Test
    void shouldReturnSupportedMarkets() throws Exception {
        mockMvc.perform(get("/markets"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0]").value("BTC-USD"))
            .andExpect(jsonPath("$[1]").value("ETH-USD"));
    }
}
