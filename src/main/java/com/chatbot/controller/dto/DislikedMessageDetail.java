package com.chatbot.controller.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DislikedMessageDetail(
        UUID messageId,
        UUID sessionId,
        String userQuestion,
        String chatbotAnswer,
        LocalDateTime createdAt
) {}
