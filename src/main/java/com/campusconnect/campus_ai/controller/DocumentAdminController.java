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
    public List<Document> all() {
        documentService.requireAdmin();
        return documentService.getAllIncludingHidden();
    }

    /** POST /documents/admin/{id}/visibility {hidden: true|false} — admin */
    @PostMapping("/{id}/visibility")
    public Document visibility(@PathVariable Long id,
                               @RequestBody Map<String, Object> body) {
        boolean hidden = body != null && Boolean.TRUE.equals(body.get("hidden"));
        if (body != null && body.get("hidden") instanceof String s) hidden = Boolean.parseBoolean(s);
        return documentService.hideDocument(id, hidden);
    }

    /** DELETE /documents/admin/{id} — admin deletes doc + chunks */
    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id) {
        return documentService.deleteDocument(id);
    }

}
