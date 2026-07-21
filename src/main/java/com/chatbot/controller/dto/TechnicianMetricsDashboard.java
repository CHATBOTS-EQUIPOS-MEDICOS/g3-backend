package com.chatbot.controller.dto;

import java.util.List;

public record TechnicianMetricsDashboard(
    TechnicianCountSummary technicianCounts,
    List<TechnicianConversationCount> conversationsPerTechnician,
    List<WeeklySupportChats> weeklySupportChats
) {}
