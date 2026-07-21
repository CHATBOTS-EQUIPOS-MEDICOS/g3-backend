package com.chatbot.repository;

import com.chatbot.model.SupportSession;
import com.chatbot.model.SupportStatus;
import com.chatbot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SupportSessionRepository extends JpaRepository<SupportSession, UUID> {
    
    List<SupportSession> findByUserAndStatusIn(User user, List<SupportStatus> statuses);
    
    Optional<SupportSession> findFirstByUserAndStatusInOrderByCreatedAtDesc(User user, List<SupportStatus> statuses);

    Optional<SupportSession> findFirstByUserOrderByCreatedAtDesc(User user);
    
    List<SupportSession> findByStatusOrderByCreatedAtDesc(SupportStatus status);
    
    List<SupportSession> findBySupportAndStatusOrderByCreatedAtDesc(User support, SupportStatus status);
    
    List<SupportSession> findBySupportAndStatusInOrderByClosedAtDesc(User support, List<SupportStatus> statuses);
    
    Optional<SupportSession> findByIdAndUser(UUID id, User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM SupportSession s WHERE s.id = :id")
    Optional<SupportSession> findByIdForUpdate(@Param("id") UUID id);

    long countBySupportAndStatus(User support, SupportStatus status);

    List<SupportSession> findByStatusInOrderByClosedAtDesc(List<SupportStatus> statuses);

    @Query("SELECT s FROM SupportSession s JOIN FETCH s.user LEFT JOIN FETCH s.support WHERE s.user = :user AND s.status IN :statuses ORDER BY s.createdAt DESC")
    List<SupportSession> findActiveSessionsWithUserAndSupport(@Param("user") User user, @Param("statuses") List<SupportStatus> statuses);

    @Query("SELECT s FROM SupportSession s JOIN FETCH s.user LEFT JOIN FETCH s.support WHERE s.id = :id")
    Optional<SupportSession> findByIdWithUserAndSupport(@Param("id") UUID id);

    @Query("SELECT s.createdAt FROM SupportSession s WHERE s.createdAt >= :since")
    List<LocalDateTime> findCreatedDatesSince(@Param("since") LocalDateTime since);
}
