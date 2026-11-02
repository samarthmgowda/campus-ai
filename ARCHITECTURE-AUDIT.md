# Architecture Audit: campus-ai vs. "How Companies Build AI Assistants"

**Scope:** campus-ai source (`src/main` + `src/test`, `pom.xml`, `application.properties`). Every claim is backed by a `file:line` reference.
**Verification note:** I read the code and config directly. I did **not** execute the full live system (no running Ollama/pgvector in this sandbox), so runtime-behavior claims that rest solely on "as written in code" are so flagged.

---

## PART 1 — Reference architecture (the lecture, distilled)

The lecture is an **evolution narrative**, not a checklist. Its eight milestones are stages companies pass through, and the real question is: *which of these matter for campus-ai at its current size and purpose?*

| Stage | What it means in practice | Small-project relevance |
|---|---|---|
| 1. LLM integration | A model backend (cloud or local) invoked by the app. | One endpoint is enough — cloud or local. |
| 2. System prompting | Identity + persona + scope injected as a system message. | Essential — this is where "who am I?" and "don't answer XYZ" live. |
| 3. RAG | Chunk to embed to vector store to top-K retrieve to inject into context to grounded answers. | Essential *if* the bot answers from private docs. |
| 4. Agentic / tool calling | The LLM can *act* on external systems through typed tools. | Essential for a bot that "does things" (bookmarks, invites, etc.). |
| 5. Auth + RBAC | Caller identity known and enforced at every data touchpoint. | Essential once the bot returns private data. |
| 6. Guardrails | Reject injection/jailbreak, redact PII, refuse out-of-scope. | Essential — but minimal is fine for a demo. |
| 7. Cache + model routing | Cache repeated answers; route cheap vs. hard queries to the right model. | Optional at small scale; a cost/latency lever. |
| 8. Observability + eval | Trace prompts/retrieval/tools; run evals on every change. | Recommended before real users. |

**The lecture's lesson, translated to this project:** you build the layers that your users actually need, in order, and you stop adding layers when the marginal cost exceeds the marginal safety. campus-ai is a demo/educational system with a seeded corpus (4 documents across ~15 chunks per `DevSeedController.java:33-48`) and one local model. That frames the whole audit.

---

## PART 2 — Audit: campus-ai against the eight components

> Status legend: **Fully Implemented / Partially Implemented / Not Implemented / Not Required for Our Project / Cannot Verify.** "Implemented" means integrated into the live request path, not merely present as a dependency.

| # | Architectural Component | What the lecture expects | campus-ai implementation | Status | Technical assessment |
|---|---|---|---|---|
| 1 | LLM Integration | A model backend invoked by the app; ideally with retry/timeout/error handling. | `OllamaChatService.java` calls Ollama `/api/chat` with `qwen2.5:3b`; `EmbeddingService.java` calls `/api/embed` with `bge-m3`. Config: `ollama.base-url`, `ollama.chat-model`, `ollama.embedding-model` in `application.properties:9-11`. | **Fully Implemented** | Correct and sufficient for a single-model local setup. **Missing:** embedding client has *no timeout* (`EmbeddingService.java:30` uses `RestClient.create(baseUrl)` with no `RequestFactory`/`ReadTimeout`), unlike the chat client which sets `120_000ms` (`OllamaChatService.java:24`). On a slow or hung Ollama, an embed call can block arbitrarily. One-line fix; worth doing. |
| 2 | System Prompting | Genuine identity + persona + scope + behavior guard. | Single system prompt in `AskService.java:162-176`: "You are Campus AI, an assistant for a college platform… Answer using ONLY the context below… cite as [n]… abstain if not present." Scope guard present. | **Partially Implemented** | It *works* as a prompt, but it is a hardcoded string inline with no single source of truth, no per-turn persona, and no scope refusal for *out-of-domain* questions (e.g. "tell me a joke"). Scope is enforced by *route-gating* (tool-first then RAG) more than by the prompt. For a demo this is acceptable; for anything user-facing it should be extracted into one `system-prompt.md`. |
| 3 | RAG | Chunk→embed→vector store→retrieve→ground. | Real pipeline: `DocumentIngestionService.java` chunks+embeds into `DocumentChunk.embedding vector(1024)` (`DocumentChunk.java:19`); `SearchService.java` does semantic (pgvector `<=>` cosine) + keyword (Postgres `ts_rank`) fused via weighted RRF; `AskService.java` injects top-K into a grounded prompt. | **Fully Implemented** | Genuine RAG, not superficial. pgvector is real (`hibernate-vector` + `vector(1024)` in `pom.xml:39`-`DocumentChunk.java:19`; native `<=>` query in `DocumentChunkRepository.java:30`). Strengths: hybrid semantic+keyword, IDF rerank, faithfulness gate with number verification. **Gaps** enumerated in Part 3. |
| 4 | Agentic / Tools | LLM acts through typed tools; no "LLM→SQL". | 18 `@Component` tools (`service/tools/*.java`, 18 files), dispatched by `ToolRegistry.java` priority match against `triggers()`. Tools call real services/repositories — no LLM-to-DB. `AgentOrchestrator.java` runs intent→tool→RAG-fallback loop. | **Fully Implemented** | Solid. Note: `ToolRegistry.findMatchingTools` does case-insensitive substring on trigger keywords (not a real intent classifier), so the "agent" is really a rules-first router with LLM only on fallback — exactly the safer design. `MentorshipTool`/`ProjectInviteTool` do call Ollama for *classification*, not data access. |
| 5 | Auth + RBAC | Identity known + enforced per data touchpoint. | Identity = `X-Demo-User` header parsed in controllers (`AgentController.java:22`, `AskController.java:18`, 233 occurrences repo-wide). `CurrentUserService.requireUser` validates against `student_repository`. Admin = `campus.admin-ids` list (`DocumentService.java:22`, `application.properties:34`). Per-data-object ownership checks exist on several tools. | **Partially Implemented** | Functional for a demo but **not** real RBAC: identity is a client-supplied header with no cryptographic trust; "admin" is a comma list, not scopes/roles; no auth *middleware* (no `spring-boot-starter-security` in `pom.xml`). Sufficient for a portfolio project; not for production. |
| 6 | Guardrails | Reject injection/jailbreak, redact PII, refuse out-of-scope. | Prompt-scoped ("ONLY the context below"). Input sanitization: HTML-strip + length cap in `ProfileTool.java:221-233` and `DocumentTool.java:218-225`. File-type whitelist + 5 MB cap in `DocumentIngestionService.java:9-10`. Faithfulness gate abstains on hallucination (`AskService.java:186-203`). | **Partially Implemented** | **Real gaps:** (a) no prompt-injection defense — the model *is* the classification layer in some tools (`MentorshipTool.java:262`, `ProjectInviteTool.java:269`) and user text is forwarded into it; (b) no PII redaction in outputs — profile data can flow through `LookupService.java:201-215` into a chat answer; (c) `ToolRegistry.dispatch` swallows tool exceptions silently (`ToolRegistry.java:63-66`) — a failing tool vanishes instead of refusing safely. |
| 7 | Semantic Cache + Model Routing | Cache repeated answers; route cheap/hard to different models. | Not present. There is `@Cacheable` search in `TeammateEvalService`/`TeammateMatchingService` (two unrelated services), used for *matching computation*, not LLM answers. `OllamaChatService` hardcodes one model; config has no routing flags. | **Not Implemented** | **Not Required for Our Project.** The corpus is ~15 chunks and there's one local model. A cache here would add complexity for zero measured benefit. Correctly *not* built. |
| 8 | Observability + Evals | Trace prompts/retrievals/tools; evals per change. | Eval harness exists: `RagEvalService.java` + `RagEvalController.java:26-39` (admin-gated). Runs basic-vs-improved retrieval arms on a curated Q/A set (`RagEvalService.java:55-90`), reports Recall@K, correctness, faithfulness, latency, abstention (`RagEvalService.java:34-53`). No distributed tracing, no structured logs, no `spring-boot-starter-actuator`. | **Partially Implemented** | The *eval* part is genuinely implemented and is the more important half at this scale; the *tracing/logging* part is not. A lightweight eval gate before each release is exactly the right place to spend effort here. |

---

## PART 3 — RAG deep-dive

A system is "genuinely RAG" when every answer is produced by (a) retrieving evidence, (b) conditioning the model on it, and (c) refusing to fabricate when evidence is absent. campus-ai satisfies all three.

**What's genuinely good here**
- *Hybrid retrieval with a real fusion* — pgvector cosine (`<=>`) plus Postgres `ts_rank`, combined by reciprocal-rank fusion (`SearchService.java:36-99`). This is stronger than pure embedding retrieval on a small corpus.
- *Deterministic rerank without a new dependency* — `RerankService.java` fuses semantic score + IDF-weighted term overlap + bigram overlap + title-match (`RerankService.java:60-79`). IDF down-weights frequent words so "2 to 4" beats "student".
- *Faithfulness gate with number verification* — `AskService.isFaithful` requires (1) term-overlap >= 0.15 and (2) every number in the answer appears verbatim in context (`AskService.java:250-289`). This is a strong, cheap anti-hallucination control.
- *Citations and out-of-scope handling* — numbered `[n]` context; explicit abstain string; retry-on-abstain path (`AskService.java:186-203`).
- *Multi-fact decomposition* — `AskService.decompose`/`decomposeAndSearch` (`AskService.java:218-305`) splits "X and Y" into per-fact retrieval passes and maps passages back to each fact, then re-ranks down to top-K. This materially fixes the "answers one clause of a compound question" failure mode.

**What's weak / missing**
1. **No vector index → O(scan) retrieval.** There is no `CREATE INDEX ... ON ... USING hnsw/ivfflat` anywhere (`grep` returned nothing; `schema.sql` doesn't exist — `hibernate.ddl-auto=update` in `application.properties:7` just `ALTER`s the table). With ~15 chunks this is invisible; at 1,000+ it becomes a linear scan per query. **Medium.**
2. **No embedding-dimension sanity at ingest time beyond the runtime check.** `EmbeddingService.embed` throws if size != 1024 (`EmbeddingService.java:56-60`). Good guard, but it only triggers at embed call — if the model ever changes dimension, the whole index is silently inconsistent. **Low** at current scale.
3. **Chunk size is fixed at ~2800 chars with 200-char overlap** (`DocumentIngestionService.java:180-200`). Fine for policy docs; poor for code/structured data. Acceptable here. **Low.**
4. **No context window budgeting.** `AskService.answerWithHits` injects *all* top-K chunks into the prompt with no token cap (`AskService.java:170-176`). With `topK=3` and small policy text this is fine; it's the first thing to break if chunks grow. **Medium.**
5. **Seed corpus = 4 docs, 15-ish chunks** (`DevSeedController.java:33-48`). The demo answers are correct *because the corpus is tiny*, not because RAG is bulletproof. Do not extrapolate quality to a real knowledge base. **Contextual, not a bug.**

**Verdict:** this is a **genuine RAG system** — retrieval, grounding, and abstention are all wired and the faithfulness gate actually fires. The weaknesses are scaling/correctness edges, not architectural fraud.

---

## PART 4 — Ollama vs. DeepSeek API

Current stack: `qwen2.5:3b` (chat, 4 GB class) + `bge-m3` (1024-dim embed) via Ollama on `localhost:11434`.

| Dimension | Ollama (current) | DeepSeek API |
|---|---|---|
| Offline | Full offline — matches a demo that must "just run" on a plane/dorm wi-fi. | Needs internet every turn. |
| Privacy | Docs never leave the machine. | Text sent to a remote endpoint; depends on vendor policy. |
| HW demand | CPU-friendly 3B; the sandbox ran it. No GPU required. | Zero local HW demand. |
| Speed (this corpus) | Cold-start ~500 ms/token on CPU; fine for short answers. | Typically faster per-token over the wire. |
| Quality | Good instruction-following for a 3B; struggles on multi-fact extraction (`MentorshipTool`/`ProjectInviteTool` call it for classification, which is acceptable). | Stronger reasoning, better at multi-clause extraction — would shrink some retry paths. |
| Cost | $0 after HW is bought. | Per-token cost scales linearly with usage. |
| Maintenance | Pull/update models locally; version them explicitly. | Transparent vendor versioning; no local ops. |
| Deployment | Works on any box with Docker/Ollama. | Works anywhere with egress. |
| Scalability | CPU-bound; throughput is low. | High throughput; scales elastically. |

**The explicit answers:**
1. **Is the Ollama approach technically sound?** Yes. `bge-m3` is a strong embedder; `qwen2.5:3b` is competent for this scope; the wiring is correct. The one blemish is the missing embedding timeout (`EmbeddingService.java:30`, see Part 2 #1).
2. **Is switching to DeepSeek *necessary*?** No — there is no functional failure that a better model fixes. The real failures are in test logic and auth/RBAC, not model quality.
3. **Would DeepSeek *meaningfully* improve performance?** Marginally on reasoning-classification tasks and latency. It would *not* fix the recall/RBAC/guardrail gaps, which are engineering problems, not model problems.
4. **Is Ollama chosen because it's free/offline?** Mostly correctly — offline + privacy are the right call for a college demo. But the *motivation should be explicit*: this is a "run anywhere, no secrets in transit" choice, which fits the project's stated "local RAG" direction (`master-context.md`).
5. **HW limitations?** This project targets CPU-class machines (no GPU assumed). That constrains you to 3-7B-class models — DeepSeek's larger models wouldn't run locally anyway. So "Ollama with a small model" and "DeepSeek API" are the two realistic poles, and Ollama is the right pole *for this stage*.
6. **Recommendation for now:** **keep Ollama.** It is fit-for-purpose. The moment you need either (a) sub-300 ms responses or (b) better multi-fact extraction, evaluate the API — but as a *second* concern after the engineering gaps below.
7. **For a real deployment:** hybrid is actually the textbook move — small/cheap local model for retrieval-augmented, low-stakes turns; a stronger remote model for ambiguous/comparative questions behind an opt-in. campus-ai already has the *shape* for that (single `OllamaChatService` injected into `AskService`), so adding a routed client later is cheap.

**Verdict:** Ollama is the *correct* architectural choice for campus-ai's current stage. The decision is appropriate to the requirements (offline, private, low-spec HW, demo corpus), not merely the cheapest.

---

## PART 5 — Feature triage (Essential / Recommended / Not-Required)

**A. Essential (must have to be correct and reliable)**
- Fix the test failures blocking the build (#1 in Part 6) — you can't improve what doesn't compile/test.
- Add the embedding timeout and a small generation timeout at `OllamaChatService`/`EmbeddingService` — prevents indefinite hangs.
- Extract the system prompt to one file and enforce out-of-scope refusal in it (not just route-gating).

**B. Recommended (significant lift, not mandatory)**
- pgvector HNSW index + an `ANALYZE` step — only when the corpus grows past a few hundred chunks.
- Structured logging (`System.err` → `Logback`) on `ToolRegistry.dispatch` failures so swallowed exceptions surface.
- A `/ask` response with a confidence/score and a flag when the faithfulness gate *abstained vs. never answered* — improves debuggability.
- Context-window budgeting before injecting top-K (cap characters of retrieved text).

**C. Enterprise / future (do not add now)**
- MCP server wrappers, distributed tracing (OpenTelemetry), Prometheus metrics, model routing, semantic cache, OAuth/JWT auth, multi-model routing, cross-encoder rerankers — none of these solve a current campus-ai problem, and several would obscure the clean "one local model, typed tools, RAG" design.

The lecture mentions all of these; the project correctly implements none of the C items.

---

## PART 6 — Weaknesses, mistakes, and improvements

| # | What is wrong | Why it matters | Recommended fix | Priority |
|---|---|---|---|---|
| W1 | **Two broken tests in `ProjectInviteToolTest`** (now fixed): `extractParams_withMyInvites_*` asserted a service call that `extractParams` intentionally makes (it only classifies intent — see `extractParams_withMyInvites_setsActionOnly` at `ProjectInviteToolTest.java:73-80`, which correctly uses `verifyNoInteractions`); and `execute_withMyInvites_*` used `anyString()` to stub `getMyInvites(userId, null)`, which Mockito 5 does not match for `null`. | False red in CI; obscures the real `extractParams` contract. | Rewrote the two tests to assert `action=myInvites` + `verifyNoInteractions(projectInviteService)`, and stubbed with `nullable(String.class)`. These now pass (confirmed by a post-fix `mvn test` run — see Verification note). | **Critical** |
| W2 | **Real production bug in `ProjectInviteTool.extractParams` / `extractInviteeName`**. Three compounding defects conspired to break ~4 tests and the real "invite Sarah to hackathon" path: (1) `extractInviteeName` only matched a name *after* "to" — "invite Sarah to X" matched nothing; (2) the LLM-fallback branch fired only when *both* IDs were null (`if projectId == null && inviteeId == null`), so even when name extraction failed, "invite Sarah to hackathon" (no project id → projectId null) reached the LLM branch, but because `inviteeId` was already set-by-name-resolver the condition was false, so the LLM was never called and the test's `verify(chatService).chat(...)` failed; (3) `execute` called `dto.inviteeName()` on a null `InviteDto`, NPEing when `invite` could not be sent (`ProjectInviteTool.java:176`). | Correct intent never extracted; user-facing mis-behavior ("invite X to Y" silently mis-parsed); NPE in `execute`. | Fixed `extractInviteeName` with an "invite NAME to" pattern (case-preserving); changed the LLM fallback to fire when *either* ID is null; null-guarded `execute` to return a clarification instead of NPE-ing on a null `inviteeId`. Also null-guarded `extractParamsWithLlm` against a null chat response. | **High** |

> **Verification note (post-fix):** I ran `mvn -Dtest=ProjectInviteToolTest test` after the fix. Results: **Tests run: 13, Failures: 0, Errors: 0.** All 13 pass. (The two W1 test-side fixes pass trivially; the 4 W2 failures were the real production bug and are now green.)

> **Other pre-existing, untouched failures (full transparency):** the full suite also reports `MentorshipToolTest` (2 failures, `expected: <request> but was: <myRequests>` — a *sibling* trigger-precedence bug in `MentorshipTool.java:72-115` that mis-routes "request a mentor" queries; `MentorshipTool.java` was not modified by my edits) and `CampusAiApplicationTests` (1 error: `Unable to determine Dialect … set jakarta.persistence.jdbc.url` — a missing test-profile JDBC URL; production is unaffected since `application.properties:1` supplies Postgres). Independent of the invite-tool fix; noted so the audit does not claim a green build.

| W3 | `EmbeddingService` (`EmbeddingService.java:30`) uses `RestClient.create(baseUrl)` with **no read timeout**, while `OllamaChatService` sets `120_000ms` (`OllamaChatService.java:24`). | A hung/buggy embed model hangs a retrieval thread forever — under load this is a thread-starvation DoS. | Mirror the chat client's `SimpleClientHttpRequestFactory` + `setReadTimeout` on the embed client. | **Medium** |
| W4 | Tool exceptions are swallowed in `ToolRegistry.dispatch` (`ToolRegistry.java:63-66`) with only `System.err.println`. | A throwing tool silently becomes "no tool matched" and the user falls through to RAG with no signal. | Log via SLF4J (add `spring-boot-starter-logging`, already transitively present) and surface a `ToolResult.error` so the orchestrator can report it. | **High** |
| W5 | No pgvector HNSW/IVFFLAT index. `hibernate.ddl-auto=update` only `ALTER`s the table (`application.properties:7`); no `schema.sql` / migration exists. | O(n) cosine scan per query. Invisible at 15 chunks, painful at 1k+. | Add a native `@Index`/`schema.sql` `CREATE INDEX ... USING hnsw` on `embedding` once corpus > a few hundred chunks. | **Medium** (currently a latent risk) |
| W6 | `ToolRegistry.findMatchingTools` matches on substrings of trigger keywords, not intents. Trigger `"invite"` overlaps `"accept invite"`/`"reject invite"` (`AgentOrchestrator` path), and `"my invite"` vs `"invitation"` can both match `ProjectInviteTool`. | Ambiguity is resolved only by try-order + success, so a well-meaning misparse surfaces as a wrong tool result rather than a clarification. | Acceptable now (fail-fast on success + RAG fallback). Revisit only if user complaints about wrong routes rise. | **Low** |
| W7 | Identity is a client-supplied `X-Demo-User` header with no cryptographic trust (`AgentController.java:22`). | Anyone can impersonate any user; admin list (`DocumentService.java:22`) is trivially spoofable. | **Not a fix to ship now** — this is a demo auth model. Document it loudly as a known limitation; don't pretend it's secure. | **Low** (known by design) |
| W8 | No PII redaction in tool responses that surface profile data (`LookupService.java:201-215`, `ProfileController`). | CGPA/availability/etc. are platform data here, not regulated PII, but the *pattern* (echo user profile into a free-text LLM answer) is the exact shape that leaks PII at scale. | Add a small allowlist filter if the bot ever returns user-attribute data to third parties. Not needed for a same-user demo. | **Low** (informational) |

Two architectural *mistakes* worth flagging honestly:
- There is **no MCP** integration and that is correct (`pom.xml` has no MCP deps; `Tool` is a tiny custom interface). The lecture lists MCP as one example of a typed-connector layer — campus-ai reimplements that idea with `Tool`/`ToolRegistry`, which is the *right* call for a single-codebase demo. Do not "add MCP" just to match the lecture's example.
- The **retry-on-abstain** path in `AskService.answerWithHits` (`AskService.java:186-203`, second `chatService.chat`) doubles generation calls for borderline answers — fine for a demo, but it will double token cost and latency if the corpus grows. Not a bug, a scaling watch-item.

---

## PART 7 — Final verdict and roadmap

**1. Overall assessment.** campus-ai is a *coherent, correctly-wired* AI assistant for its scope: one local model (Ollama), genuine RAG on pgvector, a typed tool layer that never lets the LLM touch the DB, a faithfulness gate, and an eval harness. The lecture's "enterprise" toppings are deliberately *not* present, which is the right call here — the architecture matches the project's stated intent ("local RAG" per `master-context.md`).

**2. How closely it follows the lecture.** It implements the *essential four* layers (integration, prompting, RAG, tools) faithfully; partially implements Auth/RBAC and Guardrails (functionally enough to demo); fully skips Cache+Routing; and implements Observability as *evals only* (the half that matters at this scale). That is a deliberate, defensible deviation from the lecture's "build everything" narrative.

**3. Are the technical choices justified?** Largely yes — and the Ollama choice specifically is justified by offline/privacy/HW constraints, not just cost. The unjustified bit is the **test failures** (W1/W2): a broken test is a broken gate, and right now the build is red on two tests that misstate the tool's contract.

**4. Ollama vs. DeepSeek?** Keep Ollama for now. The blockers are engineering, not model quality. Re-evaluate the API only when latency or multi-fact extraction becomes a user-visible problem.

**5. Most important missing components:** (a) the embedding timeout, (b) correct test stubbing, (c) surface-swallowed tool errors, in that order.

**6. Features to deliberately avoid now:** MCP servers, OAuth/JWT, distributed tracing, semantic caching, model routing, cross-encoder rerankers, and moving to a remote LLM API. None of these fix a currently-broken behavior.

### Prioritized roadmap

#### Phase 1 — Fix existing implementation (must ship first)
| Priority | Feature / Improvement | Why It Is Needed | Expected Benefit |
|---|---|---|---|
| 1 | Fix `ProjectInviteToolTest` action-test (assert `myInvites` + `verifyNoInteractions`) | Build is red; two failing tests block any change | Green, trustworthy gate |
| 2 | Fix `ProjectInviteToolTest` execute-test stubbing (`nullable(String.class)`) | `null` status not matched by `anyString()`; false-empty result | Test reflects real `getMyInvites(userId, null)` path |
| 3 | Add read timeout to `EmbeddingService` RestClient | Hang = thread death under load | No unbounded blocking on embed |
| 4 | Log swallowed tool errors in `ToolRegistry.dispatch` (SLF4J) | Silent failures mask real tool bugs | Debuggable tool layer |

#### Phase 2 — Improve RAG quality and reliability
| Priority | Feature / Improvement | Why It Is Needed | Expected Benefit |
|---|---|---|---|
| 5 | Add pgvector HNSW index once corpus > ~500 chunks | O(n) cosine scan | Sub-linear retrieval |
| 6 | Add context-window budgeting (cap char length of injected chunks) | Faithfulness gate injects all top-K untrimmed | Stable on larger chunks |
| 7 | Promote system prompt to `system-prompt.md` + out-of-scope refusal | Persona is inline and scope refusal is route-gated | Clearer, versioned persona |

#### Phase 3 — Essential security and agent capabilities
| Priority | Feature / Improvement | Why It Is Needed | Expected Benefit |
|---|---|---|---|
| 8 | Audit tool input sanitizers are consistent (Profile/Document already sanitize; ensure parity) | Some tool paths call the LLM with raw user text | Reduce injection surface |
| 9 | Decide auth model explicitly (demo header vs. JWT) and document its limits | `X-Demo-User` is spoofable; admin list is a comma string | No false sense of security |

#### Phase 4 — Prepare for deployment and future scalability
| Priority | Feature / Improvement | Why It Is Needed | Expected Benefit |
|---|---|---|---|
| 10 | Add `spring-boot-starter-actuator` + basic `/health` | No observability surface for deploy | Deployable, monitorable |
| 11 | Make chat model configurable per-route (small local vs. stronger remote) | Today's single model is correct; prep for growth | Clean handoff to "Phase 2" of the lecture's story |

**Net:** campus-ai's architecture is sound for a demo/local-first college AI. The lecture's later layers are correctly deferred, not missing by accident. Ship the Phase-1 fixes, then grow the layers in lecture-order — only when a user need justifies them."


