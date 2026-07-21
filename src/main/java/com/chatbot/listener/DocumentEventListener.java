package com.chatbot.listener;

import com.chatbot.event.DocumentDeletedEvent;
import com.chatbot.event.DocumentIngestedEvent;
import com.chatbot.model.Document;
import com.chatbot.repository.DocumentRepository;
import com.chatbot.service.DocumentProcessingService;
import com.chatbot.service.SupabaseStorageService;
import com.chatbot.service.VectorStoreService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class DocumentEventListener {

    private final DocumentRepository documentRepository;
    private final DocumentProcessingService documentProcessingService;
    private final VectorStoreService vectorStoreService;
    private final SupabaseStorageService supabaseStorageService;

    public DocumentEventListener(DocumentRepository documentRepository,
                                 DocumentProcessingService documentProcessingService,
                                 VectorStoreService vectorStoreService,
                                 SupabaseStorageService supabaseStorageService) {
        this.documentRepository = documentRepository;
        this.documentProcessingService = documentProcessingService;
        this.vectorStoreService = vectorStoreService;
        this.supabaseStorageService = supabaseStorageService;
    }

    @Async
    @TransactionalEventListener
    public void handleDocumentIngested(DocumentIngestedEvent event) {
        UUID docId = event.getDocumentId();
        log.info("Iniciando procesamiento asíncrono del documento ID: {}", docId);

        Optional<Document> docOpt = documentRepository.findById(docId);
        if (docOpt.isEmpty()) {
            log.error("Documento ID {} no encontrado para procesamiento.", docId);
            return;
        }

        Document document = docOpt.get();
        try {
            // 1. Descargar el archivo PDF desde Supabase Storage usando su storagePath
            byte[] pdfBytes = supabaseStorageService.downloadFile(document.getStoragePath());

            // 2. Utilizar el servicio existente para extraer texto, chunkear y generar embeddings con Gemini
            documentProcessingService.processPdf(docId, document.getName(), pdfBytes);

            // 3. Actualizar estado del documento a completado
            document.setStatus("COMPLETED");
            documentRepository.save(document);
            log.info("Procesamiento de documento ID: {} finalizado con éxito.", docId);

        } catch (Throwable t) {
            log.error("Error o excepción grave procesando asíncronamente el documento ID: {}", docId, t);
            document.setStatus("FAILED");
            documentRepository.save(document);
            if (t instanceof Error) {
                throw (Error) t;
            } else if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw new RuntimeException(t);
            }
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resetProcessingDocuments() {
        log.info("Buscando documentos en estado PROCESSING para resetearlos a FAILED debido a posible reinicio/apagado del servidor...");
        try {
            List<Document> processingDocs = documentRepository.findByStatus("PROCESSING");
            if (!processingDocs.isEmpty()) {
                for (Document doc : processingDocs) {
                    log.info("Actualizando documento ID {} ({}) de PROCESSING a FAILED", doc.getId(), doc.getName());
                    doc.setStatus("FAILED");
                }
                documentRepository.saveAll(processingDocs);
                log.info("Reseteados {} documentos a estado FAILED.", processingDocs.size());
            }
        } catch (Exception e) {
            log.error("Error al resetear documentos en estado PROCESSING en el arranque", e);
        }
    }

    @Async
    @TransactionalEventListener
    public void handleDocumentDeleted(DocumentDeletedEvent event) {
        UUID docId = event.getDocumentId();
        String storagePath = event.getStoragePath();
        log.info("Iniciando eliminación asíncrona de embeddings y archivo físico del documento ID: {}", docId);
        try {
            // 1. Eliminar los embeddings asociados de la tabla vectorial en Supabase
            vectorStoreService.deleteByDocumentId(docId);

            // 2. Eliminar el archivo físico de Supabase Storage
            if (storagePath != null && !storagePath.isBlank()) {
                supabaseStorageService.deleteFile(storagePath);
            }

            log.info("Eliminación de embeddings y archivo de storage para el documento ID: {} completada con éxito.", docId);
        } catch (Exception e) {
            log.error("Error eliminando recursos para el documento ID: {}", docId, e);
        }
    }
}
