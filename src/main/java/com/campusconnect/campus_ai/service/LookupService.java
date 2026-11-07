package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Alumni;
import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.ReviewRepository;
import com.campusconnect.campus_ai.repository.EventRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import com.campusconnect.campus_ai.service.MentorshipService;
import com.campusconnect.campus_ai.service.ReferralService;
import com.campusconnect.campus_ai.service.DocumentService;
import com.campusconnect.campus_ai.service.RecruiterPostingService;
import com.campusconnect.campus_ai.service.SearchService;
import com.campusconnect.campus_ai.service.ProjectInviteService;
import com.campusconnect.campus_ai.service.TeammateRequestService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LookupService {

    private final StudentRepository studentRepository;
    private final EventRepository eventRepository;
    private final AlumniRepository alumniRepository;
    private final SkillGraphService skillGraphService;
    private final ReviewRepository reviewRepository;
    private final ProjectToolService projectToolService;
    private final MessageService messageService;
    private final AnalyticsService analyticsService;
    private final NotificationService notificationService;
    private final EventAttendanceService eventAttendanceService;
    private final ProjectInviteService projectInviteService;
    private final TeammateRequestService teammateRequestService;
    private final FollowService followService;
    private final SkillEndorsementService skillEndorsementService;
    private final BookmarkService bookmarkService;
    private final ProfileService profileService;
    private final ActivityService activityService;
    private final ModerationService moderationService;
    private final MentorshipService mentorshipService;
    private final ReferralService referralService;
    private final ChatHistoryService chatHistoryService;
    private final DocumentService documentService;
    private final SearchService searchService;
    private final RecruiterPostingService recruiterPostingService;
    private final DashboardService dashboardService;

    public LookupService(StudentRepository studentRepository,
                         EventRepository eventRepository,
                         AlumniRepository alumniRepository,
                         SkillGraphService skillGraphService,
                         ReviewRepository reviewRepository,
                         ProjectToolService projectToolService,
                         MessageService messageService,
                         AnalyticsService analyticsService,
                         NotificationService notificationService,
                         EventAttendanceService eventAttendanceService,
                         ProjectInviteService projectInviteService,
                         TeammateRequestService teammateRequestService,
                         FollowService followService,
                         SkillEndorsementService skillEndorsementService,
                         BookmarkService bookmarkService,
                         ProfileService profileService,
                         ActivityService activityService,
                         ModerationService moderationService,
                         MentorshipService mentorshipService,
                         ReferralService referralService,
                         ChatHistoryService chatHistoryService,
                         DocumentService documentService,
                         SearchService searchService,
                         RecruiterPostingService recruiterPostingService,
                         DashboardService dashboardService) {
        this.studentRepository = studentRepository;
        this.eventRepository = eventRepository;
        this.alumniRepository = alumniRepository;
        this.skillGraphService = skillGraphService;
        this.reviewRepository = reviewRepository;
        this.projectToolService = projectToolService;
        this.messageService = messageService;
        this.analyticsService = analyticsService;
        this.notificationService = notificationService;
        this.eventAttendanceService = eventAttendanceService;
        this.projectInviteService = projectInviteService;
        this.teammateRequestService = teammateRequestService;
        this.followService = followService;
        this.skillEndorsementService = skillEndorsementService;
        this.bookmarkService = bookmarkService;
        this.profileService = profileService;
        this.activityService = activityService;
        this.moderationService = moderationService;
        this.mentorshipService = mentorshipService;
        this.referralService = referralService;
        this.chatHistoryService = chatHistoryService;
        this.documentService = documentService;
        this.searchService = searchService;
        this.recruiterPostingService = recruiterPostingService;
        this.dashboardService = dashboardService;
    }

    public Optional<AskService.AskResponse> tryAnswer(String question) {
        return tryAnswer(question, null);
    }

    public Optional<AskService.AskResponse> tryAnswer(String question, Long userId) {
        if (question == null || question.isBlank()) return Optional.empty();
        String q = question.toLowerCase();

        // --- Greeting route ---
        // Bare greetings ("hello", "hi", "hey") or very short hello-variants
        // that also mention the assistant ("hello campus ai"). Returns a
        // personalized greeting when the user is authenticated.
        if (isGreeting(q, userId)) {
            return Optional.of(new AskService.AskResponse(true, buildGreeting(userId), List.of()));
        }

        // --- Chat history route (needs auth) ---
        if (userId != null && (q.contains("chat history") || q.contains("conversation history") || q.contains("my history") || q.contains("previous question") || q.contains("what did i ask"))) {
            try {
                var history = chatHistoryService.getHistory(userId, 5);
                long total = chatHistoryService.count(userId);
                if (history.isEmpty()) return Optional.of(new AskService.AskResponse(true, "You have no chat history yet. Ask something via GET /ask?q=... when signed in.", List.of()));
                StringBuilder sb = new StringBuilder("Your chat history (" + total + " turn(s), showing " + history.size() + "):");
                for (var h : history) sb.append("\n- Q: ").append(h.question()).append(" -> ").append(h.answered() ? h.answer().substring(0, Math.min(80, h.answer().length())) : "(no answer)").append(h.answer() != null && h.answer().length() > 80 ? "..." : "");
                sb.append("\nFull: GET /ask/history?limit=20 and GET /ask/history/count when signed in | DELETE /ask/history to clear");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Follow / Connections route (needs auth) ---
        if (userId != null && (q.contains("follow") || q.contains("following") || q.contains("follower") || q.contains("connect"))) {
            try {
                var stats = followService.getStats(userId);
                long following = ((Number) stats.get("followingCount")).longValue();
                long followers = ((Number) stats.get("followerCount")).longValue();
                if (q.contains("who") || q.contains("list")) {
                    var followingList = followService.getFollowing(userId);
                    var followerList = followService.getFollowers(userId);
                    StringBuilder sb = new StringBuilder("Your connections  -  following: " + following + ", followers: " + followers);
                    if (!followingList.isEmpty()) {
                        sb.append("\nFollowing: ").append(followingList.stream().map(p -> p.name() + " (" + p.branch() + ")").collect(java.util.stream.Collectors.joining(", ")));
                    }
                    if (!followerList.isEmpty()) {
                        sb.append("\nFollowers: ").append(followerList.stream().map(p -> p.name() + " (" + p.branch() + ")").collect(java.util.stream.Collectors.joining(", ")));
                    }
                    sb.append("\nEndpoints: POST /follow/{id}, POST /follow/{id}/unfollow, GET /follow/following, GET /follow/followers");
                    return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                }
                return Optional.of(new AskService.AskResponse(true, "Your connections  -  following: " + following + ", followers: " + followers + ". Manage at POST /follow/{id} and GET /follow/following|followers.", List.of()));
            } catch (Exception ignored) {}
        }

        // --- Mentorship route (needs auth) ---
        if (userId != null && (q.contains("mentorship") || q.contains("mentor request") || q.contains("request mentor") || q.contains("my mentorship") || (q.contains("mentor") && q.contains("request")))) {
            try {
                var mine = mentorshipService.getMyRequests(userId, "PENDING");
                if (mine.isEmpty()) return Optional.of(new AskService.AskResponse(true, "You have no pending mentorship requests. Request via POST /mentorship/request {alumniId, message} when signed in. List yours at GET /mentorship/my. Alumni view: GET /mentorship/alumni/{alumniId}", List.of()));
                StringBuilder sb = new StringBuilder("Your pending mentorship requests (" + mine.size() + "):");
                for (var mr : mine) sb.append("\n- alumni ").append(mr.alumniName()).append(" (").append(mr.alumniId()).append(")  -  ").append(mr.status()).append(" (request ").append(mr.id()).append(")");
                sb.append("\nRespond: POST /mentorship/{id}/respond {action: ACCEPT|REJECT} or POST /mentorship/{id}/withdraw");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Referral route (needs auth) ---
        if (userId != null && (q.contains("referral") || q.contains("refer me") || q.contains("my referral") || (q.contains("refer") && q.contains("request")))) {
            try {
                var mine = referralService.getMyRequests(userId, "PENDING");
                if (mine.isEmpty()) return Optional.of(new AskService.AskResponse(true, "You have no pending referral requests. Request via POST /referrals/request {alumniId, message, company, role} when signed in. List yours at GET /referrals/my. Alumni view: GET /referrals/alumni/{alumniId}", List.of()));
                StringBuilder sb = new StringBuilder("Your pending referral requests (" + mine.size() + "):");
                for (var rr : mine) sb.append("\n- alumni ").append(rr.alumniName()).append(" (").append(rr.alumniId()).append(")").append(rr.company() != null ? " @ " + rr.company() : "").append(rr.role() != null ? " — " + rr.role() : "").append(" (request ").append(rr.id()).append(")");
                sb.append("\nRespond: POST /referrals/{id}/respond {action: ACCEPT|REJECT} or POST /referrals/{id}/withdraw");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Moderation / reports route (needs auth for personal queue) ---
        if (q.contains("report") || q.contains("moderation") || q.contains("flagged") || q.contains("abuse")) {
            try {
                if (userId != null) {
                    var mine = moderationService.myReports(userId);
                    long open = mine.stream().filter(r -> "OPEN".equals(r.status())).count();
                    if (mine.isEmpty()) return Optional.of(new AskService.AskResponse(true, "You have not filed any reports. File one via POST /reports {targetType, targetId, reason} when signed in. View your reports at GET /reports/my.", List.of()));
                    StringBuilder sb = new StringBuilder("Your reports (" + mine.size() + ", open: " + open + "):");
                    for (var r : mine.stream().limit(5).toList()) sb.append("\n- ").append(r.targetType()).append(" ").append(r.targetId()).append("  -  ").append(r.reason()).append(" (").append(r.status()).append(")");
                    return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                }
                var stats = moderationService.stats();
                return Optional.of(new AskService.AskResponse(true, "Moderation queue  -  open: " + stats.get("open") + ", admin resolve: POST /reports/{id}/resolve when signed in (admin: "+stats.get("total") + ", resolved: " + stats.get("resolved") + ". Browse GET /reports?status=OPEN", List.of()));
            } catch (Exception ignored) {}
        }

        // --- Activity feed route (needs auth) ---
        if (userId != null && (q.contains("activity") || q.contains("timeline") || q.contains("feed") || q.contains("what's new") || q.contains("what is new"))) {
            try {
                var feed = activityService.getFeed(userId, 5, "ALL");
                if (feed.isEmpty()) return Optional.of(new AskService.AskResponse(true, "Your activity feed is empty. Try posting, following someone, saving a bookmark, or RSVPing to an event.", List.of()));
                StringBuilder sb = new StringBuilder("Your activity (" + feed.size() + "):");
                for (var item : feed) sb.append("\n- [").append(item.get("type")).append("] ").append(item.get("title"));
                sb.append("\nFull: GET /activity?limit=20&filter=ALL when signed in (filters: POSTS, INVITES, RSVPS, FOLLOWS, BOOKMARKS, ENDORSEMENTS, NOTIFICATIONS)");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Profile aggregator route (needs auth) ---
        if (userId != null && (q.contains("my profile") || q.contains("show my") || (q.contains("profile") && q.contains("me")) || q.equals("who am i"))) {
            try {
                var me = profileService.getMe(userId);
                var prof = (java.util.Map<?,?>) me.get("profile");
                var conn = (java.util.Map<?,?>) me.get("connections");
                var rep = (java.util.Map<?,?>) me.get("reputation");
                var proj = (java.util.Map<?,?>) me.get("projects");
                StringBuilder sb = new StringBuilder();
                sb.append(prof.get("name")).append(" (").append(prof.get("branch")).append(", year ").append(prof.get("year")).append(")  -  ").append(prof.get("availability"));
                if (prof.get("skills") != null) sb.append("\nSkills: ").append(prof.get("skills"));
                if (conn != null) sb.append("\nConnections: following ").append(conn.get("followingCount")).append(", followers ").append(conn.get("followerCount"));
                if (rep != null) sb.append("\nReputation: ").append(rep.get("reviewCount")).append(" review(s)").append(rep.get("averageRating") != null ? " avg " + rep.get("averageRating") + "/5" : "");
                if (proj != null) sb.append("\nProjects: ").append(proj.get("count"));
                sb.append("\nFull: GET /me when signed in or GET /profile/" + userId);
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Profile completeness / skill recommendations route (needs auth) ---
        if (userId != null && (q.contains("completeness") || q.contains("complete my profile") || q.contains("profile complete") || q.contains("how complete"))) {
            try {
                var comp = profileService.getCompleteness(userId);
                StringBuilder sb = new StringBuilder();
                sb.append("Profile completeness: ").append(comp.get("score")).append("/100 (").append(comp.get("level")).append(")");
                var missing = (java.util.List<?>) comp.get("missingFields");
                if (missing != null && !missing.isEmpty()) sb.append("\nMissing: ").append(String.join(", ", missing.stream().map(Object::toString).toList()));
                else sb.append("\nYour profile looks complete!");
                sb.append("\nFull: GET /me/completeness when signed in | GET /me to see your profile");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }
        if (userId != null && ((q.contains("recommend") && q.contains("skill")) || q.contains("what skill should") || q.contains("suggest skill") || q.contains("skill suggestion"))) {
            try {
                var rec = profileService.getSkillRecommendations(userId, 5);
                var recs = (java.util.List<?>) rec.get("recommendations");
                if (recs == null || recs.isEmpty()) return Optional.of(new AskService.AskResponse(true, "No skill recommendations yet. Add a few skills to your profile first. Try GET /me/recommendations?limit=5", List.of()));
                StringBuilder sb = new StringBuilder("Recommended skills for you:");
                for (Object o : recs) {
                    if (o instanceof java.util.Map<?,?> m) sb.append("\n- ").append(m.get("skill")).append(" [").append(m.get("category")).append("]  -  ").append(m.get("reason"));
                }
                sb.append("\nFull: GET /me/recommendations?limit=5 when signed in");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Project create / add-member hints (needs auth) ---
        if (userId != null && (q.contains("create project") || q.contains("new project") || (q.contains("create") && q.contains("project")))) {
            try {
                var my = projectToolService.getMyProjects(userId);
                StringBuilder sb = new StringBuilder();
                sb.append("Create a project: POST /projects when signed in and {name, description, domain, requiredSkills}. Name 3-120 chars, unique. RequiredSkills are normalized (e.g. js -> JavaScript).");
                sb.append(" You currently own/are in ").append(my.size()).append(" active project(s).");
                sb.append(" Add a teammate: POST /projects/{id}/members {studentId} (only members can add, max 10, notifies the student).");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Bookmarks / Saved route (needs auth) ---
        if (userId != null && (q.contains("bookmark") || q.contains("saved") || q.contains("save this") || (q.contains("my") && q.contains("saved")))) {
            try {
                var list = bookmarkService.list(userId, null);
                if (list.isEmpty()) return Optional.of(new AskService.AskResponse(true, "You have no saved bookmarks yet. Save via POST /bookmarks {refType: POST|EVENT|PROJECT, refId} when signed in or toggle at POST /bookmarks/toggle.", List.of()));
                var byType = bookmarkService.saveSummary(userId).get("byType");
                StringBuilder sb = new StringBuilder("Your saved bookmarks (" + list.size() + ")  -  " + byType);
                for (int i = 0; i < Math.min(5, list.size()); i++) {
                    var b = list.get(i);
                    sb.append("\n- " + b.refType() + " " + b.refId());
                }
                if (list.size() > 5) sb.append("\n... and " + (list.size() - 5) + " more. GET /bookmarks when signed in");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Endorsements route (needs auth) ---
        if (userId != null && (q.contains("endors") || q.contains("endorsement"))) {
            try {
                // name lookup for another user, else self
                String targetName = findStudentNameInQuestion(q);
                Long targetId = userId;
                if (targetName != null) {
                    var m = studentRepository.findAll().stream().filter(s -> s.getName().equalsIgnoreCase(targetName)).findFirst();
                    if (m.isPresent()) targetId = m.get().getId();
                }
                var summary = skillEndorsementService.getSummary(targetId);
                var bySkill = (java.util.Map<?,?>) summary.get("bySkill");
                long total = ((Number) summary.get("totalEndorsements")).longValue();
                String who = targetId.equals(userId) ? "You have" : (targetName != null ? targetName + " has" : "They have");
                if (total == 0) return Optional.of(new AskService.AskResponse(true, who + " no endorsements yet. Endorse via POST /endorsements {endorseeId, skill} when signed in.", List.of()));
                StringBuilder sb = new StringBuilder(who + " " + total + " endorsement(s): ");
                sb.append(bySkill.entrySet().stream().map(e -> e.getKey() + " (" + e.getValue() + ")").collect(java.util.stream.Collectors.joining(", ")));
                sb.append(". Details: GET /endorsements/" + targetId + " and /endorsements/" + targetId + "/summary");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Project Invite route (needs auth) ---
        if (userId != null && (q.contains("invite") || q.contains("join my project") || q.contains("project invite") || q.contains("pending invite"))) {
            try {
                var invites = projectInviteService.getMyInvites(userId, "PENDING");
                if (invites.isEmpty()) return Optional.of(new AskService.AskResponse(true, "You have no pending project invites. To invite someone: POST /projects/{id}/invite {inviteeId} when signed in. To accept: POST /project-invites/{id}/respond {action: ACCEPT} when signed in.", List.of()));
                StringBuilder sb = new StringBuilder("Your pending project invites (" + invites.size() + "):");
                for (var inv : invites) sb.append("\n- ").append(inv.projectName()).append(" from ").append(inv.inviterName()).append(" (invite ").append(inv.id()).append(")");
                sb.append("\nAccept with POST /project-invites/{id}/respond {action: ACCEPT} or REJECT");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Event RSVP route (needs auth) ---
        if (userId != null && (q.contains("join event") || q.contains("rsvp") || q.contains("attend event") || (q.contains("my event") && !q.contains("my message")))) {
            try {
                var myEvents = eventAttendanceService.getMyEvents(userId);
                if (myEvents.isEmpty()) return Optional.of(new AskService.AskResponse(true, "You have not RSVP'd to any events yet. Browse GET /events?upcoming=true and POST /events/{id}/join.", List.of()));
                StringBuilder sb = new StringBuilder("Your RSVPs (" + myEvents.size() + "):");
                for (var e : myEvents) sb.append("\n- ").append(e.title()).append(" (").append(e.type()).append(") on ").append(e.eventDate()).append(" at ").append(e.location());
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Messaging route (needs auth) ---
        if (userId != null && (q.contains("message") || q.contains("inbox") || q.contains("dm") || q.contains("unread"))) {
            try {
                if (q.contains("unread")) {
                    long unread = messageService.getUnreadCount(userId);
                    if (unread == 0) return Optional.of(new AskService.AskResponse(true, "You have no unread messages.", List.of()));
                    return Optional.of(new AskService.AskResponse(true, "You have " + unread + " unread message(s). Open your inbox at GET /messages/inbox.", List.of()));
                }
                if (q.contains("inbox") || q.contains("my message") || q.contains("my inbox")) {
                    var inbox = messageService.getInbox(userId);
                    if (inbox.isEmpty()) return Optional.of(new AskService.AskResponse(true, "Your inbox is empty. Start a conversation at POST /messages.", List.of()));
                    StringBuilder sb = new StringBuilder("Your inbox (" + inbox.size() + " conversations):");
                    for (var c : inbox) {
                        sb.append("\n- ").append(c.peerName()).append(" (id ").append(c.peerId()).append(")  -  ").append(c.preview()).append(" [").append(c.direction()).append("] unread: ").append(c.unreadCount());
                    }
                    return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                }
                // thread with named peer: "messages with Aarav" / "thread with Sneha"
                String peerName = findStudentNameInQuestion(q);
                if (peerName != null && (q.contains("thread") || q.contains("conversation") || q.contains("messages with") || q.contains("chat with"))) {
                    var peer = studentRepository.findAll().stream()
                            .filter(s -> s.getName().equalsIgnoreCase(peerName))
                            .findFirst().orElse(null);
                    if (peer != null) {
                        var thread = messageService.getThread(userId, peer.getId());
                        if (thread.isEmpty()) return Optional.of(new AskService.AskResponse(true, "No messages with " + peer.getName() + " yet.", List.of()));
                        StringBuilder sb = new StringBuilder("Thread with " + peer.getName() + " (" + thread.size() + " messages):");
                        for (var m : thread) sb.append("\n- ").append(m.senderName()).append(": ").append(m.content());
                        return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                    }
                }
            } catch (Exception ignored) {}
        }

        // --- Skill glossary / skill search route ---
        boolean isGlossaryQuery = q.contains("glossary") || q.contains("alias for") || q.contains("aliases for")
                || q.contains("what does") || (q.contains("what is") && (q.contains("skill") || findSkillInQuestion(q) != null))
                || q.contains("tell me about") || q.contains("skill meaning") || q.contains("skill info");
        if (isGlossaryQuery) {
            try {
                String rawSkill = findSkillInQuestion(q);
                // fallback: token after "glossary" or "for" or last word after "is"
                if (rawSkill == null) {
                    String[] tokens = q.split("\\s+");
                    // try last meaningful token that looks like a skill
                    for (int i = tokens.length - 1; i >= 0; i--) {
                        String tok = tokens[i].replaceAll("[^a-z0-9#+\\.\\-]", "");
                        if (tok.length() >= 2 && !java.util.Set.of("skill","glossary","what","does","mean","alias","aliases","for","the","a","is","tell","about","info","meaning").contains(tok)) {
                            rawSkill = tok; break;
                        }
                    }
                }
                if (rawSkill != null) {
                    var g = skillGraphService.getGlossary(rawSkill);
                    StringBuilder sb = new StringBuilder();
                    sb.append(g.get("canonical")).append(" [").append(g.get("category")).append("]");
                    var aliases = (java.util.List<?>) g.get("aliases");
                    if (aliases != null && !aliases.isEmpty()) sb.append("\nAliases: ").append(String.join(", ", aliases.stream().map(Object::toString).toList()));
                    var related = (java.util.List<?>) g.get("related");
                    if (related != null && !related.isEmpty()) {
                        sb.append("\nRelated: ").append(related.stream().limit(3).map(o -> {
                            if (o instanceof java.util.Map<?,?> m) return String.valueOf(m.get("skill"));
                            return o.toString();
                        }).collect(java.util.stream.Collectors.joining(", ")));
                    }
                    sb.append("\nProfiles: ").append(g.get("profileCount")).append(" (students ").append(g.get("studentCount")).append(", alumni ").append(g.get("alumniCount")).append(")");
                    sb.append("\nEndpoints: GET /skills/glossary?skill=").append(g.get("canonical")).append(" | GET /skills/search?q=").append(g.get("canonical"));
                    return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                }
            } catch (Exception ignored) {}
        }
        // skill search / autocomplete chat intent: "search skills for rea" / "find skills like react"
        if (q.contains("search skill") || q.contains("find skill") || q.contains("autocomplete") || q.contains("skill search")) {
            try {
                String needle = "";
                // extract after "for" or "like" or "search"
                int idx = -1;
                for (String kw : new String[]{"for ", "like ", "search ", "find "}) {
                    int p2 = q.indexOf(kw);
                    if (p2 >= 0) idx = Math.max(idx, p2 + kw.length());
                }
                if (idx >= 0 && idx < q.length()) needle = q.substring(idx).trim().split("\\s+")[0].replaceAll("[^a-z0-9#+\\.\\-]", "");
                if (!needle.isEmpty()) {
                    var hits = skillGraphService.searchSkills(needle, 8);
                    if (hits.isEmpty()) return Optional.of(new AskService.AskResponse(true, "No canonical skills match '" + needle + "'. Try GET /skills/search?q=" + needle, List.of()));
                    return Optional.of(new AskService.AskResponse(true, "Skills matching '" + needle + "': " + String.join(", ", hits) + ". Details: GET /skills/search?q=" + needle, List.of()));
                }
            } catch (Exception ignored) {}
        }

        // --- STUDENT route (alias-aware) ---
        String matchedSkill = findSkillInQuestion(q);
        boolean hasStudentWord = containsAny(q, "student", "students", "someone", "who", "teammate", "find");
        if (matchedSkill != null && hasStudentWord) {
            String skillFinal = matchedSkill;
            List<Student> matches = studentRepository.findAll().stream()
                    .filter(s -> StudentService.hasWholeSkill(s.getSkills(), skillFinal))
                    .limit(5)
                    .collect(Collectors.toList());
            if (matches.isEmpty()) {
                String canonLower = skillGraphService.normalize(skillFinal);
                if (canonLower == null) canonLower = skillFinal;
                String wantedLower = canonLower.toLowerCase(Locale.ROOT);
                matches = studentRepository.findAll().stream()
                        .filter(s -> {
                            if (s.getSkills() == null) return false;
                            for (String part : s.getSkills().split(",")) {
                                String raw = part.trim(); if (raw.isEmpty()) continue;
                                String n = skillGraphService.normalize(raw); if (n == null) n = raw;
                                if (n.toLowerCase(Locale.ROOT).equals(wantedLower)) return true;
                            }
                            return false;
                        }).limit(5).collect(Collectors.toList());
            }
            if (!matches.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("I found ").append(matches.size()).append(" student(s) who know ").append(skillFinal).append(":");
                for (Student st : matches) {
                    sb.append("\n- ").append(st.getName())
                      .append(" (").append(st.getBranch()).append(", year ").append(st.getYear()).append(")")
                      .append(" - skills: ").append(st.getSkills())
                      .append(" - ").append(st.getAvailability());
                }
                List<SkillGraphService.RelatedSkill> related = skillGraphService.getRelatedSkills(skillFinal, 3);
                if (related.isEmpty()) {
                    String canon = skillGraphService.normalize(skillFinal);
                    if (canon != null && !canon.equals(skillFinal)) related = skillGraphService.getRelatedSkills(canon, 3);
                }
                if (!related.isEmpty()) {
                    sb.append("\n\nRelated skills people often pair with ").append(skillFinal).append(": ");
                    sb.append(related.stream().map(SkillGraphService.RelatedSkill::skillName).collect(Collectors.joining(", ")));
                }
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            }
        }

        // --- EVENT route ---
        boolean hasEventWord = containsAny(q, "event", "events", "workshop", "workshops", "hackathon", "hackathons");
        boolean hasUpcomingWord = containsAny(q, "upcoming", "coming up", "next");
        if (hasEventWord && hasUpcomingWord) {
            LocalDate today = LocalDate.now();
            List<Event> upcoming = eventRepository.findByEventDateGreaterThanEqual(today).stream()
                    .filter(e -> !e.isHidden()).collect(java.util.stream.Collectors.toList());
            // Detect an optional topic qualifier (e.g. "ai workshops", "cybersecurity
            // workshop") and narrow the list to events whose title or description
            // mentions that topic. `topicLabel` is remembered so the empty-state
            // message can reference the topic the user actually asked about.
            String topicLabel = null;
            java.util.Set<String> topics = new java.util.LinkedHashSet<>();
            if (q.contains("artificial intelligence") || q.matches(".*\\bai\\b.*") || q.contains("ai ") || q.contains("ai workshop") || q.contains("ai hackathon") || q.contains("ai event")) {
                topics.add("ai"); topicLabel = "AI";
            }
            if (q.contains("machine learning") || q.matches(".*\\bml\\b.*") || q.contains("ml workshop")) { topics.add("machine learning"); topicLabel = "machine learning"; }
            if (q.contains("deep learning")) { topics.add("deep learning"); topicLabel = "deep learning"; }
            if (q.contains("data science")) { topics.add("data science"); topicLabel = "data science"; }
            if (q.contains("blockchain")) { topics.add("blockchain"); topicLabel = "blockchain"; }
            if (q.contains("devops")) { topics.add("devops"); topicLabel = "DevOps"; }
            if (q.contains("cybersecurity")) { topics.add("cybersecurity"); topicLabel = "cybersecurity"; }
            if (q.contains("quantum")) { topics.add("quantum"); topicLabel = "quantum"; }
            if (q.contains("cloud workshop") || q.contains("cloud computing")) { topics.add("cloud"); topicLabel = "cloud"; }
            if (!topics.isEmpty()) {
                upcoming = upcoming.stream()
                        .filter(e -> {
                            String haystack = (e.getTitle() + " " + (e.getDescription() != null ? e.getDescription() : "")).toLowerCase(Locale.ROOT);
                            return topics.stream().anyMatch(t -> topicMatches(haystack, t));
                        })
                        .collect(java.util.stream.Collectors.toList());
            }
            upcoming.sort(Comparator.comparing(Event::getEventDate));
            if (upcoming.isEmpty()) {
                String msg = topicLabel != null
                        ? "I couldn't find any upcoming " + topicLabel + " events in Campus Connect right now. " +
                          "Try \"upcoming events\" to see everything scheduled."
                        : "There are no upcoming events in Campus Connect right now.";
                return Optional.of(new AskService.AskResponse(true, msg, List.of()));
            }
            List<Event> top = upcoming.stream().limit(5).toList();
            StringBuilder sb = new StringBuilder(topicLabel != null
                    ? "Upcoming " + topicLabel + " events:"
                    : "Upcoming events:");
            for (Event e : top) {
                sb.append("\n- ").append(e.getTitle())
                  .append(" (").append(e.getType()).append(") on ").append(e.getEventDate())
                  .append(" at ").append(e.getLocation());
            }
            return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
        }

        // --- Event create hint (needs auth) ---
        if (userId != null && (q.contains("create event") || q.contains("new event") || (q.contains("create") && q.contains("event")))) {
            try {
                StringBuilder sb = new StringBuilder();
                sb.append("Create an event: POST /events when signed in and {title, type, eventDate (YYYY-MM-DD), description, location}. Type: HACKATHON|WORKSHOP|SEMINAR|NETWORKING|PLACEMENT. Title 3-120 chars.");
                sb.append(" Browse: GET /events?upcoming=true or GET /events?type=HACKATHON. New events need admin approval before they appear publicly.");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- ALUMNI / ROADMAP route ---
        boolean hasAlumniWord = containsAny(q, "alumni", "alumnus", "alumna", "mentor", "mentors", "mentorship", "roadmap", "roadmaps", "career path", "placement roadmap");
        if (hasAlumniWord) {
            String matchedAlumniSkill = null;
            Set<String> knownAlumniSkills = new LinkedHashSet<>();
            for (Alumni a : alumniRepository.findAll()) {
                if (a.getSkills() == null) continue;
                for (String part : a.getSkills().split(",")) {
                    String t = part.trim();
                    if (!t.isEmpty()) knownAlumniSkills.add(t);
                }
            }
            for (String skill : knownAlumniSkills) {
                String norm = skillGraphService.normalize(skill);
                if (norm == null) norm = skill;
                String needle = norm.toLowerCase();
                if (containsWholeWord(q, needle) || containsWholeWord(q, skill.toLowerCase())) {
                    matchedAlumniSkill = skill;
                    break;
                }
            }
            if (matchedAlumniSkill != null) {
                String alumniSkillFinal = matchedAlumniSkill;
                List<Alumni> mentors = alumniRepository.findAll().stream()
                        .filter(a -> AlumniService.hasWholeSkill(a.getSkills(), alumniSkillFinal))
                        .filter(a -> "MENTORING".equalsIgnoreCase(a.getAvailability()) || "BOTH".equalsIgnoreCase(a.getAvailability()))
                        .limit(5)
                        .collect(Collectors.toList());
                if (!mentors.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("I found ").append(mentors.size()).append(" mentor(s) for ").append(matchedAlumniSkill).append(":");
                    for (Alumni a : mentors) {
                        sb.append("\n- ").append(a.getName())
                          .append(" (").append(a.getCurrentRole()).append(" at ").append(a.getCurrentCompany()).append(")")
                          .append(" - skills: ").append(a.getSkills());
                    }
                    return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                }
            }
            if (containsAny(q, "roadmap", "roadmaps", "career path", "placement roadmap", "how to become")) {
                List<Alumni> alumniWithRoadmaps = alumniRepository.findAll().stream()
                        .filter(a -> a.getRoadmaps() != null && !a.getRoadmaps().isEmpty())
                        .toList();
                if (!alumniWithRoadmaps.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Available roadmaps from alumni:");
                    for (Alumni a : alumniWithRoadmaps) {
                        for (var r : a.getRoadmaps()) {
                            sb.append("\n- ").append(r.getTitle())
                              .append(" by ").append(a.getName())
                              .append(" (").append(a.getCurrentRole()).append(" at ").append(a.getCurrentCompany()).append(")")
                              .append(": ").append(r.getDescription().substring(0, Math.min(120, r.getDescription().length()))).append("...");
                        }
                    }
                    return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                }
            }
        }

        // --- Project / teammate suggestions (needs auth) ---
        if (userId != null && (q.contains("find teammate") || q.contains("skill gap") || (q.contains("project") && (q.contains("teammate") || q.contains("gap"))))) {
            try {
                var myProjects = projectToolService.getMyProjects(userId);
                for (var ps : myProjects) {
                    var gaps = projectToolService.getSkillGaps(ps.id());
                    if (gaps.isEmpty()) continue;
                    var suggestion = projectToolService.findTeammates(userId, ps.id());
                    if (!suggestion.candidates().isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("For your project \"").append(suggestion.projectName()).append("\" skill gaps: ").append(String.join(", ", suggestion.gaps())).append("\nCandidates:\n");
                        for (var c : suggestion.candidates()) {
                            sb.append("- ").append(c.name()).append(" (").append(c.branch()).append(", year ").append(c.year()).append(") - ").append(c.skills()).append(" | matched: ").append(String.join(", ", c.matchedSkills())).append("\n");
                        }
                        sb.append("\nReply 'request teammate' with a candidate name to send them an invite.");
                        return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                    }
                }
                if (!myProjects.isEmpty()) {
                    var first = myProjects.get(0);
                    var gaps0 = projectToolService.getSkillGaps(first.id());
                    if (!gaps0.isEmpty()) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Your project \"").append(first.name()).append("\" skill gaps: ").append(String.join(", ", gaps0));
                        sb.append("\nNo available candidates matched the gaps right now.");
                        return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                    }
                }
            } catch (Exception ignored) {}
        }

        // --- Teammate request: "request teammate <name>" or "invite <name> to <project>" ---
        if (userId != null && q.contains("request teammate")) {
            try {
                String[] parts = q.substring(q.indexOf("request teammate") + 18).trim().split("\\s+");
                String candidateName = String.join(" ", parts).trim();
                if (candidateName.isEmpty()) {
                    return Optional.of(new AskService.AskResponse(true,
                            "Specify a name: 'request teammate <name>'", List.of()));
                }
                // Find the candidate by name
                var candidateOpt = studentRepository.findAll().stream()
                        .filter(s -> candidateName.equalsIgnoreCase(s.getName()))
                        .findFirst();
                if (candidateOpt.isEmpty()) {
                    return Optional.of(new AskService.AskResponse(true,
                            "No student found with name '" + candidateName + "'.", List.of()));
                }
                // Find the user's first project with gaps
                var myProjects = projectToolService.getMyProjects(userId);
                for (var ps : myProjects) {
                    var gaps = projectToolService.getSkillGaps(ps.id());
                    if (gaps.isEmpty()) continue;
                    var dto = teammateRequestService.send(userId, ps.id(), candidateOpt.get().getId(), null);
                    return Optional.of(new AskService.AskResponse(true,
                            "Teammate request sent to " + candidateName + " for project \"" + ps.name() + "\".", List.of()));
                }
                return Optional.of(new AskService.AskResponse(true,
                        "No active project with skill gaps found for you.", List.of()));
            } catch (Exception e) {
                return Optional.of(new AskService.AskResponse(true,
                        "Could not send teammate request: " + e.getMessage(), List.of()));
            }
        }

        // --- Role dashboards ---
        if (userId != null && (q.contains("dashboard") || (q.contains("my") && (q.contains("overview") || q.contains("homepage"))))) {
            try {
                var dash = dashboardService.getStudentDashboard(userId);
                var projCount = (int) ((Map<?, ?>) dash.getOrDefault("projects", Map.of())).get("count");
                var inviteCount = (int) ((Map<?, ?>) dash.getOrDefault("pendingInvites", Map.of())).get("count");
                var notifCount = (int) dash.get("unreadNotifications");
                var rsvpCount = (int) ((Map<?, ?>) dash.getOrDefault("events", Map.of())).get("count");
                StringBuilder sb = new StringBuilder("Your dashboard:\n");
                sb.append("- Projects: ").append(projCount).append("\n");
                sb.append("- Pending invites: ").append(inviteCount).append("\n");
                sb.append("- Upcoming events RSVP'd: ").append(rsvpCount).append("\n");
                sb.append("- Unread notifications: ").append(notifCount).append("\n");
                sb.append("\nOpen the /dashboard/student page for full details.");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Skill graph dashboard ---
        if (q.contains("skill graph") || (q.contains("skill") && (q.contains("dashboard") || q.contains("graph") || q.contains("explore")))) {
            try {
                var dash = skillGraphService.getDashboardData(userId);
                var topSkills = (List<?>) dash.get("topSkills");
                var toGrow = (List<?>) dash.get("skillsToGrow");
                StringBuilder sb = new StringBuilder("Skill Graph Dashboard:\n\nTop skills on campus:\n");
                for (Object o : topSkills) {
                    var entry = (Map<?, ?>) o;
                    sb.append("- ").append(entry.get("skill")).append(" (").append(entry.get("count")).append(" profiles)\n");
                }
                if (userId != null && toGrow != null && !toGrow.isEmpty()) {
                    sb.append("\nSkills to grow for you: ");
                    for (Object o : toGrow) {
                        var entry = (Map<?, ?>) o;
                        sb.append(entry.get("skill")).append(", ");
                    }
                }
                sb.append("\n\nFull dashboard: GET /skills/dashboard (or /skills-graph)");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Reviews / reputation route ---
        if (q.contains("review") || q.contains("reputation") || q.contains("rating")) {
            String targetName = findStudentNameInQuestion(q);
            if (targetName != null) {
                var matched = studentRepository.findAll().stream()
                        .filter(s -> s.getName().equalsIgnoreCase(targetName) || s.getName().toLowerCase(Locale.ROOT).contains(targetName.toLowerCase(Locale.ROOT)))
                        .findFirst();
                if (matched.isPresent()) {
                    var reviews = reviewRepository.findByRevieweeIdOrderByCreatedAtDesc(matched.get().getId());
                    double avg = reviews.isEmpty() ? 0 : reviews.stream().mapToInt(r -> r.getRating()).average().orElse(0);
                    StringBuilder sb = new StringBuilder();
                    sb.append(matched.get().getName()).append("  -  reputation: ").append(reviews.size()).append(" review(s)");
                    if (!reviews.isEmpty()) sb.append(String.format(Locale.ROOT, " avg %.1f/5", avg));
                    sb.append("\n");
                    for (var r : reviews) sb.append("- ").append(r.getRating()).append("/5 by ").append(r.getReviewer() != null ? r.getReviewer().getName() : "?").append(": ").append(r.getComment() != null ? r.getComment() : "").append("\n");
                    return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                }
            }
        }

        // --- Recruiter / hiring route ---
        if (q.contains("recruiter") || q.contains("hire") || q.contains("hiring") || q.contains("candidate") || q.contains("talent")) {
            String skillHire = findSkillInQuestion(q);
            if (skillHire != null) {
                String canon = skillGraphService.normalize(skillHire);
                if (canon == null) canon = skillHire;
                String wantedLower = canon.toLowerCase(Locale.ROOT);
                var candidates = studentRepository.findAll().stream()
                        .filter(s -> {
                            if (s.getSkills() == null) return false;
                            for (String part : s.getSkills().split(",")) {
                                String raw = part.trim(); if (raw.isEmpty()) continue;
                                String n = skillGraphService.normalize(raw); if (n == null) n = raw;
                                if (n.toLowerCase(Locale.ROOT).equals(wantedLower)) return true;
                            }
                            return false;
                        }).limit(5).collect(Collectors.toList());
                if (!candidates.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("Candidates for ").append(skillHire).append(" (").append(canon).append("):\n");
                    for (var st : candidates) sb.append("- ").append(st.getName()).append(" (").append(st.getBranch()).append(", year ").append(st.getYear()).append(") - ").append(st.getSkills()).append("  -  ").append(st.getAvailability()).append("\n");
                    var rel = skillGraphService.getRelatedSkills(canon, 3);
                    if (!rel.isEmpty()) sb.append("Related: ").append(rel.stream().map(SkillGraphService.RelatedSkill::skillName).collect(Collectors.joining(", ")));
                    return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                }
            }
        }

        // --- Skill graph helpers ---
        if (q.contains("what is") && (q.contains("alias") || q.contains("canonical") || q.contains("related skill"))) {
            String skillQ = findSkillInQuestion(q);
            if (skillQ != null) {
                String canon = skillGraphService.normalize(skillQ);
                if (canon == null) canon = skillQ;
                var related = skillGraphService.getRelatedSkills(canon, 5);
                StringBuilder sb = new StringBuilder();
                sb.append(skillQ).append(" → canonical: ").append(canon).append("\n");
                if (!related.isEmpty()) sb.append("Related: ").append(related.stream().map(SkillGraphService.RelatedSkill::skillName).collect(Collectors.joining(", ")));
                else sb.append("No co-occurrence data yet. Run POST /skills/rebuild.");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            }
        }

        // --- Notifications route (needs auth) ---
        if (userId != null && (q.contains("notification") || q.contains("notif") || q.contains("unread") || q.contains("alert"))) {
            try {
                long unread = notificationService.getUnreadCount(userId);
                var recent = notificationService.getForUser(userId, false, 3);
                StringBuilder sb = new StringBuilder();
                if (unread == 0) sb.append("You have no unread notifications.");
                else sb.append("You have ").append(unread).append(" unread notification(s).");
                if (!recent.isEmpty()) {
                    sb.append("\nRecent:");
                    for (var n : recent) sb.append("\n- [").append(n.type()).append("] ").append(n.title()).append(n.readFlag() ? " (read)" : " (unread)");
                }
                sb.append("\nOpen GET /notifications when signed in or GET /notifications?unreadOnly=true");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Analytics / Campus Intelligence route ---
        // NOTE: "team" guard — "How many students can be in a hackathon team?" must fall through to RAG, not analytics.
        if ((q.contains("analytics") || q.contains("stats") || q.contains("statistics") || q.startsWith("how many students in the platform") || q.startsWith("how many projects on the platform") || q.contains("campus intelligence")) && !q.contains("team")) {
            try {
                var summary = analyticsService.getSummary();
                var top = analyticsService.topSkills(5);
                StringBuilder sb = new StringBuilder("Campus Intelligence  -  Analytics:");
                sb.append("\nStudents: ").append(summary.get("students")).append(", Alumni: ").append(summary.get("alumni")).append(", Projects: ").append(summary.get("projects")).append(", Documents: ").append(summary.get("documents")).append(" (").append(summary.get("chunks")).append(" chunks)");
                sb.append("\nPosts: ").append(summary.get("posts")).append(", Messages: ").append(summary.get("messages")).append(", Reviews: ").append(summary.get("reviews")).append(", Events: ").append(summary.get("events")).append(" (").append(summary.get("upcomingEvents")).append(" upcoming)");
                if (!top.isEmpty()) {
                    sb.append("\nTop skills: ").append(top.stream().map(m -> m.get("skill") + " (" + m.get("count") + ")").collect(java.util.stream.Collectors.joining(", ")));
                }
                sb.append("\nDetails: GET /analytics/summary, /analytics/skills, /analytics/projects, /analytics/community");
                return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
            } catch (Exception ignored) {}
        }

        // --- Recruiter postings / applications route ---
        if (q.contains("posting") || q.contains("job opening") || q.contains("job posting") || q.contains("opening") || q.contains("application") || q.contains("shortlist")) {
            // my applications (needs auth)
            if (userId != null && (q.contains("my application") || q.contains("my shortlist") || q.contains("applied"))) {
                try {
                    var apps = recruiterPostingService.getMyApplications(userId, null);
                    if (apps.isEmpty()) return Optional.of(new AskService.AskResponse(true, "You have no applications yet. Apply via POST /recruiter/postings/{id}/apply when signed in.", List.of()));
                    StringBuilder sb = new StringBuilder("Your applications (" + apps.size() + "):");
                    for (var a : apps) sb.append("\n- #").append(a.id()).append(" ").append(a.postingTitle()).append("  -  ").append(a.status()).append(" (posting ").append(a.postingId()).append(")");
                    sb.append("\nManage: GET /recruiter/applications/my?status=APPLIED and POST /recruiter/applications/{id}/withdraw when signed in");
                    return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                } catch (Exception ignored) {}
            }
            // list open postings
            if (q.contains("posting") || q.contains("opening") || q.contains("job")) {
                try {
                    var list = recruiterPostingService.listPostings("OPEN", null);
                    if (list.isEmpty()) return Optional.of(new AskService.AskResponse(true, "No open postings right now.", List.of()));
                    int show = Math.min(5, list.size());
                    StringBuilder sb = new StringBuilder("Open postings (" + list.size() + ", showing " + show + "):");
                    for (int i = 0; i < show; i++) {
                        var po = list.get(i);
                        sb.append("\n- #").append(po.id()).append(" ").append(po.title()).append("  -  ").append(po.company() != null ? po.company() : "?").append(po.requiredSkills() != null ? " [" + po.requiredSkills() + "]" : "").append(" (").append(po.applicantCount()).append(" applicants)");
                    }
                    sb.append("\nDetail: GET /recruiter/postings/{id} | Apply: POST /recruiter/postings/{id}/apply when signed in | Create (recruiter): POST /recruiter/postings when signed in");
                    return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                } catch (Exception ignored) {}
            }
        }

        // --- Document / upload manager route ---
        if (q.contains("document") || q.contains("upload") || q.contains("curated") || q.contains("rag") || q.contains("knowledge base")) {
            if (userId != null && (q.contains("my upload") || q.contains("my document"))) {
                try {
                    var mine = documentService.getMyUploads(userId);
                    if (mine.isEmpty()) return Optional.of(new AskService.AskResponse(true, "You have no uploads yet. Upload via POST /documents/upload (multipart file .txt/.md, 5MB) when signed in.", List.of()));
                    StringBuilder sb = new StringBuilder("Your uploads (" + mine.size() + "):");
                    for (var d : mine) sb.append("\n- #").append(d.getId()).append(" ").append(d.getTitle()).append(d.isHidden() ? " [hidden]" : "").append("  -  ").append(d.getContent() != null ? d.getContent().length() + " chars" : "");
                    sb.append("\nCurated: GET /documents (visible), GET /documents/admin/all (admin), POST /documents/admin/{id}/visibility when signed in (admin)");
                    return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                } catch (Exception ignored) {}
            }
            if (q.contains("how many document") || q.contains("document count") || q.contains("how many chunk")) {
                try {
                    var stats = documentService.stats();
                    StringBuilder sb = new StringBuilder("Documents  -  visible: " + stats.get("visible") + ", hidden: " + stats.get("hidden") + ", total: " + stats.get("total") + ", chunks: " + stats.get("totalChunks"));
                    sb.append("\nVisible: GET /documents | All (admin): GET /documents/admin/all when signed in | Stats: GET /documents/stats/overview");
                    return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                } catch (Exception ignored) {}
            }
            if (q.contains("document") || q.contains("curated")) {
                try {
                    var visible = documentService.getVisibleDocuments();
                    if (visible.isEmpty()) return Optional.of(new AskService.AskResponse(true, "No visible documents. Upload via POST /documents/upload or curated manager at GET /documents/admin/all (admin).", List.of()));
                    int show = Math.min(5, visible.size());
                    StringBuilder sb = new StringBuilder("Visible documents (" + visible.size() + ", showing " + show + "):");
                    for (int i = 0; i < show; i++) {
                        var d = visible.get(i);
                        String snippet = d.getContent() != null ? d.getContent().substring(0, Math.min(60, d.getContent().length())).replace("\n"," ") : "";
                        sb.append("\n- #").append(d.getId()).append(" ").append(d.getTitle()).append("  -  \"").append(snippet).append("...\"");
                    }
                    sb.append("\nUpload: POST /documents/upload (multipart .txt/.md, 5MB) | My uploads: GET /documents/my/uploads when signed in | Hide (admin): POST /documents/admin/{id}/visibility");
                    return Optional.of(new AskService.AskResponse(true, sb.toString(), List.of()));
                } catch (Exception ignored) {}
            }
        }

        return Optional.empty();
    }

    private String findSkillInQuestion(String q) {
        Set<String> canonical = new LinkedHashSet<>(skillGraphService.getAllCanonicalSkills());
        for (Student s : studentRepository.findAll()) {
            if (s.getSkills() == null) continue;
            for (String part : s.getSkills().split(",")) {
                String t = part.trim();
                if (t.isEmpty()) continue;
                String n = skillGraphService.normalize(t);
                canonical.add(n != null ? n : t);
            }
        }
        for (String skill : canonical) {
            String lower = skill.toLowerCase();
            if (containsWholeWord(q, lower)) return skill;
        }
        String[] tokens = q.split("[^a-z0-9#+.]+");
        for (String tok : tokens) {
            if (tok.isBlank()) continue;
            String n = skillGraphService.normalize(tok);
            if (n != null && !n.equalsIgnoreCase(tok) && canonical.contains(n)) return n;
        }
        return null;
    }

    private String findStudentNameInQuestion(String qLower) {
        for (var s : studentRepository.findAll()) {
            if (qLower.contains(s.getName().toLowerCase(Locale.ROOT))) return s.getName();
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

    /**
     * Match a topic keyword against an event's title+description. Short/ambiguous
     * tokens ("ai", "ml") must match as whole words — plain substring matching made
     * "ai" fire on "main", "available" and "detail", pulling unrelated events into
     * an "ai workshops" search. Longer, distinctive phrases ("machine learning",
     * "cybersecurity") still allow a loose substring match so hyphenation and
     * spelling variants are tolerated.
     */
    private static boolean topicMatches(String haystack, String topic) {
        if (topic == null || topic.isEmpty()) return false;
        if (topic.length() <= 3 || topic.indexOf(' ') < 0) {
            return containsWholeWord(haystack, topic) || containsWholeWord(haystack, topic.replace('-', ' '));
        }
        return containsWholeWord(haystack, topic) || haystack.contains(topic);
    }

    /**
     * Detects bare greetings: "hello", "hi", "hey", "good morning", etc.
     * Only matches when the entire (lowercased) input is essentially a greeting
     * — so "hello" qualifies, "good morning" qualifies, but "hello world event"
     * or "hello world" does not (the trailing token is not a known name).
     */
    private static boolean isGreeting(String q, Long userId) {
        String trimmed = q.trim();
        if (trimmed.isEmpty()) return false;
        String[] tokens = trimmed.split("\\s+");
        // Single-token greetings: "hello", "hi", "hey"
        if (tokens.length == 1) {
            return GREETING_WORDS.contains(tokens[0]);
        }
        // Two-token greetings: "good morning", "good evening", "good afternoon", "good night"
        if (tokens.length == 2 && tokens[0].equals("good")) {
            return GREETING_WORDS.contains(trimmed);
        }
        // "hello <name>" — greeting + a single token that is a known student name.
        if (tokens.length == 2 && (tokens[0].equals("hello") || tokens[0].equals("hi")) && userId != null) {
            // Defer name validation to the caller (buildGreeting already resolves the user).
            return true;
        }
        return false;
    }

    private static final java.util.Set<String> GREETING_WORDS = java.util.Set.of(
            "hello", "hi", "hey", "hi there", "good morning", "good evening",
            "good afternoon", "good night");

    /**
     * Builds a friendly, personalized greeting for the given user.
     */
    private String buildGreeting(Long userId) {
        if (userId != null) {
            try {
                var student = studentRepository.findById(userId).orElse(null);
                if (student != null && student.getName() != null) {
                    return "Hello " + student.getName() + " 👋 I'm Campus AI, built for Campus Connect. " +
                           "I can help you with: upcoming events and workshops, your profile, projects and teammates, " +
                           "mentorship requests, referrals, bookmarks, notifications, and college documents. " +
                           "What can I help you with today?";
                }
            } catch (Exception ignored) {}
        }
        return "Hello 👋 I'm Campus AI, built for Campus Connect. " +
               "I can help you with: upcoming events and workshops, your profile, projects and teammates, " +
               "mentorship requests, referrals, bookmarks, notifications, and college documents. " +
               "What can I help you with today?";
    }
}
