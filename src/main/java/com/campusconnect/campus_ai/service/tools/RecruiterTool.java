package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.service.RecruiterPostingService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for recruiter workflows: list postings, apply, my applications.
 * Triggers: "job", "posting", "apply", "openings", "my applications"
 */
@Component
public class RecruiterTool implements Tool {

    private final RecruiterPostingService recruiterPostingService;

    public RecruiterTool(RecruiterPostingService recruiterPostingService) {
        this.recruiterPostingService = recruiterPostingService;
    }

    @Override
    public String name() {
        return "recruiterManager";
    }

    @Override
    public String[] triggers() {
        return new String[]{"my application", "my applications", "applied", "openings", "job", "posting", "apply", "hire", "hiring"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "Recruiter workflows: list open postings, apply to jobs, view your applications, create/close postings (recruiters only).";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        if (q.contains("my application") || q.contains("my applications") || q.contains("applied")) {
            params.put("action", "myApplications");
        } else if (q.contains("apply") && (q.contains("job") || q.contains("posting"))) {
            params.put("action", "apply");
            Long postingId = extractNumberAfter(intent, "apply |join ");
            params.put("postingId", postingId);
        } else {
            params.put("action", "listPostings");
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");

        if ("myApplications".equals(action)) {
            var apps = recruiterPostingService.getMyApplications(userId, null);
            if (apps.isEmpty()) {
                return ToolResult.success(
                        "You have no applications yet.",
                        Map.of("action", "Apply: POST /recruiter/postings/{id}/apply"));
            }
            StringBuilder sb = new StringBuilder("Your applications:\n");
            for (var a : apps) {
                sb.append("  - #").append(a.id()).append(" ")
                        .append(a.postingTitle()).append(" - ").append(a.status()).append("\n");
            }
            return ToolResult.success(sb.toString(), Map.of());
        }

        if ("apply".equals(action)) {
            Long postingId = (Long) params.get("postingId");
            if (postingId == null) {
                return ToolResult.clarification(
                        "Which posting would you like to apply for? " +
                        "GET /recruiter/postings to browse, then say \"apply to job 5\".");
            }
            try {
                var app = recruiterPostingService.apply(userId, postingId);
                return ToolResult.success(
                        "You applied to " + app.postingTitle() + " (application #" + app.id() + ")",
                        Map.of("applicationId", app.id(), "status", app.status()));
            } catch (Exception e) {
                return ToolResult.error("Application failed", e.getMessage());
            }
        }

        if ("listPostings".equals(action)) {
            var list = recruiterPostingService.listPostings("OPEN", null);
            if (list.isEmpty()) {
                return ToolResult.success("No open postings right now.", Map.of());
            }
            StringBuilder sb = new StringBuilder("Open postings:\n");
            int show = Math.min(5, list.size());
            for (int i = 0; i < show; i++) {
                var po = list.get(i);
                sb.append("  - #").append(po.id()).append(" ").append(po.title()).append(" @ ")
                        .append(po.company() != null ? po.company() : "?")
                        .append(" [").append(po.requiredSkills() != null ? po.requiredSkills() : "")
                        .append("] (").append(po.applicantCount()).append(" applicants)\n");
            }
            sb.append("\nApply: POST /recruiter/postings/{id}/apply with X-Demo-User");
            return ToolResult.success(sb.toString(), Map.of());
        }

        return ToolResult.error("Unknown recruiter action", "Action: " + action);
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