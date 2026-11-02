package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.AskService;
import com.campusconnect.campus_ai.service.ChatHistoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AskController.class)
class AskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AskService askService;

    @MockitoBean
    private ChatHistoryService chatHistoryService;

    /**
     * Happy path: AskService returns a normal answered response.
     */
    @Test
    void askReturnsAnsweredResponse() throws Exception {
        AskService.AskResponse resp = new AskService.AskResponse(
                true, "There are 2 to 4 students.", List.of());
        when(askService.ask(any(), anyLong())).thenReturn(resp);

        mockMvc.perform(get("/ask")
                        .param("q", "hackathon team size")
                        .header("X-Demo-User", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(true))
                .andExpect(jsonPath("$.answer").value("There are 2 to 4 students."))
                .andExpect(jsonPath("$.sources").isArray());

        verify(chatHistoryService).save(1L, "hackathon team size",
                "There are 2 to 4 students.", true);
    }

    /**
     * A4 regression: when Ollama is down, AskService.ask() throws
     * IllegalStateException. The controller should NOT bubble this up as a 500.
     * Instead it returns a graceful 200 with answered=false so the
     * PENDING_TESTS.md expectation ("still 200 via Lookup fallback") holds
     * even for RAG-fallback queries.
     */
    @Test
    void askWhenOllamaDownReturnsGraceful200() throws Exception {
        when(askService.ask(any(), anyLong()))
                .thenThrow(new IllegalStateException("Could not reach Ollama"));

        mockMvc.perform(get("/ask")
                        .param("q", "tell me a joke")
                        .header("X-Demo-User", "1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(false))
                .andExpect(jsonPath("$.answer").value("RAG unavailable"))
                .andExpect(jsonPath("$.sources").isArray())
                .andExpect(jsonPath("$.sources").isEmpty());

        // Chat history should still be persisted with the error response.
        verify(chatHistoryService).save(1L, "tell me a joke", "RAG unavailable", false);
    }

    /**
     * When no X-Demo-User header is present, userId is null and
     * ChatHistoryService.save is never called.
     */
    @Test
    void askWithoutUserHeaderDoesNotPersistHistory() throws Exception {
        AskService.AskResponse resp = new AskService.AskResponse(
                false, "I don't have that information in the college documents.", List.of());
        when(askService.ask(any(), isNull())).thenReturn(resp);

        mockMvc.perform(get("/ask")
                        .param("q", "tell me a joke")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(false));

        verify(chatHistoryService, never()).save(anyLong(), any(), any(), anyBoolean());
    }

    /**
     * Malformed X-Demo-User header should be treated as "no user" — not a crash.
     */
    @Test
    void askWithMalformedUserHeaderTreatsAsAnonymous() throws Exception {
        when(askService.ask(any(), isNull())).thenReturn(
                new AskService.AskResponse(false, "abstain", List.of()));

        mockMvc.perform(get("/ask")
                        .param("q", "hello")
                        .header("X-Demo-User", "not-a-number")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(chatHistoryService, never()).save(anyLong(), any(), any(), anyBoolean());
    }

    /**
     * Composite "role:id" header (e.g. "student:1") resolves to the same user id
     * as the bare number — chat history stays scoped to the acting user.
     */
    @Test
    void askWithCompositeRoleHeaderResolvesUserId() throws Exception {
        AskService.AskResponse resp = new AskService.AskResponse(
                true, "Answers are grounded.", List.of());
        when(askService.ask(any(), anyLong())).thenReturn(resp);

        mockMvc.perform(get("/ask")
                        .param("q", "hackathon team size")
                        .header("X-Demo-User", "student:1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(true));

        verify(chatHistoryService).save(1L, "hackathon team size", "Answers are grounded.", true);
    }

    /**
     * An unknown role in the header is not a valid identity — treated as anonymous
     * rather than silently resolving against the wrong table.
     */
    @Test
    void askWithUnknownRoleHeaderTreatsAsAnonymous() throws Exception {
        when(askService.ask(any(), isNull())).thenReturn(
                new AskService.AskResponse(false, "abstain", List.of()));

        mockMvc.perform(get("/ask")
                        .param("q", "hello")
                        .header("X-Demo-User", "professor:1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(chatHistoryService, never()).save(anyLong(), any(), any(), anyBoolean());
    }
}
