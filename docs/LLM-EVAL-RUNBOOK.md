# LLM eval runbook

How to measure a change to retrieval or generation, instead of guessing.

Everything here runs against **your** machine: the sandbox that wrote these
changes has no Ollama and no Postgres, so the numbers have to come from a real
run. Nothing in this document requires a code change to execute.

---

## 0. Before you start

Start Postgres and Ollama, run the app, then seed the corpus:

```bash
# Ollama must have the models pulled
ollama pull qwen2.5:3b
ollama pull bge-m3

# run (from the repo root)
./mvnw spring-boot:run
```

```bash
# in another shell — seed the sample documents the eval cases reference.
# Without this the corpus is empty and every case scores 0 for the wrong reason.
curl -s -X POST http://localhost:8080/dev/seed | python -m json.tool
```

The seed is idempotent — it skips a document whose title already exists, so
re-running it after a schema reset is safe. It reports `skipped` for those.

### Identity header

The eval endpoints are **admin-gated by id**, not by role: `requireAdmin`
looks the id up in the demo-user table and then checks it against
`campus.admin-ids` (configured as `campus.admin-ids=1`). So the id in the
header must be **1**, and a user with id 1 must exist in the database.

Identity is sent as `X-Demo-User`. A bare number is accepted and defaults to
the student role, and `role:id` sets the role explicitly — both resolve to
id 1 here, so either works:

```
X-Demo-User: 1            # bare id — what requests.http uses
X-Demo-User: student:1    # explicit role, same id
```

Using `student:2` will return **403**, not because students are barred but
because id 2 is not in `campus.admin-ids`.

---

## 1. The two modes

```bash
# Retrieval only — fast, no LLM calls. Use while tuning retrieval.
curl -s -X POST "http://localhost:8080/eval/rag/run?retrievalOnly=true" \
  -H "X-Demo-User: student:1" | python -m json.tool

# Full generation — calls Ollama twice per case (once per arm). Slow but real.
curl -s -X POST "http://localhost:8080/eval/rag/run?retrievalOnly=false" \
  -H "X-Demo-User: student:1" | python -m json.tool
```

The full run is the one that answers "did the answer get better". Retrieval-only
answers "did the right passage get found", which is necessary but not sufficient.

---

## 2. Reading the summary

```json
{
  "recallAtKBasic": 0.667,
  "recallAtKImproved": 0.667,
  "correctnessImproved": 0.7,
  "faithfulnessImproved": 0.9,
  "abstentionRate": 1.0,
  "improvedWins": 1, "ties": 8, "basicWins": 1,
  "verdict": { "winner": "tie", "reason": "..." },
  "topK": 3, "rerankEnabled": true, "chatModel": "qwen2.5:3b"
}
```

What each number means, and which one to trust:

- **recallAtK** — did the expected document appear in the top K. The blunt
  instrument: it can be high while answers are still wrong.
- **correctness** — the answer contained the expected keywords and numbers.
  **This is the one that matters most**, because it is end-to-end.
- **faithfulness** — the answer passed the groundedness gate. A high
  faithfulness with low correctness means the system is confidently not
  answering the question.
- **abstentionRate** — of the unanswerable questions, how many correctly
  declined. Should stay at 1.0. If it drops, the system is inventing answers.
- **verdict** — the harness's own call on whether rerank beats baseline. It
  refuses to declare a winner on a tie, which is the common case. Treat "tie"
  as "no evidence to switch".

`topK`, `rerankEnabled` and `chatModel` are echoed in the summary so a pasted
result is self-describing — you can tell what produced it without remembering.

---

## 3. Sweeping K

`rag.top-k=3` is tight for multi-fact questions. To test whether 5 is better,
**no restart needed**:

```bash
for k in 3 5 8; do
  echo "=== topK=$k ==="
  curl -s -X POST "http://localhost:8080/eval/rag/run?retrievalOnly=true&topK=$k" \
    -H "X-Demo-User: student:1" \
  | python -c "import sys,json; d=json.load(sys.stdin); print('recall basic/improved:', d['recallAtKBasic'], d['recallAtKImproved']); print('verdict:', d['verdict']['reason'])"
done
```

Then repeat with `retrievalOnly=false` for the K you are considering, and compare
`correctnessImproved` and `abstentionRate`. Raise `rag.top-k` in
`application.properties` only if full-generation correctness improves **and**
abstention does not fall.

---

## 4. A/B a different chat model

`qwen2.5:3b` is small. A larger local model is the single biggest quality lever,
and it is a one-line change:

```bash
# 1. baseline
curl -s -X POST "http://localhost:8080/eval/rag/run?retrievalOnly=false" \
  -H "X-Demo-User: student:1" > /tmp/eval-3b.json

# 2. edit application.properties:  ollama.chat-model=qwen2.5:7b-instruct
#    (ollama pull qwen2.5:7b-instruct first), restart the app

# 3. re-run
curl -s -X POST "http://localhost:8080/eval/rag/run?retrievalOnly=false" \
  -H "X-Demo-User: student:1" > /tmp/eval-7b.json

# 4. compare
python - <<'PY'
import json
a=json.load(open('/tmp/eval-3b.json')); b=json.load(open('/tmp/eval-7b.json'))
for k in ['correctnessImproved','faithfulnessImproved','abstentionRate',
          'avgLatencyImprovedMs','perFactCitationPrecisionImproved']:
    print(f"{k:34} {a[k]:>8} -> {b[k]:>8}")
print("model:", a['chatModel'], "->", b['chatModel'])
PY
```

The tradeoff is latency: `avgLatencyImprovedMs` will rise substantially on CPU.
Judge whether the correctness gain is worth it.

---

## 5. Tuning retrieval weights

Both fusion weights and all five rerank weights are now in
`application.properties` (`rag.fusion.*`, `rag.rerank.w-*`). Change **one at a
time** and re-run the eval each time — the verdict is win/tie/loss, so a
combined change cannot be attributed.

The weights sum to 1.0 by convention but nothing enforces it; if you change one,
consider whether another should absorb the difference.

---

## 6. New cases added for grounding

The case list now includes three that specifically exercise the groundedness
fixes:

- a two-number question spanning two documents ("minimum attendance percentage
  *and* how many students per hackathon team") — fails if either passage is
  dropped by the context budget;
- a near-miss number question ("exactly what attendance percentage") — fails if
  the groundedness check accepts a substring (a bare `5` from `75`);
- an in-scope-vocabulary-but-absent-fact question ("semester exchange programme
  fee") — measures abstention **precision**, not recall.

If `abstentionRate` drops on the last one, the system is answering from
adjacency rather than evidence.

---

## 7. What was already verified offline

These were checked in the sandbox and need no re-run:

- Groundedness matching — 12 unit tests pinning whole-token + stemmed
  matching, covering both failure directions: too loose (a bare `5` accepted
  because the context contains `75`; `art` matched inside `start`) and too
  strict (a truthful `students` rejected against a source saying `student`).
- Context-budget fitting and token estimation — 14 unit tests.
- The full offline suite: 144 passed, 0 failed (the Spring context test is
  excluded because it needs Postgres).

What is **not** verified, and is what this runbook is for: whether any of it
improves answer quality on the real corpus.
