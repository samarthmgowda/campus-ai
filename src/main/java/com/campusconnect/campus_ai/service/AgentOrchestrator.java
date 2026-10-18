package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 3 - AI-Agent Execution Loop.
 *
 * Loop: intent -> parse -> execute controlled tools -> gather -> refine -> reply.
 * Each step maps to an existing backend service (no LLM-to-DB). The agent
 * can break complex intents into multi-step plans.
 *
 * Example: "Find me teammates for my project" ->
 *   1. getMyProjects(userId) -> [Project1]
 *   2. getSkillGaps(Project1.id) -> [React, AWS]
 *   3. searchStudents([React, AWS], availableOnly=true) -> [StudentA, StudentB]
 *   4. Return ranked list + suggestion to send requests
 *
 * Example: "Send a teammate request to John for the project" ->
 *   1. Find project -> 2. Find John by name -> 3. sendTeammateRequest()
 */
@Service
public class AgentOrchestrator {

    private final CurrentUserService currentUserService;
    private final ProjectToolService projectToolService;
    private final ProjectInviteService projectInviteService;
    private final TeammateRequestService teammateRequestService;
    private final EventAttendanceService eventAttendanceService;
    private final EventService eventService;
    private final StudentRepository studentRepository;
    private final AlumniRepository alumniRepository;
    private final SearchService searchService;
    private final FollowService followService;
    private final MentorshipService mentorshipService;
    private final RecruiterPostingService recruiterPostingService;
    private final NotificationService notificationService;
    private final BookmarkService bookmarkService;
    private final ModerationService moderationService;
    private final DocumentService documentService;
    private final SkillGraphService skillGraphService;
    private final AskService askService;

    public AgentOrchestrator(
            CurrentUserService currentUserService,
            ProjectToolService projectToolService,
            ProjectInviteService projectInviteService,
            TeammateRequestService teammateRequestService,
            EventAttendanceService eventAttendanceService,
            EventService eventService,
            StudentRepository studentRepository,
            AlumniRepository alumniRepository,
            SearchService searchService,
            FollowService followService,
            MentorshipService mentorshipService,
            RecruiterPostingService recruiterPostingService,
            NotificationService notificationService,
            BookmarkService bookmarkService,
            ModerationService moderationService,
            DocumentService documentService,
            SkillGraphService skillGraphService,
            AskService askService) {
        this.currentUserService = currentUserService;
        this.projectToolService = projectToolService;
        this.projectInviteService = projectInviteService;
        this.teammateRequestService = teammateRequestService;
        this.eventAttendanceService = eventAttendanceService;
        this.eventService = eventService;
        this.studentRepository = studentRepository;
        this.alumniRepository = alumniRepository;
        this.searchService = searchService;
        this.followService = followService;
        this.mentorshipService = mentorshipService;
        this.recruiterPostingService = recruiterPostingService;
        this.notificationService = notificationService;
        this.bookmarkService = bookmarkService;
        this.moderationService = moderationService;
        this.documentService = documentService;
        this.skillGraphService = skillGraphService;
        this.askService = askService;
    }

    // ---- Public entry point ----
    // processIntent: main agent loop. Receives a natural-language intent and a userId
    // (may be null for unauthenticated RAG lookups). Returns a structured AgentResponse.
    // The method decomposes the intent into a sequence of tool calls rather than
    // calling the LLM directly for data access.

    public AgentResponse processIntent(String intent, Long userId) {
        if (intent == null || intent.isBlank()) {
            return AgentResponse.clarification("Could you rephrase your request?");
        }
        String q = intent.trim().toLowerCase(Locale.ROOT);

        try {
            // 0. Profile completeness check (Priority)
            if (q.contains("complete") && q.contains("profile")) {
                if (userId == null) return AgentResponse.error("User not identified", "Please ensure you are logged in.");
                currentUserService.requireUser(userId);
                var student = studentRepository.findById(userId).orElseThrow();

                int score = 0;
                int total = 4; // name, skills, branch, year
                if (student.getName() != null && !student.getName().isBlank()) score++;
                if (student.getSkills() != null && !student.getSkills().isBlank()) score++;
                if (student.getBranch() != null && !student.getBranch().isBlank()) score++;
                if (student.getYear() > 0) score++;

                double percent = (score / (double)total) * 100;
                // Use AgentResponse.success and explicitly mark as answered to prevent RAG fallthrough
                return new AgentResponse("success",
                    String.format("Your profile is %.0f%% complete. You have provided %d of %d basic details.", percent, score, total),
                    Map.of("completeness", percent, "score", score, "total", total),
                    null);
            }

            // 1. Project teammate workflow
            Optional<AgentResponse> r = tryTeammate(intent, q, userId);
            if (r.isPresent()) return r.get();

            // 2. Project creation
            r = tryCreateProject(intent, q, userId);
            if (r.isPresent()) return r.get();

            // 3. Event workflow
            r = tryEvent(intent, q, userId);
            if (r.isPresent()) return r.get();

            // 4. Follow / connections
            r = tryFollow(intent, q, userId);
            if (r.isPresent()) return r.get();

            // 5. Mentorship
            r = tryMentorship(intent, q, userId);
            if (r.isPresent()) return r.get();

            // 6. Recruiter posting / job application
            r = tryRecruiter(intent, q, userId);
            if (r.isPresent()) return r.get();

            // 7. Moderation / reports
            r = tryModeration(intent, q, userId);
            if (r.isPresent()) return r.get();

            // 8. Document / search
            r = tryDocumentSearch(intent, q, userId);
            if (r.isPresent()) return r.get();

            // 9. Notifications
            r = tryNotifications(intent, q, userId);
            if (r.isPresent()) return r.get();

            // 10. Skill graph
            r = trySkillGraph(intent, q, userId);
            if (r.isPresent()) return r.get();

            // 11. Bookmarks
            r = tryBookmark(intent, q, userId);
            if (r.isPresent()) return r.get();

        } catch (ResponseStatusException e) {
            return AgentResponse.error("Action failed", e.getReason() != null ? e.getReason() : e.getMessage());
        } catch (IllegalArgumentException e) {
            return AgentResponse.error("Invalid request", e.getMessage());
        } catch (Exception e) {
            return AgentResponse.error("Something went wrong", e.getMessage());
        }

        // Fall back to RAG / keyword search
        return tryRagFallback(intent, userId);
    }


    // ---- Intent classification: Teammate workflow ----
    // Handles: "find teammate", "search for teammates", "who has React skills",
    // "request teammate <name> for <project>"

    private Optional<AgentResponse> tryTeammate(String intent, String q, Long userId) {
        if (userId == null) return Optional.empty();

        // "find teammate" / "search teammate" -> show gaps + candidates
        if (q.contains("find teammate") || q.contains("search for teammate")
                || q.contains("search teammate") || q.contains("find teammates")) {
            currentUserService.requireUser(userId);
            var myProjects = projectToolService.getMyProjects(userId);
            if (myProjects.isEmpty()) {
                return Optional.of(AgentResponse.info(
                        "You are not a member of any active projects yet.",
                        Map.of("suggestions", List.of(
                                "Create one: POST /projects {name, requiredSkills} with X-Demo-User",
                                "Browse all: GET /projects/public"))));
            }
            StringBuilder sb = new StringBuilder("Searching for teammates for your projects...\n\n");
            for (var ps : myProjects) {
                var gaps = projectToolService.getSkillGaps(ps.id());
                if (gaps.isEmpty()) {
                    sb.append(ps.name()).append(": no skill gaps found.\n");
                    continue;
                }
                var suggestion = projectToolService.findTeammates(userId, ps.id());
                if (suggestion.candidates().isEmpty()) {
                    sb.append(ps.name()).append(": skill gaps [").append(String.join(", ", gaps))
                            .append("] but no available candidates matched.\n");
                } else {
                    sb.append(ps.name()).append(": skill gaps [")
                            .append(String.join(", ", suggestion.gaps())).append("]\nCandidates:\n");
                    for (var c : suggestion.candidates()) {
                        sb.append("  - " + c.name() + " (" + c.branch() + ", Y" + c.year() + ") - "
                                + c.skills() + " | matched: " + String.join(", ", c.matchedSkills()) + "\n");
                    }
                    sb.append("Reply \"request teammate <name> for <project name>\" to send a request.\n");
                }
            }
            return Optional.of(AgentResponse.success(sb.toString(), null));
        }

        // "request teammate <name> for <project>" or "request <name> for <project>"
        if (q.contains("request teammate") || (q.contains("request") && q.contains("for"))) {
            var result = parseTeammateRequest(intent, userId);
            if (result.isPresent()) return result;
        }

        // "who knows <skill>" -> search students by skill
        String skill = findSkillInQuestion(q);
        boolean hasStudentWord = containsAny(q, "student", "students", "someone", "who", "teammate", "find");
        if (skill != null && hasStudentWord && !q.contains("find teammate")) {
            currentUserService.requireUser(userId);
            var candidates = projectToolService.searchStudents(
                    List.of(skill), true, Set.of(userId));
            if (candidates.isEmpty()) {
                return Optional.of(AgentResponse.info(
                        "No available students found with '" + skill + "'.",
                        Map.of("skill", skill)));
            }
            StringBuilder sb = new StringBuilder("Students with '" + skill + "':\n");
            for (var c : candidates) {
                sb.append("  - " + c.name() + " (" + c.branch() + ", Y" + c.year() + ") - "
                        + c.skills() + " | availability: " + c.availability() + "\n");
            }
            sb.append("Send them a teammate request: POST /projects/{projectId}/teammate-request {candidateId}");
            return Optional.of(AgentResponse.success(sb.toString(), null));
        }

        // "give me a teammate" -> treat as find teammates
        if (q.contains("teammate") && !q.contains("request")) {
            return tryTeammate("find teammates", "find teammates", userId);
        }

        return Optional.empty();
    }


    // ---- Intent classification: Project creation ----
    // Handles: "create project", "new project", "I need a project for <topic>"
    private Optional<AgentResponse> tryCreateProject(String intent, String q, Long userId) {
        if (userId == null) return Optional.empty();

        if (q.contains("create project") || q.contains("new project")
                || (q.contains("create") && q.contains("project"))
                || q.contains("need a project")) {
            currentUserService.requireUser(userId);

            // Parse structured info from intent
            String name = extractQuotedAfter(intent, "called");
            if (name == null) name = extractQuotedAfter(intent, "project");
            if (name == null) name = extractQuotedAfter(intent, "named");

            if (name != null) {
                // Try to parse description, domain, skills from the intent
                String desc = extractQuotedAfter(intent, "for ");
                String domain = extractAfterKeyword(intent, "domain");
                String skills = extractSkillsFromIntent(intent);

                try {
                    var result = projectToolService.createProject(userId, name, desc, domain, skills);
                    return Optional.of(AgentResponse.success(
                            "Project created: " + result.name() + " (#" + result.id() + ")",
                            Map.of("projectId", result.id(),
                                    "name", result.name(),
                                    "domain", result.domain(),
                                    "requiredSkills", result.requiredSkills())));
                } catch (Exception e) {
                    return Optional.of(AgentResponse.error("Project creation failed", e.getMessage()));
                }
            }

            // No name parsed - ask for details
            return Optional.of(AgentResponse.clarification(
                    "To create a project, I need a name and optional details. " +
                    "You can create one directly: POST /projects with X-Demo-User header " +
                    "and {name, description, domain, requiredSkills}. " +
                    "Or tell me: \"create a project called <name> for <topic> needing <skills>\"."));
        }

        // "add member to <project>"
        if (q.contains("add") && q.contains("member")) {
            currentUserService.requireUser(userId);
            return Optional.of(AgentResponse.clarification(
                    "Add a teammate: POST /projects/{id}/members {studentId} with X-Demo-User, " +
                    "or tell me the name and I'll look them up."));
        }

        // "list my projects"
        if (q.contains("my project") || q.contains("my projects") || q.contains("show my project")) {
            currentUserService.requireUser(userId);
            var myProjects = projectToolService.getMyProjects(userId);
            if (myProjects.isEmpty()) {
                return Optional.of(AgentResponse.info(
                        "You are not a member of any active projects.", Map.of()));
            }
            StringBuilder sb = new StringBuilder("Your projects:\n");
            for (var ps : myProjects) {
                sb.append("  - " + ps.name() + " [" + ps.domain() + "] - " + ps.status()
                        + " - skills: " + ps.requiredSkills() + "\n");
            }
            return Optional.of(AgentResponse.success(sb.toString(), null));
        }

        return Optional.empty();
    }


    // ---- Intent classification: Event workflow ----
    // Handles: "create event", "upcoming events", "join event", "rsvp", "my events"
    private Optional<AgentResponse> tryEvent(String intent, String q, Long userId) {
        if (q.contains("create event") || q.contains("new event")
                || (q.contains("create") && q.contains("event"))) {
            if (userId == null) return Optional.empty();
            return Optional.of(AgentResponse.clarification(
                    "To create an event, provide: title, type (HACKATHON|WORKSHOP|SEMINAR|NETWORKING|PLACEMENT), " +
                    "date (YYYY-MM-DD), and optional description + location. " +
                    "POST /events with X-Demo-User header. Note: new events need admin approval."));
        }

        if (q.contains("upcoming") && containsAny(q, "event", "events", "workshop", "hackathon")) {
            var upcoming = eventService.search(null, true);
            if (upcoming.isEmpty()) {
                return Optional.of(AgentResponse.info(
                        "There are no upcoming events right now.", Map.of()));
            }
            StringBuilder sb = new StringBuilder("Upcoming events:\n");
            for (var e : upcoming) {
                sb.append("  - " + e.getTitle() + " (" + e.getType() + ") on "
                        + e.getEventDate() + " at " + e.getLocation() + "\n");
            }
            sb.append("\nRSVP: POST /events/{id}/join with X-Demo-User");
            return Optional.of(AgentResponse.success(sb.toString(), null));
        }

        if (userId != null && (q.contains("join event") || q.contains("rsvp") || q.contains("attend event") || q.contains("attend an event") || q.contains("attend the event"))) {
            Long eventId = extractNumberAfter(intent, "join event |rsvp for event |attend event ");
            if (eventId == null) {
                return Optional.of(AgentResponse.clarification(
                        "Which event? Reply with the event ID: \"join event 5\" or GET /events?upcoming=true"));
            }
            currentUserService.requireUser(userId);
            var result = eventAttendanceService.join(eventId, userId);
            return Optional.of(AgentResponse.success(
                    "You joined the event. Attendee count: " + result.get("attendeeCount"),
                    Map.of("eventId", eventId, "status", result.get("status"))));
        }

        if (userId != null && (q.contains("my event") || q.contains("rsvp'd") || q.contains("i joined"))) {
            currentUserService.requireUser(userId);
            var myEvents = eventAttendanceService.getMyEvents(userId);
            if (myEvents.isEmpty()) {
                return Optional.of(AgentResponse.info(
                        "You haven't RSVP'd to any events yet.", Map.of()));
            }
            StringBuilder sb = new StringBuilder("Your RSVPs:\n");
            for (var e : myEvents) {
                sb.append("  - " + e.title() + " (" + e.type() + ") on "
                        + e.eventDate() + " at " + e.location() + "\n");
            }
            return Optional.of(AgentResponse.success(sb.toString(), null));
        }

        return Optional.empty();
    }


    // ---- Intent classification: Follow / connections ----
    // Handles: "follow <name>", "who follows me", "my followers", "unfollow"
    private Optional<AgentResponse> tryFollow(String intent, String q, Long userId) {
        if (userId == null) return Optional.empty();

        if (q.contains("follow") || q.contains("following") || q.contains("follower") || q.contains("connect")) {
            currentUserService.requireUser(userId);

            if (q.contains("my follower") || q.contains("who follows") || q.contains("follower count")) {
                var stats = followService.getStats(userId);
                StringBuilder sb = new StringBuilder();
                sb.append("Your connections - following: ").append(stats.get("followingCount"))
                        .append(", followers: ").append(stats.get("followerCount"));
                var followers = followService.getFollowers(userId);
                if (!followers.isEmpty()) {
                    sb.append("\nFollowers:\n");
                    for (var f : followers) {
                        sb.append("  - ").append(f.name()).append(" (").append(f.branch()).append(")\n");
                    }
                }
                return Optional.of(AgentResponse.success(sb.toString(), null));
            }

            if (q.contains("my following") || q.contains("who am i following")) {
                var stats = followService.getStats(userId);
                StringBuilder sb = new StringBuilder();
                sb.append("Your connections - following: " + stats.get("followingCount")
                        + ", followers: " + stats.get("followerCount"));
                var following = followService.getFollowing(userId);
                if (!following.isEmpty()) {
                    sb.append("\nFollowing:\n");
                    for (var f : following) sb.append("  - " + f.name() + " (" + f.branch() + ")\n");
                }
                return Optional.of(AgentResponse.success(sb.toString(), null));
            }

            if (q.contains("unfollow")) {
                String name = findStudentNameInQuestion(intent);
                if (name != null) {
                    var target = findStudentByName(name);
                    if (target != null) {
                        followService.unfollow(userId, target.getId());
                        return Optional.of(AgentResponse.success("You unfollowed " + name + ".", null));
                    }
                    return Optional.of(AgentResponse.error(
                            "Could not find student", "No student named '" + name + "' found."));
                }
                return Optional.of(AgentResponse.clarification(
                        "Whom would you like to unfollow? Reply with their name."));
            }

            if (q.contains("follow")) {
                String name = findStudentNameInQuestion(intent);
                if (name != null) {
                    var target = findStudentByName(name);
                    if (target == null) {
                        return Optional.of(AgentResponse.error(
                                "Could not find student", "No student named '" + name + "' found."));
                    }
                    if (target.getId().equals(userId)) {
                        return Optional.of(AgentResponse.error(
                                "Cannot follow yourself", "You can't follow yourself."));
                    }
                    var result = followService.follow(userId, target.getId());
                    return Optional.of(AgentResponse.success(
                            result.get("status").equals("followed")
                                    ? "You are now following " + name + "."
                                    : "You already follow " + name + ".",
                            result));
                }
                return Optional.of(AgentResponse.clarification(
                        "Who would you like to follow? Reply with their name."));
            }
        }

        return Optional.empty();
    }


    // ---- Intent classification: Mentorship ----
    // Handles: "request mentor", "find alumni for mentorship", "mentorship requests"
    private Optional<AgentResponse> tryMentorship(String intent, String q, Long userId) {
        if (q.contains("mentorship") || q.contains("mentor") || q.contains("alumni")) {

            if (q.contains("my mentorship") || q.contains("mentorship request") || q.contains("mentor request")) {
                if (userId == null) return Optional.empty();
                currentUserService.requireUser(userId);
                var mine = mentorshipService.getMyRequests(userId, "PENDING");
                if (mine.isEmpty()) {
                    return Optional.of(AgentResponse.info(
                            "You have no pending mentorship requests.",
                            Map.of("action", "Request one: POST /mentorship/request {alumniId, message}")));
                }
                StringBuilder sb = new StringBuilder("Your pending mentorship requests:\n");
                for (var mr : mine) {
                    sb.append("  - alumni " + mr.alumniName() + " (id " + mr.alumniId()
                            + ") status: " + mr.status() + " (request " + mr.id() + ")\n");
                }
                sb.append("\nRespond: POST /mentorship/{id}/respond {action: ACCEPT|REJECT|CANCEL}");
                return Optional.of(AgentResponse.success(sb.toString(), null));
            }

            if (q.contains("request") && q.contains("mentor")) {
                if (userId == null) return Optional.empty();
                String name = findStudentNameInQuestion(intent);
                if (name != null) {
                    // Look up alumni by name
                    var alumniOpt = alumniRepository.findAll().stream()
                            .filter(a -> a.getName().equalsIgnoreCase(name))
                            .findFirst();
                    if (alumniOpt.isPresent()) {
                        var a = alumniOpt.get();
                        if ("UNAVAILABLE".equalsIgnoreCase(a.getAvailability())) {
                            return Optional.of(AgentResponse.error(
                                    "Alumni unavailable",
                                    a.getName() + " is not available for mentorship."));
                        }
                        return Optional.of(AgentResponse.clarification(
                                "I found " + name + " in alumni records. " +
                                "Send a mentorship request: POST /mentorship/request with X-Demo-User " +
                                "and {alumniId: " + a.getId() + ", message: \"your message\"}"));
                    }
                    return Optional.of(AgentResponse.error(
                            "Could not find alumni", "No alumni named '" + name + "' found."));
                }
                return Optional.of(AgentResponse.clarification(
                        "Which alum would you like to request as a mentor? " +
                        "Browse at GET /alumni or specify their name."));
            }

            if (q.contains("find") && q.contains("mentor")) {
                // Skill-based alumni lookup
                String skill = findSkillInQuestion(q);
                if (skill != null) {
                    var results = searchAlumniBySkill(skill);
                    if (results.isEmpty()) {
                        return Optional.of(AgentResponse.info(
                                "No alumni found with '" + skill + "'.", Map.of("skill", skill)));
                    }
                    StringBuilder sb = new StringBuilder("Alumni with '" + skill + "':\n");
                    for (var a : results) {
                        sb.append("  - " + a.getName() + " (" + a.getCurrentRole() + " at "
                                + a.getCurrentCompany() + ") - skills: " + a.getSkills() + "\n");
                    }
                    sb.append("\nRequest mentorship: POST /mentorship/request {alumniId, message}");
                    return Optional.of(AgentResponse.success(sb.toString(), null));
                }
            }

            // Generic alumni listing
            if (q.contains("alumni")) {
                var all = alumniRepository.findAll();
                if (all.isEmpty()) {
                    return Optional.of(AgentResponse.info(
                            "No alumni records available yet.", Map.of()));
                }
                StringBuilder sb = new StringBuilder("Alumni (" + all.size() + "):\n");
                for (var a : all) {
                    sb.append("  - " + a.getName() + " (" + a.getCurrentRole() + " at "
                            + a.getCurrentCompany() + ") - availability: " + a.getAvailability() + "\n");
                }
                sb.append("\nRequest mentorship: POST /mentorship/request {alumniId, message}");
                return Optional.of(AgentResponse.success(sb.toString(), null));
            }
        }

        return Optional.empty();
    }


    // ---- Intent classification: Recruiter / job workflow ----
    // Handles: "apply for job", "open positions", "my applications", "post a job"
    private Optional<AgentResponse> tryRecruiter(String intent, String q, Long userId) {
        if (q.contains("posting") || q.contains("job") || q.contains("hire") || q.contains("hiring")
                || q.contains("opening") || q.contains("candidate") || q.contains("talent")) {

            if (q.contains("my application") || q.contains("applied") || q.contains("my application")) {
                if (userId == null) return Optional.empty();
                currentUserService.requireUser(userId);
                var apps = recruiterPostingService.getMyApplications(userId, null);
                if (apps.isEmpty()) {
                    return Optional.of(AgentResponse.info(
                            "You have no applications yet.",
                            Map.of("action", "Apply: POST /recruiter/postings/{id}/apply")));
                }
                StringBuilder sb = new StringBuilder("Your applications:\n");
                for (var a : apps) {
                    sb.append("  - #" + a.id() + " " + a.postingTitle() + " - " + a.status() + "\n");
                }
                return Optional.of(AgentResponse.success(sb.toString(), null));
            }

            if ((q.contains("opening") || q.contains("job") || q.contains("posting"))
                    && !q.contains("my application") && !q.contains("apply")) {
                // List open postings
                var list = recruiterPostingService.listPostings("OPEN", null);
                if (list.isEmpty()) {
                    return Optional.of(AgentResponse.info(
                            "No open postings right now.", Map.of()));
                }
                StringBuilder sb = new StringBuilder("Open postings:\n");
                int show = Math.min(5, list.size());
                for (int i = 0; i < show; i++) {
                    var po = list.get(i);
                    sb.append("  - #" + po.id() + " " + po.title() + " @ "
                            + (po.company() != null ? po.company() : "?")
                            + " [" + (po.requiredSkills() != null ? po.requiredSkills() : "")
                            + "] (" + po.applicantCount() + " applicants)\n");
                }
                sb.append("\nApply: POST /recruiter/postings/{id}/apply with X-Demo-User");
                return Optional.of(AgentResponse.success(sb.toString(), null));
            }

            if (q.contains("apply") && q.contains("job")) {
                Long postingId = extractNumberAfter(intent, "apply |join ");
                if (postingId == null) {
                    return Optional.of(AgentResponse.clarification(
                            "Which posting would you like to apply for? " +
                            "GET /recruiter/postings to browse, then say \"apply to job 5\"."));
                }
                if (userId == null) return Optional.empty();
                currentUserService.requireUser(userId);
                try {
                    var app = recruiterPostingService.apply(userId, postingId);
                    return Optional.of(AgentResponse.success(
                            "You applied to " + app.postingTitle() + " (application #" + app.id() + ")",
                            Map.of("applicationId", app.id(), "status", app.status())));
                } catch (Exception e) {
                    return Optional.of(AgentResponse.error("Application failed", e.getMessage()));
                }
            }
        }

        return Optional.empty();
    }


    // ---- Intent classification: Moderation / reports ----
    // Handles: "report spam", "my reports", "moderation queue", "flag this"
    private Optional<AgentResponse> tryModeration(String intent, String q, Long userId) {
        if (q.contains("report") || q.contains("moderation") || q.contains("flagged") || q.contains("abuse")) {
            if (userId != null && (q.contains("my report") || q.contains("my flag")
                    || q.contains("what did i report") || q.contains("reports i made"))) {
                currentUserService.requireUser(userId);
                var mine = moderationService.myReports(userId);
                if (mine.isEmpty()) {
                    return Optional.of(AgentResponse.info(
                            "You have not filed any reports.",
                            Map.of("action", "POST /reports {targetType, targetId, reason}")));
                }
                StringBuilder sb = new StringBuilder("Your reports:\n");
                for (var r : mine) {
                    sb.append("  - " + r.targetType() + " " + r.targetId() + " - "
                            + r.reason() + " (" + r.status() + ")\n");
                }
                return Optional.of(AgentResponse.success(sb.toString(), null));
            }

            // Global moderation stats
            var stats = moderationService.stats();
            StringBuilder sb = new StringBuilder("Moderation queue - open: " + stats.get("open")
                    + ", resolved: " + stats.get("resolved") + ", dismissed: " + stats.get("dismissed")
                    + ", total: " + stats.get("total"));
            if (userId != null) {
                sb.append("\nFile a report: POST /reports {targetType, targetId, reason} with X-Demo-User");
            }
            return Optional.of(AgentResponse.success(sb.toString(), null));
        }

        return Optional.empty();
    }

    // ---- Intent classification: Document / search ----
    // Handles: "search for <query>", "what is <topic>", "documents about"
    private Optional<AgentResponse> tryDocumentSearch(String intent, String q, Long userId) {
        if (q.contains("document") || q.contains("upload") || q.contains("search for")
                || q.contains("what is") || q.contains("how to")) {

            // "my documents" / "my uploads"
            if (userId != null && (q.contains("my document") || q.contains("my upload"))) {
                currentUserService.requireUser(userId);
                var mine = documentService.getMyUploads(userId);
                if (mine.isEmpty()) {
                    return Optional.of(AgentResponse.info(
                            "You have no uploads yet. Upload via POST /documents/upload.", Map.of()));
                }
                StringBuilder sb = new StringBuilder("Your uploads (" + mine.size() + "):\n");
                for (var d : mine) {
                    sb.append("  - #" + d.getId() + " " + d.getTitle() + " (" + (d.getContent() != null ? d.getContent().length() : 0) + " chars)\n");
                }
                return Optional.of(AgentResponse.success(sb.toString(), null));
            }

            // "list documents" / "what documents"
            if (q.contains("list document") || q.contains("show document") || q.contains("what documents")) {
                var visible = documentService.getVisibleDocuments();
                if (visible.isEmpty()) {
                    return Optional.of(AgentResponse.info(
                            "No visible documents. Upload one via POST /documents/upload.", Map.of()));
                }
                StringBuilder sb = new StringBuilder("Documents (" + visible.size() + "):\n");
                for (int i = 0; i < Math.min(5, visible.size()); i++) {
                    var d = visible.get(i);
                    String snippet = d.getContent() != null
                            ? d.getContent().substring(0, Math.min(80, d.getContent().length())).replace("\n", " ")
                            : "";
                    sb.append("  - #" + d.getId() + " " + d.getTitle() + " - \"" + snippet + "...\"\n");
                }
                sb.append("\nUpload: POST /documents/upload (multipart .txt/.md, 5MB)");
                return Optional.of(AgentResponse.success(sb.toString(), null));
            }

            // Generic knowledge query - delegate to RAG
            // Let the fallthrough handle it
            if (!q.contains("document")) {
                return Optional.empty();
            }

            return Optional.of(AgentResponse.info(
                    "No specific document request matched. Try: \"what is X\", " +
                    "\"search for Y\", or GET /documents for the full list.", Map.of()));
        }

        return Optional.empty();
    }


    // ---- Intent classification: Notifications ----
    // Handles: "notifications", "unread messages", "alerts"
    private Optional<AgentResponse> tryNotifications(String intent, String q, Long userId) {
        if (q.contains("notification") || q.contains("notif") || q.contains("alert")
                || (q.contains("unread") && (q.contains("message") || q.contains("msg")))) {
            if (userId == null) return Optional.empty();
            currentUserService.requireUser(userId);

            long unread = notificationService.getUnreadCount(userId);
            var recent = notificationService.getForUser(userId, false, 5);

            StringBuilder sb = new StringBuilder();
            if (unread == 0) {
                sb.append("You have no unread notifications.");
            } else {
                sb.append("You have ").append(unread).append(" unread notification(s).");
            }
            if (!recent.isEmpty()) {
                sb.append("\nRecent:\n");
                for (var n : recent) {
                    sb.append("  - [" + n.type() + "] " + n.title()
                            + (n.readFlag() ? " (read)" : " (unread)") + "\n");
                }
            }
            sb.append("\nFull list: GET /notifications?unreadOnly=true with X-Demo-User");
            return Optional.of(AgentResponse.success(sb.toString(), null));
        }

        return Optional.empty();
    }

    // ---- Intent classification: Skill graph ----
    // Handles: "what skills match <topic>", "skill glossary", "related skills"
    private Optional<AgentResponse> trySkillGraph(String intent, String q, Long userId) {
        boolean isGlossaryQuery = q.contains("glossary") || q.contains("alias for")
                || (q.contains("what is") && q.contains("skill"))
                || q.contains("skill meaning") || q.contains("skill info");

        if (isGlossaryQuery) {
            String skill = findSkillInQuestion(q);
            if (skill == null) {
                // Try last meaningful token
                String[] tokens = q.split("[\\s,]+");
                for (int i = tokens.length - 1; i >= 0; i--) {
                    String tok = tokens[i].replaceAll("[^a-z0-9#+\\-.]", "");
                    if (tok.length() >= 2 && !Set.of("skill", "glossary", "what", "does", "is", "the", "meaning", "info", "for", "alias", "related").contains(tok)) {
                        skill = tok;
                        break;
                    }
                }
            }
            if (skill != null) {
                currentUserService.requireUser(userId);
                var g = skillGraphService.getGlossary(skill);
                StringBuilder sb = new StringBuilder();
                sb.append(g.get("canonical")).append(" [").append(g.get("category")).append("]");
                var aliases = (List<?>) g.get("aliases");
                if (aliases != null && !aliases.isEmpty()) {
                    sb.append("\nAliases: ").append(String.join(", ", aliases.stream().map(Object::toString).toList()));
                }
                sb.append("\nProfiles: ").append(g.get("profileCount"))
                        .append(" (students ").append(g.get("studentCount"))
                        .append(", alumni ").append(g.get("alumniCount"));
                var related = (List<?>) g.get("related");
                if (related != null && !related.isEmpty()) {
                    sb.append("\nRelated: ");
                    for (int i = 0; i < Math.min(3, related.size()); i++) {
                        var m = (Map<?, ?>) related.get(i);
                        sb.append(m.get("skill")).append(" (").append(m.get("count")).append("), ");
                    }
                }
                return Optional.of(AgentResponse.success(sb.toString(), null));
            }
        }

        // "skill graph dashboard"
        if (q.contains("skill graph") || q.contains("skill dashboard") || q.contains("top skills")) {
            var dash = skillGraphService.getDashboardData(userId);
            var topSkills = (List<?>) dash.get("topSkills");
            var toGrow = (List<?>) dash.get("skillsToGrow");
            var topRelated = (List<?>) dash.get("topRelated");
            var categoryDist = (Map<?, ?>) dash.get("categoryDistribution");
            StringBuilder sb = new StringBuilder("Top skills on campus:\n");
            for (int i = 0; i < Math.min(8, topSkills.size()); i++) {
                var entry = (Map<?, ?>) topSkills.get(i);
                sb.append("  - " + entry.get("skill") + " (" + entry.get("count") + " profiles)\n");
            }
            if (userId != null && toGrow != null && !toGrow.isEmpty()) {
                sb.append("\nSkills to grow for you:");
                for (Object o : toGrow) {
                    var entry = (Map<?, ?>) o;
                    sb.append(" ").append(entry.get("skill")).append(",");
                }
                sb.setLength(sb.length() - 1); // remove trailing comma
                sb.append("\n");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("topSkills", topSkills);
            data.put("skillsToGrow", toGrow);
            data.put("topRelated", topRelated);
            data.put("categoryDistribution", categoryDist);
            data.put("totalSkills", dash.get("totalSkills"));
            data.put("totalProfiles", dash.get("totalProfiles"));
            return Optional.of(AgentResponse.success(sb.toString(), data));
        }

        return Optional.empty();
    }


    // ---- Intent classification: Bookmarks ----
    // Handles: "save this", "bookmark", "my bookmarks", "unsave"
    private Optional<AgentResponse> tryBookmark(String intent, String q, Long userId) {
        if (userId == null) return Optional.empty();

        if (q.contains("my bookmark") || q.contains("saved")) {
            currentUserService.requireUser(userId);
            var list = bookmarkService.list(userId, null);
            if (list.isEmpty()) {
                return Optional.of(AgentResponse.info(
                        "You have no saved bookmarks yet.",
                        Map.of("action", "POST /bookmarks {refType, refId}")));
            }
            var summary = bookmarkService.saveSummary(userId);
            var byType = (Map<?, ?>) summary.get("byType");
            StringBuilder sb = new StringBuilder("Your bookmarks (" + summary.get("total") + "):\n");
            for (var e : byType.entrySet()) {
                sb.append("  - " + e.getKey() + ": " + e.getValue() + "\n");
            }
            for (int i = 0; i < Math.min(5, list.size()); i++) {
                var b = list.get(i);
                sb.append("  - #" + b.id() + " " + b.refType() + " " + b.refId() + "\n");
            }
            return Optional.of(AgentResponse.success(sb.toString(), null));
        }

        if (q.contains("bookmark") || q.contains("save")) {
            Long refId = extractNumberAfter(intent, "save |bookmark |project |event |post ");
            if (refId != null) {
                currentUserService.requireUser(userId);
                String refType = "PROJECT";
                if (q.contains("event")) refType = "EVENT";
                if (q.contains("post")) refType = "POST";
                try {
                    var result = bookmarkService.save(userId, refType, refId);
                    return Optional.of(AgentResponse.success(
                            result.get("status").equals("saved") ? "Saved!" : "Removed.",
                            result));
                } catch (Exception e) {
                    return Optional.of(AgentResponse.error("Could not save bookmark", e.getMessage()));
                }
            }
            return Optional.of(AgentResponse.clarification(
                    "Save a bookmark: POST /bookmarks {refType: PROJECT|EVENT|POST, refId} with X-Demo-User"));
        }

        return Optional.empty();
    }

    // ---- RAG fallback ----
    // Delegate to AskService for knowledge-base / document search
    private AgentResponse tryRagFallback(String intent, Long userId) {
        try {
            var answer = askService.ask(intent, userId);
            if (answer.answered()) {
                return AgentResponse.success(answer.answer(), Map.of("sources", answer.sources()));
            }
            return AgentResponse.info(answer.answer(), Map.of(
                    "sources", answer.sources(),
                    "hint", "Try GET /ask?q=... for a direct RAG answer."));
        } catch (IllegalStateException e) {
            return AgentResponse.error("RAG unavailable", e.getMessage());
        } catch (Exception e) {
            return AgentResponse.error("Search failed", e.getMessage());
        }
    }


    // ---- Intent classification: Recruiter / job workflow ----
    // (Placeholder - handled in tryRecruiter above)
    // The recruiter workflow is already dispatched above, no separate method needed.

    // ========================
    // ---- Helper methods ----
    // ========================

    // Parse "request teammate <name> for <project>"
    private Optional<AgentResponse> parseTeammateRequest(String intent, Long userId) {
        currentUserService.requireUser(userId);

        // Extract candidate name: everything between "request teammate" and "for"
        String candidateName = null;
        String projectName = null;
        String lower = intent.toLowerCase(Locale.ROOT);
        int reqIdx = lower.indexOf("request teammate");
        if (reqIdx < 0) reqIdx = lower.indexOf("request ");
        int forIdx = lower.indexOf(" for ", reqIdx >= 0 ? reqIdx : 0);

        if (reqIdx >= 0 && forIdx >= 0) {
            candidateName = intent.substring(reqIdx, forIdx).trim();
            // Strip leading keywords
            candidateName = candidateName.replaceFirst("(?i)request teammate\\s*", "")
                    .replaceFirst("(?i)request\\s*", "").trim();
            projectName = intent.substring(forIdx + 4).trim();
            projectName = projectName.replaceFirst("(?i)project\\s*", "").trim();
            if (projectName.endsWith(".")) projectName = projectName.substring(0, projectName.length() - 1).trim();
        } else if (reqIdx >= 0) {
            // Just a name without "for project"
            candidateName = intent.substring(reqIdx).trim();
            candidateName = candidateName.replaceFirst("(?i)request teammate\\s*", "")
                    .replaceFirst("(?i)request\\s*", "").trim();
        }

        if (candidateName == null || candidateName.isBlank()) {
            return Optional.of(AgentResponse.clarification(
                    "Specify who: \"request teammate <name> for <project>\""));
        }

        // Find the candidate student
        final String finalCandidateName = candidateName != null ? candidateName.trim() : "";
        var candidateOpt = studentRepository.findAll().stream()
                .filter(s -> s.getName() != null && s.getName().equalsIgnoreCase(finalCandidateName))
                .findFirst();
        if (candidateOpt.isEmpty()) {
            return Optional.of(AgentResponse.error(
                    "Candidate not found",
                    "No student named '" + candidateName + "' found."));
        }
        Student candidate = candidateOpt.get();

        // Find the project
        Long projectId = null;
        var myProjects = projectToolService.getMyProjects(userId);
        if (projectName != null && !projectName.isBlank()) {
            for (var ps : myProjects) {
                if (ps.name().equalsIgnoreCase(projectName.trim())) {
                    projectId = ps.id();
                    break;
                }
            }
            if (projectId == null) {
                return Optional.of(AgentResponse.error(
                        "Project not found",
                        "No project named '" + projectName + "' found among your projects."));
            }
        } else if (!myProjects.isEmpty()) {
            // Use first project with gaps
            for (var ps : myProjects) {
                if (!projectToolService.getSkillGaps(ps.id()).isEmpty()) {
                    projectId = ps.id();
                    break;
                }
            }
            if (projectId == null) projectId = myProjects.get(0).id();
        }

        if (projectId == null) {
            return Optional.of(AgentResponse.clarification(
                    "You have no projects. Create one first or specify \"for <project name>\"."));
        }

        // Compute which skills matched
        var gaps = projectToolService.getSkillGaps(projectId);
        var matched = gaps.stream()
                .filter(g -> {
                    String canon = skillGraphService.normalize(g);
                    if (canon == null) canon = g;
                    String wl = canon.toLowerCase(Locale.ROOT);
                    for (String part : candidate.getSkills().split(",")) {
                        String raw = part.trim();
                        if (raw.isEmpty()) continue;
                        String n = skillGraphService.normalize(raw);
                        if (n == null) n = raw;
                        if (n.toLowerCase(Locale.ROOT).equals(wl)) return true;
                    }
                    return false;
                })
                .collect(Collectors.toList());

        // Send the teammate request
        var dto = teammateRequestService.send(userId, projectId, candidate.getId(), null);

        StringBuilder sb = new StringBuilder();
        sb.append("Teammate request sent to ").append(candidate.getName())
                .append(" for project \"").append(dto.projectName()).append("\".");
        if (!matched.isEmpty()) {
            sb.append("\nMatched skills: ").append(String.join(", ", matched));
        }
        sb.append("\nThey can accept via POST /teammate-requests/{id}/respond {action: ACCEPT}");
        return Optional.of(AgentResponse.success(sb.toString(), Map.of(
                "requestId", dto.id(),
                "candidateName", candidate.getName(),
                "projectId", projectId)));
    }


    // ---- Student search helpers ----

    private Student findStudentByName(String name) {
        if (name == null || name.isBlank()) return null;
        String lc = name.toLowerCase(Locale.ROOT);
        for (Student s : studentRepository.findAll()) {
            if (s.getName() != null && s.getName().toLowerCase(Locale.ROOT).contains(lc)) {
                // Prefer exact match
                if (s.getName().equalsIgnoreCase(name.trim())) return s;
            }
        }
        // Fallback: return first partial match
        for (Student s : studentRepository.findAll()) {
            if (s.getName() != null && s.getName().toLowerCase(Locale.ROOT).contains(lc)) {
                return s;
            }
        }
        return null;
    }

    private String findStudentNameInQuestion(String q) {
        // Look for "to <Name>" or "for <Name>" or "with <Name>"
        String[] patterns = {"to ", "for ", "with ", "from "};
        String lower = q.toLowerCase(Locale.ROOT);
        for (String pat : patterns) {
            int idx = lower.indexOf(pat);
            if (idx >= 0) {
                String rest = q.substring(idx + pat.length()).trim();
                // Take first 1-3 words as the name
                String[] words = rest.split("\\s+");
                if (words.length >= 1) {
                    StringBuilder name = new StringBuilder(words[0]);
                    if (words.length > 1 && words[1].matches("^[A-Z][a-z]+$")) name.append(" ").append(words[1]);
                    if (words.length > 2 && words[2].matches("^[A-Z][a-z]+$") && name.length() < 30) name.append(" ").append(words[2]);
                    return name.toString();
                }
            }
        }
        return null;
    }

    private List<Alumni> searchAlumniBySkill(String skill) {
        if (skill == null || skill.isBlank()) return List.of();
        String canon = skillGraphService.normalize(skill);
        if (canon == null) canon = skill;
        String wantedLower = canon.toLowerCase(Locale.ROOT);

        return alumniRepository.findAll().stream()
                .filter(a -> {
                    if (a.getSkills() == null) return false;
                    for (String part : a.getSkills().split(",")) {
                        String raw = part.trim();
                        if (raw.isEmpty()) continue;
                        String n = skillGraphService.normalize(raw);
                        if (n == null) n = raw;
                        if (n.toLowerCase(Locale.ROOT).equals(wantedLower)) return true;
                    }
                    return false;
                })
                .filter(a -> "MENTORING".equalsIgnoreCase(a.getAvailability())
                        || "BOTH".equalsIgnoreCase(a.getAvailability()))
                .limit(5)
                .collect(Collectors.toList());
    }

    // ---- Term extraction helpers ----

    private String findSkillInQuestion(String q) {
        var canonical = new LinkedHashSet<>(skillGraphService.getAllCanonicalSkills());
        for (Student s : studentRepository.findAll()) {
            if (s.getSkills() != null) {
                for (String part : s.getSkills().split(",")) {
                    String t = part.trim();
                    if (t.isEmpty()) continue;
                    String n = skillGraphService.normalize(t);
                    canonical.add(n != null ? n : t);
                }
            }
        }
        for (Alumni a : alumniRepository.findAll()) {
            if (a.getSkills() != null) {
                for (String part : a.getSkills().split(",")) {
                    String t = part.trim();
                    if (t.isEmpty()) continue;
                    String n = skillGraphService.normalize(t);
                    canonical.add(n != null ? n : t);
                }
            }
        }
        for (String skill : canonical) {
            if (containsWholeWord(q, skill.toLowerCase(Locale.ROOT))) return skill;
        }
        // Fallback: token matching
        String[] tokens = q.split("[^a-z0-9#+\\-.]+");
        for (String tok : tokens) {
            if (tok.isBlank()) continue;
            String n = skillGraphService.normalize(tok);
            if (n != null && canonical.contains(n) && !n.equalsIgnoreCase(tok)) return n;
        }
        return null;
    }

    private static boolean containsAny(String haystack, String... needles) {
        for (String n : needles) if (haystack.contains(n)) return true;
        return false;
    }

    private static boolean containsWholeWord(String haystack, String needle) {
        int idx = haystack.indexOf(needle);
        while (idx != -1) {
            boolean leftOk = idx == 0 || !Character.isLetterOrDigit(haystack.charAt(idx - 1));
            int end = idx + needle.length();
            boolean rightOk = end >= haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (leftOk && rightOk) return true;
            idx = haystack.indexOf(needle, idx + 1);
        }
        return false;
    }

    // Extract a number that appears after a keyword pattern (e.g. "join event 5" -> 5)
    private Long extractNumberAfter(String text, String pattern) {
        String[] parts = pattern.split("\\|");
        String lower = text.toLowerCase(Locale.ROOT);
        for (String pat : parts) {
            String p = pat.trim();
            int idx = lower.indexOf(p);
            if (idx >= 0) {
                String rest = text.substring(idx + p.length()).trim();
                // Take first number found
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\d+").matcher(rest);
                if (m.find()) {
                    try {
                        return Long.parseLong(m.group());
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }

    // Extract quoted or last token after a keyword
    private String extractQuotedAfter(String text, String keyword) {
        String lower = text.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(keyword);
        if (idx >= 0) {
            String rest = text.substring(idx + keyword.length()).trim();
            // Try quotes first
            if (rest.startsWith("\"") || rest.startsWith("\"")) {
                int end = rest.indexOf('"', 1);
                if (end > 1) return rest.substring(1, end);
            }
            // Take first few words until next keyword
            String[] words = rest.split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                if (w.matches("^[A-Za-z]+$") && sb.length() > 0
                        && Set.of("for", "named", "called", "with", "needs", "needing", "about", "on", "domain", "skills", "skill", "project", "named", "create", "new", "to", "the", "a", "an").contains(w.toLowerCase(Locale.ROOT))) {
                    break;
                }
                sb.append(w).append(" ");
                if (sb.length() > 50) break;
            }
            String result = sb.toString().trim();
            return result.isEmpty() ? null : result;
        }
        return null;
    }

    // Extract skills from "needing <skills>" or "with <skills>"
    private String extractSkillsFromIntent(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf("needing");
        if (idx < 0) idx = lower.indexOf("needs");
        if (idx < 0) idx = lower.indexOf("require");
        if (idx >= 0) {
            String rest = text.substring(idx).trim();
            // Extract comma-or-space separated skill names
            String clean = rest.replaceAll("(?i)(needing|needs|require|requires)[\\s:]+", "").trim();
            // Take up to first couple of skill-like tokens
            String[] words = clean.split("[, ]+");
            StringBuilder sb = new StringBuilder();
            for (String w : words) {
                w = w.replaceAll("[^a-zA-Z0-9+.#]", "");
                if (w.length() >= 2) {
                    if (sb.length() > 0) sb.append(", ");
                    sb.append(w);
                    if (sb.length() > 200) break;
                }
                if (sb.length() > 0 && Set.of("for", "project", "and", "the", "a", "on", "at").contains(w.toLowerCase(Locale.ROOT))) break;
            }
            return sb.isEmpty() ? null : sb.toString();
        }
        return null;
    }

    private String extractAfterKeyword(String text, String keyword) {
        String lower = text.toLowerCase(Locale.ROOT);
        int idx = lower.indexOf(keyword);
        if (idx >= 0) {
            String rest = text.substring(idx + keyword.length()).trim();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\"([^\"]+)\"").matcher(rest);
            if (m.find()) return m.group(1);
            String[] words = rest.split("\\s+");
            if (words.length >= 1) return words[0].replaceAll("[^a-zA-Z0-9]", "");
        }
        return null;
    }


    // ========================
    // ---- Response type ----
    // ========================

    /**
     * Structured response from the agent loop.
     *
     * @param type      "success", "info", "error", "clarification", "confirmation"
     * @param text      Human-readable reply (rendered to user)
     * @param data      Optional structured payload (tool result details, IDs, etc.)
     * @param suggestions Follow-up prompts the client can surface
     */
    public record AgentResponse(
            String type,
            String text,
            Map<String, Object> data,
            List<String> suggestions
    ) {
        public static AgentResponse success(String text, Map<String, Object> data) {
            return new AgentResponse("success", text, data, null);
        }

        public static AgentResponse info(String text, Map<String, Object> data) {
            return new AgentResponse("info", text, data, null);
        }

        public static AgentResponse error(String text, String detail) {
            return new AgentResponse("error", text, Map.of("detail", detail), null);
        }

        public static AgentResponse clarification(String text) {
            return new AgentResponse("clarification", text, Map.of(),
                    List.of("Show my projects", "Find teammates", "List upcoming events",
                            "What documents are available?"));
        }

        public static AgentResponse confirmation(String text, Map<String, Object> data) {
            return new AgentResponse("confirmation", text, data, null);
        }
    }
}
