package com.cryptoplatform.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "positions")
public class Position {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Account account;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "avg_entry_price")
    private BigDecimal avgEntryPrice; // Optional: track average entry

    public Position() {}

    public Position(Account account, String symbol, BigDecimal quantity) {
        this.account = account;
        this.symbol = symbol;
        this.quantity = quantity;
        this.avgEntryPrice = BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getAvgEntryPrice() { return avgEntryPrice; }
    public void setAvgEntryPrice(BigDecimal avgEntryPrice) { this.avgEntryPrice = avgEntryPrice; }
}
