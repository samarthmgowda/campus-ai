package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.entity.Project;
import com.campusconnect.campus_ai.repository.ProjectMemberRepository;
import com.campusconnect.campus_ai.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * End-to-end flow for event-driven teammate discovery: resolve event → infer
 * domain → match a project (or stand alone) → rank candidates. Collaborators are
 * mocked so the test pins the orchestration, not the scoring internals (covered by
 * EventDomainServiceTest and the matching service).
 */
class EventTeammateServiceTest {

    private EventDomainService eventDomainService;
    private TeammateMatchingService matchingService;
    private ProjectRepository projectRepository;
    private ProjectMemberRepository memberRepository;
    private ProjectToolService projectToolService;
    private CurrentUserService currentUserService;
    private SkillGraphService skillGraphService;
    private EventTeammateService service;

    @BeforeEach
    void setUp() {
        eventDomainService = mock(EventDomainService.class);
        matchingService = new TeammateMatchingService(projectRepository = mock(ProjectRepository.class),
                memberRepository = mock(ProjectMemberRepository.class),
                mock(com.campusconnect.campus_ai.repository.StudentRepository.class),
                currentUserService = mock(CurrentUserService.class),
                projectToolService = mock(ProjectToolService.class),
                skillGraphService = mock(SkillGraphService.class),
                mock(com.campusconnect.campus_ai.repository.SkillEndorsementRepository.class),
                mock(com.campusconnect.campus_ai.repository.ModerationReportRepository.class),
                mock(com.campusconnect.campus_ai.repository.ReviewRepository.class),
                mock(EmbeddingService.class),
                0.50, 0.20, 0.20, 0.10, 0.30);
        service = new EventTeammateService(eventDomainService, matchingService, projectRepository,
                memberRepository, projectToolService, currentUserService, skillGraphService);
    }

    private static Event aiEvent() {
        return new Event("AI Hackathon", "HACKATHON", LocalDate.now().plusDays(20), "an ai challenge", "Hall A");
    }

    /** Entities get their id from JPA; tests must set it so id-keyed lookups line up. */
    private static void setId(Object entity, Long id) {
        try {
            Class<?> c = entity.getClass();
            while (c != null) {
                try {
                    java.lang.reflect.Field f = c.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(entity, id);
                    return;
                } catch (NoSuchFieldException e) {
                    c = c.getSuperclass();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void standaloneEventWhenNoProjectMatches() {
        Event event = aiEvent();
        when(eventDomainService.inferDomain(event)).thenReturn(
                new EventDomainService.Domain("AI / Machine Learning", List.of("Python", "Machine Learning")));
        when(projectToolService.getMyProjects(1L)).thenReturn(List.of()); // no projects
        when(eventDomainService.domainTokens(any(), any())).thenReturn(java.util.Set.of("python", "machine learning"));

        EventTeammateService.EventTeammatePlan plan = service.plan(event, 1L, true, false);

        assertTrue(plan.standalone(), "no matching project → standalone event");
        assertNull(plan.matchedProject());
        assertEquals("AI / Machine Learning", plan.domain().label());
    }

    @Test
    void matchesProjectWhenDomainOverlaps() {
        Event event = aiEvent();
        var domain = new EventDomainService.Domain("AI / Machine Learning", List.of("Python", "Machine Learning"));
        when(eventDomainService.inferDomain(event)).thenReturn(domain);
        when(eventDomainService.domainTokens(any(), any())).thenReturn(java.util.Set.of("python"));

        ProjectToolService.ProjectSummary summary =
                new ProjectToolService.ProjectSummary(7L, "ai research 2026", "AI", "Python, Machine Learning", "ACTIVE", "Sam");
        when(projectToolService.getMyProjects(1L)).thenReturn(List.of(summary));

        Project p = new Project("ai research 2026", "desc", "AI", "Python, Machine Learning", "ACTIVE", null);
        setId(p, 7L);
        when(projectRepository.findById(7L)).thenReturn(java.util.Optional.of(p));
        when(projectToolService.getSkillGaps(7L)).thenReturn(List.of("Machine Learning"));
        // High domain↔project overlap so the project is selected.
        when(eventDomainService.projectMatchScore(any(), eq(domain), any())).thenReturn(0.80);
        when(memberRepository.findByProjectId(7L)).thenReturn(List.of());

        EventTeammateService.EventTeammatePlan plan = service.plan(event, 1L, true, false);

        assertFalse(plan.standalone(), "overlapping project → not standalone");
        assertNotNull(plan.matchedProject());
        assertEquals(7L, plan.matchedProject().projectId());
        assertEquals(List.of("Machine Learning"), plan.matchedProject().gaps());
    }

    @Test
    void whySummarisesSignals() {
        var c = new TeammateMatchingService.ScoredCandidate(
                5L, "Priya", "ISE", 2, "React, JavaScript", "AVAILABLE",
                List.of("React"), 0.5, 0.4, 1.0, 0.0, null, 0.62, 3L, 4.5, false);
        String why = EventTeammateService.why(c);
        assertTrue(why.contains("React"));
        assertTrue(why.contains("endorsement"));
        assertTrue(why.contains("4.5"));
        assertTrue(why.contains("available"));
    }
}
