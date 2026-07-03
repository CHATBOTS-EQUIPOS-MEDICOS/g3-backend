package com.chatbot.event;

import java.util.UUID;

public class DocumentIngestedEvent {
    private final UUID documentId;

    public DocumentIngestedEvent(UUID documentId) {
        this.documentId = documentId;
    }

    public UUID getDocumentId() {
        return documentId;
    }
}
