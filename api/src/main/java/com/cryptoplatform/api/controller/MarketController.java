package com.cryptoplatform.api.controller;

import com.cryptoplatform.api.model.Ticker;
import com.cryptoplatform.api.service.MarketService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    @GetMapping("/markets")
    public List<String> getMarkets() {
        return marketService.getSupportedMarkets();
    }

    @GetMapping("/prices/latest")
    public ResponseEntity<?> getLatestPrices(@RequestParam(required = false) String symbol) {
        if (symbol != null) {
            return marketService.getLatestPrice(symbol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
        }
        return ResponseEntity.ok(marketService.getAllLatestPrices());
    }
}
