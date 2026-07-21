package com.chatbot.service;

import com.chatbot.model.*;
import com.chatbot.repository.ChatMessageRepository;
import com.chatbot.repository.ChatSessionRepository;
import com.chatbot.repository.SupportSessionRepository;
import com.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatHistoryServiceTest {

    @Mock
    private ChatSessionRepository sessionRepository;

    @Mock
    private ChatMessageRepository messageRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ChatService chatService;

    @Mock
    private SupportSessionRepository supportSessionRepository;

    private ChatHistoryService chatHistoryService;

    private User user;

    @BeforeEach
    void setUp() {
        chatHistoryService = new ChatHistoryService(
                sessionRepository,
                messageRepository,
                userRepository,
                chatService,
                supportSessionRepository
        );

        user = new User();
        user.setId(UUID.randomUUID());
    }

    @Test
    void rateMessage_ShouldSetLikedStatus_WhenMessageBelongsToUserAndIsAI() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setUser(user);

        ChatMessage message = new ChatMessage();
        message.setId(messageId);
        message.setSession(session);
        message.setRole("MODEL");

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ChatMessage likedMessage = chatHistoryService.rateMessage(user.getId(), messageId, true);

        // Assert
        assertThat(likedMessage.getLiked()).isTrue();
        verify(messageRepository).save(message);
    }

    @Test
    void rateMessage_ShouldThrowException_WhenMessageDoesNotBelongToUser() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        User anotherUser = new User();
        anotherUser.setId(UUID.randomUUID());

        ChatSession session = new ChatSession();
        session.setUser(anotherUser);

        ChatMessage message = new ChatMessage();
        message.setId(messageId);
        message.setSession(session);
        message.setRole("MODEL");

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        // Act & Assert
        assertThatThrownBy(() -> chatHistoryService.rateMessage(user.getId(), messageId, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El mensaje no pertenece a una sesión de este usuario.");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void rateMessage_ShouldThrowException_WhenMessageIsFromUser() {
        // Arrange
        UUID messageId = UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setUser(user);

        ChatMessage message = new ChatMessage();
        message.setId(messageId);
        message.setSession(session);
        message.setRole("USER");

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(message));

        // Act & Assert
        assertThatThrownBy(() -> chatHistoryService.rateMessage(user.getId(), messageId, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Solo se pueden calificar los mensajes de la IA.");
        verify(messageRepository, never()).save(any());
    }

    @Test
    void submitFeedback_ShouldSaveFeedbackAndCloseSession_WhenSessionBelongsToUser() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setUser(user);
        session.setIsClosed(false);

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(sessionRepository.findByIdAndUser(sessionId, user)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        ChatSession updatedSession = chatHistoryService.submitFeedback(user.getId(), sessionId, true, "Excelente bot");

        // Assert
        assertThat(updatedSession.getFeedbackUseful()).isTrue();
        assertThat(updatedSession.getFeedbackComment()).isEqualTo("Excelente bot");
        assertThat(updatedSession.getIsClosed()).isTrue();
        assertThat(updatedSession.getClosedAt()).isNotNull();
        verify(sessionRepository).save(session);
    }

    @Test
    void submitFeedback_ShouldThrowException_WhenSessionHasAlreadyBeenRated() {
        // Arrange
        UUID sessionId = UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setId(sessionId);
        session.setUser(user);
        session.setFeedbackUseful(true); // Ya calificado

        when(userRepository.findById(user.getId())).thenReturn(Optional.of(user));
        when(sessionRepository.findByIdAndUser(sessionId, user)).thenReturn(Optional.of(session));

        // Act & Assert
        assertThatThrownBy(() -> chatHistoryService.submitFeedback(user.getId(), sessionId, false, "Intento modificar"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La sesión de chat ya ha sido calificada.");
        verify(sessionRepository, never()).save(any());
    }
}
