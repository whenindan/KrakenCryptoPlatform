package com.cryptoplatform.api.service;

import com.cryptoplatform.api.model.Order;
import java.math.BigDecimal;

public class TradeRequest {
    private String symbol;
    private Order.Side side;
    private Order.Type type;
    private BigDecimal quantity;
    private BigDecimal limitPrice; // Optional

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public Order.Side getSide() { return side; }
    public void setSide(Order.Side side) { this.side = side; }

    public Order.Type getType() { return type; }
    public void setType(Order.Type type) { this.type = type; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getLimitPrice() { return limitPrice; }
    public void setLimitPrice(BigDecimal limitPrice) { this.limitPrice = limitPrice; }
}
