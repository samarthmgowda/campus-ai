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
}
