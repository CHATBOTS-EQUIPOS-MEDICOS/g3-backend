package com.chatbot.controller.dto;

import java.util.UUID;

public record TechnicianConversationCount(
    UUID technicianId,
    String fullName,
    String email,
    long conversationCount
) {}
