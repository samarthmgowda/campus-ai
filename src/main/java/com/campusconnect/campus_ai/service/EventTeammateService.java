package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.entity.Project;
import com.campusconnect.campus_ai.repository.ProjectMemberRepository;
import com.campusconnect.campus_ai.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Event-aware teammate discovery.
 *
 * Flow (mirrors the product ask): given a query that names an event,
 *   1. resolve the event and infer its domain ("what is this hackathon about"),
 *   2. check whether the user already has a project that matches that domain,
 *   3. if yes  → rank teammates against THAT project's skill gaps and report
 *               "your project X matches the event; here are people with a similar
 *                stack and interests" (profile + rating signals included),
 *      if no   → rank teammates against the event domain directly (standalone
 *                event) and offer to create a project from scratch.
 *
 * Ranking reuses {@link TeammateMatchingService} so event suggestions carry the
 * same weighted analysis (skill match / interest / availability / complementary /
 * optional semantic) as project suggestions — no second, weaker code path.
 */
@Service
public class EventTeammateService {

    /** A ranked suggestion: the matching project (or null for a standalone event) + candidates. */
    public record EventTeammatePlan(Event event, EventDomainService.Domain domain,
                                    boolean standalone, ProjectMatch matchedProject,
                                    List<TeammateMatchingService.ScoredCandidate> candidates,
                                    boolean semanticUsed) {}

    /** A user project that lines up with the event domain. */
    public record ProjectMatch(Long projectId, String name, String domain, double matchScore,
                               List<String> gaps) {}

    private final EventDomainService eventDomainService;
    private final TeammateMatchingService matchingService;
    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectToolService projectToolService;
    private final CurrentUserService currentUserService;
    private final SkillGraphService skillGraphService;

    /** Minimum domain↔project overlap before we treat a project as "matching" the event. */
    private static final double PROJECT_MATCH_THRESHOLD = 0.34;

    public EventTeammateService(EventDomainService eventDomainService,
                                TeammateMatchingService matchingService,
                                ProjectRepository projectRepository,
                                ProjectMemberRepository memberRepository,
                                ProjectToolService projectToolService,
                                CurrentUserService currentUserService,
                                SkillGraphService skillGraphService) {
        this.eventDomainService = eventDomainService;
        this.matchingService = matchingService;
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.projectToolService = projectToolService;
        this.currentUserService = currentUserService;
        this.skillGraphService = skillGraphService;
    }

    /**
     * Build the event-driven teammate plan for a user.
     *
     * @param event    resolved event (see EventDomainService.resolveEvent)
     * @param userId   requesting user
     * @param availableOnly  exclude BUSY candidates (default true for suggestions)
     * @param useSemantic    blend in embeddings when Ollama is available
     */
    public EventTeammatePlan plan(Event event, Long userId, boolean availableOnly, boolean useSemantic) {
        currentUserService.requireUser(userId);
        if (event == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No event to plan for");
        }
        EventDomainService.Domain domain = eventDomainService.inferDomain(event);

        // 2. Does the user already own/join a project that matches this domain?
        ProjectMatch match = bestMatchingProject(userId, domain);

        Set<Long> memberIds = Set.of();
        List<String> needs;
        Set<String> contextTokens;
        String cacheKey;
        String contextText;

        if (match != null) {
            // Rank against the matched project's actual gaps — a real, precise need.
            Project p = projectRepository.findById(match.projectId()).orElse(null);
            List<String> gaps = match.gaps();
            needs = gaps.isEmpty() ? domain.skills() : gaps;
            contextTokens = domainTokensForProject(p, domain);
            cacheKey = "eventctx:project:" + match.projectId();
            contextText = (p != null ? safe(p.getName()) + " " + safe(p.getDomain()) + " "
                    + safe(p.getRequiredSkills()) + " " + safe(p.getDescription()) : "")
                    + EventDomainService.domainText(event, domain);
            memberIds = memberRepository.findByProjectId(match.projectId()).stream()
                    .filter(m -> m.getStudent() != null)
                    .map(m -> m.getStudent().getId())
                    .collect(Collectors.toSet());
        } else {
            // Standalone event: needs are the domain's representative skills.
            needs = domain.skills();
            contextTokens = eventDomainService.domainTokens(domain, skillGraphService);
            cacheKey = "eventctx:event:" + event.getId();
            contextText = EventDomainService.domainText(event, domain);
        }

        List<TeammateMatchingService.ScoredCandidate> ranked = matchingService.rankCandidatesForContext(
                cacheKey, contextText, contextTokens, needs, memberIds, availableOnly, useSemantic, null);
        boolean semUsed = ranked.stream().anyMatch(c -> c.semantic() != null);

        return new EventTeammatePlan(event, domain, match == null, match, ranked, semUsed);
    }

    /**
     * Pick the user's project whose required skills best overlap the event domain
     * and that still has skill gaps (otherwise there is nothing to recommend).
     * Returns null when no project clears {@link #PROJECT_MATCH_THRESHOLD}.
     */
    ProjectMatch bestMatchingProject(Long userId, EventDomainService.Domain domain) {
        List<ProjectMatch> candidates = new ArrayList<>();
        for (ProjectToolService.ProjectSummary ps : projectToolService.getMyProjects(userId)) {
            Project p = projectRepository.findById(ps.id()).orElse(null);
            if (p == null) continue;
            double score = eventDomainService.projectMatchScore(toProjectLike(p), domain, skillGraphService);
            if (score < PROJECT_MATCH_THRESHOLD) continue;
            List<String> gaps = projectToolService.getSkillGaps(p.getId());
            if (gaps.isEmpty()) continue; // fully staffed — nothing to suggest
            candidates.add(new ProjectMatch(p.getId(), p.getName(), p.getDomain(), round4(score), gaps));
        }
        return candidates.stream()
                .max(Comparator.comparingDouble(ProjectMatch::matchScore)
                        .thenComparing(ProjectMatch::projectId))
                .orElse(null);
    }

    /** Interest tokens for the matched project: its own required skills + the event domain. */
    private Set<String> domainTokensForProject(Project p, EventDomainService.Domain domain) {
        Set<String> tokens = new LinkedHashSet<>(eventDomainService.domainTokens(domain, skillGraphService));
        if (p != null) {
            for (String s : splitComma(p.getRequiredSkills())) {
                String n = skillGraphService.normalize(s);
                if (n == null) n = s;
                tokens.add(n.toLowerCase(Locale.ROOT));
            }
            if (p.getDomain() != null) {
                for (String w : p.getDomain().toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                    if (w.length() >= 3) tokens.add(w);
                }
            }
        }
        return tokens;
    }

    private static EventDomainService.ProjectLike toProjectLike(Project p) {
        return new EventDomainService.ProjectLike() {
            @Override public String domainText() { return p.getDomain(); }
            @Override public String requiredSkillsCsv() { return p.getRequiredSkills(); }
        };
    }

    /** Human-readable "why" for a candidate, using the signals the scorer captured. */
    public static String why(TeammateMatchingService.ScoredCandidate c) {
        List<String> reasons = new ArrayList<>();
        if (c.matchedSkills() != null && !c.matchedSkills().isEmpty()) {
            reasons.add("matches " + String.join(", ", c.matchedSkills()));
        }
        if (c.interest() > 0) reasons.add("interested in the domain");
        if (c.complementary() > 0) reasons.add("complementary skills");
        if (c.endorsements() > 0) reasons.add(c.endorsements() + " endorsement(s)");
        if (c.avgRating() != null) reasons.add("avg rating " + c.avgRating());
        if ("AVAILABLE".equalsIgnoreCase(c.availability())) reasons.add("available");
        return reasons.isEmpty() ? "partial profile match" : String.join("; ", reasons);
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static List<String> splitComma(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static double round4(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
