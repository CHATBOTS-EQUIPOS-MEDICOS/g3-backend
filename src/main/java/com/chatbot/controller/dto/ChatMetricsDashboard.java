package com.chatbot.controller.dto;

import java.util.List;

public record ChatMetricsDashboard(
        ChatMetricsSummary summary,
        List<DislikedMessageDetail> recentDislikes,
        List<SessionFeedbackDetail> recentFeedbacks
) {}
