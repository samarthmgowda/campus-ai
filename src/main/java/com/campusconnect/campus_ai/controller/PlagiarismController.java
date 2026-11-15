package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.EmbeddingService;
import com.campusconnect.campus_ai.service.PlagiarismDetectionService;
import com.campusconnect.campus_ai.service.PlagiarismDetectionService.ScanResult;
import com.campusconnect.campus_ai.service.PlagiarismService;
import com.campusconnect.campus_ai.service.PlagiarismService.CrossScanResult;
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
 * <h2>Two endpoints, two different costs</h2>
 *
 * {@code /scan} (library) is a synchronous in-memory comparison — no Ollama, no
 * embeddings, no network. That is deliberate: it keeps a check that may run on
 * every submission cheap.
 *
 * <p>{@code /scan-all-submissions} (peer submissions) additionally asks Ollama
 * to embed the text, so that a paraphrased or machine-translated copy of another
 * student's work is visible and not just a copy-paste. The embedding is an
 * <em>optional</em> input to that scan: if it cannot be computed the endpoint
 * still answers, word-only, and says so. See the method comment.
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
    private final PlagiarismService plagiarismService;
    private final EmbeddingService embeddingService;

    public PlagiarismController(PlagiarismDetectionService plagiarismDetectionService,
                                PlagiarismService plagiarismService,
                                EmbeddingService embeddingService) {
        this.plagiarismDetectionService = plagiarismDetectionService;
        this.plagiarismService = plagiarismService;
        this.embeddingService = embeddingService;
    }

    /** Body is a record rather than a bare String so the JSON shape can grow. */
    public record ScanRequest(String text) {}

    /**
     * Scan a submission against the library corpus (document chunks).
     * Gated to ADMIN only — the people who need this are the ones who curate
     * the corpus. A student being able to call it would turn it into a way to
     * probe what the corpus contains.
     */
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

    /**
     * Scan a submission against ALL other submissions in the corpus.
     *
     * <p>This catches the "biggest one" use case: two students who copy from each
     * other, with NEITHER text in the library, would both score NONE against the
     * library but would match each other.
     *
     * <p>The text is embedded when Ollama is reachable so a paraphrased copy is
     * visible too. <b>An embedding failure must not fail the request.</b> The
     * check is primarily word overlap and has to work with the model server down
     * — that was true before this endpoint had any model dependency, and a
     * feature that goes dark because Ollama restarted would be worse than one
     * that never had the semantic signal. On failure the scan runs word-only and
     * the response reports {@code semanticComparisons: 0}, which the summary
     * turns into an explicit "semantic comparison was unavailable".
     *
     * <p>Gated to ADMIN only — the people who need this are the ones who curate
     * the corpus and manage the submission pipeline.
     */
    @PostMapping("/scan-all-submissions")
    public ResponseEntity<?> scanAllSubmissions(@RequestBody(required = false) ScanRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Paste the text you want to check."));
        }
        if (request.text().length() > MAX_CHARS) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "That is " + request.text().length()
                            + " characters. The limit is " + MAX_CHARS + " — check a section at a time."));
        }

        float[] embedding = null;
        try {
            embedding = embeddingService.embed(request.text());
        } catch (RuntimeException e) {
            // Deliberately swallowed and only logged by the summary flag: see the
            // method comment. Anything that throws here — Ollama down, timeout,
            // wrong dimension, malformed response — means "no semantic signal",
            // not "no answer".
        }

        CrossScanResult result = plagiarismService.scanAgainstAllSubmissions(request.text(), embedding);
        return ResponseEntity.ok(result);
    }
}
