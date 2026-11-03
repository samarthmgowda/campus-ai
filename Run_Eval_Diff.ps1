# Run_Eval_Diff.ps1
# Run the RAG eval, save the result, and diff it against a stored baseline.
#
# Run from PowerShell:
#   powershell -ExecutionPolicy Bypass -File "C:\PROJECTS\campus-ai\Run_Eval_Diff.ps1"
#
# Prereqs: backend at localhost:8080, Ollama up, Postgres up. Admin = X-Demo-User 1.
#
# What it does, in order:
#   1. Preflight: is the app up, is the header accepted, is Ollama up.
#   2. Seed the corpus (idempotent) so the eval is measuring the model and not
#      an empty database.
#   3. Run the eval, save the raw JSON under evals\ with a timestamp.
#   4. Diff the summary against the baseline and flag regressions.
#   5. Exit 0 if clean, 1 if a regression was found (so a scheduled task can
#      signal failure), 2 if the run could not be performed at all.
#
# First run: there is no baseline, so it saves the run AS the baseline and says
# so. Run it once to establish the baseline, then use -SetBaseline whenever you
# deliberately accept a new set of numbers.
#
# ASCII only, and PowerShell 5.1 compatible: no ternary operator, no null
# coalescing, no Get-Content -AsByteStream.

param(
    [string]$Base = "http://localhost:8080",
    [string]$AdminId = "1",
    # Full generation is the meaningful measurement. -RetrievalOnly is fast and
    # needs no Ollama, but it only tells you whether the right passage was found.
    [switch]$RetrievalOnly,
    # Override rag.top-k for this run. Leave empty to use the configured value.
    [int]$TopK = 0,
    # Compare against a specific file instead of evals\baseline.json.
    [string]$Baseline = "",
    # Save this run as the new baseline (use when you accept the new numbers).
    [switch]$SetBaseline,
    # Exercise the diff logic against files on disk without calling the server.
    # For verifying the script itself, and for re-diffing a saved run.
    [string]$FromFile = "",
    # Skip the seeding step. Only correct if you know the corpus is already there.
    [switch]$SkipSeed,
    # Regression tolerance, in rate points (0.02 = two percentage points). A drop
    # smaller than this is treated as noise rather than a regression.
    [double]$Tolerance = 0.02,
    # Treat a latency increase beyond this fraction as a regression.
    [double]$LatencyTolerance = 0.25
)

$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$evalDir = Join-Path $repoRoot "evals"
if (-not (Test-Path $evalDir)) { New-Item -ItemType Directory -Path $evalDir | Out-Null }
if ([string]::IsNullOrWhiteSpace($Baseline)) { $Baseline = Join-Path $evalDir "baseline.json" }

$script:problems = @()

function Info($msg) { Write-Host "  $msg" -ForegroundColor Gray }
function Head($msg) { Write-Host $msg -ForegroundColor Cyan }
function Pass($msg) { Write-Host "  OK    $msg" -ForegroundColor Green }
function Fail($msg) { Write-Host "  FAIL  $msg" -ForegroundColor Red; $script:problems += $msg }
function Warn($msg) { Write-Host "  WARN  $msg" -ForegroundColor Yellow }

# Invoke-RestMethod's default timeout is 100 seconds. A full-generation eval run
# makes one LLM call per case per arm, so on CPU it can take many minutes. The
# default would abort mid-run and be reported as "Ollama is down", which would be
# the wrong diagnosis. So the timeout is set explicitly and generously.
$LongTimeoutSec = 3600

# Write JSON without a byte-order mark.
#
# Set-Content -Encoding UTF8 writes a BOM on Windows PowerShell 5.1 (it does not
# on PowerShell 7), and a BOM makes `python -m json.tool` and `json.load` fail on
# a file that is otherwise perfectly valid JSON - which is exactly what the
# runbook tells you to do with these files. Writing the bytes directly sidesteps
# the version difference entirely.
function Save-Json($obj, $path) {
    $json = $obj | ConvertTo-Json -Depth 10
    $enc = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($path, $json, $enc)
}

# Read JSON, tolerating a BOM if one is present (a baseline saved by an earlier
# version of this script, or hand-edited in Notepad).
function Read-Json($path) {
    $raw = [System.IO.File]::ReadAllText($path)
    $raw = $raw.TrimStart([char]0xFEFF)
    return ($raw | ConvertFrom-Json)
}

function ApiCall($method, $path, $timeoutSec = 60) {
    $headers = @{}
    if ($AdminId) { $headers["X-Demo-User"] = "$AdminId" }
    if ($method -eq "POST") { $headers["Content-Type"] = "application/json" }
    $url = "$Base$path"
    try {
        $data = Invoke-RestMethod -Method $method -Uri $url -Headers $headers -TimeoutSec $timeoutSec -ErrorAction Stop
        return @{ ok = $true; status = 200; data = $data; error = $null }
    } catch {
        $status = 0
        $detail = $_.Exception.Message
        if ($_.Exception.Response) {
            try { $status = [int]$_.Exception.Response.StatusCode } catch { $status = 0 }
        }
        return @{ ok = $false; status = $status; data = $null; error = $detail }
    }
}

# ---------------------------------------------------------------- diff logic --
# Pulled out as a function so it can be exercised with -FromFile, without a
# server. Everything that decides pass/fail lives here.

function Compare-Summary($base, $curr, $tolerance, $latencyTolerance) {
    $problems = @()
    $lines = @()
    # Metrics that could not be compared because a value was not numeric. Non-empty
    # means the comparison is incomplete, which is reported separately from a
    # regression: "could not measure" and "measured, and it got worse" are
    # different outcomes and must not share an exit code.
    $skipped = @()

    # A comparison is only meaningful if the run settings match. A different
    # model, top-k, rerank setting or run mode is a different experiment, not a
    # regression.
    #
    # When the settings differ the verdict is withheld entirely rather than
    # printed alongside the numbers: a "REGRESSIONS" block computed across a
    # model swap is a false alarm, and a false alarm that exits 1 trains you to
    # ignore the exit code. The caller turns $incomparable into exit 2.
    $mismatch = @()
    if (($base.PSObject.Properties.Name -contains "chatModel") -and ($curr.PSObject.Properties.Name -contains "chatModel")) {
        if ("$($base.chatModel)" -ne "$($curr.chatModel)") {
            $mismatch += "chat model changed  $($base.chatModel) -> $($curr.chatModel)"
        }
        if ("$($base.topK)" -ne "$($curr.topK)") {
            $mismatch += "topK changed  $($base.topK) -> $($curr.topK)"
        }
        if ("$($base.rerankEnabled)" -ne "$($curr.rerankEnabled)") {
            $mismatch += "rerankEnabled changed  $($base.rerankEnabled) -> $($curr.rerankEnabled)"
        }
    }
    # Run mode decides whether the generation metrics mean anything at all: a
    # retrieval-only run reports 0.0 for correctness, faithfulness, abstention
    # and latency by construction. Comparing those zeros against a full run's
    # real numbers produces four phantom regressions every time.
    $baseMode = ""
    $currMode = ""
    $baseHasMode = ($base.PSObject.Properties.Name -contains "retrievalOnly")
    $currHasMode = ($curr.PSObject.Properties.Name -contains "retrievalOnly")
    if ($baseHasMode) {
        if ([bool]$base.retrievalOnly) { $baseMode = "retrieval-only" } else { $baseMode = "full generation" }
    }
    if ($currHasMode) {
        if ([bool]$curr.retrievalOnly) { $currMode = "retrieval-only" } else { $currMode = "full generation" }
    }
    if ($baseHasMode -and $currHasMode -and $baseMode -ne $currMode) {
        $mismatch += "run mode changed  $baseMode -> $currMode"
    }

    # A baseline written before the retrievalOnly field existed cannot be
    # classified, so the mode check above is silently skipped on one side. That
    # is the worst case for the phantom-regression guard: a retrieval-only run
    # compared against a pre-field baseline produces zeros on the left and real
    # numbers on the right, and every one of those zeros reads as a catastrophic
    # regression. Detect it from the run itself instead of trusting the field.
    # A retrieval-only run scores exactly 0 for correctness and faithfulness
    # because it never generates text, so zero on both, alongside a non-zero
    # recall, is diagnostic of the mode rather than of a quality collapse.
    if (-not $baseHasMode -and $currHasMode -and $currMode -eq "retrieval-only") {
        $mismatch += "this run is retrieval-only, but the baseline does not record its" + [Environment]::NewLine +
                     "  mode, so the generation metrics cannot be compared against it" + [Environment]::NewLine +
                     "  (a retrieval-only run scores 0 for correctness and faithfulness by" + [Environment]::NewLine +
                     "  construction, not because quality dropped)"
    }

    if ($mismatch.Count -gt 0) {
        foreach ($m in $mismatch) { $lines += "NOT COMPARABLE: $m" }
        return @{ lines = $lines; problems = @(); incomparable = $true; mismatch = $mismatch }
    }

    # name, direction, is-rate. Higher-is-better unless noted.
    $metrics = @(
        @{ n = "recallAtKImproved";          up = $true;  rate = $true;  label = "recall@K (improved arm)" },
        @{ n = "correctnessImproved";        up = $true;  rate = $true;  label = "correctness (improved arm)" },
        @{ n = "faithfulnessImproved";       up = $true;  rate = $true;  label = "faithfulness (improved arm)" },
        @{ n = "precisionAtKImproved";       up = $true;  rate = $true;  label = "precision@K (improved arm)" },
        @{ n = "perFactCitationPrecisionImproved"; up = $true; rate = $true; label = "per-fact citation precision" },
        @{ n = "abstentionRate";             up = $true;  rate = $true;  label = "abstention rate" },
        @{ n = "avgLatencyImprovedMs";       up = $false; rate = $false; label = "avg latency (improved, ms)" },
        # Latency over answered cases only - the all-case figure above folds in ~3ms
        # abstentions that never called the model, which compress the gap between arms.
        @{ n = "avgLatencyAnsweredImprovedMs"; up = $false; rate = $false; label = "avg latency, answered only (improved, ms)" },
        # More retries is not better or worse on its own, but a large swing moves the
        # latency columns, so it is surfaced for the human to reason about.
        @{ n = "retriesImproved";            up = $false; rate = $false; label = "retries (improved arm)" }
    )

    foreach ($m in $metrics) {
        $hasBase = $base.PSObject.Properties.Name -contains $m.n
        $hasCurr = $curr.PSObject.Properties.Name -contains $m.n

        # A metric that is missing from BOTH sides is a shape change, not a
        # quality change - the field was renamed or dropped. It cannot be
        # compared, but it must not disappear from the report either: a silently
        # skipped metric is one whose regression nobody will ever see.
        if (-not $hasBase -and -not $hasCurr) {
            $lines += ("{0,-34} not present in either run" -f $m.label)
            continue
        }
        # Present on one side only is the dangerous case. The run may be
        # measuring something the baseline never recorded (a metric added to
        # this list, or a summary field added since the baseline was written),
        # and a regression in it is invisible precisely because there is nothing
        # to regress against. Reported as advisory so the verdict is withheld
        # rather than issued on a partial comparison.
        if (-not $hasBase) {
            $lines += ("{0,-34} not in the baseline - not compared" -f $m.label)
            $skipped += "$($m.label): the baseline does not record this metric"
            continue
        }
        if (-not $hasCurr) {
            $lines += ("{0,-34} not in this run - not compared" -f $m.label)
            $skipped += "$($m.label): the run did not report this metric"
            continue
        }

        # A null, empty-string or non-numeric value is not a number, and coercing
        # it to 0.0 would invent a catastrophic regression out of a serialization
        # glitch. It is also not "no change" - the metric simply was not measured,
        # so it is recorded and surfaced as an incomplete comparison rather than
        # quietly passing.
        $bRaw = $base.($m.n)
        $cRaw = $curr.($m.n)
        if (-not (Test-Numeric $bRaw)) {
            $lines += ("{0,-34} baseline value missing/non-numeric" -f $m.label)
            $skipped += "$($m.label): baseline value is not numeric"
            continue
        }
        if (-not (Test-Numeric $cRaw)) {
            $lines += ("{0,-34} value missing/non-numeric" -f $m.label)
            $skipped += "$($m.label): run value is not numeric"
            continue
        }
        $b = [double]$bRaw
        $c = [double]$cRaw
        $delta = $c - $b

        # $verdict names the DIRECTION of the change; $bad records whether that
        # direction is unwanted. They must be computed independently - labelling
        # every out-of-tolerance change "UP" made a drop in correctness print as
        # "^^ UP" while also being reported as a regression, which is worse than
        # no output at all.
        $verdict = "same"
        $bad = $false
        if ($m.rate) {
            if ($delta -gt $tolerance) {
                $verdict = "UP"
                $bad = (-not $m.up)
            } elseif ($delta -lt (-1 * $tolerance)) {
                $verdict = "DOWN"
                $bad = $m.up
            }
        } else {
            # Latency: relative change, since absolute ms vary with machine load.
            if ($b -gt 0) {
                $pct = $delta / $b
                if ($pct -gt $latencyTolerance) { $verdict = "SLOWER"; $bad = $true }
                elseif ($pct -lt (-1 * $latencyTolerance)) { $verdict = "FASTER"; $bad = $false }
            }
        }

        # The arrow shows direction of travel; the verdict word carries the
        # judgement, so a regression can never read as good news.
        $arrow = "  "
        if ($verdict -eq "UP" -or $verdict -eq "FASTER") { $arrow = "^^" }
        if ($verdict -eq "DOWN" -or $verdict -eq "SLOWER") { $arrow = "vv" }

        if ($m.rate) {
            $line = "{0,-34} {1,8:N3} -> {2,8:N3}   {3} {4}" -f $m.label, $b, $c, $arrow, $verdict
        } else {
            $line = "{0,-34} {1,8:N0} -> {2,8:N0}   {3} {4}" -f $m.label, $b, $c, $arrow, $verdict
        }
        $lines += $line
        if ($bad) { $problems += "$($m.label) regressed: $b -> $c" }
    }

    # Abstention deserves its own note: a drop means the system started
    # answering questions it cannot support, which is the failure mode the
    # product most needs to avoid.
    $hasAbst = ($base.PSObject.Properties.Name -contains "abstentionRate") -and ($curr.PSObject.Properties.Name -contains "abstentionRate")
    if ($hasAbst) {
        $a = [double]$curr.abstentionRate
        if ($a -lt 0.999) {
            $lines += ""
            $lines += "NOTE: abstention rate is $a, below 1.0 - at least one unanswerable"
            $lines += "      question was answered. Check the cases listed below."
        }
    }

    # Name the specific cases that lost correctness, so a regression is
    # actionable without opening the JSON by hand.
    if (($base.PSObject.Properties.Name -contains "cases") -and ($curr.PSObject.Properties.Name -contains "cases")) {
        # Group by question rather than assuming one entry per question: a
        # hashtable keyed on the question silently collapses duplicates, and the
        # multi-fact case set is exactly where duplicates could appear.
        $baseCases = @{}
        foreach ($c in $base.cases) {
            $q = "$($c.question)"
            if (-not $baseCases.ContainsKey($q)) { $baseCases[$q] = @() }
            $baseCases[$q] += ,$c
        }
        $currCases = @{}
        foreach ($c in $curr.cases) {
            $q = "$($c.question)"
            if (-not $currCases.ContainsKey($q)) { $currCases[$q] = @() }
            $currCases[$q] += ,$c
        }

        $lost = @()
        foreach ($q in $baseCases.Keys) {
            if (-not $currCases.ContainsKey($q)) { continue }
            # Compare position-wise: a case is only "lost" if it was correct in
            # the baseline and is not correct now. A missing or null sub-object is
            # partial data, not a regression, so it is skipped rather than counted.
            $bList = $baseCases[$q]
            $cList = $currCases[$q]
            $n = [Math]::Min($bList.Count, $cList.Count)
            for ($i = 0; $i -lt $n; $i++) {
                $bi = $bList[$i].improved
                $ci = $cList[$i].improved
                if ($null -eq $bi -or $null -eq $ci) { continue }
                if (($bi.PSObject.Properties.Name -contains "correct") -and
                    ($ci.PSObject.Properties.Name -contains "correct") -and
                    ($bi.correct -eq $true) -and ($ci.correct -ne $true)) {
                    $lost += $q
                }
            }
        }
        if ($lost.Count -gt 0) {
            $lines += ""
            $lines += "Cases that lost correctness:"
            foreach ($q in ($lost | Select-Object -Unique)) { $lines += "  - $q" }
            $problems += "$($lost.Count) case(s) lost correctness"
        }
    } else {
        # Silent absence would look identical to "no case lost correctness".
        $lines += ""
        $lines += "NOTE: per-case detail missing from the baseline or the run, so"
        $lines += "      the case-level comparison was skipped."
    }

    return @{ lines = $lines; problems = $problems; incomparable = $false; mismatch = @(); skipped = $skipped }
}

# A value that can be compared as a number. Rejects null, empty strings, and
# unparseable text, all of which would silently become 0.0 under a bare [double]
# cast and turn a missing field into a fabricated regression.
function Test-Numeric($v) {
    if ($null -eq $v) { return $false }
    if ($v -is [string]) {
        $s = $v.Trim()
        if ($s -eq "") { return $false }
        $parsed = 0.0
        $style = [System.Globalization.NumberStyles]::Float
        $inv = [System.Globalization.CultureInfo]::InvariantCulture
        return [double]::TryParse($s, $style, $inv, [ref]$parsed)
    }
    if ($v -is [double] -or $v -is [int] -or $v -is [long] -or $v -is [decimal] -or $v -is [single]) {
        # NaN and Infinity parse cleanly but compare false against everything, so
        # a NaN metric would silently pass as "same".
        return -not [double]::IsNaN([double]$v) -and -not [double]::IsInfinity([double]$v)
    }
    return $false
}

function Show-Diff($basePath, $currObj) {
    if (-not (Test-Path $basePath)) { return @{ lines = @(); problems = @(); baselineMissing = $true; baselineCorrupt = $false; summaryBad = $false; skipped = @() } }

    # An empty file, or one holding "null", parses without throwing and yields
    # $null. That must NOT be treated as "no baseline yet", because the caller's
    # response to a missing baseline is to overwrite it - which would destroy a
    # real baseline that had merely been truncated, and report success while
    # doing it.
    try {
        $baseObj = Read-Json $basePath
    } catch {
        return @{ lines = @("Baseline is not valid JSON: $basePath"); problems = @("baseline is corrupt"); baselineMissing = $false; baselineCorrupt = $true; summaryBad = $false; skipped = @() }
    }
    if ($null -eq $baseObj -or -not (Test-SummaryShape $baseObj)) {
        return @{ lines = @("Baseline is unreadable or empty: $basePath"); problems = @("baseline is corrupt"); baselineMissing = $false; baselineCorrupt = $true; summaryBad = $false; skipped = @() }
    }
    if (-not (Test-SummaryShape $currObj)) {
        # Not a regression - there is nothing to regress. Reported as "could not
        # run" (exit 2) so a scheduled task does not read it as bad news about
        # the code, and does not read it as good news either.
        return @{ lines = @("This run's summary is empty or not shaped like an eval result - nothing to compare"); problems = @(); baselineMissing = $false; baselineCorrupt = $false; summaryBad = $true; skipped = @() }
    }

    $r = Compare-Summary $baseObj $currObj $Tolerance $LatencyTolerance
    return @{ lines = $r.lines; problems = $r.problems; baselineMissing = $false; baselineCorrupt = $false; incomparable = $r.incomparable; summaryBad = $false; skipped = $r.skipped }
}

# Does this object look like an EvalSummary? A JSON object with no expected
# metric at all is not a summary, however valid its syntax.
function Test-SummaryShape($obj) {
    if ($null -eq $obj) { return $false }
    if ($obj -is [array] -or $obj -is [string] -or $obj -is [bool]) { return $false }
    $names = $obj.PSObject.Properties.Name
    if (-not $names) { return $false }
    return ($names -contains "correctnessImproved") -or ($names -contains "recallAtKBasic")
}

# ------------------------------------------------------------ from-file mode --
# Re-diff a saved run without touching the server. Used to verify this script,
# and to re-read a run you saved earlier.

if ($FromFile) {
    Head "=== Eval diff (from file, no server) ==="
    if (-not (Test-Path $FromFile)) { Write-Host "FAIL  no such file: $FromFile" -ForegroundColor Red; exit 2 }
    try {
        $currObj = Read-Json $FromFile
    } catch {
        Write-Host "FAIL  $FromFile is not valid JSON" -ForegroundColor Red
        exit 2
    }
    Write-Host "Compared: $FromFile"
    Write-Host "Baseline: $Baseline"
    Write-Host ""
    $d = Show-Diff $Baseline $currObj
    if ($d.baselineMissing) {
        Write-Host "No baseline to compare against." -ForegroundColor Yellow
        exit 0
    }
    if ($d.baselineCorrupt) {
        Write-Host "FAIL  the baseline could not be read:" -ForegroundColor Red
        foreach ($l in $d.lines) { Write-Host "  $l" -ForegroundColor Red }
        Write-Host "  Fix or delete it, or establish a new one with -SetBaseline." -ForegroundColor Yellow
        exit 2
    }
    foreach ($l in $d.lines) { Write-Host $l }
    Write-Host ""
    if ($d.summaryBad) {
        Write-Host "FAIL  the run summary could not be read - nothing was compared." -ForegroundColor Red
        exit 2
    }
    if ($d.incomparable) {
        Write-Host "=== NOT COMPARABLE ===" -ForegroundColor Yellow
        Write-Host "  The run settings differ, so a regression verdict would be meaningless." -ForegroundColor Yellow
        Write-Host "  Re-establish the baseline with -SetBaseline if the change is intended." -ForegroundColor Yellow
        exit 2
    }
    if ($d.skipped.Count -gt 0) {
        Write-Host "=== INCOMPLETE COMPARISON ===" -ForegroundColor Yellow
        foreach ($s in $d.skipped) { Write-Host "  - $s" -ForegroundColor Yellow }
        Write-Host "  These metrics were not compared. Treat this run as advisory." -ForegroundColor Yellow
        exit 2
    }
    if ($d.problems.Count -gt 0) {
        Write-Host "=== REGRESSIONS ($($d.problems.Count)) ===" -ForegroundColor Red
        foreach ($p in $d.problems) { Write-Host "  - $p" -ForegroundColor Red }
        exit 1
    }
    Write-Host "=== No regressions ===" -ForegroundColor Green
    exit 0
}

# ---------------------------------------------------------------- preflight --

Head "=== Campus AI RAG eval ==="
Write-Host "  Base:      $Base"
Write-Host "  Admin:     X-Demo-User $AdminId"
Write-Host "  Mode:      $(if ($RetrievalOnly) { 'retrieval only' } else { 'full generation' })"
if ($TopK -gt 0) { Write-Host "  topK:      $TopK (override)" }
Write-Host "  Baseline:  $Baseline"
Write-Host ""

Head "[1/5] Preflight"

# Is the app up at all? A connection failure and a 403 both mean "stop", but
# they mean different things, so they are distinguished rather than collapsed.
$probe = ApiCall "GET" "/eval/rag/cases" 20
if (-not $probe.ok -and $probe.status -eq 0) {
    Fail "cannot reach the app at $Base - is the backend running?"
    Write-Host ""
    Write-Host "  Start it with:  ./mvnw spring-boot:run" -ForegroundColor Yellow
    exit 2
}
if (-not $probe.ok -and $probe.status -eq 403) {
    Fail "the app rejected X-Demo-User $AdminId (403)."
    Write-Host ""
    Write-Host "  The eval endpoints are admin-gated by id, not by role: the id must" -ForegroundColor Yellow
    Write-Host "  be in campus.admin-ids (currently 1) and that user row must exist." -ForegroundColor Yellow
    exit 2
}
if (-not $probe.ok) {
    Fail "preflight call failed (status $($probe.status)): $($probe.error)"
    exit 2
}
$caseCount = ($probe.data | Measure-Object).Count
Pass "app up, admin accepted, $caseCount eval cases available"

if (-not $RetrievalOnly) {
    # Ollama is the thing most likely to be down, and the failure is confusing
    # if it surfaces as a mid-run timeout instead of here.
    $ollamaProbe = ApiCall "POST" "/eval/rag/run?retrievalOnly=true" $LongTimeoutSec
    if ($ollamaProbe.ok) {
        Pass "retrieval path healthy (Ollama not required for this probe)"
    } else {
        Warn "retrieval probe failed (status $($ollamaProbe.status)) - continuing anyway"
    }
}

# -------------------------------------------------------------------- seed --

if (-not $SkipSeed) {
    Head "[2/5] Seeding corpus"
    $seed = ApiCall "POST" "/dev/seed" 120
    if ($seed.ok) {
        $created = "$($seed.data.documentsCreated)"
        $skipped = "$($seed.data.skipped)"
        Info "documents created: $created"
        Info "skipped (already present): $skipped"
        Pass "corpus seeded"
    } else {
        # Seeding failure is not fatal - the corpus may already be populated.
        Warn "seed call failed (status $($seed.status)) - continuing, corpus may already exist"
    }
} else {
    Head "[2/5] Seeding corpus - skipped by request"
}

# --------------------------------------------------------------------- run --

Head "[3/5] Running eval"
$path = "/eval/rag/run?retrievalOnly=$($RetrievalOnly.IsPresent.ToString().ToLower())"
if ($TopK -gt 0) { $path = "$path&topK=$TopK" }
Info "POST $path"
if (-not $RetrievalOnly) { Info "this makes one LLM call per case per arm - it can take several minutes on CPU" }

$run = ApiCall "POST" $path $LongTimeoutSec
if (-not $run.ok) {
    Fail "eval run failed (status $($run.status)): $($run.error)"
    Write-Host ""
    if (-not $RetrievalOnly) {
        Write-Host "  For the full run, check that Ollama is up and has the model:" -ForegroundColor Yellow
        Write-Host "    ollama list" -ForegroundColor Yellow
        Write-Host "    ollama pull qwen2.5:3b" -ForegroundColor Yellow
    }
    exit 2
}
Pass "eval completed"

$summary = $run.data
$stamp = Get-Date -Format "yyyy-MM-dd-HHmmss"
$modeTag = "retrieval"
if (-not $RetrievalOnly) { $modeTag = "full" }
$outFile = Join-Path $evalDir "$stamp-$modeTag.json"
Save-Json $summary $outFile
Info "saved raw result to $outFile"

# --------------------------------------------------------------- headline --

Head "[4/5] Summary"
Write-Host ""
$rows = @(
    @{ k = "totalCases";        v = "$($summary.totalCases) ($($summary.answerable) answerable, $($summary.unanswerable) unanswerable)" },
    @{ k = "recallAtK";         v = "$($summary.recallAtKBasic) basic  ->  $($summary.recallAtKImproved) improved" },
    @{ k = "correctness";       v = "$($summary.correctnessBasic) basic  ->  $($summary.correctnessImproved) improved" },
    @{ k = "faithfulness";      v = "$($summary.faithfulnessBasic) basic  ->  $($summary.faithfulnessImproved) improved" },
    @{ k = "abstentionRate";    v = "$($summary.abstentionRate)" },
    @{ k = "wins/ties/losses";  v = "$($summary.improvedWins) / $($summary.ties) / $($summary.basicWins)" },
    @{ k = "avgLatency";        v = "$($summary.avgLatencyBasicMs) basic  ->  $($summary.avgLatencyImprovedMs) improved (ms, all cases)" },
    @{ k = "avgLatencyAnswered"; v = "$($summary.avgLatencyAnsweredBasicMs) basic  ->  $($summary.avgLatencyAnsweredImprovedMs) improved (ms, answered only)" },
    @{ k = "retries";           v = "$($summary.retriesBasic) basic  ->  $($summary.retriesImproved) improved (2nd LLM call after abstain)" },
    @{ k = "verdict";           v = "$($summary.verdict.winner)" }
)
foreach ($r in $rows) { "  {0,-20} {1}" -f $r.k, $r.v | Write-Host }
Write-Host ""
Write-Host "  verdict reason: $($summary.verdict.reason)" -ForegroundColor Gray
Write-Host ""
Write-Host "  run settings: model=$($summary.chatModel) topK=$($summary.topK) rerank=$($summary.rerankEnabled)" -ForegroundColor Gray

# ------------------------------------------------------------------ diff --

Head "[5/5] Comparing against baseline"

if ($SetBaseline) {
    Save-Json $summary $Baseline
    Pass "saved this run as the new baseline: $Baseline"
    exit 0
}

$d = Show-Diff $Baseline $summary
if ($d.baselineMissing) {
    Write-Host ""
    if (-not (Test-SummaryShape $summary)) {
        Write-Host "  This run returned no usable summary, so it is NOT being saved as" -ForegroundColor Red
        Write-Host "  the baseline. Check the app log and the raw result: $outFile" -ForegroundColor Red
        exit 2
    }
    Write-Host "  No baseline yet, so this run becomes the baseline." -ForegroundColor Yellow
    Save-Json $summary $Baseline
    Info "saved baseline: $Baseline"
    Write-Host ""
    Write-Host "  Re-run this script after a change to compare against it." -ForegroundColor Gray
    exit 0
}
if ($d.baselineCorrupt) {
    Write-Host ""
    Write-Host "=== BASELINE UNREADABLE ===" -ForegroundColor Red
    foreach ($l in $d.lines) { Write-Host "  $l" -ForegroundColor Red }
    Write-Host ""
    Write-Host "  Refusing to overwrite it - a corrupt baseline is usually a truncated" -ForegroundColor Yellow
    Write-Host "  write, and silently replacing it would hide the regression you are" -ForegroundColor Yellow
    Write-Host "  running this script to catch." -ForegroundColor Yellow
    Write-Host "  Inspect it, or accept this run explicitly with -SetBaseline." -ForegroundColor Yellow
    exit 2
}

Write-Host ""
foreach ($l in $d.lines) { Write-Host $l }
Write-Host ""

if ($d.summaryBad) {
    Write-Host "=== RUN UNUSABLE ===" -ForegroundColor Red
    Write-Host "  The eval returned no usable summary, so nothing was compared." -ForegroundColor Red
    Write-Host "  Raw result: $outFile" -ForegroundColor Gray
    exit 2
}

if ($d.incomparable) {
    Write-Host "=== NOT COMPARABLE ===" -ForegroundColor Yellow
    Write-Host "  The run settings differ from the baseline, so a regression verdict" -ForegroundColor Yellow
    Write-Host "  would be meaningless - the difference is the config, not the change." -ForegroundColor Yellow
    Write-Host ""
    Write-Host "  If the change is intended, re-establish the baseline:" -ForegroundColor Gray
    Write-Host "    .\Run_Eval_Diff.ps1 -SetBaseline" -ForegroundColor Gray
    exit 2
}

if ($d.skipped.Count -gt 0) {
    Write-Host "=== INCOMPLETE COMPARISON ===" -ForegroundColor Yellow
    foreach ($s in $d.skipped) { Write-Host "  - $s" -ForegroundColor Yellow }
    Write-Host "  These metrics were not compared - the result is advisory, not a verdict." -ForegroundColor Yellow
    Write-Host "  Raw result: $outFile" -ForegroundColor Gray
    exit 2
}

if ($d.problems.Count -gt 0) {
    Write-Host "=== REGRESSIONS ($($d.problems.Count)) ===" -ForegroundColor Red
    foreach ($p in $d.problems) { Write-Host "  - $p" -ForegroundColor Red }
    Write-Host ""
    Write-Host "  Raw result: $outFile" -ForegroundColor Gray
    Write-Host "  Accept with: .\Run_Eval_Diff.ps1 -SetBaseline" -ForegroundColor Gray
    exit 1
}

Write-Host "=== No regressions ===" -ForegroundColor Green
Write-Host "  Raw result: $outFile" -ForegroundColor Gray
exit 0
