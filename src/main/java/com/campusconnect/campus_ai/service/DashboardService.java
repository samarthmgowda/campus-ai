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
 * Role-based dashboards (Phase 2 M5).
 * Aggates per-role views: projects, events, connections, skill growth, activity, etc.
 */
@Service
public class DashboardService {

    private final CurrentUserService currentUserService;
    private final StudentRepository studentRepository;
    private final AlumniRepository alumniRepository;
    private final ProjectToolService projectToolService;
    private final ProjectInviteService projectInviteService;
    private final TeammateRequestService teammateRequestService;
    private final EventAttendanceService eventAttendanceService;
    private final FollowService followService;
    private final NotificationService notificationService;
    private final MessageService messageService;
    private final CommunityService communityService;
    private final ReviewService reviewService;
    private final SkillEndorsementService skillEndorsementService;
    private final BookmarkService bookmarkService;
    private final MentorshipService mentorshipService;
    private final ReferralService referralService;
    private final RecruiterPostingService recruiterPostingService;
    private final SkillGraphService skillGraphService;

    public DashboardService(CurrentUserService currentUserService,
                            StudentRepository studentRepository,
                            AlumniRepository alumniRepository,
                            ProjectToolService projectToolService,
                            ProjectInviteService projectInviteService,
                            TeammateRequestService teammateRequestService,
                            EventAttendanceService eventAttendanceService,
                            FollowService followService,
                            NotificationService notificationService,
                            MessageService messageService,
                            CommunityService communityService,
                            ReviewService reviewService,
                            SkillEndorsementService skillEndorsementService,
                            BookmarkService bookmarkService,
                            MentorshipService mentorshipService,
                            ReferralService referralService,
                            RecruiterPostingService recruiterPostingService,
                            SkillGraphService skillGraphService) {
        this.currentUserService = currentUserService;
        this.studentRepository = studentRepository;
        this.alumniRepository = alumniRepository;
        this.projectToolService = projectToolService;
        this.projectInviteService = projectInviteService;
        this.teammateRequestService = teammateRequestService;
        this.eventAttendanceService = eventAttendanceService;
        this.followService = followService;
        this.notificationService = notificationService;
        this.messageService = messageService;
        this.communityService = communityService;
        this.reviewService = reviewService;
        this.skillEndorsementService = skillEndorsementService;
        this.bookmarkService = bookmarkService;
        this.mentorshipService = mentorshipService;
        this.referralService = referralService;
        this.recruiterPostingService = recruiterPostingService;
        this.skillGraphService = skillGraphService;
    }

    // ——— Student Dashboard ———

    public Map<String, Object> getStudentDashboard(Long studentId) {
        Student s = currentUserService.requireUser(studentId);

        Map<String, Object> dash = new LinkedHashMap<>();

        // Identity summary
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("name", s.getName());
        identity.put("branch", s.getBranch());
        identity.put("year", s.getYear());
        identity.put("availability", s.getAvailability());
        dash.put("identity", identity);

        // Projects (membership + invites)
        try {
            var myProjects = projectToolService.getMyProjects(studentId);
            dash.put("projects", Map.of("count", myProjects.size(), "items", myProjects));
        } catch (Exception e) {
            dash.put("projects", Map.of("count", 0, "items", List.of()));
        }
        try {
            var pendingInvites = projectInviteService.getMyInvites(studentId, "PENDING");
            dash.put("pendingInvites", Map.of("count", pendingInvites.size(), "items", pendingInvites));
        } catch (Exception e) {
            dash.put("pendingInvites", Map.of("count", 0, "items", List.of()));
        }
        try {
            var sentRequests = teammateRequestService.getSentRequests(studentId);
            dash.put("sentRequests", Map.of("count", sentRequests.size(), "items", sentRequests));
        } catch (Exception e) {
            dash.put("sentRequests", Map.of("count", 0, "items", List.of()));
        }

        // Events (RSVPs)
        try {
            var rsvps = eventAttendanceService.getMyEvents(studentId);
            dash.put("events", Map.of("count", rsvps.size(), "items", rsvps));
        } catch (Exception e) {
            dash.put("events", Map.of("count", 0, "items", List.of()));
        }

        // Connections
        try {
            dash.put("connections", followService.getStats(studentId));
        } catch (Exception e) {
            dash.put("connections", Map.of());
        }

        // Reputation
        try {
            dash.put("reputation", reviewService.getReputationForStudent(studentId));
        } catch (Exception e) {
            dash.put("reputation", Map.of());
        }

        // Skill growth recommendations
        try {
            var skillData = skillGraphService.getDashboardData(studentId);
            if (skillData != null && skillData.get("skillsToGrow") != null) {
                dash.put("skillRecommendations", Map.of("recommendations", skillData.get("skillsToGrow")));
            } else {
                dash.put("skillRecommendations", Map.of("recommendations", List.of()));
            }
        } catch (Exception e) {
            dash.put("skillRecommendations", Map.of("recommendations", List.of()));
        }

        // Activity
        try {
            dash.put("unreadNotifications", notificationService.getUnreadCount(studentId));
        } catch (Exception e) {
            dash.put("unreadNotifications", 0);
        }
        try {
            dash.put("unreadMessages", messageService.getUnreadCount(studentId));
        } catch (Exception e) {
            dash.put("unreadMessages", 0);
        }
        try {
            var posts = communityService.getPostsByAuthor(studentId);
            dash.put("posts", Map.of("count", posts.size()));
        } catch (Exception e) {
            dash.put("posts", Map.of("count", 0));
        }
        try {
            dash.put("bookmarks", bookmarkService.saveSummary(studentId));
        } catch (Exception e) {
            dash.put("bookmarks", Map.of());
        }

        return dash;
    }

    // ——— Alumni Dashboard ———

    public Map<String, Object> getAlumniDashboard(Long alumniId, Long adminAsUserId) {
        Alumni a = alumniRepository.findById(alumniId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Alumni not found: " + alumniId));

        Map<String, Object> dash = new LinkedHashMap<>();

        // Identity summary
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("name", a.getName());
        identity.put("branch", a.getBranch());
        identity.put("graduationYear", a.getGraduationYear());
        identity.put("currentRole", a.getCurrentRole());
        identity.put("currentCompany", a.getCurrentCompany());
        identity.put("availability", a.getAvailability());
        dash.put("identity", identity);

        // Mentorship / referral activity
        try {
            var incomingRequests = mentorshipService.getIncomingRequests(alumniId, "PENDING");
            dash.put("mentorshipRequests", Map.of("count", incomingRequests.size(), "items", incomingRequests));
        } catch (Exception e) {
            dash.put("mentorshipRequests", Map.of("count", 0, "items", List.of()));
        }
        try {
            var referralRequests = referralService.getForAlumni(alumniId, "PENDING");
            dash.put("referralRequests", Map.of("count", referralRequests.size(), "items", referralRequests));
        } catch (Exception e) {
            dash.put("referralRequests", Map.of("count", 0, "items", List.of()));
        }

        // Connections (who follows this alumni's activity)
        try {
            if (adminAsUserId != null) {
                dash.put("connections", followService.getStats(adminAsUserId));
            }
        } catch (Exception e) {
            dash.put("connections", Map.of());
        }

        // Reputation (if alumni also has student profile / reviews)
        try {
            dash.put("skillRecommendations", skillGraphService.getDashboardData(adminAsUserId != null ? adminAsUserId : null));
        } catch (Exception e) {
            dash.put("skillRecommendations", Map.of());
        }

        return dash;
    }

    // ——— Recruiter Dashboard ———

    public Map<String, Object> getRecruiterDashboard(Long recruiterId) {
        // Recruiters see postings, applications, and shortlisted candidates
        Map<String, Object> dash = new LinkedHashMap<>();

        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("role", "recruiter");
        identity.put("userId", recruiterId);
        dash.put("identity", identity);

        // Postings + applications (handled by RecruiterPostingService)
        // This is the heaviest dashboard — requires recruiter permission model
        try {
            var postings = recruiterPostingService.getPostings(recruiterId);
            dash.put("postings", Map.of("count", postings.size(), "items", postings));
        } catch (Exception e) {
            dash.put("postings", Map.of("count", 0, "items", List.of()));
        }

        try {
            dash.put("unreadNotifications", notificationService.getUnreadCount(recruiterId));
        } catch (Exception e) {
            dash.put("unreadNotifications", 0);
        }

        return dash;
    }

    // ——— Role detection for /ask routing ———

    public String detectRoleForStudent(Long userId) {
        Student s = studentRepository.findById(userId).orElse(null);
        if (s == null) return null;
        // Check if student is also an alumni record (graduated)
        var alumni = alumniRepository.findAll().stream()
                .filter(a -> a.getName() != null && a.getName().equals(s.getName()))
                .findFirst();
        if (alumni.isPresent()) return "alumni";
        return "student";
    }
}
