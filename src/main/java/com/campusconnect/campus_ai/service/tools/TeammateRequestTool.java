package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.service.TeammateRequestService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for teammate request workflows: find teammates, send requests, accept/reject/withdraw.
 * Triggers: "teammate", "find teammate", "suggested", "my request", "pending teammate"
 */
@Component
public class TeammateRequestTool implements Tool {

    private final TeammateRequestService teammateRequestService;

    public TeammateRequestTool(TeammateRequestService teammateRequestService) {
        this.teammateRequestService = teammateRequestService;
    }

    @Override
    public String name() {
        return "teammateManager";
    }

    @Override
    public String[] triggers() {
        return new String[]{"teammate", "find teammate", "suggested", "my request", "pending teammate", "join request", "invite teammate"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "Find suggested teammates for a project, send invitations, accept/reject/witdraw teammate requests.";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        if (q.contains("accept") && (q.contains("teammate") || q.contains("request") || q.contains("invite"))) {
            params.put("action", "accept");
            Long requestId = extractNumberAfter(intent, "accept ");
            params.put("requestId", requestId);
        } else if (q.contains("reject") && (q.contains("teammate") || q.contains("request") || q.contains("invite"))) {
            params.put("action", "reject");
            Long requestId = extractNumberAfter(intent, "reject ");
            params.put("requestId", requestId);
        } else if (q.contains("withdraw")) {
            params.put("action", "withdraw");
            Long requestId = extractNumberAfter(intent, "withdraw ");
            params.put("requestId", requestId);
        } else if (q.contains("suggest") || q.contains("find teammate") || q.contains("my request")) {
            params.put("action", "suggest");
            Long projectId = extractNumberAfter(intent, "project ");
            params.put("projectId", projectId);
            params.put("mine", q.contains("my request"));
        } else {
            params.put("action", "suggest");
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");

        if ("accept".equals(action) || "reject".equals(action)) {
            Long requestId = (Long) params.get("requestId");
            if (requestId == null) {
                return ToolResult.clarification(
                        "Which request? Try: \"accept teammate request 3\" or \"reject request 5\"");
            }
            try {
                var dto = teammateRequestService.respond(requestId, userId, "accept".equals(action) ? "ACCEPT" : "REJECT");
                return ToolResult.success(
                        "Teammate request " + dto.status().toLowerCase() + " for " + dto.projectName()
                                + " — " + dto.candidateName(),
                        Map.of("requestId", dto.id(), "status", dto.status()));
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot respond", e.getMessage());
            } catch (org.springframework.web.server.ResponseStatusException e) {
                return ToolResult.error("Request error", e.getReason());
            }
        }

        if ("withdraw".equals(action)) {
            Long requestId = (Long) params.get("requestId");
            if (requestId == null) {
                return ToolResult.clarification(
                        "Which request to withdraw? Try: \"withdraw request 3\"");
            }
            try {
                var dto = teammateRequestService.withdraw(requestId, userId);
                return ToolResult.success("Teammate request withdrawn.", Map.of("requestId", dto.id(), "status", dto.status()));
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot withdraw", e.getMessage());
            } catch (org.springframework.web.server.ResponseStatusException e) {
                return ToolResult.error("Request error", e.getReason());
            }
        }

        if ("suggest".equals(action)) {
            Boolean mine = (Boolean) params.get("mine");
            Long projectId = (Long) params.get("projectId");

            if (Boolean.TRUE.equals(mine)) {
                var myRequests = teammateRequestService.getMyRequests(userId, null);
                if (myRequests.isEmpty()) {
                    return ToolResult.success("You have no incoming teammate requests.", Map.of());
                }
                StringBuilder sb = new StringBuilder("Your teammate requests:\n");
                for (var r : myRequests) {
                    sb.append("  - #").append(r.id()).append(" ")
                            .append(r.requesterName()).append(" wants you on ").append(r.projectName())
                            .append(" [").append(r.status()).append("]");
                    if (r.matchedSkills() != null && !r.matchedSkills().isBlank()) {
                        sb.append(" (matches: ").append(r.matchedSkills()).append(")");
                    }
                    sb.append("\n");
                }
                return ToolResult.success(sb.toString(), Map.of("count", myRequests.size()));
            }

            if (projectId == null) {
                return ToolResult.clarification(
                        "Which project? Try: \"find teammates for project 3\" or \"suggest teammates project 3\"");
            }

            try {
                var suggestions = teammateRequestService.suggestForProject(userId, projectId);
                var sugList = suggestions.candidates();
                if (sugList == null || sugList.isEmpty()) {
                    return ToolResult.success(
                            "No recommended teammates found for this project. " +
                            "You can still invite by user ID: \"invite teammate 5 to project 3\"",
                            Map.of());
                }
                StringBuilder sb = new StringBuilder("Suggested teammates for project #" + projectId + ":\n");
                int show = Math.min(5, sugList.size());
                for (int i = 0; i < show; i++) {
                    var s = sugList.get(i);
                    sb.append("  - ").append(s.name()).append(" (ID: ").append(s.studentId())
                            .append(", ").append(s.branch()).append(" Y").append(s.year())
                            .append(") — skills: ").append(s.skills());
                    if (s.matchedSkills() != null && !s.matchedSkills().isEmpty()) {
                        sb.append(" (matched: ").append(String.join(", ", s.matchedSkills())).append(")");
                    }
                    sb.append("\n");
                }
                sb.append("\nSend request: POST /projects/teammate-requests or say \"invite teammate 5 to project 3\"");
                return ToolResult.success(sb.toString(), Map.of("count", sugList.size(), "projectId", projectId));
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot get suggestions", e.getMessage());
            } catch (org.springframework.web.server.ResponseStatusException e) {
                return ToolResult.error("Project access error", e.getReason());
            }
        }

        return ToolResult.error("Unknown teammate action", "Action: " + action);
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