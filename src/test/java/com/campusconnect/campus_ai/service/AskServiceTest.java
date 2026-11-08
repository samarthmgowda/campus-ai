package com.campusconnect.campus_ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class AskServiceTest {

    private SearchService searchService;
    private OllamaChatService chatService;
    private LookupService lookupService;
    private ProjectToolService projectToolService;
    private CurrentUserService currentUserService;
    private com.campusconnect.campus_ai.repository.DocumentRepository documentRepository;

    private AskService askService;

    @BeforeEach
    void setUp() {
        searchService = mock(SearchService.class);
        chatService = mock(OllamaChatService.class);
        lookupService = mock(LookupService.class);
        projectToolService = mock(ProjectToolService.class);
        currentUserService = mock(CurrentUserService.class);
        documentRepository = mock(com.campusconnect.campus_ai.repository.DocumentRepository.class);

        askService = new AskService(searchService, chatService, lookupService,
                projectToolService, currentUserService, documentRepository, 0.3, 5, 0.15);
    }

    /**
     * When no documents match (results empty), the answer should be friendly
     * rather than the bare "I don't have that information in the college documents."
     */
    @Test
    void noResults_outOfDomainQuery_returnsFriendlyFallback() {
        when(searchService.search(any(), anyInt())).thenReturn(List.of());

        var resp = askService.ask("what are the trending technologies that i need to learn", null);

        assertFalse(resp.answered());
        String answer = resp.answer();
        // Should NOT be the cold abstention
        assertNotEquals("I don't have that information in the college documents.", answer);
        // Should be friendly and offer help
        assertTrue(answer.contains("trending-technology"),
                "should mention trending tech: " + answer);
        assertTrue(answer.contains("What would you like help with?"),
                "should offer help: " + answer);
    }

    /**
     * A short follow-up like "when does it start" (which references a prior question)
     * should not get a cold refusal — it should get a clarification prompt.
     */
    @Test
    void noResults_shortFollowUpQuery_returnsClarificationRequest() {
        when(searchService.search(any(), anyInt())).thenReturn(List.of());

        var resp = askService.ask("when does it start", null);

        assertFalse(resp.answered());
        String answer = resp.answer();
        assertTrue(answer.contains("more context") || answer.contains("rephrase"),
                "should ask for clarification: " + answer);
    }

    /**
     * Regression for the branch that bypassed the friendly fallback.
     *
     * When documents ARE retrieved but the model abstains, the answer used to be
     * returned as the bare "I don't have that information in the college documents."
     * — a dead end, since the user cannot tell "not in the corpus" from "never
     * uploaded". It must go through buildNoAnswerResponse like the empty-results path.
     */
    @Test
    void abstainWithRetrievedDocs_returnsFriendlyNoAnswerNotBareAbstain() {
        when(chatService.chat(any(), any())).thenReturn("I don't have that information in the college documents.");
        when(documentRepository.findAll()).thenReturn(List.of());

        // A real hit that passes the min-similarity gate, but which the model won't answer.
        var hit = new SearchService.SearchResult(
                1L, 1L, "SAMPLE - Placement Guidelines",
                "Students must register for each placement drive on the placement portal.",
                1, "Registration", 0.9, 0.9);

        var resp = askService.answerWithHits("when is the submission deadline",
                null, List.of(hit));

        assertFalse(resp.answered());
        assertNotEquals("I don't have that information in the college documents.", resp.answer(),
                "must not return the bare abstain when the friendly fallback exists");
        assertFalse(resp.answer().equals("I don't have that information in the college documents."),
                "bare abstain should be replaced");
    }

    /**
     * The same path must still name the indexed documents so the user can tell an
     * absent fact from an un-uploaded document.
     */
    @Test
    void abstainWithRetrievedDocs_namesIndexedDocuments() {
        when(chatService.chat(any(), any())).thenReturn("I don't have that information in the college documents.");

        var doc = new com.campusconnect.campus_ai.entity.Document("SAMPLE - Attendance Policy", "SAMPLE - Attendance Policy");
        when(documentRepository.findAll()).thenReturn(List.of(doc));

        var hit = new SearchService.SearchResult(
                1L, 1L, "SAMPLE - Attendance Policy",
                "Students must maintain at least 75 percent attendance in each subject.",
                1, "Eligibility", 0.9, 0.9);

        var resp = askService.answerWithHits("what is the hostel fee refund policy",
                null, List.of(hit));

        assertFalse(resp.answered());
        assertTrue(resp.answer().contains("SAMPLE - Attendance Policy"),
                "should list the documents it can see: " + resp.answer());
    }

    /**
     * An unanswerable query with no relevant enough hits must still reach the friendly
     * fallback rather than the bare abstain.
     */
    @Test
    void lowSimilarityHit_returnsFriendlyNoAnswer() {
        when(documentRepository.findAll()).thenReturn(List.of());

        // similarity below rag.min-similarity (0.3 in this fixture) → refuses without an LLM call.
        var weak = new SearchService.SearchResult(
                1L, 1L, "SAMPLE - Technical Fest Information",
                "Registration for the technical fest closes one week before the event begins.",
                1, "Registration", 0.05, 0.05);

        var resp = askService.answerWithHits("when is the submission deadline",
                null, List.of(weak));

        assertFalse(resp.answered());
        assertNotEquals("I don't have that information in the college documents.", resp.answer());
        verify(chatService, never()).chat(any(), any());
    }

    /**
     * "how many" with a short deictic query should also get the clarification prompt.
     */
    @Test
    void noResults_shortHowManyQuery_returnsClarificationRequest() {
        when(searchService.search(any(), anyInt())).thenReturn(List.of());

        var resp = askService.ask("how many", null);

        assertFalse(resp.answered());
        String answer = resp.answer();
        assertTrue(answer.contains("more context") || answer.contains("rephrase"),
                "should ask for clarification: " + answer);
    }

    /**
     * When documents DO exist but are too low-similarity, we still fall back
     * gracefully (no LLM call, since the gate blocks it).
     */
    @Test
    void lowSimilarityResults_doesNotCallChatService() {
        var lowHit = new SearchService.SearchResult(
                1L, 1L, "Doc", "some content", 1, "sec", 0.05, 0.05);
        when(searchService.search(any(), anyInt())).thenReturn(List.of(lowHit));

        var resp = askService.ask("campus event date", null);

        assertFalse(resp.answered());
        // LLM should NOT be called when the gate blocks (no results above threshold)
        verifyNoInteractions(chatService);
    }

    /**
     * When results are above threshold, the LLM IS called for generation.
     */
    @Test
    void goodSimilarityResults_callsChatService() {
        var goodHit = new SearchService.SearchResult(
                1L, 1L, "Doc", "There are 2 to 4 students per team.", 1, "sec", 0.8, 0.8);
        when(searchService.search(any(), anyInt())).thenReturn(List.of(goodHit));
        when(chatService.chat(any(), any())).thenReturn("2 to 4 students per team [1].");

        var resp = askService.ask("hackathon team size", null);

        verify(chatService, atLeastOnce()).chat(any(), any());
    }

    /**
     * The retry is a SECOND generation: abstain first, then regenerate because the
     * context still holds the question's terms. The eval reports it, so the response
     * must say whether it happened — a case that retried cost two round-trips and its
     * latency is not comparable to one that did not.
     */
    @Test
    void retryAfterAbstain_flagsRetried() {
        // Content deliberately carries the question's own terms ("attendance", "exams")
        // so the contextContainsRelevantTerms gate — 50% stemmed overlap — passes and
        // the retry actually fires.
        var hit = new SearchService.SearchResult(
                1L, 1L, "SAMPLE - Attendance Policy",
                "Students must maintain at least 75 percent attendance in each subject. Exams require attendance records.",
                1, "Eligibility", 0.9, 0.9);
        // First call abstains; the retry returns the grounded value.
        when(chatService.chat(any(), any()))
                .thenReturn("I don't have that information in the college documents.")
                .thenReturn("At least 75 percent attendance is required for exams [1].");

        var resp = askService.answerWithHits(
                "how much attendance is needed for exams", null, List.of(hit));

        assertTrue(resp.retried(), "a second generation ran, so retried must be true");
        assertTrue(resp.answered(), "the grounded retry answer should be accepted");
        verify(chatService, times(2)).chat(any(), any());
    }

    /**
     * A normal single-shot answer must NOT be flagged as retried, or the eval's retry
     * counter would report every call.
     */
    @Test
    void firstShotAnswer_isNotFlaggedRetried() {
        var hit = new SearchService.SearchResult(
                1L, 1L, "SAMPLE - Attendance Policy",
                "Students must maintain at least 75 percent attendance in each subject.",
                1, "Eligibility", 0.9, 0.9);
        when(chatService.chat(any(), any()))
                .thenReturn("At least 75 percent attendance is required [1].");

        var resp = askService.answerWithHits(
                "how much attendance is needed for exams", null, List.of(hit));

        assertTrue(resp.answered());
        assertFalse(resp.retried(), "no retry happened, so the flag must stay false");
        verify(chatService, times(1)).chat(any(), any());
    }

    // --- Short no-op / acknowledgement guard ---

    private static final String ACK_REPLY =
            "How can I help you? Questions about courses, placements, events, or alumni are welcome.";

    @Test
    void pureAcknowledgement_isShortCircuitedWithoutRag() {
        for (String msg : List.of("ok", "Okay", "thanks", "thanks!", "got it", "ok, thanks", "sure", "yes")) {
            var resp = askService.ask(msg, null);
            assertEquals(ACK_REPLY, resp.answer(), "should short-circuit: " + msg);
            assertTrue(resp.answered());
        }
        // No RAG pass should have been attempted for any of them.
        verify(searchService, never()).search(any(), anyInt());
    }

    @Test
    void questionStartingWithAckWord_isNotSwallowed() {
        // Regression: a bare prefix match on "ok " / "sure " used to swallow real short questions.
        when(searchService.search(any(), anyInt())).thenReturn(List.of());
        for (String msg : List.of("ok and the date?", "sure, when is it?", "ok when", "great — how many can join?")) {
            var resp = askService.ask(msg, null);
            assertNotEquals(ACK_REPLY, resp.answer(), "must NOT be treated as a no-op: " + msg);
        }
        verify(searchService, atLeastOnce()).search(any(), anyInt());
    }

    @Test
    void greetingIsNotTreatedAsAcknowledgement() {
        when(searchService.search(any(), anyInt())).thenReturn(List.of());
        var resp = askService.ask("hello", null);
        assertNotEquals(ACK_REPLY, resp.answer());
    }
}
