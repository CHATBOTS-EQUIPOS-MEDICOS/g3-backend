package com.chatbot.event;

import java.util.UUID;

public class DocumentDeletedEvent {
    private final UUID documentId;
    private final String storagePath;

    public DocumentDeletedEvent(UUID documentId, String storagePath) {
        this.documentId = documentId;
        this.storagePath = storagePath;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getStoragePath() {
        return storagePath;
    }
}
