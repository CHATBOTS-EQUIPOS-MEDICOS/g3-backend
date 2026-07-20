package com.chatbot.repository;

import com.chatbot.model.ChatSession;
import com.chatbot.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, UUID> {
    List<ChatSession> findByUserOrderByUpdatedAtDesc(User user);
    Optional<ChatSession> findFirstByUserOrderByUpdatedAtDesc(User user);
    Optional<ChatSession> findByIdAndUser(UUID id, User user);
    List<ChatSession> findByIsClosedFalse();
}
