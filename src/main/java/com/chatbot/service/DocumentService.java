package com.chatbot.service;

import com.chatbot.event.DocumentDeletedEvent;
import com.chatbot.event.DocumentIngestedEvent;
import com.chatbot.model.Document;
import com.chatbot.repository.DocumentRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final SupabaseStorageService supabaseStorageService;
    private final ApplicationEventPublisher eventPublisher;

    public DocumentService(DocumentRepository documentRepository,
                           SupabaseStorageService supabaseStorageService,
                           ApplicationEventPublisher eventPublisher) {
        this.documentRepository = documentRepository;
        this.supabaseStorageService = supabaseStorageService;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Document uploadDocument(String filename, String contentType, byte[] fileData) {
        // Generar un nombre único de archivo y sanitizarlo para evitar problemas con S3 / Supabase Storage
        String uniqueId = UUID.randomUUID().toString();
        String sanitizedFilename = sanitizeFilename(filename);
        String storagePath = uniqueId + "_" + sanitizedFilename;

        // 1. Subir el archivo binario a Supabase Storage (Bucket 'documents')
        supabaseStorageService.uploadFile(storagePath, fileData, contentType);

        // 2. Guardar los metadatos en la base de datos PostgreSQL
        Document document = Document.builder()
                .name(filename) // Conservamos el nombre original legible para la UI
                .contentType(contentType)
                .sizeBytes((long) fileData.length)
                .status("PROCESSING")
                .storagePath(storagePath)
                .build();

        Document savedDocument = documentRepository.save(document);

        // 3. Publicar el evento para el procesamiento asíncrono y generación de embeddings
        eventPublisher.publishEvent(new DocumentIngestedEvent(savedDocument.getId()));

        return savedDocument;
    }

    public List<Document> getAllDocuments() {
        return documentRepository.findAllByOrderByCreatedAtDesc();
    }

    public Document getDocumentById(UUID id) {
        return documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado con ID: " + id));
    }

    @Transactional
    public void deleteDocument(UUID id) {
        Document document = documentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Documento no encontrado con ID: " + id));

        // Publicar el evento para limpiar los embeddings y el archivo en Supabase Storage de forma asíncrona
        eventPublisher.publishEvent(new DocumentDeletedEvent(id, document.getStoragePath()));

        // Eliminar el registro de metadatos del documento de la base de datos
        documentRepository.deleteById(id);
    }

    @Transactional
    public Document enableDocument(UUID id) {
        Document document = getDocumentById(id);
        document.setEnabled(true);
        return documentRepository.save(document);
    }

    @Transactional
    public Document disableDocument(UUID id) {
        Document document = getDocumentById(id);
        document.setEnabled(false);
        return documentRepository.save(document);
    }

    /**
     * Remueve tildes, diacríticos y caracteres especiales para cumplir con las reglas 
     * de nombrado de claves S3 de Supabase Storage.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "document.pdf";
        }
        // Descomponer acentos (NFD)
        String normalized = java.text.Normalizer.normalize(filename, java.text.Normalizer.Form.NFD);
        // Remover acentos combinados
        String clean = normalized.replaceAll("\\p{M}", "");
        // Reemplazar todo lo que no sea alfanumérico, puntos, guiones o guiones bajos
        clean = clean.replaceAll("[^a-zA-Z0-9._-]", "_");
        return clean;
    }
}
