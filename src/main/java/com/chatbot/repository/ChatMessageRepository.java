package com.chatbot.repository;

import com.chatbot.model.ChatMessage;
import com.chatbot.model.ChatSession;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, UUID> {
    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session);
    List<ChatMessage> findTop6BySessionOrderByCreatedAtDesc(ChatSession session);
    List<ChatMessage> findTop5BySessionOrderByCreatedAtDesc(ChatSession session);

    long countByRole(String role);
    long countByRoleAndLiked(String role, Boolean liked);
    long countByRoleAndLikedIsNotNull(String role);

    List<ChatMessage> findByRoleAndLikedOrderByCreatedAtDesc(String role, Boolean liked, Pageable pageable);

    @Query("SELECT m FROM ChatMessage m WHERE m.session.id = :sessionId AND m.role = 'USER' AND m.createdAt < :createdAt ORDER BY m.createdAt DESC")
    List<ChatMessage> findPrecedingUserMessages(
            @Param("sessionId") UUID sessionId,
            @Param("createdAt") LocalDateTime createdAt,
            Pageable pageable
    );
}
