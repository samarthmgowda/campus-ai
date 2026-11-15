package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.PlagiarismDetectionService;
import com.campusconnect.campus_ai.service.PlagiarismDetectionService.ScanResult;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Originality check against the campus document corpus.
 *
 * Mounted under {@code /evaluation/**} and gated to ADMIN in SecurityConfig, on
 * the same reasoning as the Library itself: the scan reads every chunk of the
 * knowledge base and reports which documents a submission overlaps with, so a
 * student being able to call it would turn it into a way to probe what the
 * corpus contains. The people who need this are the ones who curate the corpus.
 *
 * The scan is a synchronous in-memory comparison, not an LLM call — no Ollama,
 * no embeddings, no network. That is deliberate: it makes the feature usable
 * with the model server down, and it keeps a check that may run on every
 * submission cheap.
 */
@RestController
@RequestMapping("/evaluation/plagiarism")
public class PlagiarismController {

    /**
     * Generous for a single assignment, small enough that the linear corpus
     * scan stays bounded. Without a cap one request could submit a whole book
     * and hold a request thread while every chunk is tokenised.
     */
    private static final int MAX_CHARS = 50_000;

    private final PlagiarismDetectionService plagiarismDetectionService;

    public PlagiarismController(PlagiarismDetectionService plagiarismDetectionService) {
        this.plagiarismDetectionService = plagiarismDetectionService;
    }

    /** Body is a record rather than a bare String so the JSON shape can grow. */
    public record ScanRequest(String text) {}

    @PostMapping("/scan")
    public ResponseEntity<?> scan(@RequestBody(required = false) ScanRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Paste the text you want to check."));
        }
        if (request.text().length() > MAX_CHARS) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "That is " + request.text().length()
                            + " characters. The limit is " + MAX_CHARS + " — check a section at a time."));
        }

        ScanResult result = plagiarismDetectionService.scan(request.text());
        return ResponseEntity.ok(result);
    }
}
