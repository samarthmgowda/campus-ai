package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.DocumentService;
import com.campusconnect.campus_ai.service.TeammateEvalService;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-gated three-way teammate evaluation (research track, Task #40).
 * Mirrors RagEvalController gating: the JWT principal must hold ROLE_ADMIN.
 */
@RestController
@RequestMapping("/eval/teammates")
public class TeammateEvalController {

    private final TeammateEvalService evalService;
    private final DocumentService documentService;

    public TeammateEvalController(TeammateEvalService evalService, DocumentService documentService) {
        this.evalService = evalService;
        this.documentService = documentService;
    }

    /**
     * POST /eval/teammates/run?availableOnly=true&includeSemantic=false — admin.
     * Leave-one-out over ACTIVE multi-member projects: overlap vs hybrid
     * vs hybrid+semantic (hit@3/hit@5, mean rank, review-rating signal).
     * includeSemantic=true needs Ollama up (bge-m3); run on Windows JDK 22.
     */
    @PostMapping("/run")
    public TeammateEvalService.TeammateEvalSummary run(
            @RequestParam(defaultValue = "true") boolean availableOnly,
            @RequestParam(defaultValue = "false") boolean includeSemantic) {
        documentService.requireAdmin();
        return evalService.run(availableOnly, includeSemantic);
    }

}
