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

    // Target chunk settings
    private static final int CHUNK_SIZE = 1000; // characters
    private static final int OVERLAP = 200;     // characters

    public DocumentProcessingService(GeminiService geminiService, VectorStoreService vectorStoreService) {
        this.geminiService = geminiService;
        this.vectorStoreService = vectorStoreService;
    }

    /**
     * Extracts text from a PDF document, splits it into overlapping chunks,
     * generates embeddings using Gemini, and saves them to Supabase with the document UUID.
     */
    public int processPdf(java.util.UUID documentId, String documentName, byte[] pdfBytes) throws IOException {
        log.info("Processing PDF document: {}, UUID: {}, bytes size: {}", documentName, documentId, pdfBytes.length);

        // 1. Extract text from PDF using Apache PDFBox 3.x
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

        // 2. Chunk text using an intelligent sliding window
        List<String> chunks = chunkText(extractedText, CHUNK_SIZE, OVERLAP);
        log.info("Split document into {} chunks.", chunks.size());

        // 3. Clear existing chunks for the same document to allow re-uploads
        vectorStoreService.deleteByDocumentName(documentName);

        // 4. Generate embeddings and save to database
        int savedChunksCount = 0;
        for (int i = 0; i < chunks.size(); i++) {
            String chunk = chunks.get(i).trim();
            if (chunk.isEmpty()) {
                continue;
            }

            try {
                // Add context headers to help Gemini identify document sources
                String chunkWithHeader = String.format("Document: %s | Page Info/Chunk: %d | Text:\n%s", 
                        documentName, i + 1, chunk);
                
                List<Double> embedding = geminiService.getEmbedding(chunkWithHeader);
                vectorStoreService.saveChunk(documentName, chunk, i, embedding, documentId);
                savedChunksCount++;
                
                // Print progress every 10 chunks to avoid output spam
                if (savedChunksCount % 10 == 0) {
                    log.info("Generated embeddings for {}/{} chunks...", savedChunksCount, chunks.size());
                }
            } catch (Exception e) {
                log.error("Error creating embedding/saving chunk index {} for document {}", i, documentName, e);
                // Continue processing other chunks but log error
            }
        }

        log.info("Successfully finished processing and indexing document: {}. Saved {} chunks.", documentName, savedChunksCount);
        return savedChunksCount;
    }

    /**
     * Extracts text from a PDF document, splits it into overlapping chunks,
     * generates embeddings using Gemini, and saves them to SQLite.
     * Overwrites any existing chunks for a document with the same name (legacy fallback).
     */
    public int processPdf(String documentName, byte[] pdfBytes) throws IOException {
        return processPdf(java.util.UUID.randomUUID(), documentName, pdfBytes);
    }

    /**
     * Splits the text into chunks of roughly `chunkSize` characters with `overlap` characters.
     * Tries to respect word boundaries by searching for spaces or newlines near the target splits.
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
                // Last chunk
                chunks.add(text.substring(start));
                break;
            }

            // Look backward from end to find a space or newline (up to 100 characters back)
            int boundaryIdx = end;
            int lookbackLimit = Math.max(start, end - 100);
            while (boundaryIdx > lookbackLimit) {
                char c = text.charAt(boundaryIdx - 1);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '.') {
                    break;
                }
                boundaryIdx--;
            }

            // If no separator was found, fallback to hard cutoff
            if (boundaryIdx == lookbackLimit) {
                boundaryIdx = end;
            }

            chunks.add(text.substring(start, boundaryIdx));
            
            // Advance start position accounting for overlap
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
