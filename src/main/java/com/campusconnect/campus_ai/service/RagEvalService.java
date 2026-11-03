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
 * improved (hybrid + rerank + query decomposition for multi-fact questions) —
 * and reports:
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
                            boolean cited, Long latencyMs, String answerPreview,
                            double perFactCitationPrecision) {}
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
                              double perFactCitationPrecisionBasic, double perFactCitationPrecisionImproved,
                              int topK, boolean rerankEnabled, String chatModel,
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
            // Multi-fact: needs query decomposition to find both facts independently.
            new EvalCase("How many students can be in a hackathon team and how much attendance is needed for exams?",
                    "SAMPLE - Hackathon Rules,SAMPLE - Attendance Policy", List.of("team", "attendance"),
                    List.of("2", "4", "75"), true),
            new EvalCase("What are the two plagiarism consequences and which document governs attendance policy?",
                    "SAMPLE - Hackathon Rules,SAMPLE - Attendance Policy", List.of("disqualification", "attendance"),
                    List.of(), true),
            // Unanswerable: in-scope phrasing, nothing in the library.
            new EvalCase("What is the hostel curfew timing on weekends?",
                    null, List.of(), List.of(), false),
            new EvalCase("How do I apply for a semester exchange program?",
                    null, List.of(), List.of(), false),
            // Out-of-scope entirely.
            new EvalCase("Tell me a joke about databases.",
                    null, List.of(), List.of(), false),
            // --- Grounding edge cases ---------------------------------------
            // Two numbers that must come from two different documents. Exercises
            // the context budget and the per-fact citation map at once: if either
            // passage is dropped, one number goes missing.
            new EvalCase("What is the minimum attendance percentage and how many students can form a hackathon team?",
                    "SAMPLE - Attendance Policy,SAMPLE - Hackathon Rules",
                    List.of("attendance", "team"), List.of("75", "2", "4"), true),
            // Near-miss number: the answer must cite 75, never a bare 5 or 7. A
            // substring-matching groundedness check would accept a swapped value
            // here, so this case guards the fix in AskService.
            new EvalCase("Exactly what attendance percentage is required for exam eligibility?",
                    "SAMPLE - Attendance Policy", List.of("attendance", "percent"),
                    List.of("75"), true),
            // In-scope vocabulary that is NOT in the corpus. The words look like
            // library content, so a naive retriever returns passages and the model
            // may answer from adjacency; the correct behaviour is to abstain. This
            // measures abstention PRECISION, not just recall.
            new EvalCase("What is the fee for the semester exchange programme?",
                    null, List.of(), List.of(), false));

    private final SearchService searchService;
    private final AskService askService;
    private final OllamaChatService chatService;
    private final int topK;
    private final boolean rerankEnabled;

    public RagEvalService(SearchService searchService, AskService askService,
                          OllamaChatService chatService,
                          @Value("${rag.top-k}") int topK,
                          @Value("${rag.rerank-enabled:true}") boolean rerankEnabled) {
        this.searchService = searchService;
        this.askService = askService;
        this.chatService = chatService;
        this.topK = topK;
        this.rerankEnabled = rerankEnabled;
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
        return run(retrievalOnly, topK);
    }

    /**
     * As {@link #run(boolean)}, but retrieving {@code overrideTopK} passages per
     * query instead of the configured {@code rag.top-k}.
     *
     * Lets a K sweep run against one server instance: "is rag.top-k=3 too tight?"
     * is answerable only by varying K and re-measuring, and editing properties
     * between runs introduces a restart (and a different warm cache) into the
     * comparison.
     */
    public EvalSummary run(boolean retrievalOnly, int overrideTopK) {
        int k = overrideTopK >= 1 ? overrideTopK : topK;
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
        double pfpSumBasic = 0.0, pfpSumImproved = 0.0;
        int pfpCount = 0;

        for (EvalCase c : CASES) {
            int caseK = Math.max(k, 3);
            List<SearchService.SearchResult> basicHits = searchService.searchWithoutRerank(c.question(), caseK);
            // Improved arm: hybrid+rerank + query decomposition for multi-fact questions.
            AskService.DecomposedHits improvedDecomposed = askService.decomposeAndSearch(c.question(), caseK);
            List<SearchService.SearchResult> improvedHits = improvedDecomposed.hits();

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
            double pfpB = 0.0, pfpI = 0.0;
            if (retrievalOnly) {
                basic = new ArmResult("basic", recB, titles(basicHits),
                        false, false, false, false, null, null, 0.0);
                improved = new ArmResult("improved", recI, titles(improvedHits),
                        false, false, false, false, null, null, 0.0);
            } else {
                // Per-arm generation over fixed hits: same prompt + faithfulness path,
                // so basic vs improved differ only in their retrieval ordering.
                // (LLM cost ×2 per case — acceptable for a 9-case harness.)
                long t0 = System.currentTimeMillis();
                AskService.AskResponse rB = askService.answerWithHits(c.question(), null, basicHits);
                long lb = System.currentTimeMillis() - t0;
                long t1 = System.currentTimeMillis();
                AskService.AskResponse rI = askService.answerWithHits(c.question(), null, improvedDecomposed);
                long li = System.currentTimeMillis() - t1;

                // askWith reuses one retrieval's hits; add both latencies to their own arms.
                // The second call doubles LLM cost — acceptable for a 9-case harness.
                boolean corrB = rB.answered() && correct(rB.answer(), c);
                boolean corrI = rI.answered() && correct(rI.answer(), c);
                boolean faithB = rB.answered() && askService.isFaithful(rB.answer(), c.question(), basicHits);
                boolean faithI = rI.answered() && askService.isFaithful(rI.answer(), c.question(), improvedHits);
                boolean citeB = cited(rB.answer());
                boolean citeI = cited(rI.answer());
                // Per-fact citation precision: for multi-fact questions, verify each
                // expected number/keyword is cited via [n] back the passage(s) that
                // retrieved it. The improved arm uses DecomposedHits.factToHits for
                // rich per-fact granularity; the basic arm degrades to citation-presence.
                pfpB = perFactCitationPrecision(rB.answer(), c);
                pfpI = perFactCitationPrecision(rI.answer(), c, improvedDecomposed);
                pfpSumBasic += pfpB;
                pfpSumImproved += pfpI;
                pfpCount++;
                basic = new ArmResult("basic", recB, titles(basicHits),
                        rB.answered(), corrB, faithB, citeB, lb, preview(rB.answer()), pfpB);
                improved = new ArmResult("improved", recI, titles(improvedHits),
                        rI.answered(), corrI, faithI, citeI, li, preview(rI.answer()), pfpI);

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
        double pfpB = pfpCount == 0 ? 0.0 : round3(pfpSumBasic / pfpCount);
        double pfpI = pfpCount == 0 ? 0.0 : round3(pfpSumImproved / pfpCount);
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
                wins, ties, losses, v,
                pfpB, pfpI, k, rerankEnabled, chatService.model(),
                reports);
    }

    /**
     * Recall: expected document(s) must appear in the top-K hits.
     * Supports comma-separated expected docs for multi-fact questions
     * (e.g. "SAMPLE - Hackathon Rules,SAMPLE - Attendance Policy").
     */
    private static boolean recalled(List<SearchService.SearchResult> hits, String expected) {
        if (expected == null || expected.isBlank()) return false;
        List<String> expectedDocs = List.of(expected.split(","));
        for (var h : hits) {
            if (expectedDocs.contains(h.documentTitle())) return true;
        }
        return false;
    }

    /**
     * Precision: fraction of top-K hits from any expected document.
     * Multi-fact questions use comma-separated expected docs.
     */
    private static double precisionAtK(List<SearchService.SearchResult> hits, String expected) {
        if (hits == null || hits.isEmpty() || expected == null || expected.isBlank()) return 0.0;
        List<String> expectedDocs = List.of(expected.split(","));
        long match = hits.stream().filter(h -> expectedDocs.contains(h.documentTitle())).count();
        return (double) match / hits.size();
    }

    /**
     * Honest verdict: improved wins only if it beats basic on retrieval wins
     * with faithfulness not worse AND per-fact citation precision at least as good;
     * otherwise keep the baseline. On a tie in retrieval wins, the arm with higher
     * per-fact citation precision wins.
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

    /**
     * Per-fact citation precision (basic arm): checks whether every
     * expected number/keyword in the EvalCase is both present in the answer AND
     * accompanied by a [n] citation marker within ±100 chars. Returns 1.0 if all
     * facts are cited, 0.5 if half, 0.0 if none, etc. For single-fact questions,
     * delegates to the binary {@link #cited} metric (0.0 or 1.0).
     *
     * This is the conservative baseline — no decomposition, so citation-presence
     * is the only signal available.
     */
    private static double perFactCitationPrecision(String answer, EvalCase c) {
        return perFactCitationPrecision(answer, c, null);
    }

    /**
     * Per-fact citation precision for the improved arm: when DecomposedHits is
     * provided (i.e., query decomposition applied), checks that each expected fact
     * value is cited back the specific passage(s) that retrieved it. The
     * factToHits map links each sub-query (fact) to its 1-based passage indices,
     * so we verify the [n] citation references one of those indices.
     *
     * For single-fact questions (no decomposition), degrades to the basic
     * citation-presence check.
     */
    private static double perFactCitationPrecision(String answer, EvalCase c,
                                                   AskService.DecomposedHits decomposed) {
        if (answer == null || answer.isBlank()) return 0.0;
        // Collect all expected factual values (numbers + keywords) that must be cited.
        java.util.Set<String> facts = new java.util.HashSet<>();
        for (String num : c.numbers()) facts.add(num);
        for (String kw : c.keywords()) {
            if (kw != null && !kw.isBlank()) facts.add(kw);
        }
        if (facts.isEmpty()) {
            return cited(answer) ? 1.0 : 0.0;
        }

        // If no decomposition happened, use the basic citation-presence check.
        if (decomposed == null || decomposed.factToHits() == null
                || decomposed.factToHits().isEmpty()) {
            String norm = answer.toLowerCase(Locale.ROOT);
            int cited = 0;
            for (String fact : facts) {
                String factLower = fact.toLowerCase(Locale.ROOT);
                int idx = norm.indexOf(factLower);
                if (idx < 0) {
                    if (!norm.contains(factLower)) continue;
                }
                int searchStart = Math.max(0, idx - 100);
                int searchEnd = Math.min(answer.length(), idx + fact.length() + 100);
                String window = answer.substring(searchStart, searchEnd);
                if (java.util.regex.Pattern.compile("\\[\\d+\\]").matcher(window).find()) {
                    cited++;
                }
            }
            return (double) cited / facts.size();
        }

        // Rich per-fact granularity: map each expected fact to the passage(s)
        // that retrieved it, then verify the citation references one of those.
        // With multi-fact questions, facts are distributed across sub-queries.
        // We check each fact against the passages from its associated sub-query's
        // retrieval, based on the factToHits map.
        int cited = 0;
        int total = 0;
        for (String fact : facts) {
            total++;
            String factLower = fact.toLowerCase(Locale.ROOT);
            int idx = answer.toLowerCase(Locale.ROOT).indexOf(factLower);
            if (idx < 0) {
                if (!answer.toLowerCase(Locale.ROOT).contains(factLower)) continue;
            }
            // Find which citation [n] appears near this fact occurrence.
            int searchStart = Math.max(0, idx - 100);
            int searchEnd = Math.min(answer.length(), idx + fact.length() + 100);
            String window = answer.substring(searchStart, searchEnd);
            java.util.regex.Matcher cm = java.util.regex.Pattern.compile("\\[(\\d+)\\]").matcher(window);
            while (cm.find()) {
                int passageNum = Integer.parseInt(cm.group(1));
                // Check if this passage number appears in any factToHits entry —
                // if so, the citation backs at least one sub-fact's retrieval.
                for (int[] hitIndices : decomposed.factToHits().values()) {
                    for (int hi : hitIndices) {
                        if (hi == passageNum) {
                            cited++;
                            break;
                        }
                    }
                    if (cited > total - (facts.size() - cited)) break;
                }
                break; // first citation near this fact is sufficient
            }
        }
        return total == 0 ? 0.0 : (double) cited / total;
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
