package com.cryptoplatform.api.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "agent_rules")
public class AgentRule {

    public enum Condition {
        PRICE_ABOVE,
        PRICE_BELOW,
        PRICE_EQUALS
    }

    public enum Action {
        BUY,
        SELL
    }

    public enum AmountType {
        USD,
        CRYPTO,
        ALL
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @com.fasterxml.jackson.annotation.JsonIgnore
    private User user;

    @Column(nullable = false, length = 500)
    private String ruleText;

    @Column(nullable = false)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Condition condition;

    @Column(nullable = false)
    private BigDecimal targetPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Action action;

    @Enumerated(EnumType.STRING)
    @Column(name = "amount_type")
    private AmountType amountType = AmountType.USD; // Default for backward compatibility

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime executedAt;

    public AgentRule() {
        this.createdAt = LocalDateTime.now();
    }

    public AgentRule(User user, String ruleText, String symbol, Condition condition, 
                     BigDecimal targetPrice, Action action, AmountType amountType, BigDecimal amount) {
        this.user = user;
        this.ruleText = ruleText;
        this.symbol = symbol;
        this.condition = condition;
        this.targetPrice = targetPrice;
        this.action = action;
        this.amountType = amountType;
        this.amount = amount;
        this.isActive = true;
        this.createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public String getRuleText() { return ruleText; }
    public void setRuleText(String ruleText) { this.ruleText = ruleText; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public Condition getCondition() { return condition; }
    public void setCondition(Condition condition) { this.condition = condition; }

    public BigDecimal getTargetPrice() { return targetPrice; }
    public void setTargetPrice(BigDecimal targetPrice) { this.targetPrice = targetPrice; }

    public Action getAction() { return action; }
    public void setAction(Action action) { this.action = action; }

    public AmountType getAmountType() { return amountType; }
    public void setAmountType(AmountType amountType) { this.amountType = amountType; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getExecutedAt() { return executedAt; }
    public void setExecutedAt(LocalDateTime executedAt) { this.executedAt = executedAt; }
}
