# seed_test_data.ps1
# Seeds all test data for AI chatbot testing
# Run this BEFORE running test_runner.ps1
# Requires: backend running on localhost:8080

$baseUrl = "http://localhost:8080"

Write-Host "=== Seeding Test Data ===" -ForegroundColor Cyan
Write-Host ""

# 1. Seed documents (RAG content)
Write-Host "[1/4] Seeding documents..." -ForegroundColor Yellow
$response = Invoke-RestMethod -Uri "$baseUrl/dev/seed" -Method Post
Write-Host "  Documents created: $($response.documentsCreated)"
Write-Host "  Chunks created: $($response.chunksCreated)"
if ($response.skipped.Count -gt 0) {
    Write-Host "  Skipped (already exist): $($response.skipped -join ', ')"
}

# 2. Seed skills
Write-Host "[2/4] Seeding skills..." -ForegroundColor Yellow
$response = Invoke-RestMethod -Uri "$baseUrl/dev/seed-skills" -Method Post
Write-Host "  Skill glossary entries: $($response.skillsCreated)"
Write-Host "  Aliases: $($response.aliasesCreated)"

# 3. Seed demo users (students, alumni)
Write-Host "[3/4] Seeding demo users..." -ForegroundColor Yellow
$response = Invoke-RestMethod -Uri "$baseUrl/dev/seed" -Method Post
Write-Host "  Seeded: $($response | ConvertTo-Json -Compress)"

# 4. Seed recruiters
Write-Host "[4/4] Seeding recruiters..." -ForegroundColor Yellow
try {
    $response = Invoke-RestMethod -Uri "$baseUrl/dev/seed-recruiter" -Method Post
    Write-Host "  Recruiters created: $($response.recruitersCreated)"
} catch {
    Write-Host "  (Recruiter seed not available — may already be seeded)"
}

Write-Host ""
Write-Host "=== Seeding Complete ===" -ForegroundColor Green
Write-Host ""
Write-Host "Health check: GET /documents" -ForegroundColor Gray
$response = Invoke-RestMethod -Uri "$baseUrl/documents"
Write-Host "  Documents visible: $($response.Count)"
Write-Host ""
Write-Host "You can now run test_runner.ps1 to execute the query catalog."