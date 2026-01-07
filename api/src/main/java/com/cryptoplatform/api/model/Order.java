package com.cryptoplatform.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
public class Order {

    public enum Side { BUY, SELL }
    public enum Type { MARKET, LIMIT }
    public enum Status { OPEN, FILLED, CANCELLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Side side;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(nullable = false)
    private BigDecimal quantity;

    @Column(name = "limit_price")
    private BigDecimal limitPrice;

    @Column(name = "filled_price")
    private BigDecimal filledPrice;

    @Column(nullable = false)
    private BigDecimal fee;
    
    @Column(name = "kraken_txid")
    private String krakenTxid;  // For tracking Kraken transactions in live mode

    @Column(nullable = false)
    private LocalDateTime timestamp;

    public Order() {}

    public Order(User user, String symbol, Side side, Type type, BigDecimal quantity, BigDecimal limitPrice) {
        this.user = user;
        this.symbol = symbol;
        this.side = side;
        this.type = type;
        this.quantity = quantity;
        this.limitPrice = limitPrice;
        this.status = Status.OPEN;
        this.timestamp = LocalDateTime.now();
        this.fee = BigDecimal.ZERO;
        this.filledPrice = BigDecimal.ZERO;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public Side getSide() { return side; }
    public void setSide(Side side) { this.side = side; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getLimitPrice() { return limitPrice; }
    public void setLimitPrice(BigDecimal limitPrice) { this.limitPrice = limitPrice; }

    public BigDecimal getFilledPrice() { return filledPrice; }
    public void setFilledPrice(BigDecimal filledPrice) { this.filledPrice = filledPrice; }

    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }
    
    public String getKrakenTxid() { return krakenTxid; }
    public void setKrakenTxid(String krakenTxid) { this.krakenTxid = krakenTxid; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }
}
