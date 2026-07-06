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
     * Elimina todos los fragmentos anteriores asociados a un nombre de documento.
     */
    public void deleteByDocumentName(String documentName) {
        repository.deleteByDocumentName(documentName);
    }

    /**
     * Elimina todos los fragmentos asociados al ID de un documento.
     */
    public void deleteByDocumentId(UUID documentId) {
        repository.deleteByDocumentId(documentId);
    }

    /**
     * Guarda un fragmento y su embedding vectorial en el repositorio, incluyendo el ID del documento.
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
     * Guarda un fragmento y su embedding vectorial en el repositorio (compatibilidad heredada).
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
     * Realiza una búsqueda de similitud vectorial en todos los fragmentos almacenados en Supabase.
     */
    public List<DocumentChunk> findSimilar(List<Double> queryEmbedding, int limit) {
        return repository.findSimilar(queryEmbedding, limit);
    }
}
