package com.chatbot.model;

public class ChatSource {
    private String documentName;
    private Integer chunkIndex;
    private String snippet;

    public ChatSource() {
    }

    public ChatSource(String documentName, Integer chunkIndex, String snippet) {
        this.documentName = documentName;
        this.chunkIndex = chunkIndex;
        this.snippet = snippet;
    }

    public String getDocumentName() {
        return documentName;
    }

    public void setDocumentName(String documentName) {
        this.documentName = documentName;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public String getSnippet() {
        return snippet;
    }

    public void setSnippet(String snippet) {
        this.snippet = snippet;
    }
}
