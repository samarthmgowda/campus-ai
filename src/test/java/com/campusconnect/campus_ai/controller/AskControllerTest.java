package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.AskService;
import com.campusconnect.campus_ai.service.ChatHistoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * The acting user for /ask comes from the JWT principal (set here with
 * {@link TestAuth}), never a client header. Anonymous callers are allowed —
 * they simply get no personalisation and no chat-history persistence.
 */
@WebMvcTest(AskController.class)
class AskControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AskService askService;

    @MockitoBean
    private ChatHistoryService chatHistoryService;

    // The JWT filter is auto-registered in the web slice; it needs a JwtService
    // even though these tests set the principal directly via TestAuth.
    @MockitoBean
    private com.campusconnect.campus_ai.service.JwtService jwtService;

    @AfterEach
    void clearCaller() {
        TestAuth.clear();
    }

    /**
     * Happy path: AskService returns a normal answered response.
     */
    @Test
    void askReturnsAnsweredResponse() throws Exception {
        TestAuth.asStudent(1L);
        AskService.AskResponse resp = new AskService.AskResponse(
                true, "There are 2 to 4 students.", java.util.List.of());
        when(askService.ask(any(), anyLong())).thenReturn(resp);

        mockMvc.perform(get("/ask")
                        .param("q", "hackathon team size")
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
        TestAuth.asStudent(1L);
        when(askService.ask(any(), anyLong()))
                .thenThrow(new IllegalStateException("Could not reach Ollama"));

        mockMvc.perform(get("/ask")
                        .param("q", "tell me a joke")
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
     * When the request is anonymous, userId is null and ChatHistoryService.save
     * is never called.
     */
    @Test
    void askWithoutCallerDoesNotPersistHistory() throws Exception {
        AskService.AskResponse resp = new AskService.AskResponse(
                false, "I don't have that information in the college documents.", java.util.List.of());
        when(askService.ask(any(), isNull())).thenReturn(resp);

        mockMvc.perform(get("/ask")
                        .param("q", "tell me a joke")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answered").value(false));

        verify(chatHistoryService, never()).save(anyLong(), any(), any(), anyBoolean());
    }
}
