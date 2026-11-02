package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.service.ProjectToolService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Tool for creating new projects.
 * Triggers: "create project", "new project", "create a project"
 */
@Component
public class CreateProjectTool implements Tool {

    private final ProjectToolService projectToolService;

    public CreateProjectTool(ProjectToolService projectToolService) {
        this.projectToolService = projectToolService;
    }

    @Override
    public String name() {
        return "createProject";
    }

    @Override
    public String[] triggers() {
        return new String[]{"create project", "new project", "create a project"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "Create a new project with name, optional description, domain, and required skills.";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();

        // Extract name after "called" or "named"
        String name = extractQuotedAfter(intent, "called");
        if (name == null) name = extractQuotedAfter(intent, "named");
        if (name == null) name = extractQuotedAfter(intent, "project");
        params.put("name", name);

        // Extract description
        String desc = extractQuotedAfter(intent, "for ");
        params.put("description", desc);

        // Extract domain
        String domain = extractAfterKeyword(intent, "domain");
        params.put("domain", domain);

        // Extract skills
        String skills = extractSkillsFromIntent(intent);
        params.put("requiredSkills", skills);

        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String name = (String) params.get("name");
        if (name == null || name.isBlank()) {
            return ToolResult.clarification(
                    "To create a project, I need a name. " +
                    "Tell me: \"create a project called <name> for <topic> needing <skills>\" " +
                    "or use POST /projects directly with {name, description, domain, requiredSkills}.");
        }

        String desc = (String) params.get("description");
        String domain = (String) params.get("domain");
        String skills = (String) params.get("requiredSkills");

        try {
            var result = projectToolService.createProject(userId, name, desc, domain, skills);
            return ToolResult.success(
                    "Project created: " + result.name() + " (#" + result.id() + ")",
                    Map.of("projectId", result.id(),
                            "name", result.name(),
                            "domain", result.domain(),
                            "requiredSkills", result.requiredSkills()));
        } catch (Exception e) {
            return ToolResult.error("Project creation failed", e.getMessage());
        }
    }

    // Helper methods (copied from AgentOrchestrator)
    private String extractQuotedAfter(String text, String keyword) {
        String lower = text.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(keyword);
        if (idx >= 0) {
            String rest = text.substring(idx + keyword.length()).trim();
            if (rest.startsWith("\"") || rest.startsWith("'")) {
                int end = rest.indexOf(rest.charAt(0), 1);
                if (end > 1) return rest.substring(1, end);
            }
            String[] words = rest.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (w.matches("^[A-Za-z]+$") && sb.length() > 0
                        && Set.of("for", "named", "called", "with", "needs", "needing", "about", "on", "domain", "skills", "skill", "project", "create", "new", "to", "the", "a", "an").contains(w.toLowerCase(Locale.ROOT))) {
                    break;
                }
                sb.append(w).append(" ");
                if (sb.length() > 50) break;
            }
            String result = sb.toString().trim();
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    private String extractAfterKeyword(String text, String keyword) {
        String lower = text.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(keyword);
        if (idx >= 0) {
            String rest = text.substring(idx + keyword.length()).trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(rest);
            if (m.find()) return m.group(1);
            String[] words = rest.split("\\s+");
            if (words.length >= 1) return words[0].replaceAll("[^a-zA-Z0-9]", "");
        }
        return null;
    }

    private String extractSkillsFromIntent(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("needing");
        if (idx < 0) idx = lower.indexOf("needs");
        if (idx < 0) idx = lower.indexOf("require");
        if (idx < 0) idx = lower.indexOf("with");
        if (idx >= 0) {
            String rest = text.substring(idx).trim();
            String clean = rest.replaceAll("(?i)(needing|needs|require|requires|with)[\\s:]+", "").trim();
            String[] words = clean.split("[, ]+");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                w = w.replaceAll("[^a-zA-Z0-9+.#]", "");
                if (w.length() >= 2) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(w);
                    if (sb.length() > 200) break;
                }
                if (sb.length() > 0 && Set.of("for", "project", "domain", "and", "the", "a", "on", "at").contains(w.toLowerCase(Locale.ROOT))) break;
            }
            return sb.isEmpty() ? null : sb.toString();
        }
        return null;
    }
}