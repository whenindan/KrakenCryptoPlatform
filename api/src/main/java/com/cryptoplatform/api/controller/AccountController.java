package com.cryptoplatform.api.controller;

import com.cryptoplatform.api.dto.TradingModeRequest;
import com.cryptoplatform.api.dto.TradingModeResponse;
import com.cryptoplatform.api.model.User;
import com.cryptoplatform.api.repository.UserRepository;
import com.cryptoplatform.api.service.KrakenApiService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/account")
public class AccountController {

    private final UserRepository userRepository;
    private final KrakenApiService krakenApiService;

    public AccountController(UserRepository userRepository, KrakenApiService krakenApiService) {
        this.userRepository = userRepository;
        this.krakenApiService = krakenApiService;
    }

    @GetMapping("/balance")
    public ResponseEntity<Map<String, BigDecimal>> getBalance() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (String) auth.getPrincipal();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        BigDecimal balance;
        
        // If in LIVE mode, fetch balance from Kraken
        if (user.getTradingMode() == User.TradingMode.LIVE) {
            try {
                com.fasterxml.jackson.databind.JsonNode krakenBalance = krakenApiService.getBalance();
                // Get USD balance from Kraken (ZUSD)
                balance = krakenBalance.has("ZUSD") ? 
                    new BigDecimal(krakenBalance.get("ZUSD").asText()) : 
                    BigDecimal.ZERO;
            } catch (Exception e) {
                // Fallback to paper balance if Kraken fetch fails
                balance = user.getAccount().getBalance();
            }
        } else {
            // PAPER mode - use paper trading balance
            balance = user.getAccount().getBalance();
        }

        return ResponseEntity.ok(Collections.singletonMap("balance", balance));
    }
    
    @GetMapping("/trading-mode")
    public ResponseEntity<TradingModeResponse> getTradingMode() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (String) auth.getPrincipal();
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        boolean krakenConnected = false;
        if (user.getTradingMode() == User.TradingMode.LIVE) {
            krakenConnected = krakenApiService.testConnection();
        }
        
        String message = user.getTradingMode() == User.TradingMode.PAPER 
            ? "Paper trading mode (simulated trades)" 
            : "Live trading mode (real Kraken trades)";
            
        return ResponseEntity.ok(new TradingModeResponse(user.getTradingMode(), krakenConnected, message));
    }
    
    @PostMapping("/trading-mode")
    public ResponseEntity<TradingModeResponse> setTradingMode(@RequestBody TradingModeRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (String) auth.getPrincipal();
        
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        // Parse and validate mode
        User.TradingMode newMode;
        try {
            newMode = User.TradingMode.valueOf(request.getMode().toUpperCase());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(
                new TradingModeResponse(user.getTradingMode(), false, "Invalid mode. Must be PAPER or LIVE")
            );
        }
        
        // If switching to LIVE, test Kraken connection
        if (newMode == User.TradingMode.LIVE) {
            try {
                boolean connected = krakenApiService.testConnection();
                if (!connected) {
                    return ResponseEntity.badRequest().body(
                        new TradingModeResponse(user.getTradingMode(), false, 
                            "Cannot switch to LIVE mode: Kraken API connection failed. Please check your API credentials.")
                    );
                }
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(
                    new TradingModeResponse(user.getTradingMode(), false, 
                        "Cannot switch to LIVE mode: " + e.getMessage())
                );
            }
        }
        
        // Update mode
        user.setTradingMode(newMode);
        userRepository.save(user);
        
        String message = newMode == User.TradingMode.PAPER
            ? "Switched to paper trading mode (simulated trades)"
            : "⚠️ Switched to LIVE trading mode. All trades will execute with REAL MONEY on Kraken!";
        
        return ResponseEntity.ok(new TradingModeResponse(newMode, newMode == User.TradingMode.LIVE, message));
    }
}
