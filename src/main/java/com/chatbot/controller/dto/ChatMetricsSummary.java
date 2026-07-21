package com.chatbot.controller.dto;

public record ChatMetricsSummary(
        long totalSessions,
        long totalMessages,
        long totalModelMessages,
        long totalRatedMessages,
        long totalLikes,
        long totalDislikes,
        double satisfactionRate,
        long totalSessionsWithFeedback,
        long sessionsHelped,
        long sessionsNotHelped,
        double sessionSatisfactionRate
) {}
