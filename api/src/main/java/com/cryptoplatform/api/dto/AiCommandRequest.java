package com.cryptoplatform.api.dto;

public class AiCommandRequest {
    private String command;

    public AiCommandRequest() {}

    public AiCommandRequest(String command) {
        this.command = command;
    }

    public String getCommand() { return command; }
    public void setCommand(String command) { this.command = command; }
}
