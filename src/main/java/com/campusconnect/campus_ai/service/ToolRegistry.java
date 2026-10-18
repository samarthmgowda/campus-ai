package com.campusconnect.campus_ai.service;

import java.util.*;

/**
 * Unified tool registry for the Campus AI agent handoff.
 * <p>
 * This documents every controlled tool the backend exposes for the future agent.
 * The agent (LLM) never gets direct database access — it only calls these tools
 * via the backend, which enforces permissions, validation, and returns DTOs.
 * <p>
 * Source of truth: DB/RAG. Backend: control. LLM: reasoning/orchestration only.
 */
public final class ToolRegistry {

    private ToolRegistry() {}

    // ============================================================
    // STUDENT TOOLS
    // ============================================================

    /**
     * Search students by whole-skill match, branch, availability.
     * Returns: List of {id, name, branch, year, skills, availability}
     * Whole-skill: "Java" does NOT match "JavaScript".
     * Endpoint: GET /students?skill=&branch=&availability=
     * Service: StudentService.search()
     */

    /**
     * Find students who have at least one of the given skills.
     * Excludes specified student IDs.
     * If availableOnly=true, drops BUSY students.
     * Returns: List of CandidateMatch {studentId, name, branch, year, skills, availability, matchedSkills}
     * Sorted: matchedSkills count desc → AVAILABLE first → name.
     * Max 10 results.
     * Service: ProjectToolService.searchStudents(skills, availableOnly, excludeIds)
     */

    // ============================================================
    // PROJECT / TEAM TOOLS
    // ============================================================

    /**
     * Get ACTIVE projects where the user is owner or member.
     * Returns: List of ProjectSummary {id, name, domain, requiredSkills, status, ownerName}
     * Service: ProjectToolService.getMyProjects(userId)
     * Endpoint: GET /projects/mine (header X-Demo-User)
     * Also routed in /ask for "my projects" / "my teams" queries.
     */

    /**
     * Get full project details if user is a member (owner counts as member).
     * Throws 403 if not a member, 404 if project not found.
     * Returns: ProjectDetails {id, name, description, domain, requiredSkills[], members[{studentId,name,branch,year,availability,role,skills[]}]}
     * Service: ProjectToolService.getProjectDetails(userId, projectId)
     */

    /**
     * Compute skill gaps: required skills that NO current member has.
     * Whole-skill, case-insensitive, trim. Returns skills with original casing from project.
     * Returns: List<String>
     * Service: ProjectToolService.getSkillGaps(projectId)
     */

    /**
     * Convenience: check membership → get gaps → searchStudents(gaps, true, memberIds).
     * Returns: TeammateSuggestions {projectName, gaps[], candidates[]}
     * Service: ProjectToolService.findTeammates(userId, projectId)
     * Endpoint: GET /dev/tools/teammates?projectId= (header X-Demo-User) — TEMPORARY
     */

    /**
     * Weighted teammate ranking (research track): S = 0.50*SkillMatch +
     * 0.20*ProjectInterest + 0.20*Availability + 0.10*ComplementarySkills,
     * all components 0..1, hard filters first (members, suspended, BUSY when
     * availableOnly). Optional bge-m3 semantic blend (never throws; Ollama down
     * keeps the hybrid score). Trust (endorsements, reviews, open reports) is
     * a tiebreak/eval signal, not part of S.
     * Returns: WeightedSuggestions {projectName, gaps[], candidates[{..., score}]}
     * Service: TeammateMatchingService.rankForProject(userId, projectId, availableOnly, useSemantic)
     * Endpoint: GET /dev/tools/teammates-weighted?projectId=&availableOnly=&semantic= (header X-Demo-User) — TEMPORARY
     */

    /**
     * Three-way teammate eval (research track, admin X-Demo-User in campus.admin-ids):
     * leave-one-out over ACTIVE multi-member projects with required skills —
     * overlap baseline vs hybrid vs hybrid+semantic, hit@3/hit@5, mean rank,
     * review-rating signal (avg rating + coverage over top-3). Endpoint:
     * POST /eval/teammates/run?availableOnly=&includeSemantic= (semantic arm needs Ollama).
     * Service: TeammateEvalService.run(availableOnly, includeSemantic)
     */

    // ============================================================
    // DASHBOARD TOOLS (Phase 2 M5)
    // ============================================================

    /**
     * Student dashboard: projects, invites, teammate requests, events, connections,
     * reputation, skill recommendations, activity counts (notifications, messages).
     * Returns: Map{identity, projects{count, items}, pendingInvites{count, items},
     *   sentRequests{count, items}, events{count, items}, connections, reputation,
     *   skillRecommendations, unreadNotifications, unreadMessages, posts{count}, bookmarks}
     * Service: DashboardService.getStudentDashboard(userId)
     * Endpoint: GET /dashboard/student (header X-Demo-User)
     * Also routed in LookupService for "my dashboard" queries.
     */

    /**
     * Alumni dashboard: mentorship requests, referrals, connections, skill graph snapshot.
     * Returns: Map{identity, mentorshipRequests{count, items}, referralRequests{count, items},
     *   connections, skillRecommendations}
     * Service: DashboardService.getAlumniDashboard(alumniId, adminAsUserId)
     * Endpoint: GET /dashboard/alumni?alumniId= (optional X-Demo-User for connection stats)
     * Only accessible to admin (campus.admin-ids) for other alumni; self-access via header.
     */

    /**
     * Recruiter dashboard: postings, applications, notifications.
     * Returns: Map{identity{role, userId}, postings{count, items}, unreadNotifications}
     * Service: DashboardService.getRecruiterDashboard(recruiterId)
     * Endpoint: GET /dashboard/recruiter (header X-Demo-User)
     */

    // ============================================================
    // EVENT TOOLS
    // ============================================================

    /**
     * Search events by type, optionally only upcoming (date >= today).
     * Returns: List of {id, title, type, eventDate, description, location}
     * Endpoint: GET /events?type=&upcoming=
     * Service: EventService.search()
     * Also routed in LookupService for "upcoming events" queries.
     */

    // ============================================================
    // ALUMNI / MENTORSHIP TOOLS
    // ============================================================

    /**
     * Search alumni by whole-skill match and/or availability.
     * Availability values: MENTORING, REFERRALS, BOTH, UNAVAILABLE
     * Returns: List of {id, name, branch, graduationYear, skills, currentRole, currentCompany, availability}
     * Endpoint: GET /alumni?skill=&availability=
     * Service: AlumniService.search()
     */

    /**
     * Find mentors for a skill: alumni with MENTORING or BOTH availability who have that skill.
     * Whole-skill match.
     * Returns: List of {name, currentRole, currentCompany, skills}
     * Routed in LookupService for "mentor for X" queries.
     */

    /**
     * Get all available roadmaps from alumni.
     * Returns: List of {title, targetRole, description, authorName, authorRole, authorCompany}
     * Routed in LookupService for "roadmap", "career path", "placement roadmap", "how to become" queries.
     */

    // ============================================================
    // DOCUMENT / RAG TOOLS
    // ============================================================

    /**
     * Hybrid retrieval over document chunks: pgvector cosine (semantic) fused with
     * Postgres full-text ts_rank (keyword) via weighted reciprocal-rank fusion (0.5/0.5, k=60).
     * Embeds query via Ollama bge-m3 (1024-dim). Keyword-only hits get cosine similarity
     * computed locally against the query embedding so the similarity gate still applies.
     * Returns: List of SearchResult {id, documentId, documentTitle, content, pageNumber, section, similarity, hybridScore, rerankScore}
     * Retrieve-wide then deterministic rerank (RerankService: 0.45 semantic + 0.25 term + 0.15 bigram + 0.10 phrase + 0.05 title),
     * trimmed to limit. RerankScore null when rag.rerank-enabled=false (fusion order kept).
     * Sorted by rerankScore (or hybridScore when disabled) desc. Similarity rounded to 4 decimals.
     * Gate: rag.min-similarity (default 0.45) and rag.top-k (default 3) enforced before LLM call.
     * Endpoint: GET /search?q=&limit=
     * Service: SearchService.search()
     */

    /**
     * Upload a .txt or .md file (max 5 MB) → extract text → structure-aware sectioning
     * (heading breadcrumb paths, explicit "Page N" markers or ~3000-char page estimates,
     * nearest academic-year marker appended to section) → paragraph-pack chunks (~2800 chars,
     * sentence-boundary splits, 200 overlap) → embed with document+section context → store in pgvector.
     * Chunk text stays raw; structure travels in section/page metadata for citations.
     * Returns: IngestionResult {documentId, title, chunksCreated, skippedSections}
     * Endpoint: POST /documents/upload (multipart form, key "file")
     * Service: DocumentIngestionService.ingest()
     */

    /**
     * Add a single text chunk to an existing document with embedding.
     * Returns: DocumentChunk {id, documentId, content, pageNumber, section, embeddingDimension}
     * Endpoint: POST /documents/{documentId}/chunks
     * Service: DocumentChunkService.addChunk()
     */

    // ============================================================
    // PERMISSION & CONTEXT RULES (enforced in Java, not by LLM)
    // ============================================================

    /**
     * All "my" tools (getMyProjects, getProjectDetails, findTeammates) require
     * authenticated user via X-Demo-User header → CurrentUserService.requireUser().
     * LLM never supplies user IDs — backend derives from auth context.
     * Project membership checked before any project data is returned (403 if not member).
     */

    /**
     * Data minimization: no phone, address, password, CGPA in any DTO.
     * Student/Alumni skills are comma-separated strings; whole-skill matching used everywhere.
     */

    /**
     * RAG gating: if search returns empty or best similarity < rag.min-similarity,
     * /ask returns OUT_OF_SCOPE_ANSWER without calling the LLM.
     * Grounded generation: answers must cite passages as [n]; a deterministic
     * faithfulness post-check (citation presence + term overlap >=
     * rag.faithfulness-min-overlap + verbatim numbers) turns ungrounded answers
     * into abstentions ("I don't have that information in the college documents.")
     * with sources attached. Sources carry {documentId, documentTitle, section,
     * pageNumber, similarity, hybridScore, rerankScore}.
     */

    // ============================================================
    // ALL TOOLS SUMMARY (for agent prompt injection)
    // ============================================================

    /**
     * Agent-ready summary for prompt injection.
     * Each tool: name, purpose, inputs, outputs, permissions, when to use.
     */
    public static List<ToolSpec> allTools() {
        return List.of(
                new ToolSpec("searchStudents",
                        "Find students by skill/branch/availability",
                        Map.of("skill", "string (optional)", "branch", "string (optional)", "availability", "string (optional)"),
                        "List<StudentSummary>",
                        "Public — no auth required",
                        "Discover teammates, browse profiles"),

                new ToolSpec("searchStudentsAdvanced",
                        "Find students matching any of given skills with evidence",
                        Map.of("skills", "List<String>", "availableOnly", "boolean", "excludeStudentIds", "List<Long>"),
                        "List<CandidateMatch>",
                        "Internal — used by findTeammates",
                        "Skill-gap based teammate matching"),

                new ToolSpec("createProject",
                        "Create a new ACTIVE project (owner auto-joins)",
                        Map.of("name", "String 3-120 unique", "description", "String (optional, max 2000)", "domain", "String (optional)", "requiredSkills", "String CSV, normalized"),
                        "ProjectSummary{id, name, domain, requiredSkills, status, ownerName}",
                        "Requires auth (X-Demo-User) — becomes OWNER",
                        "Start a new collaboration"),

                new ToolSpec("addProjectMember",
                        "Add a student to a project team (max 10, notifies)",
                        Map.of("projectId", "Long", "studentId", "Long"),
                        "MemberInfo{studentId, name, branch, year, availability, role}",
                        "Requires auth + membership (FORBIDDEN if not member, CONFLICT if already member)",
                        "Grow team after gaps check"),

                new ToolSpec("getMyProjects",
                        "List ACTIVE projects where user is owner/member",
                        Map.of("userId", "Long (from auth)"),
                        "List<ProjectSummary>",
                        "Requires auth (X-Demo-User)",
                        "User asks 'my projects' or 'my teams'"),

                new ToolSpec("getProjectDetails",
                        "Full project details if user is member",
                        Map.of("userId", "Long (from auth)", "projectId", "Long"),
                        "ProjectDetails",
                        "Requires auth + membership (403 if not member)",
                        "View team composition before asking for teammates"),

                new ToolSpec("getSkillGaps",
                        "Required skills missing from current team",
                        Map.of("projectId", "Long"),
                        "List<String>",
                        "Requires auth + membership",
                        "Identify what skills to recruit for"),

                new ToolSpec("findTeammates",
                        "End-to-end: gaps → available candidates with matched skills",
                        Map.of("userId", "Long (from auth)", "projectId", "Long"),
                        "TeammateSuggestions",
                        "Requires auth + membership",
                        "User asks 'find teammates for my project'"),

                new ToolSpec("rankTeammatesWeighted",
                        "Weighted teammate ranking: S=0.50*SkillMatch+0.20*Interest+0.20*Availability+0.10*Complementary, optional bge-m3 semantic blend",
                        Map.of("userId", "Long (from auth)", "projectId", "Long", "availableOnly", "boolean", "useSemantic", "boolean (needs Ollama)"),
                        "WeightedSuggestions{projectName, gaps, candidates[{studentId, name, score, skillMatch, interest, availabilityScore, complementary, semantic}]}",
                        "Requires auth + membership",
                        "Research track: compare against overlap baseline via /eval/teammates/run before claiming wins"),

                new ToolSpec("evalTeammates",
                        "Three-way teammate eval: leave-one-out hit@3/hit@5 + mean rank + review-rating signal (overlap vs hybrid vs hybrid+semantic)",
                        Map.of("availableOnly", "boolean", "includeSemantic", "boolean (needs Ollama)"),
                        "TeammateEvalSummary{projects, cases, skippedFiltered, arms[], detail[]}",
                        "Admin only (campus.admin-ids)",
                        "Prove the weighted formula before switching chat routing"),

                new ToolSpec("createEvent",
                        "Create a new campus event (title 3-120, type validated, date YYYY-MM-DD). New events start pending admin approval before appearing publicly",
                        Map.of("title", "String 3-120", "type", "String HACKATHON|WORKSHOP|SEMINAR|NETWORKING|PLACEMENT", "eventDate", "String YYYY-MM-DD", "description", "String (optional, max 2000)", "location", "String (optional)"),
                        "Event{id, title, type, eventDate, description, location} (hidden until approved)",
                        "Requires auth (X-Demo-User)",
                        "Student creates an event"),

                new ToolSpec("searchEvents",
                        "Find events by type and/or upcoming",
                        Map.of("type", "string (optional)", "upcoming", "boolean (optional)"),
                        "List<EventSummary>",
                        "Public",
                        "User asks about events/workshops/hackathons"),

                new ToolSpec("searchAlumni",
                        "Find alumni by skill and/or availability",
                        Map.of("skill", "string (optional)", "availability", "string (optional)"),
                        "List<AlumniSummary>",
                        "Public",
                        "Browse mentors, referrals"),

                new ToolSpec("findMentors",
                        "Alumni with MENTORING/BOTH availability for a skill",
                        Map.of("skill", "string"),
                        "List<MentorSummary>",
                        "Internal — used by LookupService",
                        "User asks 'mentor for Java'"),

                new ToolSpec("getRoadmaps",
                        "All alumni roadmaps for career guidance",
                        Map.of(),
                        "List<RoadmapSummary>",
                        "Internal — used by LookupService",
                        "User asks 'roadmap', 'career path', 'how to become'"),

                new ToolSpec("searchDocuments",
                        "Semantic search over college documents (RAG)",
                        Map.of("query", "string", "limit", "int (default 5)"),
                        "List<SearchResult>",
                        "Public",
                        "Policy questions, rules, guidelines"),

                new ToolSpec("uploadDocument",
                        "Upload .txt/.md → chunk → embed → store",
                        Map.of("file", "MultipartFile (max 5 MB, text/markdown)"),
                        "IngestionResult",
                        "Requires auth",
                        "Add new knowledge to RAG"),

                new ToolSpec("addDocumentChunk",
                        "Add single chunk to existing document",
                        Map.of("documentId", "Long", "content", "string", "pageNumber", "Integer", "section", "string"),
                        "DocumentChunk",
                        "Internal/Dev",
                        "Seed or extend documents"),

                new ToolSpec("normalizeSkill",
                        "Normalize raw skill to canonical form using aliases",
                        Map.of("raw", "string"),
                        "Map{raw, canonical}",
                        "Public",
                        "Clean user input before searching"),

                new ToolSpec("normalizeSkillCsv",
                        "Normalize comma-separated skills to deduplicated canonical list",
                        Map.of("csv", "string"),
                        "List<String>",
                        "Public",
                        "Normalize profile skills or search queries"),

                new ToolSpec("getRelatedSkills",
                        "Find skills that frequently co-occur with a given skill",
                        Map.of("skill", "string (canonical)", "limit", "int (default 10)"),
                        "List<RelatedSkill{skillName, count}>",
                        "Internal — used for suggestions",
                        "Expand search query, recommend adjacent skills"),

                new ToolSpec("rebuildSkillGraph",
                        "Recompute canonical skills + co-occurrences from all profiles",
                        Map.of(),
                        "Map{status}",
                        "Admin/Dev",
                        "Run after bulk profile changes"),

                new ToolSpec("getAllCanonicalSkills",
                        "All known canonical skills for autocomplete/dropdowns",
                        Map.of(),
                        "List<String>",
                        "Public",
                        "UI skill picker"),

                new ToolSpec("getSkillGlossary",
                        "Alias -> canonical + aliases + related + profile counts for a skill",
                        Map.of("skill", "string (raw or alias, e.g. js, reactjs)"),
                        "Map{query, canonical, category, aliases, related[{skill,count}], studentCount, alumniCount, profileCount}",
                        "Public",
                        "Skill glossary / tooltip"),
                new ToolSpec("searchSkills",
                        "Substring search over canonical skill names (autocomplete)",
                        Map.of("q", "string", "limit", "int (default 10, max 50)"),
                        "List<String>",
                        "Public",
                        "Skill autocomplete / discovery"),

                new ToolSpec("getSkillsByCategory",
                        "Filter canonical skills by category",
                        Map.of("category", "string (Language/Framework/Cloud/ML/Design/Tooling/Other)"),
                        "List<String>",
                        "Public",
                        "Browse skills by domain"),

                new ToolSpec("recruiterSearchStudents",
                        "Recruiter view: find students by skill/availability/year with canonical normalization",
                        Map.of("skill", "string (optional)", "availability", "string (optional)", "minYear", "Integer (optional)", "maxYear", "Integer (optional)"),
                        "List<StudentResult>",
                        "Recruiter (public for demo)",
                        "Candidate discovery for roles"),

                new ToolSpec("recruiterGetStudentPortfolio",
                        "Projects where a student is a member (recruiter view)",
                        Map.of("studentId", "Long"),
                        "List<ProjectPortfolioEntry>",
                        "Recruiter (public for demo)",
                        "View candidate's work history"),

                new ToolSpec("recruiterGetStudentDetail",
                        "Full student profile + portfolio in one call",
                        Map.of("studentId", "Long"),
                        "Map (profile + portfolio)",
                        "Recruiter (public for demo)",
                        "Recruiter deep dive on candidate"),

                new ToolSpec("createPost",
                        "Create a community post",
                        Map.of("content", "string"),
                        "PostDto",
                        "Requires auth (authorId)",
                        "Share updates, questions, or highlights"),

                new ToolSpec("getFeed",
                        "Get community feed (most recent posts)",
                        Map.of("limit", "int (default 20)"),
                        "List<PostDto>",
                        "Public",
                        "Browse community"),

                new ToolSpec("getPost",
                        "Fetch a single community post",
                        Map.of("postId", "Long"),
                        "PostDto",
                        "Public",
                        "Deep link to a post"),

                new ToolSpec("getPostsByAuthor",
                        "Posts by a specific student",
                        Map.of("authorId", "Long"),
                        "List<PostDto>",
                        "Public",
                        "View someone's activity"),

                new ToolSpec("toggleLike",
                        "Like or unlike a post (toggles)",
                        Map.of("postId", "Long"),
                        "Map{postId, likeCount}",
                        "Requires auth",
                        "Engage with posts"),

                new ToolSpec("addComment",
                        "Add a comment to a post",
                        Map.of("postId", "Long", "content", "string"),
                        "CommentDto",
                        "Requires auth",
                        "Discuss a post"),

                new ToolSpec("getComments",
                        "Comments for a post (oldest first)",
                        Map.of("postId", "Long"),
                        "List<CommentDto>",
                        "Public",
                        "Read discussion"),

                new ToolSpec("writeReview",
                        "Write a peer review (1–5 stars + comment) for a student, optionally scoped to a project",
                        Map.of("revieweeId", "Long", "reviewerId", "Long", "rating", "Integer (1-5)", "comment", "string", "projectId", "Long (optional)"),
                        "ReviewDto",
                        "Requires auth (reviewer != reviewee)",
                        "Rate a collaborator after a project"),

                new ToolSpec("getReviewsForStudent",
                        "All reviews + reputation for a student",
                        Map.of("studentId", "Long"),
                        "Map{reviews, reputation}",
                        "Public",
                        "View profile trust signal"),

                new ToolSpec("getReputationForStudent",
                        "Reputation summary: reviewCount + averageRating",
                        Map.of("studentId", "Long"),
                        "Map{studentId, reviewCount, averageRating}",
                        "Public",
                        "Quick reputation check"),

                new ToolSpec("getReviewsForProject",
                        "All reviews scoped to a project",
                        Map.of("projectId", "Long"),
                        "List<ReviewDto>",
                        "Public",
                        "Team retrospectives"),

                new ToolSpec("sendMessage",
                        "Send a direct message to another student",
                        Map.of("recipientId", "Long", "content", "string"),
                        "MessageDto",
                        "Requires auth (X-Demo-User)",
                        "User wants to message/DM someone"),

                new ToolSpec("getThread",
                        "Get full message thread with a peer",
                        Map.of("peerId", "Long"),
                        "List<MessageDto>",
                        "Requires auth",
                        "View conversation history"),

                new ToolSpec("markThreadRead",
                        "Mark all messages from a peer as read",
                        Map.of("peerId", "Long"),
                        "Map{peerId, markedRead}",
                        "Requires auth",
                        "Clear unread badge for a thread"),

                new ToolSpec("getInbox",
                        "Inbox grouped by peer with preview and unread count",
                        Map.of(),
                        "List<ConversationDto>",
                        "Requires auth",
                        "User asks inbox / my messages"),

                new ToolSpec("getUnreadCount",
                        "Total unread message count",
                        Map.of(),
                        "Map{userId, unreadCount}",
                        "Requires auth",
                        "Badge / unread check"),

                new ToolSpec("getAnalyticsSummary",
                        "Campus Intelligence summary counts",
                        Map.of(),
                        "Map{students, alumni, projects, documents, chunks, posts, likes, comments, messages, reviews, events, upcomingEvents, roadmaps}",
                        "Public",
                        "Dashboard overview"),

                new ToolSpec("getTopSkills",
                        "Top skills by student count (canonical)",
                        Map.of("limit", "int (default 10)"),
                        "List<Map{skill, count}>",
                        "Public",
                        "Skill demand chart"),

                new ToolSpec("getProjectStats",
                        "Projects grouped by status/domain plus avg team size",
                        Map.of(),
                        "Map{total, byStatus, byDomain, avgMembers}",
                        "Public",
                        "Project portfolio stats"),

                new ToolSpec("getCommunityStats",
                        "Community health: posts/likes/comments/messages/reviews + top posts",
                        Map.of(),
                        "Map{posts, likes, comments, messages, reviews, topPosts}",
                        "Public",
                        "Engagement metrics"),

                new ToolSpec("getAvailabilityBreakdown",
                        "Students grouped by AVAILABLE/BUSY",
                        Map.of(),
                        "Map<String, Long>",
                        "Public",
                        "Capacity planning"),

                new ToolSpec("getBranchBreakdown",
                        "Students grouped by branch",
                        Map.of(),
                        "Map<String, Long>",
                        "Public",
                        "Branch distribution"),

                new ToolSpec("getNotifications",
                        "List notifications for current user",
                        Map.of("unreadOnly", "boolean (optional)", "limit", "int (default 20)"),
                        "List<NotificationDto>",
                        "Requires auth (X-Demo-User)",
                        "Inbox badge, notification center"),

                new ToolSpec("getNotificationUnreadCount",
                        "Unread notification count",
                        Map.of(),
                        "Map{userId, unreadCount}",
                        "Requires auth",
                        "Badge count"),

                new ToolSpec("markNotificationRead",
                        "Mark a single notification as read",
                        Map.of("notificationId", "Long"),
                        "Map{id, readFlag}",
                        "Requires auth + ownership",
                        "Dismiss one alert"),

                new ToolSpec("joinEvent",
                        "RSVP to an event (idempotent)",
                        Map.of("eventId", "Long"),
                        "Map{eventId, studentId, status, attendeeCount}",
                        "Requires auth (X-Demo-User)",
                        "User wants to join/attend/RSVP"),

                new ToolSpec("leaveEvent",
                        "Cancel RSVP for an event",
                        Map.of("eventId", "Long"),
                        "Map{eventId, studentId, status, attendeeCount}",
                        "Requires auth",
                        "User cancels attendance"),

                new ToolSpec("getEventAttendees",
                        "List attendees for an event",
                        Map.of("eventId", "Long"),
                        "List<AttendeeDto>",
                        "Public",
                        "See who is going"),

                new ToolSpec("getEventAttendeeCount",
                        "Attendee count for an event",
                        Map.of("eventId", "Long"),
                        "Map{eventId, attendeeCount}",
                        "Public",
                        "Capacity badge"),

                new ToolSpec("inviteToProject",
                        "Invite a student to join your project (member/owner only, dedup pending)",
                        Map.of("projectId", "Long", "inviteeId", "Long"),
                        "InviteDto{id, projectId, projectName, inviterId, inviterName, inviteeId, inviteeName, status, createdAt}",
                        "Requires auth + project membership",
                        "Add teammate to your project"),

                new ToolSpec("respondToInvite",
                        "Accept or reject a pending project invite (invitee only; accept adds membership)",
                        Map.of("inviteId", "Long", "action", "String ACCEPT|REJECT"),
                        "InviteDto",
                        "Requires auth (invitee)",
                        "Join a project you were invited to"),

                new ToolSpec("withdrawInvite",
                        "Withdraw a pending invite (inviter/owner only)",
                        Map.of("inviteId", "Long"),
                        "Map{id, status}",
                        "Requires auth (inviter)",
                        "Cancel an invite"),

                new ToolSpec("getMyInvites",
                        "Pending invites for current user",
                        Map.of("status", "String PENDING|ACCEPTED|REJECTED (optional)"),
                        "List<InviteDto>",
                        "Requires auth",
                        "Invite inbox"),

                // ── M2: Teammate Request (gap-driven) ──────────────────────

                new ToolSpec("sendTeammateRequest",
                        "Send a teammate request to a candidate who fills a project's skill gaps (member/owner only, dedup pending). Optionally include a message.",
                        Map.of("projectId", "Long", "candidateId", "Long", "message", "String (optional, max 1000)"),
                        "RequestDto{id, projectId, projectName, requesterId, requesterName, candidateId, candidateName, status, matchedSkills, createdAt}",
                        "Requires auth + project membership",
                        "User picks a suggested candidate and requests them"),

                new ToolSpec("respondToTeammateRequest",
                        "Accept or reject an incoming teammate request (candidate only; accept auto-adds as ProjectMember)",
                        Map.of("requestId", "Long", "action", "String ACCEPT|REJECT"),
                        "RequestDto",
                        "Requires auth (candidate)",
                        "Join a project via teammate request"),

                new ToolSpec("withdrawTeammateRequest",
                        "Withdraw a pending teammate request (requester/owner only)",
                        Map.of("requestId", "Long"),
                        "RequestDto",
                        "Requires auth (requester)",
                        "Cancel a teammate request"),

                new ToolSpec("getMyTeammateRequests",
                        "Incoming teammate requests for the current user",
                        Map.of("status", "String PENDING|ACCEPTED|REJECTED|WITHDRAWN (optional)"),
                        "List<RequestDto>",
                        "Requires auth",
                        "Teammate request inbox"),

                new ToolSpec("getSentTeammateRequests",
                        "Teammate requests sent by the current user",
                        Map.of(),
                        "List<RequestDto>",
                        "Requires auth",
                        "Sent requests"),

                new ToolSpec("getProjectTeammateRequests",
                        "All teammate requests for a project (owner/member only)",
                        Map.of("projectId", "Long", "status", "String (optional)"),
                        "List<RequestDto>",
                        "Requires auth + project membership",
                        "Project request inbox"),

                new ToolSpec("suggestTeammatesForProject",
                        "Gap analysis + ranked candidates for a project (owner/member only)",
                        Map.of("projectId", "Long"),
                        "TeammateSuggestions{projectName, gaps[], candidates[{studentId, name, matchedSkills, score}]}",
                        "Requires auth + project membership",
                        "User asks 'who should I add to this project'"),

                new ToolSpec("followUser",
                        "Follow a student (idempotent, creates FOLLOW notification)",
                        Map.of("followingId", "Long"),
                        "Map{followerId, followingId, status, followingCount, followerCount}",
                        "Requires auth (X-Demo-User)",
                        "Connect with someone"),

                new ToolSpec("unfollowUser",
                        "Unfollow a student",
                        Map.of("followingId", "Long"),
                        "Map{followerId, followingId, status, followingCount, followerCount}",
                        "Requires auth",
                        "Remove connection"),

                new ToolSpec("getFollowing",
                        "Students the current user follows",
                        Map.of(),
                        "List<PeerDto>",
                        "Requires auth",
                        "Following list"),

                new ToolSpec("getFollowers",
                        "Students who follow the current user",
                        Map.of(),
                        "List<PeerDto>",
                        "Requires auth",
                        "Follower list"),

                new ToolSpec("getFollowStats",
                        "Follower and following counts for a student",
                        Map.of("studentId", "Long (optional, defaults to auth)"),
                        "Map{studentId, followingCount, followerCount}",
                        "Requires auth or public with id",
                        "Connection counts"),

                new ToolSpec("isFollowing",
                        "Check if current user follows another",
                        Map.of("followingId", "Long"),
                        "Map{followerId, followingId, following}",
                        "Requires auth",
                        "Follow status check"),

                new ToolSpec("endorseSkill",
                        "Endorse a student's skill (canonical-normalized, idempotent)",
                        Map.of("endorseeId", "Long", "skill", "string"),
                        "EndorsementDto{id, endorserId, endorserName, endorseeId, endorseeName, skill, createdAt}",
                        "Requires auth (X-Demo-User as endorser)",
                        "Vouch for a peer's skill"),

                new ToolSpec("withdrawEndorsement",
                        "Withdraw an endorsement you gave",
                        Map.of("endorseeId", "Long", "skill", "string"),
                        "Map{endorserId, endorseeId, skill, status}",
                        "Requires auth (endorser)",
                        "Retract an endorsement"),

                new ToolSpec("getEndorsementsForStudent",
                        "All endorsements for a student, optionally filtered by skill",
                        Map.of("endorseeId", "Long", "skill", "string (optional)"),
                        "List<EndorsementDto>",
                        "Public",
                        "View trust signals for a profile"),

                new ToolSpec("getEndorsementSummary",
                        "Grouped endorsement counts by skill for a student",
                        Map.of("endorseeId", "Long"),
                        "Map{endorseeId, totalEndorsements, bySkill}",
                        "Public",
                        "Skill credibility summary"),

                new ToolSpec("createReport",
                        "File a moderation report against a post/project/student/event",
                        Map.of("targetType", "String POST|PROJECT|STUDENT|EVENT", "targetId", "Long", "reason", "String SPAM|HARASSMENT|INAPPROPRIATE|PLAGIARISM|OTHER", "details", "String (optional)"),
                        "ReportDto{id, reporterId, reporterName, targetType, targetId, reason, details, status, resolution, createdAt, resolvedAt}",
                        "Requires auth (X-Demo-User)",
                        "Flag inappropriate content"),

                new ToolSpec("listReports",
                        "Moderation queue filtered by status",
                        Map.of("status", "String OPEN|RESOLVED|DISMISSED (default OPEN)"),
                        "List<ReportDto>",
                        "Public (admin view)",
                        "Review reports"),

                new ToolSpec("getMyReports",
                        "Reports filed by current user",
                        Map.of(),
                        "List<ReportDto>",
                        "Requires auth",
                        "My reports"),

                new ToolSpec("getReportsForTarget",
                        "Reports for a specific target",
                        Map.of("targetType", "String", "targetId", "Long"),
                        "List<ReportDto>",
                        "Public",
                        "Target report history"),

                new ToolSpec("requestMentorship",
                        "Request mentorship from an alumni (idempotent pending, UNAVAILABLE blocked)",
                        Map.of("alumniId", "Long", "message", "String (optional, max 2000)"),
                        "MentorshipDto{id, studentId, studentName, alumniId, alumniName, status, message, createdAt}",
                        "Requires auth (X-Demo-User)",
                        "Career / Learn & Get Guidance"),

                new ToolSpec("respondMentorship",
                        "Accept/reject/withdraw own mentorship request (pending only)",
                        Map.of("requestId", "Long (path)", "action", "String ACCEPT|REJECT|CANCEL"),
                        "MentorshipDto",
                        "Requires auth — owner only",
                        "Manage my requests"),

                new ToolSpec("alumniRespondMentorship",
                        "Alumni-side accept/reject for demo (alumniId in body)",
                        Map.of("requestId", "Long (path)", "alumniId", "Long", "action", "String ACCEPT|REJECT"),
                        "MentorshipDto",
                        "Demo — alumni view",
                        "Simulate alumni decision"),

                new ToolSpec("getMyMentorshipRequests",
                        "List my mentorship requests filtered by status",
                        Map.of("status", "String PENDING|ACCEPTED|REJECTED (optional)"),
                        "List<MentorshipDto>",
                        "Requires auth",
                        "My mentorship queue"),

                new ToolSpec("getMentorshipForAlumni",
                        "Requests for a given alumni filtered by status",
                        Map.of("alumniId", "Long", "status", "String (optional)"),
                        "List<MentorshipDto>",
                        "Public",
                        "Alumni queue"),

                new ToolSpec("requestReferral",
                        "Request a job referral from an alumni (idempotent pending, REFERRALS/BOTH availability only)",
                        Map.of("alumniId", "Long", "message", "String (optional, max 2000)", "company", "String (optional)", "role", "String (optional)"),
                        "ReferralDto{id, studentId, studentName, alumniId, alumniName, status, message, company, role, createdAt}",
                        "Requires auth (X-Demo-User)",
                        "Career / referrals"),

                new ToolSpec("respondReferral",
                        "Accept/reject/withdraw own referral request (pending only)",
                        Map.of("requestId", "Long (path)", "action", "String ACCEPT|REJECT|CANCEL"),
                        "ReferralDto",
                        "Requires auth — owner only",
                        "Manage my referrals"),

                new ToolSpec("alumniRespondReferral",
                        "Alumni-side accept/reject for demo (alumniId in body)",
                        Map.of("requestId", "Long (path)", "alumniId", "Long", "action", "String ACCEPT|REJECT"),
                        "ReferralDto",
                        "Demo — alumni view",
                        "Simulate alumni decision"),

                new ToolSpec("getMyReferralRequests",
                        "List my referral requests filtered by status",
                        Map.of("status", "String PENDING|ACCEPTED|REJECTED (optional)"),
                        "List<ReferralDto>",
                        "Requires auth",
                        "My referral queue"),

                new ToolSpec("getReferralsForAlumni",
                        "Referral requests for a given alumni filtered by status",
                        Map.of("alumniId", "Long", "status", "String (optional)"),
                        "List<ReferralDto>",
                        "Public",
                        "Alumni queue"),

                new ToolSpec("resolveReport",
                        "Resolve (hides/suspends target) or dismiss a report — admin-only (campus.admin-ids)",
                        Map.of("reportId", "Long (path)", "action", "String RESOLVED|DISMISSED", "resolution", "String (optional) + header X-Demo-User (admin)"),
                        "ReportDto",
                        "Admin/moderator",
                        "Close a report"),

                new ToolSpec("getReportStats",
                        "Counts by status for reports",
                        Map.of(),
                        "Map{open, resolved, dismissed, total}",
                        "Public",
                        "Moderation dashboard"),

                new ToolSpec("getActivityFeed",
                        "Unified timeline: posts, invites, RSVPs, follows, bookmarks, endorsements, notifications — sorted by time",
                        Map.of("limit", "int (default 20)", "filter", "String ALL|POSTS|INVITES|RSVPS|FOLLOWS|BOOKMARKS|ENDORSEMENTS|NOTIFICATIONS (optional)"),
                        "List<Map{type, id, title, preview?, at, refType, refId, status?}>",
                        "Requires auth (X-Demo-User)",
                        "Home feed / activity timeline"),

                new ToolSpec("getMyProfile",
                        "Aggregated profile header: identity, connections, projects, invites, bookmarks, endorsements, reputation, RSVPs, posts, unread counts",
                        Map.of(),
                        "Map{profile, connections, projects{count,items}, pendingInvites, bookmarks{total,byType}, endorsements{totalEndorsements,bySkill}, reputation{reviewCount,averageRating}, rsvps{count,items}, posts{count}, unreadNotifications, unreadMessages}",
                        "Requires auth (X-Demo-User)",
                        "Profile header / who am I"),

                new ToolSpec("getChatHistory",
                        "Recent /ask turns for current user (newest first)",
                        Map.of("limit", "int (default 20, max 50)"),
                        "List<ChatTurnDto{id, question, answer, answered, createdAt}>",
                        "Requires auth (X-Demo-User)",
                        "Conversation history / recall"),

                new ToolSpec("getChatHistoryCount",
                        "Count of stored chat turns for current user",
                        Map.of(),
                        "Map{count}",
                        "Requires auth",
                        "History badge"),

                new ToolSpec("clearChatHistory",
                        "Delete all stored chat turns for current user",
                        Map.of(),
                        "Map{cleared}",
                        "Requires auth",
                        "Clear history / privacy"),

                new ToolSpec("getProfileCompleteness",
                        "Score 0-100 + missing fields for completeness",
                        Map.of(),
                        "Map{studentId, score, level, missingFields, suggestions}",
                        "Requires auth (X-Demo-User)",
                        "Profile completeness badge"),

                new ToolSpec("getSkillRecommendations",
                        "Top N skill suggestions based on owned skills co-occurrence + trending fallback",
                        Map.of("limit", "int (default 5, max 20)"),
                        "Map{studentId, ownedSkills, recommendations[{skill, category, reason, score?}]}",
                        "Requires auth (X-Demo-User)",
                        "Learn next / skill gaps"),

                new ToolSpec("getPublicSkillRecommendations",
                        "Skill suggestions for any student (public view)",
                        Map.of("studentId", "Long", "limit", "int (default 5)"),
                        "Map{studentId, ownedSkills, recommendations}",
                        "Public",
                        "Suggest for someone else"),

                new ToolSpec("getPublicProfile",
                        "Public profile for any student: identity + connections + reputation + endorsements",
                        Map.of("studentId", "Long"),
                        "Map{id, name, branch, year, skills, interests, bio, githubUrl, linkedinUrl, availability, connections, reputation, endorsements}",
                        "Public",
                        "View someone else's profile"),

                new ToolSpec("toggleBookmark",
                        "Toggle save/remove for a post/event/project (idempotent)",
                        Map.of("refType", "String POST|EVENT|PROJECT", "refId", "Long"),
                        "Map{status, bookmark?|refType, refId}",
                        "Requires auth (X-Demo-User)",
                        "Save for later or unsave"),

                new ToolSpec("saveBookmark",
                        "Save a post/event/project",
                        Map.of("refType", "String POST|EVENT|PROJECT", "refId", "Long"),
                        "Map{status, bookmark}",
                        "Requires auth",
                        "Bookmark an item"),

                new ToolSpec("removeBookmark",
                        "Remove a saved bookmark",
                        Map.of("refType", "String POST|EVENT|PROJECT", "refId", "Long"),
                        "Map{status, refType, refId}",
                        "Requires auth",
                        "Unsave an item"),

                new ToolSpec("getBookmarks",
                        "List saved bookmarks, optionally filtered by type",
                        Map.of("refType", "String POST|EVENT|PROJECT (optional)"),
                        "List<BookmarkDto{id, refType, refId, createdAt}>",
                        "Requires auth",
                        "My saved items"),

                new ToolSpec("isBookmarked",
                        "Check if an item is saved",
                        Map.of("refType", "String POST|EVENT|PROJECT", "refId", "Long"),
                        "Map{studentId, refType, refId, saved}",
                        "Requires auth",
                        "Saved badge"),

                new ToolSpec("getEndorsementsGiven",
                        "Endorsements the current user has given",
                        Map.of(),
                        "List<EndorsementDto>",
                        "Requires auth",
                        "My endorsements"),

                new ToolSpec("getProjectInvites",
                        "Invites for a project filtered by status",
                        Map.of("projectId", "Long", "status", "String (default PENDING)"),
                        "List<InviteDto>",
                        "Public",
                        "See who was invited"),

                new ToolSpec("getMyEventRsvps",
                        "Events the current user has joined",
                        Map.of(),
                        "List<EventDto>",
                        "Requires auth",
                        "My RSVPs / my events"),

                new ToolSpec("createRecruiterPosting",
                        "Create a recruiter posting (title, description, requiredSkills)",
                        Map.of("title", "String 3-150", "description", "String (optional, max 3000)", "requiredSkills", "String CSV (optional)"),
                        "PostingDto{id, recruiterId, recruiterName, company, title, requiredSkills, status, applicantCount}",
                        "Requires auth (X-Demo-User as recruiterId)",
                        "Recruiter creates a job opening"),

                new ToolSpec("listRecruiterPostings",
                        "List recruiter postings, optionally filtered by status or recruiterId",
                        Map.of("status", "String OPEN|CLOSED (optional)", "recruiterId", "Long (optional)"),
                        "List<PostingDto>",
                        "Public",
                        "Browse openings"),

                new ToolSpec("getRecruiterPosting",
                        "Get a single posting by id",
                        Map.of("postingId", "Long"),
                        "PostingDto",
                        "Public",
                        "Posting detail"),

                new ToolSpec("closeRecruiterPosting",
                        "Close a posting owned by the recruiter",
                        Map.of("postingId", "Long"),
                        "PostingDto with status CLOSED",
                        "Requires auth (owner recruiter)",
                        "Close hiring"),

                new ToolSpec("applyToPosting",
                        "Apply to an open posting as the current student",
                        Map.of("postingId", "Long"),
                        "ApplicationDto{id, postingId, studentId, status}",
                        "Requires auth (X-Demo-User as studentId)",
                        "Student applies"),

                new ToolSpec("withdrawApplication",
                        "Withdraw own APPLIED application",
                        Map.of("applicationId", "Long"),
                        "Map{id, status=withdrawn}",
                        "Requires auth (owner student, APPLIED only)",
                        "Cancel application"),

                new ToolSpec("decideApplication",
                        "Shortlist or reject an application for a posting owned by the recruiter",
                        Map.of("postingId", "Long", "applicationId", "Long", "action", "String SHORTLIST|REJECT"),
                        "ApplicationDto with status SHORTLISTED|REJECTED",
                        "Requires auth (owner recruiter, APPLIED only)",
                        "Recruiter decision + notifies student"),

                new ToolSpec("getApplicationsForPosting",
                        "List applications for a posting owned by the recruiter, optionally by status",
                        Map.of("postingId", "Long", "status", "String APPLIED|SHORTLISTED|REJECTED (optional)"),
                        "List<ApplicationDto>",
                        "Requires auth (owner recruiter)",
                        "Recruiter inbox"),

                new ToolSpec("getMyApplications",
                        "List own applications as student, optionally filtered by status",
                        Map.of("status", "String APPLIED|SHORTLISTED|REJECTED (optional)"),
                        "List<ApplicationDto>",
                        "Requires auth (X-Demo-User as studentId)",
                        "My applications"),

                new ToolSpec("markAllNotificationsRead",

                        "Mark all notifications as read",
                        Map.of(),
                        "Map{markedRead}",
                        "Requires auth",
                        "Clear all alerts")
        );
    }

    public record ToolSpec(
            String name,
            String purpose,
            Map<String, String> inputs,
            String outputType,
            String permissions,
            String whenToUse
    ) {}
}