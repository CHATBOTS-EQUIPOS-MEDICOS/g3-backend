package com.chatbot.service;

import com.chatbot.model.gemini.GeminiApiModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

@Service
@Slf4j
public class GeminiService {

    private final String apiKey;
    private final String chatModel;
    private final String embeddingModel;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public GeminiService(
            @Value("${gemini.api.key}") String apiKey,
            @Value("${gemini.model.chat}") String chatModel,
            @Value("${gemini.model.embedding}") String embeddingModel,
            ObjectMapper objectMapper) {
        this.apiKey = apiKey;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * Generates a dense embedding vector (usually 768 dimensions) for the input text.
     */
    public List<Double> getEmbedding(String text) {
        String url = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:embedContent?key=%s",
                embeddingModel, apiKey
        );

        Content content = new Content(List.of(new Part(text)), null);
        EmbeddingRequest requestBody = new EmbeddingRequest("models/" + embeddingModel, content);

        try {
            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(15))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to generate embedding: Code {}, Body: {}", response.statusCode(), response.body());
                throw new RuntimeException("Gemini API embedding generation failed with status code " + response.statusCode());
            }

            EmbeddingResponse embeddingResponse = objectMapper.readValue(response.body(), EmbeddingResponse.class);
            if (embeddingResponse == null || embeddingResponse.embedding() == null) {
                throw new RuntimeException("Invalid embedding response from Gemini API");
            }

            return embeddingResponse.embedding().values();

        } catch (IOException | InterruptedException e) {
            log.error("Exception occurred while calling Gemini Embeddings API", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to call Gemini Embeddings API", e);
        }
    }

    /**
     * Generates text content using Gemini based on a user prompt and optional system instructions.
     */
    public String generateAnswer(String prompt, String systemText) {
        String url = String.format(
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                chatModel, apiKey
        );

        Content userContent = Content.user(prompt);
        SystemInstruction systemInstruction = systemText != null ? 
                new SystemInstruction(List.of(new Part(systemText))) : null;
        GenerationConfig config = new GenerationConfig(0.2, "text/plain");

        ChatRequest requestBody = new ChatRequest(
                List.of(userContent),
                systemInstruction,
                config
        );

        try {
            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.error("Failed to generate content: Code {}, Body: {}", response.statusCode(), response.body());
                throw new RuntimeException("Gemini API generate content failed with status code " + response.statusCode());
            }

            ChatResponse chatResponse = objectMapper.readValue(response.body(), ChatResponse.class);
            if (chatResponse == null || chatResponse.candidates() == null || chatResponse.candidates().isEmpty()) {
                throw new RuntimeException("No candidates returned from Gemini API");
            }

            Candidate candidate = chatResponse.candidates().get(0);
            if (candidate.content() == null || candidate.content().parts() == null || candidate.content().parts().isEmpty()) {
                throw new RuntimeException("Empty content in Gemini response candidate");
            }

            return candidate.content().parts().get(0).text();

        } catch (IOException | InterruptedException e) {
            log.error("Exception occurred while calling Gemini Chat API", e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Failed to call Gemini Chat API", e);
        }
    }
}
