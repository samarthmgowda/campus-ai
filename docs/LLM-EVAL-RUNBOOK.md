# LLM eval runbook

How to measure a change to retrieval or generation, instead of guessing.

Everything here runs against **your** machine: the sandbox that wrote these
changes has no Ollama and no Postgres, so the numbers have to come from a real
run. Nothing in this document requires a code change to execute.

The quickest path is the script — it does all of the below, saves the result,
and diffs it against the baseline:

```powershell
powershell -ExecutionPolicy Bypass -File "C:\PROJECTS\campus-ai\Run_Eval_Diff.ps1"
```

The rest of this section is what that script does, for when you want to run it
by hand or understand a step it took.

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

## 7. Running it on a schedule

`Run_Eval_Diff.ps1` exits non-zero when it finds a regression, so it is safe to
run unattended. Its exit codes:

| Code | Meaning |
|---|---|
| 0 | Ran, no regressions (or first run, which becomes the baseline) |
| 1 | Ran, and a metric regressed beyond tolerance |
| 2 | No verdict available — app down, header rejected, corrupt baseline, malformed summary, metrics that could not be compared, or a config mismatch with the baseline |

Exit 2 is deliberately distinct from 1. Three outcomes are possible and
collapsing them would make each indistinguishable from the others: the run
measured a regression (1), the run measured nothing wrong (0), or the run could
not measure at all (2). Only 1 means "the code got worse".

Exit 2 covers more than a crash, and the message says which case it is:

- **Config differs from the baseline** (`chatModel`, `topK`, `rerankEnabled`, or
  run mode). A regression computed across a model swap or a K change is a
  difference in config, not in code, so no verdict is issued. If the change is
  intended, record it with `-SetBaseline`.
- **Corrupt or empty baseline.** The script will not overwrite it — a corrupt
  baseline is usually a truncated write, and silently replacing it would discard
  the reference point you are measuring against. Fix it or pass `-SetBaseline`.
- **Malformed or empty run summary**, or **metrics that were not numeric**.
  Missing numbers are never coerced to 0, which would fabricate a regression out
  of a serialization glitch.
- **A retrieval-only run against a baseline that does not record its mode.**
  The script detects the run mode from the `retrievalOnly` field, so a baseline
  written before that field existed cannot be classified. That is the one case
  where the phantom-regression guard has nothing to work with: the run's zeros
  for correctness and faithfulness are structural, and against real baseline
  numbers they read as three or four catastrophic regressions. So a
  retrieval-only run is refused outright when the baseline is unclassified,
  rather than diffed on a guess.

Mixing a retrieval-only run with a full-generation baseline is the easiest way to
hit this by accident: the retrieval run reports `0.0` for correctness,
faithfulness, abstention and latency *by construction*, so diffing it against a
full baseline would otherwise report four phantom regressions.

**Important — where the task must run.** A Cowork scheduled task runs in the
Linux sandbox, which has **no network route to your `localhost:8080`** (verified:
no `host.docker.internal`, and the sandbox gateway refuses port 8080). A
scheduled task cannot curl the eval endpoint, and one that tries will fail
silently. So schedule on **Windows**, where the app actually runs:

```powershell
# Register a nightly run at 02:00, logging to evals\scheduled.log
$inner   = '& ''C:\PROJECTS\campus-ai\Run_Eval_Diff.ps1'' *>> ''C:\PROJECTS\campus-ai\evals\scheduled.log''; exit $LASTEXITCODE'
$action  = New-ScheduledTaskAction -Execute "powershell.exe" `
           -Argument ('-NoProfile -ExecutionPolicy Bypass -Command "{0}"' -f $inner)
$trigger = New-ScheduledTaskTrigger -Daily -At 2am
Register-ScheduledTask -TaskName "CampusAI-Eval" -Action $action -Trigger $trigger
```

Task Scheduler launches the process directly — there is no shell — and that
changes two things about how the command has to be written:

- **The redirection must be parsed by PowerShell, not passed to `-File`.** With
  no shell in the chain, `-File script.ps1 *>> log` delivers `*>>` and the log
  path as two ordinary arguments; `Run_Eval_Diff.ps1` ignores them and no log is
  ever created. Wrapping the call in `-Command "& 'script' *>> 'log'"` gives
  PowerShell something to parse the redirection.
- **Quote the inner paths with single quotes.** The argument is already wrapped
  in double quotes, so double quotes inside it nest badly and the command fails
  to parse. Single quotes avoid that entirely. (The doubled `''` in the
  PowerShell string above is just how you write one single quote inside a
  single-quoted PowerShell string.)

The exit code reaches Task Scheduler's Last Run Result, so Task History shows a
non-zero result when the eval finds a regression. Verify the whole thing once by
hand before trusting the schedule:

```powershell
schtasks /Query /TN "CampusAI-Eval" /V /FO LIST
```

The run needs the backend already up when it fires, so either start the app as a
service or accept that an app-down run exits 2 with the reason logged.

Useful flags:

- `-RetrievalOnly` — fast, no Ollama; checks that the right passage is found.
- `-TopK 5` — sweep K for this run only.
- `-SetBaseline` — accept this run as the new baseline.
- `-FromFile <path>` — re-diff a saved run without calling the server.
- `-Tolerance 0.05` — loosen the regression threshold (default 0.02).

Caveat worth knowing before you trust a scheduled run: the same code can score
differently run to run, because generation samples. A single-run regression of
one or two cases is closer to noise than signal — re-run before believing it,
and prefer `-Tolerance` slightly above the observed jitter to the default.

---

## 8. The baseline that is already in the repo

`evals/baseline.json` is committed and already holds the last real numbers
recorded by hand (2026-10-12). They are the values quoted in section 2, so the
to-do list below is small. But read this before you run the script, or the first
run will surprise you.

**The first run exits 2.** The old numbers exist as prose in this document and
in the eval notes, not as the JSON the harness emits, so `baseline.json` is a
hand-transcribed file. It carries the fields a pasted summary carries and none
of the ones the script added later, which trips two separate guards.

The first is the config check. The script compares config before it compares
metrics, and run mode is part of the config: it detects "full generation" by
reading `retrievalOnly` from the summary, and this file has no such field. A
retrieval-only run against an unclassified baseline is refused outright rather
than diffed on a guess (see the last bullet in section 7). A full-generation run
gets past that check.

The second is metrics the baseline does not record. `perFactCitationPrecisionImproved`
and `avgLatencyImprovedMs` postdate the October run, so they are present in the
run and absent from the baseline. There is no way to know from one side alone
whether such a metric improved or regressed — which is exactly the condition
under which it must not be silently skipped — so the script reports them and
exits 2 with the comparison marked incomplete.

Neither exit 2 is a failure. Both are the script declining to issue a verdict it
cannot support. Your first run is therefore two runs:

```powershell
# 1. establish a real baseline - the file above is prose-derived, this is not
.\Run_Eval_Diff.ps1 -SetBaseline

# 2. from here on, every run compares against machine-written numbers
.\Run_Eval_Diff.ps1
```

If step 1's run disagrees with the 2026-10-12 numbers, believe step 1. A real
run on your machine outranks a figure transcribed from a chat log — the corpus
has been reseeded and the case list has grown since October, so the deltas are
most likely real corpus changes rather than a quality change.

The two missing metrics, and what that costs:

- `perFactCitationPrecisionImproved` — added with the multi-hop work. Its first
  real baseline is your own run.
- `avgLatencyImprovedMs` — recorded, but machine-specific and not worth
  comparing across machines at all.

Two more were added on 2026-11-03, so the hand-written baseline is missing them
as well and the diff will list them as "not in the baseline":

- `avgLatencyAnsweredImprovedMs` — latency over answered cases only. The
  all-case `avgLatencyImprovedMs` includes the unanswerable cases, which abstain
  at the similarity gate in ~3ms without ever calling the model; those pull both
  arms' means toward zero and compress the difference between them, so the
  all-case figure understates how far apart the arms really are.
- `retriesImproved` — how many cases needed a second generation because the first
  abstained while the retrieved context still held the question's own terms. That
  retry is asymmetric by arm, so it is the main reason a latency delta can appear
  without the arms differing per call. It is not good or bad on its own.

### Reading latency honestly

Until 2026-11-03 the harness ran basic first and improved second on every case,
and did not warm the model. An arm therefore looked seconds faster merely for
going second — on cases where both arms retrieved byte-identical hits. Now a
warmup generation runs before the timed loop (its time discarded) and the arm
order alternates by case index, so the first-slot cost lands on both arms. If you
compare a run from before that change against one after, the latency columns are
not comparable at all.

Until `baseline.json` has been written by a real run, no comparison against it
can be complete. After `-SetBaseline`, every metric in the list is present on
both sides and the normal exit 0/1 behaviour applies.

Once `baseline.json` has been written by a real run, none of this applies: the
script overwrites it only with `-SetBaseline`, and it never overwrites a corrupt
one at all.

---

## 9. Checking the checker

`Run_Eval_Diff.ps1` is what decides whether a change made the product worse, so
it is worth being able to test on its own:

```powershell
.\Verify_Eval_Diff.ps1
```

No server, no Ollama, no database — it drives the script through 24 scenarios
built from JSON fixtures and checks the exit code and the message each one
produces. Run it after any change to the diff logic.

What it pins, and why each one is a rule rather than a preference:

| Scenario | Expected | Why |
|---|---|---|
| No change; within-tolerance jitter, up and down | exit 0 | Generation samples; small movement is noise, not a regression |
| Correctness collapse | exit 1 | The ordinary regression case |
| Latency triples | exit 1 | Only metric where lower is better, and compared as a ratio |
| Chat model swapped | exit 2 | A difference in config, not in code |
| Retrieval-only vs classified baseline | exit 2 | Zeros are structural, would read as three regressions |
| Retrieval-only vs baseline with no mode recorded | exit 2 | The guard cannot classify it, so it must not guess |
| Metric present in neither run | reported, exit 0 | A renamed field is a shape change, not a quality change |
| Metric present in only one run | exit 2 | Cannot be compared, and must not vanish silently |
| `null`, `""`, `"n/a"` as a metric value | exit 2 | Each coerces to a number that invents or hides a regression |
| Corrupt baseline / malformed run | exit 2 | Nothing to compare; the corrupt baseline is left alone |

The last five are the ones worth re-reading. They are all cases where the
tempting behaviour is to produce a verdict anyway, and every one of them would
either fabricate a regression or hide one.

## 10. What was already verified offline

These were checked in the sandbox and need no re-run:

- Groundedness matching — 12 unit tests pinning whole-token + stemmed
  matching, covering both failure directions: too loose (a bare `5` accepted
  because the context contains `75`; `art` matched inside `start`) and too
  strict (a truthful `students` rejected against a source saying `student`).
- Context-budget fitting and token estimation — 14 unit tests.
- The diff script's guards — 24 scenarios in `Verify_Eval_Diff.ps1`. They were
  themselves checked by mutation: nine deliberate breakages of the script (the
  unclassified-baseline guard, the one-sided-metric guard, the coercion guards,
  the tolerance comparison, the latency direction, the config check, and three
  baseline-protection branches) and all nine are detected.
- The full offline suite: 145 tests, 0 failures (the Spring context test is
  excluded because it needs Postgres).

What is **not** verified, and is what this runbook is for: whether any of it
improves answer quality on the real corpus.
