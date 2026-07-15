package com.chatbot.service;

import com.chatbot.model.*;
import com.chatbot.repository.ChatMessageRepository;
import com.chatbot.repository.ChatSessionRepository;
import com.chatbot.repository.MessageRepository;
import com.chatbot.repository.SupportSessionRepository;
import com.chatbot.repository.UserRepository;
import com.chatbot.websocket.SupportWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupportServiceTest {

    @Mock
    private SupportSessionRepository supportSessionRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private GeminiService geminiService;

    @Mock
    private SupportWebSocketHandler webSocketHandler;

    @Mock
    private SupportAcceptanceQueue acceptanceQueue;

    private SupportService supportService;

    private User clientUser;
    private User techUser;
    private Role clientRole;
    private Role techRole;

    @BeforeEach
    void setUp() {
        supportService = new SupportService(
                supportSessionRepository,
                messageRepository,
                userRepository,
                chatSessionRepository,
                chatMessageRepository,
                geminiService,
                webSocketHandler,
                acceptanceQueue,
                2 // maxSimultaneousConversations
        );

        clientRole = new Role(1L, NameRol.CLIENT);
        techRole = new Role(2L, NameRol.TECHNICIAN);

        clientUser = new User();
        clientUser.setId(UUID.randomUUID());
        clientUser.setFullName("Client Name");
        clientUser.setId_rol(clientRole);

        techUser = new User();
        techUser.setId(UUID.randomUUID());
        techUser.setFullName("Tech Name");
        techUser.setId_rol(techRole);
    }

    @Test
    void findOrCreateActiveSession_ShouldGenerateSummaryWithGemini_AndSaveSession() {
        // Arrange
        UUID clientId = clientUser.getId();
        ChatSession chatSession = new ChatSession(UUID.randomUUID(), clientUser, "Chat title", LocalDateTime.now(), LocalDateTime.now());
        ChatMessage msg1 = new ChatMessage(UUID.randomUUID(), chatSession, "USER", "My machine is broken", null, null, null, LocalDateTime.now());
        ChatMessage msg2 = new ChatMessage(UUID.randomUUID(), chatSession, "MODEL", "Let me check...", null, null, null, LocalDateTime.now());

        when(userRepository.findById(clientId)).thenReturn(Optional.of(clientUser));
        when(supportSessionRepository.findActiveSessionsWithUserAndSupport(any(), any())).thenReturn(Collections.emptyList());
        when(chatSessionRepository.findFirstByUserOrderByUpdatedAtDesc(clientUser)).thenReturn(Optional.of(chatSession));
        when(chatMessageRepository.findBySessionOrderByCreatedAtAsc(chatSession)).thenReturn(Arrays.asList(msg1, msg2));
        when(geminiService.generateAnswer(anyString(), anyString())).thenReturn("AI generated summary");
        
        SupportSession savedSession = new SupportSession();
        savedSession.setId(UUID.randomUUID());
        savedSession.setUser(clientUser);
        savedSession.setSummary("AI generated summary");
        savedSession.setStatus(SupportStatus.WAITING);
        savedSession.setCreatedAt(LocalDateTime.now());

        when(supportSessionRepository.save(any(SupportSession.class))).thenReturn(savedSession);

        // Act
        SupportSession session = supportService.findOrCreateActiveSession(clientId);

        // Assert
        assertThat(session.getSummary()).isEqualTo("AI generated summary");
        verify(supportSessionRepository).save(any(SupportSession.class));
        verify(webSocketHandler).sendToUser(eq(clientId), any());
        verify(webSocketHandler).broadcastToTechnicians(any());
    }

    @Test
    void queueAcceptance_ShouldAddToQueue() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        UUID techId = UUID.randomUUID();

        // Act
        supportService.queueAcceptance(sessionId, techId);

        // Assert
        verify(acceptanceQueue).addRequest(sessionId, techId);
    }

    @Test
    void processAcceptance_WhenSessionNotWaiting_ShouldSendClaimFailed() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        UUID techId = techUser.getId();
        SupportSession session = new SupportSession();
        session.setId(sessionId);
        session.setStatus(SupportStatus.ACTIVE);

        when(supportSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));

        // Act
        supportService.processAcceptance(sessionId, techId);

        // Assert
        verify(webSocketHandler).sendToUser(eq(techId), argThat(payload -> {
            Map<?, ?> map = (Map<?, ?>) payload;
            return "SESSION_CLAIM_FAILED".equals(map.get("type")) && "La sesión ya fue tomada por otro técnico o ya no está en espera.".equals(map.get("reason"));
        }));
        verify(supportSessionRepository, never()).save(any());
    }

    @Test
    void processAcceptance_WhenTechnicianNotTechnicianRole_ShouldSendClaimFailed() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        UUID clientAsTechId = clientUser.getId();
        SupportSession session = new SupportSession();
        session.setId(sessionId);
        session.setStatus(SupportStatus.WAITING);

        when(supportSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(userRepository.findById(clientAsTechId)).thenReturn(Optional.of(clientUser));

        // Act
        supportService.processAcceptance(sessionId, clientAsTechId);

        // Assert
        verify(webSocketHandler).sendToUser(eq(clientAsTechId), argThat(payload -> {
            Map<?, ?> map = (Map<?, ?>) payload;
            return "SESSION_CLAIM_FAILED".equals(map.get("type")) && "Solo los usuarios técnicos pueden aceptar sesiones de soporte.".equals(map.get("reason"));
        }));
        verify(supportSessionRepository, never()).save(any());
    }

    @Test
    void processAcceptance_WhenMaxSimultaneousConversationsReached_ShouldSendClaimFailed() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        UUID techId = techUser.getId();
        SupportSession session = new SupportSession();
        session.setId(sessionId);
        session.setStatus(SupportStatus.WAITING);

        when(supportSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(userRepository.findById(techId)).thenReturn(Optional.of(techUser));
        when(supportSessionRepository.countBySupportAndStatus(techUser, SupportStatus.ACTIVE)).thenReturn(2L); // Limit is 2

        // Act
        supportService.processAcceptance(sessionId, techId);

        // Assert
        verify(webSocketHandler).sendToUser(eq(techId), argThat(payload -> {
            Map<?, ?> map = (Map<?, ?>) payload;
            return "SESSION_CLAIM_FAILED".equals(map.get("type")) && map.get("reason").toString().contains("límite de conversaciones simultáneas");
        }));
        verify(supportSessionRepository, never()).save(any());
    }

    @Test
    void processAcceptance_Success_ShouldUpdateSessionAndSendAccepted() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        UUID techId = techUser.getId();
        SupportSession session = new SupportSession();
        session.setId(sessionId);
        session.setUser(clientUser);
        session.setStatus(SupportStatus.WAITING);

        when(supportSessionRepository.findByIdForUpdate(sessionId)).thenReturn(Optional.of(session));
        when(userRepository.findById(techId)).thenReturn(Optional.of(techUser));
        when(supportSessionRepository.countBySupportAndStatus(techUser, SupportStatus.ACTIVE)).thenReturn(1L); // Limit is 2

        // Act
        supportService.processAcceptance(sessionId, techId);

        // Assert
        assertThat(session.getStatus()).isEqualTo(SupportStatus.ACTIVE);
        assertThat(session.getSupport()).isEqualTo(techUser);
        verify(supportSessionRepository).save(session);
        verify(webSocketHandler).sendToUser(eq(clientUser.getId()), argThat(payload -> {
            Map<?, ?> map = (Map<?, ?>) payload;
            return "SESSION_ACCEPTED".equals(map.get("type"));
        }));
        verify(webSocketHandler).sendToUser(eq(techId), argThat(payload -> {
            Map<?, ?> map = (Map<?, ?>) payload;
            return "SESSION_ACCEPTED".equals(map.get("type"));
        }));
        verify(webSocketHandler).broadcastToTechnicians(argThat(payload -> {
            Map<?, ?> map = (Map<?, ?>) payload;
            return "SESSION_CLAIMED".equals(map.get("type"));
        }));
    }

    @Test
    void closeSession_ShouldUpdateStatusAndSendClosed() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        SupportSession session = new SupportSession();
        session.setId(sessionId);
        session.setUser(clientUser);
        session.setSupport(techUser);
        session.setStatus(SupportStatus.ACTIVE);

        when(supportSessionRepository.findById(sessionId)).thenReturn(Optional.of(session));
        when(supportSessionRepository.save(any(SupportSession.class))).thenReturn(session);

        // Act
        supportService.closeSession(sessionId);

        // Assert
        assertThat(session.getStatus()).isEqualTo(SupportStatus.RESOLVED);
        verify(supportSessionRepository).save(session);
        verify(webSocketHandler).sendToUser(eq(clientUser.getId()), argThat(payload -> {
            Map<?, ?> map = (Map<?, ?>) payload;
            return "SESSION_CLOSED".equals(map.get("type"));
        }));
        verify(webSocketHandler).sendToUser(eq(techUser.getId()), argThat(payload -> {
            Map<?, ?> map = (Map<?, ?>) payload;
            return "SESSION_CLOSED".equals(map.get("type"));
        }));
    }
}
