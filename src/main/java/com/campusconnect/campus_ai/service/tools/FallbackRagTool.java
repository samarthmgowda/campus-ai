package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.service.AskService;
import com.campusconnect.campus_ai.service.ChatHistoryService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Fallback tool that delegates to AskService for RAG-based question answering.
 * This is the catch-all tool with lowest priority.
 */
@Component
public class FallbackRagTool implements Tool {

    private final AskService askService;
    private final ChatHistoryService chatHistoryService;

    public FallbackRagTool(AskService askService, ChatHistoryService chatHistoryService) {
        this.askService = askService;
        this.chatHistoryService = chatHistoryService;
    }

    @Override
    public String name() {
        return "ragFallback";
    }

    @Override
    public String[] triggers() {
        // Empty triggers = only invoked when no other tool matches
        return new String[]{};
    }

    @Override
    public boolean requiresAuth() {
        return false; // RAG works for unauthenticated users too
    }

    @Override
    public String description() {
        return "Fallback: search college documents and answer questions using RAG (Retrieval-Augmented Generation).";
    }

    @Override
    public int priority() {
        return -100; // Lowest priority - only runs if no other tool matches
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        // The intent is passed in params
        String rawIntent = (String) params.get("rawIntent");
        List<String> contextRefs = (List<String>) params.get("contextRefs");

        if (rawIntent == null) {
            return ToolResult.error("No question provided", "rawIntent parameter missing");
        }

        try {
            var answer = askService.ask(rawIntent, userId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sources", answer.sources());

            if (answer.answered()) {
                String text = answer.answer();
                // Prepend context reference if available
                if (contextRefs != null && !contextRefs.isEmpty()
                        && answer.sources() != null && !answer.sources().isEmpty()) {
                    text = maybePrependContext(text, contextRefs);
                }
                return ToolResult.success(text, data);
            }
            return ToolResult.success(answer.answer(), Map.of(
                    "sources", answer.sources(),
                    "hint", "Try GET /ask?q=... for a direct RAG answer."));
        } catch (IllegalStateException e) {
            return ToolResult.error("RAG unavailable", e.getMessage());
        } catch (Exception e) {
            return ToolResult.error("Search failed", e.getMessage());
        }
    }

    private String maybePrependContext(String answer, List<String> contextRefs) {
        String last = contextRefs.getFirst();
        if (last == null || last.isBlank()) return answer;
        if (answer.toLowerCase(Locale.ROOT).contains(last.toLowerCase(Locale.ROOT))) {
            return answer;
        }
        return "Based on our conversation — you asked about \"" + truncate(last, 60)
                + "\" — " + answer;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s == null ? "" : s;
        return s.substring(0, max) + "...";
    }
}