package com.chatbot.controller;

import com.chatbot.model.Message;
import com.chatbot.model.SupportSession;
import com.chatbot.model.SupportStatus;
import com.chatbot.service.SupportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/support")
@CrossOrigin(origins = "http://localhost:4200")
public class SupportController {

    private static final Logger log = LoggerFactory.getLogger(SupportController.class);

    private final SupportService supportService;

    public SupportController(SupportService supportService) {
        this.supportService = supportService;
    }

    // DTO de respuesta seguro para SupportSession
    public record SupportSessionResponse(
            UUID id,
            UUID userId,
            String clientName,
            UUID supportId,
            String supportName,
            String status,
            String createdAt,
            String assignedAt,
            String closedAt,
            String summary) {
        public static SupportSessionResponse fromEntity(SupportSession session) {
            return new SupportSessionResponse(
                    session.getId(),
                    session.getUser().getId(),
                    session.getUser().getFullName(),
                    session.getSupport() != null ? session.getSupport().getId() : null,
                    session.getSupport() != null ? session.getSupport().getFullName() : null,
                    session.getStatus().name(),
                    session.getCreatedAt().toString(),
                    session.getAssignedAt() != null ? session.getAssignedAt().toString() : null,
                    session.getClosedAt() != null ? session.getClosedAt().toString() : null,
                    session.getSummary());
        }
    }

    // DTO de respuesta seguro para Message
    public record SupportMessageResponse(
            UUID id,
            UUID sessionId,
            UUID senderId,
            String senderType,
            String content,
            String createdAt) {
        public static SupportMessageResponse fromEntity(Message message) {
            return new SupportMessageResponse(
                    message.getId(),
                    message.getSession().getId(),
                    message.getSenderId(),
                    message.getSenderType().name(),
                    message.getContent(),
                    message.getCreatedAt().toString());
        }
    }

    private UUID getAuthenticatedUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.authentication.BadCredentialsException("Usuario no autenticado.");
        }
        return UUID.fromString(authentication.getName());
    }

    private String getAuthenticatedUserRole() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new org.springframework.security.authentication.BadCredentialsException("Usuario no autenticado.");
        }
        return authentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority().replace("ROLE_", ""))
                .findFirst()
                .orElse("CLIENT");
    }

    /**
     * El cliente solicita soporte en vivo. Crea una sesión en espera (WAITING) o
     * retorna la activa.
     */
    @PostMapping("/request")
    public ResponseEntity<?> requestSupport() {
        UUID userId = getAuthenticatedUserId();
        log.info("Client {} requested support", userId);
        try {
            SupportSession session = supportService.findOrCreateActiveSession(userId);
            return ResponseEntity.ok(SupportSessionResponse.fromEntity(session));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obtiene la sesión de soporte activa o en espera del cliente logueado (si
     * existe).
     */
    @GetMapping("/sessions/active")
    public ResponseEntity<SupportSessionResponse> getActiveSession() {
        UUID userId = getAuthenticatedUserId();
        return supportService.findActiveSession(userId)
                .map(session -> ResponseEntity.ok(SupportSessionResponse.fromEntity(session)))
                .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Obtiene el historial de mensajes para una sesión específica, validando permisos.
     * CLIENT: ve sus propios chats.
     * TECHNICIAN: ve mensajes de sus chats ACTIVES únicamente. No puede ver chats cerrados.
     * ADMIN: ve mensajes de cualquier chat.
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<?> getMessages(@PathVariable UUID sessionId) {
        UUID authenticatedId = getAuthenticatedUserId();
        String role = getAuthenticatedUserRole();

        SupportSession session = supportService.getSessionById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de soporte no encontrada con ID: " + sessionId));

        if ("ADMIN".equals(role)) {
            List<SupportMessageResponse> messages = supportService.getMessagesForSession(sessionId).stream()
                    .map(SupportMessageResponse::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(messages);
        } else if ("TECHNICIAN".equals(role)) {
            // Verificar si está asignado y la sesión está activa
            if (session.getStatus() == SupportStatus.ACTIVE && session.getSupport() != null && session.getSupport().getId().equals(authenticatedId)) {
                List<SupportMessageResponse> messages = supportService.getMessagesForSession(sessionId).stream()
                        .map(SupportMessageResponse::fromEntity)
                        .collect(Collectors.toList());
                return ResponseEntity.ok(messages);
            } else {
                return ResponseEntity.status(403).body(Map.of("error", "No tienes permiso para ver los mensajes de esta sesión. Los técnicos no pueden ver el historial de conversaciones de sesiones cerradas."));
            }
        } else if ("CLIENT".equals(role)) {
            // Verificar si es dueño
            if (session.getUser().getId().equals(authenticatedId)) {
                List<SupportMessageResponse> messages = supportService.getMessagesForSession(sessionId).stream()
                        .map(SupportMessageResponse::fromEntity)
                        .collect(Collectors.toList());
                return ResponseEntity.ok(messages);
            } else {
                return ResponseEntity.status(403).body(Map.of("error", "No tienes permiso para acceder a esta sesión."));
            }
        }

        return ResponseEntity.status(403).body(Map.of("error", "Rol no autorizado."));
    }

    /**
     * Un técnico encola la aceptación de una sesión de soporte.
     */
    @PostMapping("/sessions/{sessionId}/accept")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<?> acceptSession(@PathVariable UUID sessionId) {
        UUID technicianId = getAuthenticatedUserId();
        log.info("Technician {} accepting support session {}", technicianId, sessionId);
        supportService.queueAcceptance(sessionId, technicianId);
        return ResponseEntity.ok(Map.of("message", "Solicitud de aceptación encolada. Procesando..."));
    }

    /**
     * Finaliza/cierra una sesión de soporte (puede hacerlo el cliente o el técnico asignado).
     */
    @PostMapping("/sessions/{sessionId}/close")
    public ResponseEntity<?> closeSession(@PathVariable UUID sessionId) {
        log.info("Closing support session {}", sessionId);
        UUID userId = getAuthenticatedUserId();
        String role = getAuthenticatedUserRole();
        
        SupportSession session = supportService.getSessionById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Sesión de soporte no encontrada."));

        // Validar permisos de cierre: Solo el técnico asignado puede cerrar
        if (!"TECHNICIAN".equals(role)) {
            return ResponseEntity.status(403).body(Map.of("error", "Solo el técnico asignado puede cerrar la conversación de soporte."));
        }
        if (session.getSupport() == null || !session.getSupport().getId().equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error", "No puedes cerrar una sesión que no tienes asignada."));
        }

        SupportSession closedSession = supportService.closeSession(sessionId);
        return ResponseEntity.ok(SupportSessionResponse.fromEntity(closedSession));
    }

    /**
     * Lista todas las sesiones en espera (solo técnicos).
     */
    @GetMapping("/sessions/waiting")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<List<SupportSessionResponse>> getWaitingSessions() {
        List<SupportSessionResponse> waiting = supportService.getWaitingSessions().stream()
                .map(SupportSessionResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(waiting);
    }

    /**
     * Lista las sesiones activas asignadas al técnico logueado.
     */
    @GetMapping("/sessions/technician/active")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<List<SupportSessionResponse>> getTechnicianActiveSessions() {
        UUID technicianId = getAuthenticatedUserId();
        List<SupportSessionResponse> active = supportService.getActiveSessionsForTechnician(technicianId).stream()
                .map(SupportSessionResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(active);
    }

    /**
     * Lista las sesiones de soporte cerradas/finalizadas del técnico logueado (sin mensajes).
     */
    @GetMapping("/sessions/technician/history")
    @PreAuthorize("hasRole('TECHNICIAN')")
    public ResponseEntity<List<SupportSessionResponse>> getTechnicianSessionHistory() {
        UUID technicianId = getAuthenticatedUserId();
        List<SupportSessionResponse> history = supportService.getClosedSessionsForTechnician(technicianId).stream()
                .map(SupportSessionResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }

    /**
     * Lista todas las sesiones cerradas de soporte en el sistema (solo administradores).
     */
    @GetMapping("/sessions/admin/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SupportSessionResponse>> getAdminSessionHistory() {
        List<SupportSessionResponse> history = supportService.getAllClosedSessions().stream()
                .map(SupportSessionResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }
}
