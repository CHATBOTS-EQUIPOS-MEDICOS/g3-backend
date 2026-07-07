package com.chatbot.controller;

import com.chatbot.model.Message;
import com.chatbot.model.SupportSession;
import com.chatbot.service.SupportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
            String closedAt) {
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
                    session.getClosedAt() != null ? session.getClosedAt().toString() : null);
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

    /**
     * El cliente solicita soporte en vivo. Crea una sesión en espera (WAITING) o
     * retorna la activa.
     */
    @PostMapping("/request")
    public ResponseEntity<SupportSessionResponse> requestSupport() {
        UUID userId = getAuthenticatedUserId();
        log.info("Client {} requested support", userId);
        SupportSession session = supportService.findOrCreateActiveSession(userId);
        return ResponseEntity.ok(SupportSessionResponse.fromEntity(session));
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
     * Obtiene el historial de mensajes para una sesión específica.
     */
    @GetMapping("/sessions/{sessionId}/messages")
    public ResponseEntity<List<SupportMessageResponse>> getMessages(@PathVariable UUID sessionId) {
        // En un caso real, validaríamos que el usuario autenticado sea el dueño o sea
        // ADMIN
        List<SupportMessageResponse> messages = supportService.getMessagesForSession(sessionId).stream()
                .map(SupportMessageResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(messages);
    }

    /**
     * Un administrador acepta una sesión de soporte (cambia a ACTIVE y la asigna).
     */
    @PostMapping("/sessions/{sessionId}/accept")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<SupportSessionResponse> acceptSession(@PathVariable UUID sessionId) {
        UUID adminId = getAuthenticatedUserId();
        log.info("Admin {} accepting support session {}", adminId, sessionId);
        SupportSession session = supportService.acceptSession(adminId, sessionId);
        return ResponseEntity.ok(SupportSessionResponse.fromEntity(session));
    }

    /**
     * Finaliza/cierra una sesión de soporte.
     */
    @PostMapping("/sessions/{sessionId}/close")
    public ResponseEntity<SupportSessionResponse> closeSession(@PathVariable UUID sessionId) {
        log.info("Closing support session {}", sessionId);
        SupportSession session = supportService.closeSession(sessionId);
        return ResponseEntity.ok(SupportSessionResponse.fromEntity(session));
    }

    /**
     * Lista todas las sesiones en espera (solo administradores).
     */
    @GetMapping("/sessions/waiting")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SupportSessionResponse>> getWaitingSessions() {
        List<SupportSessionResponse> waiting = supportService.getWaitingSessions().stream()
                .map(SupportSessionResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(waiting);
    }

    /**
     * Lista las sesiones activas asignadas al administrador logueado.
     */
    @GetMapping("/sessions/admin/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SupportSessionResponse>> getAdminActiveSessions() {
        UUID adminId = getAuthenticatedUserId();
        List<SupportSessionResponse> active = supportService.getActiveSessionsForAdmin(adminId).stream()
                .map(SupportSessionResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(active);
    }

    /**
     * Lista las sesiones de soporte cerradas/finalizadas del administrador logueado.
     */
    @GetMapping("/sessions/admin/history")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<SupportSessionResponse>> getAdminSessionHistory() {
        UUID adminId = getAuthenticatedUserId();
        List<SupportSessionResponse> history = supportService.getClosedSessionsForAdmin(adminId).stream()
                .map(SupportSessionResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(history);
    }
}
