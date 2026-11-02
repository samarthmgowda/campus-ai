# test_runner.ps1
# Comprehensive AI chatbot test runner for Campus AI
# Run seed_test_data.ps1 FIRST, then this script
# Requires: backend running on localhost:8080 with Ollama + PostgreSQL

param(
    [string]$BaseUrl = "http://localhost:8080",
    [int]$UserId = 1,
    [string]$ResultsFile = "test_results.csv",
    [int]$DelayMs = 500
)

$headers = @{}
$headers["X-Demo-User"] = "$UserId"

Write-Host "=== Campus AI Chatbot Test Runner ===" -ForegroundColor Cyan
Write-Host "Base URL: $BaseUrl"
Write-Host "User ID: $UserId (admin)"
Write-Host "Output: $ResultsFile"
Write-Host ""

# Initialize CSV
"query_id,category,endpoint,question,answered,answer_preview,sources,notes,status" | Out-File -FilePath $ResultsFile -Encoding UTF8

$totalTests = 0
$passedTests = 0
$failedTests = 0

function Test-Ask {
    param([string]$Id, [string]$Category, [string]$Question, [string]$ExpectedKeyword)
    $totalTests++
    $url = "$BaseUrl/ask?q=$([uri]::EscapeDataString($Question))"
    try {
        $resp = Invoke-RestMethod -Uri $url -Method Get -Headers $headers -TimeoutSec 30
        $answered = $resp.answered
        $answer = $resp.answer
        $sources = if ($resp.sources -and $resp.sources.Count -gt 0) { $resp.sources -join "; " } else { "none" }
        $answerShort = $answer.Substring(0, [Math]::Min(100, $answer.Length)).Replace("`n", " ").Replace("`r", " ")

        $correct = if ($answered -and $ExpectedKeyword) {
            $answer -match $ExpectedKeyword
        } elseif (-not $answered -and $Category -eq "unanswerable") {
            $true
        } elseif ($answered -and -not $ExpectedKeyword) {
            $true
        } else { $false }

        if ($correct) { $passedTests++; $status = "PASS" } else { $failedTests++; $status = "FAIL" }

        "$Id,$Category,/ask,$Question,$answered,$answerShort,$sources,,,$status" | Out-File -FilePath $ResultsFile -Append -Encoding UTF8
        Write-Host "[$status] $Id ($Category): $Question" -ForegroundColor $(if ($correct) { "Green" } else { "Red" })
        Write-Host "       answered=$answered, sources=[$sources]" -ForegroundColor Gray

        Start-Sleep -Milliseconds $DelayMs
    } catch {
        $totalTests++
        "ERROR,$Category,/ask,$Question,false,,,$_,-,ERROR" | Out-File -FilePath $ResultsFile -Append -Encoding UTF8
        $failedTests++
        Write-Host "[ERROR] $Id: $_" -ForegroundColor Red
    }
}

function Test-ApiAgent {
    param([string]$Id, [string]$Category, [string]$Question, [string]$ExpectedText)
    $totalTests++
    $body = @{ question = $Question } | ConvertTo-Json
    try {
        $resp = Invoke-RestMethod -Uri "$BaseUrl/api/agent" -Method Post -Headers $headers -Body $body -ContentType "application/json" -TimeoutSec 30
        $text = $resp.text
        $textShort = $text.Substring(0, [Math]::Min(100, $text.Length)).Replace("`n", " ").Replace("`r", " ")
        $dataSummary = if ($resp.data) { ($resp.data | ConvertTo-Json -Compress).Substring(0, [Math]::Min(50, ($resp.data | ConvertTo-Json -Compress).Length)) } else { "" }

        $correct = if ($ExpectedText) { $text -match $ExpectedText } else { $true }
        if ($correct) { $passedTests++; $status = "PASS" } else { $failedTests++; $status = "FAIL" }

        "$Id,$Category,/api/agent,$Question,$(if($resp.type -eq 'success' -or $resp.type -eq 'info'){'true'}else{'false'}),$textShort,$dataSummary,,,$status" | Out-File -FilePath $ResultsFile -Append -Encoding UTF8
        Write-Host "[$status] $Id ($Category): $Question" -ForegroundColor $(if ($correct) { "Green" } else { "Red" })
        Write-Host "       type=$($resp.type), text=$textShort" -ForegroundColor Gray

        Start-Sleep -Milliseconds $DelayMs
    } catch {
        "ERROR,$Category,/api/agent,$Question,false,,,$_,-,ERROR" | Out-File -FilePath $ResultsFile -Append -Encoding UTF8
        $failedTests++
        Write-Host "[ERROR] $Id: $_" -ForegroundColor Red
    }
}

# ==========================================
# Category A: Single-Fact RAG
# ==========================================
Write-Host ""
Write-Host "--- Category A: Single-Fact RAG ---" -ForegroundColor Yellow

Test-Ask "A1" "single_fact" "How many students can be in a hackathon team?" "hackathon team"
Test-Ask "A2" "single_fact" "What happens to plagiarised or pre-built hackathon projects?" "plagiarised"
Test-Ask "A3" "single_fact" "How much attendance is needed to be eligible for semester exams?" "75"
Test-Ask "A4" "single_fact" "What documents do I need for medical leave absence?" "medical"
Test-Ask "A5" "single_fact" "Who can attend campus placement drives?" "backlogs"
Test-Ask "A6" "single_fact" "When does technical fest registration close?" "registration"
Test-Ask "A7" "single_fact" "Who evaluates the hackathon projects?" "judging"

# ==========================================
# Category B: Multi-Fact RAG
# ==========================================
Write-Host ""
Write-Host "--- Category B: Multi-Fact RAG ---" -ForegroundColor Yellow

Test-Ask "B1" "multi_fact" "How many students can be in a hackathon team and how much attendance is needed for exams?" "team"
Test-Ask "B2" "multi_fact" "What are the two plagiarism consequences and which document governs attendance policy?" "disqualification"
Test-Ask "B3" "multi_fact" "Who can attend placement drives and when does technical fest registration close?" "backlogs"

# ==========================================
# Category C: Unanswerable / Out-of-Scope
# ==========================================
Write-Host ""
Write-Host "--- Category C: Unanswerable / Out-of-Scope ---" -ForegroundColor Yellow

Test-Ask "C1" "unanswerable" "What is the hostel curfew timing on weekends?" ""
Test-Ask "C2" "unanswerable" "How do I apply for a semester exchange program?" ""
Test-Ask "C3" "out_of_scope" "Tell me a joke about databases." ""
Test-Ask "C4" "out_of_scope" "What's the weather like in Mumbai today?" ""

# ==========================================
# Category D: Structured Tool Queries
# ==========================================
Write-Host ""
Write-Host "--- Category D: Structured Tool Queries ---" -ForegroundColor Yellow

Test-ApiAgent "D1" "tool_structured" "show my projects" "project"
Test-ApiAgent "D2" "tool_structured" "find teammates" "teammate"
Test-ApiAgent "D3" "tool_structured" "upcoming events" "event"
Test-ApiAgent "D4" "tool_structured" "my bookmarks" "bookmark"
Test-ApiAgent "D5" "tool_structured" "notifications" "notification"
Test-ApiAgent "D6" "tool_structured" "who knows Java" "Java"
Test-ApiAgent "D7" "tool_structured" "glossary for js" "JavaScript"
Test-ApiAgent "D8" "tool_structured" "skill graph dashboard" "skill"

# ==========================================
# Category E: Follow-up (Conversation Memory)
# ==========================================
Write-Host ""
Write-Host "--- Category E: Follow-up (Conversation Memory) ---" -ForegroundColor Yellow
Write-Host "  (Note: These tests require history from a prior query)" -ForegroundColor Gray

# First seed history by asking A3
Write-Host "  Seeding history with: 'How much attendance is needed?'" -ForegroundColor Gray
$ignored = Invoke-RestMethod -Uri "$BaseUrl/ask?q=How%20much%20attendance%20is%20needed%20for%20exams" -Method Get -Headers $headers -TimeoutSec 30
Start-Sleep -Milliseconds 500

# Now ask follow-up
Test-Ask "E1" "followup" "what?" ""

# Seed history with another question
Write-Host "  Seeding history with: 'How many students in a hackathon team?'" -ForegroundColor Gray
$ignored = Invoke-RestMethod -Uri "$BaseUrl/ask?q=How%20many%20students%20can%20be%20in%20a%20hackathon%20team" -Method Get -Headers $headers -TimeoutSec 30
Start-Sleep -Milliseconds 500

# Follow-up variations
Test-Ask "E2" "followup" "and for hackathons?" ""
Test-Ask "E3" "followup" "details" ""

# ==========================================
# Category F: Edge Cases
# ==========================================
Write-Host ""
Write-Host "--- Category F: Edge Cases ---" -ForegroundColor Yellow

Test-Ask "F1" "edge_case" "hello" ""
Test-Ask "F3" "edge_case" "create project called MyProject for AI needing Java, React" "project"
Test-Ask "F4" "edge_case" "complete my profile" "complete"

# ==========================================
# Admin Eval
# ==========================================
Write-Host ""
Write-Host "--- Admin RAG Eval (retrieval only) ---" -ForegroundColor Yellow
try {
    Write-Host "  Running POST /eval/rag/run?retrievalOnly=true ..." -ForegroundColor Gray
    $evalResult = Invoke-RestMethod -Uri "$BaseUrl/eval/rag/run?retrievalOnly=true" -Method Post -Headers $headers -TimeoutSec 120
    Write-Host "  Verdict: $($evalResult.verdict.winner) - $($evalResult.verdict.reason)" -ForegroundColor Green
    Write-Host "  Recall@K: basic=$([math]::Round($evalResult.recallAtKBasic, 3)) improved=$([math]::Round($evalResult.recallAtKImproved, 3))"
    Write-Host "  Precision@K: basic=$([math]::Round($evalResult.precisionAtKBasic, 3)) improved=$([math]::Round($evalResult.precisionAtKImproved, 3))"
    Write-Host "  Cases: $($evalResult.totalCases) total, $($evalResult.answerable) answerable, $($evalResult.unanswerable) unanswerable"
    Write-Host "  Wins: $($evalResult.improvedWins) improved, $($evalResult.ties) ties, $($evalResult.basicWins) basic"
} catch {
    Write-Host "  Eval failed: $_" -ForegroundColor Red
}

# ==========================================
# Summary
# ==========================================
Write-Host ""
Write-Host "=== Test Summary ===" -ForegroundColor Cyan
Write-Host "Total tests: $totalTests"
Write-Host "Passed: $passedTests" -ForegroundColor Green
Write-Host "Failed: $failedTests" -ForegroundColor Red
Write-Host ""
Write-Host "Results saved to: $ResultsFile"
