# Campus AI — Windows Test Run Checklist (Triage)

Companion to `PENDING_TESTS.md`. Run top-to-bottom: each phase gates the next.
Thunder Client everywhere; header `X-Demo-User: 1` on every authed call (Header tab, never URL).
Admin = user 1 (`campus.admin-ids=1`).

## Phase 0 — One-time environment (do once, verify each session)

- [ ] Postgres up at `localhost:5432` (db `postgres` / `1234`)
- [ ] Ollama up at `localhost:11434` with models `bge-m3` + `qwen2.5:3b` (`ollama list`)
- [ ] `set JAVA_HOME=C:\Program Files\Java\jdk-22` (Linux VM here reports `release 21 not supported` — expected, ignore)
- [ ] Thunder Client: saved header preset `X-Demo-User: 1`; second preset with value `2` for non-admin/negative tests
- [ ] `POST /dev/seed` → seed documents; `POST /dev/seed-skills` → seed skills/aliases/co-occurrences

## Phase 1 — Build + boot (fresh restart required)

Why fresh: referral table + latest bundle only load on restart.

Note: Phase 6 refactor replaced all inline `tryX()` intent classification in AgentOrchestrator
with a dynamic ToolRegistry dispatch. 18 `@Component Tool` implementations are auto-registered
via `ToolAutoConfiguration`. Tools use case-insensitive substring trigger matching, sorted by
priority (higher first). RAG fallback is still handled directly by `AgentOrchestrator.tryRagFallback`.

- [ ] `mvnw.cmd clean compile` → 0 warnings on JDK 22
- [ ] `mvnw.cmd spring-boot:run` → boots without stack trace
- [ ] Health: `GET /documents` → 200 (list or `[]`)

## Phase 2 — RAG core (Section A — gates everything else)

If A fails, stop and fix before continuing; every chat route depends on it.

- [ ] A1: `POST /documents` Hackathon Rules → 200; `GET /documents` shows it
- [ ] A2: `POST /documents/{id}/chunks` → 201, `embeddingDimension: 1024`
- [ ] A3: `GET /search?q=...hackathon team` → similarity > 0.45 passes
- [ ] A4: `GET /ask` team-size answered with exact numbers; `GET /ask?q=Tell me a joke` gated
- [ ] A-regression: stop Ollama → `/ask` still 200 via Lookup fallback → restart Ollama

## Phase 3 — User-context features (Sections B, C, D — quick wins)

- [ ] B5: `GET /ask?q=hello` (header) → history contains it; count ≥ 1; `DELETE` empties; no header → 400
- [ ] B6: `GET /ask?q=show my chat history` returns 5 inline
- [ ] C7–C9: completeness → recommendations → chat variants (`how complete is my profile`, `recommend skills`)
- [ ] D10: glossary `js` → canonical JavaScript; search `rea` → React; chat `glossary for js`

### Phase 6 Tool dispatch verification (category D queries via /api/agent)

Each query should route to the correct Tool via trigger matching:

- [ ] D1: `show my projects` → ProjectTeammateTool.list → lists user's projects (or empty state)
- [ ] D2: `find teammates` → ProjectTeammateTool.search → skill gaps + candidates
- [ ] D3: `upcoming events` → EventTool → approved upcoming events only
- [ ] D4: `my bookmarks` → BookmarkTool → saved items
- [ ] D5: `notifications` → NotificationTool → unread count + recent
- [ ] D6: `who knows Java` → SearchStudentsTool → students with Java skill
- [ ] D7: `glossary for js` → SkillGraphTool.glossary → canonical JavaScript + aliases
- [ ] D8: `skill graph dashboard` → SkillGraphTool.dashboard → top skills + stats

Loose teammate-discovery phrasings — all must route to ProjectTeammateTool.search (regression-tested in `ProjectTeammateToolTriggerTest`):

- [ ] D8a: `can u find me a best teammates for the auto hack hacathon` → ProjectTeammateTool.search
- [ ] D8b: `find me a best teamates for the auto hack hacathon` (typo) → typo-normalized, ProjectTeammateTool.search
- [ ] D8c: `help me find a teammate` → ProjectTeammateTool.search
- [ ] D8d: `find me a team for the hackathon` → ProjectTeammateTool.search
- [ ] D8e: `I need a teammate for my capstone` → ProjectTeammateTool.search
- [ ] D8f: `search for a teammate with React skills` → ProjectTeammateTool.search (must NOT route to SearchStudentsTool)
- [ ] D8g: `find me a mentor for ai` → MentorshipTool (guard: must NOT match teammate triggers)

### Event-aware teammate discovery (2026-11-01)

Requires seeded projects + an AI/ML-ish event. Tool routes on the event named in the query.

- [ ] D9a: `find me a best teamates for the auto hack hacathon` → resolves event, infers domain, replies "'<title>' is a hackathon about **AI / Machine Learning** …"
- [ ] D9b: same query when the user owns a project whose skills overlap the domain → reply says "Your project \"X\" matches this event — domain overlap NN%. Skill gaps: …" then top-3 candidates with `score` + a "why" line (matched skills / endorsements / rating / available)
- [ ] D9c: same query when NO project matches → "You don't have a project that matches this event yet." + top-3 for the domain + offer to create a project from scratch
- [ ] D9d: `show my projects` → ProjectTeammateTool.list (unchanged)
- [ ] D9e: `find teammates` (no event named) → weighted per-project search, top-3 per project with scores (NOT a full candidate dump)

### Chat answer-quality fixes (2026-11-01)

- [ ] Q1: `what about 2nd and 3rd internals` → answer contains the real numbers (80% / 85%) and does NOT print literal "Fact 1:" / "Fact 2:" labels
- [ ] Q2: same → answer does not both assert a value AND claim "no information about 3rd internals" (self-contradiction dropped)
- [ ] Q3: `how can i prepare for the upcoming placement drive` then `when does it start` → resolves to the placement drive (no "I need a bit more context")
- [ ] Q4: `any upcoming ai workshops` → topic filter uses whole-word matching; events whose titles merely contain "ai" as a substring (e.g. "Main Hall") are NOT pulled in

## Phase 4 — The six extensions (Sections E–J, in dependency order)

- [ ] E11–E13: project create (200, dup → 409) → add-member (200, dup 409, non-owner 403, 11th 400, hidden 404) → chat hint
- [ ] F14–F16: event create → `hidden:true`, absent from `GET /events` → admin approve publishes → chat lists only approved
- [ ] G17–G18: mentorship request (PENDING, idempotent dup) → accept/reject/withdraw + alumni-respond + `my` lists
- [ ] H19–H25: **seed recruiters first** (`POST /dev/seed-recruiter`) → posting → apply → owner-only applications → decide → withdraw → close → chat routes
- [ ] I26–I27: seed report (`POST /reports`, self-STUDENT-report → 400) → resolve → verify side-effect (hidden post/student/doc disappears); non-admin → 403; re-resolve → 400
- [ ] J28–J30: upload txt/md ≤ 5MB → listings filter hidden → visibility toggle excludes from RAG → delete drops count → chat routes

## Phase 5 — Later slices (Sections K–N)

- [ ] K: messaging / analytics / bookmarks / endorsements / follow / activity / RSVP / invites spot checks
- [ ] L31–L32: roadmaps CRUD + role filter + chat route + `/roadmaps` page
- [ ] M33–M35: referrals (needs Phase-1 fresh restart for table) — PENDING → idempotent dup → accept/ Limb; REFERRALS-only alumni; chat + `/referrals` page
- [ ] N36–N38: reviews (self-review 400) → search fans out + tabs → SPA fallback (`Accept: text/html` → 200 index.html)

## Phase 6 — Research track (Section O — needs data + Ollama)

- [ ] O39: `GET /dev/tools/teammates-weighted?projectId=<id>` → scores 0–1 desc, members excluded; `?semantic=true` needs Ollama
- [ ] O40: `POST /eval/teammates/run?availableOnly=true&includeSemantic=false` (header 1) → overlap + hybrid arms; non-admin → 403
- [ ] O40-semantic: rerun with `includeSemantic=true` (Ollama up) → third arm appears
- [ ] Verdict rule: keep overlap baseline in `/ask` until an arm beats it on hit@K with rating coverage not worse

## Phase 7 — RAG Eval Harness

- [ ] P-cases: `GET /eval/rag/cases` (admin 1) → 11 cases (7 single-fact + 2 multi-fact + 2 out-of-scope), including multi-fact case #7 (team size AND attendance)
- [ ] P-retrieval: `POST /eval/rag/run?retrievalOnly=true` (admin 1) → Recall@K + Precision@K for basic vs improved arms
- [ ] P-full: `POST /eval/rag/run?retrievalOnly=false` (admin 1, Ollama up) → correctness, faithfulness, citation rate, abstention rate per arm
- [ ] P-multi-fact: case #7 ("hackathon team AND attendance 75%") — improved arm uses query decomposition (`decomposeAndSearch`) to retrieve each fact independently; verify both docs recalled
- [ ] P-403: non-admin (header 2) → 403
- [ ] P-method: `GET /eval/rag/run` → 405 (POST only)

## Gotcha list (check when a test fails before debugging code)

- Header in URL instead of Header tab → auth failures
- Unencoded spaces in `?q=` → 400; always encode
- Hidden entities return 404 by design (projects, docs, pending events)
- New tables (referrals) need a backend restart, not just recompile
- Recruiter ids live in their own table — confirm actual ids after seeding (assume 1, adjust)
- Student 2 / alumni 1 must exist (prototype SQL import); if missing, re-import before E–M
