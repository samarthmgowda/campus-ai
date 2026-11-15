package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.ProjectSubmission;
import com.campusconnect.campus_ai.repository.ProjectSubmissionRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cross-submission plagiarism detection.
 *
 * <p>{@link PlagiarismDetectionService} compares a submission against the
 * <em>library</em> — the curated document corpus. That check is blind to the one
 * case people actually worry about most: two students copying from each other.
 * Neither text is in the library, so both score NONE against it, and the system
 * reports two clean results for a pair of identical submissions.
 *
 * <p>This service closes that hole by comparing every stored submission against
 * every other stored submission. The scoring is deliberately identical to the
 * library scan — containment, 60% unigram / 40% bigram, stopwords stripped,
 * plurals-only stemming — so a score means the same thing whichever check
 * produced it. {@link PlagiarismDetectionService#tokens} and
 * {@link PlagiarismDetectionService#bigrams} are reused rather than copied: two
 * implementations of the same measure would drift, and a threshold calibrated
 * for one would stop meaning anything on the other.
 *
 * <p>No model server, no embeddings, no network. Same reasoning as the library
 * scan: it has to work with Ollama down, and it may run on every submission.
 *
 * <h2>What this cannot catch</h2>
 *
 * Word-overlap only. A synonym-swapped or machine-translated copy of another
 * student's work can score LOW here. That is a limitation of the method, not a
 * bug, and it is why the verdict is a pointer to a human, never an accusation.
 */
@Service
public class PlagiarismService {

    /** Same weights as the library scan — see PlagiarismDetectionService. */
    private static final double W_UNIGRAM = 0.6;
    private static final double W_BIGRAM = 0.4;

    /** Below this many distinct meaningful tokens the score is noise. */
    private static final int MIN_MEANINGFUL_TOKENS = 8;

    /** How many matching submissions to return, best first. */
    private static final int MAX_MATCHES = 5;

    private static final double HIGH_THRESHOLD = 0.70;
    private static final double MODERATE_THRESHOLD = 0.45;
    private static final double LOW_THRESHOLD = 0.25;

    private final ProjectSubmissionRepository submissionRepository;

    public PlagiarismService(ProjectSubmissionRepository submissionRepository) {
        this.submissionRepository = submissionRepository;
    }

    /**
     * One other submission the checked text overlaps with.
     *
     * @param submissionId   id of the matching submission, so the UI can link to it
     * @param title          that submission's title
     * @param userId         who submitted it — what an admin actually needs next
     * @param status         that submission's status (SUBMITTED, GRADED, …)
     * @param score          blended containment score
     * @param wordCoverage   share of the checked text's distinct words found there
     * @param phraseCoverage share of its adjacent word pairs found there
     */
    public record SubmissionMatch(
            Long submissionId,
            String title,
            String userId,
            String status,
            double score,
            double wordCoverage,
            double phraseCoverage) {}

    /**
     * @param verdict             NONE, LOW, MODERATE, HIGH or INSUFFICIENT
     * @param topScore            best matching submission's score, or 0
     * @param scannedSubmissions  how many stored submissions were compared
     * @param submissionWords     distinct meaningful words the score is based on
     * @param summary             a sentence the caller can display as-is
     * @param matches             up to five matching submissions, best first
     */
    public record CrossScanResult(
            String verdict,
            double topScore,
            int scannedSubmissions,
            int submissionWords,
            String summary,
            List<SubmissionMatch> matches) {}

    /**
     * Compare {@code submissionText} against every other stored submission.
     *
     * @throws IllegalArgumentException if the text is null or blank
     */
    public CrossScanResult scanAgainstAllSubmissions(String submissionText) {
        if (submissionText == null || submissionText.isBlank()) {
            throw new IllegalArgumentException("Submission text must not be empty");
        }

        List<String> submissionTokens = PlagiarismDetectionService.tokens(submissionText);
        Set<String> submissionWords = new HashSet<>(submissionTokens);
        Set<String> submissionBigrams = PlagiarismDetectionService.bigrams(submissionTokens);

        if (submissionWords.size() < MIN_MEANINGFUL_TOKENS) {
            return new CrossScanResult("INSUFFICIENT", 0.0, 0, submissionWords.size(),
                    "Add more text — a comparison needs at least " + MIN_MEANINGFUL_TOKENS
                            + " distinct words to mean anything.",
                    List.of());
        }

        List<ProjectSubmission> all = submissionRepository.findAll();
        if (all.size() < 2) {
            return new CrossScanResult("NONE", 0.0, all.size(), submissionWords.size(),
                    "Only " + all.size() + " submission" + (all.size() == 1 ? "" : "s")
                            + " stored — comparing submissions needs at least two with text.",
                    List.of());
        }

        List<SubmissionMatch> matches = new ArrayList<>();
        int comparable = 0;
        boolean skippedSelf = false;
        String checkedText = submissionText.strip();

        for (ProjectSubmission other : all) {
            // A submission with no stored body cannot be compared to anything.
            // Counted separately so the summary does not overstate the scan.
            String otherText = other.getSubmissionText();
            if (otherText == null || otherText.isBlank()) continue;

            // The text being checked is usually one of the stored submissions —
            // the panel stores a body and then checks it. Comparing it against
            // itself would report a perfect match against itself every time and
            // bury the real answer. Exactly ONE identical body is dropped for
            // that reason. Only one: if two students submitted identical text it
            // is stored twice, and the second copy is the finding, not the echo.
            if (!skippedSelf && otherText.strip().equals(checkedText)) {
                skippedSelf = true;
                continue;
            }

            List<String> otherTokens = PlagiarismDetectionService.tokens(otherText);
            if (otherTokens.isEmpty()) continue;
            comparable++;

            Set<String> otherWords = new HashSet<>(otherTokens);
            Set<String> otherBigrams = PlagiarismDetectionService.bigrams(otherTokens);

            double wordCoverage = (double) intersect(submissionWords, otherWords).size()
                    / submissionWords.size();
            // Div-by-zero guard: a very short submission has no pairs.
            double phraseCoverage = submissionBigrams.isEmpty() ? 0.0
                    : (double) intersect(submissionBigrams, otherBigrams).size()
                            / submissionBigrams.size();
            double score = W_UNIGRAM * wordCoverage + W_BIGRAM * phraseCoverage;

            // Same floor as the library scan: below half the LOW threshold the
            // overlap is ordinary shared vocabulary, not a lead worth showing.
            if (score <= LOW_THRESHOLD / 2) continue;

            matches.add(new SubmissionMatch(other.getId(), other.getTitle(), other.getUserId(),
                    other.getStatus(), round4(score), round4(wordCoverage), round4(phraseCoverage)));
        }

        matches.sort(Comparator.comparingDouble(SubmissionMatch::score).reversed());
        if (matches.size() > MAX_MATCHES) {
            matches = new ArrayList<>(matches.subList(0, MAX_MATCHES));
        }

        double topScore = matches.isEmpty() ? 0.0 : matches.get(0).score();
        String verdict = verdictFor(topScore);

        return new CrossScanResult(verdict, round4(topScore), comparable, submissionWords.size(),
                summarize(verdict, matches, comparable), List.copyOf(matches));
    }

    private static Set<String> intersect(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>();
        for (String s : a) if (b.contains(s)) out.add(s);
        return out;
    }

    private static String verdictFor(double topScore) {
        if (topScore >= HIGH_THRESHOLD) return "HIGH";
        if (topScore >= MODERATE_THRESHOLD) return "MODERATE";
        if (topScore >= LOW_THRESHOLD) return "LOW";
        return "NONE";
    }

    /**
     * Written here rather than in the UI so the thresholds and the wording that
     * explains them cannot drift apart.
     */
    private static String summarize(String verdict, List<SubmissionMatch> matches, int scanned) {
        if (scanned == 0) {
            return "No stored submission has text yet. Save a submission body, then check again.";
        }
        String first = matches.isEmpty() ? "" : matches.get(0).title();
        return switch (verdict) {
            case "HIGH" -> "Close match with \"" + first
                    + "\". Read both submissions side by side before drawing a conclusion.";
            case "MODERATE" -> "Substantial overlap with \"" + first
                    + "\" — possibly a shared source or close collaboration.";
            case "LOW" -> "Some shared vocabulary with \"" + first
                    + "\", which usually means a common topic rather than copying.";
            default -> "No meaningful overlap with the " + scanned
                    + " other submission" + (scanned == 1 ? "" : "s") + " compared.";
        };
    }

    private static double round4(double v) {
        return Math.round(v * 10_000d) / 10_000d;
    }
}