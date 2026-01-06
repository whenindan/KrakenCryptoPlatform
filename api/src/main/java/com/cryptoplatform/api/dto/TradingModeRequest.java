package com.cryptoplatform.api.dto;

public class TradingModeRequest {
    private String mode; // "PAPER" or "LIVE"
    
    public TradingModeRequest() {}
    
    public TradingModeRequest(String mode) {
        this.mode = mode;
    }
    
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
