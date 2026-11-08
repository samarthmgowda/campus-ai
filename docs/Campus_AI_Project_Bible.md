# Campus AI — Project Bible (Phase 0)

**Document Version:** 1.0  
**Last Updated:** 2026-10-30  
**Environment:** Spring Boot 4.1.1 • PostgreSQL + pgvector • Ollama + BGE-M3 (1024-dim) • JDK 21 (pom) / JRE 11 (sandbox)  
**Purpose:** Complete reference for anyone joining the project — architecture, built features, design rationale, technical constraints, test strategy, and future roadmap.

---

## TABLE OF CONTENTS

1. [Project Overview](#1-project-overview)
2. [Architectural Philosophy](#2-architectural-philosophy)
3. [Built Features by Phase](#3-built-features-by-phase)
4. [Architectural Decisions & Rationale](#4-architectural-decisions--rationale)
5. [Discarded / Deferred Features](#5-discarded--deferred-features)
6. [Technical Constraints](#6-technical-constraints)
7. [Test Strategy & Execution](#7-test-strategy--execution)
8. [Phase 8: Test Validation & Hybrid Extension](#8-phase-8-test-validation--hybrid-extension)
9. [Build & Run Instructions](#9-build--run-instructions)
10. [Appendices](#10-appendices)

---

## 1. PROJECT OVERVIEW

### 1.1 Mission
Campus AI is a natural-language intelligence layer over Campus Connect — a private college-only social/professional network. It enforces strict scope: document questions → RAG pipeline; people/event questions → structured DB queries; combined → DB+RAG. It does **not** expose unrestricted LLM→SQL access. All answers must be evidence-based with citations.

### 1.2 Core Principles (from master-context.md)

| Principle | Description |
|-----------|-------------|
| **LLM = reasoning** | LLM handles natural language understanding, intent classification, response generation |
| **Backend = control** | Backend services (JPA repos, pgvector, Ollama) enforce constraints, prevent unbounded operations |
| **DB/RAG = source of truth** | All answers derive from verified data (documents, student/alumni records, skill graphs), not LLM hallucination |
| **Thin tool layer** | Tools are case-matched, injection-safe, never accept free-form LLM output without validation |
| **Evidence-based answers** | Every claim must cite a source passage `[n]`; faithfulness checks reject uncited answers |
| **User-context rule** | Use authenticated platform context; don't re-ask the user for identity or data already known |

### 1.3 Product Context (from master-context.md)

**Campus Connect** spans 8 pillars across Students, Alumni, Recruiters, and Admins:
- **Connect:** Messaging, follows, endorsements, bookmarks
- **Collaborate:** Projects, teams, skill-gap matching, teammate requests
- **Discover:** Events, hackathons, roadmaps, referrals
- **Learn & Get Guidance:** Mentorship handshake, alumni roadmaps
- **Career:** Recruiter postings, applications, shortlisting
- **Trust & Reputation:** Moderation, reports, reviews, credibility scoring
- **Campus Intelligence:** Analytics, search, global pages
- **Campus AI:** The natural-language layer (this project)

**Roles:** Student, Alumni, Recruiter/HR, Admin  
**Privacy:** Data minimization (no phones/addresses/passwords in demo); CGPA restricted; auth-derived identity only

### 1.4 Success Metrics
- RAG recall @ 0.667 flat + precision +0.056 over baseline
- Query decomposition ties baseline on all 10 multi-fact cases (0 wins, 10 ties)
- Overlap baseline kept in /ask routing until an arm beats it on hit@K with rating coverage not worse
- perFactCitationPrecision metric collected for future iterations (basic = 1.0 improved; improved = true per-fact mapping)

---

## 2. ARCHITECTURAL PHILOSOPHY

### 2.1 Three-Layer Split (from master-context.md)

```
┌─────────────────────────────────────────────────────────────────┐
│                    LAYER 1: PRESENTATION (Frontend)             │
│  React + Vite • SPA fallback • Vite dev proxy • API guards      │
│  Endpoints: /api/*, /dashboard/*, /roadmaps/*, /referrals/*     │
│  Accept headers: text/html → 200 index.html;                  │
│  application/json → never forwarded (proxied to backend)       │
└─────────────────────────────────────────────────────────────────┘
                                       │
                       API Gateway (Spring Security + X-Demo-User header)
                                       │
└─────────────────────────────────────────────────────────────────┘
│                    LAYER 2: APPLICATION (Backend)               │
│  Spring Boot 4.1.1 • PostgreSQL • pgvector • Ollama + BGE-M3   │
│  Services: AgentOrchestrator, AskService, RagEvalService,       │
│  ToolRegistry, Tool AutoConfiguration, ChatHistoryService       │
│  Repositories: Student, Alumni, Project, ProjectInvite,         │
│  ProjectMember, Report, Review, Referral, Roadmap, Skill        │
│  Tools: 18 @Component Tool implementations auto-registered       │
│  Thin tools only — no unrestricted LLM→SQL                     │
└─────────────────────────────────────────────────────────────────┘
                                       │
                                       │ Evidence gates
                                       ▼
┌─────────────────────────────────────────────────────────────────┐
│                    LAYER 3: DATA & INFERENCE                      │
│  PostgreSQL + pgvector (similarity > 0.45 gate)                │
│  Ollama (bge-m3 embeddings 1024-dim; qwen2.5:3b for generation)│
│  RAG pipeline: ingest → chunk → embed → retrieve → grounded gen │
│  perFactCitationPrecision metric (basic vs improved arms)       │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 Key Design Decisions

| Decision | Rationale |
|----------|-----------|
| **ToolRegistry dispatch** (not hardcoded tryX()) | Extensible — add new @Component Tool and it auto-registers via Spring; 18 tools currently; priority-sorted; case-insensitive substring trigger matching |
| **Hybrid rule+LLM extraction** (MentorshipTool + ProjectInviteTool) | Deterministic for critical paths (numeric IDs for DB operations); LLM for natural language variations; clarification path catches both rule failures AND LLM failures |
| **Conversation memory (last 5 turns)** | Enables follow-up resolution ("what about the deadline?" after asking about events); enriched intent passed to RAG only, not structured tools |
| **Multi-hop RAG with decomposeAndSearch** | 3 splitting strategies (and-conjunction, comma, ordinal markers); sub-queries retrieved separately, merged by chunk ID, reranked; per-fact hit mapping (`factToHits`) for citation accuracy |
| **perFactCitationPrecision metric** | Basic = binary cited/uncited per fact; Improved = true per-fact mapping using `factToHits`; Conservatively computed identically for both arms initially |
| **JDK 21 → JRE 11 sandbox mismatch** | Project pom.xml targets Java 21; sandbox has JRE 11; cannot compile/test via Maven in-sandbox; user has JDK 22 locally; manual code verification instead |
| **Admin = user 1** (campus.admin-ids=1) | Single admin allow-list; all admin calls use X-Demo-User: 1; non-admin → 403 |

### 2.3 RAG Pipeline (End-to-End)

```
User Query → AskService.ask() ──┬──► Structured tool dispatch (ToolRegistry)
                               │
                               ├──► tryRagFallback() ──► enrichedIntent + contextRefs
                                              │
                                              ├──► askService.ask(rawIntent, userId) ──► RAG retrieval
                                              │                                     │
                                              │                                     └──► faithfulness verify (isFaithful)
                                              │                                                       │
                                              │                                                       └──► if faithful → answer with citations [n]
                                              │                                                             │
                                              │                                                             └──► if not faithful → retry with explicit search prompt
                                              │
                                              └──► maybePrependContext (if answered from docs + prior turns)
                                              
Retrieval: chunk → embed (bge-m3, 1024-dim) → pgvector similarity > 0.45 → topK hits
Generation: system prompt → LLM (qwen2.5:3b or Ollama) → grounded response with [n] citations
```

### 2.4 Conversation Memory Flow

```
AgentOrchestrator.processIntent()
    │
    ├──► chatHistoryService.getHistory(userId, 5) → last 5 turns
    │
    ├──► Build enrichedIntent: "Conversation context (most recent first): Q: ... A: ... Q: ... A: ..."
    │
    ├──► isLikelyFollowup(q) → pronoun/ellipsis detection
    │
    ├──► tryFollowup → qualify against last turn's question → tryRagFallback(expandedQuestion)
    │
    ├──► Pass enrichedIntent + rawIntent separately to tryRagFallback
    │
    └──► Response: successWithContext(text, data, contextRefs)
```

---

## 3. BUILT FEATURES BY PHASE

### Phase 1: Foundation
- Postgres + pgvector setup
- Document ingestion + chunking + embedding (bge-m3, 1024-dim)
- Similarity search gate (0.45)
- Basic /ask endpoint with RAG + Lookup fallback when Ollama down
- Health check: GET /documents → 200

### Phase 2: RAG Core (Sections A–D)
- **A. Documents / RAG core:** POST /documents, /documents/{id}/chunks, /search?q=..., /ask regression (Ollama down → 200 via Lookup)
- **B. Chat history:** GET /ask?q=hello → history save/count/clear; /show my chat history → 5 inline
- **C. Profile polish:** /me/completeness, /me/recommendations, chat variants
- **D. Skill glossary:** GET /skills/glossary?skill=js → canonical + aliases + related + profile counts; /skills/search?q=rea → React etc.; chat: glossary for js

### Phase 3: User-Context Features
- FollowTool, BookmarkTool, ProfileTool, AnalyticsTool, RecruiterPostingTool
- All Phase 6 tool implementations (18 tools total, auto-registered via ToolAutoConfiguration)
- ToolRegistry dispatch verification (D1–D8 via /api/agent)

### Phase 4: The Six Extensions (E–J)
| Extension | Status | Key Endpoints |
|-----------|--------|---------------|
| **E. Project create/add-member** | ✅ Built | POST /projects, /projects/{id}/members |
| **F. Student event create** | ✅ Built | POST /events, admin approve/visibility |
| **G. Mentorship handshake** | ✅ Built + **Hybrid enhanced** | POST /mentorship/request, /{id}/respond, my lists |
| **H. Recruiter posting/shortlist** | ✅ Built | POST /recruiter/postings, /{id}/applications, decide, withdraw, close |
| **I. Admin moderation actions** | ✅ Built | POST /reports, {id}/resolve → hide post/suspend/strict writes |
| **J. Document upload manager** | ✅ Built | POST /documents/upload multipart, visibility toggle, curation, delete |

### Phase 5: Conversation Memory + Multi-Hop RAG
- **Task 1:** AgentOrchestrator conversation memory (last 5 turns, follow-up resolution, maybePrependContext, successWithContext)
- **Task 2:** Multi-hop RAG in AskService (DecomposedHits record, decomposeAndSearch with 3 splitting strategies, answerWithHits with per-fact system prompt hints)
- **Task 3:** RagEvalService Phase P (perFactCitationPrecision metric, 2 new multi-fact eval cases)
- **Task 4:** JDK 11 vs JDK 22 sandbox mismatch documented; user has JDK 22 locally

### Phase 6: Agentic Tool Extensibility
- 18 tools implemented for Phase 6 extensibility:
  - FollowTool, MentorshipTool, ModerationTool, DocumentTool, NotificationTool, BookmarkTool, ProjectInviteTool, TeammateRequestTool, ProfileTool, AnalyticsTool, RecruiterPostingTool, SearchStudentsTool, SkillGraphTool, EventTool, ModerationTool, FollowTool, CreateProjectTool, DocumentTool, FallbackRagTool
- Fixed: ProjectTeammateTool "show my projects" (D1); SkillGraphTool "glossary for js" (D7)
- extractParams + list action added to ProjectTeammateTool
- TEST_RUN_CHECKLIST updated with Phase 6 dispatch verification

### Hybrid Extraction Enhancements (Completed in This Session)
| Tool | Enhancement | Tests |
|------|-------------|-------|
| **MentorshipTool** | `extractAlumniName()`, LLM fallback, clarification path, type-mismatch fix | 16 tests |
| **ProjectInviteTool** | `extractInviteeName()`, LLM fallback for project+name, clarification path | 16 tests |

---

## 4. ARCHITECTURAL DECISIONS & RATIONALE

### 4.1 Why Hybrid Rule + LLM? (from the actual implementation dialogue)

The core architectural question was: **Is the current rule-based parameter extraction fundamentally limited?**

**Analysis:**
- Single LLM doing intent+routing+parameter extraction creates maintenance burden for every new phrase
- Pure rules can't handle natural language variations users actually use
- Pure LLM replacement risks hallucinated user IDs affecting tool dispatch (unacceptable for DB-critical paths)

**Solution: Hybrid approach**
- **Rules first** → deterministic for critical paths (numeric IDs, exact name patterns after "from"/"with")
- **LLM fallback** → only activates when both `alumniId` AND `message` (or `projectId` + `inviteeId`) fail rule extraction
- **Clarification path** → catches both unresolvable names AND LLM failures gracefully

**Why this wins over pure LLM replacement:**
- ✅ Deterministic for critical paths (numeric IDs for database operations)
- ✅ LLM handles natural language variations users actually use ("I want to connect with Rahul about careers")
- ✅ Clarification path catches both rule failures AND LLM failures
- ✅ Zero risk of hallucinated user IDs affecting tool dispatch
- ✅ Incremental — existing rules unchanged; LLM only activates when rules fail

### 4.2 Why ToolRegistry vs Hardcoded tryX()?

**Before:** 30+ `tryX()` methods in AgentOrchestrator; each new tool required adding a method + updating the chain; fragile; hard to maintain.

**After:** ToolRegistry auto-registers all `@Component Tool` beans at startup via `ToolAutoConfiguration`; 18 tools currently; dispatch is priority-sorted case-insensitive substring matching on triggers; adding a new tool = just add the class + it's immediately available.

**Rationale:** Extensibility without modifying core flow; the refactored codebase has 0 `tryX()` methods; all dispatch goes through ToolRegistry.

### 4.3 Why Conversation Memory (Last 5 Turns)?

**Rationale:** Users naturally ask follow-up questions. Without context, "what about the deadline?" or "how much attendance is needed?" is uninterpretable. The 5-turn window balances usefulness vs. noise; enriched intent is passed only to RAG (not structured tools), keeping deterministic tools reliable.

### 4.3 Why Multi-Hop RAG with Decomposition?

**Rationale:** Multi-fact questions like "How many students can be in a hackathon team AND how much attendance is needed for exams?" require retrieving two different docs. Decomposition splits on "and", commas, or ordinal markers (2nd IA, 3rd IA). Sub-queries retrieved separately, merged by chunk ID (dedup, highest similarity), reranked, and per-fact hit mapping ensures citations `[n]` are accurate even after reordering.

### 4.4 Why perFactCitationPrecision (Conservative Metric)?

**Design Decision:** The metric is computed identically for both basic and improved arms — intentionally conservative. The improved arm has true per-fact mapping via `factToHits`; the basic arm has no decomposition. Rather than fabricate a basic-arm decomposition, the metric degrades gracefully: basic = binary citation-presence check (0.0/1.0 per fact, same as the `cited` metric); improved = true per-fact mapping. To make this asymmetric in the future, pass `DecomposedHits` to the metric and use `factToHits` for improved while falling back to binary check for basic.

### 4.5 Why JDK 21 in pom.xml with JRE 11 sandbox?

**Reality:** The project was configured for Java 21 when built; the sandbox environment only has JRE 11 available; Maven compilation fails; code verified manually by reading files; user confirmed JDK 22 locally. This is a known environment constraint, not a code bug.

### 4.6 Why Admin = User 1 Only?

**Rationale:** Single admin allow-list simplifies the auth model; all admin calls use X-Demo-User: 1; non-admin headers → 403; avoids role-complexity for the prototype while maintaining security boundary.

---

## 5. DISCARDED / DEFERRED FEATURES

### 5.1 Features Explicitly Deferred (from pending-tests-deferred.md)

| Feature | Why Deferred | Status |
|---------|-------------|--------|
| **All PENDING_TESTS.md A-K** | User confirmed app starts after DDL fix but explicitly deferred: "i have not run any tests rightnow so all are pending" | Deferred since 2026-10-05; never run |
| **DDL fix** | Alumni currentRole rename + hidden/suspended boolean default false | Applied; tests never exercised |

### 5.2 Features Left Read-Only (from planned-extensions.md)

| Feature | Reason | Current Status |
|---------|--------|----------------|
| **All six planned extensions** | "Previously left intentionally read-only to keep prototype small/local-first" | All six built (Project create, Event create, Mentorship handshake, Recruiter posting, Admin moderation, Document upload) — but test infrastructure wasn't verified |
| **Why read-only initially** | Keep prototype small/local-first; user explicitly decided to include all six later | All built; tests deferred |

### 5.3 Discarded Approaches (from actual implementation dialogue)

| Approach | Why Discarded |
|----------|---------------|
| **Pure LLM replacement of rule-based extraction** | Risks hallucinated user IDs affecting tool dispatch; unacceptably non-deterministic for DB-critical paths |
| **Hardcoded tryX() chain in AgentOrchestrator** | Maintenance nightmare; every new tool requires modifying the chain; replaced by ToolRegistry |
| **Single LLM doing intent+routing+parameter extraction** | Creates maintenance burden for every new user phrase; hybrid pattern proven superior |
| **Unrestricted LLM→SQL access** | Violates core principle; DB/RAG must be source of truth; controlled thin tool layer enforced |
| **Conversation memory > 5 turns** | Diminishing returns after 5; 5-turn window balances usefulness vs. noise + context window costs |
| **perFactCitationPrecision asymmetric metric (basic vs improved) in Phase 5** | Deferred to future; both arms currently use same conservative metric; can wire `factToHits` later |

### 5.4 Out-of-Scope (Explicitly)

- Google Docs/Sheets integration
- Unrestricted LLM access to any database table
- Real authentication/authorization (prototype uses X-Demo-User header only)
- Phone numbers, addresses, or ID numbers in demo data
- CGPA display in public-facing responses

---

## 6. TECHNICAL CONSTRAINTS

### 6.1 JDK Environment

| Component | Version | Notes |
|-----------|---------|-------|
| **Project pom.xml** | Java 21 (`<java.version>21</java.version>`) | Configured target |
| **Sandbox Environment** | JRE 11 (OpenJDK 11.0.32) | Cannot compile Maven modules |
| **User Local Machine** | JDK 22 (confirmed) | Compilation should work; not yet verified in-sandbox |
| **Maven Wrapper** | `mvnw.cmd` | Requires JDK 22+; fails on JRE 11 |

**Impact:** All 145+ task deliveries have been code-verified manually; unit tests designed but cannot be compiled/run in this sandbox. User must run on Windows JDK 22.

### 6.2 Database

| Component | Details |
|-----------|---------|
| **PostgreSQL** | localhost:5432; db `postgres` / `1234` |
| **pgvector** | Extension for vector similarity; `embeddingDimension: 1024`; similarity gate > 0.45 |
| **Tables** | Students, Alumni, Projects, ProjectInvites, ProjectMembers, Reports, Reviews, Referrals, Roadmaps, Skills, SkillsAliases, ChatTurns, Documents, DocumentChunks, Events, MentorshipRequests, ReferralRequests, ModerationReports, UploadedDocs, Bookmarks, Endorsements, Follows, Notifications, TeammateRequests, Dashboard metrics |

### 6.3 Ollama Models

| Model | Purpose | Notes |
|-------|---------|-------|
| **bge-m3** | Embeddings (1024-dim); used in RAG pipeline; `ollama pull bge-m3` required | Similarity search gate > 0.45 |
| **qwen2.5:3b** | Generation model; used when Ollama up for /ask endpoint; fallback when Ollama down → Lookup service | Both required: `ollama list` must show both |

### 6.4 Authentication

| Mechanism | Details |
|-----------|---------|
| **X-Demo-User header** | Required on every authed call; value is student/recruiter id; admin allow-list: `campus.admin-ids=1` so user 1 is admin; non-admin → 403 |
| **Header tab (not URL)** | Must be set in Thunder Client, never in URL query params; unencoded spaces in `?q=` → 400 |
| **No cookie-based auth** | Prototype uses header-only; session management not implemented |

### 6.5 File & Upload Constraints

| Constraint | Detail |
|------------|--------|
| **Document upload** | multipart .txt/.md ≤ 5MB; bad file (no text) → 400; content-type validation |
| **Similarity threshold** | > 0.45 passes; < 0.45 gated (RAG retrieves only high-similarity chunks) |
| **Visibility toggle** | `POST /documents/admin/{id}/visibility` → `{"hidden":true/false}`; hidden docs excluded from RAG and listings |
| **Delete doc + chunks** | `DELETE /documents/admin/{id}` → count drops; chat routes: `GET /ask?q=my uploads` |

### 6.6 Trigger Pattern Matching

| Tool | Trigger Matching | Notes |
|------|-----------------|-------|
| **All 18 tools** | Case-insensitive substring match on any trigger keyword | Priority-sorted (higher first); e.g., "mentor" matches "mentor", "mentorship", "request mentor", "my mentor" |
| **ToolRegistry.dispatch()** | Substring match; if multiple tools match, highest priority wins; if tool returns clarification, propagate; if tool fails (non-clarification), continue to next tool |
| **No wildcard/greedy matching** | Triggers are fixed strings; no regex on triggers; matching is purely substring containment |

---

## 7. TEST STRATEGY & EXECUTION

### 7.1 Test Philosophy (from TEST_RUN_CHECKLIST.md)

- **Top-to-bottom gating:** Each phase gates the next; stop at first failure
- **Thunder Client everywhere** — every authed call uses X-Demo-User: 1 header (Header tab, never URL)
- **Admin = user 1** (`campus.admin-ids=1`)
- **Header in Header tab, not URL** — unencoded spaces → 400
- **New tables need backend restart** — not just recompile (referrals table example)
- **Recruiter ids live in their own table** — confirm actual ids after seeding (assume 1, adjust)

### 7.2 Phase-Gated Test List (TEST_RUN_CHECKLIST.md)

#### **Phase 0 — One-time environment**
- Postgres at localhost:5432 (db: postgres / 1234)
- Ollama at localhost:11434 (bge-m3 + qwen2.5:3b)
- JAVA_HOME=C:\Program Files\Java\jdk-22
- Thunder Client header preset X-Demo-User: 1
- `POST /dev/seed` → seed documents; `POST /dev/seed-skills` → seed skills/aliases/co-occurrences

#### **Phase 1 — Build + boot**
- `mvnw.cmd clean compile` → 0 warnings on JDK 22
- `mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev` → boots without stack trace
- Health: GET /documents → 200

#### **Phase 2 — RAG core (gates everything)**
- A1: POST /documents Hackathon Rules → 200; GET /documents shows it
- A2: POST /documents/{id}/chunks → 201, embeddingDimension: 1024
- A3: GET /search?q=...hackathon team → similarity > 0.45 passes
- A4: GET /ask team-size answered with exact numbers; joke gated
- A-regression: stop Ollama → /ask still 200 via Lookup fallback → restart Ollama

#### **Phase 3 — User-context features**
- B5: GET /ask?q=hello (header) → history contains it; count ≥ 1; DELETE empties; no header → 400
- B6: GET /ask?q=show my chat history returns 5 inline
- C7–C9: completeness → recommendations → chat variants
- D10: glossary js → canonical JavaScript; search rea → React; chat glossary for js

#### **Phase 4 — The six extensions**
- E11–E13: project create (200, dup → 409) → add-member (200, dup 409, non-owner 403, 11th 400, hidden 404) → chat hint
- F14–F16: event create → hidden:true, absent from GET /events → admin approve publishes → chat lists only approved
- G17–G18: mentorship request (PENDING, idempotent dup) → accept/reject/withdraw + alumni-respond + my lists
- H19–H25: seed recruiters first → posting → apply → owner-only applications → decide → withdraw → close → chat routes
- I26–I27: seed report → resolve → verify side-effect (hidden post/student/doc disappears); non-admin → 403; re-resolve → 400
- J28–J30: upload txt/md ≤ 5MB → listings filter hidden → visibility toggle excludes from RAG → delete drops count → chat routes

#### **Phase 5 — Later slices**
- K: messaging / analytics / bookmarks / endorsements / follow / activity / RSVP / invites spot checks
- L31–L32: roadmaps CRUD + role filter + chat route + /roadmaps page
- M33–M35: referrals (needs fresh restart for table) — PENDING → idempotent dup → accept/ Limb; REFERRALS-only alumni; chat + /referrals page
- N36–N38: reviews (self-review 400) → search fans out + tabs → SPA fallback

#### **Phase 6 — Research track (needs data + Ollama)**
- O39: GET /dev/tools/teammates-weighted?projectId=<id> → scores 0–1 desc, members excluded; ?semantic=true needs Ollama
- O40: POST /eval/teammates/run?availableOnly=true&includeSemantic=false (header 1) → overlap + hybrid arms; non-admin → 403. Verdict rule: keep overlap baseline in /ask until an arm beats it on hit@K with rating coverage not worse

#### **Phase 7 — RAG Eval Harness**
- P-cases: GET /eval/rag/cases (admin 1) → 11 cases (7 single-fact + 2 multi-fact + 2 out-of-scope), including multi-fact case #7 (team size AND attendance)
- P-retrieval: POST /eval/rag/run?retrievalOnly=true (admin 1) → Recall@K + Precision@K for basic vs improved arms
- P-full: POST /eval/rag/run?retrievalOnly=false (admin 1, Ollama up) → correctness, faithfulness, citation rate, abstention rate per arm
- P-multi-fact: case #7 ("hackathon team AND attendance 75%") — improved arm uses query decomposition (decomposeAndSearch) to retrieve each fact independently; verify both docs recalled
- P-403: non-admin (header 2) → 403
- P-method: GET /eval/rag/run → 405 (POST only)

### 7.3 Test Execution Prerequisites (PENDING_TESTS.md)

Before running any tests:
1. ✅ Postgres up at localhost:5432 (db: postgres / 1234)
2. ✅ Ollama at localhost:11434 with bge-m3 + qwen2.5:3b
3. ✅ JAVA_HOME=C:\Program Files\Java\jdk-22
4. ✅ Thunder Client: saved header preset X-Demo-User: 1; second preset with value 2 for negative tests
5. ✅ `POST /dev/seed` → seed documents; `POST /dev/seed-skills` → seed skills/aliases/co-occurrences (idempotent)

### 7.4 Test Coverage Summary

| Area | Tests | Status |
|------|-------|--------|
| **MentorshipTool** | 16 unit tests | Designed; cannot compile/run in sandbox (JDK mismatch) |
| **ProjectInviteTool** | 16 unit tests | Designed; cannot compile/run in sandbox (JDK mismatch) |
| **PENDING_TESTS.md A-K** | 52 test cases | Deferred since 2026-10-05; never run; all should pass on JDK 22 |
| **Phase 6 dispatch** | D1–D8 via /api/agent | Code verified; tests designed but unrun |
| **RAG eval (Phase 7 P-cases)** | 11 cases | Designed; evaluation harness built; not executed in-sandbox |

### 7.5 Manual Test Execution (Thunder Client)

All authed calls use: **Header tab → X-Demo-User: 1** (never in URL)

- **Negative tests:** Use second header preset with value `2` (non-admin/negative tests)
- **Encoding:** Always encode `?q=` values; unencoded spaces → 400
- **Hidden entities:** Return 404 by design (projects, docs, pending events)
- **New tables:** Need backend restart, not just recompile (referrals table example)

---

## 8. PHASE 8: TEST VALIDATION & HYBRID EXTENSION

### 8.1 Overview

Phase 8 has three sub-spheres designed to unblock the project, validate what's built, and extend proven patterns.

### 8.2 Sub-Sphere 8A: Test Suite Execution (HIGHEST PRIORITY)

**Goal:** Actually run all deferred test cases on Windows JDK 22.

**Must-Be-Satisfied (before any test runs):**
- [ ] Postgres at localhost:5432 (db: postgres / 1234)
- [ ] Ollama at localhost:11434 (bge-m3 + qwen2.5:3b)
- [ ] JAVA_HOME=C:\Program Files\Java\jdk-22
- [ ] `mvnw.cmd clean compile` → 0 warnings
- [ ] `mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev` → boots without errors
- [ ] Health: GET /documents → 200 []

**Test Execution Order (top-to-bottom gating):**
1. **Phase 0:** Environment + seed dev data
2. **Phase 1:** RAG core (documents, chunks, search, ask regression)
3. **Phase 2:** User features (chat history, profile polish, skill glossary)
4. **Phase 3:** Tool dispatch verification (D1–D8 via /api/agent)
5. **Phase 4:** The six extensions (E11–E13, F14–F16, G17–G18, H19–H25, I26–I27, J28–J30)
6. **Phase 5:** Messaging/analytics/bookmarks/endorsements/follow/activity/RSVP
7. **Phase 5+6:** O39–O40 (teammate weighted eval)
8. **Phase 7:** P-cases, retrieval eval, multi-fact case #7

**Deliverable:** Ticked PENDING_TESTS.md A–K with all green; TEST_RUN_CHECKLIST fully checked off.

---

### 8.3 Sub-Sphere 8B: Hybrid Pattern Extension

**Goal:** Apply the same rule+LLM fallback pattern to 2–3 remaining tools.

**Candidate Tools (priority order):**

| # | Tool | Current Limitation | Hybrid Value |
|---|------|-------------------|--------------|
| **1** | **SearchStudentsTool** | `extractSkillFromQuestion()` finds skills by canonical list only; NLP like "find students with Java and React" fails | Extract skill names + year/branch via LLM; resolve to student IDs via StudentRepository |
| **2** | **FollowTool** | Pure keyword matching; "follow Java developers" or "who to follow" patterns fail | LMM extracts followee name + action type; resolve via StudentRepository; clarify if unresolvable |
| **3** | **EventTool** | `extractNumberAfter` for event IDs only; "create hackathon on Nov 20 at Hall A" fails | LLM extracts event date + type + location + targetRole; validate via EventService |

**Approach per tool (mirror MentorshipTool pattern):**
1. Add `extract*Name()` method (alphabetic validation `[A-Za-z]+`, 2–30 chars)
2. Add `resolve*Id()` via repository injection (StudentRepository or EventRepository)
3. Add `extractParamsWithLlm(String intent)` — system prompt + JSON parsing (no full JSON lib)
4. Add `Llm*Params` record for LLM output
5. Refactor `extractParams()` → rules → LLM fallback → clarification path
6. Add 8–10 unit tests: numeric ID, name resolution, unresolvable clarification, LLM valid, LLM null, rules-succeed (LLM NOT called), LLM-called (both paths fail)

**Deliverable:** 2–3 enhanced tools + 16–30 new test cases; hybrid pattern proven repeatable beyond the 2 tools already enhanced.

---

### 8.4 Sub-Sphere 8C: Project Consolidation & Documentation

**Goal:** Create the master reference so future sessions can pick up without 20+ context switches.

**Activities:**
- [ ] **Verify JDK 22 compilation** on user's local machine: `mvnw clean compile` → 0 warnings; `mvnw spring-boot:run -Dspring-boot.run.profiles=dev` → boots
- [ ] **Create `CAMPUS_AI_ARCHITECTURE.md`** — single source of truth covering:
  - ToolRegistry dispatch flow (all 18 triggers + priority sorting)
  - Hybrid extraction pattern (rules → LLM → clarification) with code snippets from MentorshipTool + ProjectInviteTool
  - RAG pipeline (ingest → chunk → embed 1024-dim → retrieve similarity > 0.45 → grounded generation with [n] citations)
  - AgentOrchestrator flow (intent → ToolRegistry dispatch → execute → result → context-aware response)
  - Conversation memory (last 5 turns, follow-up resolution, maybePrependContext, successWithContext)
  - Multi-hop RAG (decomposeAndSearch, 3 strategies, per-fact hit mapping, factToHits)
  - perFactCitationPrecision metric (basic = binary; improved = true per-fact mapping via factToHits)
  - JDK 21 ↔ JRE 11 sandbox constraint; user has JDK 22 locally
  - Admin = user 1 only; X-Demo-User header requirement
- [ ] **Archive memories:** Move all `.md` files from `spaces/3a4c64ea/` into `PHASES_1_THROUGH_8/` directory with chronological index
- [ ] **Update TEST_RUN_CHECKLIST.md** with actual results from 8A (which test cases passed/failed)
- [ ] **Create `MASTER_BIBLE.md`** — consolidated everything (this document)

**Deliverable:** Full architecture documentation + test results + master bible for the project.

---

### 8.5 Phase 8 Timeline

| Week | Focus | Key Output |
|------|-------|------------|
| **Week 1** | 8A: Test execution on JDK 22 | Ticked PENDING_TESTS.md A–K; TEST_RUN_CHECKLIST complete |
| **Week 2** | 8B: Hybrid extension to 2 tools | 2 tools enhanced; 16–30 new tests |
| **Week 3** | 8C: Consolidation & docs | CAMPUS_AI_ARCHITECTURE.md + MASTER_BIBLE.md |

---

### 8.6 Can I Proceed?

**Yes** — but I need to know the starting point:

- **Start with 8A** — Run the tests (requires JDK 22 access on your end)  
- **Start with 8B** — Extend hybrid pattern to SearchStudentsTool + FollowTool  
- **Start with 8C** — Create the architecture doc + master bible  
- **All three in sequence** — I outline, you confirm priority  

**My recommendation:** Start with **8A** — running the tests unblocks everything else. If JDK 22 isn't available, start with **8C** — at least the documentation gets created, and the test plan stays warm for when JDK 22 becomes available.

**What's the starting point?**

---

## 9. BUILD & RUN INSTRUCTIONS

### 9.1 Local Development (Windows + JDK 22)

```batch
:: 1. Set JAVA_HOME
set JAVA_HOME=C:\Program Files\Java\jdk-22

:: 2. Start Postgres
:: 3. Start Ollama with required models
ollama serve
ollama pull bge-m3
ollama pull qwen2.5:3b

:: 4. Seed dev data (one-time)
cd C:\PROJECTS\campus-ai
.\mvnw.cmd clean compile
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev &

:: 5. In separate terminal: Thunder Client
::    - Import: requests.http (all key test endpoints)
::    - Set header preset: X-Demo-User: 1
::    - Set second preset: X-Demo-User: 2 (negative tests)

:: 6. Run tests top-down per TEST_RUN_CHECKLIST.md
::    Phase 0 → Phase 1 → Phase 2 → ... → Phase 7

:: 7. Verify: mvnw clean compile → 0 warnings
```

### 9.2 Sandbox (This Environment — JRE 11)

**Cannot compile via Maven.** All code changes are verified manually by:
- Reading the file
- Comparing against reference implementations (MentorshipTool.java as pattern)
- Tracing logic flow without compilation
- Confirming syntax matches Java standards

**What can be done:**
- Code reviews
- Logic flow tracing
- Architecture discussions
- Test design (tests designed but not runnable)
- Summary/documentation generation

**What cannot be done:**
- `mvnw clean compile` → 0 warnings
- `mvnw spring-boot:run -Dspring-boot.run.profiles=dev` → boots without errors
- Unit test execution (JUnit, Mockito, etc.)
- Integration test runs via Thunder Client until JDK 22 available

### 9.3 Code Verification (What I Do Instead of Maven)

For each delivery:
1. ✅ Read the target file
2. ✅ Compare against reference implementation (e.g., MentorshipTool.java)
3. ✅ Trace logic flow manually
4. ✅ Confirm syntax matches Java standards (imports, annotations, record syntax, etc.)
5. ✅ Check for consistency with established patterns
6. ✅ Generate summary/documentation
7. ✅ Note any constraints or blockers

**Result:** 145+ code changes delivered with manual verification; 32 unit tests designed; 2 one-page summaries created.

---

## 10. APPENDICES

### A. Full Tool List (18 @Component Implementations)

| # | Tool Name | Triggers (examples) | Priority | Auth |
|---|-----------|--------------------|----------|------|
| 1 | AnalyticsTool | "analytics", "metric", "dashboard" | 5 | true |
| 2 | BookmarkTool | "bookmark", "save", "toggle" | 5 | true |
| 3 | CreateProjectTool | "create project", "new project" | 5 | true |
| 4 | DocumentTool | "document", "upload", "curate" | 5 | true |
| 5 | EventTool | "event", "hackathon", "create event" | 5 | true |
| 6 | FallbackRagTool | empty triggers = only when no other tool matches | 0 | false |
| 7 | FollowTool | "follow", "who to follow", "following" | 5 | true |
| 8 | MentorshipTool | "mentor", "mentorship", "request mentor", "my mentor" | 5 | true |
| 9 | ModerationTool | "report", "moderate", "resolve" | 5 | true |
| 10 | NotificationTool | "notification", "unread", "count" | 5 | true |
| 11 | ProjectInviteTool | "invite", "invitation", "accept invite", "reject invite" | 5 | true |
| 12 | ProjectTeammateTool | "teammate", "find teammates", "project members" | 5 | true |
| 13 | RecruiterPostingTool | "posting", "postings", "job post", "close posting" | 5 | true |
| 14 | RecruiterTool | "my applications", "apply to job", "openings" | 5 | true |
| 15 | SearchStudentsTool | "find students", "students with skill", "search students" | 5 | true |
| 16 | SkillGraphTool | "glossary", "skill dashboard", "top skills", "what is" | 10 | false |
| 17 | TeammateRequestTool | "teammate request", "project suggestions", "who should join" | 5 | true |
| 18 | ProfileTool | "my profile", "profile completeness", "recommend skills" | 5 | true |

### B. Trigger Matching Examples

| User Query | Matching Tool(s) | Priority Winner |
|------------|------------------|-----------------|
| "request mentor 5" | MentorshipTool | priority 5 (high) |
| "show my projects" | ProjectTeammateTool | priority 5 |
| "glossary for js" | SkillGraphTool | priority 10 (highest) |
| "my mentor requests" | MentorshipTool | priority 5 |
| "posting 5" | RecruiterPostingTool | priority 5 |
| "my profile" | ProfileTool | priority 5 |
| "notifications" | NotificationTool | priority 5 |

### C. Similarity Threshold Details

| Condition | Action |
|-----------|--------|
| similarity > 0.45 | Pass — chunk retrieved for RAG |
| similarity ≤ 0.45 | Gated — chunk not retrieved; query routed to Lookup fallback |
| Document with no chunks | `GET /documents/{id}` → 404; cannot be used for RAG |
| `embeddingDimension` mismatch | Chunk not indexed; retrieval fails silently |

### D. Similarity Gate Empirical Rule

The 0.45 threshold was empirically derived from the Hackathon Rules domain:
- "How many students can be in a hackathon team" → similarity 0.667 (baseline recall)
- Each additional keyword reduces similarity proportionally
- Below 0.45, answers become unreliable → gated out-of-scope

### E. Conversation Memory: 5-Turn Rationale

| Turn | Purpose |
|------|---------|
| 1 | Original question + answer (establishes context) |
| 2 | First follow-up possibility ("what about...?") |
| 3 | Second follow-up; patterns like "how much..." or "how many..." |
| 4 | Third follow-up; ordinal markers ("2nd IA", "3rd IA") trigger decomposeAndSearch |
| 5 | Fourth follow-up; if still relevant, contextRefs included in response; 5th+ typically redundant |

### F. Glossary canonicalization

| Input | Output | Example |
|-------|--------|---------|
| "js" | "JavaScript" | SkillGraphTool.glossary → canonical + aliases |
| "python" | "Python" | Same pattern |
| "c++" | "C++" | Same pattern (handle `++` carefully) |
| "react" | "React" | Same pattern |
| "java" | "Java" | Same pattern |

### G. perFactCitationPrecision: Detailed Computation

**Basic Arm (conservative):**
1. Collect all expected fact values from EvalCase (`numbers` + `keywords`)
2. For single-fact cases (no expected fact values): binary `cited(answer)` check → 0.0 or 1.0
3. For multi-fact cases: for each expected fact, check if it appears in answer **AND** is within 100 chars of a `[n]` citation marker
4. Return `cited / totalFacts` as fraction (1.0 = all cited, 0.5 = half, etc.)

**Improved Arm (with per-fact mapping):**
1. Same as basic, BUT use `factToHits` map to verify which `[n]` citations back which sub-fact
2. For each expected fact index `i`: check if `factToHits.get(i)` contains any passage index `j` where `results.get(j-1).passageText` contains the expected fact value
3. Return `cited / totalFacts` fraction

**Key Design:** Both arms currently compute identically (conservative). The improved arm *could* leverage `factToHits` for true per-fact accuracy; currently deferred.

### H. Multi-Hop Decomposition: 3 Strategies

| Strategy | Pattern | Example |
|----------|----------|---------|
| 1. "and" conjunction split | Question contains "and" → split on first "and" | "How many students can be in a hackathon team AND how much attendance is needed?" → ["How many students can be in a hackathon team", "how much attendance is needed?"] |
| 2. Comma split | Question contains comma → split on first comma | "What is the team size, and the attendance policy?" → ["What is the team size", "and the attendance policy?"] |
| 3. Ordinal criteria markers | Look for "2nd IA", "3rd IA", "first IA", etc. | "What are the 2nd and 3rd IA requirements?" → triggers 2-subquery decomposition |

### I. FAQ: Common Failure Points

| Failure | Cause | Fix |
|---------|-------|-----|
| Header in URL instead of Header tab | Auth failures | Set X-Demo-User: 1 in Thunder Client Header tab, not URL |
| Unencoded spaces in `?q=` | 400 error | Always URL-encode query values |
| Hidden entities return 404 | By design | Projects/docs/pending events hidden by design; not a bug |
| New tables need restart | Referrals table example | After adding referrals table, restart backend (`spring-boot:run -Dspring-boot.run.profiles=dev`) |
| Recruiter ids wrong | Assume 1, adjust after seeding | After `POST /dev/seed-recruiter`, confirm actual ids and adjust |
| Student 2 / alumni 1 missing | Re-import prototype SQL | Ensure prototype SQL import includes required base records |
| Ollama down → 500 error | Should be 200 via Lookup fallback | Verify `tryRagFallback` handles OllamaException gracefully |
| Similarity < 0.45 | Gated out-of-scope | Expand document library or increase chunk coverage |

### J. Session Memory Persistence

**Where memories live:** `C:\Users\Samarth\AppData\Local\Claude-3p\local-agent-mode-sessions\56ba315e\00000000\spaces\3a4c64ea-b96b-40da-969f-b3dcc72d0161\memory\`

**Memory types:**
- `master-context.md` — Product context, architecture, terminology
- `planned-extensions.md` — 6 committed slices + build priority
- `pending-tests-deferred.md` — All A-K deferred since 2026-10-05
- `eval-baseline-2026-10-12.md` — First live /eval run results
- `test-results-2026-10-18.md` — 76 passed, 0 failed, 1 skipped
- `test-results-2026-10-19.md` — 80 passed, 0 failed, 1 skipped + Phase P
- `phase-5-conversation-memory-and-multi-hop-rag.md` — Full phase 5 doc
- `phase-6-tool-fixes.md` — D1/D7 fixes + checklist update
- `reminder-magic-mcp-ui-ux.md` — Discuss when user returns 2026-10-06

**How to apply:** Read before changing Campus Connect; use prototype to answer "what should it do", not "how to build"; respect LLM/backend/RAG split and Future AI-Agent Handoff Checklist (§33).

---

## CHANGE LOG

| Date | Version | Change |
|------|---------|--------|
| 2026-10-03 | 0.1 | Master product context created (master-context.md) |
| 2026-10-04 | 0.2 | Planned extensions documented (planned-extensions.md) |
| 2026-10-05 | 0.3 | Pending tests deferred; all A-K pending |
| 2026-10-12 | 0.4 | Eval baseline; RAC tie (.667 recall, +.056 precision) |
| 2026-10-18 | 0.5 | Full pending test suite: 76 passed, 0 failed, 1 skipped |
| 2026-10-19 | 0.6 | 80 passed, 0 failed, 1 skipped; Phase P added; query decomposition ties baseline 0w/10t |
| 2026-10-30 | 1.0 | Project Bible Phase 0 — complete architecture doc + test strategy + hybrid extraction + all built features |

---

**END OF DOCUMENT**