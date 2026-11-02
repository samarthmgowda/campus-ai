package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Document;
import com.campusconnect.campus_ai.service.DocumentChunkService;
import com.campusconnect.campus_ai.service.DocumentService;
import com.campusconnect.campus_ai.service.DemoIdentity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/documents")
public class DocumentController {

    private final DocumentService service;
    private final DocumentChunkService chunkService;

    public DocumentController(DocumentService service, DocumentChunkService chunkService) {
        this.service = service;
        this.chunkService = chunkService;
    }

    @GetMapping
    public List<Document> getAllDocuments() {
        return service.getVisibleDocuments();
    }

    @GetMapping("/stats/overview")
    public Map<String, Object> stats() {
        return service.stats();
    }

    @GetMapping("/my/uploads")
    public List<Document> myUploads(@RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long userId = requireHeader(header);
        return service.getMyUploads(userId);
    }

    @GetMapping("/{id}")
    public Document getDocument(@PathVariable Long id) {
        return service.getById(id);
    }

    @PostMapping
    public Document createDocument(@RequestBody Document document) {
        Document saved = service.saveDocument(document);
        // Auto-create a searchable chunk with embedding so POST /documents documents
        // are findable via RAG (same as /documents/upload and /dev/seed).
        // Only chunk if content exists and no chunks have been explicitly managed.
        if (saved.getContent() != null && !saved.getContent().isBlank()) {
            chunkService.addChunk(saved.getId(), saved.getContent(), 1, "Content");
        }
        return saved;
    }

    private Long requireHeader(String header) {
        Long resolved = DemoIdentity.parseId(header);
        if (resolved != null) return resolved;
        throw new IllegalArgumentException("X-Demo-User header required");
    }
}
