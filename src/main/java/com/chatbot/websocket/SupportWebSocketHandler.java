package com.chatbot.websocket;

import com.chatbot.model.Message;
import com.chatbot.model.SenderType;
import com.chatbot.model.SupportSession;
import com.chatbot.model.SupportStatus;
import com.chatbot.service.SupportService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SupportWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SupportWebSocketHandler.class);
    private static final Map<UUID, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final SupportService supportService;

    public SupportWebSocketHandler(ObjectMapper objectMapper, @Lazy SupportService supportService) {
        this.objectMapper = objectMapper;
        this.supportService = supportService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            UUID userId = UUID.fromString((String) session.getAttributes().get("userId"));
            WebSocketSession decorator = new ConcurrentWebSocketSessionDecorator(session, 5000, 64 * 1024);
            userSessions.put(userId, decorator);
            String role = (String) session.getAttributes().get("role");
            log.info("WebSocket connection established. User: {}, Role: {}", userId, role);

            // Si es un cliente y tiene una sesión activa, notificarle de su conexión
            if ("CLIENT".equals(role)) {
                supportService.findActiveSession(userId).ifPresent(supportSession -> {
                    session.getAttributes().put("activeSupportSession", supportSession);
                    sendToUser(userId, Map.of(
                            "type", "SESSION_STATUS",
                            "sessionId", supportSession.getId(),
                            "status", supportSession.getStatus().name()
                    ));
                });
            }
        } catch (Exception e) {
            log.error("Error in afterConnectionEstablished: {}", e.getMessage(), e);
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        try {
            UUID userId = UUID.fromString((String) session.getAttributes().get("userId"));
            userSessions.remove(userId);
            session.getAttributes().clear();
            log.info("WebSocket connection closed. User: {}", userId);
        } catch (Exception e) {
            log.error("Error in afterConnectionClosed: {}", e.getMessage(), e);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        UUID senderId = UUID.fromString((String) session.getAttributes().get("userId"));
        String role = (String) session.getAttributes().get("role");

        try {
            JsonNode jsonNode = objectMapper.readTree(message.getPayload());
            String type = jsonNode.has("type") ? jsonNode.get("type").asText() : "MESSAGE";

            if ("MESSAGE".equals(type)) {
                String content = jsonNode.get("content").asText();

                if ("CLIENT".equals(role)) {
                    // El cliente envía un mensaje. Obtenemos sesión desde caché o DB
                    SupportSession supportSession = (SupportSession) session.getAttributes().get("activeSupportSession");
                    if (supportSession == null || supportSession.getStatus() == SupportStatus.RESOLVED || supportSession.getStatus() == SupportStatus.EXPIRED) {
                        supportSession = supportService.findActiveSession(senderId)
                                .orElseThrow(() -> new IllegalStateException("No tienes una sesión de soporte activa. Por favor, solicita soporte técnico primero."));
                        session.getAttributes().put("activeSupportSession", supportSession);
                    } else if (supportSession.getStatus() == SupportStatus.WAITING) {
                        // Refrescar para verificar si ya fue asignado a un técnico
                        SupportSession reloaded = supportService.getSessionById(supportSession.getId()).orElse(supportSession);
                        session.getAttributes().put("activeSupportSession", reloaded);
                        supportSession = reloaded;
                    }
                    
                    // Guardar el mensaje en la base de datos usando objeto precargado (1 solo INSERT directo)
                    Message dbMessage = supportService.saveMessage(supportSession, senderId, SenderType.USER, content);

                    Map<String, Object> payload = Map.of(
                            "type", "MESSAGE",
                            "id", dbMessage.getId(),
                            "sessionId", supportSession.getId(),
                            "senderId", senderId,
                            "senderType", "USER",
                            "content", content,
                            "createdAt", dbMessage.getCreatedAt().toString()
                    );

                    // Reenviar el mensaje al cliente (para confirmar recepción con ID real)
                    sendToUser(senderId, payload);

                    // Si la sesión está activa y tiene un técnico asignado, reenviarle el mensaje
                    if (supportSession.getStatus() == SupportStatus.ACTIVE && supportSession.getSupport() != null) {
                        UUID supportId = supportSession.getSupport().getId();
                        sendToUser(supportId, payload);
                    } else {
                        // Si la sesión está en espera o no tiene técnico asignado, notificar al cliente
                        sendToUser(senderId, Map.of(
                                "type", "SYSTEM_MESSAGE",
                                "sessionId", supportSession.getId(),
                                "content", "Por favor, espera unos segundos a que un técnico acepte tu solicitud.",
                                "createdAt", LocalDateTime.now().toString()
                        ));
                    }
                } else if ("TECHNICIAN".equals(role)) {
                    // El técnico envía un mensaje. Requiere un sessionId en el cuerpo
                    if (!jsonNode.has("sessionId")) {
                        sendToUser(senderId, Map.of("type", "ERROR", "message", "Missing sessionId parameter."));
                        return;
                    }
                    UUID sessionId = UUID.fromString(jsonNode.get("sessionId").asText());
                    SupportSession supportSession = (SupportSession) session.getAttributes().get("techSession_" + sessionId);
                    if (supportSession == null) {
                        supportSession = supportService.getSessionById(sessionId)
                                .orElseThrow(() -> new IllegalArgumentException("Sesión de soporte no encontrada."));
                        session.getAttributes().put("techSession_" + sessionId, supportSession);
                    }

                    // Verificar que el técnico sea el asignado
                    if (supportSession.getSupport() == null || !supportSession.getSupport().getId().equals(senderId)) {
                        sendToUser(senderId, Map.of("type", "ERROR", "message", "No estás asignado a esta sesión."));
                        return;
                    }

                    // Guardar el mensaje en la base de datos usando objeto precargado
                    Message dbMessage = supportService.saveMessage(supportSession, senderId, SenderType.TECHNICIAN, content);

                    Map<String, Object> payload = Map.of(
                            "type", "MESSAGE",
                            "id", dbMessage.getId(),
                            "sessionId", sessionId,
                            "senderId", senderId,
                            "senderType", "TECHNICIAN",
                            "content", content,
                            "createdAt", dbMessage.getCreatedAt().toString()
                    );

                    // Confirmar recepción al técnico
                    sendToUser(senderId, payload);

                    // Reenviar el mensaje al cliente
                    UUID clientId = supportSession.getUser().getId();
                    sendToUser(clientId, payload);
                }
            } else if ("REQUEST_SUPPORT".equals(type)) {
                if (!"CLIENT".equals(role)) {
                    sendToUser(senderId, Map.of("type", "ERROR", "message", "Solo los clientes pueden solicitar soporte."));
                    return;
                }
                SupportSession activeSession = supportService.findOrCreateActiveSession(senderId);
                session.getAttributes().put("activeSupportSession", activeSession);
                sendToUser(senderId, Map.of(
                        "type", "SESSION_STATUS",
                        "sessionId", activeSession.getId(),
                        "status", activeSession.getStatus().name()
                ));
            } else if ("ACCEPT_SUPPORT".equals(type)) {
                if (!"TECHNICIAN".equals(role)) {
                    sendToUser(senderId, Map.of("type", "ERROR", "message", "Solo los técnicos pueden aceptar solicitudes de soporte."));
                    return;
                }
                if (!jsonNode.has("sessionId")) {
                    sendToUser(senderId, Map.of("type", "ERROR", "message", "Falta el parámetro sessionId."));
                    return;
                }
                UUID sessionId = UUID.fromString(jsonNode.get("sessionId").asText());
                supportService.queueAcceptance(sessionId, senderId);
            } else if ("CLOSE_SUPPORT".equals(type)) {
                if (!jsonNode.has("sessionId")) {
                    sendToUser(senderId, Map.of("type", "ERROR", "message", "Falta el parámetro sessionId."));
                    return;
                }
                UUID sessionId = UUID.fromString(jsonNode.get("sessionId").asText());
                SupportSession supportSession = supportService.getSessionById(sessionId)
                        .orElseThrow(() -> new IllegalArgumentException("Sesión de soporte no encontrada."));

                // Validar permisos de cierre: Solo el técnico asignado puede cerrar
                if (!"TECHNICIAN".equals(role)) {
                    sendToUser(senderId, Map.of("type", "ERROR", "message", "Solo el técnico asignado puede cerrar la conversación de soporte."));
                    return;
                }
                if (supportSession.getSupport() == null || !supportSession.getSupport().getId().equals(senderId)) {
                    sendToUser(senderId, Map.of("type", "ERROR", "message", "No puedes cerrar una sesión que no tienes asignada."));
                    return;
                }

                supportService.closeSession(sessionId);
                session.getAttributes().remove("activeSupportSession");
                session.getAttributes().remove("techSession_" + sessionId);
            } else if ("PING".equals(type)) {
                sendToUser(senderId, Map.of("type", "PONG"));
            }
        } catch (Exception e) {
            log.error("Error processing WebSocket message from user {}", senderId, e);
            try {
                sendToUser(senderId, Map.of("type", "ERROR", "message", "Error: " + e.getMessage()));
            } catch (Exception ex) {
                // Ignore
            }
        }
    }

    /**
     * Envía un mensaje a un usuario específico si está conectado por WebSocket.
     */
    public void sendToUser(UUID userId, Object payload) {
        WebSocketSession session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                String json = objectMapper.writeValueAsString(payload);
                session.sendMessage(new TextMessage(json));
                log.debug("Sent WS message to user {}: {}", userId, json);
            } catch (IOException e) {
                log.error("Failed to send WS message to user {}", userId, e);
            }
        } else {
            log.debug("User {} is not connected via WebSocket", userId);
        }
    }

    /**
     * Envía un mensaje de difusión a todos los técnicos conectados.
     */
    public void broadcastToTechnicians(Object payload) {
        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (IOException e) {
            log.error("Failed to serialize broadcast payload", e);
            return;
        }

        TextMessage textMessage = new TextMessage(json);
        userSessions.forEach((userId, session) -> {
            String role = (String) session.getAttributes().get("role");
            if ("TECHNICIAN".equals(role) && session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                    log.debug("Broadcasted WS message to technician {}", userId);
                } catch (IOException e) {
                    log.error("Failed to broadcast WS message to technician {}", userId, e);
                }
            }
        });
    }

    public void updateClientCachedSession(UUID userId, SupportSession session) {
        WebSocketSession wsSession = userSessions.get(userId);
        if (wsSession != null) {
            wsSession.getAttributes().put("activeSupportSession", session);
        }
    }

    public void clearClientCachedSession(UUID userId, UUID sessionId) {
        WebSocketSession wsSession = userSessions.get(userId);
        if (wsSession != null) {
            wsSession.getAttributes().remove("activeSupportSession");
            wsSession.getAttributes().remove("techSession_" + sessionId);
        }
    }
}
