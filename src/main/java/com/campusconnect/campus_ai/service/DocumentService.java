package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Document;
import com.campusconnect.campus_ai.repository.DocumentChunkRepository;
import com.campusconnect.campus_ai.repository.DocumentRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DocumentService {

    private final DocumentRepository repository;
    private final DocumentChunkRepository chunkRepository;
    private final StudentRepository studentRepository;

    public DocumentService(DocumentRepository repository, DocumentChunkRepository chunkRepository, StudentRepository studentRepository) {
        this.repository = repository;
        this.chunkRepository = chunkRepository;
        this.studentRepository = studentRepository;
    }

    public List<Document> getAllDocuments() {
        return repository.findByHiddenFalse();
    }

    public List<Document> getAllIncludingHidden() {
        return repository.findAll();
    }

    public List<Document> getVisibleDocuments() { return repository.findByHiddenFalse(); }
    public List<Document> getHiddenDocuments() { return repository.findByHiddenTrue(); }

    public Document getById(Long id) {
        Document d = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + id));
        if (d.isHidden()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + id);
        return d;
    }

    public Document getByIdIncludingHidden(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + id));
    }

    public Document saveDocument(Document document) {
        return repository.save(document);
    }

    public Document hideDocument(Long id, boolean hidden) {
        requireAdmin();
        Document d = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + id));
        d.setHidden(hidden);
        return repository.save(d);
    }

    public Map<String, Object> deleteDocument(Long id) {
        requireAdmin();
        Document d = repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Document not found: " + id));
        // delete chunks first
        try {
            var chunks = chunkRepository.findByDocument_Id(d.getId());
            if (!chunks.isEmpty()) chunkRepository.deleteAll(chunks);
        } catch (Exception ignored) {}
        repository.delete(d);
        return Map.of("id", id, "status", "deleted", "title", d.getTitle() != null ? d.getTitle() : "");
    }

    public List<Document> getMyUploads(Long uploaderId) {
        studentRepository.findById(uploaderId).orElseThrow(() -> new IllegalArgumentException("Unknown demo user"));
        return repository.findByUploaderId(uploaderId);
    }

    public Map<String, Object> stats() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("visible", repository.countByHiddenFalse());
        m.put("hidden", repository.countByHiddenTrue());
        m.put("total", repository.count());
        m.put("totalChunks", chunkRepository.count());
        return m;
    }

    public record DocDto(Long id, String title, boolean hidden, Long uploaderId, String createdAt, int contentLength) {}
    public DocDto toDto(Document d) {
        return new DocDto(d.getId(), d.getTitle(), d.isHidden(), d.getUploaderId(), d.getCreatedAt() != null ? d.getCreatedAt().toString() : null, d.getContent() != null ? d.getContent().length() : 0);
    }

    /** Gate admin-only document curation on the JWT principal's ADMIN role. */
    public void requireAdmin() {
        if (!AuthContext.isAdmin()) {
            throw new org.springframework.security.access.AccessDeniedException("Admin role required");
        }
    }
}
