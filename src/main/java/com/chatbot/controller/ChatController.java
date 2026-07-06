package com.chatbot.controller;

import com.chatbot.model.ChatMessage;
import com.chatbot.model.ChatSession;
import com.chatbot.model.ChatSource;
import com.chatbot.service.ChatHistoryService;
import com.chatbot.service.ChatService;
import com.chatbot.service.ChatService.ChatAnswer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*") // Permitir una conexión sencilla desde el frontend
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final ChatHistoryService chatHistoryService;

    public ChatController(ChatService chatService, ChatHistoryService chatHistoryService) {
        this.chatService = chatService;
        this.chatHistoryService = chatHistoryService;
    }

    public record AskRequest(
        String question
    ) {}

    // DTOs de salida seguros para evitar recursión y problemas de Lazy Loading de Hibernate
    public record ChatSessionResponse(
        UUID id,
        String title,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        public static ChatSessionResponse fromEntity(ChatSession session) {
            return new ChatSessionResponse(
                session.getId(),
                session.getTitle(),
                session.getCreatedAt(),
                session.getUpdatedAt()
            );
        }
    }

    public record ChatMessageResponse(
        UUID id,
        String role,
        String content,
        List<ChatSource> sources,
        LocalDateTime createdAt
    ) {
        public static ChatMessageResponse fromEntity(ChatMessage message) {
            return new ChatMessageResponse(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getSources(),
                message.getCreatedAt()
            );
        }
    }

    /**
     * Obtiene el ID del usuario autenticado a partir del contexto de seguridad de Spring Security.
     */
    private UUID getAuthenticatedUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.authentication.BadCredentialsException("Usuario no autenticado.");
        }
        return UUID.fromString(authentication.getName());
    }

    /**
     * Endpoint público para realizar preguntas rápidas y anónimas basadas en los manuales de equipos médicos subidos.
     * No guarda historial de chat.
     */
    @PostMapping("/ask")
    public ResponseEntity<?> askQuestion(@RequestBody AskRequest request) {
        if (request == null || request.question() == null || request.question().strip().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The question field must not be empty."));
        }

        try {
            ChatAnswer answer = chatService.askQuestion(request.question().trim());
            return ResponseEntity.ok(answer);
        } catch (Exception e) {
            log.error("Error al procesar la pregunta de chat pública", e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to answer the question: " + e.getMessage()
            ));
        }
    }

    /**
     * Crea una nueva sesión de chat para el usuario autenticado.
     */
    @PostMapping("/sessions")
    public ResponseEntity<ChatSessionResponse> createSession(@RequestBody(required = false) Map<String, String> body) {
        UUID userId = getAuthenticatedUserId();
        String title = body != null ? body.get("title") : null;
        ChatSession session = chatHistoryService.createSession(userId, title);
        return ResponseEntity.ok(ChatSessionResponse.fromEntity(session));
    }

    /**
     * Obtiene todas las sesiones de chat del usuario autenticado.
     */
    @GetMapping("/sessions")
    public ResponseEntity<List<ChatSessionResponse>> getSessions() {
        UUID userId = getAuthenticatedUserId();
        List<ChatSessionResponse> sessions = chatHistoryService.getSessionsForUser(userId).stream()
                .map(ChatSessionResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(sessions);
    }

    /**
     * Obtiene todos los mensajes de una sesión de chat específica perteneciente al usuario autenticado.
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> getMessages(@PathVariable UUID sessionId) {
        UUID userId = getAuthenticatedUserId();
        List<ChatMessageResponse> messages = chatHistoryService.getMessagesInSession(userId, sessionId).stream()
                .map(ChatMessageResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(messages);
    }

    /**
     * Realiza una pregunta dentro de una sesión de chat específica. Guarda la pregunta,
     * realiza el RAG y guarda la respuesta de la IA en la base de datos asociada a la sesión.
     */
    @PostMapping("/sessions/{sessionId}/ask")
    public ResponseEntity<?> askInSession(@PathVariable UUID sessionId, @RequestBody AskRequest request) {
        if (request == null || request.question() == null || request.question().strip().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "The question field must not be empty."));
        }

        UUID userId = getAuthenticatedUserId();
        try {
            ChatAnswer answer = chatHistoryService.askInSession(userId, sessionId, request.question().trim());
            return ResponseEntity.ok(answer);
        } catch (Exception e) {
            log.error("Error al procesar la pregunta de chat dentro de la sesión {}", sessionId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Failed to answer the question in session: " + e.getMessage()
            ));
        }
    }

    /**
     * Elimina una sesión de chat y todo su historial de mensajes asociado.
     */
    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID sessionId) {
        UUID userId = getAuthenticatedUserId();
        chatHistoryService.deleteSession(userId, sessionId);
        return ResponseEntity.noContent().build();
    }
}
