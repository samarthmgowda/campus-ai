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
}
