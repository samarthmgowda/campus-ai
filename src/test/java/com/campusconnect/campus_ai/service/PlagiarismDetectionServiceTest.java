package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.repository.DocumentChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Scores are only useful if they survive the cases that broke the first
 * implementation. Jaccard similarity reported a verbatim copy inside a longer
 * document as ~0.15, so these tests are written around the two properties that
 * matter: a copy scores high no matter how long the source is, and unrelated
 * text scores low even when it shares ordinary vocabulary.
 */
class PlagiarismDetectionServiceTest {

    private DocumentChunkRepository chunkRepository;
    private PlagiarismDetectionService service;

    /** Row shape of findScannableChunks: id, content, page, section, docId, docTitle. */
    private static Object[] chunk(long id, String content, Integer page, String section,
                                  long documentId, String title) {
        return new Object[]{id, content, page, section, documentId, title};
    }

    @BeforeEach
    void setUp() {
        chunkRepository = mock(DocumentChunkRepository.class);
        service = new PlagiarismDetectionService(chunkRepository);
    }

    private static final String HANDBOOK =
            "Students must maintain seventy five percent attendance in every registered course. "
                    + "Shortages below this threshold require written approval from the head of department "
                    + "before the examination form is accepted. Medical certificates must be submitted "
                    + "within seven days of returning to campus.";

    @Test
    void verbatimCopyOfAShortPassageScoresHigh() {
        when(chunkRepository.findScannableChunks())
                .thenReturn(List.<Object[]>of(chunk(1L, HANDBOOK, 4, "Attendance", 10L, "Academic Handbook")));

        // A passage lifted straight out of the longer source.
        String copied = "Students must maintain seventy five percent attendance in every registered course. "
                + "Shortages below this threshold require written approval from the head of department.";

        var result = service.scan(copied);

        // The old Jaccard metric scored this near 0.15 because the source is longer.
        assertThat(result.topScore()).isGreaterThan(0.8);
        assertThat(result.verdict()).isEqualTo("HIGH");
        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).documentTitle()).isEqualTo("Academic Handbook");
        assertThat(result.matches().get(0).wordCoverage()).isGreaterThan(0.9);
    }

    @Test
    void unrelatedTextOnADifferentTopicScoresLow() {
        when(chunkRepository.findScannableChunks())
                .thenReturn(List.<Object[]>of(chunk(1L, HANDBOOK, 4, "Attendance", 10L, "Academic Handbook")));

        String unrelated = "The robotics club meets on Friday afternoons in the fabrication laboratory. "
                + "Members solder circuit boards, calibrate servo motors and prepare for the regional competition.";

        var result = service.scan(unrelated);

        assertThat(result.topScore()).isLessThan(0.25);
        assertThat(result.verdict()).isEqualTo("NONE");
        assertThat(result.matches()).isEmpty();
    }

    /**
     * The failure mode that makes a plagiarism checker useless: two texts on the
     * same subject, sharing the topic's whole vocabulary, but written
     * independently. Word overlap alone is high here, which is why the score
     * only gives word overlap 60% and lets phrase overlap pull it back down.
     */
    @Test
    void sharedTopicVocabularyWithoutSharedPhrasingStaysBelowHigh() {
        when(chunkRepository.findScannableChunks())
                .thenReturn(List.<Object[]>of(chunk(1L, HANDBOOK, 4, "Attendance", 10L, "Academic Handbook")));

        String paraphrased = "Attendance of seventy five percent is compulsory for registered courses. "
                + "A student below the threshold needs written approval from the department head. "
                + "Medical certificates go to the office within one week of return.";

        var result = service.scan(paraphrased);

        assertThat(result.topScore()).isLessThan(0.70);
        assertThat(result.verdict()).isNotEqualTo("HIGH");
    }

    @Test
    void stopwordsAloneDoNotCountAsOverlap() {
        when(chunkRepository.findScannableChunks())
                .thenReturn(List.<Object[]>of(chunk(1L, HANDBOOK, 4, "Attendance", 10L, "Academic Handbook")));

        // Every one of these words appears in the handbook, but they are all
        // function words. Stripping them leaves nothing to compare, so the
        // answer is "not enough text", never a flattering 0%.
        String onlyStopwords = "the of and to in is are that this it for with on was";

        var result = service.scan(onlyStopwords);

        assertThat(result.verdict()).isEqualTo("INSUFFICIENT");
        assertThat(result.submissionWords()).isZero();
    }

    @Test
    void shortSubmissionIsInsufficientRatherThanClean() {
        when(chunkRepository.findScannableChunks())
                .thenReturn(List.<Object[]>of(chunk(1L, HANDBOOK, 4, "Attendance", 10L, "Academic Handbook")));

        var result = service.scan("spring boot microservices");

        assertThat(result.verdict()).isEqualTo("INSUFFICIENT");
        assertThat(result.matches()).isEmpty();
        // The corpus must not even be queried — there is nothing to compare.
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void emptyCorpusIsReportedAsSuch() {
        when(chunkRepository.findScannableChunks()).thenReturn(List.of());

        var result = service.scan(
                "Students must maintain seventy five percent attendance in every registered course "
                        + "and submit medical certificates within seven days.");

        assertThat(result.verdict()).isEqualTo("NONE");
        assertThat(result.scannedChunks()).isZero();
        assertThat(result.summary()).contains("no content");
    }

    /**
     * A long document is stored as several chunks. If each chunk were scored in
     * isolation, a submission spanning two of them would score at roughly half
     * its true coverage — a verbatim copy reported as a moderate match.
     */
    @Test
    void coverageIsUnionedAcrossChunksOfTheSameDocument() {
        String firstHalf = "Students must maintain seventy five percent attendance in every registered course";
        String secondHalf = "Shortages below this threshold require written approval from the head of department";

        when(chunkRepository.findScannableChunks()).thenReturn(List.of(
                chunk(1L, firstHalf, 4, "Attendance", 10L, "Academic Handbook"),
                chunk(2L, secondHalf, 5, "Attendance", 10L, "Academic Handbook")));

        var result = service.scan(firstHalf + ". " + secondHalf + ".");

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).wordCoverage()).isEqualTo(1.0);
        assertThat(result.matches().get(0).score()).isGreaterThan(0.95);
    }

    @Test
    void matchesAreRankedBestFirst() {
        when(chunkRepository.findScannableChunks()).thenReturn(List.of(
                chunk(1L, "The robotics club meets on Friday afternoons in the fabrication lab.", 1, null, 20L, "Clubs"),
                chunk(2L, HANDBOOK, 4, "Attendance", 10L, "Academic Handbook")));

        String submission = "Students must maintain seventy five percent attendance in every registered course. "
                + "Shortages require written approval from the head of department. "
                + "Medical certificates must be submitted within seven days.";

        var result = service.scan(submission);

        assertThat(result.matches()).isNotEmpty();
        assertThat(result.matches().get(0).documentTitle()).isEqualTo("Academic Handbook");
        // Descending order, checked explicitly because a trailing .reversed()
        // on the wrong comparator has inverted rankings in this codebase before.
        for (int i = 1; i < result.matches().size(); i++) {
            assertThat(result.matches().get(i - 1).score())
                    .isGreaterThanOrEqualTo(result.matches().get(i).score());
        }
    }

    @Test
    void blankTextIsRejected() {
        assertThatThrownBy(() -> service.scan("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be empty");
        assertThatThrownBy(() -> service.scan(null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void chunkWithNullOrBlankContentIsSkipped() {
        when(chunkRepository.findScannableChunks()).thenReturn(List.of(
                chunk(1L, null, 1, null, 10L, "Empty"),
                chunk(2L, "   ", 1, null, 11L, "Blank"),
                chunk(3L, HANDBOOK, 4, "Attendance", 12L, "Academic Handbook")));

        var result = service.scan("Students must maintain seventy five percent attendance in every "
                + "registered course and submit medical certificates within seven days of returning.");

        assertThat(result.matches()).hasSize(1);
        assertThat(result.matches().get(0).documentTitle()).isEqualTo("Academic Handbook");
    }

    @Test
    void tokenizerDropsStopwordsAndNormalisesPlurals() {
        List<String> tokens = PlagiarismDetectionService.tokens("The Students and their SYSTEMS are documented.");

        assertThat(tokens).contains("student", "system", "documented");
        assertThat(tokens).doesNotContain("the", "and", "are", "their");
    }

    @Test
    void bigramsPreserveOrderAndDuplicates() {
        List<String> tokens = List.of("alpha", "beta", "alpha", "gamma");

        assertThat(PlagiarismDetectionService.bigrams(tokens))
                .containsExactlyInAnyOrder("alpha beta", "beta alpha", "alpha gamma");
    }
}
