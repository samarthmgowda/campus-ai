package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.AgentOrchestrator;
import com.campusconnect.campus_ai.service.AgentOrchestrator.AgentResponse;
import com.campusconnect.campus_ai.service.ChatHistoryService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST endpoint for the Phase 3 AI-Agent Execution Loop.
 *
 * POST /api/agent  { question, userId?, forceRag?, forceTool? }
 *
 * Delegates to AgentOrchestrator.processIntent() which classifies the intent,
 * executes controlled backend tools (no LLM-to-DB), and falls back to RAG.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentOrchestrator orchestrator;
    private final ChatHistoryService chatHistoryService;

    public AgentController(AgentOrchestrator orchestrator, ChatHistoryService chatHistoryService) {
        this.orchestrator = orchestrator;
        this.chatHistoryService = chatHistoryService;
    }

    @PostMapping
    public AgentResponse agent(
            @RequestHeader(value = "X-Demo-User", required = false) String header,
            @RequestBody Map<String, Object> body) {
        Long userId = null;
        if (header != null) {
            try { userId = Long.valueOf(header.trim()); } catch (NumberFormatException ignored) {}
        }

        String intent = body != null && body.get("question") instanceof String s ? s : null;
        if (intent == null || intent.isBlank()) {
            return AgentResponse.clarification("Could you rephrase your request?");
        }

        AgentResponse resp = orchestrator.processIntent(intent, userId);

        // Persist chat turn so it survives page refresh
        boolean answered = "success".equals(resp.type()) || "info".equals(resp.type());
        chatHistoryService.save(userId, intent, resp.text(), answered);

        return resp;
    }
}
