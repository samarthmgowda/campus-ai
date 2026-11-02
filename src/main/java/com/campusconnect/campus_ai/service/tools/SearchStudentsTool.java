package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.service.ProjectToolService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for searching students by skill (standalone, not gap-driven).
 * Triggers: "who knows", "who has", "students with", "find student"
 */
@Component
public class SearchStudentsTool implements Tool {

    private final ProjectToolService projectToolService;

    public SearchStudentsTool(ProjectToolService projectToolService) {
        this.projectToolService = projectToolService;
    }

    @Override
    public String name() {
        return "searchStudents";
    }

    @Override
    public String[] triggers() {
        return new String[]{"who knows", "who has", "students with", "find student", "find students", "search student"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "Search for students by specific skill. Returns available students matching the skill.";
    }

    @Override
    public int priority() {
        return 5; // Lower than ProjectTeammateTool so teammate workflow takes precedence
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        // Try to extract skill from "who knows <skill>" or "students with <skill>"
        String skill = extractSkillFromIntent(intent);
        params.put("skill", skill);
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String skill = (String) params.get("skill");
        if (skill == null || skill.isBlank()) {
            return ToolResult.clarification(
                    "Which skill? Try: \"who knows React\" or \"students with Java\"");
        }

        var candidates = projectToolService.searchStudents(
                List.of(skill), true, Set.of(userId));
        if (candidates.isEmpty()) {
            return ToolResult.success(
                    "No available students found with '" + skill + "'.",
                    Map.of("skill", skill));
        }
        StringBuilder sb = new StringBuilder("Students with '" + skill + "':\n");
        for (var c : candidates) {
            sb.append("  - ").append(c.name()).append(" (").append(c.branch())
                    .append(", Y").append(c.year()).append(") - ")
                    .append(c.skills()).append(" | availability: ").append(c.availability()).append("\n");
        }
        sb.append("Send them a teammate request: POST /projects/{projectId}/teammate-request {candidateId}");
        return ToolResult.success(sb.toString(), Map.of());
    }

    private String extractSkillFromIntent(String intent) {
        String q = intent.toLowerCase(Locale.ROOT);
        String[] patterns = {"who knows ", "who has ", "students with ", "find student ", "find students ", "search student "};
        for (String pat : patterns) {
            int idx = q.indexOf(pat);
            if (idx >= 0) {
                String rest = q.substring(idx + pat.length()).trim();
                // Take first few words as skill
                String[] words = rest.split("\\s+");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(3, words.length); i++) {
                    if (i > 0) sb.append(" ");
                    sb.append(words[i]);
                }
                return sb.toString().trim();
            }
        }
        return null;
    }
}