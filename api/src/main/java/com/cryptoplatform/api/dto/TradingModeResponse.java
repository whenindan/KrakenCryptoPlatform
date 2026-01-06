package com.cryptoplatform.api.dto;

import com.cryptoplatform.api.model.User;

public class TradingModeResponse {
    private String mode; // "PAPER" or "LIVE"
    private boolean krakenConnected;
    private String message;
    
    public TradingModeResponse() {}
    
    public TradingModeResponse(User.TradingMode mode, boolean krakenConnected, String message) {
        this.mode = mode.name();
        this.krakenConnected = krakenConnected;
        this.message = message;
    }
    
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    
    public boolean isKrakenConnected() { return krakenConnected; }
    public void setKrakenConnected(boolean krakenConnected) { this.krakenConnected = krakenConnected; }
    
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
