package com.chatbot.service;

import com.chatbot.model.DocumentChunk;
import com.chatbot.repository.DocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

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
     * Saves a chunk and its vector embedding to the repository.
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
