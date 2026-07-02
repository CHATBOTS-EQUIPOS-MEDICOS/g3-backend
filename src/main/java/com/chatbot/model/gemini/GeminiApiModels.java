package com.chatbot.model.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

public final class GeminiApiModels {

    // --- EMBEDDING MODELS ---

    public record EmbeddingRequest(
        String model,
        Content content
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbeddingResponse(
        Embedding embedding
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Embedding(
        List<Double> values
    ) {}

    // --- CHAT GENERATION MODELS ---

    public record ChatRequest(
        List<Content> contents,
        SystemInstruction systemInstruction,
        GenerationConfig generationConfig
    ) {}

    public record SystemInstruction(
        List<Part> parts
    ) {}

    public record GenerationConfig(
        Double temperature,
        String responseMimeType
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatResponse(
        List<Candidate> candidates
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(
        Content content,
        String finishReason
    ) {}

    // --- COMMON MODELS ---

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(
        List<Part> parts,
        String role
    ) {
        public static Content user(String text) {
            return new Content(List.of(new Part(text)), "user");
        }
        
        public static Content model(String text) {
            return new Content(List.of(new Part(text)), "model");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(
        String text
    ) {}
}
