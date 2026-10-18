package com.campusconnect.campus_ai.service;

import org.springframework.stereotype.Service;

/**
 * ChatOrchestrator wires together structured-tools (LookupService) and RAG (AskService)
 * behind a single chat entrypoint.  It is the backend for ChatController (/api/chat).
 *
 * Routing logic:
 *  - forceTool=true  → pure structured-tool path (LookupService.tryAnswer)
 *  - forceRag=true   → pure RAG path (AskService.ask)
 *  - default         → try tools first; if they decline, fall back to RAG
 */
@Service
public class ChatOrchestrator {

    private final LookupService lookupService;
    private final AskService askService;

    public ChatOrchestrator(LookupService lookupService, AskService askService) {
        this.lookupService = lookupService;
        this.askService = askService;
    }

    public String chat(Long userId, String question, boolean forceRag, boolean forceTool) {
        // Pure tool path
        if (forceTool) {
            return callTools(userId, question);
        }

        // Pure RAG path
        if (forceRag) {
            return callRag(userId, question);
        }

        // Default: tools first, RAG fallback
        String toolAnswer = callTools(userId, question);
        if (toolAnswer != null) {
            return toolAnswer;
        }
        return callRag(userId, question);
    }

    /**
     * Ask LookupService for a structured answer.
     * Returns null when no tool can answer (so the orchestrator can fall back to RAG).
     */
    private String callTools(Long userId, String question) {
        try {
            var opt = lookupService.tryAnswer(question, userId);
            if (opt.isPresent()) {
                var resp = opt.get();
                return resp.answered() ? resp.answer() : null;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Ask the RAG pipeline (AskService), returning only the answer string.
     */
    private String callRag(Long userId, String question) {
        try {
            var resp = askService.ask(question, userId);
            return resp != null ? resp.answer() : null;
        } catch (Exception e) {
            return "I encountered an error: " + e.getMessage();
        }
    }
}
