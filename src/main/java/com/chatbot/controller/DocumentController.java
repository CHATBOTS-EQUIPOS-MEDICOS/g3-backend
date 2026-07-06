package com.chatbot.controller;

import com.chatbot.model.Document;
import com.chatbot.service.DocumentService;
import com.chatbot.service.SupabaseStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")

@CrossOrigin(origins = "http://localhost:4200")
public class DocumentController {

    private final DocumentService documentService;
    private final SupabaseStorageService supabaseStorageService;

    public DocumentController(DocumentService documentService, SupabaseStorageService supabaseStorageService) {
        this.documentService = documentService;
        this.supabaseStorageService = supabaseStorageService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Document> uploadDocument(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        // Validar que el archivo sea un PDF
        String contentType = file.getContentType();
        if (contentType == null || !contentType.equalsIgnoreCase("application/pdf")) {
            return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE).build();
        }

        Document document = documentService.uploadDocument(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getBytes()
        );

        // Retornamos HTTP 202 Accepted indicando que la ingesta asíncrona ha iniciado
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(document);
    }

    @GetMapping
    public ResponseEntity<List<Document>> getAllDocuments() {
        List<Document> documents = documentService.getAllDocuments();
        return ResponseEntity.ok(documents);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> downloadDocument(
            @PathVariable UUID id,
            @RequestParam(value = "inline", defaultValue = "false") boolean inline) {
        Document document = documentService.getDocumentById(id);
        
        // Descargar los bytes reales desde el bucket de Supabase Storage
        byte[] fileData = supabaseStorageService.downloadFile(document.getStoragePath());
        
        String disposition = inline ? "inline" : "attachment";
        
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition + "; filename=\"" + document.getName() + "\"")
                .body(fileData);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteDocument(@PathVariable UUID id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }
}
