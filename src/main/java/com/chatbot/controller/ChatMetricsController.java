package com.chatbot.controller;

import com.chatbot.controller.dto.ChatMetricsDashboard;
import com.chatbot.service.ChatMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/chat/metrics")
@CrossOrigin(origins = "http://localhost:4200")
@PreAuthorize("hasRole('ADMIN')")
public class ChatMetricsController {

    private final ChatMetricsService chatMetricsService;

    public ChatMetricsController(ChatMetricsService chatMetricsService) {
        this.chatMetricsService = chatMetricsService;
    }

    @GetMapping
    public ResponseEntity<ChatMetricsDashboard> getChatMetrics(
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        ChatMetricsDashboard metrics = chatMetricsService.getChatMetrics(limit);
        return ResponseEntity.ok(metrics);
    }
}
