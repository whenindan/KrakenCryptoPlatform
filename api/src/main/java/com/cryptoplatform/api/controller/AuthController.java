package com.cryptoplatform.api.controller;

import com.cryptoplatform.api.model.Account;
import com.cryptoplatform.api.model.User;
import com.cryptoplatform.api.repository.AccountRepository;
import com.cryptoplatform.api.repository.UserRepository;
import com.cryptoplatform.api.security.JwtUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, AccountRepository accountRepository, 
                          PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody AuthRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            return ResponseEntity.badRequest().body("Email already in use");
        }

        User user = new User(request.getEmail(), passwordEncoder.encode(request.getPassword()));
        
        // Create Account with 10000 balance
        Account account = new Account(new BigDecimal("10000.00"));
        user.setAccount(account);

        userRepository.save(user); // Cascades to account

        return ResponseEntity.ok("User registered successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        // In a real app, use authenticationManager.authenticate(...)
        // Here we do manual check for simplicity/explicitness with our custom setup
        User user = userRepository.findByEmail(request.getEmail())
                .orElse(null);

        if (user != null && passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            String token = jwtUtil.generateToken(user.getEmail());
            return ResponseEntity.ok(new AuthResponse(token));
        }

        return ResponseEntity.status(401).body("Invalid credentials");
    }

    // DTOs
    public static class AuthRequest {
        private String email;
        private String password;
        
        // getters setters
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class AuthResponse {
        private String token;
        public AuthResponse(String token) { this.token = token; }
        public String getToken() { return token; }
        public void setToken(String token) { this.token = token; }
    }
}
