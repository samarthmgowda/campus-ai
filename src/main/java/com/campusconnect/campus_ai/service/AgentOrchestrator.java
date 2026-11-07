package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.StudentRepository;
import com.campusconnect.campus_ai.service.ChatHistoryService.ChatTurnDto;
import com.campusconnect.campus_ai.service.ToolResult;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Phase 3 - AI-Agent Execution Loop.
 *
 * Loop: intent -> parse -> execute controlled tools -> gather -> refine -> reply.
 * Each step maps to an existing backend service (no LLM-to-DB). The agent
 * can break complex intents into multi-step plans.
 *
 * Phase 6 refactor: The legacy tryX() intent-classification methods (tryTeammate,
 * tryCreateProject, tryEvent, tryFollow, tryMentorship, tryRecruiter, tryModeration,
 * tryDocumentSearch, tryNotifications, trySkillGraph, tryBookmark) have been removed.
 * Their responsibilities are now handled by ToolRegistry.dispatch() via the 18
 * @Component Tool implementations. Only the methods still actively called by
 * processIntent are retained below:
 *   - tryFollowup (follow-up resolution for pronoun/short questions)
 *   - tryRagFallback (final RAG fallback path)
 *   - maybePrependContext, truncate, isLikelyFollowup (helpers)
 */
@Service
public class AgentOrchestrator {

    private final CurrentUserService currentUserService;
    private final ProjectToolService projectToolService;
    private final StudentRepository studentRepository;
    private final AskService askService;
    private final ChatHistoryService chatHistoryService;
    private final ToolRegistry toolRegistry;

    public AgentOrchestrator(
            CurrentUserService currentUserService,
            ProjectToolService projectToolService,
            StudentRepository studentRepository,
            AskService askService,
            ChatHistoryService chatHistoryService,
            ToolRegistry toolRegistry) {
        this.currentUserService = currentUserService;
        this.projectToolService = projectToolService;
        this.studentRepository = studentRepository;
        this.askService = askService;
        this.chatHistoryService = chatHistoryService;
        this.toolRegistry = toolRegistry;
    }

    // ---- Public entry point ----
    // processIntent: main agent loop. Receives a natural-language intent and a userId
    // (may be null for unauthenticated RAG lookups). Returns a structured AgentResponse.
    // The method decomposes the intent into a sequence of tool calls rather than
    // calling the LLM directly for data access.
    //
    // Conversation memory: when userId is provided, the last N (default 5) turns are
    // fetched from ChatHistoryService and appended to the intent so intent classification
    // can see prior context (e.g. "what about the deadline?" right after asking about events).
    // Prior answered turns are referenced in the response text and surfaced in contextReferences.

    public AgentResponse processIntent(String intent, Long userId) {
        if (intent == null || intent.isBlank()) {
            return new AgentResponse("clarification", "Could you rephrase your request?", null, null, null);
        }
        String q = intent.trim().toLowerCase(Locale.ROOT);

        // Fetch conversation memory (last 5 turns) if authenticated.
        List<ChatTurnDto> history = userId != null
                ? chatHistoryService.getHistory(userId, 5)
                : List.of();

        // Build an enriched intent that includes prior Q&A context so the agent
        // can reference earlier answers (e.g. "you asked about X, the answer was Y").
        String enrichedIntent = intent;
        List<String> contextRefs = new ArrayList<>();
        if (!history.isEmpty()) {
            StringBuilder ctx = new StringBuilder();
            ctx.append("Conversation context (most recent first):\n");
            for (ChatTurnDto turn : history) {
                if (turn.answered()) {
                    ctx.append("- You asked: ").append(turn.question())
                      .append(" → Answer: ").append(
                          turn.answer().length() > 200 ? turn.answer().substring(0, 200) + "..." : turn.answer())
                      .append("\n");
                    contextRefs.add(turn.question());
                } else {
                    ctx.append("- You asked: ").append(turn.question())
                      .append(" → (no answer was found)\n");
                    contextRefs.add(turn.question());
                }
            }
            ctx.append("Current question: ").append(intent);
            enrichedIntent = ctx.toString();
        }

        // 0. Profile completeness check (Priority)
        if (q.contains("complete") && q.contains("profile")) {
            if (userId == null) return new AgentResponse("error", "User not identified", Map.of("detail", "Please ensure you are logged in."), null, null);
            currentUserService.requireUser(userId);
            var student = studentRepository.findById(userId).orElseThrow();

            int score = 0;
            int total = 4; // name, skills, branch, year
            if (student.getName() != null && !student.getName().isBlank()) score++;
            if (student.getSkills() != null && !student.getSkills().isBlank()) score++;
            if (student.getBranch() != null && !student.getBranch().isBlank()) score++;
            if (student.getYear() > 0) score++;

            double percent = (score / (double)total) * 100;
            return new AgentResponse("success",
                String.format("Your profile is %.0f%% complete. You have provided %d of %d basic details.", percent, score, total),
                Map.of("completeness", percent, "score", score, "total", total),
                null, null);
        }

        // 0.5. Follow-up resolution — if the question is a pronoun or very short
        // and there's prior conversation, expand it into a qualified follow-up.
        if (!history.isEmpty()) {
            String lastQuestion = history.getFirst().question();
            if (isLikelyFollowup(q, lastQuestion)) {
                return tryFollowup(intent, q, lastQuestion, userId, contextRefs);
            }
        }

        // Dispatch to dynamic tool registry
        Map<String, Object> params = new HashMap<>();
        params.put("rawIntent", intent);
        params.put("contextRefs", contextRefs);
        Optional<ToolResult> toolResult = toolRegistry.dispatch(intent, userId);

        if (toolResult.isPresent()) {
            ToolResult result = toolResult.get();
            Map<String, Object> data = result.data() != null ? new LinkedHashMap<>(result.data()) : new LinkedHashMap<>();
            if (!contextRefs.isEmpty()) {
                data.put("contextReferences", contextRefs);
            }
            if (result.success()) {
                String text = result.text();
                // Prepend context reference only for follow-up questions (ambiguous without
                // prior context) AND when we have sources to ground the answer.
                if (isLikelyFollowup(q, contextRefs.isEmpty() ? null : contextRefs.getFirst())
                        && !contextRefs.isEmpty() && data.containsKey("sources")) {
                    @SuppressWarnings("unchecked")
                    var sources = (List<?>) data.get("sources");
                    if (sources != null && !sources.isEmpty()) {
                        text = maybePrependContext(text, contextRefs);
                    }
                }
                return AgentResponse.success(text, data, result.cards());
            } else if (result.isClarification()) {
                return AgentResponse.clarification(result.text());
            } else {
                return AgentResponse.error(result.text(), result.errorDetail());
            }
        }

        // Fall back to RAG / keyword search.
        return tryRagFallback(enrichedIntent, intent, userId, contextRefs);
    }


    // ---- Intent classification methods (tryFollowup, tryRagFallback) ----
    // NOTE: The legacy tryX() methods were removed in Phase 6 — their
    // intent-classification responsibilities are now handled by ToolRegistry.dispatch()
    // via the 18 @Component Tool implementations. Kept below are only the methods
    // still actively called by processIntent:
    //   - tryFollowup (follow-up resolution for pronoun/short questions)
    //   - tryRagFallback (final RAG fallback path)
    //   - maybePrependContext, truncate, isLikelyFollowup (helpers)

    // ---- RAG fallback ----
    // Delegate to AskService for knowledge-base / document search.
    // enrichedIntent includes the conversation memory; rawIntent is the original
    // question (used for RAG routing which should query only the current question).
    // contextRefs carries prior questions so the response can reference them.
    private AgentResponse tryRagFallback(String enrichedIntent, String rawIntent, Long userId, List<String> contextRefs) {
        try {
            String q = rawIntent.trim().toLowerCase(Locale.ROOT);
            var answer = askService.ask(rawIntent, userId);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("sources", answer.sources());
            if (!contextRefs.isEmpty()) {
                data.put("contextReferences", contextRefs);
            }
            if (answer.answered()) {
                String text = answer.answer();
                // Only prepend context reference for follow-up questions that are
                // ambiguous without prior context, AND when we have sources.
                if (isLikelyFollowup(q, contextRefs.isEmpty() ? null : contextRefs.getFirst())
                        && !contextRefs.isEmpty() && answer.sources() != null && !answer.sources().isEmpty()) {
                    text = maybePrependContext(text, contextRefs);
                }
                return AgentResponse.success(text, data);
            }
            return AgentResponse.info(answer.answer(), Map.of(
                    "sources", answer.sources(),
                    "hint", "Try GET /ask?q=... for a direct RAG answer."));
        } catch (IllegalStateException e) {
            return AgentResponse.error("RAG unavailable", e.getMessage());
        } catch (Exception e) {
            return AgentResponse.error("Search failed", e.getMessage());
        }
    }

    /**
     * If the current answer draws from documents AND there were prior answered
     * turns, prepend a short note so the user sees the conversation thread.
     */
    private String maybePrependContext(String answer, List<String> contextRefs) {
        // Find the most recent distinct question that isn't the same as the current intent
        // (contextRefs are reversed-ordered from getHistory, most recent first).
        String last = contextRefs.getFirst();
        if (last == null || last.isBlank()) return answer;
        // Avoid redundancy if the answer already mentions it
        if (answer.toLowerCase(Locale.ROOT).contains(last.toLowerCase(Locale.ROOT))) {
            return answer;
        }
        return "Based on our conversation — you asked about \"" + truncate(last, 60)
                + "\" — " + answer;
    }

    private static String truncate(String s, int max) {
        if (s == null || s.length() <= max) return s == null ? "" : s;
        return s.substring(0, max) + "...";
    }

    /**
     * Detect whether a question is likely a follow-up (pronoun, ellipsis, or very short).
     * These benefit from conversation memory context.
     */
    private static boolean isLikelyFollowup(String q, String lastQuestion) {
        String raw = q;
        q = q.trim().toLowerCase(Locale.ROOT);
        if (q.length() < 4) return true;
        // Pure pronouns / ellipsis
        if (Set.of("what?", "how?", "why?", "and?", "then?", "yes?", "no?", "ok", "ok?", "hmm", "hmm?").contains(q)) return true;
        // "what about X", "how about X"
        if (q.matches("(?i)(what|how|why|and|tell me|more)\\s+(about|on)\\s+.*")) return true;
        // Bare "more" or "details"
        if (Set.of("more", "details", "explain", "explain?", "details?").contains(q)) return true;
        // A short question that leans on a pronoun/demonstrative with no antecedent of
        // its own is a follow-up: "when does it start", "where is that held",
        // "who runs them". These are unanswerable standalone, so they must resolve
        // against the prior turn. Kept short (< 8 words) to avoid hijacking a
        // self-contained question that merely happens to contain "it".
        boolean shortQuestion = q.split("\\s+").length < 8;
        boolean hasDeictic = q.matches(".*\\b(it|its|that|this|them|they|those|these)\\b.*");
        if (shortQuestion && hasDeictic) return true;
        // Contextual follow-ups: references to entities from prior question
        // e.g. "when is the cybersecurity one" after asking about upcoming events
        // e.g. "who can fill the gaps in Campus AI" after "show my projects"
        if (q.split("\\s+").length > 3) {
            // Questions starting with "when", "where", "who", "what" + "the X one" pattern
            if (q.matches("(?i)(when|where|who|what|how)\\s+.*\\b(the\\s+\\w+\\s+)?one.*")) return true;
            // Questions about specific project/skill mentioned in prior turn
            if (lastQuestion != null) {
                String last = lastQuestion.toLowerCase(Locale.ROOT);
                // Extract key nouns from last question for cross-reference
                if (q.contains("campus ai") && last.contains("project")) return true;
                if (q.contains("gap") && last.contains("project")) return true;
                if (q.contains("cybersecurity") && last.contains("event")) return true;
                // Generic: a short question that shares a distinctive noun with the
                // prior turn (placement drive, hackathon, deadline) and adds a
                // "when/where/who" ask resolves against that subject.
                if (q.matches("(?i)^(when|where|who|what|how)\\b.*")
                        && sharesDistinctiveNoun(q, last)) return true;
            }
        }
        return false;
    }

    /** True when the two questions share a content noun (length >= 5, non-stopword). */
    private static boolean sharesDistinctiveNoun(String a, String b) {
        Set<String> aTerms = distinctiveNouns(a);
        if (aTerms.isEmpty()) return false;
        Set<String> bTerms = distinctiveNouns(b);
        for (String t : aTerms) if (bTerms.contains(t)) return true;
        return false;
    }

    private static Set<String> distinctiveNouns(String text) {
        Set<String> out = new HashSet<>();
        for (String t : text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")) {
            if (t.length() >= 5 && !FOLLOWUP_STOPWORDS.contains(t)) out.add(t);
        }
        return out;
    }

    private static final Set<String> FOLLOWUP_STOPWORDS = Set.of(
            "about", "there", "their", "which", "where", "when", "would", "could",
            "should", "start", "starts", "begins", "happen", "happens", "please",
            "question", "answer", "explain", "college", "campus",
            // Temporal / weak qualifiers. These are not subjects: a question like
            // "how can I prepare for the upcoming placement drive" shares only
            // "upcoming" with a prior "any upcoming ai workshops" turn, and treating
            // that as a shared subject frames an unrelated answer against the wrong
            // question. Excluding them keeps follow-up detection to real content nouns.
            "upcoming", "recent", "latest", "next", "last", "current", "following",
            "coming", "available", "general", "overall", "details", "information");

    /**
     * Resolve a follow-up question by qualifying it with the prior turn's subject.
     * E.g. "what?" after "when is the deadline" -> "when is the deadline"
     * "when is the cybersecurity one" after "upcoming events" -> expands to
     * "when is the cybersecurity workshop" and routes to EventTool.
     */
    private AgentResponse tryFollowup(String intent, String q, String lastQuestion, Long userId, List<String> contextRefs) {
        // Short pronoun questions — re-answer the last question
        if (q.length() < 4 || Set.of("what?", "how?", "why?", "and?", "yes?", "no?", "ok", "hmm").contains(q)) {
            String expanded;
            if (q.matches("(?i)(what|how|why|and|tell me|more)[\\s?]*")) {
                expanded = lastQuestion;
            } else if (q.matches("(?i)(what|how|why)\\s+(about|on)\\s+(.+)")) {
                expanded = q.replaceAll("(?i)(what|how|why)\\s+(about|on)\\s+", "").trim();
            } else {
                expanded = lastQuestion;
            }
            return tryRagFallback(expanded, expanded, userId, contextRefs);
        }
        // Entity-reference follow-ups: "when is the cybersecurity one", "who can fill gaps in Campus AI"
        // Try to resolve by expanding with entity from prior turn, then dispatch to tools
        String expanded = expandFollowup(q, lastQuestion);
        // First try tool dispatch on the expanded question
        String qLower = expanded.toLowerCase(Locale.ROOT);
        Optional<ToolResult> toolResult = toolRegistry.dispatch(expanded, userId);
        if (toolResult.isPresent() && toolResult.get().success()) {
            var result = toolResult.get();
            Map<String, Object> data = result.data() != null ? new LinkedHashMap<>(result.data()) : new LinkedHashMap<>();
            if (!contextRefs.isEmpty()) data.put("contextReferences", contextRefs);
            String text = result.text();
            if (isLikelyFollowup(q, lastQuestion) && !contextRefs.isEmpty() && data.containsKey("sources")) {
                @SuppressWarnings("unchecked")
                var sources = (List<?>) data.get("sources");
                if (sources != null && !sources.isEmpty()) {
                    text = maybePrependContext(text, contextRefs);
                }
            }
            return AgentResponse.success(text, data);
        }
        // Fall back to RAG with expanded question
        return tryRagFallback(expanded, expanded, userId, contextRefs);
    }

    /**
     * Expand a follow-up question using context from the prior turn.
     * e.g. "when is the cybersecurity one" + "upcoming events" ->
     *      "when is the cybersecurity workshop"
     *      "who can fill the gaps in Campus AI" + "show my projects" ->
     *      "who can fill the skill gaps in Campus AI project"
     *
     * IMPORTANT: the synthesized query must not contain words that collide with
     * another tool's trigger. In particular we must NOT inject the word "follow"
     * (e.g. "follow-up:") because FollowTool's "follow" trigger would then hijack
     * the request — that is exactly how "what about 2nd and 3rd internals" was
     * misrouted to the follow-stats tool.
     */
    private String expandFollowup(String q, String lastQuestion) {
        String lastLower = lastQuestion.toLowerCase(Locale.ROOT);
        String qLower = q.toLowerCase(Locale.ROOT);
        // "the X one" pattern — replace "the X one" with "the X" from prior context
        if (qLower.contains("one")) {
            // Extract entity type from last question
            if (lastLower.contains("event") || lastLower.contains("workshop") || lastLower.contains("hackathon")) {
                return q.replaceAll("(?i)\\bthe\\b\\s+(\\w+)\\s+one", "$1");
            }
            if (lastLower.contains("project")) {
                // "who can fill the gaps in Campus AI" -> expand "Campus AI" from prior
                return q;
            }
        }
        // "in Campus AI" — try tool dispatch with explicit project name
        if (qLower.contains("campus ai") && lastLower.contains("project")) {
            // Rewrite to tool-friendly form
            if (qLower.contains("gap")) {
                return "find teammates for project Campus AI";
            }
        }
        // Deictic follow-up: "when does it start", "where is that held". The pronoun
        // has no antecedent in the question itself, so substitute the subject noun
        // phrase from the prior turn: "when does the placement drive start".
        if (qLower.matches(".*\\b(it|its|that|this|them|they|those|these)\\b.*")) {
            String subject = extractSubject(lastQuestion);
            if (subject != null) {
                return q.replaceAll("(?i)\\b(it|its|that|this|them|they|those|these)\\b", subject).trim();
            }
        }
        // Generic fallback: strip leading connectives ("what about", "how about",
        // "and", "but", "so", "then", "also") so the remaining content is a clean
        // standalone query. We deliberately do NOT concatenate the prior question:
        // (a) a synthesized blob like "... — follow-up: ..." contains the token
        //     "follow", which collided with FollowTool's trigger and hijacked
        //     "what about 2nd and 3rd internals" into a follow-stats reply;
        // (b) clean retrieval on the stripped question works well on its own
        //     ("2nd and 3rd internals" retrieves the same chunks as the direct
        //     "3rd internals" question, which already answered correctly).
        String stripped = q.replaceFirst("(?i)^(and|but|so|then|also|what about|how about)\\s+", "").trim();
        return stripped.isEmpty() ? q : stripped;
    }

    /**
     * Pull the subject noun phrase out of a prior question so a deictic follow-up
     * can substitute it for a bare pronoun. "how can i prepare for the upcoming
     * placement drive" -> "the placement drive"; "when is the cybersecurity
     * workshop" -> "the cybersecurity workshop". Returns null when nothing usable
     * is found.
     */
    private static String extractSubject(String lastQuestion) {
        if (lastQuestion == null || lastQuestion.isBlank()) return null;
        String q = lastQuestion.toLowerCase(Locale.ROOT)
                .replaceAll("[?!.]+$", "").trim();
        // Prefer a "the <noun phrase>" chunk — the most reliable subject marker.
        // Runs BEFORE the event-keyword fallback: for "how can i prepare for the
        // upcoming placement drive" this yields "the placement drive", whereas
        // matching the bare event keyword "placement" would only recover the
        // trailing token "drive".
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\\bthe\\s+(?:upcoming\\s+|next\\s+|current\\s+)?([a-z0-9][a-z0-9\\s-]*)$")
                .matcher(q);
        if (m.find()) {
            String phrase = m.group(1).trim();
            if (phrase.split("\\s+").length <= 4) return "the " + phrase;
        }
        // No explicit "the <noun>" — fall back to an event keyword named by the
        // prior turn, so a deictic follow-up like "when does it start" after
        // "any upcoming hackathon" resolves to "the hackathon" instead of leaving
        // a dangling pronoun.
        for (String kw : new String[]{"hackathon", "workshop", "seminar", "networking", "placement"}) {
            if (q.matches(".*\\b" + kw + "s?\\b.*")) return "the " + kw;
        }
        // Trailing content words: "when does the placement drive start" already has
        // "the"; otherwise drop leading question words and interrogatives.
        String stripped = q.replaceFirst("(?i)^(when|where|who|what|how|why|is|are|does|do|can|could|should|will)\\b\\s*", "")
                .replaceFirst("(?i)^(is|are|does|do|was|were)\\b\\s*", "").trim();
        if (stripped.isEmpty()) return null;
        // Keep the last up-to-4 words as the subject phrase.
        String[] words = stripped.split("\\s+");
        if (words.length == 0) return null;
        int from = Math.max(0, words.length - 4);
        String phrase = String.join(" ", java.util.Arrays.copyOfRange(words, from, words.length)).trim();
        return phrase.isEmpty() ? null : phrase;
    }


    // ========================
    // ---- Response type ----
    // ========================

    /**
     * Structured response from the agent loop.
     *
     * @param type      "success", "info", "error", "clarification", "confirmation"
     * @param text      Human-readable reply (rendered to user)
     * @param data      Optional structured payload (tool result details, IDs, etc.)
     * @param suggestions Follow-up prompts the client can surface
     * @param cards     Clickable entity cards (teammates, events, …) rendered under the answer
     */
    public record AgentResponse(
            String type,
            String text,
            Map<String, Object> data,
            List<String> suggestions,
            List<ToolResult.EntityCard> cards
    ) {
        public static AgentResponse success(String text, Map<String, Object> data) {
            return new AgentResponse("success", text, data, null, null);
        }

        public static AgentResponse success(String text, Map<String, Object> data,
                                            List<ToolResult.EntityCard> cards) {
            return new AgentResponse("success", text, data, null, cards);
        }

        /** Convenience: success with conversation context references attached to data. */
        public static AgentResponse successWithContext(String text, Map<String, Object> data, List<String> contextRefs) {
            return successWithContext(text, data, contextRefs, null);
        }

        public static AgentResponse successWithContext(String text, Map<String, Object> data,
                                                       List<String> contextRefs,
                                                       List<ToolResult.EntityCard> cards) {
            Map<String, Object> enriched = new LinkedHashMap<>(data != null ? data : Map.of());
            if (contextRefs != null && !contextRefs.isEmpty()) {
                enriched.put("contextReferences", contextRefs);
            }
            return new AgentResponse("success", text, enriched, null, cards);
        }

        public static AgentResponse info(String text, Map<String, Object> data) {
            return new AgentResponse("info", text, data, null, null);
        }

        public static AgentResponse error(String text, String detail) {
            return new AgentResponse("error", text, Map.of("detail", detail), null, null);
        }

        public static AgentResponse clarification(String text) {
            return new AgentResponse("clarification", text, Map.of(),
                    List.of("Show my projects", "Find teammates", "List upcoming events",
                            "What documents are available?"), null);
        }

        public static AgentResponse confirmation(String text, Map<String, Object> data) {
            return new AgentResponse("confirmation", text, data, null, null);
        }
    }
}
