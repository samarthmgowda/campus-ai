package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.ProjectSubmission;
import com.campusconnect.campus_ai.repository.ProjectSubmissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The cross-submission check exists for one case: two students copy from each
 * other and neither text is in the library, so the library scan reports both
 * clean. These tests are written around that case, plus the ones that would
 * make the feature dangerous if it got them wrong — ordinary shared vocabulary
 * must not read as HIGH, and an empty corpus must say so rather than implying
 * a clean result.
 */
class PlagiarismServiceTest {

    private ProjectSubmissionRepository submissionRepository;
    private PlagiarismService service;

    private static ProjectSubmission submission(long id, String title, String userId, String text) {
        ProjectSubmission s = new ProjectSubmission();
        s.setId(id);
        s.setTitle(title);
        s.setUserId(userId);
        s.setStatus("SUBMITTED");
        s.setSubmissionText(text);
        return s;
    }

    private static final String ORIGINAL =
            "Campus placement season has become the single most stressful period in an engineering "
                    + "student's life. Students apply to dozens of companies and still struggle to convert "
                    + "interviews into offers. The core problem is not a lack of opportunities but a lack of "
                    + "preparation that matches what recruiters actually test.";

    /** The same text with a few words swapped — what a "rewrite" actually looks like. */
    private static final String REWRITTEN =
            "Placement season has become the most stressful period in an engineering student's life. "
                    + "Students apply to dozens of companies and still struggle to convert interviews into "
                    + "offers. The core problem is not a lack of opportunities but a lack of preparation "
                    + "that matches what recruiters actually test.";

    private static final String UNRELATED =
            "Beacon based attendance replaces manual roll calls in large lecture halls. Each classroom "
                    + "broadcasts a rotating identifier that a phone resolves into a signed attendance token, "
                    + "which stops one student sharing a token with a friend who never entered the room.";

    @BeforeEach
    void setUp() {
        submissionRepository = mock(ProjectSubmissionRepository.class);
        service = new PlagiarismService(submissionRepository);
    }

    @Test
    void verbatimCopyOfAnotherSubmissionScoresHigh() {
        when(submissionRepository.findAll()).thenReturn(List.of(
                submission(1L, "Placement report", "aarav@campus.edu", ORIGINAL),
                submission(2L, "Placement report (copy)", "rohan@campus.edu", ORIGINAL)));

        PlagiarismService.CrossScanResult result = service.scanAgainstAllSubmissions(ORIGINAL);

        assertThat(result.verdict()).isEqualTo("HIGH");
        assertThat(result.topScore()).isGreaterThanOrEqualTo(0.70);
        // One match, not two: the row the text came from is dropped as the
        // echo of the checked text, and the other student's copy is the
        // finding.
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).submissionId()).isEqualTo(2L);
        assertThat(result.matches().get(0).score()).isEqualTo(1.0);
    }

    /**
     * The row the checked text came from must not be reported as its own match —
     * otherwise every scan of a stored submission comes back 100% HIGH against
     * itself. Exactly one identical body is dropped. A third student who
     * submitted the same text again is still a finding.
     */
    @Test
    void theCheckedTextIsNotReportedAsMatchingItself() {
        for (List<ProjectSubmission> corpus : List.of(
                List.of(submission(1L, "Report", "aarav@campus.edu", ORIGINAL)),
                List.of(submission(1L, "Report", "aarav@campus.edu", ORIGINAL),
                        submission(2L, "Report copy", "rohan@campus.edu", ORIGINAL),
                        submission(3L, "Report copy 2", "vikram@campus.edu", ORIGINAL)))) {

            when(submissionRepository.findAll()).thenReturn(corpus);

            PlagiarismService.CrossScanResult result = service.scanAgainstAllSubmissions(ORIGINAL);

            assertThat(result.matches())
                    .extracting(PlagiarismService.SubmissionMatch::submissionId)
                    .doesNotContain(1L);
            assertThat(result.matches()).hasSize(corpus.size() - 1);
        }
    }

    @Test
    void aRewrittenCopyStillScoresHighEnoughToInvestigate() {
        when(submissionRepository.findAll()).thenReturn(List.of(
                submission(1L, "Placement report", "aarav@campus.edu", ORIGINAL),
                submission(2L, "Placement report (rewritten)", "rohan@campus.edu", REWRITTEN)));

        PlagiarismService.CrossScanResult result = service.scanAgainstAllSubmissions(REWRITTEN);

        // The whole point: a handful of swapped words is a copy, and the
        // containment measure has to survive that.
        assertThat(result.verdict()).isIn("HIGH", "MODERATE");
        assertThat(result.matches()).isNotEmpty();
    }

    /**
     * Shared topic vocabulary is the failure mode that would make this feature
     * useless in practice — every report on the same assignment shares words.
     * It must land low, not HIGH.
     */
    @Test
    void unrelatedWorkOnASharedTopicDoesNotScoreHigh() {
        when(submissionRepository.findAll()).thenReturn(List.of(
                submission(1L, "Placement report", "aarav@campus.edu", ORIGINAL),
                submission(2L, "Beacon attendance", "vikram@campus.edu", UNRELATED)));

        PlagiarismService.CrossScanResult result = service.scanAgainstAllSubmissions(UNRELATED);

        assertThat(result.verdict()).isIn("NONE", "LOW");
        assertThat(result.topScore()).isLessThan(0.45);
    }

    @Test
    void emptyCorpusSaysSoInsteadOfReportingClean() {
        when(submissionRepository.findAll()).thenReturn(List.of());

        PlagiarismService.CrossScanResult result = service.scanAgainstAllSubmissions(ORIGINAL);

        assertThat(result.verdict()).isEqualTo("NONE");
        assertThat(result.scannedSubmissions()).isZero();
        assertThat(result.matches()).isEmpty();
        // "No overlap" would be a lie here — there was nothing to compare to.
        assertThat(result.summary()).containsIgnoringCase("at least two");
    }

    @Test
    void submissionsWithNoStoredTextAreNotCountedAsCompared() {
        ProjectSubmission noBody = submission(2L, "Empty", "rohan@campus.edu", null);
        ProjectSubmission other = submission(3L, "Beacon attendance", "vikram@campus.edu", UNRELATED);
        when(submissionRepository.findAll()).thenReturn(List.of(
                submission(1L, "Placement report", "aarav@campus.edu", ORIGINAL),
                noBody,
                other));

        PlagiarismService.CrossScanResult result = service.scanAgainstAllSubmissions(ORIGINAL);

        // One *other* submission has comparable text. The body-less row must
        // not inflate the count the summary reports, and the row the text came
        // from is not "another submission" either.
        assertThat(result.scannedSubmissions()).isEqualTo(1);
    }

    @Test
    void tooLittleTextIsInsufficientRatherThanClean() {
        PlagiarismService.CrossScanResult result = service.scanAgainstAllSubmissions("spring boot");

        assertThat(result.verdict()).isEqualTo("INSUFFICIENT");
        assertThat(result.matches()).isEmpty();
    }

    @Test
    void blankTextIsRejected() {
        assertThatThrownBy(() -> service.scanAgainstAllSubmissions("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void neverReturnsMoreThanFiveMatches() {
        List<ProjectSubmission> corpus = new java.util.ArrayList<>();
        corpus.add(submission(1L, "Original", "aarav@campus.edu", ORIGINAL));
        for (int i = 2; i <= 9; i++) {
            corpus.add(submission(i, "Copy " + i, "student" + i + "@campus.edu", ORIGINAL));
        }
        when(submissionRepository.findAll()).thenReturn(corpus);

        PlagiarismService.CrossScanResult result = service.scanAgainstAllSubmissions(ORIGINAL);

        assertThat(result.matches()).hasSize(5);
    }

    // ------------------------------------------------- semantic similarity

    /**
     * A machine paraphrase: the same argument in different words. Word overlap
     * is deliberately weak here (different vocabulary), so the semantic signal
     * is the only thing that can surface it — which is exactly the case this
     * column exists for.
     */
    private static final String PARAPHRASE =
            "Recruitment drives are the most anxiety-inducing stretch of an engineering degree. "
                    + "Undergraduates send resumes to many firms yet seldom turn interviews into job "
                    + "offers. The real obstacle is not scarce openings but readiness that does not "
                    + "align with what hiring panels assess.";

    private static ProjectSubmission withVector(long id, String title, String userId, String text,
                                                float[] embedding) {
        ProjectSubmission s = submission(id, title, userId, text);
        s.setEmbedding(embedding);
        return s;
    }

    /**
     * The direction the checked text is embedded in, fixed so a test can name
     * the cosine it wants instead of the vector it needs.
     */
    private static final float[] QUERY_VECTOR = {1.0f, 0.0f};

    /**
     * A unit vector whose cosine with {@link #QUERY_VECTOR} is exactly
     * {@code cosine}. Unit length is the point: these really do sit at that
     * angle, which is what a test claiming "cosine 0.88" has to mean.
     */
    private static float[] vectorWithCosine(double cosine) {
        return new float[]{(float) cosine, (float) Math.sqrt(Math.max(0.0, 1.0 - cosine * cosine))};
    }

    /**
     * The headline case. Without the vector this pair scores too low to appear
     * on a reader's radar; with a near-identical embedding it has to be HIGH.
     */
    @Test
    void aParaphraseWithNearIdenticalMeaningScoresHigh() {
        when(submissionRepository.findAll()).thenReturn(List.of(
                withVector(1L, "Placement report", "aarav@campus.edu", ORIGINAL, QUERY_VECTOR),
                withVector(2L, "Placement write-up", "rohan@campus.edu", PARAPHRASE, QUERY_VECTOR)));

        PlagiarismService.CrossScanResult wordOnly = service.scanAgainstAllSubmissions(PARAPHRASE);
        PlagiarismService.CrossScanResult semantic =
                service.scanAgainstAllSubmissions(PARAPHRASE, QUERY_VECTOR);

        // Premise of the test: words alone would not have caught it.
        assertThat(wordOnly.verdict()).isIn("NONE", "LOW");
        assertThat(semantic.verdict()).isEqualTo("HIGH");
        assertThat(semantic.topScore()).isGreaterThan(wordOnly.topScore());
        // Row 2 is the checked text itself and is dropped as the echo; row 1 is
        // the only comparison, and it is the one the semantic signal caught.
        assertThat(semantic.semanticComparisons()).isEqualTo(1);
        assertThat(semantic.matches().get(0).semanticScore()).isEqualTo(1.0);
    }

    /**
     * The failure that would make this unusable: two independent reports on the
     * same assignment are genuinely similar in meaning. They must not be
     * escalated. 0.88 is the top of the "same subject" band.
     */
    @Test
    void sameSubjectWorkWithARealisticCosineIsNotEscalated() {
        when(submissionRepository.findAll()).thenReturn(List.of(
                withVector(1L, "Placement report", "aarav@campus.edu", ORIGINAL, QUERY_VECTOR),
                withVector(2L, "Beacon attendance", "vikram@campus.edu", UNRELATED, QUERY_VECTOR)));

        PlagiarismService.CrossScanResult wordOnly = service.scanAgainstAllSubmissions(UNRELATED);
        PlagiarismService.CrossScanResult semantic =
                service.scanAgainstAllSubmissions(UNRELATED, vectorWithCosine(0.88));

        assertThat(semantic.verdict()).isEqualTo(wordOnly.verdict());
        assertThat(semantic.verdict()).isIn("NONE", "LOW");
        assertThat(semantic.topScore()).isCloseTo(wordOnly.topScore(), org.assertj.core.data.Offset.offset(1e-4));
    }

    /**
     * Rows stored while Ollama was down have no vector. A null on either side
     * must reduce that pair to word overlap rather than failing the scan —
     * otherwise one old row would break the check for the whole corpus.
     */
    @Test
    void aStoredRowWithoutAVectorFallsBackToWordOverlap() {
        when(submissionRepository.findAll()).thenReturn(List.of(
                submission(1L, "Placement report", "aarav@campus.edu", ORIGINAL),
                submission(2L, "Placement report (copy)", "rohan@campus.edu", ORIGINAL)));

        PlagiarismService.CrossScanResult result =
                service.scanAgainstAllSubmissions(ORIGINAL, QUERY_VECTOR);

        assertThat(result.verdict()).isEqualTo("HIGH");
        assertThat(result.semanticComparisons()).isZero();
        assertThat(result.matches().get(0).semanticScore()).isNull();
    }

    /** The query side can also be missing — same guarantee, other direction. */
    @Test
    void aNullQueryVectorReproducesTheWordOnlyResultExactly() {
        when(submissionRepository.findAll()).thenReturn(List.of(
                submission(1L, "Placement report", "aarav@campus.edu", ORIGINAL),
                submission(2L, "Placement report (rewritten)", "rohan@campus.edu", REWRITTEN)));

        PlagiarismService.CrossScanResult wordOnly = service.scanAgainstAllSubmissions(REWRITTEN);
        PlagiarismService.CrossScanResult nullVector = service.scanAgainstAllSubmissions(REWRITTEN, null);

        assertThat(nullVector.verdict()).isEqualTo(wordOnly.verdict());
        assertThat(nullVector.topScore()).isEqualTo(wordOnly.topScore());
        assertThat(nullVector.semanticComparisons()).isZero();
        assertThat(nullVector.summary()).isEqualTo(wordOnly.summary());
    }

    /** semanticComparisons must count only the pairs that had vectors on both sides. */
    @Test
    void semanticComparisonsCountsOnlyFullyEmbeddedPairs() {
        when(submissionRepository.findAll()).thenReturn(List.of(
                submission(1L, "Checked report", "aarav@campus.edu", ORIGINAL),
                withVector(2L, "Embedded A", "rohan@campus.edu", UNRELATED, vectorWithCosine(1.0)),
                withVector(3L, "Embedded B", "vikram@campus.edu", REWRITTEN, vectorWithCosine(1.0)),
                submission(4L, "No vector", "neha@campus.edu", UNRELATED)));

        PlagiarismService.CrossScanResult result =
                service.scanAgainstAllSubmissions(ORIGINAL, vectorWithCosine(1.0));

        assertThat(result.scannedSubmissions()).isEqualTo(3);
        assertThat(result.semanticComparisons()).isEqualTo(2);
    }

    /**
     * "We found nothing" and "we could not check" must not read alike. With no
     * embeddings anywhere the summary has to admit the blind spot. Both stored
     * rows carry no vector, so every comparison is word-only.
     *
     * <p>The corpus is deliberately about a different subject from the checked
     * text, so the NONE verdict is unambiguous and the sentence being tested is
     * the only one that could appear.
     */
    @Test
    void aWordOnlyNoMatchAdmitsThatParaphrasesWouldBeInvisible() {
        when(submissionRepository.findAll()).thenReturn(List.of(
                submission(1L, "Hostel mess review", "aarav@campus.edu", HOSTEL),
                submission(2L, "Hostel mess review (rewritten)", "rohan@campus.edu", HOSTEL_REWRITTEN)));

        PlagiarismService.CrossScanResult result = service.scanAgainstAllSubmissions(UNRELATED);

        assertThat(result.verdict()).isEqualTo("NONE");
        assertThat(result.semanticComparisons()).isZero();
        assertThat(result.summary()).containsIgnoringCase("semantic comparison was unavailable");
        assertThat(result.summary()).containsIgnoringCase("paraphrase");
    }

    /** With embeddings available, the same verdict is a real finding and says so. */
    @Test
    void aSemanticallyCheckedNoMatchDoesNotClaimABlindSpot() {
        // Stored rows point along the second axis, the checked text along the
        // first: cosine 0, so the blend leaves the word score untouched and the
        // verdict is a genuine NONE rather than an unexamined one.
        when(submissionRepository.findAll()).thenReturn(List.of(
                withVector(1L, "Hostel mess review", "aarav@campus.edu", HOSTEL, vectorWithCosine(0.0)),
                withVector(2L, "Hostel mess review (rewritten)", "rohan@campus.edu", HOSTEL_REWRITTEN,
                        vectorWithCosine(0.0))));

        PlagiarismService.CrossScanResult result =
                service.scanAgainstAllSubmissions(UNRELATED, QUERY_VECTOR);

        assertThat(result.verdict()).isEqualTo("NONE");
        assertThat(result.semanticComparisons()).isEqualTo(2);
        assertThat(result.summary()).doesNotContainIgnoringCase("unavailable");
        assertThat(result.summary()).containsIgnoringCase("meaning");
    }

    /**
     * A corpus filler about something the checked texts never discuss, so a
     * NONE verdict can only come from an actual comparison rather than from
     * near-miss vocabulary.
     */
    private static final String HOSTEL =
            "The hostel mess publishes a weekly menu every Sunday evening on the notice board outside "
                    + "the dining hall. Residents may raise complaints through the warden's office, and "
                    + "the committee reviews them at the start of each month.";

    private static final String HOSTEL_REWRITTEN =
            "A weekly menu is posted each Sunday evening outside the dining hall by the hostel mess. "
                    + "Complaints go to the warden's office, where the committee reviews them monthly.";

    /**
     * A heavy rewrite: the same claim in vocabulary the source never uses. Word
     * overlap is near zero by construction, so the word-only scan filters the
     * pair out entirely — which is exactly the pair the floor would have thrown
     * away if it were applied before the blend instead of after.
     */
    private static final String HEAVY_REWRITE =
            "Candidates dispatch resumes to numerous firms yet seldom convert screenings into jobs. "
                    + "The obstacle is not scarce openings but readiness misaligned with what hiring "
                    + "panels assess. Recruiting drives remain the most anxiety inducing stretch of an "
                    + "engineering degree.";

    private static final String HEAVY_REWRITE_SOURCE =
            "Undergraduates send applications to many companies but rarely turn interviews into offers. "
                    + "The real problem is not a shortage of roles but preparation that does not match "
                    + "what recruiters test. Placement season stays the single most stressful period of "
                    + "an engineering student's life.";

    /**
     * The floor has to be applied to the blended score. Filtering on word
     * overlap first would discard exactly the paraphrased pair the semantic
     * signal was added to find.
     */
    @Test
    void aParaphraseBelowTheWordFloorIsStillReported() {
        // Two rows so the scan does not short-circuit on "only one submission".
        // Row 2 is both about something else and points away from the query
        // vector, so it cannot match on words or on meaning — leaving row 1 as
        // the only thing that could reach the report.
        when(submissionRepository.findAll()).thenReturn(List.of(
                withVector(1L, "Recruitment study", "aarav@campus.edu", HEAVY_REWRITE_SOURCE, QUERY_VECTOR),
                withVector(2L, "Hostel mess review", "vikram@campus.edu", HOSTEL, vectorWithCosine(0.0))));

        // The premise, checked rather than assumed: on words alone this pair
        // never reaches the report. If a future change to tokenisation starts
        // surfacing it, this test stops proving what it claims to prove.
        assertThat(service.scanAgainstAllSubmissions(HEAVY_REWRITE).matches()).isEmpty();

        PlagiarismService.CrossScanResult result =
                service.scanAgainstAllSubmissions(HEAVY_REWRITE, QUERY_VECTOR);

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).submissionId()).isEqualTo(1L);
        assertThat(result.matches().get(0).score()).isGreaterThan(0.70);
    }

    /** The semantic signal lifts the score, so self-match exclusion must survive it. */
    @Test
    void theCheckedTextIsStillExcludedWhenEmbeddingsAreUsed() {
        when(submissionRepository.findAll()).thenReturn(List.of(
                withVector(1L, "Report", "aarav@campus.edu", ORIGINAL, QUERY_VECTOR),
                withVector(2L, "Report copy", "rohan@campus.edu", ORIGINAL, QUERY_VECTOR)));

        PlagiarismService.CrossScanResult result =
                service.scanAgainstAllSubmissions(ORIGINAL, QUERY_VECTOR);

        assertThat(result.matches())
                .extracting(PlagiarismService.SubmissionMatch::submissionId)
                .containsExactly(2L);
    }
}
