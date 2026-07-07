package com.chatbot.repository;

import com.chatbot.model.SupportSession;
import com.chatbot.model.SupportStatus;
import com.chatbot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportSessionRepository extends JpaRepository<SupportSession, UUID> {
    
    List<SupportSession> findByUserAndStatusIn(User user, List<SupportStatus> statuses);
    
    Optional<SupportSession> findFirstByUserAndStatusInOrderByCreatedAtDesc(User user, List<SupportStatus> statuses);
    
    List<SupportSession> findByStatusOrderByCreatedAtDesc(SupportStatus status);
    
    List<SupportSession> findBySupportAndStatusOrderByCreatedAtDesc(User support, SupportStatus status);
    
    List<SupportSession> findBySupportAndStatusInOrderByClosedAtDesc(User support, List<SupportStatus> statuses);
    
    Optional<SupportSession> findByIdAndUser(UUID id, User user);
}
