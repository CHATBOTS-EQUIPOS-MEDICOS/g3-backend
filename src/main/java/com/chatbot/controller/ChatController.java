package com.chatbot.controller;

import com.chatbot.model.ChatMessage;
import com.chatbot.model.ChatSession;
import com.chatbot.model.ChatSource;
import com.chatbot.service.ChatHistoryService;
import com.chatbot.service.ChatService;
import com.chatbot.service.ChatService.ChatAnswer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "http://localhost:4200")
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final ChatHistoryService chatHistoryService;

    public ChatController(ChatService chatService, ChatHistoryService chatHistoryService) {
        this.chatService = chatService;
        this.chatHistoryService = chatHistoryService;
    }

    public record AskRequest(
            String question,
            String imageBase64,
            String imageMimeType) {
    }

    // DTOs de salida seguros para evitar recursión y problemas de Lazy Loading de
    // Hibernate
    public record ChatSessionResponse(
            UUID id,
            String title,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            Boolean isClosed,
            LocalDateTime closedAt) {
        public static ChatSessionResponse fromEntity(ChatSession session) {
            return new ChatSessionResponse(
                    session.getId(),
                    session.getTitle(),
                    session.getCreatedAt(),
                    session.getUpdatedAt(),
                    session.getIsClosed(),
                    session.getClosedAt());
        }
    }

    public record ChatMessageResponse(
            UUID id,
            String role,
            String content,
            String imageBase64,
            String imageMimeType,
            List<ChatSource> sources,
            LocalDateTime createdAt,
            Boolean liked) {
        public static ChatMessageResponse fromEntity(ChatMessage message) {
            return new ChatMessageResponse(
                    message.getId(),
                    message.getRole(),
                    message.getContent(),
                    message.getImageBase64(),
                    message.getImageMimeType(),
                    message.getSources(),
                    message.getCreatedAt(),
                    message.getLiked());
        }
    }

    /**
     * Obtiene el ID del usuario autenticado a partir del contexto de seguridad de
     * Spring Security.
     */
    private UUID getAuthenticatedUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.authentication.BadCredentialsException("Usuario no autenticado.");
        }
        return UUID.fromString(authentication.getName());
    }

    /**
     * Endpoint público para realizar preguntas rápidas y anónimas basadas en los
     * manuales de equipos médicos subidos.
     * No guarda historial de chat.
     */
    @PostMapping(value = "/ask", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> askQuestion(
            @RequestParam(value = "question", required = false) String question,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        boolean hasQuestion = question != null && !question.strip().isEmpty();
        boolean hasFile = file != null && !file.isEmpty();

        if (!hasQuestion && !hasFile) {
            return ResponseEntity.badRequest().body(Map.of("error", "The question or image file must be provided."));
        }

        String imageBase64 = null;
        String imageMimeType = null;

        if (hasFile) {
            imageMimeType = file.getContentType();
            try {
                imageBase64 = java.util.Base64.getEncoder().encodeToString(file.getBytes());
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Error al procesar el archivo de imagen: " + e.getMessage()));
            }
        }

        try {
            ChatAnswer answer = chatService.askQuestion(
                    hasQuestion ? question.trim() : null,
                    imageBase64,
                    imageMimeType);
            return ResponseEntity.ok(answer);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al procesar la pregunta de chat pública", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to answer the question: " + e.getMessage()));
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
     * Obtiene todos los mensajes de una sesión de chat específica perteneciente al
     * usuario autenticado.
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
     * Realiza una pregunta dentro de una sesión de chat específica. Guarda la
     * pregunta,
     * realiza el RAG y guarda la respuesta de la IA en la base de datos asociada a
     * la sesión.
     */
    @PostMapping(value = "/sessions/{sessionId}/ask", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> askInSession(
            @PathVariable UUID sessionId,
            @RequestParam(value = "question", required = false) String question,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        boolean hasQuestion = question != null && !question.strip().isEmpty();
        boolean hasFile = file != null && !file.isEmpty();

        if (!hasQuestion && !hasFile) {
            return ResponseEntity.badRequest().body(Map.of("error", "The question or image file must be provided."));
        }

        String imageBase64 = null;
        String imageMimeType = null;

        if (hasFile) {
            imageMimeType = file.getContentType();
            try {
                imageBase64 = java.util.Base64.getEncoder().encodeToString(file.getBytes());
            } catch (IOException e) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(Map.of("error", "Error al procesar el archivo de imagen: " + e.getMessage()));
            }
        }

        UUID userId = getAuthenticatedUserId();
        try {
            ChatAnswer answer = chatHistoryService.askInSession(
                    userId,
                    sessionId,
                    hasQuestion ? question.trim() : null,
                    imageBase64,
                    imageMimeType);
            return ResponseEntity.ok(answer);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Error al procesar la pregunta de chat dentro de la sesión {}", sessionId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Failed to answer the question in session: " + e.getMessage()));
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

    /**
     * Cierra una sesión de chat para el usuario autenticado.
     */
    @PostMapping("/sessions/{sessionId}/close")
    public ResponseEntity<ChatSessionResponse> closeSession(@PathVariable UUID sessionId) {
        UUID userId = getAuthenticatedUserId();
        ChatSession session = chatHistoryService.closeSession(userId, sessionId);
        return ResponseEntity.ok(ChatSessionResponse.fromEntity(session));
    }

    /**
     * Registra un "like" (calificación positiva) en un mensaje del usuario.
     */
    @PostMapping("/messages/{messageId}/like")
    public ResponseEntity<ChatMessageResponse> likeMessage(@PathVariable UUID messageId) {
        UUID userId = getAuthenticatedUserId();
        try {
            ChatMessage message = chatHistoryService.rateMessage(userId, messageId, true);
            return ResponseEntity.ok(ChatMessageResponse.fromEntity(message));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Registra un "dislike" (calificación negativa) en un mensaje del usuario.
     */
    @PostMapping("/messages/{messageId}/dislike")
    public ResponseEntity<ChatMessageResponse> dislikeMessage(@PathVariable UUID messageId) {
        UUID userId = getAuthenticatedUserId();
        try {
            ChatMessage message = chatHistoryService.rateMessage(userId, messageId, false);
            return ResponseEntity.ok(ChatMessageResponse.fromEntity(message));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
