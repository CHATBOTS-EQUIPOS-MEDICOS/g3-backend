package com.chatbot.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "chat_message")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;

    @Column(name = "role", nullable = false)
    private String role; // 'USER' o 'MODEL'

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "image_base64", columnDefinition = "TEXT")
    private String imageBase64;

    @Column(name = "image_mime_type", length = 100)
    private String imageMimeType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "sources")
    private List<ChatSource> sources;

    @Column(name = "liked")
    private Boolean liked; // Indica si el mensaje tiene like (true), dislike (false) o no ha sido calificado (null)

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public ChatMessage() {
    }

    public ChatMessage(UUID id, ChatSession session, String role, String content, String imageBase64, String imageMimeType, List<ChatSource> sources, LocalDateTime createdAt) {
        this(id, session, role, content, imageBase64, imageMimeType, sources, createdAt, null);
    }

    public ChatMessage(UUID id, ChatSession session, String role, String content, String imageBase64, String imageMimeType, List<ChatSource> sources, LocalDateTime createdAt, Boolean liked) {
        this.id = id;
        this.session = session;
        this.role = role;
        this.content = content;
        this.imageBase64 = imageBase64;
        this.imageMimeType = imageMimeType;
        this.sources = sources;
        this.createdAt = createdAt;
        this.liked = liked;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public ChatSession getSession() {
        return session;
    }

    public void setSession(ChatSession session) {
        this.session = session;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getImageMimeType() {
        return imageMimeType;
    }

    public void setImageMimeType(String imageMimeType) {
        this.imageMimeType = imageMimeType;
    }

    public List<ChatSource> getSources() {
        return sources;
    }

    public void setSources(List<ChatSource> sources) {
        this.sources = sources;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getLiked() {
        return liked;
    }

    public void setLiked(Boolean liked) {
        this.liked = liked;
    }
}
