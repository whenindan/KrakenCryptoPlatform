package com.cryptoplatform.api.dto;

import com.fasterxml.jackson.databind.JsonNode;

public class PendingCommand {
    private String userId;
    private String  functionName;
    private JsonNode arguments;
    private String originalMessage;
    private long timestamp;
    
    public PendingCommand() {
        this.timestamp = System.currentTimeMillis();
    }
    
    public PendingCommand(String userId, String functionName, JsonNode arguments, String originalMessage) {
        this.userId = userId;
        this.functionName = functionName;
        this.arguments = arguments;
        this.originalMessage = originalMessage;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Getters and setters
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    
    public String getFunctionName() { return functionName; }
    public void setFunctionName(String functionName) { this.functionName = functionName; }
    
    public JsonNode getArguments() { return arguments; }
    public void setArguments(JsonNode arguments) { this.arguments = arguments; }
    
    public String getOriginalMessage() { return originalMessage; }
    public void setOriginalMessage(String originalMessage) { this.originalMessage = originalMessage; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
