package com.chatbot.service;

import com.chatbot.model.DocumentChunk;
import com.chatbot.repository.DocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class VectorStoreService {

    private final DocumentChunkRepository repository;

    public VectorStoreService(DocumentChunkRepository repository) {
        this.repository = repository;
    }

    /**
     * Deletes all previous chunks associated with a document name.
     */
    public void deleteByDocumentName(String documentName) {
        repository.deleteByDocumentName(documentName);
    }

    /**
     * Deletes all chunks associated with a document ID.
     */
    public void deleteByDocumentId(UUID documentId) {
        repository.deleteByDocumentId(documentId);
    }

    /**
     * Saves a chunk and its vector embedding to the repository, including document ID.
     */
    public void saveChunk(String documentName, String content, int chunkIndex, List<Double> embedding, UUID documentId) {
        DocumentChunk chunk = DocumentChunk.builder()
                .documentName(documentName)
                .content(content)
                .chunkIndex(chunkIndex)
                .embedding(embedding)
                .build();
        repository.save(chunk, documentId);
    }

    /**
     * Saves a chunk and its vector embedding to the repository (legacy fallback).
     */
    public void saveChunk(String documentName, String content, int chunkIndex, List<Double> embedding) {
        DocumentChunk chunk = DocumentChunk.builder()
                .documentName(documentName)
                .content(content)
                .chunkIndex(chunkIndex)
                .embedding(embedding)
                .build();
        repository.save(chunk);
    }

    /**
     * Performs a vector similarity search across all stored chunks in Supabase.
     */
    public List<DocumentChunk> findSimilar(List<Double> queryEmbedding, int limit) {
        return repository.findSimilar(queryEmbedding, limit);
    }
}
