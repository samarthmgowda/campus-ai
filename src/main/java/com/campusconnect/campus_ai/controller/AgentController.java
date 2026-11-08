package com.campusconnect.campus_ai.controller;

import com.campusconnect.campus_ai.service.AgentOrchestrator;
import com.campusconnect.campus_ai.service.AgentOrchestrator.AgentResponse;
import com.campusconnect.campus_ai.service.AuthContext;
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
 *
 * The endpoint is public: {@code userId} comes from an optional JWT, so an
 * anonymous caller can still ask — it simply gets no personalised tools and no
 * chat-history persistence.
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
    public AgentResponse agent(@RequestBody Map<String, Object> body) {
        Long userId = AuthContext.idOrNull();

        String intent = body != null && body.get("question") instanceof String s ? s : null;
        if (intent == null || intent.isBlank()) {
            return AgentResponse.clarification("Could you rephrase your request?");
        }

        // Optional thread id. Absent → the default conversation, so existing
        // single-thread callers keep working unchanged.
        String conversationId = body != null && body.get("conversationId") instanceof String c ? c : null;

        AgentResponse resp = orchestrator.processIntent(intent, userId);

        // Persist chat turn so it survives page refresh — cards included, so the
        // structured profile/event cards survive a reload and a history replay.
        boolean answered = "success".equals(resp.type()) || "info".equals(resp.type());
        chatHistoryService.save(userId, intent, resp.text(), answered, resp.cards(), conversationId);

        return resp;
    }
}
