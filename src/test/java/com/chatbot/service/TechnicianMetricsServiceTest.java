package com.chatbot.service;

import com.chatbot.controller.dto.TechnicianConversationCount;
import com.chatbot.controller.dto.TechnicianMetricsDashboard;
import com.chatbot.model.NameRol;
import com.chatbot.repository.SupportSessionRepository;
import com.chatbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TechnicianMetricsServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private SupportSessionRepository supportSessionRepository;

    private TechnicianMetricsService technicianMetricsService;

    @BeforeEach
    void setUp() {
        technicianMetricsService = new TechnicianMetricsService(userRepository, supportSessionRepository);
    }

    @Test
    void getTechnicianMetrics_ShouldCalculateSummaryCorrectly() {
        // Arrange
        when(userRepository.countByRoleName(NameRol.TECHNICIAN)).thenReturn(5L);
        when(userRepository.countByRoleNameAndActive(NameRol.TECHNICIAN, true)).thenReturn(3L);
        when(userRepository.countByRoleNameAndActive(NameRol.TECHNICIAN, false)).thenReturn(2L);

        UUID tech1 = UUID.randomUUID();
        UUID tech2 = UUID.randomUUID();
        List<Object[]> rawTechCounts = List.of(
                new Object[]{tech1, "Tech One", "tech1@example.com", 15L},
                new Object[]{tech2, "Tech Two", "tech2@example.com", 24L}
        );
        when(userRepository.findActiveTechniciansWithSessionCount(NameRol.TECHNICIAN)).thenReturn(rawTechCounts);

        LocalDateTime now = LocalDateTime.now();
        List<LocalDateTime> createdDates = List.of(
                now,
                now.minusWeeks(1),
                now.minusWeeks(2),
                now.minusWeeks(2)
        );
        when(supportSessionRepository.findCreatedDatesSince(any(LocalDateTime.class))).thenReturn(createdDates);

        // Act
        TechnicianMetricsDashboard dashboard = technicianMetricsService.getTechnicianMetrics();

        // Assert
        assertThat(dashboard.technicianCounts().total()).isEqualTo(5L);
        assertThat(dashboard.technicianCounts().active()).isEqualTo(3L);
        assertThat(dashboard.technicianCounts().inactive()).isEqualTo(2L);

        assertThat(dashboard.conversationsPerTechnician()).hasSize(2);
        TechnicianConversationCount count1 = dashboard.conversationsPerTechnician().get(0);
        assertThat(count1.technicianId()).isEqualTo(tech1);
        assertThat(count1.fullName()).isEqualTo("Tech One");
        assertThat(count1.email()).isEqualTo("tech1@example.com");
        assertThat(count1.conversationCount()).isEqualTo(15L);

        assertThat(dashboard.weeklySupportChats()).hasSize(12);

        // Current week must have 1 chat
        LocalDate currentWeekMonday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        var currentWeekOpt = dashboard.weeklySupportChats().stream()
                .filter(w -> w.weekStartDate().equals(currentWeekMonday.toString()))
                .findFirst();
        assertThat(currentWeekOpt).isPresent();
        assertThat(currentWeekOpt.get().chatCount()).isEqualTo(1L);
    }
}
