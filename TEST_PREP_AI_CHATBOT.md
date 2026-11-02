# AI Chatbot Comprehensive Test Preparation Guide

## Overview

This document prepares for rigorous testing of the Campus AI chatbot (the `/ask` and `/api/agent` endpoints). The testing covers:
- **RAG (Retrieval-Augmented Generation)** — document-based Q&A via Ollama
- **Structured intent classification** — tool-based DB queries (projects, events, teammates, etc.)
- **Conversation memory** — follow-up resolution, context references
- **Multi-fact questions** — query decomposition
- **Abstention / out-of-scope gating** — questions the system should refuse

## Prerequisites

### Environment Setup (on Windows)
1. **JDK 22** — `set JAVA_HOME=C:\Program Files\Java\jdk-22`
2. **PostgreSQL** at `localhost:5432` (db `postgres`, user `postgres`, password `1234`)
3. **Ollama** at `localhost:11434` with models:
   - `bge-m3` (1027M param embeddings)
   - `qwen2.5:3b` (3B param generation)
4. **Backend running**: `cd C:\PROJECTS\campus-ai && mvnw.cmd spring-boot:run`
5. **Verify health**: `GET http://localhost:8080/documents` → 200

### Test Data Seeding
Run these endpoints in order before testing:

```
POST /dev/seed          → seeds 4 documents with 11 chunks total
POST /dev/seed-skills   → seeds skill glossary, aliases, co-occurrences
POST /dev/seed          → seeds demo users (students, alumni, recruiters)
POST /dev/seed-recruiter → seeds recruiter accounts
```

All seed endpoints are idempotent (skip existing).

## Endpoints Under Test

### Primary Chat Endpoints
| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/ask?q=<question>` | GET | Optional (X-Demo-User) | RAG chatbot — returns `AskResponse(answered, answer, sources)` |
| `/api/agent` | POST | Optional (X-Demo-User) | Agent orchestrator — returns `AgentResponse(type, text, data, suggestions)` |

### Chat History Endpoints
| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/ask/history?limit=N` | GET | Required | Get last N turns |
| `/ask/history/count` | GET | Required | Count of turns |
| `/ask/history` | DELETE | Required | Clear history |

### Eval Endpoints (admin-only)
| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/eval/rag/cases` | GET | Admin (X-Demo-User: 1) | View test cases |
| `/eval/rag/run?retrievalOnly=bool` | POST | Admin (X-Demo-User: 1) | Run full eval |

## Query Catalog

The query catalog is organized by category. Each query has:
- **Query** — the exact question to send
- **Expected behavior** — what the system should do
- **Expected answer keywords** — for correctness checking
- **Expected citation** — document that should be cited

### Category A: Single-Fact RAG (Direct Questions)

| ID | Query | Expected Document | Answer Keywords | Numbers |
|----|-------|-------------------|-----------------|---------|
| A1 | `How many students can be in a hackathon team?` | SAMPLE - Hackathon Rules | `team`, `students` | `2`, `4` |
| A2 | `What happens to plagiarised hackathon projects?` | SAMPLE - Hackathon Rules | `disqualification`, `plagiarised` | — |
| A3 | `How much attendance do I need for exams?` | SAMPLE - Attendance Policy | `attendance`, `75` | `75` |
| A4 | `What documents do I need for medical leave?` | SAMPLE - Attendance Policy | `medical`, `certificate` | — |
| A5 | `Who can attend campus placement drives?` | SAMPLE - Placement Guidelines | `backlogs`, `eligible` | — |
| A6 | `When does tech fest registration close?` | SAMPLE - Technical Fest Information | `registration`, `week` | — |
| A7 | `What are the judging criteria for hackathons?` | SAMPLE - Hackathon Rules | `innovation`, `technical` | — |

### Category B: Multi-Fact RAG (Query Decomposition)

| ID | Query | Expected Documents | Answer Keywords |
|----|-------|-------------------|-----------------|
| B1 | `How many students can be in a hackathon team and how much attendance is needed for exams?` | SAMPLE - Hackathon Rules, SAMPLE - Attendance Policy | `team`, `attendance`, `2`, `4`, `75` |
| B2 | `What are the plagiarism consequences and which document governs attendance policy?` | SAMPLE - Hackathon Rules, SAMPLE - Attendance Policy | `disqualification`, `attendance` |
| B3 | `Who can attend placement drives and when does technical fest registration close?` | SAMPLE - Placement Guidelines, SAMPLE - Technical Fest Information | `backlogs`, `registration`, `week` |

### Category C: Unanswerable / Out-of-Scope (Abstention)

| ID | Query | Expected Behavior |
|----|-------|-------------------|
| C1 | `What is the hostel curfew timing on weekends?` | Gated — no answer in documents |
| C2 | `How do I apply for a semester exchange program?` | Gated — no answer in documents |
| C3 | `Tell me a joke about databases.` | Gated — out of scope |
| C4 | `What's the weather like today?` | Gated — out of scope, no RAG |

### Category D: Structured Tool Queries (DB-backed)

These test the intent classification → ToolRegistry dispatch path:

| ID | Query | Tool Triggered | Expected Behavior |
|----|-------|----------------|-------------------|
| D1 | `show my projects` | ProjectTool | Lists user's projects or "no projects" |
| D2 | `find teammates` | ProjectTeammateTool | Shows skill gaps + candidates |
| D3 | `upcoming events` | EventTool | Lists approved events |
| D4 | `my bookmarks` | BookmarkTool | Lists saved bookmarks |
| D5 | `notifications` | NotificationTool | Shows unread count + recent |
| D6 | `who knows Java` | SearchStudentsTool | Lists students with Java skill |
| D7 | `glossary for js` | SkillGraphTool | Shows canonical: JavaScript |
| D8 | `skill graph dashboard` | SkillGraphTool | Shows top skills on campus |

### Category E: Follow-up / Conversation Memory

These require conversation context (prior turns):

| ID | Prior Question | Follow-up Query | Expected Behavior |
|----|----------------|-----------------|-------------------|
| E1 | `How many students can be in a hackathon team?` | `what?` | Re-answers with context reference |
| E2 | `How much attendance is needed for exams?` | `and for hackathons?` | Expanded query, answers both |
| E3 | `How many students can be in a hackathon team?` | `details` | Delegates to RAG on last question |
| E4 | `What documents do I need for medical leave?` | `what?` | Re-answers with context reference |

### Category F: Edge Cases & Negatives

| ID | Query | Expected Behavior |
|----|-------|-------------------|
| F1 | `hello` | Clarification or greeting response |
| F2 | `` (empty) | Clarification: "Could you rephrase?" |
| F3 | `tell me about campus` | Should match documents or give info |
| F4 | `create project called MyProject for AI needing Java, React` | Tool route: project creation hint |
| F5 | `complete my profile` | Profile completeness check |

## Test Script

The test runner script is at `test_runner.sh` (Linux/macOS) or `test_runner.ps1` (Windows).

### Running the Tests

**Option 1: Automated eval (admin-only, needs Ollama)**
```
POST /eval/rag/run?retrievalOnly=true
POST /eval/rag/run?retrievalOnly=false
```

**Option 2: Manual query catalog**
Use the Thunder Client or curl to send each query from the catalog above. Record results in `test_results.csv`.

**Option 3: Agent endpoint**
```
POST /api/agent
Header: X-Demo-User: 1
Body: {"question": "How many students can be in a hackathon team?"}
```

## Evaluation Criteria

| Metric | Definition | Target |
|--------|------------|--------|
| **Recall@K** | Fraction of expected docs retrieved in top-K | ≥ 0.667 |
| **Precision@K** | Fraction of retrieved docs that are relevant | ≥ 0.722 |
| **Correctness** | Answer contains all expected keywords/numbers | ≥ 80% of answerable |
| **Faithfulness** | Answer can be verified against cited sources (no hallucination) | 100% of answerable |
| **Citation rate** | Answer includes `[n]` citation markers | ≥ 66% of answerable |
| **Abstention rate** | Unanswerable questions return `answered: false` | 100% of C-category |
| **Tool dispatch** | Structured queries routed to correct Tool | 100% of D-category |
| **Follow-up resolution** | E-category queries use conversation memory | 100% of E-category |

## Data Flow for Testing

```
User Query
  → /ask or /api/agent
    → AskService.ask() (RAG path, if no tool matches)
      → SearchService.search()
        → pgvector similarity search on chunks
        → RerankService.rerank() (IDF-weighted)
      → AskService.answerWithHits()
        → Ollama qwen2.5:3b generation
        → Faithfulness verification
    → ToolRegistry.dispatch() (structured path, if tool matches)
      → Tool.execute()
        → Backend service call
    → ChatHistoryService.save()
      → Persist turn for conversation memory
```

## Post-Test Analysis

After running all tests, analyze:
1. Which queries failed correctness checks? (missing keywords/numbers)
2. Which queries had no citations? (potential hallucination risk)
3. Did any unanswerable questions get `answered: true`? (abstention failure)
4. Did query decomposition help for multi-fact questions? (compare basic vs improved arms)
5. Did follow-up queries resolve context correctly? (contextReferences present?)
6. Which tool routes failed to dispatch? (intent classification gaps)

## Files to Review After Testing
- `test_results.csv` — record of all query outcomes
- `/eval/rag/run` output — full eval summary with per-arm metrics
- `/ask/history?limit=5` — verify conversation memory works
