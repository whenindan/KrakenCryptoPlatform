package com.cryptoplatform.api.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    public enum TradingMode {
        PAPER,  // Simulated trading (default)
        LIVE    // Real Kraken API trading
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String password;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "trading_mode")
    private TradingMode tradingMode = TradingMode.PAPER; // Default to paper trading for safety

    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Account account;

    public User() {}

    public User(String email, String password) {
        this.email = email;
        this.password = password;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public Account getAccount() { return account; }
    public void setAccount(Account account) {
        this.account = account;
        if (account != null) {
            account.setUser(this);
        }
    }
    
    public TradingMode getTradingMode() { return tradingMode; }
    public void setTradingMode(TradingMode tradingMode) { this.tradingMode = tradingMode; }
}
