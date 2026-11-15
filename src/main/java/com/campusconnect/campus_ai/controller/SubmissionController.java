package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.entity.ProjectSubmission;
import com.campusconnect.campus_ai.repository.ProjectSubmissionRepository;
import com.campusconnect.campus_ai.service.AuthContext;
import com.campusconnect.campus_ai.service.CurrentUser;
import com.campusconnect.campus_ai.service.EmbeddingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The corpus that the cross-submission originality check reads.
 *
 * <p>{@code /evaluation/plagiarism/scan-all-submissions} answers "does this text
 * overlap with another student's submission" — but only for submissions that
 * have actually been stored. Nothing else in the app writes {@code
 * ProjectSubmission.submissionText}, so without this endpoint the cross-scan
 * would only ever be able to say "0 submissions stored" and the UI built on top
 * of it would be decorative.
 *
 * <p>Admin-gated in SecurityConfig (see {@code /submissions/**}). A submission
 * body is the student's own work; listing the corpus hands one person everyone
 * else's text, so this is the same audience as the Library and the originality
 * check itself.
 *
 * <h2>Why both list and create</h2>
 *
 * Create exists so the corpus can be filled from the same place it is used —
 * an admin pastes a submission in, stores it, and it becomes comparable. List
 * exists so the panel can say what is in the corpus instead of leaving the
 * reader to guess why a scan came back NONE.
 */
@RestController
@RequestMapping("/submissions")
public class SubmissionController {

    /**
     * Same ceiling as the originality scan. A body longer than this could never
     * be checked anyway, so accepting it would only store something unusable.
     */
    private static final int MAX_CHARS = 50_000;

    /** Titles are display text in a list row, not a document name. */
    private static final int MAX_TITLE = 200;

    /** Enough of the body to recognise it without shipping the whole thing. */
    private static final int EXCERPT_CHARS = 160;

    private final ProjectSubmissionRepository submissionRepository;
    private final EmbeddingService embeddingService;

    public SubmissionController(ProjectSubmissionRepository submissionRepository,
                                EmbeddingService embeddingService) {
        this.submissionRepository = submissionRepository;
        this.embeddingService = embeddingService;
    }

    /** Body is a record, not a bare map, so the accepted shape is documented. */
    public record CreateSubmissionRequest(String title, String text, String userId) {}

    /**
     * Whether the stored row got a semantic fingerprint.
     *
     * <p>Reported rather than hidden because it changes what the cross-check can
     * find later: a row stored without a vector is compared on words alone, so a
     * paraphrase of it stays invisible until it is re-embedded. Saying so at
     * store time is the only moment the person can do anything about it.
     */
    private boolean embed(ProjectSubmission s) {
        if (s.getSubmissionText() == null || s.getSubmissionText().isBlank()) return false;
        try {
            s.setEmbedding(embeddingService.embed(s.getSubmissionText()));
            return true;
        } catch (RuntimeException e) {
            // Ollama down, timed out, or returned the wrong shape. Storing the
            // body without a vector is strictly better than refusing the store:
            // the text still counts for word overlap, and the Library page can
            // backfill the vector later.
            return false;
        }
    }

    /**
     * A stored submission, trimmed for a list.
     *
     * @param id      submission id
     * @param title   what it is called
     * @param userId  who submitted it, or null when unknown
     * @param status  SUBMITTED / GRADED / …
     * @param words   how much comparable text it carries
     * @param excerpt opening of the body, for recognition
     */
    public record SubmissionSummary(
            Long id,
            String title,
            String userId,
            String status,
            int words,
            String excerpt) {}

    /** GET /submissions — everything the cross-scan will compare against. */
    @GetMapping
    public List<SubmissionSummary> list() {
        // Copied because the repository may hand back an immutable list, and
        // sorting it in place would throw rather than order it.
        List<ProjectSubmission> all = new ArrayList<>(submissionRepository.findAll());
        // Newest first: the one just stored is the one being talked about.
        all.sort((a, b) -> {
            LocalDateTime x = a.getCreatedAt();
            LocalDateTime y = b.getCreatedAt();
            if (x == null && y == null) return 0;
            if (x == null) return 1;
            if (y == null) return -1;
            return y.compareTo(x);
        });

        List<SubmissionSummary> out = new ArrayList<>(all.size());
        for (ProjectSubmission s : all) {
            out.add(new SubmissionSummary(
                    s.getId(),
                    s.getTitle(),
                    s.getUserId(),
                    s.getStatus(),
                    countWords(s.getSubmissionText()),
                    excerpt(s.getSubmissionText())));
        }
        return out;
    }

    /** POST /submissions — store a body so it becomes comparable. */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody(required = false) CreateSubmissionRequest request) {
        if (request == null || request.text() == null || request.text().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "A submission needs its text before it can be stored."));
        }
        if (request.text().length() > MAX_CHARS) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "That is " + request.text().length()
                            + " characters. The limit is " + MAX_CHARS + "."));
        }

        CurrentUser actor = AuthContext.require();

        ProjectSubmission s = new ProjectSubmission();
        String title = request.title() == null ? "" : request.title().trim();
        if (title.isEmpty()) {
            title = "Untitled submission";
        }
        if (title.length() > MAX_TITLE) {
            title = title.substring(0, MAX_TITLE);
        }
        s.setTitle(title);
        s.setSubmissionText(request.text());
        // Attribution: an explicit userId wins (an admin storing a student's
        // work), otherwise the person who stored it is the only honest answer.
        String userId = request.userId() == null || request.userId().isBlank()
                ? actor.email()
                : request.userId().trim();
        s.setUserId(userId);
        s.setStatus("SUBMITTED");
        s.setCreatedAt(LocalDateTime.now());
        s.setUpdatedAt(LocalDateTime.now());

        // Embed before saving so the row is never stored in a state the
        // semantic check cannot use. Failure is reported, not thrown.
        boolean embedded = embed(s);

        ProjectSubmission saved = submissionRepository.save(s);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("id", saved.getId());
        resp.put("title", saved.getTitle());
        resp.put("userId", saved.getUserId());
        resp.put("status", saved.getStatus());
        resp.put("words", countWords(saved.getSubmissionText()));
        resp.put("embedded", embedded);
        return ResponseEntity.ok(resp);
    }

    /**
     * Word count over the raw body. Deliberately raw rather than the scanner's
     * distinct-token count: this number is "how much text is here", and the
     * scanner's own count is reported separately by the scan.
     */
    private static int countWords(String text) {
        if (text == null || text.isBlank()) return 0;
        return text.trim().split("\\s+").length;
    }

    private static String excerpt(String text) {
        if (text == null || text.isBlank()) return "";
        String flat = text.trim().replaceAll("\\s+", " ");
        return flat.length() <= EXCERPT_CHARS ? flat : flat.substring(0, EXCERPT_CHARS) + "…";
    }
}
