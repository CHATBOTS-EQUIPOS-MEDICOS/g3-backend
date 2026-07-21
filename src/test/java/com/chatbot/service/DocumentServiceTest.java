package com.chatbot.service;

import com.chatbot.model.Document;
import com.chatbot.repository.DocumentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private SupabaseStorageService supabaseStorageService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private DocumentService documentService;

    @Test
    void enableDocument_Success() {
        // Arrange
        UUID docId = UUID.randomUUID();
        Document document = Document.builder()
                .id(docId)
                .name("manual.pdf")
                .enabled(false)
                .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Document result = documentService.enableDocument(docId);

        // Assert
        assertThat(result.getEnabled()).isTrue();
        verify(documentRepository).save(document);
    }

    @Test
    void disableDocument_Success() {
        // Arrange
        UUID docId = UUID.randomUUID();
        Document document = Document.builder()
                .id(docId)
                .name("manual.pdf")
                .enabled(true)
                .build();

        when(documentRepository.findById(docId)).thenReturn(Optional.of(document));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        Document result = documentService.disableDocument(docId);

        // Assert
        assertThat(result.getEnabled()).isFalse();
        verify(documentRepository).save(document);
    }

    @Test
    void enableDocument_NotFound_ShouldThrowException() {
        // Arrange
        UUID docId = UUID.randomUUID();
        when(documentRepository.findById(docId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> documentService.enableDocument(docId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Documento no encontrado con ID: " + docId);
    }

    @Test
    void uploadDocument_LimitExceeded_ShouldThrowException() {
        // Arrange
        when(documentRepository.countByStatus("PROCESSING")).thenReturn(3L);

        // Act & Assert
        assertThatThrownBy(() -> documentService.uploadDocument("new_manual.pdf", "application/pdf", new byte[10]))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Ya hay 3 documentos procesándose en este momento. Por favor, espere a que finalicen.");

        verify(supabaseStorageService, never()).uploadFile(anyString(), any(), anyString());
        verify(documentRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
