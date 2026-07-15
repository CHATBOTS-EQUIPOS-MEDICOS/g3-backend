package com.chatbot.service;

import java.util.UUID;

public record AcceptRequest(UUID sessionId, UUID technicianId) {
}
