package com.chatbot.service;

import com.chatbot.model.ChatMessage;
import com.chatbot.model.DocumentChunk;
import com.chatbot.model.gemini.GeminiApiModels.Content;
import com.chatbot.model.gemini.GeminiApiModels.Blob;
import com.chatbot.model.gemini.GeminiApiModels.Part;
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
        List<SourceSnippet> sources,
        boolean suggestAdmin
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
        return askQuestion(question, null, null);
    }

    public ChatAnswer askQuestion(String question, String imageBase64, String imageMimeType) {
        return askQuestion(question, imageBase64, imageMimeType, List.of());
    }

    public ChatAnswer askQuestion(String question, String imageBase64, String imageMimeType, List<ChatMessage> history) {
        log.info("Answering user query: '{}', with image: {}, history size: {}", question, (imageBase64 != null), (history != null ? history.size() : 0));

        // Validar formato de imagen si se proporciona
        if (imageBase64 != null && imageMimeType != null) {
            String mimeLower = imageMimeType.toLowerCase().trim();
            if (!mimeLower.equals("image/png") && 
                !mimeLower.equals("image/jpeg") && 
                !mimeLower.equals("image/jpg") && 
                !mimeLower.equals("image/webp")) {
                throw new IllegalArgumentException("Formato de imagen no permitido. Solo se permiten PNG, JPG, JPEG y WEBP.");
            }
        }

        // 1. Obtener la descripción de la imagen si está presente
        String imageDescription = null;
        if (imageBase64 != null && imageMimeType != null) {
            imageDescription = geminiService.describeImage(imageBase64, imageMimeType);
            log.info("Image interpretation: {}", imageDescription);
        }

        // 2. Construir la consulta para el vector store
        String searchContextQuery = "";
        if (imageDescription != null) {
            searchContextQuery += imageDescription;
        }
        if (question != null && !question.trim().isEmpty()) {
            if (!searchContextQuery.isEmpty()) {
                searchContextQuery += " ";
            }
            searchContextQuery += question.trim();
        }

        // Si no hay pregunta ni imagen, lanzar error
        if (searchContextQuery.isEmpty()) {
            throw new IllegalArgumentException("Se requiere una pregunta o una imagen para procesar.");
        }

        // 3. Obtener el embedding para la búsqueda contextual
        List<Double> queryEmbedding = geminiService.getEmbedding(searchContextQuery);

        // 4. Consultar fragmentos similares en la base de datos
        List<DocumentChunk> matchingChunks = vectorStoreService.findSimilar(queryEmbedding, TOP_K);

        // Si aún no se han subido manuales, informar al usuario
        if (matchingChunks.isEmpty()) {
            return new ChatAnswer(
                "No hay manuales cargados en el sistema. Por favor, sube archivos PDF de manuales de equipos médicos para comenzar a chatear.",
                List.of(),
                false
            );
        }

        // 5. Ensamblar el contexto del prompt
        StringBuilder contextBuilder = new StringBuilder();
        contextBuilder.append("Here is the context extracted from the medical equipment manuals:\n\n");
        
        for (DocumentChunk chunk : matchingChunks) {
            contextBuilder.append(String.format("--- START OF TEXT FROM %s (Chunk %d) ---\n", 
                    chunk.getDocumentName(), chunk.getChunkIndex() + 1));
            contextBuilder.append(chunk.getContent()).append("\n");
            contextBuilder.append(String.format("--- END OF TEXT FROM %s ---\n\n", chunk.getDocumentName()));
        }

        // 6. Formular la instrucción del sistema para forzar respuestas estrictamente fundamentadas
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

        // 7. Construir el prompt de usuario conteniendo el contexto y la pregunta (incluyendo la descripción de la imagen para contextualizar)
        StringBuilder userPromptBuilder = new StringBuilder();
        userPromptBuilder.append("CONTEXT:\n").append(contextBuilder.toString()).append("\n\n");
        
        if (imageDescription != null) {
            userPromptBuilder.append("IMAGE INTERPRETATION/DESCRIPTION: ").append(imageDescription).append("\n\n");
        }
        
        userPromptBuilder.append("USER QUESTION: ").append(question != null ? question : "Analiza la imagen provista y responde con las instrucciones del manual correspondiente.").append("\n\nANSWER:");

        // 8. Construir la lista de Content de Gemini (historial + mensaje actual)
        List<Content> contentsList = new java.util.ArrayList<>();
        if (history != null && !history.isEmpty()) {
            for (ChatMessage msg : history) {
                if ("USER".equals(msg.getRole())) {
                    if (msg.getImageBase64() != null && msg.getImageMimeType() != null) {
                        Blob blob = new Blob(msg.getImageMimeType(), msg.getImageBase64());
                        Part imagePart = new Part(null, blob);
                        Part textPart = new Part(msg.getContent(), null);
                        contentsList.add(new Content(List.of(textPart, imagePart), "user"));
                    } else {
                        contentsList.add(Content.user(msg.getContent()));
                    }
                } else if ("MODEL".equals(msg.getRole())) {
                    contentsList.add(Content.model(msg.getContent()));
                }
            }
        }

        // Añadir el mensaje de usuario actual (con RAG context y la imagen opcional en la llamada)
        Content currentUserContent;
        if (imageBase64 != null && imageMimeType != null) {
            Blob blob = new Blob(imageMimeType, imageBase64);
            Part imagePart = new Part(null, blob);
            Part textPart = new Part(userPromptBuilder.toString(), null);
            currentUserContent = new Content(List.of(textPart, imagePart), "user");
        } else {
            currentUserContent = Content.user(userPromptBuilder.toString());
        }
        contentsList.add(currentUserContent);

        // 9. Solicitar la generación de la respuesta a Gemini
        String answer = geminiService.generateAnswer(contentsList, systemInstruction);

        // 10. Compilar los fragmentos de metadatos de las fuentes para las citas del cliente
        List<SourceSnippet> sources = matchingChunks.stream()
                .map(chunk -> new SourceSnippet(
                        chunk.getDocumentName(),
                        chunk.getChunkIndex() + 1,
                        // Proporcionar un fragmento corto de previsualización del contenido
                        chunk.getContent().length() > 160 ? chunk.getContent().substring(0, 160) + "..." : chunk.getContent()
                ))
                .collect(Collectors.toList());

        return new ChatAnswer(answer, sources, false);
    }
}
