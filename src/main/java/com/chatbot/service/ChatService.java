package com.chatbot.service;

import com.chatbot.model.DocumentChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ChatService {

    private final GeminiService geminiService;
    private final VectorStoreService vectorStoreService;

    // Retrieve top 5 matching text chunks for RAG context
    private static final int TOP_K = 5;

    public ChatService(GeminiService geminiService, VectorStoreService vectorStoreService) {
        this.geminiService = geminiService;
        this.vectorStoreService = vectorStoreService;
    }

    public record ChatAnswer(
        String answer,
        List<SourceSnippet> sources
    ) {}

    public record SourceSnippet(
        String documentName,
        int chunkIndex,
        String snippet
    ) {}

    /**
     * Answers the user's question using Retrieval-Augmented Generation (RAG).
     * Retrieves the most relevant chunks from the uploaded manuals and uses
     * Gemini to compile an answer restricted strictly to those manuals.
     */
    public ChatAnswer askQuestion(String question) {
        log.info("Answering user query: '{}'", question);

        // 1. Get embedding for the user question
        List<Double> queryEmbedding = geminiService.getEmbedding(question);

        // 2. Query similar chunks in the SQLite database
        List<DocumentChunk> matchingChunks = vectorStoreService.findSimilar(queryEmbedding, TOP_K);

        // If no manuals have been uploaded yet, inform the user
        if (matchingChunks.isEmpty()) {
            return new ChatAnswer(
                "No hay manuales cargados en el sistema. Por favor, sube archivos PDF de manuales de equipos médicos para comenzar a chatear.",
                List.of()
            );
        }

        // 3. Assemble the prompt context
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Here is the context extracted from the medical equipment manuals:\n\n");
        
        for (DocumentChunk chunk : matchingChunks) {
            contextBuilder.append(String.format("--- START OF TEXT FROM %s (Chunk %d) ---\n", 
                    chunk.getDocumentName(), chunk.getChunkIndex() + 1));
            contextBuilder.append(chunk.getContent()).append("\n");
            contextBuilder.append(String.format("--- END OF TEXT FROM %s ---\n\n", chunk.getDocumentName()));
        }

        // 4. Formulate the system instruction to force strict groundedness
        String systemInstruction = """
                Eres un asistente virtual especializado en responder preguntas sobre manuales de equipos médicos.
                
                Tus respuestas deben estar basadas ÚNICAMENTE en el contexto proporcionado de los manuales. 
                Sigue estas reglas estrictamente:
                1. Responde solo con información que esté explícitamente presente en el contexto proporcionado.
                2. Si la información necesaria para responder la pregunta no está en el contexto, debes decir exactamente lo siguiente (o variaciones muy cercanas): "Lo siento, la respuesta a esa pregunta no se encuentra en los manuales de equipos médicos disponibles."
                3. No intentes adivinar, asumir ni inventar nada que no esté en el texto suministrado. No utilices tu conocimiento general pre-entrenado para rellenar vacíos.
                4. Responde en español de forma profesional y clara.
                """;

        // 5. Construct user prompt containing context and question
        String userPrompt = String.format(
                "CONTEXT:\n%s\n\nUSER QUESTION: %s\n\nANSWER:",
                contextBuilder.toString(),
                question
        );

        // 6. Request answer completion from Gemini
        String answer = geminiService.generateAnswer(userPrompt, systemInstruction);

        // 7. Compile the source metadata snippets for client citations
        List<SourceSnippet> sources = matchingChunks.stream()
                .map(chunk -> new SourceSnippet(
                        chunk.getDocumentName(),
                        chunk.getChunkIndex() + 1,
                        // Provide a short preview snippet of the content
                        chunk.getContent().length() > 160 ? chunk.getContent().substring(0, 160) + "..." : chunk.getContent()
                ))
                .collect(Collectors.toList());

        return new ChatAnswer(answer, sources);
    }
}
