package com.chatbot.model.gemini;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

public final class GeminiApiModels {

    // --- MODELOS DE EMBEDDING ---

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record EmbeddingRequest(
        String model,
        Content content,
        Integer outputDimensionality
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EmbeddingResponse(
        Embedding embedding
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Embedding(
        List<Double> values
    ) {}

    // --- MODELOS DE GENERACIÓN DE CHAT ---

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

    // --- MODELOS COMUNES ---

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

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Blob(
        String mimeType,
        String data
    ) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(
        String text,
        Blob inlineData
    ) {
        public Part(String text) {
            this(text, null);
        }
    }
}
