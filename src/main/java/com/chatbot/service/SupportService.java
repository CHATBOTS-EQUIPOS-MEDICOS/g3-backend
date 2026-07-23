package com.chatbot.service;

import com.chatbot.model.*;
import com.chatbot.repository.MessageRepository;
import com.chatbot.repository.SupportSessionRepository;
import com.chatbot.repository.UserRepository;
import com.chatbot.repository.ChatSessionRepository;
import com.chatbot.repository.ChatMessageRepository;
import com.chatbot.websocket.SupportWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SupportService {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

    private final SupportSessionRepository supportSessionRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final GeminiService geminiService;
    private final SupportWebSocketHandler webSocketHandler;
    private final SupportAcceptanceQueue acceptanceQueue;
    private final int maxSimultaneousConversations;

    public SupportService(
            SupportSessionRepository supportSessionRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            GeminiService geminiService,
            SupportWebSocketHandler webSocketHandler,
            SupportAcceptanceQueue acceptanceQueue,
            @Value("${support.max.simultaneous.conversations:3}") int maxSimultaneousConversations
    ) {
        this.supportSessionRepository = supportSessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.geminiService = geminiService;
        this.webSocketHandler = webSocketHandler;
        this.acceptanceQueue = acceptanceQueue;
        this.maxSimultaneousConversations = maxSimultaneousConversations;
    }

    /**
     * Busca la sesión de soporte activa o en espera de un usuario.
     */
    public Optional<SupportSession> findActiveSession(UUID userId) {
        List<SupportSession> sessions = supportSessionRepository.findActiveSessionsByUserIdWithUserAndSupport(
                userId, 
                Arrays.asList(SupportStatus.WAITING, SupportStatus.ACTIVE, SupportStatus.PENDING_USER)
        );
        if (sessions.isEmpty()) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null) {
                sessions = supportSessionRepository.findActiveSessionsWithUserAndSupport(
                        user,
                        Arrays.asList(SupportStatus.WAITING, SupportStatus.ACTIVE, SupportStatus.PENDING_USER)
                );
            }
        }
        return sessions.stream().findFirst();
    }

    /**
     * Obtiene una sesión de soporte activa, o crea una nueva si no existe. Genera un resumen mediante Gemini.
     */
    @Transactional
    public SupportSession findOrCreateActiveSession(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));

        if (user.getId_rol().getNameRol() != NameRol.CLIENT) {
            throw new IllegalStateException("Su rol en el sistema (" + user.getId_rol().getNameRol().name() + ") no puede solicitar soporte técnico.");
        }

        Optional<SupportSession> existingSession = findActiveSession(userId);
        if (existingSession.isPresent()) {
            return existingSession.get();
        }

        // Generar resumen de la última conversación del chatbot
        String summary = "El cliente ha solicitado soporte en vivo.";
        try {
            Optional<ChatSession> lastChatSession = chatSessionRepository.findFirstByUserOrderByUpdatedAtDesc(user);
            if (lastChatSession.isPresent()) {
                List<ChatMessage> chatMessages = chatMessageRepository.findBySessionOrderByCreatedAtAsc(lastChatSession.get());
                if (!chatMessages.isEmpty()) {
                    int skip = Math.max(0, chatMessages.size() - 15);
                    String conversationLog = chatMessages.stream().skip(skip)
                            .map(msg -> msg.getRole() + ": " + msg.getContent())
                            .collect(Collectors.joining("\n"));
                    
                    String prompt = "Por favor, resume en un párrafo corto (máximo 3 líneas) el problema técnico o la consulta que está experimentando el usuario basándote en la siguiente conversación:\n\n" + conversationLog;
                    String aiSummary = geminiService.generateAnswer(prompt, "Eres un asistente técnico experto en redactar resúmenes concisos de soporte.");
                    if (aiSummary != null && !aiSummary.trim().isEmpty()) {
                        summary = aiSummary.trim();
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error generating chatbot session summary: {}", e.getMessage(), e);
        }

        SupportSession session = new SupportSession();
        session.setUser(user);
        session.setSupport(null);
        session.setStatus(SupportStatus.WAITING);
        session.setCreatedAt(LocalDateTime.now());
        session.setAssignedAt(null);
        session.setSummary(summary);
        
        SupportSession savedSession = supportSessionRepository.save(session);
        log.info("Created support session in WAITING status: {}", savedSession.getId());

        // Notificar al cliente de que su sesión está en espera
        webSocketHandler.sendToUser(user.getId(), Map.of(
                "type", "SESSION_STATUS",
                "sessionId", savedSession.getId(),
                "status", SupportStatus.WAITING.name()
        ));

        // Notificar a todos los técnicos de una nueva solicitud de soporte en espera con el resumen
        webSocketHandler.broadcastToTechnicians(Map.of(
                "type", "NEW_WAITING_SESSION",
                "sessionId", savedSession.getId(),
                "clientId", user.getId(),
                "clientName", user.getFullName(),
                "summary", summary
        ));

        return savedSession;
    }

    /**
     * Guarda un mensaje en la sesión de soporte utilizando el objeto SupportSession ya cargado.
     */
    @Transactional
    public Message saveMessage(SupportSession session, UUID senderId, SenderType senderType, String content) {
        return savePreallocatedMessage(null, session, senderId, senderType, content, LocalDateTime.now());
    }

    /**
     * Guarda un mensaje con ID y timestamp opcionalmente pre-asignados.
     */
    @Transactional
    public Message savePreallocatedMessage(UUID messageId, SupportSession session, UUID senderId, SenderType senderType, String content, LocalDateTime createdAt) {
        if (session.getStatus() == SupportStatus.RESOLVED || session.getStatus() == SupportStatus.EXPIRED) {
            throw new IllegalStateException("La sesión de soporte está cerrada y no permite enviar más mensajes.");
        }

        Message message = new Message();
        if (messageId != null) {
            message.setId(messageId);
        }
        message.setSession(session);
        message.setSenderId(senderId);
        message.setSenderType(senderType);
        message.setContent(content);
        message.setCreatedAt(createdAt != null ? createdAt : LocalDateTime.now());

        if (senderType == SenderType.USER) {
            LocalDateTime now = LocalDateTime.now();
            if (session.getLastUserActivity() == null || session.getLastUserActivity().isBefore(now.minusSeconds(5))) {
                session.setLastUserActivity(now);
                session.setPromptSent(false);
                supportSessionRepository.save(session);
            }
        }

        return messageRepository.save(message);
    }

    /**
     * Guarda un mensaje en la sesión de soporte buscando por sessionId.
     */
    @Transactional
    public Message saveMessage(UUID sessionId, UUID senderId, SenderType senderType, String content) {
        SupportSession session = supportSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de soporte no encontrada con ID: " + sessionId));
        return saveMessage(session, senderId, senderType, content);
    }

    /**
     * Busca una sesión por su ID.
     */
    public Optional<SupportSession> getSessionById(UUID sessionId) {
        return supportSessionRepository.findByIdWithUserAndSupport(sessionId);
    }

    /**
     * Encola una solicitud de aceptación de soporte en la cola de aceptación.
     */
    public void queueAcceptance(UUID sessionId, UUID technicianId) {
        acceptanceQueue.addRequest(sessionId, technicianId);
    }

    /**
     * Procesa la aceptación de una sesión por parte de un técnico en un hilo único del Worker con bloqueo pesimista.
     */
    @Transactional
    public void processAcceptance(UUID sessionId, UUID technicianId) {
        // Bloqueo pesimista en base de datos para evitar doble asignación concurrente
        SupportSession session = supportSessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de soporte no encontrada con ID: " + sessionId));

        if (session.getStatus() != SupportStatus.WAITING) {
            webSocketHandler.sendToUser(technicianId, Map.of(
                    "type", "SESSION_CLAIM_FAILED",
                    "sessionId", sessionId,
                    "reason", "La sesión ya fue tomada por otro técnico o ya no está en espera."
            ));
            return;
        }

        User technician = userRepository.findById(technicianId)
                .orElseThrow(() -> new IllegalArgumentException("Técnico no encontrado con ID: " + technicianId));

        if (technician.getId_rol().getNameRol() != NameRol.TECHNICIAN) {
            webSocketHandler.sendToUser(technicianId, Map.of(
                    "type", "SESSION_CLAIM_FAILED",
                    "sessionId", sessionId,
                    "reason", "Solo los usuarios técnicos pueden aceptar sesiones de soporte."
            ));
            return;
        }

        // Límite de conversaciones simultáneas
        long activeConversations = supportSessionRepository.countBySupportAndStatus(technician, SupportStatus.ACTIVE);
        if (activeConversations >= maxSimultaneousConversations) {
            webSocketHandler.sendToUser(technicianId, Map.of(
                    "type", "SESSION_CLAIM_FAILED",
                    "sessionId", sessionId,
                    "reason", "Has alcanzado el límite de conversaciones simultáneas permitidas (" + maxSimultaneousConversations + ")."
            ));
            return;
        }

        session.setSupport(technician);
        session.setStatus(SupportStatus.ACTIVE);
        session.setAssignedAt(LocalDateTime.now());

        SupportSession updatedSession = supportSessionRepository.save(session);
        log.info("Support session {} accepted and assigned to technician {}", sessionId, technicianId);

        webSocketHandler.updateClientCachedSession(session.getUser().getId(), updatedSession);

        // Notificar al cliente
        webSocketHandler.sendToUser(session.getUser().getId(), Map.of(
                "type", "SESSION_ACCEPTED",
                "sessionId", sessionId,
                "supportId", technicianId,
                "supportName", technician.getFullName()
        ));

        // Notificar al técnico asignado
        webSocketHandler.sendToUser(technicianId, Map.of(
                "type", "SESSION_ACCEPTED",
                "sessionId", sessionId,
                "supportId", technicianId,
                "supportName", technician.getFullName()
        ));

        // Notificar a todos los técnicos para que retiren la sesión de su bandeja de espera
        webSocketHandler.broadcastToTechnicians(Map.of(
                "type", "SESSION_CLAIMED",
                "sessionId", sessionId
        ));
    }

    /**
     * Finaliza/resuelve una sesión de soporte activa.
     */
    @Transactional
    public SupportSession closeSession(UUID sessionId) {
        SupportSession session = supportSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de soporte no encontrada con ID: " + sessionId));

        if (session.getStatus() == SupportStatus.RESOLVED || session.getStatus() == SupportStatus.EXPIRED) {
            return session;
        }

        session.setStatus(SupportStatus.RESOLVED);
        session.setClosedAt(LocalDateTime.now());

        SupportSession updatedSession = supportSessionRepository.save(session);
        log.info("Support session {} resolved/closed", sessionId);

        webSocketHandler.clearClientCachedSession(session.getUser().getId(), sessionId);

        // Notificar al cliente con un mensaje de sistema y luego cerrar
        webSocketHandler.sendToUser(session.getUser().getId(), Map.of(
                "type", "SYSTEM_MESSAGE",
                "sessionId", sessionId,
                "content", "Su sesión de soporte ha sido finalizada.",
                "createdAt", LocalDateTime.now().toString()
        ));

        webSocketHandler.sendToUser(session.getUser().getId(), Map.of(
                "type", "SESSION_CLOSED",
                "sessionId", sessionId
        ));

        // Notificar al técnico si estaba asignado
        if (session.getSupport() != null) {
            webSocketHandler.sendToUser(session.getSupport().getId(), Map.of(
                    "type", "SESSION_CLOSED",
                    "sessionId", sessionId
            ));
        }

        return updatedSession;
    }

    /**
     * Obtiene la lista de sesiones esperando asignación.
     */
    public List<SupportSession> getWaitingSessions() {
        return supportSessionRepository.findByStatusOrderByCreatedAtDesc(SupportStatus.WAITING);
    }

    /**
     * Obtiene la lista de sesiones activas asignadas a un técnico.
     */
    public List<SupportSession> getActiveSessionsForTechnician(UUID technicianId) {
        User technician = userRepository.findById(technicianId)
                .orElseThrow(() -> new IllegalArgumentException("Técnico no encontrado con ID: " + technicianId));
        return supportSessionRepository.findBySupportAndStatusOrderByCreatedAtDesc(technician, SupportStatus.ACTIVE);
    }

    /**
     * Obtiene la lista de sesiones cerradas/historial asignadas a un técnico.
     */
    public List<SupportSession> getClosedSessionsForTechnician(UUID technicianId) {
        User technician = userRepository.findById(technicianId)
                .orElseThrow(() -> new IllegalArgumentException("Técnico no encontrado con ID: " + technicianId));
        return supportSessionRepository.findBySupportAndStatusInOrderByClosedAtDesc(
                technician,
                Arrays.asList(SupportStatus.RESOLVED, SupportStatus.EXPIRED)
        );
    }

    /**
     * Obtiene la lista de todas las sesiones de soporte cerradas (para revisión de administradores).
     */
    public List<SupportSession> getAllClosedSessions() {
        return supportSessionRepository.findByStatusInOrderByClosedAtDesc(
                Arrays.asList(SupportStatus.RESOLVED, SupportStatus.EXPIRED)
        );
    }

    /**
     * Obtiene el historial de mensajes para una sesión.
     */
    public List<Message> getMessagesForSession(UUID sessionId) {
        SupportSession session = supportSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de soporte no encontrada con ID: " + sessionId));
        return messageRepository.findBySessionOrderByCreatedAtAsc(session);
    }
}
