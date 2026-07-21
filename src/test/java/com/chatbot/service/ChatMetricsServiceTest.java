package com.chatbot.service;

import com.chatbot.controller.dto.ChatMetricsDashboard;
import com.chatbot.controller.dto.ChatMetricsSummary;
import com.chatbot.controller.dto.DislikedMessageDetail;
import com.chatbot.controller.dto.SessionFeedbackDetail;
import com.chatbot.model.ChatMessage;
import com.chatbot.model.ChatSession;
import com.chatbot.model.User;
import com.chatbot.repository.ChatMessageRepository;
import com.chatbot.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatMetricsServiceTest {

    @Mock
    private ChatSessionRepository chatSessionRepository;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    private ChatMetricsService chatMetricsService;

    @BeforeEach
    void setUp() {
        chatMetricsService = new ChatMetricsService(chatSessionRepository, chatMessageRepository);
    }

    @Test
    void getChatMetrics_ShouldCalculateSummaryCorrectly() {
        // Arrange
        when(chatSessionRepository.count()).thenReturn(10L);
        when(chatMessageRepository.count()).thenReturn(100L);
        when(chatMessageRepository.countByRole("MODEL")).thenReturn(50L);
        when(chatMessageRepository.countByRoleAndLikedIsNotNull("MODEL")).thenReturn(40L);
        when(chatMessageRepository.countByRoleAndLiked("MODEL", true)).thenReturn(30L);
        when(chatMessageRepository.countByRoleAndLiked("MODEL", false)).thenReturn(10L);

        when(chatSessionRepository.countByFeedbackUsefulIsNotNull()).thenReturn(5L);
        when(chatSessionRepository.countByFeedbackUseful(true)).thenReturn(4L);
        when(chatSessionRepository.countByFeedbackUseful(false)).thenReturn(1L);

        when(chatMessageRepository.findByRoleAndLikedOrderByCreatedAtDesc(eq("MODEL"), eq(false), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(chatSessionRepository.findByFeedbackUsefulIsNotNullOrderByClosedAtDesc(any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        // Act
        ChatMetricsDashboard dashboard = chatMetricsService.getChatMetrics(5);

        // Assert
        ChatMetricsSummary summary = dashboard.summary();
        assertThat(summary.totalSessions()).isEqualTo(10L);
        assertThat(summary.totalMessages()).isEqualTo(100L);
        assertThat(summary.totalModelMessages()).isEqualTo(50L);
        assertThat(summary.totalRatedMessages()).isEqualTo(40L);
        assertThat(summary.totalLikes()).isEqualTo(30L);
        assertThat(summary.totalDislikes()).isEqualTo(10L);
        assertThat(summary.satisfactionRate()).isEqualTo(75.0); // (30 / 40) * 100

        assertThat(summary.totalSessionsWithFeedback()).isEqualTo(5L);
        assertThat(summary.sessionsHelped()).isEqualTo(4L);
        assertThat(summary.sessionsNotHelped()).isEqualTo(1L);
        assertThat(summary.sessionSatisfactionRate()).isEqualTo(80.0); // (4 / 5) * 100

        assertThat(dashboard.recentDislikes()).isEmpty();
        assertThat(dashboard.recentFeedbacks()).isEmpty();
    }

    @Test
    void getChatMetrics_ShouldIncludeDislikedMessagesAndPrecedingQuestions() {
        // Arrange
        when(chatSessionRepository.count()).thenReturn(1L);
        when(chatMessageRepository.count()).thenReturn(2L);
        when(chatMessageRepository.countByRole("MODEL")).thenReturn(1L);
        when(chatMessageRepository.countByRoleAndLikedIsNotNull("MODEL")).thenReturn(1L);
        when(chatMessageRepository.countByRoleAndLiked("MODEL", true)).thenReturn(0L);
        when(chatMessageRepository.countByRoleAndLiked("MODEL", false)).thenReturn(1L);

        when(chatSessionRepository.countByFeedbackUsefulIsNotNull()).thenReturn(0L);
        when(chatSessionRepository.countByFeedbackUseful(true)).thenReturn(0L);
        when(chatSessionRepository.countByFeedbackUseful(false)).thenReturn(0L);

        UUID sessionId = UUID.randomUUID();
        ChatSession session = new ChatSession();
        session.setId(sessionId);

        ChatMessage modelMessage = new ChatMessage();
        modelMessage.setId(UUID.randomUUID());
        modelMessage.setSession(session);
        modelMessage.setRole("MODEL");
        modelMessage.setContent("Respuesta incorrecta de la IA");
        modelMessage.setCreatedAt(LocalDateTime.now());
        modelMessage.setLiked(false);

        ChatMessage userMessage = new ChatMessage();
        userMessage.setId(UUID.randomUUID());
        userMessage.setSession(session);
        userMessage.setRole("USER");
        userMessage.setContent("Pregunta del usuario");
        userMessage.setCreatedAt(modelMessage.getCreatedAt().minusSeconds(5));

        when(chatMessageRepository.findByRoleAndLikedOrderByCreatedAtDesc(eq("MODEL"), eq(false), any(Pageable.class)))
                .thenReturn(List.of(modelMessage));

        when(chatMessageRepository.findPrecedingUserMessages(eq(sessionId), eq(modelMessage.getCreatedAt()), any(Pageable.class)))
                .thenReturn(List.of(userMessage));

        when(chatSessionRepository.findByFeedbackUsefulIsNotNullOrderByClosedAtDesc(any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        // Act
        ChatMetricsDashboard dashboard = chatMetricsService.getChatMetrics(5);

        // Assert
        assertThat(dashboard.summary().satisfactionRate()).isEqualTo(0.0);
        assertThat(dashboard.recentDislikes()).hasSize(1);

        DislikedMessageDetail detail = dashboard.recentDislikes().get(0);
        assertThat(detail.messageId()).isEqualTo(modelMessage.getId());
        assertThat(detail.sessionId()).isEqualTo(sessionId);
        assertThat(detail.userQuestion()).isEqualTo("Pregunta del usuario");
        assertThat(detail.chatbotAnswer()).isEqualTo("Respuesta incorrecta de la IA");
    }

    @Test
    void getChatMetrics_ShouldIncludeRecentSessionFeedbacks() {
        // Arrange
        when(chatSessionRepository.count()).thenReturn(1L);
        when(chatMessageRepository.count()).thenReturn(0L);
        when(chatMessageRepository.countByRole("MODEL")).thenReturn(0L);
        when(chatMessageRepository.countByRoleAndLikedIsNotNull("MODEL")).thenReturn(0L);
        when(chatMessageRepository.countByRoleAndLiked("MODEL", true)).thenReturn(0L);
        when(chatMessageRepository.countByRoleAndLiked("MODEL", false)).thenReturn(0L);

        when(chatSessionRepository.countByFeedbackUsefulIsNotNull()).thenReturn(1L);
        when(chatSessionRepository.countByFeedbackUseful(true)).thenReturn(1L);
        when(chatSessionRepository.countByFeedbackUseful(false)).thenReturn(0L);

        User client = new User();
        client.setFullName("Juan Perez");

        ChatSession session = new ChatSession();
        session.setId(UUID.randomUUID());
        session.setTitle("Calibración de Sensor");
        session.setUser(client);
        session.setFeedbackUseful(true);
        session.setFeedbackComment("Excelente respuesta, muy rápido.");
        session.setClosedAt(LocalDateTime.now());

        when(chatMessageRepository.findByRoleAndLikedOrderByCreatedAtDesc(eq("MODEL"), eq(false), any(Pageable.class)))
                .thenReturn(Collections.emptyList());

        when(chatSessionRepository.findByFeedbackUsefulIsNotNullOrderByClosedAtDesc(any(Pageable.class)))
                .thenReturn(List.of(session));

        // Act
        ChatMetricsDashboard dashboard = chatMetricsService.getChatMetrics(5);

        // Assert
        assertThat(dashboard.recentFeedbacks()).hasSize(1);
        SessionFeedbackDetail detail = dashboard.recentFeedbacks().get(0);
        assertThat(detail.sessionId()).isEqualTo(session.getId());
        assertThat(detail.sessionTitle()).isEqualTo("Calibración de Sensor");
        assertThat(detail.clientFullName()).isEqualTo("Juan Perez");
        assertThat(detail.feedbackUseful()).isTrue();
        assertThat(detail.feedbackComment()).isEqualTo("Excelente respuesta, muy rápido.");
    }
}
