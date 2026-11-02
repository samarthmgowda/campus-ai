package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.service.EventDomainService;
import com.campusconnect.campus_ai.service.EventTeammateService;
import com.campusconnect.campus_ai.service.ProjectToolService;
import com.campusconnect.campus_ai.service.TeammateMatchingService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Tool for finding teammates, and listing the user's projects.
 *
 * Two search modes:
 *   - Event-aware: when the intent names an event ("find me teammates for the auto
 *     hack hackathon"), resolve the event, infer its domain, and (a) if the user has
 *     a project matching that domain, rank against that project's gaps, or (b) treat
 *     it as a standalone event and rank against the domain, offering to create a
 *     project from scratch. Candidates are scored with the weighted matcher.
 *   - Project search: otherwise, rank teammates for each of the user's projects by
 *     weighted score, showing a short "why" per candidate.
 *
 * Triggers: "find teammate(s)", "find a teammate", "find me a team", "best teammates",
 * "teammates for <event>", "need a teammate", "my project", "show my project".
 * Typo variants ("teamates") are repaired by ToolRegistry.normalizeTypos before matching.
 */
@Component
public class ProjectTeammateTool implements Tool {

    private final ProjectToolService projectToolService;
    private final TeammateMatchingService matchingService;
    private final EventDomainService eventDomainService;
    private final EventTeammateService eventTeammateService;

    public ProjectTeammateTool(ProjectToolService projectToolService,
                               TeammateMatchingService matchingService,
                               EventDomainService eventDomainService,
                               EventTeammateService eventTeammateService) {
        this.projectToolService = projectToolService;
        this.matchingService = matchingService;
        this.eventDomainService = eventDomainService;
        this.eventTeammateService = eventTeammateService;
    }

    @Override
    public String name() {
        return "findTeammates";
    }

    @Override
    public String[] triggers() {
        return new String[]{"find teammate", "search for teammate", "search teammate",
                            "search for a teammate", "look for a teammate",
                            "find teammates", "my project", "show my project", "list my project",
                            // Typo/phrasing variants ("can u find me a best teammates for ...")
                            "teamates", "teammates for", "find me a team", "best teammates",
                            "best teamate", "find me team",
                            "find a teammate", "find a teamate", "find me a teammate",
                            "need a teammate", "need teammates", "need teammate"};
    }

    @Override
    public boolean requiresAuth() {
        return true;
    }

    @Override
    public String description() {
        return "Find teammates for your projects based on skill gaps, or for an event based on its domain. " +
               "Candidates are ranked by a weighted match (skills, interests, availability, complementary skills).";
    }

    @Override
    public int priority() {
        return 10; // High priority for specific teammate finding
    }

    @Override
    public Map<String, Object> extractParams(String intent) {
        Map<String, Object> params = new HashMap<>();
        String q = intent.toLowerCase(Locale.ROOT);
        if (q.contains("my project") || q.contains("show my project") || q.contains("list my project")) {
            params.put("action", "list");
        } else {
            params.put("action", "search");
        }
        params.put("rawIntent", intent);
        return params;
    }

    @Override
    public ToolResult execute(Map<String, Object> params, Long userId) {
        String action = (String) params.get("action");
        String rawIntent = params.get("rawIntent") instanceof String s ? s : "";

        if ("list".equals(action)) {
            return listProjects(userId);
        }

        // Event-aware path: does the intent name a known event?
        Optional<Event> event = eventDomainService.resolveEvent(rawIntent);
        if (event.isPresent()) {
            return searchForEvent(event.get(), userId);
        }

        return searchAcrossProjects(userId);
    }

    // ——— list ———

    private ToolResult listProjects(Long userId) {
        var myProjects = projectToolService.getMyProjects(userId);
        if (myProjects.isEmpty()) {
            return ToolResult.success(
                    "You are not a member of any active projects yet. " +
                    "Create one first: POST /projects with title + description + domain + requiredSkills",
                    Map.of("count", 0));
        }
        StringBuilder sb = new StringBuilder("Your projects:\n");
        for (var ps : myProjects) {
            sb.append("  - ").append(ps.name()).append(" [").append(ps.domain())
                    .append("] - ").append(ps.status())
                    .append(" - skills: ").append(ps.requiredSkills()).append("\n");
        }
        return ToolResult.success(sb.toString(), Map.of("count", myProjects.size()));
    }

    // ——— event-aware search ———

    private ToolResult searchForEvent(Event event, Long userId) {
        EventTeammateService.EventTeammatePlan plan;
        try {
            plan = eventTeammateService.plan(event, userId, true, false);
        } catch (Exception e) {
            // Degrade gracefully to plain project search rather than erroring out.
            return searchAcrossProjects(userId);
        }

        EventDomainService.Domain domain = plan.domain();
        StringBuilder sb = new StringBuilder();
        sb.append("\"").append(event.getTitle()).append("\" is a ")
          .append(event.getType() == null ? "event" : event.getType().toLowerCase(Locale.ROOT))
          .append(" about **").append(domain.label()).append("**")
          .append(" (typical skills: ").append(String.join(", ", domain.skills())).append(").\n\n");

        if (plan.standalone()) {
            sb.append("You don't have a project that matches this event yet.\n");
            if (plan.candidates().isEmpty()) {
                sb.append("No available candidates matched the ").append(domain.label())
                  .append(" stack right now. Try \"upcoming events\" for other events, or create a project: ")
                  .append("POST /projects with domain + requiredSkills = ")
                  .append(String.join(", ", domain.skills())).append(".\n");
                return ToolResult.success(sb.toString(),
                        Map.of("eventId", event.getId(), "domain", domain.label(), "standalone", true));
            }
            sb.append("Here are teammates for the ").append(domain.label())
              .append(" domain — top picks with a similar stack and interests:\n");
            appendCandidates(sb, plan.candidates());
            sb.append("\nWant me to create a project for this event so you can send requests? ")
              .append("Say \"create project <name> for the ")
              .append(shortLabel(domain)).append(" hackathon\".");
            return ToolResult.success(sb.toString(),
                    Map.of("eventId", event.getId(), "domain", domain.label(), "standalone", true,
                            "candidates", candidateData(plan.candidates())));
        }

        var pm = plan.matchedProject();
        sb.append("Your project \"").append(pm.name()).append("\" matches this event")
          .append(pm.domain() == null ? "" : " (" + pm.domain() + ")")
          .append(" — domain overlap ").append(Math.round(pm.matchScore() * 100)).append("%.\n");
        sb.append("Skill gaps: ").append(pm.gaps().isEmpty() ? "none" : String.join(", ", pm.gaps())).append("\n\n");
        if (plan.candidates().isEmpty()) {
            sb.append("No available candidates matched those gaps right now.\n");
            return ToolResult.success(sb.toString(),
                    Map.of("eventId", event.getId(), "projectId", pm.projectId(),
                            "domain", domain.label(), "standalone", false));
        }
        sb.append("Teammates who fit (similar stack + interests, profile-rated):\n");
        appendCandidates(sb, plan.candidates());
        sb.append("\nReply \"request teammate <name> for ").append(pm.name()).append("\" to send a request.");
        return ToolResult.success(sb.toString(),
                Map.of("eventId", event.getId(), "projectId", pm.projectId(),
                        "domain", domain.label(), "standalone", false,
                        "candidates", candidateData(plan.candidates())));
    }

    // ——— project search (weighted) ———

    private ToolResult searchAcrossProjects(Long userId) {
        var myProjects = projectToolService.getMyProjects(userId);
        if (myProjects.isEmpty()) {
            return ToolResult.clarification(
                    "You are not a member of any active projects yet. " +
                    "Create one first or browse public projects at GET /projects/public");
        }

        StringBuilder sb = new StringBuilder();
        boolean anyShown = false;
        for (var ps : myProjects) {
            var gaps = projectToolService.getSkillGaps(ps.id());
            if (gaps.isEmpty()) continue;

            List<TeammateMatchingService.ScoredCandidate> ranked;
            try {
                ranked = matchingService.rankForProject(userId, ps.id(), true, false).candidates();
            } catch (Exception e) {
                ranked = List.of();
            }
            if (ranked.isEmpty()) {
                sb.append(ps.name()).append(": skill gaps [")
                  .append(String.join(", ", gaps))
                  .append("] but no available candidates matched.\n");
                anyShown = true;
                continue;
            }
            // Only show the strongest candidates so the reply stays a recommendation,
            // not an exhaustive dump: top 3, and only those with a real match.
            List<TeammateMatchingService.ScoredCandidate> top = ranked.stream()
                    .filter(c -> c.skillMatch() > 0)
                    .limit(3)
                    .toList();
            if (top.isEmpty()) continue;
            anyShown = true;
            sb.append(ps.name()).append(" — gaps [")
              .append(String.join(", ", gaps)).append("], best matches:\n");
            for (var c : top) {
                sb.append("  - ").append(c.name()).append(" (").append(c.branch())
                  .append(", Y").append(c.year()).append(") score ").append(c.score())
                  .append(" — ").append(EventTeammateService.why(c)).append("\n");
            }
        }
        if (!anyShown) {
            return ToolResult.success(
                    "No skill gaps to fill across your active projects — every required skill is covered.",
                    Map.of("count", 0));
        }
        sb.append("\nReply \"request teammate <name> for <project name>\" to send a request.");
        return ToolResult.success(sb.toString(), Map.of());
    }

    // ——— formatting helpers ———

    private static void appendCandidates(StringBuilder sb,
                                         List<TeammateMatchingService.ScoredCandidate> candidates) {
        int shown = 0;
        for (var c : candidates) {
            if (c.skillMatch() <= 0 && shown > 0) break;
            sb.append("  - ").append(c.name()).append(" (").append(c.branch())
              .append(", Y").append(c.year()).append(") score ").append(c.score())
              .append(" — ").append(EventTeammateService.why(c)).append("\n");
            if (++shown >= 3) break;
        }
    }

    private static List<Map<String, Object>> candidateData(
            List<TeammateMatchingService.ScoredCandidate> candidates) {
        List<Map<String, Object>> out = new ArrayList<>();
        int shown = 0;
        for (var c : candidates) {
            out.add(Map.of(
                    "studentId", c.studentId(),
                    "name", c.name(),
                    "score", c.score(),
                    "matchedSkills", c.matchedSkills(),
                    "why", EventTeammateService.why(c)));
            if (++shown >= 3) break;
        }
        return out;
    }

    private static String shortLabel(EventDomainService.Domain domain) {
        String label = domain.label();
        int slash = label.indexOf('/');
        return (slash > 0 ? label.substring(0, slash) : label).trim();
    }
}
