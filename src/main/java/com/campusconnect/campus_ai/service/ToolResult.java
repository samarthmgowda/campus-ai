package com.campusconnect.campus_ai.service;

import java.util.List;
import java.util.Map;

/**
 * Result of a tool execution.
 * Encapsulates success/error state, human-readable text, and optional structured data.
 */
public record ToolResult(
        boolean success,
        String text,
        Map<String, Object> data,
        String errorDetail,
        List<String> suggestions,
        List<EntityCard> cards
) {
    public static ToolResult success(String text, Map<String, Object> data) {
        return new ToolResult(true, text, data, null, null, null);
    }

    public static ToolResult successWithContext(String text, Map<String, Object> data, List<String> suggestions) {
        return new ToolResult(true, text, data, null, suggestions, null);
    }

    public static ToolResult successWithCards(String text, Map<String, Object> data, List<EntityCard> cards) {
        return new ToolResult(true, text, data, null, null, cards);
    }

    public static ToolResult error(String text, String detail) {
        return new ToolResult(false, text, Map.of(), detail, null, null);
    }

    public static ToolResult clarification(String text) {
        return new ToolResult(false, text, Map.of(), "clarification", null, null);
    }

    public boolean isClarification() {
        return "clarification".equals(errorDetail);
    }

    public record EntityCard(
            String type,      // "student", "event", "project", "document", "roadmap", "alumni"
            Long id,
            String title,
            String subtitle,
            String meta,
            String href
    ) {}
}
