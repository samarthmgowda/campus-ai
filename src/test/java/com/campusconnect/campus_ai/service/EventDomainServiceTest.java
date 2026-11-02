package com.campusconnect.campus_ai.service;

import com.campusconnect.campus_ai.entity.Event;
import com.campusconnect.campus_ai.repository.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Covers event resolution ("which event did the user mean") and domain inference
 * ("what is this hackathon about") for event-driven teammate discovery.
 */
class EventDomainServiceTest {

    private EventRepository eventRepository;
    private EventDomainService service;

    @BeforeEach
    void setUp() {
        eventRepository = mock(EventRepository.class);
        service = new EventDomainService(eventRepository);
    }

    private static Event event(String title, String type, String description, LocalDate date) {
        Event e = new Event(title, type, date, description, "Hall A");
        return e;
    }

    // ——— domain inference ———

    @Test
    void infersAiDomainFromTitle() {
        Event e = event("Auto Hack AI Edition", "HACKATHON", "build an ML model", LocalDate.now().plusDays(20));
        EventDomainService.Domain d = service.inferDomain(e);
        assertEquals("AI / Machine Learning", d.label());
        assertTrue(d.skills().contains("Python"));
        assertTrue(d.skills().contains("Machine Learning"));
    }

    @Test
    void infersAiDomainFromDescriptionOnly() {
        Event e = event("Auto Hack", "HACKATHON", "a machine learning and deep learning challenge", LocalDate.now());
        assertEquals("AI / Machine Learning", service.inferDomain(e).label());
    }

    @Test
    void infersCybersecurityDomain() {
        Event e = event("Cyber CTF", "WORKSHOP", "capture the flag security challenge", LocalDate.now());
        assertEquals("Cybersecurity", service.inferDomain(e).label());
    }

    @Test
    void infersCloudDomain() {
        Event e = event("Kubernetes Workshop", "WORKSHOP", "deploy with docker on aws", LocalDate.now());
        assertEquals("Cloud / DevOps", service.inferDomain(e).label());
    }

    @Test
    void fallsBackToGenericWhenNothingMatches() {
        Event e = event("Auto Hack 021312", "HACKATHON", "auto event", LocalDate.now());
        EventDomainService.Domain d = service.inferDomain(e);
        assertEquals("Software / Hackathon", d.label());
        assertFalse(d.skills().isEmpty());
    }

    @Test
    void aiDomainDoesNotFireOnSubstringWords() {
        // "main" and "available" contain "ai" as a substring but are not AI signals.
        Event e = event("Main Hall Session", "SEMINAR", "available to all students", LocalDate.now());
        assertNotEquals("AI / Machine Learning", service.inferDomain(e).label());
    }

    // ——— event resolution ———

    @Test
    void resolvesEventByTitleTokens() {
        Event autoHack = event("Auto Hack", "HACKATHON", "ai hackathon", LocalDate.now().plusDays(19));
        when(eventRepository.findAll()).thenReturn(List.of(autoHack));
        Optional<Event> resolved = service.resolveEvent("can u find me a best teamates for the auto hack hacathon");
        assertTrue(resolved.isPresent());
        assertEquals("Auto Hack", resolved.get().getTitle());
    }

    @Test
    void returnsEmptyWhenNoEventMentioned() {
        Event autoHack = event("Auto Hack", "HACKATHON", "ai hackathon", LocalDate.now().plusDays(19));
        when(eventRepository.findAll()).thenReturn(List.of(autoHack));
        assertTrue(service.resolveEvent("find me teammates for my project").isEmpty());
    }

    @Test
    void prefersUpcomingOverPastOnTie() {
        Event past = event("Robotics Hack", "HACKATHON", "robots", LocalDate.now().minusDays(30));
        Event future = event("Robotics Hack", "HACKATHON", "robots", LocalDate.now().plusDays(10));
        when(eventRepository.findAll()).thenReturn(List.of(past, future));
        Optional<Event> resolved = service.resolveEvent("teammates for the robotics hack");
        assertTrue(resolved.isPresent());
        assertEquals(future.getEventDate(), resolved.get().getEventDate());
    }

    @Test
    void ignoresHiddenEvents() {
        Event hidden = event("Auto Hack", "HACKATHON", "ai", LocalDate.now().plusDays(5));
        hidden.setHidden(true);
        when(eventRepository.findAll()).thenReturn(List.of(hidden));
        assertTrue(service.resolveEvent("teammates for the auto hack").isEmpty());
    }

    // ——— project ↔ domain matching ———

    @Test
    void projectMatchesDomainBySkills() {
        EventDomainService.Domain ai = new EventDomainService.Domain("AI / Machine Learning",
                List.of("Python", "Machine Learning", "PyTorch", "TensorFlow", "Deep Learning"));
        EventDomainService.ProjectLike project = new EventDomainService.ProjectLike() {
            @Override public String domainText() { return "AI research"; }
            @Override public String requiredSkillsCsv() { return "Python, Machine Learning, PyTorch"; }
        };
        // 3 of 5 domain skills present + label bonus → comfortably above threshold.
        double score = service.projectMatchScore(project, ai, mock(SkillGraphService.class, invocation -> {
            // normalize() is identity for these canonical names
            return invocation.getArgument(0);
        }));
        assertTrue(score >= 0.34, "expected match, got " + score);
    }

    @Test
    void unrelatedProjectDoesNotMatchDomain() {
        EventDomainService.Domain ai = new EventDomainService.Domain("AI / Machine Learning",
                List.of("Python", "Machine Learning", "PyTorch", "TensorFlow", "Deep Learning"));
        EventDomainService.ProjectLike project = new EventDomainService.ProjectLike() {
            @Override public String domainText() { return "Web portal"; }
            @Override public String requiredSkillsCsv() { return "React, JavaScript, CSS"; }
        };
        double score = service.projectMatchScore(project, ai, mock(SkillGraphService.class, invocation -> invocation.getArgument(0)));
        assertTrue(score < 0.34, "expected no match, got " + score);
    }
}
