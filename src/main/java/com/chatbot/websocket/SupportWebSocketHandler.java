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
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
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
        UUID userId = UUID.fromString((String) session.getAttributes().get("userId"));
        userSessions.put(userId, session);
        String role = (String) session.getAttributes().get("role");
        log.info("WebSocket connection established. User: {}, Role: {}", userId, role);

        // Si es un cliente y tiene una sesión activa, notificarle de su conexión
        if ("CLIENT".equals(role)) {
            supportService.findActiveSession(userId).ifPresent(supportSession -> {
                sendToUser(userId, Map.of(
                        "type", "SESSION_STATUS",
                        "sessionId", supportSession.getId(),
                        "status", supportSession.getStatus().name()
                ));
            });
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UUID userId = UUID.fromString((String) session.getAttributes().get("userId"));
        userSessions.remove(userId);
        log.info("WebSocket connection closed. User: {}", userId);
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
                    // El cliente envía un mensaje. Buscamos su sesión de soporte activa
                    SupportSession supportSession = supportService.findActiveSession(senderId)
                            .orElseThrow(() -> new IllegalStateException("No tienes una sesión de soporte activa. Por favor, solicita soporte técnico primero."));
                    
                    // Guardar el mensaje en la base de datos
                    Message dbMessage = supportService.saveMessage(supportSession.getId(), senderId, SenderType.USER, content);

                    // Reenviar el mensaje al cliente (para confirmar recepción)
                    sendToUser(senderId, Map.of(
                            "type", "MESSAGE",
                            "id", dbMessage.getId(),
                            "sessionId", supportSession.getId(),
                            "senderId", senderId,
                            "senderType", "USER",
                            "content", content,
                            "createdAt", dbMessage.getCreatedAt().toString()
                    ));

                    // Si la sesión está activa y tiene un administrador asignado, reenviarle el mensaje
                    if (supportSession.getStatus() == SupportStatus.ACTIVE && supportSession.getSupport() != null) {
                        UUID supportId = supportSession.getSupport().getId();
                        sendToUser(supportId, Map.of(
                                "type", "MESSAGE",
                                "id", dbMessage.getId(),
                                "sessionId", supportSession.getId(),
                                "senderId", senderId,
                                "senderType", "USER",
                                "content", content,
                                "createdAt", dbMessage.getCreatedAt().toString()
                        ));
                    }
                } else if ("ADMIN".equals(role)) {
                    // El administrador envía un mensaje. Requiere un sessionId en el cuerpo
                    if (!jsonNode.has("sessionId")) {
                        sendToUser(senderId, Map.of("type", "ERROR", "message", "Missing sessionId parameter."));
                        return;
                    }
                    UUID sessionId = UUID.fromString(jsonNode.get("sessionId").asText());
                    SupportSession supportSession = supportService.getSessionById(sessionId)
                            .orElseThrow(() -> new IllegalArgumentException("Sesión de soporte no encontrada."));

                    // Verificar que el administrador sea el asignado
                    if (supportSession.getSupport() == null || !supportSession.getSupport().getId().equals(senderId)) {
                        sendToUser(senderId, Map.of("type", "ERROR", "message", "No estás asignado a esta sesión."));
                        return;
                    }

                    // Guardar el mensaje en la base de datos
                    Message dbMessage = supportService.saveMessage(sessionId, senderId, SenderType.ADMIN, content);

                    // Confirmar recepción al administrador
                    sendToUser(senderId, Map.of(
                            "type", "MESSAGE",
                            "id", dbMessage.getId(),
                            "sessionId", sessionId,
                            "senderId", senderId,
                            "senderType", "ADMIN",
                            "content", content,
                            "createdAt", dbMessage.getCreatedAt().toString()
                    ));

                    // Reenviar el mensaje al cliente
                    UUID clientId = supportSession.getUser().getId();
                    sendToUser(clientId, Map.of(
                            "type", "MESSAGE",
                            "id", dbMessage.getId(),
                            "sessionId", sessionId,
                            "senderId", senderId,
                            "senderType", "ADMIN",
                            "content", content,
                            "createdAt", dbMessage.getCreatedAt().toString()
                    ));
                }
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
     * Envía un mensaje de difusión a todos los administradores conectados.
     */
    public void broadcastToAdmins(Object payload) {
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
            if ("ADMIN".equals(role) && session.isOpen()) {
                try {
                    session.sendMessage(textMessage);
                    log.debug("Broadcasted WS message to admin {}", userId);
                } catch (IOException e) {
                    log.error("Failed to broadcast WS message to admin {}", userId, e);
                }
            }
        });
    }
}
