package com.chatbot.controller;

import com.chatbot.controller.dto.TechnicianMetricsDashboard;
import com.chatbot.service.TechnicianMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/technician/metrics")
@CrossOrigin(origins = "http://localhost:4200")
@PreAuthorize("hasRole('ADMIN')")
public class TechnicianMetricsController {

    private final TechnicianMetricsService technicianMetricsService;

    public TechnicianMetricsController(TechnicianMetricsService technicianMetricsService) {
        this.technicianMetricsService = technicianMetricsService;
    }

    @GetMapping
    public ResponseEntity<TechnicianMetricsDashboard> getTechnicianMetrics() {
        TechnicianMetricsDashboard dashboard = technicianMetricsService.getTechnicianMetrics();
        return ResponseEntity.ok(dashboard);
    }
}
