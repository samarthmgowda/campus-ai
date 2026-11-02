package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.service.SkillGraphService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for skill graph queries: glossary, related skills, dashboard.
 * Triggers: "skill glossary", "glossary", "skill dashboard", "top skills", "what is <skill>", "related skills"
 */
@Component
public class SkillGraphTool implements Tool {

    private final SkillGraphService skillGraphService;

    public SkillGraphTool(SkillGraphService skillGraphService) {
        this.skillGraphService = skillGraphService;
    }

    @Override
    public String name() {
        return "skillGraph";
    }

    @Override
    public String[] triggers() {
        return new String[]{"glossary", "skill glossary", "skill dashboard", "top skills", "what is", "related skills", "skill info", "skill meaning"};
    }

    @Override
    public boolean requiresAuth() {
        return false; // Skill graph is public
    }

    @Override
    public String description() {
        return "Query the skill graph: get glossary entries, find related skills, see top skills on campus, and view skill dashboard.";
    }

    @Override
    public int priority() {
        return 10;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        if (q.contains("skill glossary") || q.contains("glossary") || q.contains("alias for") || q.contains("what is") && q.contains("skill")) {
            params.put("action", "glossary");
            // Try to extract skill name
            String skill = extractSkillFromQuestion(q);
            params.put("skill", skill);
        } else if (q.contains("skill dashboard") || q.contains("top skills")) {
            params.put("action", "dashboard");
        } else if (q.contains("related skills")) {
            params.put("action", "related");
            String skill = extractSkillFromQuestion(q);
            params.put("skill", skill);
        } else {
            params.put("action", "glossary");
            String skill = extractSkillFromQuestion(q);
            params.put("skill", skill);
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");
        String skill = (String) params.get("skill");

        if ("glossary".equals(action)) {
            if (skill == null || skill.isBlank()) {
                return ToolResult.clarification("Which skill? Try: \"what is React\" or \"skill glossary Java\"");
            }
            var g = skillGraphService.getGlossary(skill);
            StringBuilder sb = new StringBuilder();
            sb.append(g.get("canonical")).append(" [").append(g.get("category")).append("]");
            var aliases = (List<?>) g.get("aliases");
            if (aliases != null && !aliases.isEmpty()) {
                sb.append("\nAliases: ").append(String.join(", ", aliases.stream().map(Object::toString).toList()));
            }
            sb.append("\nProfiles: ").append(g.get("profileCount"))
                    .append(" (students ").append(g.get("studentCount"))
                    .append(", alumni ").append(g.get("alumniCount")).append(")");
            var related = (List<?>) g.get("related");
            if (related != null && !related.isEmpty()) {
                sb.append("\nRelated: ");
                for (int i = 0; i < Math.min(3, related.size()); i++) {
                    var m = (Map<?, ?>) related.get(i);
                    sb.append(m.get("skill")).append(" (").append(m.get("count")).append("), ");
                }
            }
            return ToolResult.success(sb.toString(), Map.of());
        }

        if ("dashboard".equals(action)) {
            var dash = skillGraphService.getDashboardData(userId);
            var topSkills = (List<?>) dash.get("topSkills");
            var toGrow = (List<?>) dash.get("skillsToGrow");
            var topRelated = (List<?>) dash.get("topRelated");
            var categoryDist = (Map<?, ?>) dash.get("categoryDistribution");
            StringBuilder sb = new StringBuilder("Top skills on campus:\n");
            for (int i = 0; i < Math.min(8, topSkills.size()); i++) {
                var entry = (Map<?, ?>) topSkills.get(i);
                sb.append("  - ").append(entry.get("skill")).append(" (").append(entry.get("count")).append(" profiles)\n");
            }
            if (userId != null && toGrow != null && !toGrow.isEmpty()) {
                sb.append("\nSkills to grow for you:");
                for (Object o : toGrow) {
                    var entry = (Map<?, ?>) o;
                    sb.append(" ").append(entry.get("skill")).append(",");
                }
                sb.setLength(sb.length() - 1);
                sb.append("\n");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("topSkills", topSkills);
            data.put("skillsToGrow", toGrow);
            data.put("topRelated", topRelated);
            data.put("categoryDistribution", categoryDist);
            data.put("totalSkills", dash.get("totalSkills"));
            data.put("totalProfiles", dash.get("totalProfiles"));
            return ToolResult.success(sb.toString(), data);
        }

        if ("related".equals(action)) {
            if (skill == null) {
                return ToolResult.clarification("Which skill? Try: \"related skills for React\"");
            }
            var related = skillGraphService.getRelatedSkills(skill, 10);
            if (related.isEmpty()) {
                return ToolResult.success("No related skills found for " + skill, Map.of());
            }
            StringBuilder sb = new StringBuilder("Skills related to " + skill + ":\n");
            for (var r : related) {
                sb.append("  - ").append(r.skillName()).append(" (").append(r.count()).append(" profiles)\n");
            }
            return ToolResult.success(sb.toString(), Map.of("related", related));
        }

        return ToolResult.error("Unknown skill graph action", "Action: " + action);
    }

    private String extractSkillFromQuestion(String q) {
        // Try to find a known skill in the question
        var canonical = skillGraphService.getAllCanonicalSkills();
        for (String s : canonical) {
            if (containsWholeWord(q, s.toLowerCase(Locale.ROOT))) return s;
        }
        // Fallback: last meaningful token
        String[] tokens = q.split("[\\s,]+");
        for (int i = tokens.length - 1; i >= 0; i--) {
            String tok = tokens[i].replaceAll("[^a-z0-9#+\\-.]", "");
            if (tok.length() >= 2 && !Set.of("skill", "glossary", "what", "does", "is", "the", "meaning", "info", "for", "alias", "related", "dashboard", "top", "skills").contains(tok)) {
                return tok;
            }
        }
        return null;
    }

    private boolean containsWholeWord(String haystack, String needle) {
        int idx = haystack.indexOf(needle);
        while (idx != -1) {
            boolean leftOk = idx == 0 || !Character.isLetterOrDigit(haystack.charAt(idx - 1));
            int end = idx + needle.length();
            boolean rightOk = end >= haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (leftOk && rightOk) return true;
            idx = haystack.indexOf(needle, idx + 1);
        }
        return false;
    }
}