package com.chatbot.service;

import com.chatbot.model.*;
import com.chatbot.repository.ChatMessageRepository;
import com.chatbot.repository.ChatSessionRepository;
import com.chatbot.repository.UserRepository;
import com.chatbot.repository.SupportSessionRepository;
import com.chatbot.service.ChatService.ChatAnswer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ChatHistoryService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatService chatService;
    private final SupportSessionRepository supportSessionRepository;

    public ChatHistoryService(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            UserRepository userRepository,
            ChatService chatService,
            SupportSessionRepository supportSessionRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.chatService = chatService;
        this.supportSessionRepository = supportSessionRepository;
    }

    /**
     * Crea una nueva sesión de chat para el usuario.
     */
    @Transactional
    public ChatSession createSession(UUID userId, String title) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));

        String sessionTitle = (title == null || title.trim().isEmpty()) ? "Nueva Conversación" : title.trim();

        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setTitle(sessionTitle);

        return sessionRepository.save(session);
    }

    /**
     * Obtiene todas las sesiones de chat de un usuario ordenadas por fecha de
     * actualización descendente.
     */
    public List<ChatSession> getSessionsForUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));
        return sessionRepository.findByUserOrderByUpdatedAtDesc(user);
    }

    /**
     * Obtiene todos los mensajes de una sesión específica, verificando la propiedad
     * del usuario.
     */
    public List<ChatMessage> getMessagesInSession(UUID userId, UUID sessionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));

        ChatSession session = sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(
                        () -> new IllegalArgumentException("Sesión de chat no encontrada o no pertenece al usuario."));

        return messageRepository.findBySessionOrderByCreatedAtAsc(session);
    }

    /**
     * Elimina una sesión de chat y todos sus mensajes (por cascada de base de
     * datos).
     */
    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));

        ChatSession session = sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(
                        () -> new IllegalArgumentException("Sesión de chat no encontrada o no pertenece al usuario."));

        sessionRepository.delete(session);
    }

    /**
     * Procesa una pregunta del usuario dentro de una sesión, realiza la consulta
     * RAG,
     * guarda tanto la pregunta como la respuesta con sus fuentes, y actualiza el
     * título si es necesario.
     */
    @Transactional
    public ChatAnswer askInSession(UUID userId, UUID sessionId, String question) {
        return askInSession(userId, sessionId, question, null, null);
    }

    @Transactional
    public ChatAnswer askInSession(UUID userId, UUID sessionId, String question, String imageBase64,
            String imageMimeType) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));

        ChatSession session = sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(
                        () -> new IllegalArgumentException("Sesión de chat no encontrada o no pertenece al usuario."));

        // A. Obtener el historial de los últimos 5 mensajes previos de la sesión
        List<ChatMessage> previousMessages = messageRepository.findTop5BySessionOrderByCreatedAtDesc(session);
        java.util.Collections.reverse(previousMessages);

        // 1. Guardar el mensaje del usuario
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSession(session);
        userMessage.setRole("USER");
        userMessage.setContent(question != null ? question : "[Imagen enviada]");
        userMessage.setImageBase64(imageBase64);
        userMessage.setImageMimeType(imageMimeType);
        messageRepository.save(userMessage);

        // 2. Obtener la respuesta del RAG ChatService
        ChatAnswer chatAnswer = chatService.askQuestion(question, imageBase64, imageMimeType, previousMessages);

        // Mapear los SourceSnippet de la respuesta a entidades ChatSource
        List<ChatSource> sources = chatAnswer.sources().stream()
                .map(src -> new ChatSource(
                        src.documentName(),
                        src.chunkIndex(),
                        src.snippet()))
                .collect(Collectors.toList());

        // 3. Guardar el mensaje de respuesta de la IA (MODEL)
        ChatMessage modelMessage = new ChatMessage();
        modelMessage.setSession(session);
        modelMessage.setRole("MODEL");
        modelMessage.setContent(chatAnswer.answer());
        modelMessage.setSources(sources);
        messageRepository.save(modelMessage);

        // A continuación, calculamos si se debe sugerir hablar con un administrador.
        // La condición es que el chatbot no encuentre una respuesta a las preguntas del
        // usuario después de 3 veces.
        // Si hay una sesión de soporte activa en curso, no se debe sugerir
        // (suggestAdmin = false).
        // Si la sesión de soporte anterior finalizó, reiniciamos el conteo considerando
        // únicamente mensajes posteriores a su creación.
        Optional<SupportSession> lastSupportSessionOpt = supportSessionRepository
                .findFirstByUserOrderByCreatedAtDesc(session.getUser());

        final LocalDateTime cutoffTime;
        boolean hasActiveSupport = false;

        if (lastSupportSessionOpt.isPresent()) {
            SupportSession lastSupport = lastSupportSessionOpt.get();
            if (lastSupport.getStatus() == SupportStatus.WAITING ||
                    lastSupport.getStatus() == SupportStatus.ACTIVE ||
                    lastSupport.getStatus() == SupportStatus.PENDING_USER) {
                hasActiveSupport = true;
            }
            cutoffTime = lastSupport.getCreatedAt();
        } else {
            cutoffTime = null;
        }

        boolean suggestAdmin = false;

        if (!hasActiveSupport) {
            List<ChatMessage> allSessionMessages = messageRepository.findBySessionOrderByCreatedAtAsc(session);
            long fallbackCount = allSessionMessages.stream()
                    .filter(msg -> "MODEL".equals(msg.getRole()))
                    .filter(msg -> cutoffTime == null || msg.getCreatedAt().isAfter(cutoffTime))
                    .filter(msg -> {
                        String content = msg.getContent();
                        return content != null && (content.contains("no se encuentra en los manuales") ||
                                content.contains("no se encuentra en el contexto") ||
                                content.contains("Lo siento, la respuesta"));
                    })
                    .count();

            suggestAdmin = fallbackCount >= 3;
        }

        // 4. Si el título de la sesión es "Nueva Conversación", renombrarlo según la
        // pregunta o indicar consulta de imagen
        if ("Nueva Conversación".equals(session.getTitle())) {
            String newTitle;
            if (question != null && !question.trim().isEmpty()) {
                newTitle = question.length() > 40 ? question.substring(0, 37) + "..." : question;
            } else {
                newTitle = "Consulta con Imagen";
            }
            session.setTitle(newTitle);
        }

        // Actualizar fecha de modificación de la sesión
        sessionRepository.save(session);

        return new ChatAnswer(chatAnswer.answer(), chatAnswer.sources(), suggestAdmin);
    }
}
