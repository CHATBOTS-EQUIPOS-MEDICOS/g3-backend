package com.chatbot.controller.dto;

public record TechnicianCountSummary(
    long total,
    long active,
    long inactive
) {}
