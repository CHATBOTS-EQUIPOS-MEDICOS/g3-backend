package com.chatbot.service;

import com.chatbot.model.ChatMessage;
import com.chatbot.model.ChatSession;
import com.chatbot.model.ChatSource;
import com.chatbot.model.User;
import com.chatbot.repository.ChatMessageRepository;
import com.chatbot.repository.ChatSessionRepository;
import com.chatbot.repository.UserRepository;
import com.chatbot.service.ChatService.ChatAnswer;
import com.chatbot.service.ChatService.SourceSnippet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public ChatHistoryService(
            ChatSessionRepository sessionRepository,
            ChatMessageRepository messageRepository,
            UserRepository userRepository,
            ChatService chatService
    ) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.chatService = chatService;
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
     * Obtiene todas las sesiones de chat de un usuario ordenadas por fecha de actualización descendente.
     */
    public List<ChatSession> getSessionsForUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));
        return sessionRepository.findByUserOrderByUpdatedAtDesc(user);
    }

    /**
     * Obtiene todos los mensajes de una sesión específica, verificando la propiedad del usuario.
     */
    public List<ChatMessage> getMessagesInSession(UUID userId, UUID sessionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));

        ChatSession session = sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de chat no encontrada o no pertenece al usuario."));

        return messageRepository.findBySessionOrderByCreatedAtAsc(session);
    }

    /**
     * Elimina una sesión de chat y todos sus mensajes (por cascada de base de datos).
     */
    @Transactional
    public void deleteSession(UUID userId, UUID sessionId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));

        ChatSession session = sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de chat no encontrada o no pertenece al usuario."));

        sessionRepository.delete(session);
    }

    /**
     * Procesa una pregunta del usuario dentro de una sesión, realiza la consulta RAG,
     * guarda tanto la pregunta como la respuesta con sus fuentes, y actualiza el título si es necesario.
     */
    @Transactional
    public ChatAnswer askInSession(UUID userId, UUID sessionId, String question) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));

        ChatSession session = sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de chat no encontrada o no pertenece al usuario."));

        // 1. Guardar el mensaje del usuario
        ChatMessage userMessage = new ChatMessage();
        userMessage.setSession(session);
        userMessage.setRole("USER");
        userMessage.setContent(question);
        messageRepository.save(userMessage);

        // 2. Obtener la respuesta del RAG ChatService
        ChatAnswer chatAnswer = chatService.askQuestion(question);

        // Mapear los SourceSnippet de la respuesta a entidades ChatSource
        List<ChatSource> sources = chatAnswer.sources().stream()
                .map(src -> new ChatSource(
                        src.documentName(),
                        src.chunkIndex(),
                        src.snippet()
                ))
                .collect(Collectors.toList());

        // 3. Guardar el mensaje de respuesta de la IA (MODEL)
        ChatMessage modelMessage = new ChatMessage();
        modelMessage.setSession(session);
        modelMessage.setRole("MODEL");
        modelMessage.setContent(chatAnswer.answer());
        modelMessage.setSources(sources);
        messageRepository.save(modelMessage);

        // 4. Si el título de la sesión es "Nueva Conversación", renombrarlo según la pregunta del usuario
        if ("Nueva Conversación".equals(session.getTitle())) {
            String newTitle = question.length() > 40 ? question.substring(0, 37) + "..." : question;
            session.setTitle(newTitle);
        }

        // Actualizar fecha de modificación de la sesión
        sessionRepository.save(session);

        return chatAnswer;
    }
}
