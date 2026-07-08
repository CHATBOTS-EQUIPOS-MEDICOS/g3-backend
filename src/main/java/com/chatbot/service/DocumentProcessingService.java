package com.chatbot.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class DocumentProcessingService {

    private final GeminiService geminiService;
    private final VectorStoreService vectorStoreService;

    // Configuración de los fragmentos (chunks) objetivo
    private static final int CHUNK_SIZE = 1000; // caracteres
    private static final int OVERLAP = 200;     // caracteres

    public DocumentProcessingService(GeminiService geminiService, VectorStoreService vectorStoreService) {
        this.geminiService = geminiService;
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * Extrae texto de un documento PDF, lo divide en fragmentos (chunks) con superposición,
     * genera embeddings utilizando Gemini y los guarda en Supabase con el UUID del documento.
     */
    public int processPdf(java.util.UUID documentId, String documentName, byte[] pdfBytes) throws IOException {
        log.info("Processing PDF document: {}, UUID: {}, bytes size: {}", documentName, documentId, pdfBytes.length);

        // 1. Extraer texto del PDF utilizando Apache PDFBox 3.x
        String extractedText;
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            extractedText = stripper.getText(document);
        } catch (IOException e) {
            log.error("Failed to parse PDF using PDFBox", e);
            throw new IOException("Failed to parse PDF document: " + e.getMessage(), e);
        }

        if (extractedText == null || extractedText.strip().isEmpty()) {
            throw new IllegalArgumentException("The uploaded PDF file does not contain any indexable text.");
        }

        // 2. Fragmentar el texto utilizando una ventana deslizante inteligente
        List<String> chunks = chunkText(extractedText, CHUNK_SIZE, OVERLAP);
        log.info("Split document into {} chunks.", chunks.size());

        // 3. Limpiar los fragmentos existentes para el mismo documento para permitir re-cargas
        vectorStoreService.deleteByDocumentName(documentName);

        // 4. Generar embeddings y guardar en la base de datos
        int savedChunksCount = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i).trim();
            if (chunk.isEmpty()) {
                continue;
            }

            try {
                // Agregar cabeceras de contexto para ayudar a Gemini a identificar las fuentes del documento
                String chunkWithHeader = String.format("Document: %s | Page Info/Chunk: %d | Text:\n%s", 
                        documentName, i + 1, chunk);
                
                List<Double> embedding = geminiService.getEmbedding(chunkWithHeader);
                vectorStoreService.saveChunk(documentName, chunk, i, embedding, documentId);
                savedChunksCount++;
                
                // Imprimir el progreso cada 10 fragmentos para evitar spam en la salida
                if (savedChunksCount % 10 == 0) {
                    log.info("Generated embeddings for {}/{} chunks...", savedChunksCount, chunks.size());
                }
            } catch (Exception e) {
                log.error("Error creating embedding/saving chunk index {} for document {}", i, documentName, e);
                // Continuar procesando otros fragmentos pero registrar el error
            }
        }

        log.info("Successfully finished processing and indexing document: {}. Saved {} chunks.", documentName, savedChunksCount);
        return savedChunksCount;
    }

    /**
     * Extrae el texto de un documento PDF, lo divide en fragmentos con superposición,
     * genera embeddings utilizando Gemini y los guarda en SQLite.
     * Sobrescribe cualquier fragmento existente para un documento con el mismo nombre (compatibilidad heredada).
     */
    public int processPdf(String documentName, byte[] pdfBytes) throws IOException {
        return processPdf(java.util.UUID.randomUUID(), documentName, pdfBytes);
    }

    /**
     * Divide el texto en fragmentos de aproximadamente `chunkSize` caracteres con una superposición de `overlap` caracteres.
     * Intenta respetar los límites de las palabras buscando espacios o saltos de línea cerca de los cortes objetivos.
     */
    public List<String> chunkText(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }

        int length = text.length();
        int start = 0;

        while (start < length) {
            int end = start + chunkSize;
            
            if (end >= length) {
                // Último fragmento
                chunks.add(text.substring(start));
                break;
            }

            // Buscar hacia atrás desde el final para encontrar un espacio o salto de línea (hasta 100 caracteres hacia atrás)
            int boundaryIdx = end;
            int lookbackLimit = Math.max(start, end - 100);
            while (boundaryIdx > lookbackLimit) {
                char c = text.charAt(boundaryIdx - 1);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '.') {
                    break;
                }
                boundaryIdx--;
            }

            // Si no se encontró separador, recurrir a un corte abrupto
            if (boundaryIdx == lookbackLimit) {
                boundaryIdx = end;
            }

            chunks.add(text.substring(start, boundaryIdx));
            
            // Avanzar la posición de inicio teniendo en cuenta la superposición
            int nextStart = boundaryIdx - overlap;
            if (nextStart <= start) {
                nextStart = start + chunkSize - overlap;
            }
            start = nextStart;

            if (start >= length || chunkSize <= overlap) {
                break;
            }
        }

        return chunks;
    }
}
