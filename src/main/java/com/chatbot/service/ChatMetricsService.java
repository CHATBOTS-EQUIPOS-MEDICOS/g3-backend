package com.chatbot.service;

import com.chatbot.controller.dto.ChatMetricsDashboard;
import com.chatbot.controller.dto.ChatMetricsSummary;
import com.chatbot.controller.dto.DislikedMessageDetail;
import com.chatbot.controller.dto.SessionFeedbackDetail;
import com.chatbot.model.ChatMessage;
import com.chatbot.model.ChatSession;
import com.chatbot.repository.ChatMessageRepository;
import com.chatbot.repository.ChatSessionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ChatMetricsService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;

    public ChatMetricsService(ChatSessionRepository chatSessionRepository, ChatMessageRepository chatMessageRepository) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
    }

    @Transactional(readOnly = true)
    public ChatMetricsDashboard getChatMetrics(int recentDislikesLimit) {
        long totalSessions = chatSessionRepository.count();
        long totalMessages = chatMessageRepository.count();
        long totalModelMessages = chatMessageRepository.countByRole("MODEL");
        long totalRatedMessages = chatMessageRepository.countByRoleAndLikedIsNotNull("MODEL");
        long totalLikes = chatMessageRepository.countByRoleAndLiked("MODEL", true);
        long totalDislikes = chatMessageRepository.countByRoleAndLiked("MODEL", false);

        double satisfactionRate = 0.0;
        if (totalRatedMessages > 0) {
            satisfactionRate = ((double) totalLikes / totalRatedMessages) * 100.0;
            // Redondear a 2 decimales
            satisfactionRate = Math.round(satisfactionRate * 100.0) / 100.0;
        }

        long totalSessionsWithFeedback = chatSessionRepository.countByFeedbackUsefulIsNotNull();
        long sessionsHelped = chatSessionRepository.countByFeedbackUseful(true);
        long sessionsNotHelped = chatSessionRepository.countByFeedbackUseful(false);

        double sessionSatisfactionRate = 0.0;
        if (totalSessionsWithFeedback > 0) {
            sessionSatisfactionRate = ((double) sessionsHelped / totalSessionsWithFeedback) * 100.0;
            sessionSatisfactionRate = Math.round(sessionSatisfactionRate * 100.0) / 100.0;
        }

        ChatMetricsSummary summary = new ChatMetricsSummary(
                totalSessions,
                totalMessages,
                totalModelMessages,
                totalRatedMessages,
                totalLikes,
                totalDislikes,
                satisfactionRate,
                totalSessionsWithFeedback,
                sessionsHelped,
                sessionsNotHelped,
                sessionSatisfactionRate
        );

        // Obtener respuestas del modelo calificadas negativamente
        Pageable limit = PageRequest.of(0, recentDislikesLimit);
        List<ChatMessage> dislikedMessages = chatMessageRepository.findByRoleAndLikedOrderByCreatedAtDesc("MODEL", false, limit);

        List<DislikedMessageDetail> recentDislikes = new ArrayList<>();
        for (ChatMessage msg : dislikedMessages) {
            UUID sessionId = msg.getSession().getId();
            // Buscar el mensaje de usuario anterior para tener el contexto de la pregunta
            Pageable topOne = PageRequest.of(0, 1);
            List<ChatMessage> precedingUser = chatMessageRepository.findPrecedingUserMessages(sessionId, msg.getCreatedAt(), topOne);

            String userQuestion = precedingUser.isEmpty() ? "Pregunta no encontrada" : precedingUser.get(0).getContent();
            recentDislikes.add(new DislikedMessageDetail(
                    msg.getId(),
                    sessionId,
                    userQuestion,
                    msg.getContent(),
                    msg.getCreatedAt()
            ));
        }

        // Obtener sesiones con feedback reciente
        List<ChatSession> ratedSessions = chatSessionRepository.findByFeedbackUsefulIsNotNullOrderByClosedAtDesc(limit);
        List<SessionFeedbackDetail> recentFeedbacks = new ArrayList<>();
        for (ChatSession sess : ratedSessions) {
            String clientName = sess.getUser() != null ? sess.getUser().getFullName() : "Cliente Anónimo";
            recentFeedbacks.add(new SessionFeedbackDetail(
                    sess.getId(),
                    sess.getTitle(),
                    clientName,
                    sess.getFeedbackUseful(),
                    sess.getFeedbackComment(),
                    sess.getClosedAt()
            ));
        }

        return new ChatMetricsDashboard(summary, recentDislikes, recentFeedbacks);
    }
}
