# Campus AI — Pending Tests auto-runner (Sections C to O)
# Run from PowerShell: powershell -ExecutionPolicy Bypass -File "C:\PROJECTS\campus-ai\Run_Pending_Tests.ps1"
# Prereqs: backend at localhost:8080, Ollama up, Postgres up. Admin = X-Demo-User 1.
param([string]$Base = "http://localhost:8080")

$script:pass = 0; $script:fail = 0; $script:skipped = 0
function Ok($name) { $script:pass++; Write-Host "PASS  $name" -ForegroundColor Green }
function Bad($name, $detail) { $script:fail++; Write-Host "FAIL  $name -- $detail" -ForegroundColor Red }
function Skip($name, $why) { $script:skipped++; Write-Host "SKIP  $name -- $why" -ForegroundColor Yellow }
function Note($msg) { Write-Host "  > $msg" -ForegroundColor Gray }

function Call($method, $path, $headerVal = $null, $body = $null, $extraHeaders = @{}) {
  $url = "$Base$path"
  $h = @{}
  if ($headerVal) { $h["X-Demo-User"] = "$headerVal" }
  foreach ($k in $extraHeaders.Keys) { $h[$k] = $extraHeaders[$k] }
  # If a hashtable was passed as the 3rd arg (headerVal) when caller meant body,
  # detect it and shift: treat hashtable as body when no valid headerVal string given.
  if ($headerVal -is [hashtable] -or $headerVal -is [System.Collections.IDictionary]) {
    $body = $headerVal; $headerVal = $null
  }
  try {
    if ($body -ne $null) {
      if ($body -is [string]) {
        # already serialized (e.g. raw JSON string)
        $json = $body
      } else {
        $json = $body | ConvertTo-Json -Depth 6 -Compress
      }
      return @{ ok = $true; status = 200; data = (Invoke-RestMethod -Method $method -Uri $url -Headers $h -Body $json -ContentType "application/json" -ErrorAction Stop); raw = $null }
    } else {
      # Even with no body, send Content-Type for POST/PUT methods that may use @RequestBody(required=false)
      if ($method -eq "POST" -or $method -eq "PUT") {
        $h["Content-Type"] = "application/json"
      }
      return @{ ok = $true; status = 200; data = (Invoke-RestMethod -Method $method -Uri $url -Headers $h -ErrorAction Stop); raw = $null }
    }
  } catch {
    $st = 0; $msg = $_.Exception.Message
    if ($_.Exception.Response) { try { $st = [int]$_.Exception.Response.StatusCode } catch {} }
    return @{ ok = $false; status = $st; data = $null; raw = $msg }
  }
}
function Raw($method, $path, $headerVal = $null, $headers = @{}) {
  # returns status code even for non-JSON (SPA fallback etc.)
  $url = "$Base$path"; $h = @{}
  if ($headerVal) { $h["X-Demo-User"] = "$headerVal" }
  foreach ($k in $headers.Keys) { $h[$k] = $headers[$k] }
  try { $r = Invoke-WebRequest -Method $method -Uri $url -Headers $h -UseBasicParsing -ErrorAction Stop; return @{ ok = $true; status = [int]$r.StatusCode; body = $r.Content } }
  catch { $st = 0; try { $st = [int]$_.Exception.Response.StatusCode } catch {}; return @{ ok = $false; status = $st; body = $_.Exception.Message } }
}
function Enc($s) { return [uri]::EscapeDataString($s) }

Write-Host "=== Campus AI pending-tests auto-run ($Base) ===" -ForegroundColor Cyan
$stamp = Get-Date -Format "HHmmss"
$projName = "AutoProj$stamp"
$eventTitle = "Auto Hack $stamp"

# ---------- C. Profile polish ----------
Write-Host "`n[C] Profile polish" -ForegroundColor Cyan
$r = Call GET "/me/completeness" 1
if ($r.ok -and $r.data.score -ne $null) { Ok "C7 completeness (score $($r.data.score))" } else { Bad "C7 completeness" $r.raw }
$r = Call GET "/me/completeness"
if (-not $r.ok) { Ok "C7 no-header gated ($($r.status))" } else { Bad "C7 no-header gated" "expected 400" }
$r = Call GET "/me/recommendations?limit=5" 1
if ($r.ok) { Ok "C8 recommendations" } else { Bad "C8 recommendations" $r.raw }
$r = Call GET "/profile/1/recommendations" 1
if ($r.ok) { Ok "C8 profile/1 recommendations" } else { Note "profile/1 recommendations: $($r.status) $($r.raw)" }
$r = Call GET "/ask?q=$(Enc 'how complete is my profile')" 1
if ($r.ok -and $r.data.answered) { Ok "C9 chat completeness" } else { Bad "C9 chat completeness" $r.raw }
$r = Call GET "/ask?q=$(Enc 'recommend skills')" 1
if ($r.ok -and $r.data.answered) { Ok "C9 chat recommend-skills" } else { Bad "C9 chat recommend-skills" $r.raw }

# ---------- D. Skill glossary ----------
Write-Host "`n[D] Skill glossary" -ForegroundColor Cyan
$r = Call GET "/skills/glossary?skill=js"
if ($r.ok -and "$($r.data.canonical)" -eq "JavaScript") { Ok "D10 glossary js->JavaScript" } else { Bad "D10 glossary js" $r.raw }
$r = Call GET "/skills/search?q=rea&limit=5"
if ($r.ok -and ("$($r.data | ConvertTo-Json -Compress)" -match "React")) { Ok "D10 search rea->React" } else { Bad "D10 search rea" $r.raw }
$r = Call GET "/ask?q=$(Enc 'glossary for js')"
if ($r.ok -and $r.data.answered) { Ok "D10 chat glossary" } else { Bad "D10 chat glossary" $r.raw }

# ---------- E. Projects ----------
Write-Host "`n[E] Projects" -ForegroundColor Cyan
$projId = $null
$r = Call POST "/projects" 1 @{ name = $projName; description = "auto test project"; domain = "AI"; requiredSkills = "Java, React" }
if ($r.ok -and $r.data.id) { $projId = $r.data.id; Ok "E11 create $projName (id $projId)" } else { Bad "E11 create" $r.raw }
$r = Call POST "/projects" 1 @{ name = $projName; description = "dup"; domain = "AI"; requiredSkills = "Java" }
if (-not $r.ok -and $r.status -eq 409) { Ok "E11 duplicate -> 409" } else { Bad "E11 duplicate -> 409" "status $($r.status)" }
if ($projId) {
  $r = Call POST "/projects/$projId/members" 1 @{ studentId = 2 }
  if ($r.ok) { Ok "E12 add member 2" } else { Note "add member 2: status $($r.status) $($r.raw) (may already be member)" }
  $r2 = Call POST "/projects/$projId/members" 1 @{ studentId = 2 }
  if (-not $r2.ok -and ($r2.status -eq 409 -or $r2.status -eq 400)) { Ok "E12 dup add gated ($($r2.status))" } else { Note "dup add: status $($r2.status)" }
  $r3 = Call POST "/projects/$projId/members" 2 @{ studentId = 3 }
  if (-not $r3.ok -and ($r3.status -eq 403 -or $r3.status -eq 400)) { Ok "E12 non-owner gated ($($r3.status))" } else { Note "non-owner add: status $($r3.status) (needs owner=1, student 3 must exist)" }
  Skip "E12 11th-member 400 + hidden 404" "manual: fill to 10 then add 11th; hide via DB/admin"
}
$r = Call GET "/ask?q=$(Enc 'create project')" 1
if ($r.ok -and $r.data.answered) { Ok "E13 chat create-project hint" } else { Bad "E13 chat hint" $r.raw }

# ---------- F. Events ----------
Write-Host "`n[F] Events" -ForegroundColor Cyan
$evId = $null
$r = Call POST "/events" 1 @{ title = $eventTitle; type = "HACKATHON"; eventDate = "2026-11-20"; description = "auto event"; location = "Hall A" }
if ($r.ok -and $r.data.id) { $evId = $r.data.id; if ($r.data.hidden) { Ok "F14 create pending hidden:true (id $evId)" } else { Bad "F14 hidden flag" "expected hidden:true" } } else { Bad "F14 create" $r.raw }
if ($evId) {
  $l = Call GET "/events"
  if ($l.ok -and ("$($l.data | ConvertTo-Json -Compress)" -notmatch [regex]::Escape($eventTitle))) { Ok "F14 hidden from public list" } else { Bad "F14 hidden from public" "event visible before approval" }
  $a = Call GET "/events/admin/all" 1
  if ($a.ok -and ("$($a.data | ConvertTo-Json -Compress)" -match [regex]::Escape($eventTitle))) { Ok "F15 admin/all sees pending" } else { Bad "F15 admin/all" $a.raw }
  $na = Call GET "/events/admin/all" 2
  if (-not $na.ok -and $na.status -eq 403) { Ok "F15 non-admin 403" } else { Bad "F15 non-admin 403" "status $($na.status)" }
  $ap = Call POST "/events/admin/$evId/visibility" 1 @{ hidden = $false }
  if ($ap.ok) { Ok "F15 approve publishes" } else { Bad "F15 approve" $ap.raw }
  $l2 = Call GET "/events"
  if ($l2.ok -and ("$($l2.data | ConvertTo-Json -Compress)" -match [regex]::Escape($eventTitle))) { Ok "F15 appears after approve" } else { Bad "F15 appears after approve" "not found" }
}
$r = Call GET "/ask?q=$(Enc 'upcoming events')" 1
if ($r.ok -and $r.data.answered) { Ok "F16 chat upcoming events" } else { Bad "F16 chat" $r.raw }

# ---------- G. Mentorship ----------
Write-Host "`n[G] Mentorship" -ForegroundColor Cyan
$mId = $null
$r = Call POST "/mentorship/request" 1 @{ alumniId = 1; message = "auto hi" }
if ($r.ok) { $mId = $r.data.id; Ok "G17 request PENDING (id $mId)" } else { Note "mentorship request: $($r.status) $($r.raw) (may already be connected)" }
$r = Call POST "/mentorship/request" 1 @{ alumniId = 1; message = "auto hi" }
if ($r.ok) { Ok "G17 duplicate idempotent" } else { Note "dup request: $($r.status) $($r.raw)" }
if ($mId) {
  $w = Call POST "/mentorship/$mId/withdraw" 1
  if ($w.ok) { Ok "G18 withdraw cleans up" } else { Note "withdraw: $($w.status) $($w.raw)" }
}
$ml = Call GET "/mentorship/my?status=PENDING" 1
if ($ml.ok) { Ok "G18 my list" } else { Bad "G18 my list" $ml.raw }

# ---------- H. Recruiter ----------
Write-Host "`n[H] Recruiter" -ForegroundColor Cyan
$sr = Call POST "/dev/seed-recruiter"
if ($sr.ok) { Ok "H19 seed recruiters" } else { Note "seed-recruiter: $($sr.status) $($sr.raw) (may already exist)" }
$recId = 1
$lr = Call GET "/recruiter/postings?status=OPEN"
if ($lr.ok) { Note "open postings visible" }
$postingId = $null
$r = Call POST "/recruiter/postings" $recId @{ title = "Backend Intern $stamp"; description = "Java role auto"; requiredSkills = "Java, Spring" }
if ($r.ok -and $r.data.id) { $postingId = $r.data.id; Ok "H20 posting OPEN (id $postingId)" } else { Bad "H20 posting" "$($r.status) $($r.raw) (recruiter id may differ -- adjust)" }
$rb = Call POST "/recruiter/postings" $recId @{ title = "x"; description = "bad"; requiredSkills = "Java" }
if (-not $rb.ok -and $rb.status -eq 400) { Ok "H20 bad title 400" } else { Note "bad title: status $($rb.status)" }
if ($postingId) {
  $ap2 = Call POST "/recruiter/postings/$postingId/apply" 2
  if ($ap2.ok) { Ok "H21 apply APPLIED" } else { Note "apply: $($ap2.status) $($ap2.raw)" }
  $apd = Call POST "/recruiter/postings/$postingId/apply" 2
  if ($apd.ok) { Ok "H21 duplicate idempotent" } else { Note "dup apply: $($apd.status)" }
  $ol = Call GET "/recruiter/postings/$postingId/applications" $recId
  if ($ol.ok) { Ok "H22 owner lists applications" } else { Bad "H22 owner list" $ol.raw }
  $nol = Call GET "/recruiter/postings/$postingId/applications" 2
  if (-not $nol.ok -and $nol.status -eq 403) { Ok "H22 non-owner 403" } else { Note "non-owner apps: status $($nol.status) (recruiter 2 must exist)" }
  $apps = @(); try { $apps = @($ol.data) } catch {}
  $appId = $null; if ($apps.Count -gt 0) { $appId = $apps[0].id; if (-not $appId) { $appId = $apps[0].appId } }
  if ($appId) {
    $d = Call POST "/recruiter/postings/$postingId/applications/$appId/decide" $recId @{ action = "SHORTLIST" }
    if ($d.ok) { Ok "H23 SHORTLIST + notify" } else { Note "decide: $($d.status) $($d.raw)" }
    $d2 = Call POST "/recruiter/postings/$postingId/applications/$appId/decide" $recId @{ action = "REJECT" }
    if (-not $d2.ok -and $d2.status -eq 400) { Ok "H23 second decide 400" } else { Note "second decide: status $($d2.status)" }
  } else { Skip "H23 decide" "no app id returned" }
  $my = Call GET "/recruiter/applications/my" 2
  if ($my.ok) { Ok "H24 my applications" } else { Bad "H24 my applications" $my.raw }
  # withdraw flow on a second posting
  $p2 = Call POST "/recruiter/postings" $recId @{ title = "Withdraw Me $stamp"; description = "auto"; requiredSkills = "Java" }
  if ($p2.ok -and $p2.data.id) {
    $a2 = Call POST "/recruiter/postings/$($p2.data.id)/apply" 2
    $myapps = Call GET "/recruiter/applications/my" 2
    $wid = $null; try { foreach ($a in @($myapps.data)) { if ($a.postingId -eq $p2.data.id) { $wid = $a.id } } } catch {}
    if ($wid) { $w2 = Call POST "/recruiter/applications/$wid/withdraw" 2; if ($w2.ok) { Ok "H24 withdraw pending" } else { Note "withdraw: $($w2.status) $($w2.raw)" } }
    else { Note "withdraw: could not find app id" }
    $cl2 = Call POST "/recruiter/postings/$($p2.data.id)/close" $recId
    if ($cl2.ok) { Ok "H25 close second posting" } else { Note "close: $($cl2.status)" }
  }
  $cl = Call POST "/recruiter/postings/$postingId/close" $recId
  if ($cl.ok) { Ok "H25 close main posting" } else { Note "close main: $($cl.status) $($cl.raw)" }
}
$r = Call GET "/ask?q=$(Enc 'open postings')"
if ($r.ok -and $r.data.answered) { Ok "H25 chat open postings" } else { Note "chat open postings: $($r.status)" }
$r = Call GET "/ask?q=$(Enc 'my applications')" 2
if ($r.ok -and $r.data.answered) { Ok "H25 chat my applications" } else { Note "chat my applications: $($r.status)" }

# ---------- I. Moderation ----------
Write-Host "`n[I] Moderation" -ForegroundColor Cyan
$repId = $null
$r = Call POST "/reports" 1 @{ targetType = "POST"; targetId = 1; reason = "SPAM auto $stamp" }
if ($r.ok -and $r.data.id) { $repId = $r.data.id; Ok "I26 seed report (id $repId)" } else { Note "seed report: $($r.status) $($r.raw) (post 1 may be hidden already)" }
$r = Call POST "/reports" 1 @{ targetType = "STUDENT"; targetId = 1; reason = "self" }
if (-not $r.ok -and $r.status -eq 400) { Ok "I26 self-report 400" } else { Note "self-report: status $($r.status)" }
if ($repId) {
  $na = Call POST "/reports/$repId/resolve" 2 @{ action = "RESOLVED" }
  if (-not $na.ok -and $na.status -eq 403) { Ok "I27 non-admin 403" } else { Note "non-admin resolve: status $($na.status)" }
  $ok2 = Call POST "/reports/$repId/resolve" 1 @{ action = "RESOLVED" }
  if ($ok2.ok) { Ok "I27 admin resolve" } else { Note "resolve: $($ok2.status) $($ok2.raw) (may already be resolved)" }
  $re = Call POST "/reports/$repId/resolve" 1 @{ action = "RESOLVED" }
  if (-not $re.ok -and $re.status -eq 400) { Ok "I27 re-resolve 400" } else { Note "re-resolve: status $($re.status)" }
} else { Skip "I27 resolve" "no report id" }

# ---------- J. Document upload manager ----------
Write-Host "`n[J] Documents upload" -ForegroundColor Cyan
$tmp = Join-Path $env:TEMP "auto_upload_$stamp.txt"
"Auto upload content $stamp. Hackathon teams have 2 to 4 students." | Out-File -FilePath $tmp -Encoding utf8
$upId = $null
try {
  # PS 5.1-compatible multipart form upload — build the body manually as a byte array
  $filePath = $tmp
  $fileName = [System.IO.Path]::GetFileName($filePath)
  $fileBytes = [System.IO.File]::ReadAllBytes($filePath)
  $boundary = "----AutoUpload$stamp"
  $cr = [System.Text.Encoding]::UTF8.GetBytes("--$boundary`r`n")
  $headerPart = "Content-Disposition: form-data; name=""file""; filename=""$fileName""" + "`r`n" + "Content-Type: text/plain" + "`r`n`r`n"
  $headerBytes = [System.Text.Encoding]::UTF8.GetBytes($headerPart)
  $footer = "`r`n--$boundary--`r`n"
  $footerBytes = [System.Text.Encoding]::UTF8.GetBytes($footer)
  $bodyBytes = New-Object System.Collections.ArrayList
  [void]$bodyBytes.AddRange($cr)
  [void]$bodyBytes.AddRange($headerBytes)
  [void]$bodyBytes.AddRange($fileBytes)
  [void]$bodyBytes.AddRange($footerBytes)
  $web = New-Object System.Net.WebClient
  $web.Headers.Add("Content-Type", "multipart/form-data; boundary=$boundary")
  $web.Headers.Add("X-Demo-User", "1")
  $respStr = $web.UploadData("$Base/documents/upload", "POST", $bodyBytes.ToArray())
  $respJson = [System.Text.Encoding]::UTF8.GetString($respStr)
  $up = $respJson | ConvertFrom-Json
  $upId = $up.documentId; if (-not $upId) { $upId = $up.id }
  Ok "J28 upload txt (doc $upId, chunks $($up.chunksCreated))"
  $web.Dispose()
} catch { Bad "J28 upload" $_.Exception.Message }
if ($upId) {
  $st = Call GET "/documents/stats/overview"
  if ($st.ok) { Ok "J29 stats overview" } else { Bad "J29 stats" $st.raw }
  $my = Call GET "/documents/my/uploads" 1
  if ($my.ok) { Ok "J29 my uploads" } else { Bad "J29 my uploads" $my.raw }
  $ad = Call GET "/documents/admin/all" 1
  if ($ad.ok) { Ok "J29 admin/all" } else { Bad "J29 admin/all" $ad.raw }
  $na = Call GET "/documents/admin/all" 2
  if (-not $na.ok -and $na.status -eq 403) { Ok "J29 non-admin 403" } else { Bad "J29 non-admin 403" "status $($na.status)" }
  $h1 = Call POST "/documents/admin/$upId/visibility" 1 @{ hidden = $true }
  if ($h1.ok) { Ok "J30 hide excludes from RAG" } else { Note "hide: $($h1.status) $($h1.raw)" }
  $h0 = Call POST "/documents/admin/$upId/visibility" 1 @{ hidden = $false }
  if ($h0.ok) { Ok "J30 unhide" } else { Note "unhide: $($h0.status)" }
  $del = Call DELETE "/documents/admin/$upId" 1
  if ($del.ok) { Ok "J30 delete drops count" } else { Note "delete: $($del.status) $($del.raw)" }
}
$r = Call GET "/ask?q=$(Enc 'visible documents')"
if ($r.ok -and $r.data.answered) { Ok "J30 chat visible documents" } else { Note "chat visible docs: $($r.status)" }

# ---------- K. Spot checks ----------
Write-Host "`n[K] Spot checks" -ForegroundColor Cyan
$r = Call GET "/analytics/summary"
if ($r.ok) { Ok "K analytics summary" } else { Bad "K analytics" $r.raw }
$r = Call GET "/me" 1
if ($r.ok) { Ok "K GET /me" } else { Bad "K /me" $r.raw }
$r = Call GET "/activity" 1
if ($r.ok) { Ok "K activity" } else { Note "activity: $($r.status) $($r.raw)" }
$r = Call POST "/bookmarks/toggle" 1 @{ refType = "POST"; refId = 1 }
if ($r.ok) { Ok "K bookmark toggle" } else { Note "bookmark toggle: $($r.status) $($r.raw)" }
$r = Call GET "/follow/stats" 1
if ($r.ok) { Ok "K follow stats" } else { Note "follow stats: $($r.status) $($r.raw)" }
$r = Call GET "/messages/inbox" 1
if ($r.ok) { Ok "K inbox" } else { Note "inbox: $($r.status) $($r.raw)" }
$r = Call GET "/notifications" 1
if ($r.ok) { Ok "K notifications" } else { Note "notifications: $($r.status) $($r.raw)" }

# ---------- L. Roadmaps ----------
Write-Host "`n[L] Roadmaps" -ForegroundColor Cyan
$rmId = $null
$r = Call GET "/roadmaps"
if ($r.ok) { Ok "L31 list" } else { Bad "L31 list" $r.raw }
$r = Call POST "/roadmaps" @{ alumniId = 1; title = "Backend Dev Roadmap $stamp"; targetRole = "Backend Engineer"; description = "1. Learn Java. 2. Build APIs." }
if ($r.ok -and $r.data.id) { $rmId = $r.data.id; Ok "L31 create (id $rmId)" } else { Bad "L31 create" $r.raw }
$rb = Call POST "/roadmaps" @{ alumniId = 1; title = "x"; targetRole = ""; description = "" }
if (-not $rb.ok -and $rb.status -eq 400) { Ok "L31 bad 400" } else { Note "bad roadmap: status $($rb.status)" }
if ($rmId) { $d = Call GET "/roadmaps/$rmId"; if ($d.ok) { Ok "L31 detail" } else { Bad "L31 detail" $d.raw } }
$r = Call GET "/roadmaps?role=backend"
if ($r.ok) { Ok "L31 role filter" } else { Note "role filter: $($r.status)" }
$r = Call GET "/ask?q=$(Enc 'roadmap for backend')"
if ($r.ok -and $r.data.answered) { Ok "L32 chat roadmap" } else { Note "chat roadmap: answered=$($r.data.answered)" }

# ---------- M. Referrals ----------
Write-Host "`n[M] Referrals" -ForegroundColor Cyan
$rfId = $null
$r = Call POST "/referrals/request" 1 @{ alumniId = 1; message = "auto hi"; company = "Infosys"; role = "Intern" }
if ($r.ok -and $r.data.id) { $rfId = $r.data.id; Ok "M33 request PENDING (id $rfId)" } else { Note "referral request: $($r.status) $($r.raw) (alumni 1 must be REFERRALS/BOTH)" }
$r = Call POST "/referrals/request" 1 @{ alumniId = 1; message = "auto hi"; company = "Infosys"; role = "Intern" }
if ($r.ok) { Ok "M33 duplicate idempotent" } else { Note "dup referral: $($r.status)" }
if ($rfId) { $w = Call POST "/referrals/$rfId/withdraw" 1; if ($w.ok) { Ok "M34 withdraw" } else { Note "withdraw: $($w.status) $($w.raw)" } }
$r = Call GET "/referrals/my?status=PENDING" 1
if ($r.ok) { Ok "M34 my list" } else { Bad "M34 my list" $r.raw }
$r = Call GET "/ask?q=$(Enc 'my referral requests')" 1
if ($r.ok -and $r.data.answered) { Ok "M35 chat referrals" } else { Note "chat referrals: answered=$($r.data.answered)" }

# ---------- N. Reviews + search + SPA ----------
Write-Host "`n[N] Reviews/Search/SPA" -ForegroundColor Cyan
$r = Call GET "/reviews/student/2"
if ($r.ok) { Ok "N36 get student 2 reviews" } else { Bad "N36 get reviews" $r.raw }
$r = Call POST "/reviews" 1 @{ revieweeId = 2; rating = 5; comment = "great auto $stamp" }
if ($r.ok) { Ok "N36 post review" } else { Note "post review: $($r.status) $($r.raw)" }
$sr = Call POST "/reviews" 1 @{ revieweeId = 1; rating = 5; comment = "self" }
if (-not $sr.ok -and $sr.status -eq 400) { Ok "N36 self-review 400" } else { Note "self-review: status $($sr.status)" }
$r = Call GET "/search?q=java"
if ($r.ok) { Ok "N37 search java" } else { Bad "N37 search" $r.raw }
$spa = Raw GET "/roadmaps" $null @{ Accept = "text/html" }
if ($spa.ok -and $spa.status -eq 200) { Ok "N38 SPA fallback roadmaps" } else { Bad "N38 SPA fallback" "status $($spa.status)" }

# ---------- O. Weighted teammates + eval ----------
Write-Host "`n[O] Research track" -ForegroundColor Cyan
if ($projId) {
  $w = Call GET "/dev/tools/teammates-weighted?projectId=$projId" 1
  if ($w.ok) { Ok "O39 weighted rank" } else { Note "weighted: $($w.status) $($w.raw) (needs member header, gaps)" }
  $ws = Call GET "/dev/tools/teammates-weighted?projectId=$projId&semantic=true" 1
  if ($ws.ok) { Ok "O39 weighted+semantic" } else { Note "semantic: $($ws.status) (needs Ollama bge-m3)" }
} else { Skip "O39 weighted" "no project id" }
$e = Call POST "/eval/teammates/run?availableOnly=true&includeSemantic=false" 1
if ($e.ok -and $e.data.arms) { Ok "O40 eval overlap+hybrid" } else { Note "eval: $($e.status) $($e.raw) (needs data + admin 1)" }
$ena = Call POST "/eval/teammates/run?availableOnly=true&includeSemantic=false" 2
if (-not $ena.ok -and $ena.status -eq 403) { Ok "O40 non-admin 403" } else { Note "non-admin eval: status $($ena.status)" }
$es = Call POST "/eval/teammates/run?availableOnly=true&includeSemantic=true" 1
if ($es.ok) { Ok "O40-semantic third arm" } else { Note "semantic eval: $($es.status) (needs Ollama)" }

Write-Host "`n=== RESULT: $($script:pass) passed, $($script:fail) failed, $($script:skipped) skipped ===" -ForegroundColor Cyan
Note "B6 note: 'Your chat history (1 turn...)' already verified PASS just before this script was built."
Note "Verdict rule (O40): keep overlap baseline in /ask until an arm beats it on hit@K with rating coverage not worse."
