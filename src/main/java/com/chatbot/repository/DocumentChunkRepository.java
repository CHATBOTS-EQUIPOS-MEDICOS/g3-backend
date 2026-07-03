package com.chatbot.repository;

import com.chatbot.model.DocumentChunk;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Repository
@Slf4j
public class DocumentChunkRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public DocumentChunkRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Deletes all chunk records associated with the specified document ID in metadata.
     */
    public void deleteByDocumentId(java.util.UUID documentId) {
        String sql = "DELETE FROM document_chunks WHERE metadata->>'document_id' = ?";
        int rows = jdbcTemplate.update(sql, documentId.toString());
        log.debug("Deleted {} chunk rows for document ID: {}", rows, documentId);
    }

    /**
     * Deletes all chunk records associated with the specified document name in metadata.
     */
    public void deleteByDocumentName(String documentName) {
        String sql = "DELETE FROM document_chunks WHERE metadata->>'document_name' = ?";
        int rows = jdbcTemplate.update(sql, documentName);
        log.debug("Deleted {} chunk rows for document: {}", rows, documentName);
    }

    /**
     * Inserts a DocumentChunk record into Supabase PostgreSQL, including document ID in metadata.
     */
    public void save(DocumentChunk chunk, java.util.UUID documentId) {
        try {
            Map<String, Object> metadata = Map.of(
                    "document_name", chunk.getDocumentName(),
                    "chunk_index", chunk.getChunkIndex(),
                    "document_id", documentId.toString()
            );
            String metadataJson = objectMapper.writeValueAsString(metadata);
            String embeddingJson = objectMapper.writeValueAsString(chunk.getEmbedding());

            String sql = """
                    INSERT INTO document_chunks (content, metadata, embedding)
                    VALUES (?, CAST(? AS jsonb), CAST(? AS vector))
                    """;
            jdbcTemplate.update(sql, 
                    chunk.getContent(), 
                    metadataJson, 
                    embeddingJson
            );
        } catch (Exception e) {
            log.error("Failed to save DocumentChunk to Supabase database", e);
            throw new RuntimeException("Database error saving document chunk", e);
        }
    }

    /**
     * Inserts a DocumentChunk record into Supabase PostgreSQL.
     */
    public void save(DocumentChunk chunk) {
        try {
            Map<String, Object> metadata = Map.of(
                    "document_name", chunk.getDocumentName(),
                    "chunk_index", chunk.getChunkIndex()
            );
            String metadataJson = objectMapper.writeValueAsString(metadata);
            String embeddingJson = objectMapper.writeValueAsString(chunk.getEmbedding());

            String sql = """
                    INSERT INTO document_chunks (content, metadata, embedding)
                    VALUES (?, CAST(? AS jsonb), CAST(? AS vector))
                    """;
            jdbcTemplate.update(sql, 
                    chunk.getContent(), 
                    metadataJson, 
                    embeddingJson
            );
        } catch (Exception e) {
            log.error("Failed to save DocumentChunk to Supabase database", e);
            throw new RuntimeException("Database error saving document chunk", e);
        }
    }

    /**
     * Performs a vector similarity search across all stored chunks in Supabase using pgvector.
     */
    public List<DocumentChunk> findSimilar(List<Double> queryEmbedding, int limit) {
        try {
            String embeddingJson = objectMapper.writeValueAsString(queryEmbedding);
            String sql = """
                    SELECT id, content, metadata, embedding
                    FROM document_chunks
                    ORDER BY embedding <=> CAST(? AS vector)
                    LIMIT ?
                    """;
            return jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToChunk(rs), embeddingJson, limit);
        } catch (Exception e) {
            log.error("Failed to perform vector similarity search in Supabase", e);
            throw new RuntimeException("Database error during similarity search", e);
        }
    }

    private DocumentChunk mapRowToChunk(ResultSet rs) throws SQLException {
        try {
            String id = rs.getString("id");
            String content = rs.getString("content");
            String metadataJson = rs.getString("metadata");
            String embeddingJson = rs.getString("embedding");
            
            Map<String, Object> metadata = objectMapper.readValue(metadataJson, new TypeReference<Map<String, Object>>() {});
            String docName = (String) metadata.get("document_name");
            int index = ((Number) metadata.get("chunk_index")).intValue();
            
            List<Double> embedding = objectMapper.readValue(embeddingJson, new TypeReference<List<Double>>() {});
            
            return DocumentChunk.builder()
                    .id(id)
                    .documentName(docName)
                    .content(content)
                    .chunkIndex(index)
                    .embedding(embedding)
                    .build();
        } catch (Exception e) {
            throw new SQLException("Error mapping database row to DocumentChunk", e);
        }
    }
}
