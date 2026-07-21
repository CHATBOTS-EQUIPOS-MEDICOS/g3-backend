package com.chatbot.repository;

import com.chatbot.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentRepository extends JpaRepository<Document, UUID> {

    List<Document> findAllByOrderByCreatedAtDesc();

    List<Document> findByStatus(String status);

    long countByStatus(String status);
}
