package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.entity.Student;
import com.campusconnect.campus_ai.repository.AlumniRepository;
import com.campusconnect.campus_ai.repository.EventRepository;
import com.campusconnect.campus_ai.repository.ReviewRepository;
import com.campusconnect.campus_ai.repository.StudentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LookupServiceTest {

    private final StudentRepository studentRepository = mock(StudentRepository.class);
    private final EventRepository eventRepository = mock(EventRepository.class);
    private final AlumniRepository alumniRepository = mock(AlumniRepository.class);
    private final SkillGraphService skillGraphService = mock(SkillGraphService.class);
    private final ReviewRepository reviewRepository = mock(ReviewRepository.class);
    private final ProjectToolService projectToolService = mock(ProjectToolService.class);
    private final MessageService messageService = mock(MessageService.class);
    private final AnalyticsService analyticsService = mock(AnalyticsService.class);
    private final NotificationService notificationService = mock(NotificationService.class);
    private final EventAttendanceService eventAttendanceService = mock(EventAttendanceService.class);
    private final ProjectInviteService projectInviteService = mock(ProjectInviteService.class);
    private final TeammateRequestService teammateRequestService = mock(TeammateRequestService.class);
    private final FollowService followService = mock(FollowService.class);
    private final SkillEndorsementService skillEndorsementService = mock(SkillEndorsementService.class);
    private final BookmarkService bookmarkService = mock(BookmarkService.class);
    private final ProfileService profileService = mock(ProfileService.class);
    private final ActivityService activityService = mock(ActivityService.class);
    private final ModerationService moderationService = mock(ModerationService.class);
    private final MentorshipService mentorshipService = mock(MentorshipService.class);
    private final ReferralService referralService = mock(ReferralService.class);
    private final ChatHistoryService chatHistoryService = mock(ChatHistoryService.class);
    private final DocumentService documentService = mock(DocumentService.class);
    private final SearchService searchService = mock(SearchService.class);
    private final RecruiterPostingService recruiterPostingService = mock(RecruiterPostingService.class);
    private final DashboardService dashboardService = mock(DashboardService.class);

    private LookupService lookupService;

    @BeforeEach
    void setUp() {
        when(studentRepository.findAll()).thenReturn(List.of());
        lookupService = new LookupService(
                studentRepository, eventRepository, alumniRepository, skillGraphService,
                reviewRepository, projectToolService, messageService, analyticsService,
                notificationService, eventAttendanceService, projectInviteService,
                teammateRequestService, followService, skillEndorsementService,
                bookmarkService, profileService, activityService, moderationService,
                mentorshipService, referralService, chatHistoryService, documentService,
                searchService, recruiterPostingService, dashboardService);
    }

    // Helper: create a Student with id set via reflection
    private static Student makeStudent(String name, Long id) {
        Student s = new Student();
        s.setName(name);
        try {
            Field idField = Student.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(s, id);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return s;
    }

    // Helper: create an Event with id set via reflection
    private static Event makeEvent(String title, String type, LocalDate date, String description, String location) {
        Event e = new Event(title, type, date, description, location);
        try {
            Field idField = Event.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(e, 1L);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return e;
    }

    // --- Greeting tests ---

    @Test
    void greeting_hello_returnsPersonalizedGreeting() {
        Student sam = makeStudent("Samarth", 1L);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(sam));

        var opt = lookupService.tryAnswer("hello", 1L);
        assertTrue(opt.isPresent());
        assertTrue(opt.get().answered());
        String answer = opt.get().answer();
        assertTrue(answer.contains("Hello Samarth"), "should greet by name: " + answer);
        assertTrue(answer.contains("Campus AI"), "should mention identity: " + answer);
        assertTrue(answer.contains("What can I help you with today?"), "should offer help: " + answer);
    }

    @Test
    void greeting_helloWithoutUser_returnsNonPersonalizedGreeting() {
        var opt = lookupService.tryAnswer("hello", null);
        assertTrue(opt.isPresent());
        assertTrue(opt.get().answered());
        String answer = opt.get().answer();
        assertTrue(answer.contains("Campus AI"), "should mention identity: " + answer);
        assertTrue(answer.contains("What can I help you with today?"), "should offer help: " + answer);
    }

    @Test
    void greeting_hiShortQuery_returnsGreeting() {
        var opt = lookupService.tryAnswer("hi", null);
        assertTrue(opt.isPresent());
        assertTrue(opt.get().answered());
        assertTrue(opt.get().answer().contains("Campus AI"));
    }

    @Test
    void greeting_helloWithUsername_returnsPersonalizedGreeting() {
        Student sam = makeStudent("Samarth", 1L);
        when(studentRepository.findById(1L)).thenReturn(Optional.of(sam));

        var opt = lookupService.tryAnswer("hello Samarth", 1L);
        assertTrue(opt.isPresent());
        assertTrue(opt.get().answer().contains("Hello Samarth"));
    }

    @Test
    void greeting_helloWorldNotGreeting() {
        // "hello world" is not just a greeting — it has extra content; should
        // fall through to other routes (not match as greeting).
        var opt = lookupService.tryAnswer("hello world", null);
        // It should be empty (no route handles "hello world" without "world" being
        // a keyword), OR handled by another route. Either way it should not
        // return a greeting response.
        if (opt.isPresent()) {
            assertFalse(opt.get().answer().contains("What can I help you with today?"),
                    "plain greeting should not fire for 'hello world'");
        }
    }

    // --- AI workshop filter tests ---

    @Test
    void eventRoute_aiWorkshops_filtersToAIOnly() {
        Event aiWorkshop = makeEvent("AI Workshop", "WORKSHOP",
                LocalDate.now().plusDays(5), "Learn machine learning basics", "Lab 1");
        Event cyberWorkshop = makeEvent("Cybersecurity Workshop", "WORKSHOP",
                LocalDate.now().plusDays(3), "Network security basics", "Lab 2");
        Event hackathon = makeEvent("Auto Hack", "HACKATHON",
                LocalDate.now().plusDays(10), "General hackathon", "Hall A");

        when(eventRepository.findByEventDateGreaterThanEqual(any(LocalDate.class)))
                .thenReturn(List.of(aiWorkshop, cyberWorkshop, hackathon));

        var opt = lookupService.tryAnswer("any upcoming ai workshops", null);
        assertTrue(opt.isPresent(), "should return a response");
        String answer = opt.get().answer();
        assertTrue(answer.contains("AI Workshop"), "should include AI event: " + answer);
        assertFalse(answer.contains("Cybersecurity"), "should exclude non-AI event: " + answer);
        assertFalse(answer.contains("Auto Hack"), "should exclude non-AI event: " + answer);
    }

    @Test
    void eventRoute_aiHackathons_filtersToAIOnly() {
        Event aiHackathon = makeEvent("ML Hackathon", "HACKATHON",
                LocalDate.now().plusDays(15), "Build AI projects", "Hall B");
        Event generalHackathon = makeEvent("Game Jam", "HACKATHON",
                LocalDate.now().plusDays(12), "Make games", "Hall C");

        when(eventRepository.findByEventDateGreaterThanEqual(any(LocalDate.class)))
                .thenReturn(List.of(aiHackathon, generalHackathon));

        var opt = lookupService.tryAnswer("upcoming ai hackathons", null);
        assertTrue(opt.isPresent());
        String answer = opt.get().answer();
        assertTrue(answer.contains("ML Hackathon"), "should include AI hackathon: " + answer);
        assertFalse(answer.contains("Game Jam"), "should exclude non-AI hackathon: " + answer);
    }

    @Test
    void eventRoute_workshopsNoTopicFilter_returnsAllTypes() {
        Event aiWorkshop = makeEvent("AI Workshop", "WORKSHOP",
                LocalDate.now().plusDays(5), "Machine learning", "Lab 1");
        Event cyberWorkshop = makeEvent("Cybersecurity Workshop", "WORKSHOP",
                LocalDate.now().plusDays(3), "Security basics", "Lab 2");

        when(eventRepository.findByEventDateGreaterThanEqual(any(LocalDate.class)))
                .thenReturn(List.of(aiWorkshop, cyberWorkshop));

        var opt = lookupService.tryAnswer("any upcoming workshops", null);
        assertTrue(opt.isPresent());
        String answer = opt.get().answer();
        assertTrue(answer.contains("AI Workshop"));
        assertTrue(answer.contains("Cybersecurity Workshop"));
    }

    // --- Friendly fallback tests are in AskServiceTest ---
}
