package com.campusconnect.campus_ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Pins the groundedness check to whole-token matching.
 *
 * These tests exist because the check originally used {@code String.contains},
 * which made two wrong answers pass as grounded:
 *   - a short answer term matching inside an unrelated context word
 *     ("art" in "start"), inflating the term-overlap score;
 *   - an answer number matching inside a longer context number
 *     ("5" in "75"), so a model could assert a value belonging to a
 *     different criterion and still clear the gate.
 *
 * Both are correctness bugs in the safety check, not cosmetics, so they are
 * locked down here rather than left to the eval harness to notice.
 */
class FaithfulnessMatchingTest {

    private SearchService searchService;
    private OllamaChatService chatService;
    private AskService askService;

    @BeforeEach
    void setUp() {
        searchService = mock(SearchService.class);
        chatService = mock(OllamaChatService.class);
        askService = new AskService(searchService, chatService,
                mock(LookupService.class), mock(ProjectToolService.class),
                mock(CurrentUserService.class),
                mock(com.campusconnect.campus_ai.repository.DocumentRepository.class),
                0.3, 5, 0.15);
    }

    private static SearchService.SearchResult hit(String content) {
        return new SearchService.SearchResult(
                1L, 1L, "SAMPLE - Attendance Policy", content,
                1, "Eligibility", 0.9, 0.9, null);
    }

    /**
     * A number that only appears inside a longer number in the context must NOT
     * count as grounded. Before the fix, "5" was considered present in a context
     * containing "75", so this swapped-value answer passed the gate.
     */
    @Test
    void numberMatching_isWholeToken_notSubstring() {
        List<SearchService.SearchResult> results =
                List.of(hit("A student needs 75 percent attendance to be eligible."));

        // "5" is a substring of "75" but is not a number stated in the context.
        assertFalse(askService.isFaithful("The requirement is 5 percent attendance.", null, results),
                "5 must not be satisfied by the context's 75");
    }

    /** The genuine value still passes. */
    @Test
    void numberMatching_acceptsTheRealValue() {
        List<SearchService.SearchResult> results =
                List.of(hit("A student needs 75 percent attendance to be eligible."));

        assertTrue(askService.isFaithful("A student needs 75 percent attendance to be eligible.", null, results));
    }

    /** A fabricated year must not be grounded by a different year in context. */
    @Test
    void numberMatching_rejectsFabricatedYear() {
        List<SearchService.SearchResult> results =
                List.of(hit("Team size is 2 to 4 students per hackathon team."));

        assertFalse(askService.isFaithful("Registration closes in 2021.", null, results),
                "2021 must not be grounded by an unrelated 2 or 4");
    }

    /**
     * A decimal must be matched as one token ("3.5"), not split into "3" and "5",
     * so a context containing 3.5 does not accidentally ground a bare "3".
     */
    @Test
    void numberMatching_decimalsStayWhole() {
        List<SearchService.SearchResult> results =
                List.of(hit("A minimum CGPA of 3.5 is required for the placement drive."));

        assertTrue(askService.isFaithful("A minimum CGPA of 3.5 is required.", null, results));
    }

    /**
     * Term overlap must be whole-token: "art" must not be treated as present
     * because the context contains "start".
     */
    @Test
    void termOverlap_isWholeToken_notSubstring() {
        // Context talks about when a process starts; it says nothing about "art".
        List<SearchService.SearchResult> results =
                List.of(hit("Registration starts in the second week of March and closes soon after."));

        // An answer that is essentially fabricated, sharing only the word "starts".
        // With substring matching, "art" matched inside "starts"; with token
        // matching it does not, so overlap is too low to accept.
        assertFalse(askService.isFaithful("Art submissions open in April.", null, results),
                "a term must not match inside an unrelated longer word");
    }

    /** Plain substring false-positive: "act" vs "contact". */
    @Test
    void termOverlap_doesNotMatchAcrossWordBoundaries() {
        List<SearchService.SearchResult> results =
                List.of(hit("For any queries contact the dean office during working hours."));

        // "act" is inside "contact" but is not a context word.
        assertFalse(askService.isFaithful("You must act quickly to register.", null, results));
    }

    /** A clean, fully-supported answer is still accepted. */
    @Test
    void wellGroundedAnswer_isAccepted() {
        List<SearchService.SearchResult> results =
                List.of(hit("Plagiarised or pre-built hackathon projects lead to disqualification."));

        assertTrue(askService.isFaithful(
                "Plagiarised or pre-built hackathon projects lead to disqualification.", null, results));
    }

    /**
     * Morphology must not cost recall. Whole-token equality alone is stricter
     * than the substring check it replaced in a harmful direction: a faithfully
     * grounded answer that inflects a word differently from the source — the
     * model writes "students" where the document says "student" — would be
     * flagged ungrounded and suppressed. Tokens are stemmed so this passes.
     */
    @Test
    void termOverlap_matchesAcrossSimplePlurals() {
        List<SearchService.SearchResult> results =
                List.of(hit("A student must maintain 75 percent attendance to sit for the exam."));

        assertTrue(askService.isFaithful(
                "Students must maintain 75 percent attendance to sit for exams.", null, results),
                "plural/singular variants of the same word must still match");
    }

    /** The "ies" → "y" stem is shared by both sides of the check. */
    @Test
    void termOverlap_matchesIesPlural() {
        List<SearchService.SearchResult> results =
                List.of(hit("The hostels follow a strict code of conduct for all residents."));

        assertTrue(askService.isFaithful(
                "Hostel policies govern conduct for residents.", null, results));
    }

    /**
     * Stemming merges inflectional variants of a word — it must not degrade into
     * prefix matching. "registered" and "registration" share a long prefix but are
     * different words, and the coarse stemmer deliberately leaves both alone; an
     * answer resting on one must not be grounded by the other.
     */
    @Test
    void stemming_doesNotMergeAcrossDerivations() {
        List<SearchService.SearchResult> results =
                List.of(hit("Registration opens on Monday."));

        assertFalse(askService.isFaithful("Registered students must reapply.", null, results),
                "a shared prefix is not a shared term");
    }

    /** The explicit abstention strings are always considered faithful. */
    @Test
    void abstentionStrings_areFaithful() {
        List<SearchService.SearchResult> results = List.of(hit("anything"));

        assertTrue(askService.isFaithful(
                "I don't have that information in the college documents.", null, results));
    }

    /** An empty hit list can never ground an answer. */
    @Test
    void emptyResults_neverFaithful() {
        assertFalse(askService.isFaithful("75 percent attendance.", null, List.of()));
    }
}
