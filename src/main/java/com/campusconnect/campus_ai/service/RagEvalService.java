package com.campusconnect.campus_ai.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * RAG evaluation harness (no new deps, admin-gated endpoint).
 *
 * Curated Q/A set over the seeded library (DevSeedController documents):
 * each case names the expected document title (retrieval ground truth),
 * required answer keywords + numbers (correctness), and whether the question
 * is answerable at all (abstention test).
 *
 * Per question it runs two retrieval arms — basic (fusion, no rerank) vs
 * improved (hybrid + rerank) — and reports:
 *   retrieval: Recall@K per arm + win/tie/loss comparison
 *   generation: full /ask answers scored for correctness, faithfulness
 *     (AskService.isFaithful), citation presence, and latency
 *   abstention: unanswerable questions must yield answered=false
 *
 * Answers run through the real pipeline (Ollama), so run on Windows with
 * Ollama up; retrieval-only mode skips generation for fast iteration.
 */
@Service
public class RagEvalService {

    public record EvalCase(String question, String expectedDocument, List<String> keywords,
                           List<String> numbers, boolean answerable) {}
    public record ArmResult(String arm, boolean recalled, List<String> topTitles,
                            boolean answered, boolean correct, boolean faithful,
                            boolean cited, Long latencyMs, String answerPreview) {}
    public record CaseReport(String question, String expectedDocument, boolean answerable,
                             ArmResult basic, ArmResult improved) {}
    public record Verdict(String winner, String reason) {}
    public record EvalSummary(int totalCases, int answerable, int unanswerable,
                              double recallAtKBasic, double recallAtKImproved,
                              double correctnessBasic, double correctnessImproved,
                              double faithfulnessBasic, double faithfulnessImproved,
                              double citationRateBasic, double citationRateImproved,
                              double precisionAtKBasic, double precisionAtKImproved,
                              double abstentionRate, double avgLatencyBasicMs,
                              double avgLatencyImprovedMs, int improvedWins,
                              int ties, int basicWins, Verdict verdict,
                              List<CaseReport> cases) {}

    private static final List<EvalCase> CASES = List.of(
            new EvalCase("How many students can be in a hackathon team?",
                    "SAMPLE - Hackathon Rules", List.of("team", "students"),
                    List.of("2", "4"), true),
            new EvalCase("What happens to plagiarised or pre-built hackathon projects?",
                    "SAMPLE - Hackathon Rules", List.of("disqualification"),
                    List.of(), true),
            new EvalCase("How much attendance is needed to be eligible for semester exams?",
                    "SAMPLE - Attendance Policy", List.of("attendance"),
                    List.of("75"), true),
            new EvalCase("What is needed for medical leave absence?",
                    "SAMPLE - Attendance Policy", List.of("medical", "certificate"),
                    List.of(), true),
            new EvalCase("Who can attend campus placement drives?",
                    "SAMPLE - Placement Guidelines", List.of("backlogs", "eligible"),
                    List.of(), true),
            new EvalCase("When does technical fest registration close?",
                    "SAMPLE - Technical Fest Information", List.of("registration", "week"),
                    List.of(), true),
            // Unanswerable: in-scope phrasing, nothing in the library.
            new EvalCase("What is the hostel curfew timing on weekends?",
                    null, List.of(), List.of(), false),
            new EvalCase("How do I apply for a semester exchange program?",
                    null, List.of(), List.of(), false),
            // Out-of-scope entirely.
            new EvalCase("Tell me a joke about databases.",
                    null, List.of(), List.of(), false));

    private final SearchService searchService;
    private final AskService askService;
    private final int topK;

    public RagEvalService(SearchService searchService, AskService askService,
                          @Value("${rag.top-k}") int topK) {
        this.searchService = searchService;
        this.askService = askService;
        this.topK = topK;
    }

    public List<Map<String, Object>> cases() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (EvalCase c : CASES) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("question", c.question());
            m.put("expectedDocument", c.expectedDocument());
            m.put("keywords", c.keywords());
            m.put("numbers", c.numbers());
            m.put("answerable", c.answerable());
            out.add(m);
        }
        return out;
    }

    public EvalSummary run(boolean retrievalOnly) {
        List<CaseReport> reports = new ArrayList<>();
        int recallBasic = 0, recallImproved = 0;
        int correctBasic = 0, correctImproved = 0;
        int faithfulBasic = 0, faithfulImproved = 0;
        int citedBasic = 0, citedImproved = 0;
        int answeredBasic = 0, answeredImproved = 0;
        int answeredAnswerable = 0, answerableCount = 0;
        int abstainedUnanswerable = 0, unanswerableCount = 0;
        double precisionSumBasic = 0.0, precisionSumImproved = 0.0;
        int answerableTotal = (int) CASES.stream().filter(EvalCase::answerable).count();
        long latBasic = 0, latImproved = 0;
        int genCount = 0;
        int wins = 0, ties = 0, losses = 0;

        for (EvalCase c : CASES) {
            int k = Math.max(topK, 3);
            List<SearchService.SearchResult> basicHits = searchService.searchWithoutRerank(c.question(), k);
            List<SearchService.SearchResult> improvedHits = searchService.search(c.question(), k);

            boolean recB = recalled(basicHits, c.expectedDocument());
            boolean recI = recalled(improvedHits, c.expectedDocument());
            if (recB) recallBasic++;
            if (recI) recallImproved++;
            if (c.expectedDocument() != null) {
                precisionSumBasic += precisionAtK(basicHits, c.expectedDocument());
                precisionSumImproved += precisionAtK(improvedHits, c.expectedDocument());
            }
            if (recB && recI) ties++;
            else if (recI) wins++;
            else if (recB) losses++;
            else ties++;

            ArmResult basic;
            ArmResult improved;
            if (retrievalOnly) {
                basic = new ArmResult("basic", recB, titles(basicHits),
                        false, false, false, false, null, null);
                improved = new ArmResult("improved", recI, titles(improvedHits),
                        false, false, false, false, null, null);
            } else {
                // Per-arm generation over fixed hits: same prompt + faithfulness path,
                // so basic vs improved differ only in their retrieval ordering.
                // (LLM cost ×2 per case — acceptable for a 9-case harness.)
                long t0 = System.currentTimeMillis();
                AskService.AskResponse rB = askService.answerWithHits(c.question(), null, basicHits);
                long lb = System.currentTimeMillis() - t0;
                long t1 = System.currentTimeMillis();
                AskService.AskResponse rI = askService.answerWithHits(c.question(), null, improvedHits);
                long li = System.currentTimeMillis() - t1;

                // askWith reuses one retrieval's hits; add both latencies to their own arms.
                // The second call doubles LLM cost — acceptable for a 9-case harness.
                boolean corrB = rB.answered() && correct(rB.answer(), c);
                boolean corrI = rI.answered() && correct(rI.answer(), c);
                boolean faithB = rB.answered() && askService.isFaithful(rB.answer(), basicHits);
                boolean faithI = rI.answered() && askService.isFaithful(rI.answer(), improvedHits);
                boolean citeB = cited(rB.answer());
                boolean citeI = cited(rI.answer());
                basic = new ArmResult("basic", recB, titles(basicHits),
                        rB.answered(), corrB, faithB, citeB, lb, preview(rB.answer()));
                improved = new ArmResult("improved", recI, titles(improvedHits),
                        rI.answered(), corrI, faithI, citeI, li, preview(rI.answer()));

                if (corrB) correctBasic++;
                if (corrI) correctImproved++;
                if (faithB) faithfulBasic++;
                if (faithI) faithfulImproved++;
                if (citeB) citedBasic++;
                if (citeI) citedImproved++;
                if (rB.answered()) answeredBasic++;
                if (rI.answered()) answeredImproved++;
                latBasic += lb;
                latImproved += li;
                genCount++;
                if (c.answerable()) {
                    answerableCount++;
                    if (rI.answered()) answeredAnswerable++;
                } else {
                    unanswerableCount++;
                    if (!rI.answered()) abstainedUnanswerable++;
                }
            }
            reports.add(new CaseReport(c.question(), c.expectedDocument(), c.answerable(), basic, improved));
        }

        int n = CASES.size();
        double citB = genCount == 0 ? 0.0 : round3((double) citedBasic / genCount);
        double citI = genCount == 0 ? 0.0 : round3((double) citedImproved / genCount);
        double precB = answerableTotal == 0 ? 0.0 : round3(precisionSumBasic / answerableTotal);
        double precI = answerableTotal == 0 ? 0.0 : round3(precisionSumImproved / answerableTotal);
        Verdict v = verdict(wins, losses,
                genCount == 0 ? 0.0 : (double) faithfulImproved / genCount,
                genCount == 0 ? 0.0 : (double) faithfulBasic / genCount);
        return new EvalSummary(
                n, (int) CASES.stream().filter(EvalCase::answerable).count(),
                (int) CASES.stream().filter(c -> !c.answerable()).count(),
                round3((double) recallBasic / n), round3((double) recallImproved / n),
                genCount == 0 ? 0.0 : round3((double) correctBasic / n),
                genCount == 0 ? 0.0 : round3((double) correctImproved / n),
                genCount == 0 ? 0.0 : round3((double) faithfulBasic / n),
                genCount == 0 ? 0.0 : round3((double) faithfulImproved / n),
                citB, citI, precB, precI,
                unanswerableCount == 0 ? 0.0 : round3((double) abstainedUnanswerable / unanswerableCount),
                genCount == 0 ? 0.0 : round3((double) latBasic / genCount),
                genCount == 0 ? 0.0 : round3((double) latImproved / genCount),
                wins, ties, losses, v, reports);
    }

    private static boolean recalled(List<SearchService.SearchResult> hits, String expected) {
        if (expected == null) return false;
        for (var h : hits) {
            if (expected.equals(h.documentTitle())) return true;
        }
        return false;
    }

    /** Fraction of top-K hits from the expected document (retrieval precision). */
    private static double precisionAtK(List<SearchService.SearchResult> hits, String expected) {
        if (hits == null || hits.isEmpty() || expected == null) return 0.0;
        long match = hits.stream().filter(h -> expected.equals(h.documentTitle())).count();
        return (double) match / hits.size();
    }

    /**
     * Honest verdict: improved wins only if it beats basic on retrieval wins
     * with faithfulness not worse; otherwise keep the baseline.
     */
    private static Verdict verdict(int wins, int losses, double faithImproved, double faithBasic) {
        if (wins > losses && faithImproved >= faithBasic) {
            return new Verdict("improved",
                    "hybrid+rerank wins retrieval (" + wins + " vs " + losses + ") with faithfulness not worse");
        }
        if (losses > wins) {
            return new Verdict("basic", "fusion baseline wins retrieval (" + losses + " vs " + wins + ") — keep baseline");
        }
        return new Verdict("tie", "no retrieval separation (" + wins + " vs " + losses + ") — keep baseline until an arm beats it");
    }

    private static List<String> titles(List<SearchService.SearchResult> hits) {
        List<String> out = new ArrayList<>();
        for (var h : hits) out.add(h.documentTitle());
        return out;
    }

    private static boolean correct(String answer, EvalCase c) {
        if (answer == null || answer.isBlank()) return false;
        String norm = answer.toLowerCase(Locale.ROOT);
        for (String kw : c.keywords()) {
            if (!norm.contains(kw.toLowerCase(Locale.ROOT))) return false;
        }
        String digits = norm.replaceAll("[^0-9.]", " ");
        for (String num : c.numbers()) {
            if (!digits.contains(num)) return false;
        }
        return true;
    }

    private static boolean cited(String answer) {
        if (answer == null) return false;
        return java.util.regex.Pattern.compile("\\[\\d+\\]").matcher(answer).find();
    }

    private static String preview(String answer) {
        if (answer == null) return null;
        return answer.length() > 220 ? answer.substring(0, 220) + "…" : answer;
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
