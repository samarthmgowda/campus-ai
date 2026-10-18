package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Project;
import com.campusconnect.campus_ai.entity.ProjectMember;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.ProjectMemberRepository;
import com.campusconnect.campus_ai.repository.ProjectRepository;
import com.campusconnect.campus_ai.repository.ReviewRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Teammate recommendation evaluation (research track, Task #40).
 *
 * Three-way comparison on the SAME held-out cases — no superiority claim
 * without results:
 *   arm "overlap":  ProjectToolService.searchStudents baseline
 *                   (gap overlap + endorsement/open-report/availability sort)
 *   arm "hybrid":   TeammateMatchingService weighted S (no semantic)
 *   arm "semantic": TeammateMatchingService weighted S blended with bge-m3
 *                   cosine (falls back to hybrid per-candidate if Ollama is down)
 *
 * Protocol: leave-one-out over ACTIVE projects with >= 2 members and
 * non-empty required skills. For each held-out member, recompute skill gaps
 * from the REMAINING members only, rank with remaining member ids excluded,
 * and check whether the held-out member appears in the top K (K=3 and K=5).
 * With exactly one relevant student per case, hit@K IS Precision@K and Recall@K.
 *
 * Cases where the held-out member cannot pass hard filters (BUSY with
 * availableOnly=true, suspended) are skipped and counted — they are not
 * failures of any arm, since no arm is allowed to return them.
 *
 * Student-rating signal: for each arm, the average review rating (1-5) of the
 * recommended top-K candidates that HAVE reviews, plus what fraction of
 * recommendations carry any rating at all (coverage). This measures whether an
 * arm surfaces trusted students, orthogonal to hit@K.
 *
 * Run on Windows with data seeded (prototype import + POST /dev/seed-projects):
 * retrieval-only except the semantic arm, which needs Ollama up for bge-m3.
 */
@Service
public class TeammateEvalService {

    public record ArmMetrics(String arm, int cases, double hitAt3, double hitAt5,
                             double meanRank, double avgRatingTop3, double ratingCoverageTop3,
                             boolean semanticUsed) {}
    public record CaseReport(String projectName, String heldOutStudent,
                             List<String> gaps, int rankOverlap, int rankHybrid,
                             int rankSemantic) {}
    public record Verdict(String winner, String reason) {}
    public record TeammateEvalSummary(int projects, int cases, int skippedFiltered,
                                      List<ArmMetrics> arms, Verdict verdict,
                                      List<CaseReport> detail) {}

    private static final int K3 = 3;
    private static final int K5 = 5;

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository memberRepository;
    private final ProjectToolService projectToolService;
    private final TeammateMatchingService matchingService;
    private final ReviewRepository reviewRepository;

    public TeammateEvalService(ProjectRepository projectRepository,
                               ProjectMemberRepository memberRepository,
                               ProjectToolService projectToolService,
                               TeammateMatchingService matchingService,
                               ReviewRepository reviewRepository) {
        this.projectRepository = projectRepository;
        this.memberRepository = memberRepository;
        this.projectToolService = projectToolService;
        this.matchingService = matchingService;
        this.reviewRepository = reviewRepository;
    }

    public TeammateEvalSummary run(boolean availableOnly, boolean includeSemantic) {
        List<CaseReport> detail = new ArrayList<>();
        List<Integer> ranksOverlap = new ArrayList<>();
        List<Integer> ranksHybrid = new ArrayList<>();
        List<Integer> ranksSemantic = new ArrayList<>();
        // Rating signal accumulators per arm (candidate id lists per case).
        List<List<Long>> top3Overlap = new ArrayList<>();
        List<List<Long>> top3Hybrid = new ArrayList<>();
        List<List<Long>> top3Semantic = new ArrayList<>();
        boolean anySemantic = false;
        int skipped = 0;
        int projects = 0;
        Map<String, float[]> embedCache = new HashMap<>();

        for (Project p : projectRepository.findAll()) {
            if (!"ACTIVE".equalsIgnoreCase(p.getStatus())) continue;
            if ("HIDDEN".equalsIgnoreCase(p.getStatus())) continue;
            List<String> required = splitComma(p.getRequiredSkills());
            if (required.isEmpty()) continue;
            List<ProjectMember> members = memberRepository.findByProjectId(p.getId());
            if (members.size() < 2) continue;
            projects++;

            for (ProjectMember heldOut : members) {
                Student h = heldOut.getStudent();
                if (h == null || h.getId() == null) { skipped++; continue; }
                // Hard-filter parity: arms may not return these, so they can't be cases.
                if (h.isSuspended()) { skipped++; continue; }
                if (availableOnly && "BUSY".equalsIgnoreCase(h.getAvailability())) { skipped++; continue; }

                // Gaps from REMAINING members only (canonical, case-insensitive).
                Set<String> remainingCanon = new HashSet<>();
                Set<Long> remainingIds = new HashSet<>();
                for (ProjectMember m : members) {
                    if (m.getStudent() == null || m.getStudent().getId().equals(h.getId())) continue;
                    remainingIds.add(m.getStudent().getId());
                    for (String s : splitComma(m.getStudent().getSkills())) {
                        remainingCanon.add(s.trim().toLowerCase(Locale.ROOT));
                    }
                }
                List<String> gaps = new ArrayList<>();
                for (String req : required) {
                    if (!remainingCanon.contains(req.trim().toLowerCase(Locale.ROOT))) gaps.add(req);
                }
                if (gaps.isEmpty()) { skipped++; continue; }

                // Held-out must cover at least one gap skill, else the case is
                // unretrievable by design (they joined for another skill).
                Set<String> heldLower = splitComma(h.getSkills()).stream()
                        .map(s -> s.trim().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
                boolean coversGap = gaps.stream()
                        .anyMatch(g -> heldLower.contains(g.trim().toLowerCase(Locale.ROOT)));
                if (!coversGap) { skipped++; continue; }

                // Arm 1: overlap baseline (same availableOnly + exclusion semantics).
                List<ProjectToolService.CandidateMatch> base =
                        projectToolService.searchStudents(gaps, availableOnly, remainingIds);
                // Arms 2-3: weighted (exclude = remaining members; held-out stays eligible).
                List<TeammateMatchingService.ScoredCandidate> hybrid =
                        matchingService.rankCandidates(p, gaps, remainingIds, availableOnly, false, null);
                List<TeammateMatchingService.ScoredCandidate> semantic = List.of();
                if (includeSemantic) {
                    semantic = matchingService.rankCandidates(p, gaps, remainingIds, availableOnly, true, embedCache);
                    if (semantic.stream().anyMatch(c -> c.semantic() != null)) anySemantic = true;
                }

                int rO = rankOf(base.stream().map(ProjectToolService.CandidateMatch::studentId).toList(), h.getId());
                int rH = rankOf(hybrid.stream().map(TeammateMatchingService.ScoredCandidate::studentId).toList(), h.getId());
                int rS = includeSemantic
                        ? rankOf(semantic.stream().map(TeammateMatchingService.ScoredCandidate::studentId).toList(), h.getId())
                        : -1;
                ranksOverlap.add(rO);
                ranksHybrid.add(rH);
                if (includeSemantic) ranksSemantic.add(rS);

                top3Overlap.add(topIds(base.stream().map(ProjectToolService.CandidateMatch::studentId).toList(), K3));
                top3Hybrid.add(topIds(hybrid.stream().map(TeammateMatchingService.ScoredCandidate::studentId).toList(), K3));
                if (includeSemantic) top3Semantic.add(topIds(semantic.stream().map(TeammateMatchingService.ScoredCandidate::studentId).toList(), K3));

                detail.add(new CaseReport(p.getName(), h.getName(), gaps, rO, rH, rS));
            }
        }

        List<ArmMetrics> arms = new ArrayList<>();
        arms.add(new ArmMetrics("overlap", ranksOverlap.size(),
                hitAt(ranksOverlap, K3), hitAt(ranksOverlap, K5), meanRank(ranksOverlap),
                avgRating(top3Overlap), ratingCoverage(top3Overlap), false));
        arms.add(new ArmMetrics("hybrid", ranksHybrid.size(),
                hitAt(ranksHybrid, K3), hitAt(ranksHybrid, K5), meanRank(ranksHybrid),
                avgRating(top3Hybrid), ratingCoverage(top3Hybrid), false));
        if (includeSemantic) {
            arms.add(new ArmMetrics("hybrid+semantic", ranksSemantic.size(),
                    hitAt(ranksSemantic, K3), hitAt(ranksSemantic, K5), meanRank(ranksSemantic),
                    avgRating(top3Semantic), ratingCoverage(top3Semantic), anySemantic));
        }
        return new TeammateEvalSummary(projects, ranksOverlap.size(), skipped, arms, verdict(arms), detail);
    }

    /**
     * Verdict rule: keep the overlap baseline in /ask routing until an arm
     * beats it on hit@3 with rating coverage not worse.
     */
    static Verdict verdict(List<ArmMetrics> arms) {
        ArmMetrics base = null;
        for (ArmMetrics a : arms) {
            if ("overlap".equals(a.arm())) { base = a; break; }
        }
        if (base == null) return new Verdict("overlap", "no baseline arm — keep overlap default");
        ArmMetrics best = null;
        for (ArmMetrics a : arms) {
            if ("overlap".equals(a.arm())) continue;
            if (a.cases() == 0) continue;
            if (a.hitAt3() > base.hitAt3() && a.ratingCoverageTop3() >= base.ratingCoverageTop3()) {
                if (best == null || a.hitAt3() > best.hitAt3()) best = a;
            }
        }
        if (best == null) {
            return new Verdict("overlap", "no arm beats overlap on hit@3 with coverage not worse — keep baseline");
        }
        return new Verdict(best.arm(), best.arm() + " beats overlap on hit@3 (" + best.hitAt3()
                + " vs " + base.hitAt3() + ") with coverage " + best.ratingCoverageTop3()
                + " vs " + base.ratingCoverageTop3());
    }

    // ——— metric helpers (package-visible for unit checks) ———

    /** 1-based rank of target in ordered id list, or -1 if absent. */
    static int rankOf(List<Long> orderedIds, Long target) {
        for (int i = 0; i < orderedIds.size(); i++) {
            if (orderedIds.get(i) != null && orderedIds.get(i).equals(target)) return i + 1;
        }
        return -1;
    }

    /** Fraction of cases with 1 <= rank <= k (misses rank as -1). */
    static double hitAt(List<Integer> ranks, int k) {
        if (ranks.isEmpty()) return 0.0;
        long hits = ranks.stream().filter(r -> r >= 1 && r <= k).count();
        return round3((double) hits / ranks.size());
    }

    /** Mean rank over hits only; 0.0 when no hits (reported alongside hit@K). */
    static double meanRank(List<Integer> ranks) {
        var hits = ranks.stream().filter(r -> r >= 1).toList();
        if (hits.isEmpty()) return 0.0;
        return round3(hits.stream().mapToInt(Integer::intValue).average().orElse(0.0));
    }

    private static List<Long> topIds(List<Long> ordered, int k) {
        return ordered.stream().limit(k).toList();
    }

    /** Mean review rating over recommended candidates that have reviews. */
    double avgRating(List<List<Long>> topKPerCase) {
        double sum = 0;
        int n = 0;
        for (List<Long> ids : topKPerCase) {
            for (Long id : ids) {
                Double avg = avgRatingOf(id);
                if (avg != null) { sum += avg; n++; }
            }
        }
        if (n == 0) return 0.0;
        return round3(sum / n);
    }

    /** Fraction of recommended candidates carrying at least one review. */
    double ratingCoverage(List<List<Long>> topKPerCase) {
        int total = 0, rated = 0;
        for (List<Long> ids : topKPerCase) {
            for (Long id : ids) {
                total++;
                try {
                    if (!reviewRepository.findByRevieweeId(id).isEmpty()) rated++;
                } catch (Exception ignored) {}
            }
        }
        if (total == 0) return 0.0;
        return round3((double) rated / total);
    }

    private Double avgRatingOf(Long studentId) {
        try {
            var reviews = reviewRepository.findByRevieweeId(studentId);
            if (reviews == null || reviews.isEmpty()) return null;
            double avg = reviews.stream().filter(r -> r.getRating() != null)
                    .mapToInt(r -> r.getRating()).average().orElse(Double.NaN);
            return Double.isNaN(avg) ? null : avg;
        } catch (Exception e) {
            return null;
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

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
