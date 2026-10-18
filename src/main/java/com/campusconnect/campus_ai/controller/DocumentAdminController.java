package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.Document;
import com.campusconnect.campus_ai.service.DocumentService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/documents/admin")
public class DocumentAdminController {

    private final DocumentService documentService;

    public DocumentAdminController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /** GET /documents/admin/all — admin view including hidden */
    @GetMapping("/all")
    public List<Document> all(@RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long actorId = requireHeader(header);
        documentService.requireAdmin(actorId);
        return documentService.getAllIncludingHidden();
    }

    /** POST /documents/admin/{id}/visibility {hidden: true|false} — admin */
    @PostMapping("/{id}/visibility")
    public Document visibility(@PathVariable Long id,
                               @RequestHeader(value = "X-Demo-User", required = false) String header,
                               @RequestBody Map<String, Object> body) {
        Long actorId = requireHeader(header);
        boolean hidden = body != null && Boolean.TRUE.equals(body.get("hidden"));
        if (body != null && body.get("hidden") instanceof String s) hidden = Boolean.parseBoolean(s);
        return documentService.hideDocument(id, actorId, hidden);
    }

    /** DELETE /documents/admin/{id} — admin deletes doc + chunks */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id,
                                      @RequestHeader(value = "X-Demo-User", required = false) String header) {
        Long actorId = requireHeader(header);
        return documentService.deleteDocument(id, actorId);
    }

    private Long requireHeader(String header) {
        if (header != null && !header.isBlank()) try { return Long.valueOf(header.trim()); } catch (NumberFormatException ignored) {}
        throw new IllegalArgumentException("X-Demo-User header required");
    }
}
