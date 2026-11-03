# evals

Output from `Run_Eval_Diff.ps1`, plus the committed baseline.

- `baseline.json` — the run to compare against. Committed, so everyone measures
  against the same numbers. Replaced only deliberately, with `-SetBaseline`.
- `YYYY-MM-DD-HHmmss-<mode>.json` — one raw result per run, where `<mode>` is
  `full` (generation, needs Ollama) or `retrieval` (fast, no LLM). These are
  gitignored: they are machine- and moment-specific output.

## Reading a result

The file is the raw `EvalSummary`, so it can be inspected directly:

```bash
python -m json.tool evals/2026-11-03-141502-full.json
```

The `cases` array holds one entry per eval case with both arms' results, which
is where to look when the summary says a case regressed — it shows what each arm
actually answered.

## Comparing by hand

To re-diff a saved run without calling the server:

```powershell
.\Run_Eval_Diff.ps1 -FromFile .\evals\2026-11-03-141502-full.json
```
