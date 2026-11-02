package com.campusconnect.campus_ai.service.tools;

import com.campusconnect.campus_ai.service.EventDomainService;
import com.campusconnect.campus_ai.service.EventTeammateService;
import com.campusconnect.campus_ai.service.ProjectToolService;
import com.campusconnect.campus_ai.service.TeammateMatchingService;
import com.campusconnect.campus_ai.service.Tool;
import com.campusconnect.campus_ai.service.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Locks in the trigger-matching behavior for teammate discovery, including the
 * loose phrasings ("find me a best teammates for the AI hack hackathon") and
 * common typos ("teamates"). Guards against regressions where a natural request
 * fails to route to ProjectTeammateTool.
 */
class ProjectTeammateToolTriggerTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
        ProjectToolService projectToolService = mock(ProjectToolService.class);
        registry.register(new ProjectTeammateTool(projectToolService,
                mock(TeammateMatchingService.class),
                mock(EventDomainService.class),
                mock(EventTeammateService.class)));
    }

    private boolean matches(String intent) {
        List<Tool> matched = registry.findMatchingTools(intent);
        return matched.stream().anyMatch(t -> t.name().equals("findTeammates"));
    }

    @Test
    void matchesUserReportedPhrasing() {
        assertTrue(matches("can u find me a best teammates for the auto hack hacathon"),
                "loose 'find me a best teammates' phrasing should route to teammate tool");
    }

    @Test
    void matchesTypoVariant() {
        assertTrue(matches("find me a best teamates for the auto hack hacathon"),
                "'teamates' typo should be repaired and match");
    }

    @Test
    void matchesCommonPhrasings() {
        String[] intents = {
                "find teammates for my project",
                "find me teammates",
                "help me find a teammate",
                "find me a team for the hackathon",
                "who are the best teammates here",
                "I need a teammate for my capstone",
                "need teammates for the robotics club project",
                "search for a teammate with React skills",
        };
        for (String intent : intents) {
            assertTrue(matches(intent), "expected match for: " + intent);
        }
    }

    @Test
    void matchesProjectListingPhrasings() {
        assertTrue(matches("show my projects"));
        assertTrue(matches("list my project"));
        assertTrue(matches("my project"));
    }

    @Test
    void doesNotMatchUnrelatedIntents() {
        String[] intents = {
                "what is the weather today",
                "summarize my notes",
                "show my notifications",
                "find me a mentor for ai",
                "I need team spirit in this club",
                "how do I manage my team efficiently",
                "what team won the esports tournament",
        };
        for (String intent : intents) {
            assertFalse(matches(intent), "did not expect match for: " + intent);
        }
    }
}
