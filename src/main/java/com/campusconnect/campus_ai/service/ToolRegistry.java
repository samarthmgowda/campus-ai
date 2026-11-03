package com.campusconnect.campus_ai.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Dynamic tool registry for the agent orchestrator.
 * Tools are registered at startup and dispatched by intent matching.
 * This replaces the hardcoded chain of tryX() methods in AgentOrchestrator.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final List<Tool> tools = new ArrayList<>();
    private final Map<String, Tool> toolsByName = new LinkedHashMap<>();

    /**
     * Register a tool. Called at startup (e.g., via @PostConstruct in tool beans).
     */
    public void register(Tool tool) {
        tools.add(tool);
        toolsByName.put(tool.name(), tool);
        // Re-sort by priority (higher first)
        tools.sort(Comparator.comparingInt(Tool::priority).reversed());
    }

    /**
     * Get a tool by name (for explicit LLM planning).
     */
    public Optional<Tool> getTool(String name) {
        return Optional.ofNullable(toolsByName.get(name));
    }

    /**
     * Get all registered tools.
     */
    public List<Tool> getAllTools() {
        return List.copyOf(tools);
    }

    /**
     * Find tools whose triggers match the given intent.
     * Case-insensitive, left-word-boundary-aware match on any trigger keyword.
     * Returns tools sorted by priority (highest first).
     *
     * Requiring a word boundary on the *left* of the trigger prevents mid-word
     * false positives (e.g. the trigger "read" matching inside "already", or
     * "alert" matching inside "inertalert") while still allowing plural/inflected
     * forms to match on the right (e.g. "my project" matching "my projects").
     */
    public List<Tool> findMatchingTools(String intent) {
        if (intent == null || intent.isBlank()) return List.of();
        String lower = intent.toLowerCase(Locale.ROOT);
        String normalized = normalizeTypos(lower);
        return tools.stream()
                .filter(t -> Arrays.stream(t.triggers())
                        .anyMatch(trigger -> {
                            String trg = trigger.toLowerCase(Locale.ROOT);
                            return containsTrigger(lower, trg) || containsTrigger(normalized, trg);
                        }))
                .collect(Collectors.toList());
    }

    /**
     * Repairs a handful of common typos/misspellings so trigger matching is
     * resilient to user error (e.g. "teamates" → "teammates", "hacathon" →
     * "hackathon", "attendence" → "attendance"). Deliberately small and explicit
     * rather than a fuzzy-edit-distance matcher, to avoid false-positive matches.
     */
    private static String normalizeTypos(String s) {
        String out = s;
        for (String[] pair : TYPO_FIXES) {
            out = out.replace(pair[0], pair[1]);
        }
        return out;
    }

    private static final String[][] TYPO_FIXES = {
            {"teamates", "teammates"},
            {"teamate", "teammate"},
            {"teemmate", "teammate"},
            {"hacathon", "hackathon"},
            {"hackaton", "hackathon"},
            {"attendence", "attendance"},
            {"atendance", "attendance"},
            {"mentore", "mentor"},
            {"notifcation", "notification"},
            {"notfication", "notification"},
    };

    /**
     * True if {@code trigger} appears in {@code haystack} starting at a word
     * boundary (start of string or preceded by a non-word character). The right
     * side is unconstrained so plural/inflected forms still match. Multi-word
     * triggers ("upcoming events") match as a phrase.
     */
    private static boolean containsTrigger(String haystack, String trigger) {
        if (trigger.isEmpty()) return false;
        int idx = haystack.indexOf(trigger);
        while (idx != -1) {
            if (idx == 0 || !isWordChar(haystack.charAt(idx - 1))) return true;
            idx = haystack.indexOf(trigger, idx + 1);
        }
        return false;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    /**
     * Execute the best matching tool for the intent.
     * Tries tools in priority order, returns first successful result.
     * If no tool matches or all fail, returns empty.
     */
    public Optional<ToolResult> dispatch(String intent, Long userId) {
        List<Tool> matches = findMatchingTools(intent);
        for (Tool tool : matches) {
            if (tool.requiresAuth() && userId == null) {
                return Optional.of(ToolResult.error(
                        "Authentication required",
                        "This action requires login. Please provide X-Demo-User header."));
            }
            try {
                Map<String, Object> params = tool.extractParams(intent);
                ToolResult result = tool.execute(params, userId);
                if (result.success()) {
                    return Optional.of(result);
                }
                // If tool returns clarification, propagate it
                if (result.isClarification()) {
                    return Optional.of(result);
                }
                // If tool fails but isn't a clarification, continue to next tool
            } catch (Exception e) {
                // A tool throwing must not silently disappear. System.err was a placeholder;
                // log via SLF4J and return an error ToolResult so the failure surfaces to
                // the user instead of looking like "no tool matched".
                log.warn("Tool '{}' failed: {}", tool.name(), e.getMessage(), e);
                return Optional.of(ToolResult.error(
                        "Tool " + tool.name() + " failed",
                        e.getClass().getSimpleName() + ": " + e.getMessage()));
            }
        }
        return Optional.empty();
    }

    /**
     * Get tool specs for prompt injection / documentation.
     */
    public List<ToolSpec> allToolSpecs() {
        return tools.stream()
                .map(t -> new ToolSpec(
                        t.name(),
                        t.description(),
                        extractInputSchema(t),
                        "Map<String, Object>", // output type
                        t.requiresAuth() ? "Requires auth (X-Demo-User)" : "Public",
                        t.triggers().length > 0 ? "Triggers: " + String.join(", ", t.triggers()) : "Explicit LLM planning"
                ))
                .collect(Collectors.toList());
    }

    private Map<String, String> extractInputSchema(Tool tool) {
        // This is a simplified version — in a full implementation,
        // tools would declare their input schema explicitly
        return Map.of("params", "JSON object with tool-specific fields");
    }

    public record ToolSpec(
            String name,
            String purpose,
            Map<String, String> inputs,
            String outputType,
            String permissions,
            String whenToUse
    ) {}
}