package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.StudentRepository;
import com.campusconnect.campus_ai.service.ProfileService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for user profile workflows: view profile, edit profile, completeness, skill recommendations.
 * Triggers: "profile", "my profile", "edit profile", "skill recommendation"
 */
@Component
public class ProfileTool implements Tool {

    private final ProfileService profileService;
    private final StudentRepository studentRepository;

    public ProfileTool(ProfileService profileService, StudentRepository studentRepository) {
        this.profileService = profileService;
        this.studentRepository = studentRepository;
    }

    @Override
    public String name() {
        return "profileManager";
    }

    @Override
    public String[] triggers() {
        return new String[]{"profile", "my profile", "edit profile", "complete", "completeness", "skill recommendation", "recommendation", "public profile"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "View and manage your profile, check profile completeness, get skill recommendations, and view public profiles.";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        if (q.contains("completeness") || q.equals("complete") || q.contains("complete ")) {
            params.put("action", "completeness");
        } else if (q.contains("recommendation") || q.contains("skill rec")) {
            params.put("action", "skillRecommendations");
        } else if (q.contains("edit") || q.contains("update") || q.contains("change") || q.contains("set ")) {
            params.put("action", "edit");
            String field = extractField(q);
            if (field != null && !EDITABLE_FIELDS.contains(field)) {
                field = null; // unknown field — will trigger clarification
            }
            params.put("field", field);
            String value = extractValueAfterField(q, field);
            params.put("value", value);
        } else if (q.contains("public")) {
            params.put("action", "public");
            Long userId = extractNumberAfter(intent, "id ");
            params.put("userId", userId);
        } else {
            params.put("action", "me");
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");

        if ("me".equals(action)) {
            var me = profileService.getMe(userId);
            Object profile = me.get("profile");
            Object connections = me.get("connections");
            Object projects = me.get("projects");
            Object unread = me.get("unreadNotifications");
            Object unreadMsg = me.get("unreadMessages");

            return ToolResult.success(
                    "Profile loaded. Connections: " + connections + ". Projects: " + projects +
                    ". Unread notifications: " + unread + ". Unread messages: " + unreadMsg,
                    me);
        }

        if ("completeness".equals(action)) {
            var result = profileService.getCompleteness(userId);
            int score = (Integer) result.get("score");
            String level = (String) result.get("level");
            List<String> missing = (List<String>) result.get("missingFields");

            StringBuilder sb = new StringBuilder();
            sb.append("Profile is ").append(level).append(" (").append(score).append("/100)\n");
            if (missing != null && !missing.isEmpty()) {
                sb.append("Missing: ").append(String.join(", ", missing)).append("\n");
                sb.append("Complete it: PUT /users/me with the missing fields");
            } else {
                sb.append("Your profile is complete!");
            }
            return ToolResult.success(sb.toString(), result);
        }

        if ("skillRecommendations".equals(action)) {
            var result = profileService.getSkillRecommendations(userId, 10);
            List<Map<String, Object>> recs = (List<Map<String, Object>>) result.get("recommendations");
            if (recs == null || recs.isEmpty()) {
                return ToolResult.success(
                        "No skill recommendations at the moment — add more skills to get related suggestions!",
                        result);
            }
            StringBuilder sb = new StringBuilder("Recommended skills:\n");
            int show = Math.min(8, recs.size());
            for (int i = 0; i < show; i++) {
                var r = recs.get(i);
                sb.append("  - ").append(r.get("skill"))
                        .append(" (").append(r.getOrDefault("category", "Other")).append(")")
                        .append(" — ").append(r.get("reason")).append("\n");
            }
            return ToolResult.success(sb.toString(), result);
        }

        if ("edit".equals(action)) {
            String field = (String) params.get("field");
            String value = (String) params.get("value");
            if (field == null) {
                return ToolResult.clarification(
                        "Edit which field? Supported: name, branch, year, skills, interests, bio, github_url, linkedin_url, availability");
            }
            if (value == null || value.isBlank()) {
                return ToolResult.clarification(
                        "What to set \"" + field + "\" to? Try: \"edit bio to I love building AI projects\"");
            }
            try {
                Student student = studentRepository.findById(userId)
                        .orElseThrow(() -> new IllegalArgumentException("Student not found: " + userId));
                // Validate + sanitize the new value
                String cleaned = sanitizeValue(field, value);
                updateField(student, field, cleaned);
                student = studentRepository.save(student);
                return ToolResult.success("Updated " + field + " to: " + cleaned, Map.of("field", field, "value", cleaned));
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot update profile", e.getMessage());
            } catch (Exception e) {
                return ToolResult.error("Profile update failed", e.getMessage());
            }
        }

        if ("public".equals(action)) {
            Long targetId = (Long) params.get("userId");
            if (targetId == null) {
                return ToolResult.clarification(
                        "Whose public profile? Try \"view profile of user 5\" or \"show user 5\"");
            }
            try {
                var profile = profileService.getPublicProfile(targetId);
                return ToolResult.success("Public profile for user " + targetId, profile);
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot find profile", e.getMessage());
            }
        }

        return ToolResult.error("Unknown profile action", "Action: " + action);
    }

    private static final Set<String> EDITABLE_FIELDS = Set.of(
            "name", "branch", "year", "skills", "interests", "bio",
            "github_url", "github", "linkedin_url", "linkedin", "availability");

    private String extractField(String q) {
        if (q.contains("github")) return "github_url";
        if (q.contains("linkedin")) return "linkedin_url";
        if (q.contains("year")) return "year";
        if (q.contains("skill")) return "skills";
        if (q.contains("interest")) return "interests";
        if (q.contains("bio") || q.contains("about")) return "bio";
        if (q.contains("availability")) return "availability";
        if (q.contains("branch")) return "branch";
        if (q.contains("name")) return "name";
        return null;
    }

    private String extractValueAfterField(String intent, String field) {
        if (field == null) return null;
        String q = intent.toLowerCase(Locale.ROOT);
        // Look for "field to value", "field: value", "set field to value", "edit field value"
        String[] separators = {" to ", ": ", " set to "};
        for (String sep : separators) {
            int idx = q.indexOf(field) + field.length();
            if (idx > field.length() - 1) {
                int sepIdx = intent.toLowerCase(Locale.ROOT).indexOf(sep, idx);
                if (sepIdx >= 0) {
                    String value = intent.substring(sepIdx + sep.length()).trim();
                    if (!value.isEmpty()) return value;
                }
            }
        }
        // Fallback: try "edit field value" pattern
        String lower = intent.toLowerCase(Locale.ROOT);
        int fieldIdx = lower.indexOf(field);
        if (fieldIdx >= 0) {
            String rest = intent.substring(fieldIdx + field.length()).trim();
            // Remove leading "to", ":", etc.
            rest = rest.replaceFirst("^(to |: |set to )", "").trim();
            if (!rest.isEmpty()) return rest;
        }
        return null;
    }

    private String sanitizeValue(String field, String value) {
        if (value == null) return null;
        // Strip HTML/script-like tags
        String cleaned = value.replaceAll("<[^>]+>", "").trim();
        cleaned = cleaned.replaceAll("\\s+", " "); // normalize whitespace

        int maxLen = switch (field) {
            case "bio", "interests", "skills" -> 500;
            case "github_url", "linkedin_url" -> 300;
            default -> 200;
        };
        if (cleaned.length() > maxLen) {
            cleaned = cleaned.substring(0, maxLen);
        }

        // Validate year range
        if ("year".equals(field)) {
            try {
                int y = Integer.parseInt(cleaned.trim());
                if (y < 1 || y > 4) throw new IllegalArgumentException("Year must be 1-4");
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Year must be a number (1-4)");
            }
        }

        return cleaned;
    }

    private void updateField(Student student, String field, String value) {
        switch (field) {
            case "github_url", "github" -> student.setGithubUrl(value);
            case "linkedin_url", "linkedin" -> student.setLinkedinUrl(value);
            case "name" -> student.setName(value);
            case "branch" -> student.setBranch(value);
            case "year" -> {
                try { student.setYear(Integer.parseInt(value.trim())); }
                catch (NumberFormatException e) { throw new IllegalArgumentException("Year must be a number (1-4)"); }
            }
            case "skills" -> student.setSkills(value);
            case "interests" -> student.setInterests(value);
            case "bio" -> student.setBio(value);
            case "availability" -> student.setAvailability(value);
            default -> throw new IllegalArgumentException("Cannot edit field: " + field);
        }
    }

    private Long extractNumberAfter(String text, String pattern) {
        String lower = text.toLowerCase(Locale.ROOT);
        String p = pattern.trim();
        int idx = lower.indexOf(p);
        if (idx >= 0) {
            String rest = text.substring(idx + p.length()).trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(rest);
            if (m.find()) {
                try {
                    return Long.parseLong(m.group());
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }
}