package com.projvault.ai;

public record ConversationAiConfig(
        String baseUrl,
        String apiKey,
        String model,
        int timeoutSeconds) {

    @Override
    public String toString() {
        return "ConversationAiConfig[baseUrl=" + baseUrl + ", apiKey=***, model=" + model
                + ", timeoutSeconds=" + timeoutSeconds + "]";
    }
}
