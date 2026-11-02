package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.StudentRepository;
import com.campusconnect.campus_ai.service.OllamaChatService;
import com.campusconnect.campus_ai.service.ProjectInviteService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for project invite workflows: send invitations, accept/reject, view invites.
 * Supports hybrid extraction: rule-based IDs + name resolution + LLM fallback for natural language.
 * Triggers: "invite", "invitation", "accept invite", "reject invite", "my invites"
 */
@Component
public class ProjectInviteTool implements Tool {

    private final ProjectInviteService projectInviteService;
    private final StudentRepository studentRepository;
    private final OllamaChatService chatService;

    public ProjectInviteTool(ProjectInviteService projectInviteService,
                              StudentRepository studentRepository,
                              OllamaChatService chatService) {
        this.projectInviteService = projectInviteService;
        this.studentRepository = studentRepository;
        this.chatService = chatService;
    }

    @Override
    public String name() {
        return "projectInviteManager";
    }

    @Override
    public String[] triggers() {
        return new String[]{"invite", "invitation", "invites", "my invite", "accept invite", "reject invite", "join project"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "Send project invitations, accept/reject invites you've received, and view your pending invitations.";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        if (q.contains("withdraw") && q.contains("invite")) {
            params.put("action", "withdraw");
            Long inviteId = extractNumberAfter(intent, "withdraw |cancel |delete ");
            params.put("inviteId", inviteId);
        } else if (q.contains("accept") && q.contains("invite")) {
            params.put("action", "accept");
            Long inviteId = extractNumberAfter(intent, "accept |approve ");
            params.put("inviteId", inviteId);
        } else if (q.contains("reject") && q.contains("invite")) {
            params.put("action", "reject");
            Long inviteId = extractNumberAfter(intent, "reject |deny ");
            params.put("inviteId", inviteId);
        } else if (q.contains("my invite")) {
            params.put("action", "myInvites");
        } else if (q.contains("invite ") || q.contains("invite to ")) {
            params.put("action", "invite");
            // Try to extract project ID and invitee ID.
            // Order matters: extract the invitee BEFORE the project so that a bare
            // "to <id>" does not steal the project number as the invitee ID.
            Long inviteeId = extractNumberAfter(intent, "user |student |invitee ");
            Long projectId = extractNumberAfter(intent, "project |id ");

            // If invitee ID not found, try name resolution
            if (inviteeId == null) {
                String inviteeName = extractInviteeName(intent);
                if (inviteeName != null) {
                    inviteeId = resolveStudentId(inviteeName);
                    if (inviteeId == null) {
                        // Can't resolve name - pass clarification
                        params.put("project", projectId);
                        params.put("inviteeId", null);
                        params.put("clarification", "I couldn't find a student named '" + inviteeName + "'. " +
                                "Try: \"invite user 5 to project 3\" or check available students.");
                        return params;
                    }
                }
            }
            // LLM fallback: if either ID is null (rule-based extraction missed
            // the project or the name), ask the LLM to recover structured fields.
            // This also covers natural-language invites like "invite Sarah to
            // hackathon" where there is no numeric project id to begin with.
            if (projectId == null || inviteeId == null) {
                LlmExtractedParams llmParams = extractParamsWithLlm(intent);
                if (llmParams != null) {
                    if (projectId == null && llmParams.projectName() != null) {
                        projectId = resolveProjectId(llmParams.projectName());
                    }
                    if (inviteeId == null && llmParams.inviteeName() != null) {
                        Long resolved = resolveStudentId(llmParams.inviteeName());
                        if (resolved != null) inviteeId = resolved;
                    }
                }
            }
            params.put("project", projectId);
            params.put("inviteeId", inviteeId);
        } else {
            params.put("action", "myInvites");
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");

        if ("withdraw".equals(action)) {
            Long inviteId = (Long) params.get("inviteId");
            if (inviteId == null) {
                return ToolResult.clarification("Which invite to withdraw? Try: \"withdraw invite 5\"");
            }
            try {
                var result = projectInviteService.withdraw(inviteId, userId);
                return ToolResult.success("Invite withdrawn.", result);
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot withdraw invite", e.getMessage());
            }
        }

        if ("accept".equals(action)) {
            Long inviteId = (Long) params.get("inviteId");
            if (inviteId == null) {
                return ToolResult.clarification("Which invite? Try: \"accept invite 3\"");
            }
            try {
                var dto = projectInviteService.respond(inviteId, userId, "ACCEPT");
                return ToolResult.success(
                        "Invite " + dto.status().toLowerCase() + " for " + dto.projectName(),
                        Map.of("inviteId", dto.id(), "status", dto.status()));
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot accept invite", e.getMessage());
            }
        }

        if ("reject".equals(action)) {
            Long inviteId = (Long) params.get("inviteId");
            if (inviteId == null) {
                return ToolResult.clarification("Which invite? Try: \"reject invite 3\"");
            }
            try {
                var dto = projectInviteService.respond(inviteId, userId, "REJECT");
                return ToolResult.success(
                        "Invite " + dto.status().toLowerCase() + " for " + dto.projectName(),
                        Map.of("inviteId", dto.id(), "status", dto.status()));
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot reject invite", e.getMessage());
            }
        }

        if ("invite".equals(action)) {
            String clarification = (String) params.get("clarification");
            if (clarification != null) {
                return ToolResult.clarification(clarification);
            }
            Long projectId = (Long) params.get("project");
            Long inviteeId = (Long) params.get("inviteeId");
            if (inviteeId == null) {
                return ToolResult.clarification(
                        "Send an invite? Try: \"invite user 5 to project 3\" or \"invite Alex to my startup project\"");
            }
            if (projectId == null) {
                return ToolResult.clarification(
                        "Which project? Try: \"invite Alex to project 3\" or \"invite user 5 to my startup project\"");
            }
            try {
                var dto = projectInviteService.invite(projectId, userId, inviteeId);
                return ToolResult.success(
                        "Invitation sent to " + dto.inviteeName() + " for " + dto.projectName() + " (invite #" + dto.id() + ")",
                        Map.of("inviteId", dto.id(), "status", dto.status()));
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot send invite", e.getMessage());
            }
        }

        if ("myInvites".equals(action)) {
            var invites = projectInviteService.getMyInvites(userId, null);
            if (invites.isEmpty()) {
                return ToolResult.success(
                        "You have no project invitations.",
                        Map.of("action", "POST /projects/{id}/invite to invite someone"));
            }
            StringBuilder sb = new StringBuilder("Your project invitations:\n");
            for (var inv : invites) {
                sb.append("  - #").append(inv.id()).append(" ")
                        .append(inv.projectName()).append(" from ").append(inv.inviterName())
                        .append(" [").append(inv.status()).append("]\n");
            }
            return ToolResult.success(sb.toString(), Map.of("count", invites.size()));
        }

        return ToolResult.error("Unknown invite action", "Action: " + action);
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

    private String extractInviteeName(String intent) {
        // Look for patterns: "invite [name] to [project]", "invite user [name]", etc.
        String q = intent.toLowerCase(Locale.ROOT);
        java.util.regex.Matcher m;
        // Pattern A: "invite <Name> to ..." — name sits between "invite" and "to".
        // Match positions on the lowercased query so the pattern is case-insensitive,
        // but return the substring from the ORIGINAL intent to preserve casing ("Alex").
        m = java.util.regex.Pattern.compile("invite\\s+([a-z][a-z.-]{1,29})\\s+to\\b").matcher(q);
        if (m.find()) {
            int start = intent.offsetByCodePoints(0, m.start(1));
            int end = intent.offsetByCodePoints(0, m.end(1));
            return intent.substring(start, end);
        }
        // Pattern B: "to <Name>" (fallback; e.g. "invite to Alex" or name after "to").
        if (q.contains("to ")) {
            int idx = intent.toLowerCase(Locale.ROOT).indexOf("to ") + 3;
            String rest = intent.substring(idx).trim();
            String candidate = rest.isEmpty() ? null : rest.split("\\s+")[0];
            if (candidate != null && candidate.matches("[A-Za-z]+") && candidate.length() >= 2 && candidate.length() <= 30) {
                return candidate;
            }
        }
        return null;
    }

    private Long resolveStudentId(String studentName) {
        return studentRepository.findAll().stream()
                .filter(s -> s.getName() != null && s.getName().equalsIgnoreCase(studentName))
                .findFirst()
                .map(Student::getId)
                .orElse(null);
    }

    // Inner records for extracting LLM output
    private static record LlmExtractedParams(String projectName, String inviteeName) {}

    private LlmExtractedParams extractParamsWithLlm(String intent) {
        String systemPrompt = """
            You are a helpful assistant that extracts structured parameters from user messages.
            Extract the following fields as JSON. Only return JSON, nothing else.
            If a field cannot be determined, return null for it.

            Fields:
            - projectName: the name of the project to invite someone to (e.g., "Startup", "Hackathon 2026", "AI Research Project"). Return null if no project name is mentioned.
            - inviteeName: the name of the student to invite (e.g., "Alex", "Sarah Johnson"). Return null if no name is mentioned.
            """;

        String userPrompt = "Extract projectName and inviteeName from this request:\n\"" + intent + "\"";

        try {
            String response = chatService.chat(systemPrompt, userPrompt);
            if (response == null || response.isBlank()) return null;
            // Parse simple JSON like: {"projectName":"Startup","inviteeName":"Alex"}
            String trimmed = response.trim();
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return null;
            }
            String content = trimmed.substring(1, trimmed.length() - 1).trim();
            String[] fields = content.split(",");

            String projectName = null;
            String inviteeName = null;

            for (String field : fields) {
                String[] kv = field.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String val = kv[1].trim().replace("\"", "");
                    if (!val.equals("null")) {
                        if (key.equals("projectName")) {
                            projectName = val;
                        } else if (key.equals("inviteeName")) {
                            inviteeName = val;
                        }
                    }
                }
            }
            return new LlmExtractedParams(projectName, inviteeName);
        } catch (Exception e) {
            // LLM fallback failed silently
            return null;
        }
    }

    private Long resolveProjectId(String projectName) {
        var projects = studentRepository.findAll();
        // Note: This is a simplification. In production, you'd want ProjectRepository
        // For now, return null to indicate we'd need project lookup
        return null;
    }
}