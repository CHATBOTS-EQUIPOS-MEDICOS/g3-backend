package com.chatbot.service;

import com.chatbot.model.*;
import com.chatbot.repository.ChatMessageRepository;
import com.chatbot.repository.ChatSessionRepository;
import com.chatbot.repository.MessageRepository;
import com.chatbot.repository.SupportSessionRepository;
import com.chatbot.websocket.SupportWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionInactivityServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private SupportSessionRepository supportSessionRepository;

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SupportWebSocketHandler webSocketHandler;

    private SessionInactivityService inactivityService;

    @BeforeEach
    void setUp() {
        inactivityService = new SessionInactivityService(
                chatSessionRepository,
                chatMessageRepository,
                supportSessionRepository,
                messageRepository,
                webSocketHandler
        );

        // Set properties using ReflectionTestUtils (30 seconds limit)
        ReflectionTestUtils.setField(inactivityService, "inactivityPromptSeconds", 30);
        ReflectionTestUtils.setField(inactivityService, "inactivityCloseSeconds", 30);
    }

    @Test
    void checkInactivity_ShouldSendPromptToChatSession_WhenInactivityPeriodExceeded() {
        // Arrange
        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID());
        session.setIsClosed(false);
        session.setPromptSent(false);
        session.setLastUserActivity(LocalDateTime.now().minusSeconds(40));

        when(chatSessionRepository.findByIsClosedFalse()).thenReturn(Collections.singletonList(session));
        when(supportSessionRepository.findByStatusOrderByCreatedAtDesc(SupportStatus.ACTIVE)).thenReturn(Collections.emptyList());

        // Act
        inactivityService.checkInactivity();

        // Assert
        verify(chatMessageRepository).save(argThat(msg -> 
                msg.getSession().equals(session) &&
                "MODEL".equals(msg.getRole()) &&
                "¿Sigues ahí o pudiste solucionar el problema?".equals(msg.getContent())
        ));
        verify(chatSessionRepository).save(session);
        assertThat(session.getPromptSent()).isTrue();
        assertThat(session.getLastUserActivity()).isAfter(LocalDateTime.now().minusSeconds(5));
    }

    @Test
    void checkInactivity_ShouldCloseChatSession_WhenInactivityPeriodExceededAfterPrompt() {
        // Arrange
        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID());
        session.setIsClosed(false);
        session.setPromptSent(true);
        session.setLastUserActivity(LocalDateTime.now().minusSeconds(40));

        when(chatSessionRepository.findByIsClosedFalse()).thenReturn(Collections.singletonList(session));
        when(supportSessionRepository.findByStatusOrderByCreatedAtDesc(SupportStatus.ACTIVE)).thenReturn(Collections.emptyList());

        // Act
        inactivityService.checkInactivity();

        // Assert
        verify(chatSessionRepository).save(session);
        assertThat(session.getIsClosed()).isTrue();
        assertThat(session.getClosedAt()).isNotNull();
    }

    @Test
    void checkInactivity_ShouldSendPromptToSupportSession_WhenInactivityPeriodExceeded() {
        // Arrange
        User user = new User();
        user.setId(UUID.randomUUID());

        SupportSession session = new SupportSession();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setStatus(SupportStatus.ACTIVE);
        session.setPromptSent(false);
        session.setLastUserActivity(LocalDateTime.now().minusSeconds(40));

        when(chatSessionRepository.findByIsClosedFalse()).thenReturn(Collections.emptyList());
        when(supportSessionRepository.findByStatusOrderByCreatedAtDesc(SupportStatus.ACTIVE)).thenReturn(Collections.singletonList(session));

        // Act
        inactivityService.checkInactivity();

        // Assert
        verify(messageRepository).save(argThat(msg -> 
                msg.getSession().equals(session) &&
                msg.getSenderType() == SenderType.SYSTEM &&
                "¿Sigues ahí o pudiste solucionar el problema?".equals(msg.getContent())
        ));
        verify(supportSessionRepository).save(session);
        verify(webSocketHandler).sendToUser(eq(user.getId()), any());
        assertThat(session.getPromptSent()).isTrue();
        assertThat(session.getLastUserActivity()).isAfter(LocalDateTime.now().minusSeconds(5));
    }

    @Test
    void checkInactivity_ShouldCloseSupportSession_WhenInactivityPeriodExceededAfterPrompt() {
        // Arrange
        User user = new User();
        user.setId(UUID.randomUUID());

        SupportSession session = new SupportSession();
        session.setId(UUID.randomUUID());
        session.setUser(user);
        session.setStatus(SupportStatus.ACTIVE);
        session.setPromptSent(true);
        session.setLastUserActivity(LocalDateTime.now().minusSeconds(40));

        when(chatSessionRepository.findByIsClosedFalse()).thenReturn(Collections.emptyList());
        when(supportSessionRepository.findByStatusOrderByCreatedAtDesc(SupportStatus.ACTIVE)).thenReturn(Collections.singletonList(session));

        // Act
        inactivityService.checkInactivity();

        // Assert
        verify(supportSessionRepository).save(session);
        assertThat(session.getStatus()).isEqualTo(SupportStatus.RESOLVED);
        assertThat(session.getClosedAt()).isNotNull();
        // Verifies websocket message is sent to notify close event
        verify(webSocketHandler, atLeastOnce()).sendToUser(eq(user.getId()), any());
    }
}
