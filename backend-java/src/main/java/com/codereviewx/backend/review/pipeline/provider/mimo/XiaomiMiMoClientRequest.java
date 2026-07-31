package com.codereviewx.backend.review.pipeline.provider.mimo;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class XiaomiMiMoClientRequest {

    private String model;
    private List<Message> messages;
    private double temperature;
    @JsonProperty("max_completion_tokens")
    private int maxCompletionTokens;

    public XiaomiMiMoClientRequest() {
    }

    public XiaomiMiMoClientRequest(String model, List<Message> messages, double temperature,
                                   int maxCompletionTokens) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public List<Message> getMessages() {
        return messages;
    }

    public void setMessages(List<Message> messages) {
        this.messages = messages;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxCompletionTokens() {
        return maxCompletionTokens;
    }

    public void setMaxCompletionTokens(int maxCompletionTokens) {
        this.maxCompletionTokens = maxCompletionTokens;
    }

    public static class Message {

        private String role;
        private String content;

        public Message() {
        }

        public Message(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
