package com.campusconnect.campus_ai.service;

import java.util.Map;
import java.util.Set;

/**
 * Interface for agent tools. Each tool encapsulates:
 * - An intent pattern (keyword-based matching)
 * - Execution logic that maps to backend services
 * - Required permissions/auth
 * - Response formatting
 * <p>
 * Tools are registered in ToolRegistry and dispatched by AgentOrchestrator.
 * This replaces the hardcoded tryX() methods with a pluggable architecture.
 */
public interface Tool {

    /**
     * Unique tool name (e.g., "createProject", "searchStudents").
     */
    String name();

    /**
     * Keywords/patterns that indicate this tool should be invoked.
     * Matching is case-insensitive substring search on the user's intent.
     * Return empty array if tool should only be invoked via explicit LLM planning.
     */
    String[] triggers();

    /**
     * Whether this tool requires an authenticated user.
     */
    boolean requiresAuth();

    /**
     * Which roles are allowed to use this tool.
     * Return empty set to allow all authenticated users.
     */
    default Set<String> allowedRoles() {
        return Set.of();
    }

    /**
     * Execute the tool with the given parameters.
     * @param params extracted from the user's intent (e.g., project name, skill, etc.)
     * @param userId authenticated user ID (null if requiresAuth() == false)
     * @return structured result map
     */
    ToolResult execute(Map<String, Object> params, Long userId);

    /**
     * Extract parameters from natural language intent.
     * Default implementation returns empty map (subclasses override).
     */
    default Map<String, Object> extractParams(String intent) {
        return Map.of();
    }

    /**
     * Human-readable description for help/tool listing.
     */
    String description();

    /**
     * Priority for intent matching (higher checked first).
     * Default 0. Tools with specific triggers should have higher priority
     * than generic fallback tools.
     */
    default int priority() {
        return 0;
    }
}
