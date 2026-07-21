package com.chatbot.service;

import com.chatbot.controller.dto.TechnicianConversationCount;
import com.chatbot.controller.dto.TechnicianCountSummary;
import com.chatbot.controller.dto.TechnicianMetricsDashboard;
import com.chatbot.controller.dto.WeeklySupportChats;
import com.chatbot.model.NameRol;
import com.chatbot.repository.SupportSessionRepository;
import com.chatbot.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TechnicianMetricsService {

    private final UserRepository userRepository;
    private final SupportSessionRepository supportSessionRepository;

    public TechnicianMetricsService(UserRepository userRepository, SupportSessionRepository supportSessionRepository) {
        this.userRepository = userRepository;
        this.supportSessionRepository = supportSessionRepository;
    }

    @Transactional(readOnly = true)
    public TechnicianMetricsDashboard getTechnicianMetrics() {
        // 1. Contador de técnicos
        long total = userRepository.countByRoleName(NameRol.TECHNICIAN);
        long active = userRepository.countByRoleNameAndActive(NameRol.TECHNICIAN, true);
        long inactive = userRepository.countByRoleNameAndActive(NameRol.TECHNICIAN, false);
        TechnicianCountSummary counts = new TechnicianCountSummary(total, active, inactive);

        // 2. Cantidad de conversaciones por técnico activo
        List<Object[]> rawTechCounts = userRepository.findActiveTechniciansWithSessionCount(NameRol.TECHNICIAN);
        List<TechnicianConversationCount> conversationsPerTechnician = rawTechCounts.stream()
                .map(row -> new TechnicianConversationCount(
                        (UUID) row[0],
                        (String) row[1],
                        (String) row[2],
                        (Long) row[3]
                ))
                .collect(Collectors.toList());

        // 3. Cantidad de chats de soporte por semana (últimas 12 semanas)
        LocalDate today = LocalDate.now();
        // Lunes de la semana actual
        LocalDate currentWeekMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        // Lunes de hace 11 semanas (para completar 12 semanas en total)
        LocalDate startOfWeek = currentWeekMonday.minusWeeks(11);
        LocalDateTime since = startOfWeek.atStartOfDay();

        List<LocalDateTime> createdDates = supportSessionRepository.findCreatedDatesSince(since);

        Map<LocalDate, Long> countsByWeek = createdDates.stream()
                .map(dt -> dt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        List<WeeklySupportChats> weeklySupportChats = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            LocalDate week = startOfWeek.plusWeeks(i);
            long count = countsByWeek.getOrDefault(week, 0L);
            weeklySupportChats.add(new WeeklySupportChats(week.toString(), count));
        }

        return new TechnicianMetricsDashboard(counts, conversationsPerTechnician, weeklySupportChats);
    }
}
