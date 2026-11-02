package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.service.MentorshipService;
import com.campusconnect.campus_ai.service.OllamaChatService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for mentorship workflows: request, respond, view requests.
 * Triggers: "mentor", "mentorship", "request mentor", "my mentor"
 */
@Component
public class MentorshipTool implements Tool {

    private final MentorshipService mentorshipService;
    private final AlumniRepository alumniRepository;
    private final OllamaChatService chatService;

    public MentorshipTool(MentorshipService mentorshipService,
                          AlumniRepository alumniRepository,
                          OllamaChatService chatService) {
        this.mentorshipService = mentorshipService;
        this.alumniRepository = alumniRepository;
        this.chatService = chatService;
    }

    @Override
    public String name() {
        return "mentorshipManager";
    }

    @Override
    public String[] triggers() {
        return new String[]{"mentor", "mentorship", "request mentor", "my mentor", "mentor request", "alumni mentor"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "Request mentorship from alumni, view your requests, accept/reject/withdraw requests.";
    }

    @Override
    public int priority() {
        return 5;
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);

        if (q.contains("withdraw") || q.contains("cancel")) {
            params.put("action", "withdraw");
            Long requestId = extractNumberAfter(intent, "withdraw |cancel ");
            params.put("requestId", requestId);
        } else if (q.contains("accept") || q.contains("reject")) {
            params.put("action", "respond");
            boolean accept = q.contains("accept");
            params.put("accept", accept);
            Long requestId = extractNumberAfter(intent, "accept |reject ");
            params.put("requestId", requestId);
        } else if (q.contains("my mentor") || q.contains("my request")) {
            params.put("action", "myRequests");
        } else if (q.contains("mentor") && (q.contains("request") || q.contains("find") || q.contains("looking"))
                || q.contains("connect with") || q.contains("reach out") || q.contains("find a mentor")
                || q.matches(".*want to.*(?:mentor|alumni|connect).*") || q.contains("looking for a mentor")) {
            params.put("action", "request");
            Long alumniId = extractNumberAfter(intent, "mentor |alumni |mentorship |request ");
            // If no numeric ID found, try name resolution (e.g. "from Rahul", "from Sarah")
            String alumniName = alumniId == null ? extractAlumniName(intent) : null;
            String message = extractMessage(intent);
            // If name resolution succeeded via "from <name> with <msg>", the name
            // token can bleed into the message ("from Sarah with Sarah said hi").
            // Strip a leading alumni-name token from the message when present.
            if (alumniName != null && message != null) {
                message = message.replaceFirst("(?i)^" + java.util.regex.Pattern.quote(alumniName) + "\\b\\s*", "").trim();
            }
            if (alumniId == null && alumniName != null) {
                final String name = alumniName;
                var match = alumniRepository.findAll().stream()
                        .filter(a -> a.getName() != null && a.getName().equalsIgnoreCase(name))
                        .findFirst();
                if (match.isPresent()) {
                    alumniId = match.get().getId();
                } else {
                    // Can't resolve name — pass clarification through params
                    params.put("alumniId", null);
                    params.put("message", null);
                    params.put("clarification", "I couldn't find an alumnus named '" + name + "'. " +
                            "Try using their ID: \"request mentor 5 with message\" " +
                            "or check GET /alumni for available mentors.");
                    return params;
                }
            }
            // LLM fallback: when the rule path did not fully resolve intent
            // (no alumniId yet, or no clean message yet), ask the LLM to recover
            // the structured fields. This is the recovery layer for natural-language
            // requests like "I want to connect with Sarah about internships".
            if (alumniId == null || message == null || message.isBlank()) {
                LlmExtractedParams llmParams = extractParamsWithLlm(intent);
                if (llmParams != null) {
                    if (alumniId == null && llmParams.alumniName() != null) {
                        alumniId = resolveAlumniId(llmParams.alumniName());
                    }
                    // The LLM's message overrides the rule-extracted message when it
                    // provides one: rule extraction can grab a "with <name> about ..."
                    // token that bleeds the alumni name into the message (e.g.
                    // "with Sarah about internships" -> "Sarah about internships").
                    // The LLM returns the clean intent ("internship opportunities").
                    if (llmParams.message() != null) {
                        message = llmParams.message();
                    }
                }
            }
            params.put("alumniId", alumniId);
            params.put("message", message);
        } else {
            params.put("action", "myRequests");
        }
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");

        if ("request".equals(action)) {
            String clarification = (String) params.get("clarification");
            if (clarification != null) {
                return ToolResult.clarification(clarification);
            }
            Long alumniId = (Long) params.get("alumniId");
            if (alumniId == null) {
                return ToolResult.clarification(
                        "Which alumni would you like to request mentorship from? " +
                        "Try: \"request mentor 5 with I want guidance on career growth\"");
            }
            String message = (String) params.get("message");
            try {
                var dto = mentorshipService.request(userId, alumniId, message);
                return ToolResult.success(
                        "Mentorship request sent to " + dto.alumniName() + " (request #" + dto.id() + ")",
                        Map.of("requestId", dto.id(), "status", dto.status()));
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot request mentorship", e.getMessage());
            }
        }

        if ("respond".equals(action)) {
            Long requestId = (Long) params.get("requestId");
            Boolean accept = (Boolean) params.get("accept");
            if (requestId == null || accept == null) {
                return ToolResult.clarification(
                        "Specify request ID and action. Try: \"accept mentor request 3\" or \"reject mentor request 3\"");
            }
            try {
                var dto = mentorshipService.respond(requestId, userId, accept ? "ACCEPT" : "REJECT");
                return ToolResult.success(
                        "Mentorship request " + dto.status().toLowerCase() + " (request #" + dto.id() + ")",
                        Map.of("requestId", dto.id(), "status", dto.status()));
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot respond", e.getMessage());
            }
        }

        if ("withdraw".equals(action)) {
            Long requestId = (Long) params.get("requestId");
            if (requestId == null) {
                return ToolResult.clarification(
                        "Which request to withdraw? Try: \"withdraw mentor request 3\"");
            }
            try {
                var result = mentorshipService.withdraw(requestId, userId);
                return ToolResult.success("Mentorship request withdrawn.", result);
            } catch (IllegalArgumentException e) {
                return ToolResult.error("Cannot withdraw", e.getMessage());
            }
        }

        if ("myRequests".equals(action)) {
            var requests = mentorshipService.getMyRequests(userId, null);
            if (requests.isEmpty()) {
                return ToolResult.success(
                        "You have no mentorship requests yet. " +
                        "Try: \"request mentor 5 with I want guidance on career growth\"",
                        Map.of());
            }
            StringBuilder sb = new StringBuilder("Your mentorship requests:\n");
            for (var r : requests) {
                sb.append("  - #").append(r.id()).append(" ")
                        .append(r.alumniName()).append(" — ").append(r.status());
                if (r.message() != null && !r.message().isBlank()) {
                    sb.append(" (").append(r.message()).append(")");
                }
                sb.append("\n");
            }
            return ToolResult.success(sb.toString(), Map.of("count", requests.size()));
        }

        return ToolResult.error("Unknown mentorship action", "Action: " + action);
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

    private String extractAlumniName(String intent) {
        String q = intent.toLowerCase(Locale.ROOT);
        // Only "from " introduces a name ("from Rahul", "from Sarah Johnson").
        // "with " is a message introducer and is handled by extractMessage, so
        // treating it as a name site would steal the message content and skip
        // the LLM disambiguation layer that should resolve intent like
        // "connect with Sarah about internships".
        String[] patterns = {"from "};
        for (String pat : patterns) {
            int idx = q.indexOf(pat);
            if (idx >= 0) {
                String rest = intent.substring(idx + pat.length()).trim();
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\S+").matcher(rest);
                if (m.find()) {
                    String candidate = m.group();
                    if (candidate.matches("[A-Za-z]+")) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    private Long resolveAlumniId(String alumniName) {
        return alumniRepository.findAll().stream()
                .filter(a -> a.getName() != null && a.getName().equalsIgnoreCase(alumniName))
                .findFirst()
                .map(Alumni::getId)
                .orElse(null);
    }

    private static record LlmExtractedParams(String alumniName, String message) {}

    private LlmExtractedParams extractParamsWithLlm(String intent) {
        String systemPrompt = """
            You are a helpful assistant that extracts structured parameters from user messages.
            Extract the following fields as JSON. Only return JSON, nothing else.
            If a field cannot be determined, return null for it.

            Fields:
            - alumniName: the name of a person mentioned as a potential mentor (e.g., "Rahul", "Sarah Johnson"). Return null if no name is mentioned.
            - message: the message or reason the user wants to convey to the mentor. Return null if not mentioned.
            """;

        String userPrompt = "Extract alumniName and message from this request:\n\"" + intent + "\"";

        try {
            String response = chatService.chat(systemPrompt, userPrompt);
            // Parse simple JSON like: {"alumniName":"Rahul","message":"help with career"}
            String trimmed = response.trim();
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                return null;
            }
            String content = trimmed.substring(1, trimmed.length() - 1).trim();
            String[] fields = content.split(",");

            String alumniName = null;
            String message = null;

            for (String field : fields) {
                String[] kv = field.split(":", 2);
                if (kv.length == 2) {
                    String key = kv[0].trim().replace("\"", "");
                    String val = kv[1].trim().replace("\"", "");
                    if (!val.equals("null")) {
                        if (key.equals("alumniName")) {
                            alumniName = val;
                        } else if (key.equals("message")) {
                            message = val;
                        }
                    }
                }
            }
            return new LlmExtractedParams(alumniName, message);
        } catch (Exception e) {
            // LLM fallback failed silently
            return null;
        }
    }

    private String extractMessage(String intent) {
        String q = intent.toLowerCase(Locale.ROOT);
        String[] patterns = {" with ", " message ", " saying "};
        for (String pat : patterns) {
            int idx = q.indexOf(pat);
            if (idx >= 0) {
                return intent.substring(idx + pat.length()).trim();
            }
        }
        return null;
    }
}