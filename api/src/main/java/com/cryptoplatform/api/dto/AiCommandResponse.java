package com.cryptoplatform.api.dto;

import com.cryptoplatform.api.model.AgentRule;
import com.cryptoplatform.api.model.Order;

public class AiCommandResponse {
    private boolean success;
    private String message;
    private Order orderExecuted;
    private AgentRule ruleCreated;
    
    // Confirmation support
    private boolean requiresConfirmation;
    private String confirmationId;
    private String confirmationMessage;
    private Object pendingAction; // Store the parsed command for execution after confirmation

    public AiCommandResponse() {}

    public AiCommandResponse(boolean success, String message) {
        this.success = success;
        this.message = message;
    }

    public static AiCommandResponse success(String message) {
        return new AiCommandResponse(true, message);
    }

    public static AiCommandResponse error(String message) {
        return new AiCommandResponse(false, message);
    }

    public static AiCommandResponse withOrder(Order order, String message) {
        AiCommandResponse response = new AiCommandResponse(true, message);
        response.setOrderExecuted(order);
        return response;
    }

    public static AiCommandResponse withRule(AgentRule rule, String message) {
        AiCommandResponse response = new AiCommandResponse(true, message);
        response.setRuleCreated(rule);
        return response;
    }
    
    public static AiCommandResponse requireConfirmation(String confirmationId, String confirmationMsg, Object pendingAction) {
        AiCommandResponse response = new AiCommandResponse(true, confirmationMsg);
        response.setRequiresConfirmation(true);
        response.setConfirmationId(confirmationId);
        response.setConfirmationMessage(confirmationMsg);
        response.setPendingAction(pendingAction);
        return response;
    }

    // Getters and Setters
    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public Order getOrderExecuted() { return orderExecuted; }
    public void setOrderExecuted(Order orderExecuted) { this.orderExecuted = orderExecuted; }

    public AgentRule getRuleCreated() { return ruleCreated; }
    public void setRuleCreated(AgentRule ruleCreated) { this.ruleCreated = ruleCreated; }
    
    public boolean isRequiresConfirmation() { return requiresConfirmation; }
    public void setRequiresConfirmation(boolean requiresConfirmation) { this.requiresConfirmation = requiresConfirmation; }
    
    public String getConfirmationId() { return confirmationId; }
    public void setConfirmationId(String confirmationId) { this.confirmationId = confirmationId; }
    
    public String getConfirmationMessage() { return confirmationMessage; }
    public void setConfirmationMessage(String confirmationMessage) { this.confirmationMessage = confirmationMessage; }
    
    public Object getPendingAction() { return pendingAction; }
    public void setPendingAction(Object pendingAction) { this.pendingAction = pendingAction; }
}
