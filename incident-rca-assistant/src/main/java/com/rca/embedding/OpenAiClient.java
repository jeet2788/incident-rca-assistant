package com.rca.embedding;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the OpenAI REST API.
 * Handles both embedding generation and chat completions.
 */
@Slf4j
@Component
public class OpenAiClient {

    private final WebClient webClient;
    private final String embeddingModel;
    private final String chatModel;
    private final int maxTokens;

    public OpenAiClient(
        WebClient.Builder webClientBuilder,
        @Value("${openai.base-url}") String baseUrl,
        @Value("${openai.api-key}") String apiKey,
        @Value("${openai.embedding-model}") String embeddingModel,
        @Value("${openai.chat-model}") String chatModel,
        @Value("${openai.max-tokens}") int maxTokens
    ) {
        this.webClient = webClientBuilder
            .baseUrl(baseUrl)
            .defaultHeader("Authorization", "Bearer " + apiKey)
            .defaultHeader("Content-Type", "application/json")
            .build();
        this.embeddingModel = embeddingModel;
        this.chatModel = chatModel;
        this.maxTokens = maxTokens;
    }

    /**
     * Calls OpenAI embeddings endpoint and returns the float vector.
     */
    public float[] embed(String text) {
        log.debug("Generating embedding for text of length {}", text.length());

        EmbeddingResponse response = webClient.post()
            .uri("/embeddings")
            .bodyValue(Map.of(
                "model", embeddingModel,
                "input", text
            ))
            .retrieve()
            .bodyToMono(EmbeddingResponse.class)
            .block();

        if (response == null || response.getData().isEmpty()) {
            throw new RuntimeException("Empty embedding response from OpenAI");
        }

        List<Double> vector = response.getData().get(0).getEmbedding();
        float[] result = new float[vector.size()];
        for (int i = 0; i < vector.size(); i++) {
            result[i] = vector.get(i).floatValue();
        }

        log.debug("Embedding generated: {} dimensions", result.length);
        return result;
    }

    /**
     * Calls OpenAI chat completions endpoint with a system + user prompt.
     */
    public String chat(String systemPrompt, String userPrompt) {
        log.debug("Calling chat completion with model={}", chatModel);

        ChatResponse response = webClient.post()
            .uri("/chat/completions")
            .bodyValue(Map.of(
                "model", chatModel,
                "max_tokens", maxTokens,
                "messages", List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
                )
            ))
            .retrieve()
            .bodyToMono(ChatResponse.class)
            .block();

        if (response == null || response.getChoices().isEmpty()) {
            throw new RuntimeException("Empty chat response from OpenAI");
        }

        return response.getChoices().get(0).getMessage().getContent();
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────────

    @Data
    static class EmbeddingResponse {
        private List<EmbeddingData> data;
        private Usage usage;
    }

    @Data
    static class EmbeddingData {
        private List<Double> embedding;
        private int index;
    }

    @Data
    static class ChatResponse {
        private List<Choice> choices;
        private Usage usage;
    }

    @Data
    static class Choice {
        private Message message;
        private String finishReason;
    }

    @Data
    static class Message {
        private String role;
        private String content;
    }

    @Data
    static class Usage {
        private int promptTokens;
        private int completionTokens;
        private int totalTokens;
    }
}
