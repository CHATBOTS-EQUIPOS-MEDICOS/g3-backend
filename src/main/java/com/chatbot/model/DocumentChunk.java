package com.chatbot.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentChunk {
    private String id;
    private String documentName;
    private String content;
    private int chunkIndex;
    private List<Double> embedding;
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getDocumentName() {
		return documentName;
	}
	public void setDocumentName(String documentName) {
		this.documentName = documentName;
	}
	public String getContent() {
		return content;
	}
	public void setContent(String content) {
		this.content = content;
	}
	public int getChunkIndex() {
		return chunkIndex;
	}
	public void setChunkIndex(int chunkIndex) {
		this.chunkIndex = chunkIndex;
	}
	public List<Double> getEmbedding() {
		return embedding;
	}
	public void setEmbedding(List<Double> embedding) {
		this.embedding = embedding;
	}
}
