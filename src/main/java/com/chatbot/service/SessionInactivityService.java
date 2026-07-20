package com.chatbot.service;

import com.chatbot.model.*;
import com.chatbot.repository.ChatMessageRepository;
import com.chatbot.repository.ChatSessionRepository;
import com.chatbot.repository.MessageRepository;
import com.chatbot.repository.SupportSessionRepository;
import com.chatbot.websocket.SupportWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SessionInactivityService {

    private static final Logger log = LoggerFactory.getLogger(SessionInactivityService.class);

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SupportSessionRepository supportSessionRepository;
    private final MessageRepository messageRepository;
    private final SupportWebSocketHandler webSocketHandler;

    @Value("${inactivity.prompt.seconds:30}")
    private int inactivityPromptSeconds;

    @Value("${inactivity.close.seconds:30}")
    private int inactivityCloseSeconds;

    public SessionInactivityService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            SupportSessionRepository supportSessionRepository,
            MessageRepository messageRepository,
            SupportWebSocketHandler webSocketHandler
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.supportSessionRepository = supportSessionRepository;
        this.messageRepository = messageRepository;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Tarea programada que se ejecuta cada 5 segundos para verificar la inactividad de los usuarios.
     * Si no responden después del tiempo límite, se les envía un mensaje recordatorio.
     * Si continúan sin responder después del segundo límite, se cierra la sesión.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void checkInactivity() {
        LocalDateTime now = LocalDateTime.now();

        // 1. Verificar Sesiones de Chat de la IA (ChatSession)
        List<ChatSession> openChatSessions = chatSessionRepository.findByIsClosedFalse();
        for (ChatSession session : openChatSessions) {
            try {
                if (session.getLastUserActivity() == null) {
                    session.setLastUserActivity(session.getCreatedAt());
                }

                if (Boolean.FALSE.equals(session.getPromptSent())) {
                    if (now.isAfter(session.getLastUserActivity().plusSeconds(inactivityPromptSeconds))) {
                        // Enviar mensaje automático de la IA (MODEL)
                        ChatMessage promptMsg = new ChatMessage();
                        promptMsg.setSession(session);
                        promptMsg.setRole("MODEL");
                        promptMsg.setContent("¿Sigues ahí o pudiste solucionar el problema?");
                        chatMessageRepository.save(promptMsg);

                        session.setPromptSent(true);
                        session.setLastUserActivity(now); // Reiniciar el contador de inactividad desde este mensaje
                        chatSessionRepository.save(session);

                        log.info("Reminder sent to ChatSession (IA) due to inactivity: {}", session.getId());
                    }
                } else {
                    if (now.isAfter(session.getLastUserActivity().plusSeconds(inactivityCloseSeconds))) {
                        // Cerrar la sesión automáticamente
                        session.setIsClosed(true);
                        session.setClosedAt(now);
                        chatSessionRepository.save(session);

                        log.info("ChatSession (IA) closed automatically due to inactivity: {}", session.getId());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing inactivity for ChatSession {}", session.getId(), e);
            }
        }

        // 2. Verificar Sesiones de Soporte Técnico en Vivo (SupportSession)
        // Se valida únicamente cuando la sesión de soporte está activa (status = ACTIVE)
        List<SupportSession> activeSupportSessions = supportSessionRepository.findByStatusOrderByCreatedAtDesc(SupportStatus.ACTIVE);
        for (SupportSession session : activeSupportSessions) {
            try {
                if (session.getLastUserActivity() == null) {
                    session.setLastUserActivity(session.getCreatedAt());
                }

                if (Boolean.FALSE.equals(session.getPromptSent())) {
                    if (now.isAfter(session.getLastUserActivity().plusSeconds(inactivityPromptSeconds))) {
                        // Enviar mensaje de recordatorio del SISTEMA
                        Message promptMsg = new Message();
                        promptMsg.setSession(session);
                        promptMsg.setSenderType(SenderType.SYSTEM);
                        promptMsg.setContent("¿Sigues ahí o pudiste solucionar el problema?");
                        promptMsg.setCreatedAt(now);
                        messageRepository.save(promptMsg);

                        session.setPromptSent(true);
                        session.setLastUserActivity(now); // Reiniciar el contador desde este mensaje
                        supportSessionRepository.save(session);

                        // Notificar a través de WebSockets tanto al cliente como al técnico
                        java.util.Map<String, Object> payload = new java.util.HashMap<>();
                        payload.put("type", "MESSAGE");
                        payload.put("id", promptMsg.getId() != null ? promptMsg.getId() : UUID.randomUUID());
                        payload.put("sessionId", session.getId());
                        payload.put("senderType", "SYSTEM");
                        payload.put("content", promptMsg.getContent());
                        payload.put("createdAt", promptMsg.getCreatedAt().toString());

                        webSocketHandler.sendToUser(session.getUser().getId(), payload);
                        if (session.getSupport() != null) {
                            webSocketHandler.sendToUser(session.getSupport().getId(), payload);
                        }

                        log.info("Reminder sent to SupportSession due to inactivity: {}", session.getId());
                    }
                } else {
                    if (now.isAfter(session.getLastUserActivity().plusSeconds(inactivityCloseSeconds))) {
                        // Cerrar la sesión de soporte automáticamente
                        session.setStatus(SupportStatus.RESOLVED);
                        session.setClosedAt(now);
                        supportSessionRepository.save(session);

                        // Notificar cierre mediante WebSockets al cliente
                        webSocketHandler.sendToUser(session.getUser().getId(), Map.of(
                                "type", "SYSTEM_MESSAGE",
                                "sessionId", session.getId(),
                                "content", "La sesión de soporte ha sido cerrada automáticamente por inactividad.",
                                "createdAt", now.toString()
                        ));
                        webSocketHandler.sendToUser(session.getUser().getId(), Map.of(
                                "type", "SESSION_CLOSED",
                                "sessionId", session.getId()
                        ));

                        // Notificar cierre mediante WebSockets al técnico asignado
                        if (session.getSupport() != null) {
                            webSocketHandler.sendToUser(session.getSupport().getId(), Map.of(
                                    "type", "SESSION_CLOSED",
                                    "sessionId", session.getId()
                            ));
                        }

                        log.info("SupportSession closed automatically due to inactivity: {}", session.getId());
                    }
                }
            } catch (Exception e) {
                log.error("Error processing inactivity for SupportSession {}", session.getId(), e);
            }
        }
    }
}
