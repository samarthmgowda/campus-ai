# Campus AI — Pending Tests (run on Windows JDK 22)

Keep this open and tick through in Thunder Client (or curl with encoded URLs).
Prereqs: Postgres at localhost:5432 postgres/1234, Ollama at localhost:11434 with `bge-m3` + `qwen2.5:3b`, JAVA_HOME set.

## 0. Start

```
set JAVA_HOME=C:\Program Files\Java\jdk-22
cd C:\PROJECTS\campus-ai
mvnw.cmd clean compile
mvnw.cmd spring-boot:run
```

`spring-boot:run` defaults to the `dev` profile (set in `pom.xml`), which supplies
throwaway local secrets. To run without it, pass another profile and set a real
`JWT_SECRET`, e.g. `-Dspring-boot.run.profiles=prod`. Outside `dev`, `SecretsGuard`
refuses to start when `JWT_SECRET` (or a weak `APP_ADMIN_PASSWORD`) is missing.

Health: `GET http://localhost:8080/documents` → 200 `[]` or list (visible only, hidden filtered). If 404, check port not blocked.

Header note for every authed call: Header tab → `X-Demo-User: 1` (not in URL). Value is student/recruiter id. Admin allow-list is `campus.admin-ids=1` so user 1 is admin.

Seed dev data before testing: `POST /dev/seed` (documents) and `POST /dev/seed-skills` (canonical skills + aliases + co-occurrences). These are idempotent. `POST /dev/seed-recruiter` for section H.

## A. Documents / RAG core (still gates everything)

1. `POST /documents` body `{"title":"Hackathon Rules","content":"Each team must contain between 2 and 4 students."}` → 200, then `GET /documents` shows it. Duplicate title handling via `findByTitle`.
2. `POST /documents/{id}/chunks` body `{"content":"chunk text","pageNumber":1,"section":"Intro"}` → 201, check `embeddingDimension: 1024`.
3. `GET /search?q=How%20many%20students%20can%20be%20in%20a%20hackathon%20team` → similarity > 0.45 passes, < 0.45 gated.
4. `GET /ask?q=How%20many%20students%20can%20be%20in%20a%20hackathon%20team` plus `GET /ask?q=Tell%20me%20a%20joke` → first answered true with exact numbers, second gated out-of-scope. Regression: with Ollama stopped, `/ask` still 200 via Lookup fallback.

## B. Chat history (needs X-Demo-User: 1)

5. `GET /ask?q=hello` with header → then `GET /ask/history?limit=5` → contains that turn. `GET /ask/history/count` → count =1+. `DELETE /ask/history` → 200 and history empty. Without header → 400 Unknown demo user.
6. Chat intent via `GET /ask?q=show%20my%20chat%20history` with header → returns 5 most recent inline.

## C. Profile polish

7. `GET /me/completeness` with `X-Demo-User: 1` → `{score 0-100, missingFields, level}`. Without header → 400.
8. `GET /me/recommendations?limit=5` with header → list via co-occurrence, or trending fallback if no skills. `GET /profile/1/recommendations`.
9. Via chat: `GET /ask?q=how%20complete%20is%20my%20profile` and `GET /ask?q=recommend%20skills` with header.

## D. Skill glossary

10. `GET /skills/glossary?skill=js` → `{canonical:"JavaScript", aliases, related, studentCount+alumniCount}`. `GET /skills/search?q=rea&limit=5` → contains React etc. Via chat: `GET /ask?q=glossary%20for%20js`.

## E. Project create / add-member (thin-tool, owner-checked)

11. `POST /projects` with header 1 body `{"name":"UniqueProj123","description":"desc","domain":"AI","requiredSkills":"Java, React"}` → 200. Duplicate same name (case-insensitive) → 409.
12. `POST /projects/{id}/members` with header 1 body `{"studentId":2}` → 200, member added, notification to 2. Same student again → 409. With non-owner header 2 adding → 403. 11th member → 400 max 10. Hidden project → 404 on getMyProjects/detail.
13. Via chat: `GET /ask?q=create%20project` hints tool.

## F. Student event create + approval flow (validation + admin publish)

14. `POST /events` with header 1 body `{"title":"My Hack","type":"HACKATHON","eventDate":"2026-11-20","description":"desc","location":"Hall A"}` → 200 with `"hidden":true` (pending). Bad type or missing date or title <3 or desc >2000 → 400. `GET /events` does NOT show the new event yet (hidden filtered).
15. Admin approve: `GET /events/admin/all` header 1 → includes pending event; non-admin header 2 → 403. `POST /events/admin/{id}/visibility` header 1 body `{"hidden":false}` → event published, now appears in `GET /events`. Hide again with `{"hidden":true}` → disappears from public list. Join on pending event → 400 "pending approval".
16. Via chat: `GET /ask?q=upcoming%20events` with header → only approved events listed.

## G. Mentorship handshake

17. `POST /mentorship/request` header 1 body `{"alumniId":1,"message":"hi"}` → PENDING. Duplicate pending → idempotent 200 same. After ACCEPT, new request → Already connected. Unavailable alumni → blocked.
18. `POST /mentorship/{id}/respond` header 1 body `{"action":"ACCEPT"}` (requester withdraw) / `{"action":"REJECT"}` or `{action:"CANCEL"}` pending only. Alumni side: `POST /mentorship/{id}/alumni-respond` header alumniId. `GET /mentorship/my?status=PENDING` and `GET /mentorship/alumni/1?status=PENDING`.

## H. Recruiter posting / shortlist (new, heaviest)

19. Seed recruiters if needed: `POST /dev/seed-recruiter` → created 2 (Rahul/Infosys, Priya/Flipkart). Recruiters are separate table; their id is used as X-Demo-User for recruiter calls. Use recruiter id 1 for these tests (adjust if different).
20. `POST /recruiter/postings` header recruiter 1 body `{"title":"Backend Intern","description":"Java role","requiredSkills":"Java, Spring"}` → 200 OPEN. Title <3 or >150 → 400, desc >3000 → 400. `GET /recruiter/postings?status=OPEN` → includes it, `GET /recruiter/postings/{id}` detail.
21. `POST /recruiter/postings/{id}/apply` header student 2 → APPLIED, duplicate APPLIED/SHORTLISTED → idempotent, on CLOSED → 400.
22. `GET /recruiter/postings/{id}/applications` header recruiter 1 (owner) → list; non-owner recruiter 2 → 403. Filter `?status=APPLIED`.
23. `POST /recruiter/postings/{id}/applications/{appId}/decide` header recruiter 1 body `{"action":"SHORTLIST"}` → SHORTLISTED + notification SHORTLIST to student; REJECT similarly. Second decide on non-APPLIED → 400. Wrong posting id → 400.
24. `GET /recruiter/applications/my` header student 2 (optionally `?status=SHORTLISTED`) → own apps. `POST /recruiter/applications/{appId}/withdraw` header student 2 pending only → withdrawn; non-owner → 403, already decided → 400.
25. `POST /recruiter/postings/{id}/close` header recruiter 1 → CLOSED; non-owner → 403. Via chat: `GET /ask?q=open%20postings` and `GET /ask?q=my%20applications` with student header.

## I. Admin moderation actions (admin-ids=1)

26. Seed a report: `POST /reports` header 1 body `{"targetType":"POST","targetId":1,"reason":"SPAM"}`. Self-report STUDENT with same id → 400.
27. `POST /reports/{id}/resolve` header 1 body `{"action":"RESOLVED"}` → hides post / suspends student / hides project/event per target. Non-admin header 2 → 403. Already resolved → 400. Verify side-effect: `GET /posts` no longer shows hidden post, `GET /students?skill=Java` excludes suspended, `GET /documents` excludes hidden docs (see J).

## J. Document upload manager (curated RAG visibility)

28. Upload: `POST /documents/upload` multipart `file` (.txt/.md, ≤5MB) with `X-Demo-User: 1` → `{documentId, title, chunksCreated}` and chunks embedded via `DocumentChunkRepository.findByDocument_Id`. Without file or >5MB or bad content-type → 400. Bad file contains no text → 400.
29. Listing: `GET /documents` → visible only. `GET /documents/{id}` hidden → 404. `GET /documents/stats/overview` → `{visible, hidden, total, totalChunks}`. `GET /documents/my/uploads` header 1 → own uploads including hidden. Admin `GET /documents/admin/all` header 1 → includes hidden; non-admin → 403.
30. Curation: `POST /documents/admin/{id}/visibility` header 1 body `{"hidden":true}` → doc hidden and excluded from `findNearestChunks` (RAG) and listings; `{"hidden":false}` unhides. Non-admin → 403. `DELETE /documents/admin/{id}` header 1 → deletes doc + chunks; then `GET /documents/admin/all` count drops. Via chat: `GET /ask?q=visible%20documents`, `GET /ask?q=my%20uploads` with header, `GET /ask?q=how%20many%20documents`.

## K. Other thin-tool routes (quick spot checks, already built before the six)

- Messaging: `POST /messages` self 400, `GET /messages/inbox`, `/messages/thread?otherId=`, `/messages/{id}/read`, unread-count.
- Analytics: `GET /analytics/summary` etc. Bookmarks: `POST /bookmarks/toggle`, save/remove, isSaved. Endorsements: alias `js→JavaScript` idempotent, withdraw. Follow: follow/unfollow/stats/is-following. Activity/Profile: `GET /activity`, `GET /me`. Event RSVP: join idempotent already_joined/leave/attendees/my. Project invites: invite duplicate/already-member 400 accept/reject/withdraw.

## L. Roadmaps (list/create/detail + UI)

31. `GET /roadmaps` → 200 list (empty until data). `POST /roadmaps` body `{"alumniId":1,"title":"Backend Developer Roadmap","targetRole":"Backend Engineer","description":"1. Learn Java..."}` → 200 with alumniName. Bad title <3, missing targetRole/description, unknown alumniId → 400. `GET /roadmaps?role=backend` filters by role/title. `GET /roadmaps/{id}` detail; unknown id → 400.
32. Via chat: `GET /ask?q=roadmap%20for%20backend` routes to alumni/roadmap content. UI: `/roadmaps` page loads, filter works, publish form works.

## M. Referrals (handshake mirroring mentorship)

33. `POST /referrals/request` header 1 body `{"alumniId":1,"message":"hi","company":"Infosys","role":"Intern"}` → PENDING (only REFERRALS/BOTH alumni; MENTORING-only or UNAVAILABLE → 400). Duplicate pending → idempotent 200 same. After ACCEPT, new request → Already accepted.
34. `POST /referrals/{id}/respond` header 1 `{"action":"ACCEPT"}` / REJECT / CANCEL (pending only, owner only). Alumni side: `POST /referrals/{id}/alumni-respond` body `{"alumniId":1,"action":"ACCEPT"}`. `POST /referrals/{id}/withdraw` pending only. `GET /referrals/my?status=PENDING` and `GET /referrals/alumni/1`.
35. Via chat: `GET /ask?q=my%20referral%20requests` with header → pending list with company/role. UI: `/referrals` shows only REFERRALS-available alumni, request form sends company+role.

## N. Reviews + Search + global pages (built after the six)

36. Reviews: `GET /reviews/student/2` → `{reviews, reputation}`. `POST /reviews` header 1 body `{"revieweeId":2,"rating":5,"comment":"great"}` → 200 (self-review → 400, missing header → 400). UI: `/reviews` peer picker + star writer.
37. Search: `GET /search?q=java` → doc chunks (similarity-gated); UI `/search` fans out to students/alumni/projects/events + semantic docs with tabs. `GET /students`, `GET /alumni?skill=`, `GET /projects/mine` all need header where applicable.
38. SPA fallback: `GET /roadmaps`, `/referrals`, `/reviews`, `/search`, `/events` with `Accept: text/html` → 200 index.html; API `Accept: application/json` never forwarded. Vite dev proxy covers `/roadmaps`, `/referrals`, `/reviews`, `/search`, `/events/admin`, `/referrals` backend paths.

## O. Weighted teammate recommendation + eval (research track, Task #40)

39. Weighted rank: `GET /dev/tools/teammates-weighted?projectId=<id>` with member header → `{projectName, gaps, candidates[{studentId, name, score, skillMatch, interest, availabilityScore, complementary, semantic}]}`; scores in 0..1 sorted desc, members excluded, semantic null without flag. `?semantic=true` needs Ollama (bge-m3) — without it all semantic null and scores equal the hybrid arm. Non-member header → 403, hidden project → 404, no gaps → empty candidates.
40. Three-way eval: `POST /eval/teammates/run?availableOnly=true&includeSemantic=false` header 1 → `{projects, cases, skippedFiltered, arms[{arm, cases, hitAt3, hitAt5, meanRank, avgRatingTop3, ratingCoverageTop3}], detail[]}` with arms `overlap` + `hybrid`. With `includeSemantic=true` (Ollama up) adds `hybrid+semantic` + semanticUsed. Non-admin header 2 → 403. Verdict rule: keep the overlap baseline in `/ask` routing until an arm beats it on hit@K with rating coverage not worse.

## P. Phase 2 M2 — Teammate request workflow (gap-driven, Task #56)

41. Send: `POST /teammate-requests?projectId=<id>&candidateId=<id>&message=<msg>` header 2 (project member) → `RequestDto{id, projectId, projectName, requesterId, requesterName, candidateId, candidateName, status:"PENDING", matchedSkills, createdAt}`; non-member → 403; candidate already member → 409; self-request → 400; duplicate pending → idempotent 200.
42. Respond: `PUT /teammate-requests/{id}/respond?action=ACCEPT` header <candidate> → status ACCEPTED + candidate auto-added as ProjectMember role MEMBER + requester notified; REJECT → status REJECTED + requester notified; wrong candidate → 403; non-pending → 400.
43. Withdraw: `PUT /teammate-requests/{id}/withdraw` header <requester or owner> → WITHDRAWN; candidate tries → 403; non-pending → 400.
44. Inbox: `GET /teammate-requests/mine?status=PENDING` header <candidate> → incoming list; `GET /teammate-requests/sent` header <requester> → sent list; `GET /teammate-requests/project/<id>?status=PENDING` header member → project requests.
45. Suggest: `GET /teammate-requests/project/{id}/suggestions` header member → `TeammateSuggestions{projectName, gaps, candidates[{name, matchedSkills, score}]}`.
46. Via chat: `GET /ask?q=who+should+join+my+project` routes to gap analysis + candidate list + hint "reply 'request teammate'".

When all green, `mvnw.cmd clean compile` stays 0 warnings on JDK 22; the Linux VM here will still report `release 21 not supported` (expected, ignore — use Windows).

## Q. Phase 2 M5 — Role dashboards

47. Student dashboard: `GET /dashboard/student` header 1 → `{identity, projects{count,items}, pendingInvites{count,items}, sentRequests{count,items}, events{count,items}, connections, reputation, skillRecommendations, unreadNotifications, unreadMessages, posts{count}, bookmarks}`; all fields non-null, projects excludes HIDDEN.
48. Alumni dashboard: `GET /dashboard/alumni?alumniId=2` header null, admin 1 → `{identity, mentorshipRequests, referralRequests, connections, skillRecommendations}`; non-admin header = 2 → 403 unless self.
49. Recruiter dashboard: `GET /dashboard/recruiter` header 1 (recruiter) → `{identity{role,userId}, postings{count,items}, unreadNotifications}`.
50. Via chat: `GET /ask?q=my+dashboard` header 1 → summary counts + hint "open /dashboard/student".
51. Skill graph dashboard: `GET /skills/dashboard` header 1 → `{topSkills, topRelated, categoryDistribution, skillsToGrow, totalSkills, totalProfiles}`; skillsToGrow populated when user has skills.
52. Frontend: `/dashboard` renders projects/invites/rsvps/reputation/skill-recs cards; `/skills-graph` renders top skills, related pairs, category bars; nav includes both routes.
