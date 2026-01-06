package com.cryptoplatform.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

@Service
public class OpenAiClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    @Value("${ai.api.key}")
    private String apiKey;

    @Value("${ai.api.url:https://api.openai.com/v1/chat/completions}")
    private String apiUrl;

    public OpenAiClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.webClient = WebClient.builder().build();
    }

    public JsonNode parseCommand(String userCommand) {
        try {
            // Build request payload
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", "gpt-4o-mini");
            
            // System message
            ArrayNode messages = objectMapper.createArrayNode();
            ObjectNode systemMessage = objectMapper.createObjectNode();
            systemMessage.put("role", "system");
            systemMessage.put("content", "You are a friendly cryptocurrency trading assistant. Your role is to help users trade and manage their crypto portfolio.\n\n" +
                "FLEXIBILITY: Be flexible in understanding user intent. Users may phrase commands in various ways. Try your best to interpret what they want.\n\n" +
                "EXAMPLES OF VARIATIONS:\n" +
                "- 'get me some BTC' = buy BTC\n" +
                "- 'I want ethereum' = buy ETH\n" +
                "- 'dump my bitcoin' = sell BTC\n" +
                "- 'switch from BTC to SOL' = convert BTC to SOL\n" +
                "- 'split my money between...' = diversify portfolio\n\n" +
                "AMOUNT TYPES:\n" +
                "- Dollar signs or 'dollars'/'USD': use amount_type=USD (e.g., '$100', 'buy 50 dollars of BTC')\n" +
                "- Numbers with crypto name: use amount_type=CRYPTO (e.g., '1 BTC', '0.5 ethereum')\n" +
                "- 'all', 'everything', 'my entire': use amount_type=ALL (e.g., 'sell all my BTC', 'sell everything')\n\n" +
                "FUNCTION SELECTION:\n" +
                "- Buy/sell single asset: execute_trade\n" +
                "- Convert/swap between cryptos: convert_crypto\n" +
                "- Spread across multiple assets: diversify_portfolio\n" +
                "- Automated price triggers: create_rule\n\n" +
                "SYMBOL MAPPING (be flexible):\n" +
                "- BTC, Bitcoin, bitcoin → BTC-USD\n" +
                "- ETH, Ethereum, ethereum, ether → ETH-USD\n" +
                "- SOL, Solana, solana → SOL-USD\n" +
                "- XRP, Ripple, ripple → XRP-USD\n\n" +
                "ERROR HANDLING:\n" +
                "If user asks something you cannot do (e.g., check news, predict prices, non-trading tasks), respond with a plain text message explaining you can only help with trading. DO NOT call any function.\n\n" +
                "If user command is unclear or missing information, respond with a plain text message asking for clarification. DO NOT guess.");
            messages.add(systemMessage);
            
            // User message
            ObjectNode userMessage = objectMapper.createObjectNode();
            userMessage.put("role", "user");
            userMessage.put("content", userCommand);
            messages.add(userMessage);
            
            requestBody.set("messages", messages);
            
            // Define functions for function calling
            ArrayNode functions = objectMapper.createArrayNode();
            
            // Function 1: Execute immediate trade
            ObjectNode executeTrade = objectMapper.createObjectNode();
            executeTrade.put("name", "execute_trade");
            executeTrade.put("description", "Execute an immediate buy or sell order");
            
            ObjectNode executeParams = objectMapper.createObjectNode();
            executeParams.put("type", "object");
            
            ObjectNode executeProperties = objectMapper.createObjectNode();
            
            ObjectNode symbolProp = objectMapper.createObjectNode();
            symbolProp.put("type", "string");
            symbolProp.put("description", "Trading symbol (e.g., BTC-USD, ETH-USD)");
            executeProperties.set("symbol", symbolProp);
            
            ObjectNode actionProp = objectMapper.createObjectNode();
            actionProp.put("type", "string");
            ArrayNode actionEnum = objectMapper.createArrayNode();
            actionEnum.add("BUY");
            actionEnum.add("SELL");
            actionProp.set("enum", actionEnum);
            executeProperties.set("action", actionProp);
            
            ObjectNode amountTypeProp = objectMapper.createObjectNode();
            amountTypeProp.put("type", "string");
            ArrayNode amountTypeEnum = objectMapper.createArrayNode();
            amountTypeEnum.add("USD");
            amountTypeEnum.add("CRYPTO");
            amountTypeEnum.add("ALL");
            amountTypeProp.set("enum", amountTypeEnum);
            amountTypeProp.put("description", "Type of amount: USD for dollar amount (e.g., '$100 of BTC'), CRYPTO for cryptocurrency quantity (e.g., '1 BTC'), or ALL to sell entire position (e.g., 'sell all my BTC'). When using ALL, the amount parameter is ignored.");
            executeProperties.set("amount_type", amountTypeProp);
            
            ObjectNode amountProp = objectMapper.createObjectNode();
            amountProp.put("type", "number");
            amountProp.put("description", "The numeric amount to trade. If amount_type is USD, this is the dollar value. If amount_type is CRYPTO, this is the quantity of cryptocurrency. If amount_type is ALL, this parameter is ignored (can be 0).");
            executeProperties.set("amount", amountProp);
            
            executeParams.set("properties", executeProperties);
            
            ArrayNode executeRequired = objectMapper.createArrayNode();
            executeRequired.add("symbol");
            executeRequired.add("action");
            executeRequired.add("amount_type");
            executeRequired.add("amount");
            executeParams.set("required", executeRequired);
            
            executeTrade.set("parameters", executeParams);
            functions.add(executeTrade);
            
            // Function 2: Create conditional rule
            ObjectNode createRule = objectMapper.createObjectNode();
            createRule.put("name", "create_rule");
            createRule.put("description", "Create a conditional trading rule that executes when price conditions are met");
            
            ObjectNode ruleParams = objectMapper.createObjectNode();
            ruleParams.put("type", "object");
            
            ObjectNode ruleProperties = objectMapper.createObjectNode();
            
            ruleProperties.set("symbol", symbolProp);
            
            ObjectNode conditionProp = objectMapper.createObjectNode();
            conditionProp.put("type", "string");
            ArrayNode conditionEnum = objectMapper.createArrayNode();
            conditionEnum.add("PRICE_ABOVE");
            conditionEnum.add("PRICE_BELOW");
            conditionProp.set("enum", conditionEnum);
            conditionProp.put("description", "Price condition type");
            ruleProperties.set("condition", conditionProp);
            
            ObjectNode targetPriceProp = objectMapper.createObjectNode();
            targetPriceProp.put("type", "number");
            targetPriceProp.put("description", "Target price for the condition");
            ruleProperties.set("targetPrice", targetPriceProp);
            
            ruleProperties.set("action", actionProp);
            
            ObjectNode ruleAmountTypeProp = objectMapper.createObjectNode();
            ruleAmountTypeProp.put("type", "string");
            ArrayNode ruleAmountTypeEnum = objectMapper.createArrayNode();
            ruleAmountTypeEnum.add("USD");
            ruleAmountTypeEnum.add("CRYPTO");
            ruleAmountTypeEnum.add("ALL");
            ruleAmountTypeProp.set("enum", ruleAmountTypeEnum);
            ruleAmountTypeProp.put("description", "Type of amount: USD for dollar value, CRYPTO for quantity, or ALL for entire position (SELL only)");
            ruleProperties.set("amount_type", ruleAmountTypeProp);
            
            ruleProperties.set("amount", amountProp);
            
            ruleParams.set("properties", ruleProperties);
            
            ArrayNode ruleRequired = objectMapper.createArrayNode();
            ruleRequired.add("symbol");
            ruleRequired.add("condition");
            ruleRequired.add("targetPrice");
            ruleRequired.add("action");
            ruleRequired.add("amount_type");
            ruleRequired.add("amount");
            ruleParams.set("required", ruleRequired);
            
            createRule.set("parameters", ruleParams);
            functions.add(createRule);
            
            // Function 3: Convert cryptocurrency (sell one, buy another)
            ObjectNode convertCrypto = objectMapper.createObjectNode();
            convertCrypto.put("name", "convert_crypto");
            convertCrypto.put("description", "Convert from one cryptocurrency to another by selling the first asset and immediately buying the second asset with the proceeds");
            
            ObjectNode convertParams = objectMapper.createObjectNode();
            convertParams.put("type", "object");
            
            ObjectNode convertProperties = objectMapper.createObjectNode();
            
            ObjectNode fromSymbolProp = objectMapper.createObjectNode();
            fromSymbolProp.put("type", "string");
            fromSymbolProp.put("description", "Symbol to sell (e.g., BTC-USD, ETH-USD)");
            convertProperties.set("from_symbol", fromSymbolProp);
            
            ObjectNode toSymbolProp = objectMapper.createObjectNode();
            toSymbolProp.put("type", "string");
            toSymbolProp.put("description", "Symbol to buy (e.g., ETH-USD, SOL-USD)");
            convertProperties.set("to_symbol", toSymbolProp);
            
            ObjectNode convertAmountTypeProp = objectMapper.createObjectNode();
            convertAmountTypeProp.put("type", "string");
            ArrayNode convertAmountTypeEnum = objectMapper.createArrayNode();
            convertAmountTypeEnum.add("USD");
            convertAmountTypeEnum.add("CRYPTO");
            convertAmountTypeEnum.add("ALL");
            convertAmountTypeProp.set("enum", convertAmountTypeEnum);
            convertAmountTypeProp.put("description", "Type of amount to convert: USD for dollar value, CRYPTO for specific quantity of from_symbol, or ALL to convert entire position");
            convertProperties.set("amount_type", convertAmountTypeProp);
            
            ObjectNode convertAmountProp = objectMapper.createObjectNode();
            convertAmountProp.put("type", "number");
            convertAmountProp.put("description", "Amount to convert. Ignored if amount_type is ALL.");
            convertProperties.set("amount", convertAmountProp);
            
            convertParams.set("properties", convertProperties);
            
            ArrayNode convertRequired = objectMapper.createArrayNode();
            convertRequired.add("from_symbol");
            convertRequired.add("to_symbol");
            convertRequired.add("amount_type");
            convertParams.set("required", convertRequired);
            
            convertCrypto.set("parameters", convertParams);
            functions.add(convertCrypto);
            
            // Function 4: Diversify portfolio
            ObjectNode diversifyPortfolio = objectMapper.createObjectNode();
            diversifyPortfolio.put("name", "diversify_portfolio");
            diversifyPortfolio.put("description", "Allocate a specified dollar amount across multiple cryptocurrencies with percentage-based distribution");
            
            ObjectNode diversifyParams = objectMapper.createObjectNode();
            diversifyParams.put("type", "object");
            
            ObjectNode diversifyProperties = objectMapper.createObjectNode();
            
            ObjectNode totalAmountProp = objectMapper.createObjectNode();
            totalAmountProp.put("type", "number");
            totalAmountProp.put("description", "Total USD amount to allocate across all assets");
            diversifyProperties.set("total_amount", totalAmountProp);
            
            ObjectNode allocationsProp = objectMapper.createObjectNode();
            allocationsProp.put("type", "array");
            ObjectNode allocationItemProp = objectMapper.createObjectNode();
            allocationItemProp.put("type", "object");
            
            ObjectNode allocationItemProperties = objectMapper.createObjectNode();
            ObjectNode allocSymbolProp = objectMapper.createObjectNode();
            allocSymbolProp.put("type", "string");
            allocSymbolProp.put("description", "Trading symbol (e.g., BTC-USD)");
            allocationItemProperties.set("symbol", allocSymbolProp);
            
            ObjectNode percentageProp = objectMapper.createObjectNode();
            percentageProp.put("type", "number");
            percentageProp.put("description", "Percentage of total_amount to allocate to this asset (0-100). All percentages must sum to 100.");
            allocationItemProperties.set("percentage", percentageProp);
            
            allocationItemProp.set("properties", allocationItemProperties);
            allocationsProp.set("items", allocationItemProp);
            allocationsProp.put("description", "Array of assets and their percentage allocations");
            diversifyProperties.set("allocations", allocationsProp);
            
            diversifyParams.set("properties", diversifyProperties);
            
            ArrayNode diversifyRequired = objectMapper.createArrayNode();
            diversifyRequired.add("total_amount");
            diversifyRequired.add("allocations");
            diversifyParams.set("required", diversifyRequired);
            
            diversifyPortfolio.set("parameters", diversifyParams);
            functions.add(diversifyPortfolio);
            
            
            requestBody.set("functions", functions);
            requestBody.put("function_call", "auto");
            
            // Make API call
            String response = webClient.post()
                    .uri(apiUrl)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            
            return objectMapper.readTree(response);
            
        } catch (Exception e) {
            throw new RuntimeException("Failed to call OpenAI API: " + e.getMessage(), e);
        }
    }

    public String extractFunctionName(JsonNode response) {
        JsonNode functionCall = response.path("choices").get(0).path("message").path("function_call");
        if (functionCall.isMissingNode()) {
            return null;
        }
        return functionCall.path("name").asText();
    }

    public JsonNode extractFunctionArguments(JsonNode response) {
        try {
            JsonNode functionCall = response.path("choices").get(0).path("message").path("function_call");
            if (functionCall.isMissingNode()) {
                return null;
            }
            String argsString = functionCall.path("arguments").asText();
            return objectMapper.readTree(argsString);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse function arguments: " + e.getMessage(), e);
        }
    }
    
    public String extractTextResponse(JsonNode response) {
        try {
            JsonNode message = response.path("choices").get(0).path("message");
            if (!message.path("content").isMissingNode()) {
                return message.path("content").asText();
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
