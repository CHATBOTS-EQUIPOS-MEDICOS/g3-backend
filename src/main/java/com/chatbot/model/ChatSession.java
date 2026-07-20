package com.chatbot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_session")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title")
    private String title;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // Indica si la sesión de chat con la IA ha sido cerrada por el usuario
    @Column(name = "is_closed", nullable = false)
    private Boolean isClosed = false;

    // Fecha y hora en la que se cerró la sesión
    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    @Column(name = "prompt_sent", nullable = false)
    private Boolean promptSent = false;

    @Column(name = "last_user_activity")
    private LocalDateTime lastUserActivity = LocalDateTime.now();

    public ChatSession() {
    }

    public ChatSession(UUID id, User user, String title, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.user = user;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isClosed = false;
    }

    public ChatSession(UUID id, User user, String title, LocalDateTime createdAt, LocalDateTime updatedAt, Boolean isClosed, LocalDateTime closedAt) {
        this.id = id;
        this.user = user;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.isClosed = isClosed;
        this.closedAt = closedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Boolean getIsClosed() {
        return isClosed;
    }

    public void setIsClosed(Boolean isClosed) {
        this.isClosed = isClosed;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public Boolean getPromptSent() {
        return promptSent;
    }

    public void setPromptSent(Boolean promptSent) {
        this.promptSent = promptSent;
    }

    public LocalDateTime getLastUserActivity() {
        return lastUserActivity;
    }

    public void setLastUserActivity(LocalDateTime lastUserActivity) {
        this.lastUserActivity = lastUserActivity;
    }
}
