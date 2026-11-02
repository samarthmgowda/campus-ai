package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.service.RecruiterPostingService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for recruiter job-posting management: list, create, close postings, manage applications.
 * Triggers: "posting", "postings", "job post", "close posting", "create posting"
 */
@Component
public class RecruiterPostingTool implements Tool {

    private final RecruiterPostingService recruiterPostingService;

    public RecruiterPostingTool(RecruiterPostingService recruiterPostingService) {
        this.recruiterPostingService = recruiterPostingService;
    }

    @Override
    public String name() {
        return "recruiterPostingManager";
    }

    @Override
    public String[] triggers() {
        return new String[]{"posting", "postings", "job post", "create posting", "close posting", "my postings", "company posting"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "Manage your job postings: list, create new postings, close existing ones, view applicants.";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        if (q.contains("create") && (q.contains("posting") || q.contains("job"))) {
            params.put("action", "create");
        } else if (q.contains("close")) {
            params.put("action", "close");
            Long postingId = extractNumberAfter(intent, "close |posting ");
            params.put("postingId", postingId);
        } else if (q.contains("my posting") || q.contains("my job")) {
            params.put("action", "myPostings");
        } else if (q.contains("applicants") || q.contains("application")) {
            params.put("action", "applicants");
            Long postingId = extractNumberAfter(intent, "posting ");
            params.put("postingId", postingId);
        } else {
            params.put("action", "list");
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");

        if ("list".equals(action)) {
            try {
                var list = recruiterPostingService.listPostings("OPEN", null);
                if (list.isEmpty()) {
                    return ToolResult.success("No open postings right now.", Map.of());
                }
                StringBuilder sb = new StringBuilder("Open postings:\n");
                int show = Math.min(10, list.size());
                for (int i = 0; i < show; i++) {
                    var po = list.get(i);
                    sb.append("  - #").append(po.id()).append(" ").append(po.title())
                            .append(" @ ").append(po.company() != null ? po.company() : "?")
                            .append(" [").append(po.requiredSkills() != null ? po.requiredSkills() : "")
                            .append("] (").append(po.applicantCount()).append(" applicants)\n");
                }
                if (list.size() > show) sb.append("  ... and ").append(list.size() - show).append(" more\n");
                sb.append("\nCreate: POST /recruiter/postings with title, company, required_skills, description");
                return ToolResult.success(sb.toString(), Map.of("count", list.size()));
            } catch (Exception e) {
                return ToolResult.error("Cannot list postings", e.getMessage());
            }
        }

        if ("myPostings".equals(action)) {
            try {
                var list = recruiterPostingService.listPostings(null, userId);
                if (list.isEmpty()) {
                    return ToolResult.success("You haven't created any postings yet.", Map.of());
                }
                StringBuilder sb = new StringBuilder("Your postings:\n");
                for (var po : list) {
                    sb.append("  - #").append(po.id()).append(" ").append(po.title())
                            .append(" [").append(po.status()).append("]")
                            .append(" (").append(po.applicantCount()).append(" applicants)\n");
                }
                return ToolResult.success(sb.toString(), Map.of("count", list.size()));
            } catch (Exception e) {
                return ToolResult.error("Cannot fetch your postings", e.getMessage());
            }
        }

        if ("create".equals(action)) {
            return ToolResult.clarification(
                    "Create a posting? Use POST /recruiter/postings with:\n" +
                    "  title, company, required_skills (comma-separated), description, location (optional)");
        }

        if ("close".equals(action)) {
            Long postingId = (Long) params.get("postingId");
            if (postingId == null) {
                return ToolResult.clarification("Which posting to close? Try: \"close posting 5\"");
            }
            try {
                var result = recruiterPostingService.closePosting(userId, postingId);
                var data = new HashMap<String, Object>();
                if (result != null) {
                    data.put("posting", Map.of(
                            "id", result.id(),
                            "title", result.title(),
                            "company", result.company(),
                            "status", result.status(),
                            "applicantCount", result.applicantCount()
                    ));
                }
                return ToolResult.success("Posting closed.", data);
            } catch (Exception e) {
                return ToolResult.error("Cannot close posting", e.getMessage());
            }
        }

        if ("applicants".equals(action)) {
            Long postingId = (Long) params.get("postingId");
            if (postingId == null) {
                return ToolResult.clarification("Which posting's applicants? Try: \"applicants for posting 5\"");
            }
            try {
                var list = recruiterPostingService.listPostings("OPEN", null);
                // Check ownership — would need to check the posting belongs to this recruiter
                return ToolResult.success(
                        "Applicant list available via GET /recruiter/postings/{id}/applicants",
                        Map.of("postingId", postingId));
            } catch (Exception e) {
                return ToolResult.error("Cannot fetch applicants", e.getMessage());
            }
        }

        return ToolResult.error("Unknown posting action", "Action: " + action);
    }

    private Long extractNumberAfter(String text, String pattern) {
        String[] parts = pattern.split("\\|");
        String lower = text.toLowerCase(Locale.ROOT);
        for (String pat : parts) {
            String p = pat.trim();
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
        }
        return null;
    }
}