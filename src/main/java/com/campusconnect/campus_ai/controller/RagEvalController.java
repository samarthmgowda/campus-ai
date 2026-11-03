package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.DocumentService;
import com.campusconnect.campus_ai.service.RagEvalService;
import com.campusconnect.campus_ai.service.DemoIdentity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/eval/rag")
public class RagEvalController {

    private final RagEvalService evalService;
    private final DocumentService documentService;

    public RagEvalController(RagEvalService evalService, DocumentService documentService) {
        this.evalService = evalService;
        this.documentService = documentService;
    }

    /** GET /eval/rag/cases — admin: view the curated Q/A set */
    @GetMapping("/cases")
    public List<Map<String, Object>> cases(
            @RequestHeader(value = "X-Demo-User", required = false) String header) {
        documentService.requireAdmin(requireHeader(header));
        return evalService.cases();
    }

    /**
     * POST /eval/rag/run?retrievalOnly=true&topK=5 — admin: Recall@K per arm
     * (+win/tie/loss); retrievalOnly=false adds full /ask generation per arm
     * (correctness, faithfulness, citations, latency, abstention rate).
     * Needs Ollama up; run on Windows JDK 22.
     *
     * {@code topK} is optional and defaults to the configured {@code rag.top-k}.
     * Pass it to sweep K without editing properties or restarting — e.g. run the
     * same eval at topK=3 and topK=5 and compare recall/correctness.
     */
    @PostMapping("/run")
    public RagEvalService.EvalSummary run(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestParam(defaultValue = "true") boolean retrievalOnly,
            @RequestParam(required = false) Integer topK) {
        documentService.requireAdmin(requireHeader(header));
        if (topK != null) {
            return evalService.run(retrievalOnly, topK);
        }
        return evalService.run(retrievalOnly);
    }

    private Long requireHeader(String header) {
        Long resolved = DemoIdentity.parseId(header);
        if (resolved != null) return resolved;
        throw new IllegalArgumentException("X-Demo-User header required");
    }
}
