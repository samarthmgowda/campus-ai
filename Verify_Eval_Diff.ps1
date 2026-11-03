<#
    Verify_Eval_Diff.ps1 - offline contract checks for Run_Eval_Diff.ps1.

    Run_Eval_Diff.ps1 decides whether a change to retrieval or generation made
    the product worse. That is a judgement nobody re-checks by hand, so its
    rules are pinned here instead: each scenario below is a shape of input with
    one defensible outcome, and the script is required to produce it.

    The rules that matter most are the ones that withhold a verdict rather than
    issue a wrong one. A false "no regressions" is the failure this whole file
    exists to prevent - it is silent, and it is the one a reviewer cannot see.

    No server, no Ollama, no database: every scenario is a JSON file and the
    script is invoked with -FromFile, which is the mode that diffs a saved run.

    Scope, so the coverage is not mistaken for more than it is: every scenario
    drives -FromFile, the saved-run path. That path shares Compare-Summary with
    the live path, so all the comparison rules are covered. What is NOT covered
    is the live path's own exit handling downstream of the comparison - it has
    its own corrupt-baseline and unusable-run branches whose wording differs
    from the ones tested here, and reaching them needs a running server. Those
    branches are verified by the two guards being present, not by execution.

    Usage:
        .\Verify_Eval_Diff.ps1            # verify the script next to this file
        .\Verify_Eval_Diff.ps1 -Verbose   # print each scenario's output

    Exit codes: 0 all scenarios behaved, 1 at least one did not.
#>

param(
    [string]$Script = "",
    [string]$WorkDir = "",
    [switch]$Verbose
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($Script)) {
    $Script = Join-Path $PSScriptRoot "Run_Eval_Diff.ps1"
}
if ([string]::IsNullOrWhiteSpace($WorkDir)) {
    $WorkDir = Join-Path ([System.IO.Path]::GetTempPath()) ("evaldiff-" + [Guid]::NewGuid().ToString("N").Substring(0, 8))
}

if (-not (Test-Path $Script)) { Write-Host "FAIL  no such script: $Script" -ForegroundColor Red; exit 1 }
New-Item -ItemType Directory -Force -Path $WorkDir | Out-Null

# The PowerShell host running this file, so scenarios spawn the same edition.
$hostExe = (Get-Process -Id $PID).Path

Write-Host ""
Write-Host "Verifying: $Script" -ForegroundColor Cyan
Write-Host "Host:      $hostExe" -ForegroundColor Gray
Write-Host "Work:      $WorkDir" -ForegroundColor Gray
Write-Host ""

# ------------------------------------------------------------------ fixtures --

# A complete, internally consistent summary: every metric the differ knows
# about is present. Scenarios mutate a copy of this rather than hand-writing
# partial JSON, so a metric added to the differ later does not silently drop out
# of these tests - it appears in the template below and is compared.
$template = @'
{
  "retrievalOnly": false,
  "chatModel": "qwen2.5:3b",
  "topK": 3,
  "rerankEnabled": true,
  "recallAtKBasic": 0.667,
  "recallAtKImproved": 0.667,
  "precisionAtKBasic": 0.722,
  "precisionAtKImproved": 0.778,
  "correctnessBasic": 0.333,
  "correctnessImproved": 0.333,
  "faithfulnessBasic": 0.667,
  "faithfulnessImproved": 0.667,
  "abstentionRate": 1.0,
  "avgLatencyBasicMs": 3000,
  "avgLatencyImprovedMs": 3000,
  "perFactCitationPrecisionImproved": 0.5,
  "improvedWins": 0,
  "ties": 9,
  "basicWins": 0,
  "verdict": { "winner": "tie", "reason": "no arm wins or loses" },
  "cases": []
}
'@

function New-Fixture {
    param([hashtable]$Set, [string[]]$Remove)
    $o = $template | ConvertFrom-Json
    if ($Set) { foreach ($k in $Set.Keys) { $o | Add-Member -Force -NotePropertyName $k -NotePropertyValue $Set[$k] } }
    if ($Remove) { foreach ($k in $Remove) { $o.PSObject.Properties.Remove($k) } }
    return $o
}

function Save-Fixture {
    param($Obj, [string]$Name)
    $p = Join-Path $WorkDir $Name
    ($Obj | ConvertTo-Json -Depth 10) | Set-Content $p -Encoding UTF8
    return $p
}

$bFull    = Save-Fixture (New-Fixture) "baseline-full.json"
$bNoMode  = Save-Fixture (New-Fixture -Remove @("retrievalOnly")) "baseline-nomode.json"
$bNoFact  = Save-Fixture (New-Fixture -Remove @("perFactCitationPrecisionImproved")) "baseline-nofact.json"

$rSame    = Save-Fixture (New-Fixture) "run-same.json"
$rBetter  = Save-Fixture (New-Fixture @{ correctnessImproved = 0.5 }) "run-better.json"
$rDrop    = Save-Fixture (New-Fixture @{ correctnessImproved = 0.1 }) "run-drop.json"
$rRetr    = Save-Fixture (New-Fixture @{ retrievalOnly = $true; correctnessImproved = 0.0; correctnessBasic = 0.0; faithfulnessImproved = 0.0; faithfulnessBasic = 0.0; abstentionRate = 0.0; avgLatencyImprovedMs = 0 }) "run-retrieval.json"
$rNoLat   = Save-Fixture (New-Fixture -Remove @("avgLatencyImprovedMs")) "run-nolat.json"
$rSwapped = Save-Fixture (New-Fixture @{ chatModel = "qwen2.5:7b-instruct" }) "run-swapped.json"

# Within-tolerance drift must read as "same", not as a regression: generation
# samples, so small run-to-run movement is noise. The branch that decides this
# is the one most likely to be off by a comparison operator.
$rJitter  = Save-Fixture (New-Fixture @{ correctnessImproved = 0.343 }) "run-jitter.json"
# A tiny drop WITHIN tolerance must also read as "same" - this is the mirror
# of the above and is the specific case that catches a flipped comparison.
$rSmallDrop = Save-Fixture (New-Fixture @{ correctnessImproved = 0.323 }) "run-smalldrop.json"
# Latency is the only metric where lower is better and the comparison is
# relative, so it takes a different branch from every rate above.
$rSlow    = Save-Fixture (New-Fixture @{ avgLatencyImprovedMs = 9000 }) "run-slow.json"

# Values that are not numbers. A bare [double] cast turns null into 0.0, an
# empty string into 0.0, and compares NaN as false (which reads as "no change").
# Each of those either invents a regression or hides one, so each is pinned.
$rNullVal = Save-Fixture (New-Fixture @{ correctnessImproved = $null }) "run-nullval.json"
$rEmpty   = Save-Fixture (New-Fixture @{ correctnessImproved = "" }) "run-emptyval.json"
$rText    = Save-Fixture (New-Fixture @{ correctnessImproved = "n/a" }) "run-textval.json"
$bNullVal = Save-Fixture (New-Fixture @{ correctnessImproved = $null }) "baseline-nullval.json"

$corrupt = Join-Path $WorkDir "baseline-corrupt.json"
Set-Content $corrupt "{ this is not json" -Encoding UTF8
$malformed = Join-Path $WorkDir "run-malformed.json"
Set-Content $malformed '{"hello":"world"}' -Encoding UTF8
$absent = Join-Path $WorkDir "baseline-absent.json"

# --------------------------------------------------------------- scenarios --

# name, run, baseline, expected exit, substring the output must contain.
#
# The expected text is the wording -FromFile mode actually produces, which is
# not always the wording the main path uses. The main path announces a corrupt
# baseline as "BASELINE UNREADABLE" and an unusable run as "RUN UNUSABLE"; the
# saved-run path says "FAIL the baseline could not be read" and "FAIL the run
# summary could not be read". Same conditions, same exit codes, different words.
# These scenarios pin what each mode emits, so the divergence is recorded rather
# than assumed away; if the wording is ever unified, these two lines change.
$scenarios = @(
    @{ n = "no change                        "; r = $rSame;    b = $bFull;   x = 0; m = "No regressions" }
    @{ n = "improved metric                  "; r = $rBetter;  b = $bFull;   x = 0; m = "No regressions" }
    @{ n = "correctness collapse             "; r = $rDrop;    b = $bFull;   x = 1; m = "REGRESSIONS" }
    @{ n = "chat model swapped               "; r = $rSwapped; b = $bFull;   x = 2; m = "NOT COMPARABLE" }
    @{ n = "retrieval vs classified baseline "; r = $rRetr;    b = $bFull;   x = 2; m = "NOT COMPARABLE" }
    @{ n = "retrieval vs unclassified base   "; r = $rRetr;    b = $bNoMode; x = 2; m = "NOT COMPARABLE" }
    @{ n = "metric absent from baseline      "; r = $rSame;    b = $bNoFact; x = 2; m = "INCOMPLETE COMPARISON" }
    @{ n = "metric absent from the run       "; r = $rNoLat;   b = $bFull;   x = 2; m = "INCOMPLETE COMPARISON" }
    @{ n = "jitter up within tolerance       "; r = $rJitter;  b = $bFull;   x = 0; m = "No regressions" }
    @{ n = "small drop within tolerance      "; r = $rSmallDrop; b = $bFull; x = 0; m = "No regressions" }
    @{ n = "latency regression               "; r = $rSlow;    b = $bFull;   x = 1; m = "REGRESSIONS" }
    @{ n = "null metric value                "; r = $rNullVal; b = $bFull;   x = 2; m = "INCOMPLETE COMPARISON" }
    @{ n = "empty-string metric value        "; r = $rEmpty;   b = $bFull;   x = 2; m = "INCOMPLETE COMPARISON" }
    @{ n = "non-numeric text metric value    "; r = $rText;    b = $bFull;   x = 2; m = "INCOMPLETE COMPARISON" }
    @{ n = "null value in baseline           "; r = $rSame;    b = $bNullVal; x = 2; m = "INCOMPLETE COMPARISON" }
    @{ n = "corrupt baseline                 "; r = $rSame;    b = $corrupt; x = 2; m = "the baseline could not be read" }
    @{ n = "malformed run summary            "; r = $malformed; b = $bFull;  x = 2; m = "the run summary could not be read" }
    @{ n = "first run, no baseline yet       "; r = $rSame;    b = $absent;  x = 0; m = "No baseline" }
)

$failed = 0
foreach ($s in $scenarios) {
    $out = & $hostExe -NoProfile -ExecutionPolicy Bypass -File $Script -FromFile $s.r -Baseline $s.b 2>&1 | Out-String
    $code = $LASTEXITCODE

    $okExit = ($code -eq $s.x)
    $okText = ($out -match [regex]::Escape($s.m))

    if ($okExit -and $okText) {
        Write-Host ("  PASS  {0} exit {1}" -f $s.n, $code) -ForegroundColor Green
    } else {
        $failed++
        Write-Host ("  FAIL  {0} exit {1} (want {2}), expected text '{3}'" -f $s.n, $code, $s.x, $s.m) -ForegroundColor Red
        if (-not $okText) { Write-Host "        output did not contain that text. Full output:" -ForegroundColor Yellow }
        if ($Verbose -or -not $okText) {
            foreach ($line in ($out -split "`r?`n")) { if ($line.Trim() -ne "") { Write-Host "        | $line" -ForegroundColor Gray } }
        }
    }
}

# The corrupt baseline must survive every run: overwriting it would destroy the
# reference point this script exists to measure against.
$staticChecks = 1
$corruptIntact = (Get-Content $corrupt -Raw) -match "this is not json"
if ($corruptIntact) {
    Write-Host "  PASS  corrupt baseline left untouched" -ForegroundColor Green
} else {
    $failed++
    Write-Host "  FAIL  the corrupt baseline was modified - a truncated baseline would be lost" -ForegroundColor Red
}

# ------------------------------------------------- source-level assertions --
#
# The live (server) path cannot be executed here, so the guards that protect the
# baseline on that path are checked as source instead of as behaviour. This is
# weaker than the scenarios above - it proves the guard was not deleted, not
# that it works - but the alternative is no coverage at all on the branches that
# decide whether a real run gets to overwrite the reference point.

Write-Host ""
Write-Host "  source guards (the live path, not executable offline):" -ForegroundColor Gray

$src = Get-Content $Script -Raw

# The from-file path (which the scenarios above already drive) duplicates three
# of these conditions, so a whole-file search would be satisfied by that copy
# and would pass even after the live branch was deleted. Search only the region
# after the live path begins, and match each condition together with the text it
# prints, so a neighbouring branch cannot satisfy it.
$liveStart = $src.IndexOf('=== Campus AI RAG eval ===')
if ($liveStart -lt 0) {
    $failed++
    Write-Host "  FAIL  cannot locate the live path - the source was restructured" -ForegroundColor Red
    $live = ""
} else {
    $live = $src.Substring($liveStart)
}

# The upper bound is generous on purpose: it only needs to be tight enough that
# a match cannot run past the end of the branch into some later one, and the
# next guard's condition is well beyond 700 characters in every case. Too tight
# and the check fails on correct source, which is the worse failure of the two.
$guards = @(
    @{ n = "refuses to overwrite a corrupt baseline"
       p = '(?s)if \(\$d\.baselineCorrupt\) \{(.{0,700}?)Refusing to overwrite it' }
    @{ n = "will not save an unusable summary as baseline"
       p = '(?s)if \(-not \(Test-SummaryShape \$summary\)\) \{(.{0,700}?)is NOT being saved as' }
    @{ n = "withholds a verdict on a config mismatch"
       p = '(?s)if \(\$d\.incomparable\) \{(.{0,700}?)re-establish the baseline' }
    @{ n = "reports an incomplete comparison"
       p = '(?s)if \(\$d\.skipped\.Count -gt 0\) \{(.{0,700}?)INCOMPLETE COMPARISON' }
    # The only deliberate way to move the baseline. Reached only on the live
    # path (the from-file path exits before it), so it gets a source guard too:
    # without this branch, -SetBaseline would parse, run, and quietly do nothing.
    @{ n = "-SetBaseline still replaces the baseline"
       p = '(?s)if \(\$SetBaseline\) \{(.{0,200}?)Save-Json \$summary \$Baseline' }
)

foreach ($g in $guards) {
    $staticChecks++
    if ($live -match $g.p) {
        Write-Host ("  PASS  {0}" -f $g.n) -ForegroundColor Green
    } else {
        $failed++
        Write-Host ("  FAIL  {0} - guard missing from the live path" -f $g.n) -ForegroundColor Red
    }
}

$total = $scenarios.Count + $staticChecks
Write-Host ""
if ($failed -eq 0) {
    Write-Host "All $total checks passed." -ForegroundColor Green
    exit 0
}
Write-Host "$failed of $total checks FAILED." -ForegroundColor Red
exit 1
