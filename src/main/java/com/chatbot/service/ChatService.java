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

    // Recupera los 5 fragmentos de texto más similares para el contexto RAG
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
     * Responde la pregunta del usuario utilizando Generación Aumentada por Recuperación (RAG).
     * Recupera los fragmentos más relevantes de los manuales subidos y utiliza
     * Gemini para compilar una respuesta restringida estrictamente a dichos manuales.
     */
    public ChatAnswer askQuestion(String question) {
        log.info("Answering user query: '{}'", question);

        // 1. Obtener el embedding para la pregunta del usuario
        List<Double> queryEmbedding = geminiService.getEmbedding(question);

        // 2. Consultar fragmentos similares en la base de datos
        List<DocumentChunk> matchingChunks = vectorStoreService.findSimilar(queryEmbedding, TOP_K);

        // Si aún no se han subido manuales, informar al usuario
        if (matchingChunks.isEmpty()) {
            return new ChatAnswer(
                "No hay manuales cargados en el sistema. Por favor, sube archivos PDF de manuales de equipos médicos para comenzar a chatear.",
                List.of()
            );
        }

        // 3. Ensamblar el contexto del prompt
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Here is the context extracted from the medical equipment manuals:\n\n");
        
        for (DocumentChunk chunk : matchingChunks) {
            contextBuilder.append(String.format("--- START OF TEXT FROM %s (Chunk %d) ---\n", 
                    chunk.getDocumentName(), chunk.getChunkIndex() + 1));
            contextBuilder.append(chunk.getContent()).append("\n");
            contextBuilder.append(String.format("--- END OF TEXT FROM %s ---\n\n", chunk.getDocumentName()));
        }

        // 4. Formular la instrucción del sistema para forzar respuestas estrictamente fundamentadas
        String systemInstruction = """
                Eres un asistente virtual especializado en responder preguntas sobre manuales de equipos médicos.
                
                Tus respuestas deben estar basadas ÚNICAMENTE en el contexto proporcionado de los manuales. 
                Sigue estas reglas estrictamente:
                1. Responde solo con información que esté explícitamente presente en el contexto proporcionado.
                2. Si la información necesaria para responder la pregunta no está en el contexto, debes decir exactamente lo siguiente (o variaciones muy cercanas): "Lo siento, la respuesta a esa pregunta no se encuentra en los manuales de equipos médicos disponibles."
                3. No intentes adivinar, asumir ni inventar nada que no esté en el texto suministrado. No utilices tu conocimiento general pre-entrenado para rellenar vacíos.
                4. Responde en español de forma profesional y clara.
                5. Si la pregunta del usuario es general, vaga o ambigua, y podría aplicar a múltiples equipos médicos diferentes de los que se encuentran en el contexto (por ejemplo, si el usuario dice "necesito el manual", "cómo se enciende", "código de error", etc., sin especificar el modelo o equipo), NO intentes responder con información de todos los equipos a la vez ni adivinando a cuál se refiere. En su lugar, debes responder de manera breve y directa pidiendo aclaración sobre a cuál de los equipos se refiere (por ejemplo: "¿De qué equipo?", "¿Qué manual?", o "¿Qué ventilador?", adaptando la repregunta según los equipos que identifiques en el contexto).
                """;

        // 5. Construir el prompt de usuario conteniendo el contexto y la pregunta
        String userPrompt = String.format(
                "CONTEXT:\n%s\n\nUSER QUESTION: %s\n\nANSWER:",
                contextBuilder.toString(),
                question
        );

        // 6. Solicitar la generación de la respuesta a Gemini
        String answer = geminiService.generateAnswer(userPrompt, systemInstruction);

        // 7. Compilar los fragmentos de metadatos de las fuentes para las citas del cliente
        List<SourceSnippet> sources = matchingChunks.stream()
                .map(chunk -> new SourceSnippet(
                        chunk.getDocumentName(),
                        chunk.getChunkIndex() + 1,
                        // Proporcionar un fragmento corto de previsualización del contenido
                        chunk.getContent().length() > 160 ? chunk.getContent().substring(0, 160) + "..." : chunk.getContent()
                ))
                .collect(Collectors.toList());

        return new ChatAnswer(answer, sources);
    }
}
