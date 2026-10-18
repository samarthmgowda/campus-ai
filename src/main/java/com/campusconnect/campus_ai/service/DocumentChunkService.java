package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Document;
import com.campusconnect.campus_ai.entity.DocumentChunk;
import com.campusconnect.campus_ai.repository.DocumentChunkRepository;
import com.campusconnect.campus_ai.repository.DocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentChunkService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository chunkRepository;
    private final EmbeddingService embeddingService;

    public DocumentChunkService(DocumentRepository documentRepository,
                                DocumentChunkRepository chunkRepository,
                                EmbeddingService embeddingService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.embeddingService = embeddingService;
    }

    public DocumentChunk addChunk(Long documentId, String content, Integer pageNumber, String section) {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("Chunk content must not be null or blank");
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Document not found: " + documentId));

        float[] embedding = embeddingService.embed(content);

        DocumentChunk chunk = new DocumentChunk(content, pageNumber, section);
        chunk.setDocument(document);
        chunk.setEmbedding(embedding);  // need setter - see below

        return chunkRepository.save(chunk);
    }
}