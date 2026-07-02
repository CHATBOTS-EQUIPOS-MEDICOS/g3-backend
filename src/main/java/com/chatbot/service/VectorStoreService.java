package com.chatbot.service;

import com.chatbot.model.DocumentChunk;
import com.chatbot.repository.DocumentChunkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
                .id(UUID.randomUUID().toString())
                .documentName(documentName)
                .content(content)
                .chunkIndex(chunkIndex)
                .embedding(embedding)
                .build();
        repository.save(chunk);
    }

    /**
     * Performs a vector similarity search across all stored chunks in SQLite,
     * calculating cosine similarity in-memory for accuracy and efficiency.
     */
    public List<DocumentChunk> findSimilar(List<Double> queryEmbedding, int limit) {
        List<DocumentChunk> allChunks = repository.findAll();

        if (allChunks.isEmpty()) {
            return List.of();
        }

        // Calculate cosine similarity for all chunks and sort desc
        return allChunks.stream()
                .map(chunk -> new ChunkWithScore(chunk, cosineSimilarity(queryEmbedding, chunk.getEmbedding())))
                .sorted((c1, c2) -> Double.compare(c2.score, c1.score))
                .limit(limit)
                .map(ChunkWithScore::chunk)
                .collect(Collectors.toList());
    }

    /**
     * Computes the cosine similarity between two vectors.
     */
    public static double cosineSimilarity(List<Double> vectorA, List<Double> vectorB) {
        if (vectorA.size() != vectorB.size()) {
            throw new IllegalArgumentException("Vector sizes do not match: A=" + vectorA.size() + ", B=" + vectorB.size());
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < vectorA.size(); i++) {
            double valA = vectorA.get(i);
            double valB = vectorB.get(i);
            dotProduct += valA * valB;
            normA += valA * valA;
            normB += valB * valB;
        }

        if (normA == 0.0 || normB == 0.0) {
            return 0.0; // Avoid division by zero
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private record ChunkWithScore(DocumentChunk chunk, double score) {}
}
