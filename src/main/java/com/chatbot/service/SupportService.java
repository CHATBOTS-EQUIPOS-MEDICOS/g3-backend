package com.chatbot.service;

import com.chatbot.model.*;
import com.chatbot.repository.MessageRepository;
import com.chatbot.repository.SupportSessionRepository;
import com.chatbot.repository.UserRepository;
import com.chatbot.websocket.SupportWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class SupportService {

    private static final Logger log = LoggerFactory.getLogger(SupportService.class);

    private final SupportSessionRepository supportSessionRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SupportWebSocketHandler webSocketHandler;

    public SupportService(
            SupportSessionRepository supportSessionRepository,
            MessageRepository messageRepository,
            UserRepository userRepository,
            SupportWebSocketHandler webSocketHandler
    ) {
        this.supportSessionRepository = supportSessionRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Busca la sesión de soporte activa o en espera de un usuario.
     */
    public Optional<SupportSession> findActiveSession(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));
        
        return supportSessionRepository.findFirstByUserAndStatusInOrderByCreatedAtDesc(
                user, 
                Arrays.asList(SupportStatus.WAITING, SupportStatus.ACTIVE, SupportStatus.PENDING_USER)
        );
    }

    /**
     * Obtiene una sesión de soporte activa, o crea una nueva si no existe y la asigna al administrador por defecto.
     */
    @Transactional
    public SupportSession findOrCreateActiveSession(UUID userId) {
        Optional<SupportSession> existingSession = findActiveSession(userId);
        if (existingSession.isPresent()) {
            return existingSession.get();
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + userId));

        // Buscar el administrador por defecto (primer administrador activo en base de datos)
        User defaultAdmin = userRepository.findActiveByRole(NameRol.ADMIN).stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No se encontró ningún administrador activo por defecto."));

        SupportSession session = new SupportSession();
        session.setUser(user);
        session.setSupport(defaultAdmin);
        session.setStatus(SupportStatus.ACTIVE);
        session.setCreatedAt(LocalDateTime.now());
        session.setAssignedAt(LocalDateTime.now());
        
        SupportSession savedSession = supportSessionRepository.save(session);
        log.info("Created active support session and assigned to default admin {}: {}", defaultAdmin.getId(), savedSession.getId());

        // Notificar al cliente
        webSocketHandler.sendToUser(user.getId(), Map.of(
                "type", "SESSION_ACCEPTED",
                "sessionId", savedSession.getId(),
                "supportId", defaultAdmin.getId(),
                "supportName", defaultAdmin.getFullName()
        ));

        // Notificar al administrador asignado
        webSocketHandler.sendToUser(defaultAdmin.getId(), Map.of(
                "type", "SESSION_ACCEPTED",
                "sessionId", savedSession.getId(),
                "supportId", defaultAdmin.getId(),
                "supportName", defaultAdmin.getFullName()
        ));

        return savedSession;
    }

    /**
     * Guarda un mensaje en la sesión de soporte.
     */
    @Transactional
    public Message saveMessage(UUID sessionId, UUID senderId, SenderType senderType, String content) {
        SupportSession session = supportSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de soporte no encontrada con ID: " + sessionId));

        if (session.getStatus() == SupportStatus.RESOLVED || session.getStatus() == SupportStatus.EXPIRED) {
            throw new IllegalStateException("La sesión de soporte está cerrada y no permite enviar más mensajes.");
        }

        Message message = new Message();
        message.setSession(session);
        message.setSenderId(senderId);
        message.setSenderType(senderType);
        message.setContent(content);
        message.setCreatedAt(LocalDateTime.now());

        return messageRepository.save(message);
    }

    /**
     * Busca una sesión por su ID.
     */
    public Optional<SupportSession> getSessionById(UUID sessionId) {
        return supportSessionRepository.findById(sessionId);
    }

    /**
     * Un administrador acepta una sesión de soporte en espera.
     */
    @Transactional
    public SupportSession acceptSession(UUID adminId, UUID sessionId) {
        SupportSession session = supportSessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de soporte no encontrada con ID: " + sessionId));

        if (session.getStatus() != SupportStatus.WAITING) {
            throw new IllegalStateException("La sesión no se encuentra en estado de espera (WAITING). Estado actual: " + session.getStatus());
        }

        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Administrador no encontrado con ID: " + adminId));

        session.setSupport(admin);
        session.setStatus(SupportStatus.ACTIVE);
        session.setAssignedAt(LocalDateTime.now());

        SupportSession updatedSession = supportSessionRepository.save(session);
        log.info("Support session {} accepted by admin {}", sessionId, adminId);

        // Notificar al cliente
        webSocketHandler.sendToUser(session.getUser().getId(), Map.of(
                "type", "SESSION_ACCEPTED",
                "sessionId", sessionId,
                "supportId", adminId,
                "supportName", admin.getFullName()
        ));

        // Notificar al administrador asignado
        webSocketHandler.sendToUser(adminId, Map.of(
                "type", "SESSION_ACCEPTED",
                "sessionId", sessionId,
                "supportId", adminId,
                "supportName", admin.getFullName()
        ));

        // Notificar a todos los administradores para que retiren la sesión de su bandeja de espera
        webSocketHandler.broadcastToAdmins(Map.of(
                "type", "SESSION_CLAIMED",
                "sessionId", sessionId
        ));

        return updatedSession;
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

        // Notificar al cliente
        webSocketHandler.sendToUser(session.getUser().getId(), Map.of(
                "type", "SESSION_CLOSED",
                "sessionId", sessionId
        ));

        // Notificar al administrador si estaba asignado
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
     * Obtiene la lista de sesiones activas de un administrador.
     */
    public List<SupportSession> getActiveSessionsForAdmin(UUID adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Administrador no encontrado con ID: " + adminId));
        return supportSessionRepository.findBySupportAndStatusOrderByCreatedAtDesc(admin, SupportStatus.ACTIVE);
    }

    /**
     * Obtiene la lista de sesiones resueltas o expiradas de un administrador.
     */
    public List<SupportSession> getClosedSessionsForAdmin(UUID adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Administrador no encontrado con ID: " + adminId));
        return supportSessionRepository.findBySupportAndStatusInOrderByClosedAtDesc(
                admin,
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
