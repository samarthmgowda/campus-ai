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
        List<String> suggestions
) {
    public static ToolResult success(String text, Map<String, Object> data) {
        return new ToolResult(true, text, data, null, null);
    }

    public static ToolResult successWithContext(String text, Map<String, Object> data, List<String> suggestions) {
        return new ToolResult(true, text, data, null, suggestions);
    }

    public static ToolResult error(String text, String detail) {
        return new ToolResult(false, text, Map.of(), detail, null);
    }

    public static ToolResult clarification(String text) {
        return new ToolResult(false, text, Map.of(), "clarification", null);
    }

    public boolean isClarification() {
        return "clarification".equals(errorDetail);
    }
}