package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.service.ModerationService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for moderation workflows: report content, view reports, resolve (admin).
 * Triggers: "report", "flag", "moderation", "my reports"
 */
@Component
public class ModerationTool implements Tool {

    private final ModerationService moderationService;

    public ModerationTool(ModerationService moderationService) {
        this.moderationService = moderationService;
    }

    @Override
    public String name() {
        return "moderationManager";
    }

    @Override
    public String[] triggers() {
        return new String[]{"report", "flag", "moderation", "my report", "my reports", "moderate"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "Report content (posts, projects, students, events, comments, documents), view your reports, or resolve reports (admin only).";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        if (q.startsWith("resolve ") || q.contains(" resolve ") || q.startsWith("dismiss ")) {
            params.put("action", "resolve");
            Long reportId = extractNumberAfter(intent, "resolve |dismiss ");
            params.put("reportId", reportId);
            params.put("dismiss", q.contains("dismiss"));
        } else if (q.contains("my report")) {
            params.put("action", "myReports");
        } else if (q.contains("stats") || q.contains("statistics")) {
            params.put("action", "stats");
        } else {
            // Default: report content
            params.put("action", "report");
            String targetType = extractTargetType(q);
            params.put("targetType", targetType);
            Long targetId = extractNumberAfter(intent, targetType != null ? targetType + " " : "");
            params.put("targetId", targetId);
            String reason = extractReason(q);
            params.put("reason", reason);
            String details = extractDetails(intent);
            params.put("details", details);
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");

        if ("report".equals(action)) {
            String targetType = (String) params.get("targetType");
            Long targetId = (Long) params.get("targetId");
            String reason = (String) params.get("reason");
            String details = (String) params.get("details");

            if (targetType == null || targetId == null) {
                return ToolResult.clarification(
                        "What would you like to report? Try: \"report post 5 spam inappropriate content\" " +
                        "or \"flag project 3 plagiarism\"");
            }

            try {
                var dto = moderationService.report(userId, targetType, targetId, reason, details);
                return ToolResult.success(
                        "Report filed for " + targetType.toLowerCase() + " #" + targetId + " (report #" + dto.id() + ")",
                        Map.of("reportId", dto.id(), "status", dto.status()));
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot file report", e.getMessage());
            }
        }

        if ("myReports".equals(action)) {
            var reports = moderationService.myReports(userId);
            if (reports.isEmpty()) {
                return ToolResult.success("You have not filed any reports yet.", Map.of());
            }
            StringBuilder sb = new StringBuilder("Your reports:\n");
            for (var r : reports) {
                sb.append("  - #").append(r.id()).append(" ")
                        .append(r.targetType()).append(" #").append(r.targetId())
                        .append(" — ").append(r.reason())
                        .append(" [").append(r.status()).append("]");
                if (r.resolution() != null) sb.append(" — ").append(r.resolution());
                sb.append("\n");
            }
            return ToolResult.success(sb.toString(), Map.of("count", reports.size()));
        }

        if ("resolve".equals(action)) {
            Long reportId = (Long) params.get("reportId");
            Boolean dismiss = (Boolean) params.get("dismiss");
            if (reportId == null) {
                return ToolResult.clarification(
                        "Which report to resolve? Try: \"resolve report 3\" or \"dismiss report 3\"");
            }
            try {
                String actionType = Boolean.TRUE.equals(dismiss) ? "DISMISSED" : "RESOLVED";
                var dto = moderationService.resolve(reportId, userId, actionType, dismiss ? "Dismissed by moderator" : "Resolved by moderator");
                return ToolResult.success("Report " + dto.status().toLowerCase() + ".", Map.of("reportId", dto.id(), "status", dto.status()));
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot resolve report", e.getMessage());
            } catch (org.springframework.web.server.ResponseStatusException e) {
                return ToolResult.error("Admin required", "You must be an admin to resolve reports.");
            }
        }

        if ("stats".equals(action)) {
            var stats = moderationService.stats();
            StringBuilder sb = new StringBuilder("Moderation stats:\n");
            stats.forEach((k, v) -> sb.append("  ").append(k).append(": ").append(v).append("\n"));
            return ToolResult.success(sb.toString(), stats);
        }

        return ToolResult.error("Unknown moderation action", "Action: " + action);
    }

    private String extractTargetType(String q) {
        String[] types = {"post", "project", "student", "event", "comment", "message", "document"};
        for (String t : types) {
            if (q.contains(t)) return t.toUpperCase(Locale.ROOT);
        }
        return null;
    }

    private String extractReason(String q) {
        String[] reasons = {"spam", "harassment", "inappropriate", "plagiarism", "other"};
        for (String r : reasons) {
            if (q.contains(r)) return r.toUpperCase(Locale.ROOT);
        }
        return "OTHER";
    }

    private String extractDetails(String intent) {
        String q = intent.toLowerCase(Locale.ROOT);
        // Find the first keyword after target type
        String[] keywords = {"spam", "harassment", "inappropriate", "plagiarism", "other", "report", "flag"};
        for (String kw : keywords) {
            int idx = q.indexOf(kw);
            if (idx >= 0) {
                String after = intent.substring(idx + kw.length()).trim();
                if (!after.isEmpty() && !after.matches("^\\d+.*")) {
                    return after;
                }
            }
        }
        return null;
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