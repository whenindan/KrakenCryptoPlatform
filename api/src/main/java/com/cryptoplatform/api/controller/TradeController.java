package com.cryptoplatform.api.controller;

import com.cryptoplatform.api.model.Order;
import com.cryptoplatform.api.model.Position;
import com.cryptoplatform.api.model.User;
import com.cryptoplatform.api.repository.UserRepository;
import com.cryptoplatform.api.service.TradeRequest;
import com.cryptoplatform.api.service.TradingService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/trade")
public class TradeController {

    private final TradingService tradingService;
    private final UserRepository userRepository;

    public TradeController(TradingService tradingService, UserRepository userRepository) {
        this.tradingService = tradingService;
        this.userRepository = userRepository;
    }

    private Long getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String email = (String) auth.getPrincipal();
        User user = userRepository.findByEmail(email).orElseThrow(() -> new RuntimeException("User not found"));
        return user.getId();
    }

    @PostMapping("/orders")
    public ResponseEntity<Order> placeOrder(@RequestBody TradeRequest request) {
        Order order = tradingService.placeOrder(getCurrentUserId(), request);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/orders")
    public ResponseEntity<List<Order>> getOrderHistory() {
        return ResponseEntity.ok(tradingService.getOrderHistory(getCurrentUserId()));
    }

    @GetMapping("/portfolio")
    public ResponseEntity<List<Position>> getPortfolio() {
        return ResponseEntity.ok(tradingService.getPortfolio(getCurrentUserId()));
    }
}
