package com.chatbot.controller.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record SessionFeedbackDetail(
        UUID sessionId,
        String sessionTitle,
        String clientFullName,
        Boolean feedbackUseful,
        String feedbackComment,
        LocalDateTime closedAt
) {}
