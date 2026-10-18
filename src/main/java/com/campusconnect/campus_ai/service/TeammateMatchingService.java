package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Project;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.ModerationReportRepository;
import com.campusconnect.campus_ai.repository.ProjectMemberRepository;
import com.campusconnect.campus_ai.repository.ProjectRepository;
import com.campusconnect.campus_ai.repository.ReviewRepository;
import com.campusconnect.campus_ai.repository.SkillEndorsementRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Weighted teammate recommendation (research track, Task #40).
 *
 * Initial proposal — every component normalized to 0..1, hard filters first:
 *   S = 0.50 * SkillMatch + 0.20 * ProjectInterest + 0.20 * Availability
 *       + 0.10 * ComplementarySkills
 *
 *   SkillMatch: fraction of project skill gaps the candidate covers
 *     (canonical skill equality, same normalization as the overlap baseline).
 *   ProjectInterest: fraction of project tokens (domain + required skills, normalized)
 *     that appear in the candidate's interests.
 *   Availability: AVAILABLE=1.0, unknown/blank=0.5, anything else (BUSY…)=0.2.
 *     When availableOnly=true, BUSY candidates are hard-filtered before scoring.
 *   ComplementarySkills: min(1, relatedExtras/3) where a related extra is a
 *     candidate skill outside the gaps that co-occurs with a gap skill in the
 *     skill graph (SkillGraphService.getRelatedSkills). 0 when the graph is empty.
 *
 * Hard filters (before scoring): already a project member, suspended students,
 * BUSY when availableOnly=true. Students with open moderation reports are NOT
 * filtered — they sink below equal-scored candidates, mirroring the baseline.
 * Endorsement counts + review averages are trust tiebreaks / eval signals,
 * deliberately kept OUT of S so the four-component formula stays interpretable.
 *
 * Optional semantic arm: cosine between bge-m3 embeddings of
 * (project name+domain+requiredSkills+description) and
 * (candidate skills+interests+bio), mapped to 0..1, blended as
 * S3 = (1-blend)*S + blend*semantic. Never throws: Ollama down means
 * semantic=null and the candidate keeps its hybrid score. No new dependencies.
 */
@Service
public class TeammateMatchingService {

    public record ScoredCandidate(Long studentId, String name, String branch, Integer year,
                                  String skills, String availability, List<String> matchedSkills,
                                  double skillMatch, double interest, double availabilityScore,
                                  double complementary, Double semantic, double score,
                                  long endorsements, Double avgRating, boolean hasOpenReport) {}
    public record WeightedSuggestions(String projectName, List<String> gaps,
                                      List<ScoredCandidate> candidates, boolean semanticUsed) {}

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final StudentRepository studentRepository;
    private final CurrentUserService currentUserService;
    private final ProjectToolService projectToolService;
    private final SkillGraphService skillGraphService;
    private final SkillEndorsementRepository endorsementRepository;
    private final ModerationReportRepository moderationRepository;
    private final ReviewRepository reviewRepository;
    private final EmbeddingService embeddingService;

    private final double wSkill;
    private final double wInterest;
    private final double wAvailability;
    private final double wComplementary;
    private final double semanticBlend;

    public TeammateMatchingService(ProjectRepository projectRepository,
                                   ProjectMemberRepository memberRepository,
                                   StudentRepository studentRepository,
                                   CurrentUserService currentUserService,
                                   ProjectToolService projectToolService,
                                   SkillGraphService skillGraphService,
                                   SkillEndorsementRepository endorsementRepository,
                                   ModerationReportRepository moderationRepository,
                                   ReviewRepository reviewRepository,
                                   EmbeddingService embeddingService,
                                   @Value("${teammate.w-skill:0.50}") double wSkill,
                                   @Value("${teammate.w-interest:0.20}") double wInterest,
                                   @Value("${teammate.w-availability:0.20}") double wAvailability,
                                   @Value("${teammate.w-complementary:0.10}") double wComplementary,
                                   @Value("${teammate.semantic-blend:0.30}") double semanticBlend) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.studentRepository = studentRepository;
        this.currentUserService = currentUserService;
        this.projectToolService = projectToolService;
        this.skillGraphService = skillGraphService;
        this.endorsementRepository = endorsementRepository;
        this.moderationRepository = moderationRepository;
        this.reviewRepository = reviewRepository;
        this.embeddingService = embeddingService;
        this.wSkill = wSkill;
        this.wInterest = wInterest;
        this.wAvailability = wAvailability;
        this.wComplementary = wComplementary;
        this.semanticBlend = semanticBlend;
    }

    /**
     * Rank teammates for a project with the weighted formula.
     * Membership + hidden-project guards mirror ProjectToolService.findTeammates.
     */
    public WeightedSuggestions rankForProject(Long userId, Long projectId,
                                              boolean availableOnly, boolean useSemantic) {
        currentUserService.requireUser(userId);
        Project p = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found: " + projectId));
        if ("HIDDEN".equalsIgnoreCase(p.getStatus())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project hidden by moderation");
        }
        boolean member = memberRepository.findByProjectId(p.getId()).stream()
                .anyMatch(m -> m.getStudent() != null && m.getStudent().getId().equals(userId));
        boolean owner = p.getOwner() != null && p.getOwner().getId().equals(userId);
        if (!member && !owner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a member of project: " + p.getName());
        }
        List<String> gaps = projectToolService.getSkillGaps(projectId);
        if (gaps.isEmpty()) return new WeightedSuggestions(p.getName(), gaps, List.of(), false);
        Set<Long> memberIds = memberRepository.findByProjectId(p.getId()).stream()
                .map(m -> m.getStudent().getId()).collect(Collectors.toSet());
        List<ScoredCandidate> ranked = rankCandidates(p, gaps, memberIds, availableOnly, useSemantic, null);
        boolean semUsed = ranked.stream().anyMatch(c -> c.semantic() != null);
        return new WeightedSuggestions(p.getName(), gaps, ranked, semUsed);
    }

    /**
     * Core scorer over an explicit candidate pool (eval harness entry).
     * excludeIds are hard-filtered (e.g. remaining team members in leave-one-out).
     * embeddingCache is an optional caller-owned cache (student id / project key → vector)
     * so eval runs don't re-embed the same profiles per held-out case.
     */
    public List<ScoredCandidate> rankCandidates(Project project, List<String> gaps,
                                                Collection<Long> excludeIds,
                                                boolean availableOnly, boolean useSemantic,
                                                Map<String, float[]> embeddingCache) {
        if (gaps == null || gaps.isEmpty()) return List.of();
        Set<Long> exclude = excludeIds == null ? Set.of() : new HashSet<>(excludeIds);

        // Canonical gap set (lowercase) for matching.
        Map<String, String> gapLowerToOriginal = new LinkedHashMap<>();
        for (String g : gaps) {
            if (g == null || g.isBlank()) continue;
            String n = skillGraphService.normalize(g.trim());
            if (n == null) n = g.trim();
            gapLowerToOriginal.putIfAbsent(n.toLowerCase(Locale.ROOT), g.trim());
        }
        if (gapLowerToOriginal.isEmpty()) return List.of();

        // Project interest tokens: normalized required skills + domain words.
        Set<String> projectTokens = projectTokens(project);

        // Related-skill cache per gap (one DB-backed lookup per gap, not per candidate).
        Map<String, Set<String>> relatedByGap = new HashMap<>();
        for (String gapLower : gapLowerToOriginal.keySet()) {
            relatedByGap.put(gapLower, relatedSkillsLower(gapLower));
        }

        String projectText = useSemantic ? projectText(project) : null;
        float[] projectVec = null;
        if (useSemantic) {
            projectVec = cachedEmbed(embeddingCache, "project:" + project.getId(), projectText);
        }

        List<ScoredCandidate> out = new ArrayList<>();
        for (Student s : studentRepository.findAll()) {
            if (s == null || s.getId() == null || exclude.contains(s.getId())) continue;
            if (s.isSuspended()) continue;
            if (availableOnly && "BUSY".equalsIgnoreCase(s.getAvailability())) continue;

            Set<String> studentLower = splitComma(s.getSkills()).stream()
                    .map(x -> { String n = skillGraphService.normalize(x); if (n == null) n = x; return n.toLowerCase(Locale.ROOT); })
                    .collect(Collectors.toSet());

            List<String> matched = new ArrayList<>();
            for (Map.Entry<String, String> e : gapLowerToOriginal.entrySet()) {
                if (studentLower.contains(e.getKey())) matched.add(e.getValue());
            }
            double skillMatch = (double) matched.size() / gapLowerToOriginal.size();
            double interest = interestScore(projectTokens, s.getInterests());
            double avail = availabilityScore(s.getAvailability());
            double complementary = complementaryScore(studentLower, gapLowerToOriginal.keySet(), relatedByGap);

            double hybrid = round4(wSkill * skillMatch + wInterest * interest
                    + wAvailability * avail + wComplementary * complementary);

            Double semantic = null;
            double finalScore = hybrid;
            if (useSemantic) {
                Double cos = semanticScore(projectVec, cachedEmbed(embeddingCache,
                        "student:" + s.getId(), studentText(s)));
                if (cos != null) {
                    semantic = cos;
                    finalScore = round4((1.0 - semanticBlend) * hybrid + semanticBlend * cos);
                }
            }

            long endorsements = 0;
            try {
                for (String sk : matched) endorsements += endorsementRepository.countByEndorseeIdAndSkillIgnoreCase(s.getId(), sk);
            } catch (Exception ignored) {}
            Double avgRating = avgRating(s.getId());
            boolean reported = hasOpenReport(s.getId());

            out.add(new ScoredCandidate(s.getId(), s.getName(), s.getBranch(), s.getYear(),
                    s.getSkills(), s.getAvailability(), matched,
                    round4(skillMatch), round4(interest), round4(avail),
                    round4(complementary), semantic, finalScore,
                    endorsements, avgRating, reported));
        }

        // Sort: score desc → endorsements desc → reported sinks → AVAILABLE first → name.
        out.sort(Comparator
                .comparingDouble(ScoredCandidate::score).reversed()
                .thenComparingLong(ScoredCandidate::endorsements).reversed()
                .thenComparing(c -> c.hasOpenReport() ? 1 : 0)
                .thenComparing(c -> "AVAILABLE".equalsIgnoreCase(c.availability()) ? 0 : 1)
                .thenComparing(c -> c.name() == null ? "" : c.name().toLowerCase(Locale.ROOT)));
        if (out.size() > 10) return out.subList(0, 10);
        return out;
    }

    // ——— component scorers (all 0..1, package-visible for unit checks) ———

    double interestScore(Set<String> projectTokens, String interestsRaw) {
        if (projectTokens == null || projectTokens.isEmpty()) return 0.0;
        Set<String> interests = splitComma(interestsRaw).stream()
                .map(x -> { String n = skillGraphService.normalize(x); if (n == null) n = x; return n.toLowerCase(Locale.ROOT); })
                .collect(Collectors.toSet());
        if (interests.isEmpty()) return 0.0;
        int hit = 0;
        for (String tok : projectTokens) {
            for (String in : interests) {
                if (in.equals(tok) || in.contains(tok) || tok.contains(in)) { hit++; break; }
            }
        }
        return (double) hit / projectTokens.size();
    }

    static double availabilityScore(String availability) {
        if (availability == null || availability.isBlank()) return 0.5;
        if ("AVAILABLE".equalsIgnoreCase(availability.trim())) return 1.0;
        return 0.2;
    }

    static double complementaryScore(Set<String> studentLower, Set<String> gapLower,
                                     Map<String, Set<String>> relatedByGap) {
        if (studentLower == null || studentLower.isEmpty()) return 0.0;
        Set<String> extras = new HashSet<>(studentLower);
        extras.removeAll(gapLower);
        if (extras.isEmpty()) return 0.0;
        Set<String> allRelated = new HashSet<>();
        for (Set<String> r : relatedByGap.values()) allRelated.addAll(r);
        int relatedCount = 0;
        for (String e : extras) {
            if (allRelated.contains(e)) relatedCount++;
        }
        return Math.min(1.0, relatedCount / 3.0);
    }

    // ——— helpers ———

    private Set<String> projectTokens(Project p) {
        Set<String> tokens = new HashSet<>();
        for (String rs : splitComma(p.getRequiredSkills())) {
            String n = skillGraphService.normalize(rs);
            if (n == null) n = rs;
            tokens.add(n.toLowerCase(Locale.ROOT));
        }
        if (p.getDomain() != null) {
            for (String w : p.getDomain().toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
                if (w.length() >= 3) tokens.add(w);
            }
        }
        return tokens;
    }

    private Set<String> relatedSkillsLower(String gapLower) {
        try {
            // getRelatedSkills needs a canonical name; normalize back from lowercase.
            String canon = skillGraphService.normalize(gapLower);
            if (canon == null) canon = gapLower;
            return skillGraphService.getRelatedSkills(canon, 8).stream()
                    .map(r -> r.skillName().toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            return Set.of();
        }
    }

    private Double semanticScore(float[] projectVec, float[] studentVec) {
        if (projectVec == null || studentVec == null || projectVec.length != studentVec.length || projectVec.length == 0) return null;
        double dot = 0, np = 0, ns = 0;
        for (int i = 0; i < projectVec.length; i++) {
            dot += (double) projectVec[i] * studentVec[i];
            np += (double) projectVec[i] * projectVec[i];
            ns += (double) studentVec[i] * studentVec[i];
        }
        if (np == 0 || ns == 0) return null;
        double cos = dot / (Math.sqrt(np) * Math.sqrt(ns));
        return round4(Math.min(1.0, Math.max(0.0, (cos + 1.0) / 2.0)));
    }

    private float[] cachedEmbed(Map<String, float[]> cache, String key, String text) {
        if (text == null || text.isBlank()) return null;
        if (cache != null && cache.containsKey(key)) return cache.get(key);
        try {
            float[] v = embeddingService.embed(text);
            if (cache != null) cache.put(key, v);
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    private static String projectText(Project p) {
        StringBuilder sb = new StringBuilder();
        if (p.getName() != null) sb.append(p.getName()).append(' ');
        if (p.getDomain() != null) sb.append(p.getDomain()).append(' ');
        if (p.getRequiredSkills() != null) sb.append(p.getRequiredSkills()).append(' ');
        if (p.getDescription() != null) sb.append(p.getDescription());
        return sb.toString().trim();
    }

    private static String studentText(Student s) {
        StringBuilder sb = new StringBuilder();
        if (s.getSkills() != null) sb.append(s.getSkills()).append(' ');
        if (s.getInterests() != null) sb.append(s.getInterests()).append(' ');
        if (s.getBio() != null) sb.append(s.getBio());
        return sb.toString().trim();
    }

    private Double avgRating(Long studentId) {
        try {
            var reviews = reviewRepository.findByRevieweeId(studentId);
            if (reviews == null || reviews.isEmpty()) return null;
            double avg = reviews.stream().filter(r -> r.getRating() != null)
                    .mapToInt(r -> r.getRating()).average().orElse(Double.NaN);
            if (Double.isNaN(avg)) return null;
            return Math.round(avg * 10) / 10.0;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean hasOpenReport(Long studentId) {
        try {
            return !moderationRepository.findByTargetTypeAndTargetIdOrderByCreatedAtDesc("STUDENT", studentId)
                    .stream().filter(r -> "OPEN".equals(r.getStatus())).toList().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static List<String> splitComma(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String part : csv.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
