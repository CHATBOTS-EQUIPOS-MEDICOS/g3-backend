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
     * Deletes all chunk records associated with the specified document name.
     */
    public void deleteByDocumentName(String documentName) {
        String sql = "DELETE FROM document_chunks WHERE document_name = ?";
        int rows = jdbcTemplate.update(sql, documentName);
        log.debug("Deleted {} chunk rows for document: {}", rows, documentName);
    }

    /**
     * Inserts a DocumentChunk record into SQLite.
     */
    public void save(DocumentChunk chunk) {
        try {
            String embeddingJson = objectMapper.writeValueAsString(chunk.getEmbedding());
            String sql = """
                    INSERT INTO document_chunks (id, document_name, content, chunk_index, embedding)
                    VALUES (?, ?, ?, ?, ?)
                    """;
            jdbcTemplate.update(sql, 
                    chunk.getId(), 
                    chunk.getDocumentName(), 
                    chunk.getContent(), 
                    chunk.getChunkIndex(), 
                    embeddingJson
            );
        } catch (Exception e) {
            log.error("Failed to save DocumentChunk to database", e);
            throw new RuntimeException("Database error saving document chunk", e);
        }
    }

    /**
     * Retrieves all DocumentChunks from the SQLite database.
     */
    public List<DocumentChunk> findAll() {
        String sql = "SELECT id, document_name, content, chunk_index, embedding FROM document_chunks";
        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToChunk(rs));
    }

    private DocumentChunk mapRowToChunk(ResultSet rs) throws SQLException {
        try {
            String id = rs.getString("id");
            String docName = rs.getString("document_name");
            String content = rs.getString("content");
            int index = rs.getInt("chunk_index");
            String embeddingJson = rs.getString("embedding");
            
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
