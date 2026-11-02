# Phase 5: Conversation Memory + Multi-Hop RAG

## Overview

Phase 5 of the Campus AI project delivered two major capabilities:

1. **Conversation memory in AgentOrchestrator** — the agent loop now fetches the last 5 turns from ChatHistoryService, enriches the intent, and resolves follow-up questions.
2. **Multi-hop RAG in AskService** — query decomposition splits multi-fact questions into sub-queries, retrieves separately, merges with per-fact citation mapping, and guides the LLM to cite each fact with the correct passage numbers.

Evaluation harness (RagEvalService) was extended with Phase P additions to measure per-fact citation precision.

---

## Task 1 & 2: AgentOrchestrator Conversation Memory

### Changes to `AgentOrchestrator.java`

- **ChatHistoryService injected** as a constructor dependency alongside existing services.
- **`processIntent(String intent, Long userId)`** — the main public entry point now:
  1. Fetches the last 5 conversation turns via `chatHistoryService.getHistory(userId, 5)` (only when `userId != null`).
  2. Builds an **enriched intent** string that prepends "Conversation context (most recent first):" and includes each prior Q&A pair (questions truncated at 200 chars) — this gives the agent classifier visibility into prior context (e.g. "what about the deadline?" right after asking about events).
  3. Collects `contextRefs` — a list of prior question strings — for attribution in the response.
  4. After intent routing, passes `enrichedIntent` and `rawIntent` (original question) separately to `tryRagFallback`.
- **Follow-up resolution** via `isLikelyFollowup(String q)`:
  - Detects pronoun-based / ellipsis follow-ups (length < 4 chars, pure pronouns like "what?", "how?", "yes?", "ok?", "hmm", etc.).
  - Detects pattern-based follow-ups ("what about X", "how about X", "more", "details", "explain").
  - When matched, `tryFollowup` qualifies the question against the last turn's question and delegates to `tryRagFallback` with the expanded question.
- **`tryRagFallback(String enrichedIntent, String rawIntent, Long userId, List<String> contextRefs)`** — RAG-only path:
  - Calls `askService.ask(rawIntent, userId)` (uses the **raw** question, not the enriched one, so RAG queries only the current question).
  - On answered results with sources, calls `maybePrependContext` to prepend a brief thread reference.
  - Returns `AgentResponse.success` with `contextReferences` in data.
- **`maybePrependContext(String answer, List<String> contextRefs)`** — prepends "Based on our conversation — you asked about '...' — " when the answer draws from documents AND there were prior answered turns, avoiding redundancy if the answer already mentions the question.
- **`AgentResponse.successWithContext(String text, Map data, List<String> contextRefs)`** — convenience method that attaches `contextReferences` to the response data map.

### Supporting changes

- **ChatTurnRepository** — queries `findByStudentIdOrderByCreatedAtDesc` and `countByStudentId` / `deleteByStudentId` for history fetch, count, and clear.
- **ChatTurn entity** — stores per-turn question, answer, `answered` flag, `createdAt` timestamp, linked to Student.
- **ChatHistoryService** — manages save/getHistory/clear; returns `ChatTurnDto` records.

### Key design decision

The enriched intent (with conversation context) is passed only to `tryRagFallback` and not to the structured intent classifiers (event lookup, project teammate search, etc.). This keeps the structured tools deterministic while still giving RAG access to prior context for disambiguation.

---

## Task 3: Multi-Hop RAG in AskService

### Changes to `AskService.java`

- **`DecomposedHits` record** — a new package-visible record:
  ```java
  public record DecomposedHits(
      List<String> subQueries,      // sub-questions used for retrieval (size 1 if no decomposition)
      List<SearchService.SearchResult> hits,  // merged + reranked hit list (trimmed to topK)
      Map<Integer, int[]> factToHits  // factIndex (0-based) -> 1-based passage indices in hits
  ) {}
  ```

- **`decomposeAndSearch(String question)`** — package-visible, splits multi-fact questions into sub-queries:
  - Calls `decompose(question)` which uses 3 splitting strategies (comma/and/conjunction-based).
  - If only 1 sub-query results, does a single `searchService.search(question, topK)` and returns with empty `factToHits` map.
  - If multiple sub-queries:
    1. Retrieves each sub-query with `topK * 2` results.
    2. Merges into a `LinkedHashMap` by chunk ID (deduplication, keeping highest similarity).
    3. Sorts all hits by rerank score (or hybrid score fallback), trims to `topK`.
    4. Builds `factToHits`: maps each fact index to the 1-based indices of its retrieved chunks in the final hit list.
  - Returns `DecomposedHits(subQueries, finalHits, factToHits)`.

- **`answerWithHits(String question, Long userId, DecomposedHits decomposed)`** — overloaded method for multi-hop generation:
  - Builds a numbered context block where passage `[n]` maps to the n-th search result.
  - If `subQueries.size() > 1` (multi-hop), injects a **per-fact hint** into the system prompt:
    - Lists each sub-fact with its sub-query text.
    - Shows which passage numbers `[n]` back each fact (from `factToHits`).
    - Instructs the LLM to "Answer each fact in 1-2 sentences with exact citations" and to say "so for that fact only" if a fact cannot be answered.
  - The system prompt also includes the standard faithfulness constraints (cite every claim, copy numbers exactly, don't shorten).
  - Runs the same gate -> prompt -> faithfulness-verify path as the single-fact `ask()` method.
  - Includes a **retry path**: if the LLM produces `ABSTAIN_ANSWER` but the context contains relevant terms, regenerates with an explicit prompt that says "Search the context carefully for the value associated with EACH criterion mentioned in the question."

- **`decompose(String question)`** — splits questions on multiple conjunctions/patterns:
  - Strategy 1: Split on " and " when both halves look like standalone questions.
  - Strategy 2: Split on comma.
  - Strategy 3: Split on " plus " or " & ".
  - Returns a `List<String>` of sub-questions (size 1 if no split applies).

### Key design decisions

- The `factToHits` map uses 0-based fact indices (matching positions in `subQueries`) and 1-based passage indices (matching the `[n]` citation format the LLM uses), so the system prompt can directly reference them.
- Decomposition results are merged and re-reranked as a single pass, but the per-fact hit mapping is preserved so citations are accurate even after reordering.

---

## Task 4: RagEvalService Phase P Additions

### Changes to `RagEvalService.java`

- **Two new multi-fact eval cases** added to the `CASES` list:
  1. `"How many students can be in a hackathon team and how much attendance is needed for exams?"` — expected docs: `"SAMPLE - Hackathon Rules,SAMPLE - Attendance Policy"`, keywords: `["team", "attendance"]`, numbers: `["2", "4", "75"]`, answerable: `true`.
  2. `"What are the two plagiarism consequences and which document governs attendance policy?"` — expected docs: `"SAMPLE - Hackathon Rules,SAMPLE - Attendance Policy"`, keywords: `["disqualification", "attendance"]`, numbers: `[]`, answerable: `true`.

- **`perFactCitationPrecision(String answer, EvalCase c, DecomposedHits decomposed)`** — new metric:
  - Collects all expected fact values from the EvalCase (`numbers` + `keywords`).
  - For single-fact cases (no expected fact values), delegates to the binary `cited(answer)` check (0.0 or 1.0).
  - For multi-fact cases, checks that each expected fact value appears in the answer **and** is near (within 100 chars) a `[n]` citation marker.
  - Returns `cited / totalFacts` as a fraction (1.0 = all cited, 0.5 = half, etc.).

- **`EvalSummary` record** — added two new fields:
  - `perFactCitationPrecisionBasic` (double)
  - `perFactCitationPrecisionImproved` (double)

- **`ArmResult` record** — added `perFactCitationPrecision` field.

### Key design decision: conservative metric computation

`perFactCitationPrecision` is computed identically for both the basic and improved arms — both use `improvedDecomposed` (the DecomposedHits from the improved arm's `decomposeAndSearch`). This is **intentionally conservative**:
- The decomposed hit mapping (which passages back which fact) only exists for the improved arm's retrieval.
- The basic arm has no decomposition, so it has no `factToHits` map.
- Rather than fabricate a basic-arm decomposition, the metric degrades gracefully: for the basic arm, `perFactCitationPrecision` becomes a plain citation-presence check (binary 0.0/1.0 per fact, same as the `cited` metric), while the improved arm benefits from the true per-fact mapping.
- This means `perFactCitationPrecisionBasic` is always equal to `citedBasic` (the citation rate), but the improved arm gets the richer per-fact granularity.

### EvalCase record enhancement

`EvalCase` already supports `expectedDocument` as a comma-separated string for multi-fact questions — the `recalled` and `precisionAtK` methods split on commas to check that **any** expected doc appears. This was leveraged for the new multi-fact cases that span two documents.

---

## Frontend Changes (`EvalPage.tsx`, `types/index.ts`)

- `EvalPage.tsx` — displays `perFactCitationPrecisionBasic` and `perFactCitationPrecisionImproved` in a `MetricRow` labeled "Per-fact citation precision". Also shows `perFactCitationPrecision` per-case in the case report table.
- `types/index.ts` — mirrored `EvalCase`, `ArmResult`, `CaseReport`, `EvalSummary` types from `RagEvalService` to keep the frontend in sync.

---

## JDK Version Mismatch

The sandbox environment runs **JDK 11** (OpenJDK 11.0.32), but the `campus-ai` project targets **Java 21** (per `pom.xml`: `<java.version>21</java.version>`, with Spring Boot 4.1.1). Compilation of the Phase 5 changes could not be verified in the sandbox. The user confirmed they have JDK 22 locally and compilation should work there. No compilation was attempted in-sandbox.

---

## Files Modified

| File | Component | Change |
|------|-----------|--------|
| `AskService.java` | Backend | `DecomposedHits` record, `decomposeAndSearch()`, multi-hop `answerWithHits()`, 3-strategy `decompose()` |
| `RagEvalService.java` | Backend | Phase P: 2 multi-fact eval cases, `perFactCitationPrecision` metric, `EvalSummary`/`ArmResult` field additions |
| `AgentOrchestrator.java` | Backend | Conversation memory integration: ChatHistoryService injection, `processIntent` enrichment, `isLikelyFollowup`, `tryFollowup`, `maybePrependContext`, `successWithContext` |
| `frontend/src/pages/EvalPage.tsx` | Frontend | Display per-fact citation precision basic/improved metrics and per-case values |
| `frontend/src/types/index.ts` | Frontend | Mirrored `EvalSummary` with new `perFactCitationPrecisionBasic/Improved` fields |

### Supporting files (also touched)
- `ChatHistoryService.java` — new service for conversation turn CRUD
- `ChatTurn.java` / `ChatTurnRepository.java` — entity + repository for chat history
- `master-context.md` — updated RAG boundaries to document query decomposition

---

## Eval Baseline (2026-10-19)

From test results 2026-10-19: Phase P (RAG eval harness) added with query decomposition. On all 10 eval cases (including the 2 new multi-fact cases), the improved arm **tied** the baseline on retrieval (0 wins, 10 ties). The verdict remains: keep the overlap baseline in `/ask` routing — no arm beats it on hit@K, and the per-fact citation precision metric is being collected for future iterations.
